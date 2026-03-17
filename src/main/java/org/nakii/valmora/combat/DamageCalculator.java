package org.nakii.valmora.combat;

import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.profile.ValmoraPlayer;
import org.nakii.valmora.stat.Stat;
import org.nakii.valmora.stat.StatManager;

public class DamageCalculator {

    private static Valmora plugin;

    public DamageCalculator(Valmora plugin) {
        this.plugin = plugin;
    }

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType) {
        // Formula: InitialDamage = (Damage_stat) * (1 + Strength/100)
        // Damage Multiplier = 1 + CombatLevelBonus + Enchants + GearBonus + etc...
        //TODO: Add armor reduction on custom mobs with armor
        // Full Damage = InitialDamage * DamageMultiplier * (1+ CritDamage/100) [If critical]

        
        ValmoraPlayer vPlayer = plugin.getPlayerManager().getSession(attacker.getUniqueId());
        StatManager statManager = vPlayer.getActiveProfile().getStatManager();

        double damage = statManager.getStat(Stat.DAMAGE);
        double strength = statManager.getStat(Stat.STRENGTH);
        double critDamage = statManager.getStat(Stat.CRIT_DAMAGE);
        double critChance = statManager.getStat(Stat.CRIT_CHANCE);

        double isCritical = 0;
        if (Math.random() < critChance/100) {
            isCritical = 1;
        }


        double initialDamage = damage * (1 + strength/100);
        double damageMultiplier = 1; //+ combatLevelBonus + enchants + gearBonus
        
        double fullDamage = initialDamage * damageMultiplier;
        if (isCritical == 1) {
            fullDamage *= (1 + critDamage / 100);
        }
        
        fullDamage = Math.floor(fullDamage);

        DamageResult damageResult = new DamageResult(fullDamage, damageType, isCritical == 1, attacker, victim);
        return damageResult;
    }

    /**
     * Calculates environmental/custom damage where there is no player attacker.
     */
    public static DamageResult calculateDamage(LivingEntity victim, DamageType damageType, double baseVanillaDamage) {
        // We scale vanilla base damage so it behaves appropriately for custom mob health.
        // For instance, Fire normally does 1 damage per tick. If our mobs have 500 health,
        // it feels weak, so you can easily modify this multiplier globally here.
        double multiplier = 5.0; // configurable later
        double fullDamage = Math.floor(baseVanillaDamage * multiplier);

        // No attacker, so we pass null. It's obviously never a critical hit for falling.
        return new DamageResult(fullDamage, damageType, false, null, victim);
    }
}
