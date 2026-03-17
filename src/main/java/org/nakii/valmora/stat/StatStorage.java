package org.nakii.valmora.stat;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.nakii.valmora.Keys;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class StatStorage {


    private final Plugin plugin;

    public StatStorage(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Saves a map of stats to the item's PersistentDataContainer.
     *
     * @param meta  The ItemMeta to modify.
     * @param stats The map of stats to save.
     */
    public void saveStats(ItemMeta meta, Map<Stat, Double> stats) {
        PersistentDataContainer mainPdc = meta.getPersistentDataContainer();

        // Create a new, empty container that will hold our stats.
        PersistentDataContainer statsPdc = mainPdc.getAdapterContext().newPersistentDataContainer();

        // Iterate through the stats map and add each one to the new container.
        for (Map.Entry<Stat, Double> entry : stats.entrySet()) {
            Stat stat = entry.getKey();
            Double value = entry.getValue();

            // Create a key for the individual stat, e.g., "myplugin:damage"
            NamespacedKey statKey = new NamespacedKey(plugin, stat.name().toLowerCase());
            statsPdc.set(statKey, PersistentDataType.DOUBLE, value);
        }

        // Store the new container (with all the stats) into the main container.
        mainPdc.set(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER, statsPdc);
    }

    /**
     * Loads a map of stats from the item's PersistentDataContainer.
     *
     * @param meta The ItemMeta to read from.
     * @return A map of stats found on the item. Returns an empty map if none.
     */
    public Map<Stat, Double> loadStats(ItemMeta meta) {
        Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        PersistentDataContainer mainPdc = meta.getPersistentDataContainer();

        // Check if the item has our main stats container key.
        if (!mainPdc.has(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)) {
            return stats; // Return empty map
        }

        // Retrieve the nested container.
        PersistentDataContainer statsPdc = Objects.requireNonNull(
            mainPdc.get(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)
        );

        // Iterate through all possible Stat enum values to check if they exist in the container.
        for (Stat stat : Stat.values()) {
            NamespacedKey statKey = new NamespacedKey(plugin, stat.name().toLowerCase());

            if (statsPdc.has(statKey, PersistentDataType.DOUBLE)) {
                double value = Objects.requireNonNull(statsPdc.get(statKey, PersistentDataType.DOUBLE));
                stats.put(stat, value);
            }
        }

        return stats;
    }

    /**
     * Convenience method to get a single stat value from an ItemStack.
     * This is more efficient for quick checks than loading the whole map.
     *
     * @param meta The item's meta.
     * @param stat The stat to look for.
     * @return The value of the stat, or 0.0 if not found.
     */
     public double getStat(ItemMeta meta, Stat stat) {
        PersistentDataContainer mainPdc = meta.getPersistentDataContainer();

        if (!mainPdc.has(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)) {
            return 0.0;
        }

        PersistentDataContainer statsPdc = Objects.requireNonNull(
            mainPdc.get(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)
        );

        NamespacedKey statKey = new NamespacedKey(plugin, stat.name().toLowerCase());
        
        return statsPdc.getOrDefault(statKey, PersistentDataType.DOUBLE, 0.0);
     }
}