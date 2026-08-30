package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;

/**
 * One built (translated, ready-to-give) output item from a completed craft, paired with the
 * {@code slot} id it should land in — see {@link RecipeOutput} for how that id is declared in
 * YAML. {@code slot == null} means "the machine's sole/first OUTPUT component" (only possible when
 * the recipe declared exactly one {@code outputs:} entry — {@link RecipeDefinitionParser} requires
 * every entry to have a slot once there's more than one).
 */
public record CraftOutput(ItemStack item, String slot) {
}
