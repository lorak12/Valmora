package org.nakii.valmora.module.notify.io;

import org.bukkit.entity.Player;
import org.nakii.valmora.module.notify.NotifyIO;
import org.nakii.valmora.util.Formatter;

import java.util.Map;

public class ChatIO implements NotifyIO {
    @Override public String getName() { return "chat"; }

    @Override
    public void send(Player player, String message, Map<String, String> settings) {
        player.sendMessage(Formatter.format(message));
    }
}
