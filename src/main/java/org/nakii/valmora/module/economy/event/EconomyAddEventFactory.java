package org.nakii.valmora.module.economy.event;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.economy.CoinExpressionParser;
import org.nakii.valmora.module.economy.EconomyModule;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/** economy_add &lt;amount&gt; — adds coins to the player's purse. */
public class EconomyAddEventFactory implements EventFactory {

    private final EconomyModule module;

    public EconomyAddEventFactory(EconomyModule module) {
        this.module = module;
    }

    @Override
    public String getName() { return "economy_add"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return context -> {};
        String rawAmount = args[0];
        return context -> context.getPlayerCaster().map(Player::getUniqueId).ifPresent(uuid -> {
            double amount = resolveAmount(rawAmount, context);
            if (amount > 0) module.addPurse(uuid, amount);
        });
    }

    private double resolveAmount(String raw, org.nakii.valmora.api.execution.ExecutionContext ctx) {
        Object resolved = ctx.getVariableResolver().resolve(raw, ctx);
        String str = resolved != null ? resolved.toString() : raw;
        return CoinExpressionParser.parse(str);
    }
}
