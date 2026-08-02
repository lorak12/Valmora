package org.nakii.valmora.module.quiver;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.Map;

/**
 * A per-profile arrow store, opened with {@code /quiver}. Bows/crossbows always draw ammo
 * from the player's normal inventory first; only once the inventory has none does
 * {@link #loanArrowFromQuiver} (called from {@link QuiverListener}) move a single arrow out
 * of the quiver and into the inventory so vanilla's own ammo check/consumption proceeds
 * unmodified.
 */
public class QuiverModule implements ReloadableModule {

    static final int QUIVER_SLOTS = 27;

    private final Valmora plugin;
    private QuiverListener listener;

    public QuiverModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.listener = new QuiverListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    @Override
    public String getId() { return "quiver"; }

    @Override
    public String getName() { return "Quiver"; }

    public void openQuiver(Player player) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;

        Component title = Formatter.format("<dark_gray>➶ Quiver");
        QuiverInventoryHolder holder = new QuiverInventoryHolder(player);
        Inventory inv = Bukkit.createInventory(holder, QUIVER_SLOTS, title);
        holder.setInventory(inv);

        ItemStack[] saved = profile.getQuiverItems();
        for (int i = 0; i < Math.min(saved.length, QUIVER_SLOTS); i++) {
            if (saved[i] != null) inv.setItem(i, saved[i]);
        }

        player.openInventory(inv);
    }

    public void saveQuiver(Player player, Inventory inv) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;

        ItemStack[] items = new ItemStack[QUIVER_SLOTS];
        for (int i = 0; i < QUIVER_SLOTS; i++) {
            items[i] = inv.getItem(i);
        }
        profile.setQuiverItems(items);
    }

    public boolean isArrow(ItemStack item) {
        return item != null && !item.getType().isAir() && Tag.ITEMS_ARROWS.isTagged(item.getType());
    }

    /** True if the player already has an arrow-type item in their main inventory or offhand. */
    public boolean hasArrowInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isArrow(item)) return true;
        }
        return isArrow(player.getInventory().getItemInOffHand());
    }

    /**
     * Moves a single arrow from the player's quiver into their inventory, if any quiver slot
     * holds one. Returns false (no-op, quiver left untouched) if the quiver is empty of
     * arrows or the player's inventory has no room to receive the loaned arrow.
     */
    public boolean loanArrowFromQuiver(Player player) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return false;

        ItemStack[] quiver = profile.getQuiverItems();
        for (int i = 0; i < quiver.length; i++) {
            ItemStack stack = quiver[i];
            if (!isArrow(stack)) continue;

            ItemStack loaned = stack.clone();
            loaned.setAmount(1);

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(loaned);
            if (!leftover.isEmpty()) return false; // no room — leave the quiver untouched

            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) quiver[i] = null;
            return true;
        }
        return false;
    }

    private ValmoraProfile getProfile(Player player) {
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        return session != null ? session.getActiveProfile() : null;
    }
}
