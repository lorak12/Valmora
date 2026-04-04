package org.nakii.valmora.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariDataSource;

import org.nakii.valmora.profile.PlayerState;
import org.nakii.valmora.profile.ValmoraPlayer;
import org.nakii.valmora.profile.ValmoraProfile;
import org.nakii.valmora.skill.Skill;
import org.nakii.valmora.stat.Stat;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                    player_state TEXT
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

                // 2. Load profiles
                PreparedStatement psProfiles = conn.prepareStatement("SELECT * FROM valmora_profiles WHERE player_uuid = ?");
                psProfiles.setString(1, uuid.toString());
                ResultSet rsProfiles = psProfiles.executeQuery();

                Type statsType = new TypeToken<Map<Stat, Double>>() {}.getType();
                Type skillsType = new TypeToken<Map<Skill, Double>>() {}.getType();
                Type playerStateType = new TypeToken<PlayerState>() {}.getType();

                while (rsProfiles.next()) {
                    ValmoraProfile profile = new ValmoraProfile(
                            UUID.fromString(rsProfiles.getString("id")), // Needs a minor tweak in ValmoraProfile to allow setting ID
                            rsProfiles.getString("name")
                    );

                    // Load JSON Data safely
                    Map<Stat, Double> stats = gson.fromJson(rsProfiles.getString("stats"), statsType);
                    if (stats != null) profile.getStatManager().loadData(stats);

                    Map<Skill, Double> skills = gson.fromJson(rsProfiles.getString("skills"), skillsType);
                    if (skills != null) profile.getSkillManager().loadData(skills);

                    String stateJson = rsProfiles.getString("player_state");
                    if (stateJson != null) {
                        double[] stateData = gson.fromJson(stateJson, double[].class);
                        profile.getPlayerState().loadData(stateData);
                    }

                    player.addProfile(profile);
                }

                if (activeProfileId != null) {
                    player.setActiveProfile(UUID.fromString(activeProfileId));
                }

                return player;
            } catch (SQLException e) {
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

                // 2. Save Profiles
                String upsertProfile = isMySQL ?
                        "INSERT INTO valmora_profiles (id, player_uuid, name, stats, skills, player_state) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, stats = ?, skills = ?, player_state = ?" :
                        "INSERT INTO valmora_profiles (id, player_uuid, name, stats, skills, player_state) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = ?, stats = ?, skills = ?, player_state = ?";
                
                try (PreparedStatement ps = conn.prepareStatement(upsertProfile)) {
                    for (ValmoraProfile profile : player.getProfiles().values()) {
                        ps.setString(1, profile.getId().toString());
                        ps.setString(2, player.getUuid().toString());
                        ps.setString(3, profile.getName());
                        
                        String statsJson = gson.toJson(profile.getStatManager().getSaveData());
                        String skillsJson = gson.toJson(profile.getSkillManager().getSaveData());
                        String stateJson = gson.toJson(profile.getPlayerState().getSaveData());

                        
                        ps.setString(4, statsJson);
                        ps.setString(5, skillsJson);
                        
                        // Update values
                        ps.setString(6, profile.getName());
                        ps.setString(7, statsJson);
                        ps.setString(8, skillsJson);
                        ps.setString(9, stateJson);
                        
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
    public void close() {
        if (hikari != null && !hikari.isClosed()) {
            hikari.close();
        }
        dbExecutor.shutdown();
    }
}