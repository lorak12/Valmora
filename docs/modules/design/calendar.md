# Calendar Event Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.calendar` | **Module ID:** `calendar` | **Name:** "Calendar Events"
> **Dependencies:** `scriptModule` (event DSL compilation) + `timeModule` (TimeSnapshot / `ValmoraDayChangeEvent`)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Code Structure](#2-code-structure)
3. [Architecture & Key Classes](#3-architecture--key-classes)
4. [Configuration (YAML)](#4-configuration-yaml)
5. [Data Model / Persistence](#5-data-model--persistence)
6. [API Exposed](#6-api-exposed)
7. [Dependencies & Consumers](#7-dependencies--consumers)
8. [Unfinished Things / TODOs](#8-unfinished-things--todos)
9. [Possible Improvements / Changes](#9-possible-improvements--changes)

---

## 1. Overview

The **Calendar Event Module** schedules **recurring, date-based RPG events** against Valmora's in-game calendar (the Time module's season / phase / day-of-phase clock). Each calendar event is a pure data definition: a *trigger window* (optional season, optional phase, inclusive day range `day-start`…`day-end` within the 30-day phase) plus three **compiled script blocks**:

- `on-start` — runs the first day the event becomes active
- `on-end` — runs the day after the event stops being active
- `recurring-daily` — runs every day while the event is active

The module is *dumb glue*: it holds zero gameplay logic of its own. Everything an event actually does is expressed in the Valmora **script DSL** (`notifyall`, `foreach @all stat_modify …`, etc.), so a calendar event is just "when the calendar hits this window, run these scripts."

The out-of-the-box configuration (`seasonal.yml`) defines three seasonal events — **Harvest Festival** (Autumn/Early), **Winter Blessing** (Winter/Mid), **Spring Renewal** (Spring/Early) — which broadcast announcements and grant all online players temporary stat bonuses (`farming_fortune`, `magic_find`, `health_regen`) for the duration of their 30-day window.

Module lifecycle follows the standard `ReloadableModule` contract documented in `docs/MODULE_DEVELOPMENT.md` §2 — `onEnable()`/`onDisable()` are idempotent and hot-reload safe (`/valmora reload`).

---

## 2. Code Structure

The module is intentionally small: three classes plus a single config folder. It follows the standard `XModule.java` / `XListener.java` naming convention described in `AGENTS.md` §3, but has no dedicated `XRegistry`/`XLoader` class — definition storage and YAML parsing live directly inside the module class.

```
src/main/java/org/nakii/valmora/module/calendar/
├── CalendarEventModule.java      # ReloadableModule — state, loading, parse logic
├── CalendarEventListener.java    # Listener — reacts to ValmoraDayChangeEvent
└── CalendarEventDefinition.java  # Immutable data class + isActive(snapshot) predicate

src/main/resources/calendar/
└── seasonal.yml                  # Default events (copied to plugins/Valmora/calendar/)
```

### Wiring (`src/main/java/org/nakii/valmora/Valmora.java`)

| Step | Line | Code |
|---|---|---|
| Field declaration | `Valmora.java:116` | `private CalendarEventModule calendarEventModule;` |
| Instantiation | `Valmora.java:177` | `this.calendarEventModule = new CalendarEventModule(this);` |
| Registration | `Valmora.java:215` | `moduleManager.registerModule(calendarEventModule); // Depends on scriptModule + timeModule` |
| Public getter | `Valmora.java:424` | `public CalendarEventModule getCalendarEventModule() { return calendarEventModule; }` |
| Resource seeding | `Valmora.java:476` | `name.startsWith("calendar/")` in `saveAllResources()` — copies jar resources to `plugins/Valmora/calendar/` only if the file does **not** already exist (never overwrites server edits) |

The module is registered after `hudItemModule` and before `reforgeModule` (`Valmora.java:214–216`). This is correct because it needs `script` (registered first, `Valmora.java:188`) and `time` (`Valmora.java:189`) both enabled before its `onEnable()` runs. Note that the load-order list in `docs/MODULE_DEVELOPMENT.md` §9 (`MODULE_DEVELOPMENT.md:495-517`) predates this module and does not mention `calendar` — the authoritative order is the comment block in `Valmora.java:186-222`.

---

## 3. Architecture & Key Classes

### 3.1 `CalendarEventModule` (implements `ReloadableModule`)

`CalendarEventModule.java:19` — the module owns all state:

| Field | Type | Declared | Purpose |
|---|---|---|---|
| `plugin` | `Valmora` | `CalendarEventModule.java:21` | Plugin instance (config loading, time manager, script parser access) |
| `definitions` | `Map<String, CalendarEventDefinition>` (HashMap) | `CalendarEventModule.java:22` | Id → definition. **Direct HashMap, keys are NOT lowercased** — note the divergence from the `Registry` case-insensitivity convention in `AGENTS.md` §7.2 |
| `activeEventIds` | `Set<String>` (HashSet) | `CalendarEventModule.java:23` | Ids of events currently inside their trigger window |
| `listener` | `CalendarEventListener` | `CalendarEventModule.java:24` | The registered Bukkit listener |

**`onEnable()`** (`CalendarEventModule.java:31-49`):
1. Clears `definitions` and `activeEventIds` (idempotency for hot reload, `CalendarEventModule.java:32-33`).
2. `loadDefinitions()` — loads & parses every `calendar/*.yml` file (`CalendarEventModule.java:34`).
3. Creates and registers `CalendarEventListener` (`CalendarEventModule.java:36-37`).
4. **Seeds the active set without firing events** (`CalendarEventModule.java:39-48`): reads `plugin.getTimeManager().getSnapshot()` and adds every definition whose `isActive(snapshot)` is `true` to `activeEventIds`. This is a deliberate design decision — on plugin load / hot reload, events already in progress do **not** replay `on-start`; only `recurring-daily` (and eventually `on-end`) will fire going forward.

**`onDisable()`** (`CalendarEventModule.java:52-59`):
1. `HandlerList.unregisterAll(listener)` and nulls it (`CalendarEventModule.java:53-56`) — mandatory cleanup per `AGENTS.md` §6.2 to prevent duplicate handlers after reload.
2. Clears both maps (`CalendarEventModule.java:57-58`).

**Definition accessors:**
- `getDefinitions()` → `Collection<CalendarEventDefinition>` (`CalendarEventModule.java:67-69`)
- `getDefinition(String id)` → direct `definitions.get(id)`, **case-sensitive** (`CalendarEventModule.java:71-73`)
- `getActiveEventIds()` → live mutable `Set<String>` (`CalendarEventModule.java:75-77`) — listeners/consumers may read it but should not mutate it.

**Loading — `loadDefinitions()`** (`CalendarEventModule.java:79-82`):
```java
YamlLoader<CalendarEventDefinition> loader = new YamlLoader<>(plugin, "calendar", "Calendar Event");
loader.load(this::parseDefinition, def -> definitions.put(def.getId(), def));
```
Uses the generic `YamlLoader<T>` (`infrastructure/config/YamlLoader.java:18`) with folder `calendar` and type name `Calendar Event`. `YamlLoader.load(...)` (`YamlLoader.java:37-73`) iterates every `*.yml` file in `plugins/Valmora/calendar/`, and for each **top-level key** in the file invokes the parser with `(id = key, section, relativePath)`; successful results are handed to the register action; failures are accumulated and printed as warnings by `reportErrors(...)` (`YamlLoader.java:113-123`):
```
Failed to load some Calendar Event. Please check your configuration files.
------------------------------
- [calendar/seasonal.yml] Calendar event 'x': invalid season.
------------------------------
Successfully loaded N Calendar Event.
```
Multiple events per file and multiple files are both supported.

**Parsing — `parseDefinition(...)`** (`CalendarEventModule.java:84-127`):
1. Reads the optional `trigger` section (`CalendarEventModule.java:86`). Defaults are `season = null` (any), `phase = null` (any), `dayStart = 1`, `dayEnd = 30` (`CalendarEventModule.java:87-90`).
2. `season` — `Season.valueOf(trigger.getString("season").toUpperCase())`; an unrecognized value (not `SPRING`/`SUMMER`/`AUTUMN`/`WINTER`) fails the event with `invalid season.` (`CalendarEventModule.java:93-98`).
3. `phase` — `Phase.valueOf(...toUpperCase())`; unrecognized value (not `EARLY`/`MID`/`LATE`) fails with `invalid phase.` (`CalendarEventModule.java:100-105`).
4. `day-start` / `day-end` via `getInt(key, default)` (`CalendarEventModule.java:107-108`). **There is no clamp or `dayStart <= dayEnd` validation** — an invalid range simply yields an event that never activates.
5. The three script blocks are compiled with `plugin.getScriptModule().getEventParser().parseList(...)` (`CalendarEventModule.java:111-120`). If a key is absent, a **no-op** `ctx -> {}` `CompiledEvent` is used — all three blocks are optional.
6. Success: `LoadResult.success(new CalendarEventDefinition(id, season, phase, dayStart, dayEnd, onStart, onEnd, recurringDaily))` (`CalendarEventModule.java:122-123`).
7. Any exception is caught and returned as `LoadResult.failure("[filePath] Failed to parse calendar event 'id': …")` (`CalendarEventModule.java:124-126`). A single bad event does **not** kill the whole load — other events still register, and the failure is logged.

`LoadResult<T,E>` (`api/config/LoadResult.java:8`) is the shared success/failure carrier: `success(value)` / `failure(error)`, `isSuccess()`, `getValue()`, `getError()`.

### 3.2 `CalendarEventDefinition` (immutable data class)

`CalendarEventDefinition.java:8` — the parsed contract of a single event.

| Field | Type | Line | Meaning |
|---|---|---|---|
| `id` | `String` | `CalendarEventDefinition.java:10` | The YAML top-level key |
| `season` | `Season` | `CalendarEventDefinition.java:11` | `null` = **any** season |
| `phase` | `Phase` | `CalendarEventDefinition.java:12` | `null` = **any** phase |
| `dayStart` / `dayEnd` | `int` | `CalendarEventDefinition.java:13-14` | Inclusive window within the phase's 30 days |
| `onStart` / `onEnd` / `recurringDaily` | `CompiledEvent` | `CalendarEventDefinition.java:15-17` | Compiled script blocks (no-op if absent) |

**Activation predicate — `isActive(TimeSnapshot)`** (`CalendarEventDefinition.java:40-44`):
```java
if (season != null && season != snapshot.season()) return false;
if (phase != null && phase != snapshot.phase()) return false;
return snapshot.dayInPhase() >= dayStart && snapshot.dayInPhase() <= dayEnd;
```
An event is active only when **all** specified constraints match the snapshot. With no `season` and no `phase`, the event is active every single day of the year where `dayStart <= dayInPhase <= dayEnd` (i.e., 30 days out of every 30-day phase, forever).

### 3.3 `CalendarEventListener` (Listener)

`CalendarEventListener.java:13` — registered by the module's `onEnable()`; subscribes to the **Time module's** `ValmoraDayChangeEvent` (`module/time/event/ValmoraDayChangeEvent.java:7`), which the `TimeManager` fires once per real in-game day rollover in the configured world (`TimeManager.java:80-91`).

**`onDayChange(...)`** (`CalendarEventListener.java:21-60`) — the whole trigger engine:

1. **Context construction** (`CalendarEventListener.java:24-25`):
   ```java
   var ctx = new SimpleExecutionContext(null, (org.bukkit.Location) null, new YamlConfiguration());
   ```
   A server-wide context with a **null caster and null location** and an empty `YamlConfiguration` as params (via the `SimpleExecutionContext(LivingEntity, Location, ConfigurationSection)` constructor, `api/execution/SimpleExecutionContext.java:27-29`). The code comment is explicit: only server-wide script actions (like `foreach @all …`) may be used; anything requiring a caster/target/location will no-op or fail. `recurring-daily`'s `notifyall` examples work because `notifyall` iterates `Bukkit.getOnlinePlayers()` itself (`NotifyAllEvent.java:47-49`), and `foreach @all stat_modify …` builds a fresh per-player context with the player as caster (`ForeachEventFactory.java:54-59` and `:82-84`).

2. **Transition detection** (`CalendarEventListener.java:30-39`) — diffs the previously active set against fresh evaluation:
   - `!wasActive && isNowActive` → `started`
   - `wasActive && !isNowActive` → `ended`
   Because `TimeManager` passes the snapshot of the **new** day (`TimeManager.java:86-90`), `isActive` is evaluated against the day the event becomes/ceases active. With inclusive `[dayStart, dayEnd]` bounds, `on-end` fires on the day-change into `dayEnd + 1`.

3. **`on-end` phase** (`CalendarEventListener.java:42-46`) — removes ended ids from `activeEventIds`, then executes `getOnEnd()`. Ends are processed **before** starts so a same-day boundary edge case (one event ending, another starting) is handled cleanly.

4. **`on-start` phase** (`CalendarEventListener.java:49-53`) — adds started ids to `activeEventIds`, then executes `getOnStart()`.

5. **`recurring-daily` phase** (`CalendarEventListener.java:56-59`) — iterates the now-current `activeEventIds` and executes each event's `getRecurringDaily()`. Because this runs after step 4, **an event that starts today gets both `on-start` and `recurring-daily` on that same day**; an event that ends today gets `on-end` but no `recurring-daily`.

### 3.4 The trigger clock (Time module, dependency)

The Calendar module reads the clock through two channels:

- **Snapshot on enable** — `plugin.getTimeManager().getSnapshot()` (`CalendarEventModule.java:40-47`) for seeding the active set.
- **Day-change events** — `ValmoraDayChangeEvent` carries the current `TimeSnapshot` (`ValmoraDayChangeEvent.java:11-23`, `getNewDay()` at `:21-23`).

`TimeSnapshot` (`module/time/TimeSnapshot.java:3-7`) is a record: `hour, minute, dayInPhase, phase, season, year, totalDays, phaseName, seasonName`. Derived from world time in `TimeManager.getSnapshot()` (`TimeManager.java:120-140`):

| Calendar unit | Derivation | Values |
|---|---|---|
| `dayInPhase` | `floorMod(totalDays, 30) + 1` | 1–30 (`TimeManager.java:129`) |
| `phase` | `Phase.values()[floorMod(totalDays/30, 3)]` | `EARLY`, `MID`, `LATE` (`TimeManager.java:130`) |
| `season` | `Season.values()[floorMod(totalDays/90, 4)]` | `SPRING`, `SUMMER`, `AUTUMN`, `WINTER` (`TimeManager.java:131`) |
| `year` | `max(1, totalDays/360 + 1)` | ≥ 1 (`TimeManager.java:132`) |

Consequences for calendar events:
- A phase is 30 days, a season 90 days (3 phases), a year 360 days.
- `day-start`/`day-end` are relative to **the current phase** and reset to 1 on every phase change. An event therefore **cannot span multiple phases** with a single definition — a season-wide event needs three definitions (one per phase) or a `phase: null` design that repeats every phase.
- `dayEnd > 30` or `dayStart < 1` makes the event permanently inactive (never matches `dayInPhase`).

---

## 4. Configuration (YAML)

All files live in `plugins/Valmora/calendar/` (auto-seeded from `src/main/resources/calendar/` on first run). Any number of `.yml` files; each top-level key is one event.

The **only shipped file** is `seasonal.yml` (46 lines) defining three events. Full reference of every option, with defaults taken from `parseDefinition(...)` (`CalendarEventModule.java:84-127`):

| Key | Type | Default | Description |
|---|---|---|---|
| `<event-id>` | — | *required* | Top-level key; the event's id. Also the YAML key used as the definition id. |
| `trigger` | section | — | Optional. Entire trigger block may be omitted → event active every day (`day-start`…`day-end` of every phase). |
| `trigger.season` | string | *(none = any season)* | One of `SPRING`, `SUMMER`, `AUTUMN`, `WINTER` (case-insensitive in config; `toUpperCase()` at parse). Anything else fails the event with `invalid season.` |
| `trigger.phase` | string | *(none = any phase)* | One of `EARLY`, `MID`, `LATE` (case-insensitive). Anything else fails with `invalid phase.` |
| `trigger.day-start` | int | `1` | First active day-of-phase (inclusive). No range clamping. |
| `trigger.day-end` | int | `30` | Last active day-of-phase (inclusive). No range clamping; `dayStart <= dayEnd` is not enforced. |
| `on-start` | list of strings | *(empty / no-op)* | Script DSL lines executed once, on the first day-change where the event becomes active. |
| `on-end` | list of strings | *(empty / no-op)* | Script DSL lines executed once, on the day-change where the event ceases to be active. |
| `recurring-daily` | list of strings | *(empty / no-op)* | Script DSL lines executed on **every** day-change while the event is active. |

### 4.1 `seasonal.yml` — shipped default events

`src/main/resources/calendar/seasonal.yml`

**`harvest_festival`** (`seasonal.yml:1-16`) — Autumn / Early / days 1–30
- `on-start` (`seasonal.yml:7-11`): title + subtitle + chat announcement, then `foreach @all stat_modify add farming_fortune 25`.
- `on-end` (`seasonal.yml:12-14`): chat farewell, `foreach @all stat_modify add farming_fortune -25` (reverts the bonus).
- `recurring-daily` (`seasonal.yml:15-16`): action bar reminder.

**`winter_blessing`** (`seasonal.yml:18-31`) — Winter / Mid / days 1–30
- `on-start` (`seasonal.yml:24-26`): chat announcement + `foreach @all stat_modify add magic_find 10`.
- `on-end` (`seasonal.yml:27-29`): chat farewell + `stat_modify add magic_find -10`.
- `recurring-daily` (`seasonal.yml:30-31`): action bar reminder.

**`spring_renewal`** (`seasonal.yml:33-46`) — Spring / Early / days 1–30
- `on-start` (`seasonal.yml:39-41`): chat announcement + `foreach @all stat_modify add health_regen 5`.
- `on-end` (`seasonal.yml:42-44`): chat farewell + `stat_modify add health_regen -5`.
- `recurring-daily` (`seasonal.yml:45-46`): action bar reminder.

### 4.2 Script DSL used by events

Calendar scripts are compiled by `EventParser.parseList(...)` (`module/script/event/EventParser.java:98-111`), one line per `CompiledEvent`, executed in order. Relevant factories:

- **`notifyall <message> [category:<name>] [io:<type>] [key:value …]`** — broadcast to all online players (`NotifyAllEvent.java:12-51`). `io:` values registered by `NotifyModule.onEnable()` (`module/notify/NotifyModule.java:21-27`): `chat`, `actionbar`, `title`, `subtitle`, `bossbar`, `sound`, `advancement`. In the shipped file: `io:title`, `io:subtitle`, `io:chat`, `io:actionbar`. Message tokens are re-joined with spaces; MiniMessage tags like `<gold>` survive because they contain no `:`.
- **`foreach @all <inner events…>`** — run inner events once per online player with that player as caster (`ForeachEventFactory.java:54-59`). Required because the calendar context has a null caster; e.g. `foreach @all stat_modify add farming_fortune 25`.
- **`stat_modify add|set|reset <stat_id> [value]`** — alter a player's base stat (`StatModifyEventFactory.java:21-64`). Operates on `ctx.getPlayerCaster()` only, hence the `foreach @all` wrapper. `stat_id` values are defined by `stats/*.yml` and mapped in `config.yml` under `combat:`/`mining:` (e.g. `farming_fortune`, `magic_find`, `health_regen`).

Other events registered by the Script engine (`ScriptModule.java:62-70`) and by `NotifyModule` (`NotifyModule.java:29-30`) are also available inside calendar scripts (`condition`, `give`, `variable`, `tag`, `teleport`, `spawnmob`, `stat_modify`, `foreach`, `runscript`, `notify`, `notifyall`). Any unknown event name is logged as a warning at compile time and compiled to a no-op (`EventParser.java:64-68`).

---

## 5. Data Model / Persistence

- **No database involvement.** The Calendar module never touches `DataStore`/DAO layer; `activeEventIds` and `definitions` are purely in-memory (`CalendarEventModule.java:22-23`).
- **Only persistent state is YAML on disk** — `plugins/Valmora/calendar/*.yml`, loaded on every `onEnable()` (server start and `/valmora reload`).
- **State reconstruction semantics:** the active set is rebuilt from the *current* snapshot at enable time (`CalendarEventModule.java:39-48`) without firing `on-start`. Consequences:
  - If the server was offline for part of an event window, the event is silently "in progress" on load — players see `recurring-daily` (and later `on-end`) but **not** `on-start`.
  - `on-end` is **not** fired for events whose window passed entirely while the server was offline — there is no catch-up / history replay.
  - Events that started before load but are still active on load **do not** double-fire `on-start` — this is the intended protection against reload double-firing.
- The Time module's `time.yml` (`day-offset`, `TimeManager.java:44-51`, `:150-159`) is the calendar's own persistence and is what ultimately drives these windows.

---

## 6. API Exposed

**Not exposed through the `ValmoraAPI` interface** (`api/ValmoraAPI.java:9-70` has no calendar getter). It is reachable only via the concrete plugin class:

```java
Valmora plugin = Valmora.getInstance();                       // Valmora.java:278-280
CalendarEventModule cal = plugin.getCalendarEventModule();    // Valmora.java:424
```

Public surface of the module (`CalendarEventModule.java`):

| Member | Signature | Line |
|---|---|---|
| `getDefinitions()` | `Collection<CalendarEventDefinition>` | `CalendarEventModule.java:67` |
| `getDefinition(String id)` | `CalendarEventDefinition` (nullable; case-sensitive) | `CalendarEventModule.java:71` |
| `getActiveEventIds()` | `Set<String>` (live view) | `CalendarEventModule.java:75` |

Plus the per-definition getters and `isActive(TimeSnapshot)` on `CalendarEventDefinition.java:31-44`, which any consumer can use to evaluate a definition against an arbitrary `TimeSnapshot`.

No public commands exist (`Valmora.java:227-259` registers no `/calendar` command).

---

## 7. Dependencies & Consumers

### Dependencies (compile-time / enable-time)

| Dependency | Access point | Used for |
|---|---|---|
| `scriptModule` | `plugin.getScriptModule()` (`CalendarEventModule.java:111`) | `getEventParser().parseList(...)` — compiles script blocks |
| `timeModule` | `plugin.getTimeManager()` (`CalendarEventModule.java:40`) + `ValmoraDayChangeEvent` (`CalendarEventListener.java:22`) | Snapshot seeding + day-change trigger |
| `notify` (indirect) | `ValmoraAPI.getNotifyManager()` inside `NotifyAllEvent` | `notifyall` script action used by shipped events |
| `stat` (indirect) | `ValmoraAPI.getPlayerManager()` inside `StatModifyEventFactory` | `stat_modify` script action used by shipped events |

The hard (Java-level) dependencies are only `script` and `time`; `notify` and `stat` are reached through the script DSL at execution time, which is why the registration comment at `Valmora.java:215` names just the two. Load order (`script` → `time` → … → `calendar`, `Valmora.java:188-215`) guarantees both are enabled first.

### Consumers

**None.** A repo-wide grep for `CalendarEventModule` / `CalendarEvent` / `calendar` (`src/**`) matches only the module itself, `Valmora.java` wiring, and the `calendar/` resource seed. The module is a leaf in the dependency graph — it has no downstream consumers yet.

---

## 8. Unfinished Things / TODOs

- **No admin tooling.** Events can only be created/edited by hand-editing YAML and running `/valmora reload`. There is no `/calendar` command to list active events, force-start/force-end, or preview windows.
- **Not in `ValmoraAPI`.** Unlike most sibling modules, the module is only reachable via the concrete `Valmora` class (`Valmora.java:424`); `api/ValmoraAPI.java` has no `getCalendarEventModule()`. External plugins cannot reach it through the API.
- **No catch-up / offline handling.** Events that start or end while the server is offline never fire `on-start`/`on-end` (active set is seeded from the live snapshot without firing, `CalendarEventModule.java:39-48`). No last-fired-day persistence exists to reconcile missed transitions.
- **`recurring-daily` + `on-start` fire on the same day.** Because the recurring loop runs after the start loop (`CalendarEventListener.java:49-59`), a starting event executes both blocks on day `day-start`. Not documented as intentional.
- **No validation of the day window.** `day-start`/`day-end` are not clamped to 1–30 and `dayStart <= dayEnd` is not enforced (`CalendarEventModule.java:107-108`); invalid values silently produce an event that never activates.
- **Case-sensitive id lookup.** `getDefinition(id)` (`CalendarEventModule.java:71-73`) bypasses the `Registry` case-insensitivity convention (`AGENTS.md` §7.2).
- **No unit tests** for the module (no test source under `src/test/...` covers it; no calendar entry in the test tree).
- **TODO in project roadmap** (`docs/todo.md:65-66`): richer event content ("events like a schematic that appears based on time of year or some drops that are added when the event is active") — none of that exists yet; the module today only runs script blocks.
- **`docs/MODULE_DEVELOPMENT.md` §9 load-order list** (`MODULE_DEVELOPMENT.md:495-517`) is stale — it omits `calendar` (and other late modules). The comment block in `Valmora.java:186-222` is the live source of truth.

---

## 9. Possible Improvements / Changes

- **Expose via `ValmoraAPI`** — add `getCalendarEventModule()` to the interface (`api/ValmoraAPI.java`) so external code can query definitions/active events, matching the pattern in `MODULE_DEVELOPMENT.md` §8.
- **Add a `/calendar` admin command** (registered in `Valmora.onEnable()` per `AGENTS.md` §6.3) for listing events, showing current active set, and forcing `on-start`/`on-end` for testing.
- **Offline catch-up / last-fired tracking** — persist (in `time.yml` or the DB layer) the last day-change each event's blocks ran, then replay missed `on-start`/`on-end` transitions on enable. Requires keeping the "no double-fire on reload" guarantee.
- **Extend the trigger model** for whole-season or multi-phase windows (e.g. an optional `season-window` that spans all 30-day phases, or an offset-based `day-start` that can exceed 30), so admins don't need to duplicate definitions per phase.
- **Validate windows at parse time** — clamp or reject `day-start`/`day-end` outside 1–30 and reject `dayStart > dayEnd` with a clear `LoadResult.failure` message (`CalendarEventModule.java:107-108`).
- **Normalize id lookup** — store/retrieve definition ids lowercased (like `Registry`) or make `getDefinition` case-insensitive, per `AGENTS.md` §7.2.
- **Pass event metadata into the script context** — currently params are an empty `YamlConfiguration` (`CalendarEventListener.java:25`); exposing `event_id`, `season`, `dayInPhase` as params would let event scripts branch on their own trigger window.
- **Console/server logging on transitions** — log when events start/end (the shipped events already chat-announce, but no plugin logger output exists).
- **Per-event `on-join` block** — many RPG events grant a buff on start; players joining mid-event currently get the buff only if the event uses `recurring-daily` with a `foreach @all stat_modify` line. A dedicated `on-join` block (wired via `PlayerJoinEvent`) would be cleaner than abusing `recurring-daily`.
- **Unit tests** following the `ExpressionTest` pattern (`AGENTS.md` §9): mock `ValmoraAPI`, use `DummyExecutionContext`-style stubs, and assert transition detection logic in `CalendarEventListener`.
