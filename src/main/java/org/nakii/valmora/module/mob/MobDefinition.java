package org.nakii.valmora.module.mob;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.combat.DamageType;
import org.nakii.valmora.module.mob.ability.MobAbility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobDefinition {
    private final String id;
    private final String name;
    private final MobCategory category;
    private final EntityType entityType;
    private final double health;
    private final double baseDamage;
    private final double speed;
    // Combat stats (defense + optional offensive stats mirror the player combat formula)
    private final double defense;
    private final double strength;
    private final double critChance;
    private final double critDamage;
    // Damage type -> fraction reduced (0..1); 1.0 means full immunity
    private final Map<DamageType, Double> resistances;
    private final ItemStack[] armor;
    private final ItemStack weapon;
    private final ItemStack offHand;
    private final int level;
    private final int baseXp;
    private final int goldReward;
    private final DamageType damageType;
    private final LootTable lootTable;
    // Boss features
    private final List<MobAbility> abilities;
    private final BossBarConfig bossBar;
    // Behavior flags
    private final double knockbackResistance;
    private final boolean noAi;
    private final boolean silent;
    private final boolean glowing;
    private final boolean persistent;
    private final boolean baby;
    private final boolean preventSunBurn;
    // Basic AI tuning (item/mob.md §"Known Gaps" — pathfinding/aggro/leash beyond vanilla defaults).
    // -1 on either means "leave vanilla behavior alone" (no FOLLOW_RANGE override / no leash reset).
    private final double aggroRange;
    private final double leashRange;
    // VANILLA_CONTROL_AUDIT.md §18 gap fix: EntityTargetEvent listener (MobTargetListener) evaluates
    // this against every candidate target — an empty/no-op condition (the default) means "allow
    // vanilla's own target selection unchanged". Each string is the same condition-language used by
    // GUI/machine/quest conditions (tag, health, zone, variable, or a raw expression), evaluated with
    // the *candidate target* as the condition's caster.
    private final List<String> targetConditionStrings;
    private final Condition compiledTargetCondition;
    private final boolean ignoreNpcs;
    // Natural spawning (zone-driven periodic spawn attempts — see NaturalSpawnTask).
    private final boolean naturalSpawn;
    private final double naturalSpawnChance;
    private final int naturalSpawnMaxNearby;

    private MobDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.category = builder.category;
        this.entityType = builder.entityType;
        this.health = builder.health;
        this.baseDamage = builder.baseDamage;
        this.speed = builder.speed;
        this.defense = builder.defense;
        this.strength = builder.strength;
        this.critChance = builder.critChance;
        this.critDamage = builder.critDamage;
        this.resistances = builder.resistances;
        this.armor = builder.armor;
        this.weapon = builder.weapon;
        this.offHand = builder.offHand;
        this.level = builder.level;
        this.baseXp = builder.baseXp;
        this.goldReward = builder.goldReward;
        this.damageType = builder.damageType;
        this.lootTable = builder.lootTable;
        this.abilities = builder.abilities;
        this.bossBar = builder.bossBar;
        this.knockbackResistance = builder.knockbackResistance;
        this.noAi = builder.noAi;
        this.silent = builder.silent;
        this.glowing = builder.glowing;
        this.persistent = builder.persistent;
        this.baby = builder.baby;
        this.preventSunBurn = builder.preventSunBurn;
        this.aggroRange = builder.aggroRange;
        this.leashRange = builder.leashRange;
        this.targetConditionStrings = builder.targetConditionStrings;
        this.compiledTargetCondition = builder.targetConditionStrings.isEmpty()
                ? null
                : org.nakii.valmora.api.ValmoraAPI.getInstance().getScriptModule().getConditionParser()
                        .parseList(builder.targetConditionStrings);
        this.ignoreNpcs = builder.ignoreNpcs;
        this.naturalSpawn = builder.naturalSpawn;
        this.naturalSpawnChance = builder.naturalSpawnChance;
        this.naturalSpawnMaxNearby = builder.naturalSpawnMaxNearby;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public MobCategory getCategory() { return category; }
    public EntityType getEntityType() { return entityType; }
    public double getHealth() { return health; }
    public double getBaseDamage() { return baseDamage; }
    public double getSpeed() { return speed; }
    public double getDefense() { return defense; }
    public double getStrength() { return strength; }
    public double getCritChance() { return critChance; }
    public double getCritDamage() { return critDamage; }
    public Map<DamageType, Double> getResistances() { return resistances; }
    public ItemStack[] getArmor() { return armor; }
    public ItemStack getWeapon() { return weapon; }
    public ItemStack getOffHand() { return offHand; }
    public int getLevel() { return level; }
    public int getBaseXp() { return baseXp; }
    public int getGoldReward() { return goldReward; }
    public DamageType getDamageType() { return damageType; }
    public LootTable getLootTable() { return lootTable; }
    public List<MobAbility> getAbilities() { return abilities; }
    public BossBarConfig getBossBar() { return bossBar; }
    public double getKnockbackResistance() { return knockbackResistance; }
    public boolean isNoAi() { return noAi; }
    public boolean isSilent() { return silent; }
    public boolean isGlowing() { return glowing; }
    public boolean isPersistent() { return persistent; }
    public boolean isBaby() { return baby; }
    public boolean isPreventSunBurn() { return preventSunBurn; }
    public double getAggroRange() { return aggroRange; }
    public double getLeashRange() { return leashRange; }
    public List<String> getTargetConditionStrings() { return targetConditionStrings; }
    public boolean isIgnoreNpcs() { return ignoreNpcs; }

    /** True if the candidate target passes this mob's configured target-conditions (always true if none configured). */
    public boolean canTarget(org.nakii.valmora.api.execution.ExecutionContext candidateContext) {
        return compiledTargetCondition == null || compiledTargetCondition.evaluate(candidateContext);
    }
    public boolean isNaturalSpawn() { return naturalSpawn; }
    public double getNaturalSpawnChance() { return naturalSpawnChance; }
    public int getNaturalSpawnMaxNearby() { return naturalSpawnMaxNearby; }

    /** Resistance fraction (0..1) for a damage type; 0 if none configured. */
    public double getResistance(DamageType type) {
        return resistances.getOrDefault(type, 0.0);
    }

    /** True if this mob has at least one ability or an enabled boss bar (i.e. needs runtime tracking). */
    public boolean isBoss() {
        return (abilities != null && !abilities.isEmpty()) || (bossBar != null && bossBar.isEnabled());
    }

    /**
     * HC-081: damage-per-level curve, configurable via {@code mobs.damage-scaling} (an
     * {@code Expression} evaluated with {@code $mob.base_damage$}/{@code $mob.level$}) — falls
     * back to the original hardcoded {@code baseDamage + (level-1)} linear curve when unset or
     * when the script module isn't available (e.g. unit tests).
     */
    public double getScaledDamage() {
        var api = org.nakii.valmora.api.ValmoraAPI.getInstance();
        var plugin = org.nakii.valmora.Valmora.getInstance();
        String formula = plugin != null ? plugin.getConfig().getString("mobs.damage-scaling", "") : null;
        if (formula != null && !formula.isBlank() && api != null && api.getScriptModule() != null) {
            var ctx = new org.nakii.valmora.api.execution.SimpleExecutionContext(null, null, null, null);
            ctx.set("mob:base_damage", baseDamage);
            ctx.set("mob:level", (double) level);
            Object result = api.getScriptModule().getExpressionEvaluator().evaluate(formula, ctx);
            if (result instanceof Number n) return n.doubleValue();
        }
        return baseDamage + (level - 1);
    }

    /**
     * HC-082: XP-reward curve, configurable via {@code mobs.xp-reward-formula} — falls back to the
     * original hardcoded {@code baseXp * level} linear curve when unset.
     */
    public int getXpReward() {
        var api = org.nakii.valmora.api.ValmoraAPI.getInstance();
        var plugin = org.nakii.valmora.Valmora.getInstance();
        String formula = plugin != null ? plugin.getConfig().getString("mobs.xp-reward-formula", "") : null;
        if (formula != null && !formula.isBlank() && api != null && api.getScriptModule() != null) {
            var ctx = new org.nakii.valmora.api.execution.SimpleExecutionContext(null, null, null, null);
            ctx.set("mob:base_xp", (double) baseXp);
            ctx.set("mob:level", (double) level);
            Object result = api.getScriptModule().getExpressionEvaluator().evaluate(formula, ctx);
            if (result instanceof Number n) return n.intValue();
        }
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
        private double defense = 0.0;
        private double strength = 0.0;
        private double critChance = 0.0;
        private double critDamage = 0.0;
        private Map<DamageType, Double> resistances = new HashMap<>();
        private ItemStack[] armor;
        private ItemStack weapon;
        private ItemStack offHand;
        private int level;
        private int baseXp;
        private int goldReward;
        private DamageType damageType;
        private LootTable lootTable;
        private List<MobAbility> abilities = new ArrayList<>();
        private BossBarConfig bossBar = BossBarConfig.disabled();
        private double knockbackResistance = -1.0; // -1 = leave vanilla default
        private boolean noAi = false;
        private boolean silent = false;
        private boolean glowing = false;
        private boolean persistent = false;
        private boolean baby = false;
        private boolean preventSunBurn = false;
        private double aggroRange = -1.0;
        private double leashRange = -1.0;
        private List<String> targetConditionStrings = new ArrayList<>();
        private boolean ignoreNpcs = true;
        private boolean naturalSpawn = false;
        private double naturalSpawnChance;
        private int naturalSpawnMaxNearby;

        // HC-083: mobs.defaults.* — global fallback when a mob's own YAML omits these fields.
        public Builder(String id) {
            this.id = id;
            var plugin = org.nakii.valmora.Valmora.getInstance();
            var cfg = plugin != null ? plugin.getConfig() : null;
            this.baseDamage = cfg != null ? cfg.getDouble("mobs.defaults.base-damage", 5.0) : 5.0;
            this.level = 1;
            this.baseXp = cfg != null ? cfg.getInt("mobs.defaults.base-xp", 2) : 2;
            this.goldReward = cfg != null ? cfg.getInt("mobs.defaults.gold-reward", 0) : 0;
            this.naturalSpawnChance = cfg != null ? cfg.getDouble("mobs.defaults.natural-spawn-chance", 0.1) : 0.1;
            this.naturalSpawnMaxNearby = cfg != null ? cfg.getInt("mobs.defaults.natural-spawn-max-nearby", 3) : 3;
            this.damageType = DamageType.MELEE;
            this.lootTable = LootTable.empty();
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder category(MobCategory category) { this.category = category; return this; }
        public Builder entityType(EntityType entityType) { this.entityType = entityType; return this; }
        public Builder health(double health) { this.health = health; return this; }
        public Builder baseDamage(double baseDamage) { this.baseDamage = baseDamage; return this; }
        public Builder speed(double speed) { this.speed = speed; return this; }
        public Builder defense(double defense) { this.defense = defense; return this; }
        public Builder strength(double strength) { this.strength = strength; return this; }
        public Builder critChance(double critChance) { this.critChance = critChance; return this; }
        public Builder critDamage(double critDamage) { this.critDamage = critDamage; return this; }
        public Builder resistances(Map<DamageType, Double> resistances) { this.resistances = resistances; return this; }
        public Builder armor(ItemStack[] armor) { this.armor = armor; return this; }
        public Builder weapon(ItemStack weapon) { this.weapon = weapon; return this; }
        public Builder offHand(ItemStack offHand) { this.offHand = offHand; return this; }
        public Builder level(int level) { this.level = level; return this; }
        public Builder baseXp(int baseXp) { this.baseXp = baseXp; return this; }
        public Builder goldReward(int goldReward) { this.goldReward = goldReward; return this; }
        public Builder damageType(DamageType damageType) { this.damageType = damageType; return this; }
        public Builder lootTable(LootTable lootTable) { this.lootTable = lootTable; return this; }
        public Builder abilities(List<MobAbility> abilities) { this.abilities = abilities; return this; }
        public Builder bossBar(BossBarConfig bossBar) { this.bossBar = bossBar; return this; }
        public Builder knockbackResistance(double knockbackResistance) { this.knockbackResistance = knockbackResistance; return this; }
        public Builder noAi(boolean noAi) { this.noAi = noAi; return this; }
        public Builder silent(boolean silent) { this.silent = silent; return this; }
        public Builder glowing(boolean glowing) { this.glowing = glowing; return this; }
        public Builder persistent(boolean persistent) { this.persistent = persistent; return this; }
        public Builder baby(boolean baby) { this.baby = baby; return this; }
        public Builder preventSunBurn(boolean preventSunBurn) { this.preventSunBurn = preventSunBurn; return this; }
        public Builder aggroRange(double aggroRange) { this.aggroRange = aggroRange; return this; }
        public Builder leashRange(double leashRange) { this.leashRange = leashRange; return this; }
        public Builder targetConditions(List<String> targetConditionStrings) { this.targetConditionStrings = targetConditionStrings; return this; }
        public Builder ignoreNpcs(boolean ignoreNpcs) { this.ignoreNpcs = ignoreNpcs; return this; }
        public Builder naturalSpawn(boolean naturalSpawn) { this.naturalSpawn = naturalSpawn; return this; }
        public Builder naturalSpawnChance(double naturalSpawnChance) { this.naturalSpawnChance = naturalSpawnChance; return this; }
        public Builder naturalSpawnMaxNearby(int naturalSpawnMaxNearby) { this.naturalSpawnMaxNearby = naturalSpawnMaxNearby; return this; }

        public MobDefinition build() {
            return new MobDefinition(this);
        }
    }
}
