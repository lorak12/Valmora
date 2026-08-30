package org.nakii.valmora.module.recipe;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.scripting.CompiledEvent;

import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

public class RecipeDefinition {
    private final String id;
    private final String machine;
    private final RecipeType type;
    private final Map<String, RecipeIngredient> inputMap;
    private final List<RecipeIngredient> inputList;
    private final List<RecipeOutput> outputs;
    private final CompiledEvent onCraft;
    private final boolean isVanilla;
    private final ItemStack vanillaResult;
    private final Consumer<Map<String, ItemStack>> consumeHandler;
    /** Number of columns a SHAPED recipe's numeric slot keys are laid out on — see
     *  {@link org.nakii.valmora.module.gui.GuiSession}'s sequential slot-index convention.
     *  Defaults to 3 (the crafting-table grid) for backward compatibility with every recipe
     *  written against the old hardcoded-3x3 {@code "0".."8"} slot keys. */
    private final int gridWidth;
    /** "keep-data-on-upgrade" (default true) — see {@link ItemDataCarrier}. */
    private final boolean keepDataOnUpgrade;
    /** Which ingredient key (letter or slot id) is "the item being upgraded" whose data should be
     *  carried onto the output. {@code null} disables carry-forward regardless of the flag above. */
    private final String upgradeFrom;

    public RecipeDefinition(String id, String machine, RecipeType type,
                            Map<String, RecipeIngredient> inputMap,
                            List<RecipeIngredient> inputList,
                            List<RecipeOutput> outputs,
                            CompiledEvent onCraft) {
        this(id, machine, type, inputMap, inputList, outputs, onCraft, false, null, null, 3, true, null);
    }

    public RecipeDefinition(String id, String machine, RecipeType type,
                            Map<String, RecipeIngredient> inputMap,
                            List<RecipeIngredient> inputList,
                            List<RecipeOutput> outputs,
                            CompiledEvent onCraft, int gridWidth, boolean keepDataOnUpgrade, String upgradeFrom) {
        this(id, machine, type, inputMap, inputList, outputs, onCraft, false, null, null, gridWidth, keepDataOnUpgrade, upgradeFrom);
    }

    private RecipeDefinition(String id, String machine, RecipeType type,
                            Map<String, RecipeIngredient> inputMap,
                            List<RecipeIngredient> inputList,
                            List<RecipeOutput> outputs,
                            CompiledEvent onCraft, boolean isVanilla, ItemStack vanillaResult,
                            Consumer<Map<String, ItemStack>> consumeHandler,
                            int gridWidth, boolean keepDataOnUpgrade, String upgradeFrom) {
        this.id = id;
        this.machine = machine;
        this.type = type;
        this.inputMap = inputMap;
        this.inputList = inputList;
        this.outputs = outputs;
        this.onCraft = onCraft;
        this.isVanilla = isVanilla;
        this.vanillaResult = vanillaResult;
        this.consumeHandler = consumeHandler;
        this.gridWidth = gridWidth <= 0 ? 3 : gridWidth;
        this.keepDataOnUpgrade = keepDataOnUpgrade;
        this.upgradeFrom = upgradeFrom;
    }

    public static RecipeDefinition vanilla(ItemStack result) {
        return vanilla(result, null);
    }

    public static RecipeDefinition vanilla(ItemStack result, CompiledEvent onCraft) {
        List<RecipeOutput> outputs = List.of(
                new RecipeOutput(new RecipeIngredient(result.getType().name(), result.getAmount()), null));
        return new RecipeDefinition("vanilla:" + result.getType().name(), "crafting_table",
            RecipeType.SHAPELESS, null, null, outputs, onCraft, true, result, null, 3, false, null);
    }

    /** Creates a dynamic recipe with a pre-built output item and custom consume logic. */
    public static RecipeDefinition dynamic(String machineId, ItemStack result,
                                           Consumer<Map<String, ItemStack>> consumeHandler) {
        return new RecipeDefinition("dynamic:" + machineId + ":" + System.nanoTime(), machineId,
            RecipeType.EXACT_SLOT, null, null, null, null, true, result, consumeHandler, 3, false, null);
    }

    public String getId() { return id; }
    public String getMachine() { return machine; }
    public RecipeType getType() { return type; }
    public Map<String, RecipeIngredient> getInputMap() { return inputMap; }
    public List<RecipeIngredient> getInputList() { return inputList; }
    public List<RecipeOutput> getOutputs() { return outputs; }
    public CompiledEvent getOnCraft() { return onCraft; }
    public boolean isVanilla() { return isVanilla; }
    public ItemStack getVanillaResult() { return vanillaResult; }
    public Consumer<Map<String, ItemStack>> getConsumeHandler() { return consumeHandler; }
    public int getGridWidth() { return gridWidth; }
    public boolean isKeepDataOnUpgrade() { return keepDataOnUpgrade; }
    public String getUpgradeFrom() { return upgradeFrom; }
}
