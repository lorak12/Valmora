# Notify Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `notify` | **Source:** `src/main/java/org/nakii/valmora/module/notify/`

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

The Notify module is Valmora's **centralized player-notification system**. It decouples "a notification happened" from "how the notification is shown": any other system emits a *message* plus a *category* or explicit *IO type*, and the Notify module routes it to one of seven display channels (`chat`, `actionbar`, `title`, `subtitle`, `bossbar`, `sound`, `advancement`). This is the module that powers the `notify <message> [category:…] [io:…] [key:value …]` and `notifyall …` script events used throughout quests, HUD items, calendar events, pets, and slayers.

The module is small — **twelve files, 408 lines**:

- `NotifyModule.java` — the `ReloadableModule` lifecycle. Creates the manager, registers all seven `NotifyIO` implementations, and registers the `notify` / `notifyall` script events.
- `NotifyManager.java` — the router. Holds the IO registry and the notification-category map, merges category defaults with per-call overrides, and dispatches to the correct IO.
- `NotifyIO.java` — the `interface NotifyIO` contract (`getName()` + `send(Player, String message, Map<String,String> settings)`).
- `NotifyEvent.java` / `NotifyAllEvent.java` — `EventFactory` implementations exposing `notify` and `notifyall` to the script DSL.
- `io/` — the seven concrete `NotifyIO` renderers.

Key design decisions:

- **IO type is just a string.** The IO registry is keyed case-insensitively by name (`NotifyManager.java:20-22`), and an unknown or missing IO silently falls back to `chat` (`NotifyManager.java:53`). Nothing throws on an unrecognized IO name.
- **Categories are string-keyed setting maps.** A category is a `Map<String, String>` of IO-specific settings (optionally including an `io` key). Categories merge category defaults → per-call overrides at send time (`NotifyManager.java:38-47`). Two built-ins ship hard-coded: `info` → `io: chat`, `error` → `io: actionbar` (`NotifyManager.java:14-18`).
- **No configuration of its own.** The module reads **no config file** — it has no folder, no entry in `config.yml`, and is absent from the `saveAllResources()` copy list (`Valmora.java:456-490`). Categories come from quest packages (see [Configuration (YAML)](#configuration-yaml)) and from hard-coded defaults.
- **Hook-in is via the script engine.** The module never registers a listener or a command. The only public hook is `ValmoraAPI.getNotifyManager()` plus the two script events, so any scripted system can emit notifications.
- **Hot-reload safe by construction.** `onEnable()` creates a fresh `NotifyManager` each time and re-registers the script events; `onDisable()` nulls the manager. Because `ScriptModule.onDisable()` clears its whole event registry (`ScriptModule.java:84-85`), the re-registered `notify`/`notifyall` factories do not duplicate on reload.
- **`docs/todo.md:10` confirms the intent:** `- notify: accually implement a proper nofity module` — this module *is* that implementation, and it still carries unfinished edges (see [Unfinished Things / TODOs](#unfinished-things--todos)).

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/notify/
├── NotifyModule.java            # ReloadableModule — lifecycle, IO registration, script-event registration
├── NotifyManager.java           # Router — IO registry + category map + send() resolution/merge
├── NotifyIO.java                # NotifyIO interface contract
├── NotifyEvent.java             # EventFactory for the "notify" script event (single player)
├── NotifyAllEvent.java          # EventFactory for the "notifyall" script event (broadcast)
└── io/
    ├── ChatIO.java              # "chat"       → player.sendMessage(MiniMessage)
    ├── ActionBarIO.java         # "actionbar"  → player.sendActionBar(MiniMessage)
    ├── TitleIO.java             # "title"      → Adventure showTitle, message split on \n
    ├── SubTitleIO.java          # "subtitle"   → Adventure showTitle with empty main title
    ├── BossBarIO.java           # "bossbar"    → showBossBar, auto-hide via runTaskLater
    ├── SoundIO.java             # "sound"      → player.playSound(Adventure Sound)
    └── AdvancementIO.java       # "advancement"→ STUB — falls back to actionbar with "✦ " prefix

src/main/resources/quests/forgotten_mine/notifications.yml   # shipped example notification categories
```

There is **no test coverage** for this module (`src/test/java` contains no notify test).

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `NotifyModule.java`

Implements `ReloadableModule` (see `docs/MODULE_DEVELOPMENT.md` §2).

| Method | Behavior | Lines |
|---|---|---|
| `onEnable()` | Logs, creates `new NotifyManager()`, registers all seven IOs (`ChatIO`, `ActionBarIO`, `TitleIO`, `SubTitleIO`, `BossBarIO(plugin)`, `SoundIO`, `AdvancementIO`), then registers `NotifyEvent` and `NotifyAllEvent` on the script module | `NotifyModule.java:16-31` |
| `onDisable()` | Logs and nulls `notifyManager`. **Does not unregister the script events** — that cleanup is owned by `ScriptModule.onDisable()` clearing its event registry (`ScriptModule.java:84-85`) | `NotifyModule.java:33-37` |
| `getId()` | `"notify"` | `NotifyModule.java:39` |
| `getName()` | `"Notification System"` | `NotifyModule.java:40` |
| `getNotifyManager()` | Returns the manager field; **null until `onEnable()` has run** | `NotifyModule.java:42` |

The constructor stores only the plugin reference (`NotifyModule.java:12-14`). All state is initialized in `onEnable()`, so `/valmora reload` is safe — with one caveat documented in [Unfinished Things / TODOs](#unfinished-things--todos): because the manager is null until `onEnable()`, any consumer that asks for it during an *earlier* module's `onEnable()` gets `null`.

The script-event registration (`NotifyModule.java:29-30`) is the module's only side effect on the rest of the plugin:

```java
plugin.getScriptModule().registerEvent(new NotifyEvent());
plugin.getScriptModule().registerEvent(new NotifyAllEvent());
```

`registerEvent` is case-insensitive (`SimpleRegistry.java:20-22`), so `notify` / `notifyall` are matched regardless of case in the DSL.

### 3.2 The `NotifyIO` Contract — `NotifyIO.java`

The entire module hangs off one minimal interface (`NotifyIO.java:7-9`):

```java
public interface NotifyIO {
    String getName();
    void send(Player player, String message, Map<String, String> settings);
}
```

All settings travel as `Map<String, String>` — every key and value is a string (YAML scalars become strings via `getString`, see §4). Each IO is responsible for interpreting its own keys and for defaulting missing values.

### 3.3 The Router — `NotifyManager.java`

Two registries:

| Field | Type | Populated | Lines |
|---|---|---|---|
| `ioRegistry` | `Map<String, NotifyIO>` (case-insensitive keys, lowercased) | `registerIO()` — called by `NotifyModule.onEnable()` for the seven built-ins | `NotifyManager.java:11`, `:20-22` |
| `categories` | `Map<String, Map<String, String>>` (case-insensitive keys, lowercased) | constructor (built-ins) + `loadCategory()` | `NotifyManager.java:12`, `:14-18`, `:24-26` |

**Built-in categories** (`NotifyManager.java:14-18`):

| Category | Settings | Default IO |
|---|---|---|
| `info` | `{io: chat}` | `chat` |
| `error` | `{io: actionbar}` | `actionbar` |

> **Doc mismatch:** `docs/QUEST_SYSTEM.md:991` claims the built-in `info` default is `actionbar`. The code default is **`chat`** (`NotifyManager.java:16`). `docs/USER_DOCS.md:1230` correctly says `chat`. `docs/QUEST_SYSTEM.md` is stale.

**`send(Player, String message, String ioName, String category, Map<String,String> overrides)`** (`NotifyManager.java:37-55`) is the core pipeline:

```
send(player, message, ioName, category, overrides)
  │
  ├─ 1. settings = {}                                        (NotifyManager.java:38)
  ├─ 2. if category != null → settings.putAll(categories.get(category.toLowerCase()))
  │        (absent category contributes nothing)             (NotifyManager.java:41-44)
  ├─ 3. if overrides != null → settings.putAll(overrides)     (NotifyManager.java:47)
  ├─ 4. resolvedIO = ioName != null ? ioName
  │                  : settings.getOrDefault("io", "chat")    (NotifyManager.java:50-51)
  ├─ 5. io = ioRegistry.getOrDefault(resolvedIO.toLowerCase(), ioRegistry.get("chat"))
  │        (unknown/missing IO → chat)                        (NotifyManager.java:53)
  └─ 6. if io != null → io.send(player, message,
                               Collections.unmodifiableMap(settings))  (NotifyManager.java:54)
```

Merge semantics: an explicit `ioName` beats everything; otherwise the category's `io` key is used; otherwise `chat`. IO-specific overrides always win over category defaults. The settings map handed to the IO is immutable.

`sendCategory(Player, String message, String category)` is a convenience that forwards with `ioName = null` and no overrides (`NotifyManager.java:57-60`) — used by quest objective progress notifications (§7).

**`loadCategory(String name, Map<String, String> settings)`** (`NotifyManager.java:24-26`) stores the name lowercased with a defensive copy of the settings map. It **overwrites** an existing category of the same name; there is no `clear()` for the category map (a stale category survives a package reload if a later load doesn't redefine it).

### 3.4 The Seven IOs

All render via `Formatter.format()` (see §3.6) except `SoundIO`, which plays a sound instead of text.

#### `chat` — `ChatIO.java`

```java
player.sendMessage(Formatter.format(message));   // ChatIO.java:13-14
```

Sends a MiniMessage chat message. Reads **no settings**.

#### `actionbar` — `ActionBarIO.java`

```java
player.sendActionBar(Formatter.format(message)); // ActionBarIO.java:13-14
```

Adventure action bar (replaces whatever is currently in the bar). Reads **no settings**. The action bar is transient by nature — there is no `stay`/`duration` key honored (docs in `docs/USER_DOCS.md:1239` mention a `duration` key, but **no IO implements it**).

#### `title` — `TitleIO.java`

```java
String[] parts = message.split("\\\\n", 2);                 // TitleIO.java:16 — literal backslash-n
title    = Formatter.format(parts[0])
subtitle = parts.length > 1 ? Formatter.format(parts[1]) : Component.empty()   // :17-18
fadeIn  = parseInt(settings.get("fadeIn"),  10)             // :19
stay    = parseInt(settings.get("stay"),    70)             // :20
fadeOut = parseInt(settings.get("fadeOut"), 20)             // :21
player.showTitle(Title.title(title, subtitle,
    Title.Times.times(fadeIn*50ms, stay*50ms, fadeOut*50ms)))  // :22-23
```

Uses the modern Adventure `Title` API (`AGENTS.md` §11.3). **The title/subtitle split is on the literal two-character sequence `\n`** (backslash + `n`), so in YAML you write `"notify Title text.\\nSubtitle text. io:title"`. Times are specified in **ticks** and converted to milliseconds (`* 50L`) by the IO. `parseInt` falls back to the default on missing or non-numeric values (`TitleIO.java:26-29`).

#### `subtitle` — `SubTitleIO.java`

Same time keys and defaults as `title` (`fadeIn` 10, `stay` 70, `fadeOut` 20), but renders the message as the subtitle with an empty main title (`SubTitleIO.java:16-20`). The message is **not** split on `\n` — the whole thing becomes the subtitle.

#### `bossbar` — `BossBarIO.java`

```java
BossBar.Color   color    = parseColor(settings.getOrDefault("barColor", "WHITE"))   // :22
BossBar.Overlay style    = parseOverlay(settings.getOrDefault("style", "PROGRESS")) // :23
float           progress = parseFloat(settings.get("progress"), 1.0f)               // :24
int             stay     = parseInt(settings.get("stay"), 70)                       // :25
BossBar bar = BossBar.bossBar(Formatter.format(message), progress, color, style);   // :27
player.showBossBar(bar);
Bukkit.getScheduler().runTaskLater(plugin, () -> player.hideBossBar(bar), stay);    // :29
```

- `barColor` values are `BossBar.Color` enum names (`RED`, `GREEN`, `BLUE`, `WHITE`, …); invalid → `WHITE` (`BossBarIO.java:32-34`).
- `style` values are `BossBar.Overlay` names (`SOLID`, `SEGMENTED_6`, `SEGMENTED_10`, `SEGMENTED_12`, `SEGMENTED_20`, `PROGRESS`); invalid → `PROGRESS` (`BossBarIO.java:36-38`).
- `progress` is a float in `[0.0, 1.0]`; invalid → `1.0` (`BossBarIO.java:40-43`).
- `stay` is in **ticks** and is used directly as the `runTaskLater` delay — unlike the title IOs, it is **not** multiplied by 50.
- The bar is auto-hidden by a **global** scheduler task (`Bukkit.getScheduler().runTaskLater`). `AGENTS.md` §11.13 recommends the entity scheduler for entity-bound tasks; this uses the global one, and the hide task is not cancelled on quit (`BossBarIO.java:29`).

#### `sound` — `SoundIO.java`

```java
String soundKey = settings.get("sound");
if (soundKey == null || soundKey.isEmpty()) return;                     // :15-16 — silent no-op
float volume   = parseFloat(settings.get("soundvolume"), 1.0f);         // :17
float pitch    = parseFloat(settings.get("soundpitch"), 1.0f);          // :18
Sound.Source category = parseCategory(settings.getOrDefault("soundcategory", "MASTER")); // :19
player.playSound(Sound.sound(Key.key(soundKey), category, volume, pitch));               // :20
```

- The **message is ignored entirely** — the sound comes from the `sound:` setting (e.g. `"notify io:sound sound:block.anvil.use"`).
- `soundcategory` is an Adventure `Sound.Source` name (`MASTER`, `MUSIC`, `RECORDS`, `WEATHER`, `BLOCKS`, `HOSTILE`, `NEUTRAL`, `PLAYERS`, `AMBIENT`, `VOICE`); invalid → `MASTER` (`SoundIO.java:28-30`).
- `Key.key(soundKey)` throws `IllegalArgumentException` on an invalid key (bad namespace/characters). **Not caught** — an invalid `sound:` value throws inside event execution. A missing/blank `sound:` is a silent no-op.

#### `advancement` — `AdvancementIO.java`

```java
player.sendActionBar(Formatter.format("✦ " + message));   // AdvancementIO.java:20
```

**Stub.** The class comment states the reason (`AdvancementIO.java:9-14`): Paper 1.21 does not expose a public API for fake advancement toasts without registering a real advancement, so this falls back to an action bar with a `✦` prefix. The documented `frame` and `icon` settings are ignored.

### 3.5 DSL Parsing — `NotifyEvent.java` / `NotifyAllEvent.java`

Both are `EventFactory` implementations (`docs/MODULE_DEVELOPMENT.md` §6.4, `EventFactory.java:8-21`) compiled by `EventParser` and executed against an `ExecutionContext`. Neither uses `EventOptions` — `delay:`/`conditions:`/`notify` are handled *outside* the factory by `EventParser` (§3.7).

**Grammar** (identical for both, `NotifyEvent.java:20-38`):

```
notify[all] <message> [category:<name>] [io:<type>] [key:value ...]
```

Argument scan order (single pass over the space-split args, `NotifyEvent.java:26-38`):

| Token | Handling |
|---|---|
| starts with `category:` | `category = arg.substring(9)` |
| starts with `io:` | `ioName = arg.substring(3)` |
| contains `:` (otherwise) | `extra.put(arg.split(":", 2)[0], arg.split(":", 2)[1])` — a generic setting |
| otherwise | appended to the message, joined with single spaces |

`compile` short-circuits to a no-op when `args.length < 1` (`NotifyEvent.java:18`, `NotifyAllEvent.java:18`).

**Execution:**

- `NotifyEvent` — `ctx.getPlayerCaster().ifPresent(...)`; requires the caster to be a `Player`, then calls `ValmoraAPI.getInstance().getNotifyManager().send(player, message, finalIO, finalCategory, finalExtra)` (`NotifyEvent.java:45-49`). If no player caster (e.g. console-triggered), the notification is dropped.
- `NotifyAllEvent` — broadcasts to every entry of `Bukkit.getOnlinePlayers()` (`NotifyAllEvent.java:44-50`). No player caster is required.

Both resolve the manager **at execution time** via `ValmoraAPI.getInstance().getNotifyManager()` and null-check it (`NotifyEvent.java:47-48`, `NotifyAllEvent.java:45-46`), which is what makes them reload-safe.

**Parsing quirks (verified against shipped config):**

- Tokens are matched **case-sensitively** (`startsWith("category:")`), so `CATEGORY:` or `IO:` is treated as a message word.
- Any token with a colon that isn't `category:`/`io:` becomes a generic setting. A colon inside a message word (e.g. `Peter:Heya`) is therefore consumed as `key:value` — this is exactly the escape warning in `docs/QUEST_MODULE_OUTLINE.md:449-457`.
- The shipped configs use `notify chat <gold>[Slayer] ...` (`slayers/zombie.yml:11`, `pets/baby_wolf.yml:13`). Since bare `chat` has no colon, it is treated as **the first message word** — the player sees the literal word `chat` prepended. The documented form is `notify <message> io:chat`. (Also flagged in `docs/modules/design/slayer.md:291`.)

### 3.6 Text Rendering — `util/Formatter.java`

Every text IO renders through `Formatter.format()` (`Formatter.java:13-15`):

```java
static MiniMessage miniMessage = MiniMessage.builder()
    .postProcessor(component -> component.style(component.style()
        .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)))
    .build();
```

MiniMessage is configured to **force-strip italics** on all output (so legacy/italicized source text doesn't bleed through). This is the project-standard MiniMessage formatter (`AGENTS.md` §7.5).

### 3.7 Interplay With `EventParser` — reserved tokens

`EventParser.parse(String raw)` splits the action string on spaces and strips four tokens **before** handing args to the factory (`EventParser.java:41-55`):

| Token | Effect | Lines |
|---|---|---|
| `notify` (bare word) | sets `EventOptions.notifyPlayer()` | `EventParser.java:43-44` |
| `delay:<n>` | sets `EventOptions.delay()` → wraps execution in `runTaskLater` | `EventParser.java:45-48`, `:83-89` |
| `conditions:` / `condition:` | sets the condition guard wrapped around the compiled event | `EventParser.java:49-51`, `:73-81` |

This is a distinct, unrelated mechanism from the Notify module. It means:

- A `notify` action whose message contains the **bare word `notify`** as a standalone token, or any of `delay:…` / `conditions:…` / `condition:…` tokens, will have those tokens **consumed by `EventParser`** and never reach `NotifyEvent` as message text. `category:` / `io:` and other `key:value` tokens pass through untouched.
- The `notify` **option** (e.g. `give DIAMOND:5 notify`) is handled by `GiveEvent`, which sends a **legacy `§`-code** chat message directly (`GiveEvent.java:34-36`) — it does **not** go through the Notify module. (This is the pattern in `skills/*.yml` reward lists, e.g. `skills/carpentry.yml:57-61`.)

---

## Configuration (YAML)

The Notify module has **no dedicated config file and no folder**. Configuration surfaces are:

1. **Built-in categories** — hard-coded in `NotifyManager` (`NotifyManager.java:14-18`): `info` → `chat`, `error` → `actionbar`.
2. **Quest-package `notifications:` sections** — the only admin-editable source of categories.
3. **Inline DSL settings** — per-`notify`/`notifyall` action `key:value` tokens, plus the objective-level `notify`/`notify:<n>` keyword.

### 4.1 Quest-package `notifications:` section

Parsed by `QuestPackageManager.parseRemainingFeatures` (`QuestPackageManager.java:216-226`):

```java
ConfigurationSection notifSec = cfg.getConfigurationSection("notifications");
for (String catName : notifSec.getKeys(false)) {
    ConfigurationSection cs = notifSec.getConfigurationSection(catName);
    Map<String, String> settings = new HashMap<>();
    for (String k : cs.getKeys(false)) settings.put(k, cs.getString(k, ""));
    pkg.getNotifications().put(catName.toLowerCase(), settings);
}
```

Every value is read as a **string** (`getString(k, "")`), so numeric-looking YAML (`fadeIn: 10`) arrives as `"10"`. The parsed map is stored on the package (`QuestPackage.java:39-40`, accessor `QuestPackage.java:62`) and merged through templates with `putIfAbsent` (`QuestPackageManager.java:560`).

Categories are applied to the live `NotifyManager` in `applyToManagers()`:

```java
if (nm != null)
    pkg.getNotifications().forEach(nm::loadCategory);   // QuestPackageManager.java:585-586
```

> **⚠️ These lines are currently dead in practice.** `applyToManagers()` runs only from `QuestModule.onEnable()` (via `packageManager.loadAll()`, `QuestModule.java:50`, `QuestPackageManager.java:69`), and `questModule` is registered **before** `notifyModule` (`Valmora.java:210` vs `:212`). Since `NotifyModule.notifyManager` is null until `NotifyModule.onEnable()` runs, `plugin.getNotifyManager()` returns null during `QuestModule.onEnable()` and the `if (nm != null)` guard skips category loading. This is true both on first boot and after `/valmora reload` (modules always enable in registration order, `ModuleManager.java:35-44`). Net effect: **package-defined categories are never registered**, and a `category:quest_complete` reference falls back to the default `chat` IO. See [Unfinished Things / TODOs](#unfinished-things--todos).

**Shipped example** — `quests/forgotten_mine/notifications.yml`:

```yaml
notifications:
  quest_complete:            # ← intended: title with 10/60/20 tick times
    io: title
    fadeIn: "10"
    stay: "60"
    fadeOut: "20"
  quest_progress:            # ← intended: action bar
    io: actionbar
  info:                      # ← overrides the built-in "info" for this package
    io: actionbar
```

### 4.2 The `notify` / `notifyall` action DSL

Grammar (see §3.5):

```
notify <message> [category:<name>] [io:<type>] [key:value ...]
notifyall <message> [category:<name>] [io:<type>] [key:value ...]
```

Per-IO settings and defaults (all values are strings in the settings map):

| IO (`io:`) | Setting key | Default | Meaning |
|---|---|---|---|
| `chat` | *(none)* | — | Chat message |
| `actionbar` | *(none)* | — | Action bar text |
| `title` | `fadeIn` | `10` | Fade-in time in ticks (×50 → ms) |
| `title` | `stay` | `70` | On-screen time in ticks |
| `title` | `fadeOut` | `20` | Fade-out time in ticks |
| `subtitle` | `fadeIn` / `stay` / `fadeOut` | `10` / `70` / `20` | Same as `title` |
| `bossbar` | `barColor` | `WHITE` | `BossBar.Color` enum name |
| `bossbar` | `style` | `PROGRESS` | `BossBar.Overlay` enum name |
| `bossbar` | `progress` | `1.0` | Float 0.0–1.0 |
| `bossbar` | `stay` | `70` | Ticks until auto-hide (used raw as the scheduler delay) |
| `sound` | `sound` | *(none — silent no-op if blank)* | Sound key, e.g. `block.anvil.use` |
| `sound` | `soundvolume` | `1.0` | Volume |
| `sound` | `soundpitch` | `1.0` | Pitch |
| `sound` | `soundcategory` | `MASTER` | `Sound.Source` enum name |
| `advancement` | *(ignored)* | — | Stub — renders `✦ <message>` in the action bar |

Category keys mirror the IO keys: a category may define `io` plus any of the settings above (`NotifyManager.java:50-51` uses the category's `io` when no explicit `io:` is given).

### 4.3 Objective `notify` keyword (Quest module)

Two separate parsers read the `notify` / `notify:<n>` objective token:

| Parser | Behavior | Lines |
|---|---|---|
| Package DSL (`parseObjectiveDsl`) | `notify:<n>` → interval `n`; bare `notify` → interval `1`; malformed `notify:<x>` → `1` | `QuestPackageManager.java:321-323` |
| Legacy flat quests (`QuestLoader`) | `notify:<n>` → interval `n`; non-numeric → `1` | `QuestLoader.java:45-48` |

The interval is stored on `QuestObjective` (`QuestObjective.java:15`, `:21`, accessor `:54`; default `0` = disabled). Consumption is in `QuestManager.sendProgressNotification` (`QuestManager.java:308-315`):

```java
if (obj.getNotifyInterval() <= 0) return;                                    // disabled
if (current % obj.getNotifyInterval() != 0 && current < obj.getRequired()) return;  // interval gate
String msg = "<yellow>" + obj.getTarget() + " <gray>(" + current + "/" + obj.getRequired() + ")";
nm.sendCategory(player, msg, "info");                                        // always the "info" category
```

- Fires when `current` is a multiple of the interval **or** the objective just completed (`current == required` bypasses the modulo gate — completion always notifies, matching `docs/QUEST_SYSTEM.md:1847-1849`).
- The message is hard-coded as `<yellow>{target} <gray>({current}/{required})` and always uses the `info` category (`QuestManager.java:313-314`).
- Called from `QuestManager.addProgress` after each progress increment (`QuestManager.java:210`).
- Shipped examples: `quests/forgotten_mine/quests.yml:37` (`notify: 5`), `:78` (`notify: 2`), `:106` (`notify: 10`); `quests/shardworks_quests.yml:15,27,39,62`; `quests/blacksmith_hub/quests.yml:10`.

---

## Data Model / Persistence

**None.** The module keeps no persistent state:

- `NotifyManager.ioRegistry` and `categories` are plain in-memory `HashMap`s (`NotifyManager.java:11-12`), rebuilt fresh in `onEnable()`.
- Categories originate from two volatile sources: the constructor's hard-coded built-ins and quest packages loaded at `QuestModule.onEnable()` (`QuestModule.java:49-50`). They are not written to the database and are not part of any profile/player data.
- The `notifications:` maps themselves live in `QuestPackage` in memory (`QuestPackage.java:39-40`).

Consequences:

- Any notification setting change is applied by editing package YAML + `/valmora reload` (or restart) — there is no runtime-edit command.
- Because categories are re-applied only during `QuestModule.onEnable()` and that currently fails the null-manager guard (see §4.1), the in-memory category map effectively only ever contains the two hard-coded built-ins at runtime.

---

## API Exposed

### `ValmoraAPI` (interface)

`org.nakii.valmora.module.notify.NotifyManager getNotifyManager();` — `ValmoraAPI.java:65`.

Implementation: `Valmora.java:447-449`:

```java
@Override
public org.nakii.valmora.module.notify.NotifyManager getNotifyManager() {
    return notifyModule != null ? notifyModule.getNotifyManager() : null;
}
```

**Returns null** before `NotifyModule.onEnable()` has run (and after `onDisable()`).

### `NotifyManager` public surface

| Method | Signature | Purpose | Lines |
|---|---|---|---|
| `registerIO` | `void registerIO(NotifyIO io)` | Register a display channel by lowercased `getName()` | `NotifyManager.java:20-22` |
| `loadCategory` | `void loadCategory(String name, Map<String,String> settings)` | Register/overwrite a category preset | `NotifyManager.java:24-26` |
| `send` | `void send(Player, String message, String ioName, String category, Map<String,String> overrides)` | Route a notification (category merge + IO resolution + fallback) | `NotifyManager.java:37-55` |
| `sendCategory` | `void sendCategory(Player, String message, String category)` | Shorthand for `send(p, msg, null, category, null)` | `NotifyManager.java:57-60` |

### `NotifyModule` (concrete)

`getNotifyManager()` (`NotifyModule.java:42`) — the only public getter. The module also implements `getId()`/`getName()` as `ReloadableModule` requires (`NotifyModule.java:39-40`).

### Script events

| Event | Effect |
|---|---|
| `notify <message> [category:…] [io:…] [key:value …]` | Send to the execution context's player caster (`NotifyEvent.java:14`) |
| `notifyall <message> [category:…] [io:…] [key:value …]` | Send to all online players (`NotifyAllEvent.java:14`) |

### Commands & permissions

**None.** The module registers no command, no permission, no listener, and no `plugin.yml` entries. Only the generic `/valmora reload` (`valmora.admin`) applies.

---

## Dependencies & Consumers

### Dependencies (things NotifyModule uses)

| Dependency | How it's used | Where |
|---|---|---|
| **ScriptModule** | Registers `NotifyEvent`/`NotifyAllEvent` via `registerEvent(...)`; the `EventFactory`/`CompiledEvent`/`ExecutionContext` machinery drives execution | `NotifyModule.java:29-30`, `NotifyEvent.java:12-50`, `NotifyAllEvent.java:12-51` |
| **ValmoraAPI** | `getNotifyManager()` looked up at execution time | `NotifyEvent.java:47`, `NotifyAllEvent.java:45` |
| **`util/Formatter`** | MiniMessage rendering (with forced italic-off) | `ChatIO.java:5`, `ActionBarIO.java:5`, `TitleIO.java:6`, `SubTitleIO.java:6`, `BossBarIO.java:8`, `AdvancementIO.java:5` |
| **Adventure** | `Title`, `BossBar`, `Sound`, `Key`, `Component` | `TitleIO.java:3`, `SubTitleIO.java:3`, `BossBarIO.java:3`, `SoundIO.java:3-4` |
| **Bukkit scheduler** | Boss bar auto-hide | `BossBarIO.java:29` |
| **QuestModule** (inbound) | Feeds categories via `loadCategory` and consumes `sendCategory` | `QuestPackageManager.java:586`, `QuestManager.java:314` |

Load order: `notifyModule` is registered at `Valmora.java:212`, **after** `scriptModule` (`:188`) and **after** `questModule` (`:210`). Because `NotifyModule.onEnable()` only touches `ScriptModule` at enable-time (registration order guarantees the script registry exists), and because both script events resolve the manager lazily at execution time, the late position is harmless for the events themselves. The late position **is** the root cause of the category-loading defect — see [Unfinished Things / TODOs](#unfinished-things--todos).

### Consumers (things that emit notifications)

- **QuestModule** — the only direct Java consumer of `NotifyManager`:
  - `QuestManager.sendProgressNotification` → `sendCategory(player, msg, "info")` for objective progress (`QuestManager.java:311-314`).
  - `QuestPackageManager.applyToManagers` → `loadCategory` for package categories (`QuestPackageManager.java:585-586`).
- **Every script-driven system** — `notify` / `notifyall` are registered on `ScriptModule`'s event registry (`ScriptModule.java:77-79`), so any event list can use them: quests, GUI clicks, HUD items, calendar events, pets, slayer completion, skill rewards, quest boards, NPC dialogue, etc. (compile happens at execution time via `EventParser.parseList`, e.g. `QuestManager.java:217`, so the late registration is not an issue at runtime).
- **Shipped configs using `notify`/`notifyall`:**
  - `hud-items/default.yml:11,13,25` — `notifyall io:actionbar ...` ("Menu coming soon!", "Profile coming soon!")
  - `calendar/seasonal.yml:8-46` — `notifyall io:title / io:subtitle / io:chat / io:actionbar` for the Harvest Festival, Winter Blessing, and Spring Renewal events
  - `pets/baby_wolf.yml:13-72` — `notify chat ...` for pet ability triggers and milestone levels
  - `slayers/zombie.yml:11-67` — `notify chat ...` on slayer tier completion (via slayer completion events)
  - `quests/forgotten_mine/quests.yml:9,50,86` — `notify ... category:quest_complete`
  - `quests/blacksmith_hub/events.yml:4,10` — `notify ... category:quest_progress` / `category:quest_complete`
  - `quests/shardworks_quests.yml` — `notify:` interval tokens (objective-level)
- **`give … notify`** is a *separate* path: `GiveEvent` sends its own legacy `§6§lVALMORA …` message, bypassing `NotifyManager` entirely (`GiveEvent.java:34-36`). Used in `skills/*.yml` reward lists (e.g. `skills/carpentry.yml:57-61`).

---

## Unfinished Things / TODOs

1. **Quest-package notification categories never load (load-order defect).** `questModule` is registered before `notifyModule` (`Valmora.java:210`, `:212`), so during `QuestModule.onEnable()` → `packageManager.loadAll()` → `applyToManagers()`, `plugin.getNotifyManager()` is null and the `if (nm != null)` guard skips `loadCategory` (`QuestPackageManager.java:585-586`). Verified for both first boot and `/valmora reload` (enable is always registration order, `ModuleManager.java:35-44`). Net effect: `category:quest_complete` / `category:quest_progress` in the shipped quest packages silently fall back to the default `chat` IO, and the `forgotten_mine/notifications.yml` definitions are dead. `docs/todo.md:10` ("accually implement a proper nofity module") predates and is partially resolved by this code, but this defect is unresolved.
2. **`AdvancementIO` is a stub.** It renders `✦ <message>` in the action bar and ignores `frame`/`icon` (`AdvancementIO.java:20`). The class comment (`AdvancementIO.java:9-14`) explicitly defers a real toast to a future NMS/packet-based implementation.
3. **`SoundIO` ignores the message** (`SoundIO.java:15-16`) — the text is discarded and only `sound:` matters; and an invalid `sound:` key throws `IllegalArgumentException` from `Key.key(...)` with no catch (`SoundIO.java:20`), which would propagate out of event execution.
4. **`BossBarIO` uses the global scheduler** for auto-hide (`BossBarIO.java:29`); `AGENTS.md` §11.13 recommends the entity scheduler for entity-bound tasks. The hide task is not cancelled on player quit.
5. **Documented features that do not exist in code.** `docs/QUEST_MODULE_OUTLINE.md:459-568` describes: comma-separated category lists (`category:a,b` — first existing wins), sound options attachable to *any* IO, `advancement` `frame`/`icon`, a `totem` IO, `bossbar` `barFlags`/`countdown`, `soundlocation`/`soundplayeroffset`. None are implemented. `docs/USER_DOCS.md:1239` mentions a `duration` key that no IO honors.
6. **Doc mismatch on the `info` built-in.** Code default is `chat` (`NotifyManager.java:16`); `docs/QUEST_SYSTEM.md:991` claims `actionbar`; `docs/USER_DOCS.md:1230` agrees with code.
7. **Shipped `notify chat …` syntax prints the literal word "chat".** `slayers/zombie.yml:11-67` and `pets/baby_wolf.yml:13-72` use `notify chat <gold>…`, which `NotifyEvent` treats as message text (`NotifyEvent.java:34-37`). Should be `notify <gold>… io:chat`. (Also flagged in `docs/modules/design/slayer.md:291`.)
8. **No way to customize the built-in categories globally.** `info`/`error` are hard-coded in the constructor (`NotifyManager.java:14-18`); per-package overrides exist in config but are currently unreachable (§ item 1).
9. **Category map never cleared.** `loadCategory` overwrites (`NotifyManager.java:24-26`) but there is no reset; a package reload that stops defining a category leaves the stale entry in place.
10. **Message grammar hazards.** Colons in message words are consumed as `key:value` (`NotifyEvent.java:31-33`), and the bare tokens `notify`, `delay:…`, `conditions:…`, `condition:…` are stripped by `EventParser` before the factory runs (`EventParser.java:43-51`). Nothing validates or escapes this for pack authors.
11. **No unit tests** for the module (none under `src/test/java`).

---

## Possible Improvements / Changes

1. **Fix the category load-order defect** — the highest-impact change. Options: (a) register `notifyModule` **before** `questModule` in `Valmora.java` (it only needs `scriptModule`, which is already first, `Valmora.java:188`); (b) have `NotifyModule.onEnable()` re-push categories from already-loaded packages; (c) make `applyToManagers()` defer/retry category registration, or have `NotifyManager.send()` lazily re-apply package categories when a `category:` reference is unknown. (b)/(c) are more robust than reordering because they survive any future ordering drift.
2. **Real advancement toasts** via packets (Paper 1.21 has no public fake-toast API) or via registering lightweight synthetic advancements — honoring `frame` and `icon`.
3. **Add a global `notifications.yml`** at `plugins/Valmora/` (root) so `info`/`error` and server-wide categories are editable without quest packages, and wire it into `NotifyModule.onEnable()`.
4. **Harden `SoundIO`:** validate/catch `Key.key(...)` parse failures (log a warning instead of throwing), and consider resolving bare names like `block.anvil.use` into a namespaced key if the raw key lacks a `:`.
5. **Use Paper's entity scheduler** for the boss bar hide and cancel it on player quit (`player.getScheduler().runDelayed(...)` per `AGENTS.md` §11.13), so a logout mid-`stay` doesn't leave a scheduled task touching a stale player.
6. **Implement the documented DSL extras** from `docs/QUEST_MODULE_OUTLINE.md:459-568` that are cheap: comma-separated categories (first existing), `sound` options accepted on any IO, and `duration` (currently documented but ignored). Drop or re-document the expensive ones (`totem`, `soundlocation`, `countdown`).
7. **Fix the shipped configs** that emit the literal word `chat` (`slayers/zombie.yml`, `pets/baby_wolf.yml`) to the documented `io:chat` form.
8. **Expose the IO registry publicly** (e.g. `getIO(String)` / `getIOs()`) so other modules/plugins can add display channels, and add a `clearCategories()` for reload hygiene.
9. **Validate the notify grammar at compile time** — warn in the console (like `EventParser.java:66` does for unknown events) when a `category:` reference is unknown at execution time, and document the colon/token escaping rules in the user docs.
10. **Tests:** unit-test `NotifyManager.send()` merge/resolution/fallback behavior (category defaults + overrides + unknown IO → chat), each IO's settings parsing (defaults on missing/invalid values), and the `NotifyEvent`/`NotifyAllEvent` arg grammar (category/io/extra/message reconstruction), per `AGENTS.md` §9.
