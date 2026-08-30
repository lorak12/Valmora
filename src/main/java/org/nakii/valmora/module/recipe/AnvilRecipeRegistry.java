package org.nakii.valmora.module.recipe;

import java.util.ArrayList;
import java.util.List;

/** Ordered list of {@link AnvilRecipeDefinition}s — first match wins, load order (no priority
 *  field needed today since UPGRADE/TRANSMUTE recipes are expected to have disjoint base/addition
 *  pairs; add one later if that stops being true, mirroring {@code ModifierRecipeDefinition}). */
public class AnvilRecipeRegistry {

    private final List<AnvilRecipeDefinition> recipes = new ArrayList<>();

    public void register(AnvilRecipeDefinition recipe) {
        recipes.add(recipe);
    }

    public List<AnvilRecipeDefinition> values() {
        return recipes;
    }

    public void clear() {
        recipes.clear();
    }
}
