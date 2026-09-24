package org.nakii.valmora.infrastructure.config.refs;

/** Names of the content kinds the built-in modules register in the {@link ContentIndex}. */
public final class Kinds {

    private Kinds() {}

    /** A Valmora custom item id. */
    public static final String ITEM = "item";
    /** A vanilla {@code Material} name. */
    public static final String MATERIAL = "material";
    /** Either a Valmora item id or a vanilla material — what most "item:" fields accept. */
    public static final String ITEM_OR_MATERIAL = "item_or_material";
    public static final String MOB = "mob";
    public static final String GUI = "gui";
    public static final String QUEST = "quest";
    public static final String NPC = "npc";
    public static final String DIALOGUE = "dialogue";
    public static final String ZONE = "zone";
    public static final String WARP = "warp";
    public static final String MACHINE = "machine";
    public static final String SKILL = "skill";
    public static final String STAT = "stat";
    public static final String RARITY = "rarity";
    public static final String COLLECTION = "collection";
    public static final String ENCHANT = "enchant";
    public static final String MODIFIER = "modifier";
    public static final String MODIFIER_GROUP = "modifier_group";
    public static final String PET = "pet";
    public static final String POINT = "point";
    public static final String DAMAGE_TYPE = "damage_type";
    public static final String XP_CURVE = "xp_curve";
    public static final String SET_BONUS = "set_bonus";
    public static final String RECIPE = "recipe";
    public static final String QUEST_BOARD = "quest_board";
    public static final String PROGRESSION = "progression";
}
