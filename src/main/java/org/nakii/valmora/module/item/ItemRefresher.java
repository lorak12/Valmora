package org.nakii.valmora.module.item;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brings existing items up to date with the current content: runs the {@link ItemMigrator} ladder,
 * then re-renders the name and lore when the content they were rendered against has changed.
 *
 * <p>"Changed" is tracked with a <b>render epoch</b>: a fingerprint of every file that feeds item
 * rendering (item/enchant/modifier/stat/pet/set-bonus YAML, {@code rarities.yml},
 * {@code item_types.yml}, the {@code items:} section of {@code config.yml}) plus the plugin
 * version. Each rendered item carries the epoch it was rendered at, so an up-to-date item costs one
 * PDC string compare, and any content edit re-renders every item once, lazily, the next time it's
 * seen ({@link ItemRefreshListener}: join, inventory open, pickup, held item, after reload).
 *
 * <p>Name and lore are presentation only. Stats, rarity and type are already read live from the
 * definition ({@link ItemView}), so gameplay never waits on a refresh.
 */
public final class ItemRefresher {

    /** Content roots whose files affect how items render. */
    private static final List<String> RENDER_INPUT_FOLDERS = List.of(
            "items", "enchants", "modifiers", "stats", "pets", "set_bonuses");
    private static final List<String> RENDER_INPUT_FILES = List.of("rarities.yml", "item_types.yml");

    private static volatile String epoch = "";
    private static final Set<String> warnedMissingDefinitions = ConcurrentHashMap.newKeySet();

    private ItemRefresher() {}

    public static String currentEpoch() {
        return epoch;
    }

    /** Recomputes the render epoch from the data folder; call after startup and after every reload. */
    public static void recomputeEpoch(Valmora plugin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(plugin.getPluginMeta().getVersion().getBytes(StandardCharsets.UTF_8));
            File dataFolder = plugin.getDataFolder();
            List<File> files = new ArrayList<>();
            for (String folder : RENDER_INPUT_FOLDERS) collect(new File(dataFolder, folder), files);
            for (String name : RENDER_INPUT_FILES) {
                File f = new File(dataFolder, name);
                if (f.isFile()) files.add(f);
            }
            files.sort(null);
            for (File f : files) {
                digest.update(dataFolder.toPath().relativize(f.toPath()).toString().getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(f.toPath()));
            }
            ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("items");
            if (itemsConfig != null) {
                YamlConfiguration snapshot = new YamlConfiguration();
                snapshot.set("items", itemsConfig);
                digest.update(snapshot.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            epoch = HexFormat.of().formatHex(digest.digest(), 0, 12);
        } catch (IOException | NoSuchAlgorithmException e) {
            // Without a fingerprint, force one refresh pass instead of never refreshing.
            epoch = "fallback-" + System.currentTimeMillis();
            plugin.getLogger().warning("Could not fingerprint item content (" + e.getMessage() + "); every item will be re-rendered once.");
        }
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, out);
            else if (child.getName().endsWith(".yml")) out.add(child);
        }
    }

    /** Refreshes every item in {@code inventory}. */
    public static void refresh(Valmora plugin, Inventory inventory) {
        if (inventory == null) return;
        ItemStack[] contents = inventory.getContents();
        for (ItemStack item : contents) {
            refresh(plugin, item);
        }
    }

    /** Refreshes a player's whole inventory (storage, armor, offhand) and ender chest. */
    public static void refresh(Valmora plugin, Player player) {
        refresh(plugin, player.getInventory());
        refresh(plugin, player.getEnderChest());
    }

    /**
     * Migrates and, if stale, re-renders {@code item} in place. Returns whether it changed. Items
     * with no Valmora data are ignored.
     */
    public static boolean refresh(Valmora plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        boolean hasEnchants = pdc.has(Keys.ENCHANTS_STATE_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER_ARRAY)
                || pdc.has(Keys.ENCHANTS_CONTAINER_KEY, PersistentDataType.STRING);
        boolean isPet = pdc.has(Keys.PET_ID_KEY, PersistentDataType.STRING);
        if (itemId == null && !hasEnchants && !isPet) return false;

        boolean changed = ItemMigrator.migrate(meta);

        String current = epoch;
        if (!current.isEmpty() && !current.equals(pdc.get(Keys.ITEM_RENDER_EPOCH_KEY, PersistentDataType.STRING))) {
            rerender(plugin, item, meta, itemId, hasEnchants, isPet);
            pdc.set(Keys.ITEM_RENDER_EPOCH_KEY, PersistentDataType.STRING, current);
            changed = true;
        }

        if (changed) item.setItemMeta(meta);
        return changed;
    }

    private static void rerender(Valmora plugin, ItemStack item, ItemMeta meta, String itemId,
                                 boolean hasEnchants, boolean isPet) {
        if (isPet && plugin.getPetModule() != null) {
            plugin.getPetModule().applyPetDisplay(meta);
        }
        if (itemId == null) {
            if (hasEnchants) EnchantmentHelper.rerenderGenericLore(item, meta);
            return;
        }
        if (itemId.startsWith("alchemy:")) {
            org.nakii.valmora.module.alchemy.brewing.AlchemyMachineHandler.rerenderPotion(meta);
            return;
        }

        boolean defined = ItemView.definition(meta).isPresent();
        if (defined || itemId.startsWith("vanilla_")) {
            ItemView.syncStoredCopies(meta);
            plugin.getItemManager().getItemFactory().updateLore(item, meta);
            return;
        }

        // Only items built by ItemFactory (which always writes a stats container) came from a
        // definition; other ids (e.g. the awkward potion) never had one.
        if (!meta.getPersistentDataContainer().has(Keys.STATS_CONTAINER_KEY, PersistentDataType.TAG_CONTAINER)) return;

        // The definition was deleted (and no previous-ids alias points here). Keep the last
        // rendered name/lore and stored stats, and mark it so players know why it changed.
        if (warnedMissingDefinitions.add(itemId.toLowerCase())) {
            plugin.getLogger().warning("Items with id '" + itemId + "' exist but their definition is gone. They keep "
                    + "their stored stats and are marked as legacy. Add '" + itemId + "' to another item's "
                    + "previous-ids to convert them.");
        }
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        Component legacyLine = Formatter.format(LEGACY_LINE);
        if (lore.isEmpty() || !lore.get(lore.size() - 1).equals(legacyLine)) {
            lore.add(legacyLine);
            meta.lore(lore);
        }
    }

    private static final String LEGACY_LINE = "<dark_gray>Legacy item — no longer obtainable";
}
