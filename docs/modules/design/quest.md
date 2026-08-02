# Quest Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `quest` (position 21 in load order) | **Files:** 31 Java source files

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

The Quest Module is Valmora's data-driven quest engine. It provides a full quest lifecycle (start, track, complete, fail, cancel), a flexible objective system with 35 built-in objective types, a conversation/dialogue system for NPC quest givers, quest boards for repeatable daily-style quests, a free-form per-player points system, a player-hider system for conditional visibility, and a journal GUI for players to inspect their active quests.

Quest content is defined entirely in YAML, loaded in two layers:

1. **Legacy flat loader** (`QuestLoader`) — scans `plugins/Valmora/Quests/*.yml` for the original single-file quest format.
2. **Package system** (`QuestPackageManager`) — scans `plugins/Valmora/quests/` recursively for package-based content (the modern format described in `docs/QUEST_SYSTEM.md`).

The package system is loaded on top of the legacy loader, so quests defined in packages override flat quests with the same ID.

### Module load order

`QuestModule` is registered as module #21 in `Valmora.onEnable()` (`Valmora.java:210`), after `warpModule` and before `pointsModule`, `notifyModule`, `collectionModule`, etc. It depends on `scriptModule` (for expression/condition/event parsing), `npcModule` (for NPC conversation binding and interaction events), and `itemManager` (for COLLECT objective item ID resolution via `Keys.ITEM_ID_KEY`).

---

## 2. Code Structure

All source files live under `src/main/java/org/nakii/valmora/module/quest/`. The module follows the conventional `XModule`, `XManager`, listener, loader, and registry pattern described in `docs/MODULE_DEVELOPMENT.md`.

### 2.1 Core package (`quest/`)

| File | Lines | Summary |
|------|-------|---------|
| `QuestModule.java` | 94 | The `ReloadableModule` entry point. Wires `QuestManager`, `QuestPackageManager`, `QuestListener`, `JournalManager`, `PlayerHiderManager`, objective handlers, quest boards, script event/ variable registration. Triggers auto-once objectives for online players on enable. |
| `QuestManager.java` | 321 | Core quest engine. Holds the `QuestDefinition` registry and `ObjectiveHandler` registry. Implements quest lifecycle (start/complete/cancel/fail/reset), `trigger()` for objective progression, completion checks, auto-once activation, progress notifications. |
| `QuestLoader.java` | 60 | Legacy flat-file loader. Uses `YamlLoader` to scan `plugins/Valmora/Quests/` for `.yml` files and parse them as `QuestDefinition` objects via the `objectives` map-list format. |
| `QuestListener.java` | 590 | Bukkit `Listener` with 32 event handlers covering every built-in objective type's triggering event. |
| `QuestCommand.java` | 39 | `/quest` command executor. Subcommand `journal` opens the quest journal GUI. No subcommands otherwise. |
| `QuestDefinition.java` | 32 | Immutable data class: `id`, `name`, `List<QuestObjective> objectives`, `List<String> rewardEvents`. |
| `QuestObjective.java` | 57 | Immutable data class: `id`, `type`, `target`, `required`, `conditions`, `events`, `persistent`, `autoOnce`, `notifyInterval`, `delayTicks`, `intervalTicks`. Two constructors (the full 10-arg one and a convenience 9-arg one). |
| `QuestObjectiveTypes.java` | 49 | `final class` with 35 `String` constants for every built-in objective type ID (lowercase). No enum — raw strings used for registry keys. |
| `QuestEventFactory.java` | 54 | Registers six DSL script events: `quest_start`, `quest_complete`, `quest_cancel`, `quest_fail`, `objective_start`, `objective_delete`. Each delegates to a `QuestManager` method. |
| `QuestVariableProvider.java` | 78 | Registers the `quest` variable namespace with the `ScriptModule`'s `VariableProviderRegistry`. Resolves `$quest.<id>.status$`, `$quest.<id>.progress.<index>$`, `$quest.<id>.objective.<objId>.progress$`, `$quest.<id>.objective.<objId>.required$`, `$quest.objective.<objId>.active$`. |

### 2.2 Objective handlers (`objective/`)

| File | Lines | Summary |
|------|-------|---------|
| `DelayObjectiveHandler.java` | 47 | Implements `ObjectiveHandler` for `DELAY` type. Schedules a one-shot `BukkitRunnable` (seconds mode) or a repeating timer (interval mode) that calls `questManager.trigger()`. |
| `TimerObjectiveHandler.java` | 57 | Implements `ObjectiveHandler` for `TIMER` type. Schedules a repeating 1-second task per player+objective; calls `trigger()` every second until the objective is complete. Tracks tasks in a `Map<String, BukkitTask>`. |
| `NpcRangeObjectiveHandler.java` | 111 | Implements `ObjectiveHandler` for `NPCRANGE` type. Runs a global 1-second tick that iterates all online players and all active `npcrange` objectives, comparing player distance to NPC spawn location. State stored in profile variables as `npcrange.state.<questId>.<objKey>`. |

### 2.3 Package system (`pkg/`)

| File | Lines | Summary |
|------|-------|---------|
| `QuestPackage.java` | 65 | Data container for a loaded package: `path`, `enabled`, `templateNames`, plus maps for `events`, `conditions`, `objectives`, `quests`, `conversations`, `notifications`, `playerHiders`, and `npcConversationBindings`. |
| `QuestPackageManager.java` | 662 | The package loader. Recursively scans `plugins/Valmora/quests/` for directories containing `quest.yml`, then for each package: parses `quest.yml` (package settings + NPC bindings), loads all `.yml` files, expands folder events, parses objectives/quests/notifications/player_hider (pass 1b), then parses conversations (pass 2). Merges templates. Applies all loaded features to `QuestManager`, `DialogueManager`, `NotifyManager`, `PlayerHiderManager`, and `NpcManager`. Also exposes `resolveEvent()` for cross-package event references. |

### 2.4 Quest boards (`board/`)

| File | Lines | Summary |
|------|-------|---------|
| `QuestBoardDefinition.java` | 19 | Immutable data class: `id`, `slots` (int), `pool` (list of quest IDs). |
| `QuestBoardLoader.java` | 36 | Uses `YamlLoader` to load `plugins/Valmora/Quest Boards/*.yml`, parsing each top-level key as a `QuestBoardDefinition`. |
| `QuestBoardRegistry.java` | 16 | Extends `SimpleRegistry<QuestBoardDefinition>`. Adds `registerBoard()` and `getBoard()` convenience methods. |
| `QuestBoardManager.java` | 108 | Manages the quest-board flow: `assignIfEmpty()` fills empty slots with random pool quests (avoiding duplicates); `collect()` fires reward events, resets quest progress, and rerolls a new quest into the slot. Slot keys stored as `questboard.<boardId>.slot.<n>` in profile variables. |
| `QuestBoardEventFactory.java` | 51 | Registers two DSL events: `quest_board_assign <boardId>` and `quest_board_collect <boardId> <slot>`. |
| `QuestBoardVariableProvider.java` | 54 | Registers the `questboard` variable namespace. Resolves `$questboard.<boardId>.slot.<n>.quest_id$`, `.name$`, and `.status$` from profile variables. |

### 2.5 Points system (`points/`)

| File | Lines | Summary |
|------|-------|---------|
| `PointsModule.java` | 33 | `ReloadableModule` with id `points`. On enable, creates `PointsManager`, registers `PointEvent` (DSL event factory) and `PointVariableProvider` (variable namespace). |
| `PointsManager.java` | 42 | Per-player numeric counter manager. Reads/writes profile variables under key prefix `point.<categoryLower>`. Methods: `getPoints`, `setPoints`, `addPoints`, `takePoints`. `setPoints` fires a `PointsChangedEvent`. |
| `PointEvent.java` | 32 | `EventFactory` for the `point` DSL event. Parses `point <category> <add|set|take> <amount>` and delegates to `PointsManager`. |
| `PointsChangedEvent.java` | 28 | Bukkit `Event` — fired when points change. Carries `player`, `category`, `newAmount`. `QuestListener.onPointsChanged` listens to this for `POINT` objective tracking. |
| `PointCondition.java` | 21 | `Condition` record: `point <category> <amount>` — evaluates true if player has >= required points. |
| `PointVariableProvider.java` | 24 | Registers the `point` variable namespace. Resolves `$point.<category>$` to the player's current integer count. |

### 2.6 Journal (`journal/`)

| File | Lines | Summary |
|------|-------|---------|
| `JournalManager.java` | 136 | Implements `Listener`. `openJournal()` builds a 54-slot inventory GUI showing all quests in the registry, with status-colored book icons and progress bars for in-progress quests. Cancels all clicks in the journal inventory. Registered as a Bukkit listener in `QuestModule.onEnable()`. |
| `JournalEventFactory.java` | 29 | `EventFactory` for the `journal` DSL event. Currently only supports `journal open` — opens the journal GUI for the caster player. |

### 2.7 Player hider (`hider/`)

| File | Lines | Summary |
|------|-------|---------|
| `PlayerHiderEntry.java` | 20 | Immutable data class: `id`, `sourceConditions` (list of DSL strings), `targetConditions` (list of DSL strings). |
| `PlayerHiderManager.java` | 87 | Manages conditional player visibility. Runs a `BukkitTask` every 20 ticks that iterates all online player pairs, evaluates source and target conditions, and calls `player.hidePlayer()` / `player.showPlayer()` accordingly. `start()` schedules the task; `stop()` cancels it and restores all visibility. `addEntry()` and `clear()` manage the rule list. |

### 2.8 Shared API interface

| File | Lines | Summary |
|------|-------|---------|
| `api/quest/ObjectiveHandler.java` | 30 | Public interface for custom objective types. `getTypeId()` returns the lowercase type ID; `onQuestStart()` has a default no-op implementation. Registered via `QuestManager.registerObjectiveHandler()`. Designed for external plugins to extend. |

---

## 3. Architecture & Key Classes

### 3.1 Module lifecycle (`QuestModule.java:37-70`)

`QuestModule.onEnable()`:

1. Creates a new `QuestManager` (`QuestModule.java:39`)
2. Registers built-in objective handlers: `DelayObjectiveHandler`, `TimerObjectiveHandler`, `NpcRangeObjectiveHandler` (`QuestModule.java:40-44`)
3. Creates `PlayerHiderManager` and `JournalManager` (`QuestModule.java:45-46`)
4. Loads legacy flat quests via `QuestLoader` (`QuestModule.java:48`)
5. Creates `QuestPackageManager` and loads all packages via `loadAll()` (`QuestModule.java:49-50`)
6. Starts `PlayerHiderManager` and `NpcRangeObjectiveHandler` polling tasks (`QuestModule.java:51-52`)
7. Registers quest script events (`quest_start`, etc.) and variable provider (`QuestVariableProvider`) with `ScriptModule` (`QuestModule.java:53-54`)
8. Creates `QuestBoardRegistry`, loads boards via `QuestBoardLoader`, creates `QuestBoardManager`, registers board events and variable provider, and registers the `journal` event factory (`QuestModule.java:56-61`)
9. Registers `QuestListener` and `JournalManager` as Bukkit listeners (`QuestModule.java:62-64`)
10. Triggers `startAutoOnceObjectivesForPlayer()` for all currently online players (`QuestModule.java:67-69`)

`QuestModule.onDisable()` (`QuestModule.java:73-84`):
- Stops `NpcRangeObjectiveHandler` and `TimerObjectiveHandler` tasks
- Stops `PlayerHiderManager` task and restores visibility
- Unregisters `QuestListener` and `JournalManager` Bukkit listeners
- Clears `QuestManager` registry and `QuestBoardRegistry`
- Nulls out `questBoardManager` and `packageManager`

### 3.2 Quest lifecycle (`QuestManager.java:82-273`)

**Status values** (defined as `String` constants at `QuestManager.java:19-22`):
- `STATUS_NOT_STARTED` = `"not_started"`
- `STATUS_IN_PROGRESS` = `"in_progress"`
- `STATUS_COMPLETED` = `"completed"`
- `STATUS_FAILED` = `"failed"`

Status is stored in profile variables under key `quest.<questId>.status`.

**Start** (`QuestManager.startQuest()`, `QuestManager.java:82-106`):
- Loads the player's active `ValmoraProfile`
- Looks up the `QuestDefinition` in the registry
- Returns early if status is already `in_progress` or `completed`
- Sets status to `in_progress` in profile variables
- For each objective: stores initial progress `0` under key `quest.<questId>.obj.<key>` (where `key` = `obj.getId()` or the index), sets `objective.<objId>.active = true` in profile variables if the objective has an ID
- Calls `handlerRegistry.get(obj.getType()).onQuestStart()` for each objective's handler type (e.g. `DelayObjectiveHandler` schedules timers)
- Sends a chat message: `<gold>Quest started: <questName>`

**Complete** (`QuestManager.completeQuest()`, `QuestManager.java:108-114`):
- Delegates to `finishQuest()` which clears objective flags, sets status to `completed`, and sends `<gold><bold>Quest Completed: <questName>`

**Cancel** (`QuestManager.cancelQuest()`, `QuestManager.java:116-121`):
- Clears all objective active flags
- Sets status to `not_started`

**Fail** (`QuestManager.failQuest()`, `QuestManager.java:123-129`):
- Clears objective flags
- Sets status to `failed`
- Sends `<red>Quest failed: <questId>`

**Reset** (`QuestManager.resetQuestProgress()`, `QuestManager.java:137-150`):
- Clears objective flags and removes all progress variables for the quest
- Sets status to `not_started`
- Used by `QuestBoardManager.collect()` to allow rerolling a completed board quest

### 3.3 Objective triggering (`QuestManager.trigger()`, `QuestManager.java:179-228`)

This is the central method called by `QuestListener` for every Bukkit event. It:

1. Gets the player's active `ValmoraProfile` (`QuestManager.getProfile()`, `QuestManager.java:317-320`)
2. Creates a `SimpleExecutionContext` with the player as caster, their location, and null params (`QuestManager.java:182`)
3. Iterates all registered `QuestDefinition`s in the registry
4. For each quest with `in_progress` status:
   - For each objective:
     - **Type matching**: `obj.getType().equalsIgnoreCase(typeId)` where `typeId` comes from `QuestObjectiveTypes` constants
     - **Target matching**: For `DELAY` type, matches by objective ID (`obj.getId()`). For all other types, matches by `obj.getTarget()` (case-insensitive) or `any`
     - **Condition evaluation**: `evaluateConditions()` parses the objective's `conditions` list via `ConditionParser.parseList()` and evaluates against the context
     - **Progress increment**: `profile.getVariables().put("quest.<questId>.obj.<key>", newVal)` — capped at `obj.getRequired()`
     - **Progress notification**: `sendProgressNotification()` sends an action bar message if `notifyInterval > 0` and either `current % interval == 0` or `current >= required`
     - **Completion handling**: If progress reaches required:
       - If the objective has an ID, sets `objective.<objId>.active = false`
       - Fires objective `events` via `EventParser.parseList(obj.getEvents()).execute(ctx)`
       - If `persistent`: resets progress to 0 and re-activates the objective
   - If any objective changed and the quest has at least one non-persistent objective still pending, calls `checkCompletion()`

**Completion check** (`QuestManager.checkCompletion()`, `QuestManager.java:258-267`):
- Iterates all objectives
- If any non-persistent objective has progress < required, returns (quest not yet complete)
- If all non-persistent objectives are complete, calls `finishQuest()`

**Persistent-only guard** (`QuestManager.isAnyPersistentPending()`, `QuestManager.java:295-300`):
- Returns true if every objective in the quest is persistent
- Used in `trigger()` to skip the completion check — prevents a persistent-only quest from auto-completing on its first progress tick

### 3.4 Condition evaluation (`QuestManager.evaluateConditions()`, `QuestManager.java:302-315`)

Delegates to `ScriptModule.getConditionParser().parseList(conditionStrings)` which returns a `ConditionGroup` (AND logic). Evaluated against the quest's `SimpleExecutionContext`.

### 3.5 Progress notification (`QuestManager.sendProgressNotification()`, `QuestManager.java:308-315`)

Sends a message via `NotifyManager.sendCategory(player, msg, "info")`:
- Format: `<yellow><target> <gray>(<current>/<required>)`
- Only fires when `notifyInterval > 0` AND (`current % interval == 0` OR `current >= required`)
- Uses the `info` notification category (overridable per-package via `notifications:`)

### 3.6 Auto-once objectives (`QuestManager.startAutoOnceObjectivesForPlayer()`, `QuestManager.java:234-246`)

On player join (`QuestListener.onJoin()`, `QuestListener.java:67-74`):
- Iterates all quests and objectives
- For objectives with `autoOnce = true`:
  - Checks if the profile has a tag `<questId>.auto-once-<objectiveId>` (or `<questId>.auto-once-<type>` if no ID)
  - If not tagged: adds the guard tag, then calls `startObjectiveInQuest()` which sets initial progress to 0 and `objective.<id>.active = true`

### 3.7 Objective handlers (`ObjectiveHandler` interface, `api/quest/ObjectiveHandler.java:19-29`)

The `ObjectiveHandler` interface has:
- `String getTypeId()` — must match the lowercase type string in YAML (e.g. `"delay"`, `"timer"`, `"npcrange"`)
- `default void onQuestStart(Player, QuestObjective, QuestManager)` — no-op by default

Registered handlers (in `QuestModule.onEnable()`, `QuestModule.java:40-44`):
1. `DelayObjectiveHandler` — type `DELAY`
2. `TimerObjectiveHandler` — type `TIMER`
3. `NpcRangeObjectiveHandler` — type `NPCRANGE`

Handlers are stored in a `Registry<ObjectiveHandler>` keyed by `getTypeId()` (`QuestManager.java:26`). The `QuestManager.registerObjectiveHandler()` method (`QuestManager.java:34-36`) registers them.

**`DelayObjectiveHandler`** (`DelayObjectiveHandler.java:25-46`):
- If `delayTicks <= 0`: no-op
- If `intervalTicks > 0`: schedules a repeating `BukkitRunnable` that calls `questManager.trigger(player, DELAY, objectiveId, 1)` every `interval` ticks, decrementing remaining until 0
- Otherwise: schedules a one-shot `BukkitRunnable` for `delayTicks` that triggers with `objective.getRequired()` as the amount

**`TimerObjectiveHandler`** (`TimerObjectiveHandler.java:39-50`):
- If `objective.getId() == null`: no-op
- Schedules a repeating task (20 ticks = 1 second) that calls `questManager.trigger(player, TIMER, objectiveId, 1)`
- Tracks tasks in `activeTasks` map keyed by `"<uuid>:<objectiveId>"`
- `cancelAll()` cancels all tasks — called from `QuestModule.onDisable()` (`QuestModule.java:76`)

**`NpcRangeObjectiveHandler`** (`NpcRangeObjectiveHandler.java:52-110`):
- Runs a global 1-second tick on all online players
- For each player with an active `NPCRANGE` objective, iterates the objective's target (format: `<action>:<npcId>`)
- Gets the NPC's spawn location via `NpcManager.getSpawnedLocation(npcId)`
- Checks `player.distanceSquared(npcLoc) <= range * range`
- Fires `trigger()` based on the action: `inside` (while in range), `outside` (while out), `enter` (transition to in), `leave` (transition to out)
- State persisted in profile variable `npcrange.state.<questId>.<objKey>`

### 3.8 Package system (`QuestPackageManager.java:50-662`)

**`loadAll()`** (`QuestPackageManager.java:50-70`):
1. Loads all templates from `plugins/Valmora/templates/` (folders without `quest.yml` are still loaded as templates)
2. Scans all directories in `plugins/Valmora/quests/` recursively via `scanAndLoad()`

**`scanAndLoad()`** (`QuestPackageManager.java:76-96`):
- A folder is a package if it contains `quest.yml`
- Sub-folders with their own `quest.yml` are separate packages (recursively scanned)
- Sub-folders WITHOUT `quest.yml` belong to the parent package
- Disabled packages (`package.enabled: false`) are skipped
- Templates are merged via `mergeTemplates()`

**`loadPackage()`** (`QuestPackageManager.java:102-138`): Two-pass parsing:
- **Pass 1a**: Parse `events:` and `conditions:` from all YAML files in the package (including sub-folders without their own `quest.yml`). Calls `expandFolderEvents()`.
- **Pass 1b**: Parse `objectives:`, `quests:`, `notifications:`, `player_hider:` from all files.
- **Pass 2**: Parse `conversations:` — deferred so named event/condition references can be resolved.

**Folder event expansion** (`QuestPackageManager.expandFolderEvents()`, `QuestPackageManager.java:246-261`):
- A single-line event value starting with `folder ` is expanded to the concatenated action lists of the referenced named events
- Only one level of expansion — referenced events must not themselves be folder events
- Missing references produce a warning log

**Event reference resolution** (`QuestPackageManager.resolveEventRefs()`, `QuestPackageManager.java:510-521`):
- For each event reference token: checks the package's `events` map
- If found: expands to the full action list
- If not found: treated as inline DSL (the raw string is passed through)
- **Important**: Named events from other packages are NOT automatically resolved in this path — they would be treated as inline DSL strings. Cross-package resolution is available via the public `resolveEvent()` method (`QuestPackageManager.java:607-618`) for external callers, but it is not wired into the parse-time pipeline.

**Condition reference resolution** (`QuestPackageManager.resolveConditionRefs()`, `QuestPackageManager.java:528-545`):
- Used only in conversation nodes
- Each token MUST be a named condition from the package's `conditions:` block
- Supports `!` prefix for negation
- Inline DSL strings in conversation conditions produce a warning and are ignored
- Objective `conditions:` fields do NOT go through this — they are parsed as inline DSL directly by the `ConditionParser`

**Pointer resolution** (`QuestPackageManager.resolvePointerTarget()`, `QuestPackageManager.java:496-500`):
- If a pointer name matches a `player_options` key (case-insensitive), it's auto-prefixed with `player.`
- Explicit `player.` prefix is left as-is
- Otherwise returned as-is (NPC node or cross-conversation reference)

**Template merging** (`QuestPackageManager.mergeTemplates()`, `QuestPackageManager.java:551-562`):
- For each template name listed in `package.templates`:
- Uses `putIfAbsent` for events, conditions, objectives, quests, conversations, notifications
- Package's own definitions always win over templates
- Template order matters: first template wins among multiple templates

**`applyToManagers()`** (`QuestPackageManager.java:568-601`):
- Registers all quests from all packages into the `QuestManager` registry
- Registers all conversations/dialogues into the `DialogueManager` registry
- Loads notification categories into `NotifyManager`
- Adds player hider entries to `PlayerHiderManager`
- Applies `npc_conversations` bindings: for each `npcId → conversationId`, looks up the existing NPC definition in `NpcManager`'s registry and re-registers it with `.withConversation(convId)`

### 3.9 Cross-package reference resolution (`QuestPackageManager.resolveEvent()`, `questPackageManager.java:607-629`)

- Syntax: `<packagePath><featureName>` using `>` as separator (e.g. `forgotten_mine>reward_coins_large`)
- Package path uses `-` for nested folders (e.g. `main_story-chapter1`)
- Relative paths use `_` (go up one) and `-` (go down):
  - `_>sibling` — sibling package
  - `-child>child_event` — child package
  - `_-uncle>uncle_event` — up one, then into uncle
- The `resolvePackagePath()` method converts relative paths using a stack-based approach
- **Note**: This method returns a `List<String>` of action strings from the referenced event, or null if not found. It is a public utility method available via `ValmoraAPI.getInstance().getQuestPackageManager().resolveEvent()` but is not called during the standard quest-event parsing pipeline (which only resolves local named events via `resolveEventRefs()`).

### 3.10 Quest boards (`QuestBoardManager.java:23-108`)

**Slot assignment** (`assignIfEmpty()`, `QuestBoardManager.java:36-48`):
- For each slot (1 to `board.getSlots()`):
  - Reads the slot's current quest ID from profile variable `questboard.<boardId>.slot.<i>`
  - If empty or null, calls `assignSlot()`

**Slot assignment** (`assignSlot()`, `QuestBoardManager.java:79-98`):
- Collects all currently occupied quest IDs across all slots
- Filters the board's pool to exclude occupied quests (but falls back to the full pool if all are occupied)
- Picks a random quest from the remaining candidates
- Stores it in the slot variable and calls `QuestManager.startQuest()`

**Collection** (`collect()`, `QuestBoardManager.java:51-77`):
- Verifies the quest in the slot has `COMPLETED` status
- If the quest has `rewardEvents` (the `QuestDefinition` field, not objective-level events), fires them via `EventParser.parseList().execute()`
- Calls `QuestManager.resetQuestProgress()` to reset the quest to `not_started`
- Removes the slot variable
- Sends a chat message: `<gold>Collected rewards for: <questName>`
- Assigns a new slot into the vacated position

Slot key format: `questboard.<boardId>.slot.<slotNumber>` (base-1 indexing, `QuestBoardManager.java:100-102`).

### 3.11 Points system (`PointsManager.java:10-42`)

Points are stored as profile variables under key `point.<category.toLowerCase()>`. There is no separate table — they live in the same `ValmoraProfile.variables` map that persists to the database as JSON.

- `getPoints(uuid, category)` — returns the integer value (0 if unset)
- `setPoints(uuid, category, amount)` — stores and fires `PointsChangedEvent`
- `addPoints(uuid, category, amount)` — calls `setPoints` with current + amount
- `takePoints(uuid, category, amount)` — calls `setPoints` with `max(0, current - amount)`

**Quest integration** (`QuestListener.onPointsChanged()`, `QuestListener.java:452-469`):
- Listens to `PointsChangedEvent`
- Guards with `hasActiveObjectiveType(player, POINT)` to avoid unnecessary iteration
- For each in-progress quest with a `POINT` objective matching the changed category:
  - If `event.getNewAmount() >= obj.getRequired()`, triggers the objective with amount = required
- Also checked on join (`checkPointObjectives()`, `QuestListener.java:571-589`)

### 3.12 Journal GUI (`JournalManager.java:30-49`)

- Creates a 54-slot inventory with title `<dark_green><bold>Quest Journal`
- Iterates all `QuestDefinition`s in the `QuestManager` registry
- Builds an icon per quest:
  - `in_progress` → `WRITABLE_BOOK` (yellow)
  - `completed` → `WRITTEN_BOOK` (green)
  - `failed` → `BARRIER` (red)
  - `not_started` → `BOOK` (gray)
- For in-progress quests, adds lore lines showing each objective's target and progress bar
- Progress bar format: `[██████████]` (10-char, filled/unfilled)
- All clicks in the journal inventory are cancelled

### 3.13 Player hider (`PlayerHiderManager.java:45-62`)

- Runs every 20 ticks (1 second)
- For every pair of distinct online players (source, target):
  - Evaluates all source conditions against the source player's context
  - Evaluates all target conditions against the target player's context
  - If any rule matches (source passes AND target passes): `source.hidePlayer(plugin, target)`
  - Otherwise: `source.showPlayer(plugin, target)`
- Conditions are parsed inline DSL via `ConditionParser.parseList()` — NOT package-named conditions; inline DSL is directly supported
- `stop()` cancels the task and calls `restoreAll()` to show all players again

### 3.14 Objective types (`QuestObjectiveTypes.java:4-48`)

All 35 type IDs are lowercase `String` constants. They fall into natural groups:

**Original types (24):**
`KILL`, `COLLECT`, `REACH_ZONE`, `TALK_TO_NPC`, `CRAFT`, `DIE`, `LOCATION`, `BLOCK_BREAK`, `BLOCK_PLACE`, `JUMP`, `BREED`, `TAME`, `ENCHANT`, `SMELT`, `BREW`, `FISH`, `SHEAR`, `VARIABLE`, `DRINK_POTION`, `LOGIN`, `LEVEL_SKILL`, `STAT_REACH`, `EXP_GAIN`, `DELAY`

**Newer additions (11):**
`LOGOUT`, `RIDE`, `CONSUME`, `STEP`, `ACTION`, `ARROW`, `COMMAND`, `EQUIP`, `EXPERIENCE`, `INTERACT`, `NPCRANGE`, `TAG`, `TIMER`, `POINT`

Note: `CONSUME` is a generalized version of `DRINK_POTION` — `QuestListener.onConsume` fires both `CONSUME` and `DRINK_POTION` (the latter only for potion-type items). Similarly, `ACTION` fires 4 trigger combinations (right/left × specific/any).

### 3.15 QuestListener event coverage (`QuestListener.java:57-590`)

| Bukkit Event | Handler | Objective Type(s) Fired | Guard (`hasActiveObjectiveType`) |
|---|---|---|---|
| `PlayerJoinEvent` | `onJoin` | `LOGIN`, plus stat/exp/point checks | No (always fires) |
| `PlayerQuitEvent` | `onQuit` | `LOGOUT` | No |
| `EntityDeathEvent` | `onKill` | `KILL` | No |
| `EntityPickupItemEvent` | `onPickup` | `COLLECT` | No |
| `ZoneEnterEvent` | `onZoneEnter` | `REACH_ZONE` | No |
| `NpcInteractEvent` | `onNpcInteract` | `TALK_TO_NPC` | No |
| `PlayerDeathEvent` | `onDeath` | `DIE` | No |
| `BlockBreakEvent` | `onBlockBreak` | `BLOCK_BREAK` | No |
| `BlockPlaceEvent` | `onBlockPlace` | `BLOCK_PLACE`, also tags furnace | No |
| `PlayerFishEvent` | `onFish` | `FISH` | No |
| `PlayerShearEntityEvent` | `onShear` | `SHEAR` | No |
| `EntityBreedEvent` | `onBreed` | `BREED` | No |
| `EntityTameEvent` | `onTame` | `TAME` | No |
| `PlayerItemConsumeEvent` | `onConsume` | `CONSUME`, `DRINK_POTION` | No |
| `CraftItemEvent` | `onCraft` | `CRAFT` | No |
| `EnchantItemEvent` | `onEnchant` | `ENCHANT`, `ENCHANT: any` | No |
| `PlayerJumpEvent` | `onJump` | `JUMP` | No |
| `EntityMountEvent` | `onMount` | `RIDE`, `RIDE: any` | No |
| `PlayerInteractEvent` (PHYSICAL) | `onStep` | `STEP` | Yes (`STEP`) |
| `PlayerMoveEvent` | `onMove` | `LOCATION` | Yes (`LOCATION`) + block-change check |
| `SkillLevelUpEvent` | `onSkillLevelUp` | `LEVEL_SKILL` | No |
| `SkillXpGainEvent` | `onXpGain` | `EXP_GAIN` | No |
| `FurnaceSmeltEvent` | `onSmelt` | `SMELT` | Yes (`SMELT`) |
| `PlayerInteractEvent` (RIGHT/LEFT) | `onAction` | `ACTION` (4 variants) | Yes (`ACTION`) |
| `ProjectileHitEvent` (Arrow) | `onArrow` | `ARROW` | Yes (`ARROW`) |
| `PlayerCommandPreprocessEvent` | `onCommand` | `COMMAND` | Yes (`COMMAND`) |
| `PlayerArmorChangeEvent` | `onEquip` | `EQUIP` (4 variants) | Yes (`EQUIP`) |
| `PlayerLevelChangeEvent` | `onLevelChange` | `EXPERIENCE` | Yes (`EXPERIENCE`) |
| `PlayerInteractEntityEvent` | `onInteractEntity` | `INTERACT` (4 variants) | Yes (`INTERACT`) |
| `PlayerInteractAtEntityEvent` | `onInteractAtEntity` | `INTERACT` (4 variants) | Yes (`INTERACT`) |
| `TagAddedEvent` | `onTagAdded` | `TAG` | No |
| `PointsChangedEvent` | `onPointsChanged` | `POINT` | Yes (`POINT`) |

Events guarded by `hasActiveObjectiveType()` (`QuestManager.hasActiveObjectiveType()`, `QuestManager.java:66-76`): iterates all in-progress quests and checks if any objective has the matching type. This is a cheap guard before expensive per-tick listeners (move, step, arrow, command, equip, experience, interact, point).

Events NOT guarded: all events that fire only from discrete player actions (join, quit, kill, pickup, zone enter, NPC interact, death, break, place, fish, shear, breed, tame, consume, craft, enchant, jump, mount, level, xp, smelt, tag, points-changed).

### 3.16 Script event registration

**In `QuestModule.onEnable()`** (`QuestModule.java:53-54`):
- `QuestEventFactory.questManager.startQuest/complete/cancel/fail/objective_start/objective_delete` events registered with `ScriptModule.registerEvent()`
- `QuestVariableProvider` registered with `ScriptModule.registerProvider()`

**In `PointsModule.onEnable()`** (`PointsModule.java:19-20`):
- `PointEvent` (`point <category> <add|set|take> <amount>`) registered as an event factory
- `PointVariableProvider` registered as a variable provider

**In `QuestModule.onEnable()`** (`QuestModule.java:59-61`):
- `QuestBoardEventFactory` (quest_board_assign, quest_board_collect) registered
- `QuestBoardVariableProvider` registered
- `JournalEventFactory` (journal open) registered

**Condition parsing** is hardcoded in `ConditionParser.parse()` (`ConditionParser.java:23-85`):
- `quest <questId> <status>` → `QuestStatusCondition` (checks `QuestManager.getStatus()` against the required status)
- `objective <objectiveId>` → `ObjectiveActiveCondition` (checks `QuestManager.isObjectiveActive()`)
- `point <category> <amount>` → `PointCondition` (checks `PointsManager.getPoints() >= required`)
- Negation `!` prefix supported on all condition types

### 3.17 Furnace owner attribution (`Keys.java:50`, `QuestListener.java:144-151, 299-313`)

`FURNACE_OWNER_KEY` = `valmora_furnace_owner` is a PDC string key. When a player places a furnace-type block (`FURNACE`, `BLAST_FURNACE`, `SMOKER`), the placer's UUID is stored on the block's `TileState` (`QuestListener.onBlockPlace`). Later, `FurnaceSmeltEvent` is attributed to that player by reading the PDC key and looking up the offline player via `Bukkit.getPlayer(UUID)`. This was added specifically to support `SMELT` quest objectives (see `docs/UNFINISHED_FEATURES.md` §11).

---

## 4. Configuration (YAML)

### 4.1 Legacy flat quests — `plugins/Valmora/Quests/*.yml`

Loaded by `QuestLoader` (`QuestLoader.java:23-27`), which uses `YamlLoader` pointing at the `"quests"` subfolder with display name `"Quests"`.

**File path convention:** `QuestLoader` is constructed with `new YamlLoader<QuestDefinition>(plugin, "quests", "Quests")` (`QuestLoader.java:25`). This loads from `plugins/Valmora/quests/` — but `QuestPackageManager` also scans that same folder. The legacy loader runs first (line 48 in `QuestModule.java`), then the package manager runs on top (line 50). Quests with the same ID registered by the package manager will overwrite legacy ones.

Wait — actually, re-reading `QuestLoader.load()`: it clears the registry first (line 24: `registry.clear()`) and then loads from `Quests/` folder. The package manager's `applyToManagers()` then iterates all package quests and registers them into the same `QuestManager` registry (overwriting any legacy entries with the same ID). So the effective precedence is: **package quests override legacy quests**.

Actually, looking more carefully: `QuestLoader` uses folder name `"quests"` (lowercase), while the resource files are at `src/main/resources/quests/`. The `YamlLoader` loads from `plugin.getDataFolder() + "/quests"`, which maps to `plugins/Valmora/quests/`. This is the SAME folder that `QuestPackageManager` scans. So both the legacy loader and the package manager read from the same directory. The legacy loader uses `YamlLoader.load()` which iterates top-level keys in each `.yml` file. The package manager uses its own recursive directory scanning.

The key difference is the format: legacy quests use the flat format (top-level quest key with `objectives` as a map-list), while package quests use `quest.yml` + `quests:` section with structured objectives. Since both load from the same directory, and the package manager runs after the legacy loader, package quests with the same ID overwrite legacy ones.

**Legacy quest schema** (parsed in `QuestLoader.parse()`, `QuestLoader.java:29-58`):

```yaml
<quest-id>:
  name: "<display name>"           # String; defaults to quest ID
  objectives:
    - id: "<objective-id>"         # Optional String; null if absent
      type: "<TYPE>"               # String; lowercase. Defaults to "kill" if absent
      target: "<target>"           # String; empty string if absent
      amount: <integer>            # Integer; defaults to 1
      conditions:                  # Optional list of strings
        - "<condition>"
      events:                      # Optional list of strings
        - "<event>"
      persistent: <boolean>        # Boolean; defaults false
      auto-once: <boolean>         # Boolean; defaults false
      notify: <integer>            # Integer (notify every N) or any truthy value (every step)
  reward-events:                   # Optional list of script action strings
    - "<event>"
```

The `parse()` method (`QuestLoader.java:29-58`) reads:
- `name` via `sec.getString("name", id)`
- `objectives` via `sec.getMapList("objectives")` — iterates each map entry
- For each objective map: `id`, `type` (lowercased, default "kill"), `target` (default ""), `amount` (default 1)
- `conditions` and `events` read as `List<?>` if present
- `persistent` and `auto-once` via `Boolean.parseBoolean()`
- `notify` via `Integer.parseInt()` with catch to default to 1 on parse failure
- `reward-events` via `sec.getStringList("reward-events")`

### 4.2 Package quests — `plugins/Valmora/quests/<package>/*.yml`

The package system is documented in full in `docs/QUEST_SYSTEM.md`. This section documents every YAML key that the code actually parses, with the corresponding code references.

#### 4.2.1 `quest.yml` — Package manifest

Required for a folder to be recognized as a package. Can contain only package settings and NPC bindings; all other feature types can be in any `.yml` file in the package.

```yaml
package:
  enabled: <boolean>        # Default: true  (QuestPackageManager.java:108)
  templates:                # Default: []    (QuestPackageManager.java:109)
    - "<template-name>"

npc_conversations:
  <npc-id>: <conversation-id>  # QuestPackageManager.java:114-120
```

- `package.enabled`: When `false`, the entire package is skipped (`QuestPackageManager.java:83`).
- `package.templates`: List of template folder names to merge. Merged via `putIfAbsent` — package definitions win. Templates loaded from `plugins/Valmora/templates/`.
- `npc_conversations`: Maps NPC IDs (lowercase) to conversation IDs. Applied by re-registering the existing NPC definition with `withConversation()` (`QuestPackageManager.java:592-597`).

#### 4.2.2 `events:` — Named script action lists

```yaml
events:
  <event-name>: "<single-action-string>"         # Comma-separated → split (QuestPackageManager.java:175-177)
  <event-name>:                                     # YAML list → each entry is one action
    - "<action1>"
    - "<action2>"
  <event-name>: "folder <ref1>,<ref2>"            # Expands to referenced events' actions (QuestPackageManager.java:246-261)
```

- Single string values are split on commas (`splitComma()`, `QuestPackageManager.java:648-656`)
- Folder expansion is one level only — referenced events must not themselves be folder events
- Event names are stored lowercase
- Referenced named events are expanded at parse time via `resolveEventRefs()` (`QuestPackageManager.java:510-521`); unrecognized names are treated as inline DSL

#### 4.2.3 `conditions:` — Named condition strings

```yaml
conditions:
  <condition-name>: "<DSL condition string>"     # QuestPackageManager.java:182-188
```

- Values are trimmed and stored
- Used in conversation `NPC_options` and `player_options` `conditions:` fields
- Condition names are stored lowercase
- Inline DSL is NOT accepted in conversation conditions — only named references (with optional `!` negation prefix via `resolveConditionRefs()`, `QuestPackageManager.java:528-545`)

#### 4.2.4 `objectives:` — Named standalone objectives (DSL compact)

```yaml
objectives:
  <objective-id>: "<TYPE> <target> <amount> [conditions:...] [events:...] [persistent] [auto-once] [notify[:<n>]]"
```

Parsed by `parseObjectiveDsl()` (`QuestPackageManager.java:290-328`). For `DELAY` type, uses `parseDelayDsl()`:

```yaml
objectives:
  <objective-id>: "delay <amount> [ticks] [interval:<n>] [events:...]"
```

#### 4.2.5 `quests:` — Quest definitions

```yaml
quests:
  <quest-id>:
    name: "<display name>"           # String; defaults to quest ID
    objectives:
      <obj-id>:
        type: "<TYPE>"                 # String; required; lowercased
        target: "<target>"            # String; required
        amount: <integer>             # Integer; default 1
        conditions: "<condition>"    # String or list of strings
        conditions:                   # OR as a list
          - "<condition1>"
          - "<condition2>"
        events: "<event>"             # String or list
        events:
          - "<event1>"
          - "<event2>"
        persistent: <boolean>         # Boolean; default false
        auto-once: <boolean>          # Boolean; default false
        notify: <integer>             # Integer; 0 = no notify, 1 = every step, N = every N
        notify: <truthy>              # Any non-numeric value → 1 (every step)
      <obj-id>: "<TYPE> <target> <amount> [flags...]"  # DSL compact also accepted
```

Parsed by `parseQuestSection()` (`QuestPackageManager.java:399-417`) which calls either `parseStructuredObjective()` (`QuestPackageManager.java:362-393`) or `parseObjectiveDsl()` depending on whether the value is a `ConfigurationSection` or a `String`.

**DELAY objective structured format:**
```yaml
  <obj-id>:
    type: DELAY
    delay: <seconds>        # Long; seconds by default
    ticks: <boolean>        # Boolean; if true, delay value is in ticks
    interval: <integer>     # Integer; if set, scheduler fires every interval ticks
    events: "..."           # As above
```

#### 4.2.6 `conversations:` — NPC dialogue trees

```yaml
conversations:
  <conversation-id>:
    quester: "<display name>"       # String; defaults to conversation ID
    first: "<node-id>"              # String or list of strings; first passing node to show
    first:                          # List form:
      - "<node-id-1>"
      - "<node-id-2>"
    stop: <boolean>                 # Boolean; default false. If true, player can't walk away
    final_events:                   # String or list
      - "<event>"
    NPC_options:
      <node-id>:
        text: "<display text>"       # String; supports MiniMessage; default ""
        conditions: "<named-cond>"    # Named conditions only; comma-separated or list
        events: "<event>"             # Named events or inline DSL; string or list
        pointers:                    # List of strings
          - "<pointer>"
    player_options:
      <node-id>:
        text: "<display text>"
        conditions: "<named-cond>"
        events: "<event>"
        pointers:
          - "<pointer>"
```

Parsed by `parseConversationSection()` (`QuestPackageManager.java:423-488`). Two passes: NPC options first, then player options (so pointer auto-resolution has all player option keys).

#### 4.2.7 `notifications:` — Notification category overrides

```yaml
notifications:
  <category-name>:
    io: "<io-type>"        # String: actionbar, title, chat, subtitle
    fadeIn: "<ticks>"      # String (ticks); title only; default "10"
    stay: "<ticks>"        # String (ticks); title only; default "70"
    fadeOut: "<ticks>"     # String (ticks); title only; default "20"
```

Parsed in `parseRemainingFeatures()` (`QuestPackageManager.java:217-226`). Applied via `NotifyManager.loadCategory()` which does a `put` (replaces existing). Categories are lowercase-keyed.

#### 4.2.8 `player_hider:` — Conditional player visibility

```yaml
player_hider:
  <hider-id>:
    source_player:          # List of inline condition DSL strings
      - "<condition>"
    target_player:            # List of inline condition DSL strings
      - "<condition>"
```

Parsed in `parseRemainingFeatures()` (`QuestPackageManager.java:228-238`). Note: unlike conversation conditions, these accept **inline DSL directly** (no named condition indirection). Each condition is evaluated against the source or target player via `PlayerHiderManager.evaluate()` which calls `ConditionParser.parseList()`.

### 4.3 Quest boards — `plugins/Valmora/Quest Boards/*.yml`

Loaded by `QuestBoardLoader` (`QuestBoardLoader.java:18-35`), using `YamlLoader` pointing at the `"quest_boards"` subfolder.

```yaml
<board-id>:
  slots: <integer>    # Integer; default 2  (QuestBoardLoader.java:26)
  pool:               # List of quest IDs; must be non-empty  (QuestBoardLoader.java:27-29)
    - "<quest-id>"
    - "<quest-id>"
```

### 4.4 Template packages — `plugins/Valmora/templates/<template-name>/*.yml`

Templates follow the same YAML format as regular packages (events, conditions, objectives, quests, conversations, notifications, player_hider) but do NOT need a `quest.yml`. They are loaded from direct sub-folders of `plugins/Valmora/templates/` (`QuestPackageManager.java:57-60`). Every direct sub-folder is automatically a template.

Templates are merged into packages via `putIfAbsent` — the package's own definitions always win over template definitions.

---

## 5. Data Model / Persistence

### 5.1 Quest progress storage

Quest state is stored entirely in `ValmoraProfile.getVariables()` — a `Map<String, Object>` that is serialized to JSON and persisted in the `valmora_profiles` table's `player_state` column (or similar). No dedicated quest table exists.

**Variable key formats:**

| Key | Value Type | Description | Code location |
|-----|-----------|-------------|---------------|
| `quest.<questId>.status` | String | `"not_started"`, `"in_progress"`, `"completed"`, `"failed"` | `QuestManager.java:91` |
| `quest.<questId>.obj.<key>` | Integer | Objective progress (0 to required) | `QuestManager.java:97,207` |
| `objective.<objectiveId>.active` | Boolean | Whether a standalone or in-quest objective is active | `QuestManager.java:99,159,255` |
| `objective.<objectiveId>.progress` | Integer | Progress for standalone objectives | `QuestManager.java:160` |
| `<questId>.auto-once-<objectiveId>` | (tag) | Guard tag for auto-once activation | `QuestManager.java:240-242` (profile tags) |
| `questboard.<boardId>.slot.<n>` | String | Assigned quest ID for a board slot | `QuestBoardManager.java:96,101` |
| `npcrange.state.<questId>.<objKey>` | String | `"inside"` or `"outside"` | `NpcRangeObjectiveHandler.java:87` |
| `point.<category>` | Integer | Per-player point counter | `PointsManager.java:24` |

Where `<key>` in `quest.<questId>.obj.<key>` is either the objective's `id` (if set) or the objective's zero-based index string.

### 5.2 QuestDefinition registry

Stored in `QuestManager.registry` as a `SimpleRegistry<QuestDefinition>` (`QuestManager.java:25`). Populated by:
- `QuestLoader.load()` — legacy flat quests (`QuestLoader.java:26`)
- `QuestPackageManager.applyToManagers()` — package quests (`QuestPackageManager.java:580`)

Keys are lowercase quest IDs. The registry stores the last-loaded definition for a given ID (put overwrites).

### 5.3 ObjectiveHandler registry

Stored in `QuestManager.handlerRegistry` as a `SimpleRegistry<ObjectiveHandler>` (`QuestManager.java:27`). Keys are lowercase type IDs. Registered in `QuestModule.onEnable()` (`QuestModule.java:40-44`).

### 5.4 Quest board slots

Slot assignments are stored as profile variables, NOT in any database table. The slot key format is `questboard.<boardId>.slot.<slotNumber>` where `<slotNumber>` is 1-based. Values are quest IDs (strings). When a player collects a completed slot's rewards, the slot variable is removed and a new random quest is assigned.

### 5.5 Dialogue/conversation registration

Conversations from quest packages are registered into the `DialogueManager`'s `dialogueRegistry` (`SimpleRegistry<DialogueDefinition>`) during `QuestPackageManager.applyToManagers()` (`QuestPackageManager.java:582-583`). NPC-to-conversation bindings are applied by re-registering existing NPC definitions with `withConversation()`.

### 5.6 Player profile data model

Quest data lives on `ValmoraProfile` (from `module/profile`). The profile's `variables` map is persisted as JSON in the database. The relevant types for the quest system:

- `ValmoraPlayer` — per online player (UUID-keyed session), accessed via `PlayerManager.getSession(uuid)`
- `ValmoraProfile` — active character profile, accessed via `vp.getActiveProfile()`
- `profile.getVariables()` — `Map<String, Object>` for arbitrary key-value storage
- `profile.getTags()` — `Set<String>` for flag-style tracking (used for auto-once guard tags and conversation conditions like `tag forgotten_mine.done`)
- `profile.getStatManager()` — for `STAT_REACH` objective checks (`QuestListener.checkStatReachObjectives()`, `QuestListener.java:475-496`)

---

## 6. API Exposed

### 6.1 ValmoraAPI methods

| Method | Returns | Code location |
|--------|---------|---------------|
| `getQuestManager()` | `QuestManager` | `Valmora.java:415-417` |
| `getQuestPackageManager()` | `QuestPackageManager` | `Valmora.java:452-454` |
| `getPointsManager()` | `PointsManager` | `Valmora.java:442-444` |
| `getQuestModule()` | `QuestModule` (concrete, not in ValmoraAPI) | `Valmora.java:421` |

### 6.2 QuestModule methods

| Method | Returns |
|--------|---------|
| `getQuestManager()` | `QuestManager` |
| `getPackageManager()` | `QuestPackageManager` |
| `getJournalManager()` | `JournalManager` |
| `getPlayerHiderManager()` | `PlayerHiderManager` |
| `getQuestBoardManager()` | `QuestBoardManager` |

### 6.3 QuestManager public API

| Method | Signature | Description | Code |
|--------|-----------|-------------|------|
| `getRegistry()` | `Registry<QuestDefinition>` | Quest definition registry | `QuestManager.java:32` |
| `registerObjectiveHandler()` | `void register(ObjectiveHandler)` | Register a custom objective handler | `QuestManager.java:34-36` |
| `getStatus()` | `String getStatus(ValmoraProfile, String questId)` | Returns `not_started`/`in_progress`/`completed`/`failed` | `QuestManager.java:42-45` |
| `getProgress()` | `int getProgress(profile, questId, index)` | Legacy index-based progress | `QuestManager.java:47-50` |
| `getObjectiveProgress()` | `int getObjectiveProgress(profile, questId, objectiveId)` | Named-objective progress | `QuestManager.java:52-55` |
| `isObjectiveActive()` | `boolean isObjectiveActive(profile, objectiveId)` | Whether a standalone objective is active | `QuestManager.java:57-60` |
| `hasActiveObjectiveType()` | `boolean hasActiveObjectiveType(Player, String typeId)` | Cheap guard for event listeners | `QuestManager.java:66-76` |
| `startQuest()` | `void startQuest(Player, String questId)` | Start a quest for a player | `QuestManager.java:82-106` |
| `completeQuest()` | `void completeQuest(Player, String questId)` | Force-complete a quest | `QuestManager.java:108-114` |
| `cancelQuest()` | `void cancelQuest(Player, String questId)` | Reset to not_started | `QuestManager.java:116-121` |
| `failQuest()` | `void failQuest(Player, String questId)` | Mark as failed | `QuestManager.java:123-129` |
| `resetQuestProgress()` | `void resetQuestProgress(ValmoraProfile, String questId)` | Clear all progress, set not_started | `QuestManager.java:137-150` |
| `startObjective()` | `void startObjective(Player, String objectiveId)` | Activate a standalone objective | `QuestManager.java:156-161` |
| `deleteObjective()` | `void deleteObjective(Player, String objectiveId)` | Deactivate and clear a standalone objective | `QuestManager.java:163-167` |
| `trigger()` | `void trigger(Player, String typeId, String target, int amount)` | Core objective progression entry point | `QuestManager.java:179-228` |
| `startAutoOnceObjectivesForPlayer()` | `void startAutoOnceObjectivesForPlayer(Player)` | Activate all auto-once objectives | `QuestManager.java:234-246` |

### 6.4 QuestBoardManager public API

| Method | Description | Code |
|--------|-------------|------|
| `getRegistry()` | Returns `QuestBoardRegistry` | `QuestBoardManager.java:33` |
| `assignIfEmpty()` | Fills empty board slots with random pool quests | `QuestBoardManager.java:36-48` |
| `collect()` | Collects rewards for a completed slot and rerolls | `QuestBoardManager.java:51-77` |

### 6.5 Script DSL events

| Event | Args | Registered by | Code |
|-------|------|---------------|------|
| `quest_start <questId>` | 1 | `QuestEventFactory` | `QuestEventFactory.java:25` |
| `quest_complete <questId>` | 1 | `QuestEventFactory` | `QuestEventFactory.java:26` |
| `quest_cancel <questId>` | 1 | `QuestEventFactory` | `QuestEventFactory.java:27` |
| `quest_fail <questId>` | 1 | `QuestEventFactory` | `QuestEventFactory.java:28` |
| `objective_start <objId>` | 1 | `QuestEventFactory` | `QuestEventFactory.java:29` |
| `objective_delete <objId>` | 1 | `QuestEventFactory` | `QuestEventFactory.java:30` |
| `point <category> add/set/take <amount>` | 3 | `PointEvent` | `PointEvent.java:9-30` |
| `journal open` | 1 (optional) | `JournalEventFactory` | `JournalEventFactory.java:16-28` |
| `quest_board_assign <boardId>` | 1 | `QuestBoardEventFactory` | `QuestBoardEventFactory.java:29-37` |
| `quest_board_collect <boardId> <slot>` | 2 | `QuestBoardEventFactory` | `QuestBoardEventFactory.java:39-50` |

### 6.6 Script DSL conditions

| Condition | Syntax | Code |
|-----------|--------|------|
| Quest status | `quest <questId> <status>` | `ConditionParser.java:70-73` → `QuestStatusCondition` |
| Objective active | `objective <objectiveId>` | `ConditionParser.java:67-68` → `ObjectiveActiveCondition` |
| Point threshold | `point <category> <amount>` | `ConditionParser.java:76-82` → `PointCondition` |

### 6.7 Script variables

| Variable | Namespace | Provider | Code |
|----------|-----------|----------|------|
| `$quest.<id>.status$` | `quest` | `QuestVariableProvider` | `QuestVariableProvider.java:52` |
| `$quest.<id>.progress.<index>$` | `quest` | `QuestVariableProvider` | `QuestVariableProvider.java:55-58` |
| `$quest.<id>.objective.<objId>.progress$` | `quest` | `QuestVariableProvider` | `QuestVariableProvider.java:64` |
| `$quest.<id>.objective.<objId>.required$` | `quest` | `QuestVariableProvider` | `QuestVariableProvider.java:65-72` |
| `$quest.objective.<objId>.active$` | `quest` | `QuestVariableProvider` | `QuestVariableProvider.java:42-46` |
| `$point.<category>$` | `point` | `PointVariableProvider` | `PointVariableProvider.java:14-23` |
| `$questboard.<boardId>.slot.<n>.quest_id$` | `questboard` | `QuestBoardVariableProvider` | `QuestBoardVariableProvider.java:46` |
| `$questboard.<boardId>.slot.<n>.name$` | `questboard` | `QuestBoardVariableProvider` | `QuestBoardVariableProvider.java:48-50` |
| `$questboard.<boardId>.slot.<n>.status$` | `questboard` | `QuestBoardVariableProvider` | `QuestBoardVariableProvider.java:51` |

Also registered (not quest-module-specific):
- `$quest.<id>.status$` is also usable via the expression `$quest.<id>.status$` in `QuestStatusCondition` (condition keyword form: `quest <questId> <status>`)

### 6.8 ObjectiveHandler interface (`api/quest/ObjectiveHandler.java:19-29`)

Public interface for external plugins to register custom objective types:

```java
public interface ObjectiveHandler {
    String getTypeId();                                    // lowercase type ID
    default void onQuestStart(Player player, QuestObjective objective, QuestManager questManager) {}
}
```

Registered via `QuestManager.registerObjectiveHandler(handler)`. When a quest starts, the engine calls `onQuestStart()` for each objective whose `type` matches `getTypeId()`. Progress triggering for custom types is handled by calling `QuestManager.trigger(player, typeId, target, amount)` from any Bukkit listener.

---

## 7. Dependencies & Consumers

### 7.1 Module-level dependencies

`QuestModule` is registered after `npcModule` and `warpModule` (`Valmora.java:209-210`), and before `pointsModule` and `notifyModule` (`Valmora.java:211-212`). Its `onEnable()` accesses these services via `ValmoraAPI.getInstance()`:

- **`ScriptModule`** (`QuestModule.java:53,54,59`): Registers quest events, variable providers, board events/providers, journal event, and parses conditions/event strings via `EventParser.parseList()` and `ConditionParser.parseList()`.
- **`NpcManager`** (`QuestPackageManager.java:592-597`): Reads NPC registry for `npc_conversations` binding; re-registers NPC definitions with `withConversation()`.
- **`NotifyManager`** (`QuestPackageManager.java:586`): Loads notification categories from packages; `QuestManager` uses it for progress notifications (`QuestManager.java:311-314`).
- **`DialogueManager`** (`QuestPackageManager.java:582-583`): Registers conversation definitions from quest packages.

### 7.2 Cross-module code references

| Module/File | References quest system | How |
|---|---|---|
| `ScriptModule` / `ConditionParser` (`ConditionParser.java:1,70-82`) | Imports `PointCondition`; parses `quest `, `objective `, `point ` condition keywords | Condition strings like `"quest forgotten_mine in_progress"` |
| `ScriptModule` / `EventParser` | Event factories registered by quest module | Resolves `quest_start`, `point`, `journal`, `quest_board_assign`, `quest_board_collect` event names at runtime |
| `NpcModule` / `NpcLoader` (`NpcLoader.java:35-36`) | Comment notes `npc_conversations` now lives in quest packages | Quest packages re-register NPCs with conversation IDs |
| `NpcModule` / `NpcDefinition` (`NpcDefinition.java:138`) | `withConversation(String)` method | Called by `QuestPackageManager.applyToManagers()` to bind conversation IDs |
| `NpcModule` / `DialogueManager` (`DialogueManager.java:125-127,177-179`) | Executes quest events from conversation nodes | `EventParser.parseList().execute()` called for node events and choice events |
| `SlayerModule` | No direct quest references | Operates independently via its own YAML and event factories |
| `CollectionModule` | No direct quest references | Operates independently |
| `ProgressionModule` / `ProgressionManager` (`ProgressionManager.java:14,81-83,93,113,135,151-155`) | Uses `PointsManager` for cost payments and daily bonuses | Calls `addPoints`, `takePoints`, `getPoints` for node-level-up costs, tier unlock costs, and daily bonus grants |

### 7.3 Script system integration points

- **`EventFactory` registry** (`ScriptModule.registerEvent()`): Quest events are regular event factories. The `EventParser` (`EventParser.java:62-68`) looks up the first space-separated token as the event name in `module.getEventFactoryRegistry()`. Unknown event names produce a warning log.

- **`VariableProvider` registry** (`ScriptModule.registerProvider()`): Quest variables are registered as providers. Resolution happens in `VariableResolver` when an expression like `$quest.<id>.status$` is encountered.

- **`ConditionParser`** (`ConditionParser.java:23-85`): Quest conditions are built-in keywords, not registered factories. The parser checks the first token:
  - `"tag "` → `TagCondition`
  - `"quest "` → `QuestStatusCondition` (`QuestManager.getStatus()`)
  - `"objective "` → `ObjectiveActiveCondition` (`QuestManager.isObjectiveActive()`)
  - `"point "` → `PointCondition` (`PointsManager.getPoints()`)
  - Otherwise → `ExpressionCondition` (parsed as a formula, which can include `$quest.<id>.status$` etc.)

### 7.4 Database persistence

Quest data is NOT stored in dedicated database tables. It lives entirely in `ValmoraProfile.getVariables()` (`Map<String, Object>`), which is serialized as JSON and persisted in the `valmora_profiles` table's `player_state` column (see `docs/VALMORA_DOCUMENTATION.md` §12). The `QuestBoardManager` slot assignments and `NpcRangeObjectiveHandler` state are also stored in profile variables.

The `dataStore.savePlayer(ValmoraPlayer)` method persists all profile data (including quest variables) on player quit (`Valmora.onDisable()`, `Valmora.java:270-273`).

---

## 8. Unfinished Things / TODOs

### 8.1 Objective types with incomplete listeners

Based on `docs/QUEST_SYSTEM.md` §7 and the actual `QuestListener` code:

| Objective Type | Status | Code reference | Details |
|---|---|---|---|
| `SMELT` | **Implemented** (see `UNFINISHED_FEATURES.md` §11) | `QuestListener.java:298-313` | Uses `FURNACE_OWNER_KEY` to attribute smelts to the player who placed the furnace. Only newly-placed furnaces are tracked; pre-existing or world-generated furnaces won't attribute. |
| `ENCHANT` | **Listener implemented** but documented as pending | `QuestListener.java:218-229` | `EnchantItemEvent` fires `trigger()` for each enchantment and for `"any"`. The QUEST_SYSTEM.md doc says "the in-game listener is not yet fully implemented" but the code has a working handler. The doc is stale. |
| `BREW` | No listener | — | `QuestObjectiveTypes.BREW` constant exists but `QuestListener` has no brew event handler. Progress cannot be tracked. |
| `VARIABLE` | No listener | — | `QuestObjectiveTypes.VARIABLE` exists but there is no `VariableChangeEvent` handler in `QuestListener`. The `QuestVariableProvider` reads quest variables but does not trigger `VARIABLE` objectives. |
| `JUMP` | **Listener implemented** | `QuestListener.java:233-236` | `PlayerJumpEvent` fires `JUMP` for all jumping. Doc says "not yet implemented" but code has the handler. Stale doc. |
| `LOCATION` | **Listener implemented** | `QuestListener.java:265-276` | `PlayerMoveEvent` fires `LOCATION` on block position change. Doc says "not yet implemented" but code has the handler. Stale doc. |

The `QuestObjectiveTypes` constants (`QuestObjectiveTypes.java:21,23-24,45`) declare `BREW`, `VARIABLE`, and `TIMER` types, but:
- `BREW` has no event handler in `QuestListener` — the constant exists but nothing fires it
- `VARIABLE` has no event handler — progress cannot auto-advance (only checked on join via no mechanism)
- `TIMER` IS handled by `TimerObjectiveHandler` (registered `QuestModule.java:41`), which fires `trigger()` every second

### 8.2 Cross-package event references not fully wired

`QuestPackageManager.resolveEvent()` (`QuestPackageManager.java:607-629`) implements the `>` separator syntax for cross-package event references, but the `resolveEventRefs()` method used during parsing (`QuestPackageManager.java:510-521`) only checks local named events. Cross-package references that don't match a local event name are passed as raw inline DSL strings to the `EventParser`, which fails to find a matching `EventFactory` and logs a warning. The `resolveEvent()` public method is available via the API but is not called from the parsing pipeline.

### 8.3 Quest progress not persisted separately

Quest state lives entirely in `ValmoraProfile` variables (runtime map). There is no audit log, no quest-specific database table, and no way to query quest completion outside of loading the player's profile. The `QuestBoardManager` slot assignments are also ephemeral variables — if a player's profile data is corrupted or reset, all quest progress and board assignments are lost.

### 8.4 Notification categories only loaded from packages

The `QuestPackageManager.applyToManagers()` method loads notification categories from quest packages into `NotifyManager` (`QuestPackageManager.java:586`). However, these categories are only available during script event execution (via `notify` actions) — the `QuestManager.sendProgressNotification()` method (`QuestManager.java:308-315`) uses `NotifyManager.sendCategory()` which reads from the same `categories` map. Categories loaded by packages will be available, but there is no per-package scoping — all categories from all packages are merged into a single global map, and the last-loaded package's category with a given name wins.

### 8.5 `reward-events` field not used by QuestManager

`QuestDefinition` has a `rewardEvents` field (`QuestDefinition.java:15`) loaded from the `reward-events` YAML key (`QuestLoader.java:54`). However, `QuestManager` does NOT fire these events automatically on quest completion (`QuestManager.finishQuest()`, `QuestManager.java:269-273` just sets status and sends a message). These are only used by `QuestBoardManager.collect()` (`QuestBoardManager.java:67-70`) which explicitly fires them when a player collects board quest rewards. This is by design (documented in `QuestDefinition` Javadoc) but is a source of confusion — regular quests with `reward-events` get no rewards unless they use per-objective `events:`.

### 8.6 No admin commands for quest management

`QuestCommand` (`QuestCommand.java:10-39`) only supports `journal` (default). There are no subcommands for starting, completing, failing, or canceling quests, listing quests, or checking a player's progress. All quest manipulation must be done via script events (`quest_start`, `quest_complete`, etc.) or conversation nodes.

### 8.7 No quest cooldown or repeatable quest system

There is no built-in cooldown mechanism for quests. A player can start a quest, complete it, and immediately restart it via `quest_start` (though `QuestManager.startQuest()` `QuestManager.java:88` blocks restart if already `in_progress` or `completed`). The only workaround is the quest board's collect-and-reroll flow or `resetQuestProgress()`.

---

## 9. Possible Improvements / Changes

1. **Wire cross-package event resolution into `resolveEventRefs()`**: Modify `resolveEventRefs()` (`QuestPackageManager.java:510-521`) to detect the `>` separator and delegate to `resolveEvent()` for cross-package lookups. Currently, cross-package references in objective/conversation events silently fail as unrecognized DSL.

2. **Automatically fire `reward-events` on quest completion**: Either have `QuestManager.finishQuest()` check for and fire `rewardEvents`, or rename the field to clarify its board-only purpose. The current behavior (only `QuestBoardManager.collect()` fires them) is surprising.

3. **Add quest admin subcommands**: Extend `QuestCommand` to support `/quest start <id> [player]`, `/quest complete <id> [player]`, `/quest cancel <id> [player]`, `/quest list [player]`, `/quest info <id>`. Wire through `QuestManager` methods.

4. **Add a `BREW` listener**: Register a ` BrewEvent` or `BlockBrew` listener in `QuestListener` to fire `BREW` objectives. The constant exists in `QuestObjectiveTypes` (`QuestObjectiveTypes.java:21`) but no event handler triggers it.

5. **Add a `VARIABLE` event trigger**: Either listen for profile variable changes or provide a generic mechanism for objectives to be marked complete by conditions becoming true. The `VARIABLE` type constant exists (`QuestObjectiveTypes.java:24`) but has no listener.

6. **Quest cooldown / repeatable system**: Add optional `cooldown:` and `repeatable: true` fields to quest definitions. After completion, a quest with `repeatable: true` would enter a cooldown period before it can be restarted. Store last-completion timestamp in profile variables.

7. **Separate quest data persistence**: Consider a dedicated quest data table for queryability and data safety, rather than relying on profile variable JSON. This would allow admin tools, analytics, and easier data recovery.

8. **Per-objective completion messages**: Currently only `QuestManager.finishQuest()` sends a completion message. Objective-level `notify:` controls progress notifications but there's no "objective complete" message. Consider adding an `on-complete-notify:` field to objectives.

9. **Quest board auto-assign on join**: `QuestBoardManager.assignIfEmpty()` is not called automatically on player join. Players must interact with a quest board NPC first (via `quest_board_assign` event). Consider auto-assigning for players with no active boards.

10. **Stale documentation cross-check**: `docs/QUEST_SYSTEM.md` §7 claims `BREW`, `ENCHANT`, `JUMP`, `LOCATION`, and `VARIABLE` listeners are "not yet implemented," but `QuestListener.java` has handlers for `ENCHANT` (line 218), `JUMP` (line 233), and `LOCATION` (line 265). The doc should be corrected. `BREW` and `VARIABLE` are genuinely missing listeners.

---

*References: `docs/MODULE_DEVELOPMENT.md` §6 (Module System), `docs/VALMORA_DOCUMENTATION.md` §33 (Script Event DSL), `docs/QUEST_SYSTEM.md` (Quest System Author's Reference), `docs/UNFINISHED_FEATURES.md` §11 (SMELT objective).*
