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
 * against the rarity registry and stat/recipe systems, and registers the {@code custom_anvil}
 * {@code DynamicMachineHandler} for {@code APPLY_MODIFIER}/{@code REMOVE_MODIFIER} recipes (§16).
 *
 * <p>Registered after {@code recipe} (needs to register a handler) and {@code rarity} (needs
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
        ModifierValidator.validate(groupRegistry, modifierRegistry, recipeRegistry, plugin.getItemManager(), plugin.getLogger());

        // No provider registration here: $item.*$ is served by ItemAbilityVariableProvider
        // (registered once by ScriptModule) — see that class's javadoc for why a second "item"
        // provider from this module would silently clobber it (SimpleRegistry is a flat map keyed
        // by namespace, last registration wins).

        if (plugin.getRecipeModule() != null) {
            plugin.getRecipeModule().getRecipeEngine()
                    .registerHandler("custom_anvil", new ModifierAnvilHandler(plugin, recipeRegistry, engine));
        }
    }

    @Override
    public void onDisable() {
        if (plugin.getRecipeModule() != null) {
            plugin.getRecipeModule().unregisterHandler("custom_anvil");
        }
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
