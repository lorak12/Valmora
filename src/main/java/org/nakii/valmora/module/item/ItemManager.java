package org.nakii.valmora.module.item;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class ItemManager implements ReloadableModule {

    private Valmora plugin;
    private ItemRegistry itemRegistry;
    private ItemFactory itemFactory;
    private ItemLoader itemLoader;

    public ItemManager(Valmora plugin){
        this.plugin = plugin;
        this.itemFactory = new ItemFactory(plugin);
        this.itemRegistry = new ItemRegistry(itemFactory);
        this.itemLoader = new ItemLoader(plugin, itemRegistry);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Starting Item Module...");
        itemLoader.loadItems();
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Stopping Item Module...");
        itemRegistry.clear();
    }

    @Override
    public String getId() {
        return "items";
    }

    @Override
    public String getName() {
        return "Item Engine";
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public ItemStack createItemStack(String id){
        java.util.Optional<ItemStack> customItem = itemRegistry.createItemStack(id);
        if (customItem.isPresent()) {
            return customItem.get();
        }

        try {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(id);
            if (material == null) {
                return null;
            }
            ItemDefinition vanillaDef = new ItemDefinition.Builder(id)
                    .material(material)
                    .rarity(Rarity.COMMON)
                    .build();
            return itemFactory.create(vanillaDef);
        } catch (Exception e) {
            return null;
        }
    }
}
