package org.nakii.valmora.module.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
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
            if (vPlayer != null) {
                StatManager statManager = vPlayer.getActiveProfile().getStatManager();
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
            if (vVictim != null) {
                defense = vVictim.getActiveProfile().getStatManager().getStat(sys.getDefense());
            }
        } else if (victimMob != null) {
            defense = victimMob.getDefense();
        }

        DamageModifierContext context = new DamageModifierContext(baseDamage, strength, critChance, critDamage, defense, damageType);

        if (attacker instanceof Player) {
            ItemStack weapon = ((Player) attacker).getInventory().getItemInMainHand();
            if (weapon != null) {
                Map<String, Integer> enchants = EnchantmentHelper.getEnchantments(weapon);
                for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                    EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                    if (def != null && def.getLogic() != null) {
                        def.getLogic().modifyAttack(context, attacker, victim, entry.getValue());
                    }
                }
            }
        }

        if (victim instanceof Player victimPlayer) {
            ItemStack[] armor = victimPlayer.getInventory().getArmorContents();
            for (ItemStack armorItem : armor) {
                if (armorItem != null) {
                    Map<String, Integer> armorEnchants = EnchantmentHelper.getEnchantments(armorItem);
                    for (Map.Entry<String, Integer> entry : armorEnchants.entrySet()) {
                        EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                        if (def != null && def.getLogic() != null) {
                            def.getLogic().modifyDefend(context, attacker, victim, entry.getValue());
                        }
                    }
                }
            }
        }

        boolean isCritical = Math.random() < (context.getCritChance() / 100.0);

        // Phase 2.2 (docs/REFACTOR/PROGRESS.md): formulas are pre-compiled from damage_formula.yml
        // at CombatModule.onEnable() and evaluated here, not re-parsed. Falls back to the exact
        // pre-refactor hardcoded math (the fallback values below) when no CombatModule/registry
        // is available — e.g. in unit tests that mock ValmoraAPI without stubbing getCombatModule().
        ExecutionContext formulaContext = buildFormulaContext(attacker, victim, context);
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

        // Replaces the old hardcoded VOID/DROWNING/FALL exclusion list — now data-driven per damage type.
        double defenseMultiplier = 1.0;
        if (!damageType.isIgnoresDefense()) {
            defenseMultiplier = formulas != null
                    ? formulas.evaluate(DamageFormulaRegistry.DEFENSE_MULTIPLIER, formulaContext, 100.0 / (context.getDefense() + 100.0))
                    : 100.0 / (context.getDefense() + 100.0);
        }

        double mitigated = fullDamage * defenseMultiplier;

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

        if (attacker instanceof Player) {
            ItemStack weapon = ((Player) attacker).getInventory().getItemInMainHand();
            if (weapon != null) {
                Map<String, Integer> enchants = EnchantmentHelper.getEnchantments(weapon);
                for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                    EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                    if (def != null && def.getLogic() != null) {
                        def.getLogic().onPostAttack(result, attacker, victim, entry.getValue());
                    }
                }
            }
        }

        if (victim instanceof Player victimPlayer) {
            ItemStack[] armor = victimPlayer.getInventory().getArmorContents();
            for (ItemStack armorItem : armor) {
                if (armorItem != null) {
                    Map<String, Integer> armorEnchants = EnchantmentHelper.getEnchantments(armorItem);
                    for (Map.Entry<String, Integer> entry : armorEnchants.entrySet()) {
                        EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                        if (def != null && def.getLogic() != null) {
                            def.getLogic().onPostDefend(result, attacker, victim, entry.getValue());
                        }
                    }
                }
            }
        }

        return result;
    }

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType) {
        return calculateDamage(attacker, victim, damageType, 0.0);
    }

    public static DamageResult calculateDamage(LivingEntity victim, DamageType damageType, double baseVanillaDamage) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        double multiplier = 5.0;
        double fullDamage = baseVanillaDamage * multiplier;

        MobDefinition victimMob = mobOf(victim);

        double defenseMultiplier = 1.0;
        if (!damageType.isIgnoresDefense()) {
            double defense = 0.0;
            if (victim instanceof Player player) {
                ValmoraPlayer vVictim = api.getPlayerManager().getSession(player.getUniqueId());
                if (vVictim != null) {
                    defense = vVictim.getActiveProfile().getStatManager().getStat(api.getSystemStats().getDefense());
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
