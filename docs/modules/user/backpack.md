# Backpacks & Accessories — User Documentation

> There is no dedicated "backpack" or "accessory" module or command list — both are implemented as
> ordinary items + GUIs. See `docs/modules/design/backpack.md` for the implementation.

## Backpacks

A backpack is a normal item (`plugins/Valmora/items/*.yml`) with `item-type: BACKPACK` and a
`container-gui:` pointing at a GUI that contains a `STORAGE` component with `owner: ITEM`. Its
contents are saved **on the item itself**, so they travel with it through your inventory, ender
chest, trades, and item drops.

- **Open it:** right-click the backpack while holding it (the default `open` ability uses
  `OPEN_CONTAINER_GUI`).
- **5 tiers ship by default:** `backpack_tier1`…`backpack_tier5`, 9 → 45 storage slots (see
  `plugins/Valmora/items/backpacks.yml`).
- Losing/dropping the backpack loses whatever is inside it — treat it like a real bag, not
  player-bound storage.

## Accessory Bag

The accessory bag is a **player-owned** storage GUI (`STORAGE` component with `owner: PLAYER`),
saved to your profile in the database rather than to any single item — it survives even if you
never touch the item that opened it.

- **Open it:** `/accessories`
- Accepts items with `item-type: ACCESSORY`, and — as a convenience — `BACKPACK` items too, so you
  can stash a backpack inside your accessory bag and click it to open its own storage without
  pulling it out first.
- Equipping/removing accessories recalculates your stats immediately (accessory bonuses apply the
  moment you close the bag).

## Authoring a New Backpack Tier / Storage GUI

1. Create a GUI YAML (`plugins/Valmora/guis/my_bag.yml`) with a `STORAGE` component:
   ```yaml
   my_bag:
     title: "<gold>My Bag"
     rows: 3
     layout:
       - "SSSSSSSSS"
       - "SSSSSSSSS"
       - "SSSSSSSSS"
     components:
       S:
         type: STORAGE
         id: storage
         owner: ITEM      # or PLAYER for a player-bound bag (add `storage-id:` too)
   ```
2. Point an item at it:
   ```yaml
   my_bag_item:
     item-type: BACKPACK
     container-gui: my_bag
     abilities:
       open:
         trigger: RIGHT_CLICK
         mechanics:
           - type: OPEN_CONTAINER_GUI
   ```
3. `/valmora reload`. Right-clicking the item opens the bag.

Use `condition:` on the `STORAGE` component (with the `$candidate.item.*$` variables) to restrict
what can be placed in it — e.g. an accessory-only bag, or a quiver-style bag that only accepts
arrows.

## Quiver

A player-owned, DB-backed 27-slot arrow storage — the shipped `guis/quiver.yml` example referenced
above, made real (2026-08-07). Replaces the old removed ammo-quiver feature.

- **Open it:** `/quiver`
- Accepts vanilla `ARROW`, `SPECTRAL_ARROW`, and `TIPPED_ARROW` items only.
- **Auto-refill:** whenever you fire a bow and end up with zero arrows left in your main inventory,
  one arrow stack is automatically pulled out of your quiver and into your inventory — you don't
  have to open the quiver mid-fight to keep shooting. This is a top-up after the fact, not a true
  "bow reads directly from the quiver" — vanilla still requires arrows physically in your inventory
  to draw a bow at all, so the first shot of an empty-inventory-but-full-quiver session still needs
  one manual refill.
