package org.nakii.valmora.module.script.condition;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

public record HealthCondition(double required) implements Condition {

    @Override
    public boolean evaluate(ExecutionContext context) {
        return context.getPlayerCaster()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .map(p -> p.getHealth() >= required)
                .orElse(false);
    }
}
