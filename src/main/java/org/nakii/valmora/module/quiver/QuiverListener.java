package org.nakii.valmora.module.quiver;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;

public class QuiverListener implements Listener {

    private final QuiverModule module;

    public QuiverListener(QuiverModule module) {
        this.module = module;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof QuiverInventoryHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        module.saveQuiver(player, event.getInventory());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof QuiverInventoryHolder)) return;
        // Only allow arrow-type items in the quiver — reject anything else being placed in
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && !module.isArrow(cursor)) {
            event.setCancelled(true);
        }
    }

    // Bows/crossbows always draw from the normal inventory first (vanilla behavior, untouched).
    // Only when the player has no arrows anywhere in their inventory do we top it up with a
    // single arrow from the quiver here — before vanilla's own ammo check runs — so the rest
    // of the draw/fire/consume flow proceeds exactly as it would for an inventory arrow.
    @EventHandler(ignoreCancelled = true)
    public void onBowUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        Material type = item.getType();
        if (type != Material.BOW && type != Material.CROSSBOW) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // A loaded crossbow already consumed its ammo when it was loaded, not on this fire click.
        if (type == Material.CROSSBOW
                && item.getItemMeta() instanceof CrossbowMeta crossbowMeta
                && crossbowMeta.hasChargedProjectiles()) {
            return;
        }

        if (module.hasArrowInInventory(player)) return;
        module.loanArrowFromQuiver(player);
    }
}
