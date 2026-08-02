# Progression Module — User Documentation

> **Module ID:** `progression` | **Display name:** "Progression System" | **Version:** 0.1
> **Server:** Paper 1.21.x | **Config lives in:** `plugins/Valmora/progression/*.yml`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

The **Progression System** is Valmora's **skill-tree / skill-point engine**. Server admins define *progression trees* in YAML; players spend **points** to:

- **unlock tiers** of a tree (whole groups of nodes open up), and
- **level up nodes** inside an unlocked tier, stacking permanent bonuses (stats or daily rewards).

Each tree has **two currencies** (both are points categories — see the Points system): a *level currency* spent on node level-ups, and a *tier currency* spent on tier unlocks. A single tree is a single `.yml` file — adding a new tree needs **no plugin code**.

The module ships with **one** tree: **Geomancy** (`plugins/Valmora/progression/geomancy.yml`), the mining progression tree for the Shardworks demo server. It is opened with **`/geomancy`**.

> **Related systems:** points are managed by the **Points module** (free-form per-player counters, rewarded by quests and scripts). The stat bonuses Geomancy grants feed the **stats** system and power the **Resource** mining engine.

---

## 2. Player Guide

### 2.1 Opening the tree

Type **`/geomancy`**. A 6-row GUI opens showing the Geomancy tree: each node is an icon, the `T` button unlocks the next tier, and `R` resets the whole tree.

### 2.2 How the Geomancy tree is laid out

| Tier | Unlock cost | What it contains |
| --- | --- | --- |
| 0 — Novice | free | **Steady Hands** (Mining Speed) |
| 1 — Adept | 3 Geomancy Tokens | **Rich Veins** (Mining Fortune), **Wide Excavation** (Mining Spread) |
| 2 — Master | 5 Geomancy Tokens | **Deep Prospecting** (Mining Fortune), **Ferrite Cache** (daily Ferrite Powder), **Overflowing Veins** (Mining Fortune) |
| 3 — Ascendant | 8 Geomancy Tokens | **Aetherial Resonance** (Mining Spread) |

### 2.3 Earning points

Points come from **quest rewards**. The Shardworks quests grant:

- **Ferrite Powder** — the Geomancy *level currency* (e.g. `+40`/`+60`/`+80`/`+100` per quest; see `quests/shardworks_quests.yml`).
- **Geomancy Tokens** — the Geomancy *tier currency* (`+1`/`+2` per quest).

Your balances are visible in the tree GUI (the "available" state compares your Ferrite Powder against the next level's cost; the `T` button compares your Geomancy Tokens against the next tier's cost).

### 2.4 Unlocking tiers

Click the **`T` (END_CRYSTAL) button** at the bottom of the GUI. Each tier costs a fixed amount of **Geomancy Tokens** (`3`, `5`, `8`). Tier 0 ("Novice") is already unlocked. Nodes in a tier stay locked until that tier is unlocked.

### 2.5 Levelling nodes

Click a node's icon. Each click **pays the current cost in Ferrite Powder** and raises the node by one level. The cost is **not** flat — it grows with the level (`floor(5 * pow(1.12, level))` for Steady Hands, etc.), so deep levels get expensive.

Each node has a `max-level`; once reached it shows **(MAX)** and can't be levelled further. Node states in the GUI:

| State | Meaning |
| --- | --- |
| **Available** (colored icon, "Click to upgrade!") | You have enough Ferrite Powder. |
| **Locked** (gray dye) | You don't have enough Ferrite Powder. |
| **Tier-locked** (barrier) | Unlock the node's tier first. |
| **Prereq-locked** (barrier) | Level up the required node to level 1 first. |

### 2.6 What nodes do

- **Stat nodes** grant a passive stat bonus **per level**. For example:
  - Steady Hands → `+4 Mining Speed` per level (max 10 → up to +40).
  - Rich Veins / Deep Prospecting / Overflowing Veins → `+3` / `+5` / `+4` **Mining Fortune** per level.
  - Wide Excavation / Aetherial Resonance → `+1` **Mining Spread** per level.

  These stack with your other stat sources and are reapplied automatically on every stat recalculation — equip/unequip gear and they still apply.
- **Daily-bonus nodes** grant currency once per **rolling 24 hours** per level. The **Ferrite Cache** node (`+25 Ferrite Powder` per level, max 5) is checked automatically every 5 minutes while you're online — no need to click anything; the grant happens when 24 h have passed since your last claim.

### 2.7 Resetting

Click the **`R` (BARRIER) button**, then click it **again** to confirm (first click arms a confirm flag). A reset:

- refunds **100% of every Ferrite Powder and Geomancy Token you ever spent** in this tree, and
- zeroes every node level and tier back to 0 / Novice.

So it's safe to experiment — respeccing costs nothing. Your stat bonuses disappear the moment nodes hit level 0 (they're derived from level, not stored).

---

## 3. Admin Guide

### 3.1 Where trees live

Every file in `plugins/Valmora/progression/` is loaded at startup (and on `/valmora reload`). One file = one tree; the file's top-level key is the tree id. Files are auto-saved from the plugin jar on first run (`progression/` is in the auto-save list), but existing files are **never overwritten**, so server edits persist.

Validation happens at load: if a tree is missing `level-currency` or `tier-currency`, the whole tree fails to load and the reason is printed to the console ("Failed to load some Progression Trees"). Bad tiers (non-integer keys) and bad node `icon` names are skipped/fallback silently.

### 3.2 Adding a new tree

Copy `geomancy.yml` as a template, or write a minimal tree:

```yaml
my_tree:
  name: "<green>My Tree"
  description: "An example tree."
  level-currency: my_points
  tier-currency: my_tokens

  tiers:
    0:
      name: "Initiate"
      unlock-cost: 0
      nodes: [basic_node]
    1:
      name: "Adept"
      unlock-cost: 10
      nodes: [advanced_node]

  nodes:
    basic_node:
      name: "<white>Basic Node"
      description: "Grants a stat."
      icon: STONE
      tier: 0
      max-level: 5
      cost-curve: "floor(4 * pow(1.1, $level$))"
      stat-bonus:
        stat: damage
        per-level: 2

    advanced_node:
      name: "<gold>Advanced Node"
      description: "Needs the basic node first."
      icon: DIAMOND
      tier: 1
      max-level: 3
      prerequisites: [basic_node]
      cost-curve: "floor(10 * pow(1.2, $level$))"
      daily-bonus:
        category: my_points
        per-level: 50
```

Run `/valmora reload`. If the tree parsed, the console logs "Successfully loaded N Progression Trees." **There is no tree-list or management command** — the tree is only playable if you give players a GUI for it (see §3.4).

### 3.3 Wiring up the currencies

The `level-currency` and `tier-currency` are **points categories**. You must give players points in those categories or the tree is unreachable. Options:

- **Quest rewards:** in a quest's reward script, `point <category> add <amount>` (see `quests/shardworks_quests.yml` for working examples).
- **Scripts / GUI events / NPC actions:** same `point <category> add <amount>` event.
- **Points variable:** show balances in GUI lore with `$point.<category>$`.

### 3.4 Giving players a GUI

The module has **no built-in GUI renderer** — the Geomancy tree browser is a normal GUI-module file, `plugins/Valmora/guis/geomancy_tree.yml`. If you add your own tree you must either:

- add your own `guis/<your_tree>.yml` that renders it (the file can declare `command: mytree` to register `/mytree`), or
- reuse the existing Geomancy file pattern for your tree.

The GUI reads progression state through script variables and triggers actions through DSL events:

| Script variable | Meaning |
| --- | --- |
| `$progression.<tree>.tier$` | Current unlocked tier. |
| `$progression.<tree>.tier.next.unlock_cost$` | Cost of the next tier. |
| `$progression.<tree>.<node>.level$` | Current node level. |
| `$progression.<tree>.<node>.max_level$` | Node's max level. |
| `$progression.<tree>.<node>.next_cost$` | Cost of the next level. |
| `$progression.<tree>.<node>.unlocked$` | Whether the node is unlocked (`true`/`false`). |

| DSL event | Effect |
| --- | --- |
| `progression_levelup <treeId> <nodeId>` | Pay the level cost and level the node up once. |
| `progression_unlock_tier <treeId>` | Pay the tier cost and unlock the next tier. |
| `progression_reset <treeId>` | Refund everything and reset the tree. |

Example from the Geomancy GUI (state-selected actions):

```yaml
available:
  condition: "$point.ferrite_powder$ >= $progression.geomancy.mining_speed_root.next_cost$"
  display-item:
    material: IRON_PICKAXE
    name: "<white>Steady Hands"
    lore:
      - "<gray>Cost: <aqua>$progression.geomancy.mining_speed_root.next_cost$ Ferrite Powder"
  actions:
    left:
      actions: ["progression_levelup geomancy mining_speed_root"]
```

### 3.5 Permissions

There are **no progression-specific permissions**. Access is whatever you configure on the tree's GUI:

- If the GUI declares `command-permission:`, players need that permission to run the command.
- The `geomancy_tree.yml` GUI ships with `command: geomancy` and **no** `command-permission`, so any player can open it.

Reset confirmation is handled by the GUI (two clicks), not by a permission.

### 3.6 Integration points for developers

- **Custom Bukkit events:** `ProgressionNodeLevelUpEvent`, `ProgressionTierUnlockedEvent`, `ProgressionTreeResetEvent` fire after each mutation — other plugins can listen and react (announcements, effects, etc.).
- **Stats:** stat-bonus nodes are applied automatically during every stat recalculation. No extra setup.
- **Daily bonuses:** granted automatically by a 5-minute main-thread task while the player is online. The window is a **rolling 24 h** from the player's last claim — not a midnight reset.

---

## 4. Configuration Reference

### 4.1 Tree-level (`<treeId>:`)

| Key | Type | Default | Required | Description |
| --- | --- | --- | --- | --- |
| `name` | string (MiniMessage) | tree id | no | Display name, e.g. `<gold>Geomancy`. |
| `description` | string | `""` | no | Free-form description of the tree. |
| `level-currency` | string | — | **yes** | Points category deducted for every node level-up. Missing ⇒ tree fails to load. |
| `tier-currency` | string | — | **yes** | Points category deducted for every tier unlock. Missing ⇒ tree fails to load. |
| `tiers` | map | absent | no | Tier definitions, keyed by integer index. |
| `nodes` | map | absent | no | Node definitions, keyed by node id. |

### 4.2 Tier (`tiers.<index>:`)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | string | `"Tier <index>"` | Tier display name. |
| `unlock-cost` | int | `0` | Amount of `tier-currency` to unlock this tier. |
| `nodes` | list of string | absent | Node ids in this tier (informational). |

### 4.3 Node (`nodes.<nodeId>:`)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | string (MiniMessage) | node id | Node display name. |
| `description` | string | `""` | Node description. |
| `icon` | Material name | `BOOK` | GUI icon; unparseable names fall back to `BOOK`. |
| `tier` | int | `0` | Tier that must be unlocked before this node can be levelled. |
| `max-level` | int | `1` | Maximum node level. |
| `cost-curve` | string expression | `"1"` | Cost of the *next* level. Use `$level$` for the current level. Supported math: `floor`, `ceil`, `round`, `abs`, `sqrt`, `log`, `log10`, `pow`, `min`, `max`. Example: `floor(5 * pow(1.12, $level$))`. |
| `prerequisites` | list of string | absent | Node ids that must reach level 1 before this node unlocks. |
| `stat-bonus` | map | absent | Passive stat granted per level (see §4.4). |
| `daily-bonus` | map | absent | Currency granted once per rolling 24 h per level (see §4.5). |

### 4.4 Node `stat-bonus` (`stat-bonus:`)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `stat` | string | `""` | Stat id (must match a registered stat, e.g. `mining_fortune`, `damage`, `speed`). |
| `per-level` | double | `0.0` | Bonus per node level. Total = `per-level × level`. |

### 4.5 Node `daily-bonus` (`daily-bonus:`)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `category` | string | `""` | Points category granted. |
| `per-level` | double | `0.0` | Amount per level per claim. Grant = `round(per-level × level)`. |

### 4.6 Shipped default values — `geomancy.yml`

```
geomancy:
  name: "<gold>Geomancy"
  description: "Master the art of mining Shardworks' living stone."
  level-currency: ferrite_powder
  tier-currency: geomancy_tokens

  tiers:
    0:  Novice      unlock-cost: 0   nodes: [mining_speed_root]
    1:  Adept       unlock-cost: 3   nodes: [mining_fortune_vein, mining_spread_focus]
    2:  Master      unlock-cost: 5   nodes: [deep_prospecting, daily_ferrite_cache, mining_fortune_vein_ii]
    3:  Ascendant   unlock-cost: 8   nodes: [aetherial_resonance]

  nodes:
    mining_speed_root:        tier 0, max 10, cost floor(5 * pow(1.12, $level$)),        stat mining_speed   +4/lvl
    mining_fortune_vein:      tier 1, max 10, cost floor(8 * pow(1.15, $level$)),        stat mining_fortune +3/lvl
    mining_spread_focus:      tier 1, max  5, cost floor(10 * pow(1.2, $level$)),        stat mining_spread  +1/lvl
    deep_prospecting:         tier 2, max  5, cost floor(20 * pow(1.18, $level$)),       stat mining_fortune +5/lvl  (prereq: mining_fortune_vein)
    daily_ferrite_cache:      tier 2, max  5, cost floor(15 * pow(1.15, $level$)),       daily ferrite_powder +25/lvl
    mining_fortune_vein_ii:   tier 2, max 10, cost floor(25 * pow(1.15, $level$)),       stat mining_fortune +4/lvl  (prereq: mining_fortune_vein)
    aetherial_resonance:      tier 3, max  3, cost floor(50 * pow(1.25, $level$)),       stat mining_spread  +1/lvl  (prereq: mining_spread_focus)
```

### 4.7 Data you should know about

- All progression state (node levels, tier, spent totals, last daily claim) is stored per player in the **profile's variables** and persists across restarts automatically — no manual database setup.
- Resetting always refunds **100%** of the tree's two currencies.
- Daily bonuses are claimed on a **rolling 24-hour** clock (per player, per node) and checked every 5 minutes; be online within that window to collect.

---

_Last updated: see git history._
