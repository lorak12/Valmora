package org.nakii.valmora.module.zone;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.Optional;

public class ZoneVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() { return "zone"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        Optional<Player> maybePlayer = context.getPlayerCaster();
        if (maybePlayer.isEmpty() || path.length == 0) return null;

        ZoneManager zm = ValmoraAPI.getInstance().getZoneManager();
        if (zm == null) return null;

        Optional<ZoneDefinition> zone = zm.getCurrentZone(maybePlayer.get());
        return switch (path[0].toLowerCase()) {
            case "id" -> zone.map(ZoneDefinition::getId).orElse(null);
            case "name" -> zone.map(ZoneDefinition::getDisplayName).orElse(null);
            case "pvp" -> zone.map(ZoneDefinition::isPvpEnabled).orElse(false);
            default -> null;
        };
    }
}
