package org.nakii.valmora.combat;

import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.profile.PlayerState;
import org.nakii.valmora.profile.ValmoraProfile;

public class DamageApplier {
    DamageResult damageResult;
    Valmora plugin;

    public DamageApplier(DamageResult damageResult, Valmora plugin) {
        this.damageResult = damageResult;
        this.plugin = plugin;
    }

    public void applyDamage() {
        if (damageResult.getVictim() instanceof Player player) {
            // --- PLAYER VICTIM LOGIC ---
            ValmoraProfile profile = plugin.getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();
            PlayerState state = profile.getPlayerState();
            
            // Apply damage to virtual health
            state.reduceHealth(damageResult.getFinalDamage());
            
            // Sync to visual hearts
            plugin.getPlayerManager().syncVisualHealth(player, state, profile.getStatManager());

            // Set combat timer
            state.setInCombat();

        } else {
            // --- MOB VICTIM LOGIC (Your existing code) ---
            damageResult.getVictim().setHealth(Math.max(0, damageResult.getVictim().getHealth() - damageResult.getFinalDamage()));
            plugin.getMobManager().updateVisuals(damageResult.getVictim());
        }

        // Apply invulnerability frames to prevent rapid overlapping DoT triggers
        damageResult.getVictim().setNoDamageTicks(20);
    }
}