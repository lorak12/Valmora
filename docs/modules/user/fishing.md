# Fishing Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `fishing` | **Config folder:** `plugins/Valmora/fishing/`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The Fishing module replaces Minecraft's vanilla fishing loot with **zone-based loot tables**. What you catch depends on where you fish:

- Each fishing **zone** (see `docs/USER_DOCS.md` §12 / the Zone module) can point at a **loot table**.
- A loot table is a weighted list of items — common items appear often, rare items almost never.
- Tables can also roll a **sea creature**: a chance to spawn a Valmora custom mob instead of handing out loot.

A default table ships with the plugin as `fishing/hub_fishing.yml`. Fishing also levels up the **Fishing skill** (`/skill info fishing`) — XP is handled by the Skill module (see `docs/VALMORA_DOCUMENTATION.md` §27).

---

## Player Guide

### How fishing works

1. **Cast your rod** anywhere. Casting and waiting work like vanilla Minecraft.
2. **When the bobber splashes** (the "catch" moment), Valmora intercepts the catch:
   - The vanilla fish is removed and replaced by a result from the **loot table for the zone you're standing in**.
   - The item is placed **directly into your inventory**.
3. **What you get is random**, driven by `weight`:
   - Higher `weight` → more common.
   - Lower `weight` → rarer.
   - Stack amounts can also be random between `min` and `max`.
4. If the table has `sea-creature-chance` set, each catch has that probability of spawning a **custom mob** (a "sea creature") at your location **instead of** giving loot.

### What you can catch (default table `hub_fishing`)

| Item | Relative weight | Stack amount | Rarity |
|---|---|---|---|
| Cod | 40 | 1–2 | Common |
| Salmon | 25 | 1 | Common |
| Tropical Fish | 15 | 1 | Uncommon |
| Pufferfish | 10 | 1 | Uncommon |
| Nautilus Shell | 5 | 1 | Rare |
| Heart of the Sea | 1 | 1 | Very rare |

> Exact percentages: Cod ≈ 41.7%, Salmon ≈ 26.0%, Tropical Fish ≈ 15.6%, Pufferfish ≈ 10.4%, Nautilus Shell ≈ 5.2%, Heart of the Sea ≈ 1.0%.

With the default config there is also a 5% (`0.05`) chance per catch to spawn a sea creature. **Note:** the shipped default references a `squid` sea creature, but no such mob is defined in the shipped mobs — so with a stock install the sea-creature roll never actually spawns anything. Servers should define the mob or remove the option (see [Configuration Reference](#configuration-reference)).

### Fishing XP

Catching fish grants **Fishing skill XP** based on what you reel in (defined in `skills/fishing.yml`):

| Vanilla catch | XP |
|---|---|
| Cod | 50 |
| Salmon | 70 |
| Pufferfish | 150 |
| Tropical Fish | 150 |

XP is awarded from the **vanilla** catch type — not from custom loot-table items. Leveling Fishing grants per-level rewards and milestones configured in `skills/fishing.yml` (e.g. +0.3 mana and +25 coins per level, a `master_angler` tag at level 15, an enchanted book at level 25).

### Commands

There are **no fishing-specific commands**. Fishing XP/levels are viewed through the standard skill commands:

- `/skill info fishing` — view your Fishing level and progress.
- `/skill add fishing <amount>` (admin) — grant XP (see `docs/VALMORA_DOCUMENTATION.md` §28).

---

## Admin Guide

### Setup

1. **Install Valmora** on a Paper 1.21.11 server (Java 21).
2. On first start the plugin auto-creates `plugins/Valmora/fishing/` and copies the default table there (see `Valmora.java` resource-shipping, `fishing/` prefix).
3. **Create or assign a fishing zone.** Fishing loot only applies inside zones; outside any zone the plugin looks for a table literally named `default`.
   - A zone opts into a table via the `fishing-loot-table` key in `plugins/Valmora/zones/<zone>.yml`:

     ```yaml
     fishing_village:
       display-name: "<aqua>Fishing Village"
       world: world
       min: [100, 50, 100]
       max: [200, 100, 200]
       fishing-loot-table: hub_fishing
     ```

   - Full zone schema: `docs/USER_DOCS.md` §12.
4. **Reload** with `/valmora reload` (requires `valmora.admin`). Loot tables are re-read from `plugins/Valmora/fishing/` on every reload; your edits are never overwritten by the plugin.

### Permissions

There are **no fishing-specific permissions**. The only permission involved is the standard reload permission:

| Permission | Effect |
|---|---|
| `valmora.admin` | Allows `/valmora reload`, which re-reads all fishing tables and zones. |

Fishing itself is available to everyone with a fishing rod.

### Configuring loot tables

Create (or edit) a YAML file under `plugins/Valmora/fishing/`. **Each top-level key is one loot table** — you can put many tables in one file:

```yaml
ocean:
  sea-creature-chance: 0.08
  sea-creature-mob: sea_guardian
  entries:
    - item: COD
      weight: 40
      min: 1
      max: 2
    - item: SALMON
      weight: 25
      min: 1
      max: 1
    - item: HEART_OF_THE_SEA
      weight: 1
      min: 1
      max: 1

swamp:
  entries:
    - item: ROTTEN_FLESH
      weight: 60
      min: 1
      max: 3
    - item: SLIME_BALL
      weight: 20
      min: 1
      max: 2
    - item: LILY_PAD
      weight: 10
      min: 1
      max: 1
```

**Key rules:**

- **Weights are relative.** Total doesn't need to equal 100. An entry with `weight: 40` is twice as common as `weight: 20`.
- **`item` accepts two kinds of identifiers:**
  1. A **Valmora custom item ID** (defined in `plugins/Valmora/items/*.yml`), e.g. `my_epic_fish`.
  2. A **vanilla Material name**, e.g. `COD`, `SALMON`, `NAUTILUS_SHELL`, `HEART_OF_THE_SEA`.
  - Custom items are tried first; if the ID doesn't exist, the name is matched as a Material. If neither matches, **the catch is consumed with no reward**.
- **`min`/`max`** are inclusive. If `min` equals `max`, the amount is fixed. If `min` is greater than `max`, the amount is `min`.
- **`sea-creature-chance`** is 0.0–1.0. At each catch, that probability spawns the mob from `sea-creature-mob` (a Valmora mob definition from `mobs/*.yml`) at the **player's location** and skips the loot roll. If the mob ID doesn't exist, the catch is consumed with nothing spawning.
- **The `default` table.** Any player fishing outside a zone, or inside a zone without `fishing-loot-table`, falls back to a table whose ID is exactly `default`. Create one if you want fishing to work everywhere:

  ```yaml
  default:
    entries:
      - item: COD
        weight: 50
        min: 1
        max: 2
      - item: SALMON
        weight: 30
        min: 1
        max: 1
  ```

### Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Fishing gives nothing, no items at all | You're outside any zone and no `default` table exists; or the rolled item ID resolves to neither a custom item nor a Material. |
| Sea creatures never spawn | The `sea-creature-mob` ID isn't defined in `mobs/*.yml`, or `sea-creature-chance` is 0/missing. |
| Loot changes don't apply | Run `/valmora reload`. Tables are only parsed at module enable/reload. |
| Bad YAML in a table | The table is skipped with a warning in console ("Failed to load some Fishing Tables"); other tables still load. |
| No Fishing XP | XP comes from the Skill module's vanilla-catch mapping in `skills/fishing.yml` — custom items and "TREASURE" entries never award XP. |

---

## Configuration Reference

All keys below. Defaults are applied when a key is omitted.

### `plugins/Valmora/fishing/*.yml` — loot tables

**One table per top-level key.** Multiple tables may live in one file.

| Key | Type | Default | Explanation |
|---|---|---|---|
| `<table-id>` | section | — | Table identifier. Referenced from zones via `fishing-loot-table`. IDs are case-insensitive. |
| `sea-creature-chance` | double | `0.0` | Chance (0.0–1.0) per catch to spawn a sea creature instead of loot. |
| `sea-creature-mob` | string | *(none)* | Valmora mob definition ID to spawn as the sea creature. Must exist in `mobs/*.yml`. |
| `entries` | list | `[]` | The weighted loot entries. |

**Each `entries[]` entry:**

| Key | Type | Default | Explanation |
|---|---|---|---|
| `item` | string | `COD` | Custom item ID (checked first) or vanilla Material name. |
| `weight` | int | `10` | Relative rarity — higher is more common. |
| `min` | int | `1` | Minimum stack amount. |
| `max` | int | `1` | Maximum stack amount (inclusive). Fixed amount when `min >= max`. |

### Related: `plugins/Valmora/zones/*.yml`

| Key | Type | Default | Explanation |
|---|---|---|---|
| `fishing-loot-table` | string | *(none)* | Which fishing table applies inside this zone. Absent → the `default` table (if it exists) is used. |

### Related: `plugins/Valmora/skills/fishing.yml` (Skill module)

| Key | Type | Default | Explanation |
|---|---|---|---|
| `sources.FISHING.<MATERIAL>` | double | — | XP per vanilla fish caught (e.g. `COD: 50.0`). Only vanilla catch materials match — `TREASURE` and custom items never match. |

### Shipped default — `fishing/hub_fishing.yml` (verbatim)

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
    - item: PUFFERFISH
      weight: 10
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
