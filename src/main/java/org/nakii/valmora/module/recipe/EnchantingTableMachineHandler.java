package org.nakii.valmora.module.recipe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * A gui_force_craft-compatible dynamic handler for a hypothetical {@code enchanting_table}
 * machine GUI — previously the {@code enchanting_table} machine id only ever fell through to
 * (machine-agnostic, since fixed) vanilla crafting-table recipes, i.e. never actually enchanted
 * anything via the recipe engine.
 *
 * <p>Note: the shipped {@code guis/enchanting.yml} does <b>not</b> use this — it has its own
 * fully custom interaction (`enchant_select`/`enchant_apply`/`enchant_remove` script events,
 * see {@code EnchantSelectEventFactory} and friends) that acts on the input item directly rather
 * than going through a craft button. This handler is for GUI authors who'd rather build an
 * enchanting GUI the same data-driven way as the anvil/reforge GUIs (a plain
 * {@code gui_force_craft} button), with input slot ids {@code item} (required) and {@code lapis}
 * (optional, consumed 1-per-level as a cost) — it picks one random eligible enchantment for the
 * item's {@link ItemType} at a random level up to that enchantment's {@code etable-max-level}.
 */
public class EnchantingTableMachineHandler implements DynamicMachineHandler {

    private final Valmora plugin;
    private final Random random = new Random();

    public EnchantingTableMachineHandler(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) {
        return match(inputs, null);
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
        ItemStack item = inputs.get("item");
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return Optional.empty();

        String itemTypeStr = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_TYPE_KEY, org.bukkit.persistence.PersistentDataType.STRING);
        if (itemTypeStr == null) return Optional.empty();
        ItemType itemType;
        try {
            itemType = ItemType.valueOf(itemTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        List<EnchantmentDefinition> eligible = new ArrayList<>();
        for (var def : plugin.getEnchantModule().getRegistry().values()) {
            if (def.canApplyTo(itemType) && def.getEtableMaxLevel() > 0) eligible.add(def);
        }
        if (eligible.isEmpty()) return Optional.empty();

        EnchantmentDefinition chosen = eligible.get(random.nextInt(eligible.size()));
        int level = 1 + random.nextInt(chosen.getEtableMaxLevel());

        ItemStack lapis = inputs.get("lapis");
        int lapisAvailable = lapis != null ? lapis.getAmount() : 0;
        if (lapisAvailable < level) return Optional.empty();

        ItemStack output = item.clone();
        EnchantmentHelper.applyEnchantment(output, chosen.getId(), level);

        return Optional.of(RecipeDefinition.dynamic("enchanting_table", output, inp -> {
            ItemStack itemSlot = inp.get("item");
            if (itemSlot != null) itemSlot.setAmount(0);
            ItemStack lapisSlot = inp.get("lapis");
            if (lapisSlot != null) lapisSlot.setAmount(lapisSlot.getAmount() - level);
        }));
    }
}
