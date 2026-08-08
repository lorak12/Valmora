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
    // Wired in AbilityTriggerListener (2026-08-08) — see docs/IMPLEMENTATION_BACKLOG.md.
    ON_DAMAGE_TAKEN,
    ON_TELEPORT
}
