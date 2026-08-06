# Backpacks & Accessories — User Documentation

> There is no dedicated "backpack" or "accessory" module or command list — both are implemented as
> ordinary items + GUIs. See `docs/modules/design/backpack.md` for the implementation. There is
> currently no quiver feature (the old one was removed with no replacement).

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
