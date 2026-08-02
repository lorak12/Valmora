package org.nakii.valmora.module.stat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.nakii.valmora.Valmora;
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

    private final Map<String, Double> effectiveStats = new HashMap<>();
    private final Map<String, Double> baseStats = new HashMap<>();

    public StatManager() {
        StatRegistry registry = ValmoraAPI.getInstance().getStatRegistry();
        for (StatDefinition def : registry.values()) {
            baseStats.put(def.getId(), def.getDefaultValue());
            effectiveStats.put(def.getId(), def.getDefaultValue());
        }
    }

    public Map<String, Double> getSaveData() {
        return new HashMap<>(baseStats);
    }

    public void loadData(Map<String, Double> savedData) {
        if (savedData == null) return;
        // Normalize keys to lowercase to handle any legacy uppercase keys
        savedData.forEach((k, v) -> this.baseStats.put(k.toLowerCase(), v));
        this.effectiveStats.putAll(baseStats);
    }

    public void addStat(Player player, String statId, double value) {
        String key = statId.toLowerCase();
        baseStats.put(key, baseStats.getOrDefault(key, 0.0) + value);
        recalculateStats(player);
    }

    public void reduceStat(Player player, String statId, double value) {
        String key = statId.toLowerCase();
        baseStats.put(key, baseStats.getOrDefault(key, 0.0) - value);
        recalculateStats(player);
    }

    public void setStat(Player player, String statId, double value) {
        baseStats.put(statId.toLowerCase(), value);
        recalculateStats(player);
    }

    public void resetStat(Player player, String statId) {
        ValmoraAPI.getInstance().getStatRegistry().get(statId)
                .ifPresent(def -> setStat(player, statId, def.getDefaultValue()));
    }

    public void addModifier(String statId, double value) {
        String key = statId.toLowerCase();
        effectiveStats.put(key, effectiveStats.getOrDefault(key, 0.0) + value);
    }

    public double getStat(String statId) {
        return effectiveStats.getOrDefault(statId.toLowerCase(), 0.0);
    }

    public List<String> getStatIds() {
        return effectiveStats.keySet().stream().toList();
    }

    // Kept for hot-reload / profile-switch attribute sync
    public void recalculateAttributes(Player player) {
        Valmora.getInstance().getStatModule().recalculateAttributes(player, this);
    }

    public void recalculateStats(Player player) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        StatRegistry registry = api.getStatRegistry();
        StatModule statModule = api.getStatModule();

        effectiveStats.clear();
        effectiveStats.putAll(baseStats);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getDuration() > 20 * 60 * 60) {
                player.removePotionEffect(effect.getType());
            }
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack[] armor = player.getInventory().getArmorContents();
        // armor[0]=boots, [1]=leggings, [2]=chestplate, [3]=helmet
        ItemStack[] items = new ItemStack[]{mainHand, offHand, armor[0], armor[1], armor[2], armor[3]};

        for (ItemStack item : items) {
            if (item == null || !item.hasItemMeta()) continue;

            if (statModule != null) {
                Map<String, Double> itemStats = statModule.loadStats(item.getItemMeta());
                for (Map.Entry<String, Double> entry : itemStats.entrySet()) {
                    addModifier(entry.getKey(), entry.getValue());
                }
            }

            String itemId = item.getItemMeta().getPersistentDataContainer()
                    .get(Keys.ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
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
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                var enchantDef = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                if (enchantDef != null && enchantDef.getLogic() != null) {
                    enchantDef.getLogic().applyStats(player, entry.getValue(), this);
                }
            }
        }

        var alchemyManager = api.getAlchemyManager();
        if (alchemyManager != null) {
            alchemyManager.applyEffectsToStats(player, this);
        }

        // Accessory bag stats
        var playerSession = api.getPlayerManager().getSession(player.getUniqueId());
        if (playerSession != null && playerSession.getActiveProfile() != null) {
            for (ItemStack acc : playerSession.getActiveProfile().getAccessoryItems()) {
                if (acc == null || !acc.hasItemMeta()) continue;
                if (statModule != null) {
                    Map<String, Double> accStats = statModule.loadStats(acc.getItemMeta());
                    for (Map.Entry<String, Double> entry : accStats.entrySet()) {
                        addModifier(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        // Pet stat bonuses (applied by PetModule if a pet is summoned)
        var valmora = org.nakii.valmora.Valmora.getInstance();
        if (valmora != null && valmora.getPetModule() != null) {
            valmora.getPetModule().applyPetStats(player, this);
        }

        // Armor set bonuses (e.g. full Young Dragon → +Speed).
        org.nakii.valmora.module.item.set.SetBonusService.applyTo(player, this);

        // Progression-tree stat bonuses (e.g. Geomancy's Mining Speed/Fortune/Spread branches).
        org.nakii.valmora.module.progression.ProgressionStatService.applyTo(player, this);

        // Temporary stat modifiers granted by item abilities (e.g. "+100 Speed for 30s").
        org.nakii.valmora.module.item.TemporaryStatService.applyTo(player.getUniqueId(), this);

        // Cap effective stats to their defined maxValue (fixes CRIT_CHANCE and LUCK never being capped)
        for (StatDefinition def : registry.values()) {
            if (def.getMaxValue() < Double.MAX_VALUE) {
                effectiveStats.put(def.getId(),
                        Math.min(effectiveStats.getOrDefault(def.getId(), 0.0), def.getMaxValue()));
            }
        }

        // Apply vanilla attribute mappings (e.g. MOVEMENT_SPEED for the speed stat)
        if (statModule != null) statModule.recalculateAttributes(player, this);

        var session = api.getPlayerManager().getSession(player.getUniqueId());
        if (session == null) return;

        ValmoraProfile profile = session.getActiveProfile();
        if (profile != null) {
            if (!profile.getPlayerState().isInCombat()) {
                profile.getPlayerState().capToMax(this);
            }
            api.getPlayerManager().syncVisualHealth(player, profile.getPlayerState(), this);
        }
    }
}
