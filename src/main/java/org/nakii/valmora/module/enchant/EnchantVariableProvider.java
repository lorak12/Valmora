package org.nakii.valmora.module.enchant;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes the enchant instance currently being evaluated (attached onto the dispatch context by
 * {@link EnchantCombatHook}/{@link EnchantDispatcher}/{@link EnchantKillListener} before running
 * that enchant's conditions/modifiers/triggers) via the {@code enchant} namespace:
 * <ul>
 *     <li>{@code $enchant.level$} — the level of the enchant instance currently firing</li>
 *     <li>{@code $enchant.state.<key>$} — that enchant instance's persistent state value for
 *     {@code <key>} (0 if unset). Reads only persistent (on-item) state for now — transient
 *     combo/hit-counter state (Phase 3) layers a check on top of this once it exists.</li>
 * </ul>
 * No bespoke context subclass is used — the enchant id/level/instance are plain attachments
 * (convention: {@code "enchant:id"}, {@code "enchant:level"}, {@code "enchant:instance"}) on
 * whatever {@link ExecutionContext} the dispatch code is currently using, per the generic
 * attachment-map mechanism {@link ExecutionContext} already provides.
 */
public class EnchantVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "enchant";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;

        return switch (path[0].toLowerCase()) {
            case "level" -> context.get("enchant:level", 0);
            case "state" -> {
                if (path.length < 2) yield null;
                EnchantStateStore.EnchantInstance instance = context.get("enchant:instance");
                yield EnchantStateStore.getPersistentState(instance, path[1], 0);
            }
            default -> null;
        };
    }
}
