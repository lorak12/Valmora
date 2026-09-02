package org.nakii.valmora.module.pack.manifest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.api.config.LoadResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses a {@code pack.yml} manifest. Follows the same "single top-level key = the entity's own
 * id" convention every other Valmora content type uses (docs/modules/user/modifier.md §4) — there
 * is no {@code manifest:} wrapper key.
 */
public final class PackManifestParser {

    private PackManifestParser() {}

    /**
     * Reads and parses a {@code pack.yml} file. The file must contain exactly one top-level key
     * (the pack's own id) — zero or more than one is a failure, since there'd be no unambiguous
     * pack id to namespace content under.
     */
    public static LoadResult<PackManifest, String> parseFile(File packYml) {
        if (!packYml.exists() || !packYml.isFile()) {
            return LoadResult.failure("pack.yml not found at " + packYml.getPath());
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(packYml);
        List<String> keys = new ArrayList<>(config.getKeys(false));
        if (keys.isEmpty()) {
            return LoadResult.failure("pack.yml has no top-level entries — expected exactly one, the pack's own id");
        }
        if (keys.size() > 1) {
            return LoadResult.failure("pack.yml has " + keys.size() + " top-level keys (" + keys
                    + ") — expected exactly one, the pack's own id");
        }
        String id = keys.get(0);
        ConfigurationSection section = config.getConfigurationSection(id);
        if (section == null) {
            return LoadResult.failure("pack.yml's top-level key '" + id + "' is not a section");
        }
        return parse(id, section, packYml.getPath());
    }

    /** Parses one already-located {@code (id, section)} pair, matching {@code YamlLoader.SectionParser}'s shape. */
    public static LoadResult<PackManifest, String> parse(String id, ConfigurationSection section, String filePath) {
        try {
            if (id == null || id.isBlank()) {
                return LoadResult.failure("[" + filePath + "] pack id is blank");
            }
            if (id.contains(":") || id.contains("/") || id.contains("\\") || id.contains(" ")) {
                return LoadResult.failure("[" + filePath + "] pack id '" + id
                        + "' must not contain ':', '/', '\\\\' or spaces — it becomes a content-id namespace prefix");
            }

            String name = section.getString("name", id);
            String version = requireString(section, "version", filePath, id);
            String author = section.getString("author", "");
            String description = section.getString("description", "");
            String engineVersionMin = requireString(section, "engine_version_min", filePath, id);
            String engineVersionMax = section.getString("engine_version_max", null);
            String checksum = section.getString("checksum", null);

            List<String> dependsPlugins = section.getStringList("depends.plugins");
            List<PackDependency> dependsPacks = parseDependencyList(section, "depends.packs");
            List<PackDependency> softDependsPacks = parseDependencyList(section, "soft_depends.packs");

            List<String> providesContent = section.getStringList("provides.content");
            List<String> providesShared = section.getStringList("provides.shared");
            if (providesContent.isEmpty() && providesShared.isEmpty()) {
                return LoadResult.failure("[" + filePath + "] pack '" + id
                        + "' declares no provides.content or provides.shared entries — it wouldn't install anything");
            }

            return LoadResult.success(new PackManifest(id, name, version, author, description,
                    engineVersionMin, engineVersionMax, dependsPlugins, dependsPacks, softDependsPacks,
                    providesContent, providesShared, checksum));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse pack manifest '" + id + "': " + e.getMessage());
        }
    }

    private static String requireString(ConfigurationSection section, String path, String filePath, String id) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + path + "' is required");
        }
        return value;
    }

    private static List<PackDependency> parseDependencyList(ConfigurationSection section, String path) {
        List<PackDependency> result = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList(path)) {
            Object rawId = entry.get("id");
            if (rawId == null) {
                throw new IllegalArgumentException("'" + path + "' entry is missing 'id'");
            }
            Object rawVersion = entry.get("version");
            result.add(new PackDependency(rawId.toString(), rawVersion != null ? rawVersion.toString() : null));
        }
        return result;
    }
}
