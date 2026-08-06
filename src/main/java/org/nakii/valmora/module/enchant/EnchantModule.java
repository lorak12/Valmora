package org.nakii.valmora.module.enchant;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.nakii.valmora.module.enchant.logic.StatBonusLogic;
import org.nakii.valmora.module.enchant.logic.DamageMultiplierLogic;
import org.nakii.valmora.module.enchant.logic.DefenseReductionLogic;
import org.nakii.valmora.module.item.ItemType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class EnchantModule implements ReloadableModule {

    private final Valmora plugin;
    private final EnchantmentRegistry registry;
    private final Map<String, EnchantmentLogic> logicMap;
    private final Map<String, Function<ConfigurationSection, EnchantmentLogic>> logicFactories;

    public EnchantModule(Valmora plugin) {
        this.plugin = plugin;
        this.registry = new EnchantmentRegistry();
        this.logicMap = new ConcurrentHashMap<>();
        this.logicFactories = new ConcurrentHashMap<>();
    }

    @Override
    public void onEnable() {
        registerBuiltinLogics();
        loadEnchants();
    }

    private void registerBuiltinLogics() {
        // Phase 4.5 follow-up (docs/REFACTOR/PROGRESS.md): "sharpness" used to be a separate
        // hardcoded class (SharpnessLogic) applying a fixed 5%/level MELEE multiplier — an exact
        // special case of the generic valmora:damage_multiplier logic below. Registering it as a
        // factory with matching defaults means existing enchants/*.yml with `logic:
        // valmora:sharpness` and no logic-params behave identically, while a new enchant can now
        // override the damage type or percent-per-level via YAML.
        logicFactories.put("valmora:sharpness", params ->
            new DamageMultiplierLogic(params.getString("type", "MELEE"), params.getDouble("percent-per-level", 5.0)));

        // Phase 4.5 (docs/REFACTOR/PROGRESS.md): "growth", "fortune", and "efficiency" used to be
        // separate hardcoded Java classes (GrowthLogic, FortuneLogic, EfficiencyLogic), each just
        // a fixed per-level bonus to one stat — i.e. already a special case of the generic
        // valmora:stat_bonus logic below. Registering them as factories means any existing
        // enchants/*.yml with `logic: valmora:fortune` and no logic-params behaves identically
        // (defaults match the old hardcoded values), while a new enchant can override the bonus
        // via YAML without a code change. Defaults resolve through StatRoleRegistry (via
        // SystemStats) rather than a literal "health"/"mining_fortune" string, so a server that
        // renamed those roles keeps getting the right stat.
        logicFactories.put("valmora:growth", params ->
            new StatBonusLogic(params.getString("stat", ValmoraAPI.getInstance().getSystemStats().getHealth()),
                    params.getDouble("per-level", 10.0)));
        logicFactories.put("valmora:fortune", params ->
            new StatBonusLogic(params.getString("stat", ValmoraAPI.getInstance().getSystemStats().getMiningFortune()),
                    params.getDouble("per-level", 10.0)));
        logicFactories.put("valmora:efficiency", params ->
            new StatBonusLogic(params.getString("stat", ValmoraAPI.getInstance().getSystemStats().getMiningSpeed()),
                    params.getDouble("per-level", 50.0)));

        // Parameterized generic logics
        logicFactories.put("valmora:stat_bonus", params ->
            new StatBonusLogic(params.getString("stat", "strength"), params.getDouble("per-level", 1.0)));
        logicFactories.put("valmora:damage_multiplier", params ->
            new DamageMultiplierLogic(params.getString("type", "MELEE"), params.getDouble("percent-per-level", 5.0)));
        logicFactories.put("valmora:defense_reduction", params ->
            new DefenseReductionLogic(params.getDouble("percent-per-level", 3.0)));
    }

    @Override
    public void onDisable() {
        registry.clear();
        logicMap.clear();
        logicFactories.clear();
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
                ConfigurationSection logicParams = section.getConfigurationSection("logic-params");
                if (logicParams == null) logicParams = new MemoryConfiguration();

                EnchantmentLogic logic;
                Function<ConfigurationSection, EnchantmentLogic> factory = logicFactories.get(logicId.toLowerCase());
                if (factory != null) {
                    logic = factory.apply(logicParams);
                } else {
                    logic = logicMap.get(logicId.toLowerCase());
                }

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