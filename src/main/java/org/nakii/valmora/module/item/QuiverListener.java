package org.nakii.valmora.module.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraProfile;

/**
 * Quiver auto-refill (added 2026-08-07 — see {@code guis/quiver.yml} and
 * docs/modules/design/backpack.md §6, replacing the old removed ammo-quiver feature).
 *
 * <p>Scoped deliberately simpler than the original feature: rather than intercepting vanilla's
 * own bow-draw permission check (which requires arrows already be in the inventory before a shot
 * is even possible — there's no "tried to shoot, had none" event to hook), this tops the player's
 * main inventory up from their quiver storage right after every shot whenever they're now out of
 * arrows there. In practice this reads the same to a player as "the quiver directly feeds the
 * bow" — they never have to manually move arrows out — without fighting vanilla's shoot-event
 * internals (creative mode, Infinity enchant, crossbows, dispenser-fired arrows, etc. all still
 * behave exactly as vanilla intends).
 */
public class QuiverListener implements Listener {

    private static final String STORAGE_ID = "quiver";

    private final Valmora plugin;

    public QuiverListener(Valmora plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getConsumable() == null) return; // infinity/creative — nothing to refill

        // Deferred a tick so this runs after vanilla's own consumption has already applied.
        Bukkit.getScheduler().runTask(plugin, () -> refillIfEmpty(player));
    }

    private void refillIfEmpty(Player player) {
        if (!player.isOnline()) return;
        if (hasAnyArrows(player)) return;

        ValmoraProfile profile = getActiveProfile(player);
        if (profile == null) return;

        ItemStack[] quiver = profile.getStorage(STORAGE_ID);
        for (int i = 0; i < quiver.length; i++) {
            ItemStack stack = quiver[i];
            if (stack == null || stack.getType().isAir() || !isArrow(stack.getType())) continue;

            var leftover = player.getInventory().addItem(stack.clone());
            if (leftover.isEmpty()) {
                quiver[i] = null;
            } else {
                // Inventory was (unexpectedly) full — put back only what didn't fit.
                quiver[i] = leftover.values().iterator().next();
            }
            // Persist, not just mirror in memory: otherwise the DB still holds the arrows just
            // moved into the inventory, and they come back on the next join (a dupe).
            plugin.getGuiModule().persistPlayerStorage(profile, STORAGE_ID, quiver);
            return;
        }
    }

    private boolean hasAnyArrows(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isArrow(item.getType())) return true;
        }
        return false;
    }

    private boolean isArrow(Material material) {
        // HC-048 fix: use the vanilla arrow tag instead of an enumerated list, so any future
        // arrow variant (added by a data pack or a Minecraft update) is picked up automatically.
        return org.bukkit.Tag.ITEMS_ARROWS.isTagged(material);
    }

    private ValmoraProfile getActiveProfile(Player player) {
        var vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        return vp != null ? vp.getActiveProfile() : null;
    }
}
