package org.nakii.valmora.module.pack;

import org.bukkit.Bukkit;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.module.ModuleManager;
import org.nakii.valmora.module.pack.download.GitHubReleaseResolver;
import org.nakii.valmora.module.pack.download.PackDownloader;
import org.nakii.valmora.module.pack.download.PackExtractor;
import org.nakii.valmora.module.pack.install.PackBackupManager;
import org.nakii.valmora.module.pack.install.PackInstaller;
import org.nakii.valmora.module.pack.manifest.PackManifest;
import org.nakii.valmora.module.pack.manifest.PackManifestParser;
import org.nakii.valmora.module.pack.validate.PackDependencyResolver;
import org.nakii.valmora.module.pack.validate.PackValidationReport;
import org.nakii.valmora.module.pack.validate.PackValidator;
import org.nakii.valmora.util.DebugManager;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Orchestrates the pack lifecycle (docs/modules/design/pack.md §1): validates a staged pack against
 * already-installed packs, installs/uninstalls it via {@link PackInstaller}, persists its ledger row
 * via {@link DataStore}, and reloads exactly the modules it touches via
 * {@link ModuleManager#reloadModules(Set)}. {@link #install(File)}/{@link #uninstall}/
 * {@link #rollback} operate on a pack already staged in a local directory and run synchronously on
 * the calling thread (including a blocking {@code CompletableFuture.join()} on the DB calls),
 * matching {@code /valmora reload}'s own fully synchronous nature — this is admin-command-triggered,
 * infrequent, and must finish before a module reload can safely run (which itself touches Bukkit API,
 * so it cannot happen from an async continuation per CLAUDE.md §7.4).
 *
 * <p>{@link #installFromSource} is the one entry point that may involve real network I/O (a direct
 * URL or a {@code github:owner/repo@tag} shorthand) — it runs the download/extract off the main
 * thread via {@code Bukkit.getScheduler().runTaskAsynchronously}, then hops back to the main thread
 * only for the actual {@link #install(File)} call, delivering the result to {@code callback} there.
 */
public class PackManager {

    private final Valmora plugin;
    private final DataStore dataStore;
    private final PackFileIndex fileIndex;
    private final PackBackupManager backupManager;
    private final PackInstaller installer;
    private final PackDownloader downloader;
    private final HttpClient httpClient;
    private final Logger logger;

    private final Map<String, PackRecord> installedByPackId = new ConcurrentHashMap<>();

    public PackManager(Valmora plugin, DataStore dataStore, PackFileIndex fileIndex) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.fileIndex = fileIndex;
        this.backupManager = new PackBackupManager(plugin.getDataFolder());
        this.installer = new PackInstaller(plugin.getDataFolder(), fileIndex, backupManager, plugin.getLogger());
        this.downloader = new PackDownloader();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.logger = plugin.getLogger();
    }

    /** Reloads the in-memory installed-pack cache from the DB ledger — call on module (re)enable. */
    public void loadInstalledPacks() {
        installedByPackId.clear();
        for (PackRecord record : dataStore.loadPackRecords().join()) {
            installedByPackId.put(record.packId().toLowerCase(java.util.Locale.ROOT), record);
        }
    }

    public Optional<PackRecord> getInstalled(String packId) {
        return Optional.ofNullable(installedByPackId.get(packId.toLowerCase(java.util.Locale.ROOT)));
    }

    public List<PackRecord> listInstalled() {
        return List.copyOf(installedByPackId.values());
    }

    public record OperationResult(boolean success, PackValidationReport report, String summary) {
        public static OperationResult failure(PackValidationReport report) {
            return new OperationResult(false, report, String.join("; ", report.getErrors()));
        }

        public static OperationResult success(PackValidationReport report, String summary) {
            return new OperationResult(true, report, summary);
        }
    }

    /**
     * Validates and installs the pack staged at {@code packSourceDir} (must contain a {@code pack.yml}
     * at its root). If a pack with the same id is already installed, it's uninstalled first (upgrade
     * = clean uninstall + fresh install — no in-place content diffing in this phase).
     */
    public OperationResult install(File packSourceDir) {
        OperationResult result = installInternal(packSourceDir);
        DebugManager.log("pack", "install(" + packSourceDir + ") -> success=" + result.success() + " summary=" + result.summary());
        return result;
    }

    private OperationResult installInternal(File packSourceDir) {
        var manifestResult = PackManifestParser.parseFile(new File(packSourceDir, "pack.yml"));
        if (!manifestResult.isSuccess()) {
            PackValidationReport report = new PackValidationReport();
            report.addError(manifestResult.getError());
            return OperationResult.failure(report);
        }
        PackManifest manifest = manifestResult.getValue();

        PackValidationReport report = PackValidator.validateManifest(manifest, plugin.getDescription().getVersion(),
                name -> plugin.getServer().getPluginManager().getPlugin(name) != null);

        Map<String, PackManifest> alreadyInstalledManifests = installedByPackId.values().stream()
                .filter(r -> !r.packId().equalsIgnoreCase(manifest.id()))
                .collect(Collectors.toMap(PackRecord::packId, r -> minimalManifestFor(r)));
        PackDependencyResolver.Result depResult = PackValidator.validateDependencyGraph(
                Map.of(manifest.id(), manifest), alreadyInstalledManifests);
        report.merge(depResult.report());

        if (!report.isValid()) {
            return OperationResult.failure(report);
        }

        if (getInstalled(manifest.id()).isPresent()) {
            logger.info("Pack '" + manifest.id() + "' is already installed — uninstalling the old version before reinstalling.");
            OperationResult uninstallResult = uninstall(manifest.id());
            if (!uninstallResult.success()) {
                return uninstallResult;
            }
        }

        try {
            PackInstaller.InstallOutcome outcome = installer.install(manifest, packSourceDir);
            report.merge(outcome.mergeWarnings());
            dataStore.savePackRecord(outcome.record()).join();
            installedByPackId.put(manifest.id().toLowerCase(java.util.Locale.ROOT), outcome.record());
            reloadAffectedModules(manifest.providesContent());
            return OperationResult.success(report, "Installed pack '" + manifest.id() + "' v" + manifest.version());
        } catch (IOException e) {
            report.addError("Failed to install pack '" + manifest.id() + "': " + e.getMessage());
            return OperationResult.failure(report);
        }
    }

    /**
     * Resolves {@code source} (a direct {@code .zip} URL, or a {@code github:owner/repo@tag}
     * shorthand resolved via {@link GitHubReleaseResolver}), downloads and verifies it, extracts it
     * into a fresh staging directory, and installs it exactly like {@link #install(File)} — all off
     * the main thread except the final install step. {@code callback} always runs on the main thread.
     *
     * @param source         a {@code http(s)://} URL, or {@code github:owner/repo[@tag]}
     * @param expectedSha256 optional checksum to verify the download against (bare hex or {@code "sha256:<hex>"})
     */
    public void installFromSource(String source, String expectedSha256, Consumer<OperationResult> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File stagingDir = null;
            OperationResult failure;
            try {
                String resolvedUrl = resolveSourceUrl(source);
                File cacheDir = new File(plugin.getDataFolder(), ".pack-cache");
                File zipFile = new File(cacheDir, System.currentTimeMillis() + ".zip");
                PackDownloader.DownloadResult downloadResult = downloader.download(resolvedUrl, zipFile, expectedSha256);

                stagingDir = new File(new File(plugin.getDataFolder(), ".pack-staging"), Long.toString(System.nanoTime()));
                PackExtractor.extract(downloadResult.file(), stagingDir, maxExtractedBytes(), maxEntries());
                downloadResult.file().delete();

                File finalStagingDir = stagingDir;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    OperationResult installResult = install(finalStagingDir);
                    deleteRecursively(finalStagingDir);
                    callback.accept(installResult);
                });
                return;
            } catch (Exception e) {
                PackValidationReport report = new PackValidationReport();
                report.addError("Failed to download/extract pack from '" + source + "': " + e.getMessage());
                failure = OperationResult.failure(report);
            }
            if (stagingDir != null) deleteRecursively(stagingDir);
            OperationResult result = failure;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    private String resolveSourceUrl(String source) throws IOException, InterruptedException {
        if (source.startsWith("github:")) {
            return GitHubReleaseResolver.resolve(source, httpClient)
                    .orElseThrow(() -> new IOException("Could not resolve a .zip release asset for '" + source + "'"));
        }
        return source;
    }

    private long maxExtractedBytes() {
        return plugin.getConfig().getLong("pack.max-extracted-size-mb", 200) * 1024L * 1024L;
    }

    private int maxEntries() {
        return plugin.getConfig().getInt("pack.max-entries", 5000);
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    /** Uninstalls an installed pack by id: removes its content, reverts its shared-config diffs, and drops its ledger row. */
    public OperationResult uninstall(String packId) {
        OperationResult result = uninstallInternal(packId);
        DebugManager.log("pack", "uninstall(" + packId + ") -> success=" + result.success() + " summary=" + result.summary());
        return result;
    }

    private OperationResult uninstallInternal(String packId) {
        Optional<PackRecord> recordOpt = getInstalled(packId);
        if (recordOpt.isEmpty()) {
            PackValidationReport report = new PackValidationReport();
            report.addError("No pack named '" + packId + "' is installed");
            return OperationResult.failure(report);
        }
        PackRecord record = recordOpt.get();
        try {
            installer.uninstall(record);
            dataStore.deletePackRecord(record.packId()).join();
            installedByPackId.remove(record.packId().toLowerCase(java.util.Locale.ROOT));
            reloadAffectedModules(contentFoldersOwnedBy(record));
            return OperationResult.success(new PackValidationReport(), "Uninstalled pack '" + record.packId() + "'");
        } catch (IOException e) {
            PackValidationReport report = new PackValidationReport();
            report.addError("Failed to fully uninstall pack '" + packId + "': " + e.getMessage());
            return OperationResult.failure(report);
        }
    }

    /** Restores the most recent (or a specific) backup snapshot for a pack and reloads its modules. */
    public OperationResult rollback(String packId, Long timestampOrNull) {
        Optional<File> snapshot = timestampOrNull != null
                ? backupManager.snapshotAt(packId, timestampOrNull)
                : backupManager.mostRecentSnapshot(packId);
        if (snapshot.isEmpty()) {
            PackValidationReport report = new PackValidationReport();
            report.addError("No backup snapshot found for pack '" + packId + "'");
            return OperationResult.failure(report);
        }
        try {
            backupManager.restore(snapshot.get(), plugin.getDataFolder());
            Optional<PackRecord> record = getInstalled(packId);
            reloadAffectedModules(record.map(this::contentFoldersOwnedBy).orElse(Set.of()));
            return OperationResult.success(new PackValidationReport(), "Restored pack '" + packId + "' from backup");
        } catch (IOException e) {
            PackValidationReport report = new PackValidationReport();
            report.addError("Failed to restore backup for pack '" + packId + "': " + e.getMessage());
            return OperationResult.failure(report);
        }
    }

    private void reloadAffectedModules(java.util.Collection<String> providesContentEntries) {
        Set<String> moduleIds = new HashSet<>();
        for (String entry : providesContentEntries) {
            PackContentFolders.moduleIdFor(entry).ifPresent(moduleIds::add);
        }
        plugin.getModuleManager().reloadModules(moduleIds);
    }

    /** Best-effort recovery of a record's owned content folders (for reload targeting) — the manifest itself isn't persisted, only the file manifest. */
    private Set<String> contentFoldersOwnedBy(PackRecord record) {
        Set<String> folders = new HashSet<>();
        for (String relativePath : record.fileManifest()) {
            String[] segments = relativePath.replace('\\', '/').split("/");
            for (int i = 0; i < segments.length; i++) {
                if (segments[i].equalsIgnoreCase(record.packId())) {
                    folders.add(String.join("/", java.util.Arrays.copyOfRange(segments, 0, i)));
                    break;
                }
            }
        }
        return folders;
    }

    /** A minimal stand-in manifest for an already-installed pack (for dependency-graph checks, which only need id/version). */
    private static PackManifest minimalManifestFor(PackRecord record) {
        return new PackManifest(record.packId(), record.packId(), record.version(), "", "", "0.0.0", null,
                List.of(), List.of(), List.of(), List.of(), List.of(), record.checksum());
    }
}
