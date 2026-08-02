package org.nakii.valmora.module.accessory;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;

public class AccessoryModule implements ReloadableModule {

    /** Max usable item slots on a single page (5 rows) — the 6th row is always the control row. */
    private static final int PAGE_ITEM_CAP = 45;
    private static final int CONTROL_ROW_SIZE = 9;

    private final Valmora plugin;
    private AccessoryListener listener;

    private int startingSlots = 25;
    private int maxSlotsCap = 90;

    public AccessoryModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.startingSlots = plugin.getConfig().getInt("accessories.starting-slots", 25);
        this.maxSlotsCap = Math.max(1, plugin.getConfig().getInt("accessories.max-slots-cap", 90));
        if (this.startingSlots > this.maxSlotsCap) this.startingSlots = this.maxSlotsCap;

        this.listener = new AccessoryListener(this);
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
    public String getId() { return "accessories"; }

    @Override
    public String getName() { return "Accessory System"; }

    // ------------------------------------------------------------------
    // Slot cap management
    // ------------------------------------------------------------------

    public int getStartingSlots() { return startingSlots; }

    public int getMaxSlotsCap() { return maxSlotsCap; }

    /** Sets the server-wide ceiling; persisted to config.yml so it survives restarts. */
    public void setMaxSlotsCap(int cap) {
        this.maxSlotsCap = Math.max(1, cap);
        plugin.getConfig().set("accessories.max-slots-cap", this.maxSlotsCap);
        plugin.saveConfig();
    }

    /** The number of accessory slots this profile currently has unlocked, clamped to the server cap. */
    public int getUnlockedSlots(ValmoraProfile profile) {
        int stored = profile.getAccessorySlotsUnlocked();
        int base = stored < 0 ? startingSlots : stored;
        return Math.max(0, Math.min(base, maxSlotsCap));
    }

    /** Sets a profile's unlocked slot count, clamped to [0, maxSlotsCap]. */
    public void setUnlockedSlots(ValmoraProfile profile, int slots) {
        int clamped = Math.max(0, Math.min(slots, maxSlotsCap));
        profile.setAccessorySlotsUnlocked(clamped);
        ensureCapacity(profile);
    }

    public void addUnlockedSlots(ValmoraProfile profile, int delta) {
        setUnlockedSlots(profile, getUnlockedSlots(profile) + delta);
    }

    /** Grows the backing item array to the current cap if needed, preserving contents. */
    private void ensureCapacity(ValmoraProfile profile) {
        ItemStack[] current = profile.getAccessoryItems();
        if (current.length >= maxSlotsCap) return;
        ItemStack[] grown = new ItemStack[maxSlotsCap];
        System.arraycopy(current, 0, grown, 0, current.length);
        profile.setAccessoryItems(grown);
    }

    // ------------------------------------------------------------------
    // Layout — the bag dynamically resizes to fit the unlocked slot count,
    // paginating once it would exceed a single inventory (45 item slots +
    // one reserved control row).
    // ------------------------------------------------------------------

    public record AccessoryLayout(int page, int totalPages, int itemsStart, int itemsOnPage, int rows,
                                   int size, int controlRowStart) {
    }

    public AccessoryLayout computeLayout(ValmoraProfile profile, int requestedPage) {
        int unlocked = getUnlockedSlots(profile);
        int totalPages = Math.max(1, (int) Math.ceil(unlocked / (double) PAGE_ITEM_CAP));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int itemsStart = page * PAGE_ITEM_CAP;
        int itemsOnPage = Math.max(0, Math.min(PAGE_ITEM_CAP, unlocked - itemsStart));
        int rows = Math.max(1, (int) Math.ceil(itemsOnPage / 9.0));
        int controlRowStart = rows * 9;
        int size = controlRowStart + CONTROL_ROW_SIZE;
        return new AccessoryLayout(page, totalPages, itemsStart, itemsOnPage, rows, size, controlRowStart);
    }

    // ------------------------------------------------------------------
    // Opening / saving
    // ------------------------------------------------------------------

    public void openAccessoryBag(Player player) {
        openAccessoryBag(player, 0);
    }

    public void openAccessoryBag(Player player, int page) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        ensureCapacity(profile);

        AccessoryLayout layout = computeLayout(profile, page);

        Component title = Formatter.format("<dark_gray>✦ Accessory Bag <gray>· <white>" + getUnlockedSlots(profile)
                + " <gray>slots · <white>" + (layout.page() + 1) + "<gray>/<white>" + layout.totalPages());
        AccessoryInventoryHolder holder = new AccessoryInventoryHolder(player, layout);
        Inventory inv = Bukkit.createInventory(holder, layout.size(), title);
        holder.setInventory(inv);

        ItemStack[] saved = profile.getAccessoryItems();
        for (int i = 0; i < layout.itemsOnPage(); i++) {
            ItemStack item = saved[layout.itemsStart() + i];
            if (item != null) inv.setItem(i, item.clone());
        }
        for (int i = layout.itemsOnPage(); i < layout.controlRowStart(); i++) {
            inv.setItem(i, lockedFillerItem());
        }
        populateControlRow(inv, layout);

        player.openInventory(inv);
    }

    public void saveAccessories(Player player, Inventory inv, AccessoryLayout layout) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        ensureCapacity(profile);

        ItemStack[] items = profile.getAccessoryItems();
        for (int i = 0; i < layout.itemsOnPage(); i++) {
            ItemStack item = inv.getItem(i);
            items[layout.itemsStart() + i] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
        profile.setAccessoryItems(items);

        // Recalculate stats to apply new accessory bonuses
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session != null && session.getActiveProfile() != null) {
            session.getActiveProfile().getStatManager().recalculateStats(player);
        }
    }

    private ValmoraProfile getProfile(Player player) {
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        return session != null ? session.getActiveProfile() : null;
    }

    public boolean isAccessoryItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String typeStr = item.getItemMeta().getPersistentDataContainer()
                .get(org.nakii.valmora.util.Keys.ITEM_TYPE_KEY, org.bukkit.persistence.PersistentDataType.STRING);
        return "ACCESSORY".equalsIgnoreCase(typeStr);
    }

    // ------------------------------------------------------------------
    // Control-row / filler items
    // ------------------------------------------------------------------

    private void populateControlRow(Inventory inv, AccessoryLayout layout) {
        int start = layout.controlRowStart();
        for (int i = 0; i < CONTROL_ROW_SIZE; i++) {
            inv.setItem(start + i, fillerPane());
        }
        if (layout.page() > 0) {
            inv.setItem(start, controlButton(Material.ARROW, "<yellow>◀ Previous Page",
                    "<gray>Go to page <white>" + layout.page()));
        }
        inv.setItem(start + 4, controlButton(Material.BARRIER, "<red>Close",
                "<gray>Close the accessory bag.", "<gray>(saves automatically)"));
        if (layout.page() < layout.totalPages() - 1) {
            inv.setItem(start + 8, controlButton(Material.ARROW, "<yellow>Next Page ▶",
                    "<gray>Go to page <white>" + (layout.page() + 2)));
        }
    }

    private ItemStack fillerPane() {
        return controlButton(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private ItemStack lockedFillerItem() {
        return controlButton(Material.GRAY_STAINED_GLASS_PANE, "<gray>🔒 Locked Slot",
                "<dark_gray>Ask an admin to unlock", "<dark_gray>more accessory slots.");
    }

    private ItemStack controlButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Formatter.format(name));
            if (lore.length > 0) {
                List<Component> loreLines = new ArrayList<>();
                for (String line : lore) loreLines.add(Formatter.format(line));
                meta.lore(loreLines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
