package org.nakii.valmora.module.gui.event;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.module.recipe.RecipeEngine;
import org.nakii.valmora.module.recipe.RecipeIngredient;
import org.nakii.valmora.module.gui.components.OutputComponent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.List;
import java.util.Optional;

public class GuiForceCraftEventFactory implements EventFactory {

    private final Valmora plugin;

    public GuiForceCraftEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "gui_force_craft";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> {
            if (!(context instanceof GuiExecutionContext guiContext)) return;
            GuiSession session = guiContext.getSession();
            if (session == null) return;

            Player player = session.getPlayer();
            String machineId = session.getDefinition().getMachine();
            RecipeEngine engine = plugin.getRecipeModule().getRecipeEngine();

            Optional<RecipeDefinition> match = engine.match(machineId, session.getInputSnapshot());
            if (match.isEmpty()) return;

            RecipeDefinition recipe = match.get();
            ItemStack output;

            if (recipe.isVanilla()) {
                output = recipe.getVanillaResult();
            } else {
                RecipeIngredient firstOutput = recipe.getOutputs().values().iterator().next();
                Material mat = Material.matchMaterial(firstOutput.item());
                if (mat == null) {
                    output = plugin.getItemManager().createItemStack(firstOutput.item());
                    if (output != null) output.setAmount(firstOutput.amount());
                } else {
                    output = new ItemStack(mat, firstOutput.amount());
                }
            }

            if (output == null || output.getType() == Material.AIR) return;

            // Find output slot
            int outputSlot = -1;
            List<List<Character>> layout = session.getDefinition().getLayout();
            for (int r = 0; r < layout.size(); r++) {
                List<Character> row = layout.get(r);
                for (int c = 0; c < row.size(); c++) {
                    char ch = row.get(c);
                    if (session.getDefinition().getComponents().get(ch) instanceof OutputComponent) {
                        outputSlot = r * 9 + c;
                        break;
                    }
                }
                if (outputSlot != -1) break;
            }

            if (outputSlot == -1) return;

            // Consume ingredients
            engine.consume(recipe, session.getInputSnapshot());

            // Place output
            session.getInventory().setItem(outputSlot, output);

            // Execute on-craft
            if (recipe.getOnCraft() != null) recipe.getOnCraft().execute(guiContext);

            // Re-render
            new GuiRenderer(plugin).render(session);
        };
    }
}
