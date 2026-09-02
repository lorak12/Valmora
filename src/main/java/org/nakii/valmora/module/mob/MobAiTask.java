package org.nakii.valmora.module.mob;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Keys;

/**
 * Basic leash-range enforcement for custom mobs (see {@code MobDefinition#getLeashRange()} /
 * {@code ai: { leash-range: ... }} in {@code mobs/*.yml}).
 *
 * <p>Deliberately does <b>not</b> reimplement targeting/wandering — vanilla AI already handles
 * that (aggro range is instead applied once at spawn via the {@code FOLLOW_RANGE} attribute, see
 * {@link MobFactory#applyData}). This task's only job is periodically checking mobs that strayed
 * further than their configured leash range from their spawn point and pathing them back, using
 * Paper's {@code Pathfinder} API per CLAUDE.md §14.2 (never raw NMS navigation).
 */
public class MobAiTask implements Runnable {

    private final Valmora plugin;
    private final MobRegistry mobRegistry;

    public MobAiTask(Valmora plugin, MobRegistry mobRegistry) {
        this.plugin = plugin;
        this.mobRegistry = mobRegistry;
    }

    @Override
    public void run() {
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                var pdc = entity.getPersistentDataContainer();
                if (!pdc.has(Keys.MOB_HOME_X_KEY, PersistentDataType.DOUBLE)) continue;

                String mobId = pdc.get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
                if (mobId == null) continue;
                MobDefinition def = mobRegistry.getMob(mobId).orElse(null);
                if (def == null || def.getLeashRange() < 0) continue;

                double homeX = pdc.get(Keys.MOB_HOME_X_KEY, PersistentDataType.DOUBLE);
                double homeY = pdc.get(Keys.MOB_HOME_Y_KEY, PersistentDataType.DOUBLE);
                double homeZ = pdc.get(Keys.MOB_HOME_Z_KEY, PersistentDataType.DOUBLE);
                Location home = new Location(world, homeX, homeY, homeZ);

                if (entity.getLocation().distanceSquared(home) > def.getLeashRange() * def.getLeashRange()) {
                    if (!mob.getPathfinder().hasPath()) {
                        // HC-087: leash-return speed — slow vs. fast boss leash-back feel.
                        double speed = plugin.getConfig().getDouble("mobs.ai.leash-return-speed", 1.0);
                        mob.getPathfinder().moveTo(home, speed);
                    }
                }
            }
        }
    }
}
