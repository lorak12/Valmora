package org.nakii.valmora.module.gui.event;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiComponent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.gui.components.InputComponent;
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

                // Find output slot in GUI layout; fall back to input slot for in-place machines
                int outputSlot = findOutputSlot(session);
                if (outputSlot == -1) {
                    int inputSlot = findInputSlot(session, "base_item");
                    if (inputSlot == -1) return;
                    session.getInventory().setItem(inputSlot, craft.output());
                } else {
                    session.getInventory().setItem(outputSlot, craft.output());
                }

                // Give any additional outputs beyond the primary one directly to the player —
                // the GUI only has one OUTPUT slot to place items into (see CraftResult).
                if (!craft.extraOutputs().isEmpty()) {
                    for (ItemStack extra : craft.extraOutputs()) {
                        var leftover = player.getInventory().addItem(extra);
                        leftover.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
                    }
                }

                // Execute on-craft script
                if (craft.onCraft() != null) craft.onCraft().execute(guiContext);

                // BREW quest objective: "the player who last added/changed an item before the
                // brew completed" (docs/Objective_list.md) — that's exactly this craft's actor,
                // for the alchemy machine specifically (not crafting/anvil/etc).
                if ("alchemy".equalsIgnoreCase(machineId) && craft.output() != null) {
                    var questManager = org.nakii.valmora.api.ValmoraAPI.getInstance().getQuestManager();
                    if (questManager != null) {
                        String itemId = craft.output().getItemMeta() != null
                                ? craft.output().getItemMeta().getPersistentDataContainer()
                                        .get(org.nakii.valmora.util.Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING)
                                : null;
                        String target = itemId != null ? itemId : craft.output().getType().name();
                        questManager.trigger(player, org.nakii.valmora.module.quest.QuestObjectiveTypes.BREW,
                                target, craft.output().getAmount());
                    }
                }

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

    private int findInputSlot(GuiSession session, String inputId) {
        List<List<Character>> layout = session.getDefinition().getLayout();
        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                GuiComponent comp = session.getDefinition().getComponents().get(row.get(c));
                if (comp instanceof InputComponent input && inputId.equals(input.getId())) {
                    return r * 9 + c;
                }
            }
        }
        return -1;
    }
}
