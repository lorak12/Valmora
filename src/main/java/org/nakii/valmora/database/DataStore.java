package org.nakii.valmora.database;

import org.nakii.valmora.module.profile.ValmoraPlayer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DataStore {
    void init();
    CompletableFuture<ValmoraPlayer> loadPlayer(UUID uuid);
    CompletableFuture<Void> savePlayer(ValmoraPlayer player);

    /** Returns [purse, bank] or null if no row exists for this UUID. */
    CompletableFuture<double[]> loadEconomy(UUID uuid);

    CompletableFuture<Void> saveEconomy(UUID uuid, double purse, double bank);

    void close();
}
