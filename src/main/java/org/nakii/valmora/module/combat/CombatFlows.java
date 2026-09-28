package org.nakii.valmora.module.combat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.pipeline.flow.BuiltinStep;
import org.nakii.valmora.api.pipeline.flow.BuiltinStepFactory;
import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowSpec;
import org.nakii.valmora.api.pipeline.flow.NumberParam;
import org.nakii.valmora.api.pipeline.flow.StepOutcome;
import org.nakii.valmora.api.pipeline.flow.StepParams;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
import org.nakii.valmora.module.item.AbilityExecutor;
import org.nakii.valmora.module.item.AbilityTrigger;
import org.nakii.valmora.module.item.CombatTracker;
import org.nakii.valmora.util.DebugManager;
import org.nakii.valmora.util.Keys;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The flows of {@code combat_pipeline.yml} (docs/PIPELINE_FLOWS_PLAN.md §5.6) — what used to be
 * {@code CombatListener.onEntityDamageByEntity} ({@code entity_hit}) and {@code onEntityDamage}
 * ({@code environment_damage}). The damage math itself stays one step ({@code calculate_damage}),
 * already data-driven through {@code damage_formula.yml} and enchant hooks.
 */
public final class CombatFlows {

    private CombatFlows() {}

    public static final String DOMAIN_ID = "combat";
    public static final String POINT_PREFIX = "combat:";
    public static final String PRE_DAMAGE = POINT_PREFIX + "pre_damage";
    public static final String POST_CALCULATION = POINT_PREFIX + "post_calculation";
    public static final String POST_APPLICATION = POINT_PREFIX + "post_application";
    public static final String ON_DMG_DEALT = POINT_PREFIX + "on_dmg_dealt";
    public static final String PRE_ENVIRONMENT_DAMAGE = POINT_PREFIX + "pre_environment_damage";
    public static final String POST_ENVIRONMENT_DAMAGE = POINT_PREFIX + "post_environment_damage";

    private static final String DEBUG_MODULE = "combat";

    private static void debug(String msg) {
        if (DebugManager.isEnabled(DEBUG_MODULE)) {
            Valmora.getInstance().getLogger().info("[combat-debug] " + msg);
        }
    }

    // ------------------------------------------------------------------ shared steps

    /** {@code iframe_gate} — HC-029: a repeat hit inside the victim's invulnerability window is cancelled. */
    private static BuiltinStepFactory<HitState> iframeGate() {
        return BuiltinStepFactory.of("iframe_gate", List.of("threshold-factor"), params -> {
            NumberParam factor = params.number("threshold-factor", NumberParam.config("combat.iframe-threshold-factor", 0.5));
            return (ctx, s) -> {
                if (s.victim.getNoDamageTicks() > s.victim.getMaximumNoDamageTicks() * (float) factor.get(ctx, s)) {
                    debug("HIT REJECTED (i-frames): victim=" + s.victim.getName() + " noDamageTicks="
                            + s.victim.getNoDamageTicks() + " max=" + s.victim.getMaximumNoDamageTicks());
                    s.event.setCancelled(true);
                    return StepOutcome.STOP;
                }
                return StepOutcome.CONTINUE;
            };
        });
    }

    /** {@code zero_vanilla_damage} — Valmora applies its own damage, so vanilla's is zeroed. */
    private static BuiltinStepFactory<HitState> zeroVanillaDamage(boolean stopOnNoDamage) {
        return BuiltinStepFactory.required("zero_vanilla_damage", (ctx, s) -> {
            s.rawDamage = s.event.getDamage();
            if (stopOnNoDamage && s.rawDamage <= 0) return StepOutcome.STOP;
            s.event.setDamage(0);
            return StepOutcome.CONTINUE;
        });
    }

    private static final BuiltinStep<HitState> RECORD_DEATH_CONTEXT = (ctx, s) -> {
        // VANILLA_CONTROL_AUDIT.md §9 — recorded before apply_damage so a death message built from
        // PlayerDeathEvent (fired synchronously inside apply() if this hit is fatal) can read what
        // actually caused it, independent of vanilla's own last-damage-cause bookkeeping.
        if (s.victim instanceof Player) {
            DeathContextCache.record(s.victim.getUniqueId(), s.result.getDamageType(), s.attacker);
        }
        return StepOutcome.CONTINUE;
    };

    private static final BuiltinStep<HitState> APPLY_DAMAGE = (ctx, s) -> {
        s.result.apply();
        debug("HIT APPLIED: victim=" + s.victim.getName() + " healthAfter=" + s.victim.getHealth()
                + " dealt=" + s.result.getFinalDamage());
        return StepOutcome.CONTINUE;
    };

    private static final BuiltinStep<HitState> SPAWN_INDICATOR = (ctx, s) -> {
        ValmoraAPI.getInstance().getDamageIndicatorManager().spawnIndicator(s.result);
        return StepOutcome.CONTINUE;
    };

    /** ON_DAMAGE_TAKEN on the victim's held item + armor (docs/IMPLEMENTATION_BACKLOG.md) — skipped when immune. */
    private static final BuiltinStep<HitState> FIRE_ON_DAMAGE_TAKEN = (ctx, s) -> {
        if (!s.result.isImmune() && s.victim instanceof Player victimPlayer) {
            AbilityExecutor.fireHeld(victimPlayer, AbilityTrigger.ON_DAMAGE_TAKEN, s.attacker, true);
            AbilityExecutor.fireModifiersHeld(victimPlayer, AbilityTrigger.ON_DAMAGE_TAKEN, s.attacker, true);
            fireArmorOnDamageTaken(victimPlayer, s.attacker);
            debug("fired ON_DAMAGE_TAKEN item + armor abilities for victim=" + victimPlayer.getName());
        }
        return StepOutcome.CONTINUE;
    };

    /** Fires ON_DAMAGE_TAKEN on every armor piece — mirrors {@code AbilityTriggerListener.fireArmor}. */
    private static void fireArmorOnDamageTaken(Player player, LivingEntity attacker) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            String itemId = armor.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (itemId != null) {
                ValmoraAPI.getInstance().getItemManager().getItemRegistry().getItem(itemId)
                        .ifPresent(def -> AbilityExecutor.fire(player, def, AbilityTrigger.ON_DAMAGE_TAKEN, attacker, true));
            }
            AbilityExecutor.fireModifiersForItem(player, armor, AbilityTrigger.ON_DAMAGE_TAKEN, attacker, true);
        }
    }

    private static DamageType damageTypeParam(StepParams params, String key, DamageType def) {
        String id = params.string(key, (String) null);
        if (id == null) return def;
        DamageType found = DamageType.find(id).orElse(null);
        if (found == null) {
            params.reader().warn(key, "unknown damage type '" + id + "' — using " + def.getId());
            return def;
        }
        return found;
    }

    // ------------------------------------------------------------------ entity_hit

    /** {@code entity_hit}: a living entity (or a projectile it shot) hit a living entity. */
    public static final FlowSpec<HitState> ENTITY_HIT = new FlowSpec<HitState>(DOMAIN_ID, "entity_hit")
            .builtin(iframeGate())
            .builtin(BuiltinStepFactory.required("resolve_attacker", (ctx, s) -> {
                // Non-living damagers (and projectiles without a living shooter) stay vanilla.
                if (!(s.event instanceof EntityDamageByEntityEvent byEntity)) return StepOutcome.STOP;
                if (byEntity.getDamager() instanceof LivingEntity living) {
                    s.attacker = living;
                } else if (byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
                    s.attacker = living;
                }
                return s.attacker == null ? StepOutcome.STOP : StepOutcome.CONTINUE;
            }))
            .builtin(zeroVanillaDamage(false))
            .builtin(BuiltinStepFactory.of("classify_damage_type", List.of("melee", "projectile"), params -> {
                DamageType melee = damageTypeParam(params, "melee", DamageType.MELEE);
                DamageType projectile = damageTypeParam(params, "projectile", DamageType.PROJECTILE);
                return (ctx, s) -> {
                    // HC-030: every projectile entity type counts, not just arrows.
                    s.type = ((EntityDamageByEntityEvent) s.event).getDamager() instanceof Projectile ? projectile : melee;
                    if (DebugManager.isEnabled(DEBUG_MODULE)) {
                        debug("HIT START: attacker=" + s.attacker.getName() + " victim=" + s.victim.getName()
                                + " damager=" + ((EntityDamageByEntityEvent) s.event).getDamager().getType()
                                + " damageType=" + s.type + " rawEventDamage=" + s.rawDamage);
                    }
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.required("calculate_damage", (ctx, s) -> {
                if (s.type == null) s.type = DamageType.MELEE;
                s.result = DamageCalculator.calculateDamage(s.attacker, s.victim, s.type, 0.0, s.scripted ? ctx : null);
                debug("HIT CALCULATED: finalDamage=" + s.result.getFinalDamage() + " crit=" + s.result.isCritical()
                        + " immune=" + s.result.isImmune() + " victimHealthBefore=" + s.victim.getHealth());
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("attack_cooldown_scaling", List.of("enabled"), params -> {
                BooleanSupplier enabled = params.bool("enabled", AttackCooldownService::isEnabled);
                return (ctx, s) -> {
                    // Swing-charge scaling (VANILLA_CONTROL_AUDIT.md §12/§14 High #9) — melee only.
                    // Charge is read from the *previous* hit before recordAttack() updates it.
                    if (!s.result.isImmune() && s.type == DamageType.MELEE && enabled.getAsBoolean()) {
                        double charge = AttackCooldownService.getChargeProgress(s.attacker);
                        double multiplier = AttackCooldownService.damageMultiplierFor(charge);
                        if (multiplier < 1.0) {
                            s.rescale(s.result.getFinalDamage() * multiplier);
                            debug("attack-cooldown scaling: charge=" + charge + " multiplier=" + multiplier
                                    + " -> finalDamage=" + s.result.getFinalDamage());
                        }
                    }
                    AttackCooldownService.recordAttack(s.attacker);
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("shield_block", (ctx, s) -> {
                // VANILLA_CONTROL_AUDIT.md §14 — see ShieldBlockService for why this runs after
                // Valmora's own calculation.
                if (!s.result.isImmune() && s.victim instanceof Player victimPlayer) {
                    ShieldBlockService.Result block = ShieldBlockService.resolve(victimPlayer, s.attacker, s.type);
                    if (block.blocked()) {
                        s.rescale(s.result.getFinalDamage() * block.damageMultiplier());
                        if (block.disablesShield()) {
                            victimPlayer.setCooldown(org.bukkit.Material.SHIELD, ShieldBlockService.shieldDisableTicks());
                        }
                        debug("HIT BLOCKED by shield: victim=" + victimPlayer.getName() + " multiplier="
                                + block.damageMultiplier() + " -> finalDamage=" + s.result.getFinalDamage());
                    }
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("record_death_context", RECORD_DEATH_CONTEXT))
            .builtin(BuiltinStepFactory.required("apply_damage", APPLY_DAMAGE))
            .builtin(BuiltinStepFactory.of("set_knockback", (ctx, s) -> {
                // Hands this hit's knockback multiplier (VANILLA_CONTROL_AUDIT.md §14 Medium #22) to
                // CombatKnockbackListener, which sees vanilla's separate EntityKnockbackEvent.
                if (!s.result.isImmune()) {
                    KnockbackModifierTracker.set(s.victim.getUniqueId(), s.result.getKnockbackMultiplier());
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("spawn_indicator", SPAWN_INDICATOR))
            .builtin(BuiltinStepFactory.of("fire_on_hit_abilities", (ctx, s) -> {
                if (s.attacker instanceof Player attackerPlayer) {
                    CombatTracker.recordDamageDealt(attackerPlayer.getUniqueId(), s.result.getFinalDamage());
                    AbilityExecutor.fireHeld(attackerPlayer, AbilityTrigger.ON_HIT, s.victim, true);
                    AbilityExecutor.fireModifiersHeld(attackerPlayer, AbilityTrigger.ON_HIT, s.victim, true);
                    debug("fired ON_HIT item + modifier abilities for attacker=" + attackerPlayer.getName());
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("fire_on_damage_taken_abilities", FIRE_ON_DAMAGE_TAKEN))
            .builtin(BuiltinStepFactory.of("boss_triggers", (ctx, s) -> {
                var bossController = ValmoraAPI.getInstance().getMobManager().getBossController();
                if (bossController.isTracked(s.attacker.getUniqueId())) bossController.onAttack(s.attacker, s.victim);
                if (bossController.isTracked(s.victim.getUniqueId())) bossController.onDamaged(s.victim, s.attacker);
                return StepOutcome.CONTINUE;
            }))
            .anchor(PRE_DAMAGE, true, "before the damage is calculated — interrupt cancels the hit")
            .anchor(POST_CALCULATION, true, "damage known, not yet applied — interrupt stops it landing")
            .anchor(POST_APPLICATION, false, "after health is reduced and the indicator spawned")
            .anchor(ON_DMG_DEALT, false, "after on-hit/damage-taken abilities and boss triggers")
            .writable("dmg.final_damage", "dmg.damage_type");

    // ------------------------------------------------------------------ environment_damage

    /** {@code environment_damage}: damage with no attacking entity (fall, fire, drowning, ...). */
    public static final FlowSpec<HitState> ENVIRONMENT_DAMAGE = new FlowSpec<HitState>(DOMAIN_ID, "environment_damage")
            .builtin(iframeGate())
            .builtin(zeroVanillaDamage(true))
            .builtin(BuiltinStepFactory.of("map_cause", List.of("mapping", "fallback"), params -> {
                Map<EntityDamageEvent.DamageCause, DamageType> mapping = causeMapping(params);
                String fallbackParam = params.string("fallback", (String) null);
                return (ctx, s) -> {
                    s.type = mapCause(s.event.getCause(), mapping, fallbackParam);
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.required("calculate_environment_damage", List.of("multiplier"), params -> {
                NumberParam multiplier = params.number("multiplier", NumberParam.config("combat.environment-damage-multiplier", 5.0));
                return (ctx, s) -> {
                    if (s.type == null) s.type = fallbackDamageType(null);
                    s.result = DamageCalculator.calculateDamage(s.victim, s.type, s.rawDamage, multiplier.get(ctx, s));
                    return StepOutcome.CONTINUE;
                };
            }))
            .builtin(BuiltinStepFactory.of("record_death_context", RECORD_DEATH_CONTEXT))
            .builtin(BuiltinStepFactory.required("apply_damage", (ctx, s) -> {
                s.result.apply();
                debug("ENV HIT: victim=" + s.victim.getName() + " cause=" + s.event.getCause() + " mappedType=" + s.type
                        + " rawDamage=" + s.rawDamage + " finalDamage=" + s.result.getFinalDamage()
                        + " immune=" + s.result.isImmune());
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("clear_immune_burn", (ctx, s) -> {
                // A fully fire/lava-immune mob shouldn't keep burning, and a fully freeze-immune one
                // shouldn't keep re-accumulating freeze ticks (VANILLA_CONTROL_AUDIT.md §7 — this
                // Paper API has no freeze-tick event to intercept accumulation itself).
                if (s.result.isImmune()) {
                    if (s.type == DamageType.FIRE || s.type == DamageType.LAVA) s.victim.setFireTicks(0);
                    if (s.type == DamageType.FREEZE) s.victim.setFreezeTicks(0);
                }
                return StepOutcome.CONTINUE;
            }))
            .builtin(BuiltinStepFactory.of("spawn_indicator", SPAWN_INDICATOR))
            .builtin(BuiltinStepFactory.of("fire_on_damage_taken_abilities", FIRE_ON_DAMAGE_TAKEN))
            .anchor(PRE_ENVIRONMENT_DAMAGE, true, "cause mapped, damage not yet calculated — interrupt cancels it (vanilla's is already zeroed)")
            .anchor(POST_ENVIRONMENT_DAMAGE, false, "after the damage landed")
            .writable("dmg.final_damage", "dmg.damage_type");

    public static final FlowDomain DOMAIN = new FlowDomain(DOMAIN_ID, "combat_pipeline.yml", POINT_PREFIX,
            List.of(ENTITY_HIT, ENVIRONMENT_DAMAGE),
            List.of("pre_damage", "post_calculation", "post_application", "on_dmg_dealt", "on_death",
                    "pre_environment_damage", "post_environment_damage"));

    // ------------------------------------------------------------------ cause mapping

    private static Map<EntityDamageEvent.DamageCause, DamageType> causeMapping(StepParams params) {
        Map<EntityDamageEvent.DamageCause, DamageType> mapping = new EnumMap<>(EntityDamageEvent.DamageCause.class);
        ConfigReader reader = params.reader();
        ConfigurationSection section = reader.section() == null ? null : reader.section().getConfigurationSection("mapping");
        if (section == null) return mapping;
        for (String key : section.getKeys(false)) {
            EntityDamageEvent.DamageCause cause = ConfigReader.parseEnum(key, EntityDamageEvent.DamageCause.class);
            DamageType type = DamageType.find(String.valueOf(section.get(key))).orElse(null);
            if (cause == null) {
                reader.warn("mapping", "unknown damage cause '" + key + "' — ignored",
                        Suggestions.hint(key, ConfigReader.enumNames(EntityDamageEvent.DamageCause.class)));
            } else if (type == null) {
                reader.warn("mapping", "unknown damage type '" + section.get(key) + "' for " + key + " — ignored");
            } else {
                mapping.put(cause, type);
            }
        }
        return mapping;
    }

    /**
     * Pipeline-file {@code mapping:} first, then HC-031's {@code combat.cause-mapping.<CAUSE>} in
     * config.yml, then the built-in table, then the fallback type.
     */
    @SuppressWarnings("deprecation") // DamageCause.DRAGON_BREATH (since 1.21.5): no replacement constant; kept
    // mapped so any server still reporting it keeps its dedicated damage type instead of the fallback.
    static DamageType mapCause(EntityDamageEvent.DamageCause cause, Map<EntityDamageEvent.DamageCause, DamageType> mapping,
                               String fallbackParam) {
        DamageType mapped = mapping.get(cause);
        if (mapped != null) return mapped;
        Valmora plugin = Valmora.getInstance();
        if (plugin != null) {
            String configured = plugin.getConfig().getString("combat.cause-mapping." + cause.name());
            if (configured != null && !configured.isBlank()) {
                var found = DamageType.find(configured);
                if (found.isPresent()) return found.get();
            }
        }
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
            case LIGHTNING -> DamageType.LIGHTNING;
            case FREEZE -> DamageType.FREEZE;
            default -> fallbackDamageType(fallbackParam);
        };
    }

    /** The {@code fallback} param, else {@code combat.environment.fallback-damage-type} (HC-033), else MELEE. */
    private static DamageType fallbackDamageType(String fallbackParam) {
        String id = fallbackParam;
        if (id == null) {
            Valmora plugin = Valmora.getInstance();
            id = plugin != null ? plugin.getConfig().getString("combat.environment.fallback-damage-type", "MELEE") : "MELEE";
        }
        try {
            return DamageType.valueOf(id.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DamageType.MELEE;
        }
    }
}
