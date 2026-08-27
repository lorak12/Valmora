package org.nakii.valmora.module.modifier.recipe;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.modifier.ModifierEngine;
import org.nakii.valmora.module.recipe.DynamicMachineHandler;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code DynamicMachineHandler} for the {@code custom_anvil} machine (docs/
 * Valmora_Modifier_Framework_Design.docx §16) — the generic replacement for a gemstone-specific
 * {@code STAT_MODIFIER} recipe operation. Input slots: {@code base_item} (required) and {@code
 * addition_item} (required for APPLY_MODIFIER, ignored for REMOVE_MODIFIER). Mirrors {@code
 * ReforgeModule}'s {@code reforge_anvil} handler shape.
 */
public class ModifierAnvilHandler implements DynamicMachineHandler {

    private final Valmora plugin;
    private final ModifierRecipeRegistry recipes;
    private final ModifierEngine engine;

    public ModifierAnvilHandler(Valmora plugin, ModifierRecipeRegistry recipes, ModifierEngine engine) {
        this.plugin = plugin;
        this.recipes = recipes;
        this.engine = engine;
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) {
        return match(inputs, null);
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
        ItemStack baseItem = inputs.get("base_item");
        if (isEmpty(baseItem)) return Optional.empty();
        ItemStack additionItem = inputs.get("addition_item");

        ItemType baseType = readItemType(baseItem);

        for (ModifierRecipeDefinition recipe : recipes.values()) {
            if (!"custom_anvil".equalsIgnoreCase(recipe.getMachine())) continue;
            if (!recipe.appliesToBase(baseType)) continue;

            if (recipe.getOperation() == ModifierRecipeDefinition.Operation.APPLY_MODIFIER) {
                if (isEmpty(additionItem)) continue;
                if (!matchesAdditionItem(additionItem, recipe.getAdditionItemId())) continue;
                if (additionItem.getAmount() < recipe.getAdditionAmount()) continue;
                if (!checkAndNotifyCost(player, recipe)) continue;

                var outcome = engine.apply(baseItem, recipe.getModifierGroup(), recipe.getModifierId(), recipe.getModifierTier());
                if (!outcome.isSuccess()) continue;

                ItemStack output = outcome.item();
                plugin.getItemManager().getItemFactory().updateLore(output);
                return Optional.of(RecipeDefinition.dynamic("custom_anvil", output, inp -> {
                    consumeAmount(inp, "addition_item", recipe.getAdditionAmount());
                    consumeItem(inp, "base_item");
                    deductCost(player, recipe);
                }));
            } else { // REMOVE_MODIFIER
                var outcome = engine.remove(baseItem, recipe.getModifierGroup(), recipe.getModifierId());
                if (!outcome.isSuccess()) continue;
                if (!checkAndNotifyCost(player, recipe)) continue;

                ItemStack output = outcome.item();
                plugin.getItemManager().getItemFactory().updateLore(output);
                return Optional.of(RecipeDefinition.dynamic("custom_anvil", output, inp -> {
                    consumeItem(inp, "base_item");
                    deductCost(player, recipe);
                }));
            }
        }
        return Optional.empty();
    }

    private boolean matchesAdditionItem(ItemStack item, String expectedId) {
        if (expectedId == null) return false;
        String normalizedExpected = expectedId.contains(":") ? expectedId.substring(expectedId.indexOf(':') + 1) : expectedId;
        if (item.hasItemMeta()) {
            String actualId = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (actualId != null) return actualId.equalsIgnoreCase(normalizedExpected);
        }
        return item.getType().name().equalsIgnoreCase(normalizedExpected);
    }

    private ItemType readItemType(ItemStack item) {
        if (!item.hasItemMeta()) return ItemType.NONE;
        String raw = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
        return raw == null ? ItemType.NONE : ItemType.find(raw).orElse(ItemType.NONE);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() == 0;
    }

    private boolean checkAndNotifyCost(@Nullable Player player, ModifierRecipeDefinition recipe) {
        if (player == null) return true;
        if (recipe.getCostCoins() > 0) {
            var eco = plugin.getEconomy();
            if (eco != null && !eco.hasCoins(player, recipe.getCostCoins())) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(Formatter.format("<red>You need <gold>" + recipe.getCostCoins() + " Coins</gold> for this modifier.")));
                return false;
            }
        }
        if (recipe.getCostXpLevels() > 0 && player.getLevel() < recipe.getCostXpLevels()) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Formatter.format("<red>You need <green>" + recipe.getCostXpLevels() + " XP levels</green> for this modifier.")));
            return false;
        }
        return true;
    }

    private void deductCost(@Nullable Player player, ModifierRecipeDefinition recipe) {
        if (player == null) return;
        if (recipe.getCostCoins() > 0) {
            var eco = plugin.getEconomy();
            if (eco != null) eco.removeCoins(player, recipe.getCostCoins());
        }
        if (recipe.getCostXpLevels() > 0) {
            player.setLevel(Math.max(0, player.getLevel() - recipe.getCostXpLevels()));
        }
    }

    private void consumeItem(Map<String, ItemStack> inputs, String key) {
        ItemStack item = inputs.get(key);
        if (item != null) item.setAmount(0);
    }

    private void consumeAmount(Map<String, ItemStack> inputs, String key, int amount) {
        ItemStack item = inputs.get(key);
        if (item != null) item.setAmount(Math.max(0, item.getAmount() - amount));
    }
}
