package org.nakii.valmora.module.stat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.item.AbilityDefinition;
import org.nakii.valmora.module.item.AbilityTrigger;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.stat.event.StatModifyEvent;
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

    /**
     * Like {@link #loadData(Map)}, but splits {@code savedData} against the live
     * {@link StatRegistry} first (Phase 5 Task 21 — see docs/REFACTOR/PROGRESS.md): recognized
     * keys load normally, unrecognized ones (e.g. a deleted/renamed custom stat role) are
     * excluded from {@code baseStats}/{@code effectiveStats} entirely — so they can never
     * silently affect gameplay math — and returned instead, for the caller to stash on
     * {@link org.nakii.valmora.module.profile.ValmoraProfile#getQuarantinedStats()} so they're
     * preserved and written back to the SQL row unchanged rather than being lost.
     *
     * @return the subset of {@code savedData} (lowercase keys) not present in the current registry
     */
    public Map<String, Double> loadDataAndQuarantineUnrecognized(Map<String, Double> savedData) {
        if (savedData == null) return Map.of();

        StatRegistry registry = ValmoraAPI.getInstance().getStatRegistry();
        Map<String, Double> recognized = new HashMap<>();
        Map<String, Double> quarantined = new HashMap<>();

        savedData.forEach((rawKey, value) -> {
            String key = rawKey.toLowerCase();
            if (registry.contains(key)) {
                recognized.put(key, value);
            } else {
                quarantined.put(key, value);
            }
        });

        loadData(recognized);
        return quarantined;
    }

    public void addStat(Player player, String statId, double value) {
        String key = statId.toLowerCase();
        double oldValue = baseStats.getOrDefault(key, 0.0);
        double newValue = oldValue + value;
        baseStats.put(key, newValue);
        recalculateStats(player);
        fireStatModify(player, key, oldValue, newValue);
    }

    public void reduceStat(Player player, String statId, double value) {
        String key = statId.toLowerCase();
        double oldValue = baseStats.getOrDefault(key, 0.0);
        double newValue = oldValue - value;
        baseStats.put(key, newValue);
        recalculateStats(player);
        fireStatModify(player, key, oldValue, newValue);
    }

    public void setStat(Player player, String statId, double value) {
        String key = statId.toLowerCase();
        double oldValue = baseStats.getOrDefault(key, 0.0);
        baseStats.put(key, value);
        recalculateStats(player);
        fireStatModify(player, key, oldValue, value);
    }

    /**
     * Fires a {@link StatModifyEvent} for a genuine base-stat change (not the internal
     * modifier-only path used every recalculation). Informational only — the mutation has
     * already happened by the time this fires.
     */
    private void fireStatModify(Player player, String statId, double oldValue, double newValue) {
        if (oldValue == newValue) return;
        // Bukkit.getServer() is null in unit tests (no live server bootstrapped, per AGENTS.md
        // §12) — guard rather than let StatManager's plain unit tests crash on this side effect.
        if (Bukkit.getServer() == null) return;
        Bukkit.getPluginManager().callEvent(new StatModifyEvent(player, statId, oldValue, newValue));
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
        ValmoraAPI.getInstance().getStatModule().recalculateAttributes(player, this);
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

            // Generic modifier framework: STAT effects from every attached modifier (reforges,
            // gemstones, traits, etc.) — see docs/Valmora_Modifier_Framework_Design.docx.
            var modifierModule = api.getModifierModule();
            if (modifierModule != null && modifierModule.getEngine() != null) {
                modifierModule.getEngine().contributeStats(item, player, this::addModifier);
                modifierModule.getEngine().applyPassiveAbilities(item, player);
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
                if (enchantDef == null) continue;
                if (enchantDef.getLogic() != null) {
                    enchantDef.getLogic().applyStats(player, entry.getValue(), this);
                }
                // YAML-declared stats: block (Phase 4 of the enchant overhaul) — runs alongside the
                // legacy Java hook above, not instead of it, matching every other tier's hybrid
                // Java+YAML coexistence.
                if (!enchantDef.getStatBonuses().isEmpty()) {
                    var statCtx = new SimpleExecutionContext(player, null, null, null);
                    statCtx.set("enchant:id", enchantDef.getId());
                    statCtx.set("enchant:level", entry.getValue());
                    for (Map.Entry<String, Expression> bonus : enchantDef.getStatBonuses().entrySet()) {
                        Object value = bonus.getValue().evaluate(statCtx);
                        if (value instanceof Number n) {
                            addModifier(bonus.getKey(), n.doubleValue());
                        }
                    }
                }
            }
        }

        var alchemyManager = api.getAlchemyManager();
        if (alchemyManager != null) {
            alchemyManager.applyEffectsToStats(player, this);
        }

        // Accessory bag stats (generic GUI storage-id "accessories" — see module/gui/storage)
        var playerSession = api.getPlayerManager().getSession(player.getUniqueId());
        if (playerSession != null && playerSession.getActiveProfile() != null) {
            for (ItemStack acc : playerSession.getActiveProfile().getStorage("accessories")) {
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
        var petModule = api.getPetModule();
        if (petModule != null) {
            petModule.applyPetStats(player, this);
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
