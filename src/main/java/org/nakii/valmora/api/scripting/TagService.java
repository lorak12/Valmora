package org.nakii.valmora.api.scripting;

/**
 * Interface for managing and checking tags on player profiles.
 */
public interface TagService {

    /**
     * Checks if the current context has the specified tag.
     * @param tag the tag name to check
     * @return true if the tag exists
     */
    boolean hasTag(String tag);

    /**
     * Adds a tag to the player in the current context.
     * @param tag the tag name to add
     */
    void addTag(String tag);

    /**
     * Removes a tag from the player in the current context.
     * @param tag the tag name to remove
     */
    void removeTag(String tag);
}
