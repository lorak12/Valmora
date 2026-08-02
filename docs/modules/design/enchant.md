# Enchant Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `enchants` | **Source:** `src/main/java/org/nakii/valmora/module/enchant/`

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

The Enchant module is Valmora's **custom RPG enchantment system**. It is a data-driven `ReloadableModule` that loads **enchantment definitions** from `plugins/Valmora/enchants/*.yml`, stores applied enchantments on items in the item's **PersistentDataContainer (PDC)**, renders them as an **enchanted glint** plus **lore block**, and feeds their runtime effects into two other subsystems:

- the **Stat system** (passive stat bonuses while equipped/held, via `StatManager.recalculateStats()`), and
- the **Combat damage pipeline** (pre-hit damage modifiers and post-hit hooks, via `DamageCalculator`).

It is deliberately **stateless as a module** — there is no event listener, no scheduled task, and no database table. All state lives on the `ItemStack` itself (PDC), so enchantments travel with the item through inventories, drops, trades, and GUIs exactly like item attributes do. The module's only jobs are: parse YAML into `EnchantmentDefinition`s, expose a registry of them, and register the `EnchantmentLogic` handlers that other modules invoke.

The module also exposes the GUI-facing plumbing used by the **Enchanting Table GUI** (`guis/enchanting.yml`, `machine: enchanting_table`) and the **Anvil merge** (`AnvilMachineHandler`). Those GUIs are owned by the `gui` and `recipe` modules respectively, but they call back into `EnchantmentHelper` and the `EnchantModule` registry.

Per the module load order (`docs/MODULE_DEVELOPMENT.md` §9), `EnchantModule` is registered **after** `gui` and `recipe`, and **depends on** the Items and GUI systems (`Valmora.java:204`):

```
... → gui → recipe → alchemy → enchant → zone → ...
```

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/enchant/
├── EnchantModule.java              # ReloadableModule — lifecycle, builtin logic registry, YAML parser
├── EnchantmentHelper.java          # Static utility — PDC serialization, apply/remove, lore rendering, books
├── EnchantmentDefinition.java      # Immutable enchant definition POJO
├── EnchantmentLogic.java           # Logic hook interface (5 default no-op methods)
├── EnchantmentRegistry.java        # SimpleRegistry<EnchantmentDefinition> subclass
└── logic/
    ├── SharpnessLogic.java         # +5% melee damage per level (pre-hit multiplier)
    ├── GrowthLogic.java            # +10 max health per level (passive, players only)
    ├── FortuneLogic.java           # +10 Mining Fortune per level (passive, players only)
    ├── EfficiencyLogic.java        # +50 Mining Speed per level (passive, players only)
    ├── StatBonusLogic.java         # Generic: +per-level of any stat (parameterized)
    ├── DamageMultiplierLogic.java  # Generic: +% damage of a DamageType per level (parameterized)
    └── DefenseReductionLogic.java  # Generic: reduce victim defense by % per level (parameterized)

src/main/resources/enchants/
└── example_enchantments.yml        # Shipped example file (10 enchant definitions)

src/main/resources/guis/
└── enchanting.yml                  # Enchanting Table GUI definition (consumer, not module code)
```

There are **no unit tests** for the enchant module — `src/test/java/org/nakii/valmora/module/enchant/` does not exist. The only test touching enchant code is `src/test/java/org/nakii/valmora/module/combat/DamageCalculatorTest.java`, which mocks `EnchantModule`/`EnchantmentRegistry` (`DamageCalculatorTest.java:41-54`, `:89`) and feeds a fake PDC enchant string `"sharpness:5"` (`DamageCalculatorTest.java:196-197`).

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `EnchantModule.java`

Implements `ReloadableModule` (see `docs/MODULE_DEVELOPMENT.md` §2).

| Method | Behavior | Lines |
|---|---|---|
| `onEnable()` | Calls `registerBuiltinLogics()` then `loadEnchants()` | `EnchantModule.java:41-45` |
| `onDisable()` | Clears the registry and both logic maps | `EnchantModule.java:62-67` |
| `getId()` | `"enchants"` | `EnchantModule.java:69-72` |
| `getName()` | `"Enchant System"` | `EnchantModule.java:74-77` |
| `getRegistry()` | Returns the live `EnchantmentRegistry` | `EnchantModule.java:79-81` |
| `getLogic(String id)` | Returns a registered `EnchantmentLogic` by lowercase id, or `null` | `EnchantModule.java:83-85` |
| `registerLogic(String id, EnchantmentLogic)` | Registers an external logic handler (lowercased key) | `EnchantModule.java:87-89` |

Note the constructor (`EnchantModule.java:34-39`) allocates the registry and the two logic maps but **does not populate them** — population happens in `onEnable()`, preserving the hot-reload contract. `logicMap` and `logicFactories` are `ConcurrentHashMap`s (`EnchantModule.java:31-32`).

**`registerBuiltinLogics()`** (`EnchantModule.java:47-60`) registers two kinds of handlers:

1. **Direct instances** (no parameters):
   - `valmora:sharpness` → `new SharpnessLogic()`
   - `valmora:growth` → `new GrowthLogic()`
   - `valmora:fortune` → `new FortuneLogic()`
   - `valmora:efficiency` → `new EfficiencyLogic()`
2. **Parameterized factories** (`Function<ConfigurationSection, EnchantmentLogic>`), which read their tuning from the enchant's `logic-params` YAML section:
   - `valmora:stat_bonus` → `new StatBonusLogic(params.getString("stat", "strength"), params.getDouble("per-level", 1.0))`
   - `valmora:damage_multiplier` → `new DamageMultiplierLogic(params.getString("type", "MELEE"), params.getDouble("percent-per-level", 5.0))`
   - `valmora:defense_reduction` → `new DefenseReductionLogic(params.getDouble("percent-per-level", 3.0))`

**`loadEnchants()`** (`EnchantModule.java:91-96`) uses `YamlLoader<EnchantmentDefinition>` on folder `"enchants"` with type name `"Enchantment"` and registers every successfully parsed definition into the registry. Per `YamlLoader` semantics, one malformed enchant logs a warning but does not stop the rest (`YamlLoader.java:113-123`).

**The parser** (`EnchantModule.java:98-138`) builds an `EnchantmentDefinition` from each top-level YAML key and returns `LoadResult.success(...)` / `LoadResult.failure(...)` (`LoadResult.java:17-23`). Important behavior:

- **Logic resolution is lenient** (`EnchantModule.java:120-126`): it first checks `logicFactories`, then `logicMap`. If the `logic:` string matches neither, `logic` is **`null`** — the definition still loads and is still visible in GUIs and lore, but has **no runtime effect**. Unknown logic IDs are **not** logged as errors.
- **`targets` parsing is silently lossy** (`parseTargets`, `EnchantModule.java:140-151`): each string is upper-cased and resolved against the `ItemType` enum (`ItemType.valueOf(...)`); invalid values are dropped with an ignored `IllegalArgumentException`.
- `logic-params` defaults to an empty `MemoryConfiguration` when absent (`EnchantModule.java:117-118`).

### 3.2 The Enchant Definition — `EnchantmentDefinition.java`

An immutable value object (`EnchantmentDefinition.java:9-29`):

| Field | Source YAML | Notes |
|---|---|---|
| `id` | top-level key | The registry key (stored lowercase). |
| `name` | `name` | Display name used in the enchanting GUI; **not** used in item lore (see §3.4). |
| `description` | `description` | List of MiniMessage lore lines. |
| `etableMaxLevel` | `etable-max-level` | "Enchanting Table" ceiling; used by GUI and anvil book logic. |
| `absoluteMaxLevel` | `absolute-max-level` | Hard ceiling; used by anvil merge capping. |
| `targets` | `targets` | `List<ItemType>` — which item categories are compatible. |
| `conflicts` | `conflicts` | IDs this enchant cannot coexist with. |
| `logic` | `logic` (+ `logic-params`) | The `EnchantmentLogic` handler (nullable!). |

Behavioral helpers:
- `canApplyTo(ItemType)` — `targets.contains(type)` (`EnchantmentDefinition.java:63-65`).
- `conflictsWith(String)` — `conflicts.contains(otherId.toLowerCase())` (`EnchantmentDefinition.java:67-69`).

Note that **level caps are not enforced here** — they are pure data read by the GUI (`GuiVariableProvider`) and the anvil handler. Neither `EnchantmentHelper.applyEnchantment` nor `createEnchantedBook` ever consults them.

### 3.3 The Logic Contract — `EnchantmentLogic.java`

A 5-hook interface, all methods defaulting to no-ops (`EnchantmentLogic.java:10-18`):

| Hook | Signature | Invoked from |
|---|---|---|
| `applyStats` | `(LivingEntity, int level, StatManager)` | `StatManager.recalculateStats()` — passive stat contribution while equipped/held |
| `modifyAttack` | `(DamageModifierContext, attacker, victim, level)` | `DamageCalculator` — before damage resolution, when **attacker** carries the enchant |
| `modifyDefend` | `(DamageModifierContext, attacker, victim, level)` | `DamageCalculator` — before damage resolution, when **victim** wears the enchant in armor |
| `onPostAttack` | `(DamageResult, attacker, victim, level)` | `DamageCalculator` — after the `DamageResult` is computed |
| `onPostDefend` | `(DamageResult, attacker, victim, level)` | `DamageCalculator` — after the `DamageResult` is computed |

`DamageModifierContext` carries `baseDamage`, `strength`, `critChance`, `critDamage`, `defense`, `damageMultiplier` (default `1.0`), and the immutable `damageType` (`DamageModifierContext.java:4-19`). `DamageType` is a Valmora enum (`MELEE`, `PROJECTILE`, `FALL`, `DROWNING`, `FIRE`, `LAVA`, `MAGIC`, `VOID`, `POISON`, `WITHER`, `EXPLOSION` — `DamageType.java:3-14`).

#### Builtin implementations

**`SharpnessLogic`** (`SharpnessLogic.java:9-22`) — pre-hit only. `applyStats` is an explicit no-op ("Passive stat removed. Sharpness is a pre-hit multiplier only.", `SharpnessLogic.java:12-14`). In `modifyAttack`, if `context.getDamageType() == MELEE`, multiplies the damage multiplier by `1.0 + 0.05 * level` (i.e. +5% per level, `SharpnessLogic.java:17-22`).

**`GrowthLogic`** (`GrowthLogic.java:9-17`) — passive only. `applyStats` adds `+10.0 * level` Health **modifiers** (`statManager.addModifier`, not `addStat`) for `Player` entities. Health stat id resolved through `ValmoraAPI.getInstance().getSystemStats().getHealth()` (`SystemStats.java:65`).

**`FortuneLogic`** (`FortuneLogic.java:9-16`) — passive only. Adds `+10.0 * level` to the Mining Fortune stat (`getMiningFortune()`, `SystemStats.java:76`) for players.

**`EfficiencyLogic`** (`EfficiencyLogic.java:9-16`) — passive only. Adds `+50.0 * level` to the Mining Speed stat (`getMiningSpeed()`, `SystemStats.java:77`) for players.

**`StatBonusLogic`** (`StatBonusLogic.java:7-20`) — generic passive. Constructor lowercases the `statId` (`StatBonusLogic.java:13`) and stores `perLevel`. `applyStats` adds `perLevel * level` to that stat via `addModifier`. Note: unlike Growth/Fortune/Efficiency it does **not** check for `Player` — it applies to any `LivingEntity`.

**`DamageMultiplierLogic`** (`DamageMultiplierLogic.java:9-29`) — generic pre-hit. Constructor parses `type`: `"ANY"` (or unparseable) → `damageType = null` meaning "apply to any damage type"; otherwise resolves via `DamageType.valueOf(type.toUpperCase())` (`DamageMultiplierLogic.java:15-22`). `modifyAttack` multiplies the damage multiplier by `1.0 + (percentPerLevel / 100.0) * level` when the type matches or is null (`DamageMultiplierLogic.java:25-29`).

**`DefenseReductionLogic`** (`DefenseReductionLogic.java:8-21`) — generic pre-hit. `modifyAttack` reduces the **victim's** effective defense by `percentPerLevel * level` percent of its current value, clamped at `0` (`DefenseReductionLogic.java:17-21`).

### 3.4 Application to Items — `EnchantmentHelper.java`

`EnchantmentHelper` is a **static** utility (no instance state). All operations go through `ItemMeta` + PDC.

**PDC serialization format.** The enchant map is flattened into a single string under `Keys.ENCHANTS_CONTAINER_KEY` (`valmora_enchants_container`, `Keys.java:48`), `PersistentDataType.STRING`, as comma-separated `id:level` pairs:

```
"sharpness:5,growth:3"
```

- `loadEnchantMap(PDC)` (`EnchantmentHelper.java:116-135`) splits on `,` then `:`, parsing integer levels; malformed pairs are silently skipped.
- `saveEnchantMap(PDC, map)` (`EnchantmentHelper.java:137-146`) rebuilds the string. Map iteration order is `HashMap` order — display order is re-sorted later (see §3.4 lore).

**Public operations:**

| Method | Behavior | Lines |
|---|---|---|
| `canApplyEnchantment(item, id)` | `true` only if the item has meta **and** a `Keys.ITEM_TYPE_KEY` PDC value that resolves to an `ItemType` in the definition's `targets` | `EnchantmentHelper.java:26-47` |
| `applyEnchantmentMap(item, map)` | Writes a full enchant map **bypassing the type check** (documented as for pre-validated sets like anvil merging); no-op on null/no-meta/empty map | `EnchantmentHelper.java:53-59` |
| `applyEnchantment(item, id, level)` | Guards with `canApplyEnchantment`, then puts `id.toLowerCase() → level` into the loaded map, saves, renders glow/lore | `EnchantmentHelper.java:61-79` |
| `getEnchantments(item)` | Returns the map (empty map if no meta) | `EnchantmentHelper.java:81-86` |
| `getEnchantLevel(item, id)` | Returns level or `0` | `EnchantmentHelper.java:88-90` |
| `removeEnchantment(item, id)` | Removes the key; when the map becomes empty also removes the `ENCHANTS_CONTAINER_KEY`, removes the fake `UNBREAKING` enchant and `HIDE_ENCHANTS` flag | `EnchantmentHelper.java:92-110` |
| `hasValmoraEnchants(item)` | Whether the enchant map is non-empty | `EnchantmentHelper.java:112-114` |
| `updateItemLore(item)` | Re-renders lore if the enchant map is non-empty; early-returns if empty | `EnchantmentHelper.java:166-181` |
| `createEnchantedBook(id, level)` | Builds an `Material.ENCHANTED_BOOK` with the given enchant + glow/lore | `EnchantmentHelper.java:269-285` |

**Key nuance on `canApplyEnchantment`:** it requires the `ITEM_TYPE_KEY` PDC tag, which is only written by `ItemFactory.create()` for **Valmora items** (`ItemFactory.java:33-35`). A stock vanilla item has no such tag, so `canApplyEnchantment` returns `false` — meaning `/item enchant` rejects it (`ItemCommand.java:161-164`) and `applyEnchantment` silently no-ops on it. `applyEnchantmentMap` (anvil) is the path that *can* touch generic items.

### 3.5 Lore Rendering & the Enchanted Glint

`applyGlowAndLore(item, meta, enchantMap)` (`EnchantmentHelper.java:183-219`) is the single rendering entry point used by every apply/remove path:

1. **Glint:** `meta.addEnchant(Enchantment.UNBREAKING, 1, true)` + `meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)` (`EnchantmentHelper.java:184-185`) — a fake "glint without a visible enchant line", the pattern documented in AGENTS.md §11.12.
2. **Valmora items** (have `ITEM_ID_KEY`, `EnchantmentHelper.java:187-194`): delegates to `ItemFactory.updateLore(item, meta)` so stats, abilities, rarity and reforge prefix are all rebuilt together (`ItemFactory.java:70-197`, enchant section at `ItemFactory.java:160-165`).
3. **Generic items** (`EnchantmentHelper.java:196-218`): snapshots the item's lore the first time it is enchanted (before any enchant block exists) into `Keys.GENERIC_BASE_LORE_KEY` (`valmora_generic_base_lore`, `Keys.java:49`) as a MiniMessage-serialized, newline-joined string (`serializeLore`/`deserializeLore`, `EnchantmentHelper.java:148-164`), then **always rebuilds** lore from that snapshot + the formatted enchant block. This is the fix documented in `docs/UNFINISHED_FEATURES.md` §12 — re-enchanting no longer stacks stale enchant blocks because the source of truth is the snapshot, not the already-mutated `meta.lore()`.

**`formatEnchants(Map)`** (`EnchantmentHelper.java:221-267`) builds the enchant lore block:

- IDs are sorted case-insensitively (`EnchantmentHelper.java:223-225`).
- **Fewer than 4 enchants:** each enchant gets one line `<blue><id> <level></blue>` followed by the definition's description lines as `<gray><desc-line></gray>` (`EnchantmentHelper.java:227-238`).
- **4 or more enchants:** compact lines of `<id> <level>` joined by `", "`, wrapped when a line would exceed **40 characters**; descriptions are **omitted** (`EnchantmentHelper.java:239-263`).
- Always appends a trailing empty `Component` (`EnchantmentHelper.java:264`).

**Important:** the enchant lore line uses the raw **enchant ID** (`"sharpness 5"`), not `EnchantmentDefinition.getName()` (`EnchantmentHelper.java:230`). The display name only appears in the enchanting GUI (`GuiVariableProvider.buildLevelList` → `def.getName() + " " + toRoman(level)`, `GuiVariableProvider.java:189`).

### 3.6 Damage Integration — `DamageCalculator.java`

`DamageCalculator.calculateDamage(attacker, victim, damageType, baseDamageOverride)` (`DamageCalculator.java:20-156`) is the single damage pipeline. The four logic hooks are invoked at these points:

| Hook | When | Caller site |
|---|---|---|
| `modifyAttack` | attacker is a `Player`; for each enchant on the main-hand weapon | `DamageCalculator.java:68-79` |
| `modifyDefend` | victim is a `Player`; for each enchant on each armor piece | `DamageCalculator.java:81-94` |
| `onPostAttack` | after `DamageResult` construction; per weapon enchant | `DamageCalculator.java:127-138` |
| `onPostDefend` | after `DamageResult` construction; per armor enchant | `DamageCalculator.java:140-153` |

Every lookup guards `def != null && def.getLogic() != null` (`DamageCalculator.java:74`, `:87`, `:133`, `:147`) — so enchants with unregistered logic are inert rather than crashing.

The multiplier set by `modifyAttack` is applied as `fullDamage *= context.getDamageMultiplier()` **after** the crit roll and strength scaling (`DamageCalculator.java:96-103`), and before the defense mitigation `100 / (defense + 100)` (`DamageCalculator.java:105-110`).

### 3.7 Stat Integration — `StatManager.java`

`StatManager.recalculateStats(player)` (`StatManager.java:83-193`) scans main-hand, off-hand, and the four armor slots. For each item with Valmora enchants it calls `enchantDef.getLogic().applyStats(player, level, this)` (`StatManager.java:129-135`). Because `applyStats` uses `addModifier` (an effective-stat overlay, `StatManager.java:65-68`), enchant bonuses layer on top of base stats and vanish on the next recalc — the recalc loop is the "lifecycle" that keeps passive enchant stats current. Set/cap logic later in the method still applies (e.g. the stat max-value cap at `StatManager.java:173-178`).

### 3.8 GUI / Anvil integration

- **Enchanting Table GUI** — `guis/enchanting.yml` defines the 6-row `enchanting_table` GUI (`machine: enchanting_table`, `guis/enchanting.yml:4`). The `GuiVariableProvider` exposes the `$gui.enchanting.*$` namespace (`GuiVariableProvider.java:43-44`):
  - `$gui.enchanting.has_selection$` — whether an enchant is selected (`GuiVariableProvider.java:126`).
  - `$gui.enchanting.display_list$` — the unified list; Phase 1 returns the enchant catalog, Phase 2 returns level rows for the selected enchant (`GuiVariableProvider.java:121-203`).
  - Catalog entries are filtered by `targets.contains(ItemType.ALL) || targets.contains(itemType)` (`GuiVariableProvider.java:155-156`, `:248-250`). Item type resolution first reads `ITEM_TYPE_KEY` PDC, then falls back to **material-name inference** for vanilla items (`GuiVariableProvider.java:212-244`).
  - Level states: `locked` (already surpassed), `active` (== current level, click to remove), `available` (applyable) (`GuiVariableProvider.java:178-203`).
  - GUI click events: `enchant_select ingredient $entry.id$` (`guis/enchanting.yml:94`), `enchant_apply ingredient $entry.enchantId$ $entry.level$` (`guis/enchanting.yml:110`), `enchant_remove ingredient $entry.enchantId$` (`guis/enchanting.yml:124`).
- **GUI event factories** (registered by `GuiModule.onEnable()`, `GuiModule.java:45-48`): `EnchantApplyEventFactory` (name `enchant_apply`, applies then clears selection and re-renders, `EnchantApplyEventFactory.java:25-60`), `EnchantSelectEventFactory` (`enchant_select`, stores `selected_enchant` prop, `EnchantSelectEventFactory.java:21-43`), `EnchantRemoveEventFactory` (`enchant_remove`, `EnchantRemoveEventFactory.java:25-55`), `EnchantBackEventFactory` (`enchant_back`, `EnchantBackEventFactory.java:21-36`).
- **Anvil merge** — `AnvilMachineHandler implements DynamicMachineHandler`, registered for machine `"anvil"` in `RecipeModule.onEnable()` (`RecipeModule.java:27`). Merge math (`AnvilMachineHandler.java:39-82`):
  - Both items must carry Valmora enchants; material side must be non-empty (`AnvilMachineHandler.java:29-33`).
  - **Enchanted-book inputs** (`base.getType() == ENCHANTED_BOOK`) are capped at `etable-max-level` and refuse inputs already above the ceiling (`AnvilMachineHandler.java:35-47`, `:66`, `:76`); non-book merges cap at `absolute-max-level` (`AnvilMachineHandler.java:60`).
  - Conflicts are checked via `def.conflictsWith(existingId)`; conflicting enchants are skipped (`AnvilMachineHandler.java:49-58`).
  - Same-level merge → `level + 1` (capped); different levels → `max(base, material)` (`AnvilMachineHandler.java:62-81`).
  - Result is a clone of `base` written via `EnchantmentHelper.applyEnchantmentMap` (bypasses the type check) (`AnvilMachineHandler.java:87-88`).
  - **Cost:** `10 coins × total merged level`, deducted by an `on-craft` script `variable add player.var.coins -<cost>` (`AnvilMachineHandler.java:90-97`).

---

## Configuration (YAML)

Config lives in `plugins/Valmora/enchants/*.yml` (auto-extracted from the jar's `enchants/` folder by `Valmora.saveAllResources()`, `Valmora.java:469-479`, only if the file does not already exist). Each top-level key is the enchant **ID** (registry key, stored lowercase).

### Schema

```yaml
<enchant-id>:
  name: "<display name>"
  logic: "<namespace:logic_key>"
  description:
    - "<MiniMessage lore line>"
  targets:
    - SWORD
  conflicts:
    - "other_enchant_id"
  etable-max-level: 5
  absolute-max-level: 7
  logic-params:            # only read by parameterized logic factories
    stat: "strength"
    per-level: 1.0
```

### Field Reference

| Field | Required | Default | Parser site | Explanation |
|---|---|---|---|---|
| *(top-level key)* | Yes | — | `EnchantModule.java:99` | Enchant ID; used in PDC, lore, GUI events, anvil, and registry lookups. Stored lowercase. |
| `name` | No | the enchant ID | `EnchantModule.java:101` | Display name shown in the enchanting GUI and `/item info` (`ItemCommand.java:330`). **Not** used in item lore. |
| `logic` | No* | `""` | `EnchantModule.java:116` | Handler key. Must match a registered direct logic or factory. Unknown keys silently produce `logic = null` (definition loads, no effect). |
| `description` | No | `[]` | `EnchantModule.java:102-105` | MiniMessage lore lines rendered under the enchant line (only for items with < 4 enchants). |
| `targets` | Yes | — | `EnchantModule.java:110`, `:140-151` | Compatible `ItemType`s. Case-insensitive; invalid entries silently dropped. `ALL` matches every type (`GuiVariableProvider.java:155-156`). |
| `conflicts` | No | `[]` | `EnchantModule.java:111-114` | Enchant IDs this enchant cannot coexist with; enforced **only** by the anvil handler. |
| `etable-max-level` | No | `5` | `EnchantModule.java:107` | Enchanting-Table ceiling: GUI level list range and anvil book caps. |
| `absolute-max-level` | No | `10` | `EnchantModule.java:108` | Hard ceiling for non-book anvil merges. |
| `logic-params` | No | empty section | `EnchantModule.java:117-118` | Parameter section consumed by the parameterized logic factories (below). |

\* `logic` is effectively required for an enchant to *do* anything, but nothing validates it.

### Registered logic keys and their parameters

| `logic` | Params (under `logic-params`) | Effect |
|---|---|---|
| `valmora:sharpness` | — | +5% melee damage per level (pre-hit) |
| `valmora:growth` | — | +10 max Health per level (passive, players) |
| `valmora:fortune` | — | +10 Mining Fortune per level (passive, players) |
| `valmora:efficiency` | — | +50 Mining Speed per level (passive, players) |
| `valmora:stat_bonus` | `stat` (default `"strength"`), `per-level` (default `1.0`) | +`per-level`×level of any stat (passive) |
| `valmora:damage_multiplier` | `type` (default `"MELEE"`; `ANY` or a `DamageType`), `percent-per-level` (default `5.0`) | +`percent-per-level`% damage per level for the given damage type (pre-hit) |
| `valmora:defense_reduction` | `percent-per-level` (default `3.0`) | Reduce victim defense by `percent-per-level`%×level of its current value (pre-hit) |

Registered in `EnchantModule.java:48-59`.

### Shipped example file — `enchants/example_enchantments.yml`

| ID | logic | targets | etable / absolute | Conflicts |
|---|---|---|---|---|
| `sharpness` | `valmora:sharpness` ✅ | `SWORD` | 5 / 7 | smite, bane_of_arthropods |
| `growth` | `valmora:growth` ✅ | HELMET, CHESTPLATE, LEGGINGS, BOOTS | 3 / 5 | — |
| `execute` | `valmora:execute` ❌ unregistered | `SWORD` | 5 / 6 | prosecute |
| `first_strike` | `valmora:first_strike` ❌ | `SWORD` | 4 / 5 | triple_strike |
| `life_steal` | `valmora:life_steal` ❌ | `SWORD` | 3 / 5 | syphon, mana_steal |
| `lethality` | `valmora:lethality` ❌ | `SWORD` | 6 / 6 | — |
| `protection` | `valmora:protection` ❌ | armor slots | 4 / 6 | blast/fire/projectile_protection |
| `respite` | `valmora:respite` ❌ | armor slots | 5 / 5 | rejuvenate |
| `thorns` | `valmora:thorns` ❌ | armor slots | 3 / 4 | — |
| `fortune` | `valmora:fortune` ✅ | PICKAXE, AXE, SHOVEL, HOE, FISHING_ROD | 3 / 5 | — |
| `efficiency` | `valmora:efficiency` ✅ | PICKAXE, AXE, SHOVEL, HOE | 5 / 7 | — |

Six of the eleven shipped enchants (`execute`, `first_strike`, `life_steal`, `lethality`, `protection`, `respite`, `thorns`) reference **logic IDs that are not registered** — they display in GUIs and lore but have no runtime effect. `docs/todo.md:5` tracks "enchant: add all of the enchantments".

---

## Data Model / Persistence

There is **no database persistence** for enchantments — they are item-bound data that travels with the item.

**PDC keys used** (all defined in `util/Keys.java`):

| Key | NamespacedKey | Type | Written by | Read by |
|---|---|---|---|---|
| `ENCHANTS_CONTAINER_KEY` | `valmora_enchants_container` | STRING (`id:level,...`) | `EnchantmentHelper.saveEnchantMap` (`EnchantmentHelper.java:145`) | `EnchantmentHelper.loadEnchantMap` (`EnchantmentHelper.java:119-120`) |
| `GENERIC_BASE_LORE_KEY` | `valmora_generic_base_lore` | STRING (MiniMessage lore, newline-joined) | `applyGlowAndLore` first-time snapshot (`EnchantmentHelper.java:206`) | `applyGlowAndLore` rebuild (`EnchantmentHelper.java:202-203`) |
| `ITEM_TYPE_KEY` | `item_type` | STRING (ItemType name) | `ItemFactory.create` (`ItemFactory.java:34`) | `canApplyEnchantment` (`EnchantmentHelper.java:31-32`), `GuiVariableProvider.getItemType` (`GuiVariableProvider.java:216-217`) |
| `ITEM_ID_KEY` | `valmora_item_id` | STRING | `ItemFactory.create` (`ItemFactory.java:31`) | `applyGlowAndLore` Valmora-vs-generic branch (`EnchantmentHelper.java:187`) |

**Serialization format** — a single string under `valmora_enchants_container`, `"id1:level1,id2:level2"` (`EnchantmentHelper.java:137-146`). Levels are `int`s. IDs are stored lowercase. There is no ordering guarantee in storage; display ordering is re-derived by `formatEnchants`.

**Vanilla mirror:** the enchanted glint is a fake vanilla `Enchantment.UNBREAKING` level 1 with `ItemFlag.HIDE_ENCHANTS` (`EnchantmentHelper.java:184-185`), so vanilla XP/table/anvil code sees an "enchanted" item but no enchant line leaks through.

---

## API Exposed

**Via `ValmoraAPI`** (`ValmoraAPI.java:43`, implemented at `Valmora.java:349-351`):

```java
EnchantModule enchant = ValmoraAPI.getInstance().getEnchantModule();
```

`EnchantModule` public surface:

- `EnchantmentRegistry getRegistry()` — `EnchantmentRegistry extends SimpleRegistry<EnchantmentDefinition>` (`EnchantmentRegistry.java:5`), a synchronized, case-insensitive registry (`SimpleRegistry.java:15-58`): `register`, `unregister`, `get → Optional`, `contains`, `getKeys`, `values`, `clear`, `size`.
- `EnchantmentLogic getLogic(String id)` — direct handler lookup (`EnchantModule.java:83-85`).
- `void registerLogic(String id, EnchantmentLogic logic)` — extension point for external plugins (`EnchantModule.java:87-89`). **Caveat:** `logicMap` is cleared in `onDisable()`, so externally-registered logics are dropped on every reload and must be re-registered after it.

**Static helper (`EnchantmentHelper`)**: `canApplyEnchantment`, `applyEnchantment`, `applyEnchantmentMap`, `getEnchantments`, `getEnchantLevel`, `removeEnchantment`, `hasValmoraEnchants`, `loadEnchantMap`, `updateItemLore`, `formatEnchants`, `createEnchantedBook`. All are `public static` (`EnchantmentHelper.java:26-285`).

There is no dedicated enchant command; admin/player entry points are `/item enchant` and `/item enchantbook` (registered in `Valmora.onEnable()`, `Valmora.java:237`).

---

## Dependencies & Consumers

### Dependencies (loads-after)

| Dependency | Why |
|---|---|
| Items (`ItemManager`/`ItemFactory`) | `applyGlowAndLore` delegates lore rebuilds for Valmora items (`EnchantmentHelper.java:192`); `ItemType` enum for target matching; `ItemFactory` writes the PDC tags `canApplyEnchantment` reads. |
| Stats (`StatModule`/`SystemStats`) | `GrowthLogic`/`FortuneLogic`/`EfficiencyLogic` resolve stat IDs through `SystemStats`; `StatManager` drives `applyStats`. |
| GUI (`GuiModule`) | `GuiVariableProvider` and the enchant event factories run inside the GUI subsystem (registered by `GuiModule.onEnable()`, `GuiModule.java:45-48`). |
| Recipe (`RecipeModule`) | `AnvilMachineHandler` is registered by the recipe module (`RecipeModule.java:27`). |
| Script (`ScriptModule`) | GUI event factories are `EventFactory` implementations registered via `ScriptModule.registerEvent`. |

### Consumers (who calls the module)

| Consumer | How it uses enchants | Sites |
|---|---|---|
| Combat `DamageCalculator` | `modifyAttack` / `modifyDefend` / `onPostAttack` / `onPostDefend` hooks | `DamageCalculator.java:68-79`, `:81-94`, `:127-153` |
| Stat `StatManager` | `applyStats` for equipped/held items | `StatManager.java:129-135` |
| Item `ItemFactory` | Enchant lore section during Valmora lore rebuild | `ItemFactory.java:160-165` |
| Item `ItemCommand` | `/item enchant`, `/item enchantbook`, held-item info, tab-completion | `ItemCommand.java:140-188`, `:322-334`, `:364-371` |
| GUI `GuiVariableProvider` | Enchanting-table catalog + level lists | `GuiVariableProvider.java:121-251` |
| GUI event factories | Apply/remove/select/back clicks in the enchanting GUI | `EnchantApplyEventFactory.java:50`, `EnchantRemoveEventFactory.java:46` |
| Recipe `AnvilMachineHandler` | Anvil merge math + cost | `AnvilMachineHandler.java:29-97` |
| Tests `DamageCalculatorTest` | Mocked `EnchantModule`/`EnchantmentRegistry` | `DamageCalculatorTest.java:41-54`, `:89`, `:196-197` |

**Non-consumer (related but separate):** the Quest module's `ENCHANT` objective tracks **vanilla** `EnchantItemEvent` (`QuestListener.java:219-227`), and the Enchanting **skill** gains XP from vanilla enchant actions (`skills/enchanting.yml:10-43`). Neither touches Valmora enchant definitions.

---

## Unfinished Things / TODOs

- **Seven shipped enchants are inert.** `example_enchantments.yml` references `valmora:execute`, `valmora:first_strike`, `valmora:life_steal`, `valmora:lethality`, `valmora:protection`, `valmora:respite`, and `valmora:thorns`, none of which are registered in `EnchantModule.registerBuiltinLogics()` (`EnchantModule.java:47-60`). They load with `logic == null` and silently do nothing. Tracked by `docs/todo.md:5` ("enchant: add all of the enchantments").
- **No level-cap enforcement.** `etable-max-level`/`absolute-max-level` are display/anvil data only. `applyEnchantment` (`EnchantmentHelper.java:61-79`) and `createEnchantedBook` accept any level (e.g. `/item enchant sharpness 999`).
- **No conflict enforcement on direct apply.** `conflictsWith` is only consulted by `AnvilMachineHandler` (`AnvilMachineHandler.java:49-58`); the GUI and `/item` paths can stack conflicting enchants.
- **Vanilla items can't be enchanted via the type-checked paths.** `canApplyEnchantment` requires the `ITEM_TYPE_KEY` PDC tag (`EnchantmentHelper.java:31-34`), which only Valmora items carry. The enchanting GUI *lists* enchants for vanilla items (material-name fallback, `GuiVariableProvider.java:225-243`), but `enchant_apply` then silently no-ops because `applyEnchantment` re-checks `canApplyEnchantment`. The anvil (`applyEnchantmentMap`) is the only path that touches generic items — the exact scenario `docs/UNFINISHED_FEATURES.md` §12 fixed.
- **The enchanting table GUI has no cost.** The `GUI_MODULE_ENHANCEMENT_PLAN.md` Phase 2 goal ("costing XP/Mana") is not implemented — `EnchantApplyEventFactory` only applies the enchant (`EnchantApplyEventFactory.java:50`). The bookshelf "power level" display is hardcoded to `0 / 15` (`guis/enchanting.yml:59-60`) with no bookshelf detection.
- **No way to open the enchanting GUI in stock installs.** `guis/enchanting.yml` has no `command` key and no `enchanting_table` `DynamicMachineHandler` is registered (the only dynamic handlers are `anvil`, `alchemy`, `reforge_anvil`, `forge_random` — `RecipeModule.java:27`, `AlchemyModule.java:52`, `ReforgeModule.java:55/63`). It can only be opened via an `open_gui` action from another GUI.
- **Unused logic hooks.** No shipped logic implements `modifyDefend`, `onPostAttack`, or `onPostDefend` — those pipeline hooks exist but only `modifyAttack` and `applyStats` are exercised.
- **Unknown logic IDs are silent.** `EnchantModule.java:120-126` yields `logic = null` with no warning, so config typos are hard to spot (only visible as inert enchants).
- **External logics are reload-fragile.** `onDisable()` clears `logicMap`/`logicFactories` (`EnchantModule.java:64-66`), so `registerLogic()` consumers must re-register after every `/valmora reload`.
- **Docs drift.** `docs/VALMORA_DOCUMENTATION.md:1294` documents `$enchant.NAME.prop$` as the list-iteration variable, but the enchanting GUI actually uses `$entry.*$` loop items (`guis/enchanting.yml:76-138`).

---

## Possible Improvements / Changes

- **Render the display name in lore.** `formatEnchants` prints the raw ID (`EnchantmentHelper.java:230`); switching to `def.getName()` (plus roman numerals, mirroring `GuiVariableProvider.toRoman`) would match the GUI presentation.
- **Enforce caps centrally.** Have `applyEnchantment`/`applyEnchantmentMap`/`createEnchantedBook` clamp to `absolute-max-level`, and let the GUI clamp to `etable-max-level`, so all paths agree.
- **Enforce conflicts in apply paths** (not just the anvil) so the GUI and `/item` respect `conflicts`.
- **Write `ITEM_TYPE_KEY` fallback.** Either write an inferred `ItemType` tag onto generic items at first enchant (material inference already exists in `GuiVariableProvider.getItemType`, `GuiVariableProvider.java:225-243`) or relax `canApplyEnchantment` to infer from material — would make the GUI actually apply to vanilla items.
- **Add costs to the enchanting table.** The plan's XP/mana/coin deduction (`GUI_MODULE_ENHANCEMENT_PLAN.md` Phase 2) and real bookshelf-power calculation would complete the machine.
- **Register an `enchanting_table` dynamic handler or a GUI `command`** so the GUI is reachable without an `open_gui` chain.
- **Implement the missing logic handlers** listed in §8 (execute, first strike, life steal, etc.) — mostly `onPostAttack`/`onPostDefend`-shaped hooks that currently have no implementors.
- **Validate `logic` at parse time** — warn on unknown IDs instead of loading a null-logic definition.
- **Cache enchant-map parsing.** The GUI plan itself flags aggressive PDC string parsing on 0-tick slot updates (`GUI_MODULE_ENHANCEMENT_PLAN.md:117`); `getEnchantments` could be cached per item version.
- **Deduplicate lore rendering.** The generic-item base-lore snapshot and `ItemFactory`'s full rebuild are two parallel lore pipelines; a single enchant-lore module consumed by both would prevent future drift.
