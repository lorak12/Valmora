package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.module.stat.StatManager;

/**
 * Modifies a player's base stat value.
 *
 * DSL:
 *   stat_modify add <stat_id> <value>    — adds value to base stat
 *   stat_modify set <stat_id> <value>    — sets base stat to exact value
 *   stat_modify reset <stat_id>          — resets base stat to registry default
 *
 * Value supports $variable$ expressions resolved at execution time.
 */
public class StatModifyEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "stat_modify";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return ctx -> {};

        String action = args[0].toLowerCase();
        String statId = args[1].toLowerCase();
        String rawValue = args.length > 2 ? args[2] : "0";

        return ctx -> {
            ctx.getPlayerCaster().ifPresent(player -> {
                ValmoraProfile profile = ValmoraAPI.getInstance()
                        .getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();
                if (profile == null) return;

                StatManager sm = profile.getStatManager();

                switch (action) {
                    case "add" -> sm.addStat(player, statId, resolveDouble(rawValue, ctx, player));
                    case "set" -> sm.setStat(player, statId, resolveDouble(rawValue, ctx, player));
                    case "reset" -> sm.resetStat(player, statId);
                }
            });
        };
    }

    private double resolveDouble(String raw, org.nakii.valmora.api.execution.ExecutionContext ctx, Player player) {
        String value = raw;
        if (raw.startsWith("$") && raw.endsWith("$")) {
            Object resolved = ctx.getVariableResolver().resolve(raw, ctx);
            value = resolved != null ? resolved.toString() : "0";
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
