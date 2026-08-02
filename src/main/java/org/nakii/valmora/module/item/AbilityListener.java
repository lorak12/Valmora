package org.nakii.valmora.module.item;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.util.Keys;

import java.util.Optional;

public class AbilityListener implements Listener {

    private final ValmoraAPI api;

    public AbilityListener(ValmoraAPI api) {
        this.api = api;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        AbilityTrigger inputTrigger;
        Action action = event.getAction();
        
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            inputTrigger = AbilityTrigger.RIGHT_CLICK;
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            inputTrigger = AbilityTrigger.LEFT_CLICK;
        } else {
            return;
        }

        String itemId = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) return;

        Optional<ItemDefinition> defOpt = api.getItemManager().getItemRegistry().getItem(itemId);
        if (defOpt.isEmpty()) return;

        Player player = event.getPlayer();
        AbilityExecutor.fire(player, defOpt.get(), inputTrigger, null, false);
    }
}
