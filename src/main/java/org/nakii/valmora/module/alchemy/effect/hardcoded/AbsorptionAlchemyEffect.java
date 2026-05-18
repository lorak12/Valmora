package org.nakii.valmora.module.alchemy.effect.hardcoded;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;

/**
 * Grants absorption HP that sits on top of max health.
 * Values per level: 20, 40, 60, 80, 100, 150, 200, 300.
 */
public class AbsorptionAlchemyEffect implements HardcodedAlchemyEffect {

    private static final double[] ABSORPTION_VALUES = {20, 40, 60, 80, 100, 150, 200, 300};

    @Override
    public String getEffectId() { return "absorption"; }

    @Override
    public void onApply(LivingEntity entity, int level, int durationSeconds) {
        int idx = Math.max(0, Math.min(level - 1, ABSORPTION_VALUES.length - 1));
        entity.setAbsorptionAmount(ABSORPTION_VALUES[idx]);
    }

    @Override
    public void onExpire(LivingEntity entity, int level) {
        entity.setAbsorptionAmount(0);
    }
}
