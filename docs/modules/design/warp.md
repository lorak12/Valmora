# Warp Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `warp` | **Source:** `src/main/java/org/nakii/valmora/module/warp/`

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

The Warp module provides **named teleport destinations** (`WarpDefinition`s) that players reach in two ways:

1. **Warp pads** — a set of exact block coordinates (`pad-locations`) configured per warp. A `PlayerMoveEvent` listener detects a player stepping onto a pad block and teleports them (`WarpListener.java`, `WarpManager.teleport`).
2. **The `/warp` command** — `/warp` with no arguments tries to open a GUI id `fast_travel`; `/warp <id>` teleports directly (`WarpCommand.java`).

Warps can be gated behind **unlock conditions** that are evaluated per-player against their active profile: `always`, `tag:<tag>`, or `skill:<skillId>:<level>` (`WarpManager.isUnlocked`, `WarpManager.java:27-52`).

The module is a `ReloadableModule` wrapping eight collaborating classes (there is **no `sign/` subpackage** — see §8):

```
WarpModule (lifecycle)
  ├── WarpLoader            — reads warps/*.yml via YamlLoader (multi-warp per file)
  ├── WarpManager           — case-insensitive Registry<WarpDefinition> + unlock checks + teleport
  ├── WarpListener          — PlayerMoveEvent → pad detection → teleport
  ├── WarpDefinition        — immutable warp value object
  ├── WarpCommand           — /warp [id] TabExecutor (registered in Valmora.java)
  ├── WarpEventFactory      — script event "warp_to <id>"
  └── WarpVariableProvider  — script variables $warp.<id>.name$ / $warp.<id>.unlocked$
```

Warps are **purely config-driven**. The module has no database table and no persistence of its own — definitions live in `plugins/Valmora/warps/*.yml`, auto-copied from the jar by `Valmora.saveAllResources` (`Valmora.java:474`) and re-parsed on every `onEnable()`/`/valmora reload`. Teleportation uses Paper's async `Player.teleportAsync` (`WarpManager.java:62`), consistent with AGENTS.md §11.19.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/warp/
├── WarpModule.java            # ReloadableModule — lifecycle (enable/disable/getId); owns manager + listener
├── WarpManager.java           # Registry<WarpDefinition>; isUnlocked(); teleport(); getWarpByPad()
├── WarpDefinition.java        # Immutable value object (id, display-name, coords, yaw/pitch, condition, pads)
├── WarpLoader.java            # YamlLoader<WarpDefinition> over plugins/Valmora/warps/*.yml (key = warp id)
├── WarpListener.java          # PlayerMoveEvent (MONITOR) — pad hit → teleport
├── WarpCommand.java           # /warp [id] — TabExecutor, registered in Valmora.java (executor only)
├── WarpEventFactory.java      # Script event factory, event name "warp_to"
└── WarpVariableProvider.java  # Script variable provider, namespace "warp"

src/main/resources/warps/
└── hub.yml                    # 5 example warps (all unlock-condition: always, 2×2 pads)

No sign/ subpackage exists. The only "sign" code in the codebase is the GUI sign-input
subsystem (src/main/java/org/nakii/valmora/module/gui/sign/SignInputManager.java,
SignInputListener.java), which is unrelated to warps. See §8.
```

Total source: ~322 lines across 8 files. The module ships no unit tests.

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `WarpModule.java`

Implements `ReloadableModule` (see `docs/MODULE_DEVELOPMENT.md` §2).

| Method | Behavior | Lines |
|---|---|---|
| constructor | Stores the `Valmora` plugin instance only — **no state in the constructor** | `WarpModule.java:13-15` |
| `onEnable()` | Logs; creates `WarpManager`; runs `WarpLoader.load()`; registers the `warp_to` event factory and the `warp` variable provider on the script module; creates `WarpListener` and registers it with the plugin manager | `WarpModule.java:18-26` |
| `onDisable()` | Logs; `HandlerList.unregisterAll(listener)`; clears the warp registry and nulls `warpManager` | `WarpModule.java:29-33` |
| `getId()` | `"warp"` | `WarpModule.java:35` |
| `getName()` | `"Warp System"` | `WarpModule.java:36` |
| `getWarpManager()` | Returns the live manager | `WarpModule.java:38` |

**Idempotency across `/valmora reload`:** the module intentionally builds a *fresh* `WarpManager` on every `onEnable()` (`WarpModule.java:20`) rather than reusing one, and `WarpLoader.load()` clears the registry first (`WarpLoader.java:23`). The script-module registrations (`WarpEventFactory`, `WarpVariableProvider`) are re-registered each enable; duplicates are not possible because `ScriptModule.onDisable()` clears both the event-factory and variable-provider registries (`ScriptModule.java:82-86`) and — since `script` is registered *before* `warp` — it is disabled *after* warp during a reverse-order teardown, so the registries are guaranteed empty by the time warp re-enables.

**Wiring in `Valmora.java`:**
- Field declaration at `Valmora.java:110`.
- Instantiated at `Valmora.java:171` (`new WarpModule(this)`).
- Registered with the `ModuleManager` at `Valmora.java:209` — **position 20**, after `npcModule` (`Valmora.java:208`) and before `questModule` (`Valmora.java:210`). `docs/MODULE_DEVELOPMENT.md:515` lists the rationale as "depends on zone, gui"; see §7 for what the code actually depends on.
- `/warp` executor registered at `Valmora.java:245` (`getCommand("warp").setExecutor(new WarpCommand(this))`). Note: **no tab completer is attached** even though `WarpCommand` implements `TabExecutor` (see §8-3).
- Exposed via API at `Valmora.java:410-412` (`getWarpManager()`, null-safe), plus concrete `getWarpModule()` at `Valmora.java:420`. Interface declaration: `ValmoraAPI.java:59`.

### 3.2 Warp Definition Model — `WarpDefinition.java`

Immutable value object with no validation — every field is final and populated by the parser:

| Field | Type | Meaning | Lines |
|---|---|---|---|
| `id` | `String` | Warp identifier, from the YAML key (lowercased in the registry) | `WarpDefinition.java:6` |
| `displayName` | `String` | MiniMessage display name shown in teleport/lock messages | `WarpDefinition.java:7` |
| `worldName` | `String` | Destination world name | `WarpDefinition.java:8` |
| `x`, `y`, `z` | `double` | Destination coordinates | `WarpDefinition.java:9` |
| `yaw`, `pitch` | `float` | Destination facing | `WarpDefinition.java:10` |
| `unlockCondition` | `String` | Gate expression (`always` / `tag:<tag>` / `skill:<id>:<level>`) | `WarpDefinition.java:11` |
| `padLocations` | `List<int[]>` | Trigger blocks; each element is `{x, y, z}` | `WarpDefinition.java:12` |

Constructor at `WarpDefinition.java:14-20`; getters at `WarpDefinition.java:22-31`.

### 3.3 Loading — `WarpLoader.java`

Uses the generic `YamlLoader<T>` (`org.nakii.valmora.infrastructure.config.YamlLoader`) with `folderName = "warps"` and `typeName = "Warps"` (`WarpLoader.java:24`), i.e. files are read from `plugins/Valmora/warps/*.yml`. Crucially it calls `load(...)` (not `loadFilesAsSections`), so the **YAML top-level keys are the warp ids** and a single file can hold many warps — unlike modules where the *filename* is the id (e.g. `SkillLoader`, which uses `loadFilesAsSections`). The shipped `hub.yml` follows the multi-warp-per-file style.

`load()` clears the registry then parses every top-level section in every `.yml` file, registering each `WarpDefinition` by its id (`WarpLoader.java:22-26`). YamlLoader aggregates parse failures and logs them as warnings (`YamlLoader.java:113-123`).

`parse(...)` builds a `WarpDefinition` with `LoadResult.success(...)` or captures any exception into `LoadResult.failure(...)` (`WarpLoader.java:28-49`):

- **`pad-locations`** is read with `ConfigurationSection.getMapList` (`WarpLoader.java:31`). Each map entry contributes an `int[]{x, y, z}`; a missing coordinate defaults to `0` (`WarpLoader.java:32-35`). Note the casts `((Number) padSec.get("x")).intValue()` — a non-numeric or non-map pad entry raises `ClassCastException`, which the surrounding `try/catch` converts into a load failure for that warp (`WarpLoader.java:46-48`).
- Defaults applied by the parser: `display-name` → `id`, `world` → `"world"`, `x` → `0`, `y` → `64`, `z` → `0`, `yaw` → `0`, `pitch` → `0`, `unlock-condition` → `"always"` (`WarpLoader.java:39-43`).
- If the same id appears in two files (or twice in one file), the last parse wins because `SimpleRegistry.register` overwrites (`SimpleRegistry.java:20-22`).

### 3.4 WarpManager — registry, unlock checks, teleport

`WarpManager` holds the single `Registry<WarpDefinition>` (a `SimpleRegistry`, case-insensitive keys) and all warp behaviour (`WarpManager.java:16-23`).

**`isUnlocked(Player, WarpDefinition)`** (`WarpManager.java:27-52`) is the gate:

| `unlock-condition` value | Evaluation |
|---|---|
| `null` or `"always"` (case-insensitive) | Always true (`WarpManager.java:29`) |
| `tag:<tag>` | True iff `profile.getTags().contains(tag)` (`WarpManager.java:36`); tags come from `ValmoraProfile.getTags()` (`ValmoraProfile.java:78-80`) |
| `skill:<id>:<level>` | Splits on `:` (`substring(6)`), parses the required level, looks up the skill definition, computes the player's level from their profile XP via `SkillRegistry.getProgressData(curve, xp).currentLevel()`, and requires `level >= required` (`WarpManager.java:38-50`) |
| anything else | False (locked) |

Prerequisites that silently yield *false*: no online session (`PlayerManager.getSession` returns `null`, `WarpManager.java:31-32`), no active profile (`WarpManager.java:33-34`), malformed `skill:` string (`parts.length < 2`), unparseable level, unknown skill id. The skill-level calculation depends on `SkillRegistry.getLevelFromXp`, which currently hard-codes `DEFAULT_XP_THRESHOLDS` and ignores the `xp-curve` argument (`SkillRegistry.java:27-36`) — the same dead-curve limitation noted in `docs/modules/design/skill.md` §8-1.

**`teleport(Player, WarpDefinition)`** (`WarpManager.java:54-65`) is the single teleport entry point used by the command, the pad listener, `warp_to` script events, and the script `teleport warp:<id>` event:

1. Lock check — locked players get `<red>This warp is locked! Condition: <gray><condition>` and are NOT teleported (`WarpManager.java:55-58`).
2. World lookup — `Bukkit.getWorld(worldName)`; unknown world → `<red>World not loaded.` (`WarpManager.java:59-60`).
3. Destination built with `new Location(world, x, y, z, yaw, pitch)` (`WarpManager.java:61`).
4. `player.teleportAsync(dest).thenAccept(success -> ...)` — on success sends `<green>Teleported to <white><display-name>` (`WarpManager.java:62-64`). On failure no message is sent.

No cooldown, no warmup/charging, no fee, no sound/particles, no safe-landing check. It also does **not** consult `ZoneFlags.teleportation` — see §8-4.

**`getWarpByPad(worldName, bx, by, bz)`** (`WarpManager.java:67-75`) scans every warp whose `worldName` matches and compares the given block coords against each `int[]{x,y,z}` pad; returns `Optional<WarpDefinition>`.

### 3.5 Pad triggers — `WarpListener.java`

A single handler on `PlayerMoveEvent` at `EventPriority.MONITOR` with `ignoreCancelled = true` (`WarpListener.java:16`):

- Early-returns when the player has not changed block (`WarpListener.java:18-20`).
- Otherwise looks up a warp by the **destination** block (`loc.getWorld().getName()`, `blockX/Y/Z`) via `getWarpByPad` and teleports (`WarpListener.java:21-23`).

Properties worth noting:
- It fires on any block-boundary crossing, in any world, for any player (no permission check).
- Because it only reacts to *movement*, a player who is already standing on a pad when the module loads, or who is teleported/portaled directly onto a pad, will **not** trigger it.
- `MONITOR` + non-cancelling means the move event still completes normally; the teleport is purely additive.
- Since the destination location is never itself a pad in the shipped config, the post-teleport `PlayerMoveEvent` (if any) does not cause a loop — but nothing in code prevents an admin from creating one (see §9-6).

### 3.6 `/warp` command — `WarpCommand.java`

`TabExecutor` constructed with the plugin (`WarpCommand.java:13-19`). `onCommand` (`WarpCommand.java:22-37`):

| Invocation | Behavior |
|---|---|
| non-player sender | `"Player only."` (`WarpCommand.java:23`) |
| manager is `null` (module disabled) | `<red>Warp system not loaded.` (`WarpCommand.java:25`) |
| no arguments | `plugin.getGuiModule().openGui(player, "fast_travel", new HashMap<>())` (`WarpCommand.java:27-30`) — see §8-1, the GUI is not shipped |
| one+ arguments | `wm.getRegistry().get(args[0]).ifPresentOrElse(warp -> teleport, () -> "<red>Unknown warp: <id>")` (`WarpCommand.java:32-35`) |

`onTabComplete` (`WarpCommand.java:40-46`) returns registered warp keys prefixed by the typed argument (lowercased) when `args.length == 1`; empty otherwise. As noted in §3.1 the completer is **not wired up** in `Valmora.java`, so players never see these completions.

There is **no permission node** on `/warp` (`plugin.yml:43-45`) and no per-warp permission concept anywhere in the module — every player may use every warp, subject only to `unlock-condition`.

### 3.7 Script integration

**`WarpEventFactory` — script event `warp_to`** (`WarpEventFactory.java:9-22`):
- `getName()` → `"warp_to"` (`WarpEventFactory.java:11`), so the DSL is `warp_to <warpId>`.
- `compile(args, options)` requires at least one arg (`WarpEventFactory.java:14-15`); the compiled event teleports the **player caster** (`ctx.getPlayerCaster()`) to the named warp via `ValmoraAPI.getInstance().getWarpManager()` (`WarpEventFactory.java:17-21`). Silent no-op if the manager or warp id is missing.

**`WarpVariableProvider` — namespace `warp`** (`WarpVariableProvider.java:10-29`):
- `getNamespace()` → `"warp"` (`WarpVariableProvider.java:12`).
- `resolve(path, context)` reads `path[0]` as a warp id and `path[1]` as a field (`WarpVariableProvider.java:16,23-24`):
  - `$warp.<id>.name$` → `warp.getDisplayName()` (`WarpVariableProvider.java:25`)
  - `$warp.<id>.unlocked$` → `wm.isUnlocked(playerCaster, warp)` (`WarpVariableProvider.java:26`)
  - any other field → `null`
- Requires a player caster (`WarpVariableProvider.java:17-18`) and a live manager (`WarpVariableProvider.java:20-21`).

Both are registered in `WarpModule.onEnable` (`WarpModule.java:22-23`).

**Script `teleport` event — `warp:<id>` form** (`TeleportEventFactory.java:25-60`): the script engine's own `teleport` event supports `teleport warp:<id>`. It first enforces the zone `teleportation` flag (`isBlockedByZone`, `TeleportEventFactory.java:36-48`, `ZoneFlags.java:10`) and then delegates to `WarpManager.teleport` (`TeleportEventFactory.java:52-59`). This is the **only** path that combines warps with the zone teleportation flag.

### 3.8 Registration order & command table

- Module slot 20 in the enable order: `script → time → stat → player → economy → ui → ability → item → mob → skill → combat → gui → recipe → alchemy → enchant → zone → resource → fishing → npc → warp → quest → …` (`Valmora.java:188-222`, `docs/VALMORA_DOCUMENTATION.md:264`).
- `/warp` command declared in `plugin.yml:43-45` (usage `/warp [id]`, description "Teleport to a warp point or open the fast travel menu.", no permission).
- Console execution is rejected by the command itself ("Player only."); the `/warp` registration in `plugin.yml` does not restrict sender type.

---

## Configuration (YAML)

Files live in `plugins/Valmora/warps/`. `Valmora.saveAllResources` copies the bundled `src/main/resources/warps/` contents on first boot but **only if the target file does not already exist**, so server edits survive reloads (`Valmora.java:474,481-483`). `WarpLoader` reads every `.yml` file in that folder; **each top-level YAML key becomes a warp id** (`WarpLoader.java:22-26`, `YamlLoader.java:37-73`). Warp ids are stored lowercased by the registry (`SimpleRegistry.java:20-21`), so `Coal_Mine` and `coal_mine` are the same warp.

### 4.1 Per-warp keys

| Key | Type | Default | Explanation |
|---|---|---|---|
| `display-name` | string (MiniMessage) | the warp id | Name shown in the arrival message (`Teleported to …`) and in the lock message; tags like `<gold>` are honored via `Formatter` (`WarpLoader.java:39`) |
| `world` | string | `"world"` | Destination world; `Bukkit.getWorld(...)` must return a loaded world or the teleport aborts with "World not loaded." (`WarpLoader.java:40`, `WarpManager.java:59-60`) |
| `x` | double | `0` | Destination X (`WarpLoader.java:41`) |
| `y` | double | `64` | Destination Y (`WarpLoader.java:41`) |
| `z` | double | `0` | Destination Z (`WarpLoader.java:41`) |
| `yaw` | double→float | `0` | Destination horizontal facing (`WarpLoader.java:42`) |
| `pitch` | double→float | `0` | Destination vertical facing (`WarpLoader.java:42`) |
| `unlock-condition` | string | `"always"` | Gate expression — see §4.2 (`WarpLoader.java:43`) |
| `pad-locations` | list of `{x, y, z}` maps | empty list | Trigger blocks; see §4.3 (`WarpLoader.java:31-36`) |

### 4.2 `unlock-condition` grammar

Evaluated per player against their **active profile** (`WarpManager.java:31-34`):

| Value | Effect |
|---|---|
| `always` (or missing) | Unlocked for everyone (`WarpManager.java:29`) |
| `tag:<tag>` | Unlocked iff the active profile carries the tag (`WarpManager.java:36`) |
| `skill:<skillId>:<level>` | Unlocked iff the profile's level in `<skillId>` is `>= <level>` (`WarpManager.java:38-50`) |
| anything else | Always locked |

Example: `unlock-condition: skill:mining:10` requires Mining level 10. There is **no** quest/points/zones-based condition, no nested `and`/`or`, and no reward-on-lock expression.

### 4.3 `pad-locations` schema

```
pad-locations:
  - {x: 10, y: 64, z: 10}
  - {x: 11, y: 64, z: 10}
```

Each entry is an exact **block** coordinate (ints). A player crossing into one of these blocks triggers the warp (`WarpListener.java:21-23`). A missing `x`/`y`/`z` in an entry defaults to `0` (`WarpLoader.java:32-34`). Multiple pads per warp define a teleport pad area — the shipped warps use a 2×2 footprint. A warp with **no** pads is still usable via `/warp <id>` and script events; it just has no floor trigger.

### 4.4 Shipped file — `src/main/resources/warps/hub.yml`

| Warp id | display-name | world | x / y / z | unlock-condition | pad-locations |
|---|---|---|---|---|---|
| `hub_spawn` | `<gold>Hub Spawn` | world | 0.5 / 65.0 / 0.5 | always | `(10,64,10) (11,64,10) (10,64,11) (11,64,11)` |
| `coal_mine_warp` | `<gray>Coal Mine` | world | -55.5 / 65.0 / 90.5 | always | `(-20,64,20) (-21,64,20) (-20,64,21) (-21,64,21)` |
| `forest_warp` | `<green>Whispering Forest` | world | 90.5 / 65.0 / -90.5 | always | `(30,64,-30) (31,64,-30) (30,64,-31) (31,64,-31)` |
| `fishing_village_warp` | `<aqua>Fishing Village` | world | 150.5 / 65.0 / 150.5 | always | `(50,64,50) (51,64,50) (50,64,51) (51,64,51)` |
| `graveyard_warp` | `<dark_gray>Graveyard` | world | -115.5 / 65.0 / -105.5 | always | `(-40,64,-40) (-41,64,-40) (-40,64,-41) (-41,64,-41)` |

All five use `yaw: 0` / `pitch: 0`, are on `unlock-condition: always`, and share a 2×2 pad layout. There is no `config.yml` section for warps — warp configuration is exclusively file-based.

### 4.5 Load-time behaviour

- `YamlLoader.load` parses **all** top-level sections of **all** `.yml` files and aggregates failures; each failure is logged as `- [<path>] Error parsing warp '<id>': <msg>` followed by a summary `Successfully loaded N Warps.` (`YamlLoader.java:43-73,113-123`).
- A single malformed warp does not abort the others; it is simply skipped.
- A missing `warps/` folder is created empty (`YamlLoader.java:38-41`).

---

## Data Model / Persistence

```
Registry<WarpDefinition>  (SimpleRegistry, keys lowercased — WarpManager.java:19)
  └── Map<String, WarpDefinition>  — rebuilt from YAML on every module enable
```

- **No database involvement.** Warps never touch `SQLDataStore`; there is no warp table or column. The module's entire state is the in-memory registry (`WarpManager.java:19`).
- Registry lifecycle: populated in `onEnable()` via `WarpLoader.load()` (`WarpModule.java:20-21`, `WarpLoader.java:23-25`), cleared in `onDisable()` (`WarpModule.java:32`). Hot-reload (`/valmora reload`) rebuilds everything from disk.
- **Player unlock state is not persisted by this module.** Unlock conditions read live profile data — `ValmoraProfile.getTags()` (tags are stored per profile in `ValmoraProfile.java:24`) and per-profile skill XP (`profile.getSkillManager().getXp(...)`, persisted in the `valmora_profiles.skills` JSON column by `SQLDataStore`, see `docs/modules/design/skill.md` §5) — so nothing about "which warps a player has unlocked" is stored anywhere; it is always derived.
- `Keys.WARP_ID_KEY` (`Keys.java:24,60`, namespace key `valmora_warp_id`) is declared but **unused anywhere** — a reserved hook (likely for per-entity/item warp data) with no current consumer.

---

## API Exposed

- **`ValmoraAPI.getWarpManager()`** → `WarpManager` (`ValmoraAPI.java:59`); implementation `Valmora.java:410-412` returns `null` while the module is disabled.
- **`Valmora.getWarpModule()`** → concrete `WarpModule` (not on the interface) with `getWarpManager()` (`Valmora.java:420`, `WarpModule.java:38`).

`WarpManager` public surface (`WarpManager.java`):
- `getRegistry()` → `Registry<WarpDefinition>` (`WarpManager.java:25`); `Registry` ops from `SimpleRegistry` (`SimpleRegistry.java:19-57`): `register`, `unregister`, `get` (Optional), `contains`, `getKeys`, `values`, `size`, `clear`.
- `isUnlocked(Player, WarpDefinition)` → `boolean` (`WarpManager.java:27`).
- `teleport(Player, WarpDefinition)` → `void` (async; messages player) (`WarpManager.java:54`).
- `getWarpByPad(String worldName, int bx, int by, int bz)` → `Optional<WarpDefinition>` (`WarpManager.java:67`).

`WarpDefinition` public surface (`WarpDefinition.java:22-31`): `getId`, `getDisplayName`, `getWorldName`, `getX/getY/getZ`, `getYaw/getPitch`, `getUnlockCondition`, `getPadLocations`.

**Script-surface** (not Java API): event `warp_to <id>` (`WarpEventFactory.java:11`) and variables `$warp.<id>.name$` / `$warp.<id>.unlocked$` (`WarpVariableProvider.java:12,25-26`).

No custom Bukkit events are defined or fired by this module.

---

## Dependencies & Consumers

### Upstream dependencies (load before `warp` — slot 20)

| Dependency | Why | Evidence |
|---|---|---|
| `script` | Registers the `warp_to` event factory and `warp` variable provider on the script engine | `WarpModule.java:22-23`, `ScriptModule.java:73-79` |
| `profile` / `PlayerManager` | Resolves the online session and active `ValmoraProfile` for unlock checks | `WarpManager.java:31-34`, `PlayerManager.java:165`, `ValmoraProfile.java:78-80` |
| `skill` | `skill:<id>:<level>` unlock conditions read skill XP and curve math | `WarpManager.java:44-49`, `SkillRegistry.java:27-36` |
| `gui` | `/warp` with no args calls `GuiModule.openGui(player, "fast_travel", ...)` | `WarpCommand.java:28`, `GuiModule.java:71-73` |

The `docs/MODULE_DEVELOPMENT.md:515` note "depends on zone, gui" is **only half true**: the warp module itself never touches `ZoneManager`. The zone `teleportation` flag (`ZoneFlags.java:10`) is enforced exclusively by the script `teleport` event (`TeleportEventFactory.java:36-48`) and is **bypassed** by `WarpManager.teleport`, warp pads, `/warp`, and `warp_to` (see §8-4).

### Downstream consumers

| Consumer | What it uses | Evidence |
|---|---|---|
| `script` events | `warp_to <id>` (player caster), `teleport warp:<id>` | `WarpEventFactory.java:14-22`, `TeleportEventFactory.java:52-59` |
| `script` variables | `$warp.<id>.name$`, `$warp.<id>.unlocked$` in expressions/conditions/GUIs | `WarpVariableProvider.java:23-27` |
| `quest` | The `/warp` command satisfies quest `command` objectives (matched via `PlayerCommandPreprocessEvent`) | `QuestListener.java:365-374`, `QuestObjectiveTypes.java:39`, `docs/Objective_list.md:117-130` |
| `gui` (planned) | `/warp` opens GUI id `fast_travel`; no such GUI ships today | `WarpCommand.java:28`, `src/main/resources/guis/` |

---

## Unfinished Things / TODOs

1. **No `sign/` subpackage; sign warps are not implemented.** The task brief for this doc assumed a sign-warp subsystem, but none exists in `module/warp/`. There is no right-click-sign teleport logic anywhere; `Keys.WARP_ID_KEY` (`Keys.java:24,60`) is defined but has zero usages — the reserved hook for warp-tagged entities/items/signs. `docs/todo.md:19` ("warp: finish the ui for the warp") is the only roadmap item.
2. **The `fast_travel` GUI is not shipped.** `/warp` with no arguments calls `openGui(player, "fast_travel", ...)` (`WarpCommand.java:28`), but there is no `fast_travel.yml` in `src/main/resources/guis/` (only the 17 GUIs listed by the glob), and `GuiModule.openGui` silently returns when the id is missing (`GuiModule.java:72-73`). Players pressing plain `/warp` see nothing happen.
3. **No tab completion wired.** `WarpCommand` implements `TabExecutor` (`WarpCommand.java:13`) but `Valmora.java:245` only calls `setExecutor`, so `/warp` never auto-completes warp names (same wart as `/skill`, see `docs/modules/design/skill.md` §8-5).
4. **Zone `teleportation` flag is not enforced by the warp module.** `WarpManager.teleport` (`WarpManager.java:54-65`) never consults `ZoneManager`; only the script `teleport` event does (`TeleportEventFactory.java:36-48`). Warp pads, `/warp`, and `warp_to` teleport even inside zones flagged `teleportation: false`.
5. **No fees, cooldowns, warmup, or per-warp permissions.** `docs/TESTING_GUIDE.md:164-166` (TC-WARP, rows WARP-01/02/03) describes a warmup-and-move-cancel behaviour and a permission-denied case that do not exist in the code; the docs are aspirational. `plugin.yml:43-45` declares `/warp` without a permission node, and there is no `valmora.warp.<id>`-style permission anywhere.
6. **`WARP_ID_KEY` is dead code** (`Keys.java:24,60`) — see §5.
7. **Pad detection is move-only.** Players already occupying a pad at load time, or teleported/portaled onto one, never trigger (`WarpListener.java:17-20`). No `PlayerInteractEvent`/`EntityChangeBlockEvent` alternative exists.
8. **`pad-locations` parsing is brittle.** `((Number) padSec.get("x")).intValue()` (`WarpLoader.java:32-34`) throws `ClassCastException` on non-numeric input; a typo'd pad entry fails the *entire warp* rather than just that pad.
9. **Registry build is not idempotent-safe by key collision.** Duplicate ids across files silently overwrite (`SimpleRegistry.java:20-22`) with no warning.
10. **`docs/USER_DOCS.md` §16 (lines 1066-1118)** documents warp pads as triggered by *"pressing the interact key"* — the code triggers on *walking onto* the pad block (`WarpListener.java:16-24`); the user doc is stale on the activation method.

---

## Possible Improvements / Changes

1. **Ship a `fast_travel` GUI** (`plugins/Valmora/guis/fast_travel.yml`) rendering `$warp.*$` data, or fall back to a chat list (`/warp` + names) when the GUI is absent, so bare `/warp` is useful. `docs/todo.md:19` names this as the pending warp work.
2. **Implement sign warps** using the reserved `WARP_ID_KEY` (or sign-line lookup): right-click a sign whose PDC/line holds a warp id → `WarpManager.teleport`. This would give the "sign/ subpackage" the codebase is missing and put the dead key to use.
3. **Add fees/cooldowns/warmup** — e.g. an `economy` fee per warp (`ValmoraAPI.getEconomy()`, `ValmoraAPI.java:47`), a per-player cooldown via `CooldownManager` (`ValmoraProfile.java:23`), and an optional charge-time cancelled by movement (matching the aspirational `docs/TESTING_GUIDE.md` TC-WARP). The module currently has zero anti-abuse mechanics.
4. **Enforce `ZoneFlags.teleportation` inside `WarpManager.teleport`** so every entry path (pads, command, `warp_to`) is consistent with the script `teleport` event (`TeleportEventFactory.java:36-48`), rather than only that one path.
5. **Per-warp permissions** — a `valmora.warp.<id>` node checked in `WarpCommand`/`WarpListener`, or a `permission:` key on the definition, plus a `valmora.admin`-gated admin command (`/warp create|delete|list|set` with a selection wand) so pads can be authored in-game.
6. **Loop protection** — a short per-player teleport cooldown inside `WarpManager.teleport` (or a "recently teleported" timestamp) so an admin who places a destination on a pad cannot create an infinite bounce.
7. **More trigger surfaces** — trigger pads on `PlayerInteractEvent`/pressure plates, and/or also check the *from* block so already-standing players fire on first interaction; plus activation feedback (sound/particles via `TextDisplay` or `Player.spawnParticle`).
8. **Safe destination handling** — verify the destination block is safe (air/water) or use a "spawn above the pad" offset, since `teleportAsync` blindly drops players at raw coordinates (`WarpManager.java:61`).
9. **Tighter parsing** — validate pad entries defensively (skip bad entries with a warning instead of `ClassCastException`), and log a warning on duplicate warp ids instead of silent overwrite.
10. **Richer conditions** — support `quest:<id>`/`points:<min>`/`money:<min>` and compound expressions in `unlock-condition`, all evaluated inside `WarpManager.isUnlocked` alongside the existing `tag:`/`skill:` grammar.
