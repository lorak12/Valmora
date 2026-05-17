package org.nakii.valmora.module.fishing;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
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

        if (table.getSeaCreatureMobId() != null && Math.random() < table.getSeaCreatureChance()) {
            var def = plugin.getMobManager().getMobDefinition(table.getSeaCreatureMobId());
            if (def != null) plugin.getMobManager().spawnMob(def, player.getLocation());
            return true;
        }

        FishingLootEntry entry = table.roll();
        if (entry == null) return false;

        ItemStack item = createItem(entry.getItemId(), entry.rollAmount());
        if (item != null) player.getInventory().addItem(item);
        return true;
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
