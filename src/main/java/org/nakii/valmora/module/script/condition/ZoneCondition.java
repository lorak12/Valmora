package org.nakii.valmora.module.script.condition;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.profile.ValmoraPlayer;

public record ZoneCondition(String zoneId) implements Condition {

    @Override
    public boolean evaluate(ExecutionContext context) {
        return context.getPlayerCaster()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .map(p -> {
                    ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(p.getUniqueId());
                    if (vp == null || vp.getActiveProfile() == null) return false;
                    String current = vp.getActiveProfile().getPlayerState().getCurrentZoneId();
                    return zoneId.equalsIgnoreCase(current);
                })
                .orElse(false);
    }
}
