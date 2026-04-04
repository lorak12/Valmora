package org.nakii.valmora.module.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;

public class RegenTask implements Runnable {

    private final Valmora plugin;

    public RegenTask(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip dead players
            if (player.isDead() || !player.isValid()) continue;

            ValmoraPlayer vPlayer = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (vPlayer == null || vPlayer.getActiveProfile() == null) continue;

            ValmoraProfile profile = vPlayer.getActiveProfile();
            PlayerState state = profile.getPlayerState();
            StatManager stats = profile.getStatManager();

            double maxHealth = stats.getStat(Stat.HEALTH);
            double maxMana = stats.getStat(Stat.MANA);

            boolean needsHealthSync = false;

            // --- HEALTH REGENERATION ---
            if (state.getCurrentHealth() < maxHealth && !state.isInCombat()) {
                double healthRegen = stats.getStat(Stat.HEALTH_REGEN);

                state.heal(healthRegen, stats);
                needsHealthSync = true;
            }

            // --- MANA REGENERATION ---
            if (state.getCurrentMana() < maxMana) {
                double manaRegen = stats.getStat(Stat.MANA_REGEN);
                
                // Mana usually regens fully even in combat, but you can change this!
                state.restoreMana(manaRegen, stats); 
            }

            // Sync visual hearts only if health actually changed
            if (needsHealthSync) {
                plugin.getPlayerManager().syncVisualHealth(player, state, stats);
            }
        }
    }
}
