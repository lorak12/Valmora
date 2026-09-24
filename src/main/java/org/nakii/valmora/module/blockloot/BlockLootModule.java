package org.nakii.valmora.module.blockloot;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

/**
 * Global (server-wide, zone-independent) per-block-type loot overrides —
 * VANILLA_CONTROL_AUDIT.md §1/§4's "generic BlockDropItemEvent/BlockExpEvent control for arbitrary
 * blocks" gap, a user-directed content-authoring pass. See {@code docs/modules/design/blockloot.md}.
 *
 * <p>Distinct from the zone-scoped {@code resource} module's {@code resource-blocks:} — that one
 * takes precedence for any block a zone also configures (see {@code module.item.LootListener}'s
 * existing defer checks, which this module's own defer check sits alongside).
 */
public class BlockLootModule implements ReloadableModule {

    private final Valmora plugin;
    private BlockLootRegistry registry;
    private BlockLootManager manager;
    private BlockLootLoader loader;
    private BlockLootListener listener;

    public BlockLootModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        registry = new BlockLootRegistry(plugin);

        boolean enabled = plugin.getConfig().getBoolean("block-loot.enabled", true);
        if (!enabled) {
            plugin.getLogger().info("Block Loot Overrides module disabled by config (block-loot.enabled: false).");
            return;
        }

        manager = new BlockLootManager(plugin, registry);
        loader = new BlockLootLoader(plugin, registry);
        loader.load();

        listener = new BlockLootListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void onDisable() {
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
        if (registry != null) { registry.clear(); registry = null; }
        manager = null;
        loader = null;
    }

    @Override public String getId() { return "block_loot"; }
    @Override public String getName() { return "Block Loot Overrides"; }

    public BlockLootRegistry getRegistry() { return registry; }
}
