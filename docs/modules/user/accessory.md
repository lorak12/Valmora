# Accessory Module — User Documentation

> **Version:** 0.1 | **Server:** Paper 1.21.x | **Command:** `/accessories`

---

## Overview

The Accessory module gives every profile a dedicated **Accessory Bag** — a 45-slot inventory that
follows your character like an extra equipment page. Accessory items are collectible items that you
place into this bag to permanently boost your stats.

The key idea is that **everything in the bag is always active**. You don't have to hold an accessory
or wear it in a gear slot — if it's sitting anywhere in the 45-slot bag, its stat bonuses apply to
your character at all times. Fill the bag with as many accessories as you can collect.

Accessory items are ordinary Valmora items with the special `item-type: ACCESSORY` tag. Only items
tagged as accessories can be placed in the bag; everything else is blocked at the click.

---

## Player Guide

### Opening your Accessory Bag

Run the command:

```
/accessories
```

Your Accessory Bag opens — a 45-slot inventory titled **✦ Accessory Bag** (`plugin.yml:64-66`,
`AccessoryModule.java:47-63`). It has no player-inventory area of its own; the bag is the whole
window.

### Placing and removing accessories

- **Place:** Pick up an accessory item from your inventory and click it into a bag slot.
- **Remove:** Click an accessory in the bag to pick it up and move it back to your inventory.
- **Guard:** The bag refuses items that are **not** accessory items — if you try to place a normal
  item, the click is cancelled (`AccessoryListener.java:25-33`).
- **Save:** The bag saves automatically the moment you close the window
  (`AccessoryListener.java:18-23`). Every time you close it, your character's stats are
  recalculated so your new accessory setup takes effect instantly
  (`AccessoryModule.java:76-79`).

### How bonuses work

- Every accessory in the bag contributes its `stats` to your character **at the same time** — all
  45 slots count as equipped simultaneously. There are no "equipped vs. stored" slots
  (`StatManager.java:143-155`).
- Accessory bonuses are applied on top of your base stats whenever stats are recalculated
  (bag close, profile switch, join, etc.).
- Like all stats in Valmora, an accessory bonus cannot push a stat above that stat's defined
  maximum value (`StatManager.java:173-178`).
- Accessory stats are **not** permanent points — remove the accessory from the bag and the bonus
  disappears. Your base stats are unchanged.

### Per-profile storage

Accessories are stored **per profile**, so each of your profiles (created with the profile system)
has its own independent 45-slot bag. Switching profiles swaps to that profile's accessories.

> **Important known limitation:** Accessory bag contents are currently kept in memory only and are
> **not** saved to the database. They survive while you are online and across profile switches, but
> they will be lost on server restart and on `/valmora reload`. See the Admin Guide below.

---

## Admin Guide

### Defining accessory items

Accessory items are defined exactly like any other Valmora item in a file under
`plugins/Valmora/items/`. The only requirement is the `item-type: ACCESSORY` field. The item's
`stats` block is what the player gains while the accessory sits in the bag.

Example — `plugins/Valmora/items/accessories.yml`:

```yaml
lucky_charm:
  name: "<yellow>Lucky Charm"
  material: "GOLD_INGOT"
  rarity: "RARE"
  item-type: "ACCESSORY"
  lore:
    - "<gray>Legend says this charm brings"
    - "<gray>fortune to its owner."
  stats:
    LUCK: 10
    HEALTH: 20

speed_scarab:
  name: "<aqua>Speed Scarab"
  material: "EMERALD"
  rarity: "EPIC"
  item-type: "ACCESSORY"
  stats:
    SPEED: 15
```

After adding or editing a file, apply the changes with `/valmora reload` (requires
`valmora.admin`). Item reloading follows the standard item-module flow; the accessory bag itself has
no definitions to reload — it reads whatever your items define.

### Giving accessories to players

Use the standard item command (requires `valmora.admin`, `plugin.yml:16`):

```
/item give lucky_charm
/item give lucky_charm 5
/item give lucky_charm 5 <player>
```

The player then opens their bag with `/accessories` and clicks the accessory in
(`ItemCommand.java:54-104`).

### Permissions

| Permission | Effect |
|---|---|
| *(none)* | `/accessories` has **no** permission node — every player can open their own bag (`plugin.yml:64-66`). |
| `valmora.admin` | Required for `/item give` (distributing accessory items) and `/valmora reload` (`plugin.yml:16`, `:57-60`). |

### Validation behaviour

Only items whose PersistentData tag `item_type` equals `ACCESSORY` can enter the bag
(`AccessoryModule.java:87-92`). The tag is written automatically by the item factory whenever a
definition with `item-type: ACCESSORY` is created (`ItemFactory.java:33-35`). Vanilla/untagged
items are rejected on click.

> **Known issue for admins:** a player Shift-clicking a non-accessory item into the bag is not
> currently blocked — the bag only rejects non-accessories being dragged or clicked in via the
> cursor. Test your server's Shift-click behaviour if strict item-locking matters to you.

### Persistence warning (read before deploying)

At the time of writing, the accessory bag is **not persisted to the database**:

- There is no `accessory` column in the `valmora_profiles` table
  (`SQLDataStore.java:124-144`, `:117-120`).
- `savePlayer`/`loadPlayer` serialize inventory and quiver contents, but not the bag
  (`SQLDataStore.java:286-312`, `:241-249`).

In practice: a player's bag contents will be **lost on server restart, on player quit-and-rejoin
after a restart, and on `/valmora reload`**. They survive only while the player's session stays
loaded in memory (including profile switches). Do not hand out rare accessories expecting them to
persist until this is fixed.

### Reload behaviour

`/valmora reload` (permission `valmora.admin`, `Valmora.java:232`, `plugin.yml:57-60`) runs the
standard module disable/enable cycle. The accessory module itself re-registers its single listener
cleanly, but because profiles are re-loaded from the database, current bag contents are dropped for
online players (see the persistence warning above).

---

## Configuration Reference

### Module config

**The accessory module has no dedicated YAML configuration.** There is no `accessories/` folder,
no section in `config.yml`, no permission setting, and no per-bag options. The following are
hardcoded in `AccessoryModule.java`:

| Setting | Value | Where |
|---|---|---|
| Bag size | **45 slots** | `ACCESSORY_SLOTS = 45`, `AccessoryModule.java:18` |
| Bag title | `<dark_gray>✦ Accessory Bag` | `AccessoryModule.java:51` |
| Open command | `/accessories` (player-only) | `Valmora.java:250-254`, `plugin.yml:64-66` |
| Identity tag | PDC `valmora:item_type` = `ACCESSORY` | `Keys.java:45`, `AccessoryModule.java:87-92` |

### Item schema fields relevant to accessories

Defined in `plugins/Valmora/items/*.yml` (see `docs/VALMORA_DOCUMENTATION.md` §23 for the full
schema). These are the fields that matter for an accessory:

| Field | Required | Notes |
|---|---|---|
| `material` | Yes | Any Bukkit `Material` (e.g., `GOLD_INGOT`, `EMERALD`, `PLAYER_HEAD`). |
| `name` | Recommended | Display name; supports MiniMessage. |
| `item-type` | Yes (for accessories) | Must be `ACCESSORY` — this is what makes it a bag item (`ItemDefinitionParser.java:44-52`). |
| `rarity` | No | `COMMON` … `MYTHIC`. Default `COMMON`. |
| `stats` | No | The bonuses applied while the accessory is in the bag (`StatManager.java:148-152`). Keys must match registered stat IDs. |
| `lore` | No | Extra description lines. Supports MiniMessage. |
| `abilities` | No | Standard item abilities. Note: `PASSIVE` abilities on bagged accessories are **not** currently executed — only the `stats` block takes effect while an accessory sits in the bag (`StatManager.java:146-154`). |

### Database

No accessory-specific tables exist. Persistence is the known gap documented above; the relevant
code lives in `database/SQLDataStore.java` (profile upsert at `:286-312`, profile load at
`:199-251`, quiver migration at `:117-120`).
