package org.nakii.valmora.item;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;

public class ItemManager {

    private Valmora plugin;
    private ItemRegistry itemRegistry;
    private ItemFactory itemFactory;
    private ItemLoader itemLoader;

    public ItemManager(Valmora plugin){
        this.plugin = plugin;
        this.itemFactory = new ItemFactory(plugin);
        this.itemRegistry = ItemRegistry.getInstance(itemFactory);
        this.itemLoader = new ItemLoader(plugin, itemRegistry);
    }

    public void initialize(){
        plugin.getLogger().info("Initializing Item System...");

        registerAllItems();

        plugin.getLogger().info("Item System initialized with " + itemRegistry.getItemCount() + " items");
    }

    private void registerAllItems(){
        itemLoader.loadItems();
    }

    public void reload(){
        itemRegistry.clear();
        registerAllItems();
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
