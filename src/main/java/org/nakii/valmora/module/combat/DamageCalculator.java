package org.nakii.valmora.module.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.enchant.EnchantCombatHook;
import org.nakii.valmora.module.enchant.EnchantDispatcher;
import org.nakii.valmora.module.enchant.EnchantStateStore;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.util.Keys;

import java.util.Map;

public class DamageCalculator {

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType, double baseDamageOverride) {
        return calculateDamage(attacker, victim, damageType, baseDamageOverride, null);
    }

    /**
     * Same as {@link #calculateDamage(LivingEntity, LivingEntity, DamageType, double)}, but also
     * accepts the combat pipeline's {@code combat:pre_damage} context (see
     * docs/COMBAT_PIPELINE_ANALYSIS.md and {@code CombatListener}), if the pipeline was engaged for
     * this hit. {@code pipelineContext} may be {@code null} — every other call site (item/mob
     * mechanics that deal damage outside the combat pipeline) keeps using the 4-arg overload above.
     *
     * <p><b>Ordering rule</b> (resolves the "conflict with existing enchants" question raised in
     * the pipeline analysis doc's §5): the pipeline's {@code multiply_damage} contribution is
     * applied <i>after</i> strength/crit and all enchant {@code modifyAttack} hooks — so a pipeline
     * stage sees/reacts to the attacker's fully-computed output rather than racing enchants for
     * calculation order — but <i>before</i> defense mitigation, so it behaves like a strength/crit
     * attacker-side buff rather than a defense-bypass effect. Use a damage type with
     * {@code ignores-defense: true} if a stage needs to bypass defense entirely.
     */
    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType,
                                                 double baseDamageOverride, ExecutionContext pipelineContext) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        SystemStats sys = api.getSystemStats();

        double baseDamage = baseDamageOverride;
        double strength = 0.0;
        double critChance = 0.0;
        double critDamage = 0.0;
        double defense = 0.0;

        if (attacker instanceof Player player) {
            ValmoraPlayer vPlayer = api.getPlayerManager().getSession(player.getUniqueId());
            org.nakii.valmora.module.profile.ValmoraProfile activeProfile = vPlayer != null ? vPlayer.getActiveProfile() : null;
            if (activeProfile != null) {
                StatManager statManager = activeProfile.getStatManager();
                if (baseDamageOverride <= 0) {
                    baseDamage = statManager.getStat(sys.getDamage());
                }
                strength = statManager.getStat(sys.getStrength());
                critChance = statManager.getStat(sys.getCritChance());
                critDamage = statManager.getStat(sys.getCritDamage());
            }
        } else if (attacker != null) {
            MobDefinition mob = mobOf(attacker);
            if (mob != null) {
                if (baseDamageOverride <= 0) {
                    baseDamage = mob.getScaledDamage();
                }
                // Optional offensive stats feed the same player damage formula
                strength = mob.getStrength();
                critChance = mob.getCritChance();
                critDamage = mob.getCritDamage();
            } else if (baseDamageOverride <= 0) {
                baseDamage = 1.0;
            }
        }

        MobDefinition victimMob = mobOf(victim);
        if (victim instanceof Player victimPlayer) {
            ValmoraPlayer vVictim = api.getPlayerManager().getSession(victimPlayer.getUniqueId());
            org.nakii.valmora.module.profile.ValmoraProfile victimProfile = vVictim != null ? vVictim.getActiveProfile() : null;
            if (victimProfile != null) {
                defense = victimProfile.getStatManager().getStat(sys.getDefense());
            }
        } else if (victimMob != null) {
            defense = victimMob.getDefense();
        }

        DamageModifierContext context = new DamageModifierContext(baseDamage, strength, critChance, critDamage, defense, damageType);

        // Built here (moved up from after the pre-hit loops) so the enchant combat hook can attach
        // per-enchant $enchant.level$/$calc.*$ values to it while evaluating modify-attack/
        // modify-defend conditions and formulas below — the same context is then reused and
        // incrementally attached to for the rest of the hit (dmg:*, hit:*). caster=attacker,
        // target=victim, matching every other attacker-side use of this context in the codebase.
        ExecutionContext formulaContext = buildFormulaContext(attacker, victim, context);

        // Defender-side enchants (armor) need "myself"/"the other party" to mean the wearer/
        // attacker respectively — the reverse of formulaContext's roles — so $target.*$ in a
        // modify-defend/ON_DEFEND_POST condition means "the attacker", and a trigger action's
        // @self/@target (TargetResolver) means "the wearer"/"the attacker". A child context swaps
        // caster/target while still inheriting every dmg:*/hit:* attachment via the parent chain.
        ExecutionContext defendContext = victim != null
                ? new SimpleExecutionContext(victim, attacker, formulaContext.getLocation(), null, formulaContext)
                : formulaContext;

        ItemStack weapon = attacker instanceof Player attackerPlayer ? attackerPlayer.getInventory().getItemInMainHand() : null;
        if (weapon != null) {
            for (EnchantStateStore.EnchantInstance instance : EnchantStateStore.load(weapon).values()) {
                EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(instance.getId()).orElse(null);
                if (def == null) continue;
                if (def.getLogic() != null) {
                    def.getLogic().modifyAttack(context, attacker, victim, instance.getLevel());
                }
                attachEnchantVars(def, instance, formulaContext);
                EnchantCombatHook.applyAttack(context, formulaContext, def.getModifyAttack());
            }
        }

        if (victim instanceof Player victimPlayer) {
            ItemStack[] armor = victimPlayer.getInventory().getArmorContents();
            for (ItemStack armorItem : armor) {
                if (armorItem == null) continue;
                for (EnchantStateStore.EnchantInstance instance : EnchantStateStore.load(armorItem).values()) {
                    EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(instance.getId()).orElse(null);
                    if (def == null) continue;
                    if (def.getLogic() != null) {
                        def.getLogic().modifyDefend(context, attacker, victim, instance.getLevel());
                    }
                    attachEnchantVars(def, instance, defendContext);
                    EnchantCombatHook.applyDefend(context, defendContext, def.getModifyDefend());
                }
            }
        }

        boolean isCritical = Math.random() < (context.getCritChance() / 100.0);

        // Phase 2.2 (docs/REFACTOR/PROGRESS.md): formulas are pre-compiled from damage_formula.yml
        // at CombatModule.onEnable() and evaluated here, not re-parsed. Falls back to the exact
        // pre-refactor hardcoded math (the fallback values below) when no CombatModule/registry
        // is available — e.g. in unit tests that mock ValmoraAPI without stubbing getCombatModule().
        DamageFormulaRegistry formulas = getFormulaRegistry();

        double damageMultiplier = formulas != null
                ? formulas.evaluate(DamageFormulaRegistry.DAMAGE_MULTIPLIER, formulaContext, 1 + context.getStrength() / 100.0)
                : 1 + context.getStrength() / 100.0;
        double fullDamage = context.getBaseDamage() * damageMultiplier;

        if (isCritical) {
            double critMultiplier = formulas != null
                    ? formulas.evaluate(DamageFormulaRegistry.CRIT_MULTIPLIER, formulaContext, 1 + context.getCritDamage() / 100.0)
                    : 1 + context.getCritDamage() / 100.0;
            fullDamage *= critMultiplier;
        }

        fullDamage *= context.getDamageMultiplier();

        // Combat pipeline `multiply_damage` contribution — see this method's ordering-rule javadoc.
        if (pipelineContext != null) {
            fullDamage *= (double) pipelineContext.get("dmg:pipeline_multiplier", 1.0);
        }

        // Attacker-side enchant defense-shred (combat.modify-attack.modifiers.defense-shred-percent)
        // reduces the victim's effective defense before the mitigation formula sees it.
        double effectiveDefense = Math.max(0, context.getDefense() * (1.0 - context.getDefenseShredPercent() / 100.0));

        // Replaces the old hardcoded VOID/DROWNING/FALL exclusion list — now data-driven per damage type.
        double defenseMultiplier = 1.0;
        if (!damageType.isIgnoresDefense()) {
            defenseMultiplier = formulas != null
                    ? formulas.evaluate(DamageFormulaRegistry.DEFENSE_MULTIPLIER, formulaContext, 100.0 / (effectiveDefense + 100.0))
                    : 100.0 / (effectiveDefense + 100.0);
        }

        double mitigated = fullDamage * defenseMultiplier;

        // Defender-side enchant flat reduction (combat.modify-defend.modifiers.damage-reduction-percent).
        if (context.getDamageReductionPercent() > 0) {
            mitigated *= Math.max(0, 1.0 - context.getDamageReductionPercent() / 100.0);
        }

        // Mob victim damage-type resistances (1.0 = full immunity)
        boolean immune = false;
        if (victimMob != null) {
            double resistance = victimMob.getResistance(damageType);
            if (resistance > 0) {
                mitigated *= (1.0 - resistance);
                immune = resistance >= 1.0;
            }
        }

        // Phase 2.3: PDC-based resistance component, stacks multiplicatively with the mob's own
        // table above and applies to any entity (players included), not just custom mobs.
        double pdcResistance = DamageResistanceComponent.getResistance(victim, damageType);
        if (pdcResistance > 0) {
            mitigated *= (1.0 - pdcResistance);
            immune = immune || pdcResistance >= 1.0;
        }

        double finalDamage = Math.floor(mitigated);

        DamageResult result = new DamageResult(finalDamage, damageType, isCritical, attacker, victim);
        result.setImmune(immune);

        damageType.fireOnHit(formulaContext);

        // $hit.*$ (HitVariableProvider) — attached once the result is known, read by enchant
        // ON_ATTACK_POST/ON_DEFEND_POST trigger conditions/actions below.
        formulaContext.set("hit:damage", finalDamage);
        formulaContext.set("hit:is_crit", isCritical);
        formulaContext.set("hit:damage_type", damageType.getId());

        if (weapon != null) {
            for (EnchantStateStore.EnchantInstance instance : EnchantStateStore.load(weapon).values()) {
                EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(instance.getId()).orElse(null);
                if (def != null) {
                    EnchantDispatcher.dispatchPostAttack(result, attacker, victim, instance.getLevel(), instance, def, formulaContext);
                }
            }
        }

        if (victim instanceof Player victimPlayer) {
            ItemStack[] armor = victimPlayer.getInventory().getArmorContents();
            for (ItemStack armorItem : armor) {
                if (armorItem == null) continue;
                for (EnchantStateStore.EnchantInstance instance : EnchantStateStore.load(armorItem).values()) {
                    EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(instance.getId()).orElse(null);
                    if (def != null) {
                        EnchantDispatcher.dispatchPostDefend(result, attacker, victim, instance.getLevel(), instance, def, defendContext);
                    }
                }
            }
        }

        return result;
    }

    /** Attaches {@code enchant:id}/{@code enchant:level}/{@code enchant:instance} and every
     *  {@code variables:} formula (as {@code calc:<name>}) for the enchant instance currently being
     *  evaluated — shared by the pre-hit modify-attack/modify-defend loops above. */
    private static void attachEnchantVars(EnchantmentDefinition def, EnchantStateStore.EnchantInstance instance, ExecutionContext ctx) {
        ctx.set("enchant:id", def.getId());
        ctx.set("enchant:level", instance.getLevel());
        ctx.set("enchant:instance", instance);
        if (def.getVariables().isEmpty()) return;
        var evaluator = ValmoraAPI.getInstance().getScriptModule().getExpressionEvaluator();
        for (Map.Entry<String, String> entry : def.getVariables().entrySet()) {
            ctx.set("calc:" + entry.getKey().toLowerCase(), evaluator.evaluate(entry.getValue(), ctx));
        }
    }

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType) {
        return calculateDamage(attacker, victim, damageType, 0.0);
    }

    public static DamageResult calculateDamage(LivingEntity victim, DamageType damageType, double baseVanillaDamage) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        org.nakii.valmora.Valmora plugin = org.nakii.valmora.Valmora.getInstance();
        double multiplier = plugin != null
                ? plugin.getConfig().getDouble("combat.environment-damage-multiplier", 5.0)
                : 5.0;
        double fullDamage = baseVanillaDamage * multiplier;

        MobDefinition victimMob = mobOf(victim);

        double defenseMultiplier = 1.0;
        if (!damageType.isIgnoresDefense()) {
            double defense = 0.0;
            if (victim instanceof Player player) {
                ValmoraPlayer vVictim = api.getPlayerManager().getSession(player.getUniqueId());
                org.nakii.valmora.module.profile.ValmoraProfile victimProfile = vVictim != null ? vVictim.getActiveProfile() : null;
                if (victimProfile != null) {
                    defense = victimProfile.getStatManager().getStat(api.getSystemStats().getDefense());
                }
            } else if (victimMob != null) {
                defense = victimMob.getDefense();
            }
            defenseMultiplier = 100.0 / (defense + 100.0);
        }

        double mitigated = fullDamage * defenseMultiplier;

        // Mob victim damage-type resistances (covers environmental fire/lava/explosion/etc.)
        boolean immune = false;
        if (victimMob != null) {
            double resistance = victimMob.getResistance(damageType);
            if (resistance > 0) {
                mitigated *= (1.0 - resistance);
                immune = resistance >= 1.0;
            }
        }

        double pdcResistance = DamageResistanceComponent.getResistance(victim, damageType);
        if (pdcResistance > 0) {
            mitigated *= (1.0 - pdcResistance);
            immune = immune || pdcResistance >= 1.0;
        }

        double finalDamage = Math.floor(mitigated);
        DamageResult result = new DamageResult(finalDamage, damageType, false, null, victim);
        result.setImmune(immune);

        damageType.fireOnHit(new SimpleExecutionContext(victim, null, victim.getLocation(), null));
        return result;
    }

    /**
     * Attaches the {@code dmg:*} variables read by {@link DamageFormulaRegistry}'s compiled
     * formulas via {@code $dmg.*$} (see
     * {@link org.nakii.valmora.module.script.variable.providers.DamageVariableProvider}).
     */
    private static ExecutionContext buildFormulaContext(LivingEntity attacker, LivingEntity victim, DamageModifierContext context) {
        SimpleExecutionContext ctx = new SimpleExecutionContext(attacker != null ? attacker : victim, victim,
                victim != null ? victim.getLocation() : null, null);
        ctx.set("dmg:base_damage", context.getBaseDamage());
        ctx.set("dmg:strength", context.getStrength());
        ctx.set("dmg:crit_chance", context.getCritChance());
        ctx.set("dmg:crit_damage", context.getCritDamage());
        ctx.set("dmg:defense", context.getDefense());
        return ctx;
    }

    private static DamageFormulaRegistry getFormulaRegistry() {
        var combatModule = ValmoraAPI.getInstance().getCombatModule();
        return combatModule != null ? combatModule.getDamageFormulaRegistry() : null;
    }

    /** Returns the {@link MobDefinition} for a custom mob entity, or null if it is not one. */
    private static MobDefinition mobOf(LivingEntity entity) {
        if (entity == null) return null;
        org.bukkit.persistence.PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc == null) return null;
        String mobId = pdc.get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        if (mobId == null) return null;
        return ValmoraAPI.getInstance().getMobManager().getMobDefinition(mobId);
    }
}
