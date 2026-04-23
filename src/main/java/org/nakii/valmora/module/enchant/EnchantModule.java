package org.nakii.valmora.module.enchant;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.enchant.logic.SharpnessLogic;
import org.nakii.valmora.module.enchant.logic.GrowthLogic;
import org.nakii.valmora.module.item.ItemType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EnchantModule implements ReloadableModule {

    private final Valmora plugin;
    private final EnchantmentRegistry registry;
    private final Map<String, EnchantmentLogic> logicMap;

    public EnchantModule(Valmora plugin) {
        this.plugin = plugin;
        this.registry = new EnchantmentRegistry();
        this.logicMap = new ConcurrentHashMap<>();
    }

    @Override
    public void onEnable() {
        registerBuiltinLogics();
        loadEnchants();
    }

    private void registerBuiltinLogics() {
        logicMap.put("valmora:sharpness", new SharpnessLogic());
        logicMap.put("valmora:growth", new GrowthLogic());
    }

    @Override
    public void onDisable() {
        registry.clear();
        logicMap.clear();
    }

    @Override
    public String getId() {
        return "enchants";
    }

    @Override
    public String getName() {
        return "Enchant System";
    }

    public EnchantmentRegistry getRegistry() {
        return registry;
    }

    public EnchantmentLogic getLogic(String id) {
        return logicMap.get(id.toLowerCase());
    }

    public void registerLogic(String id, EnchantmentLogic logic) {
        logicMap.put(id.toLowerCase(), logic);
    }

    private void loadEnchants() {
        YamlLoader<EnchantmentDefinition> loader = new YamlLoader<>(plugin, "enchants", "Enchantment");
        loader.load(createParser(), definition -> {
            registry.register(definition.getId(), definition);
        });
    }

    private YamlLoader.SectionParser<EnchantmentDefinition> createParser() {
        return (id, section, filePath) -> {
            try {
                String name = section.getString("name", id);
                List<String> description = section.getStringList("description");
                if (description == null) {
                    description = new ArrayList<>();
                }

                int etableMaxLevel = section.getInt("etable-max-level", 5);
                int absoluteMaxLevel = section.getInt("absolute-max-level", 10);

                List<ItemType> targets = parseTargets(section.getStringList("targets"));
                List<String> conflicts = section.getStringList("conflicts");
                if (conflicts == null) {
                    conflicts = new ArrayList<>();
                }

                String logicId = section.getString("logic", "");
                EnchantmentLogic logic = logicMap.get(logicId.toLowerCase());

                EnchantmentDefinition definition = new EnchantmentDefinition(
                        id, name, description, etableMaxLevel,
                        absoluteMaxLevel, targets, conflicts, logic
                );

                return LoadResult.success(definition);
            } catch (Exception e) {
                return LoadResult.failure("[" + filePath + "] Failed to parse enchant '" + id + "': " + e.getMessage());
            }
        };
    }

    private List<ItemType> parseTargets(List<String> targetStrings) {
        List<ItemType> targets = new ArrayList<>();
        if (targetStrings != null) {
            for (String target : targetStrings) {
                try {
                    targets.add(ItemType.valueOf(target.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return targets;
    }
}