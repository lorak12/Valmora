package org.nakii.valmora.module.item;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
        // VANILLA_CONTROL_AUDIT.md §12: vanilla fires this event once per hand on a single physical
        // click, so main-hand and off-hand items each get their own independent pass here instead of
        // the off-hand item being unconditionally ignored (the prior `!= HAND` early-return).
        ItemStack item = event.getHand() == EquipmentSlot.HAND ? event.getItem()
                : event.getHand() == EquipmentSlot.OFF_HAND ? event.getPlayer().getInventory().getItemInOffHand()
                : null;
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

    /**
     * VANILLA_CONTROL_AUDIT.md §12 critical gap: no {@code PlayerInteractEntityEvent} handler
     * existed anywhere, so no item ability could ever react to being used on an entity.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getHand() == EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (item == null || !item.hasItemMeta()) return;

        String itemId = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) return;

        Optional<ItemDefinition> defOpt = api.getItemManager().getItemRegistry().getItem(itemId);
        if (defOpt.isEmpty()) return;

        LivingEntity target = event.getRightClicked() instanceof LivingEntity le ? le : null;
        AbilityExecutor.fire(event.getPlayer(), defOpt.get(), AbilityTrigger.ON_INTERACT_ENTITY, target, false);
    }
}
