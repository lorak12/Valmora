package org.nakii.valmora.profile;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.database.SQLDataStore;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.stat.StatRegistry;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end persistence test for the profile/player path that requires a live Bukkit
 * server for {@link ItemStack} serialization. Boots a MockBukkit server and round-trips
 * a full {@link ValmoraPlayer} (profile metadata, tags, variables, and a saved inventory)
 * through {@link SQLDataStore} against a temporary SQLite database.
 */
@Tag("mockbukkit")
class ProfilePersistenceMockTest {

    private static final Logger LOGGER = Logger.getLogger("ValmoraTest");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        // Profile managers (StatManager) read registries from the Valmora API singleton on
        // construction. Provide a mock backed by an empty StatRegistry so they build cleanly.
        ValmoraAPI api = mock(ValmoraAPI.class);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());
        ValmoraAPI.setProvider(api);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private HikariDataSource newDataSource(Path dbFile) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(2);
        cfg.setPoolName("ValmoraTest-Pool");
        return new HikariDataSource(cfg);
    }

    @Test
    void savePlayerLoadPlayerPreservesProfileAndInventory(@TempDir Path dir) {
        HikariDataSource ds = newDataSource(dir.resolve("profile.db"));
        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();

            UUID playerId = UUID.randomUUID();
            ValmoraPlayer player = new ValmoraPlayer(playerId);

            ValmoraProfile profile = new ValmoraProfile("Earth");
            profile.getStatManager().loadData(Map.of("strength", 42.0));
            profile.getSkillManager().loadData(Map.of("mining", 1500.0));
            profile.getTags().add("vip");
            profile.getVariables().put("quest_npc", "blacksmith");

            ItemStack[] inventory = new ItemStack[36];
            inventory[0] = new ItemStack(Material.DIAMOND, 5);
            inventory[8] = new ItemStack(Material.GOLDEN_APPLE, 2);
            profile.setSavedInventory(inventory);

            player.addProfile(profile);
            player.setActiveProfile(profile.getId());

            store.savePlayer(player).join();

            // Reload from a clean read
            ValmoraPlayer loaded = store.loadPlayer(playerId).join();
            assertNotNull(loaded, "player should exist after save");
            assertEquals(playerId, loaded.getUuid());

            ValmoraProfile reloaded = loaded.getActiveProfile();
            assertNotNull(reloaded, "active profile should be restored");
            assertEquals("Earth", reloaded.getName());
            assertEquals(profile.getId(), reloaded.getId());
            assertTrue(reloaded.getTags().contains("vip"), "tags should persist");
            assertEquals("blacksmith", reloaded.getVariables().get("quest_npc"), "variables should persist");
            assertEquals(42.0, reloaded.getStatManager().getSaveData().get("strength"), 1e-6, "stats should persist");
            assertEquals(1500.0, reloaded.getSkillManager().getSaveData().get("mining"), 1e-6, "skills should persist");

            ItemStack[] loadedInventory = reloaded.getSavedInventory();
            assertNotNull(loadedInventory, "saved inventory should persist");
            assertNotNull(loadedInventory[0], "slot 0 item should persist");
            assertEquals(Material.DIAMOND, loadedInventory[0].getType());
            assertEquals(5, loadedInventory[0].getAmount());
            assertNotNull(loadedInventory[8], "slot 8 item should persist");
            assertEquals(Material.GOLDEN_APPLE, loadedInventory[8].getType());
            assertEquals(2, loadedInventory[8].getAmount());
            assertNull(loadedInventory[1], "empty slots should stay empty");
        } finally {
            store.close();
        }
    }

    @Test
    void unknownPlayerLoadsAsNull(@TempDir Path dir) {
        HikariDataSource ds = newDataSource(dir.resolve("empty.db"));
        SQLDataStore store = new SQLDataStore(ds, false, LOGGER);
        try {
            store.init();
            assertNull(store.loadPlayer(UUID.randomUUID()).join(), "unknown player should load as null");
        } finally {
            store.close();
        }
    }
}
