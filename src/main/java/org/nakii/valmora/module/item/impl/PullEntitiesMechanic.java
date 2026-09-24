package org.nakii.valmora.module.item.impl;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.MechanicDefaults;
import org.nakii.valmora.module.item.TargetResolver;

import java.util.List;

/**
 * Pulls the resolved targets toward a gravity-well point in front of the caster (e.g. Gyrokinetic
 * Wand's "Gravity Well"). The well's location is found by raycasting from the caster's eye along
 * their look direction — capped at {@code range} blocks so it can never anchor arbitrarily far
 * away — and snapped down to the ground so it doesn't float mid-air over a cliff/pit. Entities are
 * then pulled toward that fixed point repeatedly for {@code duration} seconds, rather than
 * receiving one single velocity impulse toward the caster — a real "well" holds nearby mobs for a
 * moment instead of just flinging them once and letting go almost instantly.
 */
public class PullEntitiesMechanic implements AbilityMechanic {

    private static final long PERIOD_TICKS = MechanicDefaults.getLong("pull-entities", "period-ticks", 4L);

    @Override
    public String getId() {
        return "PULL_ENTITIES";
    }

    @Override
    public void execute(ExecutionContext context) {
        if (!(context.getCaster() instanceof Player player)) return;

        double defaultStrength = MechanicDefaults.getDouble("pull-entities", "strength-default", 1.0);
        double strength = context.getParams().contains("strength")
                ? context.resolveDouble("strength", defaultStrength)
                : context.resolveDouble("force", defaultStrength);
        double range = Math.max(1.0, context.resolveDouble("range", MechanicDefaults.getDouble("pull-entities", "range-default", 20.0)));
        double durationSeconds = Math.max(0.05, context.resolveDouble("duration", MechanicDefaults.getDouble("pull-entities", "duration-default", 2.0)));
        String targetSelector = context.getString("target", MechanicDefaults.getString("pull-entities", "target-default", "@enemies_in_radius{r=10}"));

        Location well = findWellLocation(player, range);
        long totalTicks = Math.round(durationSeconds * 20);

        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !well.isWorldLoaded()) {
                    cancel();
                    return;
                }
                List<LivingEntity> targets = TargetResolver.resolve(targetSelector, context);
                for (LivingEntity target : targets) {
                    if (target.isDead() || !target.getWorld().equals(well.getWorld())) continue;
                    Vector toward = well.toVector().subtract(target.getLocation().toVector());
                    double distSq = toward.lengthSquared();
                    if (distSq < 0.25) continue; // already at the well — stop nudging so it doesn't jitter in place
                    // Per-application pull, scaled by real elapsed time (PERIOD_TICKS/20s) so the
                    // total force over the well's lifetime is comparable regardless of tick rate.
                    toward = toward.normalize().multiply(strength * (PERIOD_TICKS / 20.0));
                    target.setVelocity(target.getVelocity().add(toward));
                }
                elapsed += PERIOD_TICKS;
                if (elapsed >= totalTicks) cancel();
            }
        }.runTaskTimer(Valmora.getInstance(), 0L, PERIOD_TICKS);
    }

    /** Raycasts from the caster's eye along their look direction (capped at {@code range}) and snaps the result down to the ground. */
    private Location findWellLocation(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), range);
        Location point = hit != null
                ? hit.getHitPosition().toLocation(player.getWorld())
                : eye.clone().add(eye.getDirection().clone().multiply(range));

        Block block = point.getBlock();
        int minY = player.getWorld().getMinHeight();
        int maxY = player.getWorld().getMaxHeight();
        // Snap onto solid ground: walk down through air, then (in case the raycast landed inside
        // solid terrain) up through solid blocks, so the well always ends up resting on a surface.
        while (block.getY() > minY && block.getRelative(0, -1, 0).getType().isAir()) {
            block = block.getRelative(0, -1, 0);
        }
        while (block.getY() < maxY - 1 && !block.getType().isAir()) {
            block = block.getRelative(0, 1, 0);
        }
        point.setX(block.getX() + 0.5);
        point.setY(block.getY());
        point.setZ(block.getZ() + 0.5);
        return point;
    }
}
