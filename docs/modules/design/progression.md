# Progression Module — Design & Code

> **Module ID:** `progression` | **Display name:** "Progression System" | **Package:** `org.nakii.valmora.module.progression`
> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21

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

The Progression module is Valmora's **generic skill-tree / skill-point engine**. It is deliberately *not* tied to the fixed skill system (`skill` module): instead of XP-per-skill levels, a server admin defines **talent trees** as pure YAML, and players spend **points** (see the `points` module) to:

- **unlock tiers** of a tree (gatekeeping groups of nodes) with a tier currency, and
- **level up nodes** inside an unlocked tier (each node can have multiple levels) with a separate level currency.

Every tree is a single YAML file in `plugins/Valmora/progression/*.yml`, parsed by the shared `YamlLoader` with **zero Java changes required to add a new tree** (see the comment in `src/main/resources/progression/geomancy.yml:1-6`). The shipped tree is **Geomancy** (`progression/geomancy.yml`), the mining progression tree for the Shardworks demo server.

Key design decisions:

- **Two independent currencies per tree.** A `level-currency` (paid per node level-up) and a `tier-currency` (paid per tier unlock). Both are free-form `points` categories (`PointsManager`), so "Ferrite Powder" and "Geomancy Tokens" here are just examples.
- **Everything is stored in the profile's generic variables map** (`ValmoraProfile.getVariables()`), keyed under a `progression.<tree>.<...>` prefix. No dedicated DB table — the same JSON `variables` column that powers points, tags, and scripts persists all tree state.
- **Node progression is a flat level counter per node** (`1..max-level`), with a per-node **cost-curve expression** (evaluated with `$level$` substituted) that computes the cost of the *next* level.
- **Stat bonuses are derived, not stored.** `ProgressionStatService` re-computes every node's `stat-bonus` from the current level during each `StatManager.recalculateStats` pass — so a tree reset needs no bookkeeping to undo stats.
- **Daily bonus nodes** are supported: a node with a `daily-bonus` grants its currency once per rolling 24 hours, polled by a main-thread repeating task every 5 minutes.
- **Scripting-first integration.** The module exposes three DSL events (`progression_levelup`, `progression_unlock_tier`, `progression_reset`) and a `$progression.*$` variable namespace to the script engine, which is how the Geomancy GUI (`guis/geomancy_tree.yml`) actually spends points and renders node states.

The module is registered **last** in `Valmora.onEnable()` (`src/main/java/org/nakii/valmora/Valmora.java:222`), commented `// Depends on scriptModule + pointsModule (generic tree/skill-point engine)`.

---

## 2. Code Structure

```
src/main/java/org/nakii/valmora/module/progression/
├── ProgressionModule.java        # ReloadableModule lifecycle + daily-bonus polling task
├── ProgressionManager.java       # Query/mutation engine (levels, tiers, reset, cost curves)
├── ProgressionLoader.java        # YAML → ProgressionTreeDefinition parsing
├── ProgressionRegistry.java      # SimpleRegistry<ProgressionTreeDefinition> facade
├── ProgressionTreeDefinition.java# Tree model (currencies, tiers, nodes)
├── ProgressionNode.java          # Node model + nested StatBonus / DailyBonus records
├── ProgressionTier.java          # Tier model
├── ProgressionStatService.java   # Stat bonus aggregator (called by StatManager)
├── ProgressionVariableProvider.java  # $progression.*$ script variables
├── ProgressionEventFactory.java  # progression_levelup / unlock_tier / reset DSL events
└── event/                        # Custom Bukkit events fired on mutations
    ├── ProgressionNodeLevelUpEvent.java
    ├── ProgressionTierUnlockedEvent.java
    └── ProgressionTreeResetEvent.java
```

> **Note on `nodes/`:** the module has **no** `nodes/` sub-package. "Nodes" are modeled by the single `ProgressionNode` class (with nested `StatBonus` / `DailyBonus` records). The only sub-package is `event/`, holding the three custom Bukkit events. There is also **no** `XListener` class — the module registers no Bukkit listeners.

The module follows the AGENTS.md `XModule` / `XRegistry` / `XLoader` convention, but `ProgressionStatService` and the two scripting adapters are standalone classes.

### 2.1 Wiring in `Valmora.java`

| Location | What happens |
| --- | --- |
| `Valmora.java:123` | Field `private ProgressionModule progressionModule;` |
| `Valmora.java:184` | Instantiated in `onEnable()`: `this.progressionModule = new ProgressionModule(this);` |
| `Valmora.java:222` | Registered with `moduleManager.registerModule(progressionModule);` — **last** module, after `quiver` (`:221`) |
| `Valmora.java:432-435` | `getProgressionManager()` API override (null-safe) |
| `Valmora.java:437-439` | `getProgressionModule()` (concrete getter, not on the interface) |

The `progression/` resource folder is included in the auto-save whitelist in `Valmora.saveAllResources()` (`Valmora.java:478`).

---

## 3. Architecture & Key Classes

### 3.1 Lifecycle — `ProgressionModule`

`ProgressionModule` (`ProgressionModule.java:13`) implements `ReloadableModule`.

- **Constants** (`:15-16`):
  - `DAILY_BONUS_WINDOW_MILLIS = 24L * 60 * 60 * 1000` — rolling 24 h between daily-bonus claims.
  - `DAILY_CHECK_INTERVAL_TICKS = 20L * 60 * 5` (6000 ticks = 5 min) — polling cadence of the daily-bonus task.
- **Constructor** (`:24-28`) stores the plugin reference and builds the registry + loader. All state is created in `onEnable()` (per AGENTS.md §6.1).
- **`onEnable()`** (`:31-41`):
  1. Builds a fresh `ProgressionManager`.
  2. Calls `loader.load()`.
  3. Registers `ProgressionVariableProvider` and all three `ProgressionEventFactory` events with the script module (`:36-37`).
  4. Schedules the daily-bonus task with `runTaskTimer` on the **main thread** (`:39-40`).
- **`onDisable()`** (`:44-49`): cancels the daily task, clears the registry, nulls the manager. **Does not** unregister the script provider/events (see §8).
- **`getId()`** → `"progression"`, **`getName()`** → `"Progression System"` (`:51-52`).
- Getters: `getProgressionManager()` (`:54`), `getProgressionRegistry()` (`:55`).

#### Daily-bonus polling — `processDailyBonuses()` (`:57-90`)

Every 5 minutes, for every online player:

1. Resolve the session's active profile (`:61-64`); skip if absent.
2. For every tree in the registry, for every node carrying a `daily-bonus` (`:66-69`):
   - Skip if the node level is `<= 0` (`:71-72`).
   - Read `progression.<treeId>.<nodeId>.last_daily_claim` from the profile variables (`:74-77`).
   - If `now - lastClaim < 24h`, skip (`:79`).
   - Otherwise grant `round(perLevel * level)` points in the bonus `category` via `PointsManager.addPoints` (`:81-85`), then stamp the claim time (`:86`).

This is a **rolling** window (24 h since last claim), not a fixed server-day reset.

### 3.2 Query & mutation engine — `ProgressionManager`

`ProgressionManager` (`ProgressionManager.java:19`) holds the plugin + registry and implements all tree logic. It reads/writes exclusively through `ValmoraProfile.getVariables()`.

**Queries:**

| Method | Lines | Behavior |
| --- | --- | --- |
| `getNodeLevel(UUID, treeId, nodeId)` | `:35-40` | Reads `progression.<tree>.<node>.level`; 0 if absent or no profile. |
| `getUnlockedTier(UUID, treeId)` | `:42-47` | Reads `progression.<tree>.tier`; 0 if absent. |
| `getNodeCost(treeId, nodeId, level)` | `:50-54` | Evaluates the cost-curve at `level` (cost to go `level → level+1`); `Integer.MAX_VALUE` if the node doesn't exist. |
| `isNodeUnlocked(UUID, treeId, nodeId)` | `:56-68` | Requires tree/node to exist, unlocked tier `>=` node's `tierIndex`, and **every** prerequisite node at level `> 0`. |
| `canLevelUp(Player, treeId, nodeId)` | `:70-83` | Node exists, current level `< maxLevel`, node unlocked, and player's `level-currency` points `>=` current-level cost. |
| `canUnlockTier(Player, treeId)` | `:85-94` | Next tier exists and player's `tier-currency` points `>=` the tier's `unlock-cost`. |

**Mutations:**

| Method | Lines | Behavior |
| --- | --- | --- |
| `levelUp(Player, treeId, nodeId)` | `:100-120` | Guards with `canLevelUp`, deducts the curve cost from the `level-currency` category, records it in `spent`, writes `level + 1`, fires `ProgressionNodeLevelUpEvent`. |
| `unlockTier(Player, treeId)` | `:122-141` | Guards with `canUnlockTier`, deducts the tier `unlock-cost` from the `tier-currency` category, records it in `spent`, writes the new tier index, fires `ProgressionTierUnlockedEvent`. |
| `resetTree(Player, treeId)` | `:144-166` | **Refunds everything**: both `spent` totals are returned via `PointsManager.addPoints`, then all `spent`, `tier`, and per-node `level` keys are removed, then `ProgressionTreeResetEvent` fires. Refund is always 100%. |

**Internals:**

- `addSpent` / `getSpent` (`:172-181`) — tracks per-currency totals under `progression.<tree>.spent.<currency>` so reset can refund exactly.
- `spentKey` (`:183-185`) — `progression.<tree>.spent.<currency>`.
- `varKey` (`:187-189`) — `progression.<tree>.<node>.<field>`.
- `evaluateCostCurve(costCurve, level)` (`:191-197`) — replaces every `$level$` with the current level, parses the string with `ScriptModule.getExpressionParser().parse(...)`, evaluates it against a bare `SimpleExecutionContext(null, null, null, new MemoryConfiguration())`, and returns `max(0, intValue)` (or 0 for a non-number). Supported math functions include `floor`, `ceil`, `round`, `abs`, `sqrt`, `log10`, `log`, `pow`, `min`, `max` (`FunctionNode.java:21-33`).
- `getProfile(UUID)` (`:199-202`) — resolves the active profile via `ValmoraAPI.getInstance().getPlayerManager().getSession(uuid)`.

### 3.3 Tree definitions

- **`ProgressionTreeDefinition`** (`ProgressionTreeDefinition.java:7`) — `id`, `displayName`, `description`, `levelCurrencyCategory`, `tierCurrencyCategory`, `tiers`, `nodes`. Helpers:
  - `getNode(nodeId)` (`:36-38`) — case-insensitive lookup into the node map.
  - `getTier(index)` (`:40-42`) — finds the tier by integer index.
  - `getMaxTierIndex()` (`:44-46`).
- **`ProgressionTier`** (`ProgressionTier.java:5`) — `index`, `displayName`, `unlockCost`, `nodeIds` (the node list is informational — node gating actually works through each node's own `tier` field plus `getUnlockedTier`).
- **`ProgressionNode`** (`ProgressionNode.java:7`) — `id`, `displayName`, `description`, `icon` (`Material`), `tierIndex`, `maxLevel`, `costCurve`, `prerequisiteNodeIds`, `statBonus`, `dailyBonus`. Nested records:
  - `StatBonus(String stat, double perLevel)` (`:45`).
  - `DailyBonus(String category, double perLevel)` (`:46`).

### 3.4 Registry — `ProgressionRegistry`

`ProgressionRegistry` (`ProgressionRegistry.java:7`) extends the thread-safe, case-insensitive `SimpleRegistry<ProgressionTreeDefinition>` (`SimpleRegistry.java:15`). Adds `registerTree(...)` (`:9-11`) and `getTree(id)` (`:13-15`).

### 3.5 Stat integration — `ProgressionStatService`

`ProgressionStatService` (`ProgressionStatService.java:12`) is a **stateless static helper** (`applyTo(Player, StatManager)`, `:16-31`):

- Iterates every registered tree and node carrying a `StatBonus`.
- For nodes with `level > 0`, adds `bonus.perLevel() * level` via `statManager.addModifier(bonus.stat(), ...)`.

It is invoked as **step 9 of the stat recalculation pipeline** in `StatManager.recalculateStats` (`StatManager.java:167`), alongside set bonuses and temporary stats. Because the bonus is purely a function of the current level, a reset needs no inverse logic — the next recalc simply contributes nothing.

### 3.6 Scripting — variable provider & DSL events

**`ProgressionVariableProvider`** (`ProgressionVariableProvider.java:12`) — namespace `progression`, resolved only when the context has a `Player` caster:

| Variable | Lines | Meaning |
| --- | --- | --- |
| `$progression.<tree>.tier$` | `:30-32` | Current unlocked tier index. |
| `$progression.<tree>.tier.next$` | `:34-40` | Unlock cost of the next tier (alias). |
| `$progression.<tree>.tier.next.unlock_cost$` | `:42-49` | Same, explicit spelling. |
| `$progression.<tree>.<node>.level$` | `:55-56` | Current node level. |
| `$progression.<tree>.<node>.max_level$` | `:57-60` | Node's `max-level`. |
| `$progression.<tree>.<node>.next_cost$` | `:61-62` | Cost of the next level (`getNodeCost` at the current level). |
| `$progression.<tree>.<node>.unlocked$` | `:63` | `isNodeUnlocked` boolean. |

**`ProgressionEventFactory`** (`ProgressionEventFactory.java:19`) — three DSL events registered into the script engine (`ProgressionModule.java:37`):

| DSL | Lines | Effect |
| --- | --- | --- |
| `progression_levelup <treeId> <nodeId>` | `:25-38` | `manager.levelUp(player, treeId, nodeId)` for the caster player. No-op if fewer than 2 args. |
| `progression_unlock_tier <treeId>` | `:40-52` | `manager.unlockTier(player, treeId)`. |
| `progression_reset <treeId>` | `:54-66` | `manager.resetTree(player, treeId)`. |

These are usable anywhere the event DSL runs — GUI click actions, NPC/quest reward scripts — and the **Geomancy GUI** (`guis/geomancy_tree.yml`) is the primary consumer.

### 3.7 Custom Bukkit events

All three are plain synchronous `Event` subclasses (not cancellable), fired on the main thread **after** the mutation is applied:

- `ProgressionNodeLevelUpEvent` — `player`, `treeId`, `nodeId`, `newLevel` (`event/ProgressionNodeLevelUpEvent.java:7-25`), fired at `ProgressionManager.java:119`.
- `ProgressionTierUnlockedEvent` — `player`, `treeId`, `tierIndex` (`event/ProgressionTierUnlockedEvent.java:7-22`), fired at `ProgressionManager.java:140`.
- `ProgressionTreeResetEvent` — `player`, `treeId` (`event/ProgressionTreeResetEvent.java:7-19`), fired at `ProgressionManager.java:165`.

---

## 4. Configuration (YAML)

Trees live in `plugins/Valmora/progression/*.yml` (auto-saved from `src/main/resources/progression/`; see `Valmora.java:478`). Loading is done by `ProgressionLoader` via the shared `YamlLoader` (`ProgressionLoader.java:26`: `new YamlLoader<ProgressionTreeDefinition>(plugin, "progression", "Progression Trees")`). Each top-level YAML key is a tree id; every tree id is registered **lowercased** (`SimpleRegistry.register`, `SimpleRegistry.java:21`).

Only one tree ships: `geomancy.yml`.

### 4.1 Tree-level keys

| Key | Type | Default | Lines | Description |
| --- | --- | --- | --- | --- |
| `name` | string (MiniMessage) | tree id | `ProgressionLoader.java:32` | Display name, e.g. `<gold>Geomancy`. |
| `description` | string | `""` | `:33` | Free-form description. |
| `level-currency` | string | **required** | `:34`, `:36-38` | Points category deducted per node level-up. Missing ⇒ load failure (`LoadResult.failure`). |
| `tier-currency` | string | **required** | `:35`, `:36-38` | Points category deducted per tier unlock. Missing ⇒ load failure. |
| `tiers` | map of `<index>: <tier>` | absent | `:41-54` | Ordered tier definitions. Tier keys must be integers; non-integer keys are skipped (`:46-48`). |
| `nodes` | map of `<nodeId>: <node>` | absent | `:57-92` | All nodes in the tree. |

### 4.2 Tier keys (`tiers.<index>`)

| Key | Type | Default | Lines | Description |
| --- | --- | --- | --- | --- |
| `name` | string | `"Tier <index>"` | `:49` | Tier display name. |
| `unlock-cost` | int | `0` | `:50` | Amount of `tier-currency` to pay to unlock this tier. |
| `nodes` | list of string | absent | `:51` | Node ids in this tier (informational; actual gating uses each node's `tier` field). |

### 4.3 Node keys (`nodes.<nodeId>`)

| Key | Type | Default | Lines | Description |
| --- | --- | --- | --- | --- |
| `name` | string (MiniMessage) | node id | `:63` | Node display name. |
| `description` | string | `""` | `:64` | Node description (shown in GUI lore). |
| `icon` | Material name | `BOOK` | `:65-66` | GUI icon material. Unparseable names fall back to `BOOK`. |
| `tier` | int | `0` | `:67` | Which tier must be unlocked before this node can be levelled. |
| `max-level` | int | `1` | `:68` | Maximum node level. |
| `cost-curve` | string expression | `"1"` | `:69` | Cost of the next level; `$level$` is substituted with the current level. E.g. `floor(5 * pow(1.12, $level$))`. See `FunctionNode.java:21-33` for supported functions. |
| `prerequisites` | list of string | absent | `:70` | Node ids that must be at level `>= 1` before this node is unlocked. |
| `stat-bonus` | map | absent | `:72-78` | Passive stat granted while the node has levels. |
| `daily-bonus` | map | absent | `:80-86` | Grants currency once per rolling 24 h. |

### 4.4 `stat-bonus` keys (`nodes.<nodeId>.stat-bonus`)

| Key | Type | Default | Lines | Description |
| --- | --- | --- | --- | --- |
| `stat` | string | `""` | `:76` | Stat id (lowercased). Must match a registered stat (see `stats/core.yml`). |
| `per-level` | double | `0.0` | `:77` | Bonus amount **per node level**. Total = `per-level * level`, applied via `StatManager.addModifier` (`ProgressionStatService.java:28`). |

### 4.5 `daily-bonus` keys (`nodes.<nodeId>.daily-bonus`)

| Key | Type | Default | Lines | Description |
| --- | --- | --- | --- | --- |
| `category` | string | `""` | `:84` | Points category granted (lowercased). |
| `per-level` | double | `0.0` | `:85` | Amount **per node level** per claim. Grant = `round(per-level * level)`. |

### 4.6 The shipped tree — `geomancy.yml`

File: `src/main/resources/progression/geomancy.yml` (111 lines).

- Tree id: `geomancy`; `name: "<gold>Geomancy"`; `description` about mastering Shardworks living stone (`:8-10`).
- **Currencies:** `level-currency: ferrite_powder`, `tier-currency: geomancy_tokens` (`:11-12`).
- **Tiers** (`:14-30`):

| Index | Name | Unlock cost | Nodes |
| --- | --- | --- | --- |
| 0 | Novice | 0 | `mining_speed_root` |
| 1 | Adept | 3 | `mining_fortune_vein`, `mining_spread_focus` |
| 2 | Master | 5 | `deep_prospecting`, `daily_ferrite_cache`, `mining_fortune_vein_ii` |
| 3 | Ascendant | 8 | `aetherial_resonance` |

- **Nodes** (`:32-111`):

| Node | Tier | Max lvl | Cost curve | Bonus | Prereqs |
| --- | --- | --- | --- | --- | --- |
| `mining_speed_root` ("Steady Hands") | 0 | 10 | `floor(5 * pow(1.12, $level$))` | stat `mining_speed` +4/level | — |
| `mining_fortune_vein` ("Rich Veins") | 1 | 10 | `floor(8 * pow(1.15, $level$))` | stat `mining_fortune` +3/level | — |
| `mining_spread_focus` ("Wide Excavation") | 1 | 5 | `floor(10 * pow(1.2, $level$))` | stat `mining_spread` +1/level | — |
| `deep_prospecting` ("Deep Prospecting") | 2 | 5 | `floor(20 * pow(1.18, $level$))` | stat `mining_fortune` +5/level | `mining_fortune_vein` |
| `daily_ferrite_cache` ("Ferrite Cache") | 2 | 5 | `floor(15 * pow(1.15, $level$))` | daily `ferrite_powder` +25/level | — |
| `mining_fortune_vein_ii` ("Overflowing Veins") | 2 | 10 | `floor(25 * pow(1.15, $level$))` | stat `mining_fortune` +4/level | `mining_fortune_vein` |
| `aetherial_resonance` ("Aetherial Resonance") | 3 | 3 | `floor(50 * pow(1.25, $level$))` | stat `mining_spread` +1/level | `mining_spread_focus` |

The three stat targets (`mining_speed`, `mining_fortune`, `mining_spread`) are defined in `src/main/resources/stats/core.yml:85-99` and `:205-211`. The tree's real GUI consumer is `guis/geomancy_tree.yml` (command `/geomancy`, `:8`), which reads node/tier state via the `$progression.*$` variables, renders `maxed` / `available` / `locked` / `tier-locked` / `prereq-locked` states, and triggers the `progression_*` events on click.

---

## 5. Data Model / Persistence

No dedicated tables. All progression state is stored **inside the player profile's generic `variables` map** (`ValmoraProfile.getVariables()`, `ValmoraProfile.java:82-84`), which is serialized as a JSON blob in the `variables` column of `valmora_profiles` and round-tripped through Gson in `SQLDataStore.java` (column definition `:140`, read `:227-230`, write `:300`, `:309`, `:322`).

**Variable keys written by this module** (`ProgressionManager.java:183-189`):

| Key | Written by | Purpose |
| --- | --- | --- |
| `progression.<tree>.<node>.level` | `levelUp` (`:117`), removed by `resetTree` (`:162`) | Node level (int). |
| `progression.<tree>.tier` | `unlockTier` (`:138`), removed by `resetTree` (`:160`) | Currently unlocked tier index (int). |
| `progression.<tree>.spent.<currency>` | `addSpent` (`:172-176`) | Total spent per currency (for refunds). Removed by `resetTree` (`:158-159`). |
| `progression.<tree>.<node>.last_daily_claim` | daily task (`ProgressionModule.java:74`, `:86`) | Last daily-bonus claim epoch millis. |

Currencies themselves are also profile variables, owned by `PointsManager` under the `point.` prefix (`PointsManager.java:12`, `:17`).

Sessions are resolved through `PlayerManager.getSession(uuid)`; **offline players have no session, so all mutation/query paths return defaults (0 / false / MAX_VALUE) when the player is offline** (`ProgressionManager.getProfile`, `:199-202`). Since `levelUp`, `unlockTier`, and `resetTree` are only invoked from script events with a player caster, this is currently a non-issue.

There is **no** caching layer and **no** rollback/transactional behavior — a mutation writes directly to the in-memory profile map, and persistence happens at player-save time (as with all profile variables).

---

## 6. API Exposed

### 6.1 `ValmoraAPI`

- `getProgressionManager()` (`ValmoraAPI.java:69`) → `ProgressionManager` (implementation `Valmora.java:432-435`, null-safe when module disabled).

### 6.2 `ProgressionModule` (concrete)

- `getProgressionManager()` (`ProgressionModule.java:54`).
- `getProgressionRegistry()` (`:55`).

### 6.3 `ProgressionManager` public surface

- `getRegistry()` (`ProgressionManager.java:29`).
- Queries: `getNodeLevel`, `getUnlockedTier`, `getNodeCost`, `isNodeUnlocked`, `canLevelUp`, `canUnlockTier`.
- Mutations: `levelUp`, `unlockTier`, `resetTree`.

### 6.4 `ProgressionRegistry`

- `registerTree(def)`, `getTree(id)`, plus inherited `SimpleRegistry` API (`values()`, `getKeys()`, `size()`, `clear()`, case-insensitive).

### 6.5 Scripting surface

- Variable namespace `progression` (see §3.6).
- Events: `progression_levelup`, `progression_unlock_tier`, `progression_reset`.
- Bukkit events: `ProgressionNodeLevelUpEvent`, `ProgressionTierUnlockedEvent`, `ProgressionTreeResetEvent`.

---

## 7. Dependencies & Consumers

### 7.1 Dependencies (modules this module relies on)

| Module | Where | How it's used |
| --- | --- | --- |
| `script` | `ProgressionModule.java:36-37`, `ProgressionManager.java:193` | Registers the variable provider + event factories; evaluates `cost-curve` expressions via `getExpressionParser().parse(...)`. |
| `points` | `ProgressionManager.java:81-82, 92-93, 112-113, 134-135, 151-156`; `ProgressionModule.java:81-84` | `PointsManager.getPoints / addPoints / takePoints` for both currencies and daily bonuses, via `ValmoraAPI.getInstance().getPointsManager()`. |
| `player` | `ProgressionManager.java:199-202`, `ProgressionModule.java:61-64` | `PlayerManager.getSession` → `ValmoraProfile` (variables container). |
| `stat` | `StatManager.java:167` → `ProgressionStatService` | Consumed by, not from — the stat recalc pipeline pulls tree bonuses in. |
| `gui` | `guis/geomancy_tree.yml` | Primary UI consumer (command `/geomancy` registered by the GUI module from the YAML `command:` key, `GuiModule.java:200-221`). |

Load order (all earlier): `script` → `points` → `progression`. Progression is registered **after** `pointsModule` (`Valmora.java:211`) and everything else — it is the final entry at `Valmora.java:222`. The registration comment in `Valmora.java:222` records the dependency explicitly: *"Depends on scriptModule + pointsModule (generic tree/skill-point engine)"*.

### 7.2 Consumers (external users of this module)

- **Stat pipeline** — `StatManager.recalculateStats` calls `ProgressionStatService.applyTo` (`StatManager.java:167`).
- **Resource module** — reads the `mining_speed` / `mining_fortune` / `mining_spread` stats that Geomancy grants (see `docs/modules/design/resource.md:286`).
- **GUI module** — `guis/geomancy_tree.yml` renders and spends points.
- **Quest rewards** — `quests/shardworks_quests.yml:17-18, 29-30, 41-42, 52-53, 68-69` grant `ferrite_powder` / `geomancy_tokens` via `point <category> add <n>`, feeding the Geomancy currencies.

---

## 8. Unfinished Things / TODOs

- **No dedicated command.** There is no `/progression` player or admin command anywhere in `Valmora.java`. The tree is only reachable through the GUI-registered `/geomancy` command (`guis/geomancy_tree.yml:8`). No permission node exists for the progression module itself.
- **No `XListener`.** The module registers no Bukkit listeners; the daily bonus relies solely on the 5-minute poll. A `PlayerJoinEvent` listener could grant/catch up daily bonuses on login instead of waiting up to 5 minutes.
- **Script registrations are not unregistered in `onDisable()`** (`ProgressionModule.java:44-49`). Cleanup currently depends on `ScriptModule.onDisable()` clearing its whole registry (`ScriptModule.java:82-86`), which happens during a full `/valmora reload` because disable order is reverse registration (progression disables before script). A partial/standalone disable of only this module would leave the provider and event factories registered.
- **Tree-id case sensitivity is unvalidated.** The registry lowercases ids (`SimpleRegistry.java:21`), but the variable/level keys embed the **original-case** tree id (`ProgressionManager.java:183-189`). A YAML tree key with uppercase letters (e.g. `Geomancy`) would resolve in `getTree(...)` but store/read state under mismatched variable keys. The shipped file uses lowercase keys, but nothing enforces it.
- **`cost-curve` errors are not guarded.** `evaluateCostCurve` (`ProgressionManager.java:191-197`) has no try/catch: a malformed expression would throw out of `levelUp` / `getNodeCost`. The loader only validates *structure*, not the curve syntax.
- **No player-facing feedback.** Level-ups, tier unlocks, and resets fire Bukkit events but send no chat/action-bar/title feedback and make no use of the `notify` module.
- **No tests.** `src/test` contains no coverage for `ProgressionManager`, the loader, or the variable provider.
- **No per-node scripting callbacks.** Node level-up has no built-in `on-level` action list in the node YAML; only the (non-cancellable) Bukkit event is available. Servers must write GUI-level or listener-level logic.
- **Refund is always 100% and not configurable** (`ProgressionManager.java:144-166`).
- **Tier list on a node is informational only** — `ProgressionTier.nodeIds` is never used for gating; gating is derived from each node's `tier` field (`ProgressionManager.java:62`).

---

## 9. Possible Improvements / Changes

- **Dedicated command surface** — add `/progression` (player: open a tree list GUI) and admin subcommands (give/take tree currencies, view/reset a player's tree, force a daily-bonus grant), with a `valmora.admin`-style permission.
- **Generate the tree GUI from the definition.** Replace the hand-laid-out fixed grid in `guis/geomancy_tree.yml` with an auto-generated paginated/dispositioned renderer driven by `ProgressionTreeDefinition` (tiers as rows, prerequisites as edges). This would make new trees free-form instead of requiring a bespoke GUI.
- **Register/unregister script adapters explicitly** in `onDisable()` (`ScriptModule` unregister methods) to make the module independently reload-safe.
- **Validate and normalize tree ids** at load time (reject or lowercase them) to prevent variable-key drift (§8).
- **Add a try/catch + warning around `evaluateCostCurve`** so one bad curve degrades rather than crashes the mutation path.
- **Real date-based daily resets** — grant daily bonuses on a fixed server day boundary (via the `time` module's calendar/date system) instead of a rolling 24 h window.
- **Configurable refund percentage** per tree (`reset-refund: 0.75`, etc.).
- **Node-level actions & conditions** in YAML (`on-level-up` scripts) wired through the existing script engine, plus integration with `notify` for level-up/tier-unlock announcements.
- **Extra node gating hooks** — e.g. require a player `skill` level, a stat threshold, or points in a third category before a node unlocks.
- **Offline support** — resolve points/variables for offline profiles so admin commands and async scripts can mutate tree state without a session.
- **Tests** for `ProgressionLoader` parsing (defaults, missing currencies, bad tiers), `ProgressionManager` cost curves and refund math, and the variable provider, following the `ExpressionTest` mock pattern from AGENTS.md §9.

---

_Last updated: see git history._
