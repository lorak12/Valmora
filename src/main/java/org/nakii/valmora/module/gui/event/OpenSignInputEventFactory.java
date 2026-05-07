package org.nakii.valmora.module.gui.event;

import org.bukkit.Bukkit;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiModule;
import org.nakii.valmora.module.gui.sign.SignInputManager;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.Arrays;

/**
 * Opens a sign editor for the player and stores the parsed result in a GUI session prop.
 * DSL: open_sign_input &lt;prop_key&gt; [placeholder text...]
 */
public class OpenSignInputEventFactory implements EventFactory {

    private final Valmora plugin;
    private final SignInputManager signInputManager;
    private final GuiModule guiModule;

    public OpenSignInputEventFactory(Valmora plugin, SignInputManager signInputManager, GuiModule guiModule) {
        this.plugin = plugin;
        this.signInputManager = signInputManager;
        this.guiModule = guiModule;
    }

    @Override
    public String getName() { return "open_sign_input"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return context -> {};
        String propKey = args[0];
        String placeholder = args.length > 1
            ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
            : null;

        return context -> {
            if (!(context instanceof GuiExecutionContext guiCtx)) return;
            var session = guiCtx.getSession();
            if (session == null) return;
            var player = session.getPlayer();

            // Flag the session so InventoryCloseEvent won't destroy it
            session.setInputPending(true);
            session.setInputPropKey(propKey);

            // Schedule: close inventory first, then open sign on the same tick
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory(); // triggers InventoryCloseEvent; flag suppresses session destruction
                boolean opened = signInputManager.openSign(player, propKey, placeholder);
                
                if (!opened) {
                    // Pool exhausted — abort sign input and reopen the GUI
                    session.setInputPending(false);
                    session.setInputPropKey(null);
                    plugin.getLogger().warning("[SignInput] Pool exhausted for player " + player.getName());
                    guiModule.openGui(player, session.getDefinition().getId(), session.getProps());
                }
            });
        };
    }
}
