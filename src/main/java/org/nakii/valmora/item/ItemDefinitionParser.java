package org.nakii.valmora.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.stat.Stat;

public class ItemDefinitionParser {

    public static LoadResult<ItemDefinition, String> parse(String sectionId, ConfigurationSection section, String fileName) {
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

        return LoadResult.success(builder.build());
    }
}
