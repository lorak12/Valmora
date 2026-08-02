package org.nakii.valmora.module.resource;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.item.impl.AoeMineMechanic;
import org.nakii.valmora.util.Formatter;

public class ResourceListener implements Listener {

    private final ResourceManager resourceManager;

    public ResourceListener(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ResourceManager.BreakResult result = resourceManager.handleBlockBreak(player, event.getBlock());

        switch (result) {
            case NOT_TRACKED -> { /* vanilla handling applies */ }
            case INSUFFICIENT_POWER -> {
                event.setCancelled(true);
                player.sendMessage(Formatter.format("<red>This ore requires a more powerful tool."));
            }
            case HANDLED -> {
                event.setDropItems(false);
                double spread = getPlayerMiningSpread(player);
                int radius = (int) Math.floor(spread);
                if (radius > 0) {
                    AoeMineMechanic.mineRadius(resourceManager, player, event.getBlock(), radius);
                }
            }
        }
    }

    private double getPlayerMiningSpread(Player player) {
        var session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session == null) return 0.0;
        var profile = session.getActiveProfile();
        if (profile == null) return 0.0;
        return profile.getStatManager().getStat(ValmoraAPI.getInstance().getSystemStats().getMiningSpread());
    }
}
