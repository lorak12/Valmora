package org.nakii.valmora.module.machine;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.scripting.Condition;

import java.util.ArrayList;
import java.util.List;

public class MachineDefinitionParser {

    private final Valmora plugin;

    public MachineDefinitionParser(Valmora plugin) {
        this.plugin = plugin;
    }

    public LoadResult<MachineDefinition, String> parse(String id, ConfigurationSection section, String filePath) {
        try {
            String gui = section.getString("gui", id);
            org.nakii.valmora.infrastructure.config.diag.LoadScope.current()
                    .ifPresent(scope -> scope.sub("gui").ref(org.nakii.valmora.infrastructure.config.refs.Kinds.GUI, gui));
            String logic = section.getString("logic", "custom");
            int inputSlots = section.getInt("input-slots", 0);
            int outputSlots = section.getInt("output-slots", 0);

            MachineDefinition.Shape shape = null;
            String shapeStr = section.getString("shape", null);
            if (shapeStr != null) {
                String[] parts = shapeStr.toLowerCase().split("x");
                if (parts.length != 2) {
                    return LoadResult.failure("[" + filePath + "] Machine " + id
                            + ": shape '" + shapeStr + "' must be 'ROWSxCOLS' (e.g. '1x3').");
                }
                try {
                    shape = new MachineDefinition.Shape(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException e) {
                    return LoadResult.failure("[" + filePath + "] Machine " + id
                            + ": shape '" + shapeStr + "' is not two integers separated by 'x'.");
                }
                if (inputSlots > 0 && shape.slotCount() != inputSlots) {
                    return LoadResult.failure("[" + filePath + "] Machine " + id
                            + ": shape " + shapeStr + " = " + shape.slotCount()
                            + " slots, but input-slots is " + inputSlots + ".");
                }
            }

            // Each entry is a plain script-module condition string (ConditionParser — the same
            // language used by GUI/ability/quest conditions elsewhere: tag, health, hunger,
            // location, zone, block, variable, objective, quest, point, or a raw expression). Any
            // one of them being true opens this machine's GUI — see MachineOpenListener.
            List<Condition> openTriggers = new ArrayList<>();
            for (String raw : section.getStringList("open-triggers")) {
                openTriggers.add(plugin.getScriptModule().getConditionParser().parse(raw));
            }

            MachineDefinition def = new MachineDefinition(id, gui, logic, inputSlots, outputSlots, shape, openTriggers);
            return LoadResult.success(def);
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Error parsing Machine " + id + ": " + e.getMessage());
        }
    }
}
