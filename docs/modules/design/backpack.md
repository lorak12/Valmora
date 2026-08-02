# Backpack Module — Design & Code

> **Module ID:** `backpacks` | **Source:** `src/main/java/org/nakii/valmora/module/backpack/`
> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21

---

## Table of Contents

1. [Overview](#overview)
2. [Code Structure](#code-structure)
3. [Architecture & Key Classes](#architecture--key-classes)
4. [Configuration (YAML)](#configuration-yaml)
5. [Data Model / Persistence](#data-model--persistence)
6. [API Exposed](#api-exposed)
7. [Dependencies & Consumers](#dependencies--consumers)
8. [Unfinished Things / TODOs](#unfinished-things--todos)
9. [Possible Improvements / Changes](#possible-improvements--changes)

---

## Overview

The Backpack module adds **item-based portable storage** to Valmora. A backpack is a regular custom item (a `ValmoraItem` whose `item-type` is `BACKPACK`) that, when right-clicked, opens an inventory GUI. Whatever the player puts inside is serialized **into the backpack item itself** via PersistentDataContainer — there is no database table and no per-player ownership involved.

It is implemented as a small, self-contained `ReloadableModule` (`BackpackModule.java:22`) composed of four classes:

| Class | Responsibility |
|---|---|
| `BackpackModule` | Module lifecycle, opening/saving the GUI, PDC read/write, item identification |
| `BackpackMechanic` | Registers an `OPEN_BACKPACK` item ability mechanic (`AbilityMechanic`) |
| `BackpackListener` | `InventoryCloseEvent` (persist contents) and `InventoryClickEvent` (block nesting) handlers |
| `BackpackInventoryHolder` | `InventoryHolder` that binds the open GUI to the source backpack item + inventory slot |

**Purpose / players:** It gives players extra inventory space carried as a single held/stackable item. The storage travels with the item (tradable, droppable, storable in chests), because the contents are stored on the item itself rather than on the player profile. This is the "storage (backpack items)" item from `docs/todo.md:67`.

**Key design decisions:**

- **Contents live on the item**, not on the player. `saveContents()` serializes the whole GUI into `Keys.BACKPACK_CONTENTS_KEY` on the source item (`BackpackModule.java:69-83`). Any player holding the item can open and edit its contents — there is no ownership model.
- **The backpack is opened exclusively through the ability/mechanic system.** There is no `PlayerInteractEvent` handler in this module; opening is triggered by a `RIGHT_CLICK` ability whose mechanics list contains `OPEN_BACKPACK` (`BackpackMechanic.java:17`). Therefore a backpack must be a *registered* Valmora item with an `abilities` block.
- **The item type is the discriminator.** `isBackpack()` returns true only when the item's PDC `item_type` value equals `BACKPACK` (case-insensitive) (`BackpackModule.java:92-97`). The `BACKPACK` enum value exists in `ItemType.java:26`.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/backpack/
├── BackpackModule.java             # ReloadableModule: lifecycle, GUI open/save, PDC I/O, item checks
├── BackpackMechanic.java           # AbilityMechanic "OPEN_BACKPACK": right-click opens the GUI
├── BackpackListener.java           # InventoryCloseEvent (save) + InventoryClickEvent (no nesting)
└── BackpackInventoryHolder.java    # InventoryHolder linking the GUI to the source item + slot
```

### `BackpackModule` (`BackpackModule.java:22`)

Implements `org.nakii.valmora.api.ReloadableModule`. The only module-level state is the plugin reference and the `BackpackListener` field (`BackpackModule.java:24-25`).

- **`onEnable()`** (`BackpackModule.java:31-36`) — creates and registers `BackpackListener` with the plugin manager, then registers a fresh `BackpackMechanic(this)` into `plugin.getAbilityManager().getMechanicRegistry()`.
- **`onDisable()`** (`BackpackModule.java:38-44`) — unregisters the listener via `HandlerList.unregisterAll(listener)` and nulls the field. Note: it does **not** remove the registered mechanic from the `MechanicRegistry` (see [Unfinished Things](#unfinished-things--todos)).
- **`getId()`** returns `"backpacks"` (`BackpackModule.java:47`); **`getName()`** returns `"Backpack System"` (`BackpackModule.java:50`).
- **Public API used by the listener/mechanic:**
  - `openBackpack(Player, ItemStack backpackItem, int inventorySlot)` (`BackpackModule.java:52-67`)
  - `saveContents(ItemStack backpackItem, Inventory inv)` (`BackpackModule.java:69-83`)
  - `getBackpackSize(ItemStack item)` (`BackpackModule.java:85-90`)
  - `isBackpack(ItemStack item)` (`BackpackModule.java:92-97`)
- **Private serialization helpers:** `loadContents` (`BackpackModule.java:99-111`), `serialize` (`BackpackModule.java:113-125`), `deserialize` (`BackpackModule.java:127-140`).

### `BackpackMechanic` (`BackpackMechanic.java:8`)

Implements `org.nakii.valmora.module.item.AbilityMechanic`. Registered under the id `OPEN_BACKPACK` (`BackpackMechanic.java:17`).

- `execute(ExecutionContext)` (`BackpackMechanic.java:20-29`):
  1. Resolves the caster via `context.getPlayerCaster()`; returns silently if not a player.
  2. Reads the player's **main hand** item: `player.getInventory().getItemInMainHand()`.
  3. Bails out if the held item is not a backpack (`module.isBackpack(item)`).
  4. Computes the held-item slot (`getHeldItemSlot()`) and calls `module.openBackpack(player, item, slot)`.

Because it only inspects the main hand, opening via off-hand or a non-held backpack is not supported.

### `BackpackListener` (`BackpackListener.java:11`)

Implements Bukkit `Listener`. Two handlers:

- **`onClose(InventoryCloseEvent)`** (`BackpackListener.java:19-29`) — fires for any inventory whose holder is a `BackpackInventoryHolder`. It persists the open GUI's contents back onto the source item (`module.saveContents(...)`) and then re-places the item into the player's inventory at the stored `sourceSlot` (`player.getInventory().setItem(holder.getSourceSlot(), holder.getSourceItem())`).
- **`onClick(InventoryClickEvent)`** (`BackpackListener.java:31-44`) — registered at `EventPriority.HIGH` with `ignoreCancelled = true`. Only acts when the clicked inventory's holder is a `BackpackInventoryHolder`. If the **cursor** item is itself a backpack, the click is cancelled and the player gets `<red>You cannot place a backpack inside another backpack.` (`BackpackListener.java:36-43`). This prevents backpack nesting, but only for drag/cursor placement — see [Unfinished Things](#unfinished-things--todos).

### `BackpackInventoryHolder` (`BackpackInventoryHolder.java:8`)

Implements `InventoryHolder`. Immutable reference holder storing:

- `player` — the opening player (`BackpackInventoryHolder.java:10`),
- `sourceItem` — the `ItemStack` of the backpack in the player's inventory (`BackpackInventoryHolder.java:11`),
- `sourceSlot` — the inventory slot index the backpack occupies (`BackpackInventoryHolder.java:12`),
- `inventory` — the created GUI, set via `setInventory` right after creation (`BackpackInventoryHolder.java:28`).

The holder is what lets the `InventoryCloseEvent` handler know *which* item and *where* to write the saved contents back to.

---

## Architecture & Key Classes

### Opening flow (right-click)

```
Player right-clicks backpack (a registered Valmora item)
   │
   ├─ AbilityListener.onPlayerInteract()                     src/.../item/AbilityListener.java:24-50
   │     • only EquipmentSlot.HAND
   │     • item must carry ITEM_ID_KEY → ItemRegistry lookup
   │     • AbilityExecutor.fire(player, definition, RIGHT_CLICK, null, false)
   │
   ├─ AbilityExecutor.fire()                                 src/.../item/AbilityExecutor.java:32-82
   │     • needs an active ValmoraProfile (session/profile)
   │     • if ability.target-range > 0 and no entity in line of sight → abort with
   │       "No target in range!" action bar, mechanic never runs        (AbilityExecutor.java:45-51)
   │     • condition check → cooldown check → mana cost check
   │     • runs each ConfiguredMechanic in order
   │
   └─ ConfiguredMechanic → BackpackMechanic.execute()        module/backpack/BackpackMechanic.java:20-29
         • context.getPlayerCaster() or abort
         • getItemInMainHand(); must pass module.isBackpack()
         • slot = getHeldItemSlot()
         • module.openBackpack(player, item, slot)
```

### `openBackpack()` internals (`BackpackModule.java:52-67`)

1. `int size = getBackpackSize(backpackItem)` — reads `Keys.BACKPACK_SIZE_KEY` (int) from the item's PDC; `null`/`<= 0` → 27, clamped to max 54 (`BackpackModule.java:85-90`).
2. GUI title is **hard-coded**: `Formatter.format("<dark_gray>🎒 Backpack")` (`BackpackModule.java:54`).
3. A `BackpackInventoryHolder(player, backpackItem, inventorySlot)` is created and used to build `Bukkit.createInventory(holder, size, title)` (`BackpackModule.java:56-57`).
4. Existing contents are loaded via `loadContents(backpackItem, size)` and copied into the GUI (`BackpackModule.java:61-64`).
5. `player.openInventory(inv)` shows the GUI.

### Saving flow (GUI close)

```
InventoryCloseEvent                                          module/backpack/BackpackListener.java:19-29
   │ holder instanceof BackpackInventoryHolder
   ├─ module.saveContents(holder.getSourceItem(), event.getInventory())
   │     • copies every slot of the GUI into an ItemStack[]      BackpackModule.java:69-75
   │     • serialize() → byte[] via BukkitObjectOutputStream      BackpackModule.java:113-125
   │     • writes Keys.BACKPACK_CONTENTS_KEY (BYTE_ARRAY) onto
   │       the source item's meta and re-applies it               BackpackModule.java:79-82
   └─ player.getInventory().setItem(holder.getSourceSlot(), holder.getSourceItem())
```

Saving happens **only** on `InventoryCloseEvent`. There is no periodic auto-save and no plugin-shutdown hook; if the server stops while a backpack GUI is open, the last close was the only persistence point.

### Listener logic detail

- The click guard only inspects `event.getCursor()` (`BackpackListener.java:36`). A **shift-click** from the player's inventory into the GUI moves `event.getCurrentItem()` while `getCursor()` stays air, so a backpack can still be shift-clicked in — the anti-nesting rule is bypassable (see [Unfinished Things](#unfinished-things--todos)).
- The click guard fires at `HIGH` priority and respects `ignoreCancelled`, so it coexists with other inventory systems.
- The close handler always writes back to `holder.getSourceSlot()`. If the player moved or dropped the source backpack while the GUI was open, the write targets the stale slot (and may resurrect the item / overwrite whatever now sits there).

### Item type marker & PDC keys

The module relies on three PDC keys (all defined in `src/main/java/org/nakii/valmora/util/Keys.java`):

| Key | Type | Written by | Read by |
|---|---|---|---|
| `Keys.ITEM_TYPE_KEY` (`valmora:item_type`) | STRING | `ItemFactory.create()` for items with `item-type:` YAML (`ItemFactory.java:33-35`); value = `ItemType.name()` (e.g. `BACKPACK`) | `isBackpack()` (`BackpackModule.java:95-96`); `ItemFactory.updateLore()` type tag (`ItemFactory.java:191-194`) |
| `Keys.ITEM_ID_KEY` (`valmora:valmora_item_id`) | STRING | `ItemFactory.create()` (`ItemFactory.java:31`) | `AbilityListener` (item lookup), `ItemManager` |
| `Keys.BACKPACK_CONTENTS_KEY` (`valmora:backpack_contents`) | BYTE_ARRAY | `saveContents()` (`BackpackModule.java:81`) | `loadContents()` (`BackpackModule.java:101-103`) |
| `Keys.BACKPACK_SIZE_KEY` (`valmora:backpack_size`) | INTEGER | **never written anywhere in the codebase** | `getBackpackSize()` (`BackpackModule.java:87-89`) |

The `BACKPACK` value exists in `ItemType` (`src/main/java/org/nakii/valmora/module/item/ItemType.java:26`) and is accepted by the item parser's `item-type:` field (`ItemDefinitionParser.java:44-52`).

### Lifecycle & reload behavior

Registration order in `src/main/java/org/nakii/valmora/Valmora.java`:

```java
moduleManager.registerModule(backpackModule);     // Depends on abilityManager for mechanic
moduleManager.registerModule(quiverModule);       // ...
moduleManager.registerModule(progressionModule);  // ...
```
(`Valmora.java:220`; instantiated at `Valmora.java:182`, field at `Valmora.java:121`, import at `Valmora.java:60`.)

`/valmora reload` runs `ModuleManager.reloadModules()`, which disables modules in **reverse** order then enables them in **forward** order. Relevant ordering:

- **Disable:** `ability` is disabled *after* `backpacks` (reverse order). `AbilityManager.onDisable()` clears the whole `MechanicRegistry` (`AbilityManager.java:32`), so the stale `OPEN_BACKPACK` registration is swept away even though `BackpackModule.onDisable()` doesn't unregister it itself.
- **Enable:** `ability` is enabled *before* `backpacks`. `AbilityManager.onEnable()` re-registers all built-in mechanics (`AbilityManager.java:22`, `50-65`), then `BackpackModule.onEnable()` re-registers `OPEN_BACKPACK` (`BackpackModule.java:35`). Order is therefore always correct after a full reload.

Caveat: a **targeted** reload of just the ability module (e.g. `ModuleManager.reloadModule("abilities")`, the pattern used by `/item reload` at `ItemCommand.java:136`) clears the registry but never re-registers `OPEN_BACKPACK`, because that registration lives in the backpack module's `onEnable()` — backpacks would silently stop opening until `backpacks` is also reloaded.

---

## Configuration (YAML)

**The module ships no YAML of its own.** There is:

- no `backpacks/` resource folder (`saveAllResources()` in `Valmora.java:456-490` copies resource folders such as `items/`, `mobs/`, `guis/`, … — `backpack` is not among them);
- no `backpack:` section in `src/main/resources/config.yml` (a repo-wide grep for `backpack` finds no YAML matches);
- no example backpack item in `src/main/resources/items/*.yml` (grep for `BACKPACK` in resources returns nothing).

The only "configuration" is the **item definition YAML** in `plugins/Valmora/items/*.yml` that a server admin writes to define a backpack item. Relevant fields (see the item schema in `docs/VALMORA_DOCUMENTATION.md:983-1013` and `src/main/java/org/nakii/valmora/module/item/ItemDefinitionParser.java`):

| Field | Meaning for backpacks | Source |
|---|---|---|
| `item-type: BACKPACK` | Marks the item as a backpack (drives `isBackpack()` via PDC `item_type`) | `ItemDefinitionParser.java:44-52` → `ItemFactory.java:33-35` |
| `abilities.<id>.trigger: RIGHT_CLICK` | Opens the backpack on right-click | `AbilityListener.java:24-50` |
| `abilities.<id>.mechanics: [{type: OPEN_BACKPACK}]` | Invokes `BackpackMechanic` | `MechanicParser.java:33-56`, `BackpackMechanic.java:17` |
| `abilities.<id>.target-range` | Must be `0` (or omitted, default `0.0`) so no target entity is required to open | `AbilityExecutor.java:45-51`, `ItemDefinitionParser.java:113` |
| `abilities.<id>.cooldown` / `mana-cost` | Optional gates on how often / whether opening is possible | `AbilityExecutor.java:58-76` |

There is currently **no YAML key for the backpack capacity**: `Keys.BACKPACK_SIZE_KEY` is never populated by any loader, so every backpack in practice opens as 27 slots. See [Unfinished Things](#unfinished-things--todos).

---

## Data Model / Persistence

Backpacks are **fully item-persistent** — nothing is stored in the database (`database.db` / MySQL via `DataStore`). The `backpacks` module never touches the data layer.

Persistence is two PDC tags on the backpack item:

- `valmora:backpack_contents` — `byte[]` produced by Bukkit's `BukkitObjectOutputStream` (`BackpackModule.java:113-125`). The payload is: an `int` length followed by one serialized `ItemStack` per slot (`writeInt(contents.length)`, then `writeObject(item)` for each). Deserialization is symmetric with `BukkitObjectInputStream` (`BackpackModule.java:127-140`).
- `valmora:backpack_size` — `int` intended to hold the GUI row count; read at open time, clamped to `[1..54]`, defaulting to `27` (`BackpackModule.java:85-90`). Currently never written (see below).

`loadContents` normalizes the stored array to the GUI's current size: if the stored length differs, it copies `Math.min(stored, size)` items into a fresh array and pads the rest with `null` (`BackpackModule.java:104-110`).

Consequences of item-based storage:

- Contents survive server restarts because the item itself is persisted (chest, inventory, drops).
- A backpack can be traded, stored, or dropped — including **with its contents**.
- Any player who obtains the item sees everything inside (no private-owner encryption).
- Item serialization limits apply: contents are ordinary `ItemStack`s; anything Bukkit can serialize will store. Malformed byte arrays (e.g. from another plugin version) are caught and logged as warnings, yielding an empty backpack (`BackpackModule.java:136-138`).

---

## API Exposed

The module is **not** part of the `ValmoraAPI` interface. `src/main/java/org/nakii/valmora/api/ValmoraAPI.java` has no `getBackpackModule()` / `getBackpackManager()` entry point. The plugin exposes a concrete getter on the `Valmora` class:

```java
public BackpackModule getBackpackModule() { return backpackModule; }   // Valmora.java:429
```

Public surface on `BackpackModule` (all usable by other modules / commands):

| Method | Signature | Purpose |
|---|---|---|
| `openBackpack` | `openBackpack(Player player, ItemStack backpackItem, int inventorySlot)` (`BackpackModule.java:52`) | Opens the storage GUI for a backpack item |
| `saveContents` | `saveContents(ItemStack backpackItem, Inventory inv)` (`BackpackModule.java:69`) | Serializes an inventory into the item's PDC |
| `getBackpackSize` | `getBackpackSize(ItemStack item)` (`BackpackModule.java:85`) | Resolves GUI capacity (default 27, max 54) |
| `isBackpack` | `isBackpack(ItemStack item)` (`BackpackModule.java:92`) | True if item PDC `item_type` == `BACKPACK` |
| `getId` / `getName` | — (`BackpackModule.java:47-50`) | Module identity |

The mechanic id `OPEN_BACKPACK` is itself part of the API surface usable from item YAML. It is resolved through `MechanicRegistry` (`MechanicRegistry.java:10-17`, case-insensitive keys uppercased at registration).

---

## Dependencies & Consumers

### What the backpack module uses

| Dependency | How | Source |
|---|---|---|
| `AbilityManager` (ability module, loads earlier) | Registers `BackpackMechanic` into its `MechanicRegistry` in `onEnable` | `BackpackModule.java:35` |
| `AbilityMechanic` / `ExecutionContext` | The mechanic contract + `getPlayerCaster()` | `BackpackMechanic.java:5, 20-21` |
| Item module (`ItemType`, `ItemFactory`, `AbilityListener`, `AbilityExecutor`) | Defines/crafts the backpack items and drives the right-click trigger | `ItemType.java:26`, `ItemFactory.java:31-35`, `AbilityListener.java:24-50`, `AbilityExecutor.java:32-82` |
| `Keys` (util) | PDC key constants | `Keys.java:39-40, 75-76` |
| `Formatter` (util) | MiniMessage GUI title + player message | `BackpackModule.java:54`, `BackpackListener.java:40-41` |

Load-order note: `backpacks` is registered after `ability` (`Valmora.java:196` vs `Valmora.java:220`), which is what makes the mechanic registration safe across hot reloads.

### What consumes the backpack module

- **`Valmora`** — the only direct consumer: field, instantiation, registration, and the `getBackpackModule()` getter (`Valmora.java:121, 182, 220, 429`).
- **Item definitions in YAML** — any item whose `mechanics` list includes `OPEN_BACKPACK` invokes the mechanic at runtime.
- **No other module or command** references the backpack package (repo-wide grep for `BackpackModule`/`BackpackManager`/`backpack` matches only the module itself, `Valmora.java`, `Keys.java`, and `ItemType.java`).

---

## Unfinished Things / TODOs

There are no literal `TODO`/`FIXME` comments inside the backpack package, but several functional gaps are evident from the code:

1. **`BACKPACK_SIZE_KEY` is dead** — it is defined (`Keys.java:76`) and read (`BackpackModule.java:87-89`), but nothing in the entire codebase ever writes it. Every backpack is effectively 27 slots regardless of item definition; there is no way to configure a larger or smaller backpack.
2. **No example backpack item shipped** — `src/main/resources/items/` contains no `BACKPACK` item, so the feature is only usable if an admin authors one from scratch (no documentation exists for it either; `docs/VALMORA_DOCUMENTATION.md` has no backpack section).
3. **Anti-nesting is incomplete** — `onClick` only rejects a backpack on the **cursor** (`BackpackListener.java:36`). Shift-clicking a backpack from the player's inventory into the open GUI bypasses the check (`event.getCurrentItem()` is never inspected).
4. **Mechanic not unregistered in `onDisable()`** — `BackpackModule.onDisable()` clears only the listener (`BackpackModule.java:38-44`). Correct full-reload behavior depends on `AbilityManager.onDisable()` clearing the registry (`AbilityManager.java:32`); a targeted ability-module reload drops `OPEN_BACKPACK` permanently until `backpacks` is reloaded too.
5. **Stale-slot save** — the close handler writes to `holder.getSourceSlot()` and re-places the item there (`BackpackListener.java:25-28`) without verifying the slot still holds the backpack. Moving/dropping the item while the GUI is open can overwrite the wrong inventory slot or resurrect the item.
6. **Hard-coded GUI title** — `"<dark_gray>🎒 Backpack"` is not configurable (`BackpackModule.java:54`).
7. **No ownership/permission model** — `isBackpack()` is the only gate (`BackpackModule.java:92-97`); any player holding the item can open and modify its contents.
8. **Single open path** — opening requires a registered Valmora item with a `RIGHT_CLICK` ability; vanilla items can never be backpacks, and there is no command like `/backpack open` as an alternative.
9. **Save only on close** — no auto-save, no plugin-shutdown flush for an open backpack GUI.
10. **Mechanic ignores params** — `BackpackMechanic.execute` reads no configuration from `context.getParams()` (`BackpackMechanic.java:20-29`); `AbilityExecutor` passes an empty `MemoryConfiguration` (`AbilityExecutor.java:53`).

---

## Possible Improvements / Changes

Concrete, code-grounded suggestions:

1. **Wire `backpack_size` from YAML.** Extend `ItemDefinition`/`ItemDefinitionParser` with e.g. `backpack-size: 36` and have `ItemFactory.create()` write it to `Keys.BACKPACK_SIZE_KEY` (`ItemFactory.java:33-35`). Backpack sizes would then be declarative per item. Validate `1..54` in the parser.
2. **Ship an example.** Add a `resources/items/backpacks.yml` with a couple of tiered backpacks (e.g. 9/27/54 via the field from #1) so admins have a copy-paste starting point, and mirror it in `saveAllResources()` (`Valmora.java:469-479`).
3. **Close the shift-click nesting hole.** In `onClick` (`BackpackListener.java:31-44`), also cancel when `event.isShiftClick()` and `module.isBackpack(event.getCurrentItem())`.
4. **Unregister the mechanic properly.** Track the `BackpackMechanic` in the module and remove it from the registry in `onDisable()` (`BackpackModule.java:38-44`), making the module self-cleaning and tolerant of targeted ability reloads.
5. **Make the save robust.** On close, verify `player.getInventory().getItem(sourceSlot)` still equals the source item before writing; otherwise search for the item or drop the saved stack near the player (`BackpackListener.java:25-28`).
6. **Add an admin command.** A `/backpack give <id> [player]` or an `OPEN_BACKPACK`-invoking helper in `ItemCommand` would make backpacks testable without hand-authoring ability YAML.
7. **Expose via `ValmoraAPI`.** Add `getBackpackModule()` to `ValmoraAPI` (`ValmoraAPI.java:9-70`) and implement in `Valmora` (`Valmora.java:429` currently lives outside the interface) so sibling modules can open/inspect backpacks through the API contract.
8. **Optional ownership layer.** Add an `owner` UUID PDC tag written on first open and enforced on open, or a permission (`valmora.backpack.<id>`) — currently any holder is a full editor.
9. **Configurable title / sounds.** Read the GUI title (and optionally an open sound) from the item YAML, passed through `context.getParams()` to the mechanic, instead of the hard-coded emoji title (`BackpackModule.java:54`).
10. **Graceful size migrations.** When `loaded.length != size`, today it silently truncates/pads (`BackpackModule.java:104-110`); consider logging when truncation drops items, or offering an expand-only policy so shrinking a backpack never destroys contents.
