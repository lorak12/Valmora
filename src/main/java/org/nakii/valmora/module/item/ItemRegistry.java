package org.nakii.valmora.module.item;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.registry.SimpleRegistry;

import java.util.Optional;
import java.util.Set;

public class ItemRegistry extends SimpleRegistry<ItemDefinition> {

    private final ItemFactory factory;

    public ItemRegistry(ItemFactory factory) {
        this.factory = factory;
    }

    public void registerItem(ItemDefinition definition) {
        register(definition.getId(), definition);
    }

    public Optional<ItemDefinition> getItem(String id) {
        return get(id);
    }

    public Optional<ItemStack> createItemStack(String id) {
        return getItem(id).map(factory::create);
    }

    public Set<String> getAllItemIds() {
        return getKeys();
    }

    public int getItemCount(){
        return size();
    }
}
