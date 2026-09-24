package org.nakii.valmora.infrastructure.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The last version of every content entry that loaded successfully, per content folder, kept in
 * memory and mirrored to {@code plugins/Valmora/.last-good/<folder>.yml} so it survives restarts.
 *
 * <p>{@link YamlLoader} falls back to it when an entry (or a whole file, on a YAML syntax error)
 * no longer loads. The live server keeps the previous working version and the error is reported,
 * instead of the content silently disappearing on the next reload or restart. Entries an admin
 * deliberately removed from a file (or whose file they deleted) are forgotten, not resurrected.
 */
final class LastGoodStore {

    private static final Map<String, LastGoodStore> BY_FOLDER = new ConcurrentHashMap<>();

    private final File file;
    private final Logger logger;
    /** "relativePath\u0000key" → the entry's YAML (stored under key "v"). */
    private final Map<String, String> entries = new LinkedHashMap<>();
    private boolean dirty;

    private LastGoodStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("entries");
            if (section != null) {
                for (String k : section.getKeys(false)) {
                    ConfigurationSection e = section.getConfigurationSection(k);
                    if (e != null && e.isString("path") && e.isString("key") && e.isString("yaml")) {
                        entries.put(entryKey(e.getString("path"), e.getString("key")), e.getString("yaml"));
                    }
                }
            }
        }
    }

    static LastGoodStore of(File dataFolder, String folderName, Logger logger) {
        File file = new File(new File(dataFolder, ".last-good"), folderName.replace('/', '_') + ".yml");
        return BY_FOLDER.computeIfAbsent(file.getAbsolutePath(), path -> new LastGoodStore(file, logger));
    }

    private static String entryKey(String relativePath, String key) {
        return relativePath + "\u0000" + key;
    }

    synchronized void put(String relativePath, String key, ConfigurationSection section) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("v", section);
        String serialized = yaml.saveToString();
        String previous = entries.put(entryKey(relativePath, key), serialized);
        if (!serialized.equals(previous)) dirty = true;
    }

    synchronized ConfigurationSection get(String relativePath, String key) {
        String serialized = entries.get(entryKey(relativePath, key));
        return serialized == null ? null : parse(serialized);
    }

    /** Every remembered entry of {@code relativePath}: key → section. */
    synchronized Map<String, ConfigurationSection> entriesForFile(String relativePath) {
        Map<String, ConfigurationSection> result = new LinkedHashMap<>();
        String prefix = relativePath + "\u0000";
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            ConfigurationSection section = parse(e.getValue());
            if (section != null) result.put(e.getKey().substring(prefix.length()), section);
        }
        return result;
    }

    /** Forgets every entry not in {@code keep} (pairs built with {@link #pair}). */
    synchronized void retainOnly(Set<String> keep) {
        if (entries.keySet().retainAll(keep)) dirty = true;
    }

    static String pair(String relativePath, String key) {
        return entryKey(relativePath, key);
    }

    synchronized void save() {
        if (!dirty) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of(
                "Last successfully loaded version of each entry — Valmora falls back to these when",
                "an entry (or its whole file) stops loading. Managed automatically; don't edit."));
        int i = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            int sep = e.getKey().indexOf('\u0000');
            String base = "entries.e" + (i++);
            yaml.set(base + ".path", e.getKey().substring(0, sep));
            yaml.set(base + ".key", e.getKey().substring(sep + 1));
            yaml.set(base + ".yaml", e.getValue());
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            if (logger != null) logger.warning("Could not save last-good content cache " + file.getName() + ": " + ex.getMessage());
        }
    }

    private static ConfigurationSection parse(String serialized) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(serialized);
        } catch (InvalidConfigurationException e) {
            return null;
        }
        return yaml.getConfigurationSection("v");
    }
}
