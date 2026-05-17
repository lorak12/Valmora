package org.nakii.valmora.module.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class UIManager implements ReloadableModule {
    private final Valmora plugin;
    private final ChatUI chat;
    private final ActionBarUI actionBar;
    private final ScoreboardUI scoreboard;
    private BukkitTask uiClockTask;
    private Listener connectionListener;

    public UIManager(Valmora plugin) {
        this.plugin = plugin;
        this.chat = new ChatUI();
        this.actionBar = new ActionBarUI(plugin);
        this.scoreboard = new ScoreboardUI(plugin);
    }

    @Override
    public void onEnable() {
        connectionListener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                // Clear stale board so tick() recreates and reassigns it on next cycle.
                scoreboard.removePlayer(e.getPlayer().getUniqueId());
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                scoreboard.removePlayer(e.getPlayer().getUniqueId());
            }
        };
        plugin.getServer().getPluginManager().registerEvents(connectionListener, plugin);

        startUIClock();
    }

    @Override
    public void onDisable() {
        if (uiClockTask != null) {
            uiClockTask.cancel();
            uiClockTask = null;
        }
        if (connectionListener != null) {
            HandlerList.unregisterAll(connectionListener);
            connectionListener = null;
        }
    }

    @Override
    public String getId() {
        return "ui";
    }

    private void startUIClock() {
        if (uiClockTask != null) uiClockTask.cancel();
        
        // Runs every 2 ticks (10 times a second) for smooth action bar overriding
        uiClockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                actionBar.tick(player);
                scoreboard.tick(player);
            }
        }, 0L, 2L);
    }

    public ChatUI getChat() { return chat; }
    public ActionBarUI getActionBar() { return actionBar; }
    public ScoreboardUI getScoreboard() { return scoreboard; }
}
