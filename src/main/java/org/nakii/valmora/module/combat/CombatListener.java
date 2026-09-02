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
import org.nakii.valmora.util.DebugManager;

public class CombatListener implements Listener {

    private static final String PRE_DAMAGE = "combat:pre_damage";
    private static final String POST_CALCULATION = "combat:post_calculation";
    private static final String POST_APPLICATION = "combat:post_application";
    private static final String ON_DMG_DEALT = "combat:on_dmg_dealt";
    private static final String DEBUG_MODULE = "combat";

    public CombatListener(Valmora plugin) {
    }

    private static void debug(String msg) {
        if (DebugManager.isEnabled(DEBUG_MODULE)) {
            org.nakii.valmora.Valmora.getInstance().getLogger().info("[combat-debug] " + msg);
        }
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
             debug("HIT REJECTED (i-frames): victim=" + victim.getName() + " noDamageTicks="
                     + victim.getNoDamageTicks() + " max=" + victim.getMaximumNoDamageTicks());
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
            double rawEventDamage = event.getDamage(); // captured before zeroing — see debug line below
            event.setDamage(0);

            DamageType damageType = event.getDamageSource().getDamageType().equals(org.bukkit.damage.DamageType.ARROW) ||
                                    event.getDamageSource().getDamageType().equals(org.bukkit.damage.DamageType.MOB_PROJECTILE) ?
                                    DamageType.PROJECTILE : DamageType.MELEE;

            debug("HIT START: attacker=" + attacker.getName() + " victim=" + victim.getName()
                    + " damager=" + event.getDamager().getType() + " damageType=" + damageType
                    + " rawEventDamage=" + rawEventDamage + " cause=" + event.getDamageSource().getDamageType());

            // Combat pipeline (docs/COMBAT_PIPELINE_ANALYSIS.md) — only builds a context and runs
            // the bus if at least one stage/hook is registered at any of these points, so a default
            // install (no combat_pipeline.yml, no addon Java hooks) pays nothing for this feature.
            HookBus bus = ValmoraAPI.getInstance().getHookBus();
            boolean pipelineActive = bus != null
                    && bus.hasAnyStages(PRE_DAMAGE, POST_CALCULATION, POST_APPLICATION, ON_DMG_DEALT);
            ExecutionContext pipelineCtx = null;

            if (pipelineActive) {
                debug("pipeline active for this hit — points registered: PRE_DAMAGE=" + bus.hasStages(PRE_DAMAGE)
                        + " POST_CALCULATION=" + bus.hasStages(POST_CALCULATION)
                        + " POST_APPLICATION=" + bus.hasStages(POST_APPLICATION)
                        + " ON_DMG_DEALT=" + bus.hasStages(ON_DMG_DEALT));
                pipelineCtx = new SimpleExecutionContext(attacker, victim, victim.getLocation(), null);
                if (!bus.runPoint(PRE_DAMAGE, pipelineCtx)) {
                    debug("HIT CANCELLED at PRE_DAMAGE by a pipeline stage (interrupt)");
                    return; // a stage called `interrupt` — cancel this hit entirely
                }
            }

            DamageResult damageResult = DamageCalculator.calculateDamage(attacker, victim, damageType, 0.0, pipelineCtx);

            debug("HIT CALCULATED: finalDamage=" + damageResult.getFinalDamage() + " crit=" + damageResult.isCritical()
                    + " immune=" + damageResult.isImmune() + " victimHealthBefore=" + victim.getHealth());

            if (pipelineActive) {
                pipelineCtx.set("dmg:final_damage", damageResult.getFinalDamage());
                pipelineCtx.set("dmg:is_critical", damageResult.isCritical());
                pipelineCtx.set("dmg:damage_type", damageResult.getDamageType().getId());
                pipelineCtx.set("dmg:is_immune", damageResult.isImmune());
                if (!bus.runPoint(POST_CALCULATION, pipelineCtx)) {
                    debug("HIT PREVENTED at POST_CALCULATION by a pipeline stage");
                    return; // the hit was rolled but a stage prevented it from landing
                }
            }

            damageResult.apply();

            debug("HIT APPLIED: victim=" + victim.getName() + " healthAfter=" + victim.getHealth()
                    + " dealt=" + damageResult.getFinalDamage());

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
                org.nakii.valmora.module.item.AbilityExecutor.fireModifiersHeld(
                        attackerPlayer,
                        org.nakii.valmora.module.item.AbilityTrigger.ON_HIT,
                        victim, true);
                debug("fired ON_HIT item + modifier abilities for attacker=" + attackerPlayer.getName());
            }

            // Fire ON_DAMAGE_TAKEN on the victim's held item + armor (docs/IMPLEMENTATION_BACKLOG.md
            // — previously enumerated but never dispatched). Skipped when immune, same as the
            // damage indicator, since nothing actually landed.
            if (!damageResult.isImmune() && victim instanceof org.bukkit.entity.Player victimPlayer) {
                org.nakii.valmora.module.item.AbilityExecutor.fireHeld(
                        victimPlayer, org.nakii.valmora.module.item.AbilityTrigger.ON_DAMAGE_TAKEN, attacker, true);
                org.nakii.valmora.module.item.AbilityExecutor.fireModifiersHeld(
                        victimPlayer, org.nakii.valmora.module.item.AbilityTrigger.ON_DAMAGE_TAKEN, attacker, true);
                fireArmorOnDamageTaken(victimPlayer, attacker);
                debug("fired ON_DAMAGE_TAKEN item + armor abilities for victim=" + victimPlayer.getName());
            } else if (damageResult.isImmune()) {
                debug("skipped ON_DAMAGE_TAKEN dispatch — hit was immune");
            }

            // Boss ability triggers
            var bossController = ValmoraAPI.getInstance().getMobManager().getBossController();
            if (bossController.isTracked(attacker.getUniqueId())) {
                debug("boss controller onAttack fired for attacker=" + attacker.getName());
                bossController.onAttack(attacker, victim);
            }
            if (bossController.isTracked(victim.getUniqueId())) {
                debug("boss controller onDamaged fired for victim=" + victim.getName());
                bossController.onDamaged(victim, attacker);
            }

            if (pipelineActive) {
                bus.runPoint(ON_DMG_DEALT, pipelineCtx);
            }

            debug("HIT END: attacker=" + attacker.getName() + " victim=" + victim.getName());
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

            debug("ENV HIT: victim=" + victim.getName() + " cause=" + event.getCause() + " mappedType=" + customType
                    + " rawDamage=" + baseDamage + " finalDamage=" + damageResult.getFinalDamage()
                    + " immune=" + damageResult.isImmune());

            // Fully fire/lava-immune mobs should not keep burning
            if (damageResult.isImmune() && (customType == DamageType.FIRE || customType == DamageType.LAVA)) {
                victim.setFireTicks(0);
            }

            ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(damageResult);

            if (!damageResult.isImmune() && victim instanceof org.bukkit.entity.Player victimPlayer) {
                org.nakii.valmora.module.item.AbilityExecutor.fireHeld(
                        victimPlayer, org.nakii.valmora.module.item.AbilityTrigger.ON_DAMAGE_TAKEN, null, true);
                org.nakii.valmora.module.item.AbilityExecutor.fireModifiersHeld(
                        victimPlayer, org.nakii.valmora.module.item.AbilityTrigger.ON_DAMAGE_TAKEN, null, true);
                fireArmorOnDamageTaken(victimPlayer, null);
            }
        }
    }

    /** Fires ON_DAMAGE_TAKEN on every armor piece — mirrors {@code AbilityTriggerListener.fireArmor}, duplicated here since it's a different listener class with no shared base. */
    private void fireArmorOnDamageTaken(org.bukkit.entity.Player player, LivingEntity attacker) {
        for (org.bukkit.inventory.ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            String itemId = armor.getItemMeta().getPersistentDataContainer()
                    .get(org.nakii.valmora.util.Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
            if (itemId != null) {
                ValmoraAPI.getInstance().getItemManager().getItemRegistry().getItem(itemId)
                        .ifPresent(def -> org.nakii.valmora.module.item.AbilityExecutor.fire(
                                player, def, org.nakii.valmora.module.item.AbilityTrigger.ON_DAMAGE_TAKEN, attacker, true));
            }
            org.nakii.valmora.module.item.AbilityExecutor.fireModifiersForItem(
                    player, armor, org.nakii.valmora.module.item.AbilityTrigger.ON_DAMAGE_TAKEN, attacker, true);
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
