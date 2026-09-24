# Enchant Module — User Documentation

> **Version:** 0.2 (post enchant-overhaul) | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `enchants` | **Config folder:** `plugins/Valmora/enchants/`

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)
5. [Migrating an Old-Style Enchant](#migrating-an-old-style-enchant)
6. [Troubleshooting](#troubleshooting)

---

## Overview

The Enchant module adds **custom RPG enchantments** on top of vanilla Minecraft. Unlike vanilla
enchants, Valmora enchantments are:

- **Fully configurable** — every enchant is defined in `plugins/Valmora/enchants/*.yml` as plain YAML.
- **Stored on the item itself** — enchantments live in the item's hidden data and travel with it
  through inventories, trading, and drops. No database involved.
- **Visually distinct** — an enchanted item gains a purple **glint** plus a blue **lore block**
  listing the enchant and its description.
- **Written entirely in YAML, if you want** — an enchant's effect can be a passive stat bonus, a
  conditional damage multiplier, a per-hit combo counter, an on-kill heal, an on-hit stacking debuff,
  or any combination of those — all without writing a line of Java. A separate, still-supported Java
  hook (`logic:`) exists for anything the YAML layer doesn't (yet) cover, and both run side by side
  if you use both on the same enchant.

There are three ways to get/apply enchants in a stock install:

1. **The Enchanting Table GUI** (`/enchanting`, or right-click a physical enchanting table if a
   `machines/*.yml` entry is bound to one) — costs XP levels, and refuses a level above the enchant's
   `etable-max-level`.
2. **`/item enchant <enchant_id> <level>`** (admin) — apply directly to the item in your main hand,
   bypassing the table's XP cost (still respects `absolute-max-level`).
3. **`/item enchantbook <enchant_id> <level>`** (admin) — spawn an Enchanted Book carrying the enchant.
4. **The Anvil** — combine an item carrying Valmora enchants with another item or Enchanted Book that
   carries them.

---

## Player Guide

### How Valmora enchantments appear

Look at an enchanted item in your inventory:

- The item has a **purple glowing glint** (like a vanilla enchanted item), but no vanilla enchant
  line is shown — it's a purely visual effect.
- Below the stats, a **blue block** lists each enchant as `<Name> <Roman numeral>` (e.g.
  `Sharpness V`), followed by the enchant's grey description lines.

For items carrying **four or more** enchants, the lore switches to a **compact mode**: enchant names
are packed together on single lines and descriptions are hidden.

### How levels work

- Level is a plain integer (1, 2, 3, …) applied per enchant, displayed as a Roman numeral.
- Each enchant defines two ceilings:
  - **`etable-max-level`** — the ceiling for the Enchanting Table path.
  - **`absolute-max-level`** — the absolute hard cap for `/item enchant` and the anvil merge path.
- Both ceilings are now enforced **server-side**, not just by which buttons a GUI happens to render.

### Applying enchants via the Enchanting Table

1. Open the table and place an item in the ingredient slot.
2. Pick an enchant whose `targets` match your item's type.
3. Pick a level. The table shows you the **XP-level cost** before you click — it charges 2 XP levels
   per level of the enchant you're requesting (a level 5 enchant costs 10 XP levels), and simply
   refuses the click if you don't have enough.
4. You cannot request a level above the enchant's `etable-max-level` from the table — for higher
   levels you need the anvil.

### Applying enchants via the Anvil

1. Put an item carrying Valmora enchants in the **base** slot.
2. Put a **material** in the second slot — either a Valmora-enchanted item or an Enchanted Book
   carrying Valmora enchants.
3. The result merges the enchant maps:
   - Same enchant at the **same level** → level **+1** (capped at `absolute-max-level`, or
     `etable-max-level` if the material is an Enchanted Book).
   - Different levels → the **higher** level wins.
   - **Conflicting** enchants (per each enchant's `conflicts` list) are skipped.
4. Cost is XP levels, following the same "prior work" penalty every other anvil operation uses (see
   `docs/modules/user/recipe.md`'s anvil section) — repeatedly working the same item raises the cost.

### Admin commands

- `/item enchant sharpness 5` — applies Sharpness V to the item in your main hand, clamped to
  `absolute-max-level` (not the table's lower `etable-max-level` ceiling).
- `/item enchantbook life_steal 3` — gives you an Enchanted Book with Life Steal III.

### Enchanting skill XP & the quest objective

The **Enchanting skill** and the Quest module's `enchant` objective both still track only **vanilla**
enchanting-table/anvil actions — Valmora enchant definitions are a separate system and don't feed
either of those.

---

## Admin Guide

### Where configs live

Enchant definitions are YAML files in `plugins/Valmora/enchants/`. The plugin ships
`example_enchantments.yml` there on first run (only if the file doesn't already exist, so your edits
survive restarts) — **open that file first**: every enchant in it is commented to explain what each
block is doing and why, and it's the fastest way to see the schema in action before writing your own.

After editing, run **`/valmora reload`** (requires `valmora.admin`).

### Defining an enchant — the short version

The smallest useful enchant is just a stat bonus:

```yaml
strength_boost:
  name: "Strength Boost"
  description:
    - "<gray>Grants <yellow>+2 Strength<gray> per level."
  targets: [SWORD, AXE]
  etable-max-level: 5
  absolute-max-level: 10
  stats:
    strength: "2 * $level$"
```

No `logic:`, no Java class, no restart — just `stats:` and a formula. `$level$` is always the current
level of whichever instance is being evaluated.

### The full toolbox

Every block below is optional and independent — mix whichever ones your enchant actually needs.

| Block | What it's for | Worked example in `example_enchantments.yml` |
|---|---|---|
| `stats:` | A flat per-level bonus to any stat, evaluated as a formula | `growth`, `protection`, `fortune`, `efficiency`, `respite` |
| `combat.modify-attack:` / `modify-defend:` | A pre-hit numeric modifier (damage multiplier, crit chance, defense shred, damage reduction), optionally gated by `conditions:` | `sharpness`, `execute`, `first_strike`, `lethality` |
| `triggers.<TRIGGER>:` | Run a list of DSL events (`heal`, `damage`, `sound`, `enchant_state`, …) after a hit or kill, gated by `conditions:` | `first_strike`, `life_steal`, `lethality` |
| `state.transient:` / `state.persistent:` | A per-attacker combo/stacking counter (never saved) or a per-item counter (saved to the item's data), read back as `$enchant.state.<key>$` | `first_strike` (transient combo), `lethality` (transient stacks) |
| `variables:` | Named `$level$`-scoped formulas, evaluated once and reusable as `$calc.<name>$` in both `combat:` and `triggers:` | `first_strike`, `lethality`, `life_steal` |
| `logic:` | The old Java hook — still fully supported, and runs alongside anything above | `thorns` (the one enchant that must stay Java-only — see below) |

`example_enchantments.yml` has a complete, commented example of every one of these except a bare
`triggers:`-only enchant — read it before writing your own, since seeing a real formula in context is
much faster than reading a schema table.

### Variables available in formulas and conditions

| Variable | Where it's valid | Meaning |
|---|---|---|
| `$level$` / `$enchant.level$` | anywhere | the current enchant instance's level |
| `$enchant.state.<key>$` | anywhere | the value of a `state.transient`/`state.persistent` key |
| `$calc.<name>$` | anywhere | a pre-evaluated `variables:` formula |
| `$hit.damage$` / `$hit.is_crit$` / `$hit.damage_type$` | `triggers.ON_ATTACK_POST`/`ON_DEFEND_POST` only | the just-computed hit |
| `$target.hp_percent$` / `$target.missing_hp_percent$` | `combat:`/`triggers:` | the victim's (or, on defend-side, the attacker's) health percentage |
| `$player.in_combat$` | `stats:` | whether the wearer is currently in combat |

### `triggers:` — the four moments you can react to

| Trigger | Fires | `@self` means | `@target` means |
|---|---|---|---|
| `ON_ATTACK_POST` | after your hit lands, for your weapon's enchants | the attacker | the victim |
| `ON_DEFEND_POST` | after you take a hit, for your armor's enchants | **the wearer** (you) | **the attacker** |
| `ON_KILL` | you land the killing blow, for your weapon's enchants | the attacker | the victim (dead) |
| `ON_DEATH` | the holder/wearer dies, for every enchanted item they hold or wear | the player who died | their killer (if any) |

Note `ON_DEFEND_POST`'s selectors are intentionally swapped relative to `ON_ATTACK_POST` — a thorns-
style "hit the attacker back" action always means "hit `@target`", regardless of which side of the
exchange you're declaring a trigger for.

### `state:` — combo counters and per-item counters

```yaml
state:
  transient:
    combo_counter:
      type: HIT_COUNTER
      reset-after-seconds: 10       # counter zeroes after 10s of no hits
      reset-on-target-switch: true  # counter also zeroes if you switch targets
      max-stacks: 3                 # counter never exceeds 3
  persistent:
    kills:
      type: INTEGER
      default: 0                    # value read on an item that's never had this key written
```

- **`transient`** counters live in memory, per player, and are lost on `/valmora reload` or a server
  restart — use these for combo/stacking mechanics that only matter mid-fight.
- **`persistent`** counters are saved on the item itself and survive forever (a "kills with this
  sword" tally, for instance).
- Mutate either one with the `enchant_state` event inside a `triggers:` action list:
  `enchant_state increment <key>`, `enchant_state add <key> <amount>`, `enchant_state set <key> <amount>`,
  `enchant_state reset <key>`.

### `targets` — valid item type values

Case-insensitive: `SWORD`, `AXE`, `PICKAXE`, `SHOVEL`, `HOE`, `TRIDENT`, `BOW`, `CROSSBOW`,
`FISHING_ROD`, `SHEARS`, `SHIELD`, `ELYTRA`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`,
`HORSE_ARMOR`, `PET`, `ACCESSORY`, `BACKPACK`, `ALL`, `NONE`. `ALL` matches every item type. Unknown
entries are silently ignored at load time.

---

## Configuration Reference

### Schema

```yaml
<enchant-id>:
  name: "<display name>"
  description:
    - "<MiniMessage line>"
  targets: [SWORD]
  conflicts: ["other_enchant_id"]
  etable-max-level: 5
  absolute-max-level: 10

  logic: "<logic_key>"          # optional — legacy Java hook, see the design doc
  logic-params: { }

  variables:
    <name>: "<$level$-scoped formula>"

  combat:
    modify-attack:
      conditions: ["<condition>"]
      modifiers:
        damage-multiplier: "<formula>"
        crit-chance: "<formula>"
        crit-damage: "<formula>"
        defense-shred-percent: "<formula>"
        knockback-multiplier: "<formula>"
    modify-defend:
      conditions: ["<condition>"]
      modifiers:
        damage-multiplier: "<formula>"
        damage-reduction-percent: "<formula>"
        knockback-multiplier: "<formula>"

  triggers:
    ON_ATTACK_POST: # or ON_DEFEND_POST / ON_KILL
      conditions: ["<condition>"]
      actions: ["<event>"]
      fail-actions: ["<event>"]

  state:
    transient:
      <key>: { type: HIT_COUNTER, reset-after-seconds: N, reset-on-target-switch: bool, max-stacks: N }
    persistent:
      <key>: { type: INTEGER, default: N }

  stats:
    <statId>: "<$level$-scoped formula>"
```

### Field reference

| Field | Required | Default | Notes |
|---|---|---|---|
| `<enchant-id>` (top-level key) | **Yes** | — | Unique across all `enchants/*.yml`. |
| `name` | No | the enchant ID | Display name in GUIs and lore. |
| `description` | No | `[]` | MiniMessage lore lines (shown only under 4 total enchants). |
| `targets` | **Yes** | — | See the item-type list above. |
| `conflicts` | No | `[]` | Enforced only by the anvil merge. |
| `etable-max-level` | No | `5` | Ceiling for the Enchanting Table path. |
| `absolute-max-level` | No | `10` | Ceiling for `/item enchant` and non-book anvil merges. |
| `logic` / `logic-params` | No | — | Legacy Java hook — see the design doc's logic table. |
| `variables` | No | `{}` | `$level$`-scoped formulas, evaluated once per dispatch. |
| `combat.modify-attack` / `modify-defend` | No | none | Pre-hit numeric modifiers. |
| `triggers.<TRIGGER>` | No | none | Post-hit/kill event dispatch. |
| `state.transient` / `state.persistent` | No | none | Per-attacker or per-item counters. |
| `stats` | No | `{}` | Additive stat bonuses. |

An unknown `logic:` id, unknown trigger name, or unknown `state:` `type:` logs a warning at load time
and is skipped — the rest of that enchant (and every other enchant) still loads normally.

---

## Migrating an Old-Style Enchant

If you have an enchant that only uses `logic: valmora:stat_bonus` (or `damage_multiplier`/
`defense_reduction`), you can usually drop the Java hook entirely:

```yaml
# Before
old_strength:
  logic: "valmora:stat_bonus"
  logic-params: { stat: "strength", per-level: 2.0 }

# After — identical effect, no logic: at all
new_strength:
  stats:
    strength: "2 * $level$"
```

You are never required to migrate — `logic:` keeps working forever, and a hybrid enchant (both
`logic:` and `stats:`/`combat:`/`triggers:`) is fully supported if you only want to move part of an
enchant's behavior to YAML.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Enchant shows in lore but has no effect | The `logic:` key is unregistered, or it has none of `stats:`/`combat:`/`triggers:` either — check the server console for a load-time warning. |
| `/item enchant` says "cannot be applied to this item" | Your item's type isn't in the enchant's `targets` list, or it's not a Valmora item (vanilla items can't take type-checked enchants outside the anvil). |
| The enchanting table won't let me pick a level | You're requesting above `etable-max-level`, or you don't have enough XP levels for `EtableCostCalculator`'s cost (2 XP per level requested). |
| A combo/stacking counter isn't resetting when I expect | Check `reset-after-seconds`/`reset-on-target-switch` on that `state.transient` key — transient counters are also always lost on `/valmora reload` and server restart, by design. |
| Enchant lore repeats on re-enchant | Should not happen — lore is rebuilt from a stored base-lore snapshot on every enchant/remove. |
