package org.nakii.valmora.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariDataSource;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.module.economy.EconomyLedgerEntry;
import org.nakii.valmora.module.pack.PackRecord;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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

    // Dedicated thread pool for database operations. HC-003: size configurable via
    // database.worker-threads (default 4 kept for the legacy no-arg constructor below).
    private final ExecutorService dbExecutor;

    /** Ledger rows kept per player — see {@link #migrateToV7}. HC-005: database.ledger-retention-per-player. */
    private final int ledgerRetentionPerPlayer;

    /**
     * Per-player ordered lanes for everything that reads or writes a player's profile data
     * (profiles, their GUI storage, their economy row). A player always maps to the same
     * single-threaded lane, so a load queued after a save of the same player can never read the
     * row before that save committed — previously a quick quit→rejoin could load stale data on
     * one pool thread while the quit save was still running on another, and the stale session
     * then overwrote the newer one. Different players still run in parallel across lanes.
     */
    private static final int PLAYER_LANES = 4;
    private final ExecutorService[] playerLanes = new ExecutorService[PLAYER_LANES];

    /** profile id → owning player uuid, so profile-keyed storage ops land on the owner's lane. */
    private final Map<UUID, UUID> profileOwners = new java.util.concurrent.ConcurrentHashMap<>();

    /** Where undecodable items are written for manual recovery; {@code null} = log only. */
    private final java.io.File recoveryDir;

    public SQLDataStore(HikariDataSource hikari, boolean isMySQL, Logger logger) {
        this(hikari, isMySQL, logger, null, 4, LEDGER_RETENTION_PER_PLAYER_DEFAULT);
    }

    public SQLDataStore(HikariDataSource hikari, boolean isMySQL, Logger logger, java.io.File dataFolder) {
        this(hikari, isMySQL, logger, dataFolder, 4, LEDGER_RETENTION_PER_PLAYER_DEFAULT);
    }

    public SQLDataStore(HikariDataSource hikari, boolean isMySQL, Logger logger, int workerThreads, int ledgerRetentionPerPlayer) {
        this(hikari, isMySQL, logger, null, workerThreads, ledgerRetentionPerPlayer);
    }

    public SQLDataStore(HikariDataSource hikari, boolean isMySQL, Logger logger,
                        java.io.File dataFolder, int workerThreads, int ledgerRetentionPerPlayer) {
        this.hikari = hikari;
        this.isMySQL = isMySQL;
        this.logger = logger;
        this.gson = new Gson();
        this.recoveryDir = dataFolder != null ? new java.io.File(dataFolder, "recovery") : null;
        this.dbExecutor = Executors.newFixedThreadPool(Math.max(1, workerThreads));
        this.ledgerRetentionPerPlayer = ledgerRetentionPerPlayer > 0 ? ledgerRetentionPerPlayer : LEDGER_RETENTION_PER_PLAYER_DEFAULT;
        for (int i = 0; i < PLAYER_LANES; i++) {
            playerLanes[i] = Executors.newSingleThreadExecutor();
        }
    }

    private ExecutorService laneFor(UUID playerUuid) {
        return playerLanes[Math.floorMod(playerUuid.hashCode(), PLAYER_LANES)];
    }

    private ExecutorService laneForProfile(UUID profileId) {
        UUID owner = profileOwners.get(profileId);
        return laneFor(owner != null ? owner : profileId);
    }

    /**
     * The schema version this build of the plugin expects. Bump this and add a
     * corresponding {@code migrateToVN} step in {@link #applyMigrations} whenever
     * the database layout changes.
     */
    static final int LATEST_SCHEMA_VERSION = 8;

    private static final int LEDGER_RETENTION_PER_PLAYER_DEFAULT = 10;

    @Override
    public void init() {
        try (Connection conn = hikari.getConnection()) {
            ensureSchemaVersionTable(conn);
            int current = getSchemaVersion(conn);

            if (current > LATEST_SCHEMA_VERSION) {
                // A downgraded plugin would read (and then write back) data in a shape it doesn't
                // understand. Refuse to start rather than risk corrupting it.
                logger.severe("Valmora database schema version (" + current + ") is newer than this plugin "
                        + "supports (" + LATEST_SCHEMA_VERSION + "). Install the newer plugin version (or restore "
                        + "a matching backup) — refusing to run against it.");
                throw new IllegalStateException("Database schema v" + current + " is newer than supported v" + LATEST_SCHEMA_VERSION);
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
        if (from < 1) runMigrationStep(conn, 1, () -> migrateToV1(conn));
        if (from < 2) runMigrationStep(conn, 2, () -> migrateToV2(conn));
        if (from < 3) runMigrationStep(conn, 3, () -> migrateToV3(conn));
        if (from < 4) runMigrationStep(conn, 4, () -> migrateToV4(conn));
        if (from < 5) runMigrationStep(conn, 5, () -> migrateToV5(conn));
        if (from < 6) runMigrationStep(conn, 6, () -> migrateToV6(conn));
        if (from < 7) runMigrationStep(conn, 7, () -> migrateToV7(conn));
        if (from < 8) runMigrationStep(conn, 8, () -> migrateToV8(conn));
    }

    @FunctionalInterface
    private interface MigrationStep {
        void run() throws SQLException;
    }

    /**
     * Runs one migration step and stamps its version in a single transaction, so a failure
     * leaves the database at the previous version instead of stamped-but-half-applied. (MySQL
     * auto-commits DDL, so there the guarantee comes from every step being idempotent — a failed
     * step is simply re-run from the start on the next boot.)
     */
    private void runMigrationStep(Connection conn, int version, MigrationStep step) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            step.run();
            setSchemaVersion(conn, version);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw new SQLException("Database migration to v" + version + " failed", e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * v8 — adds the content pack manager's install ledger (docs/modules/design/pack.md). One row
     * per installed pack; {@code file_manifest}/{@code shared_diff}/{@code depends_on} are JSON,
     * matching the Gson-into-TEXT-column idiom used elsewhere in this class. This is the source of
     * truth uninstall reads from to know exactly which files and shared-config keys to remove.
     */
    private void migrateToV8(Connection conn) throws SQLException {
        conn.prepareStatement("""
            CREATE TABLE IF NOT EXISTS valmora_installed_packs (
                pack_id TEXT PRIMARY KEY,
                version TEXT NOT NULL,
                checksum TEXT,
                installed_at BIGINT NOT NULL,
                file_manifest TEXT NOT NULL,
                shared_diff TEXT NOT NULL,
                depends_on TEXT
            )
        """).execute();
    }

    /**
     * v7 — adds the append-only bank transaction ledger backing the bank GUI's "Recent
     * Transactions" display (docs/IMPLEMENTATION_BACKLOG.md, Economy module). Kept to the most
     * recent {@code economy.ledger-retention-per-player} rows per player, pruned on every insert.
     */
    private void migrateToV7(Connection conn) throws SQLException {
        conn.prepareStatement("""
            CREATE TABLE IF NOT EXISTS valmora_economy_ledger (
                uuid VARCHAR(36) NOT NULL,
                type VARCHAR(32) NOT NULL,
                amount DOUBLE NOT NULL,
                purse_after DOUBLE NOT NULL,
                bank_after DOUBLE NOT NULL,
                created_at BIGINT NOT NULL
            )
        """).execute();
        conn.prepareStatement(
                "CREATE INDEX IF NOT EXISTS idx_economy_ledger_uuid ON valmora_economy_ledger(uuid, created_at)"
        ).execute();
    }

    /**
     * v6 — adds the `cooldowns` column so item/ability cooldowns survive a save/load cycle
     * (previously reset on every reload/restart — see docs/IMPLEMENTATION_BACKLOG.md, Profile
     * module). `player_state`'s existing JSON blob format was also extended in place (no schema
     * change needed there) to carry the combat timer and current zone id alongside health/mana.
     */
    private void migrateToV6(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "valmora_profiles", "cooldowns", "TEXT");
    }

    /**
     * v4 — adds the generic per-profile GUI storage-slot table (replaces the old quiver/accessory
     * columns). Keyed by profile id, not player uuid, so each profile keeps independent storage.
     */
    private void migrateToV4(Connection conn) throws SQLException {
        conn.prepareStatement("""
            CREATE TABLE IF NOT EXISTS valmora_storage (
                profile_id VARCHAR(36) NOT NULL,
                storage_id VARCHAR(64) NOT NULL,
                contents   TEXT,
                PRIMARY KEY (profile_id, storage_id)
            )
        """).execute();
    }

    /** v5 — drops the legacy quiver/accessory columns, superseded by valmora_storage (v4). */
    private void migrateToV5(Connection conn) throws SQLException {
        dropColumnIfPresent(conn, "valmora_profiles", "quiver");
        dropColumnIfPresent(conn, "valmora_profiles", "accessory_items");
        dropColumnIfPresent(conn, "valmora_profiles", "accessory_slots");
    }

    /** Drops a column if present, tolerating engines that can't drop columns (left orphaned, harmless). */
    private void dropColumnIfPresent(Connection conn, String table, String column) throws SQLException {
        if (!columnExists(conn, table, column)) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "ALTER TABLE " + table + " DROP COLUMN " + column)) {
            ps.execute();
        } catch (SQLException e) {
            logger.warning("Could not drop obsolete column " + table + "." + column + " (left in place, harmless): " + e.getMessage());
        }
    }

    /** Whether {@code table} has {@code column} (case-insensitive, portable across SQLite/MySQL). */
    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, null)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) return true;
            }
        }
        return false;
    }

    /** v2 — adds the quiver column (per-profile arrow storage). */
    private void migrateToV2(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "valmora_profiles", "quiver", "TEXT");
    }

    /** v3 — adds the accessory bag columns (per-profile item storage + unlocked slot count). */
    private void migrateToV3(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "valmora_profiles", "accessory_items", "TEXT");
        addColumnIfMissing(conn, "valmora_profiles", "accessory_slots", "INTEGER");
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

    /**
     * Adds a column unless it already exists, so it's safe on fresh and re-run databases. Real
     * failures propagate — previously every error was swallowed as "already exists", so a
     * genuinely failed ALTER was still stamped as a completed migration.
     */
    private void addColumnIfMissing(Connection conn, String table, String column, String type) throws SQLException {
        if (columnExists(conn, table, column)) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type)) {
            ps.execute();
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
                Type cooldownsType = new TypeToken<Map<String, Long>>() {}.getType();

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
                    if (stats != null) {
                        // Phase 5 (docs/REFACTOR/PROGRESS.md Task 21): stat ids no longer in the
                        // live StatRegistry are quarantined rather than dropped or silently applied.
                        profile.getQuarantinedStats().putAll(
                                profile.getStatManager().loadDataAndQuarantineUnrecognized(stats));
                    }

                    Map<String, Double> skills = gson.fromJson(rsProfiles.getString("skills"), skillsType);
                    if (skills != null) profile.getSkillManager().loadData(skills);

                    String stateJson = rsProfiles.getString("player_state");
                    if (stateJson != null) {
                        // Extended (2026-08-07) to an object shape carrying combat timer + zone id;
                        // fall back to the pre-extension bare [health, mana] array for old saves.
                        if (stateJson.trim().startsWith("[")) {
                            profile.getPlayerState().loadData(gson.fromJson(stateJson, double[].class));
                        } else {
                            profile.getPlayerState().loadData(gson.fromJson(stateJson, org.nakii.valmora.module.profile.PlayerState.SaveData.class));
                        }
                    }

                    String cooldownsJson = rsProfiles.getString("cooldowns");
                    if (cooldownsJson != null) {
                        Map<String, Long> cooldowns = gson.fromJson(cooldownsJson, cooldownsType);
                        if (cooldowns != null) profile.getCooldownManager().loadData(cooldowns);
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

                    String collectionsJson = rsProfiles.getString("collections");
                    if (collectionsJson != null) {
                        // Extended (2026-08-07) to also carry the reward-grant ledger; fall
                        // back to the pre-extension bare counts-map shape for old saves —
                        // detected by the presence of a top-level "counts" key, since both
                        // shapes serialize as a JSON object (unlike player_state's array-vs-
                        // object distinction, a shape check alone can't tell them apart here).
                        com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(collectionsJson);
                        if (parsed.isJsonObject() && parsed.getAsJsonObject().has("counts")) {
                            profile.getCollectionManager().loadData(
                                    gson.fromJson(collectionsJson, org.nakii.valmora.module.collection.CollectionManager.SaveData.class));
                        } else {
                            Map<String, Long> collections = gson.fromJson(collectionsJson, collectionsType);
                            if (collections != null) profile.getCollectionManager().loadData(collections);
                        }
                    }

                    // Load failures here used to be swallowed, leaving the profile's inventory or
                    // storage empty in memory — which the next save then wrote back over the real
                    // data. Any failure now fails the whole load (see the catch below).
                    String inventoryJson = rsProfiles.getString("inventory");
                    if (inventoryJson != null) deserializeInventory(profile, inventoryJson,
                            "player " + uuid + " profile " + profile.getId() + " inventory");

                    // Eagerly mirror this profile's generic GUI storage (e.g. "accessories") into
                    // its in-memory cache so stat calculation has synchronous access without
                    // waiting for the corresponding GUI to be opened first.
                    try (PreparedStatement psStorage = conn.prepareStatement(
                            "SELECT storage_id, contents FROM valmora_storage WHERE profile_id = ?")) {
                        psStorage.setString(1, profile.getId().toString());
                        try (ResultSet rsStorage = psStorage.executeQuery()) {
                            while (rsStorage.next()) {
                                String storageId = rsStorage.getString("storage_id");
                                profile.putStorage(storageId, deserializeItemArray(rsStorage.getString("contents"),
                                        "player " + uuid + " profile " + profile.getId() + " storage " + storageId));
                            }
                        }
                    }

                    player.addProfile(profile);
                    profileOwners.put(profile.getId(), uuid);
                }

                if (activeProfileId != null) {
                    player.setActiveProfile(UUID.fromString(activeProfileId));
                }

                return player;
            } catch (Exception e) {
                // Never report a failed load as "no data" (null): callers treat null as a first
                // join and create a fresh profile, whose next save would hide the real one.
                logger.log(Level.SEVERE, "Failed to load player data for " + uuid, e);
                throw new java.util.concurrent.CompletionException(
                        new DataLoadException("Failed to load player data for " + uuid, e));
            }
        }, laneFor(uuid));
    }

    /** One profile row, fully serialized — built on the calling (main) thread. */
    private record ProfileRow(String id, String name, String stats, String skills, String state, String tags,
                              String variables, String collections, String inventory, String cooldowns,
                              long createdAt, long lastUsed) {}

    @Override
    public CompletableFuture<Void> savePlayer(ValmoraPlayer player) {
        // Serialize everything NOW, on the caller's thread (the main thread for every live
        // session). The profile's maps and ItemStacks are mutated by gameplay on the main thread;
        // serializing them later on a DB thread risked ConcurrentModificationException or a
        // half-updated snapshot. Only JDBC work is deferred.
        UUID playerUuid = player.getUuid();
        String activeId = player.getActiveProfile() != null ? player.getActiveProfile().getId().toString() : null;
        List<ProfileRow> rows = new ArrayList<>();
        for (ValmoraProfile profile : player.getProfiles().values()) {
            // Phase 5 Task 21: write quarantined (unrecognized) stat ids back unchanged
            // alongside the live ones, so they aren't lost across a save cycle.
            Map<String, Double> statsToSave = new java.util.HashMap<>(profile.getStatManager().getSaveData());
            statsToSave.putAll(profile.getQuarantinedStats());
            rows.add(new ProfileRow(
                    profile.getId().toString(),
                    profile.getName(),
                    gson.toJson(statsToSave),
                    gson.toJson(profile.getSkillManager().getSaveData()),
                    gson.toJson(profile.getPlayerState().getSaveData()),
                    gson.toJson(profile.getTags()),
                    gson.toJson(profile.getVariables()),
                    gson.toJson(profile.getCollectionManager().getSaveData()),
                    serializeInventory(profile),
                    gson.toJson(profile.getCooldownManager().getSaveData()),
                    profile.getCreatedAt(),
                    profile.getLastUsed()));
            profileOwners.put(profile.getId(), playerUuid);
        }

        return CompletableFuture.runAsync(() -> {
            try (Connection conn = hikari.getConnection()) {
                conn.setAutoCommit(false); // Begin Transaction

                // 1. Save Player
                String upsertPlayer = isMySQL ?
                        "INSERT INTO valmora_players (uuid, active_profile) VALUES (?, ?) ON DUPLICATE KEY UPDATE active_profile = ?" :
                        "INSERT INTO valmora_players (uuid, active_profile) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET active_profile = ?";

                try (PreparedStatement ps = conn.prepareStatement(upsertPlayer)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, activeId);
                    ps.setString(3, activeId);
                    ps.executeUpdate();
                }

                // 2. Save Profiles (created_at is set on insert only, last_used is updated on every save)
                String upsertProfile = isMySQL ?
                        "INSERT INTO valmora_profiles (id, player_uuid, name, stats, skills, player_state, tags, variables, collections, inventory, cooldowns, created_at, last_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, stats = ?, skills = ?, player_state = ?, tags = ?, variables = ?, collections = ?, inventory = ?, cooldowns = ?, last_used = ?" :
                        "INSERT INTO valmora_profiles (id, player_uuid, name, stats, skills, player_state, tags, variables, collections, inventory, cooldowns, created_at, last_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = ?, stats = ?, skills = ?, player_state = ?, tags = ?, variables = ?, collections = ?, inventory = ?, cooldowns = ?, last_used = ?";

                try (PreparedStatement ps = conn.prepareStatement(upsertProfile)) {
                    for (ProfileRow row : rows) {
                        ps.setString(1, row.id());
                        ps.setString(2, playerUuid.toString());
                        ps.setString(3, row.name());
                        ps.setString(4, row.stats());
                        ps.setString(5, row.skills());
                        ps.setString(6, row.state());
                        ps.setString(7, row.tags());
                        ps.setString(8, row.variables());
                        ps.setString(9, row.collections());
                        ps.setString(10, row.inventory());
                        ps.setString(11, row.cooldowns());
                        ps.setLong(12, row.createdAt());
                        ps.setLong(13, row.lastUsed());

                        // Update values (no created_at — preserves insertion order)
                        ps.setString(14, row.name());
                        ps.setString(15, row.stats());
                        ps.setString(16, row.skills());
                        ps.setString(17, row.state());
                        ps.setString(18, row.tags());
                        ps.setString(19, row.variables());
                        ps.setString(20, row.collections());
                        ps.setString(21, row.inventory());
                        ps.setString(22, row.cooldowns());
                        ps.setLong(23, row.lastUsed());

                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit(); // Commit Transaction
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save player data for " + playerUuid, e);
                // Surface the failure to the caller instead of completing normally.
                throw new java.util.concurrent.CompletionException(e);
            }
        }, laneFor(playerUuid));
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
        }, laneForProfile(profileId));
    }

    @Override
    public CompletableFuture<ItemStack[]> loadStorage(UUID profileId, String storageId, int expectedSize) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT contents FROM valmora_storage WHERE profile_id = ? AND storage_id = ?")) {
                ps.setString(1, profileId.toString());
                ps.setString(2, storageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return new ItemStack[expectedSize];
                    return deserializeItemArray(rs.getString("contents"), expectedSize,
                            "profile " + profileId + " storage " + storageId);
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load storage '" + storageId + "' for profile " + profileId, e);
                throw new java.util.concurrent.CompletionException(
                        new DataLoadException("Failed to load storage '" + storageId + "' for profile " + profileId, e));
            }
        }, laneForProfile(profileId));
    }

    @Override
    public CompletableFuture<Void> saveStorage(UUID profileId, String storageId, ItemStack[] contents) {
        // Serialize on the caller's thread: `contents` are typically live inventory mirrors.
        String json = serializeItemArray(contents);
        return CompletableFuture.runAsync(() -> {
            String sql = isMySQL
                    ? "INSERT INTO valmora_storage (profile_id, storage_id, contents) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE contents = ?"
                    : "INSERT INTO valmora_storage (profile_id, storage_id, contents) VALUES (?, ?, ?) ON CONFLICT(profile_id, storage_id) DO UPDATE SET contents = ?";
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, profileId.toString());
                ps.setString(2, storageId);
                ps.setString(3, json);
                ps.setString(4, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save storage '" + storageId + "' for profile " + profileId, e);
            }
        }, laneForProfile(profileId));
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

    private void deserializeInventory(ValmoraProfile profile, String json, String context) {
        String[] encoded = gson.fromJson(json, String[].class);
        if (encoded == null) return;

        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = null;

        for (int i = 0; i < Math.min(encoded.length, 41); i++) {
            if (encoded[i] == null) continue;
            ItemStack item = decodeItem(encoded[i], context + " slot " + i);
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

    // Variable-size variant — the array length is taken from the stored data itself rather
    // than a fixed constant, used by the accessory bag whose slot count can grow at runtime.
    private ItemStack[] deserializeItemArray(String json, String context) {
        if (json == null) return new ItemStack[0];
        String[] encoded = gson.fromJson(json, String[].class);
        if (encoded == null) return new ItemStack[0];
        ItemStack[] result = new ItemStack[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            if (encoded[i] == null) continue;
            result[i] = decodeItem(encoded[i], context + " slot " + i);
        }
        return result;
    }

    private ItemStack[] deserializeItemArray(String json, int size, String context) {
        ItemStack[] result = new ItemStack[size];
        if (json == null) return result;
        String[] encoded = gson.fromJson(json, String[].class);
        if (encoded == null) return result;
        for (int i = 0; i < Math.min(encoded.length, size); i++) {
            if (encoded[i] == null) continue;
            result[i] = decodeItem(encoded[i], context + " slot " + i);
        }
        return result;
    }

    private String encodeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            // Previously silent: the item simply vanished from the save.
            logger.log(Level.SEVERE, "Failed to serialize item " + item.getType() + " — it will be missing from this save", e);
            return null;
        }
    }

    private ItemStack decodeItem(String encoded, String context) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            // An undecodable item (corrupt data, or data written by a newer server version) used
            // to be dropped silently — and the next save made that permanent. The slot still
            // loads empty (the player isn't locked out), but the raw bytes are preserved for
            // manual recovery.
            logger.log(Level.SEVERE, "Failed to decode item (" + context + "); raw data preserved in "
                    + (recoveryDir != null ? recoveryDir.getPath() : "this log") + ": " + e.getMessage());
            preserveUndecodable(context, encoded);
            return null;
        }
    }

    private void preserveUndecodable(String context, String encoded) {
        if (recoveryDir == null) {
            logger.severe("Undecodable item data (" + context + "): " + encoded);
            return;
        }
        try {
            recoveryDir.mkdirs();
            java.nio.file.Files.writeString(new java.io.File(recoveryDir, "undecodable_items.log").toPath(),
                    java.time.Instant.now() + "\t" + context + "\t" + encoded + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException io) {
            logger.severe("Could not write recovery file; undecodable item data (" + context + "): " + encoded);
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
                // Must not look like "no row": the caller would cache 0/0 and the next flush
                // would overwrite the real balance.
                logger.log(Level.SEVERE, "Failed to load economy data for " + uuid, e);
                throw new java.util.concurrent.CompletionException(
                        new DataLoadException("Failed to load economy data for " + uuid, e));
            }
        }, laneFor(uuid));
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
                throw new java.util.concurrent.CompletionException(e);
            }
        }, laneFor(uuid));
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
                throw new java.util.concurrent.CompletionException(e);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> appendLedgerEntry(UUID uuid, String type, double amount, double purseAfter, double bankAfter) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = hikari.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO valmora_economy_ledger (uuid, type, amount, purse_after, bank_after, created_at) VALUES (?,?,?,?,?,?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, type);
                    ps.setDouble(3, amount);
                    ps.setDouble(4, purseAfter);
                    ps.setDouble(5, bankAfter);
                    ps.setLong(6, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                // Prune to the retention window. The inner derived-table wrapper is required for
                // MySQL, which otherwise rejects selecting from the same table a DELETE targets.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM valmora_economy_ledger WHERE uuid = ? AND created_at NOT IN (" +
                        "SELECT created_at FROM (SELECT created_at FROM valmora_economy_ledger WHERE uuid = ? " +
                        "ORDER BY created_at DESC LIMIT " + ledgerRetentionPerPlayer + ") AS keep)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to append economy ledger entry for " + uuid, e);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<List<EconomyLedgerEntry>> loadRecentLedger(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<EconomyLedgerEntry> results = new ArrayList<>();
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT type, amount, purse_after, bank_after, created_at FROM valmora_economy_ledger " +
                         "WHERE uuid = ? ORDER BY created_at DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new EconomyLedgerEntry(
                                rs.getString("type"), rs.getDouble("amount"),
                                rs.getDouble("purse_after"), rs.getDouble("bank_after"), rs.getLong("created_at")));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load economy ledger for " + uuid, e);
            }
            return results;
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> savePackRecord(PackRecord record) {
        return CompletableFuture.runAsync(() -> {
            String sql = isMySQL
                    ? "INSERT INTO valmora_installed_packs (pack_id, version, checksum, installed_at, file_manifest, shared_diff, depends_on) " +
                      "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE version=?, checksum=?, installed_at=?, file_manifest=?, shared_diff=?, depends_on=?"
                    : "INSERT INTO valmora_installed_packs (pack_id, version, checksum, installed_at, file_manifest, shared_diff, depends_on) " +
                      "VALUES (?,?,?,?,?,?,?) ON CONFLICT(pack_id) DO UPDATE SET version=?, checksum=?, installed_at=?, file_manifest=?, shared_diff=?, depends_on=?";
            String fileManifestJson = gson.toJson(record.fileManifest());
            String sharedDiffJson = gson.toJson(record.sharedDiff());
            String dependsOnJson = gson.toJson(record.dependsOn());
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.packId());
                ps.setString(2, record.version());
                ps.setString(3, record.checksum());
                ps.setLong(4, record.installedAt());
                ps.setString(5, fileManifestJson);
                ps.setString(6, sharedDiffJson);
                ps.setString(7, dependsOnJson);
                ps.setString(8, record.version());
                ps.setString(9, record.checksum());
                ps.setLong(10, record.installedAt());
                ps.setString(11, fileManifestJson);
                ps.setString(12, sharedDiffJson);
                ps.setString(13, dependsOnJson);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save pack record for " + record.packId(), e);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<List<PackRecord>> loadPackRecords() {
        return CompletableFuture.supplyAsync(() -> {
            List<PackRecord> results = new ArrayList<>();
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT pack_id, version, checksum, installed_at, file_manifest, shared_diff, depends_on FROM valmora_installed_packs");
                 ResultSet rs = ps.executeQuery()) {
                Type stringListType = new TypeToken<List<String>>() {}.getType();
                Type sharedDiffType = new TypeToken<Map<String, Map<String, List<String>>>>() {}.getType();
                while (rs.next()) {
                    List<String> fileManifest = gson.fromJson(rs.getString("file_manifest"), stringListType);
                    Map<String, Map<String, List<String>>> sharedDiff = gson.fromJson(rs.getString("shared_diff"), sharedDiffType);
                    List<String> dependsOn = gson.fromJson(rs.getString("depends_on"), stringListType);
                    results.add(new PackRecord(
                            rs.getString("pack_id"), rs.getString("version"), rs.getString("checksum"),
                            rs.getLong("installed_at"),
                            fileManifest != null ? fileManifest : List.of(),
                            sharedDiff != null ? sharedDiff : Map.of(),
                            dependsOn != null ? dependsOn : List.of()));
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to load pack records", e);
            }
            return results;
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> deletePackRecord(String packId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = hikari.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM valmora_installed_packs WHERE pack_id = ?")) {
                ps.setString(1, packId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete pack record for " + packId, e);
            }
        }, dbExecutor);
    }

    @Override
    public void close() {
        List<ExecutorService> executors = new ArrayList<>(List.of(playerLanes));
        executors.add(dbExecutor);
        for (ExecutorService executor : executors) executor.shutdown();
        try {
            for (ExecutorService executor : executors) {
                var plugin = org.nakii.valmora.Valmora.getInstance();
                int shutdownTimeoutSeconds = plugin != null ? plugin.getConfig().getInt("database.shutdown-timeout-seconds", 10) : 10;
                if (!executor.awaitTermination(shutdownTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            for (ExecutorService executor : executors) executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (hikari != null && !hikari.isClosed()) {
            hikari.close();
        }
    }
}
