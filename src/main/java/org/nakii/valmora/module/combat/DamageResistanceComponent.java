package org.nakii.valmora.module.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.util.Keys;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Per-damage-type resistance percentages stored on an entity's {@link PersistentDataContainer}
 * (Phase 2.3 — see docs/REFACTOR/PROGRESS.md). Unlike {@link MobDefinition}'s built-in
 * resistance table (which only applies to custom Valmora mobs defined in {@code mobs/*.yml}),
 * this works on *any* {@link LivingEntity} — players included — so gear, potions, or scripted
 * effects can grant "25% fire resistance" etc. at runtime without a mob definition.
 *
 * <p>Stacks multiplicatively with {@code MobDefinition}'s resistance in
 * {@link DamageCalculator}: {@code mitigated *= (1 - mobResistance) * (1 - pdcResistance)}.
 *
 * <p>Serialized as a single delimited string (`"fire=0.25;poison=0.5"`) rather than one PDC key
 * per damage type — keeps the container flat and avoids a key explosion as admins add types.
 */
public final class DamageResistanceComponent {

    private DamageResistanceComponent() {}

    /** @return a mutable, damage-type-id (uppercase) -> resistance (0..1, 1 = full immunity) map. */
    public static Map<String, Double> read(LivingEntity entity) {
        if (entity == null) return new HashMap<>();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc == null) return new HashMap<>();
        String raw = pdc.get(Keys.DAMAGE_RESISTANCES_KEY, PersistentDataType.STRING);
        Map<String, Double> result = new HashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(";")) {
            int sep = entry.indexOf('=');
            if (sep <= 0) continue;
            String id = entry.substring(0, sep).toUpperCase(Locale.ROOT);
            try {
                result.put(id, Double.parseDouble(entry.substring(sep + 1)));
            } catch (NumberFormatException ignored) {
                // corrupt entry — skip rather than fail the whole read
            }
        }
        return result;
    }

    /** Overwrites the entity's full resistance table. */
    public static void write(LivingEntity entity, Map<String, Double> resistances) {
        if (entity == null) return;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc == null) return;
        if (resistances == null || resistances.isEmpty()) {
            pdc.remove(Keys.DAMAGE_RESISTANCES_KEY);
            return;
        }
        String serialized = resistances.entrySet().stream()
                .map(e -> e.getKey().toUpperCase(Locale.ROOT) + "=" + e.getValue())
                .collect(Collectors.joining(";"));
        pdc.set(Keys.DAMAGE_RESISTANCES_KEY, PersistentDataType.STRING, serialized);
    }

    /** Sets (or clears, with {@code resistance <= 0}) a single damage type's resistance. */
    public static void set(LivingEntity entity, String damageTypeId, double resistance) {
        Map<String, Double> current = read(entity);
        if (resistance <= 0) {
            current.remove(damageTypeId.toUpperCase(Locale.ROOT));
        } else {
            current.put(damageTypeId.toUpperCase(Locale.ROOT), resistance);
        }
        write(entity, current);
    }

    /** @return the stored resistance (0..1) for the given type, or 0 if none is set. */
    public static double getResistance(LivingEntity entity, DamageType type) {
        if (entity == null || type == null) return 0.0;
        return read(entity).getOrDefault(type.getId(), 0.0);
    }
}
