package org.nakii.valmora.module.fishing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.fishing.flow.BiteState;
import org.nakii.valmora.module.fishing.flow.CatchState;
import org.nakii.valmora.module.fishing.flow.FishingFlows;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.util.DebugManager;

public class FishingManager {

    private final Valmora plugin;
    private final Registry<FishingLootTable> registry = new SimpleRegistry<>();

    public FishingManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public Registry<FishingLootTable> getRegistry() { return registry; }

    public Valmora getPlugin() { return plugin; }

    /**
     * Resolves a completed catch by running {@code fishing_pipeline.yml}'s {@code catch} flow
     * ({@link FishingFlows#CATCH}). {@code hookLocation} (per TESTING_GUIDE.md's FISH-03
     * expectation) is where any rolled sea creature spawns — the bobber's location, not the
     * player's.
     *
     * @return whether anything was caught
     */
    public boolean handleCatch(Player player, Location hookLocation) {
        CatchState state = new CatchState(this, player, hookLocation);
        var ctx = new SimpleExecutionContext(player, null, player.getLocation(), null);
        var result = FishingFlows.CATCH.run(ctx, state);
        DebugManager.log("fishing", player.getName() + " caught (table=" + (state.table() != null ? state.table().getId() : "none")
                + ") -> " + state.caught() + (result.stopped() ? " [flow stopped at '" + result.stoppedAt() + "']" : ""));
        return state.caught();
    }

    /** Plays the bite cue at the bobber ({@code fishing_pipeline.yml}'s {@code bite} flow — per TESTING_GUIDE.md's FISH-01 expectation). */
    public void playBiteFeedback(Location hookLocation) {
        if (hookLocation.getWorld() == null) return;
        FishingFlows.BITE.run(new SimpleExecutionContext(null, null, hookLocation, null), new BiteState(hookLocation));
    }

    /**
     * The loot table for where {@code player} stands: their zone's {@code fishing-loot-table}, else
     * {@code fallbackTable}; null if neither exists.
     */
    public FishingLootTable getTableFor(Player player, String fallbackTable) {
        ZoneDefinition zone = plugin.getZoneManager().getZoneAt(player.getLocation()).orElse(null);
        String tableId = zone != null ? zone.getFishingLootTable() : null;
        if (tableId == null) tableId = fallbackTable;
        return registry.get(tableId).orElse(registry.get(fallbackTable).orElse(null));
    }

    /** A Valmora item by id, else a vanilla material; null if it's neither. */
    public ItemStack createItem(String itemId, int amount) {
        try {
            var stack = plugin.getItemManager().getItemRegistry().createItemStack(itemId.toLowerCase());
            if (stack.isPresent()) { stack.get().setAmount(amount); return stack.get(); }
        } catch (Exception ignored) {}
        Material mat = Material.matchMaterial(itemId.toUpperCase());
        if (mat != null) return new ItemStack(mat, amount);
        return null;
    }
}
