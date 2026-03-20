package org.nakii.valmora.combat;

import org.bukkit.event.Listener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.nakii.valmora.Valmora;

public class CombatListener implements Listener {

    private final Valmora plugin;

    public CombatListener(Valmora plugin) {
        this.plugin = plugin;
    }

    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) return; // Player getting hurt is out-of-scope for now
        
        if (!(event.getEntity() instanceof LivingEntity victim)) {
             return;
        }

        // Vanilla invulnerability frame bypass bug fix: Since we cancel damages to 0, vanilla always thinks 1 is a new hit
        // Therefore, we must manually manage NoDamageTicks BEFORE we calculate or process any new hit
        if (victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2.0F) {
             event.setCancelled(true);
             return; // Still immune to taking new damage
        }

        Player attacker = null;

        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null) {
            event.setDamage(0); // We will manage vanilla damage ourself
            
            DamageType damageType = event.getDamageSource().getDamageType().equals(org.bukkit.damage.DamageType.ARROW) || event.getDamageSource().getDamageType().equals(org.bukkit.damage.DamageType.MOB_PROJECTILE) ? DamageType.PROJECTILE : DamageType.MELEE;
            DamageResult damageResult = DamageCalculator.calculateDamage(attacker, victim, damageType);
            
            DamageApplier damageApplier = new DamageApplier(damageResult, plugin);
            damageApplier.applyDamage();

            Valmora.getInstance().getDamageIndicatorManager().spawnIndicator(damageResult);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // If it's a direct entity hit, we already handle it in onEntityDamageByEntity.
        if (event instanceof EntityDamageByEntityEvent) {
             return; 
        }

        if (event.getEntity() instanceof LivingEntity victim) {

            if (victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2.0F) {
                 event.setCancelled(true);
                 return; // Still immune to taking new damage from environmental
            }

            // Grab the vanilla base damage before we set it to zero
            double baseDamage = event.getDamage();
            if (baseDamage <= 0) return;

            // Zero out vanilla to apply custom calculation
            event.setDamage(0);

            DamageType customType = mapCauseToType(event.getCause());
            DamageResult damageResult = DamageCalculator.calculateDamage(victim, customType, baseDamage);

            DamageApplier damageApplier = new DamageApplier(damageResult, plugin);
            damageApplier.applyDamage();

            Valmora.getInstance().getDamageIndicatorManager().spawnIndicator(damageResult);
        }
    }

    private DamageType mapCauseToType(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FALL -> DamageType.FALL;
            case FIRE, FIRE_TICK -> DamageType.FIRE;
            case LAVA -> DamageType.LAVA;
            case DROWNING -> DamageType.DROWNING;
            case MAGIC -> DamageType.MAGIC;
            case POISON -> DamageType.POISON;
            case VOID -> DamageType.VOID;
            case WITHER -> DamageType.WITHER;
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> DamageType.EXPLOSION;
            case PROJECTILE -> DamageType.PROJECTILE;
            default -> DamageType.MELEE; // Fallback
        };
    }
}
