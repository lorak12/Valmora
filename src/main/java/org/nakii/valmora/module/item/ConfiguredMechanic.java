package org.nakii.valmora.module.item;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

public class ConfiguredMechanic {
    private final AbilityMechanic mechanic;
    private final ConfigurationSection params;

    public ConfiguredMechanic(AbilityMechanic mechanic, ConfigurationSection params) {
        this.mechanic = mechanic;
        this.params = params;
    }

    /** The underlying mechanic implementation — used by callers that need to inspect a mechanic's
     *  identity/presence without executing it (e.g. {@code TrampleListener} checking for
     *  {@code CANCEL_TRAMPLE}). */
    public AbilityMechanic getMechanic() { return mechanic; }

    public void execute(LivingEntity caster, LivingEntity target){
        executeAt(caster, target, caster.getLocation());
    }

    /** Executes with an explicit origin location (used by projectile impact callbacks so that
     *  radius/cone selectors centre on the impact point rather than the caster). */
    public void executeAt(LivingEntity caster, LivingEntity target, Location location) {
        ExecutionContext context = new SimpleExecutionContext(caster, target, location, params);
        mechanic.execute(context);
    }
}
