package org.nakii.valmora.module.resource;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Protects resource nodes from environmental block changes that bypass
 * {@link ResourceManager#handleBlockBreak}. Without this, an explosion or piston could destroy or
 * displace a block mid-progression/mid-regen, leaving the tracker pointing at a block that no
 * longer exists (or desyncing the "original material" the regen timer will restore).
 *
 * <p>Policy: tracked/configured resource blocks are simply protected — explosions never consume
 * them, and pistons never move them. They can still only be mined through the normal break
 * pipeline (which is what actually grants drops and progresses the node).
 */
public class ResourceEnvironmentListener implements Listener {

    private final ResourceManager resourceManager;

    public ResourceEnvironmentListener(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    private boolean isResourceBlock(Block block) {
        return resourceManager.isTrackedResource(block.getLocation())
                || resourceManager.getResourceConfigAt(block.getLocation()) != null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isResourceBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isResourceBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isResourceBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isResourceBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
