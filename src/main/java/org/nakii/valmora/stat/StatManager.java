package org.nakii.valmora.stat;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatManager {

    private Map<Stat, Double> stats = new HashMap<>();

    public StatManager() {
        // Initialize stats with default values
        for (Stat stat : Stat.values()) {
            stats.put(stat, stat.getDefaultValue());
        }
    }

    public Map<Stat, Double> getSaveData() {
        return new HashMap<>(stats);
    }

    public void loadData(Map<Stat, Double> savedData) {
        this.stats.putAll(savedData);
    }

    public void addStat(Player player , Stat stat, Double value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative");
        }
        stats.put(stat, getStat(stat) + value);
        // Logic to add the stat value to the player's profile
        if (stat.equals(Stat.SPEED)) {
            recalculateAttributes(player);
        }
    }

    public void reduceStat(Player player, Stat stat, Double value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative");
        }
        stats.put(stat, getStat(stat) - value);
        // Logic to reduce the stat value from the player's profile
        if (stat.equals(Stat.SPEED)) {
            recalculateAttributes(player);
        }
    }

    public Double getStat(Stat stat) {
        return stats.getOrDefault(stat, 0.0);
    }

    public List<String> getStatNames() {
        return stats.keySet().stream().map(Stat::name).toList();
    }

    // Method to apply attribute changes on profile switch
    public void recalculateAttributes(Player player) {
        // Update movement speed based on the current SPEED stat
        player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue((0.1 * getStat(Stat.SPEED)) / 100);
    }

    public void recalculateStats(Player player){
        Valmora plugin = Valmora.getInstance();
        if (plugin == null) {
            Bukkit.getLogger().severe("CRITICAL: Valmora.getInstance() is NULL during stat recalculation!");
            return;
        }

        
        stats.clear();
        for (Stat stat : Stat.values()) {
            stats.put(stat, stat.getDefaultValue());
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack[] armor = player.getInventory().getArmorContents();
        
        ItemStack[] items = new ItemStack[] {mainHand, offHand, armor[0], armor[1], armor[2], armor[3]};
        
        for (ItemStack item : items) {
            if (item != null && item.hasItemMeta()) {
                Map<Stat, Double> itemStats = plugin.getStatStorage().loadStats(item.getItemMeta());
                if (!itemStats.isEmpty()) {
                    for (Map.Entry<Stat, Double> entry : itemStats.entrySet()) {
                        addStat(player, entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        recalculateAttributes(player);
    }

   

}
