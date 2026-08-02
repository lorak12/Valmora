package org.nakii.valmora.module.stat;

import org.bukkit.configuration.file.FileConfiguration;

public class SystemStats {

    private final String health;
    private final String mana;
    private final String damage;
    private final String strength;
    private final String defense;
    private final String critChance;
    private final String critDamage;
    private final String speed;
    private final String healthRegen;
    private final String manaRegen;
    private final String luck;
    private final String miningFortune;
    private final String miningSpeed;
    private final String breakingPower;
    private final String miningSpread;

    private SystemStats(String health, String mana, String damage, String strength,
                        String defense, String critChance, String critDamage, String speed,
                        String healthRegen, String manaRegen, String luck,
                        String miningFortune, String miningSpeed,
                        String breakingPower, String miningSpread) {
        this.health = health;
        this.mana = mana;
        this.damage = damage;
        this.strength = strength;
        this.defense = defense;
        this.critChance = critChance;
        this.critDamage = critDamage;
        this.speed = speed;
        this.healthRegen = healthRegen;
        this.manaRegen = manaRegen;
        this.luck = luck;
        this.miningFortune = miningFortune;
        this.miningSpeed = miningSpeed;
        this.breakingPower = breakingPower;
        this.miningSpread = miningSpread;
    }

    public static SystemStats load(FileConfiguration config) {
        return new SystemStats(
            config.getString("combat.health-stat", "health"),
            config.getString("combat.mana-stat", "mana"),
            config.getString("combat.damage-stat", "damage"),
            config.getString("combat.strength-stat", "strength"),
            config.getString("combat.defense-stat", "defense"),
            config.getString("combat.crit-chance-stat", "crit_chance"),
            config.getString("combat.crit-damage-stat", "crit_damage"),
            config.getString("combat.speed-stat", "speed"),
            config.getString("combat.health-regen-stat", "health_regen"),
            config.getString("combat.mana-regen-stat", "mana_regen"),
            config.getString("combat.luck-stat", "luck"),
            config.getString("mining.mining-fortune-stat", "mining_fortune"),
            config.getString("mining.mining-speed-stat", "mining_speed"),
            config.getString("mining.breaking-power-stat", "breaking_power"),
            config.getString("mining.mining-spread-stat", "mining_spread")
        );
    }

    public String getHealth() { return health; }
    public String getMana() { return mana; }
    public String getDamage() { return damage; }
    public String getStrength() { return strength; }
    public String getDefense() { return defense; }
    public String getCritChance() { return critChance; }
    public String getCritDamage() { return critDamage; }
    public String getSpeed() { return speed; }
    public String getHealthRegen() { return healthRegen; }
    public String getManaRegen() { return manaRegen; }
    public String getLuck() { return luck; }
    public String getMiningFortune() { return miningFortune; }
    public String getMiningSpeed() { return miningSpeed; }
    public String getBreakingPower() { return breakingPower; }
    public String getMiningSpread() { return miningSpread; }
}
