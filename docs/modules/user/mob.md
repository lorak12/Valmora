# Mob Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `mobs` | **Config folder:** `plugins/Valmora/mobs/`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The Mob module is the **custom-mob engine**. It lets a server define entirely custom mobs — with their own name, health, damage, speed, combat stats, equipment, loot, and even damage-type resistances — entirely through YAML files. Mobs are identified on the entity itself, so every system (combat, quests, slayers, zones) treats them as first-class "Valmora mobs."

Key facts:

- Mobs are defined in `plugins/Valmora/mobs/*.yml`. Each top-level key is the mob's ID.
- Mobs show a live nameplate: `[Lv.X] <Name> <currentHP>/<maxHP>❤`.
- Mobs are **not** spawned by the plugin automatically — an admin spawns them with `/mob spawn`, zone spawners spawn them, slayer bosses use them, and scripts can `spawn_mob`.
- **Bosses** are mobs with abilities and/or a boss bar. Abilities reuse the item-ability mechanic system (`DAMAGE`, `HEAL`, `APPLY_EFFECT`, `SCRIPT`, …).
- Mob combat is fully wired into the Valmora damage formula: mobs have defense, strength, crit chance/damage, and can be resistant or immune to specific damage types.

---

## Player Guide

### Custom mobs

Any mob tagged as a Valmora mob displays a **nameplate** above its head while alive:

```
[Lv.15] Cave Guardian 104/120❤
```

The nameplate is re-rendered on every hit, so the numbers always reflect the mob's **current** HP. Level is cosmetic for scaling, but it drives how much damage the mob deals and how much combat XP it grants.

### Loot

When a Valmora mob dies, its loot table is rolled **per drop entry**, independently:

- Each entry has a **chance** (0.0–1.0). `0.5` means a 50% chance the drop appears.
- Each entry has a **min/max amount**. The dropped stack size is random within that range.
- Drops marked **luck-affected** scale their chance with your **Luck stat**: `chance × (1 + Luck/100)`. A Luck of 100 doubles a luck-affected drop's chance; non-luck drops are unaffected.

You do not need to loot anything — drops are added directly to the death drop list as normal items.

### Rewards on kill

If you land the killing blow on a Valmora mob:

- You gain **combat skill XP** equal to `base-xp × level` (example: `base-xp: 10`, `level: 15` → 150 combat XP).
- If the mob has a `gold-reward`, that many coins are paid directly to your purse.

### Difficulty and resistances

- Higher **level** mobs hit harder (damage scales `base-damage + level − 1`) and grant more XP.
- Mobs can have **defense** (reduces the damage you deal to them, same formula as player defense: `100/(defense+100)`), **strength**, **crit chance**, and **crit damage** (making their own hits crit against you).
- Mobs can be **resistant** to specific damage types — e.g. a `FIRE: 1.0` resistance means fire does nothing to it, `EXPLOSION: 0.5` means it takes half explosion damage.

### Bosses

Bosses are mobs that have a **boss bar** and/or **abilities**:

- A colored Adventure boss bar is shown to players within the configured range, tracking the boss's remaining health.
- Abilities fire on different **triggers**: on a repeating timer, once below a health percentage, when it attacks, when it's damaged, when it spawns, or when it dies. Timer abilities can have a random chance and a cooldown.
- Ability **announcements** are broadcast (MiniMessage) to players within 40 blocks when an ability fires.

> **How bosses behave:** a boss without `persistent: true` can still despawn with chunk unloads, and its runtime state (HP, ability cooldowns) is lost if the entity is removed or the server restarts. `persistent: true` keeps it from despawning.

---

## Admin Guide

### Defining a mob

Create a `.yml` file in `plugins/Valmora/mobs/`. Every file there is loaded at startup (and on `/valmora reload` or `/mob reload`). Each top-level key is a mob ID — lowercase it for consistency, and reference that ID everywhere else (commands, zones, scripts, quests, slayers).

Minimal working mob:

```yaml
# plugins/Valmora/mobs/mymobs.yml
forest_goblin:
  category: BEAST
  name: "<green>Forest Goblin"
  type: ZOMBIE
  level: 5
  stats:
    health: 80.0
    damage: 12.0
    speed: 0.28
  equipment:
    main-hand: WOODEN_SWORD
```

> **Required fields:** `category` and `type`. Everything else has a default — but always set `name` (the mob will crash on spawn without one, see notes) and `stats.health` (unset health = 0 HP).

Equipment and loot items can be either **vanilla materials** (`IRON_SWORD`, `LEATHER_HELMET`) or **Valmora item IDs** (`raw_ferrite`) — item IDs are resolved through the item module.

A loot table:

```yaml
cave_guardian:
  category: GOLEM
  name: "<gray>Cave Guardian"
  type: IRON_GOLEM
  level: 15
  stats:
    health: 120.0
    damage: 12.0
    speed: 0.25
  loot-table:
    drops:
      - item: raw_ferrite
        min-amount: 1
        max-amount: 3
        chance: 0.5
        luck-affected: true
```

A full boss with resistances, boss bar, and abilities:

```yaml
forge_titan:
  category: BOSS
  type: IRON_GOLEM
  name: "<red>Forge Titan</red>"
  level: 50
  base-xp: 500
  gold-reward: 1000
  stats:
    health: 25000
    damage: 300
    defense: 400
    speed: 0.25
    strength: 60
    crit-chance: 25
    crit-damage: 80
  resistances:
    FIRE: 1.0          # immune to fire
    EXPLOSION: 0.5     # takes half explosion damage
  knockback-resistance: 1.0
  persistent: true
  boss-bar:
    enabled: true
    color: RED
    style: SEGMENTED_10
    range: 40
  abilities:
    ground-slam:
      name: "Ground Slam"
      trigger: ON_TIMER
      interval: 120
      chance: 0.5
      cooldown: 6.0
      target-range: 12
      announce: "<red>The Forge Titan slams the ground!"
      mechanics:
        - type: DAMAGE
          params:
            amount: 250
            type: MELEE
            target: "@target"
        - type: APPLY_EFFECT
          params:
            effect: slowness
            duration: 3
            amplifier: 2
            target: "@target"
    enrage:
      name: "Enrage"
      trigger: ON_HEALTH
      health-percent: 33
      announce: "<dark_red>The Forge Titan enrages and summons help!"
      mechanics:
        - type: SCRIPT
          params:
            events:
              - "spawn_mob forge_imp 3 radius:4"
```

The `forge_titan` example ships with the plugin in `mobs/test_boss.yml` and is a good reference for the full schema.

### Spawning mobs

There is **no automatic natural spawning**. Mobs appear through these mechanisms:

1. **Command:** `/mob spawn <id> [player]` (see below).
2. **Zone spawners:** `/zone spawner add <zoneId> <mobId> [spawnRadius] [maxAlive] [interval]` — mobs spawn periodically around the spawner point and are counted/alive-limited per zone.
3. **Slayer bosses:** a slayer tier's `boss-mob` field spawns the mob as the boss encounter.
4. **Fishing:** a fishing table's `sea-creature-mob` field can spawn a mob instead of a catch.
5. **Scripts:** the `spawn_mob <id> [count] radius:<r>` script event (usable in ability mechanics, quests, NPC dialogues, etc.).

### `/mob` command

Requires the `valmora.admin` permission (OP by default, `plugin.yml:18-21`). All subcommands support tab completion.

| Subcommand | Usage | Description |
|---|---|---|
| `spawn` | `/mob spawn <id> [player]` | Spawns the mob at your location, or at the named player's location. |
| `list` | `/mob list` | Lists all registered mob IDs. |
| `reload` | `/mob reload` | Hot-reloads **only** the mob module (re-reads all `mobs/*.yml`). |
| `info` | `/mob info` | Shows the definition details (ID, name, entity type, level) of the mob you are looking at within 10 blocks. |

> **Note:** `/valmora reload` reloads every module; `/mob reload` only reloads the mob module — useful when iterating on mob config while a server is live.

### Reloading and validation

When you edit `mobs/*.yml`, run `/mob reload` (or `/valmora reload`). Parse errors are logged in a batch with the file path and mob ID, e.g.:

```
Failed to load some mobs. Please check your configuration files.
- [mobs/test_mobs.yml] In mob 'test_zombie': Missing required field 'category'.
Successfully loaded 0 mobs.
```

Fix the reported entries and reload again.

### Permissions

| Permission | Default | Grants |
|---|---|---|
| `valmora.admin` | OP | `/mob` (spawn, list, reload, info). |

---

## Configuration Reference

Folder: `plugins/Valmora/mobs/`. Every `*.yml` is scanned; each top-level key becomes the mob ID (stored lowercase). ID is case-insensitive when referenced elsewhere.

### Mob fields

| Key | Type | Required | Default | Explanation |
|---|---|---|---|---|
| `<mob-id>` | map key | **yes** | — | The mob's ID, used by `/mob spawn`, zone spawners, quests, slayers, scripts. |
| `name` | String (MiniMessage) | no | *(none)* | Display name on the nameplate. ⚠ **Set it** — omitting it breaks spawning (capitalize NPE). |
| `category` | String enum | **yes** | — | One of `UNDEAD`, `ENDER`, `NETHER`, `BEAST`, `AQUATIC`, `ARTHROPOD`, `ILLAGER`, `GOLEM`, `BOSS`, `OTHER`. |
| `type` | String enum | **yes** | — | Bukkit `EntityType`, e.g. `ZOMBIE`, `IRON_GOLEM`, `VEX`, `MAGMA_CUBE`. |
| `level` | Integer | no | `1` | Shown on the nameplate. Raises damage (`+level−1`) and XP reward (`×level`). |
| `base-xp` | Integer | no | `2` | Combat XP **per level**; actual kill reward is `base-xp × level`. |
| `gold-reward` | Integer | no | `0` | Coins paid to the killer on death (`0` = none). |
| `damage-type` | String enum | no | `MELEE` | `MELEE`, `PROJECTILE`, `FALL`, `DROWNING`, `FIRE`, `LAVA`, `MAGIC`, `VOID`, `POISON`, `WITHER`, `EXPLOSION`. ⚠ Parsed but not yet used by combat logic. |
| `knockback-resistance` | Double | no | `-1.0` | Knockback-resistance attribute; omit/`-1` to keep the vanilla default. |
| `no-ai` | Boolean | no | `false` | Disables the entity's AI entirely (statue). |
| `silent` | Boolean | no | `false` | Mutes the mob's sounds. |
| `glowing` | Boolean | no | `false` | Glowing outline (visible through walls). |
| `persistent` | Boolean | no | `false` | Never despawns when far away / chunk unloads. |
| `baby` | Boolean | no | `false` | Spawns as a baby (ageable types only). |
| `prevent-sun-burn` | Boolean | no | `false` | Prevents **sunlight** burning (undead). Lava/fire sources still burn. |

### `stats:` block

Canonical place for stats. Legacy flat keys (`health`, `base-damage`, `speed`, `defense` at the top level) are also accepted and **override** the `stats:` values.

| Key | Type | Default | Explanation |
|---|---|---|---|
| `stats.health` / `health` | Double | `0.0` | Max HP and starting HP. ⚠ **Always set it** — the default is 0 HP. |
| `stats.damage` / `base-damage` | Double | `5.0` | Base melee attack damage before level scaling. |
| `stats.speed` / `speed` | Double | `0.0` | Vanilla movement-speed attribute (`0.25` ≈ normal walk). |
| `stats.defense` / `defense` | Double | `0.0` | Reduces damage taken via `100/(defense+100)`. |
| `stats.strength` | Double | `0.0` | Boosts the mob's outgoing damage by `× (1 + strength/100)`. |
| `stats.crit-chance` | Double | `0.0` | Mob critical-hit chance in percent. |
| `stats.crit-damage` | Double | `0.0` | Mob critical-hit bonus in percent (e.g. `80` = 1.8× damage). |

### `resistances:` block

Map of damage type (uppercase) → reduction fraction `0.0`–`1.0`.

| Key | Value meaning |
|---|---|
| `FIRE: 0.0` | Full damage. |
| `FIRE: 0.5` | Half damage. |
| `FIRE: 1.0` | **Immune** (fire/lava immunity also stops ongoing burning). |

Damage types: `MELEE`, `PROJECTILE`, `FALL`, `DROWNING`, `FIRE`, `LAVA`, `MAGIC`, `VOID`, `POISON`, `WITHER`, `EXPLOSION`.

### `equipment:` block

Values are vanilla materials or Valmora item IDs.

| Key | Slot |
|---|---|
| `helmet` | Helmet |
| `chestplate` | Chestplate |
| `leggings` | Leggings |
| `boots` | Boots |
| `main-hand` | Weapon |
| `off-hand` | Off-hand |

### `loot-table:` block

`drops` is a list of drop entries.

| Key | Type | Required | Default | Explanation |
|---|---|---|---|---|
| `item` | String | **yes** | — | Material name or Valmora item ID. Invalid entries are skipped. |
| `min-amount` | Integer | no | `1` | Minimum stack size. |
| `max-amount` | Integer | no | `= min-amount` | Maximum stack size (random between min and max). |
| `chance` | Double | no | `1.0` | Probability `0.0`–`1.0` this drop rolls. |
| `luck-affected` | Boolean | no | `false` | Scale chance with the killer's Luck stat: `chance × (1 + Luck/100)`. |

### `boss-bar:` block

| Key | Type | Required | Default | Explanation |
|---|---|---|---|---|
| `enabled` | Boolean | no | `false` | Show an Adventure boss bar to nearby players. |
| `color` | String enum | no | `RED` | `RED`, `BLUE`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE`, `PINK`. |
| `style` | String enum | no | `PROGRESS` | `PROGRESS`/`SOLID`, `NOTCHED_6`/`SEGMENTED_6`, `NOTCHED_10`/`SEGMENTED_10`, `NOTCHED_12`/`SEGMENTED_12`, `NOTCHED_20`/`SEGMENTED_20`. |
| `range` | Double | no | `40.0` | Radius in blocks within which the bar is shown. |

### `abilities:` block

Map of ability-ID → ability config. Any ability makes the mob a **boss** (tracked at runtime).

| Key | Type | Required | Default | Explanation |
|---|---|---|---|---|
| `<ability-id>` | map key | **yes** | — | Unique within the mob; used for cooldown/once-firing tracking. |
| `name` | String | no | ability ID | Display name. |
| `trigger` | String enum | no | `ON_TIMER` | `ON_TIMER` (repeating), `ON_HEALTH` (once below %), `ON_ATTACK`, `ON_DAMAGED`, `ON_SPAWN`, `ON_DEATH`. |
| `interval` | Integer (ticks) | no | `100` | `ON_TIMER`: minimum ticks between eligible firings (20 ticks = 1 s). |
| `chance` | Double | no | `1.0` | `ON_TIMER`: per-eligible-tick chance to actually fire. |
| `health-percent` | Double | no | `50.0` | `ON_HEALTH`: fires once when HP drops to/below this percent. |
| `target-range` | Double | no | `0.0` | Blocks to search for a target player (`0` = no target). |
| `cooldown` | Double (s) | no | `0.0` | Minimum seconds between firings. |
| `announce` | String (MiniMessage) | no | *(none)* | Message broadcast to players within 40 blocks when it fires. |
| `mechanics` | List | no | empty | List of `{type, params}` maps — the same mechanics item abilities use. |

**Available mechanic `type`s** (shared with the item ability system): `DAMAGE`, `HEAL`, `APPLY_EFFECT`, `SCRIPT`, `MODIFY_STAT`, `TELEPORT`, `PUSH_ENTITIES`, `PULL_ENTITIES`, `GIVE_COINS`, `TAKE_COINS`, `IGNITE`, `LAUNCH_PLAYER`, `LAUNCH_PROJECTILE`, `AOE_MINE`. Mechanic `params` use the ability-system placeholders (e.g. `target: "@target"` resolves to the ability's target).

### Quick reference of defaults

| Field | Default |
|---|---|
| `level` | `1` |
| `base-xp` | `2` |
| `gold-reward` | `0` |
| `damage-type` | `MELEE` |
| `stats.damage` | `5.0` |
| `stats.health` | `0.0` ⚠ |
| `knockback-resistance` | `-1.0` (vanilla) |
| `no-ai` / `silent` / `glowing` / `persistent` / `baby` / `prevent-sun-burn` | `false` |
| `loot drop: min-amount` / `max-amount` / `chance` / `luck-affected` | `1` / `= min` / `1.0` / `false` |
| `boss-bar: enabled` / `color` / `style` / `range` | `false` / `RED` / `PROGRESS` / `40.0` |
| ability `trigger` / `interval` / `chance` / `health-percent` / `target-range` / `cooldown` | `ON_TIMER` / `100` / `1.0` / `50.0` / `0.0` / `0.0` |
