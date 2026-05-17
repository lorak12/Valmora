package org.nakii.valmora.module.script.condition;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

public record LocationCondition(String worldName, double x, double y, double z, double radius) implements Condition {

    /**
     * Parses "x;y;z;world" format used throughout the Valmora location DSL.
     * Returns null if the string is invalid.
     */
    public static LocationCondition parse(String locStr, double radius) {
        String[] parts = locStr.split(";");
        if (parts.length < 4) return null;
        try {
            double px = Double.parseDouble(parts[0]);
            double py = Double.parseDouble(parts[1]);
            double pz = Double.parseDouble(parts[2]);
            return new LocationCondition(parts[3], px, py, pz, radius);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean evaluate(ExecutionContext context) {
        return context.getPlayerCaster()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .map(p -> {
                    var world = Bukkit.getWorld(worldName);
                    if (world == null || !world.equals(p.getWorld())) return false;
                    Location target = new Location(world, x, y, z);
                    return p.getLocation().distanceSquared(target) <= radius * radius;
                })
                .orElse(false);
    }
}
