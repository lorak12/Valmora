package org.nakii.valmora.module.blockloot;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Mirrors {@code module.resource.ResourceListener}: runs at {@code LOWEST} so that when this
 * break is handled here, {@code event.setDropItems(false)} lands before any other
 * {@code ignoreCancelled = true} listener — including {@code module.item.LootListener} at
 * {@code HIGH}, which has its own matching defer check — ever sees the event.
 */
public class BlockLootListener implements Listener {

    private final BlockLootManager manager;

    public BlockLootListener(BlockLootManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (manager.handleBlockBreak(player, event.getBlock())) {
            event.setDropItems(false);
        }
    }
}
