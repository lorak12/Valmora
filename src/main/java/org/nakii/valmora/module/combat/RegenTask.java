package org.nakii.valmora.module.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.util.DebugManager;

public class RegenTask implements Runnable {

    private static final String DEBUG_MODULE = "combat";
    // Runs every 20 ticks (1s) already — logging every run would still be a wall of text over a
    // play session, so a summary is only printed once every SUMMARY_INTERVAL runs (~10s).
    private static final int SUMMARY_INTERVAL_RUNS = 10;

    private final Valmora plugin;
    private int runCount = 0;

    public RegenTask(Valmora plugin) {
        this.plugin = plugin;
    }

    private boolean healthBlockedInCombat() {
        return !plugin.getConfig().getBoolean("combat.regen.health-in-combat", false);
    }

    private boolean manaBlockedInCombat() {
        return !plugin.getConfig().getBoolean("combat.regen.mana-in-combat", true);
    }

    @Override
    public void run() {
        SystemStats sys = plugin.getStatModule().getSystemStats();
        runCount++;
        boolean logSummary = DebugManager.isEnabled(DEBUG_MODULE) && runCount % SUMMARY_INTERVAL_RUNS == 0;
        int healedCount = 0;
        int manaRestoredCount = 0;
        int skippedInCombat = 0;

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

            // HC-022: whether combat blocks health/mana regen is now configurable — was hardcoded
            // to "health blocked in combat, mana always regens".
            boolean healthBlockedInCombat = healthBlockedInCombat();
            boolean manaBlockedInCombat = manaBlockedInCombat();

            if (state.getCurrentHealth() < maxHealth && !(state.isInCombat() && healthBlockedInCombat)) {
                double healthRegen = stats.getStat(sys.getHealthRegen());
                state.heal(healthRegen, stats);
                needsHealthSync = true;
                healedCount++;
            } else if (state.getCurrentHealth() < maxHealth && state.isInCombat() && healthBlockedInCombat) {
                skippedInCombat++;
            }

            if (state.getCurrentMana() < maxMana && !(state.isInCombat() && manaBlockedInCombat)) {
                double manaRegen = stats.getStat(sys.getManaRegen());
                state.restoreMana(manaRegen, stats);
                manaRestoredCount++;
            }

            if (needsHealthSync) {
                plugin.getPlayerManager().syncVisualHealth(player, state, stats);
            }
        }

        if (logSummary) {
            plugin.getLogger().info("[combat-debug] [regen] tick #" + runCount + ": healed=" + healedCount
                    + " manaRestored=" + manaRestoredCount + " skipped(in-combat)=" + skippedInCombat
                    + " online=" + Bukkit.getOnlinePlayers().size());
        }
    }
}
