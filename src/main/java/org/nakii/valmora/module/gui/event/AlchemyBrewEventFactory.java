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
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Completes an in-progress brew by transforming all filled bottle slots into
 * the result that was pre-validated and stored by gui_alchemy_start.
 *
 * If no stored result is found (brew_result not in session props), this is a
 * no-op — the brew silently fails without consuming anything.
 *
 * DSL: gui_alchemy_brew  (kept for backward compat with alchemy.yml)
 */
public class AlchemyBrewEventFactory implements EventFactory {

    private final Valmora plugin;

    public AlchemyBrewEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "gui_alchemy_brew";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> {
            if (!(context instanceof GuiExecutionContext guiContext)) return;
            GuiSession session = guiContext.getSession();
            if (session == null) return;
            if (session.isCraftingLocked()) return;
            session.setCraftingLocked(true);

            try {
                Object stored = session.getProps().remove("brew_result");
                if (!(stored instanceof ItemStack output)) return;

                Inventory inv = session.getInventory();
                GuiDefinition def = session.getDefinition();
                List<List<Character>> layout = def.getLayout();

                // Collect all bottle slot positions
                List<Integer> bottleSlots = new ArrayList<>();
                for (int r = 0; r < layout.size(); r++) {
                    List<Character> row = layout.get(r);
                    for (int c = 0; c < row.size(); c++) {
                        GuiComponent comp = def.getComponents().get(row.get(c));
                        if (comp instanceof InputComponent input && "bottle".equals(input.getId())) {
                            bottleSlots.add(r * 9 + c);
                        }
                    }
                }

                // Replace every non-empty bottle slot with one result item
                for (int slot : bottleSlots) {
                    ItemStack bottle = inv.getItem(slot);
                    if (bottle != null && bottle.getType() != Material.AIR) {
                        inv.setItem(slot, output.clone());
                    }
                }

                new GuiRenderer(plugin).render(session);
            } finally {
                session.setCraftingLocked(false);
            }
        };
    }
}
