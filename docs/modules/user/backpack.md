# Backpack Module — User Documentation

> Module ID: `backpacks` | Version: 0.1 | Paper 1.21.x | Java 21

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The Backpack module adds **portable storage bags** to Valmora. A backpack is a custom item that you hold in your hand and **right-click** to open like a chest. Anything you place inside is saved **onto the backpack item itself**, so the storage travels with the item — you can carry it, trade it, store it in a chest, or drop it, and the contents go with it.

Because the contents are stored on the item and not on your character, backpacks are **shared storage**: anyone who holds a backpack can open it and see everything inside. There is no "locked to owner" mechanic.

The backpack GUI has a fixed capacity (currently always 27 slots in this build — see [Configuration Reference](#configuration-reference)) and it is **not possible to place a backpack inside another backpack** (nesting is blocked).

Backpacks are not a built-in item — they must be **defined as custom items** by a server admin (see [Admin Guide](#admin-guide)). Nothing ships in the default `items/` folder.

---

## Player Guide

### How to open a backpack

1. Hold the backpack item in your **main hand** (off-hand opening is not supported).
2. **Right-click** while holding it.
3. A chest-style GUI titled `🎒 Backpack` opens.
4. Put items in or take items out like a normal chest.

### How to close / save

Just close the GUI (press `E` or click outside). The contents are **saved when you close the GUI** — nothing is written while the GUI is open, so close it before you move the backpack around.

### What you can and can't do

| Action | Result |
|---|---|
| Store any normal item in a backpack | Allowed |
| Store a backpack inside a backpack (drag/click placement) | **Blocked** — click is cancelled and you see `<red>You cannot place a backpack inside another backpack.` |
| Store a backpack inside a backpack (shift-click) | Currently possible (known limitation, see Admin Guide) |
| Open a backpack dropped on the ground / in a chest | Yes — pick it up and right-click it |
| Open someone else's backpack | Yes — backpacks have no owner; anyone holding the item sees its contents |
| Lose the backpack | You lose its contents too — they are stored on the item |

> **Tip:** if a backpack does not open when you right-click it, the server likely defined it with a `target-range` on the ability, so it only opens when looking at an entity — ask the admin to check the item config (see Admin Guide).

### Commands

There is **no player-facing backpack command** (no `/backpack`). Opening is done entirely by right-clicking the item.

---

## Admin Guide

### 1. Define a backpack item

Backpacks are defined exactly like any other Valmora item in `plugins/Valmora/items/*.yml` (see the item schema in `docs/VALMORA_DOCUMENTATION.md:983`). Two things make an item a backpack:

1. **`item-type: BACKPACK`** — this is the marker the module checks to recognise the item as a backpack (`BackpackModule.java:92-97`). The `BACKPACK` value is a valid `ItemType` (`ItemType.java:26`).
2. **A `RIGHT_CLICK` ability whose `mechanics` list contains `type: "OPEN_BACKPACK"`** — this is what actually opens the GUI (`BackpackMechanic.java:17`).

#### Minimal working backpack

```yaml
# plugins/Valmora/items/backpacks.yml
leather_backpack:
  name: "Leather Backpack"
  material: LEATHER
  rarity: COMMON
  item-type: BACKPACK
  abilities:
    open:
      name: "Open Backpack"
      trigger: "RIGHT_CLICK"
      target-range: 0
      mechanics:
        - type: "OPEN_BACKPACK"
```

Notes on this example:

- **`target-range: 0` is important.** If it is greater than `0`, the ability engine requires a living entity in your line of sight and aborts with a "No target in range!" message otherwise — the backpack would not open when right-clicking air (`AbilityExecutor.java:45-51`). Leaving it out is also fine (defaults to `0`).
- The backpack must be a **registered custom item** (it needs `material`, and the engine only reacts to items that carry a Valmora item ID — `AbilityListener.java:42-49`).
- A backpack must be opened from the **main hand** only.

#### Optional ability gates

You can add `cooldown` (seconds) and/or `mana-cost` to control how often and whether a backpack can be opened, same as any ability (`AbilityExecutor.java:58-76`):

```yaml
    open:
      name: "Open Backpack"
      trigger: "RIGHT_CLICK"
      cooldown: 1.0
      mana-cost: 0
      mechanics:
        - type: "OPEN_BACKPACK"
```

### 2. Give backpacks to players

Backpacks are ordinary custom items, so use the existing item command:

```
/item give leather_backpack
/item give leather_backpack 1 <player>
/item list
```

(`/item` requires the `valmora.admin` permission — see `src/main/resources/plugin.yml:14-17`.)

### 3. Reload

Item config changes are picked up with `/item reload` or by reloading the whole engine with `/valmora reload` (requires `valmora.admin`).

### 4. Known limitations to plan around

- **Capacity is fixed at 27 slots in this build.** The code reads a `backpack_size` value from the item (`BackpackModule.java:85-90`) but nothing ever writes that value, so no matter what you define in YAML, every backpack opens with 27 slots. The internal cap is 54.
- **Nesting can be bypassed with shift-click.** The anti-nesting guard only checks the item held on your cursor (`BackpackListener.java:36`). Shift-clicking a backpack from your inventory into an open backpack is not blocked.
- **No permission system.** Any player holding a backpack can open and modify its contents.
- **Save happens on GUI close only.** If the server restarts while a backpack GUI is open, the changes since the last close are lost.
- **No example files ship.** You must author your own `items/*.yml` entry as shown above.

### 5. Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Backpack does nothing on right-click | Item lacks `item-type: BACKPACK`, lacks the `OPEN_BACKPACK` mechanic, or is not a registered item (no `material` / not reloaded). |
| "No target in range!" when right-clicking | `target-range` is `> 0` on the open ability — set it to `0` or omit it. |
| Backpack opens but items vanish after close | Contents are stored on the item; if the item was replaced/duplicated, data may be on the wrong copy. Re-test with a fresh item. |
| Ability listed in `/item info` but mechanic missing | `OPEN_BACKPACK` is only registered while the backpack module is enabled. After reloading only the ability module, run `/valmora reload`. |

---

## Configuration Reference

### Module-level config

The backpack module has **no YAML configuration file** of its own. There is no `backpacks/` folder, no `backpack:` section in `config.yml`, and no default backpack items. All behaviour is driven by the **item definition** you write in `plugins/Valmora/items/*.yml`.

### Item YAML keys relevant to backpacks

| Key | Type | Default | Description |
|---|---|---|---|
| `item-type` | String | `NONE` | Set to `BACKPACK` so the module recognises the item as a backpack. Stored on the item as PDC `item_type` (`ItemFactory.java:33-35`). |
| `abilities.<id>.trigger` | String | — | Must be `RIGHT_CLICK` to open on right-click. |
| `abilities.<id>.target-range` | Double | `0.0` | Keep at `0` so opening does not require looking at an entity. |
| `abilities.<id>.cooldown` | Double | `0.0` | Optional gate: seconds between openings. |
| `abilities.<id>.mana-cost` | Double | `0.0` | Optional gate: mana spent per opening. |
| `abilities.<id>.mechanics[].type` | String | — | `"OPEN_BACKPACK"` — the mechanic that opens the GUI. |
| `backpack-size` | — | — | **Not implemented.** No YAML key writes the backpack capacity; the capacity is always 27 slots in this build. |

### Internal data (PDC tags) — for reference

These are written onto the backpack item itself; you generally don't touch them:

| PDC tag | Type | Purpose |
|---|---|---|
| `valmora:item_type` | STRING | Set to `BACKPACK` on item creation (`ItemFactory.java:33-35`). |
| `valmora:valmora_item_id` | STRING | Links the item to its definition (required for the right-click ability to fire). |
| `valmora:backpack_contents` | BYTE_ARRAY | The serialized contents of the backpack, written when the GUI is closed (`BackpackModule.java:69-83`). |
| `valmora:backpack_size` | INTEGER | Reserved for backpack capacity; currently never written by any code, so it has no effect (`Keys.java:76`, `BackpackModule.java:85-90`). |
