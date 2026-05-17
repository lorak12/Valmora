package org.nakii.valmora.module.stat;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class StatModule implements ReloadableModule {

    private final Valmora plugin;
    private final StatRegistry statRegistry = new StatRegistry();
    private SystemStats systemStats;
    private StatLoader statLoader;
    private PlayerListener playerListener;

    public StatModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Starting Stat Module...");
        this.statLoader = new StatLoader(plugin, statRegistry);
        this.statLoader.load();
        this.systemStats = SystemStats.load(plugin.getConfig());

        this.playerListener = new PlayerListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(playerListener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Stopping Stat Module...");
        if (playerListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(playerListener);
        }
        statRegistry.clear();
    }

    @Override
    public String getId() {
        return "stats";
    }

    public StatRegistry getStatRegistry() {
        return statRegistry;
    }

    public SystemStats getSystemStats() {
        return systemStats;
    }

    /**
     * Applies vanilla attribute mappings for any stat with a vanilla-attribute defined.
     */
    private static final NamespacedKey MINING_SPEED_MOD_KEY = new NamespacedKey("valmora", "mining_speed_bonus");
    private static final String BLOCK_BREAK_SPEED_KEY = "block_break_speed";

    public void recalculateAttributes(Player player, StatManager statManager) {
        for (StatDefinition def : statRegistry.values()) {
            if (def.getVanillaAttribute() == null) continue;

            NamespacedKey attrKey = NamespacedKey.fromString(def.getVanillaAttribute());
            if (attrKey == null) attrKey = NamespacedKey.minecraft(def.getVanillaAttribute().toLowerCase());

            Attribute attr = Registry.ATTRIBUTE.get(attrKey);
            if (attr == null) continue;

            AttributeInstance attrInst = player.getAttribute(attr);
            if (attrInst == null) continue;

            double statValue = statManager.getStat(def.getId());

            if (attrKey.getKey().equals(BLOCK_BREAK_SPEED_KEY)) {
                // 100 = vanilla base speed (modifier 0). Values above 100 add to the base.
                attrInst.removeModifier(MINING_SPEED_MOD_KEY);
                double bonus = Math.max(0.0, (statValue - 100.0) / 100.0);
                if (bonus > 0) {
                    attrInst.addModifier(new AttributeModifier(
                        MINING_SPEED_MOD_KEY, bonus,
                        AttributeModifier.Operation.ADD_NUMBER));
                }
            } else {
                attrInst.setBaseValue(0.1 * statValue / 100.0);
            }
        }
    }

    /**
     * Saves a map of stat values to the item's PersistentDataContainer.
     */
    public void saveStats(ItemMeta meta, Map<String, Double> stats) {
        PersistentDataContainer mainPdc = meta.getPersistentDataContainer();
        PersistentDataContainer statsPdc = mainPdc.getAdapterContext().newPersistentDataContainer();

        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            NamespacedKey statKey = new NamespacedKey(plugin, entry.getKey().toLowerCase());
            statsPdc.set(statKey, PersistentDataType.DOUBLE, entry.getValue());
        }

        mainPdc.set(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER, statsPdc);
    }

    /**
     * Loads stat values from the item's PersistentDataContainer.
     * Only returns stats registered in the StatRegistry.
     */
    public Map<String, Double> loadStats(ItemMeta meta) {
        Map<String, Double> stats = new HashMap<>();
        PersistentDataContainer mainPdc = meta.getPersistentDataContainer();

        if (!mainPdc.has(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)) {
            return stats;
        }

        PersistentDataContainer statsPdc = Objects.requireNonNull(
            mainPdc.get(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)
        );

        for (StatDefinition def : statRegistry.values()) {
            NamespacedKey statKey = new NamespacedKey(plugin, def.getId());
            if (statsPdc.has(statKey, PersistentDataType.DOUBLE)) {
                double value = Objects.requireNonNull(statsPdc.get(statKey, PersistentDataType.DOUBLE));
                stats.put(def.getId(), value);
            }
        }

        return stats;
    }

    /**
     * Convenience: get a single stat value from an item.
     */
    public double getStat(ItemMeta meta, String statId) {
        PersistentDataContainer mainPdc = meta.getPersistentDataContainer();
        if (!mainPdc.has(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)) return 0.0;

        PersistentDataContainer statsPdc = Objects.requireNonNull(
            mainPdc.get(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)
        );

        NamespacedKey statKey = new NamespacedKey(plugin, statId.toLowerCase());
        return statsPdc.getOrDefault(statKey, PersistentDataType.DOUBLE, 0.0);
    }
}
