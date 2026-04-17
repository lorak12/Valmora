package org.nakii.valmora.module.gui.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

public class CloseEventFactory implements EventFactory {

    private final Valmora plugin;

    public CloseEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "close";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> {
            if (context instanceof GuiExecutionContext guiContext) {
                guiContext.getPlayerCaster().ifPresent(player -> 
                    plugin.getGuiModule().closeGuiSession(player));
                guiContext.getPlayerCaster().ifPresent(player -> player.closeInventory());
            } else {
                context.getPlayerCaster().ifPresent(player -> player.closeInventory());
            }
        };
    }
}
