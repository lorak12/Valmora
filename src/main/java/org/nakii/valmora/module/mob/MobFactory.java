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

        // Aggro range: how far the mob's own vanilla AI will target/chase players (Paper §14.2 —
        // uses the vanilla attribute rather than fighting vanilla AI with custom targeting logic).
        if (definition.getAggroRange() >= 0) {
            AttributeInstance followRange = entity.getAttribute(Attribute.FOLLOW_RANGE);
            if (followRange != null) {
                followRange.setBaseValue(definition.getAggroRange());
            }
        }

        // Leash range: remember the spawn point so MobAiTask can path the mob back if it wanders
        // too far from home (see that class for the periodic check).
        if (definition.getLeashRange() >= 0 && entity.getLocation().getWorld() != null) {
            org.bukkit.Location spawnLoc = entity.getLocation();
            entity.getPersistentDataContainer().set(Keys.MOB_HOME_X_KEY, PersistentDataType.DOUBLE, spawnLoc.getX());
            entity.getPersistentDataContainer().set(Keys.MOB_HOME_Y_KEY, PersistentDataType.DOUBLE, spawnLoc.getY());
            entity.getPersistentDataContainer().set(Keys.MOB_HOME_Z_KEY, PersistentDataType.DOUBLE, spawnLoc.getZ());
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

    @SuppressWarnings("unchecked")
    public LivingEntity spawnMob(MobDefinition definition, Location location) {
        // Consumer-form spawn: entity is fully configured before its first tick, avoiding the
        // one-tick window where a raw spawnEntity() mob exists half-initialized (see CLAUDE.md §14.7).
        Class<? extends LivingEntity> entityClass =
                (Class<? extends LivingEntity>) definition.getEntityType().getEntityClass();
        LivingEntity entity = location.getWorld().spawn(location, entityClass, spawned -> {
            applyData(spawned, definition);
            applyEquipment(spawned, definition);
            applyVisuals(spawned, definition);
        });
        // Track bosses (abilities / boss bar) and fire ON_SPAWN abilities
        if (definition.isBoss() && bossController != null) {
            bossController.register(entity, definition);
        }
        return entity;
    }
}
