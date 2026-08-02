package org.nakii.valmora.module.zone;

import java.util.List;

public class ZoneResourceConfig {
    private final int regenDelayTicks;
    private final List<ResourceStage> stages;
    private final double requiredPower;

    public ZoneResourceConfig(int regenDelayTicks, List<ResourceStage> stages) {
        this(regenDelayTicks, stages, 0.0);
    }

    public ZoneResourceConfig(int regenDelayTicks, List<ResourceStage> stages, double requiredPower) {
        this.regenDelayTicks = regenDelayTicks;
        this.stages = stages;
        this.requiredPower = requiredPower;
    }

    public int getRegenDelayTicks() { return regenDelayTicks; }
    public List<ResourceStage> getStages() { return stages; }
    public ResourceStage getStage(int index) { return stages.get(index); }
    public int getStageCount() { return stages.size(); }
    public double getRequiredPower() { return requiredPower; }
}
