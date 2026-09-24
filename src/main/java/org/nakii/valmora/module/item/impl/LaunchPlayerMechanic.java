package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.MechanicDefaults;

/**
 * Launches the caster through the air (e.g. Leaping Sword). {@code y-force} controls the upward
 * boost and {@code forward-force} the boost along the player's facing direction. {@code
 * no-fall-damage} (default {@code false}) cancels the fall-damage hit from landing this specific
 * jump, via {@link NoFallDamageGuard} — items that want the launch to always be safe (e.g. a pure
 * mobility tool) set it {@code true}; damage-dealing knockback-style launches leave it off.
 */
public class LaunchPlayerMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "LAUNCH_PLAYER";
    }

    @Override
    public void execute(ExecutionContext context) {
        if (!(context.getCaster() instanceof Player player)) return;

        double yForce = context.resolveDouble("y-force", MechanicDefaults.getDouble("launch-player", "y-force-default", 1.0));
        double forwardForce = context.resolveDouble("forward-force", MechanicDefaults.getDouble("launch-player", "forward-force-default", 1.0));
        boolean noFallDamage = context.getBoolean("no-fall-damage", MechanicDefaults.getBoolean("launch-player", "no-fall-damage-default", false));

        Vector dir = player.getLocation().getDirection().normalize();
        Vector velocity = dir.multiply(forwardForce);
        velocity.setY(yForce);
        player.setVelocity(velocity);

        if (noFallDamage) {
            NoFallDamageGuard.protect(player.getUniqueId());
        }
    }
}
