package org.nakii.valmora.module.progression;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProgressionTreeDefinition {
    private final String id;
    private final String displayName;
    private final String description;
    private final String levelCurrencyCategory;
    private final String tierCurrencyCategory;
    private final List<ProgressionTier> tiers;
    private final Map<String, ProgressionNode> nodes;

    public ProgressionTreeDefinition(String id, String displayName, String description,
                                     String levelCurrencyCategory, String tierCurrencyCategory,
                                     List<ProgressionTier> tiers, Map<String, ProgressionNode> nodes) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.levelCurrencyCategory = levelCurrencyCategory;
        this.tierCurrencyCategory = tierCurrencyCategory;
        this.tiers = tiers != null ? tiers : List.of();
        this.nodes = nodes != null ? nodes : Map.of();
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getLevelCurrencyCategory() { return levelCurrencyCategory; }
    public String getTierCurrencyCategory() { return tierCurrencyCategory; }
    public List<ProgressionTier> getTiers() { return tiers; }
    public Map<String, ProgressionNode> getNodes() { return nodes; }

    public Optional<ProgressionNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId.toLowerCase()));
    }

    public Optional<ProgressionTier> getTier(int index) {
        return tiers.stream().filter(t -> t.getIndex() == index).findFirst();
    }

    public int getMaxTierIndex() {
        return tiers.stream().mapToInt(ProgressionTier::getIndex).max().orElse(0);
    }
}
