package org.nakii.valmora.module.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.skill.SkillXpGainEvent;
import org.nakii.valmora.util.Keys;

public class PetListener implements Listener {

    private final PetModule module;

    public PetListener(PetModule module) {
        this.module = module;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only fire on main hand right-click
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) return;

        String petId = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.PET_ID_KEY, PersistentDataType.STRING);
        if (petId == null) return;

        event.setCancelled(true);
        module.toggleSummon(player, player.getInventory().getHeldItemSlot());
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player player = event.getEntity().getKiller();
        if (!module.hasPetActive(player)) return;

        // Grant pet XP on kill
        module.gainPetXp(player, 10.0);

        // Fire ON_KILL abilities
        PetDefinition def = module.getActivePetDefinition(player);
        if (def == null) return;
        var ctx = new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());
        for (PetAbilityDefinition ability : def.getAbilities()) {
            if (ability.getTrigger() == PetAbilityTrigger.ON_KILL) {
                ability.getEvents().execute(ctx);
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!module.hasPetActive(player)) return;

        PetDefinition def = module.getActivePetDefinition(player);
        if (def == null) return;
        var ctx = new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());
        for (PetAbilityDefinition ability : def.getAbilities()) {
            if (ability.getTrigger() == PetAbilityTrigger.ON_HIT) {
                ability.getEvents().execute(ctx);
            }
        }
    }

    @EventHandler
    public void onDefend(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!module.hasPetActive(player)) return;

        PetDefinition def = module.getActivePetDefinition(player);
        if (def == null) return;
        var ctx = new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());
        for (PetAbilityDefinition ability : def.getAbilities()) {
            if (ability.getTrigger() == PetAbilityTrigger.ON_DEFEND) {
                ability.getEvents().execute(ctx);
            }
        }
    }

    @EventHandler
    public void onSkillXp(SkillXpGainEvent event) {
        Player player = event.getPlayer();
        if (!module.hasPetActive(player)) return;
        // Pet gains a fraction of skill XP earned
        module.gainPetXp(player, event.getXp() * 0.1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (module.hasPetActive(player)) {
            var entity = module.getActivePetEntities().remove(player.getUniqueId());
            if (entity != null && entity.isValid()) entity.remove();
            // Don't call unsummon() — that sends a message to the offline player
        }
    }
}
