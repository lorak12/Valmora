package org.nakii.valmora.module.alchemy.effect;

import org.bukkit.entity.LivingEntity;

/**
 * API for code-driven alchemy effects (night vision, blindness, stun, etc.).
 * Register via AlchemyManager.registerHardcodedEffect().
 * The effectId must match an AlchemyEffect id loaded from YAML.
 */
public interface HardcodedAlchemyEffect {

    String getEffectId();

    void onApply(LivingEntity entity, int level, int durationSeconds);

    void onExpire(LivingEntity entity, int level);

    /** Called once per alchemy tick for every {@link LivingEntity} (not just players) currently holding this effect. */
    default void onTick(LivingEntity entity, int level) {}
}
