package org.nakii.valmora.infrastructure.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.LoadReport;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.Severity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Parsers report through the {@link LoadScope} the loader opens around each entry. */
class YamlLoaderScopeTest {

    @BeforeEach
    void begin() {
        LoadReport.global().begin();
    }

    @AfterEach
    void end() {
        LoadReport.global().begin();
    }

    private Valmora mockPlugin(Path dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("YamlLoaderScopeTest"));
        return plugin;
    }

    @Test
    void parserWarningsAreAttributedToFileAndEntry(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("things/sub/a.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "sword:\n  power: 5\nsettings:\n  x: 1\nstray: 3\n");
        List<String> loaded = new ArrayList<>();
        new YamlLoader<String>(mockPlugin(dir), "things", "Things").load((id, section, path) -> {
            if (id.equals("settings")) return LoadResult.skip();
            LoadScope.current().orElseThrow().sub("power").warn("looks low");
            return LoadResult.success(id);
        }, loaded::add);

        assertEquals(List.of("sword"), loaded);
        assertEquals(0, LoadScope.depth(), "no scope leaked");
        List<ConfigDiagnostic> diags = LoadReport.global().current();
        ConfigDiagnostic warning = diags.stream().filter(d -> d.message().equals("looks low")).findFirst().orElseThrow();
        assertEquals("things/sub/a.yml", warning.file());
        assertEquals("sword", warning.entryId());
        assertEquals("power", warning.path());
        // A scalar top-level key is flagged; the skipped section is not an error.
        assertTrue(diags.stream().anyMatch(d -> "stray".equals(d.entryId()) && d.severity() == Severity.WARN));
        assertTrue(diags.stream().noneMatch(d -> "settings".equals(d.entryId())));
        var stats = LoadReport.global().currentTypes();
        assertEquals(1, stats.get(0).loaded());
        assertEquals(1, stats.get(0).files());
    }

    @Test
    void thrownExceptionsBecomeErrors(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("things/a.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "sword:\n  power: 5\n");
        new YamlLoader<String>(mockPlugin(dir), "things", "Things").load((id, section, path) -> {
            throw new IllegalStateException("boom");
        }, s -> {});
        assertEquals(List.of("[things/a.yml] 'sword': boom"), YamlLoader.drainReport());
        assertEquals(0, LoadScope.depth());
    }

    @Test
    void filesAsSectionsReportsSyntaxErrorsAndKeepsPrevious(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("skills/mining.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "max-level: 5\n");
        Files.writeString(dir.resolve("skills/xp_curves.yml"), "xp_curves: {}\n");
        List<String> loaded = new ArrayList<>();
        YamlLoader<String> loader = new YamlLoader<String>(mockPlugin(dir), "skills", "Skills").ignoreFiles("xp_curves.yml");
        loader.loadFilesAsSections((id, section, path) -> LoadResult.success(id + ":" + section.getInt("max-level")), loaded::add);
        assertEquals(List.of("mining:5"), loaded);

        Files.writeString(file, "max-level: [oops\n");
        loaded.clear();
        LoadReport.global().begin();
        loader.loadFilesAsSections((id, section, path) -> LoadResult.success(id + ":" + section.getInt("max-level")), loaded::add);
        assertEquals(List.of("mining:5"), loaded, "kept the last good version");
        List<String> errors = YamlLoader.drainReport();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Invalid YAML"));
        assertTrue(errors.get(0).contains("kept the previous version"));
    }
}
