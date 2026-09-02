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
    ON_TELEPORT,
    // VANILLA_CONTROL_AUDIT.md §12 critical gap: PlayerInteractEntityEvent was never intercepted at
    // all, so no item could react to being used ON an entity (feeding, taming-style interactions,
    // custom "use on mob" effects). Fired for the item in whichever hand triggered the interaction.
    ON_INTERACT_ENTITY,
    // VANILLA_CONTROL_AUDIT.md §9 death system — general extensibility hooks added alongside the
    // Totem of Undying fix, so any item/modifier ability (not just the built-in totem) can react to
    // its wielder dying or respawning. Fired for held items + armor; see AbilityTriggerListener.
    ON_DEATH,
    ON_RESPAWN
}
