package org.nakii.valmora.module.reforge;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.Rarity;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $reforge.cost.<rarity>$} so GUI lore/text can read the live forge-cost table
 * ({@link ForgeCostRegistry}, backed by {@code enchant/forge_costs.yml}) instead of a hardcoded
 * copy of the default values baked into the GUI YAML itself (which went stale the moment an
 * admin edited the cost file, since there was no way to reference it dynamically before).
 */
public class ReforgeVariableProvider implements VariableProvider {

    private final ReforgeModule module;

    public ReforgeVariableProvider(ReforgeModule module) {
        this.module = module;
    }

    @Override
    public String getNamespace() {
        return "reforge";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length < 2 || !path[0].equalsIgnoreCase("cost")) return null;
        try {
            Rarity rarity = Rarity.valueOf(path[1].toUpperCase());
            int cost = module.getForgeCostRegistry().getCost(rarity);
            return module.formatCoinsPublic(cost);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
