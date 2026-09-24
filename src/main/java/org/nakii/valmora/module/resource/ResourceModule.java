package org.nakii.valmora.module.resource;

import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class ResourceModule implements ReloadableModule {

    /** Autosave interval for crash-recovery state — 30s (600 ticks) default. Cheap: usually a
     *  handful of tracked blocks. HC-240: {@code resource.autosave-interval-seconds}. */
    private static final long DEFAULT_AUTOSAVE_INTERVAL_TICKS = 600L;

    private final Valmora plugin;
    private ResourceManager resourceManager;
    private ResourceListener listener;
    private ResourceEnvironmentListener environmentListener;
    private ResourcePipelineLoader pipelineLoader;
    private BukkitTask autosaveTask;

    public ResourceModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Resource Module...");
        this.resourceManager = new ResourceManager(plugin);
        // Restore any mid-progress blocks left over from an unclean shutdown before anything else touches them.
        resourceManager.loadState();

        this.listener = new ResourceListener(resourceManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        this.environmentListener = new ResourceEnvironmentListener(resourceManager);
        plugin.getServer().getPluginManager().registerEvents(environmentListener, plugin);

        long autosaveIntervalTicks = plugin.getConfig().contains("resource.autosave-interval-seconds")
                ? plugin.getConfig().getLong("resource.autosave-interval-seconds") * 20L
                : DEFAULT_AUTOSAVE_INTERVAL_TICKS;
        this.autosaveTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, resourceManager::saveState, autosaveIntervalTicks, autosaveIntervalTicks);

        // Resource pipeline (docs/COMBAT_PIPELINE_ANALYSIS.md) — depends on scriptModule, which
        // registers/enables before this module (see module order in Valmora.onEnable()).
        this.pipelineLoader = new ResourcePipelineLoader(plugin, plugin.getScriptModule());
        pipelineLoader.load();
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Resource Module...");
        if (autosaveTask != null) { autosaveTask.cancel(); autosaveTask = null; }
        if (resourceManager != null) {
            resourceManager.cancelAll();
            // A clean disable already restored the world, so there's nothing to recover next boot.
            resourceManager.clearStateFile();
            resourceManager = null;
        }
        if (environmentListener != null) { HandlerList.unregisterAll(environmentListener); environmentListener = null; }
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
