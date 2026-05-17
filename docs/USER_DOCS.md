# Valmora — Server Owner & Player Documentation

> **Version:** 0.1 | **Server:** Paper 1.21.x | **Java:** 21

This document covers everything you need to know to install, configure, and get the most out of the Valmora RPG plugin — from first-time setup to authoring every kind of content the engine supports.

---

## Table of Contents

1. [What is Valmora?](#1-what-is-valmora)
2. [Requirements & Installation](#2-requirements--installation)
3. [First-Time Setup](#3-first-time-setup)
4. [config.yml Reference](#4-configyml-reference)
5. [Commands Reference](#5-commands-reference)
6. [Permissions](#6-permissions)
7. [Player Systems](#7-player-systems)
   - [Profiles](#71-profiles)
   - [Stats](#72-stats)
   - [Skills](#73-skills)
   - [Combat](#74-combat)
   - [Economy](#75-economy)
8. [Items — `items/*.yml`](#8-items--itemsyml)
9. [Enchantments — `enchants/*.yml`](#9-enchantments--enchantsyml)
10. [Mobs — `mobs/*.yml`](#10-mobs--mobsyml)
11. [Alchemy — `alchemy/*.yml`](#11-alchemy--alchemyyml)
12. [Zones — `zones/*.yml`](#12-zones--zonesyml)
13. [Fishing Loot Tables — `fishing/*.yml`](#13-fishing-loot-tables--fishingyml)
14. [NPCs — `npcs/*.yml`](#14-npcs--npcsyml)
15. [Dialogues — `dialogues/*.yml`](#15-dialogues--dialoguesyml)
16. [Warps — `warps/*.yml`](#16-warps--warpsyml)
17. [Quests — `quests/*.yml`](#17-quests--questsyml)
18. [GUIs — `guis/*.yml`](#18-guis--guisyml)
19. [Recipes — `recipes/*.yml`](#19-recipes--recipesyml)
20. [Skills YAML — `skills/*.yml`](#20-skills-yaml--skillsyml)
21. [Script Variables & Events DSL](#21-script-variables--events-dsl)
22. [Stat Reference](#22-stat-reference)
23. [Rarity Reference](#23-rarity-reference)
24. [Damage Type Reference](#24-damage-type-reference)
25. [MiniMessage Formatting](#25-minimessage-formatting)
26. [Tutorials](#26-tutorials)

---

## 1. What is Valmora?

Valmora is a **modular RPG engine** for Paper 1.21.x. It replaces and extends Minecraft's core gameplay with a complete MMORPG framework including:

- **Custom stats** (Health, Damage, Defense, Mana, etc.) calculated from equipped items
- **Character profiles** — multiple save slots per player, each with independent stats and skill progress
- **Custom items** — YAML-defined with stats, rarity, lore, and multi-mechanic abilities
- **Custom mobs** — YAML-defined with health, damage, speed, level, and equipment
- **Skill system** — five levelled skills (Mining, Farming, Foraging, Fishing, Combat) with XP, level rewards, and milestones
- **Enchantment system** — custom enchantments with defined effects, level caps, and conflict rules
- **Alchemy system** — brew potions with stat buffs/debuffs, splash radius, and duration tiers
- **GUI framework** — fully data-driven inventory screens loaded from YAML
- **Recipe engine** — shapeless, shaped, and exact-slot crafting for custom machines
- **Zone system** — named world regions with resource nodes, mob spawners, and fishing tables
- **NPC system** — custom named entities with dialogue trees and GUI interactions
- **Warp system** — teleport pads with named destinations
- **Quest system** — branching quests with objectives, rewards, and on-start hooks
- **Hot-reload** — reload all content with `/valmora reload` — no server restart needed

Everything is defined in plain YAML files. No coding required to create content.

---

## 2. Requirements & Installation

| Requirement | Version |
|---|---|
| Server software | **Paper 1.21.x** (Spigot and CraftBukkit are not supported) |
| Java | **21** or newer |

**Installation steps:**

1. Download `Valmora-0.1.jar`.
2. Place it in your server's `plugins/` folder.
3. Start the server once — Valmora will create all default config files.
4. Stop the server.
5. Edit `plugins/Valmora/config.yml` as needed (see §4).
6. Restart the server.

**Generated file structure on first run:**

```
plugins/Valmora/
├── config.yml                    ← Main configuration
├── database.db                   ← SQLite database (auto-created)
├── time.yml                      ← Persistent RPG calendar state
├── items/
│   └── example.yml               ← Example items with abilities
├── mobs/
│   └── test_mobs.yml             ← Example custom mobs
├── enchants/
│   └── example_enchantments.yml  ← Example enchantments
├── guis/                         ← Pre-built GUI screens
├── recipes/                      ← Pre-built recipes
├── alchemy/                      ← Alchemy potion definitions
├── skills/                       ← Skill definitions with XP sources
├── zones/                        ← World zone definitions
├── npcs/                         ← NPC definitions
├── dialogues/                    ← NPC dialogue trees
├── warps/                        ← Warp point definitions
├── quests/                       ← Quest definitions
└── fishing/                      ← Fishing loot tables
```

---

## 3. First-Time Setup

After the first run and stopping the server:

1. **Choose a database** — defaults to SQLite (no setup needed). For a network, switch to MySQL in `config.yml`.
2. **Configure the RPG calendar** — set starting year, season, phase, and day in `config.yml` under the `time:` block. This only takes effect on the very first start; afterwards it is persisted in `time.yml`.
3. **Create your items** — copy `example.yml` or create new files in `plugins/Valmora/items/`.
4. **Create your mobs** — add entries in `plugins/Valmora/mobs/`.
5. **Design zones** — define named regions in `plugins/Valmora/zones/`.
6. **Reload at any time** in-game with `/valmora reload` (requires OP or `valmora.admin`).

---

## 4. config.yml Reference

```yaml
# ========================================================== #
#                    Database Settings                       #
# ========================================================== #
database:
  type: sqlite        # 'sqlite' (default) or 'mysql'

  # Only needed when type is 'mysql':
  # mysql:
  #   host: "127.0.0.1"
  #   port: 3306
  #   database: "valmora"
  #   username: "root"
  #   password: "password123"

# ========================================================== #
#                        Time Settings                       #
# ========================================================== #
time:
  world: world                    # World to read Minecraft day/time from
  start-year: 1
  start-season: SPRING            # SPRING | SUMMER | AUTUMN | WINTER
  start-phase: EARLY              # EARLY | MID | LATE
  start-day: 1                    # 1–30
  season-names: [Spring, Summer, Autumn, Winter]
  phase-names:  [Early, Mid, Late]
  scoreboard-enabled: true        # Show time in sidebar scoreboard

# ========================================================== #
#               Combat / Engine Stat Mapping                 #
# ========================================================== #
# Maps internal engine roles to stat IDs defined in stats/*.yml.
# Change only if you rename a core stat.
combat:
  health-stat:      health
  mana-stat:        mana
  damage-stat:      damage
  strength-stat:    strength
  defense-stat:     defense
  crit-chance-stat: crit_chance
  crit-damage-stat: crit_damage
  speed-stat:       speed
  health-regen-stat: health_regen
  mana-regen-stat:  mana_regen
  luck-stat:        luck

# ========================================================== #
#                     Mining Settings                        #
# ========================================================== #
mining:
  mining-fortune-stat: mining_fortune
  mining-speed-stat:   mining_speed

# ========================================================== #
#                     Alchemy Settings                       #
# ========================================================== #
alchemy:
  splash-radius: 4.0      # Block radius for splash potions
  tick-interval: 20       # Ticks between active-effect expiry checks
  max-active-effects: 10  # Max concurrent alchemy effects per player
```

| Field | Default | Description |
|---|---|---|
| `database.type` | `sqlite` | `sqlite` stores data locally. `mysql` is for multi-server networks. |
| `time.world` | `world` | The world whose day/night cycle drives the RPG clock. |
| `time.start-*` | See above | Starting position of the RPG calendar. Applied only once (first run). |
| `time.scoreboard-enabled` | `true` | Whether season/time lines appear in the sidebar. |
| `combat.*-stat` | Various | Links each engine role to a stat ID from `stats/core.yml`. |
| `alchemy.splash-radius` | `4.0` | Blocks around the impact point that receive splash effects. |
| `alchemy.max-active-effects` | `10` | Hard cap on simultaneous alchemy buffs/debuffs per player. |

---

## 5. Commands Reference

All commands support **tab-completion**.

### `/valmora` — Admin Commands

| Command | Permission | Description |
|---|---|---|
| `/valmora reload` | `valmora.admin` | Hot-reloads all modules: re-reads every YAML file without restarting the server. |

### `/profile` — Character Profiles

| Command | Who can use | Description |
|---|---|---|
| `/profile create <name>` | Any player | Creates a new character profile. |
| `/profile delete <name>` | Any player | Deletes the named profile. |
| `/profile switch <name>` | Any player | Switches to the named profile; stats are recalculated instantly. |
| `/profile list` | Any player | Lists all your profiles; active profile shown in green. |
| `/profile info` | Any player | Shows the active profile's name, ID, health, mana, and combat state. |

### `/stat` — Stat Management

| Command | Who can use | Description |
|---|---|---|
| `/stat list` | Any player | Prints all current stat values for the active profile. |
| `/stat add <STAT> <amount>` | Any player | Adds `amount` to the specified stat. |
| `/stat remove <STAT> <amount>` | Any player | Subtracts `amount` from the specified stat. |

Stat names: `DAMAGE`, `HEALTH`, `STRENGTH`, `DEFENSE`, `CRIT_CHANCE`, `CRIT_DAMAGE`, `SPEED`, `MANA`, `HEALTH_REGEN`, `MANA_REGEN`.

### `/item` — Custom Items

| Command | Who can use | Description |
|---|---|---|
| `/item give <id> [player]` | Any player | Gives the Valmora item by ID to yourself or another player. |
| `/item list` | Any player | Lists all registered item IDs. |
| `/item info <id>` | Any player | Shows the full definition of the item. |

### `/mob` — Custom Mobs

| Command | Who can use | Description |
|---|---|---|
| `/mob spawn <id> [player]` | Any player | Spawns the mob at your location (or the named player's location). |
| `/mob list` | Any player | Lists all registered mob IDs. |
| `/mob reload` | Any player | Hot-reloads only the mob module. |
| `/mob info` | Any player | Shows details for the mob you are looking at (within 10 blocks). |

### `/skill` — Skill Progress

| Command | Permission | Description |
|---|---|---|
| `/skill info [skill]` | Any player | Shows XP and level for all skills (or one specific skill). |
| `/skill list` | Any player | Lists all available skills and their max levels. |
| `/skill givexp <player> <skill> <amount>` | `valmora.admin` | Grants XP in the specified skill to a player. |
| `/skill setlevel <player> <skill> <level>` | `valmora.admin` | Sets the skill to the exact level. |

---

## 6. Permissions

| Node | Default | Grants |
|---|---|---|
| `valmora.admin` | OP only | `/valmora reload`, `/skill givexp`, `/skill setlevel`, and any other admin-only subcommands. |

All other commands (`/profile`, `/stat`, `/item`, `/mob`, `/skill info`, `/skill list`) are available to every player without any permission node.

---

## 7. Player Systems

### 7.1 Profiles

Each player can have **multiple character profiles**. Profiles are separate save slots — each has its own stats, skill levels, tags, and custom variables. Only one profile is active at a time.

- A **Default** profile is created automatically on first join.
- Use `/profile create <name>` to add new profiles.
- Switch between them with `/profile switch <name>`.
- Stats, skills, and progress are fully independent between profiles.

### 7.2 Stats

Stats are numerical values that define a player's power. They come from three sources:

1. **Base values** — defined per stat in `stats/core.yml`.
2. **Equipment bonuses** — added from every Valmora item in the weapon, off-hand, and armor slots.
3. **Passive abilities** — applied during stat recalculation when items with `PASSIVE` abilities are equipped.

Stats are recalculated automatically whenever the player's equipment changes (equip/unequip, swap hands, respawn, join).

| Stat | Default | Effect |
|---|---|---|
| `HEALTH` | 100 | Max health pool. Visual hearts scale to this value. |
| `MANA` | 100 | Resource consumed when using active abilities. |
| `DAMAGE` | 5 | Base attack power. |
| `STRENGTH` | 0 | Increases all outgoing damage: `damage × (1 + strength/100)`. |
| `DEFENSE` | 0 | Reduces incoming damage: multiplier = `100 / (defense + 100)`. |
| `CRIT_CHANCE` | 30 | Percentage chance (0–100) to land a critical hit. |
| `CRIT_DAMAGE` | 50 | Bonus damage on crits. A value of 50 = 1.5× normal damage. |
| `SPEED` | 100 | Movement speed. 100 = normal vanilla speed. |
| `HEALTH_REGEN` | 1 | HP restored per second while out of combat. |
| `MANA_REGEN` | 2 | Mana restored per second (always). |
| `LUCK` | 0 | Increases loot quality and drop rates. |
| `MINING_FORTUNE` | 0 | Multiplies drops from resource blocks. |
| `MINING_SPEED` | 100 | How fast blocks break. Mapped to vanilla `block_break_speed`. |

Negative stat values are valid (e.g., `SPEED: -10` on heavy armor).

### 7.3 Skills

Skills are levelled by performing in-game actions. XP sources and per-level rewards are defined in `skills/*.yml` (see §20).

| Skill | Max Level | XP Source |
|---|---|---|
| Mining | 60 | Breaking stone, ores, and deepslate |
| Farming | 60 | Breaking grown crop blocks |
| Foraging | 60 | Breaking logs |
| Fishing | 60 | Catching fish in Fishing Zones |
| Combat | 60 | Killing mobs |
| Alchemy | 60 | Brewing potions |

Players receive an **action bar notification** on XP gain and a **chat announcement** on level-up. Admins can grant XP or set levels directly with `/skill givexp` and `/skill setlevel`.

### 7.4 Combat

Valmora replaces the vanilla damage system entirely.

**Damage formula:**
```
fullDamage = baseDamage × (1 + strength / 100)
if critical: fullDamage = fullDamage × (1 + critDamage / 100)
finalDamage = floor(fullDamage × 100 / (defense + 100))
```

Damage types `VOID`, `DROWNING`, `FALL`, and `TRUE` bypass defense. `TRUE` damage skips the formula entirely and applies the raw amount.

Floating **damage indicators** spawn above the victim:
- Critical hits display gold bold text with ✧ symbols.
- Normal hits display colored numbers based on damage type.

**Regeneration** ticks every second:
- Health regen: only while **out of combat** (no damage for 3 seconds).
- Mana regen: always.

### 7.5 Economy

Valmora includes a lightweight economy system. Coins are stored in `player.var.coins` (a custom profile variable). You can modify balances via the `variable` script event or custom mechanics. A bank GUI is included in the default files.

---

## 8. Items — `items/*.yml`

Place any number of `.yml` files in `plugins/Valmora/items/`. Each top-level key defines one item. The key is the **item ID** (case-insensitive).

### Full Schema

```yaml
<item-id>:
  name: "<MiniMessage display name>"   # Shown as item name (rarity color prepended automatically)
  material: <BUKKIT_MATERIAL>          # REQUIRED — e.g. DIAMOND_SWORD, BLAZE_ROD
  rarity: <RARITY>                     # Optional. Default: COMMON
  item-type: <ITEM_TYPE>               # Optional. Default: NONE
  lore:                                # Optional custom lore before stats
    - "<line one>"
    - "<line two>"
  stats:                               # Optional stat bonuses
    STAT_NAME: <number>
  abilities:                           # Optional map of abilities (any number)
    <ability-id>:
      name: "<ability display name>"
      trigger: <TRIGGER>               # RIGHT_CLICK or PASSIVE
      target-range: <number>           # Blocks. Required for RIGHT_CLICK.
      cooldown: <seconds>              # Default: 0
      mana-cost: <number>              # Default: 0
      description:                     # Lore lines for this ability
        - "<line>"
      mechanics:                       # Ordered list of effects
        - type: <MECHANIC_TYPE>
          params:
            <key>: <value>
```

### Item Field Reference

| Field | Required | Notes |
|---|---|---|
| `name` | Recommended | MiniMessage string. Rarity color is automatically prepended. |
| `material` | **Yes** | Any Bukkit `Material` name (`DIAMOND_SWORD`, `IRON_INGOT`, etc.). |
| `rarity` | No | `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`, `MYTHIC`. Default: `COMMON`. |
| `item-type` | No | `SWORD`, `BOW`, `ARMOR`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`, `NONE`. |
| `lore` | No | Custom lore lines shown before the stat block. Supports MiniMessage. |
| `stats` | No | Stat keys from §22. Positive or negative numbers. |
| `abilities` | No | Map of ability definitions (see below). |

### Ability Field Reference

| Field | Required | Notes |
|---|---|---|
| `name` | Yes | Human-readable name shown in item lore. |
| `trigger` | Yes | `RIGHT_CLICK` — fires on right-click with a target. `PASSIVE` — fires on every equipment change. |
| `target-range` | For RIGHT_CLICK | Max distance in blocks to find a target. |
| `cooldown` | No | Seconds before the ability can be used again. `0` = no cooldown. |
| `mana-cost` | No | Mana consumed per use. `0` = free. |
| `description` | No | Lore lines describing the ability (MiniMessage). |
| `mechanics` | No | List of effects. Executed in order top to bottom. |

### Built-in Mechanics

#### `DAMAGE`
Deals damage to a target using the Valmora damage pipeline.

```yaml
- type: DAMAGE
  params:
    damage: 80.0           # Raw amount before stat scaling
    damage-type: "MAGIC"   # See Damage Type Reference (§24). Default: MAGIC
    target: "@target"      # Always @target for DAMAGE
```

#### `HEAL`
Restores HP to the player or target.

```yaml
- type: HEAL
  params:
    heal: 30.0             # HP to restore
    target: "@player"      # @player = self, @target = enemy
```

#### `APPLY_EFFECT`
Applies a vanilla potion effect.

```yaml
- type: APPLY_EFFECT
  params:
    effect: "slowness"     # Lowercase vanilla potion effect key
    duration: 3.0          # Seconds. Use -1 for infinite (PASSIVE only).
    amplifier: 2           # 1-based: 1=Level I, 2=Level II, etc.
    hide-particles: false  # Default: false
    target: "@target"      # @player or @target
```

### Complete Examples

```yaml
# --- Simple sword with stats only ---
iron_shortsword:
  name: "Iron Shortsword"
  material: IRON_SWORD
  rarity: COMMON
  item-type: SWORD
  stats:
    DAMAGE: 15
    STRENGTH: 5

# --- Staff with magic damage + slow ---
frost_staff:
  name: "Staff of Glacial Flux"
  material: BLAZE_ROD
  rarity: EPIC
  item-type: NONE
  stats:
    MANA: 250
    MANA_REGEN: 15
  abilities:
    frost_bolt:
      name: "Frost Bolt"
      trigger: RIGHT_CLICK
      target-range: 15.0
      cooldown: 2.5
      mana-cost: 45.0
      description:
        - "<gray>Shoots a freezing bolt of ice,"
        - "<gray>dealing <aqua>80 Magic Damage <gray>and"
        - "<gray>slowing the target for 3 seconds."
      mechanics:
        - type: DAMAGE
          params:
            damage: 80.0
            damage-type: MAGIC
            target: "@target"
        - type: APPLY_EFFECT
          params:
            effect: slowness
            duration: 3.0
            amplifier: 2
            target: "@target"

# --- Sword with life-drain ability ---
sanguine_carver:
  name: "Sanguine Carver"
  material: IRON_SWORD
  rarity: RARE
  item-type: SWORD
  stats:
    DAMAGE: 45
    STRENGTH: 20
    CRIT_CHANCE: 15
  abilities:
    life_tap:
      name: "Life Tap"
      trigger: RIGHT_CLICK
      target-range: 4.0
      cooldown: 8.0
      mana-cost: 30.0
      description:
        - "<gray>Drain the life force of your victim."
        - "<gray>Deals <red>60 True Damage <gray>and heals you for <green>30 Health<gray>."
      mechanics:
        - type: DAMAGE
          params:
            damage: 60.0
            damage-type: TRUE
            target: "@target"
        - type: HEAL
          params:
            heal: 30.0
            target: "@player"

# --- Legendary armor with passive resistance ---
fallen_aegis:
  name: "Aegis of the Fallen"
  material: NETHERITE_CHESTPLATE
  rarity: LEGENDARY
  item-type: CHESTPLATE
  stats:
    HEALTH: 500
    DEFENSE: 150
    SPEED: -10
  abilities:
    undying_will:
      name: "Undying Will"
      trigger: PASSIVE
      description:
        - "<gray>While worn, you gain <blue>Resistance I<gray>."
      mechanics:
        - type: APPLY_EFFECT
          params:
            effect: resistance
            duration: -1
            amplifier: 1
            hide-particles: true
            target: "@player"

# --- Boots with passive + active ability ---
mercury_treads:
  name: "Mercury's Treads"
  material: GOLDEN_BOOTS
  rarity: EPIC
  item-type: BOOTS
  stats:
    SPEED: 50
    HEALTH_REGEN: 5
  abilities:
    light_feet:
      name: "Light Feet"
      trigger: PASSIVE
      mechanics:
        - type: APPLY_EFFECT
          params:
            effect: speed
            duration: -1
            amplifier: 1
            target: "@player"
    tailwind:
      name: "Tailwind"
      trigger: RIGHT_CLICK
      cooldown: 20.0
      mana-cost: 100.0
      description:
        - "<gray>A burst of wind grants <white>Speed III <gray>for 5 seconds."
      mechanics:
        - type: APPLY_EFFECT
          params:
            effect: speed
            duration: 5.0
            amplifier: 3
            target: "@player"
```

---

## 9. Enchantments — `enchants/*.yml`

Place enchantment definition files in `plugins/Valmora/enchants/`. Each top-level key is an enchantment ID.

### Schema

```yaml
<enchant-id>:
  name: "<display name>"          # Shown in lore and GUI
  logic: "<namespace:logic_key>"  # Internal logic handler key (e.g., valmora:sharpness)
  description:                    # Lore lines explaining the effect (MiniMessage)
    - "<line>"
  targets:                        # Item types this enchantment can apply to
    - SWORD
    - PICKAXE
    # Full list: SWORD, BOW, HELMET, CHESTPLATE, LEGGINGS, BOOTS, PICKAXE, AXE, SHOVEL, HOE, FISHING_ROD
  conflicts:                      # IDs of enchantments that cannot coexist with this one
    - "other_enchant_id"
  etable-max-level: <int>         # Highest level obtainable from an enchanting table
  absolute-max-level: <int>       # Highest level possible through any means (e.g., books, commands)
```

### Field Reference

| Field | Required | Notes |
|---|---|---|
| `name` | Yes | Display name used in lore and the enchanting GUI. |
| `logic` | Yes | Maps to a registered `EnchantLogic` handler (built-in ones use `valmora:` prefix). |
| `description` | Yes | Lore lines shown on the item. Use `<yellow>` for highlighted values. |
| `targets` | Yes | List of item type strings the enchantment is compatible with. |
| `conflicts` | No | IDs of incompatible enchantments. Leave empty list `[]` for none. |
| `etable-max-level` | Yes | Max level from the enchanting table GUI. |
| `absolute-max-level` | Yes | Hard cap for any source. |

### Built-in Enchantments (Example File)

```yaml
sharpness:
  name: "Sharpness"
  logic: "valmora:sharpness"
  description:
    - "Increases melee damage by +5% per level."
  targets: [SWORD]
  conflicts: [smite, bane_of_arthropods]
  etable-max-level: 5
  absolute-max-level: 7

growth:
  name: "Growth"
  logic: "valmora:growth"
  description:
    - "Grants +10 max health per level."
  targets: [HELMET, CHESTPLATE, LEGGINGS, BOOTS]
  conflicts: []
  etable-max-level: 3
  absolute-max-level: 5

life_steal:
  name: "Life Steal"
  logic: "valmora:life_steal"
  description:
    - "Heals for <red>+0.5%<gray> of your max health each time you hit a mob."
  targets: [SWORD]
  conflicts: [syphon, mana_steal]
  etable-max-level: 3
  absolute-max-level: 5

protection:
  name: "Protection"
  logic: "valmora:protection"
  description:
    - "Grants <green>+4 Defense<gray> per level."
  targets: [HELMET, CHESTPLATE, LEGGINGS, BOOTS]
  conflicts: [blast_protection, fire_protection, projectile_protection]
  etable-max-level: 4
  absolute-max-level: 6

fortune:
  name: "Fortune"
  logic: "valmora:fortune"
  description:
    - "Increases your <gold>Mining Fortune<gray> by <gold>+10<gray> per level."
  targets: [PICKAXE, AXE, SHOVEL, HOE, FISHING_ROD]
  conflicts: []
  etable-max-level: 3
  absolute-max-level: 5
```

---

## 10. Mobs — `mobs/*.yml`

Place mob definition files in `plugins/Valmora/mobs/`. Each top-level key is a mob ID.

### Schema

```yaml
<mob-id>:
  name: "<MiniMessage display name>"   # Optional — shown as nameplate
  type: <ENTITY_TYPE>                  # REQUIRED — e.g. ZOMBIE, SKELETON, SPIDER
  health: <number>                     # Optional. Default: 20
  damage: <number>                     # Optional. Damage dealt on hit.
  speed: <number>                      # Optional. Vanilla speed attribute (0.25 = normal walk)
  level: <integer>                     # Optional. Shown in nameplate. Default: 1
  equipment:
    helmet: <MATERIAL or item-id>
    chestplate: <MATERIAL or item-id>
    leggings: <MATERIAL or item-id>
    boots: <MATERIAL or item-id>
    main-hand: <MATERIAL or item-id>
    off-hand: <MATERIAL or item-id>
```

The nameplate is formatted automatically as:
```
[Lv.<level>] <name> <currentHP>/<maxHP>❤
```

Equipment values accept either a vanilla material (e.g., `IRON_SWORD`) or a Valmora item ID (e.g., `frost_staff`).

### Examples

```yaml
forest_goblin:
  name: "<green>Forest Goblin"
  type: ZOMBIE
  health: 80.0
  damage: 12.0
  speed: 0.28
  level: 5
  equipment:
    helmet: LEATHER_HELMET
    main-hand: WOODEN_SWORD

cave_archer:
  name: "<gray>Cave Archer"
  type: SKELETON
  health: 50.0
  damage: 8.0
  level: 3
  equipment:
    main-hand: BOW

bone_knight:
  name: "<white>Bone Knight"
  type: SKELETON
  health: 200.0
  damage: 30.0
  speed: 0.22
  level: 15
  equipment:
    helmet: IRON_HELMET
    chestplate: IRON_CHESTPLATE
    leggings: IRON_LEGGINGS
    boots: IRON_BOOTS
    main-hand: IRON_SWORD
```

---

## 11. Alchemy — `alchemy/*.yml`

Alchemy potions are custom brews with stat-based effects. Place files in `plugins/Valmora/alchemy/`. Each top-level key is a potion ID.

### Schema

```yaml
<potion-id>:
  name: "<MiniMessage name>"       # Display name
  type: BUFF | DEBUFF              # Whether this potion helps or hurts
  rarity: <RARITY>                 # Item rarity
  color: "<#RRGGBB>"               # Bottle color (hex)
  lore:                            # Lore lines
    - "<line>"
  ingredient: <MATERIAL>           # Base crafting ingredient
  max-level: <int>                 # How many tiers this potion has (1–N)
  duration:                        # Duration per level in seconds (list)
    - 60                           # Level 1
    - 90                           # Level 2
    - 120                          # Level 3
  stats:                           # Stats granted/removed per level
    STAT_NAME:
      - <value_level_1>
      - <value_level_2>
      - <value_level_3>
```

### Examples

```yaml
healing_boost:
  name: "<red>Potion of Healing"
  type: BUFF
  rarity: UNCOMMON
  color: "#FF6666"
  lore:
    - "<gray>Enhances regeneration and vitality."
  ingredient: GOLDEN_APPLE
  max-level: 3
  duration: [60, 90, 120]
  stats:
    HEALTH_REGEN: [5.0, 10.0, 15.0]
    HEALTH: [20.0, 40.0, 60.0]

swiftness:
  name: "<yellow>Potion of Swiftness"
  type: BUFF
  rarity: COMMON
  color: "#FFFF44"
  lore:
    - "<gray>Increases movement speed."
  ingredient: SUGAR
  max-level: 2
  duration: [45, 75]
  stats:
    SPEED: [10.0, 20.0]

poison_brew:
  name: "<dark_green>Potion of Poison"
  type: DEBUFF
  rarity: UNCOMMON
  color: "#44AA44"
  lore:
    - "<gray>Weakens the target over time."
  ingredient: SPIDER_EYE
  max-level: 2
  duration: [30, 45]
  stats:
    HEALTH_REGEN: [-5.0, -10.0]
```

Splash potions affect players within `alchemy.splash-radius` blocks of the impact. DEBUFF potions can be thrown at enemies.

---

## 12. Zones — `zones/*.yml`

Zones are named world regions. They define boundaries and can contain resource nodes, mob spawners, and fishing loot tables. Place files in `plugins/Valmora/zones/`.

### Schema

```yaml
<zone-id>:
  display-name: "<MiniMessage name>"   # Shown when entering/in zone
  world: <world-name>                  # Bukkit world name
  min: [x, y, z]                       # Minimum corner (inclusive)
  max: [x, y, z]                       # Maximum corner (inclusive)

  # Optional: resource blocks that regenerate after being mined
  resource-blocks:
    <MATERIAL>:
      regen-delay: <seconds>           # Time before the block regenerates
      stages:                          # Ordered list of break stages
        - drops:                       # Items dropped at this stage
            - item: <MATERIAL or item-id>
              min: <int>
              max: <int>
              chance: <0.0–1.0>
          next: <MATERIAL>             # Block placed after this stage is mined

  # Optional: custom fishing loot table (links to a fishing/*.yml ID)
  fishing-loot-table: <loot-table-id>

  # Optional: automatic mob spawners within the zone
  mob-spawners:
    <spawner-id>:
      mob: <mob-id>                    # Valmora mob ID
      x: <int>
      y: <int>
      z: <int>
      spawn-interval: <ticks>          # Ticks between spawn attempts
      max-alive: <int>                 # Maximum simultaneously alive mobs from this spawner
      radius: <double>                 # Radius in which to count alive mobs
```

### Examples

```yaml
coal_mine:
  display-name: "<gray>Coal Mine"
  world: world
  min: [-80, 50, 60]
  max: [-30, 100, 120]
  resource-blocks:
    COAL_ORE:
      regen-delay: 300
      stages:
        - drops:
            - item: COAL
              min: 1
              max: 3
              chance: 1.0
          next: COBBLESTONE
        - drops:
            - item: COBBLESTONE
              min: 1
              max: 2
              chance: 0.5
          next: BEDROCK

graveyard:
  display-name: "<dark_gray>Graveyard"
  world: world
  min: [-150, 50, -150]
  max: [-80, 100, -60]
  mob-spawners:
    zombie_1:
      mob: forest_goblin
      x: -115
      y: 64
      z: -105
      spawn-interval: 300
      max-alive: 3
      radius: 25.0

fishing_village:
  display-name: "<aqua>Fishing Village"
  world: world
  min: [100, 50, 100]
  max: [200, 100, 200]
  fishing-loot-table: hub_fishing
```

---

## 13. Fishing Loot Tables — `fishing/*.yml`

Fishing loot tables define what players catch when fishing inside a zone that has `fishing-loot-table` set. Place files in `plugins/Valmora/fishing/`.

### Schema

```yaml
<table-id>:
  sea-creature-chance: <0.0–1.0>   # Probability of a sea creature spawning on cast
  sea-creature-mob: <mob-id>        # Which Valmora mob spawns (must exist in mobs/)
  entries:
    - item: <MATERIAL or item-id>   # What to give the player
      weight: <int>                 # Relative weight for random selection
      min: <int>                    # Minimum amount
      max: <int>                    # Maximum amount
```

Higher `weight` = more common. Total weight does not need to equal 100.

### Example

```yaml
hub_fishing:
  sea-creature-chance: 0.05
  sea-creature-mob: squid
  entries:
    - item: COD
      weight: 40
      min: 1
      max: 2
    - item: SALMON
      weight: 25
      min: 1
      max: 1
    - item: TROPICAL_FISH
      weight: 15
      min: 1
      max: 1
    - item: NAUTILUS_SHELL
      weight: 5
      min: 1
      max: 1
    - item: HEART_OF_THE_SEA
      weight: 1
      min: 1
      max: 1
```

---

## 14. NPCs — `npcs/*.yml`

NPCs are custom named entities that stand still in the world and react to player interaction (right-click). Place files in `plugins/Valmora/npcs/`.

### Schema

```yaml
<npc-id>:
  display-name: "<MiniMessage name>"   # Shown above the NPC
  type: <NPC_TYPE>                     # Role type: SHOP, BANK, QUEST, SLAYER, etc.
  entity-type: <ENTITY_TYPE>           # Bukkit entity type (e.g., VILLAGER, PILLAGER)
  world: <world-name>
  x: <double>
  y: <double>
  z: <double>
  yaw: <float>                         # Horizontal rotation (0–360)
  gui: <gui-id>                        # Optional — opens this GUI on interaction
  dialogue: <dialogue-id>              # Optional — starts this dialogue on interaction
```

An NPC can have either a `gui` or a `dialogue` (or neither). If both are set, the `gui` takes priority.

### Examples

```yaml
banker:
  display-name: "<gold>Banker"
  type: BANK
  entity-type: VILLAGER
  world: world
  x: 5.5
  y: 65.0
  z: -5.5
  yaw: 180
  gui: bank

quest_giver:
  display-name: "<yellow>Village Elder"
  type: QUEST
  entity-type: VILLAGER
  world: world
  x: -8.5
  y: 65.0
  z: 8.5
  yaw: 90
  dialogue: quest_giver_dialogue
```

---

## 15. Dialogues — `dialogues/*.yml`

Dialogues are branching conversation trees that NPCs can start. Place files in `plugins/Valmora/dialogues/`.

### Schema

```yaml
<dialogue-id>:
  start: <node-id>       # ID of the first node to show
  nodes:
    <node-id>:
      text: "<MiniMessage text>"   # What the NPC says
      actions:                     # Script events to run when this node is shown
        - "<event string>"
      choices:                     # Player response buttons
        - text: "<button label>"
          next-node: <node-id>     # Which node to go to (null = close dialogue)
          actions:                 # Script events when this choice is selected
            - "<event string>"
```

### Example

```yaml
quest_giver_dialogue:
  start: greeting
  nodes:
    greeting:
      text: "<yellow>Ah, a newcomer! Our village needs your help. Will you take on a task?"
      actions: []
      choices:
        - text: "I'll help!"
          next-node: accept
          actions:
            - "quest_start hub_intro"
        - text: "Not right now."
          next-node: null
    accept:
      text: "<yellow>Wonderful! Gather coal from the mine and bring back proof of your work."
      actions: []
      choices:
        - text: "I'm on it!"
          next-node: null
```

---

## 16. Warps — `warps/*.yml`

Warps are named teleport destinations. Players walk onto a **warp pad** (a set of block locations) to teleport to the destination. Place files in `plugins/Valmora/warps/`.

### Schema

```yaml
<warp-id>:
  display-name: "<MiniMessage name>"   # Shown when activating the warp
  world: <world-name>
  x: <double>
  y: <double>
  z: <double>
  yaw: <float>
  pitch: <float>
  unlock-condition: always            # 'always' or a condition expression
  pad-locations:                       # Block positions that trigger the warp
    - {x: <int>, y: <int>, z: <int>}
    - {x: <int>, y: <int>, z: <int>}
```

Multiple `pad-locations` define the warp pad area. Any player standing on one of these blocks and pressing the interact key will be teleported.

### Example

```yaml
hub_spawn:
  display-name: "<gold>Hub Spawn"
  world: world
  x: 0.5
  y: 65.0
  z: 0.5
  yaw: 0
  pitch: 0
  unlock-condition: always
  pad-locations:
    - {x: 10, y: 64, z: 10}
    - {x: 11, y: 64, z: 10}
    - {x: 10, y: 64, z: 11}
    - {x: 11, y: 64, z: 11}

coal_mine_warp:
  display-name: "<gray>Coal Mine"
  world: world
  x: -55.5
  y: 65.0
  z: 90.5
  yaw: 0
  pitch: 0
  unlock-condition: always
  pad-locations:
    - {x: -20, y: 64, z: 20}
```

---

## 17. Quests — `quests/*.yml`

Quests have objectives, rewards, and on-start hooks. Place files in `plugins/Valmora/quests/`.

### Schema

```yaml
<quest-id>:
  name: "<MiniMessage name>"
  objectives:
    - type: <OBJECTIVE_TYPE>    # COLLECT, KILL, REACH_ZONE, INTERACT_NPC
      target: <value>           # Material name, mob ID, or zone ID depending on type
      amount: <int>             # How many times to complete this objective
  rewards:                      # Script events executed on quest completion
    - "<event string>"
  on-start-actions:             # Script events executed when the quest starts
    - "<event string>"
```

### Objective Types

| Type | `target` | Description |
|---|---|---|
| `COLLECT` | Material name or item ID | Player must have N of this item in their inventory. |
| `KILL` | Mob ID | Player must kill N of this mob. |
| `REACH_ZONE` | Zone ID | Player must enter the named zone at least N times. |
| `INTERACT_NPC` | NPC ID | Player must right-click the named NPC N times. |

### Example

```yaml
hub_intro:
  name: "<yellow>A Miner's Errand"
  objectives:
    - type: COLLECT
      target: COAL
      amount: 10
    - type: REACH_ZONE
      target: coal_mine
      amount: 1
  rewards:
    - "give GOLD_NUGGET:5"
    - "sound player entity.player.levelup"
  on-start-actions:
    - "sound player entity.villager.yes"
```

To start a quest from a dialogue, use `quest_start <quest-id>` as an action string.

---

## 18. GUIs — `guis/*.yml`

GUIs are data-driven inventory screens loaded entirely from YAML. Place files in `plugins/Valmora/guis/`. The filename (without `.yml`) is the GUI's ID.

### Schema

```yaml
<gui-id>:
  title: "<MiniMessage title>"
  rows: <1–6>                    # Optional — inferred from layout if omitted
  machine: <id>                  # Optional — machine ID for recipe matching
  update-interval: <ticks>       # Optional — triggers on-update periodically

  layout:
    - "XXXXXXXXX"                # One row = 9 characters
    - "XXXXXXXXX"

  components:
    <char>:
      # Static display item:
      type: DISPLAY
      display-item:
        material: <MATERIAL>
        name: "<name>"
        lore:
          - "<line>"
        amount: <int>
        custom-model-data: <int>
      actions:
        left:                    # LEFT, RIGHT, MIDDLE, SHIFT_LEFT, SHIFT_RIGHT
          actions:
            - "<event string>"

      # OR — input slot:
      type: INPUT
      id: <slot-id>              # Optional — exposes to $gui.input.<id>.id$

      # OR — output slot:
      type: OUTPUT
      display-item:
        material: AIR

      # OR — paginated list:
      type: PAGINATED
      list: "<variable expression>"
      iterator: "<var-name>"
      states:
        <state-name>:
          condition: "<condition>"
          material: <MATERIAL>
          name: "<name>"
          lore:
            - "<line>"

      # OR — pagination buttons:
      type: PREVIOUS_PAGE
      type: NEXT_PAGE
      display-item: { material: ARROW, name: "<name>" }
      fallback: { material: GRAY_STAINED_GLASS_PANE, name: " " }

  # Lifecycle scripts:
  on-open:
    conditions: ["<condition>"]
    actions: ["<event>"]
    fail-actions: ["<event>"]

  on-slot-update:
    actions: ["<event>"]

  on-update:
    actions: ["<event>"]
```

### Layout System

Each character in the layout strings maps to a key in `components`. A space ` ` is an empty (unconfigured) slot. Rows must be exactly 9 characters. The number of rows determines the inventory size (1–6).

### Click Action Types

| Action | Description |
|---|---|
| `CLOSE` | Closes the inventory. |
| `BACK` | Returns to the previously opened GUI. |
| `open_gui <gui-id>` | Opens a different GUI. |
| `sound player <sound>` | Plays a sound to the player. |

### Reactive Variables in GUIs

| Variable | Description |
|---|---|
| `$gui.input.<id>.id$` | Valmora item ID (or Material name) in the INPUT slot with that `id`. |
| `$gui.input.<id>.amount$` | Item count in the slot. |
| `$gui.input.<id>.material$` | Bukkit material name in the slot. |
| `$prop.<key>$` | Per-session property (lost on close). Set via `variable set prop.key value`. |

### Example: Simple Menu

```yaml
main_menu:
  title: "<dark_gray>Main Menu"
  rows: 3
  layout:
    - "BBBBBBBBB"
    - "B B B B B"
    - "BBBBCBBBB"
  components:
    B:
      type: DISPLAY
      display-item:
        material: BLACK_STAINED_GLASS_PANE
        name: " "
    C:
      type: DISPLAY
      display-item:
        material: BARRIER
        name: "<red>Close"
      actions:
        left:
          actions: ["close"]
```

### Example: Crafting Machine (Forge)

```yaml
forge:
  title: "<dark_gray>Valmora Forge"
  machine: forge
  rows: 3
  layout:
    - "BBBBBBBBB"
    - "B I + O B"
    - "BBBBBBBBB"
  components:
    B:
      type: DISPLAY
      display-item:
        material: BLACK_STAINED_GLASS_PANE
        name: " "
    I:
      type: INPUT
      id: input1
    "+":
      type: DISPLAY
      display-item:
        material: LIME_STAINED_GLASS_PANE
        name: "<green>+"
    O:
      type: OUTPUT
      display-item:
        material: AIR
  on-slot-update:
    actions:
      - "gui_force_craft"
```

---

## 19. Recipes — `recipes/*.yml`

Recipes define what crafting machines produce from given inputs. Place files in `plugins/Valmora/recipes/` — sub-folders are supported.

### Schema

```yaml
<recipe-id>:
  machine: <machine-id>    # Must match the 'machine:' field in a GUI definition
  type: EXACT_SLOT | SHAPED | SHAPELESS
  inputs:
    # For EXACT_SLOT — keys match INPUT component IDs in the GUI:
    "input1": { item: <MATERIAL or item-id>, amount: <int> }
    "input2": { item: <MATERIAL or item-id>, amount: <int> }

    # For SHAPED — keys are "0"–"8" (3×3 grid, left-to-right, top-to-bottom):
    "0": { item: <MATERIAL>, amount: <int> }
    "1": { item: <MATERIAL>, amount: <int> }

    # For SHAPELESS — a list of items in any order:
    - item: <MATERIAL or item-id>
      amount: <int>

  outputs:
    result:
      item: <MATERIAL or item-id>
      amount: <int>

  on-craft:                  # Optional — script events fired when this recipe is crafted
    - "<event string>"
```

### Recipe Types

| Type | Description |
|---|---|
| `EXACT_SLOT` | Each input must be in the exact named slot. Best for 2-input forge/anvil machines. |
| `SHAPED` | Grid position matters. Keys `"0"`–`"8"` map to 3×3 slots. |
| `SHAPELESS` | Order doesn't matter; uses bag matching. |

### Examples

```yaml
# Forge (2-input machine):
forged_blade:
  machine: forge
  type: EXACT_SLOT
  inputs:
    "input1": { item: IRON_SWORD, amount: 1 }
    "input2": { item: reinforced_ingot, amount: 2 }
  outputs:
    result: { item: forged_blade, amount: 1 }

# Alchemy (shapeless 3-input):
strength_brew:
  machine: alchemy
  type: SHAPELESS
  inputs:
    - item: BLAZE_POWDER
      amount: 1
    - item: GLASS_BOTTLE
      amount: 1
  outputs:
    result:
      item: healing_boost
      amount: 1
  on-craft:
    - "sound player block.brewing_stand.brew"

# Crafting table (shaped):
basic_plank_board:
  machine: crafting
  type: SHAPED
  inputs:
    "0": { item: OAK_LOG, amount: 1 }
    "1": { item: OAK_LOG, amount: 1 }
  outputs:
    result: { item: OAK_PLANKS, amount: 8 }
```

---

## 20. Skills YAML — `skills/*.yml`

Skill definitions control XP sources, per-level rewards, and milestone rewards. Place files in `plugins/Valmora/skills/`.

### Schema

```yaml
id: "<skill-id>"               # Must match the Skill enum ID (mining, farming, etc.)
name: "<MiniMessage name>"
description:
  - "<line>"
material: <MATERIAL>           # Material shown as the skill icon in GUIs
max-level: <int>
xp-curve: "default"

sources:
  BLOCK_BREAK:                 # XP gained when a player breaks this block
    <MATERIAL>: <xp-amount>
    <MATERIAL>: <xp-amount>

rewards:
  per-level:                   # Script events run on every level-up
    - "<event string>"

  milestones:                  # Script events run at specific levels only
    "<level>":
      - "<event string>"
```

### Example: Mining Skill

```yaml
id: "mining"
name: "<gold><bold>⛏ Mining"
description:
  - "<gray>Extract precious metals and gemstones from the depths."
material: DIAMOND_PICKAXE
max-level: 60
xp-curve: "default"

sources:
  BLOCK_BREAK:
    STONE: 1.0
    DEEPSLATE: 1.2
    COAL_ORE: 10.0
    IRON_ORE: 25.0
    GOLD_ORE: 50.0
    DIAMOND_ORE: 150.0
    ANCIENT_DEBRIS: 500.0

rewards:
  per-level:
    - "variable add player.var.defense 0.5"
    - "variable add player.var.coins $param.level$*15"

  milestones:
    "10":
      - "give IRON_PICKAXE:1 notify"
    "30":
      - "give DIAMOND_PICKAXE:1 notify"
```

### XP Thresholds (Default Curve)

| Level | Total XP Needed |
|---|---|
| 1 | 10 |
| 2 | 50 |
| 3 | 100 |
| 4 | 250 |
| 5 | 500 |
| 6 | 1,000 |
| 7–10 | +500–8,500 per level |
| 11–28 | Growing to 100,000 |
| 60 | Max level |

---

## 21. Script Variables & Events DSL

The script DSL is used in `on-open`, `on-slot-update`, `on-update`, skill rewards, quest actions, and dialogue choice actions.

### Variable Syntax

Variables are embedded in strings with `$namespace.path$`:

| Variable | Returns | Example |
|---|---|---|
| `$player.name$` | String | `"Steve"` |
| `$player.stat.HEALTH$` | Double | `250.0` |
| `$player.stat.MANA$` | Double | `100.0` |
| `$player.stat.<ANY>$` | Double | Any stat from §22 |
| `$player.var.<name>$` | Object | Custom profile variable |
| `$world.name$` | String | `"world"` |
| `$world.dimension$` | String | `"NORMAL"` |
| `$system.time$` | Long | Unix timestamp in ms |
| `$prop.<key>$` | Object | Per-GUI session variable |
| `$param.<key>$` | Object | Mechanic/skill parameter |
| `$gui.input.<id>.id$` | String | Item ID in INPUT slot |
| `$gui.input.<id>.amount$` | Int | Item count in INPUT slot |
| `$range.<min>.<max>$` | Int | Random integer in range |
| `$time.season$` | String | Current RPG season |
| `$time.hour$` | Int | Current hour 0–23 |

Expressions support arithmetic: `$param.level$*10`, `$player.stat.HEALTH$ + 50`.

### Condition Syntax

Conditions appear at the start of action lists to gate further execution:

```yaml
- "condition $player.stat.HEALTH$ > 50"
- "condition $player.var.coins$ >= 100"
- "condition tag quest_started"
```

**Operators:** `==`, `!=`, `>`, `<`, `>=`, `<=`

If the condition fails, execution jumps to `fail-actions` (if defined) and skips remaining `actions`.

### Event Syntax

```
<eventName> <args...> [notify] [delay:<ticks>]
```

**`give`** — Give items to the player.
```
give DIAMOND:5
give GOLD_NUGGET:10 notify
give EMERALD:1 delay:40 notify
```

**`tag`** — Add or remove a profile tag (persistent flag).
```
tag add quest_complete
tag remove tutorial_lock
```

**`variable`** — Modify a profile variable or GUI property.
```
variable set player.var.coins 100
variable add player.var.coins 50
variable set prop.brew_progress 0
variable add prop.brew_progress 1
variable remove player.var.old_key
```

**`sound`** — Play a sound to the player.
```
sound player block.brewing_stand.brew
sound player entity.player.levelup
```

**`gui_force_craft`** — Trigger recipe matching and consumption for the current GUI's machine.
```
gui_force_craft
```

**`quest_start`** — Start a quest for the player.
```
quest_start hub_intro
```

**`enchant_apply`** — Apply an enchantment to an item in a given inventory slot.
```
enchant_apply 10 sharpness 5
```

**`open_gui`** — Open a different GUI.
```
open_gui skills_list
```

---

## 22. Stat Reference

These stat IDs are used under `stats:` in item YAML files and in script variables as `$player.stat.<ID>$`.

| ID | Display Name | Default | Effect |
|---|---|---|---|
| `HEALTH` | Health | 100 | Max health pool. Visual hearts scale to this. |
| `MANA` | Mana | 100 | Resource consumed by active abilities. |
| `DAMAGE` | Damage | 5 | Base attack power used in the damage formula. |
| `STRENGTH` | Strength | 0 | Increases all outgoing damage: `×(1 + strength/100)`. |
| `DEFENSE` | Defense | 0 | Reduces incoming damage: multiplier = `100/(defense+100)`. |
| `CRIT_CHANCE` | Crit Chance | 30 | % chance for a critical hit. Capped at 100. |
| `CRIT_DAMAGE` | Crit Damage | 50 | Bonus on crits. 50 = 1.5× damage. |
| `SPEED` | Speed | 100 | Movement speed. 100 = normal vanilla speed. |
| `HEALTH_REGEN` | Health Regen | 1 | HP/second while out of combat. |
| `MANA_REGEN` | Mana Regen | 2 | Mana/second regardless of combat. |
| `LUCK` | Luck | 0 | Loot quality multiplier. |
| `MINING_FORTUNE` | Mining Fortune | 0 | Drop quantity multiplier for resource blocks. |
| `MINING_SPEED` | Mining Speed | 100 | Block break speed. Maps to vanilla `block_break_speed`. |

---

## 23. Rarity Reference

| Rarity | Color | Notes |
|---|---|---|
| `COMMON` | White | Default for any item without a rarity specified. |
| `UNCOMMON` | Green | Slightly enhanced items. |
| `RARE` | Blue | Items with stats or a basic ability. |
| `EPIC` | Dark Purple | Multi-ability or high-stat items. |
| `LEGENDARY` | Gold | Top-tier power. |
| `MYTHIC` | Light Purple | Reserved for the rarest possible items. |

The rarity name is automatically added as a **bold colored line** at the bottom of the item lore, and the rarity color is prepended to the display name.

---

## 24. Damage Type Reference

Damage types affect indicator color and whether defense is applied.

| Type | Color | Bypasses Defense? | When to Use |
|---|---|---|---|
| `MELEE` | White | No | Direct melee hits. |
| `PROJECTILE` | Gray | No | Arrow or mob projectile hits. |
| `MAGIC` | Aqua | No | Default for `DAMAGE` mechanics. |
| `TRUE` | White | **Yes** | Bypasses all defense; use for execute abilities. |
| `FALL` | Dark Gray | **Yes** | Fall damage. |
| `DROWNING` | Blue | **Yes** | Drowning. |
| `FIRE` | Orange | No | Fire/fire tick. |
| `LAVA` | Dark Red | No | Lava contact. |
| `POISON` | Green | No | Poison effect. |
| `WITHER` | Black | No | Wither effect. |
| `EXPLOSION` | Red | No | Block/entity explosion. |
| `VOID` | Black | **Yes** | Void/out-of-world. |

---

## 25. MiniMessage Formatting

All display text in Valmora uses **MiniMessage**. Never use `§` or `&` color codes.

### Common Tags

| Tag | Effect | Example |
|---|---|---|
| `<red>` | Red color | `<red>Damage` |
| `<green>` | Green color | `<green>Unlocked` |
| `<gold>` | Gold color | `<gold>Legendary` |
| `<aqua>` | Aqua color | `<aqua>80 Magic` |
| `<white>` | White | `<white>Normal` |
| `<gray>` | Gray | `<gray>Description text` |
| `<dark_gray>` | Dark gray | `<dark_gray>Locked` |
| `<dark_purple>` | Dark purple | `<dark_purple>Epic` |
| `<light_purple>` | Light purple | `<light_purple>Mythic` |
| `<bold>` | Bold | `<bold>CRITICAL` |
| `<italic>` | Italic (Valmora suppresses this by default) | — |
| `<#RRGGBB>` | Hex color | `<#FF5500>Fire` |
| `<gradient:red:blue>` | Gradient | `<gradient:red:blue>Title` |
| `<rainbow>` | Rainbow | `<rainbow>FUN` |

Close tags with `</tag>` or reset all with `<!>` / `<reset>`.

---

## 26. Tutorials

### Tutorial 1: Creating Your First Custom Item

**Goal:** Create a RARE sword called "Iron Shortsword" with bonus damage stats and a life-drain active ability.

1. Open (or create) `plugins/Valmora/items/my_items.yml`.
2. Add the following entry:

```yaml
iron_shortsword:
  name: "Iron Shortsword"
  material: IRON_SWORD
  rarity: RARE
  item-type: SWORD
  lore:
    - "<gray>A well-balanced blade, honed to perfection."
  stats:
    DAMAGE: 30
    STRENGTH: 15
    CRIT_CHANCE: 20
  abilities:
    drain_strike:
      name: "Drain Strike"
      trigger: RIGHT_CLICK
      target-range: 4.0
      cooldown: 6.0
      mana-cost: 25.0
      description:
        - "<gray>Siphons life from your enemy."
        - "<gray>Deals <red>40 True Damage <gray>and"
        - "<gray>heals you for <green>20 Health<gray>."
      mechanics:
        - type: DAMAGE
          params:
            damage: 40.0
            damage-type: TRUE
            target: "@target"
        - type: HEAL
          params:
            heal: 20.0
            target: "@player"
```

3. Save the file.
4. In-game run `/valmora reload`.
5. Obtain the item with `/item give iron_shortsword`.

---

### Tutorial 2: Creating a Custom Mob

**Goal:** Create a level-10 boss skeleton called "Bone Warden".

1. Open `plugins/Valmora/mobs/my_mobs.yml`.
2. Add:

```yaml
bone_warden:
  name: "<white><bold>Bone Warden"
  type: SKELETON
  health: 500.0
  damage: 35.0
  speed: 0.22
  level: 10
  equipment:
    helmet: IRON_HELMET
    chestplate: IRON_CHESTPLATE
    leggings: IRON_LEGGINGS
    boots: IRON_BOOTS
    main-hand: BOW
```

3. Save and run `/valmora reload`.
4. Spawn it with `/mob spawn bone_warden`.

---

### Tutorial 3: Creating a Custom Enchantment

**Goal:** Create a "Vampirism" enchantment for swords that heals on hit.

1. Open `plugins/Valmora/enchants/my_enchants.yml`.
2. Add:

```yaml
vampirism:
  name: "Vampirism"
  logic: "valmora:life_steal"
  description:
    - "Heals <red>+1%<gray> of your max health per level"
    - "each time you deal damage."
  targets: [SWORD]
  conflicts: [life_steal]
  etable-max-level: 3
  absolute-max-level: 5
```

3. Save and run `/valmora reload`. The enchantment will appear in the enchanting table GUI.

---

### Tutorial 4: Defining a Zone with Resource Nodes

**Goal:** Create a "Silver Mine" zone where players can mine Silver Ore (IRON_ORE) for custom drops.

1. Open `plugins/Valmora/zones/silver_mine.yml`.
2. Add:

```yaml
silver_mine:
  display-name: "<white>Silver Mine"
  world: world
  min: [200, 40, 200]
  max: [300, 90, 300]
  resource-blocks:
    IRON_ORE:
      regen-delay: 180
      stages:
        - drops:
            - item: IRON_INGOT
              min: 1
              max: 3
              chance: 1.0
            - item: IRON_NUGGET
              min: 2
              max: 5
              chance: 0.5
          next: COBBLESTONE
        - drops:
            - item: COBBLESTONE
              min: 1
              max: 1
              chance: 1.0
          next: BEDROCK
```

3. Save and run `/valmora reload`. Players mining Iron Ore inside the zone boundaries will get your custom drops instead of vanilla ones.

---

### Tutorial 5: Creating an NPC with a Quest Dialogue

**Goal:** Place an NPC that starts a quest when the player chooses "Yes".

1. Create the quest in `plugins/Valmora/quests/my_quest.yml`:

```yaml
deliver_wood:
  name: "<green>The Woodcutter's Request"
  objectives:
    - type: COLLECT
      target: OAK_LOG
      amount: 20
  rewards:
    - "give EMERALD:3 notify"
    - "variable add player.var.coins 500"
  on-start-actions:
    - "sound player entity.villager.yes"
```

2. Create the dialogue in `plugins/Valmora/dialogues/woodcutter.yml`:

```yaml
woodcutter_dialogue:
  start: greeting
  nodes:
    greeting:
      text: "<green>Greetings, traveler! I need 20 Oak Logs. Can you help?"
      actions: []
      choices:
        - text: "Sure, I'll gather them!"
          next-node: accepted
          actions:
            - "quest_start deliver_wood"
        - text: "Not right now."
          next-node: null
    accepted:
      text: "<green>Wonderful! Return when you have the logs."
      actions: []
      choices:
        - text: "I'll be back soon."
          next-node: null
```

3. Create the NPC in `plugins/Valmora/npcs/woodcutter.yml`:

```yaml
woodcutter:
  display-name: "<green>Woodcutter"
  type: QUEST
  entity-type: VILLAGER
  world: world
  x: 50.5
  y: 65.0
  z: 50.5
  yaw: 180
  dialogue: woodcutter_dialogue
```

4. Run `/valmora reload`. The NPC will spawn at the defined coordinates.

---

### Tutorial 6: Creating a Crafting GUI with Custom Recipes

**Goal:** Build a two-slot forge machine that combines items.

1. Create the GUI in `plugins/Valmora/guis/my_forge.yml`:

```yaml
my_forge:
  title: "<dark_gray><bold>My Forge"
  machine: my_forge
  rows: 3
  layout:
    - "BBBBBBBBB"
    - "B I + O B"
    - "BBBBBBBBB"
  components:
    B:
      type: DISPLAY
      display-item:
        material: BLACK_STAINED_GLASS_PANE
        name: " "
    I:
      type: INPUT
      id: input1
    "+":
      type: DISPLAY
      display-item:
        material: LIME_STAINED_GLASS_PANE
        name: "<green>+"
    O:
      type: OUTPUT
      display-item:
        material: AIR
  on-slot-update:
    actions:
      - "gui_force_craft"
```

2. Create a recipe in `plugins/Valmora/recipes/my_forge.yml`:

```yaml
golden_blade:
  machine: my_forge
  type: EXACT_SLOT
  inputs:
    "input1": { item: IRON_SWORD, amount: 1 }
    input2: { item: GOLD_INGOT, amount: 3 }
  outputs:
    result: { item: GOLDEN_SWORD, amount: 1 }
```

3. Run `/valmora reload`. Open the GUI with a command or NPC interaction.

---

### Tutorial 7: Setting Up a Fishing Zone

**Goal:** Create a fishing zone where players can catch custom loot.

1. Create the loot table in `plugins/Valmora/fishing/ocean.yml`:

```yaml
ocean:
  sea-creature-chance: 0.08
  sea-creature-mob: squid
  entries:
    - item: COD
      weight: 50
      min: 1
      max: 3
    - item: SALMON
      weight: 30
      min: 1
      max: 2
    - item: PRISMARINE_CRYSTALS
      weight: 10
      min: 1
      max: 2
    - item: TRIDENT
      weight: 1
      min: 1
      max: 1
```

2. Add a zone that references this table in `plugins/Valmora/zones/ocean.yml`:

```yaml
ocean_zone:
  display-name: "<aqua>Open Ocean"
  world: world
  min: [500, 50, 500]
  max: [800, 80, 800]
  fishing-loot-table: ocean
```

3. Run `/valmora reload`. Players fishing within the zone get loot from the table.

---

_End of Valmora User Documentation — v0.1_
