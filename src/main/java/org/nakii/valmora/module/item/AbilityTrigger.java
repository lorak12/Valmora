package org.nakii.valmora.module.item;

public enum AbilityTrigger {
    RIGHT_CLICK,
    LEFT_CLICK,
    PASSIVE,
    EQUIP,
    UNEQUIP,
    ON_HIT,
    ON_KILL,
    SNEAK,
    ON_SHOOT,
    // Present for schema completeness; wired in a later phase (set-bonus consumers).
    ON_DAMAGE_TAKEN,
    ON_TELEPORT
}
