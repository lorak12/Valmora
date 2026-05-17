package org.nakii.valmora.module.quest.points;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

public class PointVariableProvider implements VariableProvider {

    @Override public String getNamespace() { return "point"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length < 1) return null;
        return context.getPlayerCaster()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .map(player -> {
                    PointsManager pm = ValmoraAPI.getInstance().getPointsManager();
                    return pm != null ? pm.getPoints(player.getUniqueId(), path[0]) : null;
                })
                .orElse(null);
    }
}
