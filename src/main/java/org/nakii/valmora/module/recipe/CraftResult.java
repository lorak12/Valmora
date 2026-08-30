package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.scripting.CompiledEvent;

import java.util.List;

/**
 * @param outputs every built {@code outputs:} entry, each paired with the OUTPUT-component slot id
 *                 it targets (see {@link CraftOutput}/{@link RecipeOutput}) — routed to the right
 *                 physical GUI slot by the caller ({@code GuiForceCraftEventFactory}); a slotted
 *                 entry naming an id the current GUI doesn't have falls back to the player's
 *                 inventory rather than being silently dropped.
 */
public record CraftResult(List<CraftOutput> outputs, RecipeDefinition recipe, CompiledEvent onCraft) {
    public CraftResult(ItemStack singleOutput, RecipeDefinition recipe, CompiledEvent onCraft) {
        this(List.of(new CraftOutput(singleOutput, null)), recipe, onCraft);
    }

    /** The first built output — the common case (a single-output recipe) needs nothing more. */
    public ItemStack output() {
        return outputs.isEmpty() ? null : outputs.get(0).item();
    }
}
