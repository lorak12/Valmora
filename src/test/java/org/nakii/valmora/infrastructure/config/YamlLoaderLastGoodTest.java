package org.nakii.valmora.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A broken edit must never remove live content: entries that stop loading, or whole files with a
 * YAML syntax error, fall back to their last successfully loaded version.
 */
class YamlLoaderLastGoodTest {

    private Valmora mockPlugin(Path dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("YamlLoaderLastGoodTest"));
        return plugin;
    }

    /** Parser: value = the entry's "power"; an entry with "power: -1" is invalid. */
    private Map<String, Integer> load(Path dataFolder) {
        Map<String, Integer> loaded = new HashMap<>();
        new YamlLoader<Map.Entry<String, Integer>>(mockPlugin(dataFolder), "things", "things").load(
                (id, section, path) -> section.getInt("power") < 0
                        ? LoadResult.failure("[" + path + "] '" + id + "': negative power")
                        : LoadResult.success(Map.entry(id, section.getInt("power"))),
                e -> loaded.put(e.getKey(), e.getValue()));
        return loaded;
    }

    @Test
    void brokenEntryKeepsPreviousVersion(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("things/a.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "sword:\n  power: 5\nbow:\n  power: 3\n");
        assertEquals(Map.of("sword", 5, "bow", 3), load(dir));

        YamlLoader.beginReport();
        Files.writeString(file, "sword:\n  power: -1\nbow:\n  power: 4\n");
        assertEquals(Map.of("sword", 5, "bow", 4), load(dir), "sword keeps v1; bow's valid edit applies");
        assertEquals(1, YamlLoader.drainReport().size());
    }

    @Test
    void syntaxErrorKeepsWholeFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("things/a.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "sword:\n  power: 5\n");
        load(dir);

        Files.writeString(file, "sword:\n  power: [unclosed\n");
        assertEquals(Map.of("sword", 5), load(dir));
    }

    @Test
    void deliberatelyRemovedEntryIsNotResurrected(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("things/a.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "sword:\n  power: 5\nbow:\n  power: 3\n");
        load(dir);

        Files.writeString(file, "bow:\n  power: 3\n");
        assertEquals(Map.of("bow", 3), load(dir));
        // And a later syntax error in that file doesn't bring it back either.
        Files.writeString(file, "bow: [\n");
        assertEquals(Map.of("bow", 3), load(dir));
    }

    @Test
    void lastGoodSurvivesARestart(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("things/a.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "sword:\n  power: 5\n");
        load(dir);
        assertTrue(Files.exists(dir.resolve(".last-good/things.yml")));
    }
}
