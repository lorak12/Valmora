package org.nakii.valmora.combat;

public enum DamageType {
    MELEE("<white>"), 
    PROJECTILE("<gray>"), 
    FALL("<dark_gray>"), 
    DROWNING("<blue>"),
    FIRE("<#FF8C00>"),
    LAVA("<dark_red>"),
    MAGIC("<aqua>"),
    VOID("<black>"),
    POISON("<green>"),
    WITHER("<black>"),
    EXPLOSION("<red>");

    private String color;

    DamageType(String color) {
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    
}
