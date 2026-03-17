package org.nakii.valmora.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nakii.valmora.DataStore;

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
        ValmoraPlayer player = dataStore.loadPlayer(uuid);
        if (player == null){
            player = new ValmoraPlayer(uuid);
        }

        if (player.getProfiles().isEmpty()){
            ValmoraProfile defaultProfile = new ValmoraProfile("Default");
            player.addProfile(defaultProfile);
            System.out.println("Created default profile for " + uuid);
        }

        activeSession.put(uuid, player);

        player.getActiveProfile().getStatManager().recalculateAttributes(Bukkit.getPlayer(uuid));

        dataStore.savePlayer(player);
    }

    public void handleQuit(UUID uuid){
        ValmoraPlayer player = activeSession.remove(uuid);
        if (player != null) {
            dataStore.savePlayer(player);
        }
    }

    public void switchProfile(Player player, String profileName){
        ValmoraPlayer vp = activeSession.get(player.getUniqueId());

        for (ValmoraProfile profile: vp.getProfiles().values()){
            if (profile.getName().equalsIgnoreCase(profileName)){
                vp.setActiveProfile(profile.getId());
                vp.getActiveProfile().getStatManager().recalculateAttributes(player);
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

}
