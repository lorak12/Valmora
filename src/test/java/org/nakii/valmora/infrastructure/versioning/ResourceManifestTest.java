package org.nakii.valmora.infrastructure.versioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ResourceManifestTest {

    private static final Logger LOGGER = Logger.getLogger("ValmoraTest");

    private static Map<String, byte[]> jar(String... pathAndContent) {
        Map<String, byte[]> map = new TreeMap<>();
        for (int i = 0; i < pathAndContent.length; i += 2) {
            map.put(pathAndContent[i], pathAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return map;
    }

    private static String read(Path p) throws Exception {
        return Files.readString(p);
    }

    @Test
    void freshInstallSeedsEverything(@TempDir Path dir) throws Exception {
        var result = ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 1", "machines/anvil.yml", "x: 1"), false, true, LOGGER);
        assertEquals(2, result.seeded().size());
        assertEquals("a: 1", read(dir.resolve("items/a.yml")));
    }

    @Test
    void deletedFileStaysDeleted(@TempDir Path dir) throws Exception {
        ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 1"), false, true, LOGGER);
        Files.delete(dir.resolve("items/a.yml"));
        var result = ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 2"), false, true, LOGGER);
        assertFalse(Files.exists(dir.resolve("items/a.yml")));
        assertEquals(1, result.keptDeleted().size());
    }

    @Test
    void newShippedFileAppearsOnUpdate(@TempDir Path dir) throws Exception {
        ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 1"), false, true, LOGGER);
        var result = ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 1", "items/b.yml", "b: 1"), false, true, LOGGER);
        assertEquals(java.util.List.of("items/b.yml"), result.seeded());
    }

    @Test
    void uneditedFileFollowsUpdates(@TempDir Path dir) throws Exception {
        ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 1"), false, true, LOGGER);
        var result = ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 2"), false, true, LOGGER);
        assertEquals(java.util.List.of("items/a.yml"), result.updated());
        assertEquals("a: 2", read(dir.resolve("items/a.yml")));
    }

    @Test
    void editedFileGetsNewAlongsideOnce(@TempDir Path dir) throws Exception {
        ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 1"), false, true, LOGGER);
        Files.writeString(dir.resolve("items/a.yml"), "a: custom");
        var result = ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 2"), false, true, LOGGER);
        assertEquals("a: custom", read(dir.resolve("items/a.yml")), "admin edits are never overwritten");
        assertEquals("a: 2", read(dir.resolve("items/a.yml.new")));
        assertEquals(1, result.offered().size());
        // Same jar again: not offered a second time.
        assertTrue(ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 2"), false, true, LOGGER).offered().isEmpty());
    }

    @Test
    void lineEndingChangesDontCountAsEdits(@TempDir Path dir) throws Exception {
        ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 1\nb: 2\n"), false, true, LOGGER);
        Files.writeString(dir.resolve("items/a.yml"), "a: 1\r\nb: 2\r\n");
        var result = ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 3\n"), false, true, LOGGER);
        assertEquals(1, result.updated().size());
    }

    @Test
    void legacyInstallOnlySeedsFoldersThatNeverExisted(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("items"));
        Files.writeString(dir.resolve("items/a.yml"), "a: 1");
        // items/b.yml missing: may have been deleted by the admin under the old marker scheme.
        var result = ResourceManifest.sync(dir.toFile(),
                jar("items/a.yml", "a: 1", "items/b.yml", "b: 1", "machines/anvil.yml", "x: 1"), true, true, LOGGER);
        assertFalse(Files.exists(dir.resolve("items/b.yml")));
        assertTrue(Files.exists(dir.resolve("machines/anvil.yml")), "a folder that never existed is installed");
        assertEquals(java.util.List.of("machines/anvil.yml"), result.seeded());
    }

    @Test
    void autoUpdateOffOffersInsteadOfOverwriting(@TempDir Path dir) throws Exception {
        ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 1"), false, false, LOGGER);
        ResourceManifest.sync(dir.toFile(), jar("items/a.yml", "a: 2"), false, false, LOGGER);
        assertEquals("a: 1", read(dir.resolve("items/a.yml")));
        assertEquals("a: 2", read(dir.resolve("items/a.yml.new")));
    }
}
