package org.nakii.valmora.module.collection;

public class CollectionCategory {
    private final String id;
    private final String name;
    private final String icon;
    private final String description;

    public CollectionCategory(String id, String name, String icon, String description) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public String getDescription() { return description; }
}
