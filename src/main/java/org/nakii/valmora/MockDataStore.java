package org.nakii.valmora;

import org.nakii.valmora.profile.ValmoraPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MockDataStore implements DataStore{

    private final Map<UUID, ValmoraPlayer> fakeDb = new HashMap<>();

    @Override
    public ValmoraPlayer loadPlayer(UUID uuid) {
        return fakeDb.get(uuid);
    }

    @Override
    public void savePlayer(ValmoraPlayer player) {
        fakeDb.put(player.getUuid(), player);
        System.out.println("[Database] Saved data for " + player.getUuid());
    }
}
