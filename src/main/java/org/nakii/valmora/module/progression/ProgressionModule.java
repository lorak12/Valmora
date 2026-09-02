package org.nakii.valmora.module.progression;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.quest.points.PointsManager;

import java.util.Map;
import java.util.UUID;

public class ProgressionModule implements ReloadableModule {

    // HC-250: progression.daily-bonus.{window-hours,check-interval-minutes}.
    private long dailyBonusWindowMillis = 24L * 60 * 60 * 1000;
    private long dailyCheckIntervalTicks = 20L * 60 * 5; // every 5 minutes

    private final Valmora plugin;
    private final ProgressionRegistry registry;
    private final ProgressionLoader loader;
    private ProgressionManager manager;
    private BukkitTask dailyBonusTask;
    private ProgressionJoinListener joinListener;

    public ProgressionModule(Valmora plugin) {
        this.plugin = plugin;
        this.registry = new ProgressionRegistry();
        this.loader = new ProgressionLoader(plugin, registry);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Progression Module...");
        this.manager = new ProgressionManager(plugin, registry);
        loader.load();

        plugin.getScriptModule().registerProvider(new ProgressionVariableProvider());
        new ProgressionEventFactory().all().forEach(plugin.getScriptModule()::registerEvent);

        dailyBonusWindowMillis = plugin.getConfig().getLong("progression.daily-bonus.window-hours", 24) * 60 * 60 * 1000;
        dailyCheckIntervalTicks = plugin.getConfig().getLong("progression.daily-bonus.check-interval-minutes", 5) * 20 * 60;
        this.dailyBonusTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::processDailyBonuses, dailyCheckIntervalTicks, dailyCheckIntervalTicks);

        // Join-triggered catch-up: previously a player only ever got their daily bonus on the next
        // 5-minute poll after joining, which could be a long wait right after login.
        this.joinListener = new ProgressionJoinListener(this);
        plugin.getServer().getPluginManager().registerEvents(joinListener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Progression Module...");
        if (dailyBonusTask != null) { dailyBonusTask.cancel(); dailyBonusTask = null; }
        if (joinListener != null) { HandlerList.unregisterAll(joinListener); joinListener = null; }
        registry.clear();
        manager = null;
    }

    @Override public String getId() { return "progression"; }
    @Override public String getName() { return "Progression System"; }

    public ProgressionManager getProgressionManager() { return manager; }
    public ProgressionRegistry getProgressionRegistry() { return registry; }

    /** Rolling 24h-since-last-claim daily bonus grant, checked every 5 minutes for all online players. */
    private void processDailyBonuses() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            processDailyBonusFor(player);
        }
    }

    /** Same check for a single player — used both by the periodic poll and the join catch-up. */
    void processDailyBonusFor(Player player) {
        ValmoraPlayer session = plugin.getPlayerManager().getSession(player.getUniqueId());
        if (session == null) return;
        ValmoraProfile profile = session.getActiveProfile();
        if (profile == null) return;

        long now = System.currentTimeMillis();
        for (ProgressionTreeDefinition tree : registry.values()) {
            for (ProgressionNode node : tree.getNodes().values()) {
                ProgressionNode.DailyBonus bonus = node.getDailyBonus();
                if (bonus == null) continue;

                int level = manager.getNodeLevel(player.getUniqueId(), tree.getId(), node.getId());
                if (level <= 0) continue;

                String claimKey = "progression." + tree.getId() + "." + node.getId() + ".last_daily_claim";
                Map<String, Object> vars = profile.getVariables();
                Object lastClaimObj = vars.get(claimKey);
                long lastClaim = lastClaimObj instanceof Number n ? n.longValue() : 0L;

                if (now - lastClaim < dailyBonusWindowMillis) continue;

                PointsManager pm = plugin.getPointsManager();
                if (pm != null) {
                    int amount = (int) Math.round(bonus.perLevel() * level);
                    pm.addPoints(player.getUniqueId(), bonus.category(), amount);
                    player.sendMessage(org.nakii.valmora.util.Formatter.format(
                            "<gold>✦ <white>Daily bonus: <gold>+" + amount + " " + bonus.category() + "</gold> from <white>"
                                    + node.getId() + "</white>."));
                }
                vars.put(claimKey, now);
            }
        }
    }
}
