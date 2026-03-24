package org.nakii.valmora.item.ability;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class ConfiguredMechanic {
    private final AbilityMechanic mechanic;
    private final ConfigurationSection params;

    public ConfiguredMechanic(AbilityMechanic mechanic, ConfigurationSection params) {
        this.mechanic = mechanic;
        this.params = params;
    }

    public void execute(Player caster, LivingEntity target){
        mechanic.execute(caster, target, params);
    }
}
