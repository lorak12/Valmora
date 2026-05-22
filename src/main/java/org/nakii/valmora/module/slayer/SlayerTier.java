package org.nakii.valmora.module.slayer;

import java.util.List;

public class SlayerTier {

    private final int tier;
    private final double cost;
    private final String targetCategory;
    private final int killsRequired;
    private final String bossMob;
    private final List<String> completionEvents;

    public SlayerTier(int tier, double cost, String targetCategory, int killsRequired,
                       String bossMob, List<String> completionEvents) {
        this.tier = tier;
        this.cost = cost;
        this.targetCategory = targetCategory;
        this.killsRequired = killsRequired;
        this.bossMob = bossMob;
        this.completionEvents = completionEvents;
    }

    public int getTier() { return tier; }
    public double getCost() { return cost; }
    public String getTargetCategory() { return targetCategory; }
    public int getKillsRequired() { return killsRequired; }
    public String getBossMob() { return bossMob; }
    public List<String> getCompletionEvents() { return completionEvents; }
}
