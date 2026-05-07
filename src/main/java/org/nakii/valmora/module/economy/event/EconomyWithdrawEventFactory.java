package org.nakii.valmora.module.economy.event;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.economy.CoinExpressionParser;
import org.nakii.valmora.module.economy.EconomyModule;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.util.Formatter;

/**
 * economy_withdraw &lt;amount|all|half|X%&gt;
 * Moves coins from bank to purse.
 */
public class EconomyWithdrawEventFactory implements EventFactory {

    private static final String PREFIX = "<dark_gray>[<gold>Bank<dark_gray>] ";

    private final EconomyModule module;

    public EconomyWithdrawEventFactory(EconomyModule module) {
        this.module = module;
    }

    @Override
    public String getName() { return "economy_withdraw"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return context -> {};
        String rawAmount = args[0];

        return context -> context.getPlayerCaster().ifPresent(player -> {
            var uuid = player.getUniqueId();
            double amount;
            boolean success;

            String lower = rawAmount.toLowerCase();
            if (lower.equals("all")) {
                amount = module.getBank(uuid);
                success = amount > 0;
                if (success) module.withdrawAll(uuid);
            } else if (lower.equals("half")) {
                amount = Math.floor(module.getBank(uuid) / 2.0);
                success = amount > 0;
                if (success) module.withdraw(uuid, amount);
            } else if (lower.endsWith("%")) {
                double pct;
                try { pct = Double.parseDouble(lower.substring(0, lower.length() - 1)); }
                catch (NumberFormatException e) { pct = 0; }
                amount = Math.floor(module.getBank(uuid) * (pct / 100.0));
                success = amount > 0;
                if (success) module.withdraw(uuid, amount);
            } else {
                amount = resolveAmount(rawAmount, context);
                success = amount > 0 && module.withdraw(uuid, amount);
            }

            if (amount <= 0) return; // silent no-op for null/cancelled input

            if (success) {
                sendMsg(player, "<green>Withdrew <gold>" + EconomyModule.formatCoins(amount)
                    + " coins<green>. Purse: <gold>" + EconomyModule.formatCoins(module.getPurse(uuid)) + " coins<green>.");
            } else {
                sendMsg(player, "<red>Insufficient funds. Bank: <gold>"
                    + EconomyModule.formatCoins(module.getBank(uuid)) + " coins<red>.");
            }
        });
    }

    private double resolveAmount(String raw, org.nakii.valmora.api.execution.ExecutionContext ctx) {
        Object resolved = ctx.getVariableResolver().resolve(raw, ctx);
        String str = resolved != null ? resolved.toString() : raw;
        return CoinExpressionParser.parse(str);
    }

    private void sendMsg(Player player, String msg) {
        player.sendMessage(Formatter.format(PREFIX + msg));
    }
}
