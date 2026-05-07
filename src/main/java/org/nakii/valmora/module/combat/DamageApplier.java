package org.nakii.valmora.module.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

public class DamageApplier {
    private final DamageResult damageResult;
    private final Plugin plugin;

    public DamageApplier(DamageResult damageResult, Plugin plugin) {
        this.damageResult = damageResult;
        this.plugin = plugin;
    }

    public void applyDamage() {
        ValmoraAPI api = ValmoraAPI.getInstance();
        if (damageResult.getVictim() instanceof Player player) {
            // --- PLAYER VICTIM LOGIC ---
            ValmoraPlayer vp = api.getPlayerManager().getSession(player.getUniqueId());
            if (vp == null) {
                plugin.getLogger().warning("DamageApplier: no session for " + player.getUniqueId());
                return;
            }
            ValmoraProfile profile = vp.getActiveProfile();
            if (profile == null) return;
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
