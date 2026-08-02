# Accessory Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21 | **Module ID:** `accessories`

---

## Overview

The Accessory module adds a per-profile **Accessory Bag** — a 45-slot, always-active equipment
inventory that lives on each `ValmoraProfile` rather than in the player's normal inventory. Any item
tagged `item-type: ACCESSORY` can be stored in the bag, and every item inside it contributes its
stat bonuses to the player at **all times** (no slot needs to be "equipped" — all 45 slots are
treated as equipped simultaneously).

It is a deliberately small, storage-oriented module:

- The bag itself is a vanilla `Inventory` with a custom `AccessoryInventoryHolder`.
- Content filtering is enforced by an `AccessoryListener` at click time via the item's PDC
  `ITEM_TYPE_KEY` (`valmora:item_type`), never by display name.
- Stat application is **not** done here — the `stat` module's `StatManager.recalculateStats()`
  reads `ValmoraProfile.getAccessoryItems()` and folds them into the player's effective stats.
- Persistence is **not** currently wired to the database (see [Unfinished Things](#unfinished-things--todos)).

Target audience: players who want permanent, always-on stat bonuses from collectible accessory
items, and admins who define those items in the standard item YAML.

---

## Code Structure

All module code lives in `src/main/java/org/nakii/valmora/module/accessory/`:

| File | Lines | Responsibility |
|---|---|---|
| `AccessoryModule.java` | 93 | The `ReloadableModule`. Owns the listener, the bag size constant, opens/saves the bag, and identifies accessory items. |
| `AccessoryListener.java` | 34 | Bukkit event handler. Saves the bag on close and rejects non-accessory items on click. |
| `AccessoryInventoryHolder.java` | 22 | `InventoryHolder` used to tag the bag inventory so the listener can recognise it. |

There is **no** `AccessoryRegistry`, `AccessoryLoader`, or `AccessoryDefinition` class. Unlike
`item`, `mob`, `skill`, etc., this module does not load any definitions of its own — it reuses the
generic item pipeline (`ItemType.ACCESSORY`) for item identity.

### Supporting types outside the module package

| Type | Location | Role in this module |
|---|---|---|
| `ItemType.ACCESSORY` | `module/item/ItemType.java:25` | The enum value that makes an item an accessory. |
| `ItemFactory.create()` | `module/item/ItemFactory.java:33-35` | Writes `ItemType` enum name into `Keys.ITEM_TYPE_KEY` PDC on item creation. |
| `ItemDefinitionParser` | `module/item/ItemDefinitionParser.java:44-52` | Parses `item-type:` from item YAML into the `ItemType` enum. |
| `Keys.ITEM_TYPE_KEY` | `util/Keys.java:9`, `:45` | NamespacedKey `valmora:item_type` — the identity tag checked by `isAccessoryItem()`. |
| `ValmoraProfile.accessoryItems` | `module/profile/ValmoraProfile.java:32-33`, `:94-95` | The 45-slot `ItemStack[]` backing store for the bag. |
| `StatManager.recalculateStats()` | `module/stat/StatManager.java:143-155` | Consumer — folds accessory item stats into effective stats. |
| `Valmora.java` | `:120`, `:181`, `:219`, `:250-254`, `:428` | Wiring: field, instantiation, registration, `/accessories` command, getter. |
| `SQLDataStore` | `database/SQLDataStore.java` | *Should* persist the bag — see [Unfinished Things](#unfinished-things--todos). |

---

## Architecture & Key Classes

### Registration order and lifecycle

The module is registered at `Valmora.java:219`:

```java
moduleManager.registerModule(accessoryModule);    // Depends on statModule for recalc
```

Load order context (`Valmora.java`): ... `slayer` (218) → **`accessory` (219)** → `backpack`
(220) → `quiver` (221) → `progression` (222). It loads late, after `stat`, `player`, and `item` —
all of which it relies on. Its own comment states the dependency: `statModule` for recalc.

The module is instantiated in `Valmora.onEnable()` at `Valmora.java:181` and disabled normally via
`ModuleManager.disableModules()` on plugin shutdown (`Valmora.java:264-266`). There is no special
handling in `/valmora reload` beyond the standard disable/enable cycle — which has persistence
consequences (see below).

### Lifecycle implementation (`AccessoryModule`)

```java
@Override
public void onEnable() {
    this.listener = new AccessoryListener(this);
    plugin.getServer().getPluginManager().registerEvents(listener, plugin);
}
```
— `AccessoryModule.java:27-31`

```java
@Override
public void onDisable() {
    if (listener != null) {
        HandlerList.unregisterAll(listener);
        listener = null;
    }
}
```
— `AccessoryModule.java:33-39`

The lifecycle is textbook `ReloadableModule` hygiene: exactly one listener, registered in
`onEnable()`, unregistered (and nulled) in `onDisable()`. This is idempotent and reload-safe. No
caches or tasks exist to clean up. `getId()` returns `"accessories"` (`:42`), `getName()` returns
`"Accessory System"` (`:45`).

### The bag itself

The bag is a plain 45-slot `Inventory` (one row of 5 double-chest rows), created on demand:

```java
static final int ACCESSORY_SLOTS = 45;                            // AccessoryModule.java:18
...
Component title = Formatter.format("<dark_gray>✦ Accessory Bag"); // :51
AccessoryInventoryHolder holder = new AccessoryInventoryHolder(player); // :52
Inventory inv = Bukkit.createInventory(holder, ACCESSORY_SLOTS, title); // :53
holder.setInventory(inv);                                          // :54
```
— `AccessoryModule.java:47-63`

`openAccessoryBag(Player)`:
1. Resolves the player's active `ValmoraProfile` via `PlayerManager.getSession(uuid)` and
   `session.getActiveProfile()` (`:82-85`); returns silently if there is no session/profile (`:49`).
2. Creates the inventory with an `AccessoryInventoryHolder` so the listener can identify it.
3. Copies the profile's saved `ItemStack[]` into the inventory (`:57-60`) — note it sets the items
   directly by reference, no cloning.
4. Opens the inventory for the player (`:62`).

`saveAccessories(Player, Inventory)`:
1. Reads the profile; returns if missing (`:66-67`).
2. Copies all 45 slots back into a fresh `ItemStack[45]` and calls `profile.setAccessoryItems(items)`
   (`:69-73`).
3. Triggers a full stat recalculation so accessory bonuses apply/clear immediately:

```java
ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
if (session != null && session.getActiveProfile() != null) {
    session.getActiveProfile().getStatManager().recalculateStats(player);
}
```
— `AccessoryModule.java:76-79`

This recalc call is the concrete realisation of the `Depends on statModule for recalc` comment in
`Valmora.java:219`. The module talks to other systems exclusively through `ValmoraAPI.getInstance()`
(`:76`, `:83`) — it never holds direct sibling-module references.

### Accessory item identity

```java
public boolean isAccessoryItem(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return false;
    String typeStr = item.getItemMeta().getPersistentDataContainer()
            .get(org.nakii.valmora.util.Keys.ITEM_TYPE_KEY, org.bukkit.persistence.PersistentDataType.STRING);
    return "ACCESSORY".equalsIgnoreCase(typeStr);
}
```
— `AccessoryModule.java:87-92`

Identification is **PDC-driven**, matching AGENTS §11.12 / §11.5 — never display-name or lore
matching. The tag is written at item-creation time by `ItemFactory.create()`:

```java
meta.getPersistentDataContainer().set(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING, definition.getItemType().name());
```
— `module/item/ItemFactory.java:33-35`

and originates from `item-type: ACCESSORY` in the item YAML, parsed in
`module/item/ItemDefinitionParser.java:44-52` (which validates against the `ItemType` enum and
fails item load on an unknown value).

### Listener logic

`AccessoryListener` (`AccessoryListener.java`) guards the bag:

- **`onClose(InventoryCloseEvent)`** — `:18-23`. If the closed inventory's holder is an
  `AccessoryInventoryHolder` and the closer is a `Player`, calls `module.saveAccessories(player,
  event.getInventory())`. This is the only save path — the bag persists to the profile whenever it
  is closed (including hot-bar drop / teleport-triggered closes).

- **`onClick(InventoryClickEvent)`** — `:25-33`. If the clicked inventory is an accessory bag and
  the **cursor** holds a non-accessory item, the click is cancelled:

```java
ItemStack cursor = event.getCursor();
if (cursor != null && !cursor.getType().isAir() && !module.isAccessoryItem(cursor)) {
    event.setCancelled(true);
}
```
— `AccessoryListener.java:28-32`

This only inspects `event.getCursor()` — it catches drags/pick-ups-and-places of foreign items but
does **not** cover Shift-click transfers (see [Unfinished Things](#unfinished-things--todos)).

`AccessoryInventoryHolder` (`AccessoryInventoryHolder.java`) is a trivial `InventoryHolder`
carrying the owning `Player` (`getPlayer()`, `:16`) and a set-once `Inventory` (`getInventory()`
`:18-19`, `setInventory()` `:21`). Its sole purpose is to act as a type token so the listener can
distinguish the accessory bag from any other inventory.

### Data flow summary

```
/item give <id>                          → ItemFactory.create() writes ITEM_TYPE_KEY = "ACCESSORY"
player runs /accessories                 → AccessoryModule.openAccessoryBag(player) (Valmora.java:250-254)
  → Bukkit.createInventory(holder, 45)   → AccessoryModule.java:53
  → populates from profile.getAccessoryItems() (:57-60)
player clicks a non-accessory item in    → AccessoryListener.onClick cancels (AccessoryListener.java:25-33)
player closes the bag                    → AccessoryListener.onClose → AccessoryModule.saveAccessories (:18-23, :65-80)
  → profile.setAccessoryItems(items)     → ValmoraProfile.java:95
  → session.activeProfile.statManager.recalculateStats(player)   → AccessoryModule.java:78
    → StatManager reads profile.getAccessoryItems() → StatManager.java:146
    → statModule.loadStats(meta) per item → adds to effective stats via addModifier (StatManager.java:149-152)
```

---

## Configuration (YAML)

**The accessory module has no dedicated YAML configuration.**

- There is no `accessories/` resource folder and no `.yml` files read by this module.
- `config.yml` (`src/main/resources/config.yml`) contains **no** `accessory` section.
- The bag size is a hardcoded constant: `ACCESSORY_SLOTS = 45` (`AccessoryModule.java:18`).
- The GUI title is hardcoded in code: `<dark_gray>✦ Accessory Bag` (`AccessoryModule.java:51`).
- There is **no** permission node and no tab-completer for `/accessories`; the command is open to
  any player (`plugin.yml:64-66`).

The only "configuration" the module consumes is the standard **item definition** schema (see
`docs/VALMORA_DOCUMENTATION.md` §23) — accessory items are just items with `item-type: ACCESSORY`.
Because of `saveAllResources()` (`Valmora.java:456-490`), only files under known prefixes are
auto-copied; accessory item definitions belong in `items/*.yml`, which **is** in the copied set.

---

## Data Model / Persistence

### In-memory model

`ValmoraProfile` owns the bag:

```java
// Accessory bag (45 slots)
private ItemStack[] accessoryItems = new ItemStack[45];
...
public ItemStack[] getAccessoryItems() { return accessoryItems; }
public void setAccessoryItems(ItemStack[] items) { this.accessoryItems = items; }
```
— `module/profile/ValmoraProfile.java:32-33`, `:94-95`

The array is per-profile, so switching profiles switches accessory sets in memory. The array is
always 45 elements (never null); entries may be `null` for empty slots. `openAccessoryBag`/
`saveAccessories` copy slots **by reference** — no `clone()` — so the profile array and the live
inventory briefly share `ItemStack` instances until the next save.

### Database — NOT persisted (confirmed gap)

The `valmora_profiles` table schema (`database/SQLDataStore.java`):

- `migrateToV1` creates columns: `id, player_uuid, name, stats, skills, player_state, tags,
  variables, collections, inventory, created_at, last_used` (`SQLDataStore.java:124-144`).
- `migrateToV2` adds a **quiver** column only: `addColumnIfMissing(conn, "valmora_profiles",
  "quiver", "TEXT")` (`SQLDataStore.java:117-120`).
- The profile upsert in `savePlayer` serializes `stats, skills, player_state, tags, variables,
  collections, inventory, quiver` (`SQLDataStore.java:286-312`) — **not** the accessory bag.
- `loadPlayer` deserializes the same set, plus inventory (`:241-244`) and quiver (`:246-249`) —
  again **not** the accessory bag.

There is even a ready-made generic serialization helper that was written for exactly this kind of
field but is only used by the quiver:

```java
// Generic fixed-size ItemStack[] <-> base64 JSON array, used for the quiver (and any
// future flat item-array profile field that isn't the multi-part player inventory).
private String serializeItemArray(ItemStack[] items) { ... }        // SQLDataStore.java:399-406
private ItemStack[] deserializeItemArray(String json, int size) { ... } // SQLDataStore.java:408-418
```

**Consequences of the gap:**

| Event | What happens to bag contents |
|---|---|
| Player quits (`PlayerManager.handleQuit` → `dataStore.savePlayer(stored)`, `PlayerManager.java:127-137`) | **Lost** — not serialized. |
| Server restart (`Valmora.onDisable` → `savePlayer(player).join()`, `Valmora.java:270-274`) | **Lost.** |
| `/valmora reload` (PlayerManager.onDisable saves + clears `activeSession` `:116-119`, then `onEnable` → `handleJoin(uuid, true)` re-loads from DB `:50-53`, `:96-97`) | **Lost** for online players — profiles are rebuilt from the DB without the bag. |
| Profile switch in-session (`PlayerManager.switchProfile`, `:150-163`) | **Preserved** — `accessoryItems` stays on the in-memory `ValmoraProfile`. |

This is the single largest defect in the module; see [Possible Improvements](#possible-improvements--changes).

---

## API Exposed

### Public methods on `AccessoryModule`

| Method | Signature | Location | Purpose |
|---|---|---|---|
| `openAccessoryBag` | `void openAccessoryBag(Player player)` | `AccessoryModule.java:47` | Opens the player's accessory bag, populated from the active profile. |
| `saveAccessories` | `void saveAccessories(Player player, Inventory inv)` | `AccessoryModule.java:65` | Copies the bag inventory into the profile and triggers stat recalc. |
| `isAccessoryItem` | `boolean isAccessoryItem(ItemStack item)` | `AccessoryModule.java:87` | True if the item's PDC `item_type` equals `ACCESSORY`. |

### Accessor on the plugin

```java
public AccessoryModule getAccessoryModule() { return accessoryModule; }
```
— `Valmora.java:428`

**Not exposed via `ValmoraAPI`.** `org.nakii.valmora.api.ValmoraAPI` (`ValmoraAPI.java:19-69`)
has no `getAccessoryModule()` entry, so third-party code must cast to the concrete `Valmora`
instance (`Valmora.getInstance()`, `Valmora.java:278-280`) rather than use the interface.
Compare with modules such as `quest`, `warp`, or `npc`, which are proper API members.

### `AccessoryInventoryHolder`

```java
public Player getPlayer();              // AccessoryInventoryHolder.java:16
public Inventory getInventory();        // :18-19
public void setInventory(Inventory);    // :21
```

---

## Dependencies & Consumers

### What this module depends on

| Dependency | How it is used |
|---|---|
| `playerManager` (`PlayerManager.getSession`, active profile) | Resolving the player's `ValmoraProfile` in `getProfile()` (`AccessoryModule.java:82-85`) and the recalc in `saveAccessories` (`:76-79`). Drives the "loads after playerManager" ordering (`Valmora.java:219` vs `:191`). |
| `statModule` / `StatManager.recalculateStats` | Applying/removing accessory bonuses when the bag changes (`AccessoryModule.java:78`). |
| `item` system (`ItemType.ACCESSORY`, `ItemFactory`, `Keys.ITEM_TYPE_KEY`) | Defining and recognising accessory items (`AccessoryModule.java:87-92`). |
| `util.Keys`, `util.Formatter` | PDC keys and MiniMessage formatting (`Keys.java:9,45`, `Formatter.java:13`). |

All cross-module access goes through `ValmoraAPI.getInstance()` — no direct sibling references
(`AccessoryModule.java:76`, `:83`), consistent with AGENTS §6.4.

### What uses this module

| Consumer | Location | Use |
|---|---|---|
| `StatManager.recalculateStats` | `module/stat/StatManager.java:143-155` | Iterates `profile.getAccessoryItems()`, loads each item's stat map via `statModule.loadStats(meta)` (`StatManager.java:148-149`) and folds the values into **effective** stats with `addModifier` (`:151`). This is the entire gameplay effect of the module. |
| `Valmora.onEnable` `/accessories` command | `Valmora.java:250-254` | Sole entry point for players; routes to `openAccessoryBag`. |
| `AccessoryListener` | `AccessoryListener.java:18-33` | Feeds clicks/closes back into the module. |

Notably, `StatManager`'s accessory loop (`StatManager.java:146-154`) applies **stats only** — it
does **not** execute `PASSIVE` abilities, unlike the equipment loop (`StatManager.java:113-127`)
which runs `PASSIVE` ability mechanics for held/armor items. Accessory `RIGHT_CLICK` abilities
would only fire if the item were physically held, which the bag does not support.

### Non-dependents

The `accessory` module is not referenced by any other module, command (other than its own
`/accessories` executor), GUI, script, or progression feature. `grep` across the codebase for
`getAccessoryModule`, `AccessoryManager`, and `isAccessoryItem` returns only `Valmora.java`,
`AccessoryModule.java`, and `AccessoryListener.java`.

---

## Unfinished Things / TODOs

There are no literal `TODO`/`FIXME` comments in the module source, but several documented-in-code
gaps exist:

1. **Bag contents are never persisted to the database.** No `accessory` column in
   `valmora_profiles` (schema in `SQLDataStore.java:124-144` + `:117-120`), and neither
   `savePlayer` (`:286-312`) nor `loadPlayer` (`:241-249`) touches `accessoryItems`. The generic
   `serializeItemArray`/`deserializeItemArray` helpers exist (`SQLDataStore.java:399-418`) and even
   carry a comment anticipating "future flat item-array profile field[s]" — the accessory bag is
   exactly that field, yet it is not wired up. Data is lost on restart, quit, and hot-reload.

2. **Shift-click validation bypass.** `AccessoryListener.onClick` only checks `event.getCursor()`
   (`AccessoryListener.java:28-32`). A `SHIFT_CLICK` of a non-accessory item from the player's
   inventory has an empty cursor, so the guard never fires and the foreign item enters the bag.
   Because the close handler saves whatever is in the inventory (`AccessoryListener.java:18-23`),
   the stray item is then stored and its stats picked up by `StatManager.recalculateStats`
   (`StatManager.java:146-154`).

3. **Hardcoded constants.** `ACCESSORY_SLOTS = 45` (`AccessoryModule.java:18`) and the GUI title
   (`:51`) are compile-time literals; no `config.yml` knobs, no permission node, no localization.

4. **No `ValmoraAPI` exposure.** The module is reachable only through the concrete
   `Valmora.getInstance().getAccessoryModule()` (`Valmora.java:428`); `ValmoraAPI` has no
   accessor.

5. **No default accessory items shipped.** `item-type: ACCESSORY` appears nowhere in the bundled
   `src/main/resources/items/*.yml` (the only "Accessory" string is a lore reference to an
   "Intimidation Accessory" on the Parrot Mask, `items/individual_pieces.yml:290`). A fresh install
   has a bag GUI but no way to obtain accessories without admin item definitions.

6. **Single fixed-size page, no pagination or filler slots.** All 45 slots are usable storage;
   there is no locked padding, no navigation, and no per-slot restrictions beyond the ACCESSORY tag.

7. **Saves only on close.** There is no periodic save, so a server crash or forced kick mid-bag
   loses changes made since the last `InventoryCloseEvent`.

8. **No cloning on save/populate.** Items are handed between the profile array and the inventory
   by reference (`AccessoryModule.java:57-60`, `:69-73`), allowing a stale `ItemStack` reference to
   be mutated by other systems between saves.

---

## Possible Improvements / Changes

All suggestions are grounded in the code referenced above.

1. **Persist the bag.** Add an `accessory` `TEXT` column in a new `migrateToV3` step (following
   the `migrateToV2` pattern, `SQLDataStore.java:117-120`), serialize with
   `serializeItemArray(profile.getAccessoryItems())` in `savePlayer` (`:302-326`), and hydrate with
   `deserializeItemArray(..., 45)` in `loadPlayer` (mirroring the quiver block, `:246-249`). Also
   bump `LATEST_SCHEMA_VERSION` from 2 (`SQLDataStore.java:48`). This resolves items 1, 7 (for
   normal quits) and the reload data loss simultaneously.

2. **Close the shift-click hole.** In `AccessoryListener.onClick`, when
   `event.getClick() == ClickType.SHIFT_LEFT || SHIFT_RIGHT`, validate `event.getCurrentItem()`
   against `module.isAccessoryItem(...)` instead of the cursor, and cancel the move when the target
   slot is inside the bag (`event.getClickedInventory()` instanceof `AccessoryInventoryHolder`).
   Optionally add a defence-in-depth pass in `saveAccessories` that strips or rejects non-accessory
   slots at save time.

3. **Make it configurable.** Drive `ACCESSORY_SLOTS` and the title from `config.yml` (with the
   current values as defaults), add a `valmora.accessories` permission checked in the `/accessories`
   executor (`Valmora.java:250-254`), and use `NamespacedKey`/message config for the title.

4. **Expose through `ValmoraAPI`.** Add `AccessoryModule getAccessoryModule()` to
   `ValmoraAPI` (and implement it in `Valmora.java`, which already implements the interface at
   `:75`) so downstream code follows the documented API pattern instead of casting.

5. **Apply accessory `PASSIVE` abilities.** Extend the accessory loop in
   `StatManager.recalculateStats` (`StatManager.java:146-154`) to also execute `PASSIVE` ability
   mechanics, matching the equipment loop (`:113-127`), or decide explicitly that accessories are
   stat-only and document it.

6. **Ship example accessories.** Add a default `items/*.yml` entry (e.g., a `PLAYER_HEAD` with
   `item-type: ACCESSORY`) so the bag is usable out of the box and serves as a documented example.

7. **Harden saves.** `clone()` items when copying into and out of the bag
   (`AccessoryModule.java:57-60`, `:69-73`), and consider saving on every mutation rather than only
   on close.

8. **UX polish.** Add locked filler rows, a header item with the bag's name, and/or pagination if
   the slot count is made configurable and grows beyond 45.
