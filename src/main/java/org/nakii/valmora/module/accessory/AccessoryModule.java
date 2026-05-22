package org.nakii.valmora.module.accessory;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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

public class AccessoryModule implements ReloadableModule {

    static final int ACCESSORY_SLOTS = 45;

    private final Valmora plugin;
    private AccessoryListener listener;

    public AccessoryModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
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

    public void openAccessoryBag(Player player) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;

        Component title = Formatter.format("<dark_gray>✦ Accessory Bag");
        AccessoryInventoryHolder holder = new AccessoryInventoryHolder(player);
        Inventory inv = Bukkit.createInventory(holder, ACCESSORY_SLOTS, title);
        holder.setInventory(inv);

        // Populate with saved accessory items
        ItemStack[] saved = profile.getAccessoryItems();
        for (int i = 0; i < Math.min(saved.length, ACCESSORY_SLOTS); i++) {
            if (saved[i] != null) inv.setItem(i, saved[i]);
        }

        player.openInventory(inv);
    }

    public void saveAccessories(Player player, Inventory inv) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;

        ItemStack[] items = new ItemStack[ACCESSORY_SLOTS];
        for (int i = 0; i < ACCESSORY_SLOTS; i++) {
            items[i] = inv.getItem(i);
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
}
