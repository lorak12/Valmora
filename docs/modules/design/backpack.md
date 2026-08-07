# Backpacks & Accessories — Design & Code

> **Version:** 0.2 | **API:** Paper 1.21.x | **Java:** 21
> **Status:** `AccessoryModule`, `BackpackModule`, and `QuiverModule` (dedicated Java modules) have
> been **removed**. Backpacks and accessories are now implemented entirely as data — a `STORAGE`
> GUI component plus item-level fields — with no dedicated module, load-order slot, or `ValmoraAPI`
> accessor. Quiver has a lightweight replacement (2026-08-07, see §6) — a `STORAGE`-based GUI plus
> a small standalone `QuiverListener` — rather than a full dedicated module.

---

## 1. Why This Changed

The old modules each hand-rolled their own inventory holder, listener, and PDC-backed persistence
for what is fundamentally the same primitive: "a set of slots that stores items and survives
restart/reload/trade." That primitive is now a first-class GUI component
(`org.nakii.valmora.module.gui.components.StorageComponent`) usable by **any** GUI definition, not
just backpacks/accessories — see `docs/modules/design/gui.md` for the general GUI component system.

## 2. `STORAGE` Component

```yaml
components:
  S:
    type: STORAGE
    id: bag                # unique within the GUI
    owner: PLAYER           # PLAYER or ITEM — see below
    storage-id: accessories # persistence key
    condition: "$candidate.item.type$ == ACCESSORY || $candidate.item.type$ == BACKPACK"
    open-container: true    # allow placing a BACKPACK item here and opening it in-place
```

- **`owner: PLAYER`** — storage is bound to the *viewing player*, DB-backed (write-through save),
  survives restarts/crashes. Used by the accessory bag.
- **`owner: ITEM`** — storage is bound to the *physical item instance* the GUI was opened from
  (its contents live in the item's own NBT/PDC via `ItemStorageCodec`), so the storage travels with
  the item through trades, drops, and other inventories. Used by backpacks.
- **`condition`** — a `Condition` (same DSL as everywhere else) evaluated per candidate item using
  the new `$candidate.*$` variable namespace (`CandidateVariableProvider`) to gate what can be
  placed in the slot(s).
- **`open-container: true`** — lets the player click a nested container item (e.g. a backpack
  placed inside the accessory bag) to open *its own* storage GUI without removing it from the slot.

Backing classes: `StorageComponent` (component model), `gui/storage/ItemStorageCodec` (item-owned
serialization), `gui/storage/PlayerHeldItemHandle` / `GuiSlotItemHandle` / `ItemBindingHandle`
(the three ways a storage GUI can be "bound" — to a held item, to a slot in another open GUI, or
generically).

## 3. Opening a Container From an Item

Any item can become a "container" by setting two fields in its `items/*.yml` definition:

```yaml
backpack_tier1:
  item-type: BACKPACK       # or ACCESSORY
  container-gui: backpack_tier1   # id of the GUI to open
  abilities:
    open:
      trigger: RIGHT_CLICK
      mechanics:
        - type: OPEN_CONTAINER_GUI
```

`OPEN_CONTAINER_GUI` (`OpenContainerMechanic`) reads the held item's `Keys.CONTAINER_GUI_KEY` PDC
tag (set from `container-gui:` at parse time) and opens that GUI bound to the held item instance —
this replaces the old backpack-only `OPEN_BACKPACK` mechanic with a generic one usable by any
item-owned storage.

## 4. Item Types

`ItemType.ACCESSORY` and `ItemType.BACKPACK` still exist (`ItemType.java`) — they gate what a
`condition:` can check for (`$candidate.item.type$ == ...`) and are otherwise plain classification
tags, same as every other `ItemType` value (see `docs/modules/design/item.md`). There is still no
`ItemType.QUIVER` — the quiver GUI (§6) gates on raw vanilla `$candidate.item.material$` values
instead, since arrows are plain vanilla items with no Valmora item-type tag of their own.

## 5. Shipped Examples

- `src/main/resources/items/backpacks.yml` — 5 tiers, `backpack_tier1`…`backpack_tier5`, 9→45
  slots, one `guis/backpack_tier<N>.yml` per tier.
- `src/main/resources/guis/accessory_bag.yml` — the player-owned accessory bag (`/accessories`),
  27 slots, accepts `ACCESSORY` and `BACKPACK` items (the latter demonstrates nested `open-container`
  use — place a backpack in the bag, click it to open its own storage).
- `on-close: recalculate_stats` on the accessory bag — accessory-granted stat bonuses are
  recomputed whenever the bag closes (see `docs/modules/design/stat.md` for how item-derived stats
  are aggregated).

## 6. Known Gaps

*(2026-08-07: both items previously listed here are resolved.)*

- **Quiver replacement shipped.** `guis/quiver.yml` — a player-owned `STORAGE` component gated on
  `ARROW`/`SPECTRAL_ARROW`/`TIPPED_ARROW` (`$candidate.item.material$`), opened via `/quiver`. Bow
  auto-consumption is approximated by `QuiverListener` (`module/item/QuiverListener.java`):
  listens on `EntityShootBowEvent`, and if the shooter is left with zero arrows anywhere in their
  main inventory after the shot, pulls one stack out of their quiver storage into the inventory.
  Deliberately not a true "bow reads from the quiver directly" — that would require intercepting
  vanilla's own bow-draw permission check (which needs arrows already present in the inventory
  before the shot is even possible; there's no "tried to shoot with none" event to hook), which was
  judged unnecessary complexity for the same practical result. Registered/unregistered by
  `ItemManager` (which also had its own pre-existing `LootListener` unregister-on-disable gap
  fixed in the same pass).
- **Admin walkthrough already existed** — `docs/modules/user/backpack.md`'s "Authoring a New
  Backpack Tier / Storage GUI" section (docs drift; this doc's Known Gaps note was stale).
