package org.nakii.valmora.api.registry;

import java.util.Collection;
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
     * Unregisters an entry with the given ID.
     * @param id the unique identifier
     * @return the removed entry, or null if none was found
     */
    T unregister(String id);

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
