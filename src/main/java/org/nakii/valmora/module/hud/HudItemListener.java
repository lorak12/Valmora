package org.nakii.valmora.module.hud;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

public class HudItemListener implements Listener {

    private final HudItemModule module;

    public HudItemListener(HudItemModule module) {
        this.module = module;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        module.giveHudItems(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Schedule 1-tick delay to run after vanilla respawn inventory restore
        event.getPlayer().getServer().getScheduler().runTaskLater(
                org.nakii.valmora.Valmora.getInstance(), () -> module.giveHudItems(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Cancel if the item being moved is a HUD item
        if (module.isHudItem(event.getCurrentItem()) || module.isHudItem(event.getCursor())) {
            if (!shouldFireClickAction(event, player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (module.isHudItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            // Restore the item to its configured slot
            module.giveHudItems(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Remove HUD items from the drop list — they should never appear in the world
        event.getDrops().removeIf(item -> module.isHudItem(item));
    }

    private boolean shouldFireClickAction(InventoryClickEvent event, Player player) {
        // Only fire click actions when clicking in the player's own inventory on the HUD item's slot
        if (event.getClickedInventory() != player.getInventory()) return false;

        HudItemDefinition def = module.getBySlot(event.getSlot());
        if (def == null) return false;

        var ctx = new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());

        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            def.getOnRightClick().execute(ctx);
        } else {
            def.getOnLeftClick().execute(ctx);
        }
        event.setCancelled(true);
        return true; // handled, but still cancel the default move
    }
}
