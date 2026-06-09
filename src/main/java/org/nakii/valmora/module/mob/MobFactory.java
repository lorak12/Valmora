package org.nakii.valmora.module.mob;


import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;


public class MobFactory {

    private final BossController bossController;

    public MobFactory(Valmora plugin, BossController bossController) {
        this.bossController = bossController;
    }

    public void applyData(LivingEntity entity, MobDefinition definition) {
        // Set the ID
        entity.getPersistentDataContainer().set(Keys.MOB_ID_KEY, PersistentDataType.STRING, definition.getId());

        // Set health
        AttributeInstance healthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.setBaseValue(definition.getHealth());
            entity.setHealth(definition.getHealth());
        }

        // Set scaled damage
        AttributeInstance damageAttribute = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damageAttribute != null) {
            damageAttribute.setBaseValue(definition.getScaledDamage());
        }

        // Set speed
        AttributeInstance speedAttribute = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.setBaseValue(definition.getSpeed());
        }

        applyFlags(entity, definition);
    }

    private void applyFlags(LivingEntity entity, MobDefinition definition) {
        if (definition.getKnockbackResistance() >= 0) {
            AttributeInstance kb = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(definition.getKnockbackResistance());
        }
        if (definition.isNoAi()) entity.setAI(false);
        if (definition.isSilent()) entity.setSilent(true);
        if (definition.isGlowing()) entity.setGlowing(true);
        if (definition.isPersistent()) {
            entity.setRemoveWhenFarAway(false);
            if (entity instanceof Mob mob) mob.setPersistent(true);
        }
        if (definition.isBaby() && entity instanceof Ageable ageable) {
            ageable.setBaby();
        }
    }

    public void applyEquipment(LivingEntity entity, MobDefinition definition) {
        ItemStack[] armor = definition.getArmor();
        if (armor != null) {
            entity.getEquipment().setArmorContents(armor);
        }
        ItemStack weapon = definition.getWeapon();
        if (weapon != null) {
            entity.getEquipment().setItemInMainHand(weapon);
        }
        ItemStack offHand = definition.getOffHand();
        if (offHand != null) {
            entity.getEquipment().setItemInOffHand(offHand);
        }
    }


    public void applyVisuals(LivingEntity entity, MobDefinition definition) {
        if (definition == null) return;
        // Format: <gray>[<white>Lv.</white>" + definition.getLevel() + "</white>]</gray><white>" + Formatter.capitalize(definition.getName()) + " " + entity.getHealth() + "</white><gray>/</gray><white>" + definition.getHealth() + "</white><red>❤</red>
        String name = "<gray>[<white>Lv." + definition.getLevel() + "</white>]</gray><white>" + Formatter.capitalize(definition.getName()) + " " + entity.getHealth() + "</white><gray>/</gray><white>" + definition.getHealth() + "</white><red>❤</red>";
        entity.customName(Formatter.format(name));
        entity.setCustomNameVisible(true);  
    }

    public LivingEntity spawnMob(MobDefinition definition, Location location) {
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, definition.getEntityType());
        applyData(entity, definition);
        applyEquipment(entity, definition);
        applyVisuals(entity, definition);
        // Track bosses (abilities / boss bar) and fire ON_SPAWN abilities
        if (definition.isBoss() && bossController != null) {
            bossController.register(entity, definition);
        }
        return entity;
    }
}
