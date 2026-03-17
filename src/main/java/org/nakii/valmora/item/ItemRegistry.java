package org.nakii.valmora.item;

import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ItemRegistry {

    private final Map<String, ItemDefinition> registry = new HashMap<>();
    private final ItemFactory factory;

    private static ItemRegistry instance;

    private ItemRegistry(ItemFactory factory) {
        this.factory = factory;
    }

    public static ItemRegistry getInstance(ItemFactory factory) {
        if (instance == null) {
            synchronized (ItemRegistry.class) {
                if (instance == null) {
                    instance = new ItemRegistry(factory);
                }
            }
        }
        return instance;
    }

    public void registerItem(ItemDefinition definition) {
        registry.put(definition.getId().toLowerCase(), definition);
    }

    public Optional<ItemDefinition> getItem(String id) {
        return Optional.ofNullable(registry.get(id.toLowerCase()));
    }

    public Optional<ItemStack> createItemStack(String id) {
        return getItem(id).map(factory::create);
    }

    public void clear() {
        registry.clear();
    }

    public int getItemCount(){
        return registry.size();
    }

    public Set<String> getAllItemIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }
}
