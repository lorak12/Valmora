package org.nakii.valmora.module.pack.download;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackExtractorTest {

    private File zipWithEntries(Path dir, String name, java.util.Map<String, byte[]> entries) throws IOException {
        File zip = dir.resolve(name).toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            for (var entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void extractsAWellFormedArchive(@TempDir Path work) throws Exception {
        File zip = zipWithEntries(work, "good.zip", java.util.Map.of(
                "pack.yml", "frostspire:\n  version: \"1.0.0\"\n".getBytes(),
                "items/frost_blade.yml", "frost_blade:\n  material: DIAMOND_SWORD\n".getBytes()
        ));
        File dest = work.resolve("dest").toFile();

        PackExtractor.extract(zip, dest, 10_000_000, 1000);

        assertTrue(new File(dest, "pack.yml").exists());
        assertTrue(new File(dest, "items/frost_blade.yml").exists());
        assertEquals("frostspire:\n  version: \"1.0.0\"\n", Files.readString(new File(dest, "pack.yml").toPath()));
    }

    @Test
    void rejectsAZipSlipEntryEscapingTheDestination(@TempDir Path work) throws Exception {
        File zip = zipWithEntries(work, "evil.zip", java.util.Map.of(
                "../../evil.yml", "malicious: true\n".getBytes()
        ));
        File dest = work.resolve("dest").toFile();

        IOException ex = assertThrows(IOException.class, () -> PackExtractor.extract(zip, dest, 10_000_000, 1000));
        assertTrue(ex.getMessage().toLowerCase().contains("outside") || ex.getMessage().toLowerCase().contains("escape")
                || ex.getMessage().toLowerCase().contains("zip-slip"));
        assertFalse(new File(work.toFile(), "../evil.yml").exists());
    }

    @Test
    void rejectsAnAbsolutePathEntry(@TempDir Path work) throws Exception {
        String absoluteEntryName = System.getProperty("os.name").toLowerCase().contains("win")
                ? "C:/evil.yml" : "/etc/evil.yml";
        File zip = zipWithEntries(work, "abs.zip", java.util.Map.of(absoluteEntryName, "x".getBytes()));
        File dest = work.resolve("dest").toFile();

        assertThrows(IOException.class, () -> PackExtractor.extract(zip, dest, 10_000_000, 1000));
    }

    @Test
    void rejectsAnArchiveWithTooManyEntries(@TempDir Path work) throws Exception {
        java.util.Map<String, byte[]> entries = new java.util.HashMap<>();
        for (int i = 0; i < 10; i++) entries.put("file" + i + ".yml", "x".getBytes());
        File zip = zipWithEntries(work, "many.zip", entries);
        File dest = work.resolve("dest").toFile();

        IOException ex = assertThrows(IOException.class, () -> PackExtractor.extract(zip, dest, 10_000_000, 5));
        assertTrue(ex.getMessage().contains("entries"));
    }

    @Test
    void rejectsAnArchiveExceedingTheUncompressedSizeCap(@TempDir Path work) throws Exception {
        byte[] big = new byte[10_000];
        File zip = zipWithEntries(work, "big.zip", java.util.Map.of("big.yml", big));
        File dest = work.resolve("dest").toFile();

        IOException ex = assertThrows(IOException.class, () -> PackExtractor.extract(zip, dest, 1_000, 1000));
        assertTrue(ex.getMessage().contains("bytes"));
    }

    @Test
    void directoryEntriesAreCreatedWithoutContent(@TempDir Path work) throws Exception {
        File zip = work.resolve("dirs.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("items/"));
            zos.closeEntry();
        }
        File dest = work.resolve("dest").toFile();

        PackExtractor.extract(zip, dest, 10_000_000, 1000);

        assertTrue(new File(dest, "items").isDirectory());
    }
}
