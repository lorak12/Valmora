package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.WitherSkull;
import org.bukkit.util.Vector;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.item.ProjectileAbilityService;

import java.util.List;
import java.util.Map;

/**
 * Spawns one or more custom projectiles from the caster. When the projectile hits an entity or a
 * block, the nested {@code on-hit} (alias {@code on-impact}/{@code on-land}) mechanics run against
 * the impact point, and any {@code damage} is applied directly to a struck entity.
 *
 * <p>Supported projectile types: ARROW, SNOWBALL, ENDER_PEARL, FIREBALL, WITHER_SKULL, EGG.</p>
 */
public class LaunchProjectileMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "LAUNCH_PROJECTILE";
    }

    @Override
    public void execute(ExecutionContext context) {
        LivingEntity caster = context.getCaster();
        if (caster == null) return;

        Class<? extends Projectile> type = mapProjectile(context.getString("projectile", "ARROW"));
        double velocity = context.resolveDouble("velocity", 2.0);
        int count = Math.max(1, context.getInt("count", 1));
        double spread = context.getDouble("spread", 0.0);

        // Nested mechanics + direct damage carried to the impact handler.
        List<Map<?, ?>> onHit = context.getParams().getMapList("on-hit");
        if (onHit.isEmpty()) onHit = context.getParams().getMapList("on-impact");
        if (onHit.isEmpty()) onHit = context.getParams().getMapList("on-land");
        double damage = context.getParams().contains("damage") ? context.resolveDouble("damage", 0.0) : 0.0;
        String damageType = context.getString("damage-type", "MAGIC");

        Vector base = caster.getLocation().getDirection().normalize();
        for (int i = 0; i < count; i++) {
            Vector dir = base.clone();
            if (spread > 0) {
                double s = Math.toRadians(spread);
                dir.add(new Vector(
                        (Math.random() - 0.5) * s,
                        (Math.random() - 0.5) * s,
                        (Math.random() - 0.5) * s));
            }
            Projectile projectile = caster.launchProjectile(type, dir.normalize().multiply(velocity));
            ProjectileAbilityService.register(projectile.getUniqueId(),
                    new ProjectileAbilityService.Callback(caster.getUniqueId(), onHit, damage, damageType));
        }
    }

    private Class<? extends Projectile> mapProjectile(String raw) {
        return switch (raw.toUpperCase()) {
            case "SNOWBALL" -> Snowball.class;
            case "ENDER_PEARL" -> EnderPearl.class;
            case "FIREBALL" -> Fireball.class;
            case "WITHER_SKULL" -> WitherSkull.class;
            case "EGG" -> org.bukkit.entity.Egg.class;
            default -> Arrow.class;
        };
    }
}
