package org.nakii.valmora.module.combat;

import org.bukkit.event.Listener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;

public class CombatListener implements Listener {

    private static final String PRE_DAMAGE = "combat:pre_damage";
    private static final String POST_CALCULATION = "combat:post_calculation";
    private static final String POST_APPLICATION = "combat:post_application";
    private static final String ON_DMG_DEALT = "combat:on_dmg_dealt";

    public CombatListener(Valmora plugin) {
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Evicts the per-UUID $player.last_damage$ tracker entry — otherwise it grows unbounded
        // across the server's lifetime as players come and go.
        org.nakii.valmora.module.item.CombatTracker.clear(event.getPlayer().getUniqueId());
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

            // Combat pipeline (docs/COMBAT_PIPELINE_ANALYSIS.md) — only builds a context and runs
            // the bus if at least one stage/hook is registered at any of these points, so a default
            // install (no combat_pipeline.yml, no addon Java hooks) pays nothing for this feature.
            HookBus bus = ValmoraAPI.getInstance().getHookBus();
            boolean pipelineActive = bus != null
                    && bus.hasAnyStages(PRE_DAMAGE, POST_CALCULATION, POST_APPLICATION, ON_DMG_DEALT);
            ExecutionContext pipelineCtx = null;

            if (pipelineActive) {
                pipelineCtx = new SimpleExecutionContext(attacker, victim, victim.getLocation(), null);
                if (!bus.runPoint(PRE_DAMAGE, pipelineCtx)) {
                    return; // a stage called `interrupt` — cancel this hit entirely
                }
            }

            DamageResult damageResult = DamageCalculator.calculateDamage(attacker, victim, damageType, 0.0, pipelineCtx);

            if (pipelineActive) {
                pipelineCtx.set("dmg:final_damage", damageResult.getFinalDamage());
                pipelineCtx.set("dmg:is_critical", damageResult.isCritical());
                pipelineCtx.set("dmg:damage_type", damageResult.getDamageType().getId());
                pipelineCtx.set("dmg:is_immune", damageResult.isImmune());
                if (!bus.runPoint(POST_CALCULATION, pipelineCtx)) {
                    return; // the hit was rolled but a stage prevented it from landing
                }
            }

            damageResult.apply();

            ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(damageResult);

            if (pipelineActive) {
                bus.runPoint(POST_APPLICATION, pipelineCtx);
            }

            // Record the hit and fire any ON_HIT item abilities on the attacker's weapon.
            if (attacker instanceof org.bukkit.entity.Player attackerPlayer) {
                org.nakii.valmora.module.item.CombatTracker
                        .recordDamageDealt(attackerPlayer.getUniqueId(), damageResult.getFinalDamage());
                org.nakii.valmora.module.item.AbilityExecutor.fireHeld(
                        attackerPlayer,
                        org.nakii.valmora.module.item.AbilityTrigger.ON_HIT,
                        victim, true);
            }

            // Boss ability triggers
            var bossController = ValmoraAPI.getInstance().getMobManager().getBossController();
            if (bossController.isTracked(attacker.getUniqueId())) {
                bossController.onAttack(attacker, victim);
            }
            if (bossController.isTracked(victim.getUniqueId())) {
                bossController.onDamaged(victim, attacker);
            }

            if (pipelineActive) {
                bus.runPoint(ON_DMG_DEALT, pipelineCtx);
            }
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

            // Fully fire/lava-immune mobs should not keep burning
            if (damageResult.isImmune() && (customType == DamageType.FIRE || customType == DamageType.LAVA)) {
                victim.setFireTicks(0);
            }

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
            case SUICIDE -> DamageType.SUICIDE;
            case CONTACT -> DamageType.CONTACT;
            case STARVATION -> DamageType.STARVATION;
            case DRAGON_BREATH -> DamageType.DRAGON_BREATH;
            case SONIC_BOOM -> DamageType.SONIC_BOOM;
            case WORLD_BORDER -> DamageType.OUTSIDE_BORDER;
            default -> DamageType.MELEE;
        };
    }
}
