package org.nakii.valmora.module.collection;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Keys;

public class CollectionListener implements Listener {

    private final Valmora plugin;
    private final CollectionRegistry registry;

    public CollectionListener(Valmora plugin, CollectionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    private ValmoraProfile getProfile(Player player) {
        ValmoraPlayer vp = plugin.getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return null;
        return vp.getActiveProfile();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ValmoraProfile profile = getProfile(event.getPlayer());
        if (profile == null) return;
        trackEvent(profile, "BLOCK_BREAK", event.getBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        ValmoraProfile profile = getProfile(killer);
        if (profile == null) return;
        trackEvent(profile, "MOB_KILL", event.getEntityType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.getCaught() == null) return;
        ValmoraProfile profile = getProfile(event.getPlayer());
        if (profile == null) return;

        String caught = "COD";
        if (event.getCaught() instanceof Item entityItem) {
            caught = entityItem.getItemStack().getType().name();
        }
        trackEvent(profile, "FISHING", caught);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;

        ItemStack item = event.getItem().getItemStack();
        trackEvent(profile, "ITEM_PICKUP", item.getType().name());

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String customId = meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (customId != null) {
                trackEvent(profile, "ITEM_PICKUP", "custom:" + customId.toLowerCase());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        trackEvent(profile, "CRAFT", event.getRecipe().getResult().getType().name());
    }

    private void trackEvent(ValmoraProfile profile, String eventType, String identifier) {
        CollectionManager manager = profile.getCollectionManager();
        for (CollectionDefinition def : registry.getCollections()) {
            if (def.matches(eventType, identifier)) {
                manager.addCount(def.getId(), 1);
            }
        }
    }
}
