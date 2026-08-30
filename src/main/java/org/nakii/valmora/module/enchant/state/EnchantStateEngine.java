package org.nakii.valmora.module.enchant.state;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.enchant.EnchantStateStore;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Facade backing {@code $enchant.state.<key>$} reads and the {@code enchant_state} event's writes —
 * resolves whichever tier (transient or persistent) the enchant actually declared {@code <key>}
 * under, so callers never need to know which one they're touching.
 *
 * <p>Transient reads/writes go through {@link TransientStateTracker}, keyed off {@code
 * context.getCaster()}/{@code context.getTarget()} — this is <em>always</em> the true
 * attacker/victim of the current hit even inside defend-side dispatch, since {@code
 * DamageCalculator} builds a caster/target-swapped child context for that case (see its class doc).
 *
 * <p>Persistent reads use the already-loaded {@code enchant:instance} attachment (cheap, no PDC
 * re-read). Persistent writes need the live item to save back to, so they read the {@code
 * "enchant:item"} attachment the dispatch code (see {@code EnchantDispatcher}/{@code
 * DamageCalculator}) sets alongside {@code enchant:instance} — a mutate call with no item attached
 * (e.g. a hand-built context in a test) is a safe no-op rather than a throw.
 */
public class EnchantStateEngine {

    private final TransientStateTracker tracker;

    public EnchantStateEngine(TransientStateTracker tracker) {
        this.tracker = tracker;
    }

    public TransientStateTracker getTracker() {
        return tracker;
    }

    /** Backs {@code $enchant.state.<key>$}. Returns 0 if no enchant is currently attached to
     *  {@code context}, or {@code <key>} isn't declared under either state tier. */
    public int resolve(ExecutionContext context, String key) {
        EnchantmentDefinition def = definitionOf(context);
        if (def == null) return 0;

        TransientStateDefinition transientDef = def.getTransientStates().get(key);
        if (transientDef != null) {
            UUID attacker = attackerOf(context);
            UUID victim = victimOf(context);
            return tracker.peek(attacker, victim, def.getId(), key, transientDef);
        }

        EnchantStateStore.EnchantInstance instance = context.get("enchant:instance");
        int defaultValue = defaultOf(def, key);
        return EnchantStateStore.getPersistentState(instance, key, defaultValue);
    }

    /** Backs the {@code enchant_state <op> <key> [amount]} event. Returns the resulting value (0 if
     *  the mutation couldn't be applied — no enchant attached, unknown key, or a persistent write
     *  with no item to save to). */
    public int mutate(ExecutionContext context, String key, String op, int amount) {
        EnchantmentDefinition def = definitionOf(context);
        if (def == null) return 0;

        TransientStateDefinition transientDef = def.getTransientStates().get(key);
        if (transientDef != null) {
            UUID attacker = attackerOf(context);
            UUID victim = victimOf(context);
            return tracker.mutate(attacker, victim, def.getId(), key, transientDef, op, amount);
        }

        return mutatePersistent(context, def, key, op, amount);
    }

    private int mutatePersistent(ExecutionContext context, EnchantmentDefinition def, String key, String op, int amount) {
        ItemStack item = context.get("enchant:item");
        if (item == null || !item.hasItemMeta()) return 0;

        ItemMeta meta = item.getItemMeta();
        Map<String, EnchantStateStore.EnchantInstance> instances = EnchantStateStore.load(meta);
        EnchantStateStore.EnchantInstance instance = instances.get(def.getId().toLowerCase(Locale.ROOT));
        if (instance == null) return 0;

        int defaultValue = defaultOf(def, key);
        int current = instance.getStateValue(key, defaultValue);
        int updated = switch (op == null ? "" : op.toLowerCase(Locale.ROOT)) {
            case "increment" -> current + 1;
            case "add" -> current + amount;
            case "set" -> amount;
            case "reset" -> defaultValue;
            default -> current;
        };

        instance.setStateValue(key, updated);
        EnchantStateStore.save(meta, instances);
        item.setItemMeta(meta);
        return updated;
    }

    private int defaultOf(EnchantmentDefinition def, String key) {
        PersistentStateDefinition persistentDef = def.getPersistentStates().get(key);
        return persistentDef != null ? persistentDef.defaultValue() : 0;
    }

    private EnchantmentDefinition definitionOf(ExecutionContext context) {
        String enchantId = context.get("enchant:id");
        if (enchantId == null) return null;
        return ValmoraAPI.getInstance().getEnchantModule().getRegistry().get(enchantId).orElse(null);
    }

    private UUID attackerOf(ExecutionContext context) {
        LivingEntity caster = context.getCaster();
        return caster != null ? caster.getUniqueId() : null;
    }

    private UUID victimOf(ExecutionContext context) {
        return context.getTarget().map(LivingEntity::getUniqueId).orElse(null);
    }
}
