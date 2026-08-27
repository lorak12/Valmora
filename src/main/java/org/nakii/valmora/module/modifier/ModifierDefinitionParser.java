package org.nakii.valmora.module.modifier;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.module.item.MechanicRegistry;
import org.nakii.valmora.module.modifier.effect.ModifierEffect;
import org.nakii.valmora.module.modifier.effect.ModifierEffectParser;
import org.nakii.valmora.module.script.condition.ConditionGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses one entry of a {@code modifiers:} YAML section (docs/Valmora_Modifier_Framework_Design.docx
 * §7/§11/§13/§14/§15) into a {@link ModifierDefinition}.
 */
public final class ModifierDefinitionParser {

    private ModifierDefinitionParser() {}

    public static LoadResult<ModifierDefinition, String> parse(String id, ConfigurationSection section, String filePath,
                                                                 MechanicRegistry mechanicRegistry) {
        try {
            String groupId = section.getString("group");
            if (groupId == null || groupId.isBlank()) {
                return LoadResult.failure("[" + filePath + "] Modifier '" + id + "' is missing required 'group'");
            }

            String displayName = null;
            String prefix = null;
            String suffix = null;
            List<String> lore = Collections.emptyList();
            ConfigurationSection displaySec = section.getConfigurationSection("display");
            if (displaySec != null) {
                displayName = displaySec.getString("name");
                prefix = displaySec.getString("prefix");
                suffix = displaySec.getString("suffix");
                if (displaySec.contains("lore")) lore = displaySec.getStringList("lore");
            }

            ConditionGroup requirements = section.contains("requirements")
                    ? ValmoraAPI.getInstance().getScriptModule().getConditionParser().parseList(section.getStringList("requirements"))
                    : new ConditionGroup(Collections.emptyList());

            Set<String> tags = new HashSet<>();
            for (String tag : section.getStringList("tags")) tags.add(tag.toUpperCase(Locale.ROOT));

            Set<String> conflictIds = new HashSet<>();
            Set<String> conflictTags = new HashSet<>();
            ConfigurationSection conflictsSec = section.getConfigurationSection("conflicts");
            if (conflictsSec != null) {
                for (String cid : conflictsSec.getStringList("ids")) conflictIds.add(cid.toLowerCase(Locale.ROOT));
                for (String ctag : conflictsSec.getStringList("tags")) conflictTags.add(ctag.toUpperCase(Locale.ROOT));
            }

            Map<String, ModifierStateDefinition> state = new HashMap<>();
            ConfigurationSection stateSec = section.getConfigurationSection("state");
            if (stateSec != null) {
                for (String key : stateSec.getKeys(false)) {
                    ConfigurationSection fieldSec = stateSec.getConfigurationSection(key);
                    if (fieldSec == null) continue;
                    int def = fieldSec.getInt("default", 0);
                    int min = fieldSec.getInt("min", Integer.MIN_VALUE);
                    int max = fieldSec.getInt("max", Integer.MAX_VALUE);
                    state.put(key.toLowerCase(Locale.ROOT), new ModifierStateDefinition(key.toLowerCase(Locale.ROOT), def, min, max));
                }
            }

            Map<Integer, ModifierTier> tiers = new HashMap<>();
            ConfigurationSection tiersSec = section.getConfigurationSection("tiers");
            if (tiersSec != null) {
                for (String tierKey : tiersSec.getKeys(false)) {
                    int tierNum;
                    try {
                        tierNum = Integer.parseInt(tierKey);
                    } catch (NumberFormatException e) {
                        return LoadResult.failure("[" + filePath + "] Modifier '" + id + "' has non-numeric tier key '" + tierKey + "'");
                    }
                    ConfigurationSection tierSec = tiersSec.getConfigurationSection(tierKey);
                    if (tierSec == null) continue;

                    String tierName = null;
                    ConfigurationSection tierDisplaySec = tierSec.getConfigurationSection("display");
                    if (tierDisplaySec != null) tierName = tierDisplaySec.getString("name");

                    List<ModifierEffect> tierEffects = ModifierEffectParser.parse(tierSec.getMapList("effects"), mechanicRegistry);
                    tiers.put(tierNum, new ModifierTier(tierNum, tierName, tierEffects));
                }
            }

            List<ModifierEffect> baseEffects = tiers.isEmpty()
                    ? ModifierEffectParser.parse(section.getMapList("effects"), mechanicRegistry)
                    : Collections.emptyList();

            double weight = section.getDouble("weight", 1.0);

            Set<org.nakii.valmora.module.item.ItemType> targetItemTypes = new HashSet<>();
            ConfigurationSection targetsSec = section.getConfigurationSection("targets");
            if (targetsSec != null) {
                for (String typeStr : targetsSec.getStringList("item_types")) {
                    org.nakii.valmora.module.item.ItemType.find(typeStr).ifPresent(targetItemTypes::add);
                }
            }

            return LoadResult.success(new ModifierDefinition(id, groupId.toLowerCase(Locale.ROOT), displayName, prefix, suffix,
                    lore, requirements, tags, conflictIds, conflictTags, state, tiers, baseEffects, weight, targetItemTypes));
        } catch (ModifierEffectParser.EffectParseException e) {
            return LoadResult.failure("[" + filePath + "] Modifier '" + id + "': " + e.getMessage());
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse modifier '" + id + "': " + e.getMessage());
        }
    }
}
