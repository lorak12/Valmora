package org.nakii.valmora.module.backpack;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BackpackModule implements ReloadableModule {

    private final Valmora plugin;
    private BackpackListener listener;

    public BackpackModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.listener = new BackpackListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getAbilityManager().getMechanicRegistry().registerMechanic(new BackpackMechanic(this));
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    @Override
    public String getId() { return "backpacks"; }

    @Override
    public String getName() { return "Backpack System"; }

    public void openBackpack(Player player, ItemStack backpackItem, int inventorySlot) {
        int size = getBackpackSize(backpackItem);
        Component title = Formatter.format("<dark_gray>🎒 Backpack");

        BackpackInventoryHolder holder = new BackpackInventoryHolder(player, backpackItem, inventorySlot);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // Load existing contents
        ItemStack[] contents = loadContents(backpackItem, size);
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) inv.setItem(i, contents[i]);
        }

        player.openInventory(inv);
    }

    public void saveContents(ItemStack backpackItem, Inventory inv) {
        int size = inv.getSize();
        ItemStack[] contents = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            contents[i] = inv.getItem(i);
        }

        byte[] serialized = serialize(contents);
        if (serialized == null) return;

        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(Keys.BACKPACK_CONTENTS_KEY, PersistentDataType.BYTE_ARRAY, serialized);
        backpackItem.setItemMeta(meta);
    }

    public int getBackpackSize(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 27;
        Integer size = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.BACKPACK_SIZE_KEY, PersistentDataType.INTEGER);
        return (size != null && size > 0) ? Math.min(size, 54) : 27;
    }

    public boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String typeStr = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
        return "BACKPACK".equalsIgnoreCase(typeStr);
    }

    private ItemStack[] loadContents(ItemStack item, int size) {
        if (item == null || !item.hasItemMeta()) return new ItemStack[size];
        byte[] bytes = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.BACKPACK_CONTENTS_KEY, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) return new ItemStack[size];
        ItemStack[] loaded = deserialize(bytes);
        if (loaded == null) return new ItemStack[size];
        // Ensure correct size
        if (loaded.length == size) return loaded;
        ItemStack[] result = new ItemStack[size];
        System.arraycopy(loaded, 0, result, 0, Math.min(loaded.length, size));
        return result;
    }

    private byte[] serialize(ItemStack[] contents) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(stream)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            return stream.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to serialize backpack contents: " + e.getMessage());
            return null;
        }
    }

    private ItemStack[] deserialize(byte[] bytes) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream in = new BukkitObjectInputStream(stream)) {
            int size = in.readInt();
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            return contents;
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().warning("Failed to deserialize backpack contents: " + e.getMessage());
            return null;
        }
    }
}
