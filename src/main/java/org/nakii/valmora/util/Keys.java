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
    public static NamespacedKey GENERIC_BASE_LORE_KEY;
    public static NamespacedKey FURNACE_OWNER_KEY;

    public static NamespacedKey ALCHEMY_EFFECT_ID;
    public static NamespacedKey ALCHEMY_EFFECT_LEVEL;
    public static NamespacedKey ALCHEMY_DURATION;
    public static NamespacedKey ALCHEMY_IS_SPLASH;
    public static NamespacedKey ALCHEMY_LEVEL_MODIFIED;
    public static NamespacedKey ALCHEMY_DURATION_MODIFIED;

    public static NamespacedKey NPC_ID_KEY;
    public static NamespacedKey WARP_ID_KEY;
    public static NamespacedKey MOB_HOME_KEY;
    public static NamespacedKey ZONE_WAND_KEY;

    public static NamespacedKey HUD_ITEM_KEY;
    public static NamespacedKey REFORGE_ID_KEY;
    public static NamespacedKey REFORGE_POOL_KEY;
    public static NamespacedKey REFORGE_DISPLAY_KEY;

    public static NamespacedKey PET_ID_KEY;
    public static NamespacedKey PET_XP_KEY;
    public static NamespacedKey PET_LEVEL_KEY;

    public static NamespacedKey SLAYER_BOSS_KEY;

    public static NamespacedKey BACKPACK_CONTENTS_KEY;
    public static NamespacedKey BACKPACK_SIZE_KEY;

    public static void init(Valmora plugin) {
        ITEM_ID_KEY = new NamespacedKey(plugin, "valmora_item_id");
        RARITY_KEY = new NamespacedKey(plugin, "rarity");
        ITEM_TYPE_KEY = new NamespacedKey(plugin, "item_type");
        STATS_CONTAINER_KEY = new NamespacedKey(plugin, "item_stats_container");
        MOB_ID_KEY = new NamespacedKey(plugin, "valmora_mob_id");
        ENCHANTS_CONTAINER_KEY = new NamespacedKey(plugin, "valmora_enchants_container");
        GENERIC_BASE_LORE_KEY = new NamespacedKey(plugin, "valmora_generic_base_lore");
        FURNACE_OWNER_KEY = new NamespacedKey(plugin, "valmora_furnace_owner");

        ALCHEMY_EFFECT_ID = new NamespacedKey(plugin, "alchemy_effect_id");
        ALCHEMY_EFFECT_LEVEL = new NamespacedKey(plugin, "alchemy_effect_level");
        ALCHEMY_DURATION = new NamespacedKey(plugin, "alchemy_duration");
        ALCHEMY_IS_SPLASH = new NamespacedKey(plugin, "alchemy_is_splash");
        ALCHEMY_LEVEL_MODIFIED = new NamespacedKey(plugin, "alchemy_level_modified");
        ALCHEMY_DURATION_MODIFIED = new NamespacedKey(plugin, "alchemy_duration_modified");

        NPC_ID_KEY = new NamespacedKey(plugin, "valmora_npc_id");
        WARP_ID_KEY = new NamespacedKey(plugin, "valmora_warp_id");
        MOB_HOME_KEY = new NamespacedKey(plugin, "mob_home");
        ZONE_WAND_KEY = new NamespacedKey(plugin, "zone_wand");

        HUD_ITEM_KEY = new NamespacedKey(plugin, "hud_item_id");
        REFORGE_ID_KEY = new NamespacedKey(plugin, "reforge_id");
        REFORGE_POOL_KEY = new NamespacedKey(plugin, "reforge_pool");
        REFORGE_DISPLAY_KEY = new NamespacedKey(plugin, "reforge_display");

        PET_ID_KEY = new NamespacedKey(plugin, "pet_id");
        PET_XP_KEY = new NamespacedKey(plugin, "pet_xp");
        PET_LEVEL_KEY = new NamespacedKey(plugin, "pet_level");

        SLAYER_BOSS_KEY = new NamespacedKey(plugin, "slayer_boss");

        BACKPACK_CONTENTS_KEY = new NamespacedKey(plugin, "backpack_contents");
        BACKPACK_SIZE_KEY = new NamespacedKey(plugin, "backpack_size");
    }
}
