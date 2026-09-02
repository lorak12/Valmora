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
import org.nakii.valmora.util.DebugManager;

import java.io.File;
import java.util.List;

public class UIManager implements ReloadableModule {
    private final Valmora plugin;
    private final ChatUI chat;
    private final ActionBarUI actionBar;
    private final ScoreboardUI scoreboard;
    private BukkitTask uiClockTask;
    private Listener connectionListener;
    // Clock ticks every 2 ticks (10x/sec) for every online player — logging every tick would drown
    // the console, so only a periodic heartbeat is emitted (~ every 10s), same pattern as
    // CombatModule's RegenTask.
    private int clockRunCount = 0;
    private static final int SUMMARY_INTERVAL_RUNS = 50;

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
            clockRunCount++;
            if (clockRunCount % SUMMARY_INTERVAL_RUNS == 0) {
                DebugManager.log("ui", "clock tick #" + clockRunCount + " — ticked "
                        + Bukkit.getOnlinePlayers().size() + " online player(s)");
            }
        }, 0L, 2L);
    }

    private static final String DEFAULT_TITLE = "<gold><bold>VALMORA RPG";
    private static final String DEFAULT_ACTION_BAR =
            "<red>❤ $player.hp$/$player.max_hp$ <dark_gray>| <green>❈ $player.stat.defense$ Defense <dark_gray>| <aqua>⛨ $player.mana$/$player.max_mana$ Mana";

    private UIConfig loadUIConfig() {
        File file = new File(plugin.getDataFolder(), "ui.yml");
        if (!file.exists()) {
            plugin.saveResource("ui.yml", false);
        }

        // Guarded (added 2026-08-07, was unguarded) — a corrupt ui.yml previously threw straight
        // out of onEnable(); now it logs a warning and falls back to hardcoded defaults, matching
        // the same fix already applied to time.yml. Not switched to the generic YamlLoader<T> —
        // that loader's contract is "one folder of files, each contributing named registry
        // entries"; ui.yml is a single fixed-shape config file, not a fit for that shape.
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            String title = cfg.getString("scoreboard.title", DEFAULT_TITLE);
            List<String> lines = cfg.getStringList("scoreboard.lines");
            String actionBarDefault = cfg.getString("action-bar.default", DEFAULT_ACTION_BAR);
            String tabHeader = cfg.getString("tab.header", "");
            String tabFooter = cfg.getString("tab.footer", "");

            plugin.getLogger().info("[UI] Loaded ui.yml: " + lines.size() + " scoreboard line(s).");
            return new UIConfig(title, lines, actionBarDefault, tabHeader, tabFooter);
        } catch (Exception e) {
            plugin.getLogger().warning("[UI] Failed to load ui.yml (" + e.getMessage() + ") — falling back to defaults.");
            return new UIConfig(DEFAULT_TITLE, List.of(), DEFAULT_ACTION_BAR, "", "");
        }
    }

    public ChatUI getChat()           { return chat; }
    public ActionBarUI getActionBar() { return actionBar; }
    public ScoreboardUI getScoreboard() { return scoreboard; }
}
