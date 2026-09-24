# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> This is the **primary orientation document** for any AI agent working on this codebase.
> Read it fully before touching any code. Then consult the referenced docs for deeper detail.

---

## Table of Contents

1. [Project Identity](#1-project-identity)
2. [Mandatory Reading](#2-mandatory-reading)
3. [Repository Layout](#3-repository-layout)
4. [Build & Dev Workflow](#4-build--dev-workflow)
5. [Architecture Overview](#5-architecture-overview)
6. [Module System — How to Work With It](#6-module-system--how-to-work-with-it)
7. [Critical Patterns You Must Follow](#7-critical-patterns-you-must-follow)
8. [GUI & Machine System](#8-gui--machine-system)
9. [Recipe Engine](#9-recipe-engine)
10. [Script DSL Reference](#10-script-dsl-reference)
11. [Database Layer](#11-database-layer)
12. [Testing](#12-testing)
13. [Common Mistakes to Avoid](#13-common-mistakes-to-avoid)
14. [Paper API Hard Topics](#14-paper-api-hard-topics)

---

## 1. Project Identity

| Property             | Value                                           |
| -------------------- | ----------------------------------------------- |
| Plugin name          | **Valmora**                                     |
| Group / package root | `org.nakii.valmora`                             |
| Version              | `1.0.0-beta1` (per `build.gradle`; verified 2026-08-26 — was stale at `0.1` here) |
| Target server        | **Paper 1.21.11** (not Spigot, not CraftBukkit) |
| Java version         | **21** (required)                               |
| Build tool           | Gradle with Shadow + run-paper plugins          |

This is a **modular RPG plugin**. Every major feature lives in its own `ReloadableModule`. The plugin is not a monolith — treat it like a collection of mini-plugins wired together through a shared API.

---

## 2. Mandatory Reading

Before implementing any feature, open and read:

- **`docs/MODULE_DEVELOPMENT.md`** — complete lifecycle guide for creating, registering, enabling, and hot-reloading modules.
- **`docs/modules/design/<module>.md`** and **`docs/modules/user/<module>.md`** for the module(s) you're touching — these are the current, per-module, verified-against-code reference (architecture/internals in `design/`, admin/player-facing YAML + commands in `user/`). Start from `docs/modules/design/INTEGRATION.md` / `docs/modules/user/INTEGRATION.md` for the cross-module map (the module list itself is §5 below / `Valmora.java`).
- **`docs/VALMORA_DOCUMENTATION.md`** — an older, broader single-file reference. Prefer the per-module docs above where they overlap (they're more current); this file is kept for content not yet migrated (e.g. §1–20 general engine architecture) and is being corrected in place as drift is found, not as a replacement for the per-module docs.
- **`docs/Valmora_Modifier_Framework_Design.docx`** — if you're touching reforges, gemstones, or anything that grants stats/abilities from an attachable item component, read this first, then `docs/modules/design/modifier.md`/`docs/modules/user/modifier.md`. It's a hard rule, not a suggestion: **no new group-specific Java** (no `if group == "my_new_group"` in the engine, no dedicated `MyGroupModule`) — express new content as a modifier group + modifiers in `modifiers/groups/*.yml` / `modifiers/definitions/*.yml` instead. `docs/MODIFIER_FRAMEWORK_BACKLOG.md` tracks what's implemented vs. still deferred (trigger-bound STATE effects, a Java builder API, item-upgrade inheritance) — check it before assuming a described behavior already works.

These are ground truth. If this CLAUDE.md ever conflicts with them, the specific doc wins.

---

## 3. Repository Layout

```
valmora/
├── build.gradle                    # Gradle build — deps, Shadow, run-paper
├── src/
│   ├── main/
│   │   ├── java/org/nakii/valmora/
│   │   │   ├── Valmora.java        # Plugin entry point — wires all modules
│   │   │   ├── api/                # Public interfaces (ValmoraAPI, ReloadableModule, etc.)
│   │   │   ├── module/             # One sub-package per module
│   │   │   │   ├── gui/            # GUI/machine system (see §8)
│   │   │   │   ├── item/
│   │   │   │   ├── mob/
│   │   │   │   ├── recipe/         # Recipe engine (see §9)
│   │   │   │   ├── script/         # Script DSL (see §10)
│   │   │   │   ├── skill/
│   │   │   │   ├── time/           # RPG calendar (TimeModule, TimeManager, TimeSnapshot)
│   │   │   │   ├── combat/
│   │   │   │   └── ui/             # Scoreboard, ActionBar, Chat
│   │   │   └── infrastructure/
│   │   │       └── config/         # YamlLoader lives here
│   │   └── resources/
│   │       ├── plugin.yml
│   │       ├── config.yml
│   │       ├── items/*.yml
│   │       ├── mobs/*.yml
│   │       ├── guis/*.yml
│   │       ├── recipes/*.yml
│   │       └── skills/*.yml
│   └── test/
│       └── java/org/nakii/valmora/
├── docs/
│   ├── MODULE_DEVELOPMENT.md       ← READ THIS
│   ├── modules/
│   │   ├── design/<module>.md      ← READ THIS (per module you touch — architecture/internals)
│   │   └── user/<module>.md        ← READ THIS (per module you touch — admin/player-facing)
│   └── VALMORA_DOCUMENTATION.md    # broader older reference; per-module docs above take priority
└── plugins/Valmora/                # Runtime data (generated, not committed)
    ├── config.yml
    ├── database.db
    ├── time.yml                    # Persisted day-offset for TimeModule
    └── ...
```

The `module/` sub-packages follow a consistent internal structure: `XModule.java`, `XListener.java`, `XRegistry.java`, `XLoader.java`. Keep new modules consistent with this convention.

---

## 4. Build & Dev Workflow

```bash
# Compile and produce the shaded JAR
./gradlew build

# Run unit tests
./gradlew test

# Start a Paper 1.21.11 dev server with the plugin auto-loaded
./gradlew runServer
```

- `build` depends on `shadowJar` — the output is always the fat JAR.
- Java 21 is required. The build will fail on earlier JDKs.
- The dev server from `runServer` uses run-paper to download Paper automatically on first run.

**Hot reload** (while server is running): `/valmora reload` — requires the `valmora.admin` permission. This calls `ModuleManager.reloadModules()`, which disables all modules in reverse order and re-enables them in forward order.

---

## 5. Architecture Overview

```
Valmora.onEnable()
    │
    ├── 1. ModuleManager created
    ├── 2. All modules instantiated (as fields in Valmora.java)
    ├── 3. All modules registered (moduleManager.registerModule)
    ├── 4. All modules enabled   (moduleManager.enableModules)
    │       └─ Each module.onEnable() runs in registration order
    └── 5. Commands registered   ← NEVER register commands inside a module
```

**Module registration order** (must be preserved — verified against `Valmora.java`, post
modifier-framework refactor: the legacy `reforge` module was removed and replaced by `rarity` +
`modifier`, see §Generic Modifier Framework below; `machine` was added after `recipe` for the
machine-definition layer, see §Machine Definition Layer below; `pack` was added last for the content
pack manager, see `docs/modules/design/pack.md` — it must stay last, since it only orchestrates other
modules' existing reload machinery and must never be a dependency of anything else):

```
script → world_rules → time → rarity → stat → player → economy → ui → ability → item → mob → skill →
combat → gui → recipe → machine → modifier → alchemy → enchant → zone → death → resource → fishing →
block_loot → npc → warp → points → notify → quest → collection → hud → calendar → pet → progression → pack
```

`world_rules` (`WorldRulesModule`, VANILLA_CONTROL_AUDIT.md §8) was added right after `script` — no
dependencies, purely applies `world.gamerules.*` from config.yml to every loaded/loading world.

`death` (`DeathModule`, VANILLA_CONTROL_AUDIT.md §9) was previously missing from this list despite
already being registered right after `zone` in code — corrected here to match `Valmora.java`.

`block_loot` (`BlockLootModule`, see `docs/modules/design/blockloot.md`/`docs/modules/user/blockloot.md`)
was added right after `fishing` and before `npc` — a global per-block-type loot override, with no
`onEnable()`-time dependency on `item` (it reaches `ItemManager` only at event time, the same
already-proven-safe pattern `item`'s own `LootListener` uses to reach the later `resource` module).

Later modules may depend on earlier ones (e.g. `skill` can access `stat`). Earlier modules must not depend on later ones. If you add a new module, insert it at the correct position — document the reason in `Valmora.java` (the file already carries inline comments next to several entries explaining a dependency, e.g. `notify` before `quest`, `hud` after `script`, `modifier` after `recipe`).

**Accessing modules at runtime:**

```java
ValmoraAPI api = ValmoraAPI.getInstance();
ItemManager items = api.getItemManager();
SkillManager skills = api.getSkillManager();
TimeManager time = api.getTimeManager();
// etc. — see ValmoraAPI interface for full list
```

---

## 6. Module System — How to Work With It

> Full details in `docs/MODULE_DEVELOPMENT.md`. This section is a working summary.

### 6.1 The `ReloadableModule` Contract

Every module implements three methods:

```java
void onEnable();   // Load configs, register listeners, start tasks
void onDisable();  // Unregister listeners, cancel tasks, clear caches
String getId();    // Unique lowercase ID, e.g. "items", "combat"
```

`onEnable()` must be **idempotent** — it can be called more than once (hot reload). Always fully initialize state inside `onEnable()`, never in the constructor.

### 6.2 Listener Registration and Cleanup

Register listeners in `onEnable()`, unregister in `onDisable()`. **Failure to unregister causes duplicate event handling after reload.**

```java
// onEnable
this.listener = new MyListener(plugin);
plugin.getServer().getPluginManager().registerEvents(listener, plugin);

// onDisable  — MANDATORY
HandlerList.unregisterAll(listener);
this.listener = null;
```

### 6.3 Never Register Commands in a Module

Commands are registered **after** all modules are enabled, directly in `Valmora.onEnable()`. If you need a new command, add it there — do not call `getCommand(...).setExecutor(...)` inside any module's `onEnable()`.

### 6.3a Reload & Versioning

Read `docs/modules/design/versioning.md` before touching persistence, item/mob templates or reload
code. The short version:
- Items and mobs read template values (stats, rarity, type, attributes) **live** from their
  definition via `ItemView`/`MobLifecycleListener`. Only instance data is stored on them.
- Every persisted shape has a version ladder: DB (`SQLDataStore`), profile JSON (`ProfileMigrator`),
  items (`ItemMigrator`), `config.yml` (`ConfigUpdater`), default files (`ResourceManifest`).
- Renames go through `previous-ids:` (`IdAliases`).
- `YamlLoader` keeps the last good version of every entry.
- Never capture profiles, managers or players in delayed tasks. Use `PlayerProfileLoadedEvent`
  for join logic and `ValmoraReloadedEvent` for post-reload reconciliation.

### 6.4 Accessing Other Modules from Within a Module

Use `ValmoraAPI.getInstance()`. Do not hold direct references to sibling module instances; go through the API. This keeps modules decoupled and reload-safe.

---

## 7. Critical Patterns You Must Follow

### 7.1 YamlLoader

Generic config loader at `org.nakii.valmora.infrastructure.config.YamlLoader`. Use it for all YAML loading — do not write custom `FileConfiguration` boilerplate.

```java
YamlLoader<GuiDefinition> loader = new YamlLoader<>(plugin, "guis", "GUIs").kind(Kinds.GUI);
loader.load(parser::parse, def -> registry.put(def.getId(), def));
```

**Diagnostics.** `YamlLoader` opens a `LoadScope` (`infrastructure/config/diag/`) around every entry
it parses. Anything inside it — your parser, `ConfigReader`, the script compilers, event factories —
reports problems through `LoadScope.current()` / `Diagnostics.warn|error(...)` and they're attributed
to the file, entry and key path automatically. They go into the `LoadReport` shown by
`/valmora reload`, `/valmora validate`, `/valmora report` and `plugins/Valmora/last-load-report.txt`.
Loaders that read YAML themselves (single settings files, quest packages, pipelines) use a
`LoadSession` for the same reporting — never `YamlConfiguration.loadConfiguration` (it silently turns
a syntax error into an empty file) and never a bare `logger.warning` for content problems.

**Parsing helpers.** Use `ConfigReader` (`infrastructure/config/read/`) instead of hand-written
`try { Enum.valueOf } catch`: `requireString/requireEnum/requireMaterial` (missing/invalid → ERROR),
`enumOf/material/intRange/doubleRange/bool/stringList` (bad value → WARN + default), `oneOf` for
registry-backed "open enums", `knownKeys(...)` (typo'd keys → WARN with "did you mean"), and
`result(() -> ...)` to fail the entry only if an ERROR was reported. Rule: a broken required field
fails the entry (the loader keeps its last good version); a broken optional part is a warning and the
rest of the entry still loads.

**Cross-references.** Record every id your content points at with `reader.ref(key, Kinds.X, id)` /
`LoadScope.ref(kind, id)`; event factories do it via `EventFactory.references(...)`. Once all modules
are enabled, `ReferenceValidator` checks them against the `ContentIndex` (kinds registered in
`BuiltinContentKinds`) and reports dangling ones as warnings — content stays loaded. Checks that need
more than "this id exists" implement `ReferenceCheck` and register with
`ReferenceValidator.global().register(...)` (see `MachineModule`, `ModifierModule`).

**Scripts in definitions.** Compile action/condition lists at load time inside the loader's scope —
hold a `CompiledScript` / `CompiledConditions` (`module/script/compile/`) and call `.run(ctx, module)`
/ `.test(ctx, module)` at the call site. Never `parseList(...)` per execution. Code that only has the
raw strings at runtime uses `ScriptModule.runCached(...)`.

### 7.2 Registry

`Registry<T>` stores keys **case-insensitively** (stored lowercase). Always retrieve with `.get(key.toLowerCase())` if you bypass the registry helper. Registries are populated during `onEnable()` and cleared in `onDisable()`.

### 7.3 ExecutionContext

Passed to all mechanics, scripting, and ability systems. Always access entities and variables through it:

```java
LivingEntity caster = context.getCaster();
Optional<LivingEntity> target = context.getTarget();
VariableResolver vars = context.getVariableResolver();
ConfigurationSection params = context.getParams();
```

Never store `ExecutionContext` beyond the scope of a single mechanic invocation. It is not thread-safe.

### 7.4 Async Operations

Database calls use HikariCP with a dedicated executor — they are async. Do **not** touch Bukkit API (entities, worlds, blocks) from async context. Schedule any Bukkit callbacks back to the main thread:

```java
// After async DB work:
plugin.getServer().getScheduler().runTask(plugin, () -> {
    // Safe Bukkit API access here
});
```

### 7.5 MiniMessage for Text

All display text uses **MiniMessage** (Adventure). Never use `ChatColor` or `§` codes. Use `Formatter.format(String)` from `org.nakii.valmora.util.Formatter` — it wraps MiniMessage and suppresses italic by default.

```java
// Correct — via Formatter
Component msg = Formatter.format("<red>You took <bold>10</bold> damage!");
player.sendMessage(msg);

// Also correct — raw MiniMessage
Component msg = MiniMessage.miniMessage().deserialize("<red>...");

// Wrong — do not do this
player.sendMessage(ChatColor.RED + "You took 10 damage!"); // ❌
```

---

## 8. GUI & Machine System

The GUI module (`module/gui/`) implements a data-driven inventory UI system driven by YAML definitions in `resources/guis/`. Each YAML file defines one GUI.

### 8.1 Component Types

| Type | Behavior |
|------|----------|
| `INPUT` | Accepts player items; protected from accidental removal; snapshot captured before re-render |
| `OUTPUT` | Displays matched recipe result; players can take items; re-populated after render |
| `DISPLAY` | Static/read-only item; can have click `actions` keyed by `ClickType` enum name |
| `PAGINATED` | Iterates a list variable; renders one item per page element; used with `PREVIOUS_PAGE`/`NEXT_PAGE` |
| `PREVIOUS_PAGE` / `NEXT_PAGE` | Modifies `GuiSession.currentPage` and triggers a re-render |

### 8.2 Event Blocks

Each GUI definition supports four event blocks:

| Block | Trigger |
|-------|---------|
| `on-open` | Fires once when the player opens the GUI |
| `on-close` | Fires once when the player closes the GUI |
| `on-slot-update` | Fires when an INPUT slot changes (item placed or removed) |
| `on-update` | Fires every `update-interval` ticks on a repeating timer |

Event blocks contain `actions` and optional `fail-actions`. The first `condition` action short-circuits the remaining actions if the condition is false and jumps to `fail-actions`.

### 8.3 Input Snapshot Mechanism

Before re-rendering, `GuiSession.snapshotInputs()` copies INPUT slot contents into a map. This snapshot is available to variable resolvers during the render pass so that `$gui.input.<id>$` variables still resolve to the items even after the inventory slots are cleared for re-population.

**Variable access to GUI slots:**
- `$gui.input.<componentId>.id$` — item ID (Valmora item ID or vanilla material name)
- `$gui.input.<componentId>.amount$` — stack size
- `$gui.input.<componentId>.material$` — Bukkit material name
- `$prop.<key>$` — per-session property bag; writable via `variable set prop.key value`

### 8.4 gui_force_craft Event

The `gui_force_craft` script event (defined in `GuiForceCraftEventFactory`) is the unified craft trigger:

1. Takes a live snapshot of INPUT slots
2. Calls `RecipeEngine.craft()` — see §9
3. Places output into OUTPUT slots
4. Re-renders the GUI

Dupe protection: `GuiSession.craftingLocked` is set to `true` during the craft pipeline and cleared afterwards. A second `gui_force_craft` while locked is a no-op.

### 8.5 Machine Handler Extensibility

`DynamicMachineHandler` is an interface that allows fully custom craft logic for a named machine. Register via:

```java
recipeModule.getRecipeEngine().registerHandler("machine_id", new MyMachineHandler(plugin));
```

Handlers are checked before YAML recipes, so they can override or augment matching behaviour.

---

## 9. Recipe Engine

The `RecipeEngine` (`module/recipe/RecipeEngine.java`) runs a three-step match for every craft attempt:

1. **Dynamic handler** — check `DynamicMachineHandler` registered for this machine ID (custom logic)
2. **YAML recipes** — match against static recipes loaded from `resources/recipes/*.yml`
3. **Vanilla** — fall back to standard Bukkit recipes (used for crafting table passthrough)

### 9.1 Recipe Types

Every YAML recipe is one of two types — there is no more named-slot `EXACT_SLOT` format
(`inputs: {input1: ..., input2: ...}`); a machine with a small fixed number of slots (e.g. the
forge's 2) is just a SHAPED recipe with a narrow pattern, the same mechanism a 3×3 crafting grid or
a 1×3 press uses.

| Type | Ingredient format | Matching |
|------|--------------------|----------|
| `SHAPED` | `ingredients:` (letter → `{material, amount}`) + `pattern:` (rows of those letters, `' '` = must be empty) | Position matters; pattern can be narrower/shorter than the machine's full grid and slides to fit (recipe-yaml rework note) |
| `SHAPELESS` | `ingredients:` — a plain list of `{item, amount}` | Bag matching; any slot/order. `pattern:` has no effect here and logs a load-time warning if present |

`RecipeType.EXACT_SLOT` still exists as an internal marker (dynamic recipes — the anvil, alchemy,
enchanting table — and the vanilla-crafting-passthrough fallback), just not as something a YAML
recipe can request via `type:`.

### 9.2 YAML Recipe Format

```yaml
my_recipe_id:
  machine: alchemy          # must match GUI's machine: field
  type: SHAPELESS
  ingredients:
    - item: NETHER_WART
      amount: 1
    - item: GLASS_BOTTLE
      amount: 1
  outputs:
    - item: custom_item_id  # Valmora item ID or vanilla material
      amount: 1
  on-craft:
    - "sound player block.brewing_stand.brew"
```

A SHAPED recipe on a small positional machine (e.g. the forge's 2 slots, left→right) looks like:

```yaml
my_forge_recipe:
  machine: forge
  type: SHAPED
  ingredients:
    i: { material: IRON_INGOT, amount: 2 }
    d: { material: DIAMOND, amount: 1 }
  pattern:
    - "id"                  # left slot = 2x iron ingot, right slot = 1x diamond
  outputs:
    - item: reinforced_ingot
      amount: 1
```

`outputs:` is a plain list — almost every recipe has exactly one entry and needs nothing more than
`{item, amount}`. A recipe with **more than one** output must give every entry a `slot:` naming
which OUTPUT component id (a GUI's `id:` under its `O`-type component, same convention INPUT
components already use — e.g. `primary`/`byproduct`) it goes into — required rather than optional,
since which physical slot an unslotted item lands in would otherwise depend on the GUI's own
layout-scan order rather than anything the recipe author actually chose:

```yaml
outputs:
  - slot: primary
    item: gold_nugget
    amount: 4
  - slot: byproduct
    item: iron_dust
    amount: 1
```

A `slot:` naming an id the matched GUI doesn't actually have logs a runtime warning and gives that
item directly to the player instead of being silently dropped (`GuiForceCraftEventFactory`).

### 9.3 Recipe Folder Structure

Recipes live in `resources/recipes/`. Sub-folders are supported for organisation:
- `recipes/crafting/` — crafting table recipes
- `recipes/alchemy/` — alchemy machine recipes
- `recipes/anvil/` — explicit `type: UPGRADE`/`TRANSMUTE` anvil recipes (`AnvilRecipeDefinition`,
  `AnvilRecipeParser`), consulted first by the unified anvil handler below.

### 9.4 Machine Definition Layer

`module/machine/` (`MachineModule`, id `machine`, registered right after `recipe`) loads
`machines/*.yml` — one entry per machine declaring `gui:` (which GUI it opens), `logic:`
(descriptive only — dispatch is still driven purely by the GUI's own `machine:` field),
`input-slots`/`output-slots`/optional `shape: ROWSxCOLS` (cross-validated at load time against the
target GUI's actual `INPUT`/`OUTPUT` component counts — logged as a warning, not a hard failure),
and an optional `open-triggers:` list, each entry a plain `ConditionParser` string (the same
condition language used by GUI/ability/quest conditions — `tag`, `health`, `hunger`, `location`,
`zone`, `block` (new: true when the player is looking at a block of the given material — a short
ray trace, not tied to actually clicking it), `variable`, `objective`, `quest`, `point`, or a raw
expression). `MachineOpenListener` evaluates every machine's trigger list (OR'd) on two occasions —
`PlayerInteractEvent` (right-click) and `ZoneEnterEvent` — and opens the GUI the first time any one
of them is true; no command needed. Machine parsing runs through `MachineDefinitionParser` (an
instance, not static, so it can reach `ScriptModule.getConditionParser()` — `machine` loads well
after `script` in the module order).

### 9.5 The Unified Anvil

Machine id `anvil` (`module/recipe/AnvilMachineHandler`) is the single dual-slot anvil (`base`/
`material` slots) implementing the full spec in
`docs/Valmora_Modifier_Framework_Design.docx`-adjacent design work — see
`docs/modules/design/recipe.md` for the full pipeline. One evaluation order per craft:

1. Explicit `recipes/anvil/*.yml` (`type: UPGRADE`/`TRANSMUTE`) — first match wins.
2. The modifier framework's `APPLY_MODIFIER`/`REMOVE_MODIFIER` recipes (reforges, gemstones, any
   group) — delegated to `ModifierModule.getAnvilHandler()`, never reimplemented, per the modifier
   framework's rule against group-specific engine code.
3. The standard combination engine — book+book/gear+book enchant merge, gear+gear enchant +
   durability merge, gear+repair-material durability repair.

A PDC-tracked "prior work" counter (`Keys.ANVIL_WORK_COUNT_KEY`) drives a `2^n - 1` XP-level
penalty (`AnvilCostCalculator`) on top of steps 1 and 3's base costs; step 2 keeps its own
independent `ValueResolver`-based cost. `keep-data-on-upgrade` (default `true`, also usable on any
ordinary crafting recipe via `upgrade-from: <ingredient key>`) carries enchants/modifier components/
durability/custom name from the upgraded item onto the result (`ItemDataCarrier`).

---

## 10. Script DSL Reference

The scripting system (`module/script/`) provides a mini-language for YAML configs. It is used in skill rewards, GUI event blocks, and item abilities.

### 10.1 Event String Syntax

```
<eventName> <arg1> <arg2> ... [notify] [delay:<ticks>]
```

- `notify` — sends a confirmation message to the player caster
- `delay:<time>` — schedules the event later: `20`/`20t` ticks, `1.5s` seconds, `1m` minutes
- Arguments with spaces go in double quotes (`\"` inside); factories declare `minArgs()`/`maxArgs()`/
  `usage()` so wrong argument counts are reported at load time

**Built-in events:**

| Event | Syntax | Effect |
|-------|--------|--------|
| `give` | `give <Material>:<amount>` | Gives item to player |
| `variable` | `variable set\|add\|remove <path> <value>` | Reads/writes a variable |
| `tag` | `tag add\|remove <tagName>` | Adds/removes a profile tag |
| `sound` | `sound <player\|world> <sound.key>` | Plays a sound |
| `gui_force_craft` | `gui_force_craft` | Triggers craft pipeline in current GUI |

### 10.2 Condition Syntax

Conditions appear as event strings starting with `condition`:

```yaml
- "condition $player.stat.HEALTH$ > 50"
- "condition $gui.input.ingredient.id$ != null"
- "condition $time.season$ == Summer"
```

When a `condition` fails, execution jumps to `fail-actions` (if present) and skips remaining `actions`.

**Operators:** `==`, `!=`, `>`, `<`, `>=`, `<=`

Condition strings (in `conditions:` lists) can combine keyword conditions (`tag`, `zone`, `health`,
`quest`, ... — `ConditionParser.registerKeyword` adds more) and expressions with `and`/`or`/`not`/`!`
and parentheses: `"tag vip or (zone hub and health 10)"`.

### 10.3 Expression & Variable Syntax

Variables are embedded in strings with `$namespace.path.path...$`:

```
$player.name$           → Player's display name
$player.stat.HEALTH$    → Health stat value (double)
$player.skill.mining.level$ → Skill level (int)
$player.var.strength$   → Custom profile variable
$time.season$           → Current season name (String)
$time.hour$             → Current hour 0–23 (int)
$time.is_day$           → true/false (boolean)
$gui.input.<id>.id$     → Valmora item ID in INPUT slot
$prop.<key>$            → Per-GUI session variable
$param.<key>$           → Mechanic/ability parameter
$range.<min>.<max>$     → Random int in range
$system.time$           → System.currentTimeMillis()
$server.online$         → Online player count
```

Expressions support arithmetic: `$param.level$*10`, `$player.stat.HEALTH$ + 50`, plus `%`, `!`/`not`,
short-circuit `and`/`or`, ternary, `'single'`/`"double"` strings with escapes, text joining with `+`,
and the functions in `FunctionRegistry` (math, `clamp`, `random`, `contains`, `lower`, `len`, `default`,
...; add-ons: `FunctionRegistry.register`). `ExpressionParser` is stateless/thread-safe; parse errors,
unknown functions and wrong argument counts are reported at load time. Unknown `$namespace.…$`
variables are reported once every module is up (`ScriptChecks`).

### 10.4 Adding New Events or Variables

**New event factory:**
```java
public class MyEvent implements EventFactory {
    @Override public String getName() { return "my_event"; }
    @Override public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> { /* impl */ };
    }
}
// Register in ScriptModule.onEnable():
registerEvent(new MyEvent());
```

**New variable provider:**
```java
public class MyProvider implements VariableProvider {
    @Override public String getNamespace() { return "mynamespace"; }
    @Override public Object resolve(String[] path, ExecutionContext context) { /* ... */ }
}
// Register in ScriptModule.onEnable():
registerProvider(new MyProvider());
```

---

## 11. Database Layer

- **Default:** SQLite (`plugins/Valmora/database.db`)
- **Optional:** MySQL via `config.yml` → `database.type: mysql`
- **Pool:** HikariCP 5.1.0
- All queries run through the async executor. Follow the async safety rule in §7.4.

When adding a new table or query, follow the existing DAO pattern in the `infrastructure` layer. Do not write raw JDBC in module classes.

---

## 12. Testing

Tests live in `src/test/java/org/nakii/valmora/`. The project uses JUnit 5 + Mockito.

- Mock `ValmoraAPI` and its sub-modules with `mock(ValmoraAPI.class)`.
- Call `ValmoraAPI.setProvider(mockApi)` in `@BeforeEach`.
- See `ExpressionTest.java` as the canonical example — it shows the correct mock setup pattern.
- Do **not** spin up a live server in unit tests. Use `DummyExecutionContext` stubs for `ExecutionContext`.

Run tests: `./gradlew test`

---

## 13. Common Mistakes to Avoid

| Mistake | Correct Approach |
|---------|-----------------|
| Registering listeners without unregistering in `onDisable()` | Always call `HandlerList.unregisterAll(listener)` |
| Registering commands inside a module's `onEnable()` | Register commands only in `Valmora.onEnable()` after modules are enabled |
| Calling Bukkit API from async threads | Schedule back to main thread via `runTask()` |
| Using `ChatColor` or `§` for text formatting | Use `Formatter.format()` or raw MiniMessage |
| Storing `ExecutionContext` as a field | Use it only within the current invocation scope |
| Accessing a module that loads after the current one at enable-time | Check module load order; restructure if needed |
| Writing raw JDBC outside the infrastructure layer | Use the existing DAO/executor pattern |
| Putting mutable state in the constructor instead of `onEnable()` | Always init state in `onEnable()`, reset in `onDisable()` |
| Using `Registry.get(key)` with mixed case | Always lowercase keys, or use the Registry's own case-insensitive helpers |
| Checking item display name in `InventoryClickEvent` to identify a GUI button | Store button identity in PDC — players can forge item names via anvil |

---

## 14. Paper API Hard Topics

This section documents Paper API areas where **AI training data is stale or wrong** due to breaking changes in 1.20.5–1.21.x. Read the relevant subsection before generating code in these areas.

---

### 14.1 Packets

Paper 1.20.5+ introduced significant internal packet structure changes when Mojang switched to data-driven items. This project **does** depend on and use **PacketEvents 2.x** (hard `depend` in `plugin.yml`) — not ProtocolLib (which has lagged on modern versions) — for NPC dialogue interception (`module/npc/dialogue/intercept/ConversationPacketManager.java`, wired up in `NpcModule`/`Valmora.java`). Corrected 2026-08-26; this section previously claimed no raw-packet usage existed.

NMS class path changed in Paper 1.21 — there is no version suffix:

```java
// WRONG — old versioned path
net.minecraft.server.v1_21_R1.EntityPlayer ep = ...; // ❌

// CORRECT — unified path in Paper 1.21+
net.minecraft.server.level.ServerPlayer sp =
    ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle();
```

---

### 14.2 Entity Pathfinding / Navigation

Paper 1.21 exposes a first-class `Pathfinder` API on `Mob`. Use it instead of raw NMS navigation:

```java
Mob mob = (Mob) entity;
Pathfinder pathfinder = mob.getPathfinder();
pathfinder.moveTo(targetLocation, 1.2); // speed multiplier
pathfinder.stopPathfinding();
boolean moving = pathfinder.hasPath();
```

Never touch `PathNavigation` via NMS directly — it breaks across minor versions.

---

### 14.3 Adventure / Component API (Text)

```java
// Item display names
meta.displayName(MiniMessage.miniMessage().deserialize("<red>My Item")); // ✓
meta.setDisplayName(ChatColor.RED + "My Item"); // ❌ deprecated

// Titles
player.showTitle(Title.title(
    Component.text("Title"),
    Component.text("Subtitle"),
    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(1000))
));
player.sendTitle("Title", "Subtitle", 10, 70, 20); // ❌ removed
```

---

### 14.4 Scheduler

```java
Bukkit.getScheduler().runTask(plugin, () -> { /* main thread */ });
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { /* async — no Bukkit API */ });
Bukkit.getScheduler().runTaskLater(plugin, () -> { /* main thread */ }, 20L);
```

Do **not** use `runTaskTimerAsynchronously` for anything that touches Bukkit state.

For entity-bound tasks use Paper's `EntityScheduler` so the task auto-cancels if the entity unloads:

```java
entity.getScheduler().runDelayed(plugin, task -> {
    entity.setFireTicks(0);
}, null, 100L);
```

---

### 14.5 ItemStack and PersistentDataContainer

PDC is the correct way to attach custom data to items and entities. Never use item lore or NBT string hacks to store data.

```java
NamespacedKey key = new NamespacedKey(plugin, "my_data");
meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "value");
String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
```

---

### 14.6 Command Registration (Brigadier API)

Paper 1.21 introduced a native Brigadier API under `io.papermc.paper.command.brigadier`. The old `plugin.yml` + `onCommand()` pattern still works. All Valmora commands are registered in `Valmora.onEnable()` after modules are enabled — keep new commands there too.

---

### 14.7 Entity Spawning

```java
// Preferred — spawn with consumer; entity is fully configured before first tick
Zombie zombie = world.spawn(location, Zombie.class, entity -> {
    entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
    entity.setHealth(40.0);
});
```

---

### 14.8 Events Agents Commonly Get Wrong in 1.21

| Incorrect / Old Usage | Correct 1.21 Pattern |
|-----------------------|----------------------|
| `PlayerInteractEvent` → check `getItem() != null` | Also check `event.getHand() == EquipmentSlot.HAND` to avoid double-firing |
| `EntityDamageByEntityEvent` to get attacker | Use `event.getDamageSource()` for full context including projectiles |
| `AsyncChatEvent` for chat handling | Use `io.papermc.paper.event.player.AsyncChatEvent` (Paper-specific) |

---

### 14.9 The Great 1.21 Enum Renames (Registries)

- **Attributes:** `Attribute.MAX_HEALTH`, `Attribute.ATTACK_DAMAGE` (not `GENERIC_*`)
- **Potion Effects:** `PotionEffectType.STRENGTH`, `PotionEffectType.HASTE` (not `INCREASE_DAMAGE`, `FAST_DIGGING`)
- **Enchantments:** `Enchantment.SHARPNESS` (not `DAMAGE_ALL`); prefer `Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"))` for dynamic lookups

---

### 14.10 Modern Damage API

Never use `entity.damage(amount)` without a `DamageSource`. Always build one:

```java
DamageSource source = DamageSource.builder(DamageType.MAGIC)
    .withDirectEntity(spellEntity)
    .withCausingEntity(caster)
    .build();
target.damage(10.0, source);
```

---

### 14.11 Item Components (Food, Tool, Jukebox)

As of 1.20.5+, items are component-based. Never use NBT strings to make an item edible:

```java
ItemMeta meta = item.getItemMeta();
FoodComponent food = meta.getFood();
food.setNutrition(5);
food.setSaturation(0.6f);
food.setCanAlwaysEat(true);
meta.setFood(food);
item.setItemMeta(meta);
```

---

### 14.12 Attribute Modifiers (Breaking Change in 1.21)

`AttributeModifier` no longer uses UUID. It requires a `NamespacedKey`:

```java
// WRONG — pre-1.21
new AttributeModifier(UUID.randomUUID(), "generic.attack_damage", 5.0, Operation.ADD_NUMBER); // ❌

// CORRECT
NamespacedKey key = new NamespacedKey(plugin, "bonus_damage");
AttributeModifier mod = new AttributeModifier(key, 5.0, AttributeModifier.Operation.ADD_NUMBER);
entity.getAttribute(Attribute.ATTACK_DAMAGE).addModifier(mod);
```

---

### 14.13 Floating Text / Holograms (Display Entities)

Never use `ArmorStand` for floating text. Use 1.19.4+ `Display` entities:

```java
TextDisplay display = world.spawn(loc, TextDisplay.class, entity -> {
    entity.text(MiniMessage.miniMessage().deserialize("<gold>Floating Text!"));
    entity.setBillboard(Display.Billboard.CENTER);
    entity.setDefaultBackground(false);
});
```

---

### 14.14 Potions (Removal of PotionData)

`PotionData` was removed in 1.20.5+. Use `setBasePotionType()` directly:

```java
PotionMeta meta = (PotionMeta) item.getItemMeta();
meta.setBasePotionType(PotionType.STRENGTH);
item.setItemMeta(meta);
```

---

### 14.15 Teleportation (Paper Async Chunk Loading)

Always use Paper's `teleportAsync()` when the target chunk may not be loaded:

```java
player.teleportAsync(distantLocation).thenAccept(success -> {
    if (success) player.sendMessage(Component.text("Woosh!", NamedTextColor.AQUA));
});
```

---

### 14.16 Smithing Recipes

As of 1.20, Smithing tables require three slots (Template + Base + Addition):

```java
RecipeChoice template = new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
Bukkit.addRecipe(new SmithingTransformRecipe(key, result, template, baseChoice, additionChoice));
```

---

### 14.17 Inventory Titles

`InventoryView` is now an interface in 1.21. Use the Component-based title method:

```java
Component title = event.getView().title(); // ✓
String title = event.getView().getTitle(); // ❌ deprecated/removed
```

Prefer checking PDC over matching titles to identify GUI windows (see §13).

---

### 14.18 Checking Material Types

Never hardcode lists of materials. Use Bukkit `Tag`s:

```java
if (Tag.LOGS.isTagged(mat)) { /* ... */ } // ✓
if (mat == Material.OAK_LOG || mat == Material.SPRUCE_LOG ...) { } // ❌
```

---

_Last updated: see git history._
