package org.nakii.valmora.mob;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

public class MobDefinition {
    private final String id;
    private final String name;
    private final EntityType entityType;
    private final double health;
    private final double damage;
    private final double speed;
    private final ItemStack[] armor;
    private final ItemStack weapon;
    private final ItemStack offHand;
    private final int level;

    private MobDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.entityType = builder.entityType;
        this.health = builder.health;
        this.damage = builder.damage;
        this.speed = builder.speed;
        this.armor = builder.armor;
        this.weapon = builder.weapon;
        this.offHand = builder.offHand;
        this.level = builder.level;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public EntityType getEntityType() { return entityType; }
    public double getHealth() { return health; }
    public double getDamage() { return damage; }
    public double getSpeed() { return speed; }
    public ItemStack[] getArmor() { return armor; }
    public ItemStack getWeapon() { return weapon; }
    public ItemStack getOffHand() { return offHand; }
    public int getLevel() { return level; }

    public static class Builder {
        private final String id;
        private String name;
        private EntityType entityType;
        private double health;
        private double damage;
        private double speed;
        private ItemStack[] armor;
        private ItemStack weapon;
        private ItemStack offHand;
        private int level;

        public Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder entityType(EntityType entityType) { this.entityType = entityType; return this; }
        public Builder health(double health) { this.health = health; return this; }
        public Builder damage(double damage) { this.damage = damage; return this; }
        public Builder speed(double speed) { this.speed = speed; return this; }
        public Builder armor(ItemStack[] armor) { this.armor = armor; return this; }
        public Builder weapon(ItemStack weapon) { this.weapon = weapon; return this; }
        public Builder offHand(ItemStack offHand) { this.offHand = offHand; return this; }
        public Builder level(int level) { this.level = level; return this; }

        public MobDefinition build() {
            return new MobDefinition(this);
        }
    }
}
