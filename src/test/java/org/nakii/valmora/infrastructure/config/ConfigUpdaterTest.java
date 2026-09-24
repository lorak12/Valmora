package org.nakii.valmora.infrastructure.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUpdaterTest {

    private static final Logger LOGGER = Logger.getLogger("ValmoraTest");

    private static final String JAR_DEFAULT = """
            config-version: 1
            economy:
              autosave-interval-seconds: 60
              # New in this version.
              death-loss-percent: 50.0
            profiles:
              max-profiles: 4
            """;

    @Test
    void addsMissingKeysAndKeepsAdminValues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                economy:
                  # admin comment
                  autosave-interval-seconds: 15
                """);

        var result = ConfigUpdater.update(file.toFile(),
                () -> new ByteArrayInputStream(JAR_DEFAULT.getBytes(StandardCharsets.UTF_8)), LOGGER);

        assertTrue(result.saved());
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(15, updated.getInt("economy.autosave-interval-seconds"), "admin value untouched");
        assertEquals(50.0, updated.getDouble("economy.death-loss-percent"));
        assertEquals(4, updated.getInt("profiles.max-profiles"));
        assertEquals(ConfigUpdater.LATEST_VERSION, updated.getInt("config-version"));
        assertTrue(Files.readString(file).contains("admin comment"), "admin comments survive");
        assertTrue(Files.list(dir.resolve("backups")).findAny().isPresent(), "a backup was written");
    }

    @Test
    void upToDateFileIsNotRewritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, JAR_DEFAULT);
        var result = ConfigUpdater.update(file.toFile(),
                () -> new ByteArrayInputStream(JAR_DEFAULT.getBytes(StandardCharsets.UTF_8)), LOGGER);
        assertFalse(result.saved());
        assertFalse(Files.exists(dir.resolve("backups")));
    }
}
