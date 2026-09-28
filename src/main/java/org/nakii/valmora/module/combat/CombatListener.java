package org.nakii.valmora.module.combat;

import org.bukkit.event.Listener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

/**
 * Replaces vanilla damage with Valmora's by running {@code combat_pipeline.yml}'s flows
 * ({@link CombatFlows}): {@code entity_hit} for entity-caused damage, {@code environment_damage}
 * for everything else.
 */
public class CombatListener implements Listener {

    public CombatListener(Valmora plugin) {
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Evicts the per-UUID $player.last_damage$ tracker entry — otherwise it grows unbounded
        // across the server's lifetime as players come and go.
        org.nakii.valmora.module.item.CombatTracker.clear(event.getPlayer().getUniqueId());
        DeathContextCache.clear(event.getPlayer().getUniqueId());
        AttackCooldownService.clear(event.getPlayer().getUniqueId());
        KnockbackModifierTracker.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        // The attacker isn't known until resolve_attacker runs; caster/target are fixed per context,
        // so the context is the attacker's view once a step needs it — see CombatFlows.
        HitState state = new HitState(event, victim);
        CombatFlows.ENTITY_HIT.run(new HitContext(state, victim), state);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        HitState state = new HitState(event, victim);
        CombatFlows.ENVIRONMENT_DAMAGE.run(new SimpleExecutionContext(victim, null, victim.getLocation(), null), state);
    }

    /**
     * The {@code entity_hit} context: caster = the attacker (resolved by a step), target = the
     * victim — the same roles the pre-flow {@code combat:*} stages saw.
     */
    static final class HitContext extends SimpleExecutionContext {
        private final HitState state;

        HitContext(HitState state, LivingEntity victim) {
            super(null, victim, victim.getLocation(), null);
            this.state = state;
        }

        @Override
        public LivingEntity getCaster() {
            return state.attacker;
        }
    }
}
