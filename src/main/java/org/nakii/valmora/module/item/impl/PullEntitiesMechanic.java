package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.TargetResolver;

import java.util.List;

/**
 * Pulls the resolved targets toward the caster (e.g. Gyrokinetic Wand gravity well).
 */
public class PullEntitiesMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "PULL_ENTITIES";
    }

    @Override
    public void execute(ExecutionContext context) {
        double strength = context.getParams().contains("strength")
                ? context.resolveDouble("strength", 1.0)
                : context.resolveDouble("force", 1.0);
        List<LivingEntity> targets = TargetResolver.resolve(context.getString("target", "@enemies_in_radius{r=10}"), context);
        Vector casterPos = context.getCaster().getLocation().toVector();

        for (LivingEntity target : targets) {
            Vector toward = casterPos.clone().subtract(target.getLocation().toVector());
            if (toward.lengthSquared() < 1.0e-6) continue;
            toward = toward.normalize().multiply(strength);
            target.setVelocity(target.getVelocity().add(toward));
        }
    }
}
