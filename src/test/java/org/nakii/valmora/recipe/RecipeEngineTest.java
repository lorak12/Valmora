package org.nakii.valmora.recipe;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.recipe.*;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("recipe")
class RecipeEngineTest {

    private Valmora plugin;
    private RecipeModule recipeModule;
    private RecipeEngine engine;

    @BeforeEach
    void setUp() {
        plugin = mock(Valmora.class);
        recipeModule = mock(RecipeModule.class);
        when(plugin.getRecipeModule()).thenReturn(recipeModule);
        engine = new RecipeEngine(plugin);
    }

    /** Creates a mock ItemStack with a Valmora item ID in its PDC. */
    private ItemStack item(String valmoraId, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.getType()).thenReturn(org.bukkit.Material.STONE); // non-AIR
        when(stack.hasItemMeta()).thenReturn(true);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING)).thenReturn(valmoraId);
        // Precompute the clone before entering when() to avoid UnfinishedStubbingException
        ItemStack cloneResult = item_noClone(valmoraId, amount);
        when(stack.clone()).thenReturn(cloneResult);
        return stack;
    }

    /** Same as item() but without setting up clone() to avoid infinite recursion. */
    private ItemStack item_noClone(String valmoraId, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.getType()).thenReturn(org.bukkit.Material.STONE);
        when(stack.hasItemMeta()).thenReturn(true);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING)).thenReturn(valmoraId);
        return stack;
    }

    /** Mock Bukkit to prevent NPE in matchVanillaRecipe (numeric-keyed inputs only). */
    private void withBukkitVanillaNoMatch(Runnable action) {
        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            Server server = mock(Server.class);
            World world = mock(World.class);
            mockedBukkit.when(Bukkit::getServer).thenReturn(server);
            when(plugin.getServer()).thenReturn(server);
            when(server.getWorlds()).thenReturn(List.of(world));
            mockedBukkit.when(() -> Bukkit.getCraftingRecipe(any(), any())).thenReturn(null);
            action.run();
        }
    }

    // ── EXACT_SLOT ────────────────────────────────────────────────────────

    @Test
    void testExactSlot_correctSlots_matches() {
        Map<String, RecipeIngredient> inputMap = Map.of(
                "input1", new RecipeIngredient("iron_ingot", 2),
                "input2", new RecipeIngredient("diamond", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r1", "forge", RecipeType.EXACT_SLOT,
                inputMap, null, Map.of(), null);
        when(recipeModule.getRecipesForMachine("forge")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("input1", item("iron_ingot", 2));
        inputs.put("input2", item("diamond", 1));

        Optional<RecipeDefinition> result = engine.match("forge", inputs);
        assertTrue(result.isPresent());
    }

    @Test
    void testExactSlot_missingSlot_noMatch() {
        Map<String, RecipeIngredient> inputMap = Map.of(
                "input1", new RecipeIngredient("iron_ingot", 2),
                "input2", new RecipeIngredient("diamond", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r1", "forge", RecipeType.EXACT_SLOT,
                inputMap, null, Map.of(), null);
        when(recipeModule.getRecipesForMachine("forge")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("input1", item("iron_ingot", 2));
        // input2 is absent

        Optional<RecipeDefinition> result = engine.match("forge", inputs);
        assertTrue(result.isEmpty());
    }

    @Test
    void testExactSlot_insufficientAmount_noMatch() {
        Map<String, RecipeIngredient> inputMap = Map.of(
                "input1", new RecipeIngredient("iron_ingot", 3)
        );
        RecipeDefinition recipe = new RecipeDefinition("r1", "forge", RecipeType.EXACT_SLOT,
                inputMap, null, Map.of(), null);
        when(recipeModule.getRecipesForMachine("forge")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("input1", item("iron_ingot", 2)); // only 2, need 3

        Optional<RecipeDefinition> result = engine.match("forge", inputs);
        assertTrue(result.isEmpty());
    }

    @Test
    void testExactSlot_wrongItemId_noMatch() {
        Map<String, RecipeIngredient> inputMap = Map.of(
                "input1", new RecipeIngredient("iron_ingot", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r1", "forge", RecipeType.EXACT_SLOT,
                inputMap, null, Map.of(), null);
        when(recipeModule.getRecipesForMachine("forge")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("input1", item("diamond", 1)); // wrong item

        Optional<RecipeDefinition> result = engine.match("forge", inputs);
        assertTrue(result.isEmpty());
    }

    // ── SHAPELESS ─────────────────────────────────────────────────────────

    @Test
    void testShapeless_correctIngredients_matches() {
        List<RecipeIngredient> inputList = List.of(
                new RecipeIngredient("nether_wart", 1),
                new RecipeIngredient("glass_bottle", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r2", "alchemy", RecipeType.SHAPELESS,
                null, inputList, Map.of(), null);
        when(recipeModule.getRecipesForMachine("alchemy")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("slot_a", item("glass_bottle", 1));
        inputs.put("slot_b", item("nether_wart", 1));

        Optional<RecipeDefinition> result = engine.match("alchemy", inputs);
        assertTrue(result.isPresent());
    }

    @Test
    void testShapeless_orderDoesNotMatter_matches() {
        List<RecipeIngredient> inputList = List.of(
                new RecipeIngredient("item_a", 1),
                new RecipeIngredient("item_b", 1),
                new RecipeIngredient("item_c", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r3", "forge", RecipeType.SHAPELESS,
                null, inputList, Map.of(), null);
        when(recipeModule.getRecipesForMachine("forge")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("slot_x", item("item_c", 1));
        inputs.put("slot_y", item("item_a", 1));
        inputs.put("slot_z", item("item_b", 1));

        Optional<RecipeDefinition> result = engine.match("forge", inputs);
        assertTrue(result.isPresent());
    }

    @Test
    void testShapeless_insufficientAmount_noMatch() {
        List<RecipeIngredient> inputList = List.of(
                new RecipeIngredient("nether_wart", 3)
        );
        RecipeDefinition recipe = new RecipeDefinition("r4", "alchemy", RecipeType.SHAPELESS,
                null, inputList, Map.of(), null);
        when(recipeModule.getRecipesForMachine("alchemy")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("slot_a", item("nether_wart", 2)); // only 2, need 3

        Optional<RecipeDefinition> result = engine.match("alchemy", inputs);
        assertTrue(result.isEmpty());
    }

    @Test
    void testShapeless_duplicateIngredientNotDoubleMatched() {
        // Recipe needs 2x iron_ingot — must use 2 distinct slots or 1 slot with amount≥2
        List<RecipeIngredient> inputList = List.of(
                new RecipeIngredient("iron_ingot", 1),
                new RecipeIngredient("iron_ingot", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r5", "forge", RecipeType.SHAPELESS,
                null, inputList, Map.of(), null);
        when(recipeModule.getRecipesForMachine("forge")).thenReturn(List.of(recipe));

        // One slot with amount 1 — can only match one of the two ingredients
        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("slot_a", item("iron_ingot", 1));

        Optional<RecipeDefinition> result = engine.match("forge", inputs);
        assertTrue(result.isEmpty(), "Single slot with amount=1 should not satisfy 2 separate iron_ingot ingredients");
    }

    // ── SHAPED ────────────────────────────────────────────────────────────

    @Test
    void testShaped_topLeft2x2_matches() {
        // 2x2 recipe defined at slots 0,1,3,4 (top-left of 3x3 grid)
        Map<String, RecipeIngredient> recipeMap = Map.of(
                "0", new RecipeIngredient("wood", 1),
                "1", new RecipeIngredient("wood", 1),
                "3", new RecipeIngredient("wood", 1),
                "4", new RecipeIngredient("wood", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r6", "crafting", RecipeType.SHAPED,
                recipeMap, null, Map.of(), null);
        when(recipeModule.getRecipesForMachine("crafting")).thenReturn(List.of(recipe));

        // Input also in top-left slots
        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("0", item("wood", 1));
        inputs.put("1", item("wood", 1));
        inputs.put("3", item("wood", 1));
        inputs.put("4", item("wood", 1));

        withBukkitVanillaNoMatch(() -> {
            Optional<RecipeDefinition> result = engine.match("crafting", inputs);
            assertTrue(result.isPresent());
        });
    }

    @Test
    void testShaped_bottomRight2x2_matches() {
        // Recipe defined at 0,1,3,4 but input placed at 4,5,7,8 (same 2x2 shape, different position)
        Map<String, RecipeIngredient> recipeMap = Map.of(
                "0", new RecipeIngredient("wood", 1),
                "1", new RecipeIngredient("wood", 1),
                "3", new RecipeIngredient("wood", 1),
                "4", new RecipeIngredient("wood", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r7", "crafting", RecipeType.SHAPED,
                recipeMap, null, Map.of(), null);
        when(recipeModule.getRecipesForMachine("crafting")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("4", item("wood", 1));
        inputs.put("5", item("wood", 1));
        inputs.put("7", item("wood", 1));
        inputs.put("8", item("wood", 1));

        withBukkitVanillaNoMatch(() -> {
            Optional<RecipeDefinition> result = engine.match("crafting", inputs);
            assertTrue(result.isPresent());
        });
    }

    @Test
    void testShaped_wrongShape_noMatch() {
        // L-shape recipe (0,3,6,7) against T-shape input (0,1,2,4)
        Map<String, RecipeIngredient> recipeMap = Map.of(
                "0", new RecipeIngredient("wood", 1),
                "3", new RecipeIngredient("wood", 1),
                "6", new RecipeIngredient("wood", 1),
                "7", new RecipeIngredient("wood", 1)
        );
        RecipeDefinition recipe = new RecipeDefinition("r8", "crafting", RecipeType.SHAPED,
                recipeMap, null, Map.of(), null);
        when(recipeModule.getRecipesForMachine("crafting")).thenReturn(List.of(recipe));

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("0", item("wood", 1));
        inputs.put("1", item("wood", 1));
        inputs.put("2", item("wood", 1));
        inputs.put("4", item("wood", 1));

        withBukkitVanillaNoMatch(() -> {
            Optional<RecipeDefinition> result = engine.match("crafting", inputs);
            assertTrue(result.isEmpty());
        });
    }

    // ── CONSUME ───────────────────────────────────────────────────────────

    @Test
    void testConsumeExactSlot_reducesAmountsCorrectly() {
        Map<String, RecipeIngredient> inputMap = Map.of(
                "input1", new RecipeIngredient("iron_ingot", 2)
        );
        RecipeDefinition recipe = new RecipeDefinition("r9", "forge", RecipeType.EXACT_SLOT,
                inputMap, null, Map.of(), null);

        ItemStack stack = mock(ItemStack.class);
        when(stack.getAmount()).thenReturn(5);
        when(stack.getType()).thenReturn(org.bukkit.Material.STONE);

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("input1", stack);

        engine.consume(recipe, inputs);

        // Should have called setAmount(5 - 2 = 3)
        verify(stack).setAmount(3);
    }

    @Test
    void testConsumeShapeless_reducesCorrectItem() {
        List<RecipeIngredient> inputList = List.of(
                new RecipeIngredient("iron_ingot", 2)
        );
        RecipeDefinition recipe = new RecipeDefinition("r10", "forge", RecipeType.SHAPELESS,
                null, inputList, Map.of(), null);

        // Numeric key so consume() targets it
        ItemStack stack = mock(ItemStack.class);
        when(stack.getAmount()).thenReturn(4);
        when(stack.getType()).thenReturn(org.bukkit.Material.STONE);
        when(stack.hasItemMeta()).thenReturn(true);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING)).thenReturn("iron_ingot");

        Map<String, ItemStack> inputs = new HashMap<>();
        inputs.put("0", stack);

        engine.consume(recipe, inputs);

        verify(stack).setAmount(2); // 4 - 2 = 2
    }
}
