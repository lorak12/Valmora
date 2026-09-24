package org.nakii.valmora.module.machine;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.gui.GuiComponent;
import org.nakii.valmora.module.gui.GuiDefinition;
import org.nakii.valmora.module.gui.components.InputComponent;
import org.nakii.valmora.module.gui.components.OutputComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Machine definition layer: loads {@code machines/*.yml}, cross-validates each machine's declared
 * {@code input-slots}/{@code output-slots}/{@code shape} against the actual GUI it points to, and
 * registers {@link MachineOpenListener} for any machine that declares {@code open-triggers:} —
 * arbitrary script-module conditions, any one of which opens the GUI.
 *
 * <p>Registered after {@code gui} (needs the GUI registry to validate against) and {@code recipe}
 * (this module doesn't call into it directly, but sits alongside it in the machine-config layer —
 * see Valmora.java's module order comment). Does NOT change how {@code RecipeEngine} dispatches a
 * craft — that's still driven purely by {@code GuiDefinition.getMachine()}; {@code logic:} here is
 * validation/documentation metadata only.
 */
public class MachineModule implements ReloadableModule {

    private final Valmora plugin;
    private final MachineRegistry registry = new MachineRegistry();
    private MachineOpenListener listener;

    public MachineModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        registry.clear();

        YamlLoader<MachineDefinition> loader = new YamlLoader<MachineDefinition>(plugin, "machines", "Machines").kind(org.nakii.valmora.infrastructure.config.refs.Kinds.MACHINE);
        MachineDefinitionParser parser = new MachineDefinitionParser(plugin);
        loader.load(parser::parse, registry::register);

        org.nakii.valmora.infrastructure.config.refs.ReferenceValidator.global().register(
                new org.nakii.valmora.infrastructure.config.refs.ReferenceCheck() {
                    @Override public String name() { return "machine-guis"; }
                    @Override public void check(org.nakii.valmora.infrastructure.config.refs.ReferenceContext ctx) {
                        validateAgainstGuis(ctx);
                    }
                });

        List<MachineDefinition> withTriggers = new ArrayList<>();
        for (MachineDefinition machine : registry.values()) {
            if (!machine.getOpenTriggers().isEmpty()) withTriggers.add(machine);
        }
        listener = new MachineOpenListener(plugin, withTriggers);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        registry.clear();
    }

    /**
     * Reload-time warning pass (matches the {@code ModifierValidator} convention — logs, does not
     * block loading): confirms the GUI a machine points to actually declares as many INPUT/OUTPUT
     * components as the machine claims.
     */
    private void validateAgainstGuis(org.nakii.valmora.infrastructure.config.refs.ReferenceContext ctx) {
        Map<String, GuiDefinition> guis = plugin.getGuiModule() != null ? plugin.getGuiModule().getGuiRegistry() : Map.of();
        for (MachineDefinition machine : registry.values()) {
            GuiDefinition gui = guis.get(machine.getGui().toLowerCase());
            if (gui == null) {
                continue; // reported as a dangling gui reference (recorded by the parser)
            }

            int actualInputs = 0, actualOutputs = 0;
            for (var row : gui.getLayout()) {
                for (char c : row) {
                    GuiComponent comp = gui.getComponents().get(c);
                    if (comp instanceof InputComponent) actualInputs++;
                    else if (comp instanceof OutputComponent) actualOutputs++;
                }
            }

            if (machine.getInputSlots() > 0 && actualInputs != machine.getInputSlots()) {
                ctx.warn("Machines", null, machine.getId(), "declares " + machine.getInputSlots()
                        + " input-slots but GUI '" + machine.getGui() + "' has " + actualInputs + " INPUT slots", null);
            }
            if (machine.getOutputSlots() > 0 && actualOutputs != machine.getOutputSlots()) {
                ctx.warn("Machines", null, machine.getId(), "declares " + machine.getOutputSlots()
                        + " output-slots but GUI '" + machine.getGui() + "' has " + actualOutputs + " OUTPUT slots", null);
            }
        }
    }

    @Override
    public String getId() { return "machine"; }

    @Override
    public String getName() { return "Machine Definitions"; }

    public MachineRegistry getRegistry() { return registry; }
}
