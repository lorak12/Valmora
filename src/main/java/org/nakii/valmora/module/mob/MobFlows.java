package org.nakii.valmora.module.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.flow.BuiltinStepFactory;
import org.nakii.valmora.api.pipeline.flow.FlowContext;
import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowSpec;
import org.nakii.valmora.api.pipeline.flow.FlowState;
import org.nakii.valmora.api.pipeline.flow.NumberParam;
import org.nakii.valmora.api.pipeline.flow.StepOutcome;
import org.nakii.valmora.api.pipeline.flow.StepParams;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.mob.ability.MobAbility;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.DebugManager;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.function.Supplier;

/**
 * The flows of {@code mob_pipeline.yml} (docs/PIPELINE_FLOWS_PLAN.md §5.5) — what used to be
 * {@code BossController.fire} (boss abilities) and {@code MobDeathListener.onMobDeath} (custom mob
 * death rewards).
 */
public final class MobFlows {

    private MobFlows() {}

    public static final String DOMAIN_ID = "mob";
    public static final String POINT_PREFIX = "mob:";
    public static final String PRE_ABILITY = POINT_PREFIX + "pre_ability";
    public static final String POST_ABILITY = POINT_PREFIX + "post_ability";
    /** Kept under its original name: stages for it still live in combat_pipeline.yml. */
    public static final String ON_DEATH = "combat:on_death";

    // ------------------------------------------------------------------ cast

    /**
     * State of one boss {@code cast} run. Script-visible as {@code $mob.ability_id$},
     * {@code $mob.ability_trigger$}, {@code $mob.id$}, {@code $mob.level$}, {@code $mob.health$},
     * {@code $mob.max_health$}, {@code $mob.health_percent$}; {@code $target.*$} is the ability's
     * target (usually a player), not the boss.
     */
    public static final class CastState implements FlowState {
        final BossController controller;
        final BossController.BossInstance instance;
        final MobAbility ability;
        final FlowContext ctx;
        /** Set by {@code cooldown_gate} — the firing never happened, so timers mustn't count it. */
        boolean onCooldown;

        CastState(BossController controller, BossController.BossInstance instance, MobAbility ability, FlowContext ctx) {
            this.controller = controller;
            this.instance = instance;
            this.ability = ability;
            this.ctx = ctx;
        }

        @Override
        public void publish(ExecutionContext c) {
            c.set("mob:ability_id", ability.getId());
            c.set("mob:ability_trigger", ability.getTrigger().name());
            c.set("mob:id", instance.definition.getId());
            c.set("mob:level", instance.definition.getLevel());
            c.set("mob:health", instance.entity.getHealth());
            c.set("mob:max_health", controller.maxHealth(instance.entity));
            c.set("mob:health_percent", controller.healthPercent(instance.entity));
        }
    }

    /** {@code cast}: a boss ability's trigger/timer fired. */
    public static final FlowSpec<CastState> CAST = new FlowSpec<CastState>(DOMAIN_ID, "cast")
            .builtin(BuiltinStepFactory.required("cooldown_gate", (ctx, s) -> {
                long now = System.currentTimeMillis();
                Long expiry = s.instance.cooldownExpiry.get(s.ability.getId());
                if (expiry != null && now < expiry) {
                    s.onCooldown = true;
                    return StepOutcome.STOP;
                }
                if (s.ability.getCooldownSeconds() > 0) {
                    s.instance.cooldownExpiry.put(s.ability.getId(), now + (long) (s.ability.getCooldownSeconds() * 1000));
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("resolve_target", (ctx, s) -> {
                if (s.ctx.getTarget().isEmpty() && s.ability.getTargetRange() > 0) {
                    s.ctx.setTarget(s.controller.findNearestPlayer(s.instance.entity, s.ability.getTargetRange()));
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("announce", List.of("radius"), params -> {
                NumberParam radius = params.number("radius", (ctx, s) -> ((CastState) s).controller.announceRadius());
                return (ctx, s) -> {
                    String announce = s.ability.getAnnounce();
                    if (announce != null && !announce.isEmpty()) {
                        for (Player p : s.controller.nearbyPlayers(s.instance.entity, radius.get(ctx, s))) {
                            p.sendMessage(Formatter.format(announce));
                        }
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.required("run_mechanics", (ctx, s) -> {
                LivingEntity target = s.ctx.getTarget().orElse(null);
                for (ConfiguredMechanic mechanic : s.ability.getMechanics()) {
                    mechanic.execute(s.instance.entity, target);
                }
                return StepOutcome.CONTINUE;
            }))
            .anchor(PRE_ABILITY, true, "after the cooldown is spent and the ability announced — interrupt cancels this firing")
            .anchor(POST_ABILITY, false, "after the mechanics ran");

    // ------------------------------------------------------------------ death

    /**
     * State of one mob {@code death} run. Script-visible as {@code $mob.id$}, {@code $mob.level$},
     * {@code $mob.has_killer$}, {@code $mob.luck$}, {@code $mob.xp$}, {@code $mob.gold$}.
     */
    public static final class DeathState implements FlowState {
        final Valmora plugin;
        final EntityDeathEvent event;
        final LivingEntity entity;
        final String mobId;
        final MobDefinition definition;
        final Player killer;
        final ValmoraProfile killerProfile;
        double luck;
        double xp;
        double gold;

        DeathState(Valmora plugin, EntityDeathEvent event, String mobId, MobDefinition definition) {
            this.plugin = plugin;
            this.event = event;
            this.entity = event.getEntity();
            this.mobId = mobId;
            this.definition = definition;
            this.killer = entity.getKiller();
            ValmoraPlayer session = killer != null ? plugin.getPlayerManager().getSession(killer.getUniqueId()) : null;
            this.killerProfile = session != null ? session.getActiveProfile() : null;
            this.xp = definition.getXpReward();
            this.gold = definition.getGoldReward();
        }

        @Override
        public void publish(ExecutionContext c) {
            c.set("mob:id", mobId);
            c.set("mob:level", definition.getLevel());
            c.set("mob:has_killer", killer != null);
            c.set("mob:luck", luck);
            c.set("mob:xp", xp);
            c.set("mob:gold", gold);
        }

        @Override
        public boolean write(String key, Object value) {
            switch (key) {
                case "mob.xp" -> xp = Math.max(0, FlowState.asDouble(value, xp));
                case "mob.gold" -> gold = Math.max(0, FlowState.asDouble(value, gold));
                case "mob.luck" -> luck = FlowState.asDouble(value, luck);
                default -> {
                    return false;
                }
            }
            return true;
        }
    }

    /** {@code death}: a custom (Valmora) mob died. */
    public static final FlowSpec<DeathState> DEATH = new FlowSpec<DeathState>(DOMAIN_ID, "death")
            .builtin(BuiltinStepFactory.of("boss_on_death", (ctx, s) -> {
                // Fires ON_DEATH boss abilities and stops tracking the boss.
                BossController bosses = s.plugin.getMobManager().getBossController();
                if (bosses.isTracked(s.entity.getUniqueId())) bosses.onDeath(s.entity);
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("resolve_luck", List.of("stats"), params -> {
                List<String> configured = params.stringList("stats");
                return (ctx, s) -> {
                    if (s.killerProfile == null) return StepOutcome.CONTINUE;
                    // pet_luck stacks additively on top of the general luck stat for loot rolls.
                    List<String> stats = configured.isEmpty()
                            ? List.of(s.plugin.getStatModule().getSystemStats().getLuck(), "pet_luck")
                            : configured;
                    double luck = 0;
                    for (String stat : stats) luck += s.killerProfile.getStatManager().getStat(stat);
                    s.luck = luck;
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("grant_skill_xp", List.of("skill"), params -> {
                // HC-091: which skill mob kills grant XP to, so renaming the core "combat" skill
                // id doesn't silently break XP gain.
                Supplier<String> skill = params.string("skill", StepParams.configString("mobs.combat-skill-id", "combat"));
                return (ctx, s) -> {
                    if (s.killerProfile != null) {
                        s.killerProfile.getSkillManager().addXp(skill.get(), s.xp, s.killer);
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("grant_gold", (ctx, s) -> {
                if (s.killerProfile != null && s.gold > 0) {
                    s.plugin.getEconomy().addCoins(s.killer, s.gold);
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("roll_loot", (ctx, s) -> {
                LootTable lootTable = s.definition.getLootTable();
                if (lootTable == null) return StepOutcome.CONTINUE;
                for (LootEntry entry : lootTable.getEntries()) {
                    double chance = entry.isLuckAffected() ? entry.getEffectiveChance(s.luck) : entry.getChance();
                    if (Math.random() < chance) {
                        ItemStack drop = entry.createDroppedItem();
                        s.event.getDrops().add(drop);
                        DebugManager.log("mobs", "'" + s.mobId + "' loot roll: " + drop.getType()
                                + " x" + drop.getAmount() + " (chance=" + chance + ")");
                    }
                }
                return StepOutcome.CONTINUE;
            }))
            .anchor(ON_DEATH, false, "after rewards and loot are resolved")
            .writable("mob.xp", "mob.gold", "mob.luck");

    public static final FlowDomain DOMAIN = new FlowDomain(DOMAIN_ID, "mob_pipeline.yml", POINT_PREFIX,
            List.of(CAST, DEATH), List.of("pre_ability", "post_ability"));
}
