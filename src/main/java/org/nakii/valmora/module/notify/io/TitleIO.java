package org.nakii.valmora.module.notify.io;

import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.nakii.valmora.module.notify.NotifyIO;
import org.nakii.valmora.util.Formatter;

import java.time.Duration;
import java.util.Map;

public class TitleIO implements NotifyIO {
    @Override public String getName() { return "title"; }

    @Override
    public void send(Player player, String message, Map<String, String> settings) {
        String[] parts = message.split("\\\\n", 2);
        var title = Formatter.format(parts[0]);
        var subtitle = parts.length > 1 ? Formatter.format(parts[1]) : net.kyori.adventure.text.Component.empty();
        int fadeIn = parseInt(settings.get("fadeIn"), 10);
        int stay = parseInt(settings.get("stay"), 70);
        int fadeOut = parseInt(settings.get("fadeOut"), 20);
        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    private int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
