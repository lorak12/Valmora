# Valmora Items System — User & Admin Guide

> **For players using custom items and server admins configuring them.**
> This document describes the *current* implementation. Where the original SkyBlock
> source data defines an ability that depends on a mechanic not yet implemented, the
> item is kept as a **DESCRIPTION-ONLY** entry (name + trigger + description, no
> `mechanics:` list) so it loads cleanly rather than crashing. See the
> [Deferred & DESCRIPTION-ONLY Items](#deferred--description-only-items) section.

---

## Table of Contents

1. [What Is a Valmora Item?](#1-what-is-a-valmora-item)
2. [Player Guide](#2-player-guide)
3. [Admin Guide — Creating Items](#3-admin-guide--creating-items)
4. [Item Schema Reference](#4-item-schema-reference)
5. [Ability Schema Reference](#5-ability-schema-reference)
6. [Available Mechanics Reference](#6-available-mechanics-reference)
7. [Ability Triggers](#7-ability-triggers)
8. [Target Selectors](#8-target-selectors)
9. [Damage Types](#9-damage-types)
10. [Item Types & Slots](#10-item-types--slots)
11. [Rarities](#11-rarities)
12. [Stat Reference](#12-stat-reference)
13. [Set Bonuses](#13-set-bonuses)
14. [Variables in Formulas](#14-variables-in-formulas)
15. [Command Reference — `/item`](#15-command-reference--item)
16. [Deferred & DESCRIPTION-ONLY Items](#16-deferred--description-only-items)

---

## 1. What Is a Valmora Item?

Every custom item in Valmora is defined as a YAML file in
`plugins/Valmora/items/`. When the plugin loads (or reloads with `/valmora
reload`), each top-level key in every `.yml` file becomes one item definition.
An item can be:

- A **weapon** (sword, bow, axe, staff) with stat bonuses and active abilities.
- An **armor piece** (helmet, chestplate, leggings, boots) with stats and set
  links.
- A **consumable / material** (e.g. alchemy ingredients, ingots, raw ores).
- A **reforge stone** (auto-generated via the `/item give` command).

All custom items carry their identity, rarity, type, stats, and abilities as
**PDC (PersistentDataContainer) data** on the `ItemStack` (`AGENTS.md` §11.5).
Vanilla items are automatically translated into Valmora items with a
`vanilla_<material>` ID so that the stat/lore pipeline treats them uniformly.

---

## 2. Player Guide

### 2.1 Getting Items

```
/item give <id> [amount] [player]
```

This command requires the `valmora.admin` permission. Some item IDs ending in
`_reforge_stone` are auto-generated as reforge stones.

**Examples:**
```
/item give aspect_of_the_end 1
/item give ferrite_pickaxe 1 @s
/item give warden_helmet   # no amount = 1
```

### 2.2 Item Info

```
/item info <id>      # shows full definition
/item info           # shows info for the item in your main hand
```

Hover over the printed item info to see stats, rarity, type, abilities, and raw
PDC keys.

### 2.3 Listing Items

```
/item list           # prints all registered item IDs
```

### 2.4 What You See In-Game

Valmora items display with:

- A **rarity-colored name** (the rarity color is prepended automatically).
- **Stat lines** showing bonuses (Damage, Strength, Health, Speed, etc.).
- **Custom-model-data** if set.
- **Ability descriptions** in the lore, including trigger type, cooldown, and
  mana cost.

### 2.5 Using Abilities

Each ability on an item has a **trigger** that determines when it fires:

| Trigger | When it fires |
|---|---|
| `RIGHT_CLICK` | Right-click with the item in your main hand |
| `LEFT_CLICK` | Left-click (attack) with the item in your main hand |
| `ON_HIT` | Your melee attack hits a target |
| `ON_KILL` | You kill a mob |
| `SNEAK` | Press sneak (fires on held item **and** worn armor pieces) |
| `ON_SHOOT` | Shoot a bow/crossbow |
| `PASSIVE` | Constantly active while equipped (re-applied on every stat recalculation) |
| `EQUIP` / `UNEQUIP` | — enumerated but **not yet wired** to a listener |
| `ON_DAMAGE_TAKEN` | — enumerated but **not yet wired** |
| `ON_TELEPORT` | — enumerated but **not yet wired** |

**Ability gating rules** (enforced by `AbilityExecutor`):

1. **Conditions** — script expressions (e.g. `$target.type$ == "ZOMBIE"`); the
   ability only fires if all conditions evaluate true.
2. **Cooldown** — if the ability is on cooldown, it will not fire and you'll see
   a red action-bar message with the remaining time.
3. **Mana cost** — if you lack the required mana, the ability is suppressed with
   an aqua "Not enough Mana!" message. Costs are silent on high-frequency
   triggers (ON_HIT, ON_KILL, PASSIVE).

### 2.6 Reloading

```
/valmora reload     # reloads all modules, including items & set bonuses
/item reload        # reloads only the item module
```

---

## 3. Admin Guide — Creating Items

### 3.1 File Layout

```
plugins/Valmora/
├── items/                # all item definitions (*.yml)
├── set_bonuses/          # armor set bonus definitions (*.yml)
├── config.yml            # database, general settings
└── ...
```

Place any number of `.yml` files inside `items/`. Each top-level key in a file
defines one item. The key becomes the item's **ID** (case-insensitive).

### 3.2 Minimal Example

```yaml
my_sword:
  name: "Ironbreaker"
  material: IRON_SWORD
  rarity: UNCOMMON
  item-type: SWORD
  stats:
    DAMAGE: 25
    STRENGTH: 10
    CRIT_CHANCE: 15
```

### 3.3 Full Example — Sword with an Ability

```yaml
flame_wave:
  name: "Flame Wave"
  material: DIAMOND_SWORD
  rarity: EPIC
  item-type: SWORD
  lore:
    - "A blazing blade."
  lore-template:
    - "Damage: <green>$item.stat.damage$</green>"
  custom-model-data: 1001
  set: "young_dragon"
  reforge-pool:
    - fierce
    - sharp
  stats:
    damage: 30.0
    strength: 5.0
  abilities:
    flame_wave:
      name: "Flame Wave"
      trigger: RIGHT_CLICK
      target-range: 12.0
      cooldown: 5.0
      mana-cost: 20.0
      description:
        - "Deals damage in a radius."
      conditions:
        - "$player.weapon_damage$ > 100"
      mechanics:
        - type: damage
          params:
            damage: 10
            damage-type: MAGIC
            target: "@target"
```

### 3.4 Validation Rules

When the item loader parses a definition, it validates:

- `material` — **required**, must be a valid Bukkit `Material`.
- `rarity` — must be one of the valid rarity enum values.
- `item-type` — must be a valid `ItemType` enum value.
- `stats` — every key must be a known stat from `StatRegistry`. Unknown keys
  **fail the entire item load**.
- `abilities` → `mechanics` — every `type:` must map to a registered mechanic.
  Unknown types throw `UnknownMechanicException` and fail the load.
- `trigger` — must be a valid `AbilityTrigger` enum value.

If an item fails to load, it is silently skipped (a warning is logged). Use
`/item list` to confirm your item registered successfully.

### 3.5 Editing While the Server Is Running

Use `/valmora reload` after editing YAML files. This disables and re-enables all
modules in order, then re-parses all `items/*.yml` and `set_bonuses/*.yml` files.
Listeners are unregistered and re-registered to prevent duplicate event handling.

---

## 4. Item Schema Reference

### Top-Level Fields

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `name` | String (MiniMessage) | Recommended | — | Display name; rarity color prepended automatically. |
| `material` | String | **Yes** | — | Any Bukkit `Material` name (e.g. `DIAMOND_SWORD`, `BLAZE_ROD`). |
| `rarity` | String | No | `COMMON` | See [Rarities](#11-rarities). |
| `item-type` | String | No | `NONE` | See [Item Types](#10-item-types--slots). |
| `lore` | List of Strings | No | — | Custom lore lines shown before stats. Supports MiniMessage. |
| `lore-template` | List of Strings | No | — | Same as `lore`, but `$item.stat.<id>$` tokens are resolved at item creation time. |
| `custom-model-data` | Integer | No | — | Custom model data for resource packs. |
| `reforge-pool` | List of Strings | No | — | Reforge-stone candidate list (comma-joined in PDC). |
| `set` | String | No | — | Links to a set ID in `set_bonuses/`. Used for armor sets. |
| `stats` | Map | No | — | Stat bonuses. Keys are case-insensitive stat IDs; values are numbers. |
| `abilities` | Map | No | — | Map of ability definitions. See [Ability Schema](#5-ability-schema-reference). |

**Stat key casing:** keys are lowercased by the parser and matched against
`StatRegistry`. Both `DAMAGE` and `damage` work; the convention in the shipped
YAML files is uppercase.

---

## 5. Ability Schema Reference

Each entry under `abilities:` uses a unique key as the ability id:

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `name` | String | Yes | — | Human-readable name shown in item lore. |
| `trigger` | String | Yes | — | An `AbilityTrigger` value. See [Triggers](#7-ability-triggers). |
| `target-range` | Double | For `RIGHT_CLICK` | — | Max blocks to find a target via `player.getTargetEntity()`. |
| `cooldown` | Double | No | `0` | Cooldown in **seconds**. `0` = no cooldown. |
| `mana-cost` | Double | No | `0` | Mana units consumed per use. `0` = free. |
| `description` | List of Strings | No | — | Lore lines describing the ability. Supports MiniMessage. |
| `conditions` | List of Strings | No | — | Script expression conditions (all must be true). |
| `mechanics` | List of Maps | No | — | Ordered list of mechanic executions. |

### Mechanic Entry Format

Each entry in the `mechanics` list:

```yaml
- type: "<MECHANIC_ID>"      # Required. See [Mechanics Reference](#6-available-mechanics-reference).
  params:                     # Required. A map of key-value parameters.
    <param-name>: <value>
```

The `type` key is case-insensitive (normalized by `MechanicRegistry`). The
`params` map is mechanic-specific (see the table below).

---

## 6. Available Mechanics Reference

All 14 mechanics are registered in `AbilityManager.registerMechanics()`. Their
IDs (as used in the `type:` field) are listed below with their parameters.

> **Note on case:** Mechanic IDs are matched **case-insensitively** by
> `MechanicRegistry` (which uppercases the key on lookup). You may write
> `damage`, `DAMAGE`, or `Damage` — they all resolve to `DamageMechanic`.
> The convention in shipped YAML is uppercase for `DAMAGE`, `HEAL`, etc.

| ID | Mechanics class | Description | Key Params |
|---|---|---|---|
| `damage` | `DamageMechanic` | Deals damage to resolved targets via `DamageCalculator`. | `damage` *or* `amount` (number or formula), `damage-type` *or* `type` (default `MAGIC`), `target` (default `@target`), `ticks` (DoT count), `interval` (seconds between ticks) |
| `heal` | `HealMechanic` | Heals resolved player targets via `PlayerState.heal`. | `heal` (number or formula), `target` (default `@player`), `ticks`, `interval` |
| `apply_effect` | `ApplyEffectMechanic` | Applies a potion effect via `Registry.POTION_EFFECT_TYPE`. | `effect` (vanilla key, e.g. `slowness`), `duration` (seconds; `-1` = infinite), `amplifier` (1-based; 1=Level I), `hide-particles` (bool), `target` |
| `modify_stat` | `ModifyStatMechanic` | Temporarily or permanently modifies a player stat. | `stat` (stat ID), `amount` (number or formula), `duration` (seconds; `-1` = permanent/passive) |
| `teleport` | `TeleportMechanic` | Teleports the caster forward along their facing direction, ray-traced to avoid landing inside blocks. | `distance` (blocks; default 8) |
| `push_entities` | `PushEntitiesMechanic` | Knocks resolved targets away from the caster. | `force` (knockback multiplier), `target` |
| `pull_entities` | `PullEntitiesMechanic` | Pulls resolved targets toward the caster (gravity-well style). | `strength` *or* `force` (knockback multiplier), `target` (default `@enemies_in_radius{r=10}`) |
| `launch_projectile` | `LaunchProjectileMechanic` | Spawns 1+ projectiles with optional on-hit callback mechanics and direct damage. | `projectile` (type), `velocity` (default 2.0), `count` (default 1), `spread` (degrees), `damage` (on impact), `damage-type` (default `MAGIC`), `on-hit` / `on-impact` / `on-land` (nested mechanics list) |
| `launch_player` | `LaunchPlayerMechanic` | Launches the caster through the air. | `y-force` (upward boost; default 1.0), `forward-force` (forward boost; default 1.0) |
| `ignite` | `IgniteMechanic` | Sets resolved targets on fire. | `duration` (seconds; default 3), `target` (default `@target`) |
| `give_coins` | `GiveCoinsMechanic` | Grants coins to the caster via economy. | `amount` (number) |
| `take_coins` | `TakeCoinsMechanic` | Removes coins from the caster's purse. | `amount` (number) |
| `script` | `ScriptMechanic` | Fires a list of script events through the script module. | `events` (list of script event strings) |
| `aoe_mine` | `AoeMineMechanic` | Mines up to `radius` adjacent resource blocks matching the origin block's material. | `radius` (default 1) |

### Damage-Over-Time (ticks / interval)

The `DAMAGE` and `HEAL` mechanics accept `ticks` and `interval` parameters for
damage- or heal-over-time effects. The first burst applies immediately; the
remaining `ticks` are scheduled at `interval` seconds apart via the Bukkit
scheduler. In the current implementation, DoT ticks use a repeating
`runTaskTimer` and stop early if the caster dies.

```yaml
- type: DAMAGE
  params:
    damage: 42,000.0
    damage-type: MAGIC
    target: "@enemies_in_radius{r=10}"
    ticks: 10
    interval: 1.0
```

### Projectile on-Hit Callbacks

`LAUNCH_PROJECTILE` supports nested mechanics that fire when the projectile
hits an entity or block:

```yaml
- type: LAUNCH_PROJECTILE
  params:
    projectile: SNOWBALL
    velocity: 1.8
    count: 1
    damage: 1000.0
    damage-type: MAGIC
    on-hit:
      - type: APPLY_EFFECT
        params:
          effect: slowness
          duration: 5.0
          amplifier: 2
          target: "@target"
```

Nested mechanics support `on-hit`, `on-impact` (alias), and `on-land` keys —
checked in that order.

### Supported Projectile Types

`arrow` (default), `snowball`, `ender_pearl`, `fireball`, `wither_skull`, `egg`.
Unrecognized values fall back to `arrow`.

---

## 7. Ability Triggers

| Trigger | Fire source | Wired? |
|---|---|---|
| `RIGHT_CLICK` | `AbilityListener.onPlayerInteract` — main hand only (`EquipmentSlot.HAND` check) | Yes |
| `LEFT_CLICK` | `AbilityListener.onPlayerInteract` — main hand only | Yes |
| `PASSIVE` | `StatManager.recalculateStats` — executes for every equipped + held item on each recalculation | Yes |
| `ON_HIT` | `CombatListener` — fires `fireHeld(attacker, ON_HIT, victim, silent=true)` after damage is dealt | Yes |
| `ON_KILL` | `AbilityTriggerListener.onKill` — fires held item ability | Yes |
| `SNEAK` | `AbilityTriggerListener.onSneak` — fires held **and** armor piece abilities | Yes |
| `ON_SHOOT` | `AbilityTriggerListener.onShoot` — `EntityShootBowEvent`, held only | Yes |
| `EQUIP` | — no listener currently dispatches this | **No** (enumerated, pending) |
| `UNEQUIP` | — no listener currently dispatches this | **No** (enumerated, pending) |
| `ON_DAMAGE_TAKEN` | — not wired; commented "wired in a later phase" in `AbilityTrigger.java:13-15` | **No** |
| `ON_TELEPORT` | — not wired | **No** |

**Armor-piece ability firing:** `SNEAK` (and any future armor triggers) iterate
the player's armor slots via `AbilityTriggerListener.fireArmor(...)` and fire any
ability on the worn item matching the trigger. This is how armor-set abilities
work (e.g. a `SNEAK` ability on a helmet).

---

## 8. Target Selectors

Mechanics that accept a `target:` parameter use `TargetResolver.resolve(selector,
context)`. The following selectors are supported:

| Selector | Meaning |
|---|---|
| `@player` / `@self` | The caster (player who activated the ability) |
| `@target` | The context target entity (default when selector is blank or unknown) |
| `@enemies_in_radius{r=X}` | Hostile mobs (any non-player living entity) within X blocks |
| `@allies_in_radius{r=X}` | Players within X blocks (includes the caster) |
| `@cone{range=X, angle=Y}` | Enemies in a forward-facing cone (X blocks range, Y degrees) |

**Default radius:** `@enemies_in_radius` and `@allies_in_radius` default to 5
blocks when `r=` is omitted.

**Origin point:** radius/cone selectors are centered on `context.getLocation()`
— the caster's location for normal abilities, or the **projectile impact point**
for on-hit callbacks.

---

## 9. Damage Types

The `damage-type` parameter on the `DAMAGE` (and `LAUNCH_PROJECTILE`) mechanic
is mapped to the combat engine's `DamageType` enum:

| Schema value | Combat engine mapping |
|---|---|
| `MAGIC` | `DamageType.MAGIC` |
| `PHYSICAL` | → `DamageType.MELEE` (the combat engine's melee equivalent) |
| `TRUE` | `DamageType.TRUE` |
| *(other)* | Defaults to `DamageType.MAGIC` |

> The schema uses `PHYSICAL` because Valmora's combat engine has no separate
> "physical" damage type — it maps to `MELEE`. This mapping is handled in
> `DamageMechanic.mapType()` (line 64-73).

---

## 10. Item Types & Slots

`ItemType` categorizes items. The type is stored in PDC and used by mechanics
and recipes to determine applicability.

| `item-type` value | Description |
|---|---|
| `SWORD` | Swords / melee weapons |
| `AXE` | Axes |
| `PICKAXE` | Pickaxes |
| `SHOVEL` | Shovels |
| `HOE` | Hoes |
| `TRIDENT` | Tridents |
| `BOW` | Bows |
| `CROSSBOW` | Crossbows |
| `FISHING_ROD` | Fishing rods |
| `SHEARS` | Shears / special tools |
| `SHIELD` | Shields |
| `ELYTRA` | Elytra wings |
| `HELMET` | Helmets / hats / masks |
| `CHESTPLATE` | Chestplates |
| `LEGGINGS` | Leggings / pants |
| `BOOTS` | Boots |
| `HORSE_ARMOR` | Horse armor |
| `PET` | Pet items |
| `ACCESSORY` | Accessories (necklaces, rings, etc.) |
| `BACKPACK` | Backpack items |
| `NONE` | No specific type (default) |
| `ALL` | Matches any type |

`ItemType.fromMaterial(material)` infers the type from a vanilla Bukkit
material name, used by the vanilla-item translator.

---

## 11. Rarities

| Rarity | Color | Description |
|---|---|---|
| `COMMON` | White (`<white>`) | Basic items |
| `UNCOMMON` | Green (`<green>`) | Improved items |
| `RARE` | Blue (`<blue>`) | Rare items |
| `EPIC` | Dark Purple (`<dark_purple>`) | Epic items |
| `LEGENDARY` | Gold (`<gold>`) | Legendary items |
| `MYTHIC` | Light Purple (`<light_purple>`) | Mythic items |
| `DIVINE` | Aqua (`<aqua>`) | Divine items (highest tier) |

The rarity color is prepended automatically to the item's display name and is
also stored in PDC for reforge cost calculation and lore generation.

---

## 12. Stat Reference

Stat keys are matched against `StatRegistry` (defined in
`stats/core.yml`). Keys are case-insensitive in YAML (lowercased by the
builder). Unknown stat keys will cause the item to fail loading.

| Stat ID | Description |
|---|---|
| `health` | Maximum health bonus |
| `mana` | Maximum mana bonus |
| `damage` | Base weapon damage |
| `strength` | Strength (scales weapon damage) |
| `defense` | Defense (reduces damage taken) |
| `crit_chance` | Critical hit chance (%) |
| `crit_damage` | Critical hit damage multiplier (%) |
| `speed` | Movement speed bonus |
| `health_regen` | Health regeneration rate |
| `mana_regen` | Mana regeneration rate |
| `luck` | Luck (affects drops, reforges) |
| `mining_fortune` | Extra drops from mining |
| `mining_speed` | Mining speed bonus |
| `intelligence` | Intelligence (mana pool / ability power scaling) |
| `ferocity` | Ferocity (chance for extra attack) |
| `pet_luck` | Luck applied to pets |
| `sea_creature_chance` | Chance to spawn sea creatures while fishing |
| `fishing_speed` | Fishing speed bonus |
| `trophy_fish_chance` | Chance for trophy fish catches |
| `bonus_attack_speed` | Additional attack speed |
| `ability_damage` | Scales ability (magic) damage |
| `magic_find` | Increases rare drop chance |
| `true_defense` | True defense (reduces true damage) |
| `vitality` | Vitality stat |
| `farming_fortune` | Extra drops from farming |
| `foraging_fortune` | Extra drops from foraging |
| `breaking_power` | Tool tier — determines which resource blocks can be mined |
| `mining_spread` | Chance for Mining Spread (AOE mining) |

---

## 13. Set Bonuses

Armor sets are defined in `plugins/Valmora/set_bonuses/*.yml`. Each top-level key
is a set ID. Armor pieces link to a set via the `set:` field in their item
definition.

### Set Bonus Schema

```yaml
young_dragon:
  set-id: "young_dragon"      # Optional — defaults to the top-level key
  name: "Young Blood"
  bonuses:
    - pieces-required: 4     # Need 4 pieces worn for this tier
      stats:
        SPEED: 70
    - pieces-required: 2     # Cumulative — 2 pieces also grants this
      stats:
        SPEED: 30
```

### How Set Bonuses Work

- `pieces-required` — the minimum number of armor pieces from this set that must
  be worn for the tier's stats to apply.
- **Tiers are cumulative** — owning 4 pieces of a 2/3/4-piece set grants both
  the 2- and 4-piece bonuses.
- Sets are counted by reading the `ITEM_ID_KEY` PDC on each worn armor piece,
  looking up the item definition, and checking its `set:` field.
- `SetBonusParser` validates every stat key against `StatRegistry`.
- `SetBonusService.applyTo(player, statManager)` counts worn pieces and applies
  cumulative tiers during stat recalculation.

### Shipped Set-Bonus Files

| File | Sets Defined |
|---|---|
| `armor_sets.yml` | `young_dragon` (4pc: +70 Speed), `farm_suit` (4pc: +20 Farming Fortune) |
| `shardworks_sets.yml` | `ferrite_set` (4pc: +5 Mining Fortune), `lumicite_set` (4pc: +10 MF, +1 Mining Spread), `aetherium_set` (4pc: +20 MF, +2 Mining Spread) |
| `sets.yml` | Many sets across categories (farming, mining, dragon, combat, fishing, slayer, dungeon, kuudra, event). See [Deferred Set Bonuses](#deferred--description-only-items) below for which are stat-only vs. deferred. |

### Deferred Set Bonuses

Only **flat-stat** set bonuses are currently expressed in YAML. Sets whose
full-set bonus is a non-stat mechanic (conditional auras, stacking, lightning,
witherborn, damage multipliers, etc.) have an entry in `sets.yml` with a comment
but no `bonuses:` block (or empty `stats: {}`). These are scheduled for a later
phase and are documented inline in the YAML files.

---

## 14. Variables in Formulas

Mechanic parameters marked for formula support (noted in comments) accept either
a plain number or a formula string. Formula values use the `$namespace.path$`
syntax, evaluated by the script module's expression evaluator.

### Supported Variables

| Variable | Meaning | Status |
|---|---|---|
| `$player.last_damage$` | The last hit's damage value (tracked by `CombatTracker`) | Active |
| `$player.max_hp$` | Player's maximum health | Active |
| `$player.stat.<stat_id>$` | Current calculated value of a stat (e.g. `$player.stat.strength$`) | Active |
| `$player.weapon_damage$` | Player's weapon damage stat | Active |
| `$target.type$` | Target entity type (e.g. `"ZOMBIE"`, `"SKELETON"`) | **No provider** — conditions stay inert |
| `$player.world_type$` | World/zone type (e.g. `"MINING_ISLAND"`) | **No provider** — conditions stay inert |
| `$player.in_water$` | Whether the player is in water | **No provider** — proc stays inert |
| `$distance_to_target$` | Distance to the target | **No provider** |
| `$ticks_sneaking$` | Ticks spent sneaking | **No provider** |

### Math Functions

`floor`, `ceil`, `round`, `abs`, `sqrt`, `min`, `max`, `log10`, `log`, `pow`.
Logical operators: `and`/`&&`, `or`/`||`.

### Example — Scaling Damage

```yaml
- type: DAMAGE
  params:
    damage: "$player.last_damage$ * 1.5"     # 150% of the triggering hit's damage
    damage-type: PHYSICAL
    target: "@target"
```

### Example — Scaling Heal

```yaml
- type: HEAL
  params:
    heal: "144 + ($player.max_hp$ * 0.05)"   # 144 + 5% of max HP
    target: "@player"
```

### Formula Limitations

Base item `stats:` values must be **plain numbers** — formula strings are
rejected by the parser and will fail the item load. Only mechanic `params`
support formulas.

---

## 15. Command Reference — `/item`

Permission: `valmora.admin`. Registered at `Valmora.java:237`.

| Command | Description |
|---|---|
| `/item give <id> [amount] [player]` | Gives a custom item. If `<id>` ends in `_reforge_stone`, auto-generates the matching reforge stone. |
| `/item info <id>` | Shows full definition info (name, material, rarity, type, stats, abilities). |
| `/item info` (held) | Shows info for the item in your main hand — includes vanilla material, display name, lore, stats, enchantments, and a raw PDC key dump. |
| `/item list` | Prints all registered item IDs. |
| `/item reload` | Reloads all `items/*.yml` and `set_bonuses/*.yml` files. |
| `/item enchant <enchant_id> <level>` | Applies a Valmora enchantment to the held item (if applicable). |
| `/item enchantbook <enchant_id> <level>` | Gives an enchanted book with the specified enchantment. |

**Tab-completion** is supported for item IDs, enchant IDs, amounts, and player
names.

---

## 16. Deferred & DESCRIPTION-ONLY Items

### 16.1 Mechanics Not Yet Implemented

The following mechanic types from the original design are **deferred** to a later
phase. Items relying solely on these are kept as **DESCRIPTION-ONLY** (no
`mechanics:` block) so the loader does not reject them:

| Missing Mechanic | Example Items Affected |
|---|---|
| `BEAM` | Fire Freeze Staff (delayed-circle freeze), Aurora Staff (beam) |
| `EXPLODE` | Bonzo's Staff (explosion on impact) — *partially approximated* via `LAUNCH_PROJECTILE` + `DAMAGE` |
| `ADD_STACK` | Growth Armor (stacking kill bonuses), Thunder (stack-based charge) |
| `CHARGE_JUMP` | Spring Boots (charged jump height) |
| `CANCEL_TRAMPLE` | Rancher's Boots (no crop trampling) |
| `CONSUME_ITEM` | Primal FEAR, quiver-based arrow consumption |
| `CHANNEL` | Ragnarock (damage-gated channel) |
| `RETURNING_PROJECTILE` | Tribal Spear, Livid Dagger |
| `NEXT_HIT_BUFF` | Edible Mace, Sword of Bad Health |
| `ENTITY_SUMON` | Necromancer Sword (soul summoning) |
| `KILL_COUNTER` | Fel Sword, Zombie Commander Whip, Recluse Fang |

### 16.2 Items That Are DESCRIPTION-ONLY in Shipped YAML

Many items in `items/*.yml` have abilities with **only** `name`, `trigger`, and
`description` but **no `mechanics:`**. These abilities have flavor text but do
nothing in-game. They are intentionally preserved so the items still load and can
be upgraded later when the missing mechanics are implemented. Examples include:

- **Midas' Sword** (Greed) — purse-scaled damage boost (needs `CONSUME_ITEM`)
- **Daedalus Blade** (Taming Mastery) — pet-stat copy + coin reward (needs
  `KILL_COUNTER` + pet variables)
- **Necromancer Sword** (Raise Souls) — soul collection & summoning
- **Flower Of Truth / Bouquet Of Lies** — ricocheting projectiles
- **Wither Cloak Sword** — deactivatable damage-immunity veil
- **Livid Dagger** — returning dagger + backstab detection
- **Spirit Sceptre** — guided/homing projectile
- **Shadow Fury** — multi-enemy sequential teleport (approximated with `APPLY_EFFECT` Slowness on nearby enemies)
- **Tarantula / Recluse Fang** — kill-counter stacking buffs
- **Warden Helmet** — `DAMAGE_MULTIPLIER` modifier recorded but not fed into the damage formula (Speed halving is active via `MODIFY_STAT`)

### 16.3 Bow Items Deferred

All bow/shortbow items that rely on `LAUNCH_PROJECTILE` with on-hit callbacks,
homing `BEAM`, returning `Bonemerang`, or missing-HP scaling are either:

- Authored with a **partial** `LAUNCH_PROJECTILE` mechanic (e.g. Dreadlord Sword,
  Yeti Sword, Ember Rod — these fire projectiles with direct damage but lack
  the on-land/on-impact firestorm callback), or
- Kept as **DESCRIPTION-ONLY** entirely (e.g. Machine Gun Shortbow, Soulstealer
  Bow, Mosquito Shortbow).

### 16.4 Damage-Heal Over-Time (DoT/HoT) Limitation

The `ticks` and `interval` parameters on `DAMAGE` and `HEAL` mechanics are
accepted by the loader and will execute multiple bursts, but several source
comments note that "damage-over-time is deferred; applies a single burst." In
practice, the `runTaskTimer` implementation in `DamageMechanic` (line 44) and
`HealMechanic` (line 35) **does** schedule repeated hits. Any YAML using
`ticks`/`interval` will produce DoT behavior.

### 16.5 Zone-Conditional Effects

Several items reference zone-conditional or player-state-conditional bonuses
(e.g. "near Farming Minions", "on Crimson Isle", "in Dungeons", "in water").
These rely on `$player.zone$` or `$player.world_type$` variables that have **no
provider yet**, so the conditions always evaluate false and the abilities stay
inert. The items load and display correctly; the mechanics simply never trigger
until a zone variable provider is added.

---

### File Index

**Item definition files** (in `src/main/resources/items/`, loaded into
`plugins/Valmora/items/` at runtime):

| File | Contents |
|---|---|
| `example.yml` | Test items, sample sword/bow/staff, forge items (`enchanted_diamond`, `reinforced_ingot`, `forged_blade`), and complete ability examples |
| `new_items.yml` | Foundation round: Rogue Sword, Cleaver, Golem Sword, Aspect of the End/Dragons, Ornate Zombie Sword, Emerald Blade, Leaping Sword, Pigman Sword, Aurora Staff, ward/staff armor sets |
| `swords.yml` | Vanilla-tier swords, early shop weapons, spider/end swords, Flaming Sword, Jerry-chine Gun, Void Sword, Zombie/Florid Zombie Swords, spears, staves (Fire Freeze, Fire Fury), Silk-Edge, Frozen Scythe, Yeti Sword, Ragnarock, Blade of the Volcano, Enrager, Great Spook, Bingolibur, Ember Rod |
| `slayer_swords.yml` | Slayer weapons: Revenant/Reaper Falchion, Halberd of the Shredded, Reaper Scythe, Recluse Fang, Scorpion Foil, Shaman Sword, Edible Mace, Pooch Sword, Voidwalker/Voidedge/Vorpal/Atomsplit Katanas, Sinseeker Scythe, Aspect of the Void, Blaze attunement daggers |
| `catacombs_swords.yml` | Catacombs weapons: Super/Hyper/Giant Cleaver, Dreadlord Sword, Zombie Knight Sword, Zombie Commander Whip, Zombie Soldier Cutlass, Ice Spray Wand, Flower Of Truth, Bouquet Of Lies, Bone Reaver, Felthorn Reaper, Fel Sword, Wither Cloak Sword, Bonzo's Staff, Adaptive Blade, Spirit Sword/Sceptre, Shadow Fury, Livid Dagger, Giant's Sword, Necromancer Sword, Necron's Blade family (Necron's Blade, Valkyrie, Hyperion, Scylla, Astraea, Dark Claymore) |
| `wands.yml` | Support wands (Healing, Mending, Restoration, Strength), damage wands (Celeste, Alchemist's, Starlight, Volcano, Rising Sun, Hellstorm), fire staffs (Fire Freeze, Fire Fury) |
| `bows.yml` | Bows: Bow, Decent Bow, Wither Bow, Prismarine Bow, Savanna Bow, End Stone Bow, Slime Bow, Hurricane Bow, Sulphur Bow, Spider Queen's Stinger, Venom's Touch, Souls Rebound, Dragon/Spider/Mosquito Shortbows, Machine Gun/Soulstealer/Sniper/Undead/Super Undead/Death Last Breath Spirit Shortbow |
| `armor_sets.yml` | ~500 lines of armor sets: generic (leather→diamond), farming (Farm/Spray/Melon/Cropie/Squash/Fermento), combat (Mushroom/Pumpkin/Cactus/Lettuce/Spider), combat (Rosetta's/Squire/Celeste/Mercenary/Starlight/Golem/Growth/Monster Hunter/Raider), mining (Prospecting/Lapis/Hardened Diamond/Mineral/Glossy Mineral/Goblin/Glacite/Heat/Yog/Flamebreaker/Sorrow/Divan), fishing (Angler/Salmon/Diver's/Sponge/Shark Scale/Thunder/Magma Lord/Hunters), dragon, slayer, dungeon, kuudra, misc/event armors |
| `individual_pieces.yml` | Hats, masks, crowns, chestplates, leggings, boots that are not part of a full set (Blaze Fish Chicken Cow Creeper etc. hats; zombie/salmon/armadillo/parrot/bee/frog/snowman/party masks; Dungeon boss heads; crowns; chestplates; pants; boots) |
| `shardworks_pickaxes.yml` | Ferrite/Lumicite/Aetherium pickaxes (mining progression tools) |
| `shardworks_ores.yml` | Raw/refined Shardworks materials (raw/processed ores, crystal wraith core) |
| `shardworks_armor.yml` | Ferrite/Lumicite/Aetherium mining armor sets (4 pieces each) |
| `alchemy_ingredients.yml` | Enchanted potion ingredients and modifiers (Speed, Strength, Regen, Poison, etc.) |

**Set bonus files** (in `src/main/resources/set_bonuses/`):

| File | Contents |
|---|---|
| `armor_sets.yml` | Young Dragon (4pc: +70 Speed), Farm Suit (4pc: +20 Farming Fortune) |
| `shardworks_sets.yml` | Ferrite Set (+5 Mining Fortune), Lumicite Set (+10 MF, +1 Spread), Aetherium Set (+20 MF, +2 Spread) |
| `sets.yml` | All other set bonuses organized by category (farming, mining, dragon, combat, slayer, dungeon, kuudra, event) — most are stat-only; many are deferred with comments |
