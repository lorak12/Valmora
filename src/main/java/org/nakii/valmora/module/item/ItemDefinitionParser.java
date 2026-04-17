package org.nakii.valmora.module.item;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.module.stat.Stat;

public class ItemDefinitionParser {

    public static LoadResult<ItemDefinition, String> parse(String sectionId, ConfigurationSection section, String fileName, MechanicRegistry mechanicRegistry) {
        ItemDefinition.Builder builder = new ItemDefinition.Builder(sectionId);

        // Name
        if (section.contains("name")) {
            builder.name(section.getString("name"));
        }

        // Material
        if (!section.contains("material")) {
            return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': Missing required field 'material'.");
        }
        String materialStr = section.getString("material");
        Material material = Material.matchMaterial(materialStr);
        if (material == null) {
            return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': Invalid material '" + materialStr + "'.");
        }
        builder.material(material);

        // Rarity
        if (section.contains("rarity")) {
            String rarityStr = section.getString("rarity");
            try {
                builder.rarity(Rarity.valueOf(rarityStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': Invalid rarity '" + rarityStr + "'. Valid options are: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY.");
            }
        }

        // ItemType
        if (section.contains("item-type")) {
            String typeStr = section.getString("item-type");
            try {
                builder.itemType(ItemType.valueOf(typeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': Invalid item-type '" + typeStr + "'.");
            }
        }

        // Lore
        if (section.contains("lore")) {
            builder.lore(section.getStringList("lore"));
        }

        // Stats
        if (section.contains("stats")) {
            ConfigurationSection statsSection = section.getConfigurationSection("stats");
            for (String statKey : statsSection.getKeys(false)) {
                try {
                    Stat stat = Stat.valueOf(statKey.toUpperCase());
                    if (!statsSection.isDouble(statKey) && !statsSection.isInt(statKey)) {
                         return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': Stat '" + statKey + "' must be a number.");
                    }
                    builder.stat(stat, statsSection.getDouble(statKey));
                } catch (IllegalArgumentException e) {
                    return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': Unknown stat '" + statKey + "'.");
                }
            }
        }

        if (section.contains("abilities")) {
            ConfigurationSection abilitiesSec = section.getConfigurationSection("abilities");
            if (abilitiesSec != null) {
                for (String abKey : abilitiesSec.getKeys(false)) {
                    ConfigurationSection abSec = abilitiesSec.getConfigurationSection(abKey);
                    if (abSec == null) continue;

                    AbilityDefinition.Builder abBuilder = new AbilityDefinition.Builder(abKey);
                    
                    if (abSec.contains("name")) abBuilder.name(abSec.getString("name"));
                    
                    if (abSec.contains("trigger")) {
                        try {
                            abBuilder.trigger(AbilityTrigger.valueOf(abSec.getString("trigger").toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            return LoadResult.failure("[" + fileName + "] Invalid trigger '" + abSec.getString("trigger") + "' in ability '" + abKey + "'.");
                        }
                    }

                    abBuilder.targetRange(abSec.getDouble("target-range", 0.0));
                    abBuilder.cooldown(abSec.getDouble("cooldown", 0.0));
                    abBuilder.manaCost(abSec.getDouble("mana-cost", 0.0));
                    
                    if (abSec.contains("description")) {
                        abBuilder.description(abSec.getStringList("description"));
                    }

                    // Parse Mechanics List
                    if (abSec.contains("mechanics")) {
                        for (Map<?, ?> map : abSec.getMapList("mechanics")) {
                            String type = (String) map.get("type");
                            if (type == null) continue;

                            Optional<AbilityMechanic> mechOpt = mechanicRegistry.getMechanic(type);
                            if (mechOpt.isEmpty()) {
                                return LoadResult.failure("[" + fileName + "] Unknown mechanic type '" + type + "' in ability '" + abKey + "'.");
                            }

                            // Convert the "params" map into a ConfigurationSection for easy Java reading
                            MemoryConfiguration params = new MemoryConfiguration();
                            if (map.containsKey("params")) {
                                Map<?, ?> paramsMap = (Map<?, ?>) map.get("params");
                                for (Map.Entry<?, ?> entry : paramsMap.entrySet()) {
                                    params.set(entry.getKey().toString(), entry.getValue());
                                }
                            }
                            abBuilder.addMechanic(new ConfiguredMechanic(mechOpt.get(), params));
                        }
                    }
                    builder.ability(abKey, abBuilder.build());
                }
            }
        }

        return LoadResult.success(builder.build());
    }
}
