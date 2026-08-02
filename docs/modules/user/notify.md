# Notify Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `notify` | **Config source:** quest packages (`notifications:` sections) + inline DSL

---

## Table of Contents

1. [Overview](#overview)
2. [Player Guide](#player-guide)
3. [Admin Guide](#admin-guide)
4. [Configuration Reference](#configuration-reference)

---

## Overview

The Notify system is Valmora's way of showing **messages to players through different display channels**. Instead of every feature sending raw chat text its own way, any feature can trigger a *notification* and let the server decide how it is shown — a chat message, an action-bar popup, a screen title, a boss bar, a sound, etc.

Notifications come from two places:

- **Script actions** — the `notify` and `notifyall` events you write inside quest packages, GUI scripts, HUD item clicks, calendar events, pet abilities, and slayer rewards:

  ```
  notify <message> [category:<name>] [io:<type>] [key:value ...]
  notifyall <message> [category:<name>] [io:<type>] [key:value ...]
  ```

  `notify` sends to the player the action runs for; `notifyall` broadcasts to every online player.

- **Automatic objective progress** — quest objectives can take a `notify` / `notify:<n>` keyword in their instruction, which shows the player a progress message as they work toward the objective.

Messages support **MiniMessage** formatting tags (`<red>`, `<bold>`, `<gold>`, `<gray>`, `<reset>`, …).

The module has **no config file of its own** — you configure it through *notification categories*, which are presets that bundle an IO type plus its settings under a name you can reuse in any `notify` action.

---

## Player Guide

### What notifications look like

| Channel | What you see/hear |
|---|---|
| `chat` | A normal chat message. |
| `actionbar` | Text floating just above your hotbar. Replaces whatever was there before. |
| `title` | A big title in the center of your screen, with an optional subtitle below it (title + `\n` + subtitle). Fades in, stays, fades out. |
| `subtitle` | The subtitle line only, under a blank title. |
| `bossbar` | A colored bar at the top of your screen, shown for a few seconds, then removed. |
| `sound` | Plays a sound effect (no visible text). |
| `advancement` | Intended to be an advancement-style toast popup. In the current build this is **not implemented** — you will instead see the message in the action bar with a `✦` prefix. |

### Automatic objective progress

When a quest objective is configured with `notify` or `notify:<n>` in its instruction, you get a progress message as you advance it, e.g.:

```
COAL_ORE (12/20)
```

- `notify` alone → a message on **every** progress step.
- `notify:5` → a message every **5** steps (at 5, 10, 15 …).
- You always get a final notification when the objective **completes**, regardless of the interval.

### Example script actions you may encounter

```
notify <gold>You earned 500 coins! category:quest_complete     → quest-completion title/chat
notify <red>Not enough coins. io:actionbar                     → red text in the action bar
notify <green>Quest complete! <bold>Well done! io:title        → green screen title
notifyall <gold>Boss Spawned! io:title                         → title broadcast to everyone
```

---

## Admin Guide

### Setup

There is nothing to install. The `notify` and `notifyall` events are available in **any** script event list as soon as the plugin loads, and the built-in categories `info` and `error` exist out of the box.

Apply changes to quest packages or config with `/valmora reload` (permission `valmora.admin`).

### Writing `notify` / `notifyall` actions

```
notify <message> [category:<name>] [io:<type>] [key:value ...]
notifyall <message> [category:<name>] [io:<type>] [key:value ...]
```

| Argument | Example | Meaning |
|---|---|---|
| `<message>` | `You earned 500 coins!` | The text, MiniMessage allowed. Every space-separated word that has no `:` is part of the message. |
| `category:<name>` | `category:quest_complete` | Load the settings (IO + extras) from the named category. |
| `io:<type>` | `io:title` | Override the display channel. Beats the category's `io`. |
| `key:value` | `sound:block.anvil.use`, `fadeIn:10`, `barColor:red` | Extra settings for the chosen IO. Override the category's settings. |

Priority: explicit `io:` > category's `io` > `chat` (the built-in default). Extra `key:value` tokens always win over category settings.

**Examples:**

```yaml
- "notify Quest complete!"                                        # uses the "info" category → chat
- "notify <red>Not enough coins. io:actionbar"                    # action bar, no category
- "notify This is a title.\nThis is a subtitle. io:title"         # title + subtitle (literal \n)
- "notify <gold>Boss Spawned! io:title fadeIn:10 stay:60 fadeOut:20"
- "notify io:sound sound:block.anvil.use"                         # just a sound (message ignored)
- "notifyall <green>Server event started! io:title"               # broadcast
```

**Escaping colons — important.** Because `key:value` tokens are detected by the `:`, a colon *inside your message* is treated as a setting, not text. `notify Peter:Heya!` would try to make a setting named `Peter`. Avoid `:` in message text, or the effect will be that the message is eaten. This also applies to `category:`/`io:` — those prefixes are matched case-sensitively, so `CATEGORY:` or `IO:` will be treated as message text.

**Gotchas that affect real configs:**

- `notify chat <gold>[Slayer] …` puts the literal word **`chat`** at the start of the message (the code has no `chat` token — use `io:chat`): `notify <gold>[Slayer] … io:chat`. Several shipped files still use the old form (`slayers/zombie.yml`, `pets/baby_wolf.yml`).
- The bare word `notify` and the tokens `delay:…`, `conditions:…`, `condition:…` are consumed by the script parser as *options* before your message is read. Don't use them as literal words inside a `notify` action.
- `give ITEM:AMOUNT notify` does **not** use this module — it shows a plain system message like `§6§lVALMORA §7» §fYou received …` (a legacy color-coded line). That's a different, separate feature.

### Defining notification categories

Categories are defined in the **`notifications:`** section of a quest package file (e.g. `quests/<package>/notifications.yml`). They let you change the look of every notification of a type in one place.

```yaml
# quests/my_package/notifications.yml
notifications:

  quest_complete:
    io: title
    fadeIn: "10"
    stay: "60"
    fadeOut: "20"

  quest_progress:
    io: actionbar

  # Override the built-in "info" category for this package:
  info:
    io: actionbar
```

Then reference them anywhere:

```yaml
- "notify <green>Quest complete! category:quest_complete"
- "notify <yellow>5 more coal needed! category:quest_progress"
```

**Built-in categories** (always present):

| Category | Default IO | Use |
|---|---|---|
| `info` | `chat` | General info, and used automatically by objective progress notifications |
| `error` | `actionbar` | Errors and warnings |

You can override `info` / `error` by defining a category with that exact name in a package's `notifications:` section.

> ⚠️ **Known limitation (current build):** package-defined categories are parsed, but due to a module load-order issue they are **not registered at runtime**, so a `category:` reference currently falls back to the built-in default (`chat`). Until this is fixed, prefer an explicit `io:` on every `notify` action if you want a specific channel. See the design doc `docs/modules/design/notify.md` §4.1 / §8.

### The objective `notify` keyword

In a quest objective's instruction line, add `notify` (every step) or `notify:<n>` (every `n` steps). Always notifies at completion.

```
collect_coal: "BLOCK_BREAK COAL_ORE 20 conditions:in_mine notify:5"
kill_wolves:  "KILL WOLF 8 events:reward_coins_large notify:2"
```

The progress message format is fixed: `<yellow><target> <gray>(<current>/<required>)`.

### Permissions

The Notify module defines **no permissions and no commands**. Any script that runs `notify`/`notifyall` does so under whatever permissions its parent system (quests, GUI, calendar, …) requires. `/valmora reload` needs `valmora.admin`.

---

## Configuration Reference

### A. `notify` / `notifyall` action grammar

| Part | Type | Default | Description |
|---|---|---|---|
| `notify` / `notifyall` | keyword | — | Event name. `notify` targets the acting player; `notifyall` targets all online players. |
| `<message>` | String (MiniMessage) | *(empty)* | Displayed text. Built from all non-`:` tokens, joined with spaces. |
| `category:<name>` | String | — | Named preset of IO + settings (case-insensitive). |
| `io:<type>` | String | from category, else `chat` | Display channel. Unknown types fall back to `chat`. |
| `key:value` | String | category's value, else IO default | Per-IO setting override. |

### B. IO types and their settings

| `io:` | Setting | Default | Description |
|---|---|---|---|
| `chat` | — | — | Chat message. |
| `actionbar` | — | — | Text above the hotbar. |
| `title` | `fadeIn` | `10` | Fade-in time, ticks. |
| `title` | `stay` | `70` | On-screen time, ticks. |
| `title` | `fadeOut` | `20` | Fade-out time, ticks. |
| `subtitle` | `fadeIn` | `10` | Fade-in time, ticks. |
| `subtitle` | `stay` | `70` | On-screen time, ticks. |
| `subtitle` | `fadeOut` | `20` | Fade-out time, ticks. |
| `bossbar` | `barColor` | `WHITE` | Color: `RED`, `GREEN`, `BLUE`, `WHITE`, `YELLOW`, `PINK`, `PURPLE` (a `BossBar.Color` name). |
| `bossbar` | `style` | `PROGRESS` | Overlay style: `SOLID`, `SEGMENTED_6`, `SEGMENTED_10`, `SEGMENTED_12`, `SEGMENTED_20`, `PROGRESS`. |
| `bossbar` | `progress` | `1.0` | Fill amount, `0.0`–`1.0`. |
| `bossbar` | `stay` | `70` | Ticks until the bar auto-disappears. |
| `sound` | `sound` | *(none)* | Sound key, e.g. `block.anvil.use` or `entity.player.levelup`. Required — blank does nothing. |
| `sound` | `soundvolume` | `1.0` | Volume. |
| `sound` | `soundpitch` | `1.0` | Pitch (0–2). |
| `sound` | `soundcategory` | `MASTER` | Sound source: `MASTER`, `MUSIC`, `RECORDS`, `WEATHER`, `BLOCKS`, `HOSTILE`, `NEUTRAL`, `PLAYERS`, `AMBIENT`, `VOICE`. |
| `advancement` | *(ignored)* | — | Currently a stub — shows `✦ <message>` in the action bar. `frame`/`icon` are documented in the design outline but not implemented. |

Notes:

- `title` splits the message on the literal `\n` (backslash-n) into title / subtitle. Write it in YAML as `"notify Title.\\nSubtitle. io:title"`.
- Times for titles are in **ticks** (20 ticks = 1 second) and converted internally to milliseconds.
- For `sound`, the message text is ignored — only the `sound:` setting matters.

### C. Category schema (quest package `notifications:`)

```yaml
notifications:
  <category-name>:          # case-insensitive; same name overrides the built-in "info"/"error"
    io: <io-type>           # required — which channel this category uses
    <setting>: <value>      # any of the settings in table B (values are read as strings)
```

| Field | Type | Default | Required | Description |
|---|---|---|---|---|
| `<category-name>` | String | — | **Yes** | Referenced as `category:<name>`. |
| `io` | String | — | **Yes** | IO type from table B. |
| any B setting | String | IO default | No | e.g. `fadeIn`, `stay`, `fadeOut`, `barColor`, `sound`, … |

Shipped example (`quests/forgotten_mine/notifications.yml`):

```yaml
notifications:
  quest_complete:
    io: title
    fadeIn: "10"
    stay: "60"
    fadeOut: "20"
  quest_progress:
    io: actionbar
  info:
    io: actionbar
```

### D. Objective `notify` keyword (quests)

| Token | Effect |
|---|---|
| `notify` | Progress notification on every step. |
| `notify:<n>` | Progress notification every `n` steps. Malformed values (`notify:x`) behave like `notify`. |
| *(absent)* | No progress notifications. |

Always sends a notification at completion. The message is fixed: `<yellow><target> <gray>(<current>/<required>)`, sent through the `info` category.
