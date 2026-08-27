package org.nakii.valmora.module.modifier.recipe;

import java.util.ArrayList;
import java.util.List;

public class ModifierRecipeRegistry {

    private final List<ModifierRecipeDefinition> recipes = new ArrayList<>();

    public void register(ModifierRecipeDefinition recipe) {
        recipes.add(recipe);
    }

    public List<ModifierRecipeDefinition> values() {
        return recipes;
    }

    public void clear() {
        recipes.clear();
    }
}
