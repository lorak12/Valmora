package org.nakii.valmora.module.death;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.flow.BuiltinStepFactory;
import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowSpec;
import org.nakii.valmora.api.pipeline.flow.FlowState;
import org.nakii.valmora.api.pipeline.flow.StepOutcome;
import org.nakii.valmora.api.pipeline.flow.StepParams;
import org.nakii.valmora.module.combat.DeathContextCache;
import org.nakii.valmora.module.zone.ZoneDefinition;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The flows of {@code death_pipeline.yml} (docs/PIPELINE_FLOWS_PLAN.md §5.3) — what used to be
 * {@code DeathListener.onDeath}/{@code onRespawn}. Script-visible as {@code $death.*$}.
 */
public final class DeathFlows {

    private DeathFlows() {}

    public static final String DOMAIN_ID = "death";
    public static final String POINT_PREFIX = "player:";
    public static final String ON_DEATH = POINT_PREFIX + "on_death";
    public static final String ON_RESPAWN = POINT_PREFIX + "on_respawn";

    /** State of one {@code death} flow run. */
    public static final class DeathState implements FlowState {
        final PlayerDeathEvent event;
        final Player player;
        boolean keepInventory;
        boolean keepExperience;

        public DeathState(PlayerDeathEvent event) {
            this.event = event;
            this.player = event.getEntity();
        }

        @Override
        public void publish(ExecutionContext ctx) {
            ctx.set("death:keep_inventory", keepInventory);
            ctx.set("death:keep_experience", keepExperience);
            ZoneDefinition zone = DeathPolicyResolver.deathZone(player);
            ctx.set("death:zone", zone != null ? zone.getId() : null);
        }

        @Override
        public boolean write(String key, Object value) {
            switch (key) {
                case "death.keep_inventory" -> keepInventory = FlowState.asBoolean(value);
                case "death.keep_experience" -> keepExperience = FlowState.asBoolean(value);
                default -> {
                    return false;
                }
            }
            return true;
        }
    }

    /** State of one {@code respawn} flow run. */
    public static final class RespawnState implements FlowState {
        final PlayerRespawnEvent event;
        final Player player;

        public RespawnState(PlayerRespawnEvent event) {
            this.event = event;
            this.player = event.getPlayer();
        }

        @Override
        public void publish(ExecutionContext ctx) {
            ctx.set("death:is_bed_spawn", event.isBedSpawn());
            ctx.set("death:is_anchor_spawn", event.isAnchorSpawn());
            // Pre-flow attachment names, still read by Java hooks at player:on_respawn.
            ctx.set("player:is_bed_spawn", event.isBedSpawn());
            ctx.set("player:is_anchor_spawn", event.isAnchorSpawn());
        }
    }

    /** {@code death}: a player died. */
    public static final FlowSpec<DeathState> DEATH = new FlowSpec<DeathState>(DOMAIN_ID, "death")
            .builtin(BuiltinStepFactory.of("death_message", List.of("broadcast"), params -> {
                BooleanSupplier broadcast = params.bool("broadcast", DeathMessageService::broadcastEnabled);
                return (ctx, s) -> {
                    if (broadcast.getAsBoolean()) {
                        s.event.deathMessage(DeathMessageService.build(s.player));
                    } else {
                        s.event.deathMessage(null);
                        // Still consume the cached context so it doesn't leak into the next message.
                        DeathContextCache.consume(s.player.getUniqueId());
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("resolve_keep_policy", List.of("keep-inventory-default", "keep-experience-default"), params -> {
                BooleanSupplier inventory = params.bool("keep-inventory-default", StepParams.configBool("death.keep-inventory-default", false));
                BooleanSupplier experience = params.bool("keep-experience-default", StepParams.configBool("death.keep-experience-default", false));
                return (ctx, s) -> {
                    DeathPolicyResolver.Policy policy = DeathPolicyResolver.resolve(s.player,
                            inventory.getAsBoolean(), experience.getAsBoolean());
                    s.keepInventory = policy.keepInventory();
                    s.keepExperience = policy.keepExperience();
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("apply_keep_policy", (ctx, s) -> {
                s.event.setKeepInventory(s.keepInventory);
                s.event.setKeepLevel(s.keepExperience);
                if (s.keepInventory) {
                    // setKeepInventory(true) alone still populates getDrops() — the documented
                    // pitfall VANILLA_CONTROL_AUDIT.md §9 calls out.
                    s.event.getDrops().clear();
                }
                if (s.keepExperience) {
                    s.event.setDroppedExp(0);
                }
                return StepOutcome.CONTINUE;
            }))
            .anchor(ON_DEATH, false, "after the death message and keep-inventory policy")
            .writable("death.keep_inventory", "death.keep_experience");

    /** {@code respawn}: a player respawned. */
    public static final FlowSpec<RespawnState> RESPAWN = new FlowSpec<RespawnState>(DOMAIN_ID, "respawn")
            .builtin(BuiltinStepFactory.of("zone_respawn_override", (ctx, s) -> {
                // Falls through to vanilla's own bed/anchor/world-spawn resolution if the death
                // zone declares no override (docs/modules/design/death.md §"Respawn location").
                ZoneDefinition deathZone = DeathPolicyResolver.deathZone(s.player);
                if (deathZone != null) {
                    Location override = DeathPolicyResolver.resolveRespawnOverride(deathZone.getId());
                    if (override != null) s.event.setRespawnLocation(override);
                }
                return StepOutcome.CONTINUE;
            }))
            .anchor(ON_RESPAWN, false, "after the respawn location is resolved");

    public static final FlowDomain DOMAIN = new FlowDomain(DOMAIN_ID, "death_pipeline.yml", POINT_PREFIX,
            List.of(DEATH, RESPAWN), List.of("on_death", "on_respawn"));
}
