package org.nakii.valmora.module.pack.manifest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nakii.valmora.api.config.LoadResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PackManifestParserTest {

    private static final String VALID = """
            frostspire:
              name: "Frostspire Expansion"
              version: "1.2.0"
              author: "SomeAuthor"
              description: "Adds the Frostspire biome."
              engine_version_min: "1.0.0-beta1"
              depends:
                plugins: ["Vault"]
                packs:
                  - id: base_rarities_pack
                    version: ">=1.0.0"
              soft_depends:
                packs:
                  - id: optional_pack
              provides:
                content:
                  - items
                  - quests
                shared:
                  - rarities.yml
              checksum: "sha256:abc123"
            """;

    private File writeManifest(Path dir, String yaml) throws Exception {
        File file = dir.resolve("pack.yml").toFile();
        Files.writeString(file.toPath(), yaml);
        return file;
    }

    @Test
    void parsesAWellFormedManifest(@TempDir Path dir) throws Exception {
        File file = writeManifest(dir, VALID);
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(file);

        assertTrue(result.isSuccess(), () -> "expected success, got: " + result.getError());
        PackManifest manifest = result.getValue();
        assertEquals("frostspire", manifest.id());
        assertEquals("Frostspire Expansion", manifest.name());
        assertEquals("1.2.0", manifest.version());
        assertEquals("1.0.0-beta1", manifest.engineVersionMin());
        assertNull(manifest.engineVersionMax());
        assertEquals(java.util.List.of("Vault"), manifest.dependsPlugins());
        assertEquals(1, manifest.dependsPacks().size());
        assertEquals("base_rarities_pack", manifest.dependsPacks().get(0).id());
        assertEquals(">=1.0.0", manifest.dependsPacks().get(0).versionConstraint());
        assertEquals(1, manifest.softDependsPacks().size());
        assertNull(manifest.softDependsPacks().get(0).versionConstraint());
        assertEquals(java.util.List.of("items", "quests"), manifest.providesContent());
        assertEquals(java.util.List.of("rarities.yml"), manifest.providesShared());
        assertEquals("sha256:abc123", manifest.checksum());
    }

    @Test
    void missingFileFails() {
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(new File("does-not-exist.yml"));
        assertFalse(result.isSuccess());
    }

    @Test
    void noTopLevelKeyFails(@TempDir Path dir) throws Exception {
        File file = writeManifest(dir, "");
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(file);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("exactly one"));
    }

    @Test
    void multipleTopLevelKeysFails(@TempDir Path dir) throws Exception {
        File file = writeManifest(dir, "packa:\n  version: \"1.0.0\"\npackb:\n  version: \"1.0.0\"\n");
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(file);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("exactly one"));
    }

    @Test
    void missingVersionFails(@TempDir Path dir) throws Exception {
        File file = writeManifest(dir, "mypack:\n  engine_version_min: \"1.0.0\"\n  provides:\n    content: [items]\n");
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(file);
        assertFalse(result.isSuccess());
    }

    @Test
    void missingEngineVersionMinFails(@TempDir Path dir) throws Exception {
        File file = writeManifest(dir, "mypack:\n  version: \"1.0.0\"\n  provides:\n    content: [items]\n");
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(file);
        assertFalse(result.isSuccess());
    }

    @Test
    void idWithColonFails(@TempDir Path dir) throws Exception {
        File file = writeManifest(dir, "my:pack:\n  version: \"1.0.0\"\n  engine_version_min: \"1.0.0\"\n  provides:\n    content: [items]\n");
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(file);
        assertFalse(result.isSuccess());
    }

    @Test
    void noProvidesEntriesFails(@TempDir Path dir) throws Exception {
        File file = writeManifest(dir, "mypack:\n  version: \"1.0.0\"\n  engine_version_min: \"1.0.0\"\n");
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(file);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("wouldn't install anything"));
    }

    @Test
    void minimalValidManifestParses(@TempDir Path dir) throws Exception {
        File file = writeManifest(dir, "mypack:\n  version: \"1.0.0\"\n  engine_version_min: \"1.0.0\"\n  provides:\n    content: [items]\n");
        LoadResult<PackManifest, String> result = PackManifestParser.parseFile(file);
        assertTrue(result.isSuccess(), () -> result.getError());
        assertEquals("mypack", result.getValue().name(), "name defaults to id when absent");
        assertEquals("", result.getValue().author());
        assertTrue(result.getValue().dependsPacks().isEmpty());
    }

    @Test
    void parseDirectlyFromSectionMatchesYamlLoaderShape() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mypack.version", "1.0.0");
        config.set("mypack.engine_version_min", "1.0.0");
        config.set("mypack.provides.content", java.util.List.of("items"));

        LoadResult<PackManifest, String> result = PackManifestParser.parse(
                "mypack", config.getConfigurationSection("mypack"), "packs/mypack/pack.yml");
        assertTrue(result.isSuccess());
        assertEquals("mypack", result.getValue().id());
    }
}
