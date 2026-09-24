package org.nakii.valmora.module.item;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.diag.ScriptCompile;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
import org.nakii.valmora.infrastructure.config.refs.Kinds;
import org.nakii.valmora.module.stat.StatRegistry;

public class ItemDefinitionParser {

    public static LoadResult<ItemDefinition, String> parse(String sectionId, ConfigurationSection section, String fileName, MechanicRegistry mechanicRegistry) {
        ItemDefinition.Builder builder = new ItemDefinition.Builder(sectionId);
        ConfigReader reader = ConfigReader.of(section).knownKeys(KNOWN_KEYS);

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
            reader.error("material", "unknown material '" + materialStr + "'", ConfigReader.materialHint(materialStr));
            return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': not loaded — invalid material.");
        }
        builder.material(material);

        // Rarity
        if (section.contains("rarity")) {
            String rarityStr = section.getString("rarity", "COMMON").toUpperCase();
            // rarities.yml is the source of truth (so custom rarities work on items); the legacy
            // Rarity enum is only a fallback when the rarity module has nothing loaded.
            if (!ItemRarities.isKnown(rarityStr)) {
                String hint = Suggestions.hint(rarityStr, ItemRarities.knownKeys());
                return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': Invalid rarity '" + rarityStr
                        + "'" + (hint != null ? " (" + hint + ")" : "") + ". Valid options are the keys in rarities.yml: "
                        + ItemRarities.knownKeys() + ".");
            }
            builder.rarityKey(rarityStr);
        }

        // ItemType
        if (section.contains("item-type")) {
            String typeStr = section.getString("item-type");
            var itemType = ItemType.find(typeStr);
            if (itemType.isEmpty()) {
                String hint = Suggestions.hint(typeStr, ItemType.values().stream().map(ItemType::getId).toList());
                return LoadResult.failure("[" + fileName + "] In item '" + sectionId + "': Invalid item-type '" + typeStr + "'"
                        + (hint != null ? " (" + hint + ")" : "") + ".");
            }
            builder.itemType(itemType.get());
        }

        // Lore
        if (section.contains("lore")) {
            builder.lore(section.getStringList("lore"));
        }

        // Lore template (supports $item.stat.<id>$ tokens resolved at create-time)
        if (section.contains("lore-template")) {
            builder.loreTemplate(section.getStringList("lore-template"));
        }

        // Custom model data
        if (section.contains("custom-model-data")) {
            builder.customModelData(section.getInt("custom-model-data", 0));
        }

        // Armor set id (links this piece to a set bonus defined under set_bonuses/)
        if (section.contains("set")) {
            builder.set(section.getString("set"));
        }

        // GUI id to open when this item is used as a storage-slot container (e.g. a backpack)
        if (section.contains("container-gui")) {
            builder.containerGui(section.getString("container-gui"));
            reader.ref("container-gui", Kinds.GUI, section.getString("container-gui"));
        }

        // Stats
        if (section.contains("stats")) {
            StatRegistry statRegistry = ValmoraAPI.getInstance().getStatRegistry();
            ConfigurationSection statsSection = section.getConfigurationSection("stats");
            ConfigReader stats = reader.section("stats");
            // A bad stat line is reported and skipped; the rest of the item still loads.
            for (String statKey : statsSection.getKeys(false)) {
                if (!statsSection.isDouble(statKey) && !statsSection.isInt(statKey)) {
                    stats.warn(statKey, "stat '" + statKey + "' must be a number, got '" + statsSection.get(statKey) + "' — ignored");
                    continue;
                }
                if (!statRegistry.contains(statKey)) {
                    stats.warn(statKey, "unknown stat '" + statKey + "' — ignored", Suggestions.hint(statKey, statRegistry.getKeys()));
                    continue;
                }
                builder.stat(statKey, statsSection.getDouble(statKey));
            }
        }

        if (section.contains("abilities")) {
            ConfigurationSection abilitiesSec = section.getConfigurationSection("abilities");
            if (abilitiesSec != null) {
                for (String abKey : abilitiesSec.getKeys(false)) {
                    ConfigurationSection abSec = abilitiesSec.getConfigurationSection(abKey);
                    if (abSec == null) continue;

                    AbilityDefinition.Builder abBuilder = new AbilityDefinition.Builder(abKey);
                    ConfigReader.of(abSec, reader.scope() == null ? null : reader.scope().sub("abilities").sub(abKey))
                            .knownKeys("name", "display", "trigger", "target-range", "cooldown", "mana-cost",
                                    "description", "conditions", "mechanics");
                    
                    if (abSec.contains("name")) abBuilder.name(abSec.getString("name"));

                    if (abSec.contains("display")) {
                        try {
                            abBuilder.displayMode(AbilityDefinition.DisplayMode.valueOf(abSec.getString("display").toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            return LoadResult.failure("[" + fileName + "] Invalid display mode '" + abSec.getString("display") + "' in ability '" + abKey + "' (expected FULL or SIMPLE).");
                        }
                    }

                    if (abSec.contains("trigger")) {
                        try {
                            abBuilder.trigger(AbilityTrigger.valueOf(abSec.getString("trigger").toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            String hint = Suggestions.hint(abSec.getString("trigger"), ConfigReader.enumNames(AbilityTrigger.class));
                            return LoadResult.failure("[" + fileName + "] Invalid trigger '" + abSec.getString("trigger") + "' in ability '" + abKey + "'"
                                    + (hint != null ? " (" + hint + ")" : "") + ". Valid triggers: " + ConfigReader.enumNames(AbilityTrigger.class) + ".");
                        }
                    }

                    abBuilder.targetRange(abSec.getDouble("target-range", 0.0));
                    abBuilder.cooldown(abSec.getDouble("cooldown", 0.0));
                    abBuilder.manaCost(abSec.getDouble("mana-cost", 0.0));
                    
                    if (abSec.contains("description")) {
                        abBuilder.description(abSec.getStringList("description"));
                    }

                    if (abSec.contains("conditions")) {
                        // Compiled inside the ability's own location so DSL problems point at it.
                        // Pre-compiled once here (Phase 5 Task 20) — see AbilityDefinition's
                        // field comment for why this used to be a hot-path re-parse.
                        var compiled = ScriptCompile.at(reader.scope(), "abilities." + abKey + ".conditions", () ->
                                ValmoraAPI.getInstance().getScriptModule().getConditionParser().parseList(abSec.getStringList("conditions")));
                        abBuilder.conditions(compiled);
                    }

                    // Parse Mechanics List
                    if (abSec.contains("mechanics")) {
                        try {
                            for (ConfiguredMechanic mechanic : MechanicParser.parse(abSec.getMapList("mechanics"), mechanicRegistry)) {
                                abBuilder.addMechanic(mechanic);
                            }
                        } catch (MechanicParser.UnknownMechanicException e) {
                            return LoadResult.failure("[" + fileName + "] Unknown mechanic type '" + e.getMessage() + "' in ability '" + abKey + "'.");
                        }
                    }
                    builder.ability(abKey, abBuilder.build());
                }
            }
        }

        return LoadResult.success(builder.build());
    }

    /** Every top-level key an item definition may use — anything else is reported as a likely typo. */
    private static final java.util.List<String> KNOWN_KEYS = java.util.List.of(
            "name", "material", "rarity", "item-type", "lore", "lore-template", "custom-model-data",
            "set", "container-gui", "stats", "abilities");
}
