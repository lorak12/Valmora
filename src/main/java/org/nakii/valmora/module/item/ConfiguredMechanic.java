package org.nakii.valmora.module.item;

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

    public void execute(LivingEntity caster, LivingEntity target){
        ExecutionContext context = new SimpleExecutionContext(caster, target, caster.getLocation(), params);
        mechanic.execute(context);
    }
}
