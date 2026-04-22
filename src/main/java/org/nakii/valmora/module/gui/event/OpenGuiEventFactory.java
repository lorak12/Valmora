package org.nakii.valmora.module.gui.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
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
        
        // Extract raw key=value props
        Map<String, String> rawProps = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String[] split = args[i].split("=", 2);
            if (split.length == 2) {
                rawProps.put(split[0], split[1]);
            }
        }

        return context -> context.getPlayerCaster().ifPresent(player -> {
            Map<String, Object> evaluatedProps = new HashMap<>();
            for (Map.Entry<String, String> entry : rawProps.entrySet()) {
                // Evaluate $loop_item$ or any other variable
                Object val = plugin.getScriptModule().getExpressionEvaluator().evaluate(entry.getValue(), context);
                evaluatedProps.put(entry.getKey(), val);
            }
            plugin.getGuiModule().openGui(player, guiId, evaluatedProps);
        });
    }
}