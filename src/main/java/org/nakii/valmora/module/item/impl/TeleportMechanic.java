package org.nakii.valmora.module.item.impl;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;

/**
 * Teleports the caster forward along their facing direction (e.g. Aspect of the End). Uses a
 * block ray-trace so the player lands against a wall rather than inside it, and Paper's
 * async teleport so distant chunks load safely.
 */
public class TeleportMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "TELEPORT";
    }

    @Override
    public void execute(ExecutionContext context) {
        if (!(context.getCaster() instanceof Player player)) return;

        double distance = context.resolveDouble("distance", 8.0);
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        double allowed = distance;
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, distance);
        if (hit != null && hit.getHitPosition() != null) {
            allowed = Math.max(0, eye.toVector().distance(hit.getHitPosition()) - 1.0);
        }

        // Compute from eye-level so the ray and destination share the same reference point.
        // Subtracting eye height converts back to feet-level, which prevents the player from
        // ending up inside the ground when aiming downward.
        Location destination = eye.clone().add(dir.clone().multiply(allowed));
        destination.setY(destination.getY() - player.getEyeHeight());
        destination.setX(Math.floor(destination.getX()) + 0.5);
        destination.setZ(Math.floor(destination.getZ()) + 0.5);
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        // Push out of solid blocks (e.g. partial landing inside terrain).
        while (destination.getBlock().getType().isSolid()) {
            destination.setY(destination.getY() + 1);
        }

        // Short-distance teleports always land in loaded chunks; synchronous teleport keeps
        // the call blocking so mechanics that run after this one (e.g. Wither Impact AoE) see
        // the player at the new position rather than the old one.
        if (destination.getChunk().isLoaded()) {
            player.teleport(destination);
        } else {
            player.teleportAsync(destination);
        }
    }
}
