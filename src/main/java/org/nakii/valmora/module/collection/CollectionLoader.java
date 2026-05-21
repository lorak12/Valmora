package org.nakii.valmora.module.collection;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;

public class CollectionLoader {

    private final Valmora plugin;
    private final CollectionRegistry registry;

    public CollectionLoader(Valmora plugin, CollectionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void loadCollections() {
        File folder = new File(plugin.getDataFolder(), "collections");
        if (!folder.exists()) {
            plugin.getLogger().warning("No collections/ folder found — skipping collection load.");
            return;
        }

        File categoriesFile = new File(folder, "categories.yml");
        if (categoriesFile.exists()) {
            loadCategories(categoriesFile);
        } else {
            plugin.getLogger().warning("[Collections] collections/categories.yml not found.");
        }

        loadCollectionsRecursive(folder);

        plugin.getLogger().info("[Collections] Loaded " + registry.getCategories().size() +
                " categories and " + registry.getCollections().size() + " collections.");
    }

    private void loadCategories(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;
            try {
                CollectionCategory cat = CollectionDefinitionParser.parseCategory(key.toLowerCase(), section);
                registry.registerCategory(cat);
            } catch (Exception e) {
                plugin.getLogger().warning("[Collections] Failed to parse category '" + key + "': " + e.getMessage());
            }
        }
    }

    private void loadCollectionsRecursive(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                loadCollectionsRecursive(f);
            } else if (f.getName().endsWith(".yml") && !f.getName().equals("categories.yml")) {
                loadCollectionFile(f);
            }
        }
    }

    private void loadCollectionFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;
            try {
                CollectionDefinition def = CollectionDefinitionParser.parseCollection(key.toLowerCase(), section);
                registry.registerCollection(def);
            } catch (Exception e) {
                plugin.getLogger().warning("[Collections] Failed to parse collection '" + key +
                        "' in " + file.getName() + ": " + e.getMessage());
            }
        }
    }
}
