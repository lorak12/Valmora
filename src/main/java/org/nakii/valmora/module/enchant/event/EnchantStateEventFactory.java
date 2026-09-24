package org.nakii.valmora.module.enchant.event;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventArgs;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Enchant-domain-specific event (registered by {@code EnchantModule} itself, unlike the generic
 * {@code heal}/{@code damage}/{@code strike_lightning} events from Phase 2) mutating whichever
 * {@code state.transient}/{@code state.persistent} key the currently-dispatching enchant declared —
 * see {@link org.nakii.valmora.module.enchant.state.EnchantStateEngine}, which resolves the tier.
 *
 * <p>DSL: {@code enchant_state <increment|add|set|reset> <key> [amount]} — {@code amount} is only
 * read by {@code add}/{@code set} (may be a literal or a {@code $}-containing formula, same
 * convention as {@link EventArgs#resolveDouble}); {@code increment} always adds exactly 1 and {@code
 * reset} always ignores it. Only valid inside a dispatch context that has an enchant attached
 * (a {@code triggers.<TRIGGER>:} action) — used anywhere else, it's a safe no-op.
 */
public class EnchantStateEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "enchant_state";
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public String usage() {
        return "enchant_state <action> <key> [amount]";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return context -> {};
        String op = args[0];
        String key = args[1];
        String rawAmount = args.length > 2 ? args[2] : "0";

        return context -> {
            var enchantModule = ValmoraAPI.getInstance().getEnchantModule();
            var stateEngine = enchantModule != null ? enchantModule.getStateEngine() : null;
            if (stateEngine == null) return;

            int amount = (int) Math.round(EventArgs.resolveDouble(rawAmount, context));
            stateEngine.mutate(context, key, op, amount);
        };
    }
}
