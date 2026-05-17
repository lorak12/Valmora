package org.nakii.valmora.module.notify.io;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.nakii.valmora.module.notify.NotifyIO;
import org.nakii.valmora.util.Formatter;

import java.util.Map;

public class BossBarIO implements NotifyIO {

    private final Plugin plugin;

    public BossBarIO(Plugin plugin) { this.plugin = plugin; }

    @Override public String getName() { return "bossbar"; }

    @Override
    public void send(Player player, String message, Map<String, String> settings) {
        BossBar.Color color = parseColor(settings.getOrDefault("barColor", "WHITE"));
        BossBar.Overlay style = parseOverlay(settings.getOrDefault("style", "PROGRESS"));
        float progress = parseFloat(settings.get("progress"), 1.0f);
        int stay = parseInt(settings.get("stay"), 70);

        BossBar bar = BossBar.bossBar(Formatter.format(message), progress, color, style);
        player.showBossBar(bar);
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.hideBossBar(bar), stay);
    }

    private BossBar.Color parseColor(String s) {
        try { return BossBar.Color.valueOf(s.toUpperCase()); } catch (Exception e) { return BossBar.Color.WHITE; }
    }

    private BossBar.Overlay parseOverlay(String s) {
        try { return BossBar.Overlay.valueOf(s.toUpperCase()); } catch (Exception e) { return BossBar.Overlay.PROGRESS; }
    }

    private float parseFloat(String s, float def) {
        if (s == null) return def;
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; }
    }

    private int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
