package org.nakii.valmora.module.alchemy;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffectType;
import org.nakii.valmora.util.Keys;

public class AlchemyListener implements Listener {

    private final AlchemyManager alchemyManager;

    public AlchemyListener(AlchemyManager alchemyManager) {
        this.alchemyManager = alchemyManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();

        String effectId = meta.getPersistentDataContainer().get(Keys.ALCHEMY_EFFECT_ID, PersistentDataType.STRING);
        if (effectId == null) return;

        // If it's a splash potion it gets consumed via PotionSplashEvent instead
        Byte isSplashByte = meta.getPersistentDataContainer().get(Keys.ALCHEMY_IS_SPLASH, PersistentDataType.BYTE);
        if (isSplashByte != null && isSplashByte == 1) {
            event.setCancelled(true);
            return;
        }

        int level = meta.getPersistentDataContainer().getOrDefault(Keys.ALCHEMY_EFFECT_LEVEL, PersistentDataType.INTEGER, 1);
        int duration = meta.getPersistentDataContainer().getOrDefault(Keys.ALCHEMY_DURATION, PersistentDataType.INTEGER, 60);

        event.setCancelled(true);

        Player player = event.getPlayer();
        alchemyManager.applyEffect(player, effectId, level, duration);

        // Consume the item
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isSimilar(item)) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.DROWNING) return;

        var effects = alchemyManager.getActiveEffects(entity.getUniqueId());
        for (var ae : effects) {
            if ("water_breathing".equalsIgnoreCase(ae.effectId()) && !ae.isExpired()) {
                double chancePercent = ae.level() * 15.0;
                if (Math.random() * 100 < chancePercent) {
                    event.setCancelled(true);
                }
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity le) {
            attacker = le;
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof LivingEntity le) {
            attacker = le;
        }
        if (attacker == null) return;

        var effects = alchemyManager.getActiveEffects(attacker.getUniqueId());
        for (var ae : effects) {
            if ("burning".equalsIgnoreCase(ae.effectId()) && !ae.isExpired()) {
                int fireTicks = ae.level() * 2 * 20;
                target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSplash(PotionSplashEvent event) {
        ItemStack item = event.getPotion().getItem();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();

        String effectId = meta.getPersistentDataContainer().get(Keys.ALCHEMY_EFFECT_ID, PersistentDataType.STRING);
        if (effectId == null) return;

        event.setCancelled(true);

        int level = meta.getPersistentDataContainer().getOrDefault(Keys.ALCHEMY_EFFECT_LEVEL, PersistentDataType.INTEGER, 1);
        int duration = meta.getPersistentDataContainer().getOrDefault(Keys.ALCHEMY_DURATION, PersistentDataType.INTEGER, 60);

        var effectOpt = alchemyManager.getEffect(effectId);
        AlchemyEffectType type = effectOpt.map(e -> e.getType()).orElse(AlchemyEffectType.BUFF);

        for (LivingEntity entity : event.getAffectedEntities()) {
            if (type == AlchemyEffectType.DEBUFF) {
                alchemyManager.applyEffect(entity, effectId, level, duration);
            } else if (entity instanceof Player) {
                alchemyManager.applyEffect(entity, effectId, level, duration);
            }
        }
    }
}
