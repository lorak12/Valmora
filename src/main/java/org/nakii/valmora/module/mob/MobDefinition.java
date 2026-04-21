package org.nakii.valmora.module.mob;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.module.combat.DamageType;

public class MobDefinition {
    private final String id;
    private final String name;
    private final MobCategory category;
    private final EntityType entityType;
    private final double health;
    private final double baseDamage;
    private final double speed;
    private final ItemStack[] armor;
    private final ItemStack weapon;
    private final ItemStack offHand;
    private final int level;
    private final int baseXp;
    private final int goldReward;
    private final DamageType damageType;
    private final LootTable lootTable;

    private MobDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.category = builder.category;
        this.entityType = builder.entityType;
        this.health = builder.health;
        this.baseDamage = builder.baseDamage;
        this.speed = builder.speed;
        this.armor = builder.armor;
        this.weapon = builder.weapon;
        this.offHand = builder.offHand;
        this.level = builder.level;
        this.baseXp = builder.baseXp;
        this.goldReward = builder.goldReward;
        this.damageType = builder.damageType;
        this.lootTable = builder.lootTable;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public MobCategory getCategory() { return category; }
    public EntityType getEntityType() { return entityType; }
    public double getHealth() { return health; }
    public double getBaseDamage() { return baseDamage; }
    public double getSpeed() { return speed; }
    public ItemStack[] getArmor() { return armor; }
    public ItemStack getWeapon() { return weapon; }
    public ItemStack getOffHand() { return offHand; }
    public int getLevel() { return level; }
    public int getBaseXp() { return baseXp; }
    public int getGoldReward() { return goldReward; }
    public DamageType getDamageType() { return damageType; }
    public LootTable getLootTable() { return lootTable; }

    public double getScaledDamage() {
        return baseDamage + (level - 1);
    }

    public int getXpReward() {
        return baseXp * level;
    }

    public static class Builder {
        private final String id;
        private String name;
        private MobCategory category;
        private EntityType entityType;
        private double health;
        private double baseDamage;
        private double speed;
        private ItemStack[] armor;
        private ItemStack weapon;
        private ItemStack offHand;
        private int level;
        private int baseXp;
        private int goldReward;
        private DamageType damageType;
        private LootTable lootTable;

        public Builder(String id) {
            this.id = id;
            this.baseDamage = 5.0;
            this.level = 1;
            this.baseXp = 2;
            this.goldReward = 0;
            this.damageType = DamageType.MELEE;
            this.lootTable = LootTable.empty();
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder category(MobCategory category) { this.category = category; return this; }
        public Builder entityType(EntityType entityType) { this.entityType = entityType; return this; }
        public Builder health(double health) { this.health = health; return this; }
        public Builder baseDamage(double baseDamage) { this.baseDamage = baseDamage; return this; }
        public Builder speed(double speed) { this.speed = speed; return this; }
        public Builder armor(ItemStack[] armor) { this.armor = armor; return this; }
        public Builder weapon(ItemStack weapon) { this.weapon = weapon; return this; }
        public Builder offHand(ItemStack offHand) { this.offHand = offHand; return this; }
        public Builder level(int level) { this.level = level; return this; }
        public Builder baseXp(int baseXp) { this.baseXp = baseXp; return this; }
        public Builder goldReward(int goldReward) { this.goldReward = goldReward; return this; }
        public Builder damageType(DamageType damageType) { this.damageType = damageType; return this; }
        public Builder lootTable(LootTable lootTable) { this.lootTable = lootTable; return this; }

        public MobDefinition build() {
            return new MobDefinition(this);
        }
    }
}
