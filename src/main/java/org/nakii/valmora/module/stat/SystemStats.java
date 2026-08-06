package org.nakii.valmora.module.stat;

/**
 * Backward-compatible facade over {@link StatRoleRegistry} (Phase 3.1 — see
 * docs/REFACTOR/PROGRESS.md). Every original getter is kept so the many existing call sites
 * (`sys.getDamage()`, `sys.getStrength()`, ...) compile and behave unchanged; new roles beyond the
 * original 15 are reachable through {@link #getRole(String)} without touching this class.
 */
public class SystemStats {

    private final StatRoleRegistry roles;

    private SystemStats(StatRoleRegistry roles) {
        this.roles = roles;
    }

    public static SystemStats load(StatRoleRegistry roles) {
        return new SystemStats(roles);
    }

    /** Generic accessor — the only one a *new* stat role needs. Roles beyond the original 15 have no dedicated getter. */
    public String getRole(String role) {
        return roles.get(role);
    }

    public String getHealth() { return roles.get("health"); }
    public String getMana() { return roles.get("mana"); }
    public String getDamage() { return roles.get("damage"); }
    public String getStrength() { return roles.get("strength"); }
    public String getDefense() { return roles.get("defense"); }
    public String getCritChance() { return roles.get("crit_chance"); }
    public String getCritDamage() { return roles.get("crit_damage"); }
    public String getSpeed() { return roles.get("speed"); }
    public String getHealthRegen() { return roles.get("health_regen"); }
    public String getManaRegen() { return roles.get("mana_regen"); }
    public String getLuck() { return roles.get("luck"); }
    public String getMiningFortune() { return roles.get("mining_fortune"); }
    public String getMiningSpeed() { return roles.get("mining_speed"); }
    public String getBreakingPower() { return roles.get("breaking_power"); }
    public String getMiningSpread() { return roles.get("mining_spread"); }
}
