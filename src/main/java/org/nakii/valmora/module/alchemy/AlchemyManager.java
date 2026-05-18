package org.nakii.valmora.module.alchemy;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.alchemy.effect.ActiveEffect;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.HardcodedAlchemyEffect;
import org.nakii.valmora.module.alchemy.modifier.AlchemyModifier;
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

    /** Maps a brewing ingredient to the effect and base level it produces. */
    public record BrewTier(AlchemyEffect effect, int baseLevel) {}

    private final Map<UUID, List<ActiveEffect>> activeEffects = new ConcurrentHashMap<>();
    private final Map<String, HardcodedAlchemyEffect> hardcodedEffects = new HashMap<>();
    private final Map<String, BrewTier> ingredientIndex = new HashMap<>();
    private final Map<String, AlchemyEffect> effectRegistry = new HashMap<>();
    private final Map<String, AlchemyModifier> modifierRegistry = new HashMap<>();

    private final int maxActiveEffects;

    public AlchemyManager(int maxActiveEffects) {
        this.maxActiveEffects = maxActiveEffects;
    }

    // ── Registry ──────────────────────────────────────────────────────────

    public void registerEffect(AlchemyEffect effect) {
        effectRegistry.put(effect.getId().toLowerCase(), effect);
        for (AlchemyEffect.Tier tier : effect.getTiers()) {
            ingredientIndex.put(tier.ingredientKey().toLowerCase(), new BrewTier(effect, tier.level()));
        }
    }

    public void registerHardcodedEffect(HardcodedAlchemyEffect effect) {
        hardcodedEffects.put(effect.getEffectId().toLowerCase(), effect);
    }

    public void registerModifier(AlchemyModifier modifier) {
        modifierRegistry.put(modifier.getItemId(), modifier);
    }

    public Optional<AlchemyEffect> getEffect(String id) {
        return Optional.ofNullable(effectRegistry.get(id.toLowerCase()));
    }

    public Optional<BrewTier> getBrewTier(String ingredientKey) {
        return Optional.ofNullable(ingredientIndex.get(ingredientKey.toLowerCase()));
    }

    public Optional<AlchemyModifier> getModifier(String itemId) {
        return Optional.ofNullable(modifierRegistry.get(itemId));
    }

    public Map<String, AlchemyEffect> getAllEffects() {
        return Collections.unmodifiableMap(effectRegistry);
    }

    public void clear() {
        effectRegistry.clear();
        ingredientIndex.clear();
        modifierRegistry.clear();
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
            for (String statId : def.getStats().keySet()) {
                double value = def.getStatValue(statId, ae.level());
                if (value != 0) statManager.addModifier(statId, value);
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
