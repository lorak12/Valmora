package org.nakii.valmora.module.slayer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SlayerModule implements ReloadableModule {

    private final Valmora plugin;
    private final Map<String, SlayerDefinition> definitions = new HashMap<>();
    private SlayerListener listener;

    public SlayerModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        definitions.clear();
        loadDefinitions();

        this.listener = new SlayerListener(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getScriptModule().registerEvent(new SlayerStartEventFactory(this, plugin));
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        definitions.clear();
    }

    @Override
    public String getId() { return "slayer"; }

    @Override
    public String getName() { return "Slayer System"; }

    public SlayerDefinition getDefinition(String id) { return definitions.get(id.toLowerCase()); }
    public Collection<SlayerDefinition> getDefinitions() { return definitions.values(); }
    public Valmora getPlugin() { return plugin; }

    private void loadDefinitions() {
        YamlLoader<SlayerDefinition> loader = new YamlLoader<>(plugin, "slayers", "Slayer");
        loader.load(this::parseDefinition, def -> definitions.put(def.getId(), def));
    }

    private LoadResult<SlayerDefinition, String> parseDefinition(String id, ConfigurationSection section, String filePath) {
        try {
            String name = section.getString("name", id);
            Map<Integer, SlayerTier> tiers = new LinkedHashMap<>();

            ConfigurationSection tiersSec = section.getConfigurationSection("tiers");
            if (tiersSec != null) {
                for (String key : tiersSec.getKeys(false)) {
                    int tier;
                    try { tier = Integer.parseInt(key); } catch (NumberFormatException ignored) { continue; }
                    ConfigurationSection tierSec = tiersSec.getConfigurationSection(key);
                    if (tierSec == null) continue;

                    double cost = tierSec.getDouble("cost", 0.0);
                    String category = tierSec.getString("target-category", "HOSTILE");
                    int kills = tierSec.getInt("kills-required", 5);
                    String bossMob = tierSec.getString("boss-mob", "");
                    var completionEvents = tierSec.getStringList("completion-events");

                    tiers.put(tier, new SlayerTier(tier, cost, category, kills, bossMob, completionEvents));
                }
            }

            return LoadResult.success(new SlayerDefinition(id, name, tiers));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse slayer '" + id + "': " + e.getMessage());
        }
    }
}
