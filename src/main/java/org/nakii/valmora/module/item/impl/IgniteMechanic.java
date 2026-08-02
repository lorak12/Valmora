package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.TargetResolver;

import java.util.List;

/**
 * Sets the resolved targets on fire (e.g. Flaming Sword "ignites enemies for 3s").
 */
public class IgniteMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "IGNITE";
    }

    @Override
    public void execute(ExecutionContext context) {
        double durationSeconds = context.resolveDouble("duration", 3.0);
        int fireTicks = (int) (durationSeconds * 20);
        List<LivingEntity> targets = TargetResolver.resolve(context.getString("target", "@target"), context);
        for (LivingEntity target : targets) {
            target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        }
    }
}
