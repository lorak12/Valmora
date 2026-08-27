package org.nakii.valmora.module.modifier;

import org.nakii.valmora.module.modifier.effect.ModifierEffect;

import java.util.List;

/**
 * One tier's display/effect override for a tiered modifier (docs/
 * Valmora_Modifier_Framework_Design.docx §11/§14, the gemstone example). A tier fully replaces the
 * base effects for that strength level rather than adding to them.
 */
public class ModifierTier {

    private final int tier;
    private final String displayNameOverride; // nullable
    private final List<ModifierEffect> effects;

    public ModifierTier(int tier, String displayNameOverride, List<ModifierEffect> effects) {
        this.tier = tier;
        this.displayNameOverride = displayNameOverride;
        this.effects = effects;
    }

    public int getTier() { return tier; }
    public String getDisplayNameOverride() { return displayNameOverride; }
    public List<ModifierEffect> getEffects() { return effects; }
}
