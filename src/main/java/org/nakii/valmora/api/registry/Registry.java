package org.nakii.valmora.api.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * A generic registry for storing and retrieving engine objects by their unique ID.
 * @param <T> the type of object to register
 */
public interface Registry<T> {

    /**
     * Registers an entry with the given ID.
     * @param id the unique identifier
     * @param entry the object to register
     */
    void register(String id, T entry);

    /**
     * Registers an entry with the given ID, tagged with the id of the content pack (or other
     * source) that owns it. {@code sourceId} may be {@code null} for base/vanilla content that
     * doesn't belong to any pack. Default implementation ignores the source and delegates to
     * {@link #register(String, Object)} — implementations that want source tracking (see
     * {@link SimpleRegistry}) should override this instead.
     * @param id the unique identifier
     * @param entry the object to register
     * @param sourceId the id of the pack/source that owns this entry, or {@code null}
     */
    default void register(String id, T entry, String sourceId) {
        register(id, entry);
    }

    /**
     * Unregisters an entry with the given ID.
     * @param id the unique identifier
     * @return the removed entry, or null if none was found
     */
    T unregister(String id);

    /**
     * Returns the ids of every entry registered under the given source (e.g. a content pack id).
     * Default implementation returns an empty set — only implementations with source tracking
     * (see {@link SimpleRegistry}) can answer this.
     * @param sourceId the source id to look up
     * @return the ids owned by that source
     */
    default Set<String> getIdsBySource(String sourceId) {
        return Collections.emptySet();
    }

    /**
     * Unregisters every entry that was registered under the given source (e.g. uninstalling a
     * content pack). Default implementation is a no-op and returns 0 — only implementations with
     * source tracking (see {@link SimpleRegistry}) can do this.
     * @param sourceId the source id whose entries should be removed
     * @return the number of entries removed
     */
    default int unregisterAllBySource(String sourceId) {
        return 0;
    }

    /**
     * Retrieves an entry by its ID.
     * @param id the unique identifier
     * @return an Optional containing the entry if found, otherwise empty
     */
    Optional<T> get(String id);

    /**
     * Returns whether the registry contains an entry with the given ID.
     * @param id the unique identifier
     * @return true if found, false otherwise
     */
    boolean contains(String id);

    /**
     * Returns all registered IDs.
     * @return a set of IDs
     */
    Set<String> getKeys();

    /**
     * Returns all registered entries.
     * @return a collection of entries
     */
    Collection<T> values();

    /**
     * Clears all entries from the registry.
     */
    void clear();

    /**
     * Returns the number of entries in the registry.
     * @return size of the registry
     */
    int size();
}
