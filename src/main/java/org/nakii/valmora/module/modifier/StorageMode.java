package org.nakii.valmora.module.modifier;

/** Storage optimization/identity semantics only — not effect semantics (docs/Valmora_Modifier_Framework_Design.docx §6). */
public enum StorageMode {
    /** At most one component entry — the group is EXCLUSIVE by construction. */
    SINGLE,
    /** One entry per distinct modifier id, with an application count (e.g. 3x the same gemstone folded into one entry). */
    STACKED,
    /** One entry per application — each carries its own tier/state independently. */
    INSTANCES
}
