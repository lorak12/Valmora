package org.nakii.valmora.api.economy;

import org.bukkit.entity.Player;

public class NoOpEconomyService implements EconomyService {

    @Override
    public void addCoins(Player player, double amount) {
        // Placeholder — replace with real economy backend
    }

    @Override
    public void removeCoins(Player player, double amount) {
    }

    @Override
    public double getCoins(Player player) {
        return 0;
    }

    @Override
    public boolean hasCoins(Player player, double amount) {
        return false;
    }
}
