package org.nakii.valmora.database;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DataStore {
    void init();
    CompletableFuture<ValmoraPlayer> loadPlayer(UUID uuid);
    CompletableFuture<Void> savePlayer(ValmoraPlayer player);

    /**
     * Profile-owned generic GUI storage slot (e.g. accessory bag), keyed by (profile id, storage id).
     * Scoped to a profile — not the player account — so each of a player's profiles has independent
     * storage, matching how accessory/quiver data worked before this became a generic mechanism.
     */
    CompletableFuture<ItemStack[]> loadStorage(UUID profileId, String storageId, int expectedSize);
    CompletableFuture<Void> saveStorage(UUID profileId, String storageId, ItemStack[] contents);

    CompletableFuture<Void> deleteProfile(UUID profileId);

    /** Returns [purse, bank] or null if no row exists for this UUID. */
    CompletableFuture<double[]> loadEconomy(UUID uuid);

    CompletableFuture<Void> saveEconomy(UUID uuid, double purse, double bank);

    /**
     * Persists many players' [purse, bank] balances in a single batched transaction (one
     * connection, one round-trip per statement batch) instead of one connection/transaction
     * per player. This is the write path used by periodic autosave and shutdown flush, where
     * saving thousands of cached players individually would otherwise dominate the cost.
     */
    CompletableFuture<Void> saveEconomyBatch(Map<UUID, double[]> balances);

    void close();
}
