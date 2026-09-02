package org.nakii.valmora.module.pack.install;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Non-destructively merges a pack's shared-config fragment (e.g. a {@code rarities.yml} containing
 * just the new rarities one pack adds) into the server's live shared config, and reverts exactly
 * that diff on uninstall (docs/modules/design/pack.md §6).
 *
 * <p>The engine's ~10 mergeable shared configs (see {@code PackContentFolders.SHARED_CONFIGS}) take
 * two shapes at their top level: a <b>list</b> (e.g. {@code item_types.yml}'s flat string list,
 * {@code combat_pipeline.yml}'s {@code stages:} list of stage maps) or a <b>section</b> (e.g.
 * {@code rarities.yml}'s {@code rarities:} map of entries). Rather than special-case each file by
 * name, this merges purely by <em>shape</em>: a top-level key that's a list gets new elements
 * appended (dedup by equality); a top-level key that's a section gets new sub-keys added
 * ({@code putIfAbsent} — exactly {@code QuestPackageManager.mergeTemplates()}'s existing precedent,
 * generalized); an existing key of any other shape, or a top-level key whose base and fragment
 * shapes disagree, is left untouched and reported as a warning rather than risking data loss. A
 * fragment key entirely absent from the base file is added wholesale.
 */
public final class SharedConfigMerger {

    private SharedConfigMerger() {}

    /** Sentinel diff entry meaning "this whole top-level key was freshly added by the pack" (as opposed to one new list element / sub-key of an existing key). */
    private static final String WHOLE_KEY_SENTINEL = "*";

    public record MergeResult(Map<String, List<String>> diff, List<String> warnings) {
    }

    /**
     * Merges {@code fragmentFile}'s content into {@code baseFile} in place, returning the diff (for
     * {@link #revert}) and any non-fatal warnings (skipped collisions).
     */
    public static MergeResult merge(File baseFile, File fragmentFile) throws IOException {
        YamlConfiguration base = YamlConfiguration.loadConfiguration(baseFile);
        YamlConfiguration fragment = YamlConfiguration.loadConfiguration(fragmentFile);

        Map<String, List<String>> diff = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (String topKey : fragment.getKeys(false)) {
            if (!base.isSet(topKey)) {
                base.set(topKey, fragment.get(topKey));
                diff.put(topKey, new ArrayList<>(List.of(WHOLE_KEY_SENTINEL)));
                continue;
            }

            if (fragment.isConfigurationSection(topKey) && base.isConfigurationSection(topKey)) {
                List<String> addedSubKeys = mergeSections(base.getConfigurationSection(topKey),
                        fragment.getConfigurationSection(topKey), topKey, warnings);
                if (!addedSubKeys.isEmpty()) diff.put(topKey, addedSubKeys);
            } else if (fragment.isList(topKey) && base.isList(topKey)) {
                List<String> addedEntries = mergeLists(base, topKey, fragment.getList(topKey), warnings);
                if (!addedEntries.isEmpty()) diff.put(topKey, addedEntries);
            } else {
                warnings.add("'" + fragmentFile.getName() + "' top-level key '" + topKey
                        + "' already exists in the base config with an incompatible shape — left untouched");
            }
        }

        base.save(baseFile);
        return new MergeResult(diff, warnings);
    }

    private static List<String> mergeSections(ConfigurationSection base, ConfigurationSection fragment,
                                                String topKey, List<String> warnings) {
        List<String> added = new ArrayList<>();
        for (String subKey : fragment.getKeys(false)) {
            if (base.isSet(subKey)) {
                warnings.add("'" + topKey + "." + subKey + "' already exists in the base config — left untouched");
                continue;
            }
            base.set(subKey, fragment.get(subKey));
            added.add(subKey);
        }
        return added;
    }

    private static List<String> mergeLists(YamlConfiguration base, String topKey, List<?> fragmentList, List<String> warnings) {
        List<Object> baseList = new ArrayList<>(base.getList(topKey));
        List<String> added = new ArrayList<>();
        for (Object entry : fragmentList) {
            if (baseList.contains(entry)) continue;
            baseList.add(entry);
            added.add(canonicalize(entry));
        }
        base.set(topKey, baseList);
        return added;
    }

    /**
     * Reverts exactly the diff a prior {@link #merge} produced: removes freshly-added whole keys,
     * removes added sub-keys from sections, and removes added elements from lists — anything the
     * base config already had before the merge (including a value the same as one the pack added)
     * is left untouched.
     */
    public static void revert(File baseFile, Map<String, List<String>> diff) throws IOException {
        if (diff == null || diff.isEmpty()) return;
        YamlConfiguration base = YamlConfiguration.loadConfiguration(baseFile);

        for (Map.Entry<String, List<String>> entry : diff.entrySet()) {
            String topKey = entry.getKey();
            List<String> addedIds = entry.getValue();
            if (!base.isSet(topKey)) continue;

            if (addedIds.size() == 1 && WHOLE_KEY_SENTINEL.equals(addedIds.get(0))) {
                base.set(topKey, null);
            } else if (base.isConfigurationSection(topKey)) {
                ConfigurationSection section = base.getConfigurationSection(topKey);
                for (String subKey : addedIds) {
                    section.set(subKey, null);
                }
            } else if (base.isList(topKey)) {
                List<Object> current = new ArrayList<>(base.getList(topKey));
                current.removeIf(item -> addedIds.contains(canonicalize(item)));
                base.set(topKey, current);
            }
        }

        base.save(baseFile);
    }

    private static String canonicalize(Object entry) {
        return String.valueOf(entry);
    }
}
