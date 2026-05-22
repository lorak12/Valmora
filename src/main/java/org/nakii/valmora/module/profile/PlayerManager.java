package org.nakii.valmora.module.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.combat.RegenTask;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.module.stat.StatManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PlayerManager implements ReloadableModule {
    private final DataStore dataStore;
    private final Map<UUID, ValmoraPlayer> activeSession = new HashMap<>();
    private final Valmora plugin;
    private BukkitTask regenTask;
    private final Random random = new Random();

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

        ProfileGui.register(plugin);

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
                String defaultName = plugin.getConfig().getString("profiles.default-name", "Earth");
                ValmoraProfile defaultProfile = new ValmoraProfile(defaultName);
                
                // Initialize their starting health to their Max Health
                String healthId = plugin.getStatModule().getSystemStats().getHealth();
                double maxHealth = defaultProfile.getStatManager().getStat(healthId);
                defaultProfile.getPlayerState().heal(maxHealth, defaultProfile.getStatManager());
                
                finalPlayer.addProfile(defaultProfile);
                plugin.getLogger().info("Created default profile for " + uuid);
            }   

            Runnable finalize = () -> {
                activeSession.put(uuid, finalPlayer);
                new PlayerProfileLoadedEvent(uuid, finalPlayer).callEvent();
                Player bukkitPlayer = Bukkit.getPlayer(uuid);
                if (bukkitPlayer != null) {
                    ValmoraProfile active = finalPlayer.getActiveProfile();
                    active.touchLastUsed();
                    applyPlayerInventory(bukkitPlayer, active);
                    active.getStatManager().recalculateAttributes(bukkitPlayer);
                    active.getStatManager().recalculateStats(bukkitPlayer);
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
        ProfileGui.unregister();

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

    public void handleQuit(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        ValmoraPlayer vp = activeSession.get(uuid);
        if (player != null && vp != null && vp.getActiveProfile() != null) {
            savePlayerInventory(player, vp.getActiveProfile());
        }
        ValmoraPlayer stored = activeSession.remove(uuid);
        if (stored != null) {
            dataStore.savePlayer(stored);
        }
    }

    public void switchProfile(Player player, String profileName) {
        ValmoraPlayer vp = activeSession.get(player.getUniqueId());
        for (ValmoraProfile profile : vp.getProfiles().values()) {
            if (profile.getName().equalsIgnoreCase(profileName)) {
                switchProfile(player, profile.getId());
                return;
            }
        }
        player.sendMessage(Component.text("Profile not found: " + profileName, NamedTextColor.RED));
    }

    public void switchProfile(Player player, UUID profileId) {
        ValmoraPlayer vp = activeSession.get(player.getUniqueId());
        if (vp == null) return;
        ValmoraProfile current = vp.getActiveProfile();
        if (current != null) savePlayerInventory(player, current);
        vp.setActiveProfile(profileId);
        ValmoraProfile next = vp.getActiveProfile();
        if (next != null) {
            next.touchLastUsed();
            applyPlayerInventory(player, next);
            next.getStatManager().recalculateStats(player);
            next.getStatManager().recalculateAttributes(player);
        }
    }

    public ValmoraPlayer getSession(UUID uuid) {
        return activeSession.get(uuid);
    }

    public boolean isLoaded(UUID uuid) {
        return activeSession.containsKey(uuid);
    }

    public int getMaxProfiles() {
        return plugin.getConfig().getInt("profiles.max-profiles", 4);
    }

    public String pickNextProfileName(ValmoraPlayer vp) {
        List<String> used = new ArrayList<>();
        for (ValmoraProfile p : vp.getProfiles().values()) used.add(p.getName().toLowerCase());

        List<String> pool = plugin.getConfig().getStringList("profiles.planet-names");
        List<String> available = new ArrayList<>();
        for (String name : pool) {
            if (!used.contains(name.toLowerCase())) available.add(name);
        }
        if (available.isEmpty()) return "Profile " + (vp.getProfiles().size() + 1);
        return available.get(random.nextInt(available.size()));
    }

    public void createProfile(UUID uuid, String profileName) {
        ValmoraPlayer vp = activeSession.get(uuid);
        if (vp == null) return;
        if (vp.getProfiles().size() >= getMaxProfiles()) return;
        ValmoraProfile newProfile = new ValmoraProfile(profileName);
        vp.addProfile(newProfile);
        dataStore.savePlayer(vp);
    }

    public void createNextProfile(UUID uuid) {
        ValmoraPlayer vp = activeSession.get(uuid);
        if (vp == null) return;
        createProfile(uuid, pickNextProfileName(vp));
    }

    public void deleteProfile(UUID playerUuid, UUID profileId) {
        ValmoraPlayer vp = activeSession.get(playerUuid);
        if (vp == null) return;
        vp.removeProfile(profileId);
        dataStore.deleteProfile(profileId);
        dataStore.savePlayer(vp);
    }

    // Legacy command-compatible overload; keep for ProfileCommand
    public void deleteProfile(UUID uuid, String profileName) {
        ValmoraPlayer vp = activeSession.get(uuid);
        if (vp == null) return;
        vp.getProfiles().values().stream()
                .filter(p -> p.getName().equalsIgnoreCase(profileName))
                .findFirst()
                .ifPresent(p -> deleteProfile(uuid, p.getId()));
    }

    private void savePlayerInventory(Player player, ValmoraProfile profile) {
        PlayerInventory inv = player.getInventory();
        profile.setSavedInventory(inv.getStorageContents().clone());
        profile.setSavedArmor(inv.getArmorContents().clone());
        ItemStack offhand = inv.getItemInOffHand();
        profile.setSavedOffhand(offhand.getType().isAir() ? null : offhand.clone());
    }

    private void applyPlayerInventory(Player player, ValmoraProfile profile) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        if (profile.getSavedInventory() != null) inv.setStorageContents(profile.getSavedInventory());
        if (profile.getSavedArmor() != null) inv.setArmorContents(profile.getSavedArmor());
        if (profile.getSavedOffhand() != null) inv.setItemInOffHand(profile.getSavedOffhand());
    }

    public Collection<ValmoraPlayer> getAllSessions() {
        return activeSession.values();
    }

    public void syncVisualHealth(org.bukkit.entity.Player player, PlayerState state, StatManager stats) {
        double maxHealth = stats.getStat(plugin.getStatModule().getSystemStats().getHealth());
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
