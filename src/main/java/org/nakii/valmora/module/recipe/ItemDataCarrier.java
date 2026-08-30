package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.modifier.ModifierInstance;
import org.nakii.valmora.module.modifier.ModifierModule;

import java.util.List;
import java.util.Map;

/**
 * "keep-data-on-upgrade" (coworker recipe-yaml note + anvil §UPGRADE) — carries enchantments,
 * every attached modifier-framework component (reforges/gemstones/any group, generic across the
 * whole framework, not special-cased), durability, and a custom display name from an existing
 * item onto a freshly built result item, before the caller layers its own {@code add_lore}/
 * {@code add_enchants}/{@code add_nbt}. Used by both a {@code keep-data-on-upgrade: true} crafting
 * recipe and the anvil's {@code type: UPGRADE} recipes — one helper, not duplicated per machine.
 */
public final class ItemDataCarrier {

    private ItemDataCarrier() {}

    public static ItemStack carryForward(Valmora plugin, ItemStack source, ItemStack result) {
        if (source == null || result == null || !source.hasItemMeta() || !result.hasItemMeta()) return result;

        ItemStack output = result.clone();
        ItemMeta srcMeta = source.getItemMeta();

        // Enchantments — EnchantmentHelper round-trips its own ItemMeta, so apply first.
        Map<String, Integer> enchants = EnchantmentHelper.getEnchantments(source);
        if (!enchants.isEmpty()) {
            EnchantmentHelper.applyEnchantmentMap(output, enchants);
        }

        // Every modifier-framework component (reforges, gemstones, any future group) — generic,
        // no group-specific code, per CLAUDE.md's hard rule on the modifier framework.
        ModifierModule modifierModule = plugin.getModifierModule();
        if (modifierModule != null) {
            ItemMeta outMeta = output.getItemMeta();
            Map<String, List<ModifierInstance>> components =
                    modifierModule.getStore().readAll(srcMeta, modifierModule.getGroupRegistry());
            for (Map.Entry<String, List<ModifierInstance>> entry : components.entrySet()) {
                modifierModule.getStore().write(outMeta, entry.getKey(), entry.getValue());
            }
            output.setItemMeta(outMeta);
        }

        // Durability
        ItemMeta outMeta = output.getItemMeta();
        if (outMeta instanceof Damageable outDmg && srcMeta instanceof Damageable srcDmg) {
            short maxDurability = output.getType().getMaxDurability();
            if (maxDurability > 0) {
                outDmg.setDamage(Math.min(srcDmg.getDamage(), (int) maxDurability));
                output.setItemMeta(outMeta);
            }
        }

        // Custom display name — only if the result didn't already declare its own override.
        outMeta = output.getItemMeta();
        if (srcMeta.hasDisplayName() && !outMeta.hasDisplayName()) {
            outMeta.displayName(srcMeta.displayName());
            output.setItemMeta(outMeta);
        }

        // Final rebuild — the enchant-carry step above already triggered one lore/name rebuild
        // (EnchantmentHelper.applyEnchantmentMap routes through ItemFactory.updateLore), but that
        // happened BEFORE the modifier component was written to output's PDC above, so it shipped
        // with a stale display name (missing e.g. a reforge's PREFIX/SUFFIX text) and stat lore
        // (missing the modifier's contributed stat bonuses) even though the underlying PDC data was
        // already correct. Re-running it now, after every piece of carried data is in place, is
        // idempotent when nothing changed (no enchants/no modifiers carried) and otherwise the only
        // point in this method where the result's name/lore actually reflect everything carried.
        if (output.hasItemMeta()) {
            plugin.getItemManager().getItemFactory().updateLore(output);
        }

        return output;
    }
}
