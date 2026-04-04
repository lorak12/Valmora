package org.nakii.valmora.module.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.combat.RegenTask;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager implements ReloadableModule {
    private final DataStore dataStore;
    private final Map<UUID, ValmoraPlayer> activeSession = new HashMap<>();
    private final Valmora plugin;
    private BukkitTask regenTask;

    public PlayerManager(Valmora plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    private PlayerConnectionListener connectionListener;

    @Override
    public void onEnable() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, new RegenTask(plugin), 0L, 20L);

        this.connectionListener = new PlayerConnectionListener(this);
        plugin.getServer().getPluginManager().registerEvents(connectionListener, plugin);

        // Load existing players SYNCHRONOUSLY if this was a hot-reload to prevent async gap NPEs
        for (Player online : Bukkit.getOnlinePlayers()) {
            handleJoin(online.getUniqueId(), true);
        }
    }

    public void handleJoin(UUID uuid) {
        handleJoin(uuid, false);
    }

    public void handleJoin(UUID uuid, boolean sync) {
        java.util.function.Consumer<ValmoraPlayer> processor = (player) -> {
            ValmoraPlayer finalPlayer = player != null ? player : new ValmoraPlayer(uuid);
            if (finalPlayer.getProfiles().isEmpty()) {
                ValmoraProfile defaultProfile = new ValmoraProfile("Default");
                
                // Initialize their starting health to their Max Health
                double maxHealth = defaultProfile.getStatManager().getStat(Stat.HEALTH);
                defaultProfile.getPlayerState().heal(maxHealth, defaultProfile.getStatManager());
                
                finalPlayer.addProfile(defaultProfile);
                plugin.getLogger().info("Created default profile for " + uuid);
            }   

            Runnable finalize = () -> {
                activeSession.put(uuid, finalPlayer);
                Player bukkitPlayer = Bukkit.getPlayer(uuid);
                if (bukkitPlayer != null) {
                    finalPlayer.getActiveProfile().getStatManager().recalculateAttributes(bukkitPlayer);
                    // Also recalculate stats to ensure they are correct immediately
                    finalPlayer.getActiveProfile().getStatManager().recalculateStats(bukkitPlayer);
                }
            };

            if (sync) {
                finalize.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, finalize);
            }
        };

        if (sync) {
            processor.accept(dataStore.loadPlayer(uuid).join());
        } else {
            dataStore.loadPlayer(uuid).thenAcceptAsync(processor);
        }
    }

    @Override
    public void onDisable() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }

        if (connectionListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(connectionListener);
        }
        
        for (ValmoraPlayer player : activeSession.values()) {
            dataStore.savePlayer(player).join(); 
        }
        activeSession.clear();
    }

    @Override
    public String getId() {
        return "profiles";
    }

    public void handleQuit(UUID uuid){
        ValmoraPlayer player = activeSession.remove(uuid);
        if (player != null) {
            dataStore.savePlayer(player);
            // Async execution handles the save seamlessly without lag spikes!
        }
    }

    public void switchProfile(Player player, String profileName){
        ValmoraPlayer vp = activeSession.get(player.getUniqueId());

        for (ValmoraProfile profile: vp.getProfiles().values()){
            if (profile.getName().equalsIgnoreCase(profileName)){
                vp.setActiveProfile(profile.getId());
                vp.getActiveProfile().getStatManager().recalculateStats(player);
                return;
            }
        }
        player.sendMessage(Component.text("Profile not found: " + profileName, NamedTextColor.RED));
    }

    public ValmoraPlayer getSession(UUID uuid) {
        return activeSession.get(uuid);
    }

    public void createProfile(UUID uuid, String profileName) {
        ValmoraPlayer vp = activeSession.get(uuid);
        ValmoraProfile newProfile = new ValmoraProfile(profileName);
        vp.addProfile(newProfile);
        dataStore.savePlayer(vp);
    }

    public void deleteProfile(UUID uuid, String profileName) {
        ValmoraPlayer vp = activeSession.get(uuid);
        ValmoraProfile activeProfile = vp.getActiveProfile();
        if (activeProfile != null) {
            vp.removeProfile(activeProfile.getId());
            dataStore.savePlayer(vp);
        }
    }

    public Collection<ValmoraPlayer> getAllSessions() {
        return activeSession.values();
    }

    public void syncVisualHealth(org.bukkit.entity.Player player, PlayerState state, StatManager stats) {
        double maxHealth = stats.getStat(Stat.HEALTH);
        double current = state.getCurrentHealth();

        // Calculate percentage of health remaining
        double percentage = current / maxHealth;
        
        // Map it to 20 vanilla HP (10 hearts)
        double visualHealth = percentage * 20.0;

        // Prevent vanilla death if they still have custom health > 0
        if (current > 0 && visualHealth < 0.5) {
            visualHealth = 0.5; // Half a heart minimum if alive
        }

        // Use Paper's health scaling so the UI is always locked to 10 hearts
        player.setHealthScale(20.0); 
        player.setHealthScaled(true);

        if (current <= 0) {
            player.setHealth(0); // Trigger actual vanilla death event!
        } else {
            player.setHealth(visualHealth);
        }
}

}
