package org.nakii.valmora.module.skill;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.infrastructure.config.YamlLoader;

public class SkillLoader {
    
    private final Valmora plugin;
    private final SkillRegistry registry;
    private final YamlLoader<SkillDefinition> loader;

    public SkillLoader(Valmora plugin, SkillRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        // Loads all .yml files from plugins/Valmora/skills/
        this.loader = new YamlLoader<>(plugin, "skills", "skills");
    }

    public void loadSkills() {
        registry.clear();
        loader.loadFilesAsSections(
            (id, section, filePath) -> SkillDefinitionParser.parse(id, section, filePath, plugin),
            registry::registerSkill
        );
    }
}