# Accessory Module — Design & Code

> **Version:** 0.2 | **API:** Paper 1.21.x | **Java:** 21 | **Module ID:** `accessories`

---

## Overview

The Accessory module adds a per-profile **Accessory Bag** — a dynamically sized, paginated,
always-active equipment inventory that lives on each `ValmoraProfile` rather than in the player's
normal inventory. Any item tagged `item-type: ACCESSORY` can be stored in the bag, and every item in
an *unlocked* slot contributes its stat bonuses to the player at **all times** (no slot needs to be
"equipped" — every unlocked slot counts as equipped simultaneously).

- The bag itself is a vanilla `Inventory` with a custom `AccessoryInventoryHolder`, resized per-page
  by an `AccessoryModule.AccessoryLayout` record.
- Content filtering is enforced by an `AccessoryListener` at click time via the item's PDC
  `ITEM_TYPE_KEY` (`valmora:item_type`), never by display name — including shift-click transfers in
  both directions.
- Each profile has an **unlocked slot count** (`ValmoraProfile.accessorySlotsUnlocked`, default
  unset → falls back to `accessories.starting-slots`), which admins can raise up to a server-wide
  `accessories.max-slots-cap` via `/accessory`.
- Stat application is **not** done here — the `stat` module's `StatManager.recalculateStats()` reads
  `ValmoraProfile.getAccessoryItems()` and folds them into the player's effective stats.
- Persistence is wired to the database as of schema v3 (`accessory_items`, `accessory_slots`
  columns).

Target audience: players who want permanent, always-on stat bonuses from collectible accessory
items, and admins who define those items in the standard item YAML and manage slot progression.

---

## Code Structure

All module code lives in `src/main/java/org/nakii/valmora/module/accessory/`:

| File | Responsibility |
|---|---|
| `AccessoryModule.java` | The `ReloadableModule`. Owns the listener, slot-cap config, layout computation, opens/saves the bag, and identifies accessory items. |
| `AccessoryListener.java` | Bukkit event handler. Saves the bag on close, handles control-row clicks (page nav / close), rejects non-accessory items on click/shift-click in both directions. |
| `AccessoryInventoryHolder.java` | `InventoryHolder` used to tag the bag inventory and carry its `AccessoryLayout` so the listener knows the current page's slot geometry without recomputing it mid-interaction. |
| `AccessoryCommand.java` | Admin `TabExecutor` for `/accessory setcap\|setslots\|addslots\|info`. |

Script DSL integration lives outside the module package, alongside the rest of the script system:

| File | Responsibility |
|---|---|
| `module/script/event/impl/AccessorySlotsEventFactory.java` | `accessory_slots add\|remove\|set <amount>` event — lets quest/skill/GUI/ability scripts grant or revoke a caster's unlocked slots without an admin running a command. Mirrors `StatModifyEventFactory`'s add/set/reset shape and `$variable$` value resolution. |
| `module/script/variable/providers/PlayerVariableProvider.java` | Extended with a `resolveAccessories(...)` branch under the existing `player` namespace, so `$player.accessories.*$` resolves alongside `$player.stat.*$`, `$player.skill.*$`, etc. |

There is **no** `AccessoryRegistry`, `AccessoryLoader`, or `AccessoryDefinition` class. Unlike
`item`, `mob`, `skill`, etc., this module does not load any definitions of its own — it reuses the
generic item pipeline (`ItemType.ACCESSORY`) for item identity.

### Supporting types outside the module package

| Type | Location | Role in this module |
|---|---|---|
| `ItemType.ACCESSORY` | `module/item/ItemType.java` | The enum value that makes an item an accessory. |
| `ItemFactory.create()` | `module/item/ItemFactory.java` | Writes `ItemType` enum name into `Keys.ITEM_TYPE_KEY` PDC on item creation. |
| `ItemDefinitionParser` | `module/item/ItemDefinitionParser.java` | Parses `item-type:` from item YAML into the `ItemType` enum. |
| `Keys.ITEM_TYPE_KEY` | `util/Keys.java` | NamespacedKey `valmora:item_type` — the identity tag checked by `isAccessoryItem()`. |
| `ValmoraProfile.accessoryItems` / `accessorySlotsUnlocked` | `module/profile/ValmoraProfile.java` | The variable-length `ItemStack[]` backing store, plus the profile's unlocked slot count (`-1` = unset/default). |
| `StatManager.recalculateStats()` | `module/stat/StatManager.java` | Consumer — folds accessory item stats into effective stats. |
| `Valmora.java` | — | Wiring: field, instantiation, registration, `/accessories` + `/accessory` command registration, `getAccessoryModule()`. |
| `ValmoraAPI` | `api/ValmoraAPI.java` | Exposes `getAccessoryModule()` so downstream code doesn't need to cast to the concrete `Valmora` instance. |
| `SQLDataStore` | `database/SQLDataStore.java` | Persists the bag (`accessory_items`) and slot count (`accessory_slots`) as of schema v3. |

---

## Architecture & Key Classes

### Registration order and lifecycle

Unchanged from v0.1: registered after `stat`, `player`, and `item` (all of which it relies on), with
the same textbook `ReloadableModule` hygiene — one listener, registered in `onEnable()`, unregistered
and nulled in `onDisable()`.

```java
@Override
public void onEnable() {
    this.startingSlots = plugin.getConfig().getInt("accessories.starting-slots", 25);
    this.maxSlotsCap = Math.max(1, plugin.getConfig().getInt("accessories.max-slots-cap", 90));
    if (this.startingSlots > this.maxSlotsCap) this.startingSlots = this.maxSlotsCap;

    this.listener = new AccessoryListener(this);
    plugin.getServer().getPluginManager().registerEvents(listener, plugin);
}
```

Config is re-read every `onEnable()`, so `/valmora reload` picks up edits to `starting-slots` /
`max-slots-cap` in `config.yml` (in addition to runtime changes via `/accessory setcap`, which write
straight back to config).

### Slot cap model

```java
public int getUnlockedSlots(ValmoraProfile profile) {
    int stored = profile.getAccessorySlotsUnlocked();          // -1 = unset
    int base = stored < 0 ? startingSlots : stored;
    return Math.max(0, Math.min(base, maxSlotsCap));            // always clamped to the live cap
}

public void setUnlockedSlots(ValmoraProfile profile, int slots) {
    int clamped = Math.max(0, Math.min(slots, maxSlotsCap));
    profile.setAccessorySlotsUnlocked(clamped);
    ensureCapacity(profile);
}
```

Clamping happens on **read**, not just on write — so if an admin lowers `max-slots-cap` below a
profile's stored value, the player's effective slot count drops immediately without needing to touch
every profile's stored number. `ensureCapacity()` grows (never shrinks) the backing `ItemStack[]` to
`maxSlotsCap`, copying existing contents by reference-safe `System.arraycopy`.

### Layout — dynamic resize + pagination

The bag has no fixed size. `AccessoryModule.AccessoryLayout` is computed fresh for every open/nav:

```java
public record AccessoryLayout(int page, int totalPages, int itemsStart, int itemsOnPage, int rows,
                               int size, int controlRowStart) {}

public AccessoryLayout computeLayout(ValmoraProfile profile, int requestedPage) {
    int unlocked = getUnlockedSlots(profile);
    int totalPages = Math.max(1, (int) Math.ceil(unlocked / (double) PAGE_ITEM_CAP)); // PAGE_ITEM_CAP = 45
    int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
    int itemsStart = page * PAGE_ITEM_CAP;
    int itemsOnPage = Math.max(0, Math.min(PAGE_ITEM_CAP, unlocked - itemsStart));
    int rows = Math.max(1, (int) Math.ceil(itemsOnPage / 9.0));
    int controlRowStart = rows * 9;
    int size = controlRowStart + CONTROL_ROW_SIZE;                                    // CONTROL_ROW_SIZE = 9
    return new AccessoryLayout(page, totalPages, itemsStart, itemsOnPage, rows, size, controlRowStart);
}
```

Key properties:

- **The last row is always the control row.** `size` is always a multiple of 9 with the final 9
  reserved, regardless of how many item rows precede it (1–5 rows → 18–54 total size).
- **Pagination** kicks in once `unlocked > 45`; each page holds up to 45 real item slots
  (`PAGE_ITEM_CAP`), addressed by a global index (`itemsStart + local index`) into the profile's
  backing array.
- **Locked filler**: within the item rows, any local slot `>= itemsOnPage` but `< controlRowStart` is
  a "not yet unlocked" slot for *this row* — shown as a gray glass pane and rejected on click. This
  is what makes the bag look like it's "growing into" a fully unlocked row rather than jumping in
  9-slot increments.

### Opening / saving

```java
public void openAccessoryBag(Player player, int page) {
    ValmoraProfile profile = getProfile(player);
    if (profile == null) return;
    ensureCapacity(profile);
    AccessoryLayout layout = computeLayout(profile, page);
    // ... build inventory sized layout.size(), populate item slots [0, itemsOnPage) from
    // profile.getAccessoryItems()[itemsStart + i] (cloned), fill [itemsOnPage, controlRowStart)
    // with locked filler, populate the control row, player.openInventory(inv)
}
```

`openAccessoryBag(Player)` (no page arg) is kept as a convenience overload defaulting to page 0 — the
existing `/accessories` command wiring in `Valmora.java` calls this unchanged.

```java
public void saveAccessories(Player player, Inventory inv, AccessoryLayout layout) {
    ValmoraProfile profile = getProfile(player);
    if (profile == null) return;
    ensureCapacity(profile);
    ItemStack[] items = profile.getAccessoryItems();
    for (int i = 0; i < layout.itemsOnPage(); i++) {
        ItemStack item = inv.getItem(i);
        items[layout.itemsStart() + i] = (item == null || item.getType().isAir()) ? null : item.clone();
    }
    profile.setAccessoryItems(items);
    // recalc stats via ValmoraAPI.getInstance().getPlayerManager()...getStatManager().recalculateStats(player)
}
```

Both directions now `clone()` items — closing the item 8 gap from v0.1 (stale shared `ItemStack`
references between the profile array and the live inventory).

**Page navigation reuses the close-save path for free.** Calling `player.openInventory(newInv)` while
an inventory is already open triggers `InventoryCloseEvent` for the old one before the new one opens,
so `AccessoryListener.onClose` fires and saves the just-viewed page's slots — no separate "save before
navigating" call is needed in the Previous/Next handlers.

### Accessory item identity

Unchanged from v0.1 — PDC-driven, never display-name or lore matching:

```java
public boolean isAccessoryItem(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return false;
    String typeStr = item.getItemMeta().getPersistentDataContainer()
            .get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
    return "ACCESSORY".equalsIgnoreCase(typeStr);
}
```

### Listener logic

`AccessoryListener` (`AccessoryListener.java`):

- **`onClose(InventoryCloseEvent)`** — resolves the holder's stored `AccessoryLayout` and calls
  `module.saveAccessories(player, event.getInventory(), layout)`. Only the current page's slots are
  written back into the profile array at `layout.itemsStart() + i`.

- **`onClick(InventoryClickEvent)`** — dispatches on `event.getClickedInventory()` rather than
  `event.getInventory()`, so top (bag) and bottom (player inventory) clicks are handled distinctly:
  - **Locked filler slot** (`itemsOnPage <= slot < controlRowStart`): always cancelled, no exceptions.
  - **Control row** (`slot >= controlRowStart`): always cancelled (never accepts items), and dispatches
    by fixed relative offset — `0` = Previous Page (only if `page > 0`), `4` = Close
    (`player.closeInventory()`), `8` = Next Page (only if another page exists). These offsets are
    positional, not PDC- or name-matched, and are not player-forgeable since they're pure inventory
    slot indices, not item metadata.
  - **Real item slot in the bag**: rejects non-accessory items both via cursor (drag/place) and via
    `event.isShiftClick()` + `event.getCurrentItem()` (fixes the v0.1 shift-click bypass — moving a
    non-accessory *out* of the bag was always fine, but a shift-click *of* a non-accessory item that
    happened to be sitting in the bag, or shift-clicking from the player's own inventory into the bag,
    previously slipped past the cursor-only check).
  - **Bottom inventory (player's own) click**: if it's a shift-click of a non-accessory item, cancel
    it — this is the other half of the shift-click fix, since vanilla shift-click from the bottom
    inventory always targets the top inventory first.

`AccessoryInventoryHolder` now additionally carries the `AccessoryLayout` computed at open time
(`getLayout()`), so the listener never needs to recompute slot geometry against a profile that may
have changed underneath the open GUI (e.g. an admin running `/accessory setslots` while the bag is
open) — the currently-open page keeps behaving consistently until the player closes and reopens it.

### Data flow summary

```
/item give <id>                              → ItemFactory.create() writes ITEM_TYPE_KEY = "ACCESSORY"
/accessory setcap 90                          → AccessoryModule.setMaxSlotsCap() → config.yml persisted
/accessory addslots <player> 65               → AccessoryModule.addUnlockedSlots() → profile.accessorySlotsUnlocked
player runs /accessories                      → AccessoryModule.openAccessoryBag(player, 0)
  → computeLayout(profile, 0)                 → sizes inventory to fit unlocked slots (+ control row)
  → populates from profile.getAccessoryItems()[itemsStart..itemsStart+itemsOnPage) (cloned)
player clicks Next Page                        → AccessoryListener control-row dispatch → openAccessoryBag(player, page+1)
  → (Bukkit fires InventoryCloseEvent on the old page first) → saveAccessories() writes page N back
player closes the bag                          → AccessoryListener.onClose → saveAccessories(..., layout)
  → profile.setAccessoryItems(items)          → recalculateStats(player)
    → StatManager reads profile.getAccessoryItems() (full array, all pages) → folds stats
player quits / server saves                    → SQLDataStore.savePlayer → accessory_items + accessory_slots columns
```

---

## Configuration (YAML)

`config.yml`:

```yaml
accessories:
  starting-slots: 25
  max-slots-cap: 90
```

- Read in `AccessoryModule.onEnable()` via `plugin.getConfig().getInt(...)`, with the same defaults
  as fallbacks if the keys are missing.
- `/accessory setcap <amount>` calls `plugin.getConfig().set(...)` + `plugin.saveConfig()`, so runtime
  changes persist across restarts without manual file edits.
- The GUI title and control-row item names/lore remain hardcoded in `AccessoryModule.java` — no
  localization layer for those yet (unchanged from v0.1, considered out of scope for this pass).
- `/accessories` still has **no** permission node (open to every player). `/accessory` (the new admin
  command) requires `valmora.admin`, matching the `/eco`-style admin command pattern.

The only other "configuration" the module consumes is the standard **item definition** schema — see
`docs/VALMORA_DOCUMENTATION.md` §23. Accessory items belong in `items/*.yml`, which `saveAllResources()`
auto-copies on first run; `items/accessories.yml` now ships two example accessories
(`lucky_charm`, `speed_scarab`) so the bag is usable out of the box.

---

## Data Model / Persistence

### In-memory model

`ValmoraProfile` owns the bag and the unlocked-slot count:

```java
private ItemStack[] accessoryItems = new ItemStack[45];   // grown on demand up to maxSlotsCap
private int accessorySlotsUnlocked = -1;                  // -1 = unset → use configured starting-slots

public ItemStack[] getAccessoryItems() { return accessoryItems; }
public void setAccessoryItems(ItemStack[] items) { this.accessoryItems = items; }
public int getAccessorySlotsUnlocked() { return accessorySlotsUnlocked; }
public void setAccessorySlotsUnlocked(int slots) { this.accessorySlotsUnlocked = slots; }
```

The array's *length* is a capacity, not a slot count in use — `AccessoryModule.ensureCapacity()` grows
it to `maxSlotsCap` the first time a profile's bag is touched (open, save, or slot-count change) each
session, preserving existing contents. It's never shrunk, even if `max-slots-cap` is later lowered —
lowering the cap only affects `getUnlockedSlots()`'s clamp, not the backing array size, so no items are
ever silently dropped by an admin tightening the cap. `openAccessoryBag`/`saveAccessories` now
`clone()` items on both the populate and save paths (closing the v0.1 shared-reference gap).

### Database — persisted as of schema v3

`SQLDataStore`:

- `migrateToV3` adds `accessory_items TEXT` and `accessory_slots INTEGER` (nullable) to
  `valmora_profiles`, following the exact `migrateToV2` (quiver) pattern. `LATEST_SCHEMA_VERSION` is
  bumped to `3`.
- `savePlayer` serializes `profile.getAccessoryItems()` with the existing generic
  `serializeItemArray()` helper (shared with the quiver) and writes `accessorySlotsUnlocked` as
  `NULL` when it's `-1` (unset) or the raw int otherwise.
- `loadPlayer` deserializes `accessory_items` with a new **variable-size** overload,
  `deserializeItemArray(String json)` (distinct from the quiver's fixed-size
  `deserializeItemArray(String json, int size)`), because the accessory array's length is itself
  meaningful — it reflects how many slots a profile had grown to at last save, independent of the
  *current* `maxSlotsCap`. `accessory_slots` is read with `rs.getInt(...)` + `rs.wasNull()` to
  correctly round-trip the "unset" sentinel through a nullable SQL column.

| Event | What happens to bag contents (v0.2+) |
|---|---|
| Player quits | Saved — `accessory_items` + `accessory_slots` written in the normal `savePlayer` batch. |
| Server restart | Saved on clean shutdown; restored on next join via `loadPlayer`. |
| `/valmora reload` | Saved before profiles are torn down, restored from DB on re-enable — same as every other profile field. |
| Profile switch in-session | Preserved — unchanged from v0.1, still purely an in-memory `ValmoraProfile` swap. |

This closes what was previously "the single largest defect in the module."

---

## API Exposed

### Public methods on `AccessoryModule`

| Method | Signature | Purpose |
|---|---|---|
| `openAccessoryBag` | `void openAccessoryBag(Player player)` | Opens page 0 of the player's bag. |
| `openAccessoryBag` | `void openAccessoryBag(Player player, int page)` | Opens a specific page, clamped to `[0, totalPages-1]`. |
| `saveAccessories` | `void saveAccessories(Player player, Inventory inv, AccessoryLayout layout)` | Writes one page's slots back into the profile and triggers stat recalc. |
| `isAccessoryItem` | `boolean isAccessoryItem(ItemStack item)` | True if the item's PDC `item_type` equals `ACCESSORY`. |
| `computeLayout` | `AccessoryLayout computeLayout(ValmoraProfile profile, int requestedPage)` | Pure function — derives page geometry from a profile's unlocked slot count. |
| `getUnlockedSlots` / `setUnlockedSlots` / `addUnlockedSlots` | on `ValmoraProfile` | Per-player slot management, clamped to the live server cap. |
| `getStartingSlots` / `getMaxSlotsCap` / `setMaxSlotsCap` | — | Server-wide config accessors; `setMaxSlotsCap` persists to `config.yml`. |

### Accessor on the plugin / API

```java
public AccessoryModule getAccessoryModule() { return accessoryModule; }   // Valmora.java
org.nakii.valmora.module.accessory.AccessoryModule getAccessoryModule();  // ValmoraAPI.java — new in v0.2
```

`AccessoryModule` is now a proper `ValmoraAPI` member, matching the pattern used by `quest`, `warp`,
`npc`, etc. Third-party code no longer needs to cast to the concrete `Valmora` instance.

### `AccessoryInventoryHolder`

```java
public Player getPlayer();
public AccessoryModule.AccessoryLayout getLayout();   // new in v0.2
public Inventory getInventory();
public void setInventory(Inventory inv);
```

---

## Dependencies & Consumers

Unchanged from v0.1: depends on `playerManager` (active profile resolution), `statModule`
(`recalculateStats`), and the `item` system (`ItemType.ACCESSORY`, `ItemFactory`,
`Keys.ITEM_TYPE_KEY`). All cross-module access goes through `ValmoraAPI.getInstance()` — no direct
sibling references.

`StatManager.recalculateStats` (`module/stat/StatManager.java`) still applies **stats only** from the
accessory loop — it does **not** execute `PASSIVE` abilities, unlike the equipment loop. This remains
an intentional design decision (see "Decisions carried over" below), not an oversight.

---

## Decisions carried over from v0.1

These items from the original "Possible Improvements" list were considered and explicitly **not**
implemented in this pass:

- **Accessory `PASSIVE` abilities are still stats-only.** Extending the accessory loop in
  `StatManager.recalculateStats` to also execute `PASSIVE` ability mechanics would blur the line
  between "collectible passive stat stick" and "equipped gear," and interacts with ability
  cooldown/mana systems designed around actively worn/held items. Decision: accessories stay
  stat-only by design.
- **No localization layer** for the bag title or control-button text — still hardcoded MiniMessage
  strings in `AccessoryModule.java`, consistent with how most other GUI modules in this codebase
  currently work.

## Script DSL Integration

### `AccessorySlotsEventFactory`

```java
public class AccessorySlotsEventFactory implements EventFactory {
    @Override public String getName() { return "accessory_slots"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return ctx -> {};
        String action = args[0].toLowerCase();
        String rawValue = args[1];

        return ctx -> ctx.getPlayerCaster().ifPresent(player -> {
            ValmoraProfile profile = /* resolve via ValmoraAPI.getInstance().getPlayerManager() */;
            AccessoryModule module = ValmoraAPI.getInstance().getAccessoryModule();
            if (profile == null || module == null) return;

            int amount = (int) Math.round(resolveDouble(rawValue, ctx, player)); // supports $var$ exprs
            switch (action) {
                case "add" -> module.addUnlockedSlots(profile, amount);
                case "remove" -> module.addUnlockedSlots(profile, -amount);
                case "set" -> module.setUnlockedSlots(profile, amount);
            }
        });
    }
}
```

Registered in `ScriptModule.onEnable()` alongside `StatModifyEventFactory`. Because it resolves
`ValmoraAPI.getInstance().getAccessoryModule()` lazily inside the returned `CompiledEvent` (not at
registration time), it's safe that `script` is registered and enabled *before* `accessories` in the
module load order (`CLAUDE.md` §5) — the factory object itself carries no direct module reference.

All three actions route through `AccessoryModule.setUnlockedSlots`/`addUnlockedSlots`, which already
clamp to `[0, maxSlotsCap]` and call `ensureCapacity` — so a script-granted slot count can never
exceed the server cap or leave the backing `ItemStack[]` undersized, matching `/accessory`'s
guarantees exactly.

### `$player.accessories.*$`

Added as a branch inside `PlayerVariableProvider.resolve()` (the existing `player` namespace
provider — same file that already handles `stat`, `skill`, `var`, etc.), not a new provider/namespace.
This keeps `$player.*$` as the single place script authors look for anything about "the player,"
consistent with how `stat`/`skill` are nested rather than split into their own top-level namespaces.

```java
private Object resolveAccessories(String[] path, ValmoraAPI api, ValmoraProfile profile) {
    AccessoryModule accessoryModule = api.getAccessoryModule();
    if (accessoryModule == null) return null;

    int unlocked = accessoryModule.getUnlockedSlots(profile);
    int cap = accessoryModule.getMaxSlotsCap();
    // used = count of non-null/non-air entries in profile.getAccessoryItems()[0, unlocked)
    // empty, remainingToCap, totalPages (via computeLayout(profile, 0).totalPages()), percentFull derived from the above
    ...
}
```

`used` deliberately only scans the `[0, unlocked)` range of the backing array, not its full capacity
— slots beyond the current unlocked count are inert even if they still hold an item from before an
admin lowered the cap (see "In-memory model" above), so counting them as "used" would misrepresent
what's actually contributing stats right now.

Supported sub-keys: `unlocked`/`slots`, `cap`/`max`, `used`/`count`/`filled`, `empty`/`free`,
`remaining_to_cap`/`lockable`, `pages`/`total_pages`, `percent_full`/`percent`, `is_full`, `is_maxed`.
The bare `$player.accessories$` (no sub-key) defaults to `unlocked`, matching the convention where a
namespace's "obvious" value is what you get without drilling further in.

---

## Remaining known limitations

- `/accessory setslots` / `addslots` only work on **online** players (mirrors the `/eco` command
  pattern) — there's no offline-player profile lookup path in this command yet.
- Locked filler slots and control-row buttons are identified by fixed slot position within the
  computed layout, not by a PDC marker — correct and non-forgeable since slot position isn't item
  metadata, but it does mean any change to `PAGE_ITEM_CAP` / `CONTROL_ROW_SIZE` must be made
  consistently across `computeLayout`, `populateControlRow`, and the listener's slot dispatch logic.
- `accessory_slots` only ever targets `context.getPlayerCaster()` — there's no `@target`-style variant
  to grant slots to someone other than whoever the script is executing for (matches how
  `stat_modify`/`tag` behave for the same reason: caster-scoped is the norm for profile-mutating
  events in this DSL).
