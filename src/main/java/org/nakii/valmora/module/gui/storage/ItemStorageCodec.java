package org.nakii.valmora.module.gui.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Serializes a fixed-size {@code ItemStack[]} to/from a byte array, for storing an
 * item-bound {@link org.nakii.valmora.module.gui.components.StorageComponent}'s contents
 * directly on the physical item's PersistentDataContainer (extracted from the old
 * BackpackModule's serialize/deserialize logic).
 */
public final class ItemStorageCodec {

    private ItemStorageCodec() {}

    public static byte[] serialize(ItemStack[] contents, Logger logger) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(stream)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            return stream.toByteArray();
        } catch (IOException e) {
            if (logger != null) logger.warning("Failed to serialize storage contents: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the stored contents padded to at least {@code expectedSize}. Never truncated: items
     * beyond {@code expectedSize} (the storage shrank in YAML) are kept for the caller to hand
     * back. Returns {@code null} if the data can't be read, so the caller refuses to open rather
     * than showing, and then saving, an empty storage over the real contents.
     */
    public static ItemStack[] deserialize(byte[] bytes, int expectedSize, Logger logger) {
        if (bytes == null) return new ItemStack[expectedSize];
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream in = new BukkitObjectInputStream(stream)) {
            int size = in.readInt();
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            if (size >= expectedSize) return contents;
            ItemStack[] result = new ItemStack[expectedSize];
            System.arraycopy(contents, 0, result, 0, size);
            return result;
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            if (logger != null) logger.warning("Failed to deserialize storage contents: " + e.getMessage());
            return null;
        }
    }
}
