package org.nakii.valmora.module.collection;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CollectionDefinitionParser {

    public static CollectionCategory parseCategory(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String icon = section.getString("icon", "CHEST").toUpperCase();
        String description = section.getString("description", "");
        return new CollectionCategory(id, name, icon, description);
    }

    public static CollectionDefinition parseCollection(String id, ConfigurationSection section) {
        String categoryId = section.getString("category", "misc");
        String name = section.getString("name", id);
        String icon = section.getString("icon", "STONE").toUpperCase();

        List<String> trackSources = section.getStringList("track");

        List<CollectionStage> stages = new ArrayList<>();
        ConfigurationSection stagesSection = section.getConfigurationSection("stages");
        if (stagesSection != null) {
            for (String key : stagesSection.getKeys(false)) {
                int stageNumber;
                try {
                    stageNumber = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                ConfigurationSection stageSection = stagesSection.getConfigurationSection(key);
                if (stageSection == null) continue;

                long required = stageSection.getLong("required", 0);
                List<String> rewards = stageSection.getStringList("rewards");
                stages.add(new CollectionStage(stageNumber, required, rewards));
            }
        }
        stages.sort(Comparator.comparingInt(CollectionStage::getNumber));

        return new CollectionDefinition(id, categoryId, name, icon, trackSources, stages);
    }
}
