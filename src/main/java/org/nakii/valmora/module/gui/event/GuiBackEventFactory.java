package org.nakii.valmora.module.gui.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Generic GUI "back" navigation (added 2026-08-07). DSL: {@code gui_back}. Closes the current GUI
 * and reopens whichever GUI it was navigated from — the session it was opened from via
 * {@code open_gui} while another GUI was already showing (see {@link OpenGuiEventFactory}). A
 * no-op if there's no parent to return to (e.g. this GUI was opened directly from a command/NPC).
 */
public class GuiBackEventFactory implements EventFactory {

    private final Valmora plugin;

    public GuiBackEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "gui_back";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> {
            if (!(context instanceof GuiExecutionContext guiContext)) return;
            GuiSession session = guiContext.getSession();
            if (session == null) return;
            GuiSession parent = session.getParent();
            if (parent == null) return;

            plugin.getGuiModule().resumeParentSession(guiContext.getPlayerCaster().orElse(session.getPlayer()), session, parent);
        };
    }
}
