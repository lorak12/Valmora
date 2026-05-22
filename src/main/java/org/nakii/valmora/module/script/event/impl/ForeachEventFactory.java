package org.nakii.valmora.module.script.event.impl;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.Collection;

/**
 * Executes an inner event for each targeted player.
 *
 * DSL:
 *   foreach @all <inner_event...>           — all online players
 *   foreach @nearby:<radius> <inner_event...> — players within radius of caster
 *
 * Note: The tokens "notify" and "delay:<n>" in the outer event string are
 * consumed by the EventParser before reaching this factory. Avoid them in
 * the inner event when using foreach.
 */
public class ForeachEventFactory implements EventFactory {

    private final ScriptModule module;

    public ForeachEventFactory(ScriptModule module) {
        this.module = module;
    }

    @Override
    public String getName() {
        return "foreach";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return ctx -> {};

        String selector = args[0];

        // Reconstruct inner event string from remaining args
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(' ');
            sb.append(args[i]);
        }
        String innerEventStr = sb.toString();
        CompiledEvent inner = module.getEventParser().parse(innerEventStr);

        if (selector.equalsIgnoreCase("@all")) {
            return ctx -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    inner.execute(contextFor(p));
                }
            };
        }

        if (selector.toLowerCase().startsWith("@nearby:")) {
            double radius;
            try {
                radius = Double.parseDouble(selector.substring(8));
            } catch (NumberFormatException e) {
                return ctx -> {};
            }
            final double finalRadius = radius;
            return ctx -> {
                if (ctx.getLocation() == null) return;
                Collection<Player> nearby = ctx.getLocation().getNearbyPlayers(finalRadius);
                for (Player p : nearby) {
                    inner.execute(contextFor(p));
                }
            };
        }

        return ctx -> {};
    }

    private ExecutionContext contextFor(Player player) {
        return new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());
    }
}
