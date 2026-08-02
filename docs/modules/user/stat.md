# Stat Module — User Documentation

> The **Stat Module** powers every number in Valmora: Health, Mana, Damage, Defense, Crit Chance, Mining Fortune, and more. Stats are defined in YAML, aggregated from your gear/enchants/pets/buffs, and displayed in the stats GUI and action bar.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

Every player has a set of **stats** — numeric values that determine how strong, fast, lucky, and resilient you are. Stats come from many places:

- Your **base profile** (starting values and points you spend via commands/plugins)
- **Items** (weapons, armor, accessories) with stat bonuses
- **Enchantments** and **armor set bonuses**
- **Pets** and **progression-tree upgrades**
- **Alchemy effects**, **temporary buffs**, and **passive ability mechanics**

All of these are combined into a single **effective value** per stat, which is what the game actually uses (combat damage, health regen, mana regen, mining output, etc.). Recalculation happens automatically whenever your loadout changes — equipping armor, swapping weapons, joining the server, respawning, or casting a buffing ability.

The base stat definitions are data-driven and fully configurable by the server owner (see §3 and §4).

---

## 2. Player Guide

### 2.1 Viewing your stats

- `/stat list` — prints every stat you have, with its current effective value.
- The **stats GUI** (if your server has it bound to a command or item) shows each stat with its icon, name, and description.
- Your **action bar** typically shows your key combat/resource stats (Health, Mana, Speed).

### 2.2 How stats change

| Trigger | Example |
| --- | --- |
| Equipping/unequipping armor | Defense goes up with a chestplate equipped |
| Changing weapons or off-hand | Damage, Strength, or Ability Damage changes |
| Hotbar scroll / hand swap (`F`) | Bonus Attack Speed, speed, etc. refresh |
| Enchanting / reforging an item | Enchant/reforge stat bonuses apply |
| Drinking a potion | Alchemy effects modify stats while active |
| Ability buffs / temporary buffs | `Modify Stat` effects add/remove stats for a duration |
| Pet summoned / set-bonus armed | Pet and set-bonus stats apply |
| Joining / respawning | Stats recalculate automatically; you respawn at full Health |

### 2.3 What the core stats mean

| Stat | Meaning |
| --- | --- |
| **Health** | Your max health pool. Damage reduces it; regeneration restores it. |
| **Mana** | Your max mana pool, spent by abilities. |
| **Damage** | Base melee/attack damage. |
| **Strength** | Bonus attack damage. |
| **Defense** | Reduces incoming damage. |
| **True Defense** | Pierces defense bypasses. |
| **Crit Chance** | Chance (up to the cap) to land a critical hit. |
| **Crit Damage** | Extra damage dealt on a critical hit. |
| **Speed** | Movement speed (drives the vanilla movement attribute). |
| **Health Regen / Mana Regen** | Points restored per regeneration tick. |
| **Luck / Magic Find / Pet Luck** | Drop-quality bonuses. |
| **Mining Fortune / Speed / Breaking Power / Spread** | Mining output and efficiency stats. |
| **Farming Fortune / Foraging Fortune** | Crop and log drop multipliers. |
| **Sea Creature Chance / Fishing Speed / Trophy Fish Chance** | Fishing stats. |
| **Ferocity** | Chance for extra attack hits. |
| **Ability Damage** | Boosts ability damage. |
| **Bonus Attack Speed** | Raises your attack speed above vanilla. |
| **Vitality** | Potentially boosts healing received. |

> Exact values, caps, and display may differ per server — everything is configurable (§4).

### 2.4 Where your stat *numbers* come from

Your **base stats** live on your player profile (persisted). **Item/enchant/pet/buff bonuses** are layered on top at all times and are *not* part of your base. Removing a piece of gear removes its bonuses automatically — your base values only change when a command, plugin, or progression system edits them.

---

## 3. Admin Guide

### 3.1 Files

| Path (runtime) | Purpose |
| --- | --- |
| `plugins/Valmora/stats/*.yml` | Stat definitions (a copy of `core.yml` is placed here on first run). |
| `plugins/Valmora/config.yml` | `combat:` / `mining:` sections map logical roles to stat ids (§3.4). |
| `plugins/Valmora/database.db` | Player profiles incl. persisted `baseStats` (SQLite). |
| `plugins/Valmora/guis/stats.yml` | The stats GUI layout (stat-driven). |

### 3.2 Reloading

Run `/valmora reload` (requires `valmora.admin`). This disables and re-enables every module, so:

- `stats/*.yml` changes are **reloaded** (definitions are rebuilt).
- `combat:` / `mining:` mapping changes are **reloaded** (`SystemStats` is rebuilt).
- Item/player stats are unaffected: item stats live in item PDC and profiles keep their `baseStats` across the reload.

> While reloading, the stats GUI/lore may show stale definitions for the instant the module is down. No player data is lost.

### 3.3 Editing stats

- **Base stat values:** use `/stat add <id> <amount>` and `/stat remove <id> <amount>` (both require `valmora.admin`). These mutate the player's persisted profile.
- **Definitions:** edit `stats/*.yml` and reload. Adding a new stat automatically gives every player its `default-value`; removing one drops the stored value.
- **Caps:** set `max-value` to enforce a hard cap on the *effective* value (e.g. `crit-chance` capped at `100.0`).

### 3.4 Remapping a core stat

If you want to rename a core stat (e.g. call Health "HP" or replace Damage with your own stat), **do not** hardcode anything — edit the `combat:` / `mining:` sections in `config.yml`:

```yaml
combat:
  health-stat: "health"          # id used for the health pool & regen
  mana-stat: "mana"              # id used for the mana pool & regen
  damage-stat: "damage"          # damage input for combat
  strength-stat: "strength"      # bonus damage
  defense-stat: "defense"        # damage reduction
  crit-chance-stat: "crit_chance"
  crit-damage-stat: "crit_damage"
  speed-stat: "speed"            # movement speed attribute binding
  health-regen-stat: "health_regen"
  mana-regen-stat: "mana_regen"
  luck-stat: "luck"

mining:
  mining-fortune-stat: "mining_fortune"
  mining-speed-stat: "mining_speed"
  breaking-power-stat: "breaking_power"
  mining-spread-stat: "mining_spread"
```

Each key maps an **engine role** to a **stat id** defined in `stats/*.yml`. Point them at any registered stat. (Known limitation: the temporary-buff stat is parsed from config but is **not yet** consumed — see the design doc §8.)

### 3.5 Vanilla attribute bindings

A definition with `vanilla-attribute` is synced to the player's vanilla attribute:

| `vanilla-attribute` | Behavior |
| --- | --- |
| `movement_speed` | Sets the attribute base to `0.1 × speed / 100`. `speed: 100` → normal walking speed. |
| `block_break_speed` | Applies a *bonus* over the vanilla baseline: values above `100` add `(value − 100) / 100` to mining speed. |
| any other attribute | Sets the attribute **base** to `0.1 × stat / 100`. |

This is applied in-memory on every recalculation (and on join), never persisted.

### 3.6 Notes on vanilla health regen

Vanilla hunger/saturation regeneration and `Regeneration` magic regen are **cancelled for players** so only stat-driven regen (via `health_regen`) applies. Custom/API healing still works normally.

---

## 4. Configuration Reference

### 4.1 Stat definition schema — `stats/*.yml`

One block per stat id (top-level key). File names are arbitrary; multiple files are merged.

```yaml
<stat-id>:
  display-name: "Health"           # shown name (MiniMessage tags supported)
  default-value: 100.0             # starting/base value for every player
  max-value: 10000.0               # hard cap on effective value; omit for no cap
  color: "<red>"                   # MiniMessage color used in display
  icon: "APPLE"                    # Bukkit Material for the stats GUI
  description: "Your maximum health pool."   # shown in the stats GUI
  pool: true                       # marks a resource pool (health/mana display)
  vanilla-attribute: "movement_speed"        # optional vanilla attribute binding
```

### 4.2 Field reference

| Key | Type | Default | Required | Effect |
| --- | --- | --- | --- | --- |
| `display-name` | string | stat id | no | Display name in GUI/commands/action bar. |
| `default-value` | double | `0.0` | no | Base value new profiles start with. |
| `max-value` | double | uncapped | no | Clamps the effective value after all bonuses. |
| `color` | string (MiniMessage) | `<white>` | no | Prefix color for the stat's display. |
| `icon` | string (Material) | `PAPER` | no | GUI icon item material. |
| `description` | string | `""` | no | Lore shown in the stats GUI. |
| `pool` | bool | `false` | no | Flags the stat as a resource pool (Health/Mana). |
| `vanilla-attribute` | string | — | no | Attribute id to sync (§3.5). |

### 4.3 Shipped defaults (`stats/core.yml`)

The plugin ships **28** default stats. Ids: `health`, `mana`, `damage`, `strength`, `defense`, `crit_chance` (cap 100), `crit_damage`, `speed` (attribute-bound), `health_regen`, `mana_regen`, `luck` (cap 100), `mining_fortune` (cap 500), `mining_speed` (attribute-bound), `intelligence`, `ferocity` (cap 100), `pet_luck`, `sea_creature_chance` (cap 100), `fishing_speed`, `trophy_fish_chance` (cap 100), `bonus_attack_speed`, `ability_damage`, `magic_find`, `true_defense`, `vitality`, `farming_fortune` (cap 500), `foraging_fortune` (cap 500), `breaking_power` (cap 20), `mining_spread` (cap 10).

### 4.4 Negative values

Negative stat values are valid: an item (or effect) can *reduce* a stat below base, and the display shows the minus sign. `default-value` can be negative too.

### 4.5 Adding a custom stat — worked example

```yaml
# stats/custom.yml
swim_speed:
  display-name: "Swim Speed"
  default-value: 0.0
  max-value: 100.0
  color: "<blue>"
  icon: "WATER_BUCKET"
  description: "Increases your swimming speed."
```

After `/valmora reload`, `swim_speed` is a real stat: it appears in `/stat list`, is available to item/reforge/enchant plugins via the API, and can be mapped into any `combat:`/`mining:` role.
