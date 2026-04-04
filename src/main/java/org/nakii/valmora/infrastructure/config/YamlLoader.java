package org.nakii.valmora.infrastructure.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * A utility class for loading and parsing YML configuration files from a directory.
 * @param <T> the type of object being loaded
 */
public class YamlLoader<T> {

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
     * Loads and parses all YML files in the configured directory.
     * @param parser a functional interface for parsing a ConfigurationSection into an object of type T
     * @param registerAction a functional interface for registering a successfully parsed object
     */
    public void load(SectionParser<T> parser, Consumer<T> registerAction) {
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
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                        for (String key : config.getKeys(false)) {
                            ConfigurationSection section = config.getConfigurationSection(key);
                            if (section != null) {
                                LoadResult<T, String> result = parser.parse(key, section, relativePath);
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
            }
        }

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
