package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;


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
            final String resolvedValue;
            if (rawValue.startsWith("$") && rawValue.endsWith("$")) {
                Object resolved = context.getVariableResolver().resolve(rawValue.substring(1, rawValue.length() - 1), context);
                resolvedValue = resolved != null ? resolved.toString() : rawValue;
            } else {
                resolvedValue = rawValue;
            }

            if (path.startsWith("prop.") && context instanceof org.nakii.valmora.module.gui.GuiExecutionContext guiCtx && guiCtx.getSession() != null) {
                String propName = path.substring(5);
                java.util.Map<String, Object> props = guiCtx.getSession().getProps();
                Object current = props.get(propName);
                if (action.equalsIgnoreCase("set")) {
                    props.put(propName, parseValue(resolvedValue));
                } else if (action.equalsIgnoreCase("add")) {
                    double curVal = current instanceof Number n ? n.doubleValue() : 0.0;
                    double addVal = parseDouble(resolvedValue);
                    props.put(propName, curVal + addVal);
                } else if (action.equalsIgnoreCase("remove")) {
                    props.remove(propName);
                }
                return;
            }

            if (!path.startsWith("player.var.")) return;

            String varName = path.substring(11);
            context.getPlayerCaster().ifPresent(player -> {
                var vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
                var profile = vp != null ? vp.getActiveProfile() : null;
                if (profile == null) return;

                Object current = profile.getVariables().get(varName);
                boolean changed = false;
                if (action.equalsIgnoreCase("set")) {
                    profile.getVariables().put(varName, parseValue(resolvedValue));
                    changed = true;
                } else if (action.equalsIgnoreCase("add")) {
                    double curVal = current instanceof Number n ? n.doubleValue() : 0.0;
                    double addVal = parseDouble(resolvedValue);
                    profile.getVariables().put(varName, curVal + addVal);
                    changed = true;
                } else if (action.equalsIgnoreCase("remove")) {
                    profile.getVariables().remove(varName);
                }

                // Drives the VARIABLE quest objective type — previously nothing triggered
                // progress from variable changes at all despite QuestVariableProvider reading them.
                if (changed) {
                    var questManager = ValmoraAPI.getInstance().getQuestManager();
                    if (questManager != null) questManager.checkVariableObjective(player, varName);
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
