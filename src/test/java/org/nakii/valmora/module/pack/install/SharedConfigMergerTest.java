package org.nakii.valmora.module.pack.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SharedConfigMergerTest {

    private File write(Path dir, String name, String content) throws Exception {
        File file = dir.resolve(name).toFile();
        Files.writeString(file.toPath(), content);
        return file;
    }

    @Test
    void mergesNewSubKeysIntoAnExistingSection(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "rarities:\n  common:\n    rank: 0\n");
        File fragment = write(dir, "fragment.yml", "rarities:\n  frostbound:\n    rank: 5\n");

        SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);

        String merged = Files.readString(base.toPath());
        assertTrue(merged.contains("common"));
        assertTrue(merged.contains("frostbound"));
        assertEquals(java.util.List.of("frostbound"), result.diff().get("rarities"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void collidingSubKeyIsLeftUntouchedAndWarned(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "rarities:\n  common:\n    rank: 0\n");
        File fragment = write(dir, "fragment.yml", "rarities:\n  common:\n    rank: 99\n");

        SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);

        org.bukkit.configuration.file.YamlConfiguration merged = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base);
        assertEquals(0, merged.getInt("rarities.common.rank"), "existing value must not be overwritten");
        assertFalse(result.diff().containsKey("rarities"), "nothing was actually added");
        assertEquals(1, result.warnings().size());
    }

    @Test
    void appendsNewListElementsAndDedupsExistingOnes(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "item_types:\n  - FISHING_BAIT\n");
        File fragment = write(dir, "fragment.yml", "item_types:\n  - FISHING_BAIT\n  - FROST_CHARM\n");

        SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);

        org.bukkit.configuration.file.YamlConfiguration merged = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base);
        assertEquals(java.util.List.of("FISHING_BAIT", "FROST_CHARM"), merged.getStringList("item_types"));
        assertEquals(java.util.List.of("FROST_CHARM"), result.diff().get("item_types"));
    }

    @Test
    void wholeNewTopLevelKeyIsAddedWithSentinelDiff(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "existing_key: true\n");
        File fragment = write(dir, "fragment.yml", "rarities:\n  frostbound:\n    rank: 5\n");

        SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);

        org.bukkit.configuration.file.YamlConfiguration merged = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base);
        assertTrue(merged.isConfigurationSection("rarities"));
        assertEquals(java.util.List.of("*"), result.diff().get("rarities"));
    }

    @Test
    void incompatibleShapeIsSkippedAndWarned(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "rarities: true\n");
        File fragment = write(dir, "fragment.yml", "rarities:\n  frostbound:\n    rank: 5\n");

        SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);

        org.bukkit.configuration.file.YamlConfiguration merged = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base);
        assertTrue(merged.getBoolean("rarities"), "base value must be untouched");
        assertTrue(result.diff().isEmpty());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void revertRemovesExactlyWhatWasAddedAndNothingElse(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "rarities:\n  common:\n    rank: 0\n");
        File fragment = write(dir, "fragment.yml", "rarities:\n  frostbound:\n    rank: 5\n");
        SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);

        SharedConfigMerger.revert(base, result.diff());

        org.bukkit.configuration.file.YamlConfiguration reverted = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base);
        assertTrue(reverted.isSet("rarities.common"), "pre-existing content must survive revert");
        assertFalse(reverted.isSet("rarities.frostbound"), "added content must be gone after revert");
    }

    @Test
    void revertOfAWholeAddedKeyRemovesTheEntireKey(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "existing_key: true\n");
        File fragment = write(dir, "fragment.yml", "rarities:\n  frostbound:\n    rank: 5\n");
        SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);

        SharedConfigMerger.revert(base, result.diff());

        org.bukkit.configuration.file.YamlConfiguration reverted = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base);
        assertFalse(reverted.isSet("rarities"));
        assertTrue(reverted.getBoolean("existing_key"));
    }

    @Test
    void revertOfAddedListElementsRemovesOnlyThose(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "item_types:\n  - FISHING_BAIT\n");
        File fragment = write(dir, "fragment.yml", "item_types:\n  - FROST_CHARM\n");
        SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);

        SharedConfigMerger.revert(base, result.diff());

        org.bukkit.configuration.file.YamlConfiguration reverted = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base);
        assertEquals(java.util.List.of("FISHING_BAIT"), reverted.getStringList("item_types"));
    }

    @Test
    void revertWithEmptyDiffIsANoOp(@TempDir Path dir) throws Exception {
        File base = write(dir, "base.yml", "rarities:\n  common:\n    rank: 0\n");
        String before = Files.readString(base.toPath());

        SharedConfigMerger.revert(base, java.util.Map.of());
        SharedConfigMerger.revert(base, null);

        assertEquals(before, Files.readString(base.toPath()));
    }
}
