package org.nakii.valmora.module.profile;

import org.nakii.valmora.infrastructure.versioning.IdAliases;
import org.nakii.valmora.module.collection.CollectionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites the content ids a profile's saved progress is keyed by, following id aliases
 * ({@code previous-ids:} and pack namespacing — see {@link IdAliases}).
 *
 * <p>Runs on every load, not once per upgrade: an alias can be added on any reload, and data only
 * needs rewriting once it's loaded. After a rename, progress under the old id (skill XP, collection
 * counts and reward ledger, quest status/objectives/auto-once guards, progression levels) moves to
 * the new id instead of being orphaned while the player silently starts over. When both keys exist
 * (e.g. the player earned progress under the new id before the alias was added), the larger
 * number wins and the key already under the new id is otherwise kept, so nothing is double-counted.
 */
public final class ProfileReferences {

    private ProfileReferences() {}

    /** Applies every alias rewrite to {@code profile}; returns how many keys moved. */
    public static int resolveAliases(ValmoraProfile profile) {
        int moved = 0;
        moved += resolveSkills(profile);
        moved += resolveCollections(profile);
        moved += resolveVariablePrefix(profile.getVariables(), "quest.", IdAliases.QUESTS);
        moved += resolveVariablePrefix(profile.getVariables(), "progression.", IdAliases.PROGRESSION);
        moved += resolveQuestGuardTags(profile.getTags());
        return moved;
    }

    private static int resolveSkills(ValmoraProfile profile) {
        return profile.getSkillManager().remapIds(id -> IdAliases.resolve(IdAliases.SKILLS, id));
    }

    private static int resolveCollections(ValmoraProfile profile) {
        CollectionManager.SaveData data = profile.getCollectionManager().getSaveData();
        CollectionManager.SaveData resolved = new CollectionManager.SaveData();
        int moved = 0;
        for (Map.Entry<String, Long> e : data.counts.entrySet()) {
            String id = IdAliases.resolve(IdAliases.COLLECTIONS, e.getKey());
            if (!id.equals(e.getKey().toLowerCase())) moved++;
            resolved.counts.merge(id, e.getValue(), Math::max);
        }
        for (Map.Entry<String, Integer> e : data.grantedStages.entrySet()) {
            String id = IdAliases.resolve(IdAliases.COLLECTIONS, e.getKey());
            if (!id.equals(e.getKey().toLowerCase())) moved++;
            resolved.grantedStages.merge(id, e.getValue(), Math::max);
        }
        for (Map.Entry<String, Set<String>> e : data.grantedKeys.entrySet()) {
            String id = IdAliases.resolve(IdAliases.COLLECTIONS, e.getKey());
            if (!id.equals(e.getKey().toLowerCase())) moved++;
            resolved.grantedKeys.computeIfAbsent(id, k -> new java.util.HashSet<>()).addAll(e.getValue());
        }
        if (moved > 0) profile.getCollectionManager().loadData(resolved);
        return moved;
    }

    /**
     * Moves {@code <prefix><oldId>.rest} variables to {@code <prefix><newId>.rest}. The id is the
     * segment right after the prefix; pack-namespaced ids ({@code pack:id}) contain no dots, so
     * the first dot after the prefix always ends the id.
     */
    static int resolveVariablePrefix(Map<String, Object> variables, String prefix, String type) {
        List<String> keys = new ArrayList<>(variables.keySet());
        int moved = 0;
        for (String key : keys) {
            if (!key.startsWith(prefix)) continue;
            int end = key.indexOf('.', prefix.length());
            if (end < 0) continue;
            String id = key.substring(prefix.length(), end);
            if (!IdAliases.isAlias(type, id)) continue;
            String newKey = prefix + IdAliases.resolve(type, id) + key.substring(end);
            Object value = variables.remove(key);
            Object existing = variables.get(newKey);
            if (existing == null) {
                variables.put(newKey, value);
            } else if (existing instanceof Number a && value instanceof Number b && b.doubleValue() > a.doubleValue()) {
                variables.put(newKey, value);
            }
            moved++;
        }
        return moved;
    }

    /** Auto-once guard tags are {@code <questId>.auto-once-<key>}. */
    private static int resolveQuestGuardTags(Set<String> tags) {
        int moved = 0;
        for (String tag : new ArrayList<>(tags)) {
            int marker = tag.indexOf(".auto-once-");
            if (marker <= 0) continue;
            String questId = tag.substring(0, marker);
            if (!IdAliases.isAlias(IdAliases.QUESTS, questId)) continue;
            tags.remove(tag);
            tags.add(IdAliases.resolve(IdAliases.QUESTS, questId) + tag.substring(marker));
            moved++;
        }
        return moved;
    }
}
