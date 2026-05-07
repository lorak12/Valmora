package org.nakii.valmora.module.gui.event;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiComponent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.gui.components.OutputComponent;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.recipe.CraftResult;
import org.nakii.valmora.module.recipe.RecipeEngine;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.List;
import java.util.Map;
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

            // Dupe protection: reject concurrent craft attempts
            if (session.isCraftingLocked()) return;
            session.setCraftingLocked(true);

            try {
                Player player = session.getPlayer();
                String machineId = session.getDefinition().getMachine();
                RecipeEngine engine = plugin.getRecipeModule().getRecipeEngine();

                // Take a fresh live snapshot — validates that the inventory hasn't been desynchronised
                Map<String, ItemStack> inputs = session.getInputSnapshot();

                // Unified craft: match + consume + build output atomically
                Optional<CraftResult> result = engine.craft(machineId, inputs, player);
                if (result.isEmpty()) return;

                CraftResult craft = result.get();

                // Find output slot in GUI layout
                int outputSlot = findOutputSlot(session);
                if (outputSlot == -1) return;

                // Place output item
                session.getInventory().setItem(outputSlot, craft.output());

                // Execute on-craft script
                if (craft.onCraft() != null) craft.onCraft().execute(guiContext);

                // Re-render
                new GuiRenderer(plugin).render(session);
            } finally {
                session.setCraftingLocked(false);
            }
        };
    }

    private int findOutputSlot(GuiSession session) {
        List<List<Character>> layout = session.getDefinition().getLayout();
        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                GuiComponent comp = session.getDefinition().getComponents().get(row.get(c));
                if (comp instanceof OutputComponent) {
                    return r * 9 + c;
                }
            }
        }
        return -1;
    }
}
