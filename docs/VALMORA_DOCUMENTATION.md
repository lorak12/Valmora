# Valmora Engine — Complete Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21  
> **Author:** nakii | **Group:** org.nakii.valmora

---

## Table of Contents

### Part 1 — Developer & Architecture Reference
1. [Project Overview](#1-project-overview)
2. [Startup Lifecycle](#2-startup-lifecycle)
3. [Module System](#3-module-system)
4. [How to Add a New Module](#4-how-to-add-a-new-module)
5. [ValmoraAPI — The Public Interface](#5-valmoraapi--the-public-interface)
6. [Registry System](#6-registry-system)
7. [YamlLoader — Config Loading Pattern](#7-yamlloader--config-loading-pattern)
8. [ExecutionContext — The Heart of the Engine](#8-executioncontext--the-heart-of-the-engine)
9. [Script Engine Internals](#9-script-engine-internals)
10. [Database Layer](#10-database-layer)
11. [Combat Engine Internals](#11-combat-engine-internals)
12. [Profile & PlayerState Data Model](#12-profile--playerstate-data-model)
13. [Stat System Internals](#13-stat-system-internals)
14. [Ability & Mechanic System Internals](#14-ability--mechanic-system-internals)
15. [Adding a Custom Ability Mechanic](#15-adding-a-custom-ability-mechanic)
16. [Adding a Custom Script Variable Provider](#16-adding-a-custom-script-variable-provider)
17. [Adding a Custom Script Event](#17-adding-a-custom-script-event)
18. [UI System Internals](#18-ui-system-internals)
19. [PersistentData Keys](#19-persistentdata-keys)
20. [Utility Classes](#20-utility-classes)

### Part 2 — Server Admin & User Reference
21. [Installation & Setup](#21-installation--setup)
22. [config.yml Reference](#22-configyml-reference)
23. [Items System — items/*.yml](#23-items-system--itemsyml)
24. [Mobs System — mobs/*.yml](#24-mobs-system--mobsyml)
25. [GUI System — gui/*.yml](#25-gui-system--guiyml)
26. [Stat Reference Table](#26-stat-reference-table)
27. [Skill Reference Table](#27-skill-reference-table)
28. [Command Reference](#28-command-reference)
29. [Permissions](#29-permissions)
30. [Damage Type Reference](#30-damage-type-reference)
31. [Rarity Reference Table](#31-rarity-reference-table)
32. [Script Variable Reference](#32-script-variable-reference)
33. [Script Event DSL Reference](#33-script-event-dsl-reference)
34. [Notify Module](#34-notify-module)
35. [Collections Module — collections/*.yml](#35-collections-module--collectionsyml)
36. [Slayer Module — slayers/*.yml](#36-slayer-module--slayersyml)
37. [Reforge Module — reforges/*.yml](#37-reforge-module--reforgesyml)
38. [Points System](#38-points-system)

---

# Part 1 — Developer & Architecture Reference

---

## 1. Project Overview

Valmora is a Paper MMORPG engine plugin. It provides a complete foundation for building RPG server experiences on top of Minecraft. The key systems it provides are:

- **Player Profiles** — Multi-slot character profiles with separate stats and skill progress, persisted to a database.
- **Stat System** — A set of numerical stats (Health, Damage, etc.) calculated dynamically from equipped items, persisted per-profile.
- **Custom Items** — YAML-defined items with stats, rarity, and multi-mechanic abilities.
- **Custom Mobs** — YAML-defined mobs with custom health, speed, damage, and equipment.
- **Ability System** — Trigger-driven ability execution (RIGHT_CLICK, PASSIVE) backed by composable Mechanic objects.
- **Skill System** — Nine levelled skills (Mining, Farming, Foraging, Fishing, Combat, Alchemy, Carpentry, Enchanting, Taming) with XP, level rewards, and milestones.
- **Combat Engine** — A fully custom damage pipeline that replaces vanilla damage with stat-driven, type-aware calculations and floating text damage indicators.
- **Script Engine** — Expression parser, condition evaluator, and event DSL for data-driven logic in YAML configs.
- **GUI Framework** — A layout-based inventory GUI system loaded entirely from YAML.
- **Notify System** — Multi-channel notification engine (chat, actionbar, title, bossbar, sound, advancement) with per-quest custom categories.
- **Collection System** — Tracks per-player item/action counts with staged unlock rewards.
- **Slayer System** — Tiered kill-challenge missions activated by players at a cost, culminating in a boss mob encounter.
- **Reforge System** — Applies rarity-scaled stat bonuses to custom items; supports stone-based and random forge machines.
- **Points System** — Free-form per-player numeric counters for quest rewards, progression gating, and scripting.
- **Database** — Async HikariCP-backed persistence via either SQLite or MySQL.
- **Module Manager** — A lifecycle-aware module registry that enables hot-reloading without server restart.

**Build Dependencies:**
| Dependency | Version | Scope |
|---|---|---|
| Paper API | 1.21.11-R0.1-SNAPSHOT | compileOnly |
| HikariCP | 5.1.0 | shaded |
| SQLite JDBC | 3.46.0.0 | shaded |
| MySQL Connector/J | 8.3.0 | shaded |
| Gson | 2.10.1 | shaded |

---

## 2. Startup Lifecycle

The `Valmora.onEnable()` method follows a strict initialization order. Understanding this order is critical for knowing when each subsystem is available.

```
onEnable()
 │
 ├── 1. instance = this
 ├── 2. ValmoraAPI.setProvider(this)
 ├── 3. new ModuleManager(this)
 ├── 4. saveDefaultConfig() + save example resource files
 ├── 5. Keys.init(this)             ← Initialize all NamespacedKeys
 │
 ├── 6. DatabaseFactory.createDataStore(this)   ← Reads config.yml -> type
 ├── 7. dataStore.init()            ← Creates SQL tables
 │
 ├── 8.  Instantiate all modules (fields in Valmora.java):
 │       ScriptModule, TimeModule, StatModule, PlayerManager, EconomyModule,
 │       UIManager, AbilityManager, ItemManager, MobManager, SkillModule,
 │       CombatModule, GuiModule, RecipeModule, AlchemyModule, EnchantModule,
 │       ZoneModule, ResourceModule, FishingModule, NpcModule, WarpModule, QuestModule
 │
 ├── 9.  moduleManager.registerModule(...)  ← 21 modules in dependency order
 ├── 10. moduleManager.enableModules()      ← onEnable() called on all
 │
 └── 11. Register Commands
```

**Shutdown order** in `onDisable()`:
1. `moduleManager.disableModules()` — in **reverse registration order**
2. Synchronous save of all active player sessions via `dataStore.savePlayer().join()`
3. `dataStore.close()` — shuts down the HikariCP pool

---

## 3. Module System

### `ReloadableModule` Interface

Every Valmora subsystem implements `ReloadableModule`:

```java
public interface ReloadableModule {
    void onEnable();   // Initialize: load configs, register listeners, start tasks
    void onDisable();  // Cleanup: unregister listeners, cancel tasks, clear caches
    String getId();    // Unique lowercase ID (e.g., "combat", "items")
    default String getName() { return getId(); }  // Human-readable name for logs
}
```

**Rules for a correct implementation:**
- `onEnable()` must be **idempotent** — calling it twice should not cause errors.
- `onDisable()` must completely undo everything `onEnable()` did. All Bukkit listeners must be unregistered with `HandlerList.unregisterAll(listener)`. All running BukkitTasks must be cancelled.
- **Never** register commands inside a module (commands are registered in `Valmora.onEnable()` after module enablement). Commands are not part of the hot-reload cycle.

### `ModuleManager`

`ModuleManager` is the lifecycle controller for all modules. It holds a `LinkedHashMap<String, ReloadableModule>` which preserves insertion order.

| Method | Description |
|---|---|
| `registerModule(module)` | Adds the module to the registry. Does not enable it. Key = `module.getId().toLowerCase()`. |
| `enableModules()` | Calls `onEnable()` on each module in registration order. Exceptions are caught and logged per-module — one failing module does not stop others. |
| `disableModules()` | Calls `onDisable()` in **reverse** registration order. |
| `reloadModules()` | Calls `disableModules()` then `enableModules()`. All modules are reloaded. |
| `reloadModule(id)` | Hot-reloads a single module by id: calls `onDisable()` then `onEnable()`. |
| `getModule(id)` | Returns the `ReloadableModule` for a given id, or null. |

---

## 4. How to Add a New Module

Follow these steps to add a new module to Valmora:

### Step 1: Create the module class

```java
package org.nakii.valmora.module.myfeature;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class MyFeatureModule implements ReloadableModule {

    private final Valmora plugin;
    private MyFeatureListener listener;

    public MyFeatureModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        // Load configs, register listeners, start tasks
        this.listener = new MyFeatureListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getLogger().info("My Feature Module enabled.");
    }

    @Override
    public void onDisable() {
        // Always unregister listeners
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }
        plugin.getLogger().info("My Feature Module disabled.");
    }

    @Override
    public String getId() {
        return "myfeature"; // must be lowercase, unique
    }

    @Override
    public String getName() {
        return "My Feature";
    }
}
```

### Step 2: Declare a field and instantiate in `Valmora.java`

```java
// In Valmora class:
private MyFeatureModule myFeatureModule;

// In onEnable(), after other modules, before moduleManager.registerModule() calls:
this.myFeatureModule = new MyFeatureModule(this);

// Register it:
moduleManager.registerModule(myFeatureModule);
```

### Step 3: Expose via `ValmoraAPI` (optional but recommended)

If other plugins or modules need to access your feature, add it to the API:

```java
// Add to ValmoraAPI interface:
MyFeatureModule getMyFeatureModule();

// Implement in Valmora.java:
@Override
public MyFeatureModule getMyFeatureModule() {
    return myFeatureModule;
}
```

### Step 4: Consider load order

The `LinkedHashMap` in `ModuleManager` preserves insertion order. Your module's `onEnable()` will fire after all previously registered modules. If your module depends on `ItemManager`, register it after `ItemManager`. If `ScriptModule` must process your config, register after `ScriptModule`.

**Current registration order:**
```
1.  ScriptModule      (script)
2.  TimeModule        (time)
3.  StatModule        (stat)
4.  PlayerManager     (player)
5.  EconomyModule     (economy)
6.  UIManager         (ui)
7.  AbilityManager    (ability)
8.  ItemManager       (items)
9.  MobManager        (mobs)
10. SkillModule       (skills)
11. CombatModule      (combat)
12. GuiModule         (gui)
13. RecipeModule      (recipe)
14. AlchemyModule     (alchemy)
15. EnchantModule     (enchants)
16. ZoneModule        (zone)
17. ResourceModule    (resource)
18. FishingModule     (fishing)
19. NpcModule         (npc)
20. WarpModule        (warp)
21. QuestModule       (quest)
22. NotifyModule      (notify)
23. CollectionModule  (collection)
24. SlayerModule      (slayer)
25. ReforgeModule     (reforge)
26. PointsModule      (points)
```

---

## 5. ValmoraAPI — The Public Interface

`ValmoraAPI` is the stable public-facing interface for the Valmora engine. It uses a static provider pattern — other plugins get the instance without needing to cast to the concrete `Valmora` class.

```java
ValmoraAPI api = ValmoraAPI.getInstance();
```

**Available accessors:**

| Method | Returns | Description |
|---|---|---|
| `getModuleManager()` | `ModuleManager` | The lifecycle manager for all modules. |
| `getScriptModule()` | `ScriptModule` | Access variable resolver, parsers, event factories. |
| `getTimeManager()` | `TimeManager` | RPG calendar — season, hour, day offset. |
| `getEconomy()` | `EconomyService` | Lightweight economy service interface. |
| `getEconomyModule()` | `EconomyModule` | Full economy module with shop/transaction logic. |
| `getPlayerManager()` | `PlayerManager` | Access player sessions, profiles, stat sync. |
| `getStatModule()` | `StatModule` | Save/load stat maps to/from ItemMeta. |
| `getStatRegistry()` | `StatRegistry` | Registry of all stat definitions. |
| `getSystemStats()` | `SystemStats` | Default stat values and metadata. |
| `getUIManager()` | `UIManager` | Access ChatUI, ActionBarUI, ScoreboardUI. |
| `getSkillManager()` | `SkillManager` | Query and modify player skill XP/levels. |
| `getItemManager()` | `ItemManager` | Create item stacks, query item registry. |
| `getMobManager()` | `MobManager` | Spawn mobs, query mob registry. |
| `getAbilityManager()` | `AbilityManager` | Access the MechanicRegistry. |
| `getDamageIndicatorManager()` | `DamageIndicatorManager` | Spawn floating damage text. |
| `getEnchantModule()` | `EnchantModule` | Access enchantment registry and helpers. |
| `getAlchemyManager()` | `AlchemyManager` | Access alchemy recipes and brew state. |
| `getZoneManager()` | `ZoneManager` | Query zones and their flags at a location. |
| `getNpcManager()` | `NpcManager` | Spawn/query custom NPCs. |
| `getDialogueManager()` | `DialogueManager` | Load and trigger NPC dialogues. |
| `getWarpManager()` | `WarpManager` | Register/use named warp points. |
| `getQuestManager()` | `QuestManager` | Track player quest state and progress. |

---

## 6. Registry System

Valmora provides a generic `Registry<T>` interface and a thread-safe `SimpleRegistry<T>` implementation. **All registry keys are case-insensitive** (stored as lowercase).

```java
public interface Registry<T> {
    void register(String id, T entry);
    T unregister(String id);
    Optional<T> get(String id);
    boolean contains(String id);
    Set<String> getKeys();
    Collection<T> values();
    void clear();
    int size();
}
```

Concrete registries that extend `SimpleRegistry`:
- `ItemRegistry` — stores `ItemDefinition` objects
- `MobRegistry` — stores `MobDefinition` objects
- `MechanicRegistry` — stores `AbilityMechanic` objects (keyed by type string, stored UPPERCASE)

**Pattern for loading:** On `onEnable()`, call `registry.clear()` before loading to prevent stale entries on reload.

---

## 7. YamlLoader — Config Loading Pattern

`YamlLoader<T>` is a generic utility that scans all `.yml` files in a plugin subdirectory and calls a user-supplied parser lambda for each top-level key in each file.

```java
YamlLoader<ItemDefinition> loader = new YamlLoader<>(plugin, "items", "items");

loader.load(
    (id, section, filePath) -> ItemDefinitionParser.parse(id, section, filePath, mechanicRegistry),
    registry::registerItem
);
```

**Behavior:**
- Creates the folder if it doesn't exist.
- Iterates every `.yml` file in the folder.
- For each file, iterates every top-level YAML key. The key becomes the object's `id`.
- Calls the `SectionParser` lambda. If it returns `LoadResult.success(value)`, the register action is called. If it returns `LoadResult.failure(errorMsg)`, the error is collected.
- After all files are processed, any errors are printed as a batch warning to the console with file paths for easy debugging.
- Logs the total count of successfully loaded objects.

### `LoadResult<T, E>`

A simple discriminated union:
```java
LoadResult.success(value)     // isSuccess() == true, getValue() returns value
LoadResult.failure(errorMsg)  // isSuccess() == false, getError() returns message
```

---

## 8. ExecutionContext — The Heart of the Engine

`ExecutionContext` is the context object passed into any mechanic, event, condition, or expression evaluation. It carries all runtime state for a single execution.

```java
public interface ExecutionContext {
    LivingEntity getCaster();                     // Who triggered this
    Optional<Player> getPlayerCaster();           // Convenience: getCaster() as Player
    Optional<LivingEntity> getTarget();           // Who is targeted
    Location getLocation();                       // Where the action occurs
    ConfigurationSection getParams();             // YAML params for this mechanic
    VariableResolver getVariableResolver();       // Resolves $variable.path$ expressions
    TagService getTagService();                   // Add/remove/check tags on the caster

    // Typed param helpers with defaults:
    double getDouble(String key, double def);
    int getInt(String key, int def);
    String getString(String key, String def);
    boolean getBoolean(String key, boolean def);
}
```

**`SimpleExecutionContext`** is the concrete implementation used throughout the engine. It pulls its `VariableResolver` from `ValmoraAPI.getInstance().getScriptModule().getVariableResolver()` and creates a new `TagServiceImpl` per context.

**Creating a context manually (for custom mechanics or tests):**
```java
ExecutionContext ctx = new SimpleExecutionContext(
    casterEntity,      // LivingEntity - required
    targetEntity,      // LivingEntity - nullable
    location,          // Location
    paramsSection      // ConfigurationSection with mechanic params
);
```

---

## 9. Script Engine Internals

The `ScriptModule` (id: `"script"`) initializes and wires together all scripting components.

### 9.1 Expression Parser

Parses a string into an AST (`Expression`) that can be evaluated against an `ExecutionContext`. The parser implements recursive descent parsing with full operator precedence.

**Token types supported:**
- `$namespace.path$` — dynamic variable reference
- `123` or `123.45` — number literal
- `"hello"` — string literal
- `true` / `false` — boolean literals
- Comparison operators: `==`, `!=`, `>`, `<`, `>=`, `<=`
- Arithmetic operators: `+`, `-`, `*`, `/`
- Grouping: `(expr)`
- Ternary: `condition ? trueVal : falseVal`

**Operator precedence (high to low):**
1. Primary (literals, variables, grouped)
2. Multiplication / Division (`*`, `/`)
3. Addition / Subtraction (`+`, `-`)
4. Comparison (`==`, `!=`, `>`, `<`, `>=`, `<=`)
5. Ternary (`? :`)

**AST Node types:**
- `LiteralNode` — holds a constant value
- `VariableNode` — resolves at runtime via `VariableResolver`
- `BinaryOpNode` — applies an operator to two sub-expressions
- `TernaryNode` — conditional branch

### 9.2 Variable Resolver

Variables use the format `$namespace.path.subpath$`. Resolution is delegated to a `VariableProvider` registered under the matching namespace.

**Built-in providers:**

| Namespace | Variable | Returns |
|---|---|---|
| `player` | `$player.name$` | Player's display name (String) |
| `player` | `$player.stat.HEALTH$` | Current stat value (Double) |
| `player` | `$player.stat.DAMAGE$` | Current stat value (Double) |
| `player` | `$player.var.myVar$` | Custom profile variable (Object) |
| `world` | `$world.name$` | World name (String) |
| `world` | `$world.dimension$` | World environment (String) |
| `system` | `$system.time$` | Current Unix time in ms (Long) |

All stat names from the `Stat` enum are valid sub-paths under `$player.stat.*`.

### 9.3 Condition Parser

Parses strings or lists of strings into `Condition` objects.

**Condition string formats:**
```
tag <tagName>          → TagCondition: checks if caster's profile has the tag
<expression>           → ExpressionCondition: evaluates expression, must result in Boolean
```

**List behavior:** Multiple condition strings in a YAML list are combined with AND logic via `ConditionGroup`. All conditions must evaluate to `true`.

```java
// Parsing in code:
ConditionParser parser = api.getScriptModule().getConditionParser();
Condition c = parser.parse("$player.stat.HEALTH$ > 50");
Condition group = parser.parseList(List.of("tag quest_started", "$player.stat.MANA$ > 10"));
```

### 9.4 Event Parser & DSL

Events are strings parsed into `CompiledEvent` objects. They follow this grammar:

```
<eventName> <arg1> [<arg2> ...] [notify] [delay:<ticks>]
```

**Built-in event factories:**

| Event Name | Syntax | Effect |
|---|---|---|
| `give` | `give <MATERIAL:AMOUNT>` | Gives items to the caster player. |
| `give` (with notify) | `give STONE:10 notify` | Gives items and sends a chat message. |
| `tag` | `tag add <tagName>` | Adds a tag to the active profile. |
| `tag` | `tag remove <tagName>` | Removes a tag from the active profile. |
| `variable` | `variable set player.var.coins 100` | Sets a custom variable. |
| `variable` | `variable add player.var.coins 50` | Adds to a numeric variable. |
| `variable` | `variable remove player.var.myFlag` | Removes a variable entry. |

**Event options** (appended to any event string):
- `notify` — Sends a notification message to the player on execution.
- `delay:<ticks>` — Schedules the event to run after N server ticks (e.g., `delay:20` = 1 second).

```yaml
# Example usage in YAML:
on-complete:
  - "give DIAMOND:5 notify"
  - "tag add quest_complete"
  - "variable set player.var.completed_quests 1"
  - "give EMERALD:10 delay:40 notify"
```

---

## 10. Database Layer

### `DataStore` Interface

```java
void init();                                          // Create tables
CompletableFuture<ValmoraPlayer> loadPlayer(UUID);    // Async load
CompletableFuture<Void> savePlayer(ValmoraPlayer);    // Async save (transactional)
void close();                                         // Shutdown pool
```

### `DatabaseFactory`

Reads `config.yml → database.type` and creates either `SqliteDataStore` or `MySqlDataStore`.

### Schema

**Table: `valmora_players`**
| Column | Type | Notes |
|---|---|---|
| `uuid` | TEXT (PK) | Player UUID string |
| `active_profile` | TEXT | UUID of the currently active profile |

**Table: `valmora_profiles`**
| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (PK) | Profile UUID string |
| `player_uuid` | TEXT (FK) | Owner's UUID |
| `name` | TEXT | Profile name (e.g., "Default") |
| `stats` | TEXT | JSON map of `Stat → Double` |
| `skills` | TEXT | JSON map of `Skill → Double` (XP values) |
| `player_state` | TEXT | JSON double[2]: `[currentHealth, currentMana]` |

Save operations use `INSERT … ON CONFLICT DO UPDATE` (SQLite) or `INSERT … ON DUPLICATE KEY UPDATE` (MySQL), inside a single transaction for atomicity.

The database uses a dedicated single-thread `ExecutorService` for all async operations, preventing concurrent write corruption. On `close()`, the executor shuts down gracefully with a 10-second timeout before forcing termination.

---

## 11. Combat Engine Internals

### Combat Pipeline

When an entity is damaged by another entity, `CombatListener.onEntityDamageByEntity()` fires at `HIGHEST` priority:

1. **Skip players as victims** — player-vs-player combat is not currently handled by Valmora.
2. **Invulnerability check** — if `noDamageTicks > maxNoDamageTicks / 2`, cancel the event (prevents rapid repeat hits).
3. **Set vanilla damage to 0** — Valmora takes complete control of all damage numbers.
4. **Determine attacker** — either a direct `LivingEntity` or the shooter of a `Projectile`.
5. **Determine damage type** — Arrow/MOB_PROJECTILE cause = `PROJECTILE`, otherwise `MELEE`.
6. **Call `DamageCalculator.calculateDamage()`** — produces a `DamageResult`.
7. **Call `damageResult.apply()`** — applies health reduction and combat state.
8. **Spawn damage indicator** — floating text above the victim.

Environmental damage (fall, fire, etc.) goes through `onEntityDamage()`. Vanilla base damage is **scaled by 5.0** to be meaningful against custom health pools, then the victim's defense is applied.

### Damage Formula

```
fullDamage = baseDamage × (1 + strength / 100)

if isCritical (random < critChance / 100):
    fullDamage = fullDamage × (1 + critDamage / 100)

defenseMultiplier = 100 / (defense + 100)

finalDamage = floor(fullDamage × defenseMultiplier)
```

**Defense bypass:** `VOID`, `DROWNING`, and `FALL` damage types bypass defense entirely (`defenseMultiplier = 1.0`). `TRUE` damage type (used in ability mechanics) skips the whole formula and applies the raw amount directly.

### Applying Damage to Players

`DamageApplier.applyDamage()`:
1. Reduces `PlayerState.currentHealth` by `finalDamage`.
2. Syncs visual hearts: `percentage = currentHealth / maxHealth`, maps to 0–20 vanilla HP.
3. Sets the player in combat (`lastCombatTime = System.currentTimeMillis()`). Combat expires after **3 seconds** of no damage taken.
4. Sets `noDamageTicks = 20` to enforce invulnerability frames.

### Regeneration

`RegenTask` runs every **20 ticks (1 second)** on the main thread and ticks every online player:

- **Health Regen:** Only while **NOT in combat**. Heals `HEALTH_REGEN` stat value per second.
- **Mana Regen:** Restores `MANA_REGEN` stat value per second regardless of combat state.

### Damage Indicator

`DamageIndicatorManager` spawns a `TextDisplay` entity at the victim's eye level with a small random offset. It lives for **1 second (20 ticks)** then is removed. A rate limit of **1 indicator per 400ms per entity** prevents indicator spam from DoT effects.

Critical hits display: `✧ <bold>DAMAGE ✧` in gold. Normal hits display colored damage numbers based on `DamageType.getColor()`.

---

## 12. Profile & PlayerState Data Model

### Object Hierarchy

```
ValmoraPlayer                    (per online player, identified by UUID)
 └── Map<UUID, ValmoraProfile>   (multiple profiles per player)
      └── (active profile) ValmoraProfile
           ├── StatManager        (current effective stats)
           ├── SkillManager       (skill XP map)
           ├── PlayerState        (current health, mana, combat timer)
           ├── CooldownManager    (ability cooldowns)
           ├── Set<String> tags   (flag strings for scripting)
           └── Map<String, Object> variables  (custom key-value store)
```

### `PlayerState`

| Field | Type | Default | Description |
|---|---|---|---|
| `currentHealth` | double | `Stat.HEALTH.defaultValue` (100.0) | Current HP. |
| `currentMana` | double | `Stat.MANA.defaultValue` (100.0) | Current Mana. |
| `lastCombatTime` | long | 0 (transient) | Timestamp of last damage taken. Not persisted. |

`isInCombat()` returns `true` if `System.currentTimeMillis() - lastCombatTime < 3000`.

`getSaveData()` returns `double[]{currentHealth, currentMana}`, stored as JSON in the DB.

### Player Lifecycle

1. **Join** (`PlayerConnectionListener.onPlayerJoin`): `PlayerManager.handleJoin(uuid)` is called asynchronously. The DB is queried. If no record exists, a brand-new `ValmoraPlayer` with a "Default" profile is created. Once loaded, `StatManager.recalculateAttributes()` and `recalculateStats()` are called on the main thread.
2. **Quit** (`onPlayerQuit`): Player is removed from `activeSession` and saved asynchronously.
3. **Hot-reload**: All currently online players are loaded synchronously (blocking) to avoid async gap NPEs.

### Profile Switching

`PlayerManager.switchProfile(player, profileName)` finds the profile by name, calls `setActiveProfile()`, and immediately recalculates stats for the new profile's equipment context.

---

## 13. Stat System Internals

### `Stat` Enum

| Enum Key | Display Name | Default | Color |
|---|---|---|---|
| `DAMAGE` | Damage | 5.0 | `<red>` |
| `HEALTH` | Health | 100.0 | `<red>` |
| `STRENGTH` | Strength | 0.0 | `<red>` |
| `DEFENSE` | Defense | 0.0 | `<green>` |
| `CRIT_CHANCE` | Crit Chance | 30.0 | `<yellow>` (max: 100) |
| `CRIT_DAMAGE` | Crit Damage | 50.0 | `<yellow>` |
| `SPEED` | Speed | 100.0 | `<white>` |
| `MANA` | Mana | 100.0 | `<aqua>` |
| `HEALTH_REGEN` | Health Regen | 1.0 | `<red>` |
| `MANA_REGEN` | Mana Regen | 2.0 | `<aqua>` |

### Stat Recalculation

`StatManager.recalculateStats(player)` is called on every equipment-changing event (join, respawn, armor change, held item change, hand swap, inventory click on armor slots):

1. **Reset** all stats to their `Stat.defaultValue`.
2. **Strip** any infinite-duration potion effects (duration > 1 hour) — prevents passive abilities from stacking across reloads.
3. **Read** main hand, off-hand, and all 4 armor slots.
4. For each item with an `ITEM_ID_KEY` PersistentData tag, load stat map from `StatModule` and add each stat to the current total.
5. Execute all **PASSIVE** ability mechanics on the player.
6. **Recalculate attributes** — `Attribute.MOVEMENT_SPEED` is set to `(0.1 × SPEED) / 100`.

### Saving Stats to Items

`StatModule.saveStats(ItemMeta, Map<Stat, Double>)` serializes the stats map to JSON and stores it in the item's PersistentDataContainer under `Keys.STATS_CONTAINER_KEY`.

`StatModule.loadStats(ItemMeta)` deserializes and returns the map.

---

## 14. Ability & Mechanic System Internals

### `AbilityMechanic` Interface

```java
public interface AbilityMechanic {
    String getId();                          // e.g., "DAMAGE", "HEAL", "APPLY_EFFECT"
    void execute(ExecutionContext context);   // Perform the mechanic's action
}
```

### `AbilityManager`

Holds a `MechanicRegistry` and registers all built-in mechanics in its constructor. Accessible via `ValmoraAPI.getInstance().getAbilityManager().getMechanicRegistry()`.

### Built-in Mechanics

**`DAMAGE`**
- Reads `params.amount` (double) and `params.type` (DamageType string, default: `MAGIC`).
- Calls `DamageCalculator.calculateDamage(caster, target, damageType, amount)`.
- Spawns a damage indicator.

**`HEAL`**
- Reads `params.heal` (double) and `params.target` (`@player` or `@target`).
- Heals the specified target by calling `PlayerState.heal()` and syncing visual health.
- Currently only works on players.

**`APPLY_EFFECT`**
- Reads: `params.effect` (potion effect name, e.g., `slowness`), `params.duration` (seconds, `-1` for infinite/`PASSIVE`), `params.amplifier` (1-based; 1 = Level I), `params.hide-particles` (bool), `params.target`.
- Creates a `PotionEffect` and applies it to the target.
- Amplifier note: config value `1` = Bukkit amplifier `0` = "Level I".

### `ConfiguredMechanic`

Bundles an `AbilityMechanic` with a pre-loaded `ConfigurationSection` (params). Its `execute(caster, target)` method creates a `SimpleExecutionContext` and calls `mechanic.execute(context)`.

### Ability Triggers

| Trigger | Behavior |
|---|---|
| `RIGHT_CLICK` | Fires when the player right-clicks while holding the item. Requires a target within `target-range` blocks. Consumes mana and starts cooldown. |
| `PASSIVE` | Fires during `StatManager.recalculateStats()` (every equipment change). No mana or cooldown. |

### Ability Execution Flow (RIGHT_CLICK)

1. `AbilityListener` catches `PlayerInteractEvent` (RIGHT_CLICK_AIR or RIGHT_CLICK_BLOCK).
2. Reads `ITEM_ID_KEY` from held item PersistentData → looks up `ItemDefinition`.
3. For each ability with `trigger == RIGHT_CLICK`:
   - Check cooldown via `CooldownManager`.
   - Check mana in `PlayerState`.
   - Find closest `LivingEntity` within `targetRange` blocks.
   - Deduct mana, set cooldown.
   - Execute all `ConfiguredMechanic` instances in sequence.

---

## 15. Adding a Custom Ability Mechanic

1. **Create the class:**
```java
package org.nakii.valmora.module.item.impl;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.AbilityMechanic;

public class LaunchMechanic implements AbilityMechanic {

    @Override
    public String getId() {
        return "LAUNCH"; // Will be referenced in YAML as type: "LAUNCH"
    }

    @Override
    public void execute(ExecutionContext context) {
        double power = context.getDouble("power", 1.5);
        context.getTarget().ifPresent(target -> {
            target.setVelocity(context.getCaster().getLocation()
                .getDirection()
                .multiply(power));
        });
    }
}
```

2. **Register it** in `AbilityManager`'s `onEnable()` or constructor:
```java
mechanicRegistry.registerMechanic(new LaunchMechanic());
```

3. **Use it in YAML:**
```yaml
my_sword:
  name: "Launcher"
  material: IRON_SWORD
  abilities:
    blast_away:
      trigger: "RIGHT_CLICK"
      target-range: 5.0
      mechanics:
        - type: "LAUNCH"
          params:
            power: 2.0
```

---

## 16. Adding a Custom Script Variable Provider

```java
public class EconomyVariableProvider implements VariableProvider {
    @Override
    public String getNamespace() { return "eco"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        if (path[0].equalsIgnoreCase("coins")) {
            // Return player coins from your economy system
            return context.getPlayerCaster()
                .map(p -> MyEconomy.getCoins(p.getUniqueId()))
                .orElse(0.0);
        }
        return null;
    }
}
```

Register in your module's `onEnable()`:
```java
ValmoraAPI.getInstance().getScriptModule()
    .getVariableProviderRegistry()
    .register("eco", new EconomyVariableProvider());
```

Now `$eco.coins$` works in all expressions and conditions.

---

## 17. Adding a Custom Script Event

```java
public class TeleportEventFactory implements EventFactory {
    @Override
    public String getName() { return "teleport"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        // args[0] = world name, args[1-3] = x y z (optional)
        String worldName = args.length > 0 ? args[0] : "world";
        return context -> {
            context.getPlayerCaster().ifPresent(player -> {
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world != null) {
                    player.teleport(world.getSpawnLocation());
                }
            });
        };
    }
}
```

Register in `onEnable()`:
```java
ValmoraAPI.getInstance().getScriptModule()
    .getEventFactoryRegistry()
    .register("teleport", new TeleportEventFactory());
```

Usage in YAML:
```yaml
on-complete:
  - "teleport world_hub"
```

---

## 18. UI System Internals

`UIManager` (id: `"ui"`) manages three UI sub-components and runs a **repeating task every 2 ticks** (10 times/second) to tick the ActionBar for smooth display overriding.

### ChatUI

Sends styled chat messages. Current implementation:
- `sendLevelUp(Player, Skill, int newLevel)` — sends a level-up announcement to the player.

### ActionBarUI

Manages the action bar for all online players. Supports two modes:
- **Permanent message** — stays until replaced.
- **Temporary message** — displayed for N ticks, then reverts to the permanent message.

Runs via the 2-tick UI clock task on `UIManager.onEnable()`.

### ScoreboardUI

Provides a per-player scoreboard with support for **dynamic sections** — plugin systems can inject a set of lines into the scoreboard that will be rendered in a designated area.

`DynamicSection(List<String> lines, boolean locked)` — a locked section cannot be overwritten by other systems.

> **Note:** The scoreboard rendering loop is currently commented out in the UI clock. The `tick(player)` method contains a pseudocode comment showing the intended assembly pattern. The infrastructure is in place; only the FastBoard/Objective integration needs to be wired.

---

## 19. PersistentData Keys

All `NamespacedKey` values are stored in `org.nakii.valmora.util.Keys` and initialized in `Keys.init(plugin)` during startup.

| Field | Key String | Type | Used On |
|---|---|---|---|
| `ITEM_ID_KEY` | `valmora:valmora_item_id` | `STRING` | ItemMeta — identifies a Valmora item |
| `RARITY_KEY` | `valmora:rarity` | `STRING` | ItemMeta — stores the rarity enum name |
| `ITEM_TYPE_KEY` | `valmora:item_type` | `STRING` | ItemMeta — stores `ItemType` enum name |
| `STATS_CONTAINER_KEY` | `valmora:item_stats_container` | `STRING` | ItemMeta — JSON-encoded stat map |
| `MOB_ID_KEY` | `valmora:valmora_mob_id` | `STRING` | Entity PDC — identifies a Valmora mob |

**Checking if an item is a Valmora item:**
```java
String itemId = itemMeta.getPersistentDataContainer()
    .get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
boolean isValmoraItem = itemId != null;
```

---

## 20. Utility Classes

### `Formatter`

`org.nakii.valmora.util.Formatter` wraps MiniMessage for all text formatting.

```java
Component c = Formatter.format("<red>Hello <white>World");
List<Component> lore = Formatter.formatList(List.of("<gray>Line 1", "<gray>Line 2"));
String s = Formatter.capitalize("hello"); // → "Hello"
```

All text rendered through `Formatter` has **italic decoration disabled by default** via a MiniMessage post-processor. This is important for item lore which is italic by default in vanilla Minecraft.

**MiniMessage tags supported:** Full MiniMessage 4.x tag set — `<red>`, `<bold>`, `<gold>`, `<#RRGGBB>`, `<gradient:...>`, `<rainbow>`, etc.

---

# Part 2 — Server Admin & User Reference

---

## 21. Installation & Setup

**Requirements:**
- Paper 1.21.x server
- Java 21

**Steps:**
1. Drop `Valmora-0.1.jar` into your `plugins/` folder.
2. Start the server once. Valmora will generate its default configuration files.
3. Stop the server.
4. Edit `plugins/Valmora/config.yml` to configure your database (SQLite by default, zero setup required).
5. Restart the server.

**Default files created on first run:**
```
plugins/Valmora/
├── config.yml
├── items/
│   └── example.yml     ← Example items with abilities
├── mobs/
│   └── test_mobs.yml   ← Example custom mobs
└── gui/
    └── forge.yml       ← Example crafting GUI
```

---

## 22. config.yml Reference

```yaml
database:
  # Which database engine to use.
  # Options: sqlite, mysql
  type: sqlite

  # Only used when type is 'mysql'.
  # mysql:
  #   host: "127.0.0.1"
  #   port: 3306
  #   database: "valmora"
  #   username: "root"
  #   password: "password123"
```

| Field | Type | Default | Description |
|---|---|---|---|
| `database.type` | String | `sqlite` | `sqlite` uses a local `database.db` file. `mysql` requires the block below. |
| `database.mysql.host` | String | — | MySQL server hostname or IP address. |
| `database.mysql.port` | Integer | — | MySQL port (typically 3306). |
| `database.mysql.database` | String | — | Database/schema name. Must exist and the user must have all privileges on it. |
| `database.mysql.username` | String | — | MySQL user. |
| `database.mysql.password` | String | — | MySQL password. |

**SQLite** is recommended for single-server setups. It requires no external software and stores all data in `plugins/Valmora/database.db`.

**MySQL** is recommended for networks with multiple servers sharing player data.

---

## 23. Items System — items/*.yml

Place any number of `.yml` files inside `plugins/Valmora/items/`. Each top-level key in a file defines one item. The key becomes the item's ID (case-insensitive).

### Full Item Schema

```yaml
<item-id>:
  name: "<display name with MiniMessage>"      # Required-ish (shown in item name)
  material: "<BUKKIT_MATERIAL>"                # REQUIRED. e.g., DIAMOND_SWORD
  rarity: "<RARITY>"                           # Optional. Default: COMMON
  item-type: "<ITEM_TYPE>"                     # Optional. Default: NONE
  lore:                                        # Optional list of lore lines
    - "<line one>"
    - "<line two>"
  stats:                                       # Optional stat bonuses
    STAT_NAME: <number>
  abilities:                                   # Optional map of abilities
    <ability-id>:
      name: "<display name>"
      trigger: "<TRIGGER>"
      target-range: <number>                   # Blocks. Required for RIGHT_CLICK.
      cooldown: <number>                       # Seconds. Default 0.
      mana-cost: <number>                      # Mana units. Default 0.
      description:                             # Optional lore lines shown on item
        - "<line>"
      mechanics:                               # Ordered list of effects
        - type: "<MECHANIC_TYPE>"
          params:
            <key>: <value>
```

### Field Reference

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | String (MiniMessage) | Recommended | Displayed as the item's name with rarity color prepended automatically. |
| `material` | String | **Yes** | Any Bukkit `Material` name (e.g., `DIAMOND_SWORD`, `BLAZE_ROD`). |
| `rarity` | String | No | `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`, `MYTHIC`. Default: `COMMON`. |
| `item-type` | String | No | `SWORD`, `BOW`, `ARMOR`, `NONE`. Default: `NONE`. Stored in NBT. |
| `lore` | List of Strings | No | Custom lore lines shown before stats. Supports MiniMessage. |
| `stats` | Map | No | Keys are `Stat` enum names (see §26). Values are numbers (positive or negative). |
| `abilities` | Map | No | Each key is a unique ability ID within this item. |

### Ability Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | String | Yes | Human-readable ability name shown in item lore. |
| `trigger` | String | Yes | `RIGHT_CLICK` or `PASSIVE`. |
| `target-range` | Double | For RIGHT_CLICK | Max distance in blocks to find a target. |
| `cooldown` | Double | No | Cooldown in seconds. `0` = no cooldown. |
| `mana-cost` | Double | No | Mana cost per use. `0` = free. |
| `description` | List | No | Lore lines describing the ability. Supports MiniMessage. |
| `mechanics` | List | No | The ordered list of effects to execute. |

### Mechanic: `DAMAGE`

```yaml
- type: "DAMAGE"
  params:
    amount: 80.0          # Raw damage before stat scaling
    type: "MAGIC"         # DamageType (see §30). Default: MAGIC
    target: "@target"     # Always @target for DAMAGE mechanic
```

### Mechanic: `HEAL`

```yaml
- type: "HEAL"
  params:
    heal: 30.0            # HP to restore
    target: "@player"     # @player = self, @target = enemy
```

### Mechanic: `APPLY_EFFECT`

```yaml
- type: "APPLY_EFFECT"
  params:
    effect: "slowness"    # Potion effect name (lowercase, vanilla key)
    duration: 3.0         # Seconds. Use -1 for infinite (PASSIVE abilities).
    amplifier: 2          # 1-based: 1=Level I, 2=Level II, etc.
    hide-particles: false # Hide potion particles. Default: false.
    target: "@target"     # @player or @target
```

### Complete Item Examples

```yaml
# A simple sword with stats
my_sword:
  name: "Ironbreaker"
  material: IRON_SWORD
  rarity: UNCOMMON
  item-type: SWORD
  stats:
    DAMAGE: 25
    STRENGTH: 10
    CRIT_CHANCE: 15

# A staff with a targeted damage ability
frost_staff:
  name: "Staff of Ice"
  material: BLAZE_ROD
  rarity: EPIC
  item-type: NONE
  stats:
    MANA: 200
    MANA_REGEN: 10
  abilities:
    frost_bolt:
      name: "Frost Bolt"
      trigger: "RIGHT_CLICK"
      target-range: 15.0
      cooldown: 2.5
      mana-cost: 45.0
      description:
        - "<gray>Fires a shard of ice, dealing <aqua>80 Magic Damage"
        - "<gray>and slowing the target for 3 seconds."
      mechanics:
        - type: "DAMAGE"
          params:
            amount: 80.0
            type: "MAGIC"
            target: "@target"
        - type: "APPLY_EFFECT"
          params:
            effect: "slowness"
            duration: 3.0
            amplifier: 2
            target: "@target"

# Armor with a passive permanent effect
fallen_chestplate:
  name: "Aegis of the Fallen"
  material: NETHERITE_CHESTPLATE
  rarity: LEGENDARY
  item-type: ARMOR
  stats:
    HEALTH: 500
    DEFENSE: 150
    SPEED: -10
  abilities:
    undying_will:
      name: "Undying Will"
      trigger: "PASSIVE"
      description:
        - "<gray>While worn, grants <blue>Resistance I<gray>."
      mechanics:
        - type: "APPLY_EFFECT"
          params:
            effect: "resistance"
            duration: -1        # Infinite, re-applied on every stat recalculation
            amplifier: 1
            hide-particles: true
            target: "@player"
```

---

## 24. Mobs System — mobs/*.yml

Place any number of `.yml` files inside `plugins/Valmora/mobs/`. Each top-level key defines one mob definition. The key becomes the mob's ID.

### Full Mob Schema

```yaml
<mob-id>:
  name: "<display name with MiniMessage>"    # Optional. Shown above mob with health.
  type: <ENTITY_TYPE>                        # REQUIRED. Bukkit EntityType enum name.
  health: <number>                           # Optional. Default: 20.
  damage: <number>                           # Optional. Default: 0.
  speed: <number>                            # Optional. Vanilla attribute value (0.25 = normal).
  level: <integer>                           # Optional. Default: 1. Shown in nameplate.
  equipment:
    helmet: <material or item-id>
    chestplate: <material or item-id>
    leggings: <material or item-id>
    boots: <material or item-id>
    main-hand: <material or item-id>
    off-hand: <material or item-id>
```

### Field Reference

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | String (MiniMessage) | No | Shown as a custom nameplate. The nameplate also shows current HP / max HP. |
| `type` | String | **Yes** | Any `EntityType` enum value: `ZOMBIE`, `SKELETON`, `CREEPER`, `SPIDER`, etc. |
| `health` | Double | No | Max HP in Valmora units. Default: 20. |
| `damage` | Double | No | Damage dealt on hit. |
| `speed` | Double | No | Vanilla movement speed attribute. Default (vanilla zombie): `~0.23`. Normal walk: `0.25`. |
| `level` | Integer | No | Shown in nameplate. Does not affect stats automatically. Default: 1. |
| `equipment` | Section | No | Equipment slots. Values can be vanilla material names or Valmora item IDs. |

### Equipment Fields

| Slot | Armor Array Index |
|---|---|
| `helmet` | Index 3 |
| `chestplate` | Index 2 |
| `leggings` | Index 1 |
| `boots` | Index 0 |
| `main-hand` | Weapon slot |
| `off-hand` | Off-hand slot |

Equipment values accept either a vanilla `Material` name (e.g., `IRON_SWORD`) or a Valmora custom item ID (e.g., `glacial_staff`).

### Mob Nameplate Format

The nameplate is automatically formatted as:
```
[Lv.X] <MobId> <currentHP>/<maxHP>❤
```

### Complete Mob Examples

```yaml
forest_goblin:
  name: "<green>Forest Goblin"
  type: ZOMBIE
  health: 80.0
  damage: 12.0
  speed: 0.28
  level: 5
  equipment:
    helmet: LEATHER_HELMET
    main-hand: WOODEN_SWORD

cave_archer:
  name: "<gray>Cave Archer"
  type: SKELETON
  health: 50.0
  damage: 8.0
  speed: 0.25
  level: 3
  equipment:
    main-hand: BOW
```

---

## 25. GUI System — gui/*.yml

Place GUI definition files in `plugins/Valmora/gui/`. Each file defines one GUI screen, referenced by its filename (without `.yml`).

### Full GUI Schema

```yaml
title: "<MiniMessage title string>"
rows: <1-6>          # Optional. Default: calculated from layout.
machine: <id>       # Optional. Machine ID for recipe matching (e.g., anvil, alchemy).
update-interval: <ticks> # Optional. Periodically runs on-update script and re-renders.

layout:
  - "XXXXXXXXX"    # Row 1 (9 chars)
  - "XXXXXXXXX"    # Row 2 (9 chars)
  - "XXXXXXXXX"    # Row 3 (9 chars)
  - "XXXXXXXXX"    # Row 4 (9 chars)

components:
  <char>:
    item: "<MATERIAL>"
    name: "<display name>"
    lore:
      - "<line>"
    custom-model-data: <integer>
    click:
      - action: "<ACTION>"
        args: "<argument>"

  # Special component types:
  <char>:
    type: "INPUT"         # Unrestricted input slot
    id: <id>              # Optional. Used for $gui.input.ID.id$ reactivity.

  <char>:
    type: "OUTPUT"        # Output slot (extraction-based)
    item: "AIR"

# Reactivity & Logic:
on-open:
  conditions: ["<condition>"]
  actions: ["<event>"]
  fail-actions: ["<event>"]

on-slot-update:
  actions: ["<event>"]
  fail-actions: ["<event>"]

on-update:
  actions: ["<event>"]
  fail-actions: ["<event>"]
```

### Layout System

Each character in the layout grid corresponds to a component key in the `components` map. Spaces (` `) are treated as empty slots. The grid determines which inventory slot gets which component type.

Layout rows must be exactly 9 characters. The number of rows (1–6) determines the inventory size.

### Reactivity Variables

GUIs support dynamic variables that update when items change or scripts run:

| Variable | Description |
|---|---|
| `$gui.input.ID.id$` | The Valmora Item ID (or Material name) in the INPUT slot with matching `id`. |
| `$gui.input.ID.amount$` | The amount of items in the INPUT slot. |
| `$prop.NAME$` | A GUI-specific property (transient, lost on close). Updated via `variable set prop.NAME`. |
| `$enchant.NAME.prop$` | In lists, resolves properties of a Valmora enchantment (name, description, level, etc). |

### Lifecycle Scripts

- **`on-open`**: Runs once when the GUI is requested. If conditions fail, `fail-actions` run and the GUI does not open.
- **`on-slot-update`**: Runs whenever an item is placed or removed in an `INPUT` slot.
- **`on-update`**: Runs every `update-interval` ticks. Useful for timers and progress bars.

### Click Actions

| Action | Args | Description |
|---|---|---|
| `CLOSE` | — | Closes the inventory. |
| `BACK` | — | Returns to the previously opened GUI. |
| `OPEN_GUI` | `"<gui-filename>"` | Opens a different GUI. |
| `SOUND` | `"<sound_id>"` | Plays a sound to the player. |

### Special Component Types

**`INPUT`** — A player-interactable slot. If an `id` is provided, its contents are exposed to scripts via `$gui.input.ID.id$`.

**`OUTPUT`** — A result slot. Players can take items from it; taking an item triggers the associated recipe consumption.

**`PREVIOUS_PAGE` / `NEXT_PAGE`** — Pagination buttons for `PAGINATED` components.

**`PAGINATED`** — Repeating component driven by a list variable (e.g. `$player.stat.list$`, `$math.range(1, 10)$`).
- `list` — variable expression resolving to a list.
- `iterator` — name bound to the current element (e.g. `{lvl}`).
- `states` — list of display configurations with `condition`. First match wins. Use `default` as catch-all.

### Complete GUI Examples

**Simple Menu:**
```yaml
title: "Main Menu"
layout:
  - "BBBBBBBBB"
  - "B B B B B"
  - "B B B B B"
  - "BBBBCBBBB"
components:
  B:
    item: "BLACK_STAINED_GLASS_PANE"
    name: " "
  C:
    item: "BARRIER"
    name: "<red>Close"
    click:
      - action: "CLOSE"
```

**Crafting / Forge GUI:**
```yaml
title: "Valmora Forge"
layout:
  - "BBBBBBBBB"
  - "B I+I=O B"
  - "BBBB^BBBB"
components:
  B:
    item: "BLACK_STAINED_GLASS_PANE"
    name: " "
  +:
    item: "LIME_STAINED_GLASS_PANE"
    name: "<green>+"
  =:
    item: "LIME_STAINED_GLASS_PANE"
    name: "<green>Result"
  ^:
    item: "ARROW"
    name: "<red>Go Back"
    click:
      - action: "BACK"
  I:
    type: "INPUT"
  O:
    type: "OUTPUT"
    item: "AIR"
```

---

## 26. Stat Reference Table

These stat names are used in item YAML files under the `stats:` section and in script expressions via `$player.stat.<NAME>$`.

| Stat Name | Description | Default | Notes |
|---|---|---|---|
| `DAMAGE` | Base attack damage before strength scaling. | 5.0 | Used as base in `DamageCalculator`. |
| `HEALTH` | Max health pool. | 100.0 | Visual hearts scale to this. |
| `STRENGTH` | Increases all outgoing damage. | 0.0 | Formula: `damage × (1 + strength/100)`. |
| `DEFENSE` | Reduces incoming damage. | 0.0 | Formula: `multiplier = 100/(defense+100)`. |
| `CRIT_CHANCE` | Percentage chance for a critical hit. | 30.0 | Capped at 100.0. |
| `CRIT_DAMAGE` | Bonus damage multiplier on crits (%). | 50.0 | A value of 50 = 1.5× normal damage. |
| `SPEED` | Movement speed relative to 100 = normal. | 100.0 | Mapped to vanilla `MOVEMENT_SPEED` attribute. |
| `MANA` | Max mana pool for abilities. | 100.0 | Depleted by ability mana costs. |
| `HEALTH_REGEN` | HP restored per second while out of combat. | 1.0 | Does not tick during combat. |
| `MANA_REGEN` | Mana restored per second. | 2.0 | Always ticks regardless of combat. |

**Negative stats** are valid (e.g., `SPEED: -10` on heavy armor).

---

## 27. Skill Reference Table

Skills are levelled by performing in-game actions. XP is gained automatically via the `SkillListener`.

| Skill | Internal Name | Max Level | XP Source |
|---|---|---|---|
| Mining | `mining` | 60 | Breaking stone, ores, and deepslate |
| Farming | `farming` | 60 | Breaking grown crop blocks |
| Foraging | `foraging` | 60 | Breaking logs |
| Fishing | `fishing` | 60 | Catching fish in Fishing Zones |
| Combat | `combat` | 60 | Killing mobs |
| Alchemy | `alchemy` | 60 | Brewing potions |
| Carpentry | `carpentry` | 60 | Defined in `skills/carpentry.yml` — sources configured per-server |
| Enchanting | `enchanting` | 60 | Applying Valmora enchantments |
| Taming | `taming` | 60 | Taming animals |

### XP Thresholds

The XP required to reach each level is cumulative (total XP, not per-level):

| Level | Total XP Required |
|---|---|
| 1 | 10 |
| 2 | 50 |
| 3 | 100 |
| 4 | 250 |
| 5 | 500 |
| 6 | 1,000 |
| 7 | 1,500 |
| 8 | 2,000 |
| 9 | 5,000 |
| 10 | 10,000 |
| 11–28 | +5,000 per level from 15,000 to 100,000 |
| 29+ | Level 28 threshold (100,000 XP) is the last defined threshold; subsequent levels use `maxLevel` cap |

Players receive an action bar notification on XP gain and a chat message on level-up.

---

## 28. Command Reference

### `/valmora` (Admin only)

| Subcommand | Usage | Description |
|---|---|---|
| `reload` | `/valmora reload` | Hot-reloads ALL modules: disables then re-enables all registered modules in order. Reloads all YAML configs without restarting the server. |

### `/profile`

| Subcommand | Usage | Description |
|---|---|---|
| `create` | `/profile create <name>` | Creates a new character profile with the given name. |
| `delete` | `/profile delete <name>` | Deletes the profile with the given name (by active profile name). |
| `switch` | `/profile switch <name>` | Switches to the named profile. Stats are immediately recalculated. |
| `list` | `/profile list` | Lists all profiles. The active profile is highlighted in green. |
| `info` | `/profile info` | Shows the active profile's ID, name, current health, mana, and combat status. |

### `/stat`

| Subcommand | Usage | Description |
|---|---|---|
| `list` | `/stat list` | Prints all current stat values for the active profile. |
| `add` | `/stat add <STAT> <amount>` | Adds `amount` to the given stat on the active profile. |
| `remove` | `/stat remove <STAT> <amount>` | Subtracts `amount` from the given stat on the active profile. |

### `/item`

| Subcommand | Usage | Description |
|---|---|---|
| `give` | `/item give <id> [player]` | Gives the Valmora item with the given ID to yourself or the specified player. |
| `list` | `/item list` | Lists all registered item IDs. |
| `info` | `/item info <id>` | Shows the definition details for an item. |

### `/mob`

| Subcommand | Usage | Description |
|---|---|---|
| `spawn` | `/mob spawn <id> [player]` | Spawns the Valmora mob at your location, or at the specified player's location. |
| `list` | `/mob list` | Lists all registered mob IDs. |
| `reload` | `/mob reload` | Hot-reloads the mob module only. |
| `info` | `/mob info` | Shows definition details for the mob you are looking at (within 10 blocks). |

### `/skill`

| Subcommand | Usage | Permission | Description |
|---|---|---|---|
| `info` | `/skill info [skill]` | Any player | Shows XP and level for all skills (or one specific skill). |
| `list` | `/skill list` | Any player | Lists all available skills and their max levels. |
| `givexp` | `/skill givexp <player> <skill> <amount>` | `valmora.admin` | Gives XP in the specified skill to a player. |
| `setlevel` | `/skill setlevel <player> <skill> <level>` | `valmora.admin` | Sets the player's skill level by adjusting their XP to the exact threshold. |

All commands support tab completion.

---

## 29. Permissions

| Permission | Default | Description |
|---|---|---|
| `valmora.admin` | OP | Grants access to `/valmora reload`, `/skill givexp`, `/skill setlevel`, and any other admin-only subcommands. |

> All other commands (`/profile`, `/stat`, `/item`, `/mob`, `/skill info`, `/skill list`) are available to all players without any specific permission node.

---

## 30. Damage Type Reference

Damage types affect the color of the damage indicator and whether defense is applied.

| Type | Indicator Color | Bypasses Defense? | Source |
|---|---|---|---|
| `MELEE` | White | No | Direct entity attack |
| `PROJECTILE` | Gray | No | Arrow or mob projectile |
| `MAGIC` | Aqua | No | Default for `DAMAGE` mechanics |
| `TRUE` | (White) | **Yes** | Ability mechanic with `type: "TRUE"` |
| `FALL` | Dark Gray | **Yes** | Fall damage |
| `DROWNING` | Blue | **Yes** | Drowning |
| `FIRE` | Orange (`#FF8C00`) | No | Fire/Fire Tick |
| `LAVA` | Dark Red | No | Lava contact |
| `POISON` | Green | No | Poison effect |
| `WITHER` | Black | No | Wither effect |
| `EXPLOSION` | Red | No | Block/entity explosion |
| `VOID` | Black | **Yes** | Void/out of world |

---

## 31. Rarity Reference Table

| Rarity | Display Name | Color Tag | Used For |
|---|---|---|---|
| `COMMON` | Common | `<white>` | Default. Basic items. |
| `UNCOMMON` | Uncommon | `<green>` | Slightly enhanced items. |
| `RARE` | Rare | `<blue>` | Items with stats or basic abilities. |
| `EPIC` | Epic | `<dark_purple>` | Multi-ability or high-stat items. |
| `LEGENDARY` | Legendary | `<gold>` | Top-tier power items. |
| `MYTHIC` | Mythic | `<light_purple>` | Reserved for the rarest items. |
| `DIVINE` | Divine | `<aqua>` | Endgame tier. Used by the reforge system and high-end items. |

The rarity name is automatically appended as a **bold** colored line at the bottom of an item's lore, and the rarity color is prepended to the item's display name.

---

## 32. Script Variable Reference

Variables are used in conditions and expressions throughout the engine. The syntax is `$namespace.path$`.

### Player Variables (`$player.*$`)

| Variable | Returns | Example |
|---|---|---|
| `$player.name$` | String | `"Steve"` |
| `$player.stat.HEALTH$` | Double | `250.0` |
| `$player.stat.DAMAGE$` | Double | `45.0` |
| `$player.stat.MANA$` | Double | `100.0` |
| `$player.stat.<ANY_STAT>$` | Double | Any stat ID from the stat registry |
| `$player.var.<varName>$` | Object | Custom profile variable value |
| `$player.skill.<skillId>.level$` | Integer | Player's level in the given skill |
| `$player.skill.<skillId>.xp$` | Double | Player's total XP in the given skill |

### World Variables (`$world.*$`)

| Variable | Returns | Example |
|---|---|---|
| `$world.name$` | String | `"world"` |
| `$world.dimension$` | String | `"NORMAL"`, `"NETHER"`, `"THE_END"` |

### System Variables (`$system.*$`)

| Variable | Returns | Example |
|---|---|---|
| `$system.time$` | Long | Current Unix timestamp in milliseconds |

### Time Variables (`$time.*$`)

| Variable | Returns | Example |
|---|---|---|
| `$time.season$` | String | `"Summer"` |
| `$time.hour$` | Integer | `14` (0–23) |
| `$time.is_day$` | Boolean | `true` |
| `$time.day$` | Integer | Current RPG day of the season |
| `$time.year$` | Integer | Current RPG year |

### Quest Variables (`$quest.*$`)

| Variable | Returns | Example |
|---|---|---|
| `$quest.<questId>.status$` | String | `"ACTIVE"`, `"COMPLETED"`, `"NOT_STARTED"` |
| `$quest.<questId>.objective.<objId>.progress$` | Integer | Current objective progress count |
| `$quest.<questId>.objective.<objId>.required$` | Integer | Required count to complete the objective |
| `$quest.objective.<objId>.active$` | Boolean | Whether this objective is currently active for the player |

### Points Variables (`$point.*$`)

| Variable | Returns | Example |
|---|---|---|
| `$point.<category>$` | Double | `15.0` — the player's current points in the given category |

### Collection Variables (`$collection.*$`)

These variables are used inside Collection GUIs and require session props `selected_category` and `selected_collection` to be set.

| Variable | Returns | Description |
|---|---|---|
| `$collection.category_list$` | List | All collection category IDs |
| `$collection.item_list$` | List | Collection IDs in the selected category |
| `$collection.stage_list$` | List | Stage numbers for the selected collection |
| `$collection.detail_name$` | String | Display name of the selected collection |
| `$collection.detail_icon$` | String | Material name for the selected collection icon |
| `$collection.detail_count$` | Integer | Player's current count for the selected collection |
| `$collection.detail_stage$` | Integer | Player's current stage (0-indexed) |
| `$collection.detail_max_stage$` | Integer | Total number of stages in the collection |
| `$collection.detail_next_required$` | Integer | Count needed to reach the next stage |

### GUI Variables (`$gui.*$`, `$prop.*$`)

| Variable | Returns | Example |
|---|---|---|
| `$gui.input.<id>.id$` | String | Valmora item ID in the INPUT slot |
| `$gui.input.<id>.amount$` | Integer | Stack size in the INPUT slot |
| `$gui.input.<id>.material$` | String | Bukkit material name in the INPUT slot |
| `$prop.<key>$` | Object | Per-GUI session property (transient) |

### Condition Strings

Conditions can be written as expressions or tag checks:

```yaml
# Expression conditions:
condition: "$player.stat.HEALTH$ > 50"
condition: "$player.stat.MANA$ == 0"
condition: "$player.var.coins$ >= 100"

# Tag condition:
condition: "tag quest_complete"

# 'default' keyword (PAGINATED states only):
condition: "default"       # Always true, used as catch-all final state

# AND logic via list (all must be true):
conditions:
  - "tag quest_started"
  - "$player.stat.HEALTH$ > 20"
```

---

## 33. Script Event DSL Reference

Script events are strings used to trigger side effects. They are parsed by `EventParser`.

### Syntax

```
<eventName> <arg1> [arg2 ...] [notify] [delay:<ticks>]
```

### Options

| Option | Description |
|---|---|
| `notify` | Sends a notification message to the player. |
| `delay:<ticks>` | Delays execution by N ticks (20 ticks = 1 second). |

### Built-in Events

**`give`** — Give items to the caster player.
```
give <MATERIAL>:<amount>
give DIAMOND:5
give STONE:64 notify
give EMERALD:1 notify delay:40
```

**`tag`** — Add or remove a tag on the active profile.
```
tag add <tagName>
tag remove <tagName>
tag add quest_complete
tag remove tutorial_lock
```

Tags are simple string flags stored on the profile. They persist across sessions (saved in the DB). Use them to track quest progress, feature unlocks, tutorial steps, etc.

**`variable`** — Modify a custom variable on the active profile or a GUI property.
```
variable set player.var.<name> <value>
variable add player.var.<name> <number>
variable set prop.<name> <value>         # GUI transient property
variable add prop.<name> <number>
variable remove <path>

variable set player.var.coins 100
variable add prop.brew_time -1
```

Variables are stored as typed values: numbers stay as `Double`, `"true"`/`"false"` become `Boolean`, anything else is stored as a `String`. Variables are available in conditions via `$player.var.<name>$` or `$prop.<name>$`.

**`gui_force_craft`** — Programmatically triggers a recipe match and consumption for the current GUI's machine.
```
gui_force_craft
```
Used in `on-update` scripts to finish time-based crafting. It finds the recipe matching the current inputs, consumes ingredients, places the result in the OUTPUT slot, and executes the recipe's `on-craft` script.

**`enchant_apply`** — Applies a Valmora enchantment to an item in a specific slot.
```
enchant_apply <slot> <enchant_id> <level>
enchant_apply 10 sharpness 5
```
Primarily used in enchanting table GUIs.

**`sound`** — Plays a sound.
```
sound player <sound_id> [volume] [pitch]
sound player block.brewing_stand.brew
```

**`notify`** — Sends a notification to the player using the Notify system.
```
notify <message> [category:<name>] [io:<io-type>] [key:value ...]
notify "Quest complete!" category:quest_complete
notify "Not enough coins." io:actionbar
notify "You found a chest!" io:title
```
The `category:` token selects a named category (which defines the default IO type and any extra settings). The `io:` token overrides the category's IO type. Any additional `key:value` tokens are passed as extra settings to the IO handler. If neither `category:` nor `io:` is given, defaults to the `info` category (chat).

**`notifyall`** — Same syntax as `notify` but broadcasts to all online players.
```
notifyall "The server event has begun!" io:title
```

**`quest_start`** — Starts a quest for the player.
```
quest_start <quest-id>
quest_start forgotten_mine_main
```

**`quest_complete`** — Marks a quest as completed (triggers rewards).
```
quest_complete <quest-id>
```

**`quest_cancel`** — Cancels an active quest (no rewards).
```
quest_cancel <quest-id>
```

**`quest_fail`** — Marks a quest as failed.
```
quest_fail <quest-id>
```

**`objective_start`** — Starts a specific objective within a quest.
```
objective_start <quest-id> <objective-id>
```

**`objective_delete`** — Removes an objective from the player's active tracking.
```
objective_delete <quest-id> <objective-id>
```

**`journal open`** — Opens the quest journal GUI for the player.
```
journal open
```

**`point`** — Modifies a point category for the player.
```
point <category> add <amount>
point <category> set <amount>
point <category> take <amount>

point kills add 1
point reputation set 0
point slayer_xp add 250
```
Points are free-form named counters stored per-player. The category name is any string. Use `$point.<category>$` to read values in conditions and expressions.

**`slayer_start`** — Starts a slayer quest for the player.
```
slayer_start <slayer-id> <tier>
slayer_start zombie_slayer 1
```

**`economy_add`** — Adds coins to the player's balance.
```
economy_add <amount>
economy_add 500
```

**`economy_remove`** — Removes coins from the player's balance.
```
economy_remove <amount>
economy_remove 250
```

### Event Examples

```yaml
# In a quest completion context:
on-complete:
  - "tag add main_quest_1_done"
  - "variable add player.var.quest_count 1"
  - "give DIAMOND:10 notify"
  - "give EXPERIENCE_BOTTLE:5 delay:20 notify"

# In a shop context:
on-purchase:
  - "variable add player.var.coins -50"
  - "give IRON_SWORD:1 notify"
```

---

---

## 34. Notify Module

`NotifyModule` (id: `"notify"`) provides a multi-channel notification engine used throughout the quest and game systems to show messages to players.

### 34.1 Built-in Categories

| Category | Default IO | Description |
|---|---|---|
| `info` | `chat` | General info messages. |
| `error` | `actionbar` | Error/warning messages shown in the action bar. |

Custom categories are defined per-quest-package in a `notifications.yml` file (see QUEST_SYSTEM.md §7).

### 34.2 IO Types

| IO Type | Description |
|---|---|
| `chat` | Sends a chat message. |
| `actionbar` | Shows text in the action bar for a short duration. |
| `title` | Shows a large title overlay. |
| `subtitle` | Shows the subtitle part of a title overlay. |
| `bossbar` | Shows a boss bar at the top of the screen. |
| `sound` | Plays a sound effect (no text). |
| `advancement` | Shows a toast notification (advancement-style). |

### 34.3 Script Event Syntax

```
notify <message> [category:<name>] [io:<io-type>] [key:value ...]
notifyall <message> [category:<name>] [io:<io-type>] [key:value ...]
```

- **`category:<name>`** — uses the named category's default IO and settings.
- **`io:<type>`** — overrides the IO type (one of the values in the table above).
- Any remaining `key:value` tokens are passed as extra settings to the IO handler (e.g., `duration:5`).

If no category or IO is specified, the `info` category (chat) is used.

### 34.4 Registering a Custom IO

```java
notifyModule.registerIO("my_io", new MyNotifyIO());
// MyNotifyIO implements NotifyIO:
public interface NotifyIO {
    void send(Player player, Component message, Map<String, String> settings);
}
```

---

## 35. Collections Module — collections/*.yml

`CollectionModule` (id: `"collection"`) tracks per-player counts of items collected or actions performed. When a player reaches a defined threshold (stage), they receive rewards.

### 35.1 Directory Structure

```
plugins/Valmora/collections/
├── <category>/                  ← Folder name is the category ID
│   ├── category.yml             ← Category definition (name, icon, description)
│   ├── coal.yml                 ← One collection per file
│   ├── iron.yml
│   └── ...
```

A folder becomes a category when it contains a `category.yml` file. All other `.yml` files in the folder are parsed as individual collection definitions.

### 35.2 Category File — `category.yml`

```yaml
<category-id>:
  name: "<MiniMessage name>"
  icon: <MATERIAL>
  description:
    - "<line>"
```

| Field | Required | Notes |
|---|---|---|
| `name` | Yes | Display name shown in the collections GUI. MiniMessage. |
| `icon` | Yes | Material used as the category icon in the GUI. |
| `description` | No | Lore lines for the category. |

### 35.3 Collection File

```yaml
<collection-id>:
  category: <category-id>        # REQUIRED — links to a category folder
  name: "<MiniMessage name>"
  icon: <MATERIAL>
  track:
    - <TYPE>:<TARGET>            # See track source format below
    - <TYPE>:<TARGET>
  stages:
    <stage-number>:              # Integer key (1, 2, 3 …)
      required: <int>            # Total count needed to reach this stage
      rewards:
        - "<event string>"
```

| Field | Required | Notes |
|---|---|---|
| `category` | Yes | Must match a loaded category ID. |
| `name` | Yes | Display name in the collections GUI. MiniMessage. |
| `icon` | Yes | Material for the GUI icon. |
| `track` | Yes | List of `TYPE:TARGET` track sources (see below). |
| `stages` | Yes | Map of stage number → `required` count and `rewards` list. Stage numbers must be positive integers starting at 1. |

### 35.4 Track Source Format

```
TYPE:TARGET
```

| Type | Target | Tracks |
|---|---|---|
| `BLOCK_BREAK` | Material name | Counts each block of this material the player breaks. |
| `ITEM_PICKUP` | Material name or Valmora item ID | Counts each item picked up from the ground. |

**Examples:**
```yaml
track:
  - BLOCK_BREAK:COAL_ORE
  - BLOCK_BREAK:DEEPSLATE_COAL_ORE
  - ITEM_PICKUP:COAL
```

Multiple track sources are cumulative — breaking either `COAL_ORE` or `DEEPSLATE_COAL_ORE` increments the same counter.

### 35.5 Complete Example

```yaml
# plugins/Valmora/collections/mining/coal.yml
coal:
  category: mining
  name: "<gray>Coal Collection"
  icon: COAL
  track:
    - BLOCK_BREAK:COAL_ORE
    - BLOCK_BREAK:DEEPSLATE_COAL_ORE
    - ITEM_PICKUP:COAL
  stages:
    1:
      required: 50
      rewards:
        - "economy_add 100"
        - "notify Stage 1 unlocked! io:chat"
    2:
      required: 250
      rewards:
        - "economy_add 500"
    3:
      required: 1000
      rewards:
        - "give DIAMOND_PICKAXE:1 notify"
    4:
      required: 10000
      rewards:
        - "economy_add 5000"
    5:
      required: 100000
      rewards:
        - "give netherite_pickaxe:1 notify"
```

### 35.6 Script Variables

Used inside collection GUI definitions. Require session props `selected_category` and `selected_collection` to be set (via `variable set prop.selected_category <id>`).

| Variable | Returns |
|---|---|
| `$collection.category_list$` | List of all category IDs |
| `$collection.item_list$` | List of collection IDs in the selected category |
| `$collection.stage_list$` | List of stage numbers for the selected collection |
| `$collection.detail_name$` | Display name of the selected collection |
| `$collection.detail_icon$` | Material name of the selected collection icon |
| `$collection.detail_count$` | Player's current count |
| `$collection.detail_stage$` | Player's current stage (0 = not started) |
| `$collection.detail_max_stage$` | Total number of stages |
| `$collection.detail_next_required$` | Count needed for the next stage (`-1` if maxed) |

---

## 36. Slayer Module — slayers/*.yml

`SlayerModule` (id: `"slayer"`) provides tiered kill-challenge quests. A player activates a slayer at a cost, accumulates kills from a target category, and then spawns and kills a boss mob to complete the tier.

### 36.1 YAML Schema

```yaml
<slayer-id>:
  name: "<display name>"
  tiers:
    <tier-number>:               # Integer key (1, 2, 3 …)
      cost: <int>                # Coins deducted when the slayer is started
      target-category: <STRING>  # Mob category tag to count kills for
      kills-required: <int>      # Kills needed before the boss spawns
      boss-mob: <mob-id>         # Valmora mob ID to spawn as the boss
      completion-events:
        - "<event string>"
```

| Field | Required | Notes |
|---|---|---|
| `name` | Yes | Display name used in the slayer GUI and notifications. |
| `tiers` | Yes | Map of tier number → tier definition. |
| `cost` | Yes | Coins deducted on activation. Player must have enough coins. |
| `target-category` | Yes | Category string matched against mob PDC tags (e.g., `UNDEAD`, `SPIDER`, `WOLF`). |
| `kills-required` | Yes | Number of category kills before the boss mob is eligible to spawn. |
| `boss-mob` | Yes | A Valmora mob ID. The boss spawns at the player's location when the kill count is met. |
| `completion-events` | No | Script events fired when the boss is killed. Receives the player as context. |

### 36.2 Script Event

```
slayer_start <slayer-id> <tier>
slayer_start zombie_slayer 1
```

Starts the specified slayer tier for the player. Deducts `cost` coins and begins tracking kills.

### 36.3 Complete Example

```yaml
zombie_slayer:
  name: "Zombie Slayer"
  tiers:
    1:
      cost: 100
      target-category: UNDEAD
      kills-required: 5
      boss-mob: zombie
      completion-events:
        - "economy_add 250"
        - "notify <gold>[Slayer] Zombie Slayer T1 complete! +250 coins io:chat"
    2:
      cost: 500
      target-category: UNDEAD
      kills-required: 15
      boss-mob: zombie
      completion-events:
        - "economy_add 1000"
        - "notify <gold>[Slayer] Zombie Slayer T2 complete! +1000 coins io:chat"
    3:
      cost: 2000
      target-category: UNDEAD
      kills-required: 30
      boss-mob: zombie
      completion-events:
        - "economy_add 5000"
        - "notify <gold>[Slayer] Zombie Slayer T3 complete! +5000 coins io:chat"
```

---

## 37. Reforge Module — reforges/*.yml

`ReforgeModule` (id: `"reforge"`) applies stat bonuses to custom items, scaled by the item's rarity. Players access reforging through two GUI machines.

### 37.1 How It Works

1. A player opens a **Reforge Anvil** (`machine: reforge_anvil`) and places an item + a Reforge Stone.
2. The stone identifies which reforge to apply. The coin cost is deducted (based on item rarity).
3. The item's stats are replaced with its base stats + the reforge's rarity-tier bonuses.

Alternatively, a **Random Forge** (`machine: forge_random`) accepts only the item and applies a random valid reforge (excluding the current one).

### 37.2 YAML Schema

```yaml
<reforge-id>:
  name: "<display name>"
  applicable-types:            # Item types this reforge can be applied to
    - SWORD
    - AXE
  generate-stone: true         # Whether to auto-create a Reforge Stone item for this reforge
  stat-bonuses-by-rarity:
    COMMON:
      <stat-id>: <value>
      <stat-id>: <value>
    UNCOMMON:
      <stat-id>: <value>
    RARE:
      <stat-id>: <value>
    EPIC:
      <stat-id>: <value>
    LEGENDARY:
      <stat-id>: <value>
    MYTHIC:
      <stat-id>: <value>
    DIVINE:
      <stat-id>: <value>
```

| Field | Required | Notes |
|---|---|---|
| `name` | Yes | Display name shown on the reforge stone and item lore. |
| `applicable-types` | Yes | List of `ItemType` values the reforge can be applied to. See valid values below. |
| `generate-stone` | No | If `true`, a Reforge Stone (AMETHYST_SHARD) for this reforge is auto-generated and can be given via `/item give <reforge-id>_stone`. Default: `false`. |
| `stat-bonuses-by-rarity` | Yes | Map of rarity name → stat-id → bonus value. If a rarity tier is missing, the nearest lower rarity is used as a fallback. |

### 37.3 Valid `applicable-types` Values

```
SWORD   AXE      BOW     CROSSBOW
HELMET  CHESTPLATE  LEGGINGS  BOOTS
NONE    ALL
```

`ALL` matches any item type. `NONE` matches items with no type assigned.

### 37.4 Rarity Tiers & Coin Costs

| Rarity | Cost to Reforge |
|---|---|
| `COMMON` | 250 |
| `UNCOMMON` | 500 |
| `RARE` | 1,000 |
| `EPIC` | 2,500 |
| `LEGENDARY` | 5,000 |
| `MYTHIC` | 10,000 |
| `DIVINE` | 15,000 |

Costs are fixed in the engine. The player must have enough coins in their economy balance before the recipe will match.

### 37.5 Machine IDs

| Machine ID | Inputs | Behavior |
|---|---|---|
| `reforge_anvil` | `base_item` + `reforge_stone` | Applies the exact reforge encoded in the stone. Consumes both items. |
| `forge_random` | `base_item` only | Applies a random valid reforge (excluding the item's current reforge). Consumes the item (it is replaced by the reforged output). |

### 37.6 Stat Application

When a reforge is applied:
1. The item's **base stats** are loaded fresh from the `ItemDefinition` (ignoring any previous reforge).
2. The **rarity-scaled reforge bonuses** are merged on top.
3. The merged stat map is written back to the item's PDC.
4. Lore is regenerated to reflect the new stats.

This means reforges do not stack — reforging always replaces the previous reforge.

### 37.7 Complete Example

```yaml
fierce:
  name: "Fierce"
  applicable-types:
    - SWORD
    - AXE
  generate-stone: true
  stat-bonuses-by-rarity:
    COMMON:
      strength: 5
      crit_damage: 3
    UNCOMMON:
      strength: 12
      crit_damage: 6
    RARE:
      strength: 20
      crit_damage: 10
    EPIC:
      strength: 32
      crit_damage: 15
    LEGENDARY:
      strength: 48
      crit_damage: 22
    MYTHIC:
      strength: 65
      crit_damage: 30
    DIVINE:
      strength: 85
      crit_damage: 40
```

---

## 38. Points System

`PointsModule` (id: `"points"`) provides free-form per-player numeric counters keyed by a category string. Points are stored in the player's profile variables and persist across sessions.

### 38.1 Script Event

```
point <category> add <amount>
point <category> set <amount>
point <category> take <amount>
```

| Sub-command | Effect |
|---|---|
| `add <amount>` | Increases the player's points in `<category>` by `<amount>`. |
| `set <amount>` | Sets the player's points in `<category>` to exactly `<amount>`. |
| `take <amount>` | Decreases the player's points (floors at 0). |

**Examples:**
```
point reputation add 10
point kills set 0
point slayer_xp add 250
point currency take 50
```

### 38.2 Script Variable

```
$point.<category>$
```

Returns the player's current point total in the given category as a `Double`. If the player has never been given points in that category, returns `0.0`.

**Example in conditions:**
```yaml
- "condition $point.reputation$ >= 100"
```

**Example in expressions:**
```yaml
- "condition $point.kills$ > $point.kills_required$"
```

### 38.3 Usage Pattern

Points are most commonly used to:
- Track progress toward repeatable rewards (`kills`, `quests_done`, `bosses_slain`).
- Gate content behind reputation thresholds.
- Store skill-like progression that doesn't fit the fixed skill system.
- Act as a currency separate from the main economy.

```yaml
# Reward 10 reputation points for completing a quest:
rewards:
  - "point reputation add 10"
  - "notify <green>+10 Reputation io:actionbar"

# Gate a quest behind a reputation threshold:
on-open:
  conditions:
    - "condition $point.reputation$ >= 50"
  fail-actions:
    - "notify <red>You need 50 Reputation to start this quest. io:actionbar"
```

---

*End of Valmora Engine Documentation — v0.1*
