package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.Objects;

/**
 * Event for updating custom variables.
 * DSL: variable <add/set/remove> <path> <value>
 */
public class VariableEvent implements EventFactory {

    @Override
    public String getName() {
        return "variable";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3) return context -> {};

        String action = args[0];
        String path = args[1]; // Currently we only support player.var.X
        String rawValue = args[2];

        return context -> {
            if (!path.startsWith("player.var.")) return;

            String varName = path.substring(11);
            context.getPlayerCaster()
                    .map(Player::getUniqueId)
                    .map(uuid -> ValmoraAPI.getInstance().getPlayerManager().getSession(uuid).getActiveProfile())
                    .ifPresent(profile -> {
                        Object current = profile.getVariables().get(varName);
                        if (action.equalsIgnoreCase("set")) {
                            profile.getVariables().put(varName, parseValue(rawValue));
                        } else if (action.equalsIgnoreCase("add")) {
                            double curVal = current instanceof Number n ? n.doubleValue() : 0.0;
                            double addVal = parseDouble(rawValue);
                            profile.getVariables().put(varName, curVal + addVal);
                        } else if (action.equalsIgnoreCase("remove")) {
                            profile.getVariables().remove(varName);
                        }
                    });
        };
    }

    private Object parseValue(String raw) {
        if (raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("false")) return false;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
