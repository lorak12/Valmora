package org.nakii.valmora.module.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.Map;

public class ItemTranslator {

    private final Valmora plugin;

    public ItemTranslator(Valmora plugin) {
        this.plugin = plugin;
    }

    /**
     * Translates a vanilla ItemStack into a Valmora-formatted item.
     * Applies Rarity, ItemType, and maps vanilla attributes to Valmora stats.
     */
    public ItemStack translate(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Skip if already a custom item with a specific definition
        if (meta.getPersistentDataContainer().has(Keys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            // We still might want to re-run updateLore if it's a generic translated item
            return item;
        }

        // 1. Determine ItemType
        ItemType type = ItemType.fromMaterial(item.getType());
        meta.getPersistentDataContainer().set(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING, type.name());

        // 2. Determine Rarity based on material tiers
        Rarity rarity = determineRarity(item.getType());
        meta.getPersistentDataContainer().set(Keys.RARITY_KEY, PersistentDataType.STRING, rarity.name());

        // 3. Map vanilla attributes to Valmora stats
        Map<Stat, Double> stats = mapVanillaStats(item.getType());
        if (!stats.isEmpty()) {
            plugin.getStatModule().saveStats(meta, stats);
            // Hide vanilla attributes to avoid double display and clutter
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        }

        // Set a "generic" ID to identify it as a translated item
        meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, "vanilla_" + item.getType().name().toLowerCase());

        // 4. Update the lore and name formatting
        plugin.getItemManager().getItemFactory().updateLore(item, meta);

        item.setItemMeta(meta);

        return item;
    }

    private Rarity determineRarity(Material material) {
        String name = material.name();
        if (name.contains("NETHERITE") || material == Material.ELYTRA) return Rarity.MYTHIC;
        if (name.contains("DIAMOND") || material == Material.TRIDENT) return Rarity.EPIC;
        if (name.contains("GOLDEN") || name.contains("ENCHANTED")) return Rarity.RARE;
        if (name.contains("IRON")) return Rarity.UNCOMMON;
        return Rarity.COMMON;
    }

    private Map<Stat, Double> mapVanillaStats(Material material) {
        Map<Stat, Double> stats = new HashMap<>();
        String name = material.name();

        // Weapons
        if (name.endsWith("_SWORD")) {
            stats.put(Stat.DAMAGE, getWeaponDamage(name));
        } else if (name.endsWith("_AXE")) {
            stats.put(Stat.DAMAGE, getWeaponDamage(name) + 2); // Axes do more base dmg in vanilla
        } else if (material == Material.BOW) {
            stats.put(Stat.DAMAGE, 6.0);
        } else if (material == Material.CROSSBOW) {
            stats.put(Stat.DAMAGE, 9.0);
        }

        // Armor
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            stats.put(Stat.DEFENSE, getArmorDefense(name));
        }

        return stats;
    }

    private double getWeaponDamage(String name) {
        if (name.contains("NETHERITE")) return 8.0;
        if (name.contains("DIAMOND")) return 7.0;
        if (name.contains("IRON")) return 6.0;
        if (name.contains("STONE")) return 5.0;
        return 4.0; // Wood/Gold
    }

    private double getArmorDefense(String name) {
        double base = 0;
        if (name.contains("NETHERITE")) base = 5.0;
        else if (name.contains("DIAMOND")) base = 4.0;
        else if (name.contains("IRON")) base = 3.0;
        else if (name.contains("CHAINMAIL")) base = 2.0;
        else if (name.contains("GOLDEN")) base = 2.0;
        else base = 1.0;

        if (name.contains("CHESTPLATE")) return base * 2.5;
        if (name.contains("LEGGINGS")) return base * 2.0;
        if (name.contains("HELMET")) return base * 1.5;
        return base; // Boots
    }
}
