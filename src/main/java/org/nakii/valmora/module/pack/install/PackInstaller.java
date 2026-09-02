package org.nakii.valmora.module.pack.install;

import org.nakii.valmora.module.pack.PackFileIndex;
import org.nakii.valmora.module.pack.PackRecord;
import org.nakii.valmora.module.pack.manifest.PackManifest;
import org.nakii.valmora.module.pack.validate.PackValidationReport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Copies a staged pack's content into the live data folder, merges its shared-config fragments, and
 * assembles the {@link PackRecord} ledger row — the write side of docs/modules/design/pack.md §6.
 * Reads from a local, already-staged source directory laid out exactly like the pack itself
 * ({@code packSourceDir/<content-folder>/...}, {@code packSourceDir/<shared-config>.yml}); fetching
 * that directory from a URL/archive is {@code PackDownloader}'s job (Phase 4 — not wired in yet).
 *
 * <p>Every write this class performs is either newly-created content (a pack-scoped subfolder that
 * belongs exclusively to this pack) or a {@link SharedConfigMerger}-tracked diff — both fully
 * reversible via {@link #uninstall}, and a failure partway through {@link #install} rolls back
 * everything written so far via the pre-install {@link PackBackupManager} snapshot.
 */
public class PackInstaller {

    private final File dataFolder;
    private final PackFileIndex fileIndex;
    private final PackBackupManager backupManager;
    private final Logger logger;

    public PackInstaller(File dataFolder, PackFileIndex fileIndex, PackBackupManager backupManager, Logger logger) {
        this.dataFolder = dataFolder;
        this.fileIndex = fileIndex;
        this.backupManager = backupManager;
        this.logger = logger;
    }

    public record InstallOutcome(PackRecord record, PackValidationReport mergeWarnings) {
    }

    /**
     * Installs {@code manifest}'s content from {@code packSourceDir} into the live data folder.
     * Assumes the manifest has already passed {@code PackValidator}/{@code PackDependencyResolver}
     * — this method does no version/dependency checking of its own, only the file-level work.
     *
     * @throws IOException if any file operation fails; on failure, everything written so far by this
     *                      call is rolled back before the exception propagates
     */
    public InstallOutcome install(PackManifest manifest, File packSourceDir) throws IOException {
        List<File> sharedTargets = new ArrayList<>();
        for (String sharedEntry : manifest.providesShared()) {
            sharedTargets.add(new File(dataFolder, sharedEntry));
        }
        File snapshot = backupManager.snapshot(manifest.id(), sharedTargets);

        List<String> fileManifest = new ArrayList<>();
        List<String> registeredFolders = new ArrayList<>();
        try {
            for (String contentEntry : manifest.providesContent()) {
                File source = new File(packSourceDir, contentEntry);
                String ownedFolder = contentEntry + "/" + manifest.id();
                File target = new File(dataFolder, ownedFolder);
                copyDirectory(source, target, fileManifest);
                fileIndex.registerFolder(ownedFolder, manifest.id());
                registeredFolders.add(ownedFolder);
            }

            Map<String, Map<String, List<String>>> sharedDiff = new LinkedHashMap<>();
            PackValidationReport mergeReport = new PackValidationReport();
            for (String sharedEntry : manifest.providesShared()) {
                File base = new File(dataFolder, sharedEntry);
                File fragment = new File(packSourceDir, sharedEntry);
                if (!fragment.exists()) continue;
                if (!base.exists()) {
                    base.getParentFile().mkdirs();
                    base.createNewFile();
                }
                SharedConfigMerger.MergeResult result = SharedConfigMerger.merge(base, fragment);
                if (!result.diff().isEmpty()) {
                    sharedDiff.put(sharedEntry, result.diff());
                }
                result.warnings().forEach(mergeReport::addWarning);
            }

            PackRecord record = new PackRecord(manifest.id(), manifest.version(), manifest.checksum(),
                    System.currentTimeMillis(), fileManifest, sharedDiff, manifest.dependsPacks().stream()
                    .map(org.nakii.valmora.module.pack.manifest.PackDependency::id).toList());

            return new InstallOutcome(record, mergeReport);
        } catch (IOException e) {
            logger.warning("Pack '" + manifest.id() + "' install failed midway (" + e.getMessage() + ") — rolling back.");
            rollbackPartialInstall(manifest.id(), fileManifest, registeredFolders, snapshot);
            throw e;
        }
    }

    private void rollbackPartialInstall(String packId, List<String> fileManifest, List<String> registeredFolders, File snapshot) {
        for (String relativePath : fileManifest) {
            new File(dataFolder, relativePath).delete();
        }
        for (String folder : registeredFolders) {
            fileIndex.unregisterFolder(folder);
            deleteEmptyParents(new File(dataFolder, folder));
        }
        try {
            backupManager.restore(snapshot, dataFolder);
        } catch (IOException e) {
            logger.severe("Failed to restore pre-install backup for pack '" + packId + "' after a failed install: " + e.getMessage());
        }
    }

    /**
     * Removes everything {@code record} owns: its content-folder subpaths, its shared-config diffs
     * (reverted precisely, not blindly re-merged), and its {@link PackFileIndex} entries. Does not
     * touch the DB ledger row — callers remove that separately (see {@code PackManager.uninstall}).
     */
    public void uninstall(PackRecord record) throws IOException {
        for (String relativePath : record.fileManifest()) {
            File file = new File(dataFolder, relativePath);
            file.delete();
            deleteEmptyParents(file.getParentFile());
        }
        fileIndex.unregisterAllForPack(record.packId());

        for (Map.Entry<String, Map<String, List<String>>> entry : record.sharedDiff().entrySet()) {
            File base = new File(dataFolder, entry.getKey());
            if (base.exists()) {
                SharedConfigMerger.revert(base, entry.getValue());
            }
        }
    }

    private void copyDirectory(File source, File target, List<String> fileManifest) throws IOException {
        if (!source.exists()) return;
        if (source.isFile()) {
            copyOneFile(source, target, fileManifest);
            return;
        }
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            copyDirectory(child, new File(target, child.getName()), fileManifest);
        }
    }

    private void copyOneFile(File source, File target, List<String> fileManifest) throws IOException {
        target.getParentFile().mkdirs();
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        String relative = dataFolder.toPath().relativize(target.toPath()).toString().replace(File.separatorChar, '/');
        fileManifest.add(relative);
    }

    /** Deletes {@code dir} and walks upward deleting now-empty parent directories, stopping at the data folder. */
    private void deleteEmptyParents(File dir) {
        while (dir != null && !dir.equals(dataFolder) && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null && children.length == 0) {
                File parent = dir.getParentFile();
                dir.delete();
                dir = parent;
            } else {
                break;
            }
        }
    }
}
