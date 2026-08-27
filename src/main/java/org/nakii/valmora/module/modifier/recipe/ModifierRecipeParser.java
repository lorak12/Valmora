package org.nakii.valmora.module.modifier.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.modifier.value.LiteralValue;
import org.nakii.valmora.module.modifier.value.ValueParser;
import org.nakii.valmora.module.modifier.value.ValueResolver;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Parses one entry of a {@code modifiers/recipes/*.yml} file (docs/Valmora_Modifier_Framework_Design.docx §16). */
public final class ModifierRecipeParser {

    private ModifierRecipeParser() {}

    public static LoadResult<ModifierRecipeDefinition, String> parse(String id, ConfigurationSection section, String filePath) {
        try {
            String machine = section.getString("machine", "custom_anvil");
            ModifierRecipeDefinition.Operation operation = ModifierRecipeDefinition.Operation.valueOf(
                    section.getString("operation", "APPLY_MODIFIER").toUpperCase(Locale.ROOT));

            Set<ItemType> baseTypes = new HashSet<>();
            ConfigurationSection baseSec = section.getConfigurationSection("base");
            if (baseSec != null) {
                for (String typeStr : baseSec.getStringList("item_types")) {
                    ItemType.find(typeStr).ifPresent(baseTypes::add);
                }
            }

            String additionItemId = null;
            int additionAmount = 1;
            ConfigurationSection additionSec = section.getConfigurationSection("addition");
            if (additionSec != null) {
                additionItemId = additionSec.getString("item");
                additionAmount = additionSec.getInt("amount", 1);
            }

            ConfigurationSection modifierSec = section.getConfigurationSection("modifier");
            if (modifierSec == null || !modifierSec.contains("group")) {
                return LoadResult.failure("[" + filePath + "] Modifier recipe '" + id + "' is missing required 'modifier.group'");
            }
            String group = modifierSec.getString("group");
            String modifierId = modifierSec.getString("id"); // may be null (REMOVE_MODIFIER wildcard)
            int tier = modifierSec.getInt("tier", 1);

            if (operation == ModifierRecipeDefinition.Operation.APPLY_MODIFIER && modifierId == null) {
                return LoadResult.failure("[" + filePath + "] APPLY_MODIFIER recipe '" + id + "' requires 'modifier.id'");
            }

            ValueResolver xpLevels = new LiteralValue(0);
            ValueResolver coins = new LiteralValue(0);
            ConfigurationSection costSec = section.getConfigurationSection("cost");
            if (costSec != null) {
                if (costSec.contains("xp_levels")) xpLevels = ValueParser.parse(costSec.get("xp_levels"));
                if (costSec.contains("coins")) coins = ValueParser.parse(costSec.get("coins"));
            }

            int priority = section.getInt("priority", 0);

            return LoadResult.success(new ModifierRecipeDefinition(id, machine, operation, baseTypes,
                    additionItemId, additionAmount, group, modifierId, tier, xpLevels, coins, priority));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse modifier recipe '" + id + "': " + e.getMessage());
        }
    }
}
