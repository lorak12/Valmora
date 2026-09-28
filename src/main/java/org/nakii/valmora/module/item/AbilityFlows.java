package org.nakii.valmora.module.item;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.flow.BuiltinStepFactory;
import org.nakii.valmora.api.pipeline.flow.FlowContext;
import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowSpec;
import org.nakii.valmora.api.pipeline.flow.FlowState;
import org.nakii.valmora.api.pipeline.flow.StepOutcome;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.DebugManager;

import java.util.List;

/**
 * The flow of {@code item_pipeline.yml} (docs/PIPELINE_FLOWS_PLAN.md §5.4) — what used to be
 * {@code AbilityExecutor.fireOne}: target, conditions, cooldown and mana gating, then mechanics.
 */
public final class AbilityFlows {

    private AbilityFlows() {}

    public static final String DOMAIN_ID = "item";
    public static final String POINT_PREFIX = "item:";
    public static final String PRE_ABILITY = POINT_PREFIX + "pre_ability";
    public static final String POST_ABILITY = POINT_PREFIX + "post_ability";

    /**
     * State of one {@code cast} run. Script-visible as {@code $item.ability_id$},
     * {@code $item.ability_trigger$}, {@code $item.mana_cost$}, {@code $item.cooldown$}.
     */
    public static final class CastState implements FlowState {
        final Player player;
        final ValmoraProfile profile;
        final PlayerState playerState;
        final AbilityDefinition ability;
        final AbilityTrigger trigger;
        final boolean silent;
        final FlowContext ctx;
        double manaCost;
        double cooldown;

        CastState(Player player, ValmoraProfile profile, AbilityDefinition ability, AbilityTrigger trigger,
                  boolean silent, FlowContext ctx) {
            this.player = player;
            this.profile = profile;
            this.playerState = profile.getPlayerState();
            this.ability = ability;
            this.trigger = trigger;
            this.silent = silent;
            this.ctx = ctx;
            this.manaCost = ability.getManaCost();
            this.cooldown = ability.getCooldown();
        }

        LivingEntity target() {
            return ctx.getTarget().orElse(null);
        }

        @Override
        public void publish(ExecutionContext c) {
            c.set("item:ability_id", ability.getId());
            c.set("item:ability_trigger", trigger.name());
            c.set("item:mana_cost", manaCost);
            c.set("item:cooldown", cooldown);
        }

        @Override
        public boolean write(String key, Object value) {
            switch (key) {
                case "item.mana_cost" -> manaCost = Math.max(0, FlowState.asDouble(value, manaCost));
                case "item.cooldown" -> cooldown = Math.max(0, FlowState.asDouble(value, cooldown));
                default -> {
                    return false;
                }
            }
            return true;
        }
    }

    private static void tell(CastState s, String message) {
        if (!s.silent && message != null && !message.isEmpty()) {
            ValmoraAPI.getInstance().getUIManager().getActionBar().showTemporary(s.player, message, 10, 2);
        }
    }

    private static void debug(CastState s, String what) {
        DebugManager.log("abilities", "ability '" + s.ability.getId() + "' (trigger=" + s.trigger + ") " + what
                + " for player=" + s.player.getName());
    }

    /** {@code cast}: an item ability's trigger fired. */
    public static final FlowSpec<CastState> CAST = new FlowSpec<CastState>(DOMAIN_ID, "cast")
            .builtin(BuiltinStepFactory.of("resolve_target", List.of("message"), params -> {
                String message = params.string("message", "<red>No target in range!");
                return (ctx, s) -> {
                    if (s.target() == null && s.ability.getTargetRange() > 0) {
                        // instanceof, not a cast: aiming at an item frame, boat or painting used to throw.
                        LivingEntity found = s.player.getTargetEntity((int) s.ability.getTargetRange(), false) instanceof LivingEntity living
                                ? living : null;
                        if (found == null) {
                            tell(s, message);
                            return StepOutcome.STOP;
                        }
                        s.ctx.setTarget(found);
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("ability_conditions", (ctx, s) -> {
                // Pre-compiled at item load time (AbilityDefinition/ItemDefinitionParser).
                if (s.ability.getConditions() != null && !s.ability.getConditions().evaluate(ctx)) {
                    debug(s, "REJECTED by conditions");
                    return StepOutcome.STOP;
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("cooldown_gate", List.of("message"), params -> {
                String message = params.string("message", "<red>Ability on cooldown: %remaining%s");
                return (ctx, s) -> {
                    if (s.profile.getCooldownManager().isOnCooldown(s.ability.getId())) {
                        double remaining = s.profile.getCooldownManager().getRemainingCooldown(s.ability.getId());
                        tell(s, message.replace("%remaining%", String.valueOf(remaining)));
                        debug(s, "REJECTED — on cooldown");
                        return StepOutcome.STOP;
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("mana_gate", List.of("message"), params -> {
                String message = params.string("message", "<aqua>Not enough Mana!");
                return (ctx, s) -> {
                    if (s.manaCost > 0 && s.playerState.getCurrentMana() < s.manaCost) {
                        tell(s, message);
                        debug(s, "REJECTED — not enough mana (have=" + s.playerState.getCurrentMana() + " need=" + s.manaCost + ")");
                        return StepOutcome.STOP;
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("consume_mana", (ctx, s) -> {
                if (s.manaCost > 0) s.playerState.reduceMana(s.manaCost);
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("start_cooldown", (ctx, s) -> {
                if (s.cooldown > 0) s.profile.getCooldownManager().setCooldown(s.ability.getId(), s.cooldown);
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.required("run_mechanics", (ctx, s) -> {
                LivingEntity target = s.target();
                debug(s, "FIRING target=" + (target != null ? target.getName() : "none")
                        + " mechanics=" + s.ability.getMechanics().size());
                for (ConfiguredMechanic mechanic : s.ability.getMechanics()) {
                    mechanic.execute(s.player, target);
                }
                return StepOutcome.CONTINUE;
            }))
            .anchor(PRE_ABILITY, true, "after cooldown/mana are spent, before mechanics — interrupt cancels this firing (costs stay spent)")
            .anchor(POST_ABILITY, false, "after the mechanics ran")
            .writable("item.mana_cost", "item.cooldown");

    public static final FlowDomain DOMAIN = new FlowDomain(DOMAIN_ID, "item_pipeline.yml", POINT_PREFIX,
            List.of(CAST), List.of("pre_ability", "post_ability"));

    /** Runs {@link #CAST} for one ability. */
    static void cast(Player player, ValmoraProfile profile, AbilityDefinition ability, AbilityTrigger trigger,
                     LivingEntity target, boolean silent) {
        FlowContext ctx = new FlowContext(player, target, player.getLocation());
        CastState state = new CastState(player, profile, ability, trigger, silent, ctx);
        var result = CAST.run(ctx, state);
        if (result.stopped() && DebugManager.isEnabled("abilities")) {
            debug(state, "stopped at step '" + result.stoppedAt() + "'");
        }
    }
}
