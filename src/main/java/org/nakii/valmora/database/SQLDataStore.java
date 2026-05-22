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

public class SQLDataStore implements DataStore {

    private final HikariDataSource hikari;
    private final Gson gson;
    private final boolean isMySQL;
    
    // Dedicated thread pool for database operations
    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    public SQLDataStore(HikariDataSource hikari, boolean isMySQL) {
        this.hikari = hikari;
        this.isMySQL = isMySQL;
        this.gson = new Gson();
    }

    @Override
    public void init() {
        try (Connection conn = hikari.getConnection()) {
            // Player Table
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS valmora_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    active_profile VARCHAR(36)
                )
            """).execute();

            // Profiles Table
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
            // Migration for existing databases
            try { conn.prepareStatement("ALTER TABLE valmora_profiles ADD COLUMN tags TEXT").execute(); } catch (Exception ignored) {}
            try { conn.prepareStatement("ALTER TABLE valmora_profiles ADD COLUMN variables TEXT").execute(); } catch (Exception ignored) {}
            try { conn.prepareStatement("ALTER TABLE valmora_profiles ADD COLUMN collections TEXT").execute(); } catch (Exception ignored) {}
            try { conn.prepareStatement("ALTER TABLE valmora_profiles ADD COLUMN inventory TEXT").execute(); } catch (Exception ignored) {}
            try { conn.prepareStatement("ALTER TABLE valmora_profiles ADD COLUMN created_at BIGINT NOT NULL DEFAULT 0").execute(); } catch (Exception ignored) {}
            try { conn.prepareStatement("ALTER TABLE valmora_profiles ADD COLUMN last_used BIGINT NOT NULL DEFAULT 0").execute(); } catch (Exception ignored) {}

            // Economy Table
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS valmora_economy (
                    uuid VARCHAR(36) PRIMARY KEY,
                    purse DOUBLE NOT NULL DEFAULT 0,
                    bank  DOUBLE NOT NULL DEFAULT 0
                )
            """).execute();
        } catch (SQLException e) {
            e.printStackTrace();
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

                    player.addProfile(profile);
                }

                if (activeProfileId != null) {
                    player.setActiveProfile(UUID.fromString(activeProfileId));
                }

                return player;
            } catch (Exception e) {
                e.printStackTrace();
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
                        "INSERT INTO valmora_profiles (id, player_uuid, name, stats, skills, player_state, tags, variables, collections, inventory, created_at, last_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, stats = ?, skills = ?, player_state = ?, tags = ?, variables = ?, collections = ?, inventory = ?, last_used = ?" :
                        "INSERT INTO valmora_profiles (id, player_uuid, name, stats, skills, player_state, tags, variables, collections, inventory, created_at, last_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = ?, stats = ?, skills = ?, player_state = ?, tags = ?, variables = ?, collections = ?, inventory = ?, last_used = ?";

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

                        ps.setString(4, statsJson);
                        ps.setString(5, skillsJson);
                        ps.setString(6, stateJson);
                        ps.setString(7, tagsJson);
                        ps.setString(8, variablesJson);
                        ps.setString(9, collectionsJson);
                        ps.setString(10, inventoryJson);
                        ps.setLong(11, profile.getCreatedAt());
                        ps.setLong(12, profile.getLastUsed());

                        // Update values (no created_at — preserves insertion order)
                        ps.setString(13, profile.getName());
                        ps.setString(14, statsJson);
                        ps.setString(15, skillsJson);
                        ps.setString(16, stateJson);
                        ps.setString(17, tagsJson);
                        ps.setString(18, variablesJson);
                        ps.setString(19, collectionsJson);
                        ps.setString(20, inventoryJson);
                        ps.setLong(21, profile.getLastUsed());

                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit(); // Commit Transaction
            } catch (SQLException e) {
                e.printStackTrace();
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
                e.printStackTrace();
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
                e.printStackTrace();
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
                e.printStackTrace();
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
