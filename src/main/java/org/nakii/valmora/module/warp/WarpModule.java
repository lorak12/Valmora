package org.nakii.valmora.module.warp;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class WarpModule implements ReloadableModule {

    private final Valmora plugin;
    private WarpManager warpManager;
    private WarpListener listener;

    public WarpModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Warp Module...");
        this.warpManager = new WarpManager(plugin);
        new WarpLoader(plugin, warpManager.getRegistry()).load();
        plugin.getScriptModule().registerEvent(new WarpEventFactory());
        plugin.getScriptModule().registerProvider(new WarpVariableProvider());
        this.listener = new WarpListener(warpManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Warp Module...");
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
        if (warpManager != null) { warpManager.getRegistry().clear(); warpManager = null; }
    }

    @Override public String getId() { return "warp"; }
    @Override public String getName() { return "Warp System"; }

    public WarpManager getWarpManager() { return warpManager; }
}
