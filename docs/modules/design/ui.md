# UI Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.ui` | **Module ID:** `ui`
> **Files:** `UIManager.java`, `UIConfig.java`, `ScoreboardUI.java`, `ActionBarUI.java`, `ChatUI.java`
> **Config:** `src/main/resources/ui.yml` (runtime: `plugins/Valmora/ui.yml`)
> **Dependencies:** none at enable time. Runtime (lazy) reads: `script` (`VariableResolver`), `npc` (`DialogueManager`), plus `time`, `player`, `zone`, `economy` in the fallback renderer.

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

The **UI Module** owns every piece of on-screen HUD text Valmora renders through Bukkit's built-in channels: the **sidebar scoreboard** (right-hand panel), the **action bar** (above the hotbar), the **player-list tab header/footer**, and a small set of **chat message helpers**. It is a pure *presentation* module: it holds no gameplay state, touches no database, and all of its content is driven from a single YAML file (`ui.yml`) plus script `$variable$` tokens resolved at render time.

The module exposes three sub-components through `UIManager`:

| Sub-component | Class | Purpose |
|---|---|---|
| Chat | `ChatUI` | Styled chat messages (level-up, rewards, errors). |
| Action bar | `ActionBarUI` | Persistent/temporary action-bar text with an override queue. |
| Scoreboard | `ScoreboardUI` | Per-player sidebar with 16-line cap, dynamic sections, tab header/footer. |

**The UI clock.** `UIManager.onEnable()` starts a synchronous repeating task on the Bukkit scheduler with period **2 ticks** (10×/second): `Bukkit.getScheduler().runTaskTimer(plugin, ..., 0L, 2L)` (`UIManager.java:75`). Every tick it iterates `Bukkit.getOnlinePlayers()` and calls `actionBar.tick(player)` and `scoreboard.tick(player)` for each (`UIManager.java:76-79`). The 2-tick cadence is what makes temporary action-bar messages (cooldowns, XP gain, zone names) feel smooth, and keeps the scoreboard lines continuously resolved against live `$player.*$`, `$time.*$`, `$zone.*$` values.

**Rendering technique (scoreboard).** `ScoreboardUI` does not use a library like FastBoard. It builds one Bukkit `Scoreboard` per player lazily on first tick (`ScoreboardUI.java:87-100`), registers a `dummy` objective named `valmora_hud` on `DisplaySlot.SIDEBAR`, and pre-registers 16 `Team`s (`valmora_line0`…`valmora_line15`). Each team's *entry* is an invisible legacy color char (`§0`…`§f`) — the classic technique — and the actual line text is set as the team **prefix**. Line order is enforced by assigning `score = lineCount - i` (top-to-bottom, `ScoreboardUI.java:117`). A max of **16 lines** is enforced by `MAX_LINES = 16` (`ScoreboardUI.java:27`); leftover lines are cleared with `resetScores` when the configured line count shrinks (`ScoreboardUI.java:120-122`).

**Text pipeline.** Every template goes through: `VariableResolver.resolveTemplate(template, ctx)` (replaces `$var$` tokens; whole-number doubles render as integers — `VariableResolver.java:37-41`) → `Formatter.format(...)` (MiniMessage with italics disabled — `Formatter.java:11-15`). The `ExecutionContext` used is a fresh `SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration())` built per tick — never stored (`ScoreboardUI.java:218-220`, `ActionBarUI.java:60`), consistent with `AGENTS.md` §7.3.

**Module lifecycle** follows the standard `ReloadableModule` contract (`docs/MODULE_DEVELOPMENT.md` §2): `onEnable()`/`onDisable()` are idempotent and hot-reload safe. `onDisable()` cancels the clock task and unregisters the connection listener (`UIManager.java:57-66`), so `/valmora reload` never leaves orphaned tasks or duplicate join/quit handlers.

---

## 2. Code Structure

Five files, all in `src/main/java/org/nakii/valmora/module/ui/`:

```
src/main/java/org/nakii/valmora/module/ui/
├── UIManager.java       # ReloadableModule facade — lifecycle + 2-tick UI clock (107 lines)
├── UIConfig.java        # Immutable config value object (27 lines)
├── ScoreboardUI.java    # Sidebar rendering, dynamic sections, tab list (233 lines)
├── ActionBarUI.java     # Action-bar override queue + default/fallback bars (90 lines)
└── ChatUI.java          # Styled chat message helpers (32 lines)

src/main/resources/
└── ui.yml               # Scoreboard / action-bar / tab-list templates (§4.1)
```

There are **no tests** for this module (no `src/test/java/org/nakii/valmora/module/ui/` directory).

### Wiring (`src/main/java/org/nakii/valmora/Valmora.java`)

| Step | Line | Code |
|---|---|---|
| Import | `Valmora.java:24` | `import org.nakii.valmora.module.ui.UIManager;` |
| Field declaration | `Valmora.java:97` | `private UIManager uiManager;` |
| Instantiation | `Valmora.java:162` | `this.uiManager = new UIManager(this);` |
| Module registration | `Valmora.java:195` | `moduleManager.registerModule(uiManager);` |
| API getter | `Valmora.java:308-310` | `public UIManager getUIManager() { return uiManager; }` |
| API interface | `ValmoraAPI.java:33` | `org.nakii.valmora.module.ui.UIManager getUIManager();` |

The module is registered **sixth**, after `script` (`Valmora.java:188`), `time` (`:189`), `stat` (`:190`), `player` (`:191`), and `economy` (`:192`). This is the earliest position that still lets every downstream module use it, because `ui` itself has **no enable-time dependencies** — it only needs the Bukkit scheduler and `plugin.yml` resources. The one cross-module access at enable time is none: `script`/`npc`/`time`/`player`/`zone`/`economy` are all fetched **lazily at tick time** via `ValmoraAPI.getInstance()` or `plugin.get...()` getters, which is what makes the ordering safe (see §7).

Note: `UIManager` is *not* exposed as a separate `get...Module()` method on `Valmora`; it is reachable only through the `ValmoraAPI.getUIManager()` interface. The load-order comment block at `Valmora.java:186-222` is authoritative over the (older) list in `docs/MODULE_DEVELOPMENT.md` §9.

---

## 3. Architecture & Key Classes

### 3.1 `UIManager` — module facade (`UIManager.java:19`)

`UIManager implements ReloadableModule`. It owns three `final` sub-components created eagerly in the constructor (`UIManager.java:27-32`) and two lifecycle fields:

| Field | Type | Line | Purpose |
|---|---|---|---|
| `plugin` | `Valmora` | `UIManager.java:20` | Scheduler, logger, `saveResource`, dialogue manager access |
| `chat` | `ChatUI` | `UIManager.java:21` | Created in constructor, never recreated |
| `actionBar` | `ActionBarUI` | `UIManager.java:22` | Created in constructor with plugin |
| `scoreboard` | `ScoreboardUI` | `UIManager.java:23` | Created in constructor with plugin |
| `uiClockTask` | `BukkitTask` | `UIManager.java:24` | The 2-tick repeating render task |
| `connectionListener` | `Listener` | `UIManager.java:25` | Join/quit cleanup handler |

**`onEnable()`** (`UIManager.java:35-54`):
1. `loadUIConfig()` → `UIConfig`, then `scoreboard.setConfig(config)` and `actionBar.setConfig(config)` (`UIManager.java:36-38`).
2. Registers an anonymous `connectionListener` (`UIManager.java:40-51`): on `PlayerJoinEvent` and `PlayerQuitEvent` it calls `scoreboard.removePlayer(uuid)` (`UIManager.java:42-49`). On join this clears any stale cached board; on quit it releases the per-player scoreboard/objective/dynamic-section maps.
3. `startUIClock()` (`UIManager.java:53`).

**`onDisable()`** (`UIManager.java:57-66`): cancels `uiClockTask` (nulls it) and `HandlerList.unregisterAll(connectionListener)` (nulls it). No other per-player state needs cleanup because the connection listener already clears each player's maps on quit.

**`startUIClock()`** (`UIManager.java:73-81`): idempotent — cancels a pre-existing task first, then starts `runTaskTimer(plugin, this::tickAll, 0L, 2L)`. The runnable loops `Bukkit.getOnlinePlayers()` and calls `actionBar.tick(player)` then `scoreboard.tick(player)` (`UIManager.java:76-79`). Runs on the **main thread** (synchronous), matching `AGENTS.md` §11.4.

**`loadUIConfig()`** (`UIManager.java:83-102`): reads `plugins/Valmora/ui.yml` directly via `YamlConfiguration.loadConfiguration` (not `YamlLoader` — see §8). If the file is missing it is copied from the jar with `plugin.saveResource("ui.yml", false)` (`UIManager.java:85-87`), so a fresh install self-seeds. It then extracts:
- `scoreboard.title` with default `"<gold><bold>VALMORA RPG"` (`UIManager.java:91`)
- `scoreboard.lines` via `getStringList` (`UIManager.java:92`)
- `action-bar.default` with the default health/defense/mana bar (`UIManager.java:94-95`)
- `tab.header` / `tab.footer` with default `""` (`UIManager.java:97-98`)
- Logs the loaded line count (`UIManager.java:100`) and returns an immutable `UIConfig` (`UIManager.java:101`).

**Getters** (`UIManager.java:104-106`): `getChat()`, `getActionBar()`, `getScoreboard()` — the public integration surface used by the whole plugin.

### 3.2 `UIConfig` — immutable config value (`UIConfig.java:5`)

A plain immutable value object with five `final` fields (`UIConfig.java:7-11`) and an all-args constructor (`UIConfig.java:13-20`):

| Field | Getter | Line |
|---|---|---|
| `scoreboardTitle` | `getScoreboardTitle()` | `UIConfig.java:22` |
| `scoreboardLines` | `getScoreboardLines()` | `UIConfig.java:23` |
| `actionBarDefault` | `getActionBarDefault()` | `UIConfig.java:24` |
| `tabHeader` | `getTabHeader()` | `UIConfig.java:25` |
| `tabFooter` | `getTabFooter()` | `UIConfig.java:26` |

It is rebuilt from scratch on every `onEnable()` (hot reload re-parses `ui.yml`), and shared by reference into `ScoreboardUI` and `ActionBarUI` via `setConfig(...)`. Because the scoreboard re-reads `getScoreboardTitle()`/`getScoreboardLines()` every tick (`ScoreboardUI.java:106`, `:147`), a `/valmora reload` change to `ui.yml` shows up on the *next tick* with no extra wiring.

### 3.3 `ScoreboardUI` — the sidebar renderer (`ScoreboardUI.java:25`)

#### Per-player state

| Field | Type | Line | Purpose |
|---|---|---|---|
| `playerBoards` | `Map<UUID, Scoreboard>` | `ScoreboardUI.java:38` | One Bukkit scoreboard per online player |
| `playerObjectives` | `Map<UUID, Objective>` | `ScoreboardUI.java:39` | The `valmora_hud` sidebar objective per player |
| `dynamicSections` | `Map<UUID, DynamicSection>` | `ScoreboardUI.java:40` | Injected extra lines (see 3.3.3) |
| `config` | `UIConfig` | `ScoreboardUI.java:42` | Set from `UIManager.onEnable()` |

#### 3.3.1 Line-entry plumbing

- `MAX_LINES = 16` (`ScoreboardUI.java:27`) caps the sidebar.
- `LINE_ENTRIES` (`ScoreboardUI.java:28-35`) is a `static` array of the 16 legacy color chars `§0`–`§f`. Each entry is a **one-character legacy color code** used only as an invisible scoreboard entry key — the visible text lives in the team prefix. (This is the only use of `§` in the module; display text itself always goes through MiniMessage via `Formatter`, per `AGENTS.md` §7.5.)
- Teams `valmora_line0`…`valmora_line15` are registered once when the board is first created, each `addEntry(LINE_ENTRIES[i])` (`ScoreboardUI.java:93-96`).

#### 3.3.2 `tick(Player)` — the render pass (`ScoreboardUI.java:84-134`)

Runs every 2 ticks from the UI clock:

1. **Lazy board creation** (`ScoreboardUI.java:87-100`): `computeIfAbsent` creates a new scoreboard via `Bukkit.getScoreboardManager().getNewScoreboard()`, registers the objective with `obj.registerNewObjective("valmora_hud", "dummy", "unused")`, sets `DisplaySlot.SIDEBAR`, registers the 16 teams, and attaches the board to the player with `player.setScoreboard(b)`.
2. **Title** (`ScoreboardUI.java:105-107`): `obj.displayName(Formatter.format(config.getScoreboardTitle()))`, falling back to the hard-coded `"<gold><bold>VALMORA RPG"` if config is null. Re-applied **every tick** so a config reload takes effect immediately.
3. **Lines** (`ScoreboardUI.java:109-118`): `buildLines(player)` produces the `List<Component>`; `lineCount = Math.min(lines.size(), MAX_LINES)`; for each row `team.prefix(lines.get(i))` and `obj.getScore(LINE_ENTRIES[i]).setScore(lineCount - i)`. The descending score makes line 0 appear at the top.
4. **Cleanup** (`ScoreboardUI.java:120-122`): for rows `i >= lineCount`, `board.resetScores(LINE_ENTRIES[i])` removes stale entries when the configured list is shorter than the previous one.
5. **Tab list** (`ScoreboardUI.java:125-133`): if a header or footer is configured, resolves both templates through the script `VariableResolver` and calls `player.sendPlayerListHeaderAndFooter(...)`. This step is silently skipped if no resolver is available (`resolverOrNull()` returns null, e.g. script module absent).

#### 3.3.3 `buildLines(Player)` — template → line resolution (`ScoreboardUI.java:136-165`)

- If `config` is null **or** `getScoreboardLines()` is empty → `legacyLines(player)` (the hard-coded fallback, 3.3.4).
- Otherwise builds `ctx = playerContext(player)` and `resolver = resolverOrNull()` once per tick, reads the player's `DynamicSection`, then iterates the configured templates:
  - `"$dynamic$"` placeholder (`ScoreboardUI.java:149-155`): if a dynamic section is active and non-empty, splices its lines in followed by a `Component.empty()` spacer.
  - `""` empty template (`ScoreboardUI.java:156-159`): renders a blank line.
  - anything else (`ScoreboardUI.java:160-161`): `resolver.resolveTemplate(template, ctx)` (or the raw template if no resolver) → `Formatter.format(resolved)`.
- Unresolvable variables resolve to `""` (see `VariableResolver.resolveTemplate`, `VariableResolver.java:42`); unresolved MiniMessage-tag *values* (like `$time.color$` rendering a tag) are parsed *after* variable substitution, so a variable that expands to a MiniMessage tag is honored.

#### 3.3.4 Dynamic sections (`ScoreboardUI.java:52-76`)

`DynamicSection` (`ScoreboardUI.java:52-60`) is a small value class: `List<Component> lines` + `boolean locked`. It is injected per-player through two overloads:

```java
void setDynamicSection(Player player, List<Component> lines, boolean locked);                 // :62
void setDynamicSection(Player player, List<String> rawLines, boolean locked, boolean miniMessage); // :74
```

Rules (`ScoreboardUI.java:62-72`):
- If a **locked** section is already active and the incoming replacement is non-empty → the replacement is rejected (the lock can't be overwritten).
- An **empty** `lines` list removes the section.
- Anything else overwrites it.
- The `String` overload maps raw lines with `Formatter::format` and delegates — **the `miniMessage` boolean parameter is ignored** (`ScoreboardUI.java:74-76`).

Sections are spliced into the sidebar only where the `"$dynamic$"` placeholder sits in `scoreboard.lines` (`ScoreboardUI.java:149-155`). This is the intended extension point for "combat lock, dialogue, quest trackers, etc." — as of writing, **no module calls `setDynamicSection`** (see §7, §8).

`removePlayer(UUID)` (`ScoreboardUI.java:78-82`) clears all three maps — called on join (stale-entry hygiene) and quit (memory release) by the connection listener (`UIManager.java:42-49`).

#### 3.3.5 `legacyLines(Player)` — hard-coded fallback (`ScoreboardUI.java:167-216`)

Used when the config hasn't loaded yet (an early tick before `UIManager.onEnable()` finishes) or when `scoreboard.lines` is empty. It builds a fixed layout by reading modules **directly** through `ValmoraAPI.getInstance()`:

| Line | Source | Code |
|---|---|---|
| `pay.valmora.net` (yellow) + blank | hard-coded | `ScoreboardUI.java:170-171` |
| Time clock line (⏰ time, emote+color, phase+season) | `getTimeManager().getSnapshot()` | `ScoreboardUI.java:173-183` |
| Day/Year line | `snap.dayInPhase()` / `snap.year()` | `ScoreboardUI.java:180-181` |
| Active dynamic section + blank | `dynamicSections` | `ScoreboardUI.java:186-190` |
| `Profile: <name>` | `getPlayerManager().getSession(uuid).getActiveProfile()` | `ScoreboardUI.java:192-199` |
| `Zone: <displayName or "Wilderness">` | `getZoneManager().getCurrentZone(player)` | `ScoreboardUI.java:201-206` |
| `Purse: <coins>` | `getEconomyModule().getPurse(uuid)` via `EconomyModule.formatCoinsDisplay` | `ScoreboardUI.java:208-213` |

Every block is wrapped in `try { } catch (Exception ignored) {}`, so a missing/disabled dependency degrades to skipping that line instead of crashing the render loop (`ScoreboardUI.java:184`, `:199`, `:206`, `:213`). Note this path calls into the **economy module's static helper** `org.nakii.valmora.module.economy.EconomyModule.formatCoinsDisplay(...)` (`ScoreboardUI.java:212`).

#### 3.3.6 Helpers

- `playerContext(Player)` (`ScoreboardUI.java:218-220`): `new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration())` — a fresh, minimal, non-thread-safe context per render (never stored).
- `resolverOrNull()` (`ScoreboardUI.java:222-232`): returns `ValmoraAPI.getInstance().getScriptModule().getVariableResolver()`, catching any exception and returning null (used to degrade gracefully if the script module isn't enabled yet or is absent).

### 3.4 `ActionBarUI` — the action bar (`ActionBarUI.java:19`)

#### Override queue

`activeOverrides` (`ActionBarUI.java:21`) maps player UUID → `QueuedMessage` (`ActionBarUI.java:33`, a `record` of `String message` + `long expirationTimeMillis`). `showTemporary(player, message, durationTicks)` (`ActionBarUI.java:35-38`) computes `expirationTimeMillis = System.currentTimeMillis() + durationTicks * 50L` (50 ms per tick) and stores it. This is the only way modules push transient text; it is **per-player** and replaced on every new call.

#### `tick(Player)` — render pass (`ActionBarUI.java:40-69`), in priority order

1. **Dialogue suppression** (`ActionBarUI.java:41-42`): if `plugin.getDialogueManager()` is non-null and reports an active session for the player, the tick returns immediately — the action bar is left to the dialogue system. (Complementary to the packet-level blocking in `module/npc/dialogue/intercept/ConversationPacketManager.java:222-225`.)
2. **Active override** (`ActionBarUI.java:45-54`): if a `QueuedMessage` exists and hasn't expired → `player.sendActionBar(Formatter.format(message))` and return (the default bar is paused). Expired → removed, fall through.
3. **Config template** (`ActionBarUI.java:57-65`): if config is loaded and `action-bar.default` is non-empty, resolves it via the script `VariableResolver` + a fresh `SimpleExecutionContext` and sends it. Wrapped in `try { } catch (Exception ignored) {}` — a resolver failure silently falls through.
4. **Legacy fallback** (`ActionBarUI.java:68`, `:71-89`): `legacyBar(player)` renders the hard-coded health/defense/mana bar.

#### `legacyBar(Player)` (`ActionBarUI.java:71-89`)

Used before config loads. Reads the active profile through the plugin directly (`plugin.getPlayerManager().getSession(uuid)` → `getActiveProfile()`), then:
- `maxHealth = stats.getStat(sys.getHealth())` (`:78`)
- `defense = stats.getStat(sys.getDefense())` (`:79`)
- `currentHealth = player.getHealth() * (maxHealth / player.getAttribute(Attribute.MAX_HEALTH).getValue())` (`:80-81`) — scales the vanilla health by the Valmora max-health ratio
- `maxMana = stats.getStat(sys.getMana())` (`:82`); `currentMana = activeProfile.getPlayerState().getCurrentMana()` (`:83`)
- builds `"<red>❤ hp/max <dark_gray>| <green>❈ def Defense <dark_gray>| <aqua>⛨ mana/maxMana Mana"` and sends it (`:85-88`).

Returns early without sending anything if there is no session or no active profile (`:72-73`).

### 3.5 `ChatUI` — chat helpers (`ChatUI.java:7`)

Stateless; just `Formatter`-wrapped `player.sendMessage(...)` calls. Prefix constant `PREFIX = "<dark_gray>[<gold>Valmora<dark_gray>] <white>"` (`ChatUI.java:10`).

| Method | Line | Output |
|---|---|---|
| `sendReward(Player, String rewardName, int amount)` | `ChatUI.java:12-15` | `[Valmora] You received: <green>Nx <name>` |
| `sendLevelUp(Player, Skill, int newLevel)` | `ChatUI.java:17-19` | Delegates to the string overload |
| `sendLevelUp(Player, String skillName, int newLevel)` | `ChatUI.java:21-27` | A 4-line block: two `<st>` strike dividers around `SKILL LEVEL UP!` and `<aqua>skill <gray>is now level <yellow>level` |
| `sendError(Player, String error)` | `ChatUI.java:29-31` | `[Valmora] <red><error>` |

---

## 4. Configuration (YAML)

### 4.1 `src/main/resources/ui.yml` — module config

Seeded automatically to `plugins/Valmora/ui.yml` on first `onEnable()` if missing (`UIManager.java:85-87`). Read in `UIManager.loadUIConfig()` (`UIManager.java:83-102`). All string values support **MiniMessage** plus **`$variable$` tokens** (see §4.2). The shipped file documents the available variable namespaces in its header comment (`ui.yml:4-6`).

| Key | Type | Default | Read at | Explanation |
|---|---|---|---|---|
| `scoreboard.title` | string | `"<gold><bold>VALMORA RPG"` | `UIManager.java:91` | Objective display name (sidebar top). Rendered via `Formatter.format` every tick (`ScoreboardUI.java:107`). |
| `scoreboard.lines` | list of string | *(empty — see fallback)* | `UIManager.java:92` | Sidebar lines, top-to-bottom. Supports: `""` for a blank line; `"$dynamic$"` to splice the active `DynamicSection` plus a spacer (`ScoreboardUI.java:149-155`); any template with `$variable$` tokens. If empty/missing, `ScoreboardUI` falls back to `legacyLines()` (`ScoreboardUI.java:139-141`). Max **16** lines rendered; extras are dropped (`ScoreboardUI.java:110`). |
| `action-bar.default` | string | `"<red>❤ $player.hp$/$player.max_hp$ <dark_gray>\| <green>❈ $player.stat.defense$ Defense <dark_gray>\| <aqua>⛨ $player.mana$/$player.max_mana$ Mana"` | `UIManager.java:94-95` | The action-bar template shown when **no** temporary override is active. Resolved per tick (`ActionBarUI.java:57-65`). Empty string disables the config path (falls back to `legacyBar`). |
| `tab.header` | string | `""` | `UIManager.java:97` | Player-list tab header template. Empty string = not sent. Only sent when the script `VariableResolver` is available (`ScoreboardUI.java:125-133`). |
| `tab.footer` | string | `""` | `UIManager.java:98` | Player-list tab footer template. Same conditions as header. |

Shipped defaults in the resource file (`ui.yml:10`, `:15-23`, `:30`, `:36-37`):

```yaml
scoreboard:
  title: "<gold><bold>VALMORA RPG"
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

action-bar:
  default: "<red>❤ $player.hp$/$player.max_hp$ <dark_gray>| <green>❈ $player.stat.defense$ Defense <dark_gray>| <aqua>⛨ $player.mana$/$player.max_mana$ Mana"

tab:
  header: "<gold><bold>VALMORA</bold></gold>"
  footer: "<gray>Players online: <white>$server.online$"
```

Note the shipped `ui.yml` comment at `ui.yml:13` claims `"$dynamic$"` is for "combat lock, dialogue, etc." — but nothing in the codebase currently calls `setDynamicSection` (see §7, §8).

### 4.2 `config.yml` — the `time.scoreboard-enabled` key

`src/main/resources/config.yml:82-83` ships:

```yaml
  # Show time lines on the sidebar scoreboard
  scoreboard-enabled: true
```

This is a **dead option**: a repo-wide search shows it is read by no Java code. The sidebar's time lines are governed exclusively by `ui.yml` → `scoreboard.lines` (the `$time.*$` templates at `ui.yml:17-18`). See §8.

### 4.3 No module-local content folder

Unlike most Valmora modules, the UI module has **no** `plugins/Valmora/ui/` content directory and no `YamlLoader` usage — `ui.yml` is the single config file, loaded with plain Bukkit `YamlConfiguration` (`UIManager.java:89`). `/valmora reload` re-runs `loadUIConfig()` and swaps the `UIConfig` in both sub-components, which is the only "reload" surface the module needs.

### 4.4 `$variable$` tokens usable in templates

All templates resolve through the script engine's `VariableResolver.resolveTemplate` (`VariableResolver.java:22-47`). The default `ui.yml` uses these namespaces (provider → source file):

| Namespace | Example used in ui.yml | Provider |
|---|---|---|
| `$player.*$` | `$player.hp$`, `$player.max_hp$`, `$player.mana$`, `$player.max_mana$`, `$player.stat.defense$`, `$player.profile$` | `PlayerVariableProvider` (`module/script/variable/providers/PlayerVariableProvider.java:19`) — `hp`/`mana` are current pool values from the active profile's `PlayerState` (`:140`, `:146`); `max_hp`/`max_mana` come from the stat manager using the `SystemStats` health/mana ids (`:141`, `:147`); `stat.<id>` returns a computed stat (`:46-87`); `profile` returns the active profile name (`:149`). |
| `$time.*$` | `$time.formatted_time$`, `$time.color$`, `$time.emote$`, `$time.phase$`, `$time.season$`, `$time.day$`, `$time.year$` | `TimeVariableProvider` — see `docs/modules/design/time.md` §7.1 for the full table. |
| `$zone.*$` | `$zone.current$` | `ZoneVariableProvider` (`module/zone/ZoneVariableProvider.java:10`) — `current`/`name` → zone display name, `"<green>Wilderness"` when none (`:26`); also `id`, `pvp`. |
| `$economy.*$` | `$economy.purse.formatted$` | `EconomyVariableProvider` (`module/economy/EconomyVariableProvider.java:10`) — `purse.formatted` → `EconomyModule.formatCoinsDisplay(...)` (`:30-31`); also `purse`, `bank`, `total`. |
| `$server.*$` | `$server.online$` (in `tab.footer`) | `ServerVariableProvider` (`module/script/variable/providers/ServerVariableProvider.java:7`) — `online`, `max_players`, `motd`. |
| `$world.*$` | *(none in defaults)* | `WorldVariableProvider` — documented in `ui.yml:5` header as available. |

Resolution rules: unresolved tokens render as `""` (`VariableResolver.java:42`); numbers with no fractional part render as integers (`VariableResolver.java:37-41`); token values that are themselves MiniMessage tags (e.g. `$time.color$` → `<yellow>`) are parsed by the subsequent `Formatter.format` pass, so they color the surrounding text.

---

## 5. Data Model / Persistence

**None.** The UI module has:
- **No database involvement** — it never touches `DataStore`/DAOs.
- **No state file** — `ui.yml` is read-only (it is only *written* by `saveResource` on first enable to copy the bundled default, `UIManager.java:85-87`).
- **No content folder** — no `plugins/Valmora/ui/`.

All runtime state is **per-player, in-memory, ephemeral**:

| State | Owner | Cleared by |
|---|---|---|
| `playerBoards` / `playerObjectives` / `dynamicSections` | `ScoreboardUI` | `removePlayer(uuid)` on join & quit (`UIManager.java:42-49`) |
| `activeOverrides` | `ActionBarUI` | Entries expire on a per-message clock (`ActionBarUI.java:47-49`); the map is never bulk-cleared (players not in it simply fall through to the default bar) |

No state is written on disable other than cancelling the clock task; on a restart everything rebuilds lazily on the first tick. `getServer().getScoreboardManager().getNewScoreboard()` allocations happen once per player per enable (hot reload re-allocates on the next tick, `ScoreboardUI.java:87-100`).

---

## 6. API Exposed

**Primary integration point** — the `ValmoraAPI` interface:

```java
UIManager ui = ValmoraAPI.getInstance().getUIManager();   // ValmoraAPI.java:33, impl Valmora.java:308-310
```

`UIManager` public surface:

| Member | Signature | Line |
|---|---|---|
| `getChat()` | `ChatUI` | `UIManager.java:104` |
| `getActionBar()` | `ActionBarUI` | `UIManager.java:105` |
| `getScoreboard()` | `ScoreboardUI` | `UIManager.java:106` |

(`onEnable`/`onDisable`/`getId` are public but lifecycle-only.)

**`ActionBarUI` public API** — used by 4 other modules today:

| Member | Signature | Line | Consumers |
|---|---|---|---|
| `showTemporary(player, message, durationTicks)` | `void` | `ActionBarUI.java:35` | `zone` (`ZoneListener.java:78`, 60 ticks), `time` (`TimeManager.java:116`, 120 ticks), `skill` (`SkillListener.java:34`, 20 ticks), `item/ability` (`AbilityExecutor.java:48,61,68`, 10 ticks) |

**`ScoreboardUI` public API** — the dynamic-section injection point (currently **unused** by other modules):

| Member | Signature | Line |
|---|---|---|
| `setDynamicSection(player, List<Component> lines, boolean locked)` | `void` | `ScoreboardUI.java:62` |
| `setDynamicSection(player, List<String> rawLines, boolean locked, boolean miniMessage)` | `void` | `ScoreboardUI.java:74` |
| `removePlayer(UUID)` | `void` | `ScoreboardUI.java:78` |

**`ChatUI` public API**:

| Member | Signature | Line | Consumers |
|---|---|---|---|
| `sendReward(player, rewardName, amount)` | `void` | `ChatUI.java:12` | *(no current caller)* |
| `sendLevelUp(player, Skill, newLevel)` / `(player, String, newLevel)` | `void` | `ChatUI.java:17` / `:21` | `skill` — `SkillListener.java:39` |
| `sendError(player, error)` | `void` | `ChatUI.java:29` | *(no current caller)* |

**No commands and no permissions** belong to this module. There is no `/ui` command and no UI-specific permission node; the only permissions that affect it are `valmora.admin` (for `/valmora reload`, which re-reads `ui.yml`) and the `valmora.admin.gui` node (for the unrelated `gui` module's `/gui` command). Nothing to add in `plugin.yml`.

---

## 7. Dependencies & Consumers

### Dependencies (enable-time)

**None.** `UIManager.onEnable()` touches only the Bukkit scheduler and `plugin.yml` resources. It is registered sixth (`Valmora.java:195`) purely so that every later module can rely on its API being live.

### Runtime (lazy) dependencies

Fetched inside `tick()`/`buildLines()`/`legacyBar()` — never at enable time, which is why the load order is safe:

| Dependency | Access point | Used for |
|---|---|---|
| `script` | `ValmoraAPI.getInstance().getScriptModule().getVariableResolver()` (`ScoreboardUI.java:222-232`, `ActionBarUI.java:59`) | Resolving `$variable$` tokens in all templates; if absent, templates fall back to raw text (`ScoreboardUI.java:160`) and the tab list is skipped (`ScoreboardUI.java:127-128`). |
| `npc` (dialogue) | `plugin.getDialogueManager().getSession(uuid)` (`ActionBarUI.java:41-42`) | Suppressing the action bar during an active NPC dialogue. `plugin.getDialogueManager()` itself returns null if `npcModule` is null (`Valmora.java:405-407`) — the null check at `ActionBarUI.java:41` handles that. |
| `time` | `ValmoraAPI.getInstance().getTimeManager().getSnapshot()` (`ScoreboardUI.java:174`) | Fallback `legacyLines` clock/day/year rows only. |
| `player` | `getPlayerManager().getSession(uuid).getActiveProfile()` (`ScoreboardUI.java:195-197`, `ActionBarUI.java:72-76`) | Fallback profile row and fallback action-bar stats. |
| `zone` | `getZoneManager().getCurrentZone(player)` (`ScoreboardUI.java:203`) | Fallback zone row only. |
| `economy` | `getEconomyModule().getPurse(uuid)` + `EconomyModule.formatCoinsDisplay(...)` (`ScoreboardUI.java:211-212`) | Fallback purse row only. |

All of these are wrapped in try/catch (`ScoreboardUI.java:184`, `:199`, `:206`, `:213`; `ActionBarUI.java:64`), so a missing dependency degrades gracefully rather than breaking the render loop.

### Consumers (other modules calling INTO the UI module)

| Module | Call site | Effect |
|---|---|---|
| `zone` | `ZoneListener.java:78` — `getUIManager().getActionBar().showTemporary(player, zone.getDisplayName(), 60)` | Zone-name banner in the action bar for 3 s on zone entry. |
| `time` | `TimeManager.java:114-117` — `showTemporary(p, "<gold><bold>✦ A new season begins — ... ✦</bold></gold>", 120)` | Season-change announcement, 6 s. |
| `skill` | `SkillListener.java:34` — `showTemporary(player, "<aqua>+<yellow>N <aqua>Name XP", 20)` (1 s); `SkillListener.java:39` — `getChat().sendLevelUp(...)` | XP-gain action bar + level-up chat block. |
| `item` (abilities) | `AbilityExecutor.java:48,61,68` — `showTemporary(player, "No target in range!" / "Ability on cooldown: Xs" / "Not enough Mana!", 10)` (0.5 s) | Ability feedback. |

`Valmora.java:189` also references the relationship in its registration comment for `time`: `// No dependencies; scoreboard and scripts read from it` — the scoreboard's `$time.*$` lines and `legacyLines` are exactly the "reads from it" consumer.

**Not a consumer relationship:** the HUD module (`HudItemModule`, `org.nakii.valmora.module.hud`) is a *separate* item-based hotbar-button system (`docs/modules/design/hud.md`) and does not push into `ScoreboardUI`/`ActionBarUI`; the two "UI" systems are independent.

---

## 8. Unfinished Things / TODOs

- **`setDynamicSection` has zero callers.** The entire dynamic-section machinery (`ScoreboardUI.java:52-76`, splice logic at `:149-155`, fallback at `:186-190`) and the `"$dynamic$"` placeholder documented in `ui.yml:13` ("combat lock, dialogue, etc.") are currently dead code — no module injects lines. Either wire up the first consumer (quest tracker, combat lock, dialogue bubble) or it will remain unexercised.
- **The `miniMessage` boolean in `setDynamicSection(Player, List<String>, boolean, boolean)` is ignored** (`ScoreboardUI.java:74-76`) — raw lines are always formatted as MiniMessage regardless of the flag. The parameter is misleading and should be removed or honored.
- **`time.scoreboard-enabled` (`config.yml:83`) is dead config.** No Java reads it; the time rows on the sidebar are controlled entirely by `ui.yml` `scoreboard.lines`. Same finding as in `docs/modules/design/time.md` §8. Wire it into `ScoreboardUI` or delete it.
- **Doc drift in the central docs.** `docs/VALMORA_DOCUMENTATION.md:877` states "the scoreboard rendering loop is currently commented out in the UI clock" — **stale**. `UIManager.java:75-80` currently ticks both the action bar and the scoreboard every 2 ticks. It also documents only `sendLevelUp` for `ChatUI` (`VALMORA_DOCUMENTATION.md:860-861`) and only the two action-bar modes at a high level; this design doc supersedes it.
- **No tests.** `src/test/java/org/nakii/valmora/module/ui/` does not exist. The render loops depend on live `Player`/`Scoreboard` objects and are hard to unit-test with the current structure (state is buried in per-player maps; `legacyLines` touches six modules).
- **Hard-coded fallbacks.** When `ui.yml` is absent/empty, the scoreboard (`ScoreboardUI.java:167-216`) and action bar (`ActionBarUI.java:71-89`) fall back to compile-time literals including `"pay.valmora.net"` — a server-specific string that will show up in the fallback layout regardless of config.
- **`ui.yml` is loaded with raw Bukkit YAML, not `YamlLoader`** (`UIManager.java:89`) — inconsistent with `AGENTS.md` §7.1. No error handling: a corrupt `ui.yml` throws during `YamlConfiguration.loadConfiguration` and fails `onEnable()`; defaults from `cfg.getString(...)` only cover a *missing key*, not a malformed file.
- **Tab header/footer are silently dropped** whenever the script `VariableResolver` is unavailable (`ScoreboardUI.java:127-128`) and only sent when a player ticks (i.e. they are online during a 2-tick pass). There is no event-driven resend on script-module enable.
- **No per-player or per-world control.** Every player on every world gets the same sidebar/action-bar/tab content. There is no toggle (e.g. `/ui toggle`), no per-player line filtering, no condition support in `scoreboard.lines`.
- **Action-bar override semantics are "last write wins".** A second `showTemporary` call silently replaces an active message (even a longer one) — there is no queue/stack, so two systems firing in the same tick clobber each other (e.g. XP-gain 20 ticks vs ability error 10 ticks). The expiry is wall-clock (`System.currentTimeMillis()`, `ActionBarUI.java:36`), so a server-side lag spike can expire messages early relative to game ticks.
- **`legacyBar` scales current health by a ratio of Valmora max to vanilla max** (`ActionBarUI.java:80-81`) and reads modules through `plugin.getPlayerManager()`/`plugin.getStatModule()` directly rather than the `ValmoraAPI` — inconsistent with the scoreboard's API-based fallback and only safe because it runs before config is loaded (and in an uncontrolled early-tick window).
- **ChatUI messages are not localizable/configurable.** All strings (`ChatUI.java:10-31`) are compile-time literals; there is no way to rebrand the prefix or reword the level-up block.

---

## 9. Possible Improvements / Changes

- **Wire the first dynamic-section consumer.** E.g. quests (`QuestManager`) could push the active objective via `setDynamicSection(player, lines, locked=true)` into the `"$dynamic$"` placeholder, and combat could use a non-locked section. This would also let the `"$dynamic$"` comment in `ui.yml:13` become true.
- **Fix the `setDynamicSection` overload.** Remove the unused `miniMessage` parameter (`ScoreboardUI.java:74`) or make it actually switch between raw and MiniMessage handling.
- **Honor or remove `scoreboard-enabled`.** Pass `config.yml → time.scoreboard-enabled` down to `ScoreboardUI` so admins can drop the time rows without editing templates, or delete the key (see `docs/modules/design/time.md` §9 for the cross-module angle).
- **Event-driven tab header/footer.** Re-send the tab header/footer once on join and on `UIManager` enable instead of relying on the 2-tick loop's condition, and re-send when the script module becomes available.
- **Robust config loading.** Move `ui.yml` to `YamlLoader` (`AGENTS.md` §7.1) with try/catch fallback to the hard-coded defaults, so a corrupt file degrades instead of failing the module.
- **Richer action-bar queue.** Give `showTemporary` a priority or stackable behavior so cooldown/XP/zone messages don't clobber each other; or track durations in game ticks instead of wall-clock millis.
- **Per-player control.** Add a `/ui toggle` (scoreboard off/on, stored per player in the existing profile/PDC) and optional per-line conditions (e.g. `when: "$player.stat.defense$ > 0"`) in `scoreboard.lines`.
- **Make fallback content config-driven.** Move the `pay.valmora.net` (`ScoreboardUI.java:170`) and action-bar literal (`ActionBarUI.java:85-87`) into `ui.yml` fallback defaults so a misconfigured install never shows stale branding.
- **Add unit tests.** Extract the pure line-building (`buildLines` against a mocked `VariableResolver` + `DynamicSection`) and the override-expiry logic (`ActionBarUI`) into testable seams, following the `ExpressionTest`/`TimeVariableProviderTest` Mockito pattern (`AGENTS.md` §9). The `SimpleExecutionContext` and `VariableResolver.resolveTemplate` behavior is already unit-testable in isolation.
- **Evaluate a library swap.** The manual team-prefix/§-entry technique (`ScoreboardUI.java:28-35`, `:93-96`) is standard and dependency-free, but if the module grows more complex (per-line animations, hover events), a maintained scoreboard library could replace ~60 lines of plumbing.
- **Unify fallback access patterns.** Make `legacyBar` (`ActionBarUI.java:71-89`) use `ValmoraAPI.getInstance()` like `legacyLines` does, removing the direct `plugin.getPlayerManager()`/`getStatModule()` coupling.
