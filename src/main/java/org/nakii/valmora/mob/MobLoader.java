package org.nakii.valmora.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.item.LoadResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MobLoader {
    
    private final Valmora plugin;
    private final MobRegistry registry;

    public MobLoader(Valmora plugin, MobRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    // Loads all mobs from the mobs folder
    public void loadMobs() {
        registry.clear();
        File folder = new File(plugin.getDataFolder(), "mobs");
        if(!folder.exists()){
            folder.mkdirs();
        }
        File[] files = folder.listFiles();
        List<String> errors = new ArrayList<>();
        int loadedCount = 0;

        if(files != null){
            for(File file : files){
                if(file.isFile() && file.getName().endsWith(".yml")){
                    try {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                        for (String key : config.getKeys(false)) {
                            ConfigurationSection section = config.getConfigurationSection(key);
                            if (section != null) {
                                LoadResult<MobDefinition, String> result = MobDefinitionParser.parse(key, section, "mobs/" + file.getName(), plugin.getItemManager());
                                if (result.isSuccess()) {
                                    registry.registerMob(result.getValue());
                                    loadedCount++;
                                } else {
                                    errors.add(result.getError());
                                }
                            }
                        }
                    } catch(Exception e) {
                        errors.add("[mobs/" + file.getName() + "] Failed to parse YAML: " + e.getMessage());
                    }
                }
            }
        }   

        if (!errors.isEmpty()) {
            plugin.getLogger().warning("Failed to load some mobs. Please check your configuration files.");
            plugin.getLogger().warning("------------------------------");
            for (String error : errors) {
                plugin.getLogger().warning("- " + error);
            }
            plugin.getLogger().warning("------------------------------");
        }
        plugin.getLogger().info("Successfully loaded " + loadedCount + " mobs.");
    }
}
