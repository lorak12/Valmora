package org.nakii.valmora.module.stat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Objects;
import java.util.logging.Logger;

public class StatLoader {

    private final Valmora plugin;
    private final StatRegistry registry;
    private final Logger log;

    public StatLoader(Valmora plugin, StatRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.log = plugin.getLogger();
    }

    public void load() {
        registry.clear();
        File statsDir = new File(plugin.getDataFolder(), "stats");
        if (!statsDir.exists()) {
            log.warning("[StatLoader] stats/ folder not found — no stats loaded.");
            return;
        }

        File[] files = statsDir.listFiles((FilenameFilter) (dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.warning("[StatLoader] No stat YAML files found in stats/.");
            return;
        }

        int count = 0;
        for (File file : files) {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            count += loadFromConfig(yaml, file.getName());
        }

        log.info("[StatLoader] Loaded " + count + " stat definitions.");
    }

    private int loadFromConfig(FileConfiguration config, String fileName) {
        int count = 0;
        for (String id : config.getKeys(false)) {
            ConfigurationSection s = config.getConfigurationSection(id);
            if (s == null) continue;

            String normalizedId = id.toLowerCase();
            String displayName = s.getString("display-name", normalizedId);
            double defaultValue = s.getDouble("default-value", 0.0);
            double maxValue = s.contains("max-value") ? s.getDouble("max-value") : Double.MAX_VALUE;
            String color = s.getString("color", "<white>");
            String icon = s.getString("icon", "PAPER");
            String description = s.getString("description", "");
            boolean pool = s.getBoolean("pool", false);
            String vanillaAttribute = s.getString("vanilla-attribute", null);

            StatDefinition def = new StatDefinition(normalizedId, displayName, defaultValue,
                    maxValue, color, icon, description, pool, vanillaAttribute);
            registry.register(def);
            count++;
        }
        return count;
    }
}
