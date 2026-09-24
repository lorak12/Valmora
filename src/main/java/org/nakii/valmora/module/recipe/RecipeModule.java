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
    private final AnvilRecipeRegistry anvilRecipeRegistry = new AnvilRecipeRegistry();
    private RecipeEngine recipeEngine;

    public RecipeModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.recipeEngine = new RecipeEngine(plugin);

        anvilTemplateRegistry.load(plugin); // Phase 4.3 — see docs/REFACTOR/PROGRESS.md
        anvilRecipeRegistry.clear();
        loadAnvilRecipes();
        // Unified anvil (coworker anvil spec) — one machine id for enchant/durability/repair merge,
        // explicit UPGRADE/TRANSMUTE recipes, AND (delegated) modifier-recipe application. Depends
        // on ModifierModule's ModifierAnvilHandler, which registers after recipe/machine — see
        // AnvilMachineHandler.matchModifierRecipe, which looks it up lazily via
        // plugin.getModifierModule() at match time rather than at registration time.
        registerHandler("anvil", new AnvilMachineHandler(plugin, anvilTemplateRegistry, anvilRecipeRegistry));

        loadRecipes();
    }

    private void loadAnvilRecipes() {
        YamlLoader<AnvilRecipeDefinition> loader = new YamlLoader<>(plugin, "recipes/anvil", "Anvil Recipe");
        loader.load(AnvilRecipeParser::parse, recipe -> {
            if (recipe != null) anvilRecipeRegistry.register(recipe);
        });
    }

    public RecipeEngine getRecipeEngine() {
        return recipeEngine;
    }

    public void registerHandler(String machineId, DynamicMachineHandler handler) {
        if (recipeEngine != null) {
            recipeEngine.registerHandler(machineId, handler);
        }
    }

    public void unregisterHandler(String machineId) {
        if (recipeEngine != null) {
            recipeEngine.unregisterHandler(machineId);
        }
    }

    @Override
    public void onDisable() {
        RecipeDefinitionParser.unregisterSmithingRecipes(plugin.getServer());
        machineRecipes.clear();
        anvilTemplateRegistry.clear();
    }

    private void loadRecipes() {
        machineRecipes.clear();
        // recipes/anvil/ holds UPGRADE/TRANSMUTE recipes in their own format (loadAnvilRecipes);
        // skip the folder here instead of parsing each of them a second time into a marker entry.
        java.io.File anvilDir = new java.io.File(plugin.getDataFolder(), "recipes/anvil");
        YamlLoader<RecipeDefinition> loader = new YamlLoader<RecipeDefinition>(plugin, "recipes", "Recipe")
                .skipDirectories(anvilDir::equals);
        RecipeDefinitionParser parser = new RecipeDefinitionParser(plugin);
        loader.load(parser::parse, recipe -> {
            // A machine-less marker (e.g. the SMITHING vanilla-recipe registration path in
            // RecipeDefinitionParser, which registers directly with Bukkit and returns a marker
            // only so the generic loader has something non-null to report success with) — skip
            // rather than register it under a null machine key.
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
