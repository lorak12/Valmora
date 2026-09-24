package org.nakii.valmora.module.profile;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.collection.CollectionManager;
import org.nakii.valmora.module.collection.CollectionModule;
import org.nakii.valmora.module.progression.ProgressionModule;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.skill.SkillModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Finds a profile's saved progress that points at content which no longer exists (after id
 * aliases were applied): XP for a deleted skill, counts for a deleted collection, quest and
 * progression variables of deleted quests/trees, and quarantined stats.
 *
 * <p>Orphans are kept on purpose. They're harmless (ignored at runtime), and keeping them means
 * restoring the content, or adding the old id to its {@code previous-ids:}, brings the progress
 * back. {@link #purge} removes them for good when an admin decides the content is gone for good.
 */
public final class OrphanReport {

    private OrphanReport() {}

    /** category → orphaned keys. A module that isn't loaded is skipped rather than reported as all-orphan. */
    public static Map<String, List<String>> find(ValmoraProfile profile) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        var modules = ValmoraAPI.getInstance().getModuleManager();

        modules.getModule("skills", SkillModule.class).ifPresent(skills -> {
            List<String> orphans = new ArrayList<>();
            for (String id : profile.getSkillManager().getSaveData().keySet()) {
                if (skills.getSkillRegistry().getSkill(id).isEmpty()) orphans.add(id);
            }
            if (!orphans.isEmpty()) result.put("skills", orphans);
        });

        modules.getModule("collections", CollectionModule.class).ifPresent(collections -> {
            List<String> orphans = new ArrayList<>();
            CollectionManager.SaveData data = profile.getCollectionManager().getSaveData();
            for (String id : data.counts.keySet()) {
                if (collections.getRegistry().getCollection(id).isEmpty()) orphans.add(id);
            }
            if (!orphans.isEmpty()) result.put("collections", orphans);
        });

        QuestManager quests = ValmoraAPI.getInstance().getQuestManager();
        if (quests != null) {
            List<String> orphans = variableOrphans(profile, "quest.", id -> quests.getRegistry().contains(id));
            if (!orphans.isEmpty()) result.put("quests", orphans);
        }

        modules.getModule("progression", ProgressionModule.class).ifPresent(progression -> {
            List<String> orphans = variableOrphans(profile, "progression.",
                    id -> progression.getProgressionRegistry().getTree(id).isPresent());
            if (!orphans.isEmpty()) result.put("progression", orphans);
        });

        if (!profile.getQuarantinedStats().isEmpty()) {
            result.put("stats", new ArrayList<>(profile.getQuarantinedStats().keySet()));
        }
        return result;
    }

    /** Removes everything {@link #find} reports; returns the number of entries removed. */
    public static int purge(ValmoraProfile profile) {
        Map<String, List<String>> orphans = find(profile);
        int removed = 0;

        List<String> skills = orphans.getOrDefault("skills", List.of());
        if (!skills.isEmpty()) {
            skills.forEach(profile.getSkillManager()::remove);
            removed += skills.size();
        }

        List<String> collections = orphans.getOrDefault("collections", List.of());
        if (!collections.isEmpty()) {
            CollectionManager.SaveData data = profile.getCollectionManager().getSaveData();
            for (String id : collections) {
                data.counts.remove(id);
                data.grantedStages.remove(id);
                data.grantedKeys.remove(id);
            }
            profile.getCollectionManager().loadData(data);
            removed += collections.size();
        }

        for (String prefix : List.of("quests", "progression")) {
            for (String id : orphans.getOrDefault(prefix, List.of())) {
                String keyPrefix = (prefix.equals("quests") ? "quest." : "progression.") + id + ".";
                removed += removeKeysWithPrefix(profile.getVariables(), keyPrefix);
                if (prefix.equals("quests")) {
                    profile.getTags().removeIf(tag -> tag.startsWith(id + ".auto-once-"));
                }
            }
        }

        removed += profile.getQuarantinedStats().size();
        profile.getQuarantinedStats().clear();
        return removed;
    }

    /** Distinct ids under {@code prefix} (the segment after it) that {@code exists} rejects. */
    private static List<String> variableOrphans(ValmoraProfile profile, String prefix, Predicate<String> exists) {
        List<String> orphans = new ArrayList<>();
        for (String key : profile.getVariables().keySet()) {
            if (!key.startsWith(prefix)) continue;
            int end = key.indexOf('.', prefix.length());
            if (end < 0) continue;
            String id = key.substring(prefix.length(), end);
            if (!orphans.contains(id) && !exists.test(id)) orphans.add(id);
        }
        return orphans;
    }

    private static int removeKeysWithPrefix(Map<String, Object> variables, String prefix) {
        List<String> doomed = new ArrayList<>();
        for (String key : variables.keySet()) {
            if (key.startsWith(prefix)) doomed.add(key);
        }
        doomed.forEach(variables::remove);
        return doomed.size();
    }
}
