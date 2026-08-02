# Accessory Module — User Documentation

> **Version:** 0.2 | **Server:** Paper 1.21.x | **Command:** `/accessories` (players), `/accessory` (admins)

---

## Overview

The Accessory module gives every profile a dedicated **Accessory Bag** — an always-active equipment
page that follows your character. Accessory items are collectible items that you place into this bag
to permanently boost your stats.

The key idea is that **everything in the bag is always active**. You don't have to hold an accessory
or wear it in a gear slot — if it's sitting in an unlocked bag slot, its stat bonuses apply to your
character at all times.

Accessory items are ordinary Valmora items with the special `item-type: ACCESSORY` tag. Only items
tagged as accessories can be placed in the bag; everything else is blocked at the click, including
shift-clicks from your own inventory.

### Slots grow over time

Unlike a fixed-size inventory, each profile has its own **unlocked slot count**:

- Every profile starts with a configurable number of slots (**25** by default).
- Admins can raise your unlocked count at any time, up to a server-wide cap (**90** by default).
- The bag window automatically resizes to fit however many slots you currently have — no fixed 45 or
  54 slot grid.
- If your unlocked count is larger than a single inventory window can show (more than 45 slots), the
  bag paginates: extra slots live on additional pages, reachable with in-GUI Previous/Next buttons.

Slots beyond your current unlocked count show as a locked, gray glass filler item you cannot interact
with — a visual preview of the extra space you can grow into.

---

## Player Guide

### Opening your Accessory Bag

Run the command:

```
/accessories
```

Your Accessory Bag opens on page 1. The window title shows your current slot count and page, e.g.
**✦ Accessory Bag · 25 slots · 1/1**.

### Layout

- The bag always reserves its **last row** for controls, regardless of how many item rows precede it.
- **◀ Previous Page** — bottom-left of the control row, only shown when you're not on page 1.
- **Close** (barrier icon) — bottom-middle of the control row. Also just press `Esc`/`E` as usual.
- **Next Page ▶** — bottom-right of the control row, only shown when there's another page.
- Item rows above the control row hold your unlocked slots; any leftover cells in the last item row
  (before the count reaches a full row of 9) show as locked gray panes.

### Placing and removing accessories

- **Place:** Pick up an accessory item from your inventory and click it into a bag slot.
- **Remove:** Click an accessory in the bag to pick it up and move it back to your inventory.
- **Guard:** The bag refuses items that are **not** accessory items — this is enforced on cursor
  clicks, drags, *and* shift-clicks in either direction.
- **Save:** The bag saves automatically the moment you close the window, switch pages, or your
  session ends. Every save recalculates your character's stats so your new accessory setup takes
  effect instantly.

### How bonuses work

- Every accessory across **all** of your unlocked slots contributes its `stats` to your character at
  the same time — there are no "equipped vs. stored" slots.
- Accessory bonuses are applied on top of your base stats whenever stats are recalculated (bag
  save, profile switch, join, etc.).
- Like all stats in Valmora, an accessory bonus cannot push a stat above that stat's defined maximum.
- Accessory stats are **not** permanent points — remove the accessory from the bag and the bonus
  disappears. Your base stats are unchanged.
- Only the item's `stats` block applies while it sits in the bag. `PASSIVE` abilities are **not**
  executed for bagged accessories (they only run for held/worn equipment) — accessories are
  intentionally stat-only.

### Per-profile storage

Accessories, and your unlocked slot count, are stored **per profile**, so each of your profiles has
its own independent bag and its own slot progression. Switching profiles swaps to that profile's
accessories. Bag contents and slot counts are saved to the database, so they survive restarts,
disconnects, and `/valmora reload`.

---

## Admin Guide

### Defining accessory items

Accessory items are defined exactly like any other Valmora item in a file under
`plugins/Valmora/items/`. The only requirement is the `item-type: ACCESSORY` field. The item's
`stats` block is what the player gains while the accessory sits in the bag.

Two example accessories ship out of the box in `items/accessories.yml`:

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

After adding or editing a file, apply the changes with `/valmora reload` (requires `valmora.admin`).

### Giving accessories to players

Use the standard item command (requires `valmora.admin`):

```
/item give lucky_charm
/item give lucky_charm 5
/item give lucky_charm 5 <player>
```

The player then opens their bag with `/accessories` and clicks the accessory in.

### Managing slot caps

New in this version — the `/accessory` admin command (requires `valmora.admin`, online players only):

| Command | Effect |
|---|---|
| `/accessory setcap <amount>` | Sets the **server-wide** ceiling that unlocked slots can be raised to. Persisted to `config.yml`. |
| `/accessory setslots <player> <amount>` | Sets a specific player's unlocked slot count outright (clamped to `[0, cap]`). |
| `/accessory addslots <player> <amount>` | Grants (or, with a negative amount, removes) unlocked slots relative to the player's current count. |
| `/accessory info <player>` | Shows a player's current unlocked slots vs. the server cap. |

Example: raise the server cap to 90, then grant a player 65 more slots on top of their starting 25:

```
/accessory setcap 90
/accessory addslots Notch 65
```

### Permissions

| Permission | Effect |
|---|---|
| *(none)* | `/accessories` has **no** permission node — every player can open their own bag. |
| `valmora.admin` | Required for `/item give`, `/accessory ...`, and `/valmora reload`. |

### Validation behaviour

Only items whose PersistentData tag `item_type` equals `ACCESSORY` can enter the bag. The tag is
written automatically by the item factory whenever a definition with `item-type: ACCESSORY` is
created. Vanilla/untagged items are rejected on click — including shift-clicks in both directions
(from the bag or from the player's own inventory) and cursor drags.

### Persistence

Accessory bag contents and unlocked slot counts are stored in the `valmora_profiles` table
(`accessory_items`, `accessory_slots` columns, added in schema v3) and are saved and loaded exactly
like the rest of a profile — on quit, on clean shutdown, and are restored on join and on
`/valmora reload`.

---

## Configuration Reference

### `config.yml`

```yaml
accessories:
  # Slots a fresh profile starts with (no accessory-slot save data yet).
  starting-slots: 25

  # Hard ceiling admins can raise a player's unlocked slots to, via
  # /accessory setslots|addslots. Also the absolute ceiling for /accessory setcap.
  max-slots-cap: 90
```

`/accessory setcap` updates `max-slots-cap` at runtime and persists it back to `config.yml`.

| Setting | Default | Meaning |
|---|---|---|
| Starting slots | **25** | Slot count a profile has until an admin changes it. |
| Server-wide slot cap | **90** | Highest value any profile's unlocked slots can reach. |
| Bag title | `<dark_gray>✦ Accessory Bag · <slots> slots · <page>/<total>` | Dynamic — reflects live slot count and page. |
| Open command | `/accessories` (player-only) | |
| Admin command | `/accessory setcap\|setslots\|addslots\|info` (`valmora.admin`) | |
| Identity tag | PDC `valmora:item_type` = `ACCESSORY` | |

### Item schema fields relevant to accessories

Defined in `plugins/Valmora/items/*.yml` (see `docs/VALMORA_DOCUMENTATION.md` §23 for the full
schema).

| Field | Required | Notes |
|---|---|---|
| `material` | Yes | Any Bukkit `Material`. |
| `name` | Recommended | Display name; supports MiniMessage. |
| `item-type` | Yes (for accessories) | Must be `ACCESSORY`. |
| `rarity` | No | `COMMON` … `MYTHIC`. Default `COMMON`. |
| `stats` | No | The bonuses applied while the accessory is in the bag. Keys must match registered stat IDs. |
| `lore` | No | Extra description lines. Supports MiniMessage. |
| `abilities` | No | Standard item abilities. Note: `PASSIVE` abilities on bagged accessories are **not** executed by design — only `stats` take effect while an accessory sits in the bag. |

### Database

The `valmora_profiles` table (schema v3+) carries two accessory-specific columns:

| Column | Type | Contents |
|---|---|---|
| `accessory_items` | `TEXT` | Base64-encoded item array (variable length — grows as a profile unlocks more slots). |
| `accessory_slots` | `INTEGER`, nullable | Unlocked slot count. `NULL` means "use the configured `starting-slots` default." |

---

## Scripting

The accessory system is reachable from the Script DSL (quest rewards, skill rewards, GUI event
blocks, item abilities) — useful for granting bonus slots as a quest/achievement reward instead of
(or in addition to) `/accessory` admin commands.

### Event: `accessory_slots`

```
accessory_slots add <amount>     # grants more unlocked slots
accessory_slots remove <amount>  # revokes unlocked slots
accessory_slots set <amount>     # sets the unlocked slot count outright
```

`<amount>` may be a literal number or a `$variable$` expression, resolved at execution time. All
three forms are clamped to `[0, accessories.max-slots-cap]` — the same clamp `/accessory` uses, so a
script can't accidentally push a player over the server cap.

Example — a quest reward that unlocks 5 extra slots:

```yaml
on-complete:
  - "accessory_slots add 5 notify"
  - "tag add unlocked_bonus_slots"
```

### Variables: `$player.accessories.*$`

| Variable | Type | Meaning |
|---|---|---|
| `$player.accessories$` | int | Same as `.unlocked` — the bare namespace defaults to the unlocked count. |
| `$player.accessories.unlocked$` / `.slots` | int | Current unlocked slot count. |
| `$player.accessories.cap$` / `.max` | int | Server-wide `max-slots-cap`. |
| `$player.accessories.used$` / `.count` / `.filled` | int | Number of unlocked slots that currently hold an accessory. |
| `$player.accessories.empty$` / `.free` | int | Unlocked slots with nothing in them. |
| `$player.accessories.remaining_to_cap$` / `.lockable` | int | How many more slots could still be unlocked before hitting the cap. |
| `$player.accessories.pages$` / `.total_pages` | int | How many bag pages the player's current unlocked count spans. |
| `$player.accessories.percent_full$` / `.percent` | int | `used / unlocked * 100`, rounded. `0` if `unlocked` is `0`. |
| `$player.accessories.is_full$` | boolean | `true` if every unlocked slot has an accessory in it. |
| `$player.accessories.is_maxed$` | boolean | `true` if `unlocked >= cap` (no more slots can ever be unlocked). |

Example condition — gate a reward behind having room left in the bag:

```yaml
- "condition $player.accessories.empty$ > 0"
```
