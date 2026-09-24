# Recipe Module — User Documentation

> The Valmora machine/crafting engine. Powered by custom **machines** (crafting table, forge, press, anvil, alchemy table) defined in GUI files and backed by recipes in `plugins/Valmora/recipes/*.yml`.
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
| **Forge** | Forge GUI | 2 input slots: left = **base item**, right = **material**. A positional (SHAPED) recipe, like the crafting table just narrower. |
| **Anvil** | Anvil GUI | Left = **item to upgrade**, right = **material**. Explicit `recipes/anvil/` upgrades, reforges and gemstones (the modifier framework), enchant merging and repair — see `docs/modules/user/modifier.md` and the website's Anvil page. |
| **Alchemy Table** | Alchemy Table GUI | Ingredient in the top slot, potions/bottles below. Brews Valmora potions on a timer. |

Reforging has no machine of its own any more: reforge stones and the random reforge are anvil
recipes of the modifier framework (`modifiers/recipes/reforges.yml`).

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
- Default files: `example_recipes.yml`, `press_examples.yml`, `alchemy.yml`, `anvil/examples.yml`.
- After editing: run `/valmora reload` (permission `valmora.admin`). The log shows how many recipes loaded and any parse errors, each prefixed with the file path.

### Referencing items

An `item:` value can be either:

- **A Valmora custom item ID** — e.g. `reinforced_ingot`, `ferrite_pickaxe`, `testSword`. The ID is the top-level key of an entry in `plugins/Valmora/items/*.yml`. Matching is case-insensitive and based on the item's persistent item ID.
- **A Bukkit Material name** — e.g. `DIAMOND`, `STICK`, `GLASS_BOTTLE`, `LOG`. Matching is by exact material type.

### Machine IDs

The `machine:` key must match the `machine:` key of a GUI definition file in `plugins/Valmora/guis/*.yml`. Machine IDs in use:

| Machine ID | GUI file | Recipes |
|---|---|---|
| `crafting_table` | `guis/crafting.yml` | YAML (`recipes/*.yml`), then vanilla crafting |
| `forge` | `guis/forge.yml` | YAML |
| `press` | `guis/press.yml` | YAML (`recipes/press_examples.yml`) |
| `alchemy` | `guis/alchemy.yml` | Code (`alchemy/*.yml` effects) |
| `anvil` | `guis/anvil.yml` | `recipes/anvil/*.yml` UPGRADE/TRANSMUTE, then `modifiers/recipes/*.yml`, then enchant merge/repair |

Each machine is also declared in `machines/*.yml` (which GUI it opens and how players open it — see
`open-triggers`). Defining a recipe under a machine ID without a matching GUI does nothing.

### Recipe types

Every recipe is `SHAPED` or `SHAPELESS` — there is no separate "named slot" type any more. A
machine with a small fixed number of slots (the forge's 2, the press's 3) is just a `SHAPED` recipe
with a narrower pattern than a 3×3 crafting grid, not a different format to learn.

| Type | `ingredients` format | Rules |
|---|---|---|
| `SHAPELESS` | **list** of `{ item, amount }` | The listed ingredients in any slots, in any order; each requirement needs its own physical slot. `pattern:` is ignored here (and warned about) if present. |
| `SHAPED` | letter → `{ material, amount }` map, referenced by `pattern:` | A spatial pattern that can be placed anywhere it fits within the machine's grid. |

Pattern rows use one character per physical slot, `' '` for "must stay empty". For the 3×3 crafting
table:

```
0 1 2
3 4 5
6 7 8
```

For a 2×2 recipe, use a pattern that's only 2 rows of 2 characters — it'll match in any of the 4
corners. For the forge (2 slots, one row), a pattern is a single 2-character row: `pattern: ["ab"]`.

### Working examples

**Shaped custom-item craft (3×3 grid):**

```yaml
test_sword_craft:
  machine: crafting_table
  type: SHAPED
  ingredients:
    r: { material: reinforced_ingot, amount: 1 }
    s: { material: STICK, amount: 1 }
  pattern:
    - "r  "
    - "r  "
    - "s  "
  outputs:
    - item: testSword
      amount: 1
```

**Forge recipe (a 2-slot positional machine — SHAPED, single-row pattern):**

```yaml
forged_blade:
  machine: forge
  type: SHAPED
  ingredients:
    s: { material: IRON_SWORD, amount: 1 }
    r: { material: reinforced_ingot, amount: 2 }
  pattern:
    - "sr"
  outputs:
    - item: forged_blade
      amount: 1
```

**Shapeless alchemy recipe with an on-craft script:**

```yaml
healing_potion_bottle:
  machine: alchemy
  type: SHAPELESS
  ingredients:
    - item: GLISTERING_MELON_SLICE
      amount: 1
    - item: GLASS_BOTTLE
      amount: 1
  outputs:
    - item: POTION
      amount: 1
  on-craft:
    - "sound player entity.witch.celebrate"
    - "variable add player.var.alchemy_xp 15"
```

**A recipe with two outputs, routed to two named OUTPUT slots** (see the GUI's own `id:` under each
`O`-type component — a "processor" machine with `primary`/`byproduct` slots, for example):

```yaml
ore_processing:
  machine: processor
  type: SHAPELESS
  ingredients:
    - item: raw_ore
      amount: 1
  outputs:
    - slot: primary
      item: refined_ore
      amount: 1
    - slot: byproduct
      item: ore_dust
      amount: 1
```

A recipe with more than one `outputs:` entry **must** give every entry a `slot:` — the load fails
with an error naming the recipe if any entry is missing one, so a mistake here is caught immediately
rather than showing up as an item landing in the wrong slot (or nowhere) later.

### Tips & warnings

- **Duplicate shapeless ingredients need separate slots.** Two `amount: 1` requirements for the same item cannot be satisfied by one stack, even if that stack holds 2+.
- **A single-output recipe doesn't need `slot:`.** It always targets the machine's sole/first OUTPUT component.
- **Vanilla recipes still work** in the crafting table GUI (e.g. sticks, torches) — the engine falls back to vanilla crafting when nothing custom matches.
- **Reload is global:** `/valmora reload` reloads *all* modules, not just recipes.

---

## Configuration Reference

File: `plugins/Valmora/recipes/<name>.yml`

### Recipe schema

```yaml
<recipe-id>:
  machine: <machine-id>            # required
  type: SHAPELESS|SHAPED           # required
  ingredients:                     # see below — list for SHAPELESS, letter-map for SHAPED
    ...
  pattern:                         # SHAPED only
    - "..."
  outputs:
    - item: <item-id-or-material>  # required
      amount: <integer>            # optional, default 1
      slot: <output-component-id>  # required only if outputs: has more than one entry
  on-craft:                        # optional
    - "<script event line>"
```

### Key reference

| Key | Required | Default | Description |
|---|---|---|---|
| `<recipe-id>` | Yes | — | Unique recipe name. Also the ID shown in load/parse errors. |
| `machine` | Yes | — | Machine ID this recipe matches in (must equal a GUI's `machine:`). Omitted → recipe never matches. |
| `type` | Yes | — | `SHAPELESS` or `SHAPED`. Case-insensitive. Anything else fails the load. |
| `ingredients` | Yes | — | Ingredient definition (see below). |
| `pattern` | SHAPED only | — | Rows of `ingredients:` letters, one character per slot. Required for SHAPED; warned-and-ignored on SHAPELESS. |
| `outputs` | Yes | — | Result item(s) — a list, every entry built and given. |
| `outputs[].item` | Yes | — | Output Valmora item ID or Bukkit Material name. |
| `outputs[].amount` | No | `1` | How many of the output item a craft produces. |
| `outputs[].slot` | Required if `outputs:` has >1 entry | `null` | Target OUTPUT component id. |
| `on-craft` | No | — | List of script events executed when the craft finishes (e.g. sounds, `variable add player.var.<name> <amount>` for XP/currency). |

### `ingredients` forms

**List form (SHAPELESS):**

| Key | Required | Default | Description |
|---|---|---|---|
| `ingredients[].item` | Yes | — | Required item (custom ID or material). |
| `ingredients[].amount` | No | `1` | Quantity required. |

**Letter-map form (SHAPED):**

| Key | Required | Default | Description |
|---|---|---|---|
| `ingredients.<letter>` | Yes | — | Single-character key, referenced by `pattern:`. |
| `ingredients.<letter>.material` | Yes | — | Required item (custom ID or material). `item:` is accepted as an alias. |
| `ingredients.<letter>.amount` | No | `1` | Quantity required in that one slot. |

### Permissions

| Permission | Effect |
|---|---|
| `valmora.admin` | Allows `/valmora reload`, which reloads recipes along with every other module. |

The recipe module itself defines no player-facing commands.
