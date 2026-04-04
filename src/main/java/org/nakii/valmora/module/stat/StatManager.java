package org.nakii.valmora.module.stat;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.item.AbilityDefinition;
import org.nakii.valmora.module.item.AbilityTrigger;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Keys;

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
        stats.put(stat, getStat(stat) + value);
        // Logic to add the stat value to the player's profile
        if (stat.equals(Stat.SPEED)) {
            recalculateAttributes(player);
        }
    }

    public void reduceStat(Player player, Stat stat, Double value) {
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
        ValmoraAPI api = ValmoraAPI.getInstance();
        
        stats.clear();
        for (Stat stat : Stat.values()) {
            stats.put(stat, stat.getDefaultValue());
        }

        for(PotionEffect effect : player.getActivePotionEffects()){
            if (effect.getDuration() > 20 * 60 * 60){
                player.removePotionEffect(effect.getType());
            }
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack[] armor = player.getInventory().getArmorContents();
        
        ItemStack[] items = new ItemStack[] {mainHand, offHand, armor[0], armor[1], armor[2], armor[3]};
        
        for (ItemStack item : items) {
            if (item != null && item.hasItemMeta()) {
                Map<Stat, Double> itemStats = api.getStatModule().loadStats(item.getItemMeta());
                if (!itemStats.isEmpty()) {
                    for (Map.Entry<Stat, Double> entry : itemStats.entrySet()) {
                        addStat(player, entry.getKey(), entry.getValue());
                    }
                }

                String itemId = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
                if (itemId != null) {
                    api.getItemManager().getItemRegistry().getItem(itemId).ifPresent(definition -> {
                        if (definition.getAbilities() != null) {
                            for (AbilityDefinition ability : definition.getAbilities().values()) {
                                if (ability.getTrigger() == AbilityTrigger.PASSIVE) {
                                    for (ConfiguredMechanic mechanic : ability.getMechanics()) {
                                        // Passives don't have targets usually, so we pass the player as the target
                                        mechanic.execute(player, player);
                                    }
                                }
                            }
                        }
                    });
                }
            }
        }
        recalculateAttributes(player);
        ValmoraProfile profile = api.getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();

        if (profile != null) {
             // Only cap stats to max value if the player is NOT in combat
            if (!profile.getPlayerState().isInCombat()) {
                profile.getPlayerState().capToMax(this);
            }
            //Sync visual health
            api.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), this);
        }
    }

   

}
