package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.scripting.CompiledEvent;

import java.util.List;

/**
 * @param output       the primary output, placed in the GUI's OUTPUT slot (unchanged behavior)
 * @param extraOutputs any additional {@code outputs:} entries beyond the first — previously
 *                     silently dropped, since a recipe definition could declare multiple named
 *                     outputs but only the first was ever built. Given directly to the player's
 *                     inventory by the caller (see {@code GuiForceCraftEventFactory}), since the
 *                     GUI has only one OUTPUT slot to place items into.
 */
public record CraftResult(ItemStack output, List<ItemStack> extraOutputs, RecipeDefinition recipe, CompiledEvent onCraft) {
    public CraftResult(ItemStack output, RecipeDefinition recipe, CompiledEvent onCraft) {
        this(output, List.of(), recipe, onCraft);
    }
}
