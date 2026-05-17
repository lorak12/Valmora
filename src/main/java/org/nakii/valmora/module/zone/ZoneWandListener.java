package org.nakii.valmora.module.zone;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

public class ZoneWandListener implements Listener {

    private final ZoneManager zoneManager;

    public ZoneWandListener(ZoneManager zoneManager) {
        this.zoneManager = zoneManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        Boolean isWand = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ZONE_WAND_KEY, PersistentDataType.BOOLEAN);
        if (!Boolean.TRUE.equals(isWand)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        event.setCancelled(true);

        int x = block.getX(), y = block.getY(), z = block.getZ();
        String coords = x + ", " + y + ", " + z;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            zoneManager.setPos1(player, x, y, z);
            player.sendMessage(Formatter.format("<dark_gray>[<gold>Zone<dark_gray>] <gray>Pos<white>1 <gray>set to <white>" + coords));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            zoneManager.setPos2(player, x, y, z);
            player.sendMessage(Formatter.format("<dark_gray>[<gold>Zone<dark_gray>] <gray>Pos<white>2 <gray>set to <white>" + coords));
        }
    }
}
