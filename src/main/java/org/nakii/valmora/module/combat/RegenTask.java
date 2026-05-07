package org.nakii.valmora.module.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.SystemStats;

public class RegenTask implements Runnable {

    private final Valmora plugin;

    public RegenTask(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        SystemStats sys = plugin.getStatModule().getSystemStats();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || !player.isValid()) continue;

            ValmoraPlayer vPlayer = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (vPlayer == null || vPlayer.getActiveProfile() == null) continue;

            ValmoraProfile profile = vPlayer.getActiveProfile();
            PlayerState state = profile.getPlayerState();
            StatManager stats = profile.getStatManager();

            double maxHealth = stats.getStat(sys.getHealth());
            double maxMana = stats.getStat(sys.getMana());

            boolean needsHealthSync = false;

            if (state.getCurrentHealth() < maxHealth && !state.isInCombat()) {
                double healthRegen = stats.getStat(sys.getHealthRegen());
                state.heal(healthRegen, stats);
                needsHealthSync = true;
            }

            if (state.getCurrentMana() < maxMana) {
                double manaRegen = stats.getStat(sys.getManaRegen());
                state.restoreMana(manaRegen, stats);
            }

            if (needsHealthSync) {
                plugin.getPlayerManager().syncVisualHealth(player, state, stats);
            }
        }
    }
}
