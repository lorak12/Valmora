package org.nakii.valmora.api.economy;

import org.bukkit.entity.Player;

public interface EconomyService {
    void addCoins(Player player, double amount);
    void removeCoins(Player player, double amount);
    double getCoins(Player player);
    boolean hasCoins(Player player, double amount);
}
