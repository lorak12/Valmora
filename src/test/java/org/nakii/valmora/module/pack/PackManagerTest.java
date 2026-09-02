package org.nakii.valmora.module.pack;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.database.SQLDataStore;
import org.nakii.valmora.module.ModuleManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of {@link PackManager} against a real (temp) SQLite {@link DataStore} and a
 * real {@link ModuleManager} with a tracking stub module — proves install -> reload-affected-modules
 * -> uninstall -> reload-affected-modules again all wire together correctly, and that uninstall
 * leaves zero trace (docs/modules/design/pack.md, Phase 3 verification plan).
 */
@Tag("database")
class PackManagerTest {

    private static final Logger LOGGER = Logger.getLogger("PackManagerTest");

    /** Tracks enable/disable calls so tests can assert a targeted reload actually happened. */
    static class TrackingModule implements ReloadableModule {
        private final String id;
        int enableCount = 0;
        int disableCount = 0;

        TrackingModule(String id) { this.id = id; }

        @Override public void onEnable() { enableCount++; }
        @Override public void onDisable() { disableCount++; }
        @Override public String getId() { return id; }
    }

    private DataStore newDataStore(Path dir) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dir.resolve("test.db").toAbsolutePath());
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(2);
        cfg.setPoolName("PackManagerTest-Pool");
        SQLDataStore store = new SQLDataStore(new HikariDataSource(cfg), false, LOGGER);
        store.init();
        return store;
    }

    private ModuleManager newModuleManager() {
        Valmora loggerStub = mock(Valmora.class);
        when(loggerStub.getLogger()).thenReturn(LOGGER);
        return new ModuleManager(loggerStub);
    }

    private Valmora mockPlugin(Path dataFolder, ModuleManager moduleManager) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(LOGGER);
        when(plugin.getModuleManager()).thenReturn(moduleManager);
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(description.getVersion()).thenReturn("1.0.0");
        when(plugin.getDescription()).thenReturn(description);
        return plugin;
    }

    private void writeFixturePack(Path sourceDir, String packId) throws Exception {
        Files.writeString(sourceDir.resolve("pack.yml"), packId + ":\n" +
                "  version: \"1.0.0\"\n" +
                "  engine_version_min: \"1.0.0\"\n" +
                "  provides:\n" +
                "    content: [items]\n" +
                "    shared: [rarities.yml]\n");
        Path itemsDir = sourceDir.resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("frost_blade.yml"), "frost_blade:\n  material: DIAMOND_SWORD\n");
        Files.writeString(sourceDir.resolve("rarities.yml"), "rarities:\n  frostbound:\n    rank: 5\n");
    }

    @Test
    void installThenUninstallRoundTripsCleanly(@TempDir Path dataFolder, @TempDir Path dbDir, @TempDir Path sourceDir) throws Exception {
        Files.writeString(dataFolder.resolve("rarities.yml"), "rarities:\n  common:\n    rank: 0\n");
        writeFixturePack(sourceDir, "frostspire");

        DataStore dataStore = newDataStore(dbDir);
        ModuleManager moduleManager = newModuleManager();
        TrackingModule itemsModule = new TrackingModule("items");
        moduleManager.registerModule(itemsModule);
        moduleManager.enableModules();

        Valmora plugin = mockPlugin(dataFolder, moduleManager);
        PackFileIndex fileIndex = new PackFileIndex();
        PackManager manager = new PackManager(plugin, dataStore, fileIndex);
        manager.loadInstalledPacks();

        assertTrue(manager.listInstalled().isEmpty());

        PackManager.OperationResult installResult = manager.install(sourceDir.toFile());
        assertTrue(installResult.success(), () -> installResult.summary() + " / " + installResult.report().getErrors());

        assertTrue(new File(dataFolder.toFile(), "items/frostspire/frost_blade.yml").exists());
        assertEquals("frostspire", fileIndex.ownerOf("items/frostspire/frost_blade.yml").orElseThrow());
        assertTrue(manager.getInstalled("frostspire").isPresent());
        assertEquals(2, itemsModule.enableCount, "1 initial enable + 1 install-triggered reload-enable");
        assertEquals(1, itemsModule.disableCount);

        String merged = Files.readString(dataFolder.resolve("rarities.yml"));
        assertTrue(merged.contains("common"));
        assertTrue(merged.contains("frostbound"));

        // A fresh PackManager (simulating a server restart) must see the same installed pack via the DB.
        PackManager restarted = new PackManager(plugin, dataStore, new PackFileIndex());
        restarted.loadInstalledPacks();
        assertTrue(restarted.getInstalled("frostspire").isPresent());

        PackManager.OperationResult uninstallResult = manager.uninstall("frostspire");
        assertTrue(uninstallResult.success(), () -> uninstallResult.report().getErrors().toString());

        assertFalse(new File(dataFolder.toFile(), "items/frostspire/frost_blade.yml").exists());
        assertTrue(fileIndex.ownerOf("items/frostspire/frost_blade.yml").isEmpty());
        assertTrue(manager.getInstalled("frostspire").isEmpty());
        assertEquals(3, itemsModule.enableCount, "+1 uninstall-triggered reload-enable");
        assertEquals(2, itemsModule.disableCount);

        String reverted = Files.readString(dataFolder.resolve("rarities.yml"));
        assertTrue(reverted.contains("common"));
        assertFalse(reverted.contains("frostbound"));

        PackManager freshCheck = new PackManager(plugin, dataStore, new PackFileIndex());
        freshCheck.loadInstalledPacks();
        assertTrue(freshCheck.listInstalled().isEmpty(), "DB ledger row must be gone after uninstall");

        dataStore.close();
    }

    @Test
    void installingAnAlreadyInstalledPackIdUpgradesInPlace(@TempDir Path dataFolder, @TempDir Path dbDir, @TempDir Path sourceDir) throws Exception {
        Files.writeString(dataFolder.resolve("rarities.yml"), "rarities: {}\n");
        writeFixturePack(sourceDir, "frostspire");

        DataStore dataStore = newDataStore(dbDir);
        ModuleManager moduleManager = newModuleManager();
        moduleManager.registerModule(new TrackingModule("items"));
        moduleManager.enableModules();

        Valmora plugin = mockPlugin(dataFolder, moduleManager);
        PackManager manager = new PackManager(plugin, dataStore, new PackFileIndex());
        manager.loadInstalledPacks();

        assertTrue(manager.install(sourceDir.toFile()).success());

        // Reinstall a v1.1.0 of the same pack id.
        Files.writeString(sourceDir.resolve("pack.yml"), "frostspire:\n" +
                "  version: \"1.1.0\"\n  engine_version_min: \"1.0.0\"\n  provides:\n    content: [items]\n");

        PackManager.OperationResult secondInstall = manager.install(sourceDir.toFile());
        assertTrue(secondInstall.success(), () -> secondInstall.report().getErrors().toString());
        assertEquals(1, manager.listInstalled().size(), "must not accumulate duplicate records for the same pack id");
        assertEquals("1.1.0", manager.getInstalled("frostspire").orElseThrow().version());

        dataStore.close();
    }

    @Test
    void installFailsValidationWhenEngineVersionTooLow(@TempDir Path dataFolder, @TempDir Path dbDir, @TempDir Path sourceDir) throws Exception {
        Files.writeString(sourceDir.resolve("pack.yml"), "frostspire:\n" +
                "  version: \"1.0.0\"\n  engine_version_min: \"99.0.0\"\n  provides:\n    content: [items]\n");
        Path itemsDir = sourceDir.resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("frost_blade.yml"), "frost_blade:\n  material: DIAMOND_SWORD\n");

        DataStore dataStore = newDataStore(dbDir);
        ModuleManager moduleManager = newModuleManager();
        moduleManager.registerModule(new TrackingModule("items"));
        moduleManager.enableModules();

        Valmora plugin = mockPlugin(dataFolder, moduleManager);
        PackManager manager = new PackManager(plugin, dataStore, new PackFileIndex());
        manager.loadInstalledPacks();

        PackManager.OperationResult result = manager.install(sourceDir.toFile());

        assertFalse(result.success());
        assertFalse(new File(dataFolder.toFile(), "items/frostspire").exists(), "nothing should be written on validation failure");
        assertTrue(manager.listInstalled().isEmpty());

        dataStore.close();
    }

    @Test
    void installFailsWhenAHardPackDependencyIsMissing(@TempDir Path dataFolder, @TempDir Path dbDir, @TempDir Path sourceDir) throws Exception {
        Files.writeString(sourceDir.resolve("pack.yml"), "frostspire:\n" +
                "  version: \"1.0.0\"\n  engine_version_min: \"1.0.0\"\n" +
                "  depends:\n    packs:\n      - id: base_rarities_pack\n" +
                "  provides:\n    content: [items]\n");
        Path itemsDir = sourceDir.resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("frost_blade.yml"), "frost_blade:\n  material: DIAMOND_SWORD\n");

        DataStore dataStore = newDataStore(dbDir);
        ModuleManager moduleManager = newModuleManager();
        moduleManager.registerModule(new TrackingModule("items"));
        moduleManager.enableModules();

        Valmora plugin = mockPlugin(dataFolder, moduleManager);
        PackManager manager = new PackManager(plugin, dataStore, new PackFileIndex());
        manager.loadInstalledPacks();

        PackManager.OperationResult result = manager.install(sourceDir.toFile());

        assertFalse(result.success());
        assertTrue(result.report().getErrors().stream().anyMatch(e -> e.contains("base_rarities_pack")));

        dataStore.close();
    }

    @Test
    void uninstallingAnUnknownPackFails(@TempDir Path dataFolder, @TempDir Path dbDir) throws Exception {
        DataStore dataStore = newDataStore(dbDir);
        ModuleManager moduleManager = newModuleManager();
        Valmora plugin = mockPlugin(dataFolder, moduleManager);
        PackManager manager = new PackManager(plugin, dataStore, new PackFileIndex());
        manager.loadInstalledPacks();

        PackManager.OperationResult result = manager.uninstall("does_not_exist");

        assertFalse(result.success());
        dataStore.close();
    }
}
