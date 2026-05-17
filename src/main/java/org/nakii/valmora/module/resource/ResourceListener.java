package org.nakii.valmora.module.resource;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class ResourceListener implements Listener {

    private final ResourceManager resourceManager;

    public ResourceListener(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        boolean suppress = resourceManager.handleBlockBreak(event.getPlayer(), event.getBlock());
        if (suppress) event.setDropItems(false);
    }
}
