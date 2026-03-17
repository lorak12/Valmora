package org.nakii.valmora.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.item.ItemManager;
import org.nakii.valmora.item.LoadResult;

public class MobDefinitionParser {

    public static LoadResult<MobDefinition, String> parse(String sectionId, ConfigurationSection section, String fileName, ItemManager itemManager) {
        MobDefinition.Builder builder = new MobDefinition.Builder(sectionId);

        // Name
        if (section.contains("name")) {
            builder.name(section.getString("name"));
        }

        // Entity Type
        if (!section.contains("type")) {
            return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Missing required field 'type'.");
        }
        String typeStr = section.getString("type");
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Invalid entity type '" + typeStr + "'.");
        }
        builder.entityType(entityType);

        // Stats
        if (section.contains("health")) {
            builder.health(section.getDouble("health"));
        } else {
            builder.health(20.0); // Default placeholder
        }
        if (section.contains("damage")) {
            builder.damage(section.getDouble("damage"));
        }
        if (section.contains("speed")) {
            builder.speed(section.getDouble("speed"));
        }

        // Equipment
        if (section.contains("equipment")) {
            ConfigurationSection equipSection = section.getConfigurationSection("equipment");
            if (equipSection != null) {
                ItemStack[] armor = new ItemStack[4];
                boolean hasArmor = false;

                // Helmet (index 3)
                if (equipSection.contains("helmet")) {
                    armor[3] = itemManager.createItemStack(equipSection.getString("helmet"));
                    hasArmor = true;
                }
                // Chestplate (index 2)
                if (equipSection.contains("chestplate")) {
                    armor[2] = itemManager.createItemStack(equipSection.getString("chestplate"));
                    hasArmor = true;
                }
                // Leggings (index 1)
                if (equipSection.contains("leggings")) {
                    armor[1] = itemManager.createItemStack(equipSection.getString("leggings"));
                    hasArmor = true;
                }
                // Boots (index 0)
                if (equipSection.contains("boots")) {
                    armor[0] = itemManager.createItemStack(equipSection.getString("boots"));
                    hasArmor = true;
                }

                if (hasArmor) {
                    builder.armor(armor);
                }

                if (equipSection.contains("main-hand")) {
                    builder.weapon(itemManager.createItemStack(equipSection.getString("main-hand")));
                }

                if (equipSection.contains("off-hand")) {
                    builder.offHand(itemManager.createItemStack(equipSection.getString("off-hand")));
                }
            }
        }

        // Level
        if (section.contains("level")) {
            builder.level(section.getInt("level"));
        } else {
            builder.level(1); // Default placeholder
        }

        return LoadResult.success(builder.build());
    }
}
