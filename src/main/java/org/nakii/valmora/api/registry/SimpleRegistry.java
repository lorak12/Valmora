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

    @Override
    public synchronized void register(String id, T entry) {
        entries.put(id.toLowerCase(), entry);
    }

    @Override
    public synchronized T unregister(String id) {
        return entries.remove(id.toLowerCase());
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
    }

    @Override
    public int size() {
        return entries.size();
    }
}
