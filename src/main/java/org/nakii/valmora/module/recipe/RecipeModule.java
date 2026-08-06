package org.nakii.valmora.module.recipe;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeModule implements ReloadableModule {

    private final Valmora plugin;
    private final Map<String, List<RecipeDefinition>> machineRecipes = new HashMap<>();
    private final AnvilTemplateRegistry anvilTemplateRegistry = new AnvilTemplateRegistry();
    private RecipeEngine recipeEngine;

    public RecipeModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.recipeEngine = new RecipeEngine(plugin);

        // Register Dynamic Handlers
        anvilTemplateRegistry.load(plugin); // Phase 4.3 — see docs/REFACTOR/PROGRESS.md
        registerHandler("anvil", new AnvilMachineHandler(plugin, anvilTemplateRegistry));

        loadRecipes();
    }

    public RecipeEngine getRecipeEngine() {
        return recipeEngine;
    }

    public void registerHandler(String machineId, DynamicMachineHandler handler) {
        if (recipeEngine != null) {
            recipeEngine.registerHandler(machineId, handler);
        }
    }

    @Override
    public void onDisable() {
        machineRecipes.clear();
        anvilTemplateRegistry.clear();
    }

    private void loadRecipes() {
        machineRecipes.clear();
        YamlLoader<RecipeDefinition> loader = new YamlLoader<>(plugin, "recipes", "Recipe");
        RecipeDefinitionParser parser = new RecipeDefinitionParser(plugin);
        loader.load(parser::parse, recipe -> {
            // recipes/anvil_templates.yml lives in this same folder (server-wide config, not a
            // recipe) and parses harmlessly through the same generic loader with machine == null
            // — skip rather than register it under a null machine key.
            if (recipe.getMachine() == null) return;
            machineRecipes.computeIfAbsent(recipe.getMachine(), k -> new ArrayList<>()).add(recipe);
        });
    }

    @Override
    public String getId() {
        return "recipe";
    }

    @Override
    public String getName() {
        return "Recipe System";
    }

    public List<RecipeDefinition> getRecipesForMachine(String machineId) {
        return machineRecipes.getOrDefault(machineId, new ArrayList<>());
    }
}
