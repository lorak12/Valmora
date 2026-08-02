# UI Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Config:** `plugins/Valmora/ui.yml` (plus one dead key in `config.yml`) | **Module ID:** `ui`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

The UI module renders Valmora's on-screen HUD text — the **sidebar scoreboard** on the right of the screen, the **action bar** just above the hotbar, and the **player-list tab** header/footer. It is a pure display layer: it stores no data and runs no gameplay logic. Everything it shows is defined in one file, `plugins/Valmora/ui.yml`, using:

- **MiniMessage formatting** for colors and styles (e.g. `<gold>`, `<bold>`, `<#RRGGBB>`), and
- **`$variable$` tokens** that get filled in live — player stats, the RPG calendar time, your current zone, your purse, etc.

The module updates every surface **10 times per second** (every 2 game ticks), so numbers like health, mana, and the clock stay current without any player action.

The UI module also supplies the standard **chat messages** for level-ups, rewards, and errors, and lets other systems push **temporary action-bar messages** — e.g. "+10 Combat XP" when you gain skill XP, "Ability on cooldown: 3s", or your zone name when you walk into a new zone.

---

## 2. Player Guide

There is nothing to install or configure as a player — the HUD is fully server-driven. Here is what you will see and where it comes from.

### 2.1 The sidebar scoreboard (right side)

A typical Valmora sidebar (from the default `ui.yml`) looks like this:

```
           VALMORA RPG
   ─────────────────────
   pay.valmora.net

   ⏰ 14:30  ☀ Early Summer
   Day 12  │  Year 3

   (dynamic content, if any)

   Profile: <your profile name>
   Zone: Wilderness
   Purse: 1.2k Coins
```

- **Title** — the gold "VALMORA RPG" at the top (configurable).
- **Time rows** — the clock, day/night emote (☀ day, ☾ evening, ✦ late night), current phase/season, day-of-phase and RPG year. These come from the Time module's calendar (`$time.*$`).
- **Dynamic section** — a reserved block that other systems may fill with live context (currently unused by shipped modules, but kept in the default layout via the `$dynamic$` placeholder).
- **Profile / Zone / Purse** — your active Valmora profile name, the zone you are standing in (or "Wilderness"), and your coin purse.

The sidebar is capped at **16 lines** — anything beyond that in the config is not rendered.

### 2.2 The action bar (above the hotbar)

By default the action bar continuously shows your combat readout:

```
❤ 87/100 ❈ 45 Defense ⛨ 50/50 Mana
```

These numbers are live Valmora values (your effective stats), updated every 2 ticks.

**Temporary messages** override the bar for a few moments, then it snaps back to the default. Examples of what you will see:

| What happens | Message | Duration |
|---|---|---|
| You gain skill XP | `+15 Combat XP` | ~1 second |
| Your skill levels up | *(chat block — see below)* | — |
| You enter a new zone | the zone's name | ~3 seconds |
| A new season begins | `✦ A new season begins — Early Summer ✦` | ~6 seconds |
| An ability fails | `No target in range!` / `Ability on cooldown: 3s` / `Not enough Mana!` | ~0.5 seconds |

**While talking to an NPC**, the action bar is taken over by the dialogue system; Valmora's own bar hides until the conversation ends.

### 2.3 The player-list tab (Pressing TAB)

If your server configured a tab header/footer, you will see them at the top and bottom of the player list — e.g. a "VALMORA" header and a footer showing `Players online: 42`.

### 2.4 Chat messages

Two canned messages come from this module:

- **Skill level up** — a highlighted block when a skill reaches a new level:

  ```
  ────────────────────────────────────
   SKILL LEVEL UP!
   Your Combat is now level 5!
  ────────────────────────────────────
  ```

- **System errors/rewards** — messages prefixed with `[Valmora]`.

### 2.5 Commands

There is **no `/ui` command**. The UI module has no player-facing or admin command of its own — you interact with it purely through what it displays.

---

## 3. Admin Guide

All UI content is configured in **`plugins/Valmora/ui.yml`**. The file is created automatically the first time the plugin runs (copied from the jar), so editing the shipped copy is safe.

### 3.1 Reloading

After editing `ui.yml`, apply changes without a restart:

```
/valmora reload
```

(requires `valmora.admin`). The scoreboard re-reads the title and lines **every tick**, so a reload takes effect on the next render pass — no extra steps needed.

### 3.2 Editing the scoreboard

`scoreboard.lines` is a list rendered **top to bottom**:

```yaml
scoreboard:
  title: "<gold><bold>VALMORA RPG"
  lines:
    - "<yellow>pay.valmora.net"      # server/store advertising line
    - ""                             # blank line (use "" )
    - "<aqua>⏰ <white>$time.formatted_time$  $time.color$$time.emote$ <gold>$time.phase$ $time.season$"
    - "<gray>Day <white>$time.day$  <dark_gray>│  <gray>Year <white>$time.year$"
    - ""
    - "$dynamic$"                    # reserved placeholder (see below)
    - "<gray>Profile: <yellow>$player.profile$"
    - "<gray>Zone: $zone.current$"
    - "<gray>Purse: <gold>$economy.purse.formatted$"
```

Rules and tips:

- **Max 16 lines** render; the rest are ignored. Keep the list short for readability.
- `""` produces a blank separator line.
- `"$dynamic$"` is a **reserved placeholder**: it splices in a dynamic block (plus a spacer) if another Valmora system injects one for the player. The shipped layout includes it, but currently no system uses it — you can safely move or remove it. If you remove it, any future dynamic content simply will not appear.
- Every line accepts **any `$variable$` token** supported by the script engine. Commonly useful ones are listed in §4.2.
- **Variables are resolved live** every tick, so a line like `Day <white>$time.day$` updates as the calendar advances.
- Line templates are parsed as MiniMessage *after* variable substitution — so a variable that itself is a color tag (like `$time.color$` → `<yellow>`) correctly colors the rest of the line.

### 3.3 Editing the action bar

```yaml
action-bar:
  default: "<red>❤ $player.hp$/$player.max_hp$ <dark_gray>| <green>❈ $player.stat.defense$ Defense <dark_gray>| <aqua>⛨ $player.mana$/$player.max_mana$ Mana"
```

- This template is shown whenever **no** temporary message is active (XP gain, cooldown, zone banner, etc.). Temporary messages always take priority and revert automatically when they expire.
- Set it to `""` to disable the custom bar — players then get the hard-coded fallback bar instead.

### 3.4 Editing the tab list

```yaml
tab:
  header: "<gold><bold>VALMORA</bold></gold>"
  footer: "<gray>Players online: <white>$server.online$"
```

- Header and footer are independent; leaving either empty disables that half.
- Note the tab header/footer are only pushed while the scripting module is active and a player is online during the update pass. If the script module is disabled, tab text is silently skipped.

### 3.5 Permissions

The UI module defines **no permissions of its own**. The only relevant node is:

| Permission | Effect |
|---|---|
| `valmora.admin` | Required for `/valmora reload`, which applies `ui.yml` changes. |

### 3.6 Caveats

- **`time.scoreboard-enabled` in `config.yml` is ignored.** The option exists (`config.yml:83`) but has no effect — the sidebar's time rows are controlled entirely by the `$time.*$` lines in `ui.yml`, not by that flag. (Known, tracked.)
- **The fallback layout is hard-coded.** If `ui.yml` is missing or its `scoreboard.lines` list is empty, players see a built-in fallback sidebar that includes a `pay.valmora.net` line, time rows, profile, zone and purse — you cannot style that fallback from config.
- **No per-player toggle.** All players see the same scoreboard/action-bar/tab content. There is no `/ui toggle` command.
- **A corrupt `ui.yml` can break the module.** The file is parsed on every enable/reload with no safety net; if you hand-edit and introduce bad YAML, run `/valmora reload` and fix the file before restarting. Defaults only cover *missing* keys, not *malformed* files.

---

## 4. Configuration Reference

All keys live in `plugins/Valmora/ui.yml` (seeded from `src/main/resources/ui.yml`). All string values support MiniMessage and `$variable$` tokens.

### 4.1 `ui.yml`

| Key | Type | Default | Explanation |
|---|---|---|---|
| `scoreboard.title` | string | `<gold><bold>VALMORA RPG` | Title at the top of the sidebar. MiniMessage. Re-applied every tick, so edits show up immediately after `/valmora reload`. |
| `scoreboard.lines` | list of string | *(see below)* | Sidebar lines, top to bottom. `""` = blank line; `"$dynamic$"` = dynamic-section splice point; any other string is a MiniMessage+variable template. Max 16 lines. If empty/absent, the hard-coded fallback sidebar is used. |
| `action-bar.default` | string | `<red>❤ $player.hp$/$player.max_hp$ <dark_gray>\| <green>❈ $player.stat.defense$ Defense <dark_gray>\| <aqua>⛨ $player.mana$/$player.max_mana$ Mana` | The action-bar template shown when no temporary override is active. `""` disables it (fallback bar is used). |
| `tab.header` | string | `""` (shipped default: `<gold><bold>VALMORA</bold></gold>`) | Player-list tab header. Empty = not sent. |
| `tab.footer` | string | `""` (shipped default: `<gray>Players online: <white>$server.online$`) | Player-list tab footer. Empty = not sent. |

Shipped default `scoreboard.lines`:

```yaml
lines:
  - "<yellow>pay.valmora.net"
  - ""
  - "<aqua>⏰ <white>$time.formatted_time$  $time.color$$time.emote$ <gold>$time.phase$ $time.season$"
  - "<gray>Day <white>$time.day$  <dark_gray>│  <gray>Year <white>$time.year$"
  - ""
  - "$dynamic$"
  - "<gray>Profile: <yellow>$player.profile$"
  - "<gray>Zone: $zone.current$"
  - "<gray>Purse: <gold>$economy.purse.formatted$"
```

### 4.2 Variable tokens

Tokens use the script engine's namespace syntax `$namespace.path$`. The ones used by the default config, plus other commonly useful ones:

| Variable | Example | Meaning |
|---|---|---|
| `$player.hp$` | `87` | Current health pool value (from your active profile's player state). |
| `$player.max_hp$` | `100` | Max health stat. |
| `$player.mana$` | `50` | Current mana pool value. |
| `$player.max_mana$` | `50` | Max mana stat. |
| `$player.health_percent$` / `$player.missing_hp_percent$` | `87` / `13` | Health as a rounded percentage. |
| `$player.stat.<id>$` | `45` | Any computed stat by id — `$player.stat.defense$`, `$player.stat.damage$`, etc. |
| `$player.profile$` | `"MyProfile"` | Active profile name. |
| `$player.name$`, `$player.world$`, `$player.ping$`, `$player.biome$` | — | Basic player info. |
| `$time.formatted_time$` | `"14:30"` | HH:mm clock string. |
| `$time.color$` / `$time.emote$` | `<yellow>` / `☀` | MiniMessage color + day/night emote matching the current hour. |
| `$time.day$`, `$time.phase$`, `$time.season$`, `$time.year$` | `12`, `"Early"`, `"Summer"`, `3` | RPG calendar position (see `docs/modules/user/time.md` §4.1 for the full list). |
| `$zone.current$` | `"Wilderness"` | Display name of the zone you are in, or `Wilderness` outside zones. |
| `$zone.id$`, `$zone.pvp$` | `"arena"`, `true` | Zone id and PvP flag. |
| `$economy.purse$` / `$economy.purse.formatted$` | `1200` / `"1.2k"` | Raw / human-formatted coin purse. |
| `$economy.bank$`, `$economy.total$` | — | Bank balance and purse+bank total. |
| `$server.online$`, `$server.max_players$`, `$server.motd$` | `42` / `100` / — | Server-wide info (used in the default tab footer). |

Resolution rules: unknown/unresolvable tokens render as empty; numbers without a fractional part render as integers; a token value that is itself a MiniMessage tag (like `$time.color$`) is honored by the surrounding format.

### 4.3 Related config in `config.yml`

| Key | Type | Default | Explanation |
|---|---|---|---|
| `time.scoreboard-enabled` | boolean | `true` | ⚠️ **Not implemented.** Accepted in `config.yml:83` but ignored — the sidebar's time rows are controlled by `ui.yml` `scoreboard.lines`. Keep it for future compatibility or remove it. |

### 4.4 Related modules

- **Time module** — the `$time.*$` tokens and the season-change action-bar announcement; see `docs/modules/user/time.md`.
- **Zone module** — `$zone.current$` and the zone-entry action-bar banner.
- **Skill module** — the "+XP" action-bar message and the level-up chat block.
- **Item/Ability module** — the cooldown/mana/target action-bar messages.
