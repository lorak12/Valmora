package org.nakii.valmora.module.fishing;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class FishingModule implements ReloadableModule {

    private final Valmora plugin;
    private FishingManager fishingManager;
    private FishingListener listener;

    public FishingModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Fishing Module...");
        this.fishingManager = new FishingManager(plugin);
        new FishingLoader(plugin, fishingManager.getRegistry()).load();
        this.listener = new FishingListener(fishingManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Fishing Module...");
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
        if (fishingManager != null) { fishingManager.getRegistry().clear(); fishingManager = null; }
    }

    @Override public String getId() { return "fishing"; }
    @Override public String getName() { return "Fishing System"; }

    public FishingManager getFishingManager() { return fishingManager; }
}
