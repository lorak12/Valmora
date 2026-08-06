package org.nakii.valmora.module.script.event.impl;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.util.Formatter;

/**
 * Sends a MiniMessage chat message to the caster (variables are resolved first).
 * DSL: {@code notify <message with spaces>}
 */
public class NotifyEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "notify";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length == 0) return context -> {};
        String raw = String.join(" ", args);

        return context -> context.getPlayerCaster().ifPresent(player -> {
            String resolved = context.getVariableResolver().resolveTemplate(raw, context);
            player.sendMessage(Formatter.format(resolved));
        });
    }
}
