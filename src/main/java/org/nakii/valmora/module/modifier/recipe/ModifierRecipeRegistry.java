package org.nakii.valmora.module.modifier.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModifierRecipeRegistry {

    private final List<ModifierRecipeDefinition> recipes = new ArrayList<>();

    public void register(ModifierRecipeDefinition recipe) {
        recipes.add(recipe);
    }

    /**
     * Recipes in match order: descending {@code priority} first (stable sort, so recipes with the
     * same priority — the default for all existing content — keep load order relative to each other,
     * same as before this field existed). See {@code ModifierRecipeDefinition#getPriority}.
     */
    public List<ModifierRecipeDefinition> values() {
        List<ModifierRecipeDefinition> sorted = new ArrayList<>(recipes);
        sorted.sort(Comparator.comparingInt(ModifierRecipeDefinition::getPriority).reversed());
        return sorted;
    }

    public void clear() {
        recipes.clear();
    }
}
