# Calendar Event Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Config folder:** `plugins/Valmora/calendar/` | **Module ID:** `calendar`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

The Calendar Event Module runs **seasonal RPG events** that trigger automatically based on Valmora's in-game calendar. Each event has a *window* — a specific season, phase, and day range — and runs scripted actions when the window **starts**, every **day** it is **active**, and when it **ends**.

Everything an event does is defined in YAML files inside `plugins/Valmora/calendar/`. No commands and no permissions are required to run events; they fire automatically on the server's day cycle. Admins only need to edit files and reload.

The calendar itself is driven by the **Time** module (`config.yml` → `time:` block). It runs on a 360-day year:

- **Seasons:** `SPRING` → `SUMMER` → `AUTUMN` → `WINTER` (90 days each)
- **Phases:** `EARLY` → `MID` → `LATE` (30 days each, inside every season)
- **Days:** `1–30` within the current phase (reset on each phase change)

So "Autumn / Early / days 1–30" means the first 30 days of autumn.

By default the plugin ships with **three** seasonal events (see `seasonal.yml`). They are just examples — you can rename, edit, or delete them freely.

---

## 2. Player Guide

When a calendar event is active, you will see and feel it on the server. Nothing to install, no commands to run — events apply to **all online players** automatically.

### 2.1 The default events

| Event | When it runs | What you experience |
|---|---|---|
| **Harvest Festival** | Autumn · Early · days 1–30 | Title + subtitle + chat announcement at the start; **+25 Farming Fortune** while active; daily action-bar reminder; chat farewell and removal of the bonus when it ends. |
| **Winter Blessing** | Winter · Mid · days 1–30 | Chat announcement at the start; **+10 Magic Find** while active; daily action-bar reminder; chat farewell when it ends. |
| **Spring Renewal** | Spring · Early · days 1–30 | Chat announcement at the start; **+5 Health Regen** while active; daily action-bar reminder; chat farewell when it ends. |

### 2.2 What happens when an event starts

The moment the calendar day changes into the event's window, everyone online sees:

- A **title** and **subtitle** (e.g. `Harvest Festival!` / *The crops are ready to harvest!*), and a chat announcement, then
- An automatic **stat bonus** applied to every online player (Farming Fortune, Magic Find, or Health Regen depending on the event).

The stat bonus stays active for the whole event window. Because bonuses are granted by a server-wide stat edit, they are live immediately and disappear when the event ends.

### 2.3 While the event is active

Once per in-game day, players see a short **action-bar** reminder (e.g. *✦ Harvest Festival active! Bonus Farming Fortune!*). The stat bonus remains in effect.

### 2.4 When an event ends

On the day after the window (day 31 of the phase), a chat message announces the end (e.g. *The Harvest Festival has ended.*) and the stat bonus is removed automatically.

### 2.5 Notes for players

- Events are **global** — they affect every online player, not just players near a location.
- If you log in **during** an active event, you still get the active bonuses (via the recurring daily effect and/or the server-side stat edit). If the event started while you were offline, you will not see its *start* announcement retroactively, but you keep the active bonus.
- Messages are formatted with MiniMessage colors; appearance depends on the server's configuration.

---

## 3. Admin Guide

### 3.1 How events are defined

Each event is one top-level key in a `.yml` file inside `plugins/Valmora/calendar/` (any number of files, any number of events per file). Files are read **only on load** — after editing, run:

```
/valmora reload
```
(requires the `valmora.admin` permission)

### 3.2 Minimal example

```yaml
my_event:
  trigger:
    season: SUMMER
    phase: MID
    day-start: 5
    day-end: 10
  on-start:
    - "notifyall io:title A Summer Celebration!"
    - "foreach @all stat_modify add magic_find 15"
  on-end:
    - "notifyall io:chat The Summer Celebration has ended."
    - "foreach @all stat_modify add magic_find -15"
  recurring-daily:
    - "notifyall io:actionbar Summer Celebration active!"
```

This event runs on **Summer / Mid / days 5–10**. When it starts it shows a title and gives every player +15 Magic Find; every day while active it shows an action-bar reminder; when it ends it announces and removes the +15.

### 3.3 Anatomy of an event

| Block | Required? | Purpose |
|---|---|---|
| `trigger:` | No | The activation window. Omit it to make the event run **every day** (day 1–30 of every phase). |
| `trigger.season:` | No | Only run in this season. Omit = any season. |
| `trigger.phase:` | No | Only run in this phase. Omit = any phase. |
| `trigger.day-start:` / `day-end:` | No | Inclusive day range inside the phase (default `1` / `30`). |
| `on-start:` | No | Scripts run once, on the first day the event activates. |
| `on-end:` | No | Scripts run once, on the day after the event's last active day. |
| `recurring-daily:` | No | Scripts run on **every** day-change while the event is active. |

### 3.4 Rules and limitations you must respect

1. **Season values:** `SPRING`, `SUMMER`, `AUTUMN`, `WINTER` (case-insensitive — the module uppercases them). Anything else is rejected at load and the event is skipped.
2. **Phase values:** `EARLY`, `MID`, `LATE` (case-insensitive). Anything else is rejected.
3. **Days are per-phase, 1–30.** The window cannot span multiple phases or seasons with a single event. For a whole-season event, define one event per phase (or omit `phase:` and accept it running each phase).
4. **`day-start` and `day-end` are not validated.** Values below 1 or above 30, or `day-start` > `day-end`, produce an event that **never activates** — there is no warning.
5. **Event scripts run with no player caster and no location.** Only **server-wide** script actions work:
   - `notifyall <message> [io:<type>]` — broadcast (see IO types below)
   - `foreach @all <inner actions>` — run actions once per online player
   - `stat_modify add|set|reset <stat_id> <value>` — inside `foreach @all`, edits that player's base stat
   Actions that need a caster/target/location (e.g. `give`, `teleport` without a target, `spawnmob` at caster) will do nothing.
6. **A broken event does not break the server.** Invalid entries are logged as warnings on load and skipped; other events still load.

### 3.5 Script action reference (what you can put in `on-start` / `on-end` / `recurring-daily`)

Each list item is one script line, executed in order. Message tokens are joined with spaces; MiniMessage tags such as `<gold>` are allowed.

**`notifyall`** — broadcast to all online players:

```
notifyall <message> [category:<name>] [io:<type>] [key:value ...]
```

| IO type (`io:`) | Effect |
|---|---|
| `chat` | Chat message (default) |
| `actionbar` | Action-bar text |
| `title` | Big title overlay |
| `subtitle` | Subtitle line of a title overlay |
| `bossbar` | Boss bar at the top of the screen |
| `sound` | Plays a sound |
| `advancement` | Toast-style notification |

**`foreach @all`** — run inner actions for each online player:

```
foreach @all stat_modify add farming_fortune 25
```

**`stat_modify`** — edit a player's base stat (usable inside `foreach @all`):

```
stat_modify add <stat_id> <value>
stat_modify set <stat_id> <value>
stat_modify reset <stat_id>
```

Valid `<stat_id>` values come from your `stats/*.yml` definitions and the mappings in `config.yml` (e.g. `farming_fortune`, `magic_find`, `health_regen`, `damage`, `defense`, …). **Remember:** when a `stat_modify add` bonus is applied, your `on-end` must apply the negative of the same value (as the shipped events do) — the module does not track or revert bonuses for you.

Other script actions registered by the Script engine (`condition`, `give`, `variable`, `tag`, `teleport`, `spawnmob`, `runscript`, `notify`, `notifyall`) are technically available, but only server-wide/`foreach @all`-safe ones are useful in calendar events.

### 3.6 Permissions

| Permission | Effect |
|---|---|
| `valmora.admin` | Required for `/valmora reload`, which picks up calendar file changes. |

There is **no** dedicated calendar command and **no** per-event permission. Players never interact with the module directly — events are purely automatic and affect everyone.

### 3.7 Reloading and editing workflow

1. Edit or add files under `plugins/Valmora/calendar/`.
2. Run `/valmora reload`.
3. Check the console for the load report:
   - `Successfully loaded N Calendar Event.`
   - `Failed to load some Calendar Event. ...` followed by one warning per broken event.
4. An event already active across a reload is **not** re-triggered (`on-start` will not replay); its daily reminder and eventual `on-end` continue normally.

---

## 4. Configuration Reference

Folder: `plugins/Valmora/calendar/` — auto-created from the plugin jar on first run (existing files are never overwritten). All keys and defaults below are taken directly from the parser (`CalendarEventModule.java:84-127`).

### 4.1 Per-event keys

| Key | Type | Default | Explanation |
|---|---|---|---|
| `<event-id>` | — | *(required)* | The YAML top-level key. Identifies the event and is used in load-error messages. |
| `trigger` | section | *(optional)* | Activation window. If omitted, the event activates every day from `day-start` to `day-end` of every phase. |
| `trigger.season` | string | *(none = any season)* | Restrict to one season. Accepts `SPRING`, `SUMMER`, `AUTUMN`, `WINTER` (case-insensitive). Any other value fails loading the event. |
| `trigger.phase` | string | *(none = any phase)* | Restrict to one phase. Accepts `EARLY`, `MID`, `LATE` (case-insensitive). Any other value fails loading the event. |
| `trigger.day-start` | integer | `1` | First active day of the phase (inclusive). Not range-checked. |
| `trigger.day-end` | integer | `30` | Last active day of the phase (inclusive). Not range-checked. |
| `on-start` | list | *(empty)* | Script lines executed once when the event becomes active. |
| `on-end` | list | *(empty)* | Script lines executed once when the event ceases to be active. |
| `recurring-daily` | list | *(empty)* | Script lines executed on every day-change while the event is active. |

### 4.2 Shipped example — `calendar/seasonal.yml`

**`harvest_festival`** — Autumn / Early / days 1–30
- Start: title `Harvest Festival!`, subtitle, chat announcement, `foreach @all stat_modify add farming_fortune 25`
- Daily: action bar `✦ Harvest Festival active! Bonus Farming Fortune!`
- End: chat announcement, `foreach @all stat_modify add farming_fortune -25`

**`winter_blessing`** — Winter / Mid / days 1–30
- Start: chat announcement, `foreach @all stat_modify add magic_find 10`
- Daily: action bar `❄ Winter Blessing active! Bonus Magic Find!`
- End: chat announcement, `foreach @all stat_modify add magic_find -10`

**`spring_renewal`** — Spring / Early / days 1–30
- Start: chat announcement, `foreach @all stat_modify add health_regen 5`
- Daily: action bar `✿ Spring Renewal active!`
- End: chat announcement, `foreach @all stat_modify add health_regen -5`

### 4.3 Related settings (Time module, `config.yml`)

The calendar windows depend on the RPG clock. Under `config.yml` → `time:`:

| Key | Default | Explanation |
|---|---|---|
| `time.world` | `world` | The world whose day cycle drives the RPG calendar. |
| `time.start-year` | `1` | Starting year (applied only on first launch). |
| `time.start-season` | `SPRING` | Starting season (first launch only). |
| `time.start-phase` | `EARLY` | Starting phase (first launch only). |
| `time.start-day` | `1` | Starting day of phase (first launch only). |
| `time.season-names` | `[Spring, Summer, Autumn, Winter]` | Display names used in messages/scoreboard. |
| `time.phase-names` | `[Early, Mid, Late]` | Display names used in messages/scoreboard. |

After the first launch the position is persisted in `plugins/Valmora/time.yml` (`day-offset`), which the plugin updates automatically — edit `time.start-*` later only to reset the calendar on a fresh server.
