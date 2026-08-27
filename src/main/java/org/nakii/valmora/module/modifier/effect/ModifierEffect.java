package org.nakii.valmora.module.modifier.effect;

import org.nakii.valmora.module.script.condition.ConditionGroup;

/**
 * One resolved effect contributed by a modifier definition/tier (docs/
 * Valmora_Modifier_Framework_Design.docx §8). The engine ships {@link StatEffect}, {@link
 * AbilityEffect}, {@link EventEffect}, and {@link StateEffect}; additional types are Java-registered
 * via {@link ModifierEffectRegistry} rather than special-cased in the engine (§20/§23).
 */
public interface ModifierEffect {

    /** The YAML {@code type} this effect was parsed from (e.g. {@code "STAT"}). */
    String getType();

    /** Conditions gating whether this effect currently applies (e.g. low-health procs). Never null. */
    ConditionGroup getConditions();
}
