# Block Loot Overrides — User Documentation

> **Module ID:** `block_loot` | **Display name:** "Block Loot Overrides"
> **Server:** Paper 1.21.x | **Config lives in:** `plugins/Valmora/block_loot/*.yml`

---

## Table of Contents

1. [Overview](#1-overview)
2. [How This Differs From a Zone's `resource-blocks:`](#2-how-this-differs-from-a-zones-resource-blocks)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)
5. [Config.yml Settings](#5-configyml-settings)

---

## 1. Overview

Block Loot Overrides let you change what a **vanilla block type drops, everywhere on the server**,
independent of any zone. Configure it once — "every `STONE` block drops X instead of cobblestone"
— and it applies globally: in the wilderness, in a player's base, inside or outside any zone.

Every drop from a configured block is a **fully Valmora-formatted item**: resolved through the
same item system as anything else in the plugin, with correct item type, rarity coloring, and
lore — not a raw vanilla `ItemStack`.

By default this module ships with **zero active overrides** — installing/updating the plugin
changes no vanilla block behavior until you write your own `block_loot/*.yml` file (see the
shipped `example.yml`, which is fully commented out).

---

## 2. How This Differs From a Zone's `resource-blocks:`

If you've used the **Resource module**'s zone-scoped `resource-blocks:` (see
`docs/modules/user/resource.md`), this looks similar but solves a different problem:

| | Block Loot Overrides (this module) | Zone `resource-blocks:` |
|---|---|---|
| **Where it applies** | Everywhere, server-wide | Only inside the specific zone that configures it |
| **Progression** | None — the block just breaks, once, like vanilla | Multi-stage (e.g. ore → cobblestone → bedrock) |
| **Regeneration** | None — a broken block is gone, exactly like vanilla | Times out and respawns automatically |
| **Tool gating** | None | Optional minimum Breaking Power |
| **Use it when...** | You want a plain material to always drop something else, everywhere | You want a curated, farmable mining node inside a specific area |

**If both are configured for the same material and a block sits inside that zone, the zone's
`resource-blocks:` config always wins.** Block Loot Overrides only ever apply where nothing more
specific already claims the block.

---

## 3. Admin Guide

1. Create (or edit) a file under `plugins/Valmora/block_loot/` — any filename ending in `.yml`,
   any number of override entries per file.
2. Each top-level key in the file is your own arbitrary label for the entry — pick anything
   readable (`stone_override`, `my_dirt_drop`, etc.). **It is not the material name.**
3. Set `material:` to the vanilla block type this entry overrides (case-insensitive).
4. List one or more `drops:` — each rolled independently when the block breaks.
5. Run `/valmora reload` to apply changes without restarting.

There is no in-game command for adding/removing overrides — this is pure-config content, the same
as items, mobs, or recipes.

### Example: make every `DIRT` block drop 1–2 gold nuggets, 20% of the time

```yaml
dirt_gold_chance:
  material: DIRT
  drops:
    - item: GOLD_NUGGET
      min: 1
      max: 2
      chance: 0.2
```

A block with no matching `chance` roll on any entry simply drops nothing (not vanilla dirt) — the
override fully replaces vanilla loot, it doesn't add to it.

### Silk Touch

A tool with Silk Touch always yields exactly **1 of the block's own vanilla material** instead of
the configured drop table — this matches vanilla Silk Touch behavior (and Fortune is ignored on a
Silk Touch break, also matching vanilla). Turn this off entirely with
`block-loot.silk-touch.enabled: false` in `config.yml` if you'd rather Silk Touch also pull from
the configured table (not currently configurable per-block — see the design doc's "possible
improvements" if you need that).

### Mining Fortune

Rolled drop amounts (from the `drops:` list, not the Silk Touch case) are scaled up by the
breaking player's **Mining Fortune** stat, the same way a zone's resource-blocks are — a higher
Mining Fortune never reduces the amount, only ever increases or leaves it unchanged.

---

## 4. Configuration Reference

### Full schema (with defaults)

```yaml
<your-own-id>:                    # arbitrary label — NOT the material name
  material: STONE                  # REQUIRED — vanilla Material name, case-insensitive
  drops:                           # list of independently-rolled drop entries
    - item: iron_dust              # REQUIRED — a Valmora item id OR a vanilla material name
      min: 1                       # default 1
      max: 1                       # default 1
      chance: 1.0                  # default 1.0 (always rolls)
```

| Key | Default | Meaning |
|---|---|---|
| `<your-own-id>` | — | An arbitrary label for this entry. Not used for lookups — only `material:` determines which block type it applies to. |
| `material` | *(none — required)* | The vanilla block type this entry overrides. Unknown/missing → the whole entry fails to load (check the server log on `/valmora reload`). |
| `drops` | `[]` | List of drop entries. Each is rolled **independently** (a block can drop 0, 1, or several entries from one break). |
| `drops[].item` | *(none — required)* | A Valmora item id (from `items/*.yml`) or a vanilla material name. Falls back to the vanilla material's own Valmora-translated formatting (type/rarity/lore) if it isn't a custom item. |
| `drops[].min` / `drops[].max` | `1` / `1` | Inclusive amount range rolled per drop, before Mining Fortune scaling. |
| `drops[].chance` | `1.0` | Independent roll probability (`0.0`–`1.0`) for this specific entry. |

**Two entries (in the same or different files) targeting the same `material:`** — the first one
loaded wins; the second is ignored with a warning in the server log. Give each material only one
entry.

---

## 5. Config.yml Settings

```yaml
block-loot:
  enabled: true            # master switch — false disables the whole module (no YAML parsed either)
  silk-touch:
    enabled: true           # Silk Touch yields 1 of the block's own material instead of the table
```
