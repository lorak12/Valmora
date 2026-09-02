package org.nakii.valmora.infrastructure.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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

    public YamlLoader(Valmora plugin, String folderName, String typeName) {
        this.plugin = plugin;
        this.folderName = folderName;
        this.typeName = typeName;
        this.logger = plugin.getLogger();
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

        List<File> files = new ArrayList<>();
        collectYamlFilesRecursive(folder, files);
        List<String> errors = new ArrayList<>();
        int loadedCount = 0;

        for (File file : files) {
            String relativePath = folderName + "/" + folder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                for (String key : config.getKeys(false)) {
                    ConfigurationSection section = config.getConfigurationSection(key);
                    if (section != null) {
                        LoadResult<T, String> result = parser.parse(qualify(key, relativePath), section, relativePath);
                        if (result.isSuccess()) {
                            registerAction.accept(result.getValue());
                            loadedCount++;
                        } else {
                            errors.add(result.getError());
                        }
                    }
                }
            } catch (Exception e) {
                errors.add("[" + relativePath + "] Failed to parse YAML: " + e.getMessage());
            }
        }

        reportErrors(errors, loadedCount);
    }

    /** Recursively collects every {@code .yml} file under {@code dir} into {@code out}, depth-first. */
    private void collectYamlFilesRecursive(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectYamlFilesRecursive(child, out);
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

                        LoadResult<T, String> result = parser.parse(qualify(id, relativePath), (ConfigurationSection) config, relativePath);
                        if (result.isSuccess()) {
                            registerAction.accept(result.getValue());
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

    private void reportErrors(List<String> errors, int loadedCount) {
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
