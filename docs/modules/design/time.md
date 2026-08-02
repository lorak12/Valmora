# Time Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.time` | **Module ID:** `time` | **Name:** "Time"
> **Dependencies:** none (hard). Runtime reads: `ui` (action-bar season announcement), `script` (consumes it), `calendar` (consumes it), `quest` (consumes it via `$time.*$` variables)

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

The **Time Module** turns the vanilla world day/night clock into Valmora's **RPG calendar**. It is a *read-only observer*: it never writes `World.setTime(...)` — it derives a full RPG date (hour, minute, day-of-phase, phase, season, year) from a single configured world's `getTime()` / `getFullTime()` and exposes it as an immutable `TimeSnapshot` to every other system.

The calendar model is entirely numeric and hard-coded in `TimeManager`:

| Calendar unit | Length | Derivation |
|---|---|---|
| Day | 1 Minecraft day = 24000 ticks | `world.getFullTime() / 24000 + dayOffset` |
| Phase | **30 days** | `EARLY`, `MID`, `LATE` |
| Season | **90 days** (3 phases) | `SPRING`, `SUMMER`, `AUTUMN`, `WINTER` |
| Year | **360 days** (4 seasons) | ≥ 1, `totalDays / 360 + 1` |

The module also acts as the **time authority for the whole plugin**: it fires three Bukkit events that downstream systems subscribe to:

- `ValmoraTimeTickEvent` — once per **second** (every 20 ticks) with the current snapshot.
- `ValmoraDayChangeEvent` — once per real in-game day rollover in the configured world.
- `ValmoraSeasonChangeEvent` — whenever a phase or season boundary is crossed (with `isNewSeason` / `isNewYear` flags).

Consumers today: the **script system** (`$time.*$` variables via `TimeVariableProvider`, registered by `ScriptModule`), the **scoreboard** (ui module, `ScoreboardUI` + `ui.yml`), the **calendar module** (`CalendarEventModule` listens to `ValmoraDayChangeEvent`), and **quests** (conditions like `$time.is_day$`).

The in-game position is persisted to `plugins/Valmora/time.yml` (`day-offset`), so the calendar does not reset on restart. Starting position is configured under `config.yml` → `time:` and only used on first launch.

Module lifecycle follows the standard `ReloadableModule` contract documented in `docs/MODULE_DEVELOPMENT.md` §2 — `onEnable()`/`onDisable()` are idempotent and hot-reload safe (`/valmora reload`). Note that `TimeModule` does **not** register any listeners of its own; its only resources are a repeating scheduler task and a small YAML file.

---

## 2. Code Structure

Nine files: the module class, the engine, the command, the value type, two enums, and three event classes.

```
src/main/java/org/nakii/valmora/module/time/
├── TimeModule.java                  # ReloadableModule facade (39 lines)
├── TimeManager.java                 # Core engine: snapshot math, tick loop, persistence (190 lines)
├── TimeCommand.java                 # /time command executor (55 lines)
├── TimeSnapshot.java                # Immutable calendar value record (30 lines)
├── Phase.java                       # EARLY | MID | LATE enum (5 lines)
├── Season.java                      # SPRING | SUMMER | AUTUMN | WINTER enum (5 lines)
└── event/
    ├── ValmoraTimeTickEvent.java    # Fired every second (29 lines)
    ├── ValmoraDayChangeEvent.java   # Fired on day rollover (33 lines)
    └── ValmoraSeasonChangeEvent.java# Fired on phase/season boundary (51 lines)

src/test/java/org/nakii/valmora/module/time/
└── TimeSnapshotTest.java            # 15 unit tests for TimeSnapshot (100 lines)

src/test/java/org/nakii/valmora/module/script/variable/
└── TimeVariableProviderTest.java    # 13 unit tests for $time.*$ variables (122 lines)

src/main/resources/
├── config.yml                       # time: block — §4
├── plugin.yml                       # time: command declaration — §3.3
└── ui.yml                           # scoreboard lines consuming $time.*$ — §7.2
```

### Wiring (`src/main/java/org/nakii/valmora/Valmora.java`)

| Step | Line | Code |
|---|---|---|
| Field declaration | `Valmora.java:95` | `private TimeModule timeModule;` |
| Instantiation | `Valmora.java:161` | `this.timeModule = new TimeModule(this);` |
| Registration | `Valmora.java:189` | `moduleManager.registerModule(timeModule); // No dependencies; scoreboard and scripts read from it` |
| Command registration | `Valmora.java:241` | `getCommand("time").setExecutor(new TimeCommand(timeModule.getTimeManager()));` |
| API getter | `Valmora.java:337-339` | `public TimeManager getTimeManager() { return timeModule.getTimeManager(); }` |
| Command declaration | `plugin.yml:29-31` | `time:` with usage `/time [info\|reset]` |

The module is registered **second**, immediately after `scriptModule` (`Valmora.java:188-189`). This is the earliest position that allows everything else to depend on it — `calendarEventModule` explicitly does (`Valmora.java:215`, `// Depends on scriptModule + timeModule`), and the scoreboard/scripts read it at runtime. The registration comment at `Valmora.java:189` is the source of the "no dependencies; scoreboard and scripts read from it" contract. The authoritative load order is the comment block at `Valmora.java:186-222`; the list in `docs/MODULE_DEVELOPMENT.md` §9 (`MODULE_DEVELOPMENT.md:493-517`) and `docs/VALMORA_DOCUMENTATION.md` §4 (`VALMORA_DOCUMENTATION.md:243-271`) match for the early modules.

---

## 3. Architecture & Key Classes

### 3.1 `TimeModule` (implements `ReloadableModule`)

`TimeModule.java:6` — a thin facade. It holds no state of its own beyond a reference to a single `TimeManager` instance created in the constructor (`TimeModule.java:11-14`). All lifecycle work is delegated:

| Method | Line | Behavior |
|---|---|---|
| `onEnable()` | `TimeModule.java:17-19` | Delegates to `timeManager.onEnable()`. |
| `onDisable()` | `TimeModule.java:22-24` | Delegates to `timeManager.onDisable()`. |
| `getId()` | `TimeModule.java:27-29` | Returns `"time"` (lowercase, unique). |
| `getName()` | `TimeModule.java:32-34` | Returns `"Time"`. |
| `getTimeManager()` | `TimeModule.java:36-38` | Exposes the manager (used by `Valmora.java:241` and `Valmora.java:337-339`). |

`onEnable()`/`onDisable()` are idempotent because `TimeManager.onEnable()` re-initializes all state and `onDisable()` cancels the single task it tracks (see below).

### 3.2 `TimeManager` — the engine

`TimeManager.java:19`. Owns every piece of state:

| Field | Type | Line | Purpose |
|---|---|---|---|
| `plugin` | `Valmora` | `TimeManager.java:21` | Config access, scheduler, logger, `UIManager` access for announcements |
| `worldName` | `String` | `TimeManager.java:23` | Configured world whose clock is read (`time.world`, default `"world"`) |
| `seasonNames` | `List<String>` | `TimeManager.java:24` | Display names per season ordinal (`time.season-names`) |
| `phaseNames` | `List<String>` | `TimeManager.java:25` | Display names per phase ordinal (`time.phase-names`) |
| `dayOffset` | `long` | `TimeManager.java:27` | Persistent offset aligning world days to the RPG calendar start |
| `lastWorldDay` | `long` | `TimeManager.java:28` | Last observed world day (`getFullTime()/24000`) — rollover detector |
| `lastPhase` | `Phase` | `TimeManager.java:29` | Previously observed phase (for season/phase-change detection) |
| `lastSeason` | `Season` | `TimeManager.java:30` | Previously observed season (for season/phase-change detection) |
| `dayCheckTask` | `BukkitTask` | `TimeManager.java:32` | The repeating tick task (1 per second) |

**`onEnable()`** (`TimeManager.java:38-63`):
1. Reads `time.world`, `time.season-names`, `time.phase-names` from `plugin.getConfig()` (`TimeManager.java:40-42`).
2. Loads or creates `time.yml` (`TimeManager.java:44-51`): if it exists, `day-offset` is read with `getLong("day-offset", computeInitialOffset())` — note the fallback calls `computeInitialOffset()` as the default, which itself reads config. If the file does not exist, the offset is computed and immediately saved (`TimeManager.java:48-51`).
3. Captures the initial snapshot and stores `lastPhase`/`lastSeason` (`TimeManager.java:53-55`), and seeds `lastWorldDay` from the world's current full time — or `0` if the world isn't loaded (`TimeManager.java:56-57`).
4. Starts the tick task: `Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L)` (`TimeManager.java:59`) — a **synchronous** main-thread repeating task, delay 20 ticks, period 20 ticks (once per second). Consistent with `AGENTS.md` §11.4.
5. Logs the loaded position: `Time loaded: Early Spring, Day 1, Year 1 (06:00)` (`TimeManager.java:60-62`).

**`onDisable()`** (`TimeManager.java:65-71`): cancels `dayCheckTask` and nulls it, then calls `save()` to persist `dayOffset`. The save-on-disable guarantees a clean reload/restart never loses the calendar position.

**`tick()`** (`TimeManager.java:73-109`) — the core loop, runs once per second:
1. Resolves the world; returns silently if it no longer exists (`TimeManager.java:74-75`).
2. Builds a fresh `TimeSnapshot` and fires `ValmoraTimeTickEvent` (`TimeManager.java:77-78`).
3. Computes `currentWorldDay = world.getFullTime() / 24000` (`TimeManager.java:80`). The `lastWorldDay < 0` guard (`TimeManager.java:81-84`) is defensive — `lastWorldDay` is always seeded in `onEnable`.
4. On rollover (`currentWorldDay > lastWorldDay`, `TimeManager.java:86`): updates `lastWorldDay`, builds a **new-day** snapshot, fires `ValmoraDayChangeEvent` (`TimeManager.java:88-90`).
5. If the new day's phase **or** season differs from the last observed (`TimeManager.java:92`), computes flags and fires `ValmoraSeasonChangeEvent` (`TimeManager.java:93-99`):
   - `isNewSeason = daySnap.season() != lastSeason` (`TimeManager.java:93`)
   - `isNewYear = isNewSeason && season == SPRING && phase == EARLY` (`TimeManager.java:94-96`)
   - Then updates `lastPhase`/`lastSeason` (`TimeManager.java:101-102`).
6. If a new season truly started, broadcasts the announcement (`TimeManager.java:104-106`).

> **Semantics note:** `ValmoraSeasonChangeEvent` fires on **phase changes too** (every 30 days), not just season changes (every 90 days). The `isNewSeason` flag is what distinguishes the two. The event name is therefore slightly misleading — it is really a "calendar boundary crossed" event.

**`notifySeasonChange(TimeSnapshot)`** (`TimeManager.java:111-118`): builds a hard-coded MiniMessage string
```java
"<gold><bold>✦ A new season begins — " + snap.phaseName() + " " + snap.seasonName() + " ✦</bold></gold>"
```
and shows it as a **temporary action bar** on every online player for **120 ticks** (6 seconds) via `ValmoraAPI.getInstance().getUIManager().getActionBar().showTemporary(p, msg, 120)` (`TimeManager.java:114-117`, `ActionBarUI.showTemporary` at `module/ui/ActionBarUI.java:35`). This is a *runtime-only* dependency on the ui module — safe despite `ui` loading after `time`, because the look-up happens lazily at fire time, not at enable time.

**`getSnapshot()`** (`TimeManager.java:120-140`) — the heart of the calendar math. Returns `TimeSnapshot.EPOCH` if the world isn't loaded (`TimeManager.java:122`). Otherwise:

| Value | Derivation | Line |
|---|---|---|
| `mcTick` | `world.getTime()` — the 0..23999 time-of-day | `TimeManager.java:124` |
| `totalDays` | `world.getFullTime() / 24000 + dayOffset` | `TimeManager.java:125` |
| `hour` | `(int) ((mcTick / 1000 + 6) % 24)` | `TimeManager.java:127` |
| `minute` | `(int) ((mcTick % 1000) * 60 / 1000)` | `TimeManager.java:128` |
| `dayInPhase` | `(int) (Math.floorMod(totalDays, 30)) + 1` → 1..30 | `TimeManager.java:129` |
| `phase` | `Phase.values()[(int) (Math.floorMod(totalDays / 30, 3))]` | `TimeManager.java:130` |
| `season` | `Season.values()[(int) (Math.floorMod(totalDays / 90, 4))]` | `TimeManager.java:131` |
| `year` | `Math.max(1, (int) (totalDays / 360) + 1)` → ≥ 1 | `TimeManager.java:132` |
| `phaseName` | `phaseNames.get(phase.ordinal())` if present, else `capitalize(phase.name())` | `TimeManager.java:134-135` |
| `seasonName` | `seasonNames.get(season.ordinal())` if present, else `capitalize(season.name())` | `TimeManager.java:136-137` |

Notes on the math:
- **Hour offset +6:** Minecraft day starts at tick 0 = 06:00; `(mcTick/1000 + 6) % 24` maps tick 0 → hour 6, tick 6000 (noon) → hour 12, tick 18000 (midnight) → hour 0.
- **Negative-safe:** `Math.floorMod` (not `%`) is used for phase/season indexing so negative `totalDays` (possible with a large negative offset or world time) never indexes out of bounds.
- **Phase/season length is fixed:** 30 days/phase, 3 phases/season, 4 seasons/year, 360 days/year. These constants are compile-time literals — there is no config knob for them.

**`resetOffset()`** (`TimeManager.java:142-148`): recomputes `dayOffset` from the configured `time.start-*` values via `computeInitialOffset()`, refreshes `lastPhase`/`lastSeason` from the new snapshot, and saves. This is what `/time reset` calls. **It realigns the calendar to the `time.start-*` position *relative to the current world day*** — it does not zero the offset to 0.

**`save()`** (`TimeManager.java:150-159`): writes a fresh `YamlConfiguration` with a single key `day-offset` to `plugins/Valmora/time.yml`; IO errors are logged as warnings but never thrown.

**`computeInitialOffset()`** (`TimeManager.java:161-176`): computes the target day count from config
```java
long targetDays = (long) (startYear - 1) * 360
        + startSeason.ordinal() * 90L
        + startPhase.ordinal() * 30L
        + (startDay - 1);
```
then returns `targetDays - currentWorldDays` (`TimeManager.java:168-175`). This makes `totalDays == targetDays` at the moment of first launch, and the calendar then advances with the world's full time.

**Helpers:** `parseSeason`/`parsePhase` (`TimeManager.java:178-184`) parse config strings via `valueOf(...toUpperCase())` with `SPRING`/`EARLY` fallbacks on any exception; `capitalize` (`TimeManager.java:186-189`) uppercases the first char and lowercases the rest.

### 3.3 `TimeCommand` — `/time [info|reset]`

`TimeCommand.java:9` — a `CommandExecutor` constructed with a `TimeManager` (`TimeCommand.java:11-15`). Registered in `Valmora.onEnable()` (`Valmora.java:241`), never inside the module — per `AGENTS.md` §6.3.

**`onCommand(...)`** (`TimeCommand.java:18-42`):
1. **`info` (or no args)** → `sendInfo(sender)` (`TimeCommand.java:19-22`). This path has **no permission check** — any player can view the time.
2. **`reset`** → requires `valmora.admin`; without it the player gets `"<red>You don't have permission to use this command."` (`TimeCommand.java:24-27`). On success: `timeManager.resetOffset()`, then confirms with the new position (`TimeCommand.java:29-38`).
3. Anything else → `"<red>Usage: /time [info|reset]"` (`TimeCommand.java:40`).

**`sendInfo(...)`** (`TimeCommand.java:44-54`): builds a MiniMessage block:
```
✦ Valmora Time
Season: Early Spring
Day: 1 of 30
Year: 1
Time: ☀ 06:00
```
The time line combines `timeOfDayMiniColor()` (a MiniMessage color tag) + `timeOfDayEmote()` + `formattedTime()` (`TimeCommand.java:51`).

The command declaration in `plugin.yml:29-31` is:
```yaml
time:
  usage: /time [info|reset]
  description: Display or reset the Valmora RPG calendar clock.
```

### 3.4 `TimeSnapshot` — the immutable value type

`TimeSnapshot.java:3-7` — a Java `record` with nine components:
```java
record TimeSnapshot(int hour, int minute, int dayInPhase, Phase phase,
                    Season season, int year, long totalDays,
                    String phaseName, String seasonName)
```

- **`EPOCH`** (`TimeSnapshot.java:8-9`): `new TimeSnapshot(6, 0, 1, Phase.EARLY, Season.SPRING, 1, 0, "Early", "Spring")`. Returned by `getSnapshot()` when the world is missing (`TimeManager.java:122`).
- **`timeOfDayEmote()`** (`TimeSnapshot.java:11-15`): hour 6–17 → `"☀"`; hour < 22 (i.e. 18–21) → `"☾"`; else (22–23) → `"✦"`.
- **`timeOfDayMiniColor()`** (`TimeSnapshot.java:17-21`): hour 6–17 → `"<yellow>"`; hour < 22 → `"<gray>"`; else → `"<dark_gray>"`. Returns MiniMessage tags (used directly inside templates), not colors — per `AGENTS.md` §7.5.
- **`formattedTime()`** (`TimeSnapshot.java:23-25`): `String.format("%02d:%02d", hour, minute)` — e.g. `"06:05"`, `"23:59"`.
- **`isDay()`** (`TimeSnapshot.java:27-29`): `hour >= 6 && hour < 18`.

### 3.5 `Phase` / `Season` enums

- `Phase.java:3-5` — `EARLY, MID, LATE` (declaration order = calendar order; the ordinal is used for arithmetic in `TimeManager.java:130` and `:170`).
- `Season.java:3-5` — `SPRING, SUMMER, AUTUMN, WINTER` (declaration order = calendar order; ordinal used at `TimeManager.java:131` and `:169`).

### 3.6 The event classes (`event/`)

All three are plain synchronous `Event` subclasses with the standard Bukkit `HandlerList` pattern (static `HANDLERS`, static `getHandlerList()`, instance `getHandlers()`). None is `@Async`.

| Event | Class line | Fired by | Carrier / accessors |
|---|---|---|---|
| `ValmoraTimeTickEvent` | `ValmoraTimeTickEvent.java:7` | `TimeManager.java:78`, every second | `getSnapshot()` (`:17-19`) |
| `ValmoraDayChangeEvent` | `ValmoraDayChangeEvent.java:7` | `TimeManager.java:90`, once per world-day rollover | `getSnapshot()` (`:17-19`), `getNewDay()` (`:21-23`) → `snapshot.dayInPhase()` |
| `ValmoraSeasonChangeEvent` | `ValmoraSeasonChangeEvent.java:9` | `TimeManager.java:98-99`, on phase/season boundary | `getSnapshot()` (`:23-25`), `getNewSeason()` (`:27-29`), `getNewPhase()` (`:31-33`), `isNewSeason()` (`:35-37`), `isNewYear()` (`:39-41`) |

The `ValmoraSeasonChangeEvent` constructor takes three arguments — `(snapshot, isNewSeason, isNewYear)` (`ValmoraSeasonChangeEvent.java:17-21`) — because both flags are derived *before* `lastPhase`/`lastSeason` are updated (`TimeManager.java:93-99`). This ordering matters: by the time a listener sees the event, `TimeManager.lastPhase`/`lastSeason` already point at the new day, so consumers must rely on the flags/snapshot rather than re-reading the manager's fields.

---

## 4. Configuration (YAML)

### 4.1 `config.yml` → `time:` block

Defined in `src/main/resources/config.yml:65-83`. All keys are read in `TimeManager.onEnable()` (`TimeManager.java:40-42`) and `computeInitialOffset()` (`TimeManager.java:161-166`).

| Key | Type | Default | Read at | Description |
|---|---|---|---|---|
| `time.world` | string | `world` | `TimeManager.java:40` | The world whose day clock drives the RPG calendar. Used for `world.getTime()`/`getFullTime()`; also validated for existence on every tick (`TimeManager.java:74`). |
| `time.start-year` | int | `1` | `TimeManager.java:163` | Starting year, used only when computing the initial `day-offset` (first launch, or `/time reset`). |
| `time.start-season` | string | `SPRING` | `TimeManager.java:164` | Starting season — one of `SPRING`/`SUMMER`/`AUTUMN`/`WINTER` (case-insensitive; invalid values silently fall back to `SPRING` via `parseSeason`, `TimeManager.java:178-180`). |
| `time.start-phase` | string | `EARLY` | `TimeManager.java:165` | Starting phase — one of `EARLY`/`MID`/`LATE` (case-insensitive; fallback `EARLY`, `TimeManager.java:182-184`). |
| `time.start-day` | int | `1` | `TimeManager.java:166` | Starting day-of-phase, clamped to `max(1, …)` (`TimeManager.java:166`). Not clamped to ≤ 30. |
| `time.season-names` | list of string | `[Spring, Summer, Autumn, Winter]` | `TimeManager.java:41` | Display names used in scoreboard, `/time`, season announcements and `$time.season$`/`$time.phase$`. Indexed by `season.ordinal()` with a `capitalize(season.name())` fallback if the list is shorter (`TimeManager.java:136-137`). |
| `time.phase-names` | list of string | `[Early, Mid, Late]` | `TimeManager.java:42` | Same role for phases (`TimeManager.java:134-135`). |
| `time.scoreboard-enabled` | boolean | `true` | **never** | **Dead option.** Present in the shipped `config.yml:83` but **not read anywhere** in the codebase (a repo-wide grep for `scoreboard-enabled` matches only `config.yml`). The scoreboard's time lines are instead controlled by `ui.yml` `scoreboard.lines` and are always rendered. See §8. |

The `time.start-*` keys only matter on **first launch** (when `time.yml` does not exist yet, `TimeManager.java:48-51`) and when `/time reset` is run. After that, the position lives in `time.yml` (§5) — editing `time.start-*` later has no effect until a reset.

### 4.2 No module-local YAML folder

Unlike most Valmora modules, the Time module has **no dedicated content folder** (`plugins/Valmora/time/` does not exist). The only file it reads/writes is `plugins/Valmora/time.yml` (state, not content). There is nothing to hot-reload for *content* — `/valmora reload` only re-reads `config.yml` and reloads `time.yml` state.

### 4.3 `plugin.yml` command declaration

`plugin.yml:29-31`:
```yaml
time:
  usage: /time [info|reset]
  description: Display or reset the Valmora RPG calendar clock.
```
No `permission:` is declared here — the `reset` subcommand enforces `valmora.admin` in code (`TimeCommand.java:24`). (`plugin.yml` defines no `permissions:` section at all; `valmora.admin` is a code-checked node.)

---

## 5. Data Model / Persistence

- **No database involvement.** The module never touches `DataStore`/DAO. The only persistent state is the `day-offset` value.

- **File:** `plugins/Valmora/time.yml` — a bare two-line file:
  ```yaml
  day-offset: 123
  ```
  Written by `TimeManager.save()` (`TimeManager.java:150-159`) on **first launch** (`TimeManager.java:50`), on every `onDisable()` (`TimeManager.java:70`), and on `resetOffset()` (`TimeManager.java:147`). Read on every `onEnable()` (`TimeManager.java:44-47`).

- **Meaning of `day-offset`:** `totalDays = world.getFullTime()/24000 + dayOffset` (`TimeManager.java:125`). It is the (potentially negative) number of days that must be added to the world's raw day count to reach the configured RPG start date. It is computed once as `targetDays - currentWorldDays` (`TimeManager.java:161-176`) and then stays constant forever — the calendar advances because `world.getFullTime()` advances.

- **State that is *not* persisted:** `lastWorldDay`, `lastPhase`, `lastSeason` (`TimeManager.java:28-30`) are pure runtime fields. Consequences:
  - On a reload/restart, `lastWorldDay` is reseeded to the *current* day (`TimeManager.java:56-57`), so **day-change events are never replayed** for days that passed while the server was offline (there is no catch-up).
  - The season announcement will only fire for a season change that actually happens *after* load.

- **`time.yml` robustness:** reading uses `tc.getLong("day-offset", computeInitialOffset())` (`TimeManager.java:47`), so a missing/corrupt key falls back to the config-derived offset. A corrupt/non-YAML file would throw at `YamlConfiguration.loadConfiguration` — there is no try/catch around the load (`TimeManager.java:45-47`), so a malformed `time.yml` would abort `onEnable()`.

- **Threading:** the tick task and all persistence run on the main thread (`TimeManager.java:59`); `save()` is only called from main-thread contexts. No async concerns.

---

## 6. API Exposed

**Public API (the main integration point):** `TimeManager` is reachable through the `ValmoraAPI` interface:

```java
TimeManager tm = ValmoraAPI.getInstance().getTimeManager();   // ValmoraAPI.java:45, impl Valmora.java:337-339
TimeSnapshot snap = tm.getSnapshot();
```

The public surface of `TimeManager` (`TimeManager.java`):

| Member | Signature | Line |
|---|---|---|
| `getSnapshot()` | `TimeSnapshot` | `TimeManager.java:120` |
| `resetOffset()` | `void` | `TimeManager.java:142` |
| `save()` | `void` | `TimeManager.java:150` |

(`onEnable`/`onDisable` are public but intended for the module lifecycle only.)

**Public value types** available to any consumer:
- `TimeSnapshot` — full record: `hour()`, `minute()`, `dayInPhase()`, `phase()`, `season()`, `year()`, `totalDays()`, `phaseName()`, `seasonName()`, plus `isDay()`, `formattedTime()`, `timeOfDayEmote()`, `timeOfDayMiniColor()`, and the `EPOCH` constant (`TimeSnapshot.java:3-30`).
- `Phase` (`Phase.java:3-5`) and `Season` (`Season.java:3-5`) enums — used by the calendar module's `trigger` parser (`CalendarEventModule.java:93-105`).

**Public events** (any plugin/module can `@EventHandler` them):
- `ValmoraTimeTickEvent` (`ValmoraTimeTickEvent.java:7`)
- `ValmoraDayChangeEvent` (`ValmoraDayChangeEvent.java:7`)
- `ValmoraSeasonChangeEvent` (`ValmoraSeasonChangeEvent.java:9`)

**Command:** `/time [info|reset]` (`TimeCommand.java:18-42`, declared `plugin.yml:29-31`).

---

## 7. Dependencies & Consumers

### Dependencies (compile-time / enable-time)

**None at enable time.** `TimeModule` is registered second (`Valmora.java:189`) and touches no other module during `onEnable()`. It depends only on the Bukkit/Paper scheduler and the world named in `time.world`.

**Runtime (lazy) dependencies:**

| Dependency | Access point | Used for |
|---|---|---|
| `ui` | `ValmoraAPI.getInstance().getUIManager()` (`TimeManager.java:114`) | `ActionBarUI.showTemporary(p, msg, 120)` — season announcement action bar (`TimeManager.java:116`) |

This is safe despite `ui` loading *after* `time` (`Valmora.java:195`) because the lookup happens at event-fire time, not at enable time — the same lazy-access pattern the docs recommend in `MODULE_DEVELOPMENT.md` §6.4.

### Consumers

**1. Script system (`$time.*$` variables) — `script` module.**
`TimeVariableProvider` (`module/script/variable/providers/TimeVariableProvider.java:9`) exposes namespace `"time"` and is registered by `ScriptModule.onEnable()` at `ScriptModule.java:58` (`registerProvider(new TimeVariableProvider())`). Every `resolve(...)` call delegates to `ValmoraAPI.getInstance().getTimeManager().getSnapshot()` (`TimeVariableProvider.java:19-22`) and switches on the first path token (`TimeVariableProvider.java:23-38`):

| Variable | Returns | Line |
|---|---|---|
| `$time.hour$` | `snap.hour()` (int 0–23) | `TimeVariableProvider.java:24` |
| `$time.minute$` | `snap.minute()` (int 0–59) | `TimeVariableProvider.java:25` |
| `$time.day$` | `snap.dayInPhase()` (1–30) | `TimeVariableProvider.java:26` |
| `$time.phase$` | `snap.phaseName()` (e.g. `"Early"`) | `TimeVariableProvider.java:27` |
| `$time.season$` | `snap.seasonName()` (e.g. `"Summer"`) | `TimeVariableProvider.java:28` |
| `$time.year$` | `snap.year()` | `TimeVariableProvider.java:29` |
| `$time.total_days$` | `snap.totalDays()` (long) | `TimeVariableProvider.java:30` |
| `$time.total_minutes$` | `totalDays*24*60 + hour*60 + minute` (long) | `TimeVariableProvider.java:31` |
| `$time.is_day$` | `snap.isDay()` (boolean) | `TimeVariableProvider.java:32` |
| `$time.time_of_day$` | `emote + " " + ("Day"\|"Night")` | `TimeVariableProvider.java:33` |
| `$time.formatted_time$` | `snap.formattedTime()` (e.g. `"14:30"`) | `TimeVariableProvider.java:34` |
| `$time.emote$` | `snap.timeOfDayEmote()` (`☀`/`☾`/`✦`) | `TimeVariableProvider.java:35` |
| `$time.color$` | `snap.timeOfDayMiniColor()` (`<yellow>`/`<gray>`/`<dark_gray>`) | `TimeVariableProvider.java:36` |

Unknown or empty paths return `null` (`TimeVariableProvider.java:18`, `:37`). The documented variable set in `docs/VALMORA_DOCUMENTATION.md` (`VALMORA_DOCUMENTATION.md:1569-1577`) lists a subset (`season`, `hour`, `is_day`, `day`, `year`) and is **incomplete** — the provider above is the authoritative list. Script expressions can therefore branch on time, e.g. `quests/forgotten_mine/quests.yml:15` — `is_day: "$time.is_day$ == true"`.

**2. Sidebar scoreboard — `ui` module.**
- **Configured path:** `ui.yml:17-18` renders the time lines on the sidebar:
  ```yaml
  - "<aqua>⏰ <white>$time.formatted_time$  $time.color$$time.emote$ <gold>$time.phase$ $time.season$"
  - "<gray>Day <white>$time.day$  <dark_gray>│  <gray>Year <white>$time.year$"
  ```
  These are resolved by `ScoreboardUI.tick()` through the script `VariableResolver` (`ScoreboardUI.java:143-162`).
- **Fallback path:** `ScoreboardUI.legacyLines()` (`ScoreboardUI.java:173-183`) — used before the UI config loads — reads the `TimeManager` snapshot directly (`ValmoraAPI.getInstance().getTimeManager()`, `ScoreboardUI.java:174`) and formats the same two lines with `snap.formattedTime()`, `timeOfDayMiniColor()`, `timeOfDayEmote()`, `phaseName()`, `seasonName()`, `dayInPhase()`, `year()`.
- This is the "scoreboard reads from it" referenced in the registration comment (`Valmora.java:189`).

**3. Calendar events — `calendar` module.**
- `CalendarEventModule.onEnable()` seeds its active-event set from a live snapshot: `plugin.getTimeManager().getSnapshot()` (`CalendarEventModule.java:40-47`).
- `CalendarEventListener.onDayChange()` subscribes to `ValmoraDayChangeEvent` (`CalendarEventListener.java:22`) and drives `on-start`/`on-end`/`recurring-daily` transitions from the event's snapshot (`CalendarEventListener.java:23`).
- The calendar's trigger windows (`season`, `phase`, `day-start`, `day-end`) are evaluated against `snapshot.season()`, `snapshot.phase()`, `snapshot.dayInPhase()` (`CalendarEventDefinition.isActive`). This is the direct consumer the user-facing `docs/modules/user/calendar.md:221-234` already cross-references.
- See `docs/modules/design/calendar.md` §7 for the full picture.

**4. Quests — `quest` module (via script variables).**
`docs/QUEST_SYSTEM.md:1256-1262` documents `$time.is_day$` / `$time.hour$` / `$time.season$` as base-system variables; the shipped quest uses them at `quests/forgotten_mine/quests.yml:15`.

**5. Tests.**
- `TimeSnapshotTest.java` — 15 unit tests (`@Tag("unit")`) covering `isDay()` boundary hours (6/17/18/5/0, `:16-38`), the three emotes (`:41-64`), `formattedTime()` padding (`:67-72`), `EPOCH` values (`:75-84`), and the three MiniMessage color tags (`:87-99`).
- `TimeVariableProviderTest.java` — 13 tests (`@Tag("scripting")`) following the `AGENTS.md` §9 Mockito pattern: mocks `ValmoraAPI`, stubs `TimeManager.getSnapshot()`, calls `ValmoraAPI.setProvider(api)` in `@BeforeEach` (`:26-31`); verifies every variable incl. `total_minutes` arithmetic (`:92-97`), `null` manager handling (`:111-116`), and the `"time"` namespace (`:119-121`).

---

## 8. Unfinished Things / TODOs

- **`time.scoreboard-enabled` is dead config.** `config.yml:83` ships the option but no Java code reads it. The scoreboard always renders the time lines; the flag is either an unimplemented toggle or stale.
- **`/time` cannot set time.** Only `info` and `reset` exist (`TimeCommand.java:19-40`). There is no way for an admin to jump to a specific day/season/year without restarting with a chosen `time.start-*` (or editing `time.yml` and reloading).
- **Read-only observer.** `TimeManager` never calls `World.setTime(...)`; there is no day-length control, no time acceleration/slowdown, and no per-player time. If other content needs a custom day/night cycle, this module does not provide it.
- **`ValmoraSeasonChangeEvent` fires on phase changes too.** The event name implies season-only (`ValmoraSeasonChangeEvent.java:9`), but `TimeManager.tick()` fires it whenever *either* phase or season changes (`TimeManager.java:92`). Consumers must check `isNewSeason()`.
- **No offline catch-up.** `lastWorldDay`/`lastPhase`/`lastSeason` are runtime-only and reseeded on load (`TimeManager.java:56-57`); day-change events never replay for offline time. (The calendar module independently seeds its active set — `CalendarEventModule.java:39-48` — but does not replay `on-start` either.)
- **No hour/minute granularity events.** Only the per-second `ValmoraTimeTickEvent` and per-day `ValmoraDayChangeEvent` exist; there is no "hour changed" hook for content that wants per-hour triggers.
- **Silent EPOCH on missing world.** If `time.world` isn't loaded, `getSnapshot()` returns `EPOCH` (`TimeManager.java:122`) — the calendar silently reads as "06:00, Spring Day 1, Year 1" until the world exists. The tick still fires events with the EPOCH snapshot every second (`TimeManager.java:77-78`).
- **Hard-coded announcement.** The season message (`TimeManager.java:112-113`) is a compile-time MiniMessage literal, not configurable or localizable; it also only uses the action-bar channel.
- **Time constants are compile-time.** 30/90/360-day lengths and the 06:00 day boundary are literals (`TimeManager.java:127-132`); no config knobs.
- **`time.yml` load is unguarded.** A malformed `time.yml` throws during `YamlConfiguration.loadConfiguration` (`TimeManager.java:45-47`) with no fallback try/catch (the `getLong` default only covers a missing key).
- **`TimeManager` itself is untested.** Only `TimeSnapshot` and `TimeVariableProvider` have unit tests; the snapshot-derivation math in `getSnapshot()` and the rollover logic in `tick()` have no direct coverage.
- **Doc drift:** `docs/VALMORA_DOCUMENTATION.md:1569-1577` documents only a 5-variable subset of `$time.*$`; the full set is in `TimeVariableProvider.java:23-38`. The `MODULE_DEVELOPMENT.md` §9 load-order list (`MODULE_DEVELOPMENT.md:493-517`) predates several late modules — the comment block at `Valmora.java:186-222` is authoritative.
- **Project roadmap** (`docs/todo.md:56`, `:66`): "mob spawning naturally on time interval" and "events like a schematic that appears based on time of year" are listed as future work — neither exists yet; both would build on this module's snapshot/events.

---

## 9. Possible Improvements / Changes

- **Add time manipulation commands** — e.g. `/time set <year> <season> <phase> <day>`, `/time add <days>`, or `/time offset <n>` — implemented by mutating `dayOffset` (the one lever that exists) and saving. Requires a `valmora.admin` guard and refreshing `lastPhase`/`lastSeason` like `resetOffset()` does (`TimeManager.java:142-148`).
- **Honor or remove `scoreboard-enabled`** — either wire `config.yml:83` into `ScoreboardUI` (it currently can't see it; the scoreboard reads `ui.yml`, not `config.yml`) or delete the dead key.
- **Make calendar constants configurable** — day length per RPG day (currently fixed 24000 ticks), phase/season lengths, the hour-offset (+6) and the day/night boundary (06:00–18:00). Would change the `TimeSnapshot`/`getSnapshot()` contract, so default-preserving config with fallbacks is important.
- **Persist runtime state** — write `lastWorldDay` (and optionally `lastPhase`/`lastSeason`) into `time.yml` so a reload can replay missed `ValmoraDayChangeEvent`/`ValmoraSeasonChangeEvent` transitions (offline catch-up). Coordinate with the calendar module so `on-start`/`on-end` don't double-fire.
- **Add hour/minute events** — `ValmoraHourChangeEvent` (and keep tick as the coarse heartbeat) so content can schedule per-hour effects without polling.
- **Configurable announcement** — move the season message template (`TimeManager.java:112-113`) into `config.yml` and add a chat/announcement channel option; also allow disabling it.
- **Guard `time.yml` loading** — wrap `YamlConfiguration.loadConfiguration` (`TimeManager.java:45-47`) in try/catch so a corrupt file degrades to `computeInitialOffset()` instead of failing `onEnable()`.
- **Per-world calendar support** — today a single `time.world` drives everything (`TimeManager.java:40`); a `Map<worldName, offset>` would let multiple worlds keep independent calendars.
- **Expose richer API** — convenience methods on `TimeManager` such as `isDay()`, `getHour()`, `isSeason(Season)` delegating to `getSnapshot()`, plus maybe a `TimeSnapshot.of(World)` static for computing a snapshot for an arbitrary world without touching module state.
- **Unit tests for the manager** — mock a `World` (`getTime()`, `getFullTime()`) and assert the `getSnapshot()` derivation table and `tick()` rollover/event-firing logic, following the `ExpressionTest`/`TimeVariableProviderTest` Mockito pattern (`AGENTS.md` §9).
