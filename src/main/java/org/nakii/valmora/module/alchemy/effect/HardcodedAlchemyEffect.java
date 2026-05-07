package org.nakii.valmora.module.alchemy.effect;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * API for code-driven alchemy effects (night vision, blindness, stun, etc.).
 * Register via AlchemyManager.registerHardcodedEffect().
 * The effectId must match an AlchemyEffect id loaded from YAML.
 */
public interface HardcodedAlchemyEffect {

    String getEffectId();

    void onApply(LivingEntity entity, int level, int durationSeconds);

    void onExpire(LivingEntity entity, int level);

    default void onTick(Player player, int level) {}
}
