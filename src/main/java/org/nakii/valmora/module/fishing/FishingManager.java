package org.nakii.valmora.module.fishing;

import org.bukkit.Material;
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

public class FishingManager {

    private final Valmora plugin;
    private final Registry<FishingLootTable> registry = new SimpleRegistry<>();

    public FishingManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public Registry<FishingLootTable> getRegistry() { return registry; }

    public boolean handleCatch(Player player) {
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
            if (def != null) plugin.getMobManager().spawnMob(def, player.getLocation());
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
        return caught;
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
