package org.nakii.valmora.module.gui.sign;

import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SignInputManager {

    private final Valmora plugin;
    private final Map<UUID, String> pendingPropKeys = new HashMap<>();

    public SignInputManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public void init() {}

    public void cleanup() {
        pendingPropKeys.clear();
    }

    /**
     * Opens a virtual sign editor for the player (no block placed in the world).
     * Always returns true; the boolean is kept for API compatibility with the factory.
     */
    public boolean openSign(Player player, String propKey, @Nullable String placeholder) {
        pendingPropKeys.put(player.getUniqueId(), propKey);
        player.openVirtualSign(player.getLocation(), Side.FRONT);
        return true;
    }

    public @Nullable String getPendingPropKey(UUID uuid) {
        return pendingPropKeys.get(uuid);
    }

    public void clearPending(UUID uuid) {
        pendingPropKeys.remove(uuid);
    }
}
