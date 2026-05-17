package org.nakii.valmora.module.warp;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.Optional;

public class WarpVariableProvider implements VariableProvider {

    @Override public String getNamespace() { return "warp"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length < 2) return null;
        Optional<Player> maybePlayer = context.getPlayerCaster();
        if (maybePlayer.isEmpty()) return null;

        WarpManager wm = ValmoraAPI.getInstance().getWarpManager();
        if (wm == null) return null;

        String warpId = path[0];
        return wm.getRegistry().get(warpId).map(warp -> switch (path[1].toLowerCase()) {
            case "name" -> warp.getDisplayName();
            case "unlocked" -> wm.isUnlocked(maybePlayer.get(), warp);
            default -> null;
        }).orElse(null);
    }
}
