package org.nakii.valmora.module.item;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.resource.ResourceModule;
import org.nakii.valmora.module.zone.ZoneManager;
import org.nakii.valmora.util.Formatter;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;

public class LootListener implements Listener {

    private final Valmora plugin;

    public LootListener(Valmora plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles Mining (Block Break) Drops.
     * Implements "Telekinesis" (auto-pickup) and auto-translation.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        // Defer to ResourceModule for resource blocks and intermediate stages
        Block block = event.getBlock();
        ZoneManager zm = plugin.getZoneManager();
        if (zm != null) {
            var zone = zm.getZoneAt(block.getLocation()).orElse(null);
            if (zone != null && zone.getResourceBlocks().containsKey(block.getType())) return;
        }
        ResourceModule rm = plugin.getResourceModule();
        if (rm != null && rm.getResourceManager() != null
                && rm.getResourceManager().isTrackedResource(block.getLocation())) return;

        // Cancel vanilla drops
        event.setDropItems(false);

        ItemStack tool = player.getInventory().getItemInMainHand();
        // Get what would have dropped
        var drops = event.getBlock().getDrops(tool, player);

        for (ItemStack rawDrop : drops) {
            processLoot(player, rawDrop, event.getBlock().getLocation().add(0.5, 0.5, 0.5));
        }
    }

    /**
     * Handles Mob Drops.
     * Auto-translates and auto-pickups for the killer.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        List<ItemStack> drops = event.getDrops();
        // Process drops from the list (modifying the list is safe if we iterate properly)
        for (int i = drops.size() - 1; i >= 0; i--) {
            ItemStack rawDrop = drops.get(i);
            drops.remove(i); // Remove from vanilla drops
            processLoot(killer, rawDrop, event.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) {
            org.bukkit.entity.Entity caught = event.getCaught();
            if (caught instanceof Item itemEntity) {
                ItemStack item = itemEntity.getItemStack();
                // Translate
                ItemStack valmoraItem = plugin.getItemManager().getItemTranslator().translate(item);
                itemEntity.setItemStack(valmoraItem);
                
                // If the player has telekinesis (which we seem to apply globally here), 
                // we could also auto-pickup fishing loot.
                // But typically fishing loot flies towards the player anyway.
            }
        }
    }

    /**
     * Translates the item and attempts to add it to the player's inventory.
     * If inventory is full, spawns a private glowing drop.
     */
    private void processLoot(Player player, ItemStack item, org.bukkit.Location location) {
        if (item == null || item.getType() == Material.AIR) return;

        // Translate vanilla item to Valmora standard
        ItemStack valmoraItem = plugin.getItemManager().getItemTranslator().translate(item);

        // Try to add to inventory
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(valmoraItem);

        if (!leftovers.isEmpty()) {
            handleFullInventory(player, leftovers.values(), location);
        }
    }

    /**
     * Handles cases where the player's inventory is full.
     * Shows a title and spawns private items that only the player can see and pick up.
     */
    private void handleFullInventory(Player player, java.util.Collection<ItemStack> items, org.bukkit.Location location) {
        player.showTitle(Title.title(
            Formatter.format("<red><bold>INVENTORY FULL</bold></red>"),
            Formatter.format("<gray>Items dropped on the ground!"),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));

        for (ItemStack drop : items) {
            Item itemEntity = location.getWorld().spawn(location, Item.class, item -> {
                item.setItemStack(drop);
                item.setGlowing(true);
                item.setPickupDelay(20); // 1 second delay
                item.setVisibleByDefault(false); // Hidden from everyone by default
            });
            // Show ONLY to the player who generated the loot
            player.showEntity(plugin, itemEntity);
        }
    }
}
