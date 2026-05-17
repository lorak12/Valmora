package org.nakii.valmora.module.zone;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class ZoneModule implements ReloadableModule {

    private final Valmora plugin;
    private final ZoneRegistry registry;
    private final ZoneLoader loader;
    private ZoneManager manager;
    private ZoneListener listener;
    private ZoneWandListener wandListener;

    public ZoneModule(Valmora plugin) {
        this.plugin = plugin;
        this.registry = new ZoneRegistry();
        this.loader = new ZoneLoader(plugin, registry);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Zone Module...");
        loader.loadZones();
        this.manager = new ZoneManager(plugin, registry);
        this.listener = new ZoneListener(plugin, manager);
        this.wandListener = new ZoneWandListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer().getPluginManager().registerEvents(wandListener, plugin);
        plugin.getScriptModule().registerProvider(new ZoneVariableProvider());
        manager.startSpawnerTask();
        manager.startMobHomeTask();
        manager.startVisualizationTask();
        manager.startSelectionTask();
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Zone Module...");
        if (manager != null) {
            manager.stopSpawnerTask();
            manager.stopMobHomeTask();
            manager.stopVisualizationTask();
            manager.stopSelectionTask();
        }
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
        if (wandListener != null) { HandlerList.unregisterAll(wandListener); wandListener = null; }
        registry.clear();
        manager = null;
    }

    @Override public String getId() { return "zone"; }
    @Override public String getName() { return "Zone System"; }

    public ZoneManager getZoneManager() { return manager; }
    public ZoneRegistry getZoneRegistry() { return registry; }
}
