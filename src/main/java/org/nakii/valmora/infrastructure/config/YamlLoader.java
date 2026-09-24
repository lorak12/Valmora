package org.nakii.valmora.infrastructure.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * A utility class for loading and parsing YML configuration files from a directory.
 * @param <T> the type of object being loaded
 */
public class YamlLoader<T> {

    /**
     * Optional hook that rewrites {@code (id, filePath)} into the id actually handed to a loader's
     * parser/registerAction — the content pack manager's namespacing choke point (see
     * {@code org.nakii.valmora.module.pack.PackNamespacer}). {@code null} (the default, and the
     * state whenever the pack module isn't enabled) means "pass ids through unchanged". Static and
     * global by design: namespacing applies uniformly to every content type without any of their
     * loader classes needing to know packs exist.
     */
    private static volatile BiFunction<String, String, String> idQualifier;

    /** Content errors reported since the last {@link #beginReport()} — see ModuleManager's reload result. */
    private static final List<String> REPORT = java.util.Collections.synchronizedList(new ArrayList<>());

    /** folder → the parser last used to load it, for {@link #validateAll}. */
    private static final Map<String, SectionParser<?>> PARSERS = new java.util.concurrent.ConcurrentHashMap<>();

    private static volatile boolean validating;

    /**
     * True while {@link #validateAll} is re-running parsers as a dry run. The few parsers with
     * side effects (e.g. registering a Bukkit recipe) must skip them when this is set.
     */
    public static boolean isValidating() {
        return validating;
    }

    /** Starts collecting content errors for a reload report. */
    public static void beginReport() {
        REPORT.clear();
    }

    /** Returns (and clears) the content errors collected since {@link #beginReport()}. */
    public static List<String> drainReport() {
        synchronized (REPORT) {
            List<String> copy = new ArrayList<>(REPORT);
            REPORT.clear();
            return copy;
        }
    }

    /**
     * Dry run: checks every content file loaded through a YamlLoader for YAML syntax errors and
     * re-runs each entry through its folder's parser, without registering anything. Cross-references
     * are checked against the content currently live, so an entry pointing at content that is new
     * in the same edit may be reported until the real reload.
     */
    public static List<String> validateAll(Valmora plugin) {
        validating = true;
        try {
            return validateAllInternal(plugin);
        } finally {
            validating = false;
        }
    }

    private static List<String> validateAllInternal(Valmora plugin) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, SectionParser<?>> e : PARSERS.entrySet()) {
            String folderName = e.getKey();
            File folder = new File(plugin.getDataFolder(), folderName);
            List<File> files = new ArrayList<>();
            collectYamlFiles(folder, files);
            for (File file : files) {
                String relativePath = folderName + "/" + folder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
                YamlConfiguration config = new YamlConfiguration();
                try {
                    config.load(file);
                } catch (Exception ex) {
                    problems.add("[" + relativePath + "] Invalid YAML — " + firstLine(ex.getMessage()));
                    continue;
                }
                for (String key : config.getKeys(false)) {
                    ConfigurationSection section = config.getConfigurationSection(key);
                    if (section == null) continue;
                    try {
                        LoadResult<?, String> result = e.getValue().parse(qualify(key, relativePath), section, relativePath);
                        if (!result.isSuccess()) problems.add(result.getError());
                    } catch (Exception ex) {
                        problems.add("[" + relativePath + "] '" + key + "': " + ex.getMessage());
                    }
                }
            }
        }
        return problems;
    }

    private static void collectYamlFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectYamlFiles(child, out);
            else if (child.getName().endsWith(".yml")) out.add(child);
        }
    }

    /** Installs (or clears, with {@code null}) the global id-qualifying hook described above. */
    public static void setIdQualifier(BiFunction<String, String, String> qualifier) {
        idQualifier = qualifier;
    }

    private static String qualify(String id, String filePath) {
        BiFunction<String, String, String> q = idQualifier;
        return q != null ? q.apply(id, filePath) : id;
    }

    private final Valmora plugin;
    private final String folderName;
    private final String typeName;
    private final Logger logger;
    private Predicate<File> directorySkip = dir -> false;

    public YamlLoader(Valmora plugin, String folderName, String typeName) {
        this.plugin = plugin;
        this.folderName = folderName;
        this.typeName = typeName;
        this.logger = plugin.getLogger();
    }

    /**
     * Makes {@link #load} skip every subdirectory (and everything under it) matching {@code skip} —
     * e.g. the flat quest loader skipping quest-package folders, which a different loader owns.
     */
    public YamlLoader<T> skipDirectories(Predicate<File> skip) {
        this.directorySkip = skip;
        return this;
    }

    /**
     * Loads and parses all YML files in the configured directory, including any subfolders (e.g.
     * {@code recipes/alchemy/}, {@code recipes/anvil/} per CLAUDE.md §9.3) — subfolders are purely
     * for author organisation and carry no meaning of their own.
     * @param parser a functional interface for parsing a ConfigurationSection into an object of type T
     * @param registerAction a functional interface for registering a successfully parsed object
     */
    public void load(SectionParser<T> parser, Consumer<T> registerAction) {
        File folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        org.nakii.valmora.infrastructure.versioning.IdAliases.clear(folderName);
        PARSERS.put(folderName, parser);
        LastGoodStore lastGood = LastGoodStore.of(plugin.getDataFolder(), folderName, logger);
        java.util.Set<String> retained = new java.util.HashSet<>();
        List<File> files = new ArrayList<>();
        collectYamlFilesRecursive(folder, files);
        List<String> errors = new ArrayList<>();
        int loadedCount = 0;

        for (File file : files) {
            String relativePath = folderName + "/" + folder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
            YamlConfiguration config = new YamlConfiguration();
            try {
                // load() (unlike loadConfiguration()) throws on a syntax error. loadConfiguration
                // logged and returned an EMPTY config, so one typo silently removed every entry
                // of the file on the next reload.
                config.load(file);
            } catch (Exception e) {
                Map<String, ConfigurationSection> previous = lastGood.entriesForFile(relativePath);
                errors.add("[" + relativePath + "] Invalid YAML — " + firstLine(e.getMessage())
                        + (previous.isEmpty() ? "" : " (kept the previous version of its " + previous.size() + " entr"
                        + (previous.size() == 1 ? "y" : "ies") + ")"));
                for (Map.Entry<String, ConfigurationSection> entry : previous.entrySet()) {
                    String id = qualify(entry.getKey(), relativePath);
                    LoadResult<T, String> result = parser.parse(id, entry.getValue(), relativePath);
                    if (result.isSuccess()) {
                        registerAction.accept(result.getValue());
                        registerAliases(entry.getKey(), id, entry.getValue());
                        retained.add(LastGoodStore.pair(relativePath, entry.getKey()));
                        loadedCount++;
                    }
                }
                continue;
            }

            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;
                String id = qualify(key, relativePath);
                LoadResult<T, String> result;
                try {
                    result = parser.parse(id, section, relativePath);
                } catch (Exception e) {
                    result = LoadResult.failure("[" + relativePath + "] '" + key + "': " + e.getMessage());
                }
                if (result.isSuccess()) {
                    registerAction.accept(result.getValue());
                    registerAliases(key, id, section);
                    lastGood.put(relativePath, key, section);
                    retained.add(LastGoodStore.pair(relativePath, key));
                    loadedCount++;
                    continue;
                }
                // The entry exists but no longer loads: keep serving the last version that did.
                ConfigurationSection previous = lastGood.get(relativePath, key);
                LoadResult<T, String> fallback = previous != null ? parser.parse(id, previous, relativePath) : null;
                if (fallback != null && fallback.isSuccess()) {
                    registerAction.accept(fallback.getValue());
                    registerAliases(key, id, previous);
                    retained.add(LastGoodStore.pair(relativePath, key));
                    loadedCount++;
                    errors.add(result.getError() + " (kept the previous version of '" + key + "')");
                } else {
                    errors.add(result.getError());
                }
            }
        }

        lastGood.retainOnly(retained);
        lastGood.save();
        reportErrors(errors, loadedCount);
    }

    private static String firstLine(String message) {
        if (message == null) return "unknown error";
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }

    /** Recursively collects every {@code .yml} file under {@code dir} into {@code out}, depth-first. */
    private void collectYamlFilesRecursive(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                if (!directorySkip.test(child)) collectYamlFilesRecursive(child, out);
            } else if (child.getName().endsWith(".yml")) {
                out.add(child);
            }
        }
    }

    /**
     * Loads each file in the folder as a single section, using the filename as the ID.
     */
    public void loadFilesAsSections(SectionParser<T> parser, Consumer<T> registerAction) {
        File folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        org.nakii.valmora.infrastructure.versioning.IdAliases.clear(folderName);
        File[] files = folder.listFiles();
        List<String> errors = new ArrayList<>();
        int loadedCount = 0;

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".yml")) {
                    String relativePath = folderName + "/" + file.getName();
                    try {
                        String id = file.getName().replace(".yml", "");
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

                        String qualifiedId = qualify(id, relativePath);
                        LoadResult<T, String> result = parser.parse(qualifiedId, (ConfigurationSection) config, relativePath);
                        if (result.isSuccess()) {
                            registerAction.accept(result.getValue());
                            registerAliases(id, qualifiedId, config);
                            loadedCount++;
                        } else {
                            errors.add(result.getError());
                        }
                    } catch (Exception e) {
                        errors.add("[" + relativePath + "] Failed to parse YAML: " + e.getMessage());
                    }
                }
            }
        }

        reportErrors(errors, loadedCount);
    }

    /**
     * Records the id aliases of one loaded definition (see {@code IdAliases}): every entry of its
     * {@code previous-ids:} list, and — when pack namespacing rewrote the id — the bare id it was
     * declared under, so data saved before namespacing still resolves.
     */
    private void registerAliases(String declaredId, String id, ConfigurationSection section) {
        org.nakii.valmora.infrastructure.versioning.IdAliases.registerAll(folderName, section.getStringList("previous-ids"), id);
        if (!declaredId.equalsIgnoreCase(id)) {
            org.nakii.valmora.infrastructure.versioning.IdAliases.register(folderName, declaredId, id);
        }
    }

    private void reportErrors(List<String> errors, int loadedCount) {
        REPORT.addAll(errors);
        if (!errors.isEmpty()) {
            logger.warning("Failed to load some " + typeName + ". Please check your configuration files.");
            logger.warning("------------------------------");
            for (String error : errors) {
                logger.warning("- " + error);
            }
            logger.warning("------------------------------");
        }
        logger.info("Successfully loaded " + loadedCount + " " + typeName + ".");
    }

    /**
     * A functional interface for parsing a ConfigurationSection into an object.
     * @param <T> the type of object to parse
     */
    @FunctionalInterface
    public interface SectionParser<T> {
        LoadResult<T, String> parse(String id, ConfigurationSection section, String filePath);
    }
}
