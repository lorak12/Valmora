package org.nakii.valmora.module.collection;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class CollectionModule implements ReloadableModule {

    private final Valmora plugin;
    private final CollectionRegistry registry;
    private CollectionListener listener;

    public CollectionModule(Valmora plugin) {
        this.plugin = plugin;
        this.registry = new CollectionRegistry();
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Collection Module...");
        registry.clear();
        new CollectionLoader(plugin, registry).loadCollections();
        plugin.getScriptModule().registerProvider(new CollectionVariableProvider(plugin));
        this.listener = new CollectionListener(plugin, registry);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Collection Module...");
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }
        registry.clear();
    }

    @Override
    public String getId() { return "collections"; }

    @Override
    public String getName() { return "Collection System"; }

    public CollectionRegistry getRegistry() { return registry; }
}
