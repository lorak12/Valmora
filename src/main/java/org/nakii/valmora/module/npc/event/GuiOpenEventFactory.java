package org.nakii.valmora.module.npc.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.HashMap;

/**
 * DSL: gui open <gui-id>
 */
public class GuiOpenEventFactory implements EventFactory {

    private final Valmora plugin;

    public GuiOpenEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "gui"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("open")) return context -> {};
        String guiId = args[1];
        return context -> context.getPlayerCaster().ifPresent(player ->
                plugin.getGuiModule().openGui(player, guiId, new HashMap<>()));
    }
}
