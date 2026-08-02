# Combat Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `combat` | **Config:** `plugins/Valmora/config.yml` → `combat:` section

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The Combat module is Valmora's **damage engine and combat-feel layer**. It replaces vanilla Minecraft damage with an RPG-style formula:

- **Your Damage, Strength, Crit Chance, Crit Damage and Defense stats** (from `stats/core.yml`) drive every hit instead of the vanilla damage/hearts model.
- **All damage types are tracked** — melee, projectiles, fall, fire, lava, magic, void, poison, wither, explosions and drowning — and each renders with its own color.
- **Floating damage numbers** appear above hit victims (crits get a golden `✧` treatment).
- **Health is a virtual pool** far larger than the vanilla 10 hearts — you see it mirrored onto a fixed 10-heart display that scales with your real Health stat.
- **Out-of-combat regeneration** refills health (and mana) every second, but pauses while you're fighting for 3 seconds after the last hit you take.

The module has no config files of its own: its tunables are split between the stat mappings in `config.yml`, the stat values in `stats/*.yml`, and per-mob combat stats in `mobs/*.yml`.

---

## Player Guide

### Damage numbers — what you see

When you (or anything) hit a creature, a **colored number** floats above the victim's head for about **one second**:

| Color | Damage type |
|---|---|
| White | Melee (weapons) |
| Grey | Projectiles (arrows, mob projectiles) |
| Orange | Fire |
| Dark red | Lava |
| Aqua | Magic |
| Green | Poison |
| Red | Explosions |
| Blue | Drowning |
| Dark grey | Falling |
| Black | Void / Wither |

**Critical hits** appear in **gold with `✧` accents and bold text** (e.g. `✧ 250 ✧`).

Two caveats you may notice in combat:
- Indicators are **rate-limited** — no more than one per victim every 0.4 seconds, so rapid damage-over-time ticks don't flood the screen with overlapping numbers.
- Because the numbers only show the floored integer, two hits of `99.9` both show `99`.

### Combat feel — how damage actually works

**For your attacks (player → anything):**

```
Damage = BaseDamage × (1 + Strength/100)
       × (1 + CritDamage/100) if you crit
       × enchant multipliers
```

- **BaseDamage** comes from your **Damage** stat (5 by default).
- **Crit chance** is rolled per hit from your **Crit Chance** stat (30% default); when it lands, **Crit Damage** (50% default) boosts the hit.
- Your **melee weapon's Valmora enchants** can add more multipliers (e.g. Sharpness: +5% melee damage per level) or shred the target's defense.
- The result is then reduced by the victim's **Defense**:

```
Defense reduction = 100 / (Defense + 100)
```

  So 100 Defense cuts damage in half, 300 Defense cuts it to 25%, etc. **Fall, drowning and void damage ignore Defense entirely.**

**When a mob or boss hits you:** the same formula runs with the mob's own damage, strength and crit stats — but uses **your Defense** to mitigate.

**Mob resistances:** custom mobs can be configured to resist or be **immune** to specific damage types (e.g. a Fire Titan that's immune to fire/lava). Immune fire/lava mobs also stop burning instantly.

### Health & mana

- Your **Health stat** is your max health pool (100 by default, up to 10,000). Damage is subtracted from this pool; your vanilla hearts display the remaining fraction on a fixed **10-heart scale**.
- **Health regen** (1 per second by default) refills you **only out of combat**. You're "in combat" for **3 seconds** after the last time you take damage.
- **Mana regen** (2 per second by default) refills constantly — even during combat — so mana is only gated by spending, not by combat.

### Attacks that go through this system

- **Melee and bow/mob-projectile hits** (they zero the vanilla damage and use Valmora numbers).
- **Environmental damage**: fall, fire/lava, drowning, explosions, magic/instant-damage, poison, wither, void. These are scaled up by a **×5 multiplier** relative to vanilla before Defense applies (i.e. a vanilla 1-damage fall becomes 5 damage before mitigation).

### What players DON'T control here

- There are **no player commands** in the Combat module. You can't "toggle" damage numbers or change combat feel in-game.
- Vanilla attack cooldowns, knockback, and crit particles are left as-is — the module only replaces the damage math, the numbers, and health/regen.

### Combat interaction with other systems

- **Item abilities** trigger `ON_HIT` when your held weapon connects (`CombatListener`), and the `damage` ability mechanic routes through this same engine — so ability damage respects your stats, crits, and the target's defense. Abilities that deal damage-by-type (e.g. a fire ability) are color-coded accordingly.
- **Bosses** (mobs with abilities/boss bars) fire their `ON_ATTACK` and `ON_DAMAGED` abilities from this engine, so boss fights are driven by the same hits you see.
- **The Combat skill** (`/skill info combat`, max level 60) earns XP from `MOB_KILL` sources (`skills/*.yml`) — the kill detection is vanilla `EntityDeathEvent`, fed by deaths that this engine's damage causes.

---

## Admin Guide

### Where things are configured

| File | What it controls |
|---|---|
| `plugins/Valmora/config.yml` → `combat:` | Which stat IDs the engine reads for damage/strength/defense/crits/regen |
| `plugins/Valmora/stats/core.yml` | The actual stat values (Damage, Strength, Defense, Crit, Health, regen) and their caps |
| `plugins/Valmora/mobs/*.yml` | Per-mob damage, defense, strength/crits, and damage-type resistances |

After edits, run **`/valmora reload`** (requires `valmora.admin`).

### Tuning damage

The formula you're tuning:

```
Player outgoing = Damage × (1 + Strength/100) [× (1 + CritDamage/100) if crit] × enchant bonuses
Reduction      = 100 / (Defense + 100)
Environmental  = vanillaDamage × 5, then the same Defense reduction (except fall/drown/void)
```

To change the feel:

- **Raise or lower the Damage stat** baseline — edit `damage.default-value` in `stats/core.yml` (default `5.0`). This is the single biggest lever for player power.
- **Buff Defense's value** — increase `defense.default-value` or add Defense to mobs/armor. Defense is most impactful early (0→100 Defense is a 50% reduction; 100→200 only drops you to 33% taken).
- **Crit pacing** — `crit_chance.default-value` (30) and `crit_damage.default-value` (50). Chance is capped at 100; crit damage is uncapped.
- **Regen** — `health_regen.default-value` (1.0, out of combat) and `mana_regen.default-value` (2.0, always). The "in combat" window is **hardcoded to 3 seconds** — you can't change it without a plugin update.
- **Health pool** — `health.default-value` (100) and `health.max-value` (10,000) control how much punishment players can take and how big the damage numbers feel relative to health.
- **Environmental damage feel** — the **×5 multiplier is hardcoded** (in `DamageCalculator`). If environmental damage feels too weak or too punishing, it can't be tuned via config in 0.1.

### Mobs

In `mobs/*.yml`, each mob can define combat stats under `stats:`:

```yaml
my_boss:
  base-damage: 300        # base of its attack
  level: 50               # effective damage = base-damage + level - 1
  damage-type: MELEE
  stats:
    defense: 400          # reduces player damage via 100/(defense+100)
    strength: 60          # mobs use the same strength formula
    crit-chance: 25
    crit-damage: 80
  resistances:
    FIRE: 1.0             # 1.0 = immune
    EXPLOSION: 0.5        # takes 50% of explosion damage
```

Valid resistance keys are the damage types: `MELEE`, `PROJECTILE`, `FALL`, `DROWNING`, `FIRE`, `LAVA`, `MAGIC`, `VOID`, `POISON`, `WITHER`, `EXPLOSION`.

### Permissions & commands

| Command | Permission | Description |
|---|---|---|
| `/valmora reload` | `valmora.admin` | Reload all modules (including combat). The Combat module itself has no commands of its own. |

There are no combat-specific permissions and no combat admin commands.

---

## Configuration Reference

### File layout

```
plugins/Valmora/
├── config.yml            # combat: stat-ID mapping
├── stats/core.yml        # stat values and caps
└── mobs/*.yml            # per-mob combat stats
```

The Combat module ships **no dedicated combat YAML folder**.

### `config.yml` → `combat:` keys

Read by `SystemStats` at startup (`SystemStats.java:45-63`). Each value is the **stat ID** from `stats/*.yml` that fills the engine's internal role:

| Key | Default | Explanation |
|---|---|---|
| `combat.health-stat` | `health` | Max-health pool stat. |
| `combat.mana-stat` | `mana` | Max-mana pool stat. |
| `combat.damage-stat` | `damage` | Player base attack power. |
| `combat.strength-stat` | `strength` | Offensive scaling (`× (1 + strength/100)`). |
| `combat.defense-stat` | `defense` | Victim mitigation (`100/(defense+100)`). |
| `combat.crit-chance-stat` | `crit_chance` | Crit chance stat. |
| `combat.crit-damage-stat` | `crit_damage` | Crit damage stat. |
| `combat.speed-stat` | `speed` | Movement speed (vanilla-attribute mapped). |
| `combat.health-regen-stat` | `health_regen` | Out-of-combat HP per second. |
| `combat.mana-regen-stat` | `mana_regen` | Mana per second (always). |
| `combat.luck-stat` | `luck` | Loot-quality stat (not used by damage). |

These exist so a server can **rename or replace a core combat stat** without code changes. If you change them, the values must still point at valid entries in `stats/*.yml`.

### Defaults at a glance

| Setting | Default |
|---|---|
| Player base Damage | `5.0` |
| Player Strength | `0.0` |
| Player Defense | `0.0` |
| Player Crit Chance | `30.0` (cap 100) |
| Player Crit Damage | `50.0` |
| Max Health | `100.0` (cap 10,000) |
| Max Mana | `100.0` (cap 5,000) |
| Health Regen (out of combat) | `1.0`/s |
| Mana Regen (always) | `2.0`/s |
| Combat window (regen pause) | 3 seconds (**hardcoded**) |
| Environmental damage multiplier | ×5 (**hardcoded**) |
| Indicator lifetime | 1 second (**hardcoded**) |
| Indicator rate limit | 1 per 0.4 s per victim (**hardcoded**) |

---

## Limitations

- The combat-window duration (3 s), the ×5 environmental multiplier, the `100/(defense+100)` formula, indicator timing, and the 10-heart visual scale are **hardcoded** — not configurable in 0.1.
- Vanilla attack speed / knockback are **not** modified; only the damage math, numbers, and health/regen are.
- Damage-type **colors** are fixed in code and can't be restyled via config.
- Mobs that are immune to a damage type still show a damage number (displaying 0) for hits of that type; only fire/lava-immune mobs visibly stop burning.
- If a player somehow has **no active profile**, they take no damage at all (the engine silently skips applying it).
