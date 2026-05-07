package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.scripting.CompiledEvent;

public record CraftResult(ItemStack output, RecipeDefinition recipe, CompiledEvent onCraft) {
}
