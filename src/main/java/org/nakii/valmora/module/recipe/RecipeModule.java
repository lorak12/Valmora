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
    private RecipeEngine recipeEngine;

    public RecipeModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.recipeEngine = new RecipeEngine(plugin);
        
        // Register Dynamic Handlers
        registerHandler("anvil", new AnvilMachineHandler(plugin));
        
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
    }

    private void loadRecipes() {
        machineRecipes.clear();
        YamlLoader<RecipeDefinition> loader = new YamlLoader<>(plugin, "recipes", "Recipe");
        RecipeDefinitionParser parser = new RecipeDefinitionParser(plugin);
        loader.load(parser::parse, recipe -> {
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
