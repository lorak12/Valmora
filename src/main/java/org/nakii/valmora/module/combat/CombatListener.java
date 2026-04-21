package org.nakii.valmora.module.combat;

import org.bukkit.event.Listener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;

public class CombatListener implements Listener {

    public CombatListener(Valmora plugin) {
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
 
        
        if (!(event.getEntity() instanceof LivingEntity victim)) {
             return;
        }

        if (victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2.0F) {
             event.setCancelled(true);
             return; 
        }

        LivingEntity attacker = null;

        if (event.getDamager() instanceof LivingEntity le) {
            attacker = le;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity le) {
            attacker = le;
        }

        if (attacker != null) {
            event.setDamage(0); 
            
            DamageType damageType = event.getDamageSource().getDamageType().equals(org.bukkit.damage.DamageType.ARROW) || 
                                    event.getDamageSource().getDamageType().equals(org.bukkit.damage.DamageType.MOB_PROJECTILE) ? 
                                    DamageType.PROJECTILE : DamageType.MELEE;
            
            DamageResult damageResult = DamageCalculator.calculateDamage(attacker, victim, damageType);
            damageResult.apply();

            ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(damageResult);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
             return; 
        }

        if (event.getEntity() instanceof LivingEntity victim) {
            if (victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2.0F) {
                 event.setCancelled(true);
                 return; 
            }

            double baseDamage = event.getDamage();
            if (baseDamage <= 0) return;

            event.setDamage(0);

            DamageType customType = mapCauseToType(event.getCause());
            DamageResult damageResult = DamageCalculator.calculateDamage(victim, customType, baseDamage);
            damageResult.apply();

            ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(damageResult);
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
            default -> DamageType.MELEE; 
        };
    }
}
