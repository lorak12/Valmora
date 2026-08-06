package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Increments/decrements/adds-to/resets a numeric {@code player.var.<name>} counter on the caster —
 * sugar over the same storage {@code variable add/set player.var.*} uses (see {@link VariableEvent}),
 * so {@code $player.var.<name>$} reads it back with no extra plumbing. Built for the "combo counter"/
 * "hit counter" pipeline stage patterns from docs/COMBAT_PIPELINE_ANALYSIS.md.
 *
 * <p><b>Known limitation:</b> only operates on the caster's profile, i.e. only works when the
 * caster is a player. Counting hits <i>landed on</i> a non-player target (e.g. a mob's own hit
 * counter) would need PDC-backed storage on arbitrary entities, which doesn't exist yet — no-op if
 * the caster isn't a player.
 *
 * DSL: {@code counter <increment|decrement|add|reset> player.var.<name> [amount]}
 * ({@code amount} defaults to {@code 1}; ignored by {@code reset}.)
 */
public class CounterEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "counter";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return context -> {};

        String action = args[0].toLowerCase();
        String path = args[1];
        if (!path.startsWith("player.var.")) return context -> {};
        String varName = path.substring("player.var.".length());

        double amount;
        try {
            amount = args.length > 2 ? Double.parseDouble(args[2]) : 1.0;
        } catch (NumberFormatException e) {
            amount = 1.0;
        }
        final double delta = amount;

        return context -> context.getPlayerCaster()
                .map(Player::getUniqueId)
                .map(uuid -> ValmoraAPI.getInstance().getPlayerManager().getSession(uuid))
                .map(session -> session != null ? session.getActiveProfile() : null)
                .ifPresent(profile -> apply(profile, varName, action, delta));
    }

    private void apply(ValmoraProfile profile, String varName, String action, double delta) {
        Object current = profile.getVariables().get(varName);
        double currentVal = current instanceof Number n ? n.doubleValue() : 0.0;

        switch (action) {
            case "increment", "add" -> profile.getVariables().put(varName, currentVal + delta);
            case "decrement" -> profile.getVariables().put(varName, currentVal - delta);
            case "reset" -> profile.getVariables().put(varName, 0.0);
            default -> { /* unknown action, no-op */ }
        }
    }
}
