package org.nakii.valmora.module.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.machine.MachineDefinition;
import org.nakii.valmora.module.machine.MachineModule;
import org.nakii.valmora.module.machine.MachineRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A SHAPED pattern narrower than its machine's grid must slide to every column it fits in —
 * previously matching numbered the machine's slots with the pattern's own width, so a 1-wide
 * column pattern on a 3x3 machine never matched at all.
 */
@Tag("mockbukkit")
public class RecipeEngineShapedTest {

    private static final String MACHINE = "test_table";

    private RecipeEngine engine;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        MachineRegistry machines = new MachineRegistry();
        machines.register(new MachineDefinition(MACHINE, MACHINE, "custom", 9, 1,
                new MachineDefinition.Shape(3, 3), List.of()));
        MachineModule machineModule = mock(MachineModule.class);
        when(machineModule.getRegistry()).thenReturn(machines);

        // A 1-wide, 3-tall column: ice / diamond / stick, laid out on the pattern's own width (1).
        Map<String, RecipeIngredient> column = new HashMap<>();
        column.put("0", new RecipeIngredient("BLUE_ICE", 1));
        column.put("1", new RecipeIngredient("DIAMOND", 1));
        column.put("2", new RecipeIngredient("STICK", 1));
        RecipeDefinition recipe = new RecipeDefinition("column", MACHINE, RecipeType.SHAPED, column, null,
                List.of(new RecipeOutput(new RecipeIngredient("DIAMOND_SWORD", 1), null)), null, 1, true, null);

        RecipeModule recipeModule = mock(RecipeModule.class);
        when(recipeModule.getRecipesForMachine(MACHINE)).thenReturn(List.of(recipe));

        Valmora plugin = mock(Valmora.class);
        when(plugin.getMachineModule()).thenReturn(machineModule);
        when(plugin.getRecipeModule()).thenReturn(recipeModule);
        engine = new RecipeEngine(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Map<String, ItemStack> column(int col) {
        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put(String.valueOf(col), new ItemStack(Material.BLUE_ICE));
        inputs.put(String.valueOf(3 + col), new ItemStack(Material.DIAMOND));
        inputs.put(String.valueOf(6 + col), new ItemStack(Material.STICK));
        return inputs;
    }

    @Test
    void narrowPatternMatchesInEveryColumn() {
        for (int col = 0; col < 3; col++) {
            assertTrue(engine.match(MACHINE, column(col)).isPresent(), "column " + col);
        }
    }

    @Test
    void itemsSpreadAcrossColumnsDoNotMatch() {
        Map<String, ItemStack> diagonal = new HashMap<>();
        diagonal.put("0", new ItemStack(Material.BLUE_ICE));
        diagonal.put("4", new ItemStack(Material.DIAMOND));
        diagonal.put("8", new ItemStack(Material.STICK));
        assertTrue(engine.match(MACHINE, diagonal).isEmpty());
    }
}
