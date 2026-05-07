package org.nakii.valmora.module.economy;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

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
            case "purse" -> module.getPurse(uuid);
            case "bank"  -> module.getBank(uuid);
            case "total" -> module.getTotal(uuid);
            default      -> null;
        };
    }
}
