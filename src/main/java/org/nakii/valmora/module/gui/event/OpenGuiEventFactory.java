package org.nakii.valmora.module.gui.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import java.util.HashMap;
import java.util.Map;

public class OpenGuiEventFactory implements EventFactory {
    private final Valmora plugin;

    public OpenGuiEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "open_gui"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length == 0) return context -> {};
        String guiId = args[0];

        // Phase 5 (docs/REFACTOR/PROGRESS.md Task 20): parse each prop value into an Expression
        // once here, at GUI-definition load time — not on every open_gui invocation (e.g. every
        // button click). Only the per-invocation evaluate() call happens in the returned lambda.
        Map<String, Expression> compiledProps = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String[] split = args[i].split("=", 2);
            if (split.length == 2) {
                compiledProps.put(split[0], plugin.getScriptModule().getExpressionParser().parse(split[1]));
            }
        }

        return context -> context.getPlayerCaster().ifPresent(player -> {
            Map<String, Object> evaluatedProps = new HashMap<>();
            for (Map.Entry<String, Expression> entry : compiledProps.entrySet()) {
                // Evaluate $loop_item$ or any other variable against this invocation's context.
                Object val = entry.getValue().evaluate(context);
                evaluatedProps.put(entry.getKey(), val);
            }
            plugin.getGuiModule().openGui(player, guiId, evaluatedProps);
        });
    }
}