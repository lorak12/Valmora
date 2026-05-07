package org.nakii.valmora.module.item;

import org.bukkit.Material;
import java.util.Arrays;

public enum ItemType {
    SWORD,
    AXE,
    PICKAXE,
    SHOVEL,
    HOE,
    TRIDENT,
    BOW,
    CROSSBOW,
    FISHING_ROD,
    SHEARS,
    SHIELD,
    ELYTRA,
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS,
    HORSE_ARMOR,
    ALL,
    NONE;

    private static final ItemType[] PRIORITY_ORDER = Arrays.stream(values())
            .filter(t -> t != ALL && t != NONE)
            .sorted((a, b) -> Integer.compare(b.name().length(), a.name().length()))
            .toArray(ItemType[]::new);

    /**
     * Determines the ItemType based on the material name.
     * Uses length-based priority to correctly handle overlapping names (e.g. PICKAXE vs AXE).
     */
    public static ItemType fromMaterial(Material material) {
        String name = material.name();
        for (ItemType type : PRIORITY_ORDER) {
            if (name.contains(type.name())) {
                return type;
            }
        }
        return NONE;
    }
}