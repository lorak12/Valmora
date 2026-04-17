package org.nakii.valmora.module.gui.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

public class OpenGuiEventFactory implements EventFactory {

    private final Valmora plugin;

    public OpenGuiEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "open_gui";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length == 0) return context -> {};
        String guiId = args[0];
        
        return context -> context.getPlayerCaster().ifPresent(player -> 
            plugin.getGuiModule().openGui(player, guiId));
    }
}
