package org.nakii.valmora.module.recipe;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public interface DynamicMachineHandler {

    Optional<RecipeDefinition> match(Map<String, ItemStack> inputs);

    /** Override to receive the crafting player for context-sensitive recipes (e.g. skill bonuses). */
    default Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
        return match(inputs);
    }
}
