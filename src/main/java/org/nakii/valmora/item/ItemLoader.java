package org.nakii.valmora.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ItemLoader {
    
    private final Valmora plugin;
    private final ItemRegistry registry;

    public ItemLoader(Valmora plugin, ItemRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    // Loads all items from the items folder
    public void loadItems() {
        registry.clear();
        File folder = new File(plugin.getDataFolder(), "items");
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
                                LoadResult<ItemDefinition, String> result = ItemDefinitionParser.parse(key, section, "items/" + file.getName(), plugin.getAbilityManager().getMechanicRegistry());
                                if (result.isSuccess()) {
                                    registry.registerItem(result.getValue());
                                    loadedCount++;
                                } else {
                                    errors.add(result.getError());
                                }
                            }
                        }
                    } catch(Exception e) {
                        errors.add("[items/" + file.getName() + "] Failed to parse YAML: " + e.getMessage());
                    }
                }
            }
        }   

        if (!errors.isEmpty()) {
            plugin.getLogger().warning("Failed to load some items. Please check your configuration files.");
            plugin.getLogger().warning("------------------------------");
            for (String error : errors) {
                plugin.getLogger().warning("- " + error);
            }
            plugin.getLogger().warning("------------------------------");
        }
        plugin.getLogger().info("Successfully loaded " + loadedCount + " items.");
    }
}
