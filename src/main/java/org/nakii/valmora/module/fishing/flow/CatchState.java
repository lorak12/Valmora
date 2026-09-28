package org.nakii.valmora.module.fishing.flow;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.pipeline.flow.FlowState;
import org.nakii.valmora.module.fishing.FishingLootTable;
import org.nakii.valmora.module.fishing.FishingManager;

/**
 * State of one {@code fishing.catch} flow run. Script-visible as {@code $fishing.table$},
 * {@code $fishing.sea_creature_chance$}, {@code $fishing.sea_creature$}, {@code $fishing.item$},
 * {@code $fishing.amount$} and {@code $fishing.caught$}.
 */
public final class CatchState implements FlowState {

    final FishingManager manager;
    final Player player;
    final Location hook;

    FishingLootTable table;
    String seaCreatureId;
    String itemId;
    int amount;
    ItemStack item;
    boolean caught;

    public CatchState(FishingManager manager, Player player, Location hook) {
        this.manager = manager;
        this.player = player;
        this.hook = hook;
    }

    public boolean caught() {
        return caught;
    }

    public FishingLootTable table() {
        return table;
    }

    @Override
    public void publish(ExecutionContext ctx) {
        ctx.set("fishing:table", table != null ? table.getId() : null);
        ctx.set("fishing:sea_creature_chance", table != null ? table.getSeaCreatureChance() : null);
        ctx.set("fishing:sea_creature", seaCreatureId);
        ctx.set("fishing:item", itemId);
        ctx.set("fishing:amount", itemId != null ? amount : null);
        ctx.set("fishing:caught", caught);
    }

    @Override
    public boolean write(String key, Object value) {
        switch (key) {
            case "fishing.sea_creature" -> seaCreatureId = FlowState.asString(value);
            case "fishing.item" -> itemId = FlowState.asString(value);
            case "fishing.amount" -> amount = (int) Math.round(FlowState.asDouble(value, amount));
            default -> {
                return false;
            }
        }
        return true;
    }
}
