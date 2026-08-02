package org.nakii.valmora.module.item;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.nakii.valmora.module.combat.DamageCalculator;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.combat.DamageType;

import java.util.List;
import java.util.Map;

/**
 * Handles the non-click item ability triggers: {@link AbilityTrigger#ON_KILL},
 * {@link AbilityTrigger#SNEAK} and {@link AbilityTrigger#ON_SHOOT}. ON_HIT is dispatched from
 * the combat pipeline ({@code CombatListener}) where damage is already resolved.
 */
public class AbilityTriggerListener implements Listener {

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        AbilityExecutor.fireHeld(killer, AbilityTrigger.ON_KILL, event.getEntity(), true);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // Fire when the player starts crouching.
        Player player = event.getPlayer();
        // Sneak abilities are usually on armor; check held item plus armor pieces.
        AbilityExecutor.fireHeld(player, AbilityTrigger.SNEAK, null, true);
        fireArmor(player, AbilityTrigger.SNEAK);
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        AbilityExecutor.fireHeld(player, AbilityTrigger.ON_SHOOT, null, true);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileAbilityService.Callback callback =
                ProjectileAbilityService.consume(projectile.getUniqueId());
        if (callback == null) return;

        Player caster = org.bukkit.Bukkit.getPlayer(callback.casterId());
        if (caster == null) return;

        LivingEntity struck = event.getHitEntity() instanceof LivingEntity le ? le : null;
        Location impact = struck != null ? struck.getLocation() : projectile.getLocation();

        // Direct damage to the struck entity, if any was configured.
        if (struck != null && callback.damage() > 0) {
            DamageType type = mapDamageType(callback.damageType());
            DamageResult result = DamageCalculator.calculateDamage(caster, struck, type, callback.damage());
            result.apply();
            org.nakii.valmora.api.ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(result);
        }

        // Nested on-hit mechanics, centred on the impact point.
        List<Map<?, ?>> onHit = callback.onHit();
        if (onHit == null || onHit.isEmpty()) return;
        try {
            List<ConfiguredMechanic> mechanics = MechanicParser.parse(onHit,
                    org.nakii.valmora.api.ValmoraAPI.getInstance().getAbilityManager().getMechanicRegistry());
            for (ConfiguredMechanic mechanic : mechanics) {
                mechanic.executeAt(caster, struck, impact);
            }
        } catch (MechanicParser.UnknownMechanicException ignored) {
            // An unknown nested mechanic is silently skipped; the YAML loader surfaces it.
        }
    }

    private DamageType mapDamageType(String raw) {
        if (raw == null) return DamageType.MAGIC;
        if (raw.equalsIgnoreCase("PHYSICAL")) return DamageType.MELEE;
        try { return DamageType.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException e) { return DamageType.MAGIC; }
    }

    private void fireArmor(Player player, AbilityTrigger trigger) {
        for (org.bukkit.inventory.ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            String itemId = armor.getItemMeta().getPersistentDataContainer()
                    .get(org.nakii.valmora.util.Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
            if (itemId == null) continue;
            org.nakii.valmora.api.ValmoraAPI.getInstance().getItemManager().getItemRegistry().getItem(itemId)
                    .ifPresent(def -> AbilityExecutor.fire(player, def, trigger, (LivingEntity) null, true));
        }
    }
}
