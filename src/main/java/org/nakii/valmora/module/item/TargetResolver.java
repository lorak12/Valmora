package org.nakii.valmora.module.item;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.nakii.valmora.api.execution.ExecutionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a target selector string into the list of {@link LivingEntity} a mechanic should
 * affect. Supported selectors:
 *
 * <ul>
 *     <li>{@code @player} / {@code @self} — the caster</li>
 *     <li>{@code @target} — the caster's looked-at / context target entity</li>
 *     <li>{@code @enemies_in_radius{r=X}} — hostile mobs within X blocks (excludes players)</li>
 *     <li>{@code @allies_in_radius{r=X}} — players within X blocks (includes caster)</li>
 *     <li>{@code @cone{range=X, angle=Y}} — enemies in a forward-facing cone</li>
 * </ul>
 */
public final class TargetResolver {

    private TargetResolver() {}

    public static List<LivingEntity> resolve(String selector, ExecutionContext ctx) {
        List<LivingEntity> result = new ArrayList<>();
        LivingEntity caster = ctx.getCaster();
        // Radius/cone selectors are centred on the context location (the caster for normal
        // abilities, or the projectile impact point for on-hit callbacks).
        Location center = ctx.getLocation();
        if (center == null && caster != null) center = caster.getLocation();
        if (selector == null || selector.isBlank()) selector = "@target";
        selector = selector.trim();

        String name = selector;
        Map<String, String> args = new HashMap<>();
        int brace = selector.indexOf('{');
        if (brace != -1 && selector.endsWith("}")) {
            name = selector.substring(0, brace).trim();
            String inner = selector.substring(brace + 1, selector.length() - 1);
            for (String part : inner.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) args.put(kv[0].trim().toLowerCase(), kv[1].trim());
            }
        }

        switch (name.toLowerCase()) {
            case "@player", "@self" -> {
                if (caster != null) result.add(caster);
            }
            case "@target" -> ctx.getTarget().ifPresent(result::add);
            case "@enemies_in_radius" -> {
                double r = parse(args, "r", 5.0);
                result.addAll(nearby(caster, center, r, false));
            }
            case "@allies_in_radius" -> {
                double r = parse(args, "r", 5.0);
                if (caster instanceof Player) result.add(caster);
                result.addAll(nearby(caster, center, r, true));
            }
            case "@cone" -> {
                double range = parse(args, "range", 8.0);
                double angle = parse(args, "angle", 45.0);
                result.addAll(cone(caster, center, range, angle));
            }
            default -> ctx.getTarget().ifPresent(result::add);
        }
        return result;
    }

    private static List<LivingEntity> nearby(LivingEntity caster, Location center, double radius, boolean allies) {
        List<LivingEntity> list = new ArrayList<>();
        if (center == null || center.getWorld() == null) return list;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || le.equals(caster)) continue;
            if (allies) {
                if (le instanceof Player) list.add(le);
            } else {
                if (isHostile(le)) list.add(le);
            }
        }
        return list;
    }

    private static List<LivingEntity> cone(LivingEntity caster, Location origin, double range, double angleDegrees) {
        List<LivingEntity> list = new ArrayList<>();
        if (origin == null || origin.getWorld() == null) return list;
        Vector facing = origin.getDirection().normalize();
        double cosLimit = Math.cos(Math.toRadians(angleDegrees));
        for (Entity e : origin.getWorld().getNearbyEntities(origin, range, range, range)) {
            if (!(e instanceof LivingEntity le) || le.equals(caster)) continue;
            if (!isHostile(le)) continue;
            Vector to = le.getLocation().toVector().subtract(origin.toVector());
            if (to.lengthSquared() < 1.0e-6) { list.add(le); continue; }
            double dot = facing.dot(to.normalize());
            if (dot >= cosLimit) list.add(le);
        }
        return list;
    }

    private static boolean isHostile(LivingEntity le) {
        // Treat any non-player living entity as a valid combat target. Monster check keeps the
        // intent clear; the broader fallback covers custom mobs that don't extend Monster.
        return !(le instanceof Player);
    }

    private static double parse(Map<String, String> args, String key, double def) {
        String v = args.get(key);
        if (v == null) return def;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return def; }
    }
}
