package org.nakii.valmora.module.resource;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.flow.FlowState;
import org.nakii.valmora.module.zone.ResourceStage;
import org.nakii.valmora.module.zone.ZoneResourceConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * State of one {@code resource.break} flow run. Script-visible as {@code $resource.material$}
 * (the block's original material), {@code $resource.stage$} (0-based stage index),
 * {@code $resource.required_power$} and {@code $resource.drops$} (number of stacks rolled).
 */
public final class BreakState implements FlowState {

    final ResourceManager manager;
    final Player player;
    final Block block;
    final String key;

    ResourceManager.ResourceTracker tracker;
    ZoneResourceConfig config;
    int stageIndex;
    int nextStageIndex;
    Material original;
    ResourceStage stage;
    final List<ItemStack> drops = new ArrayList<>();
    boolean dropsResolved;
    /** Set by the step that decides the outcome early (not tracked, depleted, too weak). */
    ResourceManager.BreakResult result;

    BreakState(ResourceManager manager, Player player, Block block, String key) {
        this.manager = manager;
        this.player = player;
        this.block = block;
        this.key = key;
    }

    @Override
    public void publish(ExecutionContext ctx) {
        ctx.set("resource:material", original != null ? original.name() : null);
        ctx.set("resource:stage", stageIndex);
        ctx.set("resource:required_power", config != null ? config.getRequiredPower() : null);
        ctx.set("resource:drops", drops.size());
    }
}
