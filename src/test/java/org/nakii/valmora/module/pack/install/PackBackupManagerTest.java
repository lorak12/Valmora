package org.nakii.valmora.module.pack.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackBackupManagerTest {

    @Test
    void snapshotAndRestoreRoundTripsFileContent(@TempDir Path dataFolder) throws Exception {
        File rarities = dataFolder.resolve("rarities.yml").toFile();
        Files.writeString(rarities.toPath(), "rarities:\n  common:\n    rank: 0\n");

        PackBackupManager manager = new PackBackupManager(dataFolder.toFile());
        File snapshot = manager.snapshot("frostspire", List.of(rarities));

        // Mutate the live file after the snapshot, as an install's merge step would.
        Files.writeString(rarities.toPath(), "rarities:\n  common:\n    rank: 0\n  frostbound:\n    rank: 5\n");

        manager.restore(snapshot, dataFolder.toFile());

        assertEquals("rarities:\n  common:\n    rank: 0\n", Files.readString(rarities.toPath()));
    }

    @Test
    void snapshotSkipsFilesThatDoNotExistYet(@TempDir Path dataFolder) throws Exception {
        File neverCreated = dataFolder.resolve("does_not_exist.yml").toFile();

        PackBackupManager manager = new PackBackupManager(dataFolder.toFile());
        assertDoesNotThrow(() -> manager.snapshot("frostspire", List.of(neverCreated)));
    }

    @Test
    void mostRecentSnapshotReturnsTheNewestOne(@TempDir Path dataFolder) throws Exception {
        File rarities = dataFolder.resolve("rarities.yml").toFile();
        Files.writeString(rarities.toPath(), "rarities: {}\n");

        PackBackupManager manager = new PackBackupManager(dataFolder.toFile());
        assertTrue(manager.mostRecentSnapshot("frostspire").isEmpty());

        File first = manager.snapshot("frostspire", List.of(rarities));
        Thread.sleep(5);
        File second = manager.snapshot("frostspire", List.of(rarities));

        assertEquals(second, manager.mostRecentSnapshot("frostspire").orElseThrow());
    }

    @Test
    void snapshotAtFindsAnExactTimestamp(@TempDir Path dataFolder) throws Exception {
        File rarities = dataFolder.resolve("rarities.yml").toFile();
        Files.writeString(rarities.toPath(), "rarities: {}\n");

        PackBackupManager manager = new PackBackupManager(dataFolder.toFile());
        File snapshot = manager.snapshot("frostspire", List.of(rarities));
        long timestamp = Long.parseLong(snapshot.getName().replace(".zip", ""));

        assertEquals(snapshot, manager.snapshotAt("frostspire", timestamp).orElseThrow());
        assertTrue(manager.snapshotAt("frostspire", timestamp + 999999).isEmpty());
    }
}
