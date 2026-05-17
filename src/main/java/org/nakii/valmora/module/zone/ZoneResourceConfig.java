package org.nakii.valmora.module.zone;

import java.util.List;

public class ZoneResourceConfig {
    private final int regenDelayTicks;
    private final List<ResourceStage> stages;

    public ZoneResourceConfig(int regenDelayTicks, List<ResourceStage> stages) {
        this.regenDelayTicks = regenDelayTicks;
        this.stages = stages;
    }

    public int getRegenDelayTicks() { return regenDelayTicks; }
    public List<ResourceStage> getStages() { return stages; }
    public ResourceStage getStage(int index) { return stages.get(index); }
    public int getStageCount() { return stages.size(); }
}
