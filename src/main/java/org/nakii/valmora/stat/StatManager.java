package org.nakii.valmora;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class StatManager {

    private Map<Stat, Double> stats = new HashMap<>();

    public StatManager() {
        // Initialize stats with default values
        for (Stat stat : Stat.values()) {
            stats.put(stat, stat.getDefaultValue(stat));
        }
    }

    public void addStat(Player player , Stat stat, Double value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative");
        }
        // Logic to add the stat value to the player's profile
        if (stat.equals(Stat.SPEED)) {
            // Set player's attribute to the new speed value
            player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue((0.1 * (getStat(Stat.SPEED) + value)) / 100);
        }
        stats.put(stat, getStat(stat) + value);
    }

    public void reduceStat(Player player, Stat stat, Double value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative");
        }
        // Logic to reduce the stat value from the player's profile
        if (stat.equals(Stat.SPEED)) {
            // Set player's attribute to the new speed value
            player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue((0.1 * (getStat(Stat.SPEED) - value)) / 100);
        }
        stats.put(stat, getStat(stat) - value);
    }

    public Double getStat(Stat stat) {
        return stats.getOrDefault(stat, 0.0);
    }

    // Method to apply attribute changes on profile switch
    public void recalculateAttributes(Player player) {
        // Update movement speed based on the current SPEED stat
        player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue((0.1 * getStat(Stat.SPEED)) / 100);
    }

}
