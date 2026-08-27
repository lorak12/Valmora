package org.nakii.valmora.module.modifier;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.module.item.ItemType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Parses one entry of a {@code groups:} YAML section (docs/Valmora_Modifier_Framework_Design.docx §6). */
public final class ModifierGroupParser {

    private ModifierGroupParser() {}

    public static LoadResult<ModifierGroupDefinition, String> parse(String id, ConfigurationSection section, String filePath) {
        try {
            DisplayFormat displayFormat = DisplayFormat.NONE;
            int displayOrder = 0;
            ConfigurationSection displaySec = section.getConfigurationSection("display");
            if (displaySec != null) {
                displayFormat = DisplayFormat.valueOf(displaySec.getString("format", "NONE").toUpperCase(Locale.ROOT));
                displayOrder = displaySec.getInt("order", 0);
            }

            ApplicationMode applicationMode = ApplicationMode.MULTIPLE;
            int max = Integer.MAX_VALUE;
            boolean replacement = false;
            boolean removal = true;
            ConfigurationSection appSec = section.getConfigurationSection("application");
            if (appSec != null) {
                applicationMode = ApplicationMode.valueOf(appSec.getString("mode", "MULTIPLE").toUpperCase(Locale.ROOT));
                max = appSec.getInt("max", Integer.MAX_VALUE);
                replacement = appSec.getBoolean("replacement", false);
                removal = appSec.getBoolean("removal", true);
            }

            Set<ItemType> targetTypes = new HashSet<>();
            ConfigurationSection targetsSec = section.getConfigurationSection("targets");
            if (targetsSec != null) {
                for (String typeStr : targetsSec.getStringList("item_types")) {
                    ItemType.find(typeStr).ifPresentOrElse(targetTypes::add, () -> {
                        throw new IllegalArgumentException("Unknown item type '" + typeStr + "'");
                    });
                }
            }

            StorageMode storageMode = StorageMode.STACKED;
            ConfigurationSection storageSec = section.getConfigurationSection("storage");
            if (storageSec != null) {
                storageMode = StorageMode.valueOf(storageSec.getString("mode", "STACKED").toUpperCase(Locale.ROOT));
            }

            TierSource tierSource = TierSource.valueOf(section.getString("tier-source", "INSTANCE").toUpperCase(Locale.ROOT));

            return LoadResult.success(new ModifierGroupDefinition(id, displayFormat, displayOrder,
                    applicationMode, max, replacement, removal, targetTypes, storageMode, tierSource));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse modifier group '" + id + "': " + e.getMessage());
        }
    }
}
