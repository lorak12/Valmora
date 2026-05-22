package org.nakii.valmora.module.ui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;
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

    private UIConfig config;

    public ActionBarUI(Valmora plugin) {
        this.plugin = plugin;
    }

    public void setConfig(UIConfig config) {
        this.config = config;
    }

    private record QueuedMessage(String message, long expirationTimeMillis) {}

    public void showTemporary(Player player, String message, int durationTicks) {
        long expireTime = System.currentTimeMillis() + (durationTicks * 50L);
        activeOverrides.put(player.getUniqueId(), new QueuedMessage(message, expireTime));
    }

    public void tick(Player player) {
        DialogueManager dialogueMgr = plugin.getDialogueManager();
        if (dialogueMgr != null && dialogueMgr.getSession(player.getUniqueId()) != null) return;

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

        // Config-driven template
        if (config != null && !config.getActionBarDefault().isEmpty()) {
            try {
                VariableResolver resolver = ValmoraAPI.getInstance().getScriptModule().getVariableResolver();
                var ctx = new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());
                String resolved = resolver.resolveTemplate(config.getActionBarDefault(), ctx);
                player.sendActionBar(Formatter.format(resolved));
                return;
            } catch (Exception ignored) {}
        }

        // Fallback: hard-coded bar (used when config hasn't loaded yet)
        legacyBar(player);
    }

    private void legacyBar(Player player) {
        ValmoraPlayer vp = plugin.getPlayerManager().getSession(player.getUniqueId());
        if (vp == null || vp.getActiveProfile() == null) return;

        StatManager stats = vp.getActiveProfile().getStatManager();
        SystemStats sys = plugin.getStatModule().getSystemStats();

        double maxHealth = stats.getStat(sys.getHealth());
        double defense = stats.getStat(sys.getDefense());
        double currentHealth = player.getHealth()
                * (maxHealth / player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        double maxMana = stats.getStat(sys.getMana());
        double currentMana = vp.getActiveProfile().getPlayerState().getCurrentMana();

        String bar = "<red>❤ " + (int) currentHealth + "/" + (int) maxHealth
                + " <dark_gray>| <green>❈ " + (int) defense + " Defense"
                + " <dark_gray>| <aqua>⛨ " + (int) currentMana + "/" + (int) maxMana + " Mana";
        player.sendActionBar(Formatter.format(bar));
    }
}
