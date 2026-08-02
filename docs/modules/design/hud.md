# HUD Item Module — Design & Code

> **Module ID:** `hud` | **Module Name:** "HUD Items" | **Package:** `org.nakii.valmora.module.hud`
> **Files:** `HudItemModule.java`, `HudItemDefinition.java`, `HudItemListener.java`
> **Resource folder:** `hud-items/*.yml` (extracted to `plugins/Valmora/hud-items/`)

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

The HUD module renders persistent **HUD buttons** into a player's inventory (typically the hotbar) as physical `ItemStack`s. These items are not normal inventory content — they are meant to look and behave like RPG-HUD buttons: the player cannot move, drop, or lose them, and right/left-clicking a configured slot fires a **scripted action** written in the Valmora event DSL (`src/main/java/org/nakii/valmora/module/hud/HudItemListener.java:63`).

The module is deliberately small: it is a thin wrapper that

1. Loads definitions from `hud-items/*.yml` (`HudItemModule.java:85`),
2. Materializes them as `ItemStack`s stamped with a persistent-data marker (`HudItemModule.java:126`),
3. Re-places them in the correct inventory slots on join, respawn, and module enable (`HudItemModule.java:45`),
4. Intercepts inventory/drop/death events so the items can never be lost or moved (`HudItemListener.java:36`), and
5. Executes the compiled click actions through the Script engine's `CompiledEvent` (`HudItemListener.java:72`).

There is no continuous rendering or per-tick update — a HUD item only changes when the definition is re-parsed (module reload). "HUD" here means *persistent hotbar button*, not a ticking overlay.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/hud/
├── HudItemModule.java        # ReloadableModule + registry + loader + item factory (143 lines)
├── HudItemDefinition.java    # Immutable per-item value object (31 lines)
└── HudItemListener.java      # Event wiring: join/respawn/click/drop/death (80 lines)
```

### Registering the module (`Valmora.java`)

- Field: `private HudItemModule hudItemModule;` — `Valmora.java:115`
- Instantiation: `this.hudItemModule = new HudItemModule(this);` — `Valmora.java:176`
- Registration: `moduleManager.registerModule(hudItemModule); // Depends on scriptModule for click DSL` — `Valmora.java:214`
- Concrete getter (not part of the `ValmoraAPI` interface): `Valmora.java:423`
- Resource extraction: `name.startsWith("hud-items/")` is included in `saveAllResources()` so example files are copied into `plugins/Valmora/hud-items/` on first boot (only if the file does not already exist) — `Valmora.java:475`, copy logic `Valmora.java:481`.

The module is registered **after** `scriptModule` (needed to compile the click DSL) and after `notifyModule` (which registers the `notify`/`notifyall` DSL events used by the default config). See [Dependencies & Consumers](#dependencies--consumers).

---

## Architecture & Key Classes

### 3.1 `HudItemModule` — module, registry, loader, item factory

`HudItemModule.java:25`

**State** (fields, `HudItemModule.java:27`):

| Field | Type | Purpose |
|---|---|---|
| `definitions` | `Map<String, HudItemDefinition>` | Definitions keyed by YAML definition ID |
| `bySlot` | `Map<Integer, HudItemDefinition>` | Definitions keyed by inventory slot (for click routing) |
| `listener` | `HudItemListener` | The registered Bukkit listener |

**Lifecycle:**

- `onEnable()` — `HudItemModule.java:37`
  1. Clears both maps (idempotent reload safety).
  2. `loadDefinitions()` — parses every `hud-items/*.yml` and fills both maps.
  3. Creates `HudItemListener` and registers it with the plugin manager.
  4. Re-gives items to every currently online player via `giveHudItems`.
- `onDisable()` — `HudItemModule.java:50`
  1. `HandlerList.unregisterAll(listener)` and nulls the field (mandatory per AGENTS.md §6.2).
  2. Clears both maps.
  3. **Does NOT** strip HUD items from players' inventories — see [Unfinished Things](#unfinished-things--todos).
- `getId()` returns `"hud"`, `getName()` returns `"HUD Items"` — `HudItemModule.java:61` / `HudItemModule.java:64`.

**Loading — `loadDefinitions()`** — `HudItemModule.java:85`

```java
YamlLoader<HudItemDefinition> loader = new YamlLoader<>(plugin, "hud-items", "HUD Item");
loader.load(this::parseDefinition, def -> {
    definitions.put(def.getId(), def);
    bySlot.put(def.getSlot(), def);
});
```

- Uses `YamlLoader.load(...)` (the **multi-definition-per-file** variant, unlike `loadFilesAsSections`). Every top-level key in every `*.yml` under `hud-items/` is parsed as one HUD item — `infrastructure/config/YamlLoader.java:53`.
- Failed definitions are collected and logged as warnings; they do not abort the module — `infrastructure/config/YamlLoader.java:56` and `YamlLoader.java:113`.
- On registration, the same definition is indexed twice: by ID (string) and by slot (int). A later definition using an already-taken slot silently **overwrites** the `bySlot` entry (but not `definitions`) — see [Unfinished Things](#unfinished-things--todos).

**Parsing — `parseDefinition(...)`** — `HudItemModule.java:93`

Returns a `LoadResult<HudItemDefinition, String>` (`api/config/LoadResult.java:8`); any parse exception is converted into a `LoadResult.failure(...)` with the file path, so one bad file cannot crash the module — `HudItemModule.java:139`.

Parsing order:

1. `slot` — default **8** (`HudItemModule.java:95`).
2. `prevent-move` — default **true** (`HudItemModule.java:96`). *Parsed and stored, but never enforced — see below.*
3. `glow` — default **false** (`HudItemModule.java:97`).
4. `item` section — **required**; missing it is a hard parse failure (`HudItemModule.java:99`).
5. `material` — default **`STONE`**, resolved via `Material.matchMaterial(...)`; invalid material is a hard parse failure (`HudItemModule.java:104`).
6. Item meta assembly (`HudItemModule.java:111`):
   - `name` → `meta.displayName(Formatter.format(...))` (MiniMessage) — `HudItemModule.java:113`. `Formatter` uses a `MiniMessage` builder whose post-processor forces `ITALIC = FALSE`, so names/lore never render italic — `util/Formatter.java:11`.
   - `lore` → `meta.lore(Formatter.formatList(...))` — `HudItemModule.java:116`.
   - `custom-model-data` → `meta.setCustomModelData(...)` only if the value is `> 0` — `HudItemModule.java:119`.
   - `glow` → fake enchant `Enchantment.UNBREAKING, 1, true` + `ItemFlag.HIDE_ENCHANTS` (classic "glint without showing enchants") — `HudItemModule.java:122`.
   - **Marker:** `meta.getPersistentDataContainer().set(Keys.HUD_ITEM_KEY, STRING, id)` — `HudItemModule.java:126`. This is the canonical "is this a HUD item?" signal used by every guard in the listener. The key is `hud_item_id` (`util/Keys.java:64`, field declared `Keys.java:28`).
7. Click DSL compilation (`HudItemModule.java:130`):
   - `on-right-click` → `plugin.getScriptModule().getEventParser().parseList(...)` if the section contains it, else a no-op `ctx -> {}` (`HudItemModule.java:131`).
   - `on-left-click` → same (`HudItemModule.java:134`).
   - Compilation happens **at load time**, so DSL typos are logged as warnings by `EventParser` (`module/script/event/EventParser.java:65`) rather than failing at click time.

**Item delivery — `giveHudItems(Player)`** — `HudItemModule.java:79`

```java
for (HudItemDefinition def : definitions.values()) {
    player.getInventory().setItem(def.getSlot(), def.getItem());
}
```

- Called from `onEnable()` for all online players (`HudItemModule.java:45`), on join (`HudItemListener.java:26`), on respawn (1 tick delayed, `HudItemListener.java:32`), and as the "restore" step when a drop is blocked (`HudItemListener.java:53`).
- `def.getItem()` returns a **clone** (`HudItemDefinition.java:28`), so every player gets an independent `ItemStack` (no shared mutable instance, and no PDC contamination across players).

**Identity check — `isHudItem(ItemStack)`** — `HudItemModule.java:74`

Returns `true` only when the item has meta and the PDC contains `Keys.HUD_ITEM_KEY` as a `STRING`. This is the only mechanism used to identify HUD items — no display-name matching (consistent with AGENTS.md §11.12).

**Lookup — `getBySlot(int)`** — `HudItemModule.java:70`

Used by the listener to resolve which definition (and thus which click actions) a click on a given slot maps to.

### 3.2 `HudItemDefinition` — immutable value object

`HudItemDefinition.java:6`

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Definition ID from YAML (also stored in the PDC marker) |
| `slot` | `int` | Inventory slot to render in |
| `preventMove` | `boolean` | Config intent — currently **unused by the listener** |
| `item` | `ItemStack` | The display item (cloned on access) |
| `onRightClick` | `CompiledEvent` | Right-click action (`api/scripting/CompiledEvent.java:8`) |
| `onLeftClick` | `CompiledEvent` | Left-click action |

All fields are `final` and exposed via getters (`HudItemDefinition.java:25`). `getItem()` returns `item.clone()` (`HudItemDefinition.java:28`).

### 3.3 `HudItemListener` — event wiring

`HudItemListener.java:16`. Registered once in `onEnable()`, fully unregistered in `onDisable()`.

**Handlers:**

| Event | Priority | Handler | Behavior |
|---|---|---|---|
| `PlayerJoinEvent` | default | `onJoin` — `HudItemListener.java:24` | `giveHudItems(player)` so HUD buttons appear immediately on login |
| `PlayerRespawnEvent` | default | `onRespawn` — `HudItemListener.java:29` | Schedules `giveHudItems` **1 tick later** via `runTaskLater` so it runs *after* vanilla respawn inventory restore (which would otherwise overwrite the HUD slots) |
| `InventoryClickEvent` | `HIGH`, `ignoreCancelled = true` | `onInventoryClick` — `HudItemListener.java:36` | Blocks any move involving a HUD item; fires the click action when the click is on the item's registered slot in the player's own inventory |
| `PlayerDropItemEvent` | default | `onDrop` — `HudItemListener.java:48` | Cancels the drop and immediately re-places the item in its configured slot |
| `PlayerDeathEvent` | default | `onDeath` — `HudItemListener.java:57` | Strips HUD items from the drop list so they never spawn on the ground |

**Click handling — `onInventoryClick`** — `HudItemListener.java:36`

```java
if (module.isHudItem(event.getCurrentItem()) || module.isHudItem(event.getCursor())) {
    if (!shouldFireClickAction(event, player)) {
        event.setCancelled(true);
    }
}
```

- Guards on *both* the item being clicked (`getCurrentItem()`) and the item on the cursor (`getCursor()`), so HUD items cannot be taken out of, or dropped into, any inventory.
- Only fires when the click is on a HUD item; other clicks pass through untouched.

**Click routing — `shouldFireClickAction(...)`** — `HudItemListener.java:63`

1. Returns `false` (→ event cancelled, no action) if `getClickedInventory() != player.getInventory()` — i.e. clicks inside chests/other GUIs never trigger actions, they only block the move. `HudItemListener.java:65`
2. Looks up `module.getBySlot(event.getSlot())`; `null` → `false` (cancelled, no action). `HudItemListener.java:67`
3. Builds the execution context: `new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration())`. `HudItemListener.java:70`
   - Caster = the clicking player; location = the player's location; **no target**; **empty params section** (so `$param.*$` variables resolve to nothing — `module/script/variable/providers/ParamVariableProvider` will find no keys).
4. Routes by click type (`HudItemListener.java:72`):
   - `ClickType.RIGHT` or `ClickType.SHIFT_RIGHT` → `def.getOnRightClick().execute(ctx)`
   - **everything else** (left, shift-left, number-key swap, middle-click, double-click, …) → `def.getOnLeftClick().execute(ctx)`
5. `event.setCancelled(true)` and returns `true`. `HudItemListener.java:77`

Note that the cancelled flag is always set when an action fires, and the outer block also cancels when `shouldFireClickAction` returns `false` — so **any interaction with a HUD item is always cancelled**, actions just decide whether something *extra* runs.

**Context notes** (`api/execution/SimpleExecutionContext.java:13`):

- `getVariableResolver()` and `getTagService()` delegate to the live `ScriptModule` instances (`SimpleExecutionContext.java:52`), so all default script variables and tags are available inside click actions: `$player.*$`, `$world.*$`, `$server.*$`, `$time.*$`, etc.
- `$target.*$` and `$param.*$` produce nothing (no target, empty params) — `SimpleExecutionContext.java:27`.
- The context is created fresh per click and never stored — compliant with AGENTS.md §7.3.

---

## Configuration (YAML)

Loaded from every `*.yml` file under `plugins/Valmora/hud-items/` (example resources auto-extracted from the JAR on first run — `Valmora.java:475`). Format: **multiple definitions per file**, one per top-level key.

### Schema

| Key | Type | Default | Required | Explanation |
|---|---|---|---|---|
| `<id>` | string | — | yes | Definition ID. Also stored in the item's PDC as the `hud_item_id` marker and used for error messages. |
| `<id>.slot` | int | `8` | no | Inventory slot the item is rendered into (`0`–`8` = hotbar, `9`–`35` = main inventory). Injected with `PlayerInventory.setItem`. |
| `<id>.prevent-move` | bool | `true` | no | **Parsed and stored but currently NOT enforced.** The listener blocks every move/drop of a HUD item unconditionally (see [Unfinished Things](#unfinished-things--todos)). |
| `<id>.glow` | bool | `false` | no | When `true`, adds `Enchantment.UNBREAKING` level 1 with `ItemFlag.HIDE_ENCHANTS`, producing an enchanted glint with no visible enchant line. |
| `<id>.item` | section | — | **yes** | Item visual definition. Missing section → hard parse failure (`HudItemModule.java:99`). |
| `<id>.item.material` | string | `STONE` | no | Bukkit material name, resolved via `Material.matchMaterial`. Invalid value → hard parse failure (`HudItemModule.java:104`). |
| `<id>.item.name` | string | absent | no | Display name in **MiniMessage** format. If absent, the vanilla material name is shown. Rendered italic-free by `Formatter`. |
| `<id>.item.lore` | string list | absent | no | Lore lines in MiniMessage format. |
| `<id>.item.custom-model-data` | int | `0` | no | `CustomModelData` value; **only applied if `> 0`** (`HudItemModule.java:119`). |
| `<id>.on-right-click` | string list | absent | no | DSL event list executed on right-click (`ClickType.RIGHT` / `SHIFT_RIGHT`). Absent → no-op. Each line is one DSL string (see the Script DSL reference, `docs/VALMORA_DOCUMENTATION.md` §33). |
| `<id>.on-left-click` | string list | absent | no | DSL event list executed on any non-right click. Absent → no-op. |

### Bundled example — `src/main/resources/hud-items/default.yml`

**`menu_button`** (`default.yml:1`)

```yaml
menu_button:
  slot: 8
  prevent-move: true
  glow: true
  item:
    material: NETHER_STAR
    name: "<gold><bold>✦ Menu"
    lore:
      - "<gray>Right-click to open the menu"
  on-right-click:
    - "notifyall io:actionbar <yellow>Menu coming soon!"
  on-left-click:
    - "notifyall io:actionbar <yellow>Menu coming soon!"
```

- Renders a glowing gold "✦ Menu" nether star in hotbar slot 8.
- Both clicks run `notifyall io:actionbar ...` (registered by `NotifyModule` — `module/notify/NotifyModule.java:30`), broadcasting the text to **all online players** via the actionbar IO (`module/notify/NotifyAllEvent.java:14`).

**`profile_button`** (`default.yml:15`)

```yaml
profile_button:
  slot: 7
  prevent-move: true
  glow: false
  item:
    material: PLAYER_HEAD
    name: "<aqua><bold>⚔ Profile"
    lore:
      - "<gray>Right-click to view your profile"
  on-right-click:
    - "notifyall io:actionbar <aqua>Profile coming soon!"
```

- Renders an un-glowing aqua "⚔ Profile" player head in hotbar slot 7.
- Only a right-click action is defined; left-click is a no-op.
- Note: `PLAYER_HEAD` has no texture set, so it renders as the default Steve head.

### Notes & edge cases

- **Multiple files / multiple defs:** any number of files and any number of defs per file are supported (`YamlLoader.load`, `infrastructure/config/YamlLoader.java:47`).
- **Parse failures** are logged as warnings like `Failed to load some HUD Item. Please check your configuration files.` followed by the per-file errors (`YamlLoader.java:113`). The offending definition is skipped; the rest still load.
- **Slot conflicts** are not validated: if two defs declare the same slot, `bySlot` keeps the **last** one registered (file order), while `giveHudItems` sets both in iteration order (so the visually last-set item wins in-game).
- **No format placeholders:** the DSL strings and MiniMessage text are parsed verbatim at load time; per-player substitution relies on script variables (`$player.x$`) inside the event DSL, not on placeholders in the item name/lore.

---

## Data Model / Persistence

There is **no database persistence** for HUD items. The module is fully in-memory and rebuildable:

- **Definitions** live only in `HudItemModule.definitions` / `bySlot` and are rebuilt on every `onEnable()` (`HudItemModule.java:38`).
- **Inventory state** is transient: items are re-issued from the definitions on join, respawn, and module enable. Nothing is stored per player.
- **The only durable artifact** is the PDC marker on each rendered item: `Keys.HUD_ITEM_KEY` (NamespacedKey `valmora:hud_item_id`) set to the definition ID — `HudItemModule.java:126`. It is what makes an item *recognized* as a HUD item by `isHudItem` (`HudItemModule.java:74`) and by all listener guards.
- **No per-player toggles, cooldowns, or customization** are tracked anywhere; click behavior is identical for every player and purely definition-driven.

---

## API Exposed

HUD is **not** exposed through the `ValmoraAPI` interface (`api/ValmoraAPI.java:9` has no HUD accessor). The only public handle is on the concrete plugin class:

```java
Valmora.getInstance().getHudItemModule();   // Valmora.java:423
```

Public surface of `HudItemModule` (`HudItemModule.java:66`):

| Method | Signature | Notes |
|---|---|---|
| `getDefinitions()` | `Collection<HudItemDefinition>` | Live view over `definitions.values()` (not copied). |
| `getBySlot(int)` | `HudItemDefinition` | Slot → definition lookup; `null` if unregistered. |
| `isHudItem(ItemStack)` | `boolean` | PDC marker check. |
| `giveHudItems(Player)` | `void` | Re-places all defined items into the player's inventory. |

`HudItemDefinition` exposes `getId()`, `getSlot()`, `isPreventMove()`, `getItem()` (cloned), `getOnRightClick()`, `getOnLeftClick()` — `HudItemDefinition.java:25`.

---

## Dependencies & Consumers

### Dependencies

| Dependency | How | Reference |
|---|---|---|
| **ScriptModule** (`scriptModule`) | Click actions are compiled through `plugin.getScriptModule().getEventParser().parseList(...)` at load time. The module is registered *after* script (registration comment at `Valmora.java:214`). | `HudItemModule.java:130` |
| **Script DSL / EventFactory registry** | `EventParser` resolves each DSL event name against `scriptModule`'s `EventFactory` registry at compile time; unknown events log a warning and compile to a no-op. | `module/script/event/EventParser.java:62` |
| **NotifyModule** (indirect, DSL-level) | The bundled config uses `notifyall`, which is registered by `NotifyModule` in its `onEnable()` — `module/notify/NotifyModule.java:30`. Notify is registered before HUD (`Valmora.java:212` vs `Valmora.java:214`), so the event resolves. Other modules registering their own DSL events (e.g. `give`, `teleport`, `statmodify`) must also be enabled before HUD to be usable in click actions. | `module/notify/NotifyModule.java:29` |
| **Keys** | `Keys.HUD_ITEM_KEY` must be initialized (done in `Valmora.onEnable()` via `Keys.init(this)` before module enable) for `isHudItem` to work. | `util/Keys.java:42`, `Valmora.java:138` |
| **YamlLoader** | Generic multi-section YAML loader. | `infrastructure/config/YamlLoader.java:37` |
| **ExecutionContext / SimpleExecutionContext** | Click actions receive a `SimpleExecutionContext`; script variables/tags resolve through the live `ScriptModule`. | `HudItemListener.java:70`, `api/execution/SimpleExecutionContext.java:52` |

### Consumers

There are **no consumers** — no other module, command, or API caller references `HudItemModule`, `getHudItemModule()`, `getDefinitions()`, `getBySlot()`, or `isHudItem()` outside the HUD package itself and `Valmora.java` (verified by grep). The module is currently a leaf: it consumes Script + Notify and exposes nothing back to the rest of the engine.

---

## Unfinished Things / TODOs

1. **`prevent-move` is parsed but not enforced.** It is read (`HudItemModule.java:96`), stored (`HudItemModule.java:138`), and exposed via `isPreventMove()` (`HudItemDefinition.java:27`) — but `HudItemListener` never checks it. All HUD-item moves and drops are blocked unconditionally regardless of the flag. Either honor the flag or remove it.
2. **Stale items after reload/disable.** `onDisable()` clears the maps but never strips HUD items from online players' inventories (`HudItemModule.java:50`). After `/valmora reload`, definitions are rebuilt and re-given (`HudItemModule.java:45`), so old defs leave orphaned, un-clickable-but-still-blocked items in old slots (a definition removed from YAML keeps its stale item on every player). This also means two sequential reloads can leave duplicates.
3. **No `ValmoraAPI` accessor.** Other modules can only reach HUD via the concrete `Valmora.getInstance()` cast — inconsistent with every other subsystem exposed on `ValmoraAPI`.
4. **Slot collision / range not validated.** Two defs on the same slot: `bySlot` silently keeps the last, and `giveHudItems` sets both (last set wins). Slots outside `0`–`35` (e.g. `36`+) will target non-existent inventory slots and silently no-op in-game.
5. **Click context is minimal.** `SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration())` (`HudItemListener.java:70`) means no target and an empty `params` section, so `$target.*$` and `$param.*$` variables resolve to nothing in click actions. If richer click scripts are wanted (e.g. per-item parameters, click cooldowns), the context must be extended.
6. **No click cooldown, sound, or visual feedback** beyond whatever the DSL actions produce.
7. **Left-click fallback semantics:** any non-right `ClickType` (including `NUMBER_KEY`, `MIDDLE`, `DOUBLE_CLICK`) routes to `on-left-click` (`HudItemListener.java:72`), which may surprise players pressing a number key over the slot.
8. **Default config is placeholder:** both bundled actions just broadcast "coming soon" via `notifyall` (`default.yml:11`, `default.yml:24`); the "Menu"/"Profile" buttons do not actually open anything yet.

---

## Possible Improvements / Changes

- **Strip HUD items in `onDisable()`** (iterate `Bukkit.getOnlinePlayers()`, remove the slots in the old `bySlot`) so reloads never leak stale buttons; makes the module fully reversible.
- **Honor or drop `prevent-move`** — wire it into `shouldFireClickAction`/`onDrop`, or delete the field and its YAML key to avoid config that silently does nothing.
- **Validate slots on load:** range-check `0–35` (or `0–44` if armor/offhand support is intended) and log a warning on slot collisions instead of silently overwriting.
- **Expose `getHudItemModule()` on `ValmoraAPI`** for parity with other modules.
- **Extend the click context** with a real `params` section (per-definition YAML params) and a self-target so click scripts can use `$param.*$` / `$target.*$`.
- **Add a re-give/clear command** (e.g. `/valmora hud refresh`) following the "commands only in `Valmora.onEnable()`" rule (AGENTS.md §6.3).
- **Per-player support:** per-player visibility flags, click cooldowns, or a toggle key — would require per-player state in `HudItemModule` cleared on `onDisable()`.
- **Guard against non-player-click `ClickType`s** (route only `LEFT`/`SHIFT_LEFT` to the left action, cancel the rest) for predictable behavior.
- **Automatic click feedback** (e.g. a configurable click sound) and a `cooldown` per definition, tracked per player.

---

_Last updated: see git history. Source of truth: `src/main/java/org/nakii/valmora/module/hud/`, bundled config `src/main/resources/hud-items/default.yml`, wiring in `Valmora.java:214`._
