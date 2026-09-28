package org.nakii.valmora.module.gui.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Serializes a fixed-size {@code ItemStack[]} to/from a byte array, for storing an
 * item-bound {@link org.nakii.valmora.module.gui.components.StorageComponent}'s contents
 * directly on the physical item's PersistentDataContainer (extracted from the old
 * BackpackModule's serialize/deserialize logic).
 *
 * <p>Writes Paper's NBT item format ({@link ItemStack#serializeItemsAsBytes(ItemStack[])}), which
 * carries a data version so the server can upgrade items across Minecraft versions. Data written
 * before that switch used Java object serialization ({@code BukkitObjectOutputStream}); it is still
 * read, recognised by the Java serialization stream header, and rewritten in the new format on the
 * next save.
 */
public final class ItemStorageCodec {

    /** {@code ObjectStreamConstants.STREAM_MAGIC} — the first two bytes of any Java serialization stream. */
    private static final int JAVA_STREAM_MAGIC = 0xACED;

    private ItemStorageCodec() {}

    public static byte[] serialize(ItemStack[] contents, Logger logger) {
        try {
            // Null slots are written as empty items and read back as null by deserialize().
            return ItemStack.serializeItemsAsBytes(contents);
        } catch (RuntimeException e) {
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
        ItemStack[] contents;
        try {
            contents = isLegacyFormat(bytes) ? deserializeLegacy(bytes) : ItemStack.deserializeItemsFromBytes(bytes);
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            if (logger != null) logger.warning("Failed to deserialize storage contents: " + e.getMessage());
            return null;
        }
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].isEmpty()) contents[i] = null;
        }
        if (contents.length >= expectedSize) return contents;
        ItemStack[] result = new ItemStack[expectedSize];
        System.arraycopy(contents, 0, result, 0, contents.length);
        return result;
    }

    private static boolean isLegacyFormat(byte[] bytes) {
        return bytes.length >= 2 && ((bytes[0] & 0xFF) << 8 | (bytes[1] & 0xFF)) == JAVA_STREAM_MAGIC;
    }

    /** Reads the pre-NBT format. Only kept so existing items can still be opened (and so migrated). */
    @SuppressWarnings("deprecation") // BukkitObjectInputStream: the only reader for the legacy format
    private static ItemStack[] deserializeLegacy(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream in = new BukkitObjectInputStream(stream)) {
            int size = in.readInt();
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            return contents;
        }
    }
}
