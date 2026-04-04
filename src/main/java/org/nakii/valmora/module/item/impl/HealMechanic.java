package org.nakii.valmora.module.item.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;
import org.nakii.valmora.module.profile.ValmoraProfile;

public class HealMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "HEAL";
    }

    @Override
    public void execute(ExecutionContext context) {
        String targetType = context.getString("target", "@player"); // Defaults to self-heal
        LivingEntity actualTarget = targetType.equalsIgnoreCase("@player") 
                ? (context.getCaster() instanceof LivingEntity ? (LivingEntity) context.getCaster() : null)
                : context.getTarget().orElse(null);
        
        if (!(actualTarget instanceof Player healTarget)) return; // Only heals players for now

        double healAmount = context.getDouble("heal", 0.0);
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
