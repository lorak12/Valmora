# Enchant Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `enchants` | **Config folder:** `plugins/Valmora/enchants/`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The Enchant module adds **custom RPG enchantments** on top of vanilla Minecraft. Unlike vanilla enchants (which come from enchanting tables, villagers, and books), Valmora enchantments are:

- **Fully configurable** — every enchant is defined in `plugins/Valmora/enchants/*.yml` as plain YAML.
- **Stored on the item itself** — enchantments live in the item's hidden data and travel with it through inventories, trading, and drops. No database involved.
- **Visually distinct** — an enchanted item gains a purple **glint** plus a blue **lore block** listing the enchant and its description.
- **Effect-driven** — each enchant maps to a `logic` handler that determines what it actually does: passive stat bonuses (e.g. +Health, +Mining Fortune), pre-hit damage modifiers (e.g. +% melee damage), or defense reduction on the target.

There are three ways to get/apply enchants in a stock install:

1. **`/item enchant <enchant_id> <level>`** (admin) — apply directly to the item in your main hand.
2. **`/item enchantbook <enchant_id> <level>`** (admin) — spawn an Enchanted Book carrying the enchant.
3. **The Anvil** — combine an item carrying Valmora enchants with another item or Enchanted Book that carries them. Cost: **10 coins per total enchant level**.

An **Enchanting Table GUI** definition ships as `guis/enchanting.yml`, but in the stock plugin it is **not wired to any command or machine handler** (see [Limitations](#limitations)), so players normally get enchants through the anvil and admin-given items.

---

## Player Guide

### How Valmora enchantments appear

Look at an enchanted item in your inventory:

- The item has a **purple glowing glint** (like a vanilla enchanted item), but no vanilla enchant line is shown — it's a purely visual effect.
- Below the stats, a **blue block** lists each enchant as `<id> <level>` (e.g. `sharpness 5`), followed by the enchant's grey description lines.

For items carrying **four or more** enchants, the lore switches to a **compact mode**: enchant names are packed together on single lines (`sharpness 5, growth 3, fortune 2, ...`) and descriptions are hidden.

### How levels work

- Level is a plain integer (1, 2, 3, …) applied per enchant.
- Each enchant defines two ceilings:
  - **`etable-max-level`** — the ceiling for the Enchanting Table / Enchanted Book path.
  - **`absolute-max-level`** — the absolute hard cap for the anvil merge path.
- **Caveat (as shipped):** no code actually clamps levels on the `/item` or GUI apply paths — an admin can apply any level with `/item enchant`. Only the **anvil merge** enforces these ceilings.

### Applying enchants

**Via anvil (anyone):**
1. Put an item carrying Valmora enchants in the **base** slot.
2. Put a **material** in the second slot — either a Valmora-enchanted item or an Enchanted Book carrying Valmora enchants.
3. The result merges the enchant maps:
   - Same enchant at the **same level** → level **+1** (capped).
   - Different levels → the **higher** level wins.
   - **Conflicting** enchants (per each enchant's `conflicts` list) are skipped.
4. Cost: **10 coins per level of the merged enchants**, deducted from the player's coin balance.

**Via admin commands (`/item enchant` / `/item enchantbook`):**
- `/item enchant sharpness 5` — applies Sharpness V to the item in your main hand. Only works if the enchant's `targets` include your item's type.
- `/item enchantbook life_steal 3` — gives you an Enchanted Book with Life Steal III.

### The Enchanting Table GUI (shipped but not reachable by default)

The file `guis/enchanting.yml` defines a custom enchanting-table interface with two phases:

1. **Enchant catalog** — shows every enchant whose `targets` match the item you placed in the ingredient slot (with its `etable-max-level` and `absolute-max-level`).
2. **Level selection** — click an enchant, then click a level. Levels are shown as:
   - **Green** (`available`) — apply it.
   - **Yellow** (`active`) — your current level; clicking removes it.
   - **Grey** (`locked`) — a level you've already surpassed.

However, in the stock plugin there is **no command or block interaction wired to open this GUI** — a server admin must add an `open_gui` action (from another GUI) or a `command` key to make it reachable. Additionally, applying an enchant through the GUI costs **nothing** (no XP/mana/coins are deducted).

### Enchanting skill XP

The **Enchanting skill** (`/skill info enchanting`, max level 60) is leveled up by performing **vanilla** enchant actions (using a vanilla enchanting table or anvil), per `skills/enchanting.yml`. Valmora enchant *definitions* themselves do not grant Enchanting skill XP — the two systems are separate.

### Quest objective

The Quest system's `enchant` objective type also tracks **vanilla** enchant actions (`/quest` editor → objective `enchant <item> <enchants>`), not Valmora enchants.

---

## Admin Guide

### Where configs live

Enchant definitions are YAML files in `plugins/Valmora/enchants/`. The plugin ships `example_enchantments.yml` there on first run. Files are only written if they don't already exist, so your edits survive restarts.

After editing, run **`/valmora reload`** to reload the module (requires `valmora.admin`). Enchants defined in new files, and edits to existing ones, take effect immediately; enchants already applied to items re-render on the next lore rebuild.

### Defining an enchant

Create a new file (or add to an existing one) in `plugins/Valmora/enchants/`. Every top-level key is an enchant **ID**:

```yaml
my_enchant:
  name: "My Enchant"
  logic: "valmora:stat_bonus"
  description:
    - "<gray>Grants <yellow>+2 Strength<gray> per level."
  targets: [SWORD, AXE]
  conflicts: [some_other_enchant]
  etable-max-level: 5
  absolute-max-level: 10
  logic-params:
    stat: "strength"
    per-level: 2.0
```

### Choosing a `logic`

The `logic` key decides what the enchant *does*. Registered values:

| `logic` | Parameters (`logic-params`) | Effect |
|---|---|---|
| `valmora:sharpness` | — | +5% **melee** damage per level (applied before hit) |
| `valmora:growth` | — | +10 **Health** per level (passive, players) |
| `valmora:fortune` | — | +10 **Mining Fortune** per level (passive, players) |
| `valmora:efficiency` | — | +50 **Mining Speed** per level (passive, players) |
| `valmora:stat_bonus` | `stat` (default `strength`), `per-level` (default `1.0`) | +`per-level` of any stat per level (passive) |
| `valmora:damage_multiplier` | `type` (default `MELEE`; `ANY` or a damage type), `percent-per-level` (default `5.0`) | +`percent-per-level`% damage per level for that damage type (before hit) |
| `valmora:defense_reduction` | `percent-per-level` (default `3.0`) | Reduces the victim's defense by `percent-per-level`%×level on hit |

Damage types usable in `type`: `MELEE`, `PROJECTILE`, `FALL`, `DROWNING`, `FIRE`, `LAVA`, `MAGIC`, `VOID`, `POISON`, `WITHER`, `EXPLOSION` — or `ANY`.

**Important:** if `logic` does not match a registered key, the enchant still appears in the GUI and lore but has **no effect**. The shipped `example_enchantments.yml` includes several such enchants (`execute`, `first_strike`, `life_steal`, `lethality`, `protection`, `respite`, `thorns`) that reference logic IDs **not yet implemented** — they are decorative until their logic is built. There is no warning at load time, so double-check your logic IDs.

### Permissions & commands

| Command | Permission | Description |
|---|---|---|
| `/item enchant <id> <level>` | `valmora.admin` | Apply an enchant to the item in your main hand (type-checked). |
| `/item enchantbook <id> <level>` | `valmora.admin` | Give yourself an Enchanted Book with the enchant. |
| `/valmora reload` | `valmora.admin` | Reload all modules, including enchants. |

Both `/item` subcommands have tab-completion for enchant IDs and levels (`1`–`5`).

### Item type values for `targets`

Valid values (case-insensitive): `SWORD`, `AXE`, `PICKAXE`, `SHOVEL`, `HOE`, `TRIDENT`, `BOW`, `CROSSBOW`, `FISHING_ROD`, `SHEARS`, `SHIELD`, `ELYTRA`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`, `HORSE_ARMOR`, `PET`, `ACCESSORY`, `BACKPACK`, `ALL`, `NONE`.

- `ALL` matches **every** item type.
- Unknown entries in `targets` are silently ignored at load time.

### Example — a full file

```yaml
# plugins/Valmora/enchants/my_enchants.yml
vampirism:
  name: "Vampirism"
  logic: "valmora:life_steal"      # NOTE: not implemented yet in 0.1
  description:
    - "Heals <red>+1%<gray> of your max health per level"
    - "each time you deal damage."
  targets: [SWORD]
  conflicts: [life_steal]
  etable-max-level: 3
  absolute-max-level: 5
```

### Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Enchant shows in lore but has no effect | The `logic` key is unregistered (see the table above) — check spelling, or it's one of the not-yet-implemented logics. |
| `/item enchant` says "cannot be applied to this item" | Your item isn't a Valmora item with a matching `targets` type. Vanilla (non-Valmora) items can't take type-checked enchants. |
| Vanilla item in the enchanting-table GUI won't apply | Same restriction — the apply path requires a Valmora item-type tag. |
| Enchant lore repeats on re-enchant | This was fixed (`docs/UNFINISHED_FEATURES.md` §12) — lore is rebuilt from a stored snapshot, so the block should never duplicate. |

---

## Configuration Reference

### File layout

```
plugins/Valmora/enchants/
└── example_enchantments.yml      # shipped example (10 enchants)
```

Each YAML file contains one or more enchant definitions under top-level keys. The key is the **enchant ID** — it becomes the registry ID, the lore label, the GUI event argument, and the PDC storage key (always stored lowercase internally).

### Schema

```yaml
<enchant-id>:
  name: "<display name>"
  logic: "<logic_key>"
  logic-params:
    <param>: <value>
  description:
    - "<MiniMessage line>"
  targets:
    - SWORD
  conflicts:
    - "other_enchant_id"
  etable-max-level: 5
  absolute-max-level: 10
```

### Field reference

| Field | Required | Default | Explanation |
|---|---|---|---|
| `<enchant-id>` (top-level key) | **Yes** | — | Enchant ID. Must be unique across all `enchants/*.yml`. Referenced by `/item enchant`, GUI events, and anvil merges. |
| `name` | No | the enchant ID | Display name shown in the enchanting-table GUI and `/item info`. **Not** used on the item's lore lines (lore shows the raw ID, e.g. `sharpness 5`). |
| `logic` | Yes* | `""` | The effect handler key — see the logic table in the [Admin Guide](#admin-guide). If unregistered, the enchant is inert. |
| `logic-params` | No | empty | Tuning parameters for the parameterized logics (`stat_bonus`, `damage_multiplier`, `defense_reduction`). |
| `description` | No | `[]` | MiniMessage lines shown under the enchant on the item lore (only when the item has fewer than 4 enchants). |
| `targets` | **Yes** | — | Item types the enchant can apply to (see the values list above). Invalid entries are silently ignored. |
| `conflicts` | No | `[]` | Enchant IDs this enchant cannot coexist with. Enforced **only** by the anvil merge; direct `/item` and GUI applies ignore it. |
| `etable-max-level` | No | `5` | Ceiling for the Enchanting-Table path and Enchanted-Book merges in the anvil. |
| `absolute-max-level` | No | `10` | Absolute hard cap applied by the anvil for non-book merges. |

\* Technically optional — but a missing/unknown `logic` produces a definition with no effect.

### Defaults at a glance

| Setting | Default |
|---|---|
| `name` | enchant ID |
| `description` | `[]` |
| `etable-max-level` | `5` |
| `absolute-max-level` | `10` |
| `conflicts` | `[]` |
| `logic` | `""` (no effect) |
| `logic-params.stat` (`stat_bonus`) | `"strength"` |
| `logic-params.per-level` (`stat_bonus`) | `1.0` |
| `logic-params.type` (`damage_multiplier`) | `"MELEE"` |
| `logic-params.percent-per-level` (`damage_multiplier`) | `5.0` |
| `logic-params.percent-per-level` (`defense_reduction`) | `3.0` |

---

## Limitations

These are the current behavior gaps of the module (see the design doc for the full list):

- The shipped **Enchanting Table GUI is not reachable** in a stock install (no command, no machine handler) and applies enchants **for free**.
- The example file's `execute`, `first_strike`, `life_steal`, `lethality`, `protection`, `respite`, and `thorns` enchants have **no logic implemented**.
- `/item enchant` and the GUI apply path **only work on Valmora items** — vanilla items can't receive enchants that way (the anvil is the exception).
- Level ceilings and `conflicts` are **not enforced** by the `/item` and GUI apply paths — only by the anvil.
