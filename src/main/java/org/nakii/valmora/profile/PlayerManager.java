package org.nakii.valmora.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.database.DataStore;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {
    private final DataStore dataStore;
    private final Map<UUID, ValmoraPlayer> activeSession = new HashMap<>();

    public PlayerManager(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void handleJoin(UUID uuid){
        dataStore.loadPlayer(uuid).thenAcceptAsync(player -> {
            ValmoraPlayer finalPlayer = player != null ? player : new ValmoraPlayer(uuid);

            if (finalPlayer.getProfiles().isEmpty()){
                ValmoraProfile defaultProfile = new ValmoraProfile("Default");
                finalPlayer.addProfile(defaultProfile);
                System.out.println("Created default profile for " + uuid);
            }

            // Sync back to main thread to modify server cache and Bukkit entities safely
            Bukkit.getScheduler().runTask(Valmora.getInstance(), () -> {
                activeSession.put(uuid, finalPlayer);
                Player bukkitPlayer = Bukkit.getPlayer(uuid);
                if (bukkitPlayer != null) {
                    finalPlayer.getActiveProfile().getStatManager().recalculateAttributes(bukkitPlayer);
                }
            });
        });
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

}
