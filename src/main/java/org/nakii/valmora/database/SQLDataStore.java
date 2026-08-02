package org.nakii.valmora.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariDataSource;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SQLDataStore implements DataStore {

    private final HikariDataSource hikari;
    private final Gson gson;
    private final boolean isMySQL;
    private final Logger logger;

    // Dedicated thread pool for database operations
    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    public SQLDataStore(HikariDataSource hikari, boolean isMySQL, Logger logger) {
        this.hikari = hikari;
        this.isMySQL = isMySQL;
        this.logger = logger;
        this.gson = new Gson();
    }

    /**
     * The schema version this build of the plugin expects. Bump this and add a
     * corresponding {@code migrateToVN} step in {@link #applyMigrations} whenever
     * the database layout changes.
     */
    static final int LATEST_SCHEMA_VERSION = 2;

    @Override
    public void init() {
        try (Connection conn = hikari.getConnection()) {
            ensureSchemaVersionTable(conn);
            int current = getSchemaVersion(conn);

            if (current > LATEST_SCHEMA_VERSION) {
                logger.warning("Valmora database schema version (" + current + ") is newer than this plugin "
                        + "supports (" + LATEST_SCHEMA_VERSION + "). Update the plugin to avoid problems.");
                return;
            }
            if (current < LATEST_SCHEMA_VERSION) {
                logger.info("Migrating Valmora database schema from v" + current + " to v" + LATEST_SCHEMA_VERSION + "...");
                applyMigrations(conn, current);
                logger.info("Valmora database schema migration complete.");
            }
        } catch (SQLException e) {
            // Schema initialization failing means every subsequent read/write will
            // fail too — fail fast so the plugin disables instead of silently losing data.
            logger.log(Level.SEVERE, "Failed to initialize Valmora database schema", e);
            throw new IllegalStateException("Valmora database initialization failed", e);
        }
    }

    private void ensureSchemaVersionTable(Connection conn) throws SQLException {
        conn.prepareStatement("""
            CREATE TABLE IF NOT EXISTS valmora_schema_version (
                id INTEGER PRIMARY KEY,
                version INTEGER NOT NULL
            )
        """).execute();
    }

    /** Returns the stored schema version, or 0 for a fresh / pre-versioning database. */
    private int getSchemaVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT version FROM valmora_schema_version WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt("version");
        }
        return 0;
    }

    private void setSchemaVersion(Connection conn, int version) throws SQLException {
        String sql = isMySQL
                ? "INSERT INTO valmora_schema_version (id, version) VALUES (1, ?) ON DUPLICATE KEY UPDATE version = ?"
                : "INSERT INTO valmora_schema_version (id, version) VALUES (1, ?) ON CONFLICT(id) DO UPDATE SET version = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setInt(2, version);
            ps.executeUpdate();
        }
    }

    /** Applies every migration newer than {@code from} in order, recording progress as it goes. */
    private void applyMigrations(Connection conn, int from) throws SQLException {
        if (from < 1) {
            migrateToV1(conn);
            setSchemaVersion(conn, 1);
        }
        if (from < 2) {
            migrateToV2(conn);
            setSchemaVersion(conn, 2);
        }
        // Future migrations go here, each gated on `from` and ending with setSchemaVersion(conn, N):
        // if (from < 3) { migrateToV3(conn); setSchemaVersion(conn, 3); }
    }

    /** v2 — adds the quiver column (per-profile arrow storage). */
    private void migrateToV2(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "valmora_profiles", "quiver", "TEXT");
    }

    /** v1 — baseline schema. Idempotent so it can also upgrade pre-versioning databases in place. */
    private void migrateToV1(Connection conn) throws SQLException {
        conn.prepareStatement("""
            CREATE TABLE IF NOT EXISTS valmora_players (
                uuid VARCHAR(36) PRIMARY KEY,
                active_profile VARCHAR(36)
            )
        """).execute();

        conn.prepareStatement("""
            CREATE TABLE IF NOT EXISTS valmora_profiles (
                id VARCHAR(36) PRIMARY KEY,
                player_uuid VARCHAR(36),
                name VARCHAR(255),
                stats TEXT,
                skills TEXT,
                player_state TEXT,
                tags TEXT,
                variables TEXT,
                collections TEXT,
                inventory TEXT
            )
        """).execute();

        // Bring pre-versioning databases (whose profiles table predates these columns) up to date.
        addColumnIfMissing(conn, "valmora_profiles", "tags", "TEXT");
        addColumnIfMissing(conn, "valmora_profiles", "variables", "TEXT");
        addColumnIfMissing(conn, "valmora_profiles", "collections", "TEXT");
        addColumnIfMissing(conn, "valmora_profiles", "inventory", "TEXT");
        addColumnIfMissing(conn, "valmora_profiles", "created_at", "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing(conn, "valmora_profiles", "last_used", "BIGINT NOT NULL DEFAULT 0");

        conn.prepareStatement("""
            CREATE TABLE IF NOT EXISTS valmora_economy (
                uuid VARCHAR(36) PRIMARY KEY,
                purse DOUBLE NOT NULL DEFAULT 0,
                bank  DOUBLE NOT NULL DEFAULT 0
            )
        """).execute();
    }

    /** Adds a column, tolerating the "already exists" error so it is safe on fresh and re-run databases. */
    private void addColumnIfMissing(Connection conn, String table, String column, String type) {
        try (PreparedStatement ps = conn.prepareStatement(
                "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type)) {
            ps.execute();
        } catch (SQLException ignored) {
            // Column already present — expected for fresh databases and idempotent re-runs.
        }
    }

    @Override
    public CompletableFuture<ValmoraPlayer> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = hikari.getConnection()) {
                // 1. Load basic player data
                PreparedStatement psPlayer = conn.prepareStatement("SELECT active_profile FROM valmora_players WHERE uuid = ?");
                psPlayer.setString(1, uuid.toString());
                ResultSet rsPlayer = psPlayer.executeQuery();

                if (!rsPlayer.next()) return null; // Player not found in DB
                
                ValmoraPlayer player = new ValmoraPlayer(uuid);
                String activeProfileId = rsPlayer.getString("active_profile");

                // 2. Load profiles in creation order
                PreparedStatement psProfiles = conn.prepareStatement(
                        "SELECT * FROM valmora_profiles WHERE player_uuid = ? ORDER BY created_at ASC, id ASC");
                psProfiles.setString(1, uuid.toString());
                ResultSet rsProfiles = psProfiles.executeQuery();

                Type statsType = new TypeToken<Map<String, Double>>() {}.getType();
                Type skillsType = new TypeToken<Map<String, Double>>() {}.getType();
                Type tagsType = new TypeToken<Set<String>>() {}.getType();
                Type variablesType = new TypeToken<Map<String, Object>>() {}.getType();
                Type collectionsType = new TypeToken<Map<String, Long>>() {}.getType();

                while (rsProfiles.next()) {
                    long createdAt = rsProfiles.getLong("created_at");
                    long lastUsed = rsProfiles.getLong("last_used");
                    ValmoraProfile profile = new ValmoraProfile(
                            UUID.fromString(rsProfiles.getString("id")),
                            rsProfiles.getString("name"),
                            createdAt,
                            lastUsed
                    );

                    Map<String, Double> stats = gson.fromJson(rsProfiles.getString("stats"), statsType);
                    if (stats != null) profile.getStatManager().loadData(stats);

                    Map<String, Double> skills = gson.fromJson(rsProfiles.getString("skills"), skillsType);
                    if (skills != null) profile.getSkillManager().loadData(skills);

                    String stateJson = rsProfiles.getString("player_state");
                    if (stateJson != null) {
                        double[] stateData = gson.fromJson(stateJson, double[].class);
                        profile.getPlayerState().loadData(stateData);
                    }

                    String tagsJson = rsProfiles.getString("tags");
                    if (tagsJson != null) {
                        Set<String> tags = gson.fromJson(tagsJson, tagsType);
                        if (tags != null) profile.getTags().addAll(tags);
                    }

                    String variablesJson = rsProfiles.getString("variables");
                    if (variablesJson != null) {
                        Map<String, Object> variables = gson.fromJson(variablesJson, variablesType);
                        if (variables != null) profile.getVariables().putAll(variables);
                    }

                    try {
                        String collectionsJson = rsProfiles.getString("collections");
                        if (collectionsJson != null) {
                            Map<String, Long> collections = gson.fromJson(collectionsJson, collectionsType);
                            if (collections != null) profile.getCollectionManager().loadData(collections);
                        }
                    } catch (SQLException ignored) {}

                    try {
                        String inventoryJson = rsProfiles.getString("inventory");
                        if (inventoryJson != null) deserializeInventory(profile, inventoryJson);
                    } catch (SQLException ignored) {}

                    try {
                        String quiverJson = rsProfiles.getString("quiver");
                        profile.setQuiverItems(deserializeItemArray(quiverJson, profile.getQuiverItems().length));
                    } catch (SQLException ignored) {}

                    player.addProfile(profile);
                }

                if (activeProfileId != null) {
                    player.setActiveProfile(UUID.fromString(activeProfileId));
                }

                return player;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load player data for " + uuid, e);
                return null;
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(ValmoraPlayer player) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = hikari.getConnection()) {
                conn.setAutoCommit(false); // Begin Transaction

                // 1. Save Player
                String upsertPlayer = isMySQL ?
                        "INSERT INTO valmora_players (uuid, active_profile) VALUES (?, ?) ON DUPLICATE KEY UPDATE active_profile = ?" :
                        "INSERT INTO valmora_players (uuid, active_profile) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET active_profile = ?";
                
                try (PreparedStatement ps = conn.prepareStatement(upsertPlayer)) {
                    ps.setString(1, player.getUuid().toString());
                    String activeId = player.getActiveProfile() != null ? player.getActiveProfile().getId().toString() : null;
                    ps.setString(2, activeId);
                    ps.setString(3, activeId);
                    ps.executeUpdate();
                }

                // 2. Save Profiles (created_at is set on insert only, last_used is updated on every save)
                String upsertProfile = isMySQL ?
                        "INSERT INTO valmora_profiles (id, player_uuid, name, stats, skills, player_state, tags, variables, collections, inventory, quiver, created_at, last_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, stats = ?, skills = ?, player_state = ?, tags = ?, variables = ?, collections = ?, inventory = ?, quiver = ?, last_used = ?" :
                        "INSERT INTO valmora_profiles (id, player_uuid, name, stats, skills, player_state, tags, variables, collections, inventory, quiver, created_at, last_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = ?, stats = ?, skills = ?, player_state = ?, tags = ?, variables = ?, collections = ?, inventory = ?, quiver = ?, last_used = ?";

                try (PreparedStatement ps = conn.prepareStatement(upsertProfile)) {
                    for (ValmoraProfile profile : player.getProfiles().values()) {
                        ps.setString(1, profile.getId().toString());
                        ps.setString(2, player.getUuid().toString());
                        ps.setString(3, profile.getName());

                        String statsJson = gson.toJson(profile.getStatManager().getSaveData());
                        String skillsJson = gson.toJson(profile.getSkillManager().getSaveData());
                        String stateJson = gson.toJson(profile.getPlayerState().getSaveData());
                        String tagsJson = gson.toJson(profile.getTags());
                        String variablesJson = gson.toJson(profile.getVariables());
                        String collectionsJson = gson.toJson(profile.getCollectionManager().getSaveData());
                        String inventoryJson = serializeInventory(profile);
                        String quiverJson = serializeItemArray(profile.getQuiverItems());

                        ps.setString(4, statsJson);
                        ps.setString(5, skillsJson);
                        ps.setString(6, stateJson);
                        ps.setString(7, tagsJson);
                        ps.setString(8, variablesJson);
                        ps.setString(9, collectionsJson);
                        ps.setString(10, inventoryJson);
                        ps.setString(11, quiverJson);
                        ps.setLong(12, profile.getCreatedAt());
                        ps.setLong(13, profile.getLastUsed());

                        // Update values (no created_at — preserves insertion order)
                        ps.setString(14, profile.getName());
                        ps.setString(15, statsJson);
                        ps.setString(16, skillsJson);
                        ps.setString(17, stateJson);
                        ps.setString(18, tagsJson);
                        ps.setString(19, variablesJson);
                        ps.setString(20, collectionsJson);
                        ps.setString(21, inventoryJson);
                        ps.setString(22, quiverJson);
                        ps.setLong(23, profile.getLastUsed());

                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit(); // Commit Transaction
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save player data for " + player.getUuid(), e);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> deleteProfile(UUID profileId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM valmora_profiles WHERE id = ?")) {
                ps.setString(1, profileId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete profile " + profileId, e);
            }
        }, dbExecutor);
    }

    // Serialize slots 0-35 (storage) + 36-39 (armor) + 40 (offhand) as a base64 JSON array
    private String serializeInventory(ValmoraProfile profile) {
        ItemStack[] storage = profile.getSavedInventory();
        ItemStack[] armor = profile.getSavedArmor();
        ItemStack offhand = profile.getSavedOffhand();
        if (storage == null && armor == null && offhand == null) return null;

        String[] encoded = new String[41];
        if (storage != null) {
            for (int i = 0; i < Math.min(storage.length, 36); i++) {
                encoded[i] = encodeItem(storage[i]);
            }
        }
        if (armor != null) {
            for (int i = 0; i < Math.min(armor.length, 4); i++) {
                encoded[36 + i] = encodeItem(armor[i]);
            }
        }
        encoded[40] = encodeItem(offhand);
        return gson.toJson(encoded);
    }

    private void deserializeInventory(ValmoraProfile profile, String json) {
        String[] encoded = gson.fromJson(json, String[].class);
        if (encoded == null) return;

        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = null;

        for (int i = 0; i < Math.min(encoded.length, 41); i++) {
            if (encoded[i] == null) continue;
            ItemStack item = decodeItem(encoded[i]);
            if (item == null) continue;
            if (i < 36) storage[i] = item;
            else if (i < 40) armor[i - 36] = item;
            else offhand = item;
        }

        profile.setSavedInventory(storage);
        profile.setSavedArmor(armor);
        profile.setSavedOffhand(offhand);
    }

    // Generic fixed-size ItemStack[] <-> base64 JSON array, used for the quiver (and any
    // future flat item-array profile field that isn't the multi-part player inventory).
    private String serializeItemArray(ItemStack[] items) {
        if (items == null) return null;
        String[] encoded = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            encoded[i] = encodeItem(items[i]);
        }
        return gson.toJson(encoded);
    }

    private ItemStack[] deserializeItemArray(String json, int size) {
        ItemStack[] result = new ItemStack[size];
        if (json == null) return result;
        String[] encoded = gson.fromJson(json, String[].class);
        if (encoded == null) return result;
        for (int i = 0; i < Math.min(encoded.length, size); i++) {
            if (encoded[i] == null) continue;
            result[i] = decodeItem(encoded[i]);
        }
        return result;
    }

    private String encodeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            return null;
        }
    }

    private ItemStack decodeItem(String encoded) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public CompletableFuture<double[]> loadEconomy(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = hikari.getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT purse, bank FROM valmora_economy WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return null;
                return new double[]{rs.getDouble("purse"), rs.getDouble("bank")};
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load economy data for " + uuid, e);
                return null;
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> saveEconomy(UUID uuid, double purse, double bank) {
        return CompletableFuture.runAsync(() -> {
            String sql = isMySQL
                ? "INSERT INTO valmora_economy (uuid, purse, bank) VALUES (?,?,?) ON DUPLICATE KEY UPDATE purse=?, bank=?"
                : "INSERT INTO valmora_economy (uuid, purse, bank) VALUES (?,?,?) ON CONFLICT(uuid) DO UPDATE SET purse=?, bank=?";
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, purse);
                ps.setDouble(3, bank);
                ps.setDouble(4, purse);
                ps.setDouble(5, bank);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save economy data for " + uuid, e);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> saveEconomyBatch(Map<UUID, double[]> balances) {
        if (balances.isEmpty()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            String sql = isMySQL
                ? "INSERT INTO valmora_economy (uuid, purse, bank) VALUES (?,?,?) ON DUPLICATE KEY UPDATE purse=?, bank=?"
                : "INSERT INTO valmora_economy (uuid, purse, bank) VALUES (?,?,?) ON CONFLICT(uuid) DO UPDATE SET purse=?, bank=?";

            try (Connection conn = hikari.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (Map.Entry<UUID, double[]> entry : balances.entrySet()) {
                        double[] row = entry.getValue();
                        double purse = row[0];
                        double bank = row[1];
                        ps.setString(1, entry.getKey().toString());
                        ps.setDouble(2, purse);
                        ps.setDouble(3, bank);
                        ps.setDouble(4, purse);
                        ps.setDouble(5, bank);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to batch-save economy data for " + balances.size() + " players", e);
            }
        }, dbExecutor);
    }

    @Override
    public void close() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (hikari != null && !hikari.isClosed()) {
            hikari.close();
        }
    }
}
