package org.nakii.valmora.module.quest.points;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class PointsModule implements ReloadableModule {

    private final Valmora plugin;
    private PointsManager pointsManager;

    public PointsModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Points Module...");
        this.pointsManager = new PointsManager();
        plugin.getScriptModule().registerEvent(new PointEvent());
        plugin.getScriptModule().registerProvider(new PointVariableProvider());
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Points Module...");
        this.pointsManager = null;
    }

    @Override public String getId() { return "points"; }
    @Override public String getName() { return "Points System"; }

    public PointsManager getPointsManager() { return pointsManager; }
}
