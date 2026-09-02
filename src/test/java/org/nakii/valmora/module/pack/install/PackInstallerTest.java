package org.nakii.valmora.module.pack.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nakii.valmora.module.pack.PackFileIndex;
import org.nakii.valmora.module.pack.PackRecord;
import org.nakii.valmora.module.pack.manifest.PackDependency;
import org.nakii.valmora.module.pack.manifest.PackManifest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PackInstallerTest {

    private static final Logger LOGGER = Logger.getLogger("PackInstallerTest");

    private PackManifest manifest(String id) {
        return new PackManifest(id, id, "1.0.0", "author", "desc", "1.0.0", null,
                List.of(), List.of(new PackDependency("base", null)), List.of(),
                List.of("items"), List.of("rarities.yml"), null);
    }

    private void writeSource(Path sourceDir, String itemContent, String raritiesFragment) throws Exception {
        Path itemsDir = sourceDir.resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("frost_blade.yml"), itemContent);
        Files.writeString(sourceDir.resolve("rarities.yml"), raritiesFragment);
    }

    @Test
    void installCopiesContentRegistersOwnershipAndMergesSharedConfig(@TempDir Path dataFolder, @TempDir Path sourceDir) throws Exception {
        Files.writeString(dataFolder.resolve("rarities.yml"), "rarities:\n  common:\n    rank: 0\n");
        writeSource(sourceDir, "frost_blade:\n  material: DIAMOND_SWORD\n", "rarities:\n  frostbound:\n    rank: 5\n");

        PackFileIndex fileIndex = new PackFileIndex();
        PackBackupManager backupManager = new PackBackupManager(dataFolder.toFile());
        PackInstaller installer = new PackInstaller(dataFolder.toFile(), fileIndex, backupManager, LOGGER);

        PackInstaller.InstallOutcome outcome = installer.install(manifest("frostspire"), sourceDir.toFile());

        assertTrue(new File(dataFolder.toFile(), "items/frostspire/frost_blade.yml").exists());
        assertEquals("frostspire", fileIndex.ownerOf("items/frostspire/frost_blade.yml").orElseThrow());
        assertEquals(List.of("items/frostspire/frost_blade.yml"), outcome.record().fileManifest());
        assertEquals(List.of("frostbound"), outcome.record().sharedDiff().get("rarities.yml").get("rarities"));
        assertEquals(List.of("base"), outcome.record().dependsOn());

        String merged = Files.readString(dataFolder.resolve("rarities.yml"));
        assertTrue(merged.contains("common"));
        assertTrue(merged.contains("frostbound"));
    }

    @Test
    void uninstallRemovesFilesRevertsSharedDiffAndClearsOwnership(@TempDir Path dataFolder, @TempDir Path sourceDir) throws Exception {
        Files.writeString(dataFolder.resolve("rarities.yml"), "rarities:\n  common:\n    rank: 0\n");
        writeSource(sourceDir, "frost_blade:\n  material: DIAMOND_SWORD\n", "rarities:\n  frostbound:\n    rank: 5\n");

        PackFileIndex fileIndex = new PackFileIndex();
        PackInstaller installer = new PackInstaller(dataFolder.toFile(), fileIndex, new PackBackupManager(dataFolder.toFile()), LOGGER);
        PackInstaller.InstallOutcome outcome = installer.install(manifest("frostspire"), sourceDir.toFile());

        installer.uninstall(outcome.record());

        assertFalse(new File(dataFolder.toFile(), "items/frostspire/frost_blade.yml").exists());
        assertFalse(new File(dataFolder.toFile(), "items/frostspire").exists(), "now-empty pack folder must be cleaned up");
        assertTrue(fileIndex.ownerOf("items/frostspire/frost_blade.yml").isEmpty());

        String reverted = Files.readString(dataFolder.resolve("rarities.yml"));
        assertTrue(reverted.contains("common"));
        assertFalse(reverted.contains("frostbound"));
    }

    @Test
    void aFailedInstallRollsBackEverythingWrittenSoFar(@TempDir Path dataFolder, @TempDir Path sourceDir) throws Exception {
        Files.writeString(dataFolder.resolve("rarities.yml"), "rarities:\n  common:\n    rank: 0\n");
        Path itemsDir = sourceDir.resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("frost_blade.yml"), "frost_blade:\n  material: DIAMOND_SWORD\n");
        Files.writeString(sourceDir.resolve("rarities.yml"), "rarities:\n  frostbound:\n    rank: 5\n");

        // Make the second provides.content entry's target unwritable to force a mid-install failure —
        // simplest reliable trigger: point provides.content at a folder whose target path collides
        // with a pre-existing plain FILE (not a directory), so copying into it throws.
        Files.writeString(dataFolder.resolve("mobs"), "not a directory");
        Path mobsSource = sourceDir.resolve("mobs");
        Files.createDirectories(mobsSource);
        Files.writeString(mobsSource.resolve("frost_wolf.yml"), "frost_wolf:\n  type: WOLF\n");

        PackManifest manifest = new PackManifest("frostspire", "frostspire", "1.0.0", "author", "desc", "1.0.0", null,
                List.of(), List.of(), List.of(), List.of("items", "mobs"), List.of("rarities.yml"), null);

        PackFileIndex fileIndex = new PackFileIndex();
        PackInstaller installer = new PackInstaller(dataFolder.toFile(), fileIndex, new PackBackupManager(dataFolder.toFile()), LOGGER);

        assertThrows(java.io.IOException.class, () -> installer.install(manifest, sourceDir.toFile()));

        assertFalse(new File(dataFolder.toFile(), "items/frostspire/frost_blade.yml").exists(),
                "the successfully-copied 'items' content must be rolled back too");
        assertTrue(fileIndex.ownerOf("items/frostspire/frost_blade.yml").isEmpty());
        String rarities = Files.readString(dataFolder.resolve("rarities.yml"));
        assertFalse(rarities.contains("frostbound"), "shared config must be restored to its pre-install state");
    }
}
