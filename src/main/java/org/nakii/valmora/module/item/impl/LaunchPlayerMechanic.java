package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;

/**
 * Launches the caster through the air (e.g. Leaping Sword). {@code y-force} controls the upward
 * boost and {@code forward-force} the boost along the player's facing direction.
 */
public class LaunchPlayerMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "LAUNCH_PLAYER";
    }

    @Override
    public void execute(ExecutionContext context) {
        if (!(context.getCaster() instanceof Player player)) return;

        double yForce = context.resolveDouble("y-force", 1.0);
        double forwardForce = context.resolveDouble("forward-force", 1.0);

        Vector dir = player.getLocation().getDirection().normalize();
        Vector velocity = dir.multiply(forwardForce);
        velocity.setY(yForce);
        player.setVelocity(velocity);
    }
}
