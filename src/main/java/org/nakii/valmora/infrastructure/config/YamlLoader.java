package org.nakii.valmora.infrastructure.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.DiagnosticSink;
import org.nakii.valmora.infrastructure.config.diag.LoadReport;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.ModuleLoadLog;
import org.nakii.valmora.infrastructure.config.diag.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * A utility class for loading and parsing YML configuration files from a directory.
 *
 * <p>Every entry is parsed inside a {@link LoadScope}, so anything the parser (or the script
 * compilers it calls) reports is attributed to the right file and entry. Problems are collected as
 * {@link ConfigDiagnostic}s into the global {@link LoadReport} and summarised through
 * {@link ModuleLoadLog}.
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

    /** Called with a folder name whenever it starts (re)loading — the reference index clears that folder's refs. */
    private static volatile Consumer<String> loadStartListener;

    /** folder → the parser last used to load it, for {@link #validateAll}. */
    private static final Map<String, SectionParser<?>> PARSERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** folder → the display name its loader uses, for {@link #validateAll}. */
    private static final Map<String, String> TYPE_NAMES = new java.util.concurrent.ConcurrentHashMap<>();

    /** folder → the directory-skip rule and ignored file names its loader uses, so the dry run walks the same files. */
    private static final Map<String, Predicate<File>> SKIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Set<String>> IGNORED = new java.util.concurrent.ConcurrentHashMap<>();
    /** folders loaded one-entry-per-file ({@link #loadFilesAsSections}). */
    private static final Set<String> FILE_ENTRIES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** folder → the content kind (see {@code ContentIndex}) its entries define, when declared via {@link #kind}. */
    private static final Map<String, String> KINDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static volatile boolean validating;

    /**
     * True while {@link #validateAll} is re-running parsers as a dry run. The few parsers with
     * side effects (e.g. registering a Bukkit recipe) must skip them when this is set.
     */
    public static boolean isValidating() {
        return validating;
    }

    /** Starts collecting content diagnostics for a report. */
    public static void beginReport() {
        LoadReport.global().begin();
    }

    /**
     * Returns (and clears) the content errors collected since {@link #beginReport()}, formatted as
     * one line each. Warnings are left out, matching the old string report — use
     * {@link #drainDiagnostics()} for everything.
     */
    public static List<String> drainReport() {
        return LoadReport.errorLines(LoadReport.global().drain());
    }

    /** Returns (and clears) every diagnostic collected since {@link #beginReport()}. */
    public static List<ConfigDiagnostic> drainDiagnostics() {
        return LoadReport.global().drain();
    }

    /** Folder names every loader has loaded at least once, with the content kind each defines (may be null). */
    public static Map<String, String> loadedFolders() {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String folder : PARSERS.keySet()) out.put(folder, KINDS.get(folder));
        return out;
    }

    public static void setLoadStartListener(Consumer<String> listener) {
        loadStartListener = listener;
    }

    /**
     * Dry run: checks every content file loaded through a YamlLoader for YAML syntax errors and
     * re-runs each entry through its folder's parser, without registering anything. Cross-references
     * are checked against the content currently live, so an entry pointing at content that is new
     * in the same edit may be reported until the real reload.
     * @return one formatted line per ERROR (warnings are only in {@link #validateAllDiagnostics})
     */
    public static List<String> validateAll(Valmora plugin) {
        return LoadReport.errorLines(validateAllDiagnostics(plugin, null));
    }

    /**
     * {@link #validateAll} returning every diagnostic, warnings included.
     * @param definedIds if non-null, receives {@code kind → ids} of every entry found on disk in a
     *                   folder with a declared {@link #kind}, so reference checks can accept content
     *                   that is new in this edit
     */
    public static List<ConfigDiagnostic> validateAllDiagnostics(Valmora plugin, Map<String, Set<String>> definedIds) {
        validating = true;
        try {
            return validateAllInternal(plugin, definedIds);
        } finally {
            validating = false;
        }
    }

    private static List<ConfigDiagnostic> validateAllInternal(Valmora plugin, Map<String, Set<String>> definedIds) {
        List<ConfigDiagnostic> problems = new ArrayList<>();
        DiagnosticSink sink = problems::add;
        for (Map.Entry<String, SectionParser<?>> e : PARSERS.entrySet()) {
            String folderName = e.getKey();
            String typeName = TYPE_NAMES.getOrDefault(folderName, folderName);
            String kind = KINDS.get(folderName);
            File folder = new File(plugin.getDataFolder(), folderName);
            List<File> files = new ArrayList<>();
            boolean perFile = FILE_ENTRIES.contains(folderName);
            collectYamlFiles(folder, files, SKIPS.getOrDefault(folderName, d -> false),
                    IGNORED.getOrDefault(folderName, Set.of()), !perFile);
            for (File file : files) {
                String relativePath = relativePath(folder, folderName, file);
                YamlConfiguration config = new YamlConfiguration();
                try {
                    config.load(file);
                } catch (Exception ex) {
                    problems.add(new ConfigDiagnostic(Severity.ERROR, typeName, relativePath, null, null,
                            "Invalid YAML — " + firstLine(ex.getMessage()), null));
                    continue;
                }
                if (perFile) {
                    String key = file.getName().substring(0, file.getName().length() - ".yml".length());
                    validateEntry(e.getValue(), typeName, kind, definedIds, relativePath, key, config, sink, problems);
                    continue;
                }
                for (String key : config.getKeys(false)) {
                    ConfigurationSection section = config.getConfigurationSection(key);
                    if (section == null) continue;
                    validateEntry(e.getValue(), typeName, kind, definedIds, relativePath, key, section, sink, problems);
                }
            }
        }
        return problems;
    }

    private static void validateEntry(SectionParser<?> parser, String typeName, String kind, Map<String, Set<String>> definedIds,
                                      String relativePath, String key, ConfigurationSection section,
                                      DiagnosticSink sink, List<ConfigDiagnostic> problems) {
        String id = qualify(key, relativePath);
        if (kind != null && definedIds != null) {
            definedIds.computeIfAbsent(kind, k -> new HashSet<>()).add(id.toLowerCase(Locale.ROOT));
        }
        try (LoadScope ignored = LoadScope.enter(typeName, relativePath, key, sink)) {
            LoadResult<?, String> result = parser.parse(id, section, relativePath);
            if (!result.isSuccess() && !result.isSkipped()) {
                problems.add(ConfigDiagnostic.legacy(Severity.ERROR, typeName, relativePath, key, result.getError()));
            }
        } catch (Exception ex) {
            problems.add(new ConfigDiagnostic(Severity.ERROR, typeName, relativePath, key, null, describe(ex), null));
        }
    }

    private static void collectYamlFiles(File dir, List<File> out, Predicate<File> skip, Set<String> ignored, boolean recurse) {
        File[] children = dir.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory()) {
                if (recurse && !skip.test(child)) collectYamlFiles(child, out, skip, ignored, true);
            } else if (child.getName().endsWith(".yml") && !ignored.contains(child.getName().toLowerCase(Locale.ROOT))) {
                out.add(child);
            }
        }
    }

    /** Installs (or clears, with {@code null}) the global id-qualifying hook described above. */
    public static void setIdQualifier(BiFunction<String, String, String> qualifier) {
        idQualifier = qualifier;
    }

    /** The id a declared key in {@code filePath} is registered under (pack namespacing applied). */
    public static String qualify(String id, String filePath) {
        BiFunction<String, String, String> q = idQualifier;
        return q != null ? q.apply(id, filePath) : id;
    }

    private final Valmora plugin;
    private final String folderName;
    private final String typeName;
    private final Logger logger;
    private Predicate<File> directorySkip = dir -> false;
    private Set<String> ignoredFiles = Set.of();

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

    /** Files (by name, e.g. {@code xp_curves.yml}) in this folder that belong to another loader. */
    public YamlLoader<T> ignoreFiles(String... names) {
        Set<String> set = new HashSet<>();
        for (String n : names) set.add(n.toLowerCase(Locale.ROOT));
        this.ignoredFiles = set;
        return this;
    }

    /**
     * Declares which content kind this folder's entries define (e.g. {@code "item"}), so
     * {@code /valmora validate} can treat ids that exist on disk but aren't loaded yet as valid
     * reference targets.
     */
    public YamlLoader<T> kind(String kind) {
        if (kind != null) KINDS.put(folderName, kind);
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
        long start = System.nanoTime();
        File folder = prepare(parser);
        LastGoodStore lastGood = LastGoodStore.of(plugin.getDataFolder(), folderName, logger);
        Set<String> retained = new HashSet<>();
        List<File> files = new ArrayList<>();
        collectYamlFilesRecursive(folder, files);
        List<ConfigDiagnostic> diagnostics = new ArrayList<>();
        Counts counts = new Counts();

        for (File file : files) {
            String relativePath = relativePath(folder, folderName, file);
            YamlConfiguration config = new YamlConfiguration();
            try {
                // load() (unlike loadConfiguration()) throws on a syntax error. loadConfiguration
                // logged and returned an EMPTY config, so one typo silently removed every entry
                // of the file on the next reload.
                config.load(file);
            } catch (Exception e) {
                Map<String, ConfigurationSection> previous = lastGood.entriesForFile(relativePath);
                int kept = 0;
                for (Map.Entry<String, ConfigurationSection> entry : previous.entrySet()) {
                    if (registerFallback(parser, registerAction, relativePath, entry.getKey(), entry.getValue())) {
                        retained.add(LastGoodStore.pair(relativePath, entry.getKey()));
                        kept++;
                    }
                }
                counts.loaded += kept;
                counts.kept += kept;
                diagnostics.add(new ConfigDiagnostic(Severity.ERROR, typeName, relativePath, null, null,
                        "Invalid YAML — " + firstLine(e.getMessage()),
                        kept == 0 ? null : "kept the previous version of its " + kept + " entr" + (kept == 1 ? "y" : "ies")));
                continue;
            }

            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) {
                    diagnostics.add(new ConfigDiagnostic(Severity.WARN, typeName, relativePath, key, null,
                            "ignored — top-level keys must be sections (an entry id followed by its settings)", null));
                    continue;
                }
                String id = qualify(key, relativePath);
                LoadResult<T, String> result = parseInScope(parser, id, key, section, relativePath, diagnostics::add);
                if (result.isSkipped()) continue;
                if (result.isSuccess()) {
                    registerAction.accept(result.getValue());
                    registerAliases(key, id, section);
                    lastGood.put(relativePath, key, section);
                    retained.add(LastGoodStore.pair(relativePath, key));
                    counts.loaded++;
                    continue;
                }
                ConfigDiagnostic failure = ConfigDiagnostic.legacy(Severity.ERROR, typeName, relativePath, key, result.getError());
                // The entry exists but no longer loads: keep serving the last version that did.
                ConfigurationSection previous = lastGood.get(relativePath, key);
                if (previous != null && registerFallback(parser, registerAction, relativePath, key, previous)) {
                    retained.add(LastGoodStore.pair(relativePath, key));
                    counts.loaded++;
                    counts.kept++;
                    failure = failure.withHint("kept the previous version of '" + key + "'");
                }
                diagnostics.add(failure);
            }
        }

        lastGood.retainOnly(retained);
        lastGood.save();
        ModuleLoadLog.publish(logger, typeName, counts.loaded, files.size(), elapsedMillis(start), counts.kept, diagnostics);
    }

    /**
     * Loads each file in the folder as a single entry, using the filename as the ID. Same
     * guarantees as {@link #load}: YAML syntax errors are reported (not silently read as an empty
     * file) and an entry that stops loading keeps its last working version.
     */
    public void loadFilesAsSections(SectionParser<T> parser, Consumer<T> registerAction) {
        long start = System.nanoTime();
        File folder = prepare(parser);
        FILE_ENTRIES.add(folderName);
        LastGoodStore lastGood = LastGoodStore.of(plugin.getDataFolder(), folderName, logger);
        Set<String> retained = new HashSet<>();
        List<ConfigDiagnostic> diagnostics = new ArrayList<>();
        Counts counts = new Counts();
        int fileCount = 0;

        File[] files = folder.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".yml")) continue;
                if (ignoredFiles.contains(file.getName().toLowerCase(Locale.ROOT))) continue;
                fileCount++;
                String relativePath = folderName + "/" + file.getName();
                String key = file.getName().substring(0, file.getName().length() - ".yml".length());
                String id = qualify(key, relativePath);

                YamlConfiguration config = new YamlConfiguration();
                LoadResult<T, String> result;
                try {
                    config.load(file);
                    result = parseInScope(parser, id, key, config, relativePath, diagnostics::add);
                } catch (Exception e) {
                    result = LoadResult.failure("Invalid YAML — " + firstLine(e.getMessage()));
                }
                if (result.isSkipped()) continue;
                if (result.isSuccess()) {
                    registerAction.accept(result.getValue());
                    registerAliases(key, id, config);
                    lastGood.put(relativePath, key, config);
                    retained.add(LastGoodStore.pair(relativePath, key));
                    counts.loaded++;
                    continue;
                }
                ConfigDiagnostic failure = ConfigDiagnostic.legacy(Severity.ERROR, typeName, relativePath, key, result.getError());
                ConfigurationSection previous = lastGood.get(relativePath, key);
                if (previous != null && registerFallback(parser, registerAction, relativePath, key, previous)) {
                    retained.add(LastGoodStore.pair(relativePath, key));
                    counts.loaded++;
                    counts.kept++;
                    failure = failure.withHint("kept the previous version of '" + key + "'");
                }
                diagnostics.add(failure);
            }
        }

        lastGood.retainOnly(retained);
        lastGood.save();
        ModuleLoadLog.publish(logger, typeName, counts.loaded, fileCount, elapsedMillis(start), counts.kept, diagnostics);
    }

    private static final class Counts {
        int loaded;
        int kept;
    }

    private File prepare(SectionParser<T> parser) {
        File folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        org.nakii.valmora.infrastructure.versioning.IdAliases.clear(folderName);
        PARSERS.put(folderName, parser);
        TYPE_NAMES.put(folderName, typeName);
        SKIPS.put(folderName, directorySkip);
        IGNORED.put(folderName, ignoredFiles);
        Consumer<String> listener = loadStartListener;
        if (listener != null) listener.accept(folderName);
        return folder;
    }

    /** Runs the parser inside a {@link LoadScope}, turning a thrown exception into a failure. */
    private LoadResult<T, String> parseInScope(SectionParser<T> parser, String id, String key,
                                               ConfigurationSection section, String relativePath, DiagnosticSink sink) {
        try (LoadScope ignored = LoadScope.enter(typeName, relativePath, key, sink)) {
            return parser.parse(id, section, relativePath);
        } catch (Exception e) {
            return LoadResult.failure(describe(e));
        }
    }

    /**
     * Re-parses and registers a remembered version of an entry. Its diagnostics are discarded — they
     * describe old content the author already replaced — but its references are still recorded, since
     * it is live again.
     */
    private boolean registerFallback(SectionParser<T> parser, Consumer<T> registerAction, String relativePath,
                                     String key, ConfigurationSection previous) {
        String id = qualify(key, relativePath);
        LoadResult<T, String> result = parseInScope(parser, id, key, previous, relativePath, d -> {});
        if (!result.isSuccess()) return false;
        registerAction.accept(result.getValue());
        registerAliases(key, id, previous);
        return true;
    }

    private static String relativePath(File folder, String folderName, File file) {
        return folderName + "/" + folder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "unexpected " + e.getClass().getSimpleName() + " while parsing"
                : message;
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
        java.util.Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory()) {
                if (!directorySkip.test(child)) collectYamlFilesRecursive(child, out);
            } else if (child.getName().endsWith(".yml") && !ignoredFiles.contains(child.getName().toLowerCase(Locale.ROOT))) {
                out.add(child);
            }
        }
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

    /**
     * A functional interface for parsing a ConfigurationSection into an object.
     * @param <T> the type of object to parse
     */
    @FunctionalInterface
    public interface SectionParser<T> {
        LoadResult<T, String> parse(String id, ConfigurationSection section, String filePath);
    }
}
