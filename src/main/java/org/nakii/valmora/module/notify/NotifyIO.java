package org.nakii.valmora.module.notify;

import org.bukkit.entity.Player;

import java.util.Map;

public interface NotifyIO {
    String getName();
    void send(Player player, String message, Map<String, String> settings);
}
