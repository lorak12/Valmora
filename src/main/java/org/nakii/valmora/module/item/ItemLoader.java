package org.nakii.valmora.module.item;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.infrastructure.config.YamlLoader;

public class ItemLoader {
    
    private final Valmora plugin;
    private final ItemRegistry registry;
    private final YamlLoader<ItemDefinition> loader;

    public ItemLoader(Valmora plugin, ItemRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.loader = new YamlLoader<>(plugin, "items", "items");
    }

    public void loadItems() {
        registry.clear();
        loader.load(
            (id, section, filePath) -> ItemDefinitionParser.parse(id, section, filePath, plugin.getAbilityManager().getMechanicRegistry()),
            registry::registerItem
        );
    }
}
