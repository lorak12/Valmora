package org.nakii.valmora.module.ui;

import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.util.Formatter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarUI {
    private final Valmora plugin;

    private final Map<UUID, QueuedMessage> activeOverrides = new HashMap<>();

    public ActionBarUI(Valmora plugin) {
        this.plugin = plugin;
    }

    private record QueuedMessage(String message, long expirationTimeMillis) {}

    public void showTemporary(Player player, String message, int durationTicks) {
        long expireTime = System.currentTimeMillis() + (durationTicks * 50L);
        activeOverrides.put(player.getUniqueId(), new QueuedMessage(message, expireTime));
    }

    public void tick(Player player) {
        UUID uuid = player.getUniqueId();
        QueuedMessage override = activeOverrides.get(uuid);

        if (override != null) {
            if (System.currentTimeMillis() > override.expirationTimeMillis()) {
                activeOverrides.remove(uuid);
            } else {
                player.sendActionBar(Formatter.format(override.message()));
                return;
            }
        }

        ValmoraPlayer vp = plugin.getPlayerManager().getSession(uuid);
        if (vp == null || vp.getActiveProfile() == null) return;

        StatManager stats = vp.getActiveProfile().getStatManager();
        SystemStats sys = plugin.getStatModule().getSystemStats();

        double maxHealth = stats.getStat(sys.getHealth());
        double defense = stats.getStat(sys.getDefense());
        double currentHealth = player.getHealth()
                * (maxHealth / player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());

        double maxMana = stats.getStat(sys.getMana());
        double currentMana = vp.getActiveProfile().getPlayerState().getCurrentMana();

        String baseBar = "<red>❤ " + (int) currentHealth + "/" + (int) maxHealth
                + " <dark_gray>| <green>❈ " + (int) defense + " Defense"
                + " <dark_gray>| <aqua>⛨ " + (int) currentMana + "/" + (int) maxMana + " Mana";
        player.sendActionBar(Formatter.format(baseBar));
    }
}
