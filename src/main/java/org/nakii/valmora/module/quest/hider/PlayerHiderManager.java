package org.nakii.valmora.module.quest.hider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PlayerHiderManager {

    private static final long TICK_INTERVAL = 20L;

    private final Valmora plugin;
    private final List<PlayerHiderEntry> entries = new ArrayList<>();
    private BukkitTask task;

    public PlayerHiderManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public void addEntry(PlayerHiderEntry entry) {
        entries.add(entry);
    }

    public void clear() {
        entries.clear();
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        // Restore visibility for all players before stopping
        restoreAll();
    }

    private void tick() {
        if (entries.isEmpty()) return;
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player source : online) {
            for (Player target : online) {
                if (source == target) continue;
                boolean shouldHide = false;
                for (PlayerHiderEntry entry : entries) {
                    if (matchesSource(source, entry) && matchesTarget(target, entry)) {
                        shouldHide = true;
                        break;
                    }
                }
                if (shouldHide) source.hidePlayer(plugin, target);
                else source.showPlayer(plugin, target);
            }
        }
    }

    private boolean matchesSource(Player player, PlayerHiderEntry entry) {
        return evaluate(player, entry.getSourceConditions());
    }

    private boolean matchesTarget(Player player, PlayerHiderEntry entry) {
        return evaluate(player, entry.getTargetConditions());
    }

    private boolean evaluate(Player player, List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) return true;
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
        Condition group = plugin.getScriptModule().getConditionParser().parseList(conditions);
        return group.evaluate(ctx);
    }

    private void restoreAll() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player source : online) {
            for (Player target : online) {
                if (source != target) source.showPlayer(plugin, target);
            }
        }
    }
}
