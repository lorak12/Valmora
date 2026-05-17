package org.nakii.valmora.module.resource;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class ResourceModule implements ReloadableModule {

    private final Valmora plugin;
    private ResourceManager resourceManager;
    private ResourceListener listener;

    public ResourceModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Resource Module...");
        this.resourceManager = new ResourceManager(plugin);
        this.listener = new ResourceListener(resourceManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Resource Module...");
        if (resourceManager != null) { resourceManager.cancelAll(); resourceManager = null; }
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
    }

    @Override public String getId() { return "resource"; }
    @Override public String getName() { return "Resource System"; }

    public ResourceManager getResourceManager() { return resourceManager; }
}
