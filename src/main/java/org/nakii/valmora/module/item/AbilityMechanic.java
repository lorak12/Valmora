package org.nakii.valmora.module.item;

import org.nakii.valmora.api.execution.ExecutionContext;

public interface AbilityMechanic {
    /**
     * The unique ID used in the YAML config (e.g., "DAMAGE", "HEAL", "TELEPORT")
     */
    String getId();

    /**
     * The logic that runs when the ability is triggered
     * 
     * @param context The execution context carrying caster, target, and data
     */
    void execute(ExecutionContext context);
}
