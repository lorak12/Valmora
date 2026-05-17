package org.nakii.valmora.module.notify.io;

import org.bukkit.entity.Player;
import org.nakii.valmora.module.notify.NotifyIO;
import org.nakii.valmora.util.Formatter;

import java.util.Map;

/**
 * Advancement toast notifications. Falls back to action bar because Paper 1.21
 * does not expose a public API for sending fake advancement toasts without
 * registering a real advancement. A NMS/packet-based implementation can replace
 * this in a future iteration.
 */
public class AdvancementIO implements NotifyIO {
    @Override public String getName() { return "advancement"; }

    @Override
    public void send(Player player, String message, Map<String, String> settings) {
        player.sendActionBar(Formatter.format("✦ " + message));
    }
}
