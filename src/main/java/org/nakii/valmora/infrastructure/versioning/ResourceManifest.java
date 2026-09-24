package org.nakii.valmora.infrastructure.versioning;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Keeps the default content files shipped in the jar ({@code items/}, {@code mobs/},
 * {@code machines/}, ...) in sync with a server's data folder across plugin updates, without ever
 * overwriting an admin's edits or resurrecting a file they deleted.
 *
 * <p>Replaces the old one-shot {@code .resources_seeded} marker. That marker meant a plugin update
 * never delivered a new or changed default file to an existing install, and a folder added later
 * ({@code machines/}) never appeared at all. The manifest ({@code .resources_manifest}, one TSV line
 * per file: {@code baseHash, offeredHash, path}) remembers, for every shipped file, the hash of the
 * shipped version the admin's copy derives from ({@code base}) and the newest shipped version
 * already offered to them ({@code offered}).
 *
 * <p>Per shipped file, on every startup:
 * <ul>
 *   <li><b>Not on disk, not in manifest</b>: new default, so write it. In legacy mode (first run after
 *       upgrading from the marker scheme) a missing file may have been deliberately deleted, so it
 *       is only written if its whole top-level folder is absent (a folder that never existed on
 *       this install, e.g. {@code machines/}).</li>
 *   <li><b>Not on disk, in manifest</b>: the admin deleted it, so leave it deleted.</li>
 *   <li><b>On disk, unchanged by the admin</b> ({@code disk == base}) and the jar version changed:
 *       update it in place (when {@code autoUpdate} is on), otherwise treat it like an edited file.</li>
 *   <li><b>On disk, edited by the admin</b>, jar version changed and not yet offered: write the new
 *       default next to it as {@code <file>.new} (the loaders only read {@code .yml}) and warn once.</li>
 * </ul>
 * Hashes ignore {@code \r}, so a Windows editor converting line endings doesn't count as an edit.
 */
public final class ResourceManifest {

    public static final String MANIFEST_FILE = ".resources_manifest";
    private static final String UNKNOWN = "-";

    /** What a sync did, for logging and tests. */
    public record Result(List<String> seeded, List<String> updated, List<String> offered, List<String> keptDeleted) {}

    private record Entry(String base, String offered) {}

    private ResourceManifest() {}

    /**
     * @param dataFolder the plugin data folder
     * @param shipped    every seedable resource in the jar: relative path → bytes
     * @param legacyMode true when upgrading from the old {@code .resources_seeded} marker scheme
     * @param autoUpdate whether unedited files are updated in place when the jar version changes
     */
    public static Result sync(File dataFolder, Map<String, byte[]> shipped, boolean legacyMode,
                              boolean autoUpdate, Logger logger) throws IOException {
        File manifestFile = new File(dataFolder, MANIFEST_FILE);
        Map<String, Entry> manifest = read(manifestFile);
        boolean firstManifestRun = !manifestFile.exists();
        boolean legacy = legacyMode && firstManifestRun;

        List<String> seeded = new ArrayList<>(), updated = new ArrayList<>(),
                offered = new ArrayList<>(), keptDeleted = new ArrayList<>();
        Set<String> absentTopFolders = legacy ? absentTopLevelFolders(dataFolder, shipped.keySet()) : Set.of();

        for (Map.Entry<String, byte[]> resource : shipped.entrySet()) {
            String path = resource.getKey();
            byte[] jarBytes = resource.getValue();
            String jarHash = hash(jarBytes);
            File target = new File(dataFolder, path);
            Entry entry = manifest.get(path);

            if (!target.exists()) {
                boolean seed;
                if (entry != null) {
                    seed = false; // admin deleted it
                } else if (legacy) {
                    seed = absentTopFolders.contains(topFolder(path));
                } else {
                    seed = true;
                }
                if (seed) {
                    write(target, jarBytes);
                    manifest.put(path, new Entry(jarHash, jarHash));
                    seeded.add(path);
                } else {
                    // Record it so later runs keep treating it as deliberately deleted.
                    manifest.put(path, new Entry(entry != null ? entry.base() : UNKNOWN, jarHash));
                    keptDeleted.add(path);
                }
                continue;
            }

            String diskHash = hash(Files.readAllBytes(target.toPath()));
            if (diskHash.equals(jarHash)) {
                manifest.put(path, new Entry(jarHash, jarHash));
                continue;
            }
            if (entry == null) {
                // Legacy install (or a file the admin created at a shipped path): its origin is
                // unknown, so treat it as edited, but don't offer this same jar version as ".new"
                // on this first run — only a later change to the shipped file is news.
                manifest.put(path, new Entry(UNKNOWN, jarHash));
                continue;
            }
            if (jarHash.equals(entry.offered())) continue; // nothing new since last time

            if (autoUpdate && diskHash.equals(entry.base())) {
                write(target, jarBytes);
                manifest.put(path, new Entry(jarHash, jarHash));
                updated.add(path);
            } else {
                write(new File(dataFolder, path + ".new"), jarBytes);
                manifest.put(path, new Entry(entry.base(), jarHash));
                offered.add(path);
            }
        }

        writeManifest(manifestFile, manifest);

        if (!seeded.isEmpty()) logger.info("Installed " + seeded.size() + " new default content file(s): " + seeded);
        if (!updated.isEmpty()) logger.info("Updated " + updated.size() + " unedited default content file(s) to this version: " + updated);
        if (!offered.isEmpty()) {
            logger.warning(offered.size() + " default content file(s) you edited changed in this plugin version. "
                    + "The new versions were saved next to yours as <file>.new so you can merge them: " + offered);
        }
        return new Result(seeded, updated, offered, keptDeleted);
    }

    static String hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte b : bytes) {
                if (b != '\r') digest.update(b);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> absentTopLevelFolders(File dataFolder, Set<String> paths) {
        Set<String> absent = new java.util.HashSet<>();
        for (String path : paths) {
            String top = topFolder(path);
            if (top != null && !new File(dataFolder, top).exists()) absent.add(top);
        }
        return absent;
    }

    private static String topFolder(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? null : path.substring(0, slash);
    }

    private static void write(File target, byte[] bytes) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(target.toPath(), bytes);
    }

    private static Map<String, Entry> read(File manifestFile) throws IOException {
        Map<String, Entry> result = new TreeMap<>();
        if (!manifestFile.exists()) return result;
        for (String line : Files.readAllLines(manifestFile.toPath(), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) result.put(parts[2], new Entry(parts[0], parts[1]));
        }
        return result;
    }

    private static void writeManifest(File manifestFile, Map<String, Entry> manifest) throws IOException {
        StringBuilder out = new StringBuilder(
                "# Valmora default-content manifest: <base hash>\\t<offered hash>\\t<path>. Don't edit.\n");
        for (Map.Entry<String, Entry> e : new TreeMap<>(manifest).entrySet()) {
            out.append(e.getValue().base()).append('\t').append(e.getValue().offered()).append('\t').append(e.getKey()).append('\n');
        }
        write(manifestFile, out.toString().getBytes(StandardCharsets.UTF_8));
    }
}
