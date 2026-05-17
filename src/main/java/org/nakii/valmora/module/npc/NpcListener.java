package org.nakii.valmora.module.npc;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.util.Keys;

public class NpcListener implements Listener {

    private final NpcManager npcManager;

    public NpcListener(NpcManager npcManager) {
        this.npcManager = npcManager;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String npcId = event.getRightClicked().getPersistentDataContainer()
                .get(Keys.NPC_ID_KEY, PersistentDataType.STRING);
        if (npcId == null) return;
        event.setCancelled(true);
        npcManager.handleRightClick(event.getPlayer(), npcId);
    }

    // Left-click: cancel damage immediately so combat module never sees it, then fire actions
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        String npcId = event.getEntity().getPersistentDataContainer()
                .get(Keys.NPC_ID_KEY, PersistentDataType.STRING);
        if (npcId == null) return;
        event.setCancelled(true);
        npcManager.handleLeftClick(player, npcId);
    }

    // Cancel all non-player damage to NPCs (fire, explosions, etc.)
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        String npcId = event.getEntity().getPersistentDataContainer()
                .get(Keys.NPC_ID_KEY, PersistentDataType.STRING);
        if (npcId == null) return;
        event.setCancelled(true);
    }
}
