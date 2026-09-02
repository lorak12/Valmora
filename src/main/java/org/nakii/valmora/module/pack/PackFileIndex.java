package org.nakii.valmora.module.pack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a content file's path (relative to the data folder, e.g. {@code "items/frostspire/blade.yml"},
 * the same string {@code YamlLoader} already computes as its {@code filePath}) to the id of the
 * content pack that owns it. Populated explicitly by the installer when a pack's files are copied
 * into place — deliberately <em>not</em> inferred from folder structure, since several content
 * types already use subfolders for pure organisation (e.g. {@code recipes/anvil/},
 * {@code recipes/crafting/} — CLAUDE.md §9.3) with no pack-ownership meaning at all.
 *
 * <p>Ownership is registered per folder <em>prefix</em> (one entry per pack, e.g.
 * {@code "items/frostspire/" -> "frostspire"}), since a pack typically owns every file under its
 * own subfolder rather than being registered file-by-file.
 */
public final class PackFileIndex {

    // prefix (lowercased, trailing '/') -> pack id (original case)
    private final Map<String, String> ownerByPrefix = new ConcurrentHashMap<>();

    /** Registers every file under {@code folderPrefix} (e.g. {@code "items/frostspire"}) as owned by {@code packId}. */
    public void registerFolder(String folderPrefix, String packId) {
        String normalized = normalize(folderPrefix);
        ownerByPrefix.put(normalized, packId);
    }

    /** Removes a previously-registered folder-ownership entry (used on uninstall). */
    public void unregisterFolder(String folderPrefix) {
        ownerByPrefix.remove(normalize(folderPrefix));
    }

    /** Removes every folder-ownership entry belonging to {@code packId} — used on uninstall. */
    public void unregisterAllForPack(String packId) {
        ownerByPrefix.values().removeIf(owner -> owner.equalsIgnoreCase(packId));
    }

    /** Returns the pack id that owns {@code filePath}, if any registered folder prefix matches it. */
    public Optional<String> ownerOf(String filePath) {
        if (filePath == null) return Optional.empty();
        String normalized = filePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : ownerByPrefix.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public void clear() {
        ownerByPrefix.clear();
    }

    /**
     * Rebuilds this index's entries for one pack from its persisted file manifest (each relative
     * path {@code PackInstaller} recorded under the data folder) — used to restore in-memory
     * ownership after a restart, since this index itself is never persisted and every entry the
     * installer writes follows the "{@code <content-folder(s)>/<pack_id>/...}" convention. For each
     * path, registers the folder prefix ending at the first segment that equals {@code packId}
     * (case-insensitive).
     */
    public void reindexFromFileManifest(String packId, List<String> fileManifest) {
        Set<String> folders = new HashSet<>();
        for (String relativePath : fileManifest) {
            String[] segments = relativePath.replace('\\', '/').split("/");
            for (int i = 0; i < segments.length; i++) {
                if (segments[i].equalsIgnoreCase(packId)) {
                    folders.add(String.join("/", Arrays.copyOfRange(segments, 0, i + 1)));
                    break;
                }
            }
        }
        for (String folder : folders) {
            registerFolder(folder, packId);
        }
    }

    private static String normalize(String folderPrefix) {
        String s = folderPrefix.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!s.endsWith("/")) s = s + "/";
        return s;
    }
}
