package org.nakii.valmora.database;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.module.economy.EconomyLedgerEntry;
import org.nakii.valmora.module.pack.PackRecord;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import java.util.List;
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

    /** Appends one bank-transaction ledger row, pruning older rows beyond the retention window for that player. */
    CompletableFuture<Void> appendLedgerEntry(UUID uuid, String type, double amount, double purseAfter, double bankAfter);

    /** Returns up to {@code limit} most recent ledger rows for a player, newest first. */
    CompletableFuture<List<EconomyLedgerEntry>> loadRecentLedger(UUID uuid, int limit);

    /** Persists (inserts or replaces) one installed content pack's ledger row. */
    CompletableFuture<Void> savePackRecord(PackRecord record);

    /** Returns every installed content pack's ledger row. */
    CompletableFuture<List<PackRecord>> loadPackRecords();

    /** Removes a pack's ledger row (does not touch its files — callers delete those separately). */
    CompletableFuture<Void> deletePackRecord(String packId);

    void close();
}
