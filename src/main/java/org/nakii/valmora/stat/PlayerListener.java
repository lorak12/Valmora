package org.nakii.valmora.stat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.profile.ValmoraPlayer;
import org.nakii.valmora.profile.ValmoraProfile;

import java.util.Set;

public class PlayerListener implements Listener {
    
    private final Valmora plugin;

    public PlayerListener(Valmora plugin) {
        this.plugin = plugin;
    }

    /**
     * Centralized method to trigger stat recalculation for a player.
     * Wraps the logic in a 1-tick delayed task to ensure the inventory state is
     * fully updated before we read it. This is a robust pattern to avoid race conditions.
     *
     * @param player The player whose stats need to be recalculated.
     */
    private void recalculate(Player player){
        Bukkit.getScheduler().runTask(plugin, () -> {
            ValmoraPlayer vplayer = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (vplayer == null || vplayer.getActiveProfile() == null) {
                return;
            }
            vplayer.getActiveProfile().getStatManager().recalculateStats(player);
            plugin.getLogger().info("Recalculated stats for " + player.getName());
        });
    }

    // --- Player Lifecycle Events ---

    /**
     * Recalculates stats when a player joins the server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        recalculate(event.getPlayer());
    }

    /**
     * Recalculates stats when a player respawns.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ValmoraProfile profile = plugin.getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();

            double maxHealth = profile.getStatManager().getStat(Stat.HEALTH);
            profile.getPlayerState().heal(maxHealth, profile.getStatManager());

            plugin.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), profile.getStatManager());
        });
        // Delay is crucial here to allow the player to fully respawn with their new inventory.
        recalculate(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaRegen(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) {
            // We only cancel SATIATED (full hunger) and REGEN (potions/beacons)
            // We allow CUSTOM so plugin developers can still heal players via API if needed.
            EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
            if (reason == EntityRegainHealthEvent.RegainReason.SATIATED || 
            reason == EntityRegainHealthEvent.RegainReason.MAGIC_REGEN ||
            reason == EntityRegainHealthEvent.RegainReason.REGEN) {
            
            event.setCancelled(true);
        }
    }
}


    // --- Inventory Interaction Events ---

    /**
     * Handles equipping/unequipping items via clicking in the inventory.
     * This is the primary event for most gear changes.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        // Only care about interactions in the player's own inventory.
        if (!event.getInventory().equals(event.getWhoClicked().getInventory())) {
            return;
        }

        InventoryType.SlotType slotType = event.getSlotType();

        // Check if an armor slot or the off-hand slot was changed.
        if (slotType == InventoryType.SlotType.ARMOR || event.getSlot() == 40) { // 40 is the off-hand slot index
            recalculate((Player) event.getWhoClicked());
        }
    }

    /**
     * Handles equipping items by dragging an item stack over armor/off-hand slots.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (!event.getInventory().equals(player.getInventory())) {
            return;
        }

        // Check if any of the slots affected by the drag are equipment slots.
        Set<Integer> affectedSlots = event.getRawSlots();
        for (Integer slot : affectedSlots) {
            // Raw player inventory slots: 36-39 for armor, 40 for off-hand.
            if ((slot >= 36 && slot <= 39) || slot == 40) {
                recalculate(player);
                return; // Recalculate once and exit.
            }
        }
    }

    // --- Held Item Change Events ---

    /**
     * Recalculates stats when the player changes their held item slot (e.g., scrolling).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        // If the new slot is empty, return
        if(event.getPlayer().getInventory().getItem(event.getNewSlot()) == null){
            return;
        }
        recalculate(event.getPlayer());
    }

    /**
     * Recalculates stats when the player swaps their main and off-hand items (default 'F' key).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        recalculate(event.getPlayer());
    }
}