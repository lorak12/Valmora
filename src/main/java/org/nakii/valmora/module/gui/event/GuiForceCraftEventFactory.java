package org.nakii.valmora.module.gui.event;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiComponent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.gui.components.InputComponent;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.recipe.CraftOutput;
import org.nakii.valmora.module.recipe.CraftResult;
import org.nakii.valmora.module.recipe.RecipeEngine;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.util.DebugManager;

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
            if (session.isCraftingLocked()) {
                DebugManager.log("gui", "gui_force_craft REJECTED — craft already in progress for gui="
                        + session.getDefinition().getId());
                return;
            }
            session.setCraftingLocked(true);

            try {
                Player player = session.getPlayer();
                String machineId = session.getDefinition().getMachine();
                RecipeEngine engine = plugin.getRecipeModule().getRecipeEngine();

                // Take a fresh live snapshot — validates that the inventory hasn't been desynchronised
                Map<String, ItemStack> inputs = session.getInputSnapshot();

                // Unified craft: match + consume + build output atomically
                Optional<CraftResult> result = engine.craft(machineId, inputs, player);
                if (result.isEmpty()) {
                    DebugManager.log("gui", "gui_force_craft: no recipe matched for gui="
                            + session.getDefinition().getId() + " machine=" + machineId + " player=" + player.getName());
                    return;
                }

                CraftResult craft = result.get();
                DebugManager.log("gui", "gui_force_craft: recipe='" + craft.recipe().getId() + "' crafted for gui="
                        + session.getDefinition().getId() + " player=" + player.getName());

                // Route each built output to the OUTPUT component slot it named (RecipeOutput.slot,
                // matching a GUI's OUTPUT id — e.g. a 2-output "processor" machine's `primary`/
                // `byproduct`), so which physical slot an item lands in is exactly what the recipe
                // author declared rather than layout-scan order. `slot() == null` (only possible for
                // a single-output recipe) falls back to the GUI's sole/first OUTPUT component.
                Map<String, Integer> outputSlotsById = session.getDefinition().findOutputSlotsById();
                for (CraftOutput craftOutput : craft.outputs()) {
                    Integer physicalSlot = craftOutput.slot() != null
                            ? outputSlotsById.get(craftOutput.slot())
                            : outputSlotsById.values().stream().findFirst().orElse(null);
                    if (craftOutput.slot() != null && physicalSlot == null) {
                        plugin.getLogger().warning("[gui_force_craft] Recipe '" + craft.recipe().getId()
                                + "' output targets slot '" + craftOutput.slot() + "', but GUI '"
                                + session.getDefinition().getId() + "' has no OUTPUT component with that"
                                + " id — giving it directly to the player instead.");
                    }
                    if (physicalSlot != null) {
                        session.getInventory().setItem(physicalSlot, craftOutput.item());
                    } else if (craftOutput.slot() == null && findInputSlot(session, "base_item") != -1) {
                        // No OUTPUT component at all and this output wasn't targeting a named slot
                        // (only possible for a single-output recipe) — in-place machines (e.g. the
                        // modifier anvil) render their result directly into the base item's own
                        // INPUT slot. A genuinely misconfigured named slot never lands here, so it
                        // can't collide with this fallback.
                        session.getInventory().setItem(findInputSlot(session, "base_item"), craftOutput.item());
                    } else {
                        var leftover = player.getInventory().addItem(craftOutput.item());
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
