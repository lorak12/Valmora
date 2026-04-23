package org.nakii.valmora.module.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.mob.MobDefinition;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.util.Keys;

import java.util.Map;


public class DamageCalculator {

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType, double amount) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        double strength = 0.0;
        double critChance = 0.0;
        double critDamage = 0.0;

        if (attacker instanceof Player player) {
            ValmoraPlayer vPlayer = api.getPlayerManager().getSession(player.getUniqueId());
            if (vPlayer != null) {
                StatManager statManager = vPlayer.getActiveProfile().getStatManager();
                strength = statManager.getStat(Stat.STRENGTH);
                critChance = statManager.getStat(Stat.CRIT_CHANCE);
                critDamage = statManager.getStat(Stat.CRIT_DAMAGE);
            }
        }

        boolean isCritical = Math.random() < (critChance / 100.0);
        double fullDamage = amount * (1 + strength / 100.0);

        if (isCritical) {
            fullDamage *= (1 + critDamage / 100.0);
        }

        double defenseMultiplier = getDefenseMultiplier(victim, damageType);
        double finalDamage = Math.floor(fullDamage * defenseMultiplier);

        return new DamageResult(finalDamage, damageType, isCritical, attacker, victim);
    }

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        double damage = 1.0;

        if (attacker instanceof Player) {
            ValmoraPlayer vPlayer = api.getPlayerManager().getSession(attacker.getUniqueId());
            if (vPlayer != null) {
                damage = vPlayer.getActiveProfile().getStatManager().getStat(Stat.DAMAGE);
            }

            ItemStack weapon = ((Player) attacker).getInventory().getItemInMainHand();
            Map<String, Integer> enchants = EnchantmentHelper.getEnchantments(weapon);
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                if (def != null && def.getLogic() != null) {
                    def.getLogic().onAttack(null, entry.getValue());
                }
            }

            if (victim instanceof Player victimPlayer) {
                ItemStack[] armor = victimPlayer.getInventory().getArmorContents();
                for (ItemStack armorItem : armor) {
                    Map<String, Integer> armorEnchants = EnchantmentHelper.getEnchantments(armorItem);
                    for (Map.Entry<String, Integer> entry : armorEnchants.entrySet()) {
                        EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                        if (def != null && def.getLogic() != null) {
                            def.getLogic().onDefend(null, entry.getValue());
                        }
                    }
                }
            }
        } else {
            String mobId = attacker.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
            MobDefinition mob = api.getMobManager().getMobDefinition(mobId);
            if (mob != null) {
                damage = mob.getScaledDamage();
            }
        }

        return calculateDamage(attacker, victim, damageType, damage);
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
        ValmoraAPI api = ValmoraAPI.getInstance();
        // Certain damage types bypass defense
        if (damageType == DamageType.VOID || damageType == DamageType.DROWNING || damageType == DamageType.FALL) {
            return 1.0;
        }

        if (victim instanceof Player player) {
            ValmoraPlayer vVictim = api.getPlayerManager().getSession(player.getUniqueId());
            if (vVictim != null) {
                double defense = vVictim.getActiveProfile().getStatManager().getStat(Stat.DEFENSE);
                // Correct damage reduction formula: Multiplier = 100 / (Def + 100)
                return 100.0 / (defense + 100.0);
            }
        }
        return 1.0;
    }
}
