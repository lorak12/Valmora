package org.nakii.valmora.module.item;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraProfile;
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

        ItemDefinition definition = defOpt.get();
        if (definition.getAbilities() == null || definition.getAbilities().isEmpty()) return;

        Player player = event.getPlayer();
        ValmoraProfile profile = api.getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();
        if (profile == null) return;

        PlayerState state = profile.getPlayerState();

        for (AbilityDefinition ability : definition.getAbilities().values()) {
            
            if (ability.getTrigger() != inputTrigger) {
                continue;
            }

            
            LivingEntity target = null;
            if (ability.getTargetRange() > 0) {
                target = (LivingEntity) player.getTargetEntity((int) ability.getTargetRange(), false);
                if (target == null) {
                    api.getUIManager().getActionBar().showTemporary(player, "<red>No target in range!", 40);
                    continue; 
                }
            }

            if (profile.getCooldownManager().isOnCooldown(ability.getId())) {
                double remaining = profile.getCooldownManager().getRemainingCooldown(ability.getId());
                api.getUIManager().getActionBar().showTemporary(player, "<red>Ability on cooldown: " + remaining + "s", 40);
                continue;
            }

            if (ability.getManaCost() > 0) {
                if (state.getCurrentMana() < ability.getManaCost()) {
                    api.getUIManager().getActionBar().showTemporary(player, "<aqua>Not enough Mana!", 40);
                    continue;
                }
                state.reduceMana(ability.getManaCost());
            }

            if (ability.getCooldown() > 0) {
                profile.getCooldownManager().setCooldown(ability.getId(), ability.getCooldown());
            }

            for (ConfiguredMechanic mechanic : ability.getMechanics()) {
                mechanic.execute(player, target);
            }
        }
    }
}
