package org.nakii.valmora.module.time;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class TimeModule implements ReloadableModule {

    private final Valmora plugin;
    private final TimeManager timeManager;

    public TimeModule(Valmora plugin) {
        this.plugin = plugin;
        this.timeManager = new TimeManager(plugin);
    }

    @Override
    public void onEnable() {
        timeManager.onEnable();
    }

    @Override
    public void onDisable() {
        timeManager.onDisable();
    }

    @Override
    public String getId() {
        return "time";
    }

    @Override
    public String getName() {
        return "Time";
    }

    public TimeManager getTimeManager() {
        return timeManager;
    }
}
