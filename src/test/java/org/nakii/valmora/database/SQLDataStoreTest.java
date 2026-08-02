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
import java.util.HashMap;
import java.util.Map;
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
            assertTrue(columnExists(ds, "valmora_profiles", "quiver"));
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
            assertTrue(columnExists(ds, "valmora_profiles", "quiver"));
        } finally {
            store.close();
        }
    }

    @Test
    void migratesV1DatabaseToAddQuiverColumn(@TempDir Path dir) throws Exception {
        HikariDataSource ds = newDataSource(dir.resolve("v1.db"));
        // Simulate a database already on schema v1 (quiver column didn't exist yet).
        try (Connection c = ds.getConnection()) {
            c.prepareStatement("""
                CREATE TABLE valmora_schema_version (id INTEGER PRIMARY KEY, version INTEGER NOT NULL)
            """).execute();
            c.prepareStatement("INSERT INTO valmora_schema_version (id, version) VALUES (1, 1)").execute();
            c.prepareStatement("""
                CREATE TABLE valmora_profiles (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36),
                    name VARCHAR(255), stats TEXT, skills TEXT, player_state TEXT, tags TEXT,
                    variables TEXT, collections TEXT, inventory TEXT,
                    created_at BIGINT NOT NULL DEFAULT 0, last_used BIGINT NOT NULL DEFAULT 0)
            """).execute();
        }
        assertFalse(columnExists(ds, "valmora_profiles", "quiver"), "precondition: v1 db lacks quiver");

        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();
            assertEquals(SQLDataStore.LATEST_SCHEMA_VERSION, readVersion(ds));
            assertTrue(columnExists(ds, "valmora_profiles", "quiver"));
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

    @Test
    void economyBatchRoundTripUpsertsAllRows(@TempDir Path dir) throws Exception {
        HikariDataSource ds = newDataSource(dir.resolve("economy-batch.db"));
        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();

            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();

            // Pre-seed one row (via the single-save path) so the batch has to both insert
            // new rows and upsert an existing one in the same transaction.
            store.saveEconomy(a, 10.0, 20.0).join();

            Map<UUID, double[]> batch = new HashMap<>();
            batch.put(a, new double[]{111.0, 222.0}); // overwrite
            batch.put(b, new double[]{5.0, 0.0});      // new
            batch.put(c, new double[]{0.0, 999.0});    // new
            store.saveEconomyBatch(batch).join();

            double[] rowA = store.loadEconomy(a).join();
            double[] rowB = store.loadEconomy(b).join();
            double[] rowC = store.loadEconomy(c).join();

            assertArrayEquals(new double[]{111.0, 222.0}, rowA, 1e-6);
            assertArrayEquals(new double[]{5.0, 0.0}, rowB, 1e-6);
            assertArrayEquals(new double[]{0.0, 999.0}, rowC, 1e-6);
        } finally {
            store.close();
        }
    }

    @Test
    void economyBatchWithEmptyMapIsNoOp(@TempDir Path dir) throws Exception {
        HikariDataSource ds = newDataSource(dir.resolve("economy-empty-batch.db"));
        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();
            assertDoesNotThrow(() -> store.saveEconomyBatch(Map.of()).join());
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
