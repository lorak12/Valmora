package org.nakii.valmora.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link SQLDataStore} against a real (temporary) SQLite database: the schema
 * migration framework and the economy persistence round-trip. No Bukkit server required.
 */
@Tag("database")
class SQLDataStoreTest {

    private static final Logger LOGGER = Logger.getLogger("ValmoraTest");

    private HikariDataSource newDataSource(Path dbFile) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(2);
        cfg.setPoolName("ValmoraTest-Pool");
        return new HikariDataSource(cfg);
    }

    @Test
    void initCreatesSchemaAndStampsVersion(@TempDir Path dir) throws Exception {
        HikariDataSource ds = newDataSource(dir.resolve("fresh.db"));
        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();

            assertEquals(SQLDataStore.LATEST_SCHEMA_VERSION, readVersion(ds));
            assertTrue(columnExists(ds, "valmora_profiles", "created_at"));
            assertTrue(columnExists(ds, "valmora_profiles", "last_used"));
            assertTrue(columnExists(ds, "valmora_profiles", "collections"));
            assertTrue(columnExists(ds, "valmora_economy", "purse"));
        } finally {
            store.close();
        }
    }

    @Test
    void initIsIdempotent(@TempDir Path dir) throws Exception {
        HikariDataSource ds = newDataSource(dir.resolve("idempotent.db"));
        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();
            assertDoesNotThrow(store::init); // a second run must be a clean no-op
            assertEquals(SQLDataStore.LATEST_SCHEMA_VERSION, readVersion(ds));
        } finally {
            store.close();
        }
    }

    @Test
    void migratesPreVersioningDatabase(@TempDir Path dir) throws Exception {
        HikariDataSource ds = newDataSource(dir.resolve("legacy.db"));
        // Simulate a database created before the migration framework existed:
        // base tables present, but no schema_version table and missing newer columns.
        try (Connection c = ds.getConnection()) {
            c.prepareStatement("CREATE TABLE valmora_players (uuid VARCHAR(36) PRIMARY KEY, active_profile VARCHAR(36))").execute();
            c.prepareStatement("CREATE TABLE valmora_profiles (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36), "
                    + "name VARCHAR(255), stats TEXT, skills TEXT, player_state TEXT)").execute();
        }
        assertFalse(columnExists(ds, "valmora_profiles", "created_at"), "precondition: legacy db lacks created_at");

        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();

            assertEquals(SQLDataStore.LATEST_SCHEMA_VERSION, readVersion(ds));
            assertTrue(columnExists(ds, "valmora_profiles", "created_at"));
            assertTrue(columnExists(ds, "valmora_profiles", "last_used"));
            assertTrue(columnExists(ds, "valmora_profiles", "tags"));
            assertTrue(columnExists(ds, "valmora_profiles", "variables"));
        } finally {
            store.close();
        }
    }

    @Test
    void economyRoundTrip(@TempDir Path dir) throws Exception {
        HikariDataSource ds = newDataSource(dir.resolve("economy.db"));
        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();
            UUID id = UUID.randomUUID();

            assertNull(store.loadEconomy(id).join(), "unknown player should have no economy row");

            store.saveEconomy(id, 1234.5, 6789.0).join();
            double[] row = store.loadEconomy(id).join();
            assertNotNull(row);
            assertEquals(1234.5, row[0], 1e-6);
            assertEquals(6789.0, row[1], 1e-6);

            // Saving again must upsert (overwrite), not duplicate.
            store.saveEconomy(id, 1.0, 2.0).join();
            double[] updated = store.loadEconomy(id).join();
            assertEquals(1.0, updated[0], 1e-6);
            assertEquals(2.0, updated[1], 1e-6);
        } finally {
            store.close();
        }
    }

    // --- helpers ---

    private int readVersion(HikariDataSource ds) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT version FROM valmora_schema_version WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("version") : -1;
        }
    }

    private boolean columnExists(HikariDataSource ds, String table, String column) throws SQLException {
        try (Connection c = ds.getConnection();
             ResultSet rs = c.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }
}
