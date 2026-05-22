package org.nakii.valmora.module.ui;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

import java.io.File;
import java.util.List;

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
        UIConfig config = loadUIConfig();
        scoreboard.setConfig(config);
        actionBar.setConfig(config);

        connectionListener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
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
        uiClockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                actionBar.tick(player);
                scoreboard.tick(player);
            }
        }, 0L, 2L);
    }

    private UIConfig loadUIConfig() {
        File file = new File(plugin.getDataFolder(), "ui.yml");
        if (!file.exists()) {
            plugin.saveResource("ui.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String title = cfg.getString("scoreboard.title", "<gold><bold>VALMORA RPG");
        List<String> lines = cfg.getStringList("scoreboard.lines");

        String actionBarDefault = cfg.getString("action-bar.default",
                "<red>❤ $player.hp$/$player.max_hp$ <dark_gray>| <green>❈ $player.stat.defense$ Defense <dark_gray>| <aqua>⛨ $player.mana$/$player.max_mana$ Mana");

        String tabHeader = cfg.getString("tab.header", "");
        String tabFooter = cfg.getString("tab.footer", "");

        plugin.getLogger().info("[UI] Loaded ui.yml: " + lines.size() + " scoreboard line(s).");
        return new UIConfig(title, lines, actionBarDefault, tabHeader, tabFooter);
    }

    public ChatUI getChat()           { return chat; }
    public ActionBarUI getActionBar() { return actionBar; }
    public ScoreboardUI getScoreboard() { return scoreboard; }
}
