package org.nakii.valmora.module.death;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.module.zone.ZoneDefinition;

/**
 * Owns {@link PlayerDeathEvent}/{@link PlayerRespawnEvent} (VANILLA_CONTROL_AUDIT.md §9) —
 * previously unowned by any module (see docs/modules/design/INTEGRATION.md's event-ownership
 * table). Builds the custom death message, resolves keepInventory/keepExperience via
 * {@link DeathPolicyResolver}, applies an optional zone-declared respawn-location override, and
 * fires the {@code player:on_death}/{@code player:on_respawn} HookBus points.
 *
 * <p>Runs at the default (NORMAL) priority — before {@code AbilityTriggerListener}'s ON_DEATH
 * dispatch ({@code HIGH}) and {@code EconomyListener}'s purse-loss penalty ({@code MONITOR}), so
 * both observe the already-resolved keepInventory/keepExperience policy. {@code HudItemListener}'s
 * HUD-item drop-stripping and {@code QuestListener}'s DIE trigger are unaffected by ordering — both
 * are independent of this policy.
 */
public class DeathListener implements Listener {

    private final Valmora plugin;

    public DeathListener(Valmora plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        event.deathMessage(DeathMessageService.broadcastEnabled() ? DeathMessageService.build(player) : null);
        // Still consume the cached context even when broadcast is disabled, so it doesn't leak
        // into the next unrelated hit's message.
        if (!DeathMessageService.broadcastEnabled()) {
            org.nakii.valmora.module.combat.DeathContextCache.consume(player.getUniqueId());
        }

        DeathPolicyResolver.Policy policy = DeathPolicyResolver.resolve(player);
        event.setKeepInventory(policy.keepInventory());
        event.setKeepLevel(policy.keepExperience());
        if (policy.keepInventory()) {
            // setKeepInventory(true) alone still populates getDrops() with the death drops — the
            // documented pitfall this audit item specifically calls out (VANILLA_CONTROL_AUDIT.md §9).
            event.getDrops().clear();
        }
        if (policy.keepExperience()) {
            event.setDroppedExp(0);
        }

        HookBus bus = ValmoraAPI.getInstance().getHookBus();
        if (bus != null && bus.hasStages("player:on_death")) {
            var ctx = new SimpleExecutionContext(player, player.getLocation(), null);
            bus.runPoint("player:on_death", ctx);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Zone-declared custom respawn override (docs/modules/design/death.md §"Respawn location")
        // — falls through to vanilla's own already-correct bed/anchor/world-spawn resolution
        // (event.getRespawnLocation() is left untouched) if the death zone declares none.
        ZoneDefinition deathZone = DeathPolicyResolver.deathZone(player);
        if (deathZone != null) {
            Location override = DeathPolicyResolver.resolveRespawnOverride(deathZone.getId());
            if (override != null) {
                event.setRespawnLocation(override);
            }
        }

        HookBus bus = ValmoraAPI.getInstance().getHookBus();
        if (bus != null && bus.hasStages("player:on_respawn")) {
            var ctx = new SimpleExecutionContext(player, player.getLocation(), null);
            ctx.set("player:is_bed_spawn", event.isBedSpawn());
            ctx.set("player:is_anchor_spawn", event.isAnchorSpawn());
            bus.runPoint("player:on_respawn", ctx);
        }
    }
}
