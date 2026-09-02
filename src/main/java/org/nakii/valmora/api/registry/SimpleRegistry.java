package org.nakii.valmora.api.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A basic thread-safe registry implementation using a HashMap.
 * All keys are case-insensitive.
 * @param <T> the type of object to register
 */
public class SimpleRegistry<T> implements Registry<T> {

    private final Map<String, T> entries = new HashMap<>();
    // Parallel map: lowercased id -> owning source id (e.g. a content pack id). Absent/null entries
    // are base/vanilla content with no owning source.
    private final Map<String, String> sourceById = new HashMap<>();

    @Override
    public synchronized void register(String id, T entry) {
        register(id, entry, null);
    }

    @Override
    public synchronized void register(String id, T entry, String sourceId) {
        String key = id.toLowerCase();
        entries.put(key, entry);
        if (sourceId != null) {
            sourceById.put(key, sourceId.toLowerCase());
        } else {
            sourceById.remove(key);
        }
    }

    @Override
    public synchronized T unregister(String id) {
        String key = id.toLowerCase();
        sourceById.remove(key);
        return entries.remove(key);
    }

    @Override
    public synchronized Set<String> getIdsBySource(String sourceId) {
        if (sourceId == null) {
            return Collections.emptySet();
        }
        String needle = sourceId.toLowerCase();
        Set<String> result = new java.util.HashSet<>();
        for (Map.Entry<String, String> e : sourceById.entrySet()) {
            if (needle.equals(e.getValue())) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    @Override
    public synchronized int unregisterAllBySource(String sourceId) {
        Set<String> ids = getIdsBySource(sourceId);
        for (String id : ids) {
            entries.remove(id);
            sourceById.remove(id);
        }
        return ids.size();
    }

    @Override
    public Optional<T> get(String id) {
        return Optional.ofNullable(entries.get(id.toLowerCase()));
    }

    @Override
    public boolean contains(String id) {
        return entries.containsKey(id.toLowerCase());
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    @Override
    public Collection<T> values() {
        return Collections.unmodifiableCollection(entries.values());
    }

    @Override
    public synchronized void clear() {
        entries.clear();
        sourceById.clear();
    }

    @Override
    public int size() {
        return entries.size();
    }
}
