package org.nakii.valmora.item.ability.impl;


import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.item.ability.AbilityMechanic;
import org.nakii.valmora.profile.ValmoraProfile;

public class HealMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "HEAL";
    }

    @Override
    public void execute(Player caster, LivingEntity target, ConfigurationSection params) {
        String targetType = params.getString("target", "@player"); // Defaults to self-heal
        LivingEntity actualTarget = targetType.equalsIgnoreCase("@player") ? caster : target;
        
        if (!(actualTarget instanceof Player healTarget)) return; // Only heals players for now

        double healAmount = params.getDouble("heal", 0.0);
        if (healAmount <= 0) return;

        // Hook into custom player state
        ValmoraProfile profile = Valmora.getInstance().getPlayerManager().getSession(healTarget.getUniqueId()).getActiveProfile();
        
        if (profile != null) {
            profile.getPlayerState().heal(healAmount, profile.getStatManager());
            // Sync custom health to visual hearts
            Valmora.getInstance().getPlayerManager().syncVisualHealth(healTarget, profile.getPlayerState(), profile.getStatManager());
        }
    }
}