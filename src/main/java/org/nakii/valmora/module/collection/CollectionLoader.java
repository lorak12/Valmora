package org.nakii.valmora.module.collection;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.LoadSession;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;

import java.io.File;

public class CollectionLoader {

    private final Valmora plugin;
    private final CollectionRegistry registry;

    public CollectionLoader(Valmora plugin, CollectionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /** Diagnostics for the load in progress. */
    private LoadSession session;

    public void loadCollections() {
        File folder = new File(plugin.getDataFolder(), "collections");
        if (!folder.exists()) {
            plugin.getLogger().warning("No collections/ folder found — skipping collection load.");
            return;
        }

        try (LoadSession s = LoadSession.open(plugin, "Collections", "collections")) {
            this.session = s;
            File categoriesFile = new File(folder, "categories.yml");
            if (categoriesFile.exists()) {
                loadCategories(categoriesFile);
            } else {
                s.warn("collections/categories.yml", null, "file not found — every collection's category: will be unknown");
            }

            org.nakii.valmora.infrastructure.versioning.IdAliases.clear(
                    org.nakii.valmora.infrastructure.versioning.IdAliases.COLLECTIONS);
            loadCollectionsRecursive(folder);

            // Cross-file check: every collection's category must exist in categories.yml.
            java.util.Set<String> categoryIds = new java.util.HashSet<>();
            for (CollectionCategory c : registry.getCategories()) categoryIds.add(c.getId().toLowerCase());
            for (CollectionDefinition def : registry.getCollections()) {
                if (!categoryIds.contains(def.getCategoryId().toLowerCase())) {
                    s.warn(null, def.getId(), "unknown category '" + def.getCategoryId() + "' — the collection won't show in any category menu",
                            org.nakii.valmora.infrastructure.config.diag.Suggestions.hint(def.getCategoryId(), categoryIds));
                }
            }
            s.loaded(registry.getCollections().size());
        } finally {
            this.session = null;
        }
    }

    private String rel(File f) {
        return plugin.getDataFolder().toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/');
    }

    private void loadCategories(File file) {
        String path = rel(file);
        YamlConfiguration config = session.readYaml(file, path);
        if (config == null) return;
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;
            try (LoadScope ignored = session.entry(path, key)) {
                CollectionCategory cat = CollectionDefinitionParser.parseCategory(key.toLowerCase(), section);
                registry.registerCategory(cat);
            } catch (Exception e) {
                session.error(path, key, "failed to parse category: " + e.getMessage());
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
        String path = rel(file);
        YamlConfiguration config = session.readYaml(file, path);
        if (config == null) return;
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;
            try (LoadScope ignored = session.entry(path, key)) {
                CollectionDefinition def = CollectionDefinitionParser.parseCollection(key.toLowerCase(), section);
                for (CollectionStage stage : def.getStages()) {
                    if (!stage.getRewards().isEmpty() && plugin.getScriptModule() != null) {
                        ScriptCompile.at("stages." + stage.getKey() + ".rewards",
                                () -> plugin.getScriptModule().compileCached(stage.getRewards()));
                    }
                }
                registry.registerCollection(def);
                org.nakii.valmora.infrastructure.versioning.IdAliases.registerAll(
                        org.nakii.valmora.infrastructure.versioning.IdAliases.COLLECTIONS,
                        section.getStringList("previous-ids"), def.getId());
            } catch (Exception e) {
                session.error(path, key, "failed to parse collection: " + e.getMessage());
            }
        }
    }
}
