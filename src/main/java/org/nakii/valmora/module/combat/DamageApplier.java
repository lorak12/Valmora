package org.nakii.valmora.module.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.DebugManager;

public class DamageApplier {

    private static final String DEBUG_MODULE = "combat";

    private final DamageResult damageResult;
    private final Plugin plugin;

    public DamageApplier(DamageResult damageResult, Plugin plugin) {
        this.damageResult = damageResult;
        this.plugin = plugin;
    }

    private void debug(String msg) {
        if (DebugManager.isEnabled(DEBUG_MODULE)) {
            plugin.getLogger().info("[combat-debug] [apply] " + msg);
        }
    }

    public void applyDamage() {
        ValmoraAPI api = ValmoraAPI.getInstance();
        if (damageResult.getVictim() instanceof Player player) {
            // --- PLAYER VICTIM LOGIC ---
            ValmoraPlayer vp = api.getPlayerManager().getSession(player.getUniqueId());
            if (vp == null) {
                plugin.getLogger().warning("DamageApplier: no session for " + player.getUniqueId());
                debug("ABORTED: no ValmoraPlayer session for " + player.getUniqueId() + " — damage silently discarded");
                return;
            }
            ValmoraProfile profile = vp.getActiveProfile();
            if (profile == null) {
                plugin.getLogger().warning("DamageApplier: no active profile for " + player.getUniqueId() + " — damage discarded.");
                debug("ABORTED: no active profile for " + player.getUniqueId() + " — damage silently discarded");
                return;
            }
            PlayerState state = profile.getPlayerState();
            double before = state.getCurrentHealth();

            // Totem of Undying interception (VANILLA_CONTROL_AUDIT.md §9) — must run before the
            // virtual-health reduction below, since vanilla's own totem-death-protection check never
            // fires against this pipeline (see TotemProtectionService's class doc).
            if (TotemProtectionService.tryProtect(player, state, profile.getStatManager(), damageResult.getFinalDamage())) {
                state.setInCombat();
                debug("player victim=" + player.getName() + " TOTEM SAVED — virtualHealth " + before + " -> "
                        + state.getCurrentHealth() + " (would-have-dealt=" + damageResult.getFinalDamage() + ")");
            } else {
                // Apply damage to virtual health
                state.reduceHealth(damageResult.getFinalDamage());

                // Sync to visual hearts
                api.getPlayerManager().syncVisualHealth(player, state, profile.getStatManager());

                // Set combat timer
                state.setInCombat();

                debug("player victim=" + player.getName() + " virtualHealth " + before + " -> " + state.getCurrentHealth()
                        + " (dealt=" + damageResult.getFinalDamage() + ")");
            }

        } else {
            // --- MOB VICTIM LOGIC ---
            double before = damageResult.getVictim().getHealth();
            damageResult.getVictim().setHealth(Math.max(0, before - damageResult.getFinalDamage()));
            api.getMobManager().updateVisuals(damageResult.getVictim());
            debug("mob victim=" + damageResult.getVictim().getName() + " health " + before + " -> "
                    + damageResult.getVictim().getHealth() + " (dealt=" + damageResult.getFinalDamage() + ")");
        }

        // Apply invulnerability frames to prevent rapid overlapping DoT triggers
        int noDamageTicks = plugin.getConfig().getInt("combat.post-hit-no-damage-ticks", 20);
        damageResult.getVictim().setNoDamageTicks(noDamageTicks);
        debug("set noDamageTicks=" + noDamageTicks + " on victim=" + damageResult.getVictim().getName());
    }
}
