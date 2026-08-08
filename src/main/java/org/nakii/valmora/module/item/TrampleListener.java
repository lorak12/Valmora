package org.nakii.valmora.module.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.util.Keys;

/**
 * Cancels crop-trample farmland-damage (jumping/landing on farmland turns it to dirt) when the
 * player's boots carry a {@code CANCEL_TRAMPLE} mechanic on any ability — e.g. Rancher's Boots'
 * "Farmer's Grace" (see docs/IMPLEMENTATION_BACKLOG.md, item-mechanic-engine — CANCEL_TRAMPLE).
 */
public class TrampleListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPhysicalInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.FARMLAND) return;

        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        if (bootsGrantTrampleImmunity(boots)) {
            event.setCancelled(true);
        }
    }

    private boolean bootsGrantTrampleImmunity(ItemStack boots) {
        if (boots == null || !boots.hasItemMeta()) return false;
        String itemId = boots.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) return false;

        return ValmoraAPI.getInstance().getItemManager().getItemRegistry().getItem(itemId)
                .map(this::hasCancelTrampleMechanic)
                .orElse(false);
    }

    private boolean hasCancelTrampleMechanic(ItemDefinition def) {
        if (def.getAbilities() == null) return false;
        for (AbilityDefinition ability : def.getAbilities().values()) {
            for (ConfiguredMechanic mechanic : ability.getMechanics()) {
                if ("CANCEL_TRAMPLE".equals(mechanic.getMechanic().getId())) return true;
            }
        }
        return false;
    }
}
