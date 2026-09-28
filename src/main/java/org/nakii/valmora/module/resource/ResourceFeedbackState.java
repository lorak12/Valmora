package org.nakii.valmora.module.resource;

import org.bukkit.Location;
import org.bukkit.Material;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.pipeline.FeedbackSteps;

/** State of the resource feedback flows ({@code mine_feedback}, {@code denied_feedback}, {@code regen_feedback}). */
public final class ResourceFeedbackState implements FeedbackSteps.Located {

    private final Location location;
    private final Material material;

    ResourceFeedbackState(Location location, Material material) {
        this.location = location;
        this.material = material;
    }

    @Override
    public Location feedbackLocation() {
        return location;
    }

    @Override
    public Material feedbackMaterial() {
        return material;
    }

    @Override
    public void publish(ExecutionContext ctx) {
        ctx.set("resource:material", material != null ? material.name() : null);
    }
}
