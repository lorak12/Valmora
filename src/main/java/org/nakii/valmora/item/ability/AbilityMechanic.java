package org.nakii.valmora.item.ability;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface AbilityMechanic {
    /**
     * The unique ID used in the YAML config (e.g., "DAMAGE", "HEAL", "TELEPORT")
     */
    String getId();

    /**
     * The logic that runs when the ability is triggered
     * 
     * @param caster The player using the ability
     * @param target The entity the ability is used on
     * @param params The parameters for the ability
     */
    void execute(Player caster, LivingEntity target, ConfigurationSection params);
}
