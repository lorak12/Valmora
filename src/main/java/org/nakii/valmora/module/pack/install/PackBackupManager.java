package org.nakii.valmora.module.pack.install;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Snapshots the shared-config files a pack install is about to merge into, before it touches them,
 * so a failed install (or an explicit {@code /valmora pack rollback}) can restore them verbatim
 * (docs/modules/design/pack.md §6). Scoped only to the files actually about to change — content-
 * folder subpaths a pack owns are exclusively its own (never pre-existing, never shared with other
 * packs or base content), so rolling those back is just deleting the folder; nothing to snapshot.
 */
public class PackBackupManager {

    private final File backupRoot;

    /** @param dataFolder the plugin's data folder; backups live under {@code <dataFolder>/.pack-backups/<packId>/}. */
    public PackBackupManager(File dataFolder) {
        this.backupRoot = new File(dataFolder, ".pack-backups");
    }

    /**
     * Zips the current on-disk contents of {@code filesToBackup} (files that don't yet exist are
     * skipped — nothing to restore for those, they're simply deleted on rollback) into a new
     * timestamped snapshot for {@code packId}, returning the snapshot file.
     */
    public File snapshot(String packId, List<File> filesToBackup) throws IOException {
        File dir = new File(backupRoot, packId);
        if (!dir.exists()) dir.mkdirs();
        File zipFile = new File(dir, System.currentTimeMillis() + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File f : filesToBackup) {
                if (!f.exists() || !f.isFile()) continue;
                zos.putNextEntry(new ZipEntry(f.getName()));
                Files.copy(f.toPath(), zos);
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    /** Restores every file in {@code snapshot} back into {@code dataFolder}, overwriting the current copy. */
    public void restore(File snapshot, File dataFolder) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(snapshot))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                // Backups are self-produced (flat filenames only, see #snapshot) — still guard
                // against path traversal defensively rather than trusting that invariant forever.
                File target = new File(dataFolder, new File(entry.getName()).getName());
                Files.copy(zis, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }
    }

    /** Returns the most recent snapshot for {@code packId}, if any exist. */
    public Optional<File> mostRecentSnapshot(String packId) {
        File dir = new File(backupRoot, packId);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".zip"));
        if (files == null || files.length == 0) return Optional.empty();
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
        return Optional.of(sorted.get(0));
    }

    /** Returns the snapshot for {@code packId} taken at exactly {@code timestampMillis}, if it exists. */
    public Optional<File> snapshotAt(String packId, long timestampMillis) {
        File file = new File(new File(backupRoot, packId), timestampMillis + ".zip");
        return file.exists() ? Optional.of(file) : Optional.empty();
    }
}
