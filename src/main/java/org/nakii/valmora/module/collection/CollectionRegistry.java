package org.nakii.valmora.module.collection;

import java.util.*;
import java.util.stream.Collectors;

public class CollectionRegistry {
    private final Map<String, CollectionCategory> categories = new LinkedHashMap<>();
    private final Map<String, CollectionDefinition> collections = new LinkedHashMap<>();

    public void registerCategory(CollectionCategory cat) {
        categories.put(cat.getId().toLowerCase(), cat);
    }

    public void registerCollection(CollectionDefinition def) {
        collections.put(def.getId().toLowerCase(), def);
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
    }
}
