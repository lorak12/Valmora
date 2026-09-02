package org.nakii.valmora.module.pack.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts a downloaded pack archive into a staging directory, guarding against zip-slip (a crafted
 * entry name that escapes the destination directory) and zip-bombs (an archive that decompresses to
 * far more data, or far more files, than its compressed size suggests) — docs/modules/design/pack.md
 * §4. Never extracts directly into a live content folder; the installer only copies files out of the
 * resulting staging directory after {@code PackValidator} has passed.
 */
public final class PackExtractor {

    private PackExtractor() {}

    /**
     * @param zipFile              the downloaded archive
     * @param destDir               staging directory to extract into (created if missing)
     * @param maxUncompressedBytes  zip-bomb guard: total bytes written across all entries
     * @param maxEntries            zip-bomb guard: total entry count
     * @throws IOException if the archive is malformed, an entry would escape {@code destDir}, or
     *                      either guard is exceeded — nothing extracted past that point is cleaned
     *                      up by this method; callers should delete {@code destDir} on failure
     */
    public static void extract(File zipFile, File destDir, long maxUncompressedBytes, int maxEntries) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        Path destRoot = destDir.toPath().toAbsolutePath().normalize();

        int entryCount = 0;
        long totalUncompressed = 0;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new IOException("Archive has more than " + maxEntries + " entries — refusing to extract (zip-bomb guard)");
                }

                String name = entry.getName();
                Path rawTarget = Path.of(name);
                if (rawTarget.isAbsolute()) {
                    throw new IOException("Zip entry '" + name + "' is an absolute path — refusing to extract");
                }

                Path target = destRoot.resolve(name).normalize();
                if (!target.startsWith(destRoot)) {
                    throw new IOException("Zip entry '" + name + "' would extract outside the staging directory — refusing to extract (zip-slip guard)");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        totalUncompressed += read;
                        if (totalUncompressed > maxUncompressedBytes) {
                            throw new IOException("Archive would extract more than " + maxUncompressedBytes
                                    + " bytes — refusing to extract (zip-bomb guard)");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
