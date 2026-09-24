package org.nakii.valmora.module.combat;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VANILLA_CONTROL_AUDIT.md §12/§14 High #9 — vanilla's own attack-cooldown "swing charge" damage
 * scaling (spam-clicking deals reduced damage until the swing timer, driven by
 * {@link Attribute#ATTACK_SPEED}, fully recharges — the same mechanic behind the small charge
 * indicator next to the vanilla crosshair) never applied to hits routed through the custom damage
 * pipeline: {@link CombatListener} zeroes the vanilla event's damage and recalculates from scratch,
 * so vanilla's own charge-scaling code (which only ever ran against that zeroed value) had nothing
 * left to scale. {@code StatModule}'s {@code bonus_attack_speed} stat only ever fed the
 * <i>attribute</i> value — which still drives the client-side charge HUD correctly — not this
 * server-side damage consequence.
 *
 * <p>Reproduces vanilla's own charge formula
 * ({@code damage *= 0.2 + attackStrength^2 * 0.8}, see {@code LivingEntity#getAttackStrengthScale}
 * in vanilla) against Bukkit's {@link Attribute#ATTACK_SPEED} value, so a weapon's base attack speed
 * plus any {@code bonus_attack_speed} stat modifier both feed the required recharge time exactly the
 * way they already feed the attribute the client reads for its own charge indicator.
 *
 * <p>Players only — mobs have no analogous swing-charge mechanic in vanilla, so mob attackers always
 * get {@link #getChargeProgress} = 1.0 (no scaling).
 */
public final class AttackCooldownService {

    private static final Map<UUID, Long> LAST_ATTACK_MILLIS = new ConcurrentHashMap<>();

    private AttackCooldownService() {}

    /** 0..1 charge progress for this attacker's *next* hit — does not record anything itself. */
    public static double getChargeProgress(LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return 1.0;

        Long lastMillis = LAST_ATTACK_MILLIS.get(player.getUniqueId());
        if (lastMillis == null) return 1.0; // first hit this session — treat as fully charged

        var attrInst = player.getAttribute(Attribute.ATTACK_SPEED);
        double attackSpeed = attrInst != null ? attrInst.getValue() : 4.0;
        if (attackSpeed <= 0) return 1.0;

        double requiredTicks = 20.0 / attackSpeed;
        long elapsedTicks = (System.currentTimeMillis() - lastMillis) / 50L;
        return Math.min(1.0, elapsedTicks / requiredTicks);
    }

    /** Records "now" as this attacker's last landed hit. Call once per hit, after reading the charge. */
    public static void recordAttack(LivingEntity attacker) {
        if (attacker instanceof Player player) {
            LAST_ATTACK_MILLIS.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** Vanilla's own charge -> damage-multiplier curve. */
    public static double damageMultiplierFor(double chargeProgress) {
        double clamped = Math.max(0.0, Math.min(1.0, chargeProgress));
        double min = minMultiplier();
        return min + clamped * clamped * (1.0 - min);
    }

    /** {@code combat.attack-cooldown.min-multiplier} — vanilla's own value is 0.2. */
    private static double minMultiplier() {
        Valmora plugin = Valmora.getInstance();
        return plugin != null ? plugin.getConfig().getDouble("combat.attack-cooldown.min-multiplier", 0.2) : 0.2;
    }

    /** {@code combat.attack-cooldown.enabled} (default true). */
    public static boolean isEnabled() {
        Valmora plugin = Valmora.getInstance();
        return plugin == null || plugin.getConfig().getBoolean("combat.attack-cooldown.enabled", true);
    }

    public static void clear(UUID uuid) {
        LAST_ATTACK_MILLIS.remove(uuid);
    }
}
