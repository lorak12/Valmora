package org.nakii.valmora.module.modifier.effect;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.module.script.condition.ConditionGroup;

/** Java extension point for a custom modifier effect {@code type} (docs/Valmora_Modifier_Framework_Design.docx §20). */
@FunctionalInterface
public interface ModifierEffectFactory {
    ModifierEffect parse(ConfigurationSection section, ConditionGroup conditions) throws Exception;
}
