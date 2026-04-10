package org.nakii.valmora.module.script.variable.providers;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.variable.VariableProvider;
import org.nakii.valmora.module.stat.Stat;

import java.util.Optional;

/**
 * Handles player-related variables: $player.name$, $player.stat.HEALTH$, etc.
 */
public class PlayerVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "player";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        Optional<Player> maybePlayer = context.getPlayerCaster();
        if (maybePlayer.isEmpty()) return null;

        Player player = maybePlayer.get();
        if (path.length == 0) return null;

        String key = path[0];
        if (key.equalsIgnoreCase("name")) return player.getName();

        ValmoraAPI api = ValmoraAPI.getInstance();
        ValmoraProfile profile = api.getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();
        if (profile == null) return null;

        if (key.equalsIgnoreCase("stat") && path.length > 1) {
            String statName = path[1];
            try {
                Stat stat = Stat.valueOf(statName.toUpperCase());
                return profile.getStatManager().getStat(stat);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        if (key.equalsIgnoreCase("var") && path.length > 1) {
            return profile.getVariables().get(path[1]);
        }

        return null;
    }
}
