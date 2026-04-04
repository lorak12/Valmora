package org.nakii.valmora.module.ui;

import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.util.Formatter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarUI {
    private final Valmora plugin;
    
    // Stores the active temporary message and how many ticks remain
    private final Map<UUID, QueuedMessage> activeOverrides = new HashMap<>();

    public ActionBarUI(Valmora plugin) {
        this.plugin = plugin;
    }

    // A simple record to hold our temporary action bar data
    private record QueuedMessage(String message, long expirationTimeMillis) {}

    /**
     * Show a temporary message on the action bar.
     * @param durationTicks 20 ticks = 1 second
     */
    public void showTemporary(Player player, String message, int durationTicks) {
        long expireTime = System.currentTimeMillis() + (durationTicks * 50L);
        activeOverrides.put(player.getUniqueId(), new QueuedMessage(message, expireTime));
    }

    // Called automatically by the UIManager clock
    public void tick(Player player) {
        UUID uuid = player.getUniqueId();
        QueuedMessage override = activeOverrides.get(uuid);

        // Check if there is an active override message
        if (override != null) {
            if (System.currentTimeMillis() > override.expirationTimeMillis()) {
                activeOverrides.remove(uuid); // Timer expired, remove it
            } else {
                // Show the override message
                player.sendActionBar(Formatter.format(override.message()));
                return; // Skip base stats
            }
        }

        // --- BASE STATE (If no override is active) ---
        ValmoraPlayer vp = plugin.getPlayerManager().getSession(uuid);
        if (vp == null || vp.getActiveProfile() == null) return;

        StatManager stats = vp.getActiveProfile().getStatManager();
        double maxHealth = stats.getStat(Stat.HEALTH);
        double defense = stats.getStat(Stat.DEFENSE);
        double currentHealth = player.getHealth() * (maxHealth / player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()); 

        double maxMana = stats.getStat(Stat.MANA);
        double currentMana = vp.getActiveProfile().getPlayerState().getCurrentMana();

        // Example: ❤ 500/500 | ❈ 150 Defense | ⛨ 100/100 Mana
        String baseBar = "<red>❤ " + (int)currentHealth + "/" + (int)maxHealth + " <dark_gray>| <green>❈ " + (int)defense + " Defense" + " <dark_gray>| <aqua>⛨ " + (int)currentMana + "/" + (int)maxMana + " Mana";
        player.sendActionBar(Formatter.format(baseBar));
    }
}
