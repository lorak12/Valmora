package org.nakii.valmora.module.mob;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.api.config.LoadResult;

import java.util.ArrayList;
import java.util.List;

public class MobDefinitionParser {

    public static LoadResult<MobDefinition, String> parse(String sectionId, ConfigurationSection section, String fileName, ItemManager itemManager) {
        MobDefinition.Builder builder = new MobDefinition.Builder(sectionId);

        // Name
        if (section.contains("name")) {
            builder.name(section.getString("name"));
        }

        // Category (required)
        if (!section.contains("category")) {
            return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Missing required field 'category'.");
        }
        String categoryStr = section.getString("category");
        MobCategory category;
        try {
            category = MobCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Invalid category '" + categoryStr + "'.");
        }
        builder.category(category);

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
        }
        if (section.contains("base-damage")) {
            builder.baseDamage(section.getDouble("base-damage"));
        }
        if (section.contains("speed")) {
            builder.speed(section.getDouble("speed"));
        }

        // Level
        if (section.contains("level")) {
            builder.level(section.getInt("level"));
        }

        // Base XP
        if (section.contains("base-xp")) {
            builder.baseXp(section.getInt("base-xp"));
        }

        // Gold Reward
        if (section.contains("gold-reward")) {
            builder.goldReward(section.getInt("gold-reward"));
        }

        // Damage Type
        if (section.contains("damage-type")) {
            String damageTypeStr = section.getString("damage-type");
            try {
                DamageType damageType = DamageType.valueOf(damageTypeStr.toUpperCase());
                builder.damageType(damageType);
            } catch (IllegalArgumentException e) {
                return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Invalid damage-type '" + damageTypeStr + "'.");
            }
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

        // Loot Table
        if (section.contains("loot-table")) {
            ConfigurationSection lootSection = section.getConfigurationSection("loot-table");
            if (lootSection != null && lootSection.contains("drops")) {
                List<?> dropsList = lootSection.getList("drops");
                if (dropsList != null) {
                    List<LootEntry> entries = new ArrayList<>();
                    for (Object dropObj : dropsList) {
                        if (dropObj instanceof ConfigurationSection dropEntry) {
                            LootEntry entry = parseLootEntry(dropEntry, sectionId, fileName, itemManager);
                            if (entry == null) {
                                return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Failed to parse loot entry.");
                            }
                            entries.add(entry);
                        }
                    }
                    builder.lootTable(new LootTable(entries));
                }
            }
        }

        return LoadResult.success(builder.build());
    }

    private static LootEntry parseLootEntry(ConfigurationSection section, String mobId, String fileName, ItemManager itemManager) {
        if (!section.contains("item")) {
            return null;
        }

        String itemStr = section.getString("item");
        ItemStack item;
        
        Material material = Material.getMaterial(itemStr.toUpperCase());
        if (material != null) {
            item = new ItemStack(material);
        } else {
            item = itemManager.createItemStack(itemStr);
            if (item == null) {
                return null;
            }
        }

        int minAmount = section.contains("min-amount") ? section.getInt("min-amount") : 1;
        int maxAmount = section.contains("max-amount") ? section.getInt("max-amount") : minAmount;
        double chance = section.contains("chance") ? section.getDouble("chance") : 1.0;
        boolean luckAffected = section.contains("luck-affected") && section.getBoolean("luck-affected");

        return new LootEntry(item, minAmount, maxAmount, chance, luckAffected);
    }
}
