package org.nakii.valmora.module.notify.io;

import org.bukkit.entity.Player;
import org.nakii.valmora.module.notify.NotifyIO;
import org.nakii.valmora.util.Formatter;

import java.util.Map;

public class ActionBarIO implements NotifyIO {
    @Override public String getName() { return "actionbar"; }

    @Override
    public void send(Player player, String message, Map<String, String> settings) {
        player.sendActionBar(Formatter.format(message));
    }
}
