package org.nakii.valmora.module.progression;

import java.util.List;

public class ProgressionTier {
    private final int index;
    private final String displayName;
    private final int unlockCost;
    private final List<String> nodeIds;

    public ProgressionTier(int index, String displayName, int unlockCost, List<String> nodeIds) {
        this.index = index;
        this.displayName = displayName;
        this.unlockCost = unlockCost;
        this.nodeIds = nodeIds != null ? nodeIds : List.of();
    }

    public int getIndex() { return index; }
    public String getDisplayName() { return displayName; }
    public int getUnlockCost() { return unlockCost; }
    public List<String> getNodeIds() { return nodeIds; }
}
