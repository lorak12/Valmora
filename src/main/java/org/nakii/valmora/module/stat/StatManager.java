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
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatManager {

    private final Map<Stat, Double> effectiveStats = new HashMap<>();
    private final Map<Stat, Double> baseStats = new HashMap<>();

    public StatManager() {
        // Initialize stats with default values
        for (Stat stat : Stat.values()) {
            baseStats.put(stat, stat.getDefaultValue());
            effectiveStats.put(stat, stat.getDefaultValue());
        }
    }

    public Map<Stat, Double> getSaveData() {
        return new HashMap<>(baseStats);
    }

    public void loadData(Map<Stat, Double> savedData) {
        this.baseStats.putAll(savedData);
        this.effectiveStats.putAll(baseStats);
    }

    /**
     * Adds a permanent stat to the player's profile (base stats).
     * Triggers a recalculation.
     */
    public void addStat(Player player , Stat stat, Double value) {
        baseStats.put(stat, baseStats.getOrDefault(stat, 0.0) + value);
        recalculateStats(player);
    }

    /**
     * Reduces a permanent stat from the player's profile (base stats).
     * Triggers a recalculation.
     */
    public void reduceStat(Player player, Stat stat, Double value) {
        baseStats.put(stat, baseStats.getOrDefault(stat, 0.0) - value);
        recalculateStats(player);
    }

    /**
     * Adds a temporary stat modifier to the effective stats.
     * Should only be called during recalculateStats.
     */
    public void addModifier(Stat stat, double value) {
        effectiveStats.put(stat, effectiveStats.getOrDefault(stat, 0.0) + value);
    }

    public Double getStat(Stat stat) {
        return effectiveStats.getOrDefault(stat, 0.0);
    }

    public List<String> getStatNames() {
        return effectiveStats.keySet().stream().map(Stat::name).toList();
    }

    // Method to apply attribute changes on profile switch
    public void recalculateAttributes(Player player) {
        // Update movement speed based on the current SPEED stat
        player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue((0.1 * getStat(Stat.SPEED)) / 100);
    }

    public void recalculateStats(Player player){
        ValmoraAPI api = ValmoraAPI.getInstance();
        
        effectiveStats.clear();
        effectiveStats.putAll(baseStats);

        for(PotionEffect effect : player.getActivePotionEffects()){
            if (effect.getDuration() > 20 * 60 * 60){
                player.removePotionEffect(effect.getType());
            }
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack[] armor = player.getInventory().getArmorContents();
        
        // armor[0]=boots, [1]=leggings, [2]=chestplate, [3]=helmet
        ItemStack[] items = new ItemStack[] {mainHand, offHand, armor[0], armor[1], armor[2], armor[3]};
        
        for (ItemStack item : items) {
            if (item != null && item.hasItemMeta()) {
                Map<Stat, Double> itemStats = api.getStatModule().loadStats(item.getItemMeta());
                if (!itemStats.isEmpty()) {
                    for (Map.Entry<Stat, Double> entry : itemStats.entrySet()) {
                        addModifier(entry.getKey(), entry.getValue());
                    }
                }

                String itemId = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
                if (itemId != null) {
                    api.getItemManager().getItemRegistry().getItem(itemId).ifPresent(definition -> {
                        if (definition.getAbilities() != null) {
                            for (AbilityDefinition ability : definition.getAbilities().values()) {
                                if (ability.getTrigger() == AbilityTrigger.PASSIVE) {
                                    for (ConfiguredMechanic mechanic : ability.getMechanics()) {
                                        mechanic.execute(player, player);
                                    }
                                }
                            }
                        }
                    });
                }

                Map<String, Integer> enchants = EnchantmentHelper.getEnchantments(item);
                if (!enchants.isEmpty()) {
                    for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                        var enchantDef = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                        if (enchantDef != null && enchantDef.getLogic() != null) {
                            enchantDef.getLogic().applyStats(player, entry.getValue(), this);
                        }
                    }
                }
            }
        }
        recalculateAttributes(player);
        
        var session = api.getPlayerManager().getSession(player.getUniqueId());
        if (session == null) return;
        
        ValmoraProfile profile = session.getActiveProfile();

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
