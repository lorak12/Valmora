package org.nakii.valmora.module.economy;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EconomyVariableProvider implements VariableProvider {

    private final EconomyModule module;

    public EconomyVariableProvider(EconomyModule module) {
        this.module = module;
    }

    @Override
    public String getNamespace() {
        return "economy";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        Optional<Player> maybePlayer = context.getPlayerCaster();
        if (maybePlayer.isEmpty() || path.length == 0) return null;
        UUID uuid = maybePlayer.get().getUniqueId();
        return switch (path[0].toLowerCase()) {
            case "purse" -> {
                if (path.length > 1 && path[1].equalsIgnoreCase("formatted"))
                    yield EconomyModule.formatCoinsDisplay(module.getPurse(uuid));
                yield module.getPurse(uuid);
            }
            case "bank"  -> module.getBank(uuid);
            case "total" -> module.getTotal(uuid);
            // $economy.ledger.<1-5>$ — one formatted "Recent Transactions" line, newest first;
            // empty string past the end of history (blank lore line in the bank GUI, not an error).
            case "ledger" -> {
                if (path.length < 2) yield null;
                int index;
                try {
                    index = Integer.parseInt(path[1]) - 1;
                } catch (NumberFormatException ex) {
                    yield null;
                }
                List<EconomyLedgerEntry> entries = module.getRecentTransactions(uuid);
                if (index == 0 && entries.isEmpty()) {
                    var plugin = org.nakii.valmora.Valmora.getInstance();
                    yield plugin != null
                            ? plugin.getConfig().getString("economy.messages.no-transactions", "<gray>There are no recent transactions!")
                            : "<gray>There are no recent transactions!";
                }
                yield (index >= 0 && index < entries.size()) ? entries.get(index).formatLine() : "";
            }
            default      -> null;
        };
    }
}
