package org.nakii.valmora.module.item;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.item.set.SetBonusRegistry;
import org.nakii.valmora.util.DebugManager;

public class ItemManager implements ReloadableModule {

    private Valmora plugin;
    private ItemRegistry itemRegistry;
    private ItemFactory itemFactory;
    private ItemLoader itemLoader;
    private ItemTranslator itemTranslator;
    private SetBonusRegistry setBonusRegistry;
    private LootListener lootListener;
    private QuiverListener quiverListener;

    public ItemManager(Valmora plugin){
        this.plugin = plugin;
        this.itemFactory = new ItemFactory(plugin);
        this.itemRegistry = new ItemRegistry(itemFactory);
        this.itemLoader = new ItemLoader(plugin, itemRegistry);
        this.itemTranslator = new ItemTranslator(plugin);
        this.setBonusRegistry = new SetBonusRegistry(plugin);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Starting Item Module...");
        ItemTypeLoader.load(plugin);
        itemLoader.loadItems();
        setBonusRegistry.load();

        this.lootListener = new LootListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(lootListener, plugin);

        // Quiver auto-refill (added 2026-08-07) — see guis/quiver.yml / QuiverListener's own doc.
        this.quiverListener = new QuiverListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(quiverListener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Stopping Item Module...");
        itemRegistry.clear();
        setBonusRegistry.clear();
        // Fixed 2026-08-07: lootListener was never unregistered here (a mandatory-per-AGENTS.md
        // §6.2 cleanup step that got missed), risking duplicate handling after /valmora reload.
        if (lootListener != null) { org.bukkit.event.HandlerList.unregisterAll(lootListener); lootListener = null; }
        if (quiverListener != null) { org.bukkit.event.HandlerList.unregisterAll(quiverListener); quiverListener = null; }
    }

    public SetBonusRegistry getSetBonusRegistry() {
        return setBonusRegistry;
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

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public ItemTranslator getItemTranslator() {
        return itemTranslator;
    }

    public ItemStack createItemStack(String id){
        java.util.Optional<ItemStack> customItem = itemRegistry.createItemStack(id);
        if (customItem.isPresent()) {
            DebugManager.log("items", "createItemStack('" + id + "') -> custom item");
            return customItem.get();
        }

        try {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(id);
            if (material == null) {
                DebugManager.log("items", "createItemStack('" + id + "') -> FAILED, no custom item or vanilla material match");
                return null;
            }
            DebugManager.log("items", "createItemStack('" + id + "') -> vanilla material " + material);
            return itemTranslator.translate(new org.bukkit.inventory.ItemStack(material));
        } catch (Exception e) {
            DebugManager.log("items", "createItemStack('" + id + "') -> EXCEPTION " + e);
            return null;
        }
    }
}
