package org.nakii.valmora.util;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.List;

/**
 * Bridges Valmora's single-integer {@code custom-model-data} YAML value to the 1.21.4+
 * {@code minecraft:custom_model_data} component. Writes exactly what the deprecated
 * {@code ItemMeta#setCustomModelData(Integer)} did — a one-element {@code floats} list — so
 * resource packs keyed on the old integer keep matching.
 */
public final class CustomModelData {

    private CustomModelData() {}

    public static void set(ItemMeta meta, int value) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) value));
        meta.setCustomModelDataComponent(component);
    }
}
