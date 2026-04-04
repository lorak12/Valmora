package org.nakii.valmora.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;

public class UIManager {
    private final Valmora plugin;
    private final ChatUI chat;
    private final ActionBarUI actionBar;
    private final ScoreboardUI scoreboard;

    public UIManager(Valmora plugin) {
        this.plugin = plugin;
        this.chat = new ChatUI();
        this.actionBar = new ActionBarUI(plugin);
        this.scoreboard = new ScoreboardUI(plugin);

        startUIClock();
    }

    private void startUIClock() {
        // Runs every 2 ticks (10 times a second) for smooth action bar overriding
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                actionBar.tick(player);
                // scoreboard.tick(player);
            }
        }, 0L, 2L);
    }

    public ChatUI getChat() { return chat; }
    public ActionBarUI getActionBar() { return actionBar; }
    public ScoreboardUI getScoreboard() { return scoreboard; }
}