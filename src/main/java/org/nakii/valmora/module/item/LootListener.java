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
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.resource.ResourceModule;
import org.nakii.valmora.module.zone.ZoneManager;
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

        // Defer to the global block_loot module for any block it has a configured override for —
        // BlockLootListener (EventPriority.LOWEST) already fully handled the drop for those.
        // Without this, a configured block would double-drop: the custom item plus the real
        // vanilla one, since the check above only excludes zone-scoped resource blocks.
        org.nakii.valmora.module.blockloot.BlockLootModule blm = plugin.getBlockLootModule();
        if (blm != null && blm.getRegistry() != null && blm.getRegistry().get(block.getType()) != null) return;

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
     * Catch-all for §1/§4 "every block break should drop a Valmora-formatted item": the paths
     * above ({@link #onBlockBreak}, {@link #onEntityDeath}, {@link #onFish}) already translate
     * drops for the common cases, but non-player block destruction (explosions, pistons, fire,
     * mob-caused block changes) never fires {@code BlockDropItemEvent} (it requires a {@code Player}
     * — Bukkit only raises it for player-caused breaks) and vanilla still spawns raw, untranslated
     * {@code Item} entities for those. Rather than chase every individual destruction mechanic,
     * this hooks the one event every dropped-item mechanic funnels through regardless of cause:
     * {@link ItemSpawnEvent}. {@link ItemTranslator#translate} is idempotent (it no-ops on anything
     * already carrying a Valmora item ID), so this is safe to run over items that were already
     * translated and re-spawned (e.g. the private full-inventory drops in
     * {@link #handleFullInventory}) or over genuine Valmora items dropped by a player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack translated = plugin.getItemManager().getItemTranslator().translate(entity.getItemStack());
        entity.setItemStack(translated);
    }

    /**
     * Translates the item and gives it to the player, falling back to a private drop if their
     * inventory is full. The give-or-drop half now lives on {@link org.nakii.valmora.module.item.ItemManager#giveOrPrivateDrop}
     * so other loot-producing modules (e.g. {@code block_loot}) can reuse it without duplicating it.
     */
    private void processLoot(Player player, ItemStack item, org.bukkit.Location location) {
        if (item == null || item.getType() == Material.AIR) return;

        ItemStack valmoraItem = plugin.getItemManager().getItemTranslator().translate(item);
        plugin.getItemManager().giveOrPrivateDrop(player, valmoraItem, location);
    }
}
