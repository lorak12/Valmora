package org.nakii.valmora.module.collection;

import java.util.List;

public class CollectionDefinition {
    private final String id;
    private final String categoryId;
    private final String name;
    private final String icon;
    // Each entry is "EVENT_TYPE:IDENTIFIER", e.g. "BLOCK_BREAK:WHEAT"
    private final List<String> trackSources;
    private final List<CollectionStage> stages;

    public CollectionDefinition(String id, String categoryId, String name, String icon,
                                List<String> trackSources, List<CollectionStage> stages) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.icon = icon;
        this.trackSources = trackSources;
        this.stages = stages;
    }

    public boolean matches(String eventType, String identifier) {
        return trackSources.contains(eventType + ":" + identifier);
    }

    public int getStageForCount(long count) {
        int stage = 0;
        for (CollectionStage s : stages) {
            if (count >= s.getRequired()) {
                stage = s.getNumber();
            } else {
                break;
            }
        }
        return stage;
    }

    public int getMaxStage() { return stages.size(); }

    public String getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public List<String> getTrackSources() { return trackSources; }
    public List<CollectionStage> getStages() { return stages; }
}
