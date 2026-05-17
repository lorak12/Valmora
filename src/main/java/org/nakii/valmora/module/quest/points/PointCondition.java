package org.nakii.valmora.module.quest.points;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

public record PointCondition(String category, int required) implements Condition {

    @Override
    public boolean evaluate(ExecutionContext context) {
        return context.getPlayerCaster()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .map(player -> {
                    PointsManager pm = ValmoraAPI.getInstance().getPointsManager();
                    return pm != null && pm.getPoints(player.getUniqueId(), category) >= required;
                })
                .orElse(false);
    }
}
