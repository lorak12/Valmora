package org.nakii.valmora.module.death;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

/**
 * Owns player death/respawn/sleep/phantom-insomnia (VANILLA_CONTROL_AUDIT.md §9) — see
 * docs/modules/design/death.md. Registered right after {@code zone} in {@code Valmora.java}: needs
 * {@code stat}, {@code economy}, {@code combat}, and {@code zone} already enabled (all earlier in
 * the module order), and nothing later depends on it.
 */
public class DeathModule implements ReloadableModule {

    private final Valmora plugin;
    private DeathListener deathListener;
    private BedListener bedListener;
    private RespawnAnchorListener respawnAnchorListener;
    private PhantomInsomniaListener phantomInsomniaListener;
    private DeathPipelineLoader pipelineLoader;

    public DeathModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        deathListener = new DeathListener(plugin);
        bedListener = new BedListener();
        respawnAnchorListener = new RespawnAnchorListener();
        phantomInsomniaListener = new PhantomInsomniaListener();

        plugin.getServer().getPluginManager().registerEvents(deathListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(bedListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(respawnAnchorListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(phantomInsomniaListener, plugin);

        pipelineLoader = new DeathPipelineLoader(plugin, plugin.getScriptModule());
        pipelineLoader.load();
    }

    @Override
    public void onDisable() {
        if (deathListener != null) { HandlerList.unregisterAll(deathListener); deathListener = null; }
        if (bedListener != null) { HandlerList.unregisterAll(bedListener); bedListener = null; }
        if (respawnAnchorListener != null) { HandlerList.unregisterAll(respawnAnchorListener); respawnAnchorListener = null; }
        if (phantomInsomniaListener != null) { HandlerList.unregisterAll(phantomInsomniaListener); phantomInsomniaListener = null; }
        if (plugin.getScriptModule() != null) {
            plugin.getScriptModule().getHookBus().clearYamlStages("player:");
        }
        pipelineLoader = null;
    }

    @Override
    public String getId() {
        return "death";
    }

    @Override
    public String getName() {
        return "Death & Respawn";
    }
}
