package org.nakii.valmora.module.enchant;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.module.combat.DamageResult;

import java.util.Map;

/**
 * Central per-hit orchestrator for enchant trigger dispatch. For every enchant instance on an
 * item, runs the legacy (Java) {@link EnchantmentLogic} hook unchanged — hybrid coexistence, per
 * the enchant overhaul's design — then attaches {@code enchant:id}/{@code enchant:level}/{@code
 * enchant:instance} and each declared {@code variables:} formula (as {@code calc:<name>}) onto the
 * dispatch context and runs that enchant's compiled {@code triggers.<TRIGGER>:} block via the
 * shared {@link HookBus}, at point {@code "enchant:<id>:<trigger>"}.
 *
 * <p>Reused by {@code DamageCalculator} (post-attack/post-defend) and {@link EnchantKillListener}
 * (kill) — the same "load instances -> run legacy logic -> attach vars -> dispatch trigger" shape
 * applies to all three.
 */
public class EnchantDispatcher {

    private EnchantDispatcher() {
    }

    public static void dispatchPostAttack(DamageResult result, LivingEntity attacker, LivingEntity victim,
                                           int level, EnchantStateStore.EnchantInstance instance,
                                           EnchantmentDefinition def, ExecutionContext hitContext) {
        if (def.getLogic() != null) {
            def.getLogic().onPostAttack(result, attacker, victim, level);
        }
        fireTrigger(def, instance, level, EnchantTrigger.ON_ATTACK_POST, hitContext);
    }

    public static void dispatchPostDefend(DamageResult result, LivingEntity attacker, LivingEntity victim,
                                           int level, EnchantStateStore.EnchantInstance instance,
                                           EnchantmentDefinition def, ExecutionContext hitContext) {
        if (def.getLogic() != null) {
            def.getLogic().onPostDefend(result, attacker, victim, level);
        }
        fireTrigger(def, instance, level, EnchantTrigger.ON_DEFEND_POST, hitContext);
    }

    /** Runs every enchant instance on {@code item} for {@code trigger} — shared by
     *  {@link EnchantKillListener} and any future death dispatch. */
    public static void fireTriggerForItem(ItemStack item, EnchantTrigger trigger, ExecutionContext context) {
        if (item == null) return;
        Map<String, EnchantStateStore.EnchantInstance> instances = EnchantStateStore.load(item);
        if (instances.isEmpty()) return;

        // So a triggered `enchant_state` action (e.g. a persistent kill counter) can save its
        // mutation back to the actual item — see EnchantStateEngine's persistent-write path.
        context.set("enchant:item", item);

        var registry = ValmoraAPI.getInstance().getEnchantModule().getRegistry();
        for (EnchantStateStore.EnchantInstance instance : instances.values()) {
            EnchantmentDefinition def = registry.get(instance.getId()).orElse(null);
            if (def == null) continue;
            fireTrigger(def, instance, instance.getLevel(), trigger, context);
        }
    }

    /** Package-private (not private) so tests can exercise it directly without going through a
     *  full item/HookBus round-trip. */
    static void fireTrigger(EnchantmentDefinition def, EnchantStateStore.EnchantInstance instance,
                                     int level, EnchantTrigger trigger, ExecutionContext context) {
        HookBus bus = ValmoraAPI.getInstance().getHookBus();
        if (bus == null || !bus.hasStages(point(def.getId(), trigger))) return;

        context.set("enchant:id", def.getId());
        context.set("enchant:level", level);
        context.set("enchant:instance", instance);
        attachCalcVariables(def, context);

        bus.runPoint(point(def.getId(), trigger), context);
    }

    private static void attachCalcVariables(EnchantmentDefinition def, ExecutionContext context) {
        if (def.getVariables().isEmpty()) return;
        var evaluator = ValmoraAPI.getInstance().getScriptModule().getExpressionEvaluator();
        for (Map.Entry<String, String> entry : def.getVariables().entrySet()) {
            Object value = evaluator.evaluate(entry.getValue(), context);
            context.set("calc:" + entry.getKey().toLowerCase(), value);
        }
    }

    static String point(String enchantId, EnchantTrigger trigger) {
        return "enchant:" + enchantId.toLowerCase() + ":" + trigger.name();
    }
}
