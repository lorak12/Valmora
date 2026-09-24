package org.nakii.valmora.module.combat;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;

/**
 * VANILLA_CONTROL_AUDIT.md §14/§23 critical gap: {@code EntityDamageBlockedEvent} was never hooked
 * anywhere, and — more importantly for this plugin — {@link DamageCalculator} computes damage
 * entirely from attacker/victim stats, independent of vanilla's own block-amount calculation. That
 * means a player's shield was purely cosmetic: raising it never actually reduced a Valmora-pipeline
 * hit. This resolves the candidate victim's block state itself (rather than trusting vanilla's
 * already-consumed block reduction on the raw event damage) so the custom pipeline can apply an
 * equivalent reduction after its own calculation.
 */
public final class ShieldBlockService {

    private ShieldBlockService() {}

    public record Result(boolean blocked, double damageMultiplier, boolean disablesShield) {
        static final Result NOT_BLOCKED = new Result(false, 1.0, false);
    }

    /** Resolves whether {@code victim} is actively blocking this hit from {@code attacker}, and what happens if so. */
    public static Result resolve(Player victim, LivingEntity attacker, DamageType damageType) {
        Valmora plugin = Valmora.getInstance();
        if (plugin == null || !plugin.getConfig().getBoolean("combat.shield.enabled", true)) return Result.NOT_BLOCKED;
        if (!victim.isBlocking()) return Result.NOT_BLOCKED;
        if (damageType != DamageType.MELEE && damageType != DamageType.PROJECTILE) return Result.NOT_BLOCKED;
        if (!isWithinBlockArc(victim, attacker)) return Result.NOT_BLOCKED;

        boolean axe = isAxe(attacker);
        if (axe) {
            // Vanilla: an axe hit disables the shield for a duration and lands unreduced this swing.
            return new Result(true, 1.0, true);
        }
        double reduction = clamp01(plugin.getConfig().getDouble("combat.shield.block-damage-reduction", 0.9));
        return new Result(true, 1.0 - reduction, false);
    }

    public static int shieldDisableTicks() {
        Valmora plugin = Valmora.getInstance();
        return plugin != null ? plugin.getConfig().getInt("combat.shield.axe-disable-ticks", 100) : 100;
    }

    private static boolean isAxe(LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return false;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        return weapon.getType().name().endsWith("_AXE");
    }

    /** True if {@code attacker} is roughly in front of {@code victim} (matches vanilla's frontal-block arc). */
    private static boolean isWithinBlockArc(Player victim, LivingEntity attacker) {
        Valmora plugin = Valmora.getInstance();
        double arcDegrees = plugin != null ? plugin.getConfig().getDouble("combat.shield.block-arc-degrees", 100.0) : 100.0;
        var toAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        toAttacker.setY(0);
        if (toAttacker.lengthSquared() < 1.0E-6) return true; // stacked on the same block — treat as frontal
        toAttacker.normalize();
        var facing = victim.getLocation().getDirection().setY(0).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, facing.dot(toAttacker)));
        double angle = Math.toDegrees(Math.acos(dot));
        return angle <= arcDegrees / 2.0;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
