package org.nakii.valmora.module.item;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.module.combat.DamageCalculator;
import org.nakii.valmora.module.combat.DamageResult;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.util.Keys;

import java.util.List;
import java.util.Map;

/**
 * Handles the non-click item ability triggers: {@link AbilityTrigger#ON_KILL},
 * {@link AbilityTrigger#SNEAK}, {@link AbilityTrigger#ON_SHOOT},
 * {@link AbilityTrigger#ON_DAMAGE_TAKEN}, {@link AbilityTrigger#ON_TELEPORT},
 * {@link AbilityTrigger#EQUIP} and {@link AbilityTrigger#UNEQUIP}. ON_HIT is dispatched from
 * the combat pipeline ({@code CombatListener}) where damage is already resolved.
 */
public class AbilityTriggerListener implements Listener {

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        AbilityExecutor.fireHeld(killer, AbilityTrigger.ON_KILL, event.getEntity(), true);
        AbilityExecutor.fireModifiersHeld(killer, AbilityTrigger.ON_KILL, event.getEntity(), true);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // Fire when the player starts crouching.
        Player player = event.getPlayer();
        // Sneak abilities are usually on armor; check held item plus armor pieces.
        AbilityExecutor.fireHeld(player, AbilityTrigger.SNEAK, null, true);
        AbilityExecutor.fireModifiersHeld(player, AbilityTrigger.SNEAK, null, true);
        fireArmor(player, AbilityTrigger.SNEAK);
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        AbilityExecutor.fireHeld(player, AbilityTrigger.ON_SHOOT, null, true);
        AbilityExecutor.fireModifiersHeld(player, AbilityTrigger.ON_SHOOT, null, true);
    }

    /** Fires on any completed teleport (warps, ender pearls, /tp, plugin teleports) — non-gating, always after the fact. */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        AbilityExecutor.fireHeld(player, AbilityTrigger.ON_TELEPORT, null, true);
        AbilityExecutor.fireModifiersHeld(player, AbilityTrigger.ON_TELEPORT, null, true);
        fireArmor(player, AbilityTrigger.ON_TELEPORT);
    }

    /**
     * Fires EQUIP/UNEQUIP on any armor-slot change (any cause — click, shift-click, dispenser,
     * command, etc.) via Paper's {@link PlayerArmorChangeEvent}, rather than trying to catch every
     * individual inventory-click case ourselves.
     */
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        fireItem(player, event.getOldItem(), AbilityTrigger.UNEQUIP);
        fireItem(player, event.getNewItem(), AbilityTrigger.EQUIP);
    }

    private void fireItem(Player player, ItemStack item, AbilityTrigger trigger) {
        if (item == null || !item.hasItemMeta()) return;
        String itemId = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId != null) {
            org.nakii.valmora.api.ValmoraAPI.getInstance().getItemManager().getItemRegistry().getItem(itemId)
                    .ifPresent(def -> AbilityExecutor.fire(player, def, trigger, null, true));
        }
        AbilityExecutor.fireModifiersForItem(player, item, trigger, null, true);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        // A piercing projectile fires this event once per entity it passes through, then again
        // when it finally hits a block — only release the tracking on that terminal hit (or a
        // non-piercing projectile's very first hit), otherwise every earlier entity hit would
        // clear it and every later hit along the same path would be silently ignored.
        boolean terminal = event.getHitBlock() != null;
        ProjectileAbilityService.Callback callback = terminal
                ? ProjectileAbilityService.consume(projectile.getUniqueId())
                : ProjectileAbilityService.peek(projectile.getUniqueId());
        if (callback == null) return;
        if (!terminal && !callback.pierce()) {
            ProjectileAbilityService.consume(projectile.getUniqueId());
        }

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
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            fireItem(player, armor, trigger);
        }
    }
}
