package org.nakii.valmora.module.modifier;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.modifier.recipe.ModifierAnvilHandler;
import org.nakii.valmora.module.modifier.recipe.ModifierRecipeDefinition;
import org.nakii.valmora.module.modifier.recipe.ModifierRecipeParser;
import org.nakii.valmora.module.modifier.recipe.ModifierRecipeRegistry;

/**
 * The generic modifier framework module (docs/Valmora_Modifier_Framework_Design.docx). Loads
 * modifier groups + definitions from {@code modifiers/} content, wires the {@link ModifierEngine}
 * against the rarity registry and stat/recipe systems, and builds a {@link ModifierAnvilHandler}
 * for {@code APPLY_MODIFIER}/{@code REMOVE_MODIFIER} recipes (§16) — exposed via
 * {@link #getAnvilHandler()} for the unified anvil ({@code module.recipe.AnvilMachineHandler}) to
 * call directly, rather than self-registered as a separate {@code DynamicMachineHandler}.
 *
 * <p>Registered after {@code recipe}/{@code machine} and {@code rarity} (needs
 * {@link org.nakii.valmora.module.rarity.RarityRegistry}) — see Valmora.java's module order comment.
 *
 * <p><b>Note on YAML shape:</b> the design doc's illustrative snippets wrap group/modifier entries
 * under a {@code groups:}/{@code modifiers:} root key. This codebase's {@link YamlLoader} convention
 * (matching {@code ReforgeModule}'s existing {@code reforges/*.yml}) instead treats each top-level
 * key in a file as one entity id directly — so shipped content here drops that wrapper key.
 */
public class ModifierModule implements ReloadableModule {

    private final Valmora plugin;
    private final ModifierGroupRegistry groupRegistry = new ModifierGroupRegistry();
    private final ModifierRegistry modifierRegistry = new ModifierRegistry();
    private final ModifierRecipeRegistry recipeRegistry = new ModifierRecipeRegistry();
    private ModifierComponentStore store;
    private ModifierEngine engine;
    private ModifierAnvilHandler anvilHandler;

    public ModifierModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        groupRegistry.clear();
        modifierRegistry.clear();
        recipeRegistry.clear();

        store = new ModifierComponentStore(plugin);
        var rarityModule = plugin.getRarityModule();
        engine = new ModifierEngine(groupRegistry, modifierRegistry, store,
                rarityModule != null ? rarityModule.getRegistry() : new org.nakii.valmora.module.rarity.RarityRegistry());

        loadGroups();
        loadModifiers();
        loadRecipes();
        // Cross-checks (groups, conflicts, recipe items, state keys) run once everything has loaded
        // and land in the load report — see ReferenceValidator.
        org.nakii.valmora.infrastructure.config.refs.ReferenceValidator.global().register(
                new org.nakii.valmora.infrastructure.config.refs.ReferenceCheck() {
                    @Override public String name() { return "modifiers"; }
                    @Override public void check(org.nakii.valmora.infrastructure.config.refs.ReferenceContext ctx) {
                        for (String w : ModifierValidator.collect(groupRegistry, modifierRegistry, recipeRegistry, plugin.getItemManager())) {
                            ctx.warn("Modifiers", null, null, w, null);
                        }
                    }
                });

        // No provider registration here: $item.*$ is served by ItemAbilityVariableProvider
        // (registered once by ScriptModule) — see that class's javadoc for why a second "item"
        // provider from this module would silently clobber it (SimpleRegistry is a flat map keyed
        // by namespace, last registration wins).

        // Not registered on RecipeEngine as its own machine handler anymore — the unified anvil
        // (module/recipe/AnvilMachineHandler, machine id "anvil") delegates to this instance
        // directly as one step of its pipeline instead of RecipeEngine dispatching to it under a
        // separate "custom_anvil" machine id. See docs/modules/design/modifier.md §4.
        this.anvilHandler = new ModifierAnvilHandler(plugin, recipeRegistry, engine);
    }

    @Override
    public void onDisable() {
        anvilHandler = null;
        groupRegistry.clear();
        modifierRegistry.clear();
        recipeRegistry.clear();
    }

    @Override
    public String getId() { return "modifier"; }

    @Override
    public String getName() { return "Modifier Framework"; }

    public ModifierGroupRegistry getGroupRegistry() { return groupRegistry; }
    public ModifierRegistry getModifierRegistry() { return modifierRegistry; }
    public ModifierComponentStore getStore() { return store; }
    public ModifierEngine getEngine() { return engine; }
    public ModifierAnvilHandler getAnvilHandler() { return anvilHandler; }

    private void loadGroups() {
        YamlLoader<ModifierGroupDefinition> loader = new YamlLoader<>(plugin, "modifiers/groups", "Modifier Group");
        loader.load(ModifierGroupParser::parse, groupRegistry::register);
    }

    private void loadModifiers() {
        var mechanicRegistry = plugin.getAbilityManager() != null ? plugin.getAbilityManager().getMechanicRegistry() : null;
        YamlLoader<ModifierDefinition> loader = new YamlLoader<>(plugin, "modifiers/definitions", "Modifier");
        loader.load((id, section, filePath) -> ModifierDefinitionParser.parse(id, section, filePath, mechanicRegistry),
                modifierRegistry::register);
    }

    private void loadRecipes() {
        YamlLoader<ModifierRecipeDefinition> loader = new YamlLoader<>(plugin, "modifiers/recipes", "Modifier Recipe");
        loader.load(ModifierRecipeParser::parse, recipeRegistry::register);
    }
}
