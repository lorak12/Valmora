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

    public static NamespacedKey ALCHEMY_EFFECT_ID;
    public static NamespacedKey ALCHEMY_EFFECT_LEVEL;
    public static NamespacedKey ALCHEMY_DURATION;
    public static NamespacedKey ALCHEMY_IS_SPLASH;

    public static NamespacedKey NPC_ID_KEY;
    public static NamespacedKey WARP_ID_KEY;
    public static NamespacedKey MOB_HOME_KEY;
    public static NamespacedKey ZONE_WAND_KEY;

    public static void init(Valmora plugin) {
        ITEM_ID_KEY = new NamespacedKey(plugin, "valmora_item_id");
        RARITY_KEY = new NamespacedKey(plugin, "rarity");
        ITEM_TYPE_KEY = new NamespacedKey(plugin, "item_type");
        STATS_CONTAINER_KEY = new NamespacedKey(plugin, "item_stats_container");
        MOB_ID_KEY = new NamespacedKey(plugin, "valmora_mob_id");
        ENCHANTS_CONTAINER_KEY = new NamespacedKey(plugin, "valmora_enchants_container");

        ALCHEMY_EFFECT_ID = new NamespacedKey(plugin, "alchemy_effect_id");
        ALCHEMY_EFFECT_LEVEL = new NamespacedKey(plugin, "alchemy_effect_level");
        ALCHEMY_DURATION = new NamespacedKey(plugin, "alchemy_duration");
        ALCHEMY_IS_SPLASH = new NamespacedKey(plugin, "alchemy_is_splash");

        NPC_ID_KEY = new NamespacedKey(plugin, "valmora_npc_id");
        WARP_ID_KEY = new NamespacedKey(plugin, "valmora_warp_id");
        MOB_HOME_KEY = new NamespacedKey(plugin, "mob_home");
        ZONE_WAND_KEY = new NamespacedKey(plugin, "zone_wand");
    }
}
