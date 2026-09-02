package org.nakii.valmora.infrastructure.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of the content pack manager's namespacing choke point
 * ({@code PackNamespacer} -> {@code YamlLoader.setIdQualifier}) through a real
 * {@link YamlLoader#load} / {@link YamlLoader#loadFilesAsSections} pass over files on disk — proves
 * every existing content loader gets mandatory pack namespacing "for free", with zero changes to
 * any of the ~19 {@code new YamlLoader<>(...)} call sites across the engine.
 */
class YamlLoaderPackNamespacingTest {

    @AfterEach
    void resetGlobalHook() {
        YamlLoader.setIdQualifier(null);
    }

    private Valmora mockPlugin(Path dataFolder) {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("YamlLoaderPackNamespacingTest"));
        return plugin;
    }

    @Test
    void idsUnderAnOwnedFolderAreTransparentlyNamespaced(@TempDir Path dataFolder) throws Exception {
        Path itemsDir = dataFolder.resolve("items/frostspire");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("frost_blade.yml"), "frost_blade:\n  material: DIAMOND_SWORD\n");

        Path baseDir = dataFolder.resolve("items");
        Files.writeString(baseDir.resolve("base_sword.yml"), "base_sword:\n  material: IRON_SWORD\n");

        // No hook installed yet — a fresh YamlLoader must pass ids through unchanged.
        List<String> idsBeforeHook = new ArrayList<>();
        new YamlLoader<String>(mockPlugin(dataFolder), "items", "items")
                .load((id, section, filePath) -> LoadResult.success(id), idsBeforeHook::add);
        assertEquals(java.util.Set.of("frost_blade", "base_sword"), java.util.Set.copyOf(idsBeforeHook));

        // Now install the pack namespacing hook, exactly as PackModule.onEnable() does.
        org.nakii.valmora.module.pack.PackFileIndex index = new org.nakii.valmora.module.pack.PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");
        org.nakii.valmora.module.pack.PackNamespacer.install(index);

        List<String> idsAfterHook = new ArrayList<>();
        new YamlLoader<String>(mockPlugin(dataFolder), "items", "items")
                .load((id, section, filePath) -> LoadResult.success(id), idsAfterHook::add);

        assertEquals(java.util.Set.of("frostspire:frost_blade", "base_sword"), java.util.Set.copyOf(idsAfterHook),
                "the pack-owned file's id is namespaced; the base-content file's id is untouched");
    }

    @Test
    void loadFilesAsSectionsIsAlsoNamespaced(@TempDir Path dataFolder) throws Exception {
        Path petsDir = dataFolder.resolve("pets/frostspire");
        Files.createDirectories(petsDir);
        // loadFilesAsSections uses the filename (sans .yml) as the id, and does not recurse —
        // so the pack's file must live directly in the registered "pets" folder for this mode.
        Path directPetsDir = dataFolder.resolve("pets");
        Files.writeString(directPetsDir.resolve("frost_wolf.yml"), "material: WOLF_SPAWN_EGG\n");

        org.nakii.valmora.module.pack.PackFileIndex index = new org.nakii.valmora.module.pack.PackFileIndex();
        index.registerFolder("pets", "frostspire");
        org.nakii.valmora.module.pack.PackNamespacer.install(index);

        List<String> ids = new ArrayList<>();
        new YamlLoader<String>(mockPlugin(dataFolder), "pets", "Pet")
                .loadFilesAsSections((id, section, filePath) -> LoadResult.success(id), ids::add);

        assertEquals(List.of("frostspire:frost_wolf"), ids);
    }

    @Test
    void uninstallingTheHookRestoresPassthroughBehavior(@TempDir Path dataFolder) throws Exception {
        Path itemsDir = dataFolder.resolve("items/frostspire");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("frost_blade.yml"), "frost_blade:\n  material: DIAMOND_SWORD\n");

        org.nakii.valmora.module.pack.PackFileIndex index = new org.nakii.valmora.module.pack.PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");
        org.nakii.valmora.module.pack.PackNamespacer.install(index);
        org.nakii.valmora.module.pack.PackNamespacer.uninstall();

        List<String> ids = new ArrayList<>();
        new YamlLoader<String>(mockPlugin(dataFolder), "items", "items")
                .load((id, section, filePath) -> LoadResult.success(id), ids::add);

        assertEquals(List.of("frost_blade"), ids);
    }
}
