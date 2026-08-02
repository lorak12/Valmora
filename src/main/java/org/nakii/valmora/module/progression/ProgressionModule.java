package org.nakii.valmora.module.progression;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.quest.points.PointsManager;

import java.util.Map;

public class ProgressionModule implements ReloadableModule {

    private static final long DAILY_BONUS_WINDOW_MILLIS = 24L * 60 * 60 * 1000;
    private static final long DAILY_CHECK_INTERVAL_TICKS = 20L * 60 * 5; // every 5 minutes

    private final Valmora plugin;
    private final ProgressionRegistry registry;
    private final ProgressionLoader loader;
    private ProgressionManager manager;
    private BukkitTask dailyBonusTask;

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

        this.dailyBonusTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::processDailyBonuses, DAILY_CHECK_INTERVAL_TICKS, DAILY_CHECK_INTERVAL_TICKS);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Progression Module...");
        if (dailyBonusTask != null) { dailyBonusTask.cancel(); dailyBonusTask = null; }
        registry.clear();
        manager = null;
    }

    @Override public String getId() { return "progression"; }
    @Override public String getName() { return "Progression System"; }

    public ProgressionManager getProgressionManager() { return manager; }
    public ProgressionRegistry getProgressionRegistry() { return registry; }

    /** Rolling 24h-since-last-claim daily bonus grant, checked every 5 minutes for all online players. */
    private void processDailyBonuses() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ValmoraPlayer session = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (session == null) continue;
            ValmoraProfile profile = session.getActiveProfile();
            if (profile == null) continue;

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

                    if (now - lastClaim < DAILY_BONUS_WINDOW_MILLIS) continue;

                    PointsManager pm = plugin.getPointsManager();
                    if (pm != null) {
                        int amount = (int) Math.round(bonus.perLevel() * level);
                        pm.addPoints(player.getUniqueId(), bonus.category(), amount);
                    }
                    vars.put(claimKey, now);
                }
            }
        }
    }
}
