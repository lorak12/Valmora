package org.nakii.valmora.module.gui.sign;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.economy.CoinExpressionParser;
import org.nakii.valmora.module.gui.GuiModule;
import org.nakii.valmora.module.gui.GuiSession;

import java.util.HashMap;
import java.util.UUID;

public class SignInputListener implements Listener {

    private final Valmora plugin;
    private final SignInputManager manager;
    private final GuiModule guiModule;

    public SignInputListener(Valmora plugin, SignInputManager manager, GuiModule guiModule) {
        this.plugin = plugin;
        this.manager = manager;
        this.guiModule = guiModule;
    }

    @EventHandler
    public void onVirtualSignInput(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String propKey = manager.getPendingPropKey(uuid);
        if (propKey == null) return;

        GuiSession session = guiModule.getSession(uuid);
        if (session == null || !session.isInputPending()) {
            manager.clearPending(uuid);
            return;
        }

        manager.clearPending(uuid);
        session.setInputPending(false);
        session.setInputPropKey(null);

        // Cancel the event so the (non-existent) virtual sign text isn't saved
        event.setCancelled(true);

        var lines = event.lines();
        String raw = lines.isEmpty() ? ""
            : PlainTextComponentSerializer.plainText().serialize(lines.get(0)).trim();

        if (!raw.isBlank()) {
            double value = CoinExpressionParser.parse(raw);
            if (value > 0) session.getProps().put(propKey, value);
        }

        if (session.getUpdateTask() != null) {
            session.getUpdateTask().cancel();
            session.setUpdateTask(null);
        }

        String guiId = session.getDefinition().getId();
        var props = new HashMap<>(session.getProps());

        Bukkit.getScheduler().runTask(plugin, () -> guiModule.openGui(player, guiId, props));
    }
}
