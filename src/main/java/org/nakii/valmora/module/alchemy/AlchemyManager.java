package org.nakii.valmora.module.alchemy;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.alchemy.effect.ActiveEffect;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AlchemyManager {

    private final Map<UUID, List<ActiveEffect>> activeEffects = new ConcurrentHashMap<>();
    private final Map<String, HardcodedAlchemyEffect> hardcodedEffects = new HashMap<>();
    private final Map<Material, AlchemyEffect> ingredientIndex = new HashMap<>();
    private final Map<String, AlchemyEffect> effectRegistry = new HashMap<>();

    private final int maxActiveEffects;

    public AlchemyManager(int maxActiveEffects) {
        this.maxActiveEffects = maxActiveEffects;
    }

    // ── Registry ──────────────────────────────────────────────────────────

    public void registerEffect(AlchemyEffect effect) {
        effectRegistry.put(effect.getId().toLowerCase(), effect);
        ingredientIndex.put(effect.getIngredient(), effect);
    }

    public void registerHardcodedEffect(HardcodedAlchemyEffect effect) {
        hardcodedEffects.put(effect.getEffectId().toLowerCase(), effect);
    }

    public Optional<AlchemyEffect> getEffect(String id) {
        return Optional.ofNullable(effectRegistry.get(id.toLowerCase()));
    }

    public Optional<AlchemyEffect> getEffectByIngredient(Material material) {
        return Optional.ofNullable(ingredientIndex.get(material));
    }

    public Map<String, AlchemyEffect> getAllEffects() {
        return Collections.unmodifiableMap(effectRegistry);
    }

    public void clear() {
        effectRegistry.clear();
        ingredientIndex.clear();
    }

    // ── Active Effect Application ─────────────────────────────────────────

    public void applyEffect(LivingEntity entity, String effectId, int level, int durationSeconds) {
        UUID uuid = entity.getUniqueId();
        List<ActiveEffect> effects = activeEffects.computeIfAbsent(uuid, k -> new ArrayList<>());

        effects.removeIf(e -> e.effectId().equalsIgnoreCase(effectId));

        if (entity instanceof Player && effects.size() >= maxActiveEffects) return;

        long expiresAt = System.currentTimeMillis() + (long) durationSeconds * 1000;
        effects.add(new ActiveEffect(effectId, level, expiresAt));

        HardcodedAlchemyEffect hardcoded = hardcodedEffects.get(effectId.toLowerCase());
        if (hardcoded != null) hardcoded.onApply(entity, level, durationSeconds);

        if (entity instanceof Player player) {
            recalculatePlayerStats(player);
        }
    }

    public void removeEffect(LivingEntity entity, String effectId) {
        List<ActiveEffect> effects = activeEffects.get(entity.getUniqueId());
        if (effects == null) return;
        effects.removeIf(e -> e.effectId().equalsIgnoreCase(effectId));
        if (entity instanceof Player player) recalculatePlayerStats(player);
    }

    public List<ActiveEffect> getActiveEffects(UUID uuid) {
        List<ActiveEffect> effects = activeEffects.get(uuid);
        if (effects == null) return List.of();
        return Collections.unmodifiableList(effects);
    }

    public void clearAllEffects(UUID uuid) {
        activeEffects.remove(uuid);
    }

    // ── Tick ─────────────────────────────────────────────────────────────

    public void tick(Player player) {
        List<ActiveEffect> effects = activeEffects.get(player.getUniqueId());
        if (effects == null || effects.isEmpty()) return;

        boolean anyExpired = false;
        Iterator<ActiveEffect> it = effects.iterator();
        while (it.hasNext()) {
            ActiveEffect ae = it.next();
            if (ae.isExpired()) {
                it.remove();
                anyExpired = true;
                HardcodedAlchemyEffect hardcoded = hardcodedEffects.get(ae.effectId().toLowerCase());
                if (hardcoded != null) hardcoded.onExpire(player, ae.level());
            } else {
                HardcodedAlchemyEffect hardcoded = hardcodedEffects.get(ae.effectId().toLowerCase());
                if (hardcoded != null) hardcoded.onTick(player, ae.level());
            }
        }

        if (anyExpired) recalculatePlayerStats(player);
    }

    // ── Stat Integration ──────────────────────────────────────────────────

    public void applyEffectsToStats(Player player, StatManager statManager) {
        List<ActiveEffect> effects = activeEffects.get(player.getUniqueId());
        if (effects == null) return;

        for (ActiveEffect ae : effects) {
            if (ae.isExpired()) continue;
            AlchemyEffect def = effectRegistry.get(ae.effectId().toLowerCase());
            if (def == null) continue;
            for (Stat stat : def.getStats().keySet()) {
                double value = def.getStatValue(stat, ae.level());
                if (value != 0) statManager.addModifier(stat, value);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void recalculatePlayerStats(Player player) {
        var session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session == null) return;
        var profile = session.getActiveProfile();
        if (profile == null) return;
        profile.getStatManager().recalculateStats(player);
    }
}
