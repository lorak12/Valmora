package org.nakii.valmora.module.modifier;

/** Generic attachment policy for a modifier group (docs/Valmora_Modifier_Framework_Design.docx §6). */
public enum ApplicationMode {
    /** Only one modifier from this group may be attached at a time (e.g. reforges). */
    EXCLUSIVE,
    /** Multiple applications of the SAME modifier id may accumulate up to {@code max} (e.g. gemstone slots). */
    STACKABLE,
    /** Multiple DIFFERENT modifiers from this group may be attached simultaneously, up to {@code max} entries. */
    MULTIPLE
}
