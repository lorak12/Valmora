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
    public int minArgs() {
        return 3;
    }

    @Override
    public String usage() {
        return "variable <set|add|remove> <path> <value...>";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3) return context -> {};

        String action = args[0];
        String path = args[1]; // Currently we only support player.var.X
        // Everything after the path is the value, so an unquoted formula works:
        // `variable set player.var.x $player.var.x$ + 1` (previously only "$player.var.x$" was used).
        String rawValue = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        if (!action.equalsIgnoreCase("set") && !action.equalsIgnoreCase("add") && !action.equalsIgnoreCase("remove")) {
            org.nakii.valmora.infrastructure.config.diag.Diagnostics.error("variable: unknown action '" + action
                    + "' — use set, add or remove", org.nakii.valmora.infrastructure.config.diag.Suggestions.hint(action,
                    java.util.List.of("set", "add", "remove")));
        }

        return context -> {
            // Full expression evaluation (added 2026-08-07) — was single-token "$var$"
            // substitution only, now handles "$player.stat.DAMAGE$ * 2"-style formulas, matching
            // ExecutionContext.resolveDouble()'s semantics. Values with no "$" at all (the common
            // case — a plain literal like a tag name or zone id) are left untouched exactly as
            // before, since routing every value through the expression parser risks mangling
            // literal strings containing characters the tokenizer doesn't expect (hyphens, etc.).
            final String resolvedValue;
            if (rawValue.contains("$")) {
                Object evaluated = ValmoraAPI.getInstance().getScriptModule().getExpressionEvaluator().evaluate(rawValue, context);
                resolvedValue = evaluated != null ? String.valueOf(evaluated) : rawValue;
            } else {
                resolvedValue = rawValue;
            }

            // player.stat.<id> (added 2026-08-07) — routes into StatManager like the dedicated
            // `stat_modify` event, so `variable` becomes a single generic entry point instead of
            // stat mutation only being reachable through a separate event name.
            if (path.startsWith("player.stat.")) {
                String statId = path.substring(12);
                context.getPlayerCaster().ifPresent(player -> {
                    var vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
                    var profile = vp != null ? vp.getActiveProfile() : null;
                    if (profile == null) return;
                    double val = parseDouble(resolvedValue);
                    if (action.equalsIgnoreCase("set")) {
                        profile.getStatManager().setStat(player, statId, val);
                    } else if (action.equalsIgnoreCase("add")) {
                        profile.getStatManager().addStat(player, statId, val);
                    } else if (action.equalsIgnoreCase("remove")) {
                        profile.getStatManager().resetStat(player, statId);
                    }
                });
                return;
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
