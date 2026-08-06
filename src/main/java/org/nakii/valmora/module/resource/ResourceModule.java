package org.nakii.valmora.module.resource;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class ResourceModule implements ReloadableModule {

    private final Valmora plugin;
    private ResourceManager resourceManager;
    private ResourceListener listener;
    private ResourcePipelineLoader pipelineLoader;

    public ResourceModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Resource Module...");
        this.resourceManager = new ResourceManager(plugin);
        this.listener = new ResourceListener(resourceManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // Resource pipeline (docs/COMBAT_PIPELINE_ANALYSIS.md) — depends on scriptModule, which
        // registers/enables before this module (see module order in Valmora.onEnable()).
        this.pipelineLoader = new ResourcePipelineLoader(plugin, plugin.getScriptModule());
        pipelineLoader.load();
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Resource Module...");
        if (resourceManager != null) { resourceManager.cancelAll(); resourceManager = null; }
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
        if (plugin.getScriptModule() != null) {
            plugin.getScriptModule().getHookBus().clearYamlStages(ResourcePipelineLoader.POINT_PREFIX);
        }
        pipelineLoader = null;
    }

    @Override public String getId() { return "resource"; }
    @Override public String getName() { return "Resource System"; }

    public ResourceManager getResourceManager() { return resourceManager; }
}
