package org.nakii.valmora.module.mob;


import org.bukkit.Bukkit;
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

    /** Vanilla's generic.max_health attribute definition hard-clamps to this range regardless of base value. */
    private static final double VANILLA_MAX_HEALTH_CAP = 1024.0;

    private final BossController bossController;

    public MobFactory(Valmora plugin, BossController bossController) {
        this.bossController = bossController;
    }

    public void applyData(LivingEntity entity, MobDefinition definition) {
        // Set the ID
        entity.getPersistentDataContainer().set(Keys.MOB_ID_KEY, PersistentDataType.STRING, definition.getId());

        // Set health. Vanilla's generic.max_health attribute is hard-clamped to [0, 1024] by the
        // game itself — a base value above that is silently accepted by setBaseValue() but then
        // CraftLivingEntity#setHealth() rejects any health above the clamped attribute value with
        // an IllegalArgumentException, killing the whole spawn (and, via /mob spawn, throwing all
        // the way up through the command). Clamp here so high-HP bosses (e.g. 25000) don't crash.
        AttributeInstance healthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute != null) {
            double health = Math.min(definition.getHealth(), VANILLA_MAX_HEALTH_CAP);
            if (health < definition.getHealth()) {
                Bukkit.getLogger().warning("[Valmora] [Mob] '" + definition.getId() + "' configured health "
                        + definition.getHealth() + " exceeds the vanilla max_health attribute cap ("
                        + VANILLA_MAX_HEALTH_CAP + "); clamping. Scale the mob's damage/level instead of relying on raw HP beyond this cap.");
            }
            healthAttribute.setBaseValue(health);
            entity.setHealth(health);
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
        // Explicitly force adult/baby either way — some vanilla mobs (e.g. Zombie) roll their own
        // random baby chance on spawn, so leaving the "not baby" case untouched let that vanilla
        // roll silently override the configured value some of the time.
        if (entity instanceof Ageable ageable) {
            if (definition.isBaby()) ageable.setBaby();
            else ageable.setAdult();
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
        String currentHealth = formatHealth(entity.getHealth());
        String maxHealth = formatHealth(definition.getHealth());
        String name = "<gray>[<white>Lv." + definition.getLevel() + "</white>]</gray><white>" + Formatter.capitalize(definition.getName()) + " " + currentHealth + "</white><gray>/</gray><white>" + maxHealth + "</white><red>❤</red>";
        entity.customName(Formatter.format(name));
        entity.setCustomNameVisible(true);
    }

    /** Renders a health value without a trailing ".0" for whole numbers (e.g. "100" not "100.0"). */
    private static String formatHealth(double health) {
        if (health == Math.rint(health)) {
            return String.valueOf((long) health);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", health);
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
