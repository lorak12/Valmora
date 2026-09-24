package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.nakii.valmora.Valmora;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.module.enchant.EnchantStateStore;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.util.Keys;
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
            // applyEnchantmentMap only carries levels; copy each enchant's persistent state too
            // (e.g. a kill counter) — it used to be reset to empty on every upgrade.
            Map<String, EnchantStateStore.EnchantInstance> srcInstances = EnchantStateStore.load(srcMeta);
            ItemMeta enchantMeta = output.getItemMeta();
            Map<String, EnchantStateStore.EnchantInstance> outInstances = EnchantStateStore.load(enchantMeta);
            boolean stateCarried = false;
            for (Map.Entry<String, EnchantStateStore.EnchantInstance> e : outInstances.entrySet()) {
                EnchantStateStore.EnchantInstance src = srcInstances.get(e.getKey());
                if (src != null && !src.getState().isEmpty()) {
                    src.getState().forEach(e.getValue()::setStateValue);
                    stateCarried = true;
                }
            }
            if (stateCarried) {
                EnchantStateStore.save(enchantMeta, outInstances);
                output.setItemMeta(enchantMeta);
            }
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

        // Per-instance data that must survive an upgrade. Previously only a display name was
        // carried (and only when the result had none, which a defined item never does), so a
        // player's rename, a backpack's contents and the anvil work counter were all lost.
        outMeta = output.getItemMeta();
        var srcPdc = srcMeta.getPersistentDataContainer();
        var outPdc = outMeta.getPersistentDataContainer();
        String customName = srcPdc.get(Keys.CUSTOM_NAME_KEY, PersistentDataType.STRING);
        if (customName != null && !outPdc.has(Keys.CUSTOM_NAME_KEY, PersistentDataType.STRING)) {
            outPdc.set(Keys.CUSTOM_NAME_KEY, PersistentDataType.STRING, customName);
        }
        byte[] storage = srcPdc.get(Keys.STORAGE_CONTENTS_KEY, PersistentDataType.BYTE_ARRAY);
        if (storage != null && !outPdc.has(Keys.STORAGE_CONTENTS_KEY, PersistentDataType.BYTE_ARRAY)) {
            outPdc.set(Keys.STORAGE_CONTENTS_KEY, PersistentDataType.BYTE_ARRAY, storage);
        }
        Integer work = srcPdc.get(Keys.ANVIL_WORK_COUNT_KEY, PersistentDataType.INTEGER);
        if (work != null) {
            int outWork = outPdc.getOrDefault(Keys.ANVIL_WORK_COUNT_KEY, PersistentDataType.INTEGER, 0);
            outPdc.set(Keys.ANVIL_WORK_COUNT_KEY, PersistentDataType.INTEGER, Math.max(work, outWork));
        }
        output.setItemMeta(outMeta);

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
