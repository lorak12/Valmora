package org.nakii.valmora.infrastructure.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Brings an admin's {@code config.yml} up to date with the version shipped in the jar.
 *
 * <p>Bukkit's {@code saveDefaultConfig()} only copies the file when it's missing, so keys added by a
 * plugin update never showed up in existing installs. They were silently served from the jar
 * default at runtime, but admins couldn't see or edit them. This updater:
 * <ol>
 *   <li>runs versioned key renames/moves ({@link #MIGRATIONS}) for the admin's
 *       {@code config-version} up to {@link #LATEST_VERSION};</li>
 *   <li>adds every leaf key present in the jar default but missing from the admin's file, with its
 *       comments. The admin's existing values are never changed, and keys under
 *       {@link #USER_OWNED_SECTIONS} are left alone so entries an admin deleted there aren't
 *       resurrected;</li>
 *   <li>stamps {@code config-version}, writing a timestamped backup first. The file is only
 *       rewritten when something actually changed.</li>
 * </ol>
 *
 * <p>To rename or move a key in a release: bump {@link #LATEST_VERSION} and add an entry to
 * {@link #MIGRATIONS} mapping the new version to its {@code oldPath → newPath} moves.
 */
public final class ConfigUpdater {

    /** The {@code config-version} this build's {@code config.yml} is at. */
    public static final int LATEST_VERSION = 1;

    /**
     * version → (old path → new path) moves applied when upgrading to that version. Empty so far;
     * version 1 only introduced the {@code config-version} key itself.
     */
    static final Map<Integer, Map<String, String>> MIGRATIONS = Map.of();

    /**
     * Sections whose child keys are admin-owned collections rather than settings. Missing children
     * there were deliberately removed, not forgotten, so they're never re-added.
     * ({@code database.mysql} is commented out by default, so it doesn't exist in the jar default.)
     */
    static final List<String> USER_OWNED_SECTIONS = List.of();

    private ConfigUpdater() {}

    /** What an update did. */
    public record Result(int fromVersion, List<String> addedKeys, List<String> movedKeys, boolean saved) {}

    /**
     * Updates {@code file} in place against the jar default read from {@code jarDefault}.
     *
     * @param file       the admin's config file (must exist)
     * @param jarDefault supplies the bundled default; closed after reading
     */
    public static Result update(File file, Supplier<InputStream> jarDefault, Logger logger) throws IOException {
        YamlConfiguration defaults;
        try (InputStream in = jarDefault.get()) {
            if (in == null) throw new IOException("Bundled default for " + file.getName() + " not found");
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);

        int fromVersion = current.getInt("config-version", 0);
        if (fromVersion > LATEST_VERSION) {
            logger.warning(file.getName() + " has config-version " + fromVersion + ", newer than this plugin's "
                    + LATEST_VERSION + ". Leaving it untouched.");
            return new Result(fromVersion, List.of(), List.of(), false);
        }

        List<String> moved = new ArrayList<>();
        for (int v = fromVersion + 1; v <= LATEST_VERSION; v++) {
            for (Map.Entry<String, String> move : MIGRATIONS.getOrDefault(v, Map.of()).entrySet()) {
                String from = move.getKey(), to = move.getValue();
                if (current.contains(from) && !current.contains(to)) {
                    current.set(to, current.get(from));
                    current.setComments(to, current.getComments(from));
                    current.set(from, null);
                    moved.add(from + " -> " + to);
                }
            }
        }

        List<String> added = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (key.equals("config-version") || isUserOwned(key)) continue;
            if (current.contains(key)) continue;

            // Remember which ancestors are new so their comments come along too.
            List<String> newAncestors = new ArrayList<>();
            for (String parent = parentOf(key); parent != null; parent = parentOf(parent)) {
                if (!current.contains(parent)) newAncestors.add(parent);
            }
            current.set(key, defaults.get(key));
            current.setComments(key, defaults.getComments(key));
            current.setInlineComments(key, defaults.getInlineComments(key));
            for (String ancestor : newAncestors) {
                current.setComments(ancestor, defaults.getComments(ancestor));
            }
            added.add(key);
        }

        boolean changed = !added.isEmpty() || !moved.isEmpty() || fromVersion != LATEST_VERSION;
        if (!changed) return new Result(fromVersion, added, moved, false);

        File backupDir = new File(file.getParentFile(), "backups");
        backupDir.mkdirs();
        Files.copy(file.toPath(), new File(backupDir, file.getName() + "." + System.currentTimeMillis() + ".bak").toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        current.set("config-version", LATEST_VERSION);
        current.setComments("config-version", List.of(
                "Managed by Valmora — used to upgrade this file between plugin versions. Don't edit."));
        current.save(file);

        if (!added.isEmpty()) {
            logger.info("Added " + added.size() + " new setting(s) to " + file.getName() + " from the plugin defaults: " + added);
        }
        if (!moved.isEmpty()) {
            logger.info("Moved setting(s) in " + file.getName() + ": " + moved);
        }
        return new Result(fromVersion, added, moved, true);
    }

    private static boolean isUserOwned(String key) {
        for (String section : USER_OWNED_SECTIONS) {
            if (key.startsWith(section + ".")) return true;
        }
        return false;
    }

    private static String parentOf(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? null : key.substring(0, dot);
    }
}
