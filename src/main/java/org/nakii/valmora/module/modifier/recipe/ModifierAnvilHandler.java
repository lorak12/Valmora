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
 * {@code STAT_MODIFIER} recipe operation, and (via the {@code addition.item}-less recipe form + the
 * {@code modifier.id: RANDOM} sentinel) also the generic replacement for the legacy reforge system's
 * two anvil machines ({@code reforge_anvil}: item + specific stone; {@code forge_random}: item alone
 * → weighted random reroll). Input slots: {@code base_item} (required) and {@code addition_item}
 * (required only when the recipe declares an {@code addition.item}).
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
                boolean needsAddition = recipe.getAdditionItemId() != null;
                if (needsAddition) {
                    if (isEmpty(additionItem)) continue;
                    if (!matchesAdditionItem(additionItem, recipe.getAdditionItemId())) continue;
                    if (additionItem.getAmount() < recipe.getAdditionAmount()) continue;
                } else if (!isEmpty(additionItem)) {
                    // A recipe with no declared addition (e.g. a random reforge reroll) shouldn't
                    // silently match while the player has an unrelated item sitting in that slot.
                    continue;
                }
                if (!checkAndNotifyCost(player, recipe, baseItem)) continue;

                var outcome = "RANDOM".equalsIgnoreCase(recipe.getModifierId())
                        ? engine.applyRandom(baseItem, recipe.getModifierGroup())
                        : engine.apply(baseItem, recipe.getModifierGroup(), recipe.getModifierId(), recipe.getModifierTier());
                if (!outcome.isSuccess()) continue;

                ItemStack output = outcome.item();
                plugin.getItemManager().getItemFactory().updateLore(output);
                return Optional.of(RecipeDefinition.dynamic("custom_anvil", output, inp -> {
                    if (needsAddition) consumeAmount(inp, "addition_item", recipe.getAdditionAmount());
                    consumeItem(inp, "base_item");
                    deductCost(player, recipe, baseItem);
                }));
            } else { // REMOVE_MODIFIER
                var outcome = engine.remove(baseItem, recipe.getModifierGroup(), recipe.getModifierId());
                if (!outcome.isSuccess()) continue;
                if (!checkAndNotifyCost(player, recipe, baseItem)) continue;

                ItemStack output = outcome.item();
                plugin.getItemManager().getItemFactory().updateLore(output);
                return Optional.of(RecipeDefinition.dynamic("custom_anvil", output, inp -> {
                    consumeItem(inp, "base_item");
                    deductCost(player, recipe, baseItem);
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
        return org.nakii.valmora.module.item.ItemView.templateType(item);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() == 0;
    }

    private boolean checkAndNotifyCost(@Nullable Player player, ModifierRecipeDefinition recipe, ItemStack baseItem) {
        if (player == null) return true;
        int coins = resolveCost(recipe.getCostCoins(), baseItem);
        int xpLevels = resolveCost(recipe.getCostXpLevels(), baseItem);

        if (coins > 0) {
            var eco = plugin.getEconomy();
            if (eco != null && !eco.hasCoins(player, coins)) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(Formatter.format("<red>You need <gold>" + coins + " Coins</gold> for this modifier.")));
                return false;
            }
        }
        if (xpLevels > 0 && player.getLevel() < xpLevels) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Formatter.format("<red>You need <green>" + xpLevels + " XP levels</green> for this modifier.")));
            return false;
        }
        return true;
    }

    private void deductCost(@Nullable Player player, ModifierRecipeDefinition recipe, ItemStack baseItem) {
        if (player == null) return;
        int coins = resolveCost(recipe.getCostCoins(), baseItem);
        int xpLevels = resolveCost(recipe.getCostXpLevels(), baseItem);

        if (coins > 0) {
            var eco = plugin.getEconomy();
            if (eco != null) eco.removeCoins(player, coins);
        }
        if (xpLevels > 0) {
            player.setLevel(Math.max(0, player.getLevel() - xpLevels));
        }
    }

    private int resolveCost(org.nakii.valmora.module.modifier.value.ValueResolver cost, ItemStack baseItem) {
        return (int) Math.round(cost.resolve(engine.readRarity(baseItem), 1, null));
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
