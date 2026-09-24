package org.nakii.valmora.module.collection;

import org.bukkit.configuration.file.YamlConfiguration;
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
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.DebugManager;
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
        trackEvent(event.getPlayer(), profile, "BLOCK_BREAK", event.getBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        ValmoraProfile profile = getProfile(killer);
        if (profile == null) return;
        trackEvent(killer, profile, "MOB_KILL", event.getEntityType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.getCaught() == null) return;
        ValmoraProfile profile = getProfile(event.getPlayer());
        if (profile == null) return;

        // Fixed 2026-08-07: previously hardcoded "COD" for any non-Item catch, misattributing
        // every real fish entity (salmon, pufferfish, tropical fish, …) to cod collections.
        String caught = (event.getCaught() instanceof Item entityItem)
                ? entityItem.getItemStack().getType().name()
                : event.getCaught().getType().name();
        trackEvent(event.getPlayer(), profile, "FISHING", caught);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;

        ItemStack item = event.getItem().getItemStack();
        trackEvent(player, profile, "ITEM_PICKUP", item.getType().name());

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String customId = meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (customId != null) {
                trackEvent(player, profile, "ITEM_PICKUP", "custom:" + customId.toLowerCase());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        trackEvent(player, profile, "CRAFT", event.getRecipe().getResult().getType().name());
    }

    private void trackEvent(Player player, ValmoraProfile profile, String eventType, String identifier) {
        CollectionManager manager = profile.getCollectionManager();
        // O(matched) via the registry's track-source index, instead of scanning every registered
        // collection per gameplay event (docs/IMPLEMENTATION_BACKLOG.md, Collection module).
        for (CollectionDefinition def : registry.getCollectionsFor(eventType, identifier)) {
            manager.addCount(def.getId(), 1);
            int newStage = def.getStageForCount(manager.getCount(def.getId()));
            DebugManager.log("collections", player.getName() + " collection '" + def.getId() + "' count="
                    + manager.getCount(def.getId()) + " stage=" + newStage);

            // Idempotency (added 2026-08-07): gate on the persisted "already granted" floor, not
            // a re-derived before/after stage comparison — the latter can re-fire a stage's
            // rewards forever if a config edit or reload ever causes getStageForCount() to
            // recompute differently. Once granted, a stage never fires again for this profile.
            //
            // The ledger is keyed by each stage's stable key (its id, else its threshold), not its
            // number, so inserting or renumbering stages in YAML neither skips nor repeats rewards.
            long count = manager.getCount(def.getId());
            java.util.Set<String> granted = manager.getGrantedKeys(def);
            SimpleExecutionContext ctx = null;
            for (CollectionStage stage : def.getStages()) {
                if (count < stage.getRequired() || granted.contains(stage.getKey())) continue;
                granted.add(stage.getKey());
                if (stage.getRewards().isEmpty()) continue;
                if (ctx == null) ctx = new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());
                plugin.getScriptModule().getEventParser()
                        .parseList(stage.getRewards())
                        .execute(ctx);
            }
            if (newStage > manager.getGrantedStage(def.getId())) {
                manager.setGrantedStage(def.getId(), newStage);
            }
        }
    }
}
