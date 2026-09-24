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
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobDefinitionParser {

    public static LoadResult<MobDefinition, String> parse(String sectionId, ConfigurationSection section, String fileName, ItemManager itemManager) {
        MobDefinition.Builder builder = new MobDefinition.Builder(sectionId);
        ConfigReader reader = ConfigReader.of(section).knownKeys(KNOWN_KEYS);

        // Name — fall back to the mob's own id so a missing 'name' can't NPE MobFactory.applyVisuals
        // (which formats it unconditionally) instead of just displaying a blank/odd name.
        builder.name(section.getString("name", sectionId));

        // Category and entity type (both required). Both are checked before bailing out so a
        // broken entry reports every problem at once.
        MobCategory category = reader.requireOneOf("category", "mob category", MobCategory::find, MobDefinitionParser::categoryNames);
        EntityType entityType = reader.requireEnum("type", EntityType.class);
        if (entityType != null && !entityType.isAlive()) {
            reader.error("type", "entity type '" + entityType.name() + "' is not a living entity");
        }
        if (category == null || entityType == null || !entityType.isAlive()) {
            return LoadResult.failure("[" + fileName + "] In mob '" + sectionId + "': not loaded — see the errors above.");
        }
        builder.category(category);
        builder.entityType(entityType);

        // Stats. The canonical form is a nested 'stats:' block; legacy flat keys
        // (health, base-damage, speed) at the top level are read as a fallback.
        // Default health to a sane vanilla baseline (20 = one player heart bar) so an unset
        // stats.health/health doesn't silently produce a 0-max-HP (instant-death) mob.
        builder.health(20.0);
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
            Map<DamageType, Double> resistances = new HashMap<>();
            ConfigReader resistReader = reader.section("resistances");
            for (String typeKey : resistSection.getKeys(false)) {
                DamageType type = DamageType.find(typeKey).orElse(null);
                if (type == null) {
                    resistReader.warn(typeKey, "unknown damage type '" + typeKey + "' — resistance ignored",
                            Suggestions.hint(typeKey, damageTypeNames()));
                    continue;
                }
                resistances.put(type, resistReader.doubleRange(typeKey, 0.0, 0.0, 1.0));
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

        // Basic AI tuning (aggro/leash — see MobAiTask) and natural spawning (see NaturalSpawnTask)
        ConfigurationSection aiSection = section.getConfigurationSection("ai");
        if (aiSection != null) {
            if (aiSection.contains("aggro-range")) builder.aggroRange(aiSection.getDouble("aggro-range"));
            if (aiSection.contains("leash-range")) builder.leashRange(aiSection.getDouble("leash-range"));
            if (aiSection.contains("ignore-npcs")) builder.ignoreNpcs(aiSection.getBoolean("ignore-npcs"));
            if (aiSection.contains("target-conditions")) {
                builder.targetConditions(aiSection.getStringList("target-conditions"));
            }
        }
        ConfigurationSection spawnSection = section.getConfigurationSection("natural-spawn");
        if (spawnSection != null) {
            builder.naturalSpawn(spawnSection.getBoolean("enabled", true));
            if (spawnSection.contains("chance")) builder.naturalSpawnChance(spawnSection.getDouble("chance"));
            if (spawnSection.contains("max-nearby")) builder.naturalSpawnMaxNearby(spawnSection.getInt("max-nearby"));
            if (spawnSection.contains("vanilla-default")) builder.vanillaDefault(spawnSection.getBoolean("vanilla-default"));
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
            DamageType damageType = reader.oneOf("damage-type", "damage type", DamageType::find, MobDefinitionParser::damageTypeNames, null);
            if (damageType != null) builder.damageType(damageType);
        }

        // Equipment
        if (section.contains("equipment")) {
            ConfigurationSection equipSection = section.getConfigurationSection("equipment");
            ConfigReader equip = reader.section("equipment");
            if (equip != null) equip.knownKeys("helmet", "chestplate", "leggings", "boots", "main-hand", "off-hand");
            if (equipSection != null) {
                ItemStack[] armor = new ItemStack[4];
                boolean hasArmor = false;

                // Helmet (index 3)
                if (equipSection.contains("helmet")) {
                    armor[3] = equipmentItem(equip, "helmet", itemManager);
                    hasArmor |= armor[3] != null;
                }
                // Chestplate (index 2)
                if (equipSection.contains("chestplate")) {
                    armor[2] = equipmentItem(equip, "chestplate", itemManager);
                    hasArmor |= armor[2] != null;
                }
                // Leggings (index 1)
                if (equipSection.contains("leggings")) {
                    armor[1] = equipmentItem(equip, "leggings", itemManager);
                    hasArmor |= armor[1] != null;
                }
                // Boots (index 0)
                if (equipSection.contains("boots")) {
                    armor[0] = equipmentItem(equip, "boots", itemManager);
                    hasArmor |= armor[0] != null;
                }

                if (hasArmor) {
                    builder.armor(armor);
                }

                if (equipSection.contains("main-hand")) {
                    builder.weapon(equipmentItem(equip, "main-hand", itemManager));
                }

                if (equipSection.contains("off-hand")) {
                    builder.offHand(equipmentItem(equip, "off-hand", itemManager));
                }
            }
        }

        // Loot Table
        if (section.contains("loot-table")) {
            ConfigurationSection lootSection = section.getConfigurationSection("loot-table");
            if (lootSection != null && lootSection.contains("drops")) {
                // NOTE: getList("drops") deserializes each map entry as a raw java.util.Map, not a
                // ConfigurationSection (that instanceof check only ever matches for actual nested
                // sections, e.g. YAML anchors) — so use getMapList() and wrap each map into a
                // section via MemoryConfiguration#createSection to reuse parseLootEntry() below.
                // A drop that can't be parsed (unknown item, missing 'item') is reported and left
                // out — the rest of the mob, including its other drops, still loads.
                List<LootEntry> entries = new ArrayList<>();
                ConfigReader loot = reader.section("loot-table");
                for (ConfigReader drop : loot.sectionList("drops")) {
                    LootEntry entry = parseLootEntry(drop, itemManager);
                    if (entry != null) entries.add(entry);
                }
                builder.lootTable(new LootTable(entries));
            }
        }

        // Boss bar
        ConfigurationSection barSection = section.getConfigurationSection("boss-bar");
        if (barSection != null && barSection.getBoolean("enabled", false)) {
            ConfigReader bar = reader.section("boss-bar").knownKeys("enabled", "color", "style", "range");
            BossBar.Color color = bar.enumOf("color", BossBar.Color.class, BossBar.Color.RED);
            BossBar.Overlay overlay = parseOverlay(barSection.getString("style", "PROGRESS"));
            if (overlay == null) {
                bar.warn("style", "unknown boss-bar style '" + barSection.getString("style") + "' — using PROGRESS",
                        Suggestions.hint(barSection.getString("style"), List.of("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20")));
                overlay = BossBar.Overlay.PROGRESS;
            }
            double range = barSection.getDouble("range", BossBarConfig.defaultRange());
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

    private static LootEntry parseLootEntry(ConfigReader drop, ItemManager itemManager) {
        drop.knownKeys("item", "min-amount", "max-amount", "amount", "chance", "luck-affected");
        String itemStr = drop.requireString("item");
        if (itemStr == null) return null;

        ItemStack item;
        Material material = Material.getMaterial(itemStr.toUpperCase());
        if (material != null) {
            item = new ItemStack(material);
        } else {
            item = itemManager.createItemStack(itemStr);
            if (item == null) {
                drop.warn("item", "unknown item '" + itemStr + "' — this drop is skipped", itemHint(itemStr, itemManager));
                return null;
            }
        }

        int fixed = drop.intRange("amount", 1, 0, 10_000);
        int minAmount = drop.intRange("min-amount", fixed, 0, 10_000);
        int maxAmount = drop.intRange("max-amount", drop.has("amount") ? fixed : minAmount, 0, 10_000);
        if (maxAmount < minAmount) {
            drop.warn("max-amount", "max-amount " + maxAmount + " is below min-amount " + minAmount + " — using " + minAmount);
            maxAmount = minAmount;
        }
        double chance = drop.doubleRange("chance", 1.0, 0.0, 1.0);
        boolean luckAffected = drop.bool("luck-affected", false);

        return new LootEntry(item, minAmount, maxAmount, chance, luckAffected);
    }

    /** An equipment slot's item; unknown ids are reported (with a suggestion) instead of silently leaving the slot empty. */
    private static ItemStack equipmentItem(ConfigReader equip, String slot, ItemManager itemManager) {
        String id = equip.string(slot, null);
        if (id == null || id.isBlank()) return null;
        ItemStack stack = itemManager.createItemStack(id);
        if (stack == null) equip.warn(slot, "unknown item '" + id + "' — slot left empty", itemHint(id, itemManager));
        return stack;
    }

    private static String itemHint(String id, ItemManager itemManager) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        try {
            ids.addAll(itemManager.getItemRegistry().getAllItemIds());
        } catch (RuntimeException ignored) {
            // registry not ready — suggest from materials only
        }
        String hint = Suggestions.hint(id, ids);
        return hint != null ? hint : ConfigReader.materialHint(id);
    }

    private static List<String> categoryNames() {
        return MobCategory.values().stream().map(MobCategory::getId).toList();
    }

    private static List<String> damageTypeNames() {
        return DamageType.values().stream().map(DamageType::getId).toList();
    }

    /** Every top-level key a mob definition may use — anything else is reported as a likely typo. */
    private static final List<String> KNOWN_KEYS = List.of(
            "name", "category", "type", "stats", "health", "base-damage", "speed", "defense",
            "resistances", "knockback-resistance", "no-ai", "silent", "glowing", "persistent", "baby",
            "prevent-sun-burn", "ai", "natural-spawn", "level", "base-xp", "gold-reward", "damage-type",
            "equipment", "loot-table", "boss-bar", "abilities");
}
