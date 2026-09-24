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

    /** HC-041: {@code items.vanilla-rarity-mapping} — vanilla material substring -> rarity, so a
     *  custom material added by a content pack can get a rarity without a code change. */
    private Rarity determineRarity(Material material) {
        String name = material.name();
        org.bukkit.configuration.ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("items.vanilla-rarity-mapping");
        if (section != null) {
            if (name.contains("NETHERITE") || material == Material.ELYTRA) return rarityOf(section, "NETHERITE", Rarity.MYTHIC);
            if (name.contains("DIAMOND") || material == Material.TRIDENT) return rarityOf(section, "DIAMOND", Rarity.EPIC);
            if (name.contains("GOLDEN") || name.contains("ENCHANTED")) return rarityOf(section, "GOLDEN", Rarity.RARE);
            if (name.contains("IRON")) return rarityOf(section, "IRON", Rarity.UNCOMMON);
            return Rarity.COMMON;
        }
        if (name.contains("NETHERITE") || material == Material.ELYTRA) return Rarity.MYTHIC;
        if (name.contains("DIAMOND") || material == Material.TRIDENT) return Rarity.EPIC;
        if (name.contains("GOLDEN") || name.contains("ENCHANTED")) return Rarity.RARE;
        if (name.contains("IRON")) return Rarity.UNCOMMON;
        return Rarity.COMMON;
    }

    private Rarity rarityOf(org.bukkit.configuration.ConfigurationSection section, String key, Rarity fallback) {
        String configured = section.getString(key);
        if (configured == null) return fallback;
        try {
            return Rarity.valueOf(configured.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private Map<String, Double> mapVanillaStats(Material material) {
        Map<String, Double> stats = new HashMap<>();
        String name = material.name();
        SystemStats sys = plugin.getStatModule().getSystemStats();

        if (name.endsWith("_SWORD")) {
            stats.put(sys.getDamage(), getWeaponDamage(name));
        } else if (name.endsWith("_AXE")) {
            stats.put(sys.getDamage(), getWeaponDamage(name) + 2);
            stats.put(sys.getMiningSpeed(), getMiningSpeed(name));
            stats.put(sys.getBreakingPower(), (double) ItemFactory.tierBreakingPower(material));
        } else if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            stats.put(sys.getMiningSpeed(), getMiningSpeed(name));
            // items.breaking-power tier (netherite 5 … wood 1) as a real stat, so a vanilla tool can
            // mine the resource nodes its lore says it can.
            stats.put(sys.getBreakingPower(), (double) ItemFactory.tierBreakingPower(material));
        } else if (material == Material.BOW) {
            stats.put(sys.getDamage(), plugin.getConfig().getDouble("items.vanilla-stats.bow-damage", 6.0));
        } else if (material == Material.CROSSBOW) {
            stats.put(sys.getDamage(), plugin.getConfig().getDouble("items.vanilla-stats.crossbow-damage", 9.0));
        }

        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            stats.put(sys.getDefense(), getArmorDefense(name));
        }

        return stats;
    }

    /** HC-042: {@code items.vanilla-stats.mining-speed}. */
    private double getMiningSpeed(String name) {
        var s = plugin.getConfig();
        if (name.contains("NETHERITE")) return s.getDouble("items.vanilla-stats.mining-speed.netherite", 450.0);
        if (name.contains("DIAMOND")) return s.getDouble("items.vanilla-stats.mining-speed.diamond", 400.0);
        if (name.contains("IRON")) return s.getDouble("items.vanilla-stats.mining-speed.iron", 300.0);
        if (name.contains("STONE")) return s.getDouble("items.vanilla-stats.mining-speed.stone", 200.0);
        if (name.contains("GOLDEN")) return s.getDouble("items.vanilla-stats.mining-speed.golden", 500.0);
        return s.getDouble("items.vanilla-stats.mining-speed.wood", 100.0); // baseline, no bonus
    }

    /** HC-043: {@code items.vanilla-stats.weapon-damage}. */
    private double getWeaponDamage(String name) {
        var s = plugin.getConfig();
        if (name.contains("NETHERITE")) return s.getDouble("items.vanilla-stats.weapon-damage.netherite", 8.0);
        if (name.contains("DIAMOND")) return s.getDouble("items.vanilla-stats.weapon-damage.diamond", 7.0);
        if (name.contains("IRON")) return s.getDouble("items.vanilla-stats.weapon-damage.iron", 6.0);
        if (name.contains("STONE")) return s.getDouble("items.vanilla-stats.weapon-damage.stone", 5.0);
        return s.getDouble("items.vanilla-stats.weapon-damage.wood", 4.0);
    }

    /** HC-044: {@code items.vanilla-stats.armor-base}/{@code armor-multiplier}. */
    private double getArmorDefense(String name) {
        var s = plugin.getConfig();
        double base;
        if (name.contains("NETHERITE")) base = s.getDouble("items.vanilla-stats.armor-base.netherite", 5.0);
        else if (name.contains("DIAMOND")) base = s.getDouble("items.vanilla-stats.armor-base.diamond", 4.0);
        else if (name.contains("IRON")) base = s.getDouble("items.vanilla-stats.armor-base.iron", 3.0);
        else if (name.contains("CHAINMAIL") || name.contains("GOLDEN")) base = s.getDouble("items.vanilla-stats.armor-base.gold", 2.0);
        else base = s.getDouble("items.vanilla-stats.armor-base.leather", 1.0);

        if (name.contains("CHESTPLATE")) return base * s.getDouble("items.vanilla-stats.armor-multiplier.chestplate", 2.5);
        if (name.contains("LEGGINGS")) return base * s.getDouble("items.vanilla-stats.armor-multiplier.leggings", 2.0);
        if (name.contains("HELMET")) return base * s.getDouble("items.vanilla-stats.armor-multiplier.helmet", 1.5);
        return base * s.getDouble("items.vanilla-stats.armor-multiplier.boots", 1.0);
    }
}
