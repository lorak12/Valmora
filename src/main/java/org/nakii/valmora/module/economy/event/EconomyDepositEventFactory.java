package org.nakii.valmora.module.economy.event;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.economy.CoinExpressionParser;
import org.nakii.valmora.module.economy.EconomyModule;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.util.Formatter;

/**
 * economy_deposit &lt;amount|all|half&gt;
 * Moves coins from purse to bank.
 */
public class EconomyDepositEventFactory implements EventFactory {

    private static final String PREFIX = "<dark_gray>[<gold>Bank<dark_gray>] ";

    private final EconomyModule module;

    public EconomyDepositEventFactory(EconomyModule module) {
        this.module = module;
    }

    @Override
    public String getName() { return "economy_deposit"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return context -> {};
        String rawAmount = args[0];

        return context -> context.getPlayerCaster().ifPresent(player -> {
            var uuid = player.getUniqueId();
            double amount;
            boolean success;

            switch (rawAmount.toLowerCase()) {
                case "all" -> {
                    amount = module.getPurse(uuid);
                    success = amount > 0;
                    if (success) module.depositAll(uuid);
                }
                case "half" -> {
                    amount = Math.floor(module.getPurse(uuid) / 2.0);
                    success = amount > 0;
                    if (success) module.deposit(uuid, amount);
                }
                default -> {
                    amount = resolveAmount(rawAmount, context);
                    success = amount > 0 && module.deposit(uuid, amount);
                }
            }

            if (amount <= 0) return; // silent no-op for null/cancelled input

            if (success) {
                sendMsg(player, "<green>Deposited <gold>" + EconomyModule.formatCoins(amount)
                    + " coins<green>. Bank: <gold>" + EconomyModule.formatCoins(module.getBank(uuid)) + " coins<green>.");
            } else {
                sendMsg(player, "<red>Insufficient funds. Purse: <gold>"
                    + EconomyModule.formatCoins(module.getPurse(uuid)) + " coins<red>.");
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
