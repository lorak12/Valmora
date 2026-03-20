package org.nakii.valmora.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Keys;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.mob.MobDefinition;
import org.nakii.valmora.profile.ValmoraPlayer;
import org.nakii.valmora.stat.Stat;
import org.nakii.valmora.stat.StatManager;

public class DamageCalculator {

    private static Valmora plugin;

    public DamageCalculator(Valmora plugin) {
        this.plugin = plugin;
    }

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType) {
        double damage = 1.0;
        double strength = 0.0;
        double critChance = 0.0;
        double critDamage = 0.0;

        // --- ATTAKER STATS ---
        if (attacker instanceof Player player) {
            ValmoraPlayer vPlayer = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (vPlayer != null) {
                StatManager statManager = vPlayer.getActiveProfile().getStatManager();
                damage = statManager.getStat(Stat.DAMAGE);
                strength = statManager.getStat(Stat.STRENGTH);
                critChance = statManager.getStat(Stat.CRIT_CHANCE);
                critDamage = statManager.getStat(Stat.CRIT_DAMAGE);
            }
        } else {
            // Fetch damage from MobDefinition if it's a custom mob
            String mobId = attacker.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
            MobDefinition mob = plugin.getMobManager().getMobDefinition(mobId);
            if (mob != null) {
                damage = mob.getDamage();
            }
        }

        // --- DAMAGE CALCULATION ---
        boolean isCritical = Math.random() < (critChance / 100.0);
        double initialDamage = damage * (1 + strength / 100.0);
        double fullDamage = initialDamage; // Apply other multipliers (enchants, etc.) here if needed

        if (isCritical) {
            fullDamage *= (1 + critDamage / 100.0);
        }

        // --- DEFENSE REDUCTION ---
        double defenseMultiplier = getDefenseMultiplier(victim, damageType);
        double finalDamage = Math.floor(fullDamage * defenseMultiplier);

        return new DamageResult(finalDamage, damageType, isCritical, attacker, victim);
    }

    /**
     * Calculates environmental/custom damage where there is no player attacker.
     */
    public static DamageResult calculateDamage(LivingEntity victim, DamageType damageType, double baseVanillaDamage) {
        // We scale vanilla base damage so it behaves appropriately for custom mob health.
        double multiplier = 5.0; // configurable later
        double fullDamage = baseVanillaDamage * multiplier;

        // Apply defense reduction even for environmental damage (unless it's void/drowning/etc.)
        double defenseMultiplier = getDefenseMultiplier(victim, damageType);
        double finalDamage = Math.floor(fullDamage * defenseMultiplier);

        return new DamageResult(finalDamage, damageType, false, null, victim);
    }

    private static double getDefenseMultiplier(LivingEntity victim, DamageType damageType) {
        // Certain damage types bypass defense
        if (damageType == DamageType.VOID || damageType == DamageType.DROWNING || damageType == DamageType.FALL) {
            return 1.0;
        }

        if (victim instanceof Player player) {
            ValmoraPlayer vVictim = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (vVictim != null) {
                double defense = vVictim.getActiveProfile().getStatManager().getStat(Stat.DEFENSE);
                // Correct damage reduction formula: Multiplier = 100 / (Def + 100)
                return 100.0 / (defense + 100.0);
            }
        }
        return 1.0;
    }
}
