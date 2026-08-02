# Time Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Config:** `config.yml` → `time:` block | **State file:** `plugins/Valmora/time.yml` | **Module ID:** `time`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

The Time module gives the server a **role-playing calendar** that runs on top of the normal Minecraft day/night cycle. The server's time of day still ticks normally — this module just *reads* it and derives a richer date from it:

- **Time of day:** hour and minute (e.g. `14:30`), plus a day/night indicator.
- **Day:** 1–30, always resetting within the current **phase**.
- **Phase:** `Early` → `Mid` → `Late` (each 30 days).
- **Season:** `Spring` → `Summer` → `Autumn` → `Winter` (each 90 days / 3 phases).
- **Year:** starts at 1 and counts up; a full year is 360 days.

So the calendar runs: **Early Spring** (days 1–30) → **Mid Spring** → **Late Spring** → **Early Summer** → … → **Late Winter**, then the next year begins.

The calendar's starting position is set once (`config.yml` → `time.start-*`) and is then stored in `plugins/Valmora/time.yml`. It keeps advancing with the world and **survives restarts** — day 74 of Year 3 stays day 74 when the server comes back.

This module is purely **informational**. It drives displays (`/time`, the scoreboard), script variables (`$time.*$`), and time-based events (the calendar module's seasonal events). It does not change how fast days pass or control weather.

---

## 2. Player Guide

Players interact with the time system passively — there is nothing to install and no commands to learn (aside from the optional `/time` info command).

### 2.1 What you see

**Sidebar scoreboard** (if your server has one) shows two time lines, e.g.:

```
⏰ 14:30  ☀ Early Summer
Day 12  │  Year 3
```

- The clock (`14:30`) and the emote (`☀` daytime, `☾` evening, `✦` late night) update continuously.
- "Day 12" is the day *within the current phase* — it resets to 1 every 30 days.
- "Year 3" counts the 360-day RPG years.

**The season change announcement.** When a new season begins, every online player sees a temporary action-bar message like:

```
✦ A new season begins — Early Summer ✦
```

(Shown for about 6 seconds.)

**Seasonal events.** Many servers define calendar-based events (e.g. the shipped **Harvest Festival**, **Winter Blessing**, **Spring Renewal**) that start, run daily, and end based on this calendar. Those are handled by the Calendar module — see `docs/modules/user/calendar.md`.

### 2.2 The `/time` command

Any player can run:

```
/time
```

or

```
/time info
```

This prints the current calendar position:

```
✦ Valmora Time
Season: Early Spring
Day: 1 of 30
Year: 1
Time: ☀ 06:00
```

The `info` view needs **no permission**. Only the admin `reset` subcommand is restricted (see the Admin Guide).

### 2.3 Day/night vs the RPG clock

- **Day** in the RPG sense (`06:00`–`18:00`) is a different concept from the in-game calendar *day number*. A quest condition like "only during daytime" means real world daytime — not "Day 1 of the phase."
- The RPG day counter increments **once per full Minecraft day** in the configured world — 24000 world ticks.

---

## 3. Admin Guide

### 3.1 The `/time` command

```
/time            → show current calendar time (public)
/time info       → same as above
/time reset      → reset the calendar to config.yml's time.start-* position (valmora.admin)
```

| Subcommand | Permission | Effect |
|---|---|---|
| *(none)* / `info` | none | Displays season, phase, day-of-30, year, and clock time. |
| `reset` | `valmora.admin` | Recomputes the calendar offset so the current date becomes exactly what `time.start-year` / `time.start-season` / `time.start-phase` / `time.start-day` specify. Persists immediately to `time.yml` and prints the new position. |

**Important:** `/time reset` does **not** reset the world clock — it re-aligns the *calendar* so that *today* becomes the configured start date. It cannot jump the calendar to an arbitrary day, season, or year; the only way to get a specific future date is to set `time.start-*`, reset, and wait (or edit `plugins/Valmora/time.yml` → `day-offset` and reload).

### 3.2 Permissions

| Permission | Effect |
|---|---|
| `valmora.admin` | Required for `/time reset`. (Also required for `/valmora reload`, which picks up config changes.) |

There is no per-player or per-command permission for viewing time — `/time` and `/time info` are open to everyone.

### 3.3 Reloading

The Time module has no content files to edit — its settings come from `config.yml`. After editing the `time:` block, run:

```
/valmora reload
```

(requires `valmora.admin`). Note: changing `time.start-*` alone will **not** change the live calendar — those values only apply when `time.yml` doesn't exist yet or when `/time reset` is run. To reposition the calendar from an existing server:

1. Edit `time.start-year` / `time.start-season` / `time.start-phase` / `time.start-day` in `config.yml`.
2. Run `/valmora reload`.
3. Run `/time reset`.

### 3.4 The state file `plugins/Valmora/time.yml`

A two-line file created automatically on first run:

```yaml
day-offset: 123
```

This is the number of days added to the world's day count to reach the RPG start date. It is maintained by the plugin — you normally never touch it. Hand-editing it (e.g. to jump the calendar forward) works, but reload the plugin afterwards and be careful: values are not validated.

### 3.5 Notes and caveats

- **`time.scoreboard-enabled` is currently ignored.** The option exists in the default `config.yml` but has no effect — the sidebar's time lines are governed by `ui.yml` (`scoreboard.lines`), not by this flag.
- **`time.world` must be loaded.** The calendar reads a single world's clock. If that world isn't loaded, the time reports as `06:00`, Spring Day 1, Year 1 (the epoch) until it is.
- **Invalid season/phase values** in `time.start-season` / `time.start-phase` are silently ignored and fall back to `SPRING` / `EARLY`.
- **Days are 1–30 per phase** — a full year is exactly 360 days. The lengths (30/90/360) are fixed by the code, not configurable.

---

## 4. Configuration Reference

All settings live in `config.yml` under the `time:` block (`config.yml:68-83`):

| Key | Type | Default | Explanation |
|---|---|---|---|
| `time.world` | string | `world` | The world whose day clock drives the RPG calendar (read from `world.getTime()` / `getFullTime()`). |
| `time.start-year` | integer | `1` | Starting year. Applied on **first launch only** (when `time.yml` is created) and on `/time reset`. |
| `time.start-season` | string | `SPRING` | Starting season: `SPRING`, `SUMMER`, `AUTUMN` or `WINTER` (case-insensitive). Invalid values fall back to `SPRING`. First launch / reset only. |
| `time.start-phase` | string | `EARLY` | Starting phase: `EARLY`, `MID` or `LATE` (case-insensitive). Invalid values fall back to `EARLY`. First launch / reset only. |
| `time.start-day` | integer | `1` | Starting day of the phase (1–30). Values below 1 are clamped to 1; first launch / reset only. |
| `time.season-names` | list | `[Spring, Summer, Autumn, Winter]` | Display names used on the scoreboard, in `/time`, in season announcements and in `$time.season$` / `$time.phase$` variables. Indexed by calendar order. |
| `time.phase-names` | list | `[Early, Mid, Late]` | Display names for the three phases. Indexed by calendar order. |
| `time.scoreboard-enabled` | boolean | `true` | ⚠️ **Not implemented.** Accepted in the file but ignored by the plugin. Keep it for future compatibility or remove it. |

### 4.1 Related: `$time.*$` script variables

The Time module feeds variables into the scripting system. These are usable in scripts, quest conditions, GUIs, and the scoreboard (`ui.yml`):

| Variable | Example value | Meaning |
|---|---|---|
| `$time.hour$` | `14` | Hour of day (0–23) |
| `$time.minute$` | `30` | Minute of the hour (0–59) |
| `$time.formatted_time$` | `"14:30"` | `HH:mm` clock string |
| `$time.day$` | `12` | Day within the current phase (1–30) |
| `$time.phase$` | `"Early"` | Phase display name |
| `$time.season$` | `"Summer"` | Season display name |
| `$time.year$` | `3` | RPG year (≥ 1) |
| `$time.is_day$` | `true` / `false` | Daytime between 06:00 and 18:00 |
| `$time.total_days$` | `742` | Total elapsed RPG days |
| `$time.total_minutes$` | `1069200` | Total elapsed RPG minutes (days × 24 × 60 + h × 60 + m) |
| `$time.time_of_day$` | `"☀ Day"` | Emote + `Day`/`Night` |
| `$time.emote$` | `☀` / `☾` / `✦` | Daytime / evening / late-night emote |
| `$time.color$` | `<yellow>` | MiniMessage color matching the emote |

Example — a quest objective that only completes during the day (`quests/forgotten_mine/quests.yml:15`):

```yaml
is_day: "$time.is_day$ == true"
```

### 4.2 Related: calendar events

The Calendar module turns the calendar into automatic events. Its `season` / `phase` / `day-start` / `day-end` trigger windows are evaluated against this calendar's current date — see `docs/modules/user/calendar.md` for the full reference.
