package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.item.TargetResolver;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Adds or removes coins via {@link org.nakii.valmora.api.economy.EconomyService} — the script-DSL
 * counterpart to the {@code GIVE_COINS}/{@code TAKE_COINS} ability mechanics, for use directly
 * inside pipeline stages / event lists. Non-player resolved targets are silently skipped.
 *
 * DSL: {@code give_coins <amount> [<target-selector>]}, {@code take_coins <amount> [<target-selector>]}
 * ({@code amount} supports a single {@code $variable$} token or a literal number; {@code target-selector}
 * defaults to {@code @self} — see {@link TargetResolver}.)
 */
public class EconomyCoinsEventFactory implements EventFactory {

    private final boolean give;

    public EconomyCoinsEventFactory(boolean give) {
        this.give = give;
    }

    @Override
    public String getName() {
        return give ? "give_coins" : "take_coins";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return context -> {};
        String rawAmount = args[0];
        String selector = args.length > 1 ? args[1] : "@self";

        return context -> {
            double amount = resolveDouble(rawAmount, context);
            if (amount <= 0) return;
            var economy = ValmoraAPI.getInstance().getEconomy();
            if (economy == null) return;

            for (LivingEntity target : TargetResolver.resolve(selector, context)) {
                if (!(target instanceof Player player)) continue;
                if (give) {
                    economy.addCoins(player, amount);
                } else {
                    economy.removeCoins(player, amount);
                }
            }
        };
    }

    private double resolveDouble(String raw, org.nakii.valmora.api.execution.ExecutionContext ctx) {
        String value = raw;
        if (raw.startsWith("$") && raw.endsWith("$")) {
            Object resolved = ctx.getVariableResolver().resolve(raw, ctx);
            value = resolved != null ? resolved.toString() : "0";
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
