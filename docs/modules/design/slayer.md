# Slayer Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `slayer` | **Source:** `src/main/java/org/nakii/valmora/module/slayer/`

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

The Slayer module implements **tiered kill-challenge quests**. A player activates a slayer tier at a coin cost, kills a required number of mobs from a target category, and then a **boss mob** spawns at the player's location. Killing the boss completes the tier and fires a configurable list of **completion events** (script DSL events).

The module is deliberately thin — four classes totalling ~290 lines:

- `SlayerModule` — the `ReloadableModule` lifecycle + YAML loading into an in-memory definition map.
- `SlayerDefinition` — immutable definition of one slayer (id, display name, tier map).
- `SlayerTier` — immutable definition of one tier (cost, target category, kill count, boss mob, completion events).
- `SlayerListener` — `EntityDeathEvent` handler that tracks kill progress, spawns the boss, and detects boss kills.
- `SlayerStartEventFactory` — registers the `slayer_start` script event so any scriptable system (quest, GUI, NPC dialogue) can start a task.

Key design decisions:

- **State lives in the player's profile variables map**, not in the module itself. The module holds *definitions only* (`SlayerModule.java:18`); per-player runtime state (`slayer.active`, `slayer.kills`, `slayer.boss`) is stored under `ValmoraProfile.getVariables()` (`SlayerListener.java:24-26`, `SlayerStartEventFactory.java:73-75`). This means slayer progress survives hot-reloads and is persisted to the database (see [Data Model / Persistence](#data-model--persistence)).
- **Boss identity is a PDC tag.** The spawned boss carries `Keys.SLAYER_BOSS_KEY` (`valmora:slayer_boss`, `Keys.java:37`, `Keys.java:73`) holding the task key string (`<slayer-id>:<tier>`). A boss kill only completes a task if the stored task key equals the player's active task (`SlayerListener.java:82-83`).
- **No GUI, no command, no boss bar wiring.** The module exposes no `ValmoraAPI` method (only a concrete-class getter `Valmora.getSlayerModule()`, `Valmora.java:427`) and is driven entirely through the `slayer_start` script event. There is currently no way for a player to open a slayer selection menu — the Slayer button in `guis/skills_details.yml:86-92` is decorative (`type: DISPLAY`, no actions).

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/slayer/
├── SlayerModule.java            # ReloadableModule — lifecycle + YAML loading (parse + register)
├── SlayerDefinition.java        # Immutable slayer definition (id, name, Map<Integer,SlayerTier>)
├── SlayerTier.java              # Immutable tier definition (cost, category, kills, boss, events)
├── SlayerListener.java          # EntityDeathEvent handler — kills, boss spawn, boss completion
└── SlayerStartEventFactory.java # Registers the "slayer_start" script EventFactory

src/main/resources/slayers/
└── zombie.yml                   # Default shipped slayers (zombie / spider / wolf)
```

There is **no test coverage** for this module (`src/test/java` contains no slayer test).

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `SlayerModule.java`

Implements `ReloadableModule` (see `docs/MODULE_DEVELOPMENT.md` §2).

| Method | Behavior | Lines |
|---|---|---|
| `onEnable()` | Clears the definition map, runs `loadDefinitions()`, constructs `SlayerListener` and registers it, then registers `SlayerStartEventFactory` with `plugin.getScriptModule().registerEvent(...)` | `SlayerModule.java:25-33` |
| `onDisable()` | Unregisters the listener via `HandlerList.unregisterAll`, nulls it, clears the definition map | `SlayerModule.java:35-42` |
| `getId()` | `"slayer"` | `SlayerModule.java:45` |
| `getName()` | `"Slayer System"` | `SlayerModule.java:48` |
| `getDefinition(String)` | Case-insensitive lookup (`id.toLowerCase()`) into the definition map | `SlayerModule.java:50` |
| `getDefinitions()` | Unmodifiable view is NOT returned — the raw `values()` collection of the internal map | `SlayerModule.java:51` |
| `getPlugin()` | Returns the `Valmora` plugin instance (convenience for the listener/factory) | `SlayerModule.java:52` |

Hot-reload safety: all mutable state (`definitions`, `listener`) is initialized/torn down inside `onEnable()`/`onDisable()`, so `/valmora reload` is safe. The constructor only stores the plugin reference (`SlayerModule.java:21-23`).

**Definition loading** — `loadDefinitions()` (`SlayerModule.java:54-57`):

```java
YamlLoader<SlayerDefinition> loader = new YamlLoader<>(plugin, "slayers", "Slayer");
loader.load(this::parseDefinition, def -> definitions.put(def.getId(), def));
```

This follows the standard `YamlLoader` pattern (`docs/VALMORA_DOCUMENTATION.md` §7): every `.yml` in `plugins/Valmora/slayers/` is scanned, each **top-level key becomes the slayer id**, and each is parsed by `parseDefinition`. Parse failures are batched and logged with file paths by the loader (`YamlLoader.java:113-123`).

**`parseDefinition`** (`SlayerModule.java:59-86`) reads:

| Field | Read as | Default | Lines |
|---|---|---|---|
| `name` | `section.getString("name", id)` | the slayer id | `SlayerModule.java:61` |
| `tiers.<n>.cost` | `tierSec.getDouble("cost", 0.0)` | `0.0` | `SlayerModule.java:72` |
| `tiers.<n>.target-category` | `tierSec.getString("target-category", "HOSTILE")` | `"HOSTILE"` | `SlayerModule.java:73` |
| `tiers.<n>.kills-required` | `tierSec.getInt("kills-required", 5)` | `5` | `SlayerModule.java:74` |
| `tiers.<n>.boss-mob` | `tierSec.getString("boss-mob", "")` | `""` | `SlayerModule.java:75` |
| `tiers.<n>.completion-events` | `tierSec.getStringList("completion-events")` | empty list | `SlayerModule.java:76` |

Tier map details:

- Tier keys must be **integer strings**. Non-integer keys are silently skipped (`SlayerModule.java:67-68`).
- Tiers are stored in a `LinkedHashMap` keyed by `Integer`, preserving YAML order (`SlayerModule.java:62`, `SlayerModule.java:78`).
- A `LoadResult.success(...)` wraps every successfully parsed slayer; any thrown exception becomes `LoadResult.failure(...)` with the file path (`SlayerModule.java:82-85`).

### 3.2 Data Classes

**`SlayerDefinition`** (`SlayerDefinition.java`) — immutable value object:

| Field | Type | Accessor | Lines |
|---|---|---|---|
| `id` | `String` | `getId()` | `SlayerDefinition.java:17` |
| `name` | `String` | `getName()` | `SlayerDefinition.java:18` |
| `tiers` | `Map<Integer, SlayerTier>` | `getTiers()` / `getTier(int)` | `SlayerDefinition.java:19`, `SlayerDefinition.java:21` |

**`SlayerTier`** (`SlayerTier.java`) — immutable value object:

| Field | Type | Accessor | Lines |
|---|---|---|---|
| `tier` | `int` | `getTier()` | `SlayerTier.java:24` |
| `cost` | `double` | `getCost()` | `SlayerTier.java:25` |
| `targetCategory` | `String` | `getTargetCategory()` | `SlayerTier.java:26` |
| `killsRequired` | `int` | `getKillsRequired()` | `SlayerTier.java:27` |
| `bossMob` | `String` | `getBossMob()` | `SlayerTier.java:28` |
| `completionEvents` | `List<String>` | `getCompletionEvents()` | `SlayerTier.java:29` |

### 3.3 Start Flow — `SlayerStartEventFactory.java`

Registered in `SlayerModule.onEnable()` (`SlayerModule.java:32`), so it participates in hot-reload (unlike the built-in script events). It is a standard `EventFactory` (`docs/VALMORA_DOCUMENTATION.md` §17) named `"slayer_start"` (`SlayerStartEventFactory.java:23-24`).

**`compile(String[] args, EventOptions)`** (`SlayerStartEventFactory.java:27-35`):
- Requires ≥ 2 args: `slayer_start <slayer-id> <tier>`. With fewer args, or a non-integer tier, it compiles to a no-op event.
- Returns a `CompiledEvent` that resolves `ctx.getPlayerCaster()` and calls `startTask(player, slayerType, tier)`.

**`startTask(Player, String, int)`** (`SlayerStartEventFactory.java:37-82`) runs on the main thread (script events execute synchronously):

1. **Definition lookup** — `slayerModule.getDefinition(slayerType)`; unknown → red error message (`SlayerStartEventFactory.java:38-41`).
2. **Tier lookup** — `def.getTier(tierNum)`; missing → red error message (`SlayerStartEventFactory.java:43-47`).
3. **Active-task guard** — reads `profile.getVariables().get("slayer.active")`; a non-blank value blocks starting a second task (`SlayerStartEventFactory.java:54-58`). Note: it reads the string literal `"slayer.active"` rather than the constants from `SlayerListener` — they must stay in sync manually.
4. **Economy check** — if `tier.getCost() > 0` and `plugin.getEconomy()` is non-null: requires `hasCoins(player, cost)` and then `removeCoins(player, cost)` (`SlayerStartEventFactory.java:61-69`). **If `getEconomy()` returns null the cost is silently neither checked nor charged.**
5. **State write** — sets:
   - `slayer.active` = `<slayer-id>:<tier>` (the task key)
   - `slayer.kills` = `0`
   - removes `slayer.boss`
   (`SlayerStartEventFactory.java:72-75`)
6. **Feedback** — two messages: task started + kill objective summary (`SlayerStartEventFactory.java:77-81`).

### 3.4 Kill & Completion Flow — `SlayerListener.java`

Single handler `onDeath(EntityDeathEvent)` (`SlayerListener.java:36-76`). Execution priority is default (no `@EventHandler(priority=...)`).

```
onDeath(event)
  │
  ├─ entity = event.getEntity()
  │
  ├─ BOSS branch:
  │    taskKey = entity.getPDC().get(SLAYER_BOSS_KEY, STRING)   (SlayerListener.java:41)
  │    if taskKey != null && entity.getKiller() != null
  │        → handleBossDeath(killer, taskKey)   and return     (SlayerListener.java:42-45)
  │
  └─ NORMAL branch:
       if entity.getKiller() == null → return                   (SlayerListener.java:48)
       killer = entity.getKiller(); profile = getProfile(killer)
       if profile == null → return                              (SlayerListener.java:50-51)
       active = profile.variables["slayer.active"]
       if active == null || blank → return                      (SlayerListener.java:53-54)
       parts = active.split(":", 2);  must be length 2          (SlayerListener.java:56-57)
       def = module.getDefinition(parts[0]);  if null → return  (SlayerListener.java:58-59)
       tierNum = parseInt(parts[1]);  if invalid → return       (SlayerListener.java:60-61)
       tier = def.getTier(tierNum);  if null → return           (SlayerListener.java:62-63)
       if !matchesCategory(entity, tier.getTargetCategory()) → return  (SlayerListener.java:65)
       kills = variables["slayer.kills"] + 1 ; store back       (SlayerListener.java:67-69)
       send "<yellow>[Slayer] Kill {kills}/{required}"          (SlayerListener.java:71)
       if kills >= tier.getKillsRequired() && variables["slayer.boss"] == null
            → spawnBoss(killer, def, tier, active)              (SlayerListener.java:73-75)
```

**`matchesCategory(Entity, String)`** (`SlayerListener.java:124-144`) is a switch on `category.toUpperCase()`:

| Category | Match rule |
|---|---|
| `MONSTER` | `entity instanceof Monster` |
| `ILLAGER` | `entity instanceof Illager` |
| `ANIMAL` | `entity instanceof Animals` |
| `ALL`, `ANY` | always `true` |
| `UNDEAD` | entity `EntityType.name()` contains `ZOMBIE`, `SKELETON`, `PHANTOM`, `DROWNED`, `WITHER`, `STRAY`, or `HUSK` |
| default | entity PDC `MOB_ID_KEY` equals the category (case-insensitive) **or** entity `EntityType.name()` equals it (case-insensitive) |

Implications worth knowing:

- `SPIDER` and `WOLF` (the shipped config) resolve via the **default** branch, comparing against the vanilla entity type name. A `CAVE_SPIDER` does **not** count as `SPIDER`.
- `UNDEAD` matching is purely name-substring based — e.g. a custom mob named `ZOMBIE_BRUTE` type would count, and any entity whose type name contains `WITHER` (including the Wither boss and `WITHER_SKELETON`) counts.
- **`HOSTILE` is not a handled category.** The parser's default category (`SlayerModule.java:73`) falls into the default branch and never matches (no mob type is named `HOSTILE`). A tier without an explicit `target-category` can therefore never progress. See [Unfinished Things / TODOs](#unfinished-things--todos).
- Category comparison is done on the **entity**, and the default branch also honours a Valmora `MOB_ID_KEY` PDC tag, so custom mobs can be counted by mob id.

**`spawnBoss(Player, SlayerDefinition, SlayerTier, String taskKey)`** (`SlayerListener.java:107-122`):

1. If `tier.getBossMob().isBlank()` → abort (no boss, task becomes permanently uncompletable for that tier) (`SlayerListener.java:108`).
2. `plugin.getMobManager().getMobDefinition(tier.getBossMob())`; if missing, logs `"Slayer boss mob not found: <id>"` and aborts (`SlayerListener.java:109-113`).
3. `mobManager.spawnMob(mobDef, player.getLocation().add(0, 0, 2))` — spawns 2 blocks south (+Z) of the player, not in front of them (`SlayerListener.java:114`). The mob engine applies health/damage/speed/equipment/visuals and boss tracking if the definition is a boss (`MobFactory.java:92-102`).
4. Tags the entity with `Keys.SLAYER_BOSS_KEY` = taskKey (`SlayerListener.java:116`).
5. Stores the boss `UUID` in `slayer.boss` (`SlayerListener.java:117-118`).
6. Sends `<red><bold>[Slayer] The {name} boss has appeared! Defeat it!` (`SlayerListener.java:119-121`).

**`handleBossDeath(Player killer, String taskKey)`** (`SlayerListener.java:78-105`):

1. Resolves the killer's profile (`SlayerListener.java:79-80`).
2. **Guard:** `taskKey` (from the dead boss PDC) must equal the killer's current `slayer.active`. This prevents a player from claiming a boss summoned under another task, and makes boss kills by non-players (or by players with no active task) harmless no-ops (`SlayerListener.java:82-83`).
3. Re-resolves the definition and tier from the task key (`SlayerListener.java:85-92`).
4. Clears `slayer.active`, `slayer.kills`, `slayer.boss` (`SlayerListener.java:94-96`).
5. Sends `<gold><bold>[Slayer] <green>Quest complete! You defeated the {name} boss!` (`SlayerListener.java:98-99`).
6. Executes `tier.getCompletionEvents()` via `plugin.getScriptModule().getEventParser().parseList(...)` against a **fresh** `SimpleExecutionContext(killer, killer.getLocation(), new YamlConfiguration())` (`SlayerListener.java:101-104`). Note: `target` is `null` and `params` is an empty `YamlConfiguration`; completion events can only rely on `getPlayerCaster()`.

**`getProfile(Player)`** (`SlayerListener.java:146-149`) goes through `ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId())` and returns the **active profile**, or `null`. All slayer state is therefore per-**profile**, not per-player.

### 3.5 Task Key Format

Both the profile variable `slayer.active` and the boss PDC tag use the same string encoding:

```
<slayer-id>:<tier-number>
e.g. "zombie_slayer:2"
```

Parsed with `split(":", 2)` (`SlayerListener.java:56`, `SlayerListener.java:85`). Note that `SlayerStartEventFactory.java:72` builds the key with string concatenation; ids containing a colon would break the format (YAML keys normally don't).

---

## Configuration (YAML)

Config folder: `plugins/Valmora/slayers/` — loaded by `YamlLoader` (`SlayerModule.java:55`). Files are only auto-copied from the jar if absent (`Valmora.java:469-484`), so server edits are preserved. Each **top-level key** is a slayer id (case-insensitive for lookups, but the internal map stores the original case — see [Possible Improvements](#possible-improvements--changes)).

### Full schema

```yaml
<slayer-id>:
  name: "<display name with MiniMessage>"
  tiers:
    <tier-number>:                    # Integer key: 1, 2, 3 ...
      cost: <coins>                   # Default: 0
      target-category: <CATEGORY>     # Default: "HOSTILE"
      kills-required: <int>           # Default: 5
      boss-mob: <mob-id>              # Default: "" (empty)
      completion-events:
        - "<script event string>"
```

### Field reference

#### Slayer level

| Field | Type | Default | Required | Notes |
|---|---|---|---|---|
| `name` | String (MiniMessage) | the slayer id | No | Display name used in task-started, boss-appeared, and completion messages. |
| `tiers` | Map | — | Yes | Keys are integer tier numbers; each value is a tier definition. Non-integer keys are skipped at load (`SlayerModule.java:67-68`). |

#### Tier level

| Field | Type | Default | Required | Notes |
|---|---|---|---|---|
| `cost` | Double | `0.0` | No | Coins deducted on activation via `slayer_start`. Player must have the balance. A `0` cost skips the economy check entirely (`SlayerStartEventFactory.java:61-69`). |
| `target-category` | String | `HOSTILE` | No | Category matched against each killed entity. See the `matchesCategory` table in §3.4. **`HOSTILE` is not actually handled** — always set an explicit category. |
| `kills-required` | Integer | `5` | No | Matching kills needed before the boss can spawn. |
| `boss-mob` | String | `""` | No | A Valmora mob id (from `mobs/*.yml`) spawned as the boss. If blank or unresolvable, no boss spawns and the tier can never complete (`SlayerListener.java:108-113`). |
| `completion-events` | List\<String\> | `[]` | No | Script DSL events executed when the boss is killed. See below. |

### Default shipped file — `slayers/zombie.yml`

Three slayers ship with the plugin:

| Slayer | Tier | cost | target-category | kills-required | boss-mob | completion-events |
|---|---|---|---|---|---|---|
| `zombie_slayer` | 1 | 100 | UNDEAD | 5 | `zombie` | `economy_add 250`, `notify chat ...` |
| `zombie_slayer` | 2 | 500 | UNDEAD | 15 | `zombie` | `economy_add 1000`, `notify chat ...` |
| `zombie_slayer` | 3 | 2000 | UNDEAD | 30 | `zombie` | `economy_add 5000`, `notify chat ...` |
| `spider_slayer` | 1 | 100 | SPIDER | 5 | `spider` | `economy_add 250`, `notify chat ...` |
| `spider_slayer` | 2 | 500 | SPIDER | 20 | `spider` | `economy_add 1500`, `notify chat ...` |
| `wolf_slayer` | 1 | 100 | WOLF | 5 | `wolf` | `economy_add 250`, `notify chat ...` |
| `wolf_slayer` | 2 | 1000 | WOLF | 25 | `wolf` | `economy_add 3000`, `notify chat ...` |

> **Warning:** the shipped tiers reference boss mobs `zombie`, `spider`, and `wolf`, but **no such mob ids ship with the plugin** (shipped mobs are `test_zombie`, `test_skeleton`, `forge_titan`, `forge_imp`, `shardworks_cave_guardian`, `shardworks_crystal_wraith` — see `src/main/resources/mobs/`). On a stock install, hitting the kill target logs `Slayer boss mob not found: <id>` (`SlayerListener.java:111`) and no boss spawns. Define these mobs (or edit the slayer YAML) before players attempt these tiers.

### Completion events

Any registered script DSL event is valid (see `docs/VALMORA_DOCUMENTATION.md` §33). The shipped examples use:

- `economy_add <amount>` — adds coins to the player's purse (`EconomyAddEventFactory.java:20-30`). Registered by `EconomyModule.onEnable()` (`EconomyModule.java:60`).
- `notify <message>` — sends a notification (defaults to `chat` IO via `NotifyManager.java:49-53`). Registered by `NotifyModule.onEnable()` (`NotifyModule.java:29`).

Event execution context (`SlayerListener.java:101-104`): caster = the killing player, target = `null`, params = empty `YamlConfiguration`.

> **Quirk:** the shipped completion events use `notify chat <gold>[Slayer] ...`. `NotifyEvent` treats `chat` as part of the message text (it has no `category:`/`io:` prefix and no colon, `NotifyEvent.java:26-38`), so the player sees the literal word `chat` prepended to the message. The documented syntax is `notify <message> io:<type>` (e.g. `notify <gold>... io:chat`).

---

## Data Model / Persistence

There are **no dedicated tables or DAOs**. Slayer runtime state is stored inside the existing player profile persistence:

| Profile variable | Type | Written | Cleared |
|---|---|---|---|
| `slayer.active` | String (`<slayer-id>:<tier>`) | `SlayerStartEventFactory.java:73` | `SlayerListener.java:94` |
| `slayer.kills` | Integer | `SlayerStartEventFactory.java:74`, `SlayerListener.java:67-69` | `SlayerListener.java:95` |
| `slayer.boss` | String (boss UUID) | `SlayerListener.java:118` | `SlayerListener.java:96` |

Profile variables live in `ValmoraProfile.variables` (`ValmoraProfile.java:25`, `ValmoraProfile.java:82-84`) and are serialized to the `variables` JSON column of `valmora_profiles` (`SQLDataStore.java:140`, `SQLDataStore.java:227-230`, `SQLDataStore.java:300`, `SQLDataStore.java:309`). Consequences:

- **Progress survives relog and server restart** (the task key, kill count, and boss UUID are all persisted).
- State is **per-profile**, so switching profiles (`PlayerManager.switchProfile`) gives each profile its own independent slayer state.
- The `slayer.boss` UUID is only ever used as a null-check gate (`SlayerListener.java:73`); the actual boss entity is **not** persisted. If the boss despawns or the server restarts, the task remains active but the boss is gone and no new one will spawn — see [Unfinished Things / TODOs](#unfinished-things--todos).

Definitions are in-memory only and re-parsed on every `onEnable()` (`SlayerModule.java:26-28`).

---

## API Exposed

- **No `ValmoraAPI` interface method.** `SlayerModule` is absent from `ValmoraAPI.java`. External code can only reach it by casting to the concrete `Valmora` class: `((Valmora) ValmoraAPI.getInstance()).getSlayerModule()` (`Valmora.java:427`).
- Public surface of `SlayerModule`: `getDefinition(String)` (case-insensitive), `getDefinitions()`, `getPlugin()` (`SlayerModule.java:50-52`).
- Script DSL event registered: **`slayer_start <slayer-id> <tier>`** (`SlayerStartEventFactory.java:23-24`). Documented in `docs/VALMORA_DOCUMENTATION.md` §36.2 and `docs/USER_DOCS.md` §20.
- No commands, no permissions, no GUI actions are registered by this module (`plugin.yml` has no slayer command).

---

## Dependencies & Consumers

### Dependencies (things SlayerModule uses)

| Dependency | How it's used | Where |
|---|---|---|
| **ScriptModule** | Registers the `slayer_start` EventFactory (`onEnable`) and runs completion events via `EventParser.parseList` | `SlayerModule.java:32`, `SlayerListener.java:103` |
| **MobModule (MobManager)** | Resolves and spawns the boss mob | `SlayerListener.java:109-114` |
| **PlayerManager** | Resolves the killer's session/active profile | `SlayerListener.java:146-149`, `SlayerStartEventFactory.java:49-51` |
| **EconomyService** (optional) | Charges activation cost | `SlayerStartEventFactory.java:61-69` |
| **Keys** | `SLAYER_BOSS_KEY` / `MOB_ID_KEY` PDC tags | `SlayerListener.java:41`, `SlayerListener.java:139` |
| **NotifyModule** (indirect) | `notify` completion events, when configured | `slayers/zombie.yml:11` |
| **EconomyModule** (indirect) | `economy_add` completion events, when configured | `slayers/zombie.yml:10` |

Load order: `slayerModule` is registered **after** `mobManager`, `scriptModule`, `playerManager`, and `economyModule` (`Valmora.java:218`, comment: "Depends on scriptModule + mobModule"). Because `SlayerModule.onEnable()` only touches `scriptModule` at enable-time, the dependency is satisfied by the registration order in `Valmora.java`.

### Consumers (things that use SlayerModule)

- **`Valmora.java`** — instantiates (`Valmora.java:180`), registers (`Valmora.java:218`), exposes (`Valmora.java:427`), and includes `slayers/` in the resource auto-copy list (`Valmora.java:477`).
- **Any scriptable system** — quest packages, GUI scripts, NPC dialogue, etc. can invoke the `slayer_start` script event. Nothing in the shipped configs currently wires it to a GUI or quest.
- **Cosmetic references** — `guis/skills_details.yml:86-92` renders a decorative "Slayer" `DISPLAY` button (no action). Various item/collection configs reference slayer-themed rewards by name only (`items/slayer_swords.yml`, `items/swords.yml:98-126`, `collections/combat/zombie.yml:13`).

---

## Unfinished Things / TODOs

1. **No slayer GUI / player-facing start path.** The only way to start a task is the `slayer_start` script event; no NPC, quest, or GUI calls it in the shipped config, and the Slayer button in `skills_details.yml` is `DISPLAY`-only. `docs/VALMORA_DOCUMENTATION.md` §36.1 and §36.3 mention a "slayer GUI" that does not exist.
2. **Shipped slayer YAML references missing boss mobs.** `boss-mob: zombie|spider|wolf` have no matching mob definitions in the shipped mobs — tasks reach the kill goal and then fail with `Slayer boss mob not found` (`SlayerListener.java:111`).
3. **`target-category` default `HOSTILE` is dead.** The parser defaults to `HOSTILE` (`SlayerModule.java:73`) but `matchesCategory` has no `HOSTILE` branch (`SlayerListener.java:124-144`) and no entity type is named `HOSTILE` — tasks without an explicit category never accumulate kills.
4. **Stuck tasks on boss loss.** `slayer.boss` persists (`SQLDataStore` variables column) and gates respawn (`SlayerListener.java:73`). If the boss despawns, dies by a non-player cause, or the server restarts, the task stays active with no way to complete or cancel it. There is no cancel/abandon command.
5. **Blank `boss-mob` locks the tier.** `SlayerListener.java:108` silently aborts boss spawning for a blank id, and since completion requires a boss kill, the task is permanently stuck (no warning to the player).
6. **No shared/team credit.** Kill credit requires `entity.getKiller()` (`SlayerListener.java:48`); damage-tagging, party credit, or last-hit-by-projection logic beyond Bukkit's default killer resolution is absent.
7. **No unit tests** for the module.
8. **String-literal duplication.** `SlayerStartEventFactory.java:54` and `:73-75` hardcode `"slayer.active"` / `"slayer.kills"` / `"slayer.boss"` instead of using `SlayerListener`'s `VAR_*` constants (`SlayerListener.java:24-26`) — a rename in one place silently breaks the other.
9. **Economy optionality.** If `getEconomy()` returns null, costs are not charged or verified (`SlayerStartEventFactory.java:62-68`); this is silent.
10. **Definition map stores original-case keys.** `definitions.put(def.getId(), ...)` (`SlayerModule.java:56`) with lowercase-only lookups (`SlayerModule.java:50`) means an uppercase character in any YAML key makes that slayer unreachable.

---

## Possible Improvements / Changes

1. **Add a slayer GUI** (or NPC dialogue) that lists tiers, shows cost/kills/rewards, and fires `slayer_start` on click — closing the gap with the docs that reference one.
2. **Make boss handling robust:**
   - Respawn the boss if the tagged entity dies or despawns without a matching killer.
   - Add a timeout/abandon mechanism (e.g., `/slayer cancel`, or auto-clear `slayer.active` after inactivity).
   - Warn the player immediately when `boss-mob` is blank or unresolvable.
3. **Add `HOSTILE` (and more) categories** to `matchesCategory` — e.g. `HOSTILE` → `entity instanceof Monster || entity instanceof Illager`, plus common groups (`ARTHROPOD`, `AQUATIC`, `NETHER`, `BOSS`).
4. **Fix the shipped config:** define `zombie`, `spider`, `wolf` mobs, or repoint `boss-mob` at existing mob ids; switch `notify chat ...` to the documented `io:chat` syntax.
5. **Use `getDamageSource()`-aware kill credit** (attacker resolution incl. projectiles, per `AGENTS.md` §11.8) and optionally a damage-contribution table for shared kills.
6. **Centralize variable names** into constants in a single shared class, and store the definitions map keys lowercased to fully honour `Registry` case-insensitivity.
7. **Expose the module on `ValmoraAPI`** (`getSlayerModule()`) so other modules/plugins can query definitions without a concrete cast.
8. **Persist boss identity structurally** (e.g., a dedicated `slayer_tasks` table or boss UUID validation against live entities) instead of relying on a persisted UUID that is only used as a null-gate.
9. **Add completion reward variety** — items, skill XP, points, tags — via existing script events (already possible: `give`, `skill` events, `point`, `tag`), plus optional per-tier rewards as structured YAML rather than raw event strings.
10. **Tests:** unit-test `parseDefinition` defaults/error paths and `matchesCategory` branch coverage (JUnit 5 + Mockito per `AGENTS.md` §9), and add a `YamlConfigLoadTest`-style validation for `slayers/zombie.yml`.
