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
    public static NamespacedKey ALCHEMY_SPLASH_MULTIPLIER;

    public static NamespacedKey NPC_ID_KEY;
    public static NamespacedKey WARP_ID_KEY;
    public static NamespacedKey MOB_HOME_KEY;
    public static NamespacedKey MOB_HOME_X_KEY;
    public static NamespacedKey MOB_HOME_Y_KEY;
    public static NamespacedKey MOB_HOME_Z_KEY;
    public static NamespacedKey ZONE_WAND_KEY;

    public static NamespacedKey HUD_ITEM_KEY;

    public static NamespacedKey PET_ID_KEY;
    public static NamespacedKey PET_XP_KEY;
    public static NamespacedKey PET_LEVEL_KEY;
    public static NamespacedKey PET_INSTANCE_KEY;

    public static NamespacedKey STORAGE_CONTENTS_KEY;
    public static NamespacedKey CONTAINER_GUI_KEY;

    public static NamespacedKey DAMAGE_RESISTANCES_KEY;

    /** Anvil "prior work" counter (coworker anvil spec §6) — climbs each time an item passes
     *  through the unified anvil, driving the 2^n-1 XP penalty curve. Absent = 0. */
    public static NamespacedKey ANVIL_WORK_COUNT_KEY;

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
        ALCHEMY_SPLASH_MULTIPLIER = new NamespacedKey(plugin, "alchemy_splash_multiplier");

        NPC_ID_KEY = new NamespacedKey(plugin, "valmora_npc_id");
        WARP_ID_KEY = new NamespacedKey(plugin, "valmora_warp_id");
        MOB_HOME_KEY = new NamespacedKey(plugin, "mob_home");
        MOB_HOME_X_KEY = new NamespacedKey(plugin, "valmora_mob_leash_home_x");
        MOB_HOME_Y_KEY = new NamespacedKey(plugin, "valmora_mob_leash_home_y");
        MOB_HOME_Z_KEY = new NamespacedKey(plugin, "valmora_mob_leash_home_z");
        ZONE_WAND_KEY = new NamespacedKey(plugin, "zone_wand");

        HUD_ITEM_KEY = new NamespacedKey(plugin, "hud_item_id");

        PET_ID_KEY = new NamespacedKey(plugin, "pet_id");
        PET_XP_KEY = new NamespacedKey(plugin, "pet_xp");
        PET_LEVEL_KEY = new NamespacedKey(plugin, "pet_level");
        PET_INSTANCE_KEY = new NamespacedKey(plugin, "pet_instance");

        STORAGE_CONTENTS_KEY = new NamespacedKey(plugin, "storage_contents");
        CONTAINER_GUI_KEY = new NamespacedKey(plugin, "container_gui");

        DAMAGE_RESISTANCES_KEY = new NamespacedKey(plugin, "damage_resistances");

        ANVIL_WORK_COUNT_KEY = new NamespacedKey(plugin, "anvil_work_count");
    }
}
