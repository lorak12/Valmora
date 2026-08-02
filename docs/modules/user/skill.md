# Skill Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `skills` | **Config folder:** `plugins/Valmora/skills/`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

Valmora has **nine levelled skills**: Mining, Farming, Foraging, Fishing, Combat, Alchemy, Carpentry, Enchanting, and Taming. Every skill is capped at **level 60**.

You earn **skill XP** by doing the thing the skill is about — mine ore for Mining XP, kill mobs for Combat XP, brew potions for Alchemy XP, and so on. Your total XP in a skill determines your level through a shared **XP curve** (the same for every skill). Leveling up fires **rewards** defined by the server: stat boosts, coins, items, tags, and more.

Skills are stored **per character profile** — switching profiles (see the Profile module) switches your skill progress too.

---

## Player Guide

### Opening the skill menu

Type **`/skills`** to open the skills overview GUI ("✦ Your Skills"). Each skill appears as a colored icon with its **name, description, and your current level** (`Level: X / 60`).

Click a skill to open its **detail view**, which shows:

- The skill's icon, description, and your **progress to the next level** (percentage + `xp_in_level / xp_required`).
- A colored level strip from 1 to the skill's max level:
  - 🟩 **Green** — levels you've already reached.
  - 🟨 **Yellow** — the level you're currently working on.
  - 🟥 **Red** — locked future levels.
- Use the arrow buttons (`<` / `>`) to page through the levels, and **Back to Skills** (barrier icon) to return to the list.

### Checking skills with commands

| Command | What it does |
|---|---|
| `/skill list` | Lists every skill id and display name. |
| `/skill get <player> <skill>` | Shows another online player's level, total XP, and progress toward the next level (e.g. `/skill get Steve mining`). |

> Command ids are lowercase: `combat`, `farming`, `fishing`, `mining`, `foraging`, `carpentry`, `alchemy`, `enchanting`, `taming`.

### How skills level up

- XP is granted automatically the moment you perform an action (see the XP source table below).
- Your level is derived from your **total** XP via a shared curve. The thresholds are cumulative, so reaching a new level always requires more total XP:

| Level | Total XP needed |
|---|---|
| 0 | 0 |
| 1 | 10 |
| 2 | 20 |
| 3 | 50 |
| 4 | 100 |
| 5 | 200 |
| 6 | 500 |
| 7 | 1,000 |
| 8 | 1,500 |
| 9 | 2,000 |
| 10 | 3,000 |
| 11 | 5,000 |
| 12 | 7,500 |
| 13 | 10,000 |
| 14 | 15,000 |
| 15 | 20,000 |
| 16 | 30,000 |
| 17 | 40,000 |
| 18 | 50,000 |
| 19 | 60,000 |
| 20 | 75,000 |
| 21 | 100,000 |
| 22 | 125,000 |
| 23 | 150,000 |
| 24 | 175,000 |
| 25 | 200,000 |
| 26 | 250,000 |
| 27 | 300,000 |
| 28 | 350,000 |
| 29 | 400,000 |
| 30 | 450,000 |
| 31 | 500,000 |
| 32 | 600,000 |
| 33 | 700,000 |
| 34 | 800,000 |
| 35 | 900,000 |
| 36 | 1,000,000 |
| 37 | 1,200,000 |
| 38 | 1,400,000 |
| 39 | 1,600,000 |
| 40 | 1,800,000 |
| 41 | 2,000,000 |
| 42 | 2,300,000 |
| 43 | 2,600,000 |
| 44 | 3,000,000 |
| 45 | 3,400,000 |
| 46 | 3,800,000 |
| 47 | 4,200,000 |
| 48 | 4,600,000 |
| 49 | 5,000,000 |
| 50 | 5,500,000 |
| 51 | 6,000,000 |
| 52 | 6,500,000 |
| 53 | 7,000,000 |
| 54 | 7,500,000 |
| 55 | 8,000,000 |
| 56 | 8,500,000 |
| 57 | 9,000,000 |
| 58 | 9,500,000 |
| 59 | 10,000,000 |
| 60 | 10,000,000+ (max) |

> Note: you start at **level 0** and hit level 1 at 10 XP.

- When you gain XP, an **action bar** message appears (`+<amount> <skill> XP`).
- When you level up, a **chat announcement** appears ("SKILL LEVEL UP! Your <skill> is now level X!") and the skill's **per-level reward** (and any **milestone reward**) fire.

### XP sources (default config)

| Skill | How to earn XP | Notable XP amounts (default) |
|---|---|---|
| ⛏ Mining | Break stone/ores: STONE 1, DEEPSLATE 1.2, coal 10, iron 25, gold 50, diamond 150, ancient debris 500 | Drops: pickaxe at 10 & 30, +defense per level |
| 🌾 Farming | Harvest crops (wheat/carrots/potatoes/beetroots 3, nether wart 5, melon/pumpkin/cocoa 4, sugar cane 2, bamboo 1, torchflower/pitcher 10); break mushroom blocks (3) and cactus (2) | Drops: hoes at 10/25/50, +farming fortune per level |
| 🪓 Foraging | Chop any log or wood block (15 XP each) | Drops: axes at 10 & 30, +strength per level |
| 🎣 Fishing | Catch fish — cod 50, salmon 70, pufferfish/tropical fish 150 | Tag `master_angler` at 15, book at 25, +mana per level |
| ⚔ Combat | Kill mobs — zombie 5, `test_zombie` 15, ender dragon 1000. Custom Valmora mobs grant their own XP reward (see Mob module) | +strength per level, sword at 10, `tier2_combat_unlocked` tag at 25 |
| ⚗ Alchemy | *(none configured by default — brewing grants no XP out of the box; see Admin Guide)* | +coins per level, blaze powder at 10, nether wart at 30 |
| 🪵 Carpentry | Craft wooden items — crafting table 2, planks 1, stick 0.5, chest/barrel 5, bookshelf 8, stairs 3, tools 4, bow 10, shield 15, jukebox 12, etc. | Logs at 10, chests at 25, shulker box at 50 |
| ✦ Enchanting | Enchant items — iron sword 25, diamond sword 60, netherite sword 100, iron chestplate 30, netherite chestplate 130, trident 80, book 5, etc. | Books/exp bottles at 10/25/50, +magic find per level |
| 🐾 Taming | Tame animals — wolf 100, cat 80, horse 200, donkey 150, mule 175, llama 150, parrot 120, fox 200, axolotl 120, camel 180 | Bone at 5, lead at 20, saddle at 40 |

### Default per-level rewards

Every shipped skill awards **coins** every level (`$param.level$ * <base>`: combat 10, mining 15, farming 10, foraging 12, fishing 25, alchemy 5, carpentry 12, enchanting 20, taming 15), plus one extra stat per level:

| Skill | Stat gained per level |
|---|---|
| Combat | +1 Strength |
| Mining | +0.5 Defense |
| Farming | +0.5 Farming Fortune |
| Foraging | +0.2 Strength |
| Fishing | +0.3 Mana |
| Carpentry | +0.2 Ability Damage |
| Enchanting | +0.3 Magic Find |
| Taming | +0.5 Pet Luck |
| Alchemy | — (coins only) |

---

## Admin Guide

### Where skills are defined

Each skill is one YAML file in **`plugins/Valmora/skills/`** (e.g. `mining.yml`, `combat.yml`). The **filename is the skill's id** — `mining.yml` defines the `mining` skill. Files are only written to disk on the first run; after editing, run **`/valmora reload`** to reload them (this also reloads every other module).

### Admin commands

| Command | Permission | What it does |
|---|---|---|
| `/skill give <player> <skill> <xp>` | `valmora.admin` | Grants XP (can cross multiple levels at once). |
| `/skill set <player> <skill> xp <value>` | `valmora.admin` | Sets the player's total XP directly. |
| `/skill set <player> <skill> level <value>` | `valmora.admin` | Sets the player's level by snapping XP to that level's threshold. |

`valmora.admin` defaults to OP. These commands only work on **online** players.

> The GUI admin test command `/gui open <player> <id>` (`valmora.admin.gui`) can be used to preview the `skills_list` / `skill_details` GUIs.

### Defining a skill — full example

```yaml
# plugins/Valmora/skills/mining.yml
name: "<gold><bold>⛏ Mining</bold></gold>"     # MiniMessage formatting is allowed
description:
  - "<gray>Extract precious metals and gemstones from the depths."
material: DIAMOND_PICKAXE                       # GUI icon (valid Material name)
max-level: 60                                   # level cap
xp-curve: "default"                             # curve id (only "default" is supported today)

sources:
  BLOCK_BREAK:                                  # source type (see table below)
    STONE: 1.0                                  # exact identifier → XP
    DEEPSLATE_COAL_ORE: 12.0
    "#minecraft:logs": 5.0                      # tag match (prefix #)
    "DEEP*": 2.0                                # glob pattern (uses *)
    DEFAULT: 0.5                                # fallback for anything unmatched

rewards:
  per-level:                                    # runs on EVERY level gained
    - "variable add player.var.defense 0.5"
    - "variable add player.var.coins $param.level$*15"
  milestones:                                   # runs when the exact level is crossed
    "10":
      - "give IRON_PICKAXE:1 notify"
    "30":
      - "give DIAMOND_PICKAXE:1 notify"
```

### Supported source types

Each is fired by a specific in-game event; identifiers are matched case-insensitively:

| Source type | Fired when | Identifier used | Notes |
|---|---|---|---|
| `BLOCK_BREAK` | Any block broken | Block material name (e.g. `IRON_ORE`) | Also fired for crop blocks — see `CROP_HARVEST` |
| `CROP_HARVEST` | Any block broken (same event) | Block material name | Use it for crops; a broken block can feed both types |
| `MOB_KILL` | A mob dies to a player | Mob `EntityType` name (e.g. `ZOMBIE`) | Tags can match `MobCategory` names too |
| `FISHING` | A fishing cast catches something | Caught item material (e.g. `COD`) | Identifier is always an item/block material |
| `CRAFT_ITEM` | Player crafts an item | Result item material | |
| `BREW_POTION` | A brewing stand finishes | Always `ANY` | Needs an `ANY` or `DEFAULT` entry to ever match |
| `TAME_MOB` | Player tames an animal | Mob `EntityType` name | |
| `ENCHANT_ITEM` | Player enchants an item | Item material | |

### Identifier forms

- **Exact name** — `ZOMBIE: 5.0`. Matches the exact (case-insensitive) identifier.
- **Tag** — `#minecraft:logs: 5.0` (the `minecraft:` prefix is optional). The identifier is checked against that Bukkit `Tag`.
- **Pattern** — `DEEP*: 2.0`. A glob where `*` matches any characters; `.` in the pattern is literal.
- **`DEFAULT`** — `DEFAULT: 0.5`. Fallback XP when nothing else matches. A source type with no match grants nothing.

Lookup order is **exact → pattern → tag → default**, so you can override broad rules with specific ones.

### Rewards

`rewards.per-level` runs on **every** level gained (including when a big XP drop crosses several levels at once). `rewards.milestones.<level>` runs only when that exact level is crossed. Both are **script command lists** (same DSL as quests/abilities): `variable add`, `give`, `tag add`, `notify`, etc.

Inside reward scripts, `$param.level$` holds the level that triggered the reward. **Caveat:** when a single XP grant crosses multiple levels, all reward executions (for every intermediate level) currently read the *final* level from `$param.level$` — so `$param.level$*15` uses the end level, not each individual level. Milestones themselves are keyed correctly per level.

> There is no XP cap on how fast levels accrue other than `max-level` — at max level, further XP is simply refused.

### Permissions

| Permission | Default | Gives |
|---|---|---|
| `valmora.admin` | OP | `/skill give`, `/skill set` (and `/valmora reload`) |
| `valmora.admin.gui` | — | `/gui open ...` previews |

`/skill list`, `/skill get`, and `/skills` need no permission.

---

## Configuration Reference

**Location:** `plugins/Valmora/skills/*.yml` (one file per skill; filename = id).

### Top-level keys

| Key | Type | Default | Explanation |
|---|---|---|---|
| `name` | string | skill id | Display name; MiniMessage tags supported. |
| `description` | list of strings | empty | Skill description; shown in the `/skills` GUI and detail view. |
| `material` | string | `BOOK` | GUI icon material (a valid Bukkit `Material`, e.g. `DIAMOND_PICKAXE`). |
| `max-level` | int | `60` | Level cap. XP past it is not awarded. |
| `xp-curve` | string | `"default"` | Curve id. **Currently ignored** — all skills use the shared built-in curve (see level table above). |
| `sources` | section | — | XP mappings (below). |
| `rewards.per-level` | list of strings | — | Script commands run on every level gained. |
| `rewards.milestones.<level>` | list of strings | — | Script commands run when `<level>` is crossed. |

### `sources` sub-keys

```
sources:
  <SOURCE_TYPE>:            # one of the 8 types in the Admin Guide table
    <IDENTIFIER>: <xp>      # exact material/mob name, "#tag", glob pattern, or DEFAULT
```

| Key | Type | Default | Explanation |
|---|---|---|---|
| `<IDENTIFIER>` | double | — | XP awarded for that identifier. Zero or missing = no XP. |

### Shipped defaults at a glance

| Skill (file) | XP actions | per-level stat | Coin per level |
|---|---|---|---|
| `combat.yml` | kill mobs | +1 Strength | ×10 |
| `mining.yml` | break stone/ores | +0.5 Defense | ×15 |
| `farming.yml` | harvest crops | +0.5 Farming Fortune | ×10 |
| `foraging.yml` | chop logs | +0.2 Strength | ×12 |
| `fishing.yml` | catch fish | +0.3 Mana | ×25 |
| `alchemy.yml` | *(none configured)* | — | ×5 |
| `carpentry.yml` | craft wooden items | +0.2 Ability Damage | ×12 |
| `enchanting.yml` | enchant items | +0.3 Magic Find | ×20 |
| `taming.yml` | tame animals | +0.5 Pet Luck | ×15 |

> **Gotchas when editing:**
> - Don't rename a file's id casually — other systems reference skills by id (`/skill get … mining`, warp conditions `skill:mining:10`, quest targets, `givexp` events). The YAML `id:` key is not read; the filename is the id.
> - `xp-curve` changes have no effect (only the built-in curve exists).
> - A `givexp` GUI/script event currently only accepts the enum skills `COMBAT, FARMING, FISHING, MINING, FORAGING, CRAFTING, ALCHEMY, ENCHANTING` — `taming` and `carpentry` are not accepted by it (use `/skill give` instead).
> - Brewing XP requires a `BREW_POTION` source with an `ANY` or `DEFAULT` entry; none ships by default.
