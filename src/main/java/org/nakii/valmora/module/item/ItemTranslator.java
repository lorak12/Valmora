package org.nakii.valmora.module.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.Map;

public class ItemTranslator {

    private final Valmora plugin;

    public ItemTranslator(Valmora plugin) {
        this.plugin = plugin;
    }

    public ItemStack translate(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (meta.getPersistentDataContainer().has(Keys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            return item;
        }

        ItemType type = ItemType.fromMaterial(item.getType());
        meta.getPersistentDataContainer().set(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING, type.name());

        Rarity rarity = determineRarity(item.getType());
        meta.getPersistentDataContainer().set(Keys.RARITY_KEY, PersistentDataType.STRING, rarity.name());

        Map<String, Double> stats = mapVanillaStats(item.getType());
        if (!stats.isEmpty()) {
            plugin.getStatModule().saveStats(meta, stats);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        }

        meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING,
                "vanilla_" + item.getType().name().toLowerCase());

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

    private Map<String, Double> mapVanillaStats(Material material) {
        Map<String, Double> stats = new HashMap<>();
        String name = material.name();
        SystemStats sys = plugin.getStatModule().getSystemStats();

        if (name.endsWith("_SWORD")) {
            stats.put(sys.getDamage(), getWeaponDamage(name));
        } else if (name.endsWith("_AXE")) {
            stats.put(sys.getDamage(), getWeaponDamage(name) + 2);
        } else if (material == Material.BOW) {
            stats.put(sys.getDamage(), 6.0);
        } else if (material == Material.CROSSBOW) {
            stats.put(sys.getDamage(), 9.0);
        }

        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            stats.put(sys.getDefense(), getArmorDefense(name));
        }

        return stats;
    }

    private double getWeaponDamage(String name) {
        if (name.contains("NETHERITE")) return 8.0;
        if (name.contains("DIAMOND")) return 7.0;
        if (name.contains("IRON")) return 6.0;
        if (name.contains("STONE")) return 5.0;
        return 4.0;
    }

    private double getArmorDefense(String name) {
        double base;
        if (name.contains("NETHERITE")) base = 5.0;
        else if (name.contains("DIAMOND")) base = 4.0;
        else if (name.contains("IRON")) base = 3.0;
        else if (name.contains("CHAINMAIL") || name.contains("GOLDEN")) base = 2.0;
        else base = 1.0;

        if (name.contains("CHESTPLATE")) return base * 2.5;
        if (name.contains("LEGGINGS")) return base * 2.0;
        if (name.contains("HELMET")) return base * 1.5;
        return base;
    }
}
