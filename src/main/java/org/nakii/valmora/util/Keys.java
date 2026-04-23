package org.nakii.valmora.util;

import org.bukkit.NamespacedKey;
import org.nakii.valmora.Valmora;

public class Keys {
    public static NamespacedKey ITEM_ID_KEY;
    public static NamespacedKey RARITY_KEY;
    public static NamespacedKey ITEM_TYPE_KEY;
    public static NamespacedKey STATS_CONTAINER_KEY;
    public static NamespacedKey MOB_ID_KEY;
    public static NamespacedKey ENCHANTS_CONTAINER_KEY;

    public static void init(Valmora plugin) {
        ITEM_ID_KEY = new NamespacedKey(plugin, "valmora_item_id");
        RARITY_KEY = new NamespacedKey(plugin, "rarity");
        ITEM_TYPE_KEY = new NamespacedKey(plugin, "item_type");
        STATS_CONTAINER_KEY = new NamespacedKey(plugin, "item_stats_container");
        MOB_ID_KEY = new NamespacedKey(plugin, "valmora_mob_id");
        ENCHANTS_CONTAINER_KEY = new NamespacedKey(plugin, "valmora_enchants_container");
    }
}
