package org.nakii.valmora.module.death;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.combat.DeathContextCache;

/**
 * Owns {@link PlayerDeathEvent}/{@link PlayerRespawnEvent} (VANILLA_CONTROL_AUDIT.md §9) —
 * previously unowned by any module (see docs/modules/design/INTEGRATION.md's event-ownership
 * table). Runs {@code death_pipeline.yml}'s {@code death} and {@code respawn} flows
 * ({@link DeathFlows}): the custom death message, keepInventory/keepExperience via
 * {@link DeathPolicyResolver}, the optional zone-declared respawn-location override, and the
 * {@code player:on_death}/{@code player:on_respawn} anchors.
 *
 * <p>Runs at the default (NORMAL) priority — before {@code AbilityTriggerListener}'s ON_DEATH
 * dispatch ({@code HIGH}) and {@code EconomyListener}'s purse-loss penalty ({@code MONITOR}), so
 * both observe the already-resolved keepInventory/keepExperience policy. {@code HudItemListener}'s
 * HUD-item drop-stripping and {@code QuestListener}'s DIE trigger are unaffected by ordering — both
 * are independent of this policy.
 */
public class DeathListener implements Listener {

    public DeathListener(Valmora plugin) {
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DeathFlows.DEATH.run(new SimpleExecutionContext(player, player.getLocation(), null), new DeathFlows.DeathState(event));
        // Whatever the flow did with the message, this death's cached attacker/weapon context must
        // not leak into the next, unrelated death message.
        DeathContextCache.consume(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DeathFlows.RESPAWN.run(new SimpleExecutionContext(player, player.getLocation(), null), new DeathFlows.RespawnState(event));
    }
}
