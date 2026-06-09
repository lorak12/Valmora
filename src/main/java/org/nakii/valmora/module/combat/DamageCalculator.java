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
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.util.Keys;

import java.util.Map;

public class DamageCalculator {

    public static DamageResult calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType damageType, double baseDamageOverride) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        SystemStats sys = api.getSystemStats();

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
                    baseDamage = statManager.getStat(sys.getDamage());
                }
                strength = statManager.getStat(sys.getStrength());
                critChance = statManager.getStat(sys.getCritChance());
                critDamage = statManager.getStat(sys.getCritDamage());
            }
        } else if (attacker != null) {
            MobDefinition mob = mobOf(attacker);
            if (mob != null) {
                if (baseDamageOverride <= 0) {
                    baseDamage = mob.getScaledDamage();
                }
                // Optional offensive stats feed the same player damage formula
                strength = mob.getStrength();
                critChance = mob.getCritChance();
                critDamage = mob.getCritDamage();
            } else if (baseDamageOverride <= 0) {
                baseDamage = 1.0;
            }
        }

        MobDefinition victimMob = mobOf(victim);
        if (victim instanceof Player victimPlayer) {
            ValmoraPlayer vVictim = api.getPlayerManager().getSession(victimPlayer.getUniqueId());
            if (vVictim != null) {
                defense = vVictim.getActiveProfile().getStatManager().getStat(sys.getDefense());
            }
        } else if (victimMob != null) {
            defense = victimMob.getDefense();
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

        double mitigated = fullDamage * defenseMultiplier;

        // Mob victim damage-type resistances (1.0 = full immunity)
        boolean immune = false;
        if (victimMob != null) {
            double resistance = victimMob.getResistance(damageType);
            if (resistance > 0) {
                mitigated *= (1.0 - resistance);
                immune = resistance >= 1.0;
            }
        }

        double finalDamage = Math.floor(mitigated);

        DamageResult result = new DamageResult(finalDamage, damageType, isCritical, attacker, victim);
        result.setImmune(immune);

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
        ValmoraAPI api = ValmoraAPI.getInstance();
        double multiplier = 5.0;
        double fullDamage = baseVanillaDamage * multiplier;

        MobDefinition victimMob = mobOf(victim);

        double defenseMultiplier = 1.0;
        if (damageType != DamageType.VOID && damageType != DamageType.DROWNING && damageType != DamageType.FALL) {
            double defense = 0.0;
            if (victim instanceof Player player) {
                ValmoraPlayer vVictim = api.getPlayerManager().getSession(player.getUniqueId());
                if (vVictim != null) {
                    defense = vVictim.getActiveProfile().getStatManager().getStat(api.getSystemStats().getDefense());
                }
            } else if (victimMob != null) {
                defense = victimMob.getDefense();
            }
            defenseMultiplier = 100.0 / (defense + 100.0);
        }

        double mitigated = fullDamage * defenseMultiplier;

        // Mob victim damage-type resistances (covers environmental fire/lava/explosion/etc.)
        boolean immune = false;
        if (victimMob != null) {
            double resistance = victimMob.getResistance(damageType);
            if (resistance > 0) {
                mitigated *= (1.0 - resistance);
                immune = resistance >= 1.0;
            }
        }

        double finalDamage = Math.floor(mitigated);
        DamageResult result = new DamageResult(finalDamage, damageType, false, null, victim);
        result.setImmune(immune);
        return result;
    }

    /** Returns the {@link MobDefinition} for a custom mob entity, or null if it is not one. */
    private static MobDefinition mobOf(LivingEntity entity) {
        if (entity == null) return null;
        org.bukkit.persistence.PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc == null) return null;
        String mobId = pdc.get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        if (mobId == null) return null;
        return ValmoraAPI.getInstance().getMobManager().getMobDefinition(mobId);
    }
}
