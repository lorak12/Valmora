package org.nakii.valmora.module.gui.event;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiComponent;
import org.nakii.valmora.module.gui.GuiDefinition;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.gui.components.InputComponent;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.module.recipe.RecipeEngine;
import org.nakii.valmora.module.script.event.ConditionAbortException;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates the alchemy recipe and consumes the ingredient immediately when
 * a brew cycle starts. The pre-built result ItemStack is stored in session
 * props under "brew_result" for gui_alchemy_complete to use at timer=0.
 *
 * Throws ConditionAbortException (aborting the start sequence) when no
 * matching recipe exists for the current ingredient + base combination.
 *
 * DSL: gui_alchemy_start
 */
public class AlchemyBrewStartEventFactory implements EventFactory {

    private final Valmora plugin;

    public AlchemyBrewStartEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "gui_alchemy_start";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> {
            if (!(context instanceof GuiExecutionContext guiContext)) return;
            GuiSession session = guiContext.getSession();
            if (session == null) return;

            Inventory inv = session.getInventory();
            GuiDefinition def = session.getDefinition();
            List<List<Character>> layout = def.getLayout();

            // Find ingredient slot
            int ingredientSlot = -1;
            ItemStack ingredientItem = null;
            outer:
            for (int r = 0; r < layout.size(); r++) {
                List<Character> row = layout.get(r);
                for (int c = 0; c < row.size(); c++) {
                    GuiComponent comp = def.getComponents().get(row.get(c));
                    if (comp instanceof InputComponent input && "ingredient".equals(input.getId())) {
                        ingredientSlot = r * 9 + c;
                        ingredientItem = inv.getItem(ingredientSlot);
                        break outer;
                    }
                }
            }

            if (ingredientSlot == -1 || ingredientItem == null || ingredientItem.getType() == Material.AIR) {
                throw new ConditionAbortException();
            }

            // Find sample bottle for recipe matching
            ItemStack sampleBottle = null;
            for (int r = 0; r < layout.size(); r++) {
                List<Character> row = layout.get(r);
                for (int c = 0; c < row.size(); c++) {
                    GuiComponent comp = def.getComponents().get(row.get(c));
                    if (comp instanceof InputComponent input && "bottle".equals(input.getId())) {
                        ItemStack item = inv.getItem(r * 9 + c);
                        if (item != null && item.getType() != Material.AIR) {
                            sampleBottle = item;
                            break;
                        }
                    }
                }
                if (sampleBottle != null) break;
            }

            if (sampleBottle == null) {
                throw new ConditionAbortException();
            }

            // Validate recipe — AlchemyMachineHandler expects "base" + "ingredient" keys
            Map<String, ItemStack> virtualInputs = new HashMap<>();
            virtualInputs.put("base", sampleBottle);
            virtualInputs.put("ingredient", ingredientItem);

            RecipeEngine engine = plugin.getRecipeModule().getRecipeEngine();
            Optional<RecipeDefinition> matched = engine.match(def.getMachine(), virtualInputs, session.getPlayer());
            if (matched.isEmpty()) {
                throw new ConditionAbortException();
            }

            RecipeDefinition recipe = matched.get();
            ItemStack output = recipe.getVanillaResult() != null
                    ? recipe.getVanillaResult()
                    : buildResultItem(recipe);
            if (output == null) {
                throw new ConditionAbortException();
            }

            // Store result for gui_alchemy_complete to apply at timer=0
            session.getProps().put("brew_result", output.clone());

            // Consume the ingredient immediately so the I slot is visually empty during brew
            int remaining = ingredientItem.getAmount() - 1;
            inv.setItem(ingredientSlot, remaining > 0 ? ingredientItem.asQuantity(remaining) : null);

            new GuiRenderer(plugin).render(session);
        };
    }

    private ItemStack buildResultItem(RecipeDefinition recipe) {
        if (recipe.getOutputs() == null || recipe.getOutputs().isEmpty()) return null;
        var firstOutput = recipe.getOutputs().values().iterator().next();
        Material mat = Material.matchMaterial(firstOutput.item());
        if (mat == null) {
            ItemStack custom = plugin.getItemManager().createItemStack(firstOutput.item());
            if (custom == null) return null;
            custom.setAmount(firstOutput.amount());
            return custom;
        }
        return new ItemStack(mat, firstOutput.amount());
    }
}
