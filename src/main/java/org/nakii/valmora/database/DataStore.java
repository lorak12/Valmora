package org.nakii.valmora.database;

import org.nakii.valmora.module.profile.ValmoraPlayer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DataStore {
    /**
     * Initializes the database tables.
     */
    void init();

    /**
     * Loads a player asynchronously.
     */
    CompletableFuture<ValmoraPlayer> loadPlayer(UUID uuid);

    /**
     * Saves a player asynchronously.
     */
    CompletableFuture<Void> savePlayer(ValmoraPlayer player);

    /**
     * Closes the database connection pool safely.
     */
    void close();
}
