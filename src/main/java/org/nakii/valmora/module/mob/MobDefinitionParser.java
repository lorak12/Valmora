package org.nakii.valmora.module.mob;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.item.MechanicRegistry;
import org.nakii.valmora.module.mob.ability.MobAbility;
import org.nakii.valmora.module.mob.ability.MobAbilityParser;
import org.nakii.valmora.api.config.LoadResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

        // Stats. The canonical form is a nested 'stats:' block; legacy flat keys
        // (health, base-damage, speed) at the top level are read as a fallback.
        ConfigurationSection statsSection = section.getConfigurationSection("stats");
        if (statsSection != null) {
            if (statsSection.contains("health")) builder.health(statsSection.getDouble("health"));
            if (statsSection.contains("damage")) builder.baseDamage(statsSection.getDouble("damage"));
            if (statsSection.contains("speed")) builder.speed(statsSection.getDouble("speed"));
            if (statsSection.contains("defense")) builder.defense(statsSection.getDouble("defense"));
            if (statsSection.contains("strength")) builder.strength(statsSection.getDouble("strength"));
            if (statsSection.contains("crit-chance")) builder.critChance(statsSection.getDouble("crit-chance"));
            if (statsSection.contains("crit-damage")) builder.critDamage(statsSection.getDouble("crit-damage"));
        }
        // Flat fallback / overrides
        if (section.contains("health")) {
            builder.health(section.getDouble("health"));
        }
        if (section.contains("base-damage")) {
            builder.baseDamage(section.getDouble("base-damage"));
        }
        if (section.contains("speed")) {
            builder.speed(section.getDouble("speed"));
        }
        if (section.contains("defense")) {
            builder.defense(section.getDouble("defense"));
        }

        // Damage resistances / immunities (DamageType -> fraction 0..1, 1.0 = immune)
        ConfigurationSection resistSection = section.getConfigurationSection("resistances");
        if (resistSection != null) {
            Map<DamageType, Double> resistances = new EnumMap<>(DamageType.class);
            for (String typeKey : resistSection.getKeys(false)) {
                DamageType type;
                try {
                    type = DamageType.valueOf(typeKey.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Invalid resistance damage-type '" + typeKey + "'.");
                }
                double value = Math.max(0.0, Math.min(1.0, resistSection.getDouble(typeKey)));
                resistances.put(type, value);
            }
            builder.resistances(resistances);
        }

        // Behavior flags
        if (section.contains("knockback-resistance")) {
            builder.knockbackResistance(section.getDouble("knockback-resistance"));
        }
        if (section.contains("no-ai")) builder.noAi(section.getBoolean("no-ai"));
        if (section.contains("silent")) builder.silent(section.getBoolean("silent"));
        if (section.contains("glowing")) builder.glowing(section.getBoolean("glowing"));
        if (section.contains("persistent")) builder.persistent(section.getBoolean("persistent"));
        if (section.contains("baby")) builder.baby(section.getBoolean("baby"));
        if (section.contains("prevent-sun-burn")) builder.preventSunBurn(section.getBoolean("prevent-sun-burn"));

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

        // Boss bar
        ConfigurationSection barSection = section.getConfigurationSection("boss-bar");
        if (barSection != null && barSection.getBoolean("enabled", false)) {
            BossBar.Color color;
            try {
                color = BossBar.Color.valueOf(barSection.getString("color", "RED").toUpperCase());
            } catch (IllegalArgumentException e) {
                return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Invalid boss-bar color '" + barSection.getString("color") + "'.");
            }
            BossBar.Overlay overlay = parseOverlay(barSection.getString("style", "PROGRESS"));
            if (overlay == null) {
                return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': Invalid boss-bar style '" + barSection.getString("style") + "'.");
            }
            double range = barSection.getDouble("range", 40.0);
            builder.bossBar(new BossBarConfig(true, color, overlay, range));
        }

        // Abilities (boss attacks). Reuses the shared mechanic registry from the ability module.
        ConfigurationSection abilitiesSection = section.getConfigurationSection("abilities");
        if (abilitiesSection != null) {
            MechanicRegistry registry = ValmoraAPI.getInstance().getAbilityManager().getMechanicRegistry();
            try {
                List<MobAbility> abilities = MobAbilityParser.parse(abilitiesSection, registry);
                builder.abilities(abilities);
            } catch (MobAbilityParser.ParseException e) {
                return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': " + e.getMessage());
            }
        }

        return LoadResult.success(builder.build());
    }

    /** Maps both Adventure overlay names and common Bukkit-style names to an Adventure overlay. */
    private static BossBar.Overlay parseOverlay(String raw) {
        return switch (raw.toUpperCase()) {
            case "PROGRESS", "SOLID" -> BossBar.Overlay.PROGRESS;
            case "NOTCHED_6", "SEGMENTED_6" -> BossBar.Overlay.NOTCHED_6;
            case "NOTCHED_10", "SEGMENTED_10" -> BossBar.Overlay.NOTCHED_10;
            case "NOTCHED_12", "SEGMENTED_12" -> BossBar.Overlay.NOTCHED_12;
            case "NOTCHED_20", "SEGMENTED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> null;
        };
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
