package org.nakii.valmora.module.alchemy.effect.hardcoded;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;

/**
 * Grants absorption HP that sits on top of max health.
 * Values per level: 20, 40, 60, 80, 100, 150, 200, 300.
 */
public class AbsorptionAlchemyEffect implements HardcodedAlchemyEffect {

    private static final double[] DEFAULT_ABSORPTION_VALUES = {20, 40, 60, 80, 100, 150, 200, 300};

    @Override
    public String getEffectId() { return "absorption"; }

    @Override
    public void onApply(LivingEntity entity, int level, int durationSeconds) {
        // HC-191: alchemy.effects.absorption.values — per-level curve, falls back to the
        // original hardcoded values when unset.
        var plugin = org.nakii.valmora.Valmora.getInstance();
        java.util.List<Double> configured = plugin != null
                ? plugin.getConfig().getDoubleList("alchemy.effects.absorption.values") : java.util.List.of();
        double[] values = configured.isEmpty() ? DEFAULT_ABSORPTION_VALUES
                : configured.stream().mapToDouble(Double::doubleValue).toArray();
        int idx = Math.max(0, Math.min(level - 1, values.length - 1));
        entity.setAbsorptionAmount(values[idx]);
    }

    @Override
    public void onExpire(LivingEntity entity, int level) {
        entity.setAbsorptionAmount(0);
    }
}
