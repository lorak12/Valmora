package org.nakii.valmora.module.machine;

import org.nakii.valmora.api.scripting.Condition;

import java.util.List;

/**
 * A machine definition: declares which GUI a machine opens, a descriptive {@code logic} key,
 * expected input/output slot counts (validated against the GUI's actual component list by
 * {@link MachineModule}), an optional {@code shape} for machines that require a spatial
 * arrangement (e.g. a 1x3 "press"), and any number of {@code open-triggers:} — plain script-module
 * condition strings (see {@code ConditionParser}), any one of which opens this machine's GUI.
 *
 * <p>{@code logic} is validation/documentation metadata only — it does NOT change how
 * {@code RecipeEngine} dispatches a craft. That dispatch is still driven purely by
 * {@code GuiDefinition.getMachine()}, exactly as before this module existed; a {@link
 * org.nakii.valmora.module.recipe.DynamicMachineHandler} is still registered per machine id by
 * whichever module owns that machine's craft logic (recipe/modifier/alchemy/enchant).
 */
public class MachineDefinition {

    /** {@code rows x cols} for machines that require a spatial ingredient arrangement. */
    public record Shape(int rows, int cols) {
        public int slotCount() { return rows * cols; }
    }

    private final String id;
    private final String gui;
    private final String logic;
    private final int inputSlots;
    private final int outputSlots;
    private final Shape shape;
    private final List<Condition> openTriggers;

    public MachineDefinition(String id, String gui, String logic, int inputSlots, int outputSlots,
                              Shape shape, List<Condition> openTriggers) {
        this.id = id;
        this.gui = gui;
        this.logic = logic;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.shape = shape;
        this.openTriggers = openTriggers;
    }

    public String getId() { return id; }
    public String getGui() { return gui; }
    public String getLogic() { return logic; }
    public int getInputSlots() { return inputSlots; }
    public int getOutputSlots() { return outputSlots; }
    public Shape getShape() { return shape; }
    public List<Condition> getOpenTriggers() { return openTriggers; }
}
