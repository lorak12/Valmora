package org.nakii.valmora.module.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraProfile;

public class DamageApplier {
    private final DamageResult damageResult;

    public DamageApplier(DamageResult damageResult, Plugin plugin) {
        this.damageResult = damageResult;
    }

    public void applyDamage() {
        ValmoraAPI api = ValmoraAPI.getInstance();
        if (damageResult.getVictim() instanceof Player player) {
            // --- PLAYER VICTIM LOGIC ---
            ValmoraProfile profile = api.getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();
            PlayerState state = profile.getPlayerState();
            
            // Apply damage to virtual health
            state.reduceHealth(damageResult.getFinalDamage());
            
            // Sync to visual hearts
            api.getPlayerManager().syncVisualHealth(player, state, profile.getStatManager());

            // Set combat timer
            state.setInCombat();

        } else {
            // --- MOB VICTIM LOGIC ---
            damageResult.getVictim().setHealth(Math.max(0, damageResult.getVictim().getHealth() - damageResult.getFinalDamage()));
            api.getMobManager().updateVisuals(damageResult.getVictim());
        }

        // Apply invulnerability frames to prevent rapid overlapping DoT triggers
        damageResult.getVictim().setNoDamageTicks(20);
    }
}
