# Recipe Module — User Documentation

> The Valmora machine/crafting engine. Powered by custom **machines** (crafting table, forge, anvil, alchemy table, reforge) defined in GUI files and backed by recipes in `plugins/Valmora/recipes/*.yml`.
> Module ID: `recipe` — has no commands and no permissions of its own; everything is driven through GUIs.

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

Recipes let players combine items inside Valmora **machine GUIs** to produce new items. A recipe declares:

- **What machine** it belongs to (which GUI screen it matches in).
- **What inputs** are required (a named set of slots, a shapeless ingredient list, or a spatial 3×3 grid pattern).
- **What output** is produced (a vanilla material or a Valmora custom item).
- Optionally an **`on-craft` script** that runs when the craft completes (sounds, variable changes, XP grants).

Custom Valmora items and vanilla materials can be mixed freely in the same recipe. Every output is automatically formatted as a proper Valmora item (rarity, stats, lore).

Recipes are **configuration-only**: edit the YAML files and run `/valmora reload` (requires the `valmora.admin` permission). No database is involved.

---

## Player Guide

### The machine GUIs

Each machine is a menu opened by the server. Recipes match automatically as you place items in the **input slots** (`INPUT`), and the result appears in the **output slot** (`OUTPUT`).

| Machine | Where to find it | How it works |
|---|---|---|
| **Crafting Table** | Crafting Table GUI | 3×3 grid. Place a pattern in the input slots on the left; the result appears on the right. Works with shaped, shapeless, and vanilla recipes. |
| **Forge** | Forge GUI | 2 input slots: left = **base item**, middle = **material**. Exact-slot recipes (forge-only). |
| **Anvil** | Anvil GUI | Left = **item to upgrade**, middle = **upgrade material**. Combines Valmora enchantments. |
| **Alchemy Table** | Alchemy Table GUI | Ingredient in the top slot, potions/bottles below. Brews Valmora potions on a timer. |
| **Reforge Anvil** | Reforge Anvil GUI | Left = **item**, right = **Reforge Stone**. Applies the stone's reforge to the item for a coin cost. |
| **Reforge Item** | Reforge GUI | One input slot. Applies a **random** valid reforge for a coin cost. |

### Crafting tips

- **Collect the result:** click the **output slot**. Normal click crafts once; **shift-click crafts up to 64** (as long as your inventory has room). The output slot is protected against "recipe mutation" — if your ingredients run out mid-craft, the loop stops instead of accidentally producing a smaller item.
- **Result updates live:** as soon as the placed items match a recipe, the output slot fills in. Removing an input clears it.
- **Alchemy is time-based:** place a base potion (or empty glass bottle) in the bottom slots and an ingredient in the top slot. The table brews over a short timer, plays a sound, and grants **Alchemy XP**. Fresh empty bottles and existing potions are both accepted for the same effects.
- **Reforge costs coins** based on your item's rarity — the exact prices are shown in the GUI.
- **Anvil merging follows enchanting-table rules:** merging identical enchantments upgrades the level; conflicting enchantments are skipped; using a book is limited to the enchantment's enchanting-table ceiling.

---

## Admin Guide

### Where recipes live

- Config folder: `plugins/Valmora/recipes/` (any `.yml` file). Defaults are auto-copied from the jar on first run and are **not** overwritten on reload.
- Default files: `crafting_table.yml`, `forge.yml`, `alchemy.yml`, `shardworks_recipes.yml`.
- After editing: run `/valmora reload` (permission `valmora.admin`). The log shows how many recipes loaded and any parse errors, each prefixed with the file path.

### Referencing items

An `item:` value can be either:

- **A Valmora custom item ID** — e.g. `reinforced_ingot`, `ferrite_pickaxe`, `testSword`. The ID is the top-level key of an entry in `plugins/Valmora/items/*.yml`. Matching is case-insensitive and based on the item's persistent item ID.
- **A Bukkit Material name** — e.g. `DIAMOND`, `STICK`, `GLASS_BOTTLE`, `LOG`. Matching is by exact material type.

### Machine IDs

The `machine:` key must match the `machine:` key of a GUI definition file in `plugins/Valmora/guis/*.yml`. Machine IDs in use:

| Machine ID | GUI file |
|---|---|
| `crafting_table` | `guis/crafting.yml` |
| `forge` | `guis/forge.yml` |
| `alchemy` | `guis/alchemy.yml` |
| `anvil` | `guis/anvil.yml` (dynamic, no YAML recipes) |
| `reforge_anvil` | `guis/reforge_anvil.yml` (dynamic) |
| `forge_random` | `guis/reforge.yml` (dynamic) |

Dynamic machines (anvil, reforge) are handled in code — you do **not** define their recipes in YAML. Defining a recipe under a machine ID without a matching GUI does nothing.

### Recipe types

| Type | `inputs` format | Rules |
|---|---|---|
| `EXACT_SLOT` | section keyed by **slot name** (`input1`, `input2`) | Every named slot must be filled with the exact item; extra items are not allowed. |
| `SHAPELESS` | **list** of `{ item, amount }` | The listed ingredients in any slots, in any order; each requirement needs its own physical slot. |
| `SHAPED` | section keyed by **grid slot number** `0`–`8` | A spatial pattern that can be placed anywhere in the grid. |

Grid layout for `SHAPED` recipes:

```
0 1 2
3 4 5
6 7 8
```

For a 2×2 recipe use only the four needed slots (e.g. `0, 1, 3, 4` for the top-left corner).

### Working examples

**Shaped custom-item craft (3×3 grid):**

```yaml
test_sword_craft:
  machine: crafting_table
  type: SHAPED
  inputs:
    "0": { item: reinforced_ingot, amount: 1 }
    "3": { item: reinforced_ingot, amount: 1 }
    "6": { item: STICK, amount: 1 }
  outputs:
    result: { item: testSword, amount: 1 }
```

**Exact-slot forge recipe (2 inputs):**

```yaml
forged_blade:
  machine: forge
  type: EXACT_SLOT
  inputs:
    "input1": { item: IRON_SWORD, amount: 1 }
    "input2": { item: reinforced_ingot, amount: 2 }
  outputs:
    result: { item: forged_blade, amount: 1 }
```

**Shapeless alchemy recipe with an on-craft script:**

```yaml
healing_potion_bottle:
  machine: alchemy
  type: SHAPELESS
  inputs:
    - item: GLISTERING_MELON_SLICE
      amount: 1
    - item: GLASS_BOTTLE
      amount: 1
  outputs:
    result:
      item: POTION
      amount: 1
  on-craft:
    - "sound player entity.witch.celebrate"
    - "variable add player.var.alchemy_xp 15"
```

### Tips & warnings

- **Duplicate shapeless ingredients need separate slots.** Two `amount: 1` requirements for the same item cannot be satisfied by one stack, even if that stack holds 2+.
- **Only the first entry under `outputs:` is used.** Name it `result` to stay safe.
- **Vanilla recipes still work** in the crafting table GUI (e.g. sticks, torches) — the engine falls back to vanilla crafting when nothing custom matches.
- **Reload is global:** `/valmora reload` reloads *all* modules, not just recipes.

---

## Configuration Reference

File: `plugins/Valmora/recipes/<name>.yml`

### Recipe schema

```yaml
<recipe-id>:
  machine: <machine-id>            # required
  type: EXACT_SLOT|SHAPELESS|SHAPED   # optional, default EXACT_SLOT
  inputs:
    <slot-or-list>:                # see below
      item: <item-id-or-material>  # required
      amount: <integer>            # optional, default 1 (section form)
  outputs:
    result:
      item: <item-id-or-material>  # required
      amount: <integer>            # optional, default 1
  on-craft:                        # optional
    - "<script event line>"
```

### Key reference

| Key | Required | Default | Description |
|---|---|---|---|
| `<recipe-id>` | Yes | — | Unique recipe name. Also the ID shown in load/parse errors. |
| `machine` | Yes | — | Machine ID this recipe matches in (must equal a GUI's `machine:`). Omitted → recipe never matches. |
| `type` | No | `EXACT_SLOT` | `EXACT_SLOT`, `SHAPELESS`, or `SHAPED`. Case-insensitive. |
| `inputs` | Yes | — | Ingredient definition (see below). |
| `outputs` | No | — | Result item(s); only the first entry is used. |
| `outputs.<name>.item` | Yes | — | Output Valmora item ID or Bukkit Material name. |
| `outputs.<name>.amount` | No | `1` | How many of the output item a craft produces. |
| `on-craft` | No | — | List of script events executed when the craft finishes (e.g. sounds, `variable add player.var.<name> <amount>` for XP/currency). |

### `inputs` forms

**Section form (EXACT_SLOT and SHAPED):**

| Key | Required | Default | Description |
|---|---|---|---|
| `inputs.<slot>` | Yes | — | Slot key. Named (`input1`) for EXACT_SLOT; grid number (`"0"`–`"8"`) for SHAPED. |
| `inputs.<slot>.item` | Yes | — | Required item (custom ID or material). |
| `inputs.<slot>.amount` | No | `1` | Quantity required in that slot. |

**List form (SHAPELESS only):**

| Key | Required | Description |
|---|---|---|
| `inputs[].item` | Yes | Required item (custom ID or material). |
| `inputs[].amount` | Yes | Quantity required (in list form this is mandatory). |

### Permissions

| Permission | Effect |
|---|---|
| `valmora.admin` | Allows `/valmora reload`, which reloads recipes along with every other module. |

The recipe module itself defines no player-facing commands.
