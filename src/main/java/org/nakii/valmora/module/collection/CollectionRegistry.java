package org.nakii.valmora.module.collection;

import java.util.*;
import java.util.stream.Collectors;

public class CollectionRegistry {
    private final Map<String, CollectionCategory> categories = new LinkedHashMap<>();
    private final Map<String, CollectionDefinition> collections = new LinkedHashMap<>();
    /** "EVENT_TYPE:IDENTIFIER" -> every collection tracking that exact source, built at register time so {@link CollectionListener} doesn't have to scan every collection per gameplay event. */
    private final Map<String, List<CollectionDefinition>> trackIndex = new HashMap<>();

    public void registerCategory(CollectionCategory cat) {
        categories.put(cat.getId().toLowerCase(), cat);
    }

    public void registerCollection(CollectionDefinition def) {
        collections.put(def.getId().toLowerCase(), def);
        for (String source : def.getTrackSources()) {
            trackIndex.computeIfAbsent(source, k -> new ArrayList<>()).add(def);
        }
    }

    /** Collections tracking exactly {@code eventType + ":" + identifier}, or an empty list if none. O(1) instead of scanning every registered collection. */
    public List<CollectionDefinition> getCollectionsFor(String eventType, String identifier) {
        return trackIndex.getOrDefault(eventType + ":" + identifier, List.of());
    }

    public Optional<CollectionCategory> getCategory(String id) {
        return Optional.ofNullable(categories.get(id.toLowerCase()));
    }

    public Optional<CollectionDefinition> getCollection(String id) {
        return Optional.ofNullable(collections.get(id.toLowerCase()));
    }

    public Collection<CollectionCategory> getCategories() {
        return categories.values();
    }

    public Collection<CollectionDefinition> getCollections() {
        return collections.values();
    }

    public List<CollectionDefinition> getCollectionsInCategory(String categoryId) {
        String lower = categoryId.toLowerCase();
        return collections.values().stream()
                .filter(def -> def.getCategoryId().equalsIgnoreCase(lower))
                .collect(Collectors.toList());
    }

    public void clear() {
        categories.clear();
        collections.clear();
        trackIndex.clear();
    }
}
