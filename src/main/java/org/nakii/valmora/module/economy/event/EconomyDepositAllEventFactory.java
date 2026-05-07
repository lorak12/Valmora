package org.nakii.valmora.module.economy.event;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.economy.EconomyModule;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.util.Formatter;

/** economy_deposit_all — moves all coins from purse to bank. */
public class EconomyDepositAllEventFactory implements EventFactory {

    private static final String PREFIX = "<dark_gray>[<gold>Bank<dark_gray>] ";

    private final EconomyModule module;

    public EconomyDepositAllEventFactory(EconomyModule module) {
        this.module = module;
    }

    @Override
    public String getName() { return "economy_deposit_all"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> context.getPlayerCaster().ifPresent(player -> {
            var uuid = player.getUniqueId();
            double purse = module.getPurse(uuid);
            if (purse <= 0) {
                player.sendMessage(Formatter.format(PREFIX + "<red>Your purse is empty."));
                return;
            }
            module.depositAll(uuid);
            player.sendMessage(Formatter.format(PREFIX + "<green>Deposited <gold>"
                + EconomyModule.formatCoins(purse) + " coins<green>. Bank: <gold>"
                + EconomyModule.formatCoins(module.getBank(uuid)) + " coins<green>."));
        });
    }
}
