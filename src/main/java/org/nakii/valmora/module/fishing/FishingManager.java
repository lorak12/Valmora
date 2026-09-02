package org.nakii.valmora.module.fishing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.util.DebugManager;

public class FishingManager {

    private final Valmora plugin;
    private final Registry<FishingLootTable> registry = new SimpleRegistry<>();

    public FishingManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public Registry<FishingLootTable> getRegistry() { return registry; }

    /**
     * Resolves a completed catch. {@code hookLocation} (per TESTING_GUIDE.md's FISH-03
     * expectation) is where any rolled sea creature spawns — the bobber's location, not the
     * player's.
     */
    public boolean handleCatch(Player player, Location hookLocation) {
        FishingLootTable table = getTableForPlayer(player);
        if (table == null) return false;

        // Fishing pipeline (docs/COMBAT_PIPELINE_ANALYSIS.md) — low-frequency event, no meaningful
        // hot-path risk, but still gated on hasAnyStages so a default install pays nothing for it.
        HookBus bus = ValmoraAPI.getInstance().getHookBus();
        boolean pipelineActive = bus != null
                && bus.hasAnyStages(FishingPipelineLoader.PRE_CATCH, FishingPipelineLoader.POST_CATCH);
        ExecutionContext pipelineCtx = null;
        if (pipelineActive) {
            pipelineCtx = new SimpleExecutionContext(player, null, player.getLocation(), null);
            pipelineCtx.set("fishing:table", table.getId());
            if (!bus.runPoint(FishingPipelineLoader.PRE_CATCH, pipelineCtx)) {
                return false; // interrupted — nothing granted
            }
        }

        boolean caught;
        if (table.getSeaCreatureMobId() != null && Math.random() < table.getSeaCreatureChance()) {
            var def = plugin.getMobManager().getMobDefinition(table.getSeaCreatureMobId());
            if (def != null) plugin.getMobManager().spawnMob(def, hookLocation);
            if (pipelineActive) pipelineCtx.set("fishing:sea_creature", table.getSeaCreatureMobId());
            caught = def != null;
        } else {
            FishingLootEntry entry = table.roll();
            if (entry == null) {
                caught = false;
            } else {
                ItemStack item = createItem(entry.getItemId(), entry.rollAmount());
                if (item != null) player.getInventory().addItem(item);
                if (pipelineActive) {
                    pipelineCtx.set("fishing:item", entry.getItemId());
                    pipelineCtx.set("fishing:amount", entry.rollAmount());
                }
                caught = item != null;
            }
        }

        if (pipelineActive) {
            bus.runPoint(FishingPipelineLoader.POST_CATCH, pipelineCtx);
        }
        DebugManager.log("fishing", player.getName() + " caught (table=" + table.getId() + ") -> " + caught);
        return caught;
    }

    /** Plays a bite-indicator sound/particle at the bobber when a fish bites (per TESTING_GUIDE.md's FISH-01 expectation). */
    public void playBiteFeedback(Location hookLocation) {
        var world = hookLocation.getWorld();
        if (world == null) return;
        world.playSound(hookLocation, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.6f, 1.2f);
        world.spawnParticle(Particle.FISHING, hookLocation, 10, 0.2, 0.1, 0.2, 0.02);
    }

    private FishingLootTable getTableForPlayer(Player player) {
        ZoneDefinition zone = plugin.getZoneManager().getZoneAt(player.getLocation()).orElse(null);
        String tableId = zone != null ? zone.getFishingLootTable() : null;
        if (tableId == null) tableId = "default";
        return registry.get(tableId).orElse(registry.get("default").orElse(null));
    }

    private ItemStack createItem(String itemId, int amount) {
        try {
            var stack = plugin.getItemManager().getItemRegistry().createItemStack(itemId.toLowerCase());
            if (stack.isPresent()) { stack.get().setAmount(amount); return stack.get(); }
        } catch (Exception ignored) {}
        Material mat = Material.matchMaterial(itemId.toUpperCase());
        if (mat != null) return new ItemStack(mat, amount);
        return null;
    }
}
