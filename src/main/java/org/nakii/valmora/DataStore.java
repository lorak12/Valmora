package org.nakii.valmora;

import org.nakii.valmora.profile.ValmoraPlayer;

import java.util.UUID;

public interface DataStore {
    ValmoraPlayer loadPlayer(UUID uuid);
    void savePlayer(ValmoraPlayer player);
}
