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

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType, double baseDamageOverride) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        double baseDamage = baseDamageOverride;
        double strength = 0.0;
        double critChance = 0.0;
        double critDamage = 0.0;
        double defense = 0.0;

        if (attacker instanceof Player player) {
            ValmoraPlayer vPlayer = api.getPlayerManager().getSession(player.getUniqueId());
            if (vPlayer != null) {
                StatManager statManager = vPlayer.getActiveProfile().getStatManager();
                if (baseDamageOverride <= 0) {
                    baseDamage = statManager.getStat(Stat.DAMAGE);
                }
                strength = statManager.getStat(Stat.STRENGTH);
                critChance = statManager.getStat(Stat.CRIT_CHANCE);
                critDamage = statManager.getStat(Stat.CRIT_DAMAGE);
            }
        } else if (baseDamageOverride <= 0 && attacker != null) {
            String mobId = attacker.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
            if (mobId != null) {
                MobDefinition mob = api.getMobManager().getMobDefinition(mobId);
                if (mob != null) {
                    baseDamage = mob.getScaledDamage();
                }
            } else {
                baseDamage = 1.0;
            }
        }

        if (victim instanceof Player victimPlayer) {
            ValmoraPlayer vVictim = api.getPlayerManager().getSession(victimPlayer.getUniqueId());
            if (vVictim != null) {
                defense = vVictim.getActiveProfile().getStatManager().getStat(Stat.DEFENSE);
            }
        }

        DamageModifierContext context = new DamageModifierContext(baseDamage, strength, critChance, critDamage, defense, damageType);

        if (attacker instanceof Player) {
            ItemStack weapon = ((Player) attacker).getInventory().getItemInMainHand();
            if (weapon != null) {
                Map<String, Integer> enchants = EnchantmentHelper.getEnchantments(weapon);
                for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                    EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                    if (def != null && def.getLogic() != null) {
                        def.getLogic().modifyAttack(context, attacker, victim, entry.getValue());
                    }
                }
            }
        }

        if (victim instanceof Player victimPlayer) {
            ItemStack[] armor = victimPlayer.getInventory().getArmorContents();
            for (ItemStack armorItem : armor) {
                if (armorItem != null) {
                    Map<String, Integer> armorEnchants = EnchantmentHelper.getEnchantments(armorItem);
                    for (Map.Entry<String, Integer> entry : armorEnchants.entrySet()) {
                        EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                        if (def != null && def.getLogic() != null) {
                            def.getLogic().modifyDefend(context, attacker, victim, entry.getValue());
                        }
                    }
                }
            }
        }

        boolean isCritical = Math.random() < (context.getCritChance() / 100.0);
        double fullDamage = context.getBaseDamage() * (1 + context.getStrength() / 100.0);

        if (isCritical) {
            fullDamage *= (1 + context.getCritDamage() / 100.0);
        }
        
        fullDamage *= context.getDamageMultiplier();

        double defenseMultiplier = 1.0;
        if (damageType != DamageType.VOID && damageType != DamageType.DROWNING && damageType != DamageType.FALL) {
            defenseMultiplier = 100.0 / (context.getDefense() + 100.0);
        }
        
        double finalDamage = Math.floor(fullDamage * defenseMultiplier);

        DamageResult result = new DamageResult(finalDamage, damageType, isCritical, attacker, victim);

        if (attacker instanceof Player) {
            ItemStack weapon = ((Player) attacker).getInventory().getItemInMainHand();
            if (weapon != null) {
                Map<String, Integer> enchants = EnchantmentHelper.getEnchantments(weapon);
                for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                    EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                    if (def != null && def.getLogic() != null) {
                        def.getLogic().onPostAttack(result, attacker, victim, entry.getValue());
                    }
                }
            }
        }

        if (victim instanceof Player victimPlayer) {
            ItemStack[] armor = victimPlayer.getInventory().getArmorContents();
            for (ItemStack armorItem : armor) {
                if (armorItem != null) {
                    Map<String, Integer> armorEnchants = EnchantmentHelper.getEnchantments(armorItem);
                    for (Map.Entry<String, Integer> entry : armorEnchants.entrySet()) {
                        EnchantmentDefinition def = api.getEnchantModule().getRegistry().get(entry.getKey()).orElse(null);
                        if (def != null && def.getLogic() != null) {
                            def.getLogic().onPostDefend(result, attacker, victim, entry.getValue());
                        }
                    }
                }
            }
        }

        return result;
    }

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType) {
        return calculateDamage(attacker, victim, damageType, 0.0);
    }

    public static DamageResult calculateDamage(LivingEntity victim, DamageType damageType, double baseVanillaDamage) {
        double multiplier = 5.0; // configurable later
        double fullDamage = baseVanillaDamage * multiplier;

        double defenseMultiplier = 1.0;
        if (damageType != DamageType.VOID && damageType != DamageType.DROWNING && damageType != DamageType.FALL) {
            ValmoraAPI api = ValmoraAPI.getInstance();
            double defense = 0.0;
            if (victim instanceof Player player) {
                ValmoraPlayer vVictim = api.getPlayerManager().getSession(player.getUniqueId());
                if (vVictim != null) {
                    defense = vVictim.getActiveProfile().getStatManager().getStat(Stat.DEFENSE);
                }
            }
            defenseMultiplier = 100.0 / (defense + 100.0);
        }

        double finalDamage = Math.floor(fullDamage * defenseMultiplier);

        return new DamageResult(finalDamage, damageType, false, null, victim);
    }
}
