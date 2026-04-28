package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;
import java.util.Map;
import java.util.Optional;

public interface DynamicMachineHandler {
    Optional<RecipeDefinition> match(Map<String, ItemStack> inputs);
}
