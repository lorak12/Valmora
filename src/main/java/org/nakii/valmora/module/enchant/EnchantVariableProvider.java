package org.nakii.valmora.module.enchant;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes the enchant instance currently being evaluated (attached onto the dispatch context by
 * {@link EnchantCombatHook}/{@link EnchantDispatcher}/{@link EnchantKillListener} before running
 * that enchant's conditions/modifiers/triggers) via the {@code enchant} namespace:
 * <ul>
 *     <li>{@code $enchant.level$} — the level of the enchant instance currently firing</li>
 *     <li>{@code $enchant.state.<key>$} — that enchant instance's state value for {@code <key>}
 *     (0 if unset), transient or persistent depending on which tier the enchant declared it under
 *     (see {@link org.nakii.valmora.module.enchant.state.EnchantStateEngine}, which resolves that).</li>
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
                var enchantModule = ValmoraAPI.getInstance().getEnchantModule();
                var stateEngine = enchantModule != null ? enchantModule.getStateEngine() : null;
                yield stateEngine != null ? stateEngine.resolve(context, path[1]) : 0;
            }
            default -> null;
        };
    }
}
