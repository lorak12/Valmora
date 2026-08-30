package org.nakii.valmora.module.recipe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link RecipeCommand}, including the new {@code preview} subcommand added per
 *  docs/IMPLEMENTATION_BACKLOG.md's "/recipe (already has list, still no preview/inspect-by-id)" note. */
public class RecipeCommandTest {

    private Valmora plugin;
    private RecipeModule recipeModule;
    private RecipeCommand command;
    private CommandSender sender;
    private Command bukkitCommand;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        recipeModule = mock(RecipeModule.class);
        sender = mock(CommandSender.class);
        bukkitCommand = mock(Command.class);

        when(plugin.getRecipeModule()).thenReturn(recipeModule);

        command = new RecipeCommand(plugin);
    }

    private RecipeDefinition definition(String id) {
        Map<String, RecipeIngredient> inputs = new HashMap<>();
        inputs.put("base", new RecipeIngredient("NETHER_WART", 1));
        List<RecipeOutput> outputs = List.of(new RecipeOutput(new RecipeIngredient("valmora:elixir", 1), null));
        return new RecipeDefinition(id, "alchemy", RecipeType.EXACT_SLOT, inputs, null, outputs, ctx -> {});
    }

    private String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void noArgsShowsUsage() {
        command.onCommand(sender, bukkitCommand, "recipe", new String[]{});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
        verify(recipeModule, never()).getRecipesForMachine(anyString());
    }

    @Test
    void listMissingArgReportsUsage() {
        command.onCommand(sender, bukkitCommand, "recipe", new String[]{"list"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Usage:")));
    }

    @Test
    void listShowsRecipesForMachine() {
        when(recipeModule.getRecipesForMachine("alchemy")).thenReturn(List.of(definition("elixir_recipe")));
        command.onCommand(sender, bukkitCommand, "recipe", new String[]{"list", "alchemy"});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void previewMissingArgsReportsUsage() {
        command.onCommand(sender, bukkitCommand, "recipe", new String[]{"preview", "alchemy"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("Usage:")));
    }

    @Test
    void previewUnknownRecipeReportsError() {
        when(recipeModule.getRecipesForMachine("alchemy")).thenReturn(List.of());
        command.onCommand(sender, bukkitCommand, "recipe", new String[]{"preview", "alchemy", "nope"});
        verify(sender).sendMessage(argThat((Component c) -> plain(c).contains("No recipe")));
    }

    @Test
    void previewKnownRecipeShowsDetail() {
        RecipeDefinition def = definition("elixir_recipe");
        when(recipeModule.getRecipesForMachine("alchemy")).thenReturn(List.of(def));
        command.onCommand(sender, bukkitCommand, "recipe", new String[]{"preview", "alchemy", "elixir_recipe"});
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void tabCompleteSuggestsSubcommands() {
        List<String> result = command.onTabComplete(sender, bukkitCommand, "recipe", new String[]{"p"});
        assertTrue(result.contains("preview"));
        assertFalse(result.contains("list"));
    }

    @Test
    void tabCompleteSuggestsRecipeIdsForPreviewThirdArg() {
        when(recipeModule.getRecipesForMachine("alchemy")).thenReturn(List.of(definition("elixir_recipe")));
        List<String> result = command.onTabComplete(sender, bukkitCommand, "recipe", new String[]{"preview", "alchemy", "eli"});
        assertEquals(List.of("elixir_recipe"), result);
    }
}
