package org.nakii.valmora.module.mob;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.infrastructure.config.YamlLoader;

public class MobLoader {
    
    private final Valmora plugin;
    private final MobRegistry registry;
    private final YamlLoader<MobDefinition> loader;

    public MobLoader(Valmora plugin, MobRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.loader = new YamlLoader<>(plugin, "mobs", "mobs");
    }

    public void loadMobs() {
        registry.clear();
        loader.load(
            (id, section, filePath) -> MobDefinitionParser.parse(id, section, filePath, plugin.getItemManager()),
            registry::registerMob
        );
    }
}
