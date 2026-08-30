package org.nakii.valmora.module.recipe;

/**
 * One entry of a recipe's {@code outputs:} list — {@code slot} is the id of the GUI's
 * {@code OUTPUT} component this item goes into (e.g. {@code slot: byproduct}), or {@code null} to
 * fall back to the machine's sole/first OUTPUT component.
 *
 * <p>{@code null} is only valid when the recipe has exactly one {@code outputs:} entry —
 * {@link RecipeDefinitionParser} rejects a recipe with more than one output where any entry omits
 * {@code slot:}, since which physical slot an unslotted item lands in would otherwise depend on
 * layout-scan order rather than anything the recipe author actually chose. See CLAUDE.md's recipe
 * multi-output routing convention.
 */
public record RecipeOutput(RecipeIngredient ingredient, String slot) {
}
