package org.nakii.valmora.module.time;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link TimeManager} — previously untested (per docs/IMPLEMENTATION_BACKLOG.md's
 * cross-cutting "unit tests for untested modules" item). Focuses on the pure date-math and
 * persistence logic (getSnapshot/setDate/resetOffset/save, the malformed-time.yml fallback,
 * scoreboard-enabled config passthrough) — the tick()/reconcileMissedTransitions() event-firing
 * paths need a live scheduler and are left for a follow-up.
 */
public class TimeManagerTest {

    private File dataFolder;
    private Valmora plugin;
    private World world;
    private MockedStatic<Bukkit> bukkitStatic;
    private long worldFullTime;
    private long worldTime;

    @BeforeEach
    void setUp() throws IOException {
        dataFolder = Files.createTempDirectory("time-manager-test").toFile();
        plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TimeManagerTest"));

        YamlConfiguration config = new YamlConfiguration();
        config.set("time.world", "world");
        config.set("time.start-year", 1);
        config.set("time.start-season", "SPRING");
        config.set("time.start-phase", "EARLY");
        config.set("time.start-day", 1);
        config.set("time.season-names", List.of("Spring", "Summer", "Autumn", "Winter"));
        config.set("time.phase-names", List.of("Early", "Mid", "Late"));
        config.set("time.scoreboard-enabled", true);
        when(plugin.getConfig()).thenReturn(config);

        world = mock(World.class);
        worldFullTime = 0L;
        worldTime = 0L;
        when(world.getFullTime()).thenAnswer(inv -> worldFullTime);
        when(world.getTime()).thenAnswer(inv -> worldTime);

        bukkitStatic = mockStatic(Bukkit.class);
        bukkitStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        bukkitStatic.when(Bukkit::getScheduler).thenReturn(scheduler);
        var server = mock(org.bukkit.Server.class);
        var pluginManager = mock(org.bukkit.plugin.PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        bukkitStatic.when(Bukkit::getServer).thenReturn(server);
        when(plugin.getServer()).thenReturn(server);
    }

    @AfterEach
    void tearDown() {
        bukkitStatic.close();
        deleteRecursive(dataFolder);
    }

    private void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        file.delete();
    }

    @Test
    void freshInstallStartsAtConfiguredStartDate() {
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();

        TimeSnapshot snap = tm.getSnapshot();
        assertEquals(1, snap.year());
        assertEquals(Season.SPRING, snap.season());
        assertEquals(Phase.EARLY, snap.phase());
        assertEquals(1, snap.dayInPhase());
        assertEquals("Spring", snap.seasonName());
        assertEquals("Early", snap.phaseName());
    }

    @Test
    void freshInstallWritesTimeYml() {
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();

        File timeFile = new File(dataFolder, "time.yml");
        assertTrue(timeFile.exists());
    }

    @Test
    void isScoreboardEnabledReadsConfig() {
        plugin.getConfig().set("time.scoreboard-enabled", false);
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();
        assertFalse(tm.isScoreboardEnabled());
    }

    @Test
    void setDateJumpsToExplicitDate() {
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();

        tm.setDate(3, Season.AUTUMN, Phase.LATE, 15);
        TimeSnapshot snap = tm.getSnapshot();
        assertEquals(3, snap.year());
        assertEquals(Season.AUTUMN, snap.season());
        assertEquals(Phase.LATE, snap.phase());
        assertEquals(15, snap.dayInPhase());
    }

    @Test
    void setDateClampsDayTo1Through30() {
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();

        tm.setDate(1, Season.SPRING, Phase.EARLY, 99);
        assertEquals(30, tm.getSnapshot().dayInPhase());

        tm.setDate(1, Season.SPRING, Phase.EARLY, -5);
        assertEquals(1, tm.getSnapshot().dayInPhase());
    }

    @Test
    void resetOffsetReturnsToConfiguredStartDate() {
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();
        tm.setDate(5, Season.WINTER, Phase.MID, 20);

        tm.resetOffset();
        TimeSnapshot snap = tm.getSnapshot();
        assertEquals(1, snap.year());
        assertEquals(Season.SPRING, snap.season());
        assertEquals(Phase.EARLY, snap.phase());
        assertEquals(1, snap.dayInPhase());
    }

    @Test
    void getSnapshotAdvancesWithWorldFullTime() {
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();

        // 45 world-days elapsed (45 * 24000 ticks) — should land in phase MID (day 16-30) of EARLY... actually
        // 45 days = 1 phase (30) + 15 days into the 2nd phase (MID), still SPRING (90-day season).
        worldFullTime = 45L * 24000L;
        TimeSnapshot snap = tm.getSnapshot();
        assertEquals(Season.SPRING, snap.season());
        assertEquals(Phase.MID, snap.phase());
        assertEquals(16, snap.dayInPhase());
    }

    @Test
    void getSnapshotDerivesHourAndMinuteFromWorldTime() {
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();

        // World tick 0 = 06:00 in-game (per TimeManager's (tick/1000 + 6) % 24 formula).
        worldTime = 0L;
        assertEquals(6, tm.getSnapshot().hour());
        assertEquals(0, tm.getSnapshot().minute());

        worldTime = 6000L; // 6000 ticks later = 6 hours later -> 12:00
        assertEquals(12, tm.getSnapshot().hour());
    }

    @Test
    void unknownWorldFallsBackToEpochSnapshot() {
        bukkitStatic.when(() -> Bukkit.getWorld("world")).thenReturn(null);
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();

        assertEquals(TimeSnapshot.EPOCH, tm.getSnapshot());
    }

    @Test
    void malformedTimeYmlFallsBackToComputedOffsetInsteadOfThrowing() throws IOException {
        File timeFile = new File(dataFolder, "time.yml");
        try (PrintWriter writer = new PrintWriter(timeFile)) {
            writer.println("day-offset: [this is not: a valid, yaml value}");
        }

        TimeManager tm = new TimeManager(plugin);
        assertDoesNotThrow(tm::onEnable);

        TimeSnapshot snap = tm.getSnapshot();
        assertEquals(1, snap.year());
        assertEquals(Season.SPRING, snap.season());
    }

    @Test
    void saveWritesDayOffsetAndLastWorldDay() {
        TimeManager tm = new TimeManager(plugin);
        tm.onEnable();
        tm.setDate(2, Season.SUMMER, Phase.EARLY, 1);

        File timeFile = new File(dataFolder, "time.yml");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(timeFile);
        assertTrue(saved.contains("day-offset"));
        assertTrue(saved.contains("last-world-day"));
    }
}
