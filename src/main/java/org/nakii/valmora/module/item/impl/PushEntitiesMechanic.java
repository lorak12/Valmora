package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.MechanicDefaults;
import org.nakii.valmora.module.item.TargetResolver;

import java.util.List;

/**
 * Knocks the resolved targets away from the caster (e.g. Dragon Rage knockback).
 */
public class PushEntitiesMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "PUSH_ENTITIES";
    }

    @Override
    public void execute(ExecutionContext context) {
        double force = context.resolveDouble("force", MechanicDefaults.getDouble("push-entities", "force-default", 1.0));
        List<LivingEntity> targets = TargetResolver.resolve(context.getString("target", "@target"), context);
        Vector casterPos = context.getCaster().getLocation().toVector();

        double yClamp = MechanicDefaults.getDouble("push-entities", "y-clamp", 0.3);
        double yFactor = MechanicDefaults.getDouble("push-entities", "y-factor", 0.4);
        for (LivingEntity target : targets) {
            Vector away = target.getLocation().toVector().subtract(casterPos);
            if (away.lengthSquared() < 1.0e-6) away = context.getCaster().getLocation().getDirection();
            away = away.normalize().multiply(force).setY(Math.max(yClamp, force * yFactor));
            target.setVelocity(target.getVelocity().add(away));
        }
    }
}
