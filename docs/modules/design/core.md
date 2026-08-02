# Core Engine — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package root:** `org.nakii.valmora` | **Main class:** `org.nakii.valmora.Valmora`
> **Scope:** Everything that is *not* a feature module — plugin entry point, module system, public API, database layer, config infrastructure, shared utilities, and root configuration. Feature modules are documented individually under `docs/modules/design/<module>.md` and referenced here only where the core wires them up.

---

## Table of Contents

1. [Overview](#overview)
2. [Plugin Entry Point](#2-plugin-entry-point)
3. [Module System](#3-module-system)
4. [Public API](#4-public-api)
5. [Database Layer](#5-database-layer)
6. [Infrastructure & Config](#6-infrastructure--config)
7. [Utilities](#7-utilities)
8. [Configuration](#8-configuration)
9. [Commands](#9-commands)
10. [Unfinished Things / TODOs](#10-unfinished-things--todos)
11. [Possible Improvements / Changes](#11-possible-improvements--changes)

---

## Overview

Valmora is a modular MMORPG engine implemented as a single Paper plugin. The core is the **wiring layer**: it boots the plugin, owns the `ModuleManager`, creates the `DataStore`, exposes the engine-wide `ValmoraAPI`, and registers every command. Each major feature lives in a `ReloadableModule` that is created, registered, and enabled in a fixed order, then hot-reloaded as a group by `/valmora reload`.

Key structural facts:

- `Valmora` (`Valmora.java:75`) is `public final class Valmora extends JavaPlugin implements ValmoraAPI` — the plugin **is** the API implementation, and `ValmoraAPI` is a static-holder interface so modules can reach the whole engine without holding a plugin reference.
- PacketEvents is initialized in `onLoad()`/`onEnable()` and terminated in `onDisable()`; the plugin declares a hard `depend: [packetevents]` in `plugin.yml:6`.
- There is **no top-level `permissions:` block** in `plugin.yml` — permissions are declared inline per-command (`plugin.yml:9-69`), and gated a second time inside each executor.
- The module list is **not** the AGENTS.md documentation order — the real registration order in `Valmora.java:187-222` includes newer modules (economy, zones, resources, fishing, npc, warp, quest, points, notify, collections, hud, calendar, reforge, pet, slayer, accessory, backpack, quiver, progression) appended after the foundational block.

---

## 2. Plugin Entry Point

### 2.1 `Valmora.java` — lifecycle

| Phase | Method | What happens | Lines |
|---|---|---|---|
| Load | `onLoad()` | Builds and loads the PacketEvents API (`SpigotPacketEventsBuilder.build(this)` → `PacketEvents.getAPI().load()`) | `Valmora.java:79-83` |
| Enable | `onEnable()` | Full boot sequence (see §2.2) | `Valmora.java:126-260` |
| Disable | `onDisable()` | `moduleManager.disableModules()` (reverse order), `PacketEvents.terminate()`, then blocks on `dataStore.savePlayer(player).join()` for every online `ValmoraPlayer` session, then `dataStore.close()` | `Valmora.java:262-276` |

There is also a static `Valmora.getInstance()` (`Valmora.java:278-280`) mirroring the `ValmoraAPI` holder.

### 2.2 The `onEnable()` boot sequence (in order)

```
Valmora.java:126-260
1.  instance = this; ValmoraAPI.setProvider(this); PacketEvents.getAPI().init()
2.  moduleManager = new ModuleManager(this)
3.  saveDefaultConfig(); saveAllResources()
4.  Keys.init(this)                                    ← all NamespacedKey fields populated
5.  dataStore = DatabaseFactory.createDataStore(this); dataStore.init()
        → on RuntimeException: SEVERE log + disablePlugin(this) + return (fail-fast, no data loss)
6.  economyModule = new EconomyModule(this, dataStore); economyService = economyModule
7.  instantiate all 33 managers/modules (fields, Valmora.java:87-123)
8.  moduleManager.registerModule(...) × 33  in order   (Valmora.java:187-222)
9.  moduleManager.enableModules()
10. getCommand(...).setExecutor(...) × 18              (Valmora.java:228-259)
```

Note the order of 5→6: the economy is the **only** module constructed with a `DataStore` reference at construction time (`Valmora.java:149`); every other module gets its dependencies through the `ValmoraAPI` at runtime.

### 2.3 `saveAllResources()` — first-run resource extraction

`Valmora.java:456-490` opens the plugin JAR as a `ZipInputStream`, iterates entries, and for any entry whose path starts with one of the known content folders, calls `saveResource(name, false)` **only if the file does not already exist** on disk. It skips `.class` files, `plugin.yml`, and `config.yml`.

Watched prefixes (`Valmora.java:469-479`):

```
items/  mobs/  guis/  recipes/  skills/  enchants/  alchemy/  stats/
zones/  fishing/  npcs/  dialogues/  warps/  quests/  collections/
hud-items/  calendar/  reforges/  pets/  slayers/  set_bonuses/
progression/  quest_boards/
```

Because extraction is `saveResource(name, false)` (never overwrite), server-side edits to any of these folders survive restarts and reloads. Exceptions are caught and logged as a warning (`Valmora.java:487-489`) — a failed extraction does not disable the plugin.

---

## 3. Module System

### 3.1 The `ReloadableModule` contract — `api/ReloadableModule.java:7-34`

```java
public interface ReloadableModule {
    void onEnable();                // init registries, load configs, register listeners
    void onDisable();               // unregister listeners, cancel tasks, clear caches
    String getId();                 // unique lowercase ID
    default String getName() { return getId(); }
}
```

`onEnable()` must be idempotent (hot reload calls it again on a live instance). All state must be initialized in `onEnable()` and reset in `onDisable()` — never in the constructor (`AGENTS.md` §6.1).

### 3.2 `ModuleManager` — `module/ModuleManager.java`

| Member | Behavior | Lines |
|---|---|---|
| Storage | `LinkedHashMap<String, ReloadableModule>` — preserves registration order, keys are lowercase IDs | `ModuleManager.java:18` |
| `registerModule(module)` | `modules.put(module.getId().toLowerCase(), module)`. Does **not** enable. | `ModuleManager.java:28-30` |
| `enableModules()` | Iterates in registration order; each `onEnable()` wrapped in try/catch — a failing module is logged at `SEVERE` and the rest continue (module isolation) | `ModuleManager.java:35-44` |
| `disableModules()` | Iterates in **reverse** registration order; same per-module try/catch | `ModuleManager.java:49-62` |
| `reloadModules()` | `disableModules()` then `enableModules()` | `ModuleManager.java:67-73` |
| `getModules()` | Unmodifiable map view | `ModuleManager.java:75-77` |
| `getModule(id)` | Case-insensitive lookup (caller may pass mixed case) | `ModuleManager.java:79-81` |
| `reloadModule(id)` | Single-module `onDisable()` → `onEnable()` | `ModuleManager.java:87-98` |

### 3.3 Registration order — `Valmora.java:187-222`

The full list (module ID ← field), with the dependency comments from source:

| # | Module ID | Field | Dependency comment (source) |
|---|---|---|---|
| 1 | `script` | `scriptModule` | Foundational — no dependencies |
| 2 | `time` | `timeModule` | "No dependencies; scoreboard and scripts read from it" |
| 3 | `stats` | `statModule` | Foundational |
| 4 | `profiles` | `playerManager` | Foundational |
| 5 | `economy` | `economyModule` | "Depends on playerManager for join/quit lifecycle" |
| 6 | `ui` | `uiManager` | — |
| 7 | `abilities` | `abilityManager` | — |
| 8 | `items` | `itemManager` | — |
| 9 | `mobs` | `mobManager` | — |
| 10 | `skills` | `skillModule` | — |
| 11 | `combat` | `combatModule` | — |
| 12 | `gui` | `guiModule` | — |
| 13 | `recipe` | `recipeModule` | — |
| 14 | `alchemy` | `alchemyModule` | — |
| 15 | `enchants` | `enchantModule` | — |
| 16 | `zones` | `zoneModule` | — |
| 17 | `resources` | `resourceModule` | — |
| 18 | `fishing` | `fishingModule` | — |
| 19 | `npcs` | `npcModule` | — |
| 20 | `warps` | `warpModule` | — |
| 21 | `quests` | `questModule` | — |
| 22 | `points` | `pointsModule` | — |
| 23 | `notify` | `notifyModule` | — |
| 24 | `collections` | `collectionModule` | — |
| 25 | `hud_items` | `hudItemModule` | "Depends on scriptModule for click DSL" |
| 26 | `calendar` | `calendarEventModule` | "Depends on scriptModule + timeModule" |
| 27 | `reforges` | `reforgeModule` | "Depends on recipeModule (registers handler)" |
| 28 | `pets` | `petModule` | "Depends on scriptModule + statModule" |
| 29 | `slayers` | `slayerModule` | "Depends on scriptModule + mobModule" |
| 30 | `accessories` | `accessoryModule` | "Depends on statModule for recalc" |
| 31 | `backpacks` | `backpackModule` | "Depends on abilityManager for mechanic" |
| 32 | `quivers` | `quiverModule` | "Depends on playerManager for the active profile" |
| 33 | `progression` | `progressionModule` | "Depends on scriptModule + pointsModule (generic tree/skill-point engine)" |

Module IDs are confirmed by the `getId()` implementations (e.g. `CombatModule` → `"combat"`, `EnchantModule` → `"enchants"`, `ItemManager` → `"items"`, `AbilityManager` → `"abilities"`, `MobManager` → `"mobs"`, `PlayerManager` → `"profiles"`, `ScriptModule` → `"script"`, `UIManager` → `"ui"`, `RecipeModule` → `"recipe"`, `StatModule` → `"stats"`, `TimeModule` → `"time"`, `SkillModule` → `"skills"`, `GuiModule` → `"gui"`). Note the plural/singular mismatches: `stats`/`skills`/`enchants`/`zones`/`npcs`/`warps`/`quests`/`pets`/`slayers` are plural while `script`, `time`, `economy`, `ui`, `gui`, `recipe`, `alchemy`, `combat` are singular — callers must use the exact ID (e.g. `reloadModule("items")` works, but the item manager's ID is `"items"`).

---

## 4. Public API

### 4.1 `ValmoraAPI` — `api/ValmoraAPI.java`

A static-holder singleton:

```java
// ValmoraAPI.java:11-17
static void setProvider(ValmoraAPI provider) { Holder.provider = provider; }
static ValmoraAPI getInstance()               { return Holder.provider; }
```

`Holder` (`ValmoraAPI.java:72-74`) is a package-private class holding a single static field. `Valmora.onEnable()` calls `ValmoraAPI.setProvider(this)` at step 1 (`Valmora.java:128`), so `Valmora.getInstance()` and `ValmoraAPI.getInstance()` are equivalent at runtime.

Accessor surface (`ValmoraAPI.java:19-70`) — 25 methods:

| Group | Methods |
|---|---|
| Module system | `getModuleManager()` |
| Core engine | `getPlayerManager()`, `getItemManager()`, `getMobManager()`, `getStatModule()`, `getStatRegistry()`, `getSystemStats()`, `getUIManager()`, `getSkillManager()`, `getAbilityManager()`, `getDamageIndicatorManager()`, `getScriptModule()`, `getEnchantModule()`, `getTimeManager()` |
| Economy | `getEconomy()` (`EconomyService`), `getEconomyModule()` |
| Feature managers | `getAlchemyManager()`, `getZoneManager()`, `getNpcManager()`, `getDialogueManager()`, `getWarpManager()`, `getQuestManager()`, `getPointsManager()`, `getNotifyManager()`, `getQuestPackageManager()`, `getProgressionManager()` |

Most feature-module accessors in `Valmora` null-guard the module field (e.g. `Valmora.java:364-366`, `Valmora.java:383-385`, `Valmora.java:400-402`) so a caller during shutdown never sees an NPE.

### 4.2 `EconomyService` — `api/economy/EconomyService.java:5-9`

```java
void addCoins(Player player, double amount);
void removeCoins(Player player, double amount);
double getCoins(Player player);
boolean hasCoins(Player player, double amount);
```

Implemented by `EconomyModule`. This is the *interface-facing* economy (purse only); the concrete module adds purse/bank/withdraw APIs. `Valmora` holds it as a swappable field — `setEconomyService(EconomyService)` (`Valmora.java:378-380`) lets a different backend be injected.

### 4.3 Quest extension point — `api/quest/ObjectiveHandler.java:19-29`

External plugins implement this interface and register via `QuestManager.registerObjectiveHandler(...)`. The engine calls `onQuestStart(player, objective, questManager)` for every objective whose `type` field matches `getTypeId()` when its quest starts. Progress is reported from game listeners by calling `QuestManager.trigger(Player, String, String, int)` (objective type, objective ID/target, amount). Default `onQuestStart` is a no-op.

### 4.4 Scripting contracts — `api/scripting/`

| Interface | Method | Purpose |
|---|---|---|
| `Expression` | `Object evaluate(ExecutionContext)` | Compiled expression AST evaluated to a value | `Expression.java:8-15` |
| `Condition` | `boolean evaluate(ExecutionContext)` | Compiled condition AST | `Condition.java:8-15` |
| `CompiledEvent` | `void execute(ExecutionContext)` | Compiled event/action to run | `CompiledEvent.java:8-14` |

### 4.5 `ExecutionContext` — `api/execution/ExecutionContext.java:16-114`

The single context object threaded through every mechanic, script, and ability invocation. It is **not thread-safe** and must not be stored beyond the current invocation (`AGENTS.md` §7.3).

| Accessor | Returns |
|---|---|
| `getCaster()` | `LivingEntity` that cast/triggered |
| `getPlayerCaster()` | `Optional<Player>` if the caster is a player (default method) |
| `getTarget()` | `Optional<LivingEntity>` |
| `getLocation()` | `Location` |
| `getVariableResolver()` | `VariableResolver` for `$var$` tokens |
| `getTagService()` | `TagService` for player tags |
| `getParams()` | `ConfigurationSection` — per-invocation YAML parameters |

Typed parameter helpers:

- `getDouble/getInt/getString/getBoolean(key, def)` — plain YAML reads (`ExecutionContext.java:61-75`).
- **Formula-capable** accessors:
  - `resolveDouble(key, def)` — accepts a literal number *or* a formula string (`"130 + floor($economy.purse$ / 1000000)"`); non-number strings are evaluated through `ScriptModule.getExpressionEvaluator()` (`ExecutionContext.java:84-97`).
  - `resolveInt(key, def)` — `Math.round(resolveDouble(...))` (`ExecutionContext.java:102-104`).
  - `resolveString(key, def)` — substitutes `$var$` tokens via `getVariableResolver().resolveTemplate(...)` (`ExecutionContext.java:109-113`).

`SimpleExecutionContext` (`api/execution/SimpleExecutionContext.java:13-59`) is the concrete implementation with two constructors (with/without target) and a `null`-safe `getTarget()` returning `Optional.empty()`. It wires `getVariableResolver()` to `ValmoraAPI.getInstance().getScriptModule().getVariableResolver()` and `getTagService()` to `new TagServiceImpl(this)` — so a bare `SimpleExecutionContext` is usable anywhere outside a live module.

### 4.6 Registry contracts — `api/registry/`

- `Registry<T>` (`Registry.java:11-62`): `register(id, entry)`, `unregister(id)`, `get(id)` → `Optional<T>`, `contains(id)`, `getKeys()`, `values()`, `clear()`, `size()`.
- `SimpleRegistry<T>` (`SimpleRegistry.java:15-57`): `HashMap`-backed; `register`/`unregister`/`clear` are `synchronized`; **all keys stored lowercase** (`SimpleRegistry.java:21`); `getKeys()`/`values()` return unmodifiable views. Callers must lowercase lookups, or use the registry's own case-insensitive helpers (`AGENTS.md` §7.2).

### 4.7 `LoadResult<T,E>` — `api/config/LoadResult.java:8-35`

Success/failure container used by all YAML parsers: `LoadResult.success(value)` / `LoadResult.failure(error)`, with `isSuccess()`, `getValue()`, `getError()`. Error type is conventionally `String`.

---

## 5. Database Layer

### 5.1 `DataStore` interface — `database/DataStore.java:8-28`

```java
void init();
CompletableFuture<ValmoraPlayer> loadPlayer(UUID uuid);
CompletableFuture<Void> savePlayer(ValmoraPlayer player);
CompletableFuture<Void> deleteProfile(UUID profileId);
CompletableFuture<double[]> loadEconomy(UUID uuid);          // [purse, bank] or null
CompletableFuture<Void> saveEconomy(UUID uuid, double purse, double bank);
CompletableFuture<Void> saveEconomyBatch(Map<UUID, double[]> balances);
void close();
```

All methods are async `CompletableFuture`s run on a dedicated executor — never on the main thread. Callers touching Bukkit API after completion must schedule back to the main thread (`AGENTS.md` §7.4). `saveEconomyBatch` is the batched write path used by periodic autosave and shutdown flush (one connection, one batch, per `DataStore.java:20-26`).

### 5.2 `DatabaseFactory.createDataStore` — `database/DatabaseFactory.java:12-45`

Reads `database.type` (default `sqlite`), builds a `HikariConfig` with pool name `"Valmora-Pool"` and `maximumPoolSize(10)` (`DatabaseFactory.java:16-18`).

| Type | JDBC URL / driver | Extra |
|---|---|---|
| `mysql` | `jdbc:mysql://<host>:<port>/<db>?useSSL=<bool>` | username/password from config; `cachePrepStmts=true`, `prepStmtCacheSize=250`, `prepStmtCacheSqlLimit=2048` | `DatabaseFactory.java:20-33` |
| `sqlite` (default) | `jdbc:sqlite:<plugins>/Valmora/database.db`, driver `org.sqlite.JDBC` | `PRAGMA journal_mode=WAL` as `connectionInitSql` | `DatabaseFactory.java:35-43` |

WAL mode lets the (now infrequent, batched) writer proceed concurrently with readers instead of blocking under SQLite's default rollback journal (`DatabaseFactory.java:39-41`).

### 5.3 `SQLDataStore` — `database/SQLDataStore.java`

Backs `DataStore`. Members: `HikariDataSource`, `Gson`, `isMySQL` flag, and a dedicated 4-thread `dbExecutor` (`SQLDataStore.java:34`).

**Schema versioning.** `LATEST_SCHEMA_VERSION = 2` (`SQLDataStore.java:48`). `init()` (`SQLDataStore.java:51-72`) creates the `valmora_schema_version` table (`SQLDataStore.java:74-81`), reads the current version (`0` for fresh, `SQLDataStore.java:84-90`), warns-and-returns if the DB is *newer* than the plugin, otherwise runs `applyMigrations`. A `SQLException` during init rethrows as `IllegalStateException` so `Valmora.onEnable()` can fail fast and disable rather than silently lose data (`SQLDataStore.java:66-71`).

`applyMigrations(from)` (`SQLDataStore.java:104-115`) runs each migration gated on `from`, stamping progress after each via `setSchemaVersion` (MySQL `ON DUPLICATE KEY UPDATE`, SQLite `ON CONFLICT(id) DO UPDATE`, `SQLDataStore.java:92-101`).

**Migrations:**

- **v1** (`SQLDataStore.java:123-161`) — baseline, idempotent (`CREATE TABLE IF NOT EXISTS`):
  - `valmora_players (uuid VARCHAR(36) PK, active_profile VARCHAR(36))`
  - `valmora_profiles (id VARCHAR(36) PK, player_uuid, name, stats, skills, player_state, tags, variables, collections, inventory)` plus `addColumnIfMissing` for `tags`, `variables`, `collections`, `inventory`, `created_at BIGINT NOT NULL DEFAULT 0`, `last_used BIGINT NOT NULL DEFAULT 0` (upgrades pre-versioning databases in place)
  - `valmora_economy (uuid VARCHAR(36) PK, purse DOUBLE NOT NULL DEFAULT 0, bank DOUBLE NOT NULL DEFAULT 0)`
- **v2** (`SQLDataStore.java:118-120`) — adds `quiver TEXT` to `valmora_profiles` (per-profile arrow storage).

`addColumnIfMissing` (`SQLDataStore.java:164-171`) swallows the "column already exists" `SQLException`, making all migrations safe on fresh and re-run databases.

**Read path — `loadPlayer(uuid)`** (`SQLDataStore.java:174-264`): async; reads `active_profile`, then loads profiles `ORDER BY created_at ASC, id ASC`; deserializes each column with Gson `TypeToken`s — `Map<String,Double>` for stats/skills, `double[]` for `player_state`, `Set<String>` for tags, `Map<String,Object>` for variables, `Map<String,Long>` for collections, and base64 item arrays for `inventory`/`quiver`. Returns `null` when the player has no row. A per-profile failure is logged and the loop continues.

**Write path — `savePlayer(player)`** (`SQLDataStore.java:267-338`): single transaction (`setAutoCommit(false)` → upsert `valmora_players` → batch upsert `valmora_profiles` → `commit()`). `created_at` is insert-only (preserves creation order); `last_used` is updated on every save (`SQLDataStore.java:285-331`).

**Item serialization** (`SQLDataStore.java:353-435`): `serializeInventory` packs slots 0-35 (storage) + 36-39 (armor) + 40 (offhand) into a 41-element base64 JSON array using `ItemStack.serializeAsBytes()`; `deserializeInventory` reverses it. `serializeItemArray`/`deserializeItemArray` are the generic fixed-size helpers used for the quiver.

**Economy paths** — `loadEconomy` (`SQLDataStore.java:438-452`, returns `double[]{purse,bank}` or `null`), `saveEconomy` single upsert (`SQLDataStore.java:455-472`), `saveEconomyBatch` one-connection batched upsert with a single `commit()` (`SQLDataStore.java:475-504`); an empty map short-circuits to a completed future without touching the pool (`SQLDataStore.java:476`).

**Shutdown — `close()`** (`SQLDataStore.java:507-521`): `dbExecutor.shutdown()`, await termination up to 10s then `shutdownNow()`, then `hikari.close()`.

---

## 6. Infrastructure & Config

### 6.1 `YamlLoader<T>` — `infrastructure/config/YamlLoader.java`

The generic content loader every module's `XLoader` is built on (`AGENTS.md` §7.1). Constructor takes the plugin, the folder name (relative to the data folder), and a display `typeName` for log messages (`YamlLoader.java:25-30`).

Two loading modes:

- **`load(parser, registerAction)`** (`YamlLoader.java:37-73`) — iterates `.yml` files in the folder; for each top-level key it passes `(key, section, relativePath)` to the `SectionParser`. One file may define multiple objects (key = object ID).
- **`loadFilesAsSections(parser, registerAction)`** (`YamlLoader.java:78-111`) — treats each file as a single object; the object ID is the filename without `.yml`.

Both modes: create the folder if missing, collect per-parse errors as strings, call `registerAction.accept(parsed)` on success. Errors and the loaded count are reported through `reportErrors` (`YamlLoader.java:113-123`): warnings prefixed with `- ` and a final `Successfully loaded N <typeName>.` info line. A whole-file YAML parse failure is caught and reported as `[<relativePath>] Failed to parse YAML: <msg>`.

```java
// YamlLoader.java:129-132 — the parser contract
@FunctionalInterface
public interface SectionParser<T> {
    LoadResult<T, String> parse(String id, ConfigurationSection section, String filePath);
}
```

---

## 7. Utilities

### 7.1 `Keys` — `util/Keys.java`

Static holder for every `NamespacedKey` the plugin uses. `Keys.init(plugin)` (`Keys.java:42-77`) is called once in `onEnable()` step 4 (`Valmora.java:138`); **before init all fields are `null`**. Full inventory:

| Field | Namespaced value | Field | Namespaced value |
|---|---|---|---|
| `ITEM_ID_KEY` | `valmora_item_id` | `NPC_ID_KEY` | `valmora_npc_id` |
| `RARITY_KEY` | `rarity` | `WARP_ID_KEY` | `valmora_warp_id` |
| `ITEM_TYPE_KEY` | `item_type` | `MOB_HOME_KEY` | `mob_home` |
| `STATS_CONTAINER_KEY` | `item_stats_container` | `ZONE_WAND_KEY` | `zone_wand` |
| `MOB_ID_KEY` | `valmora_mob_id` | `HUD_ITEM_KEY` | `hud_item_id` |
| `ENCHANTS_CONTAINER_KEY` | `valmora_enchants_container` | `REFORGE_ID_KEY` | `reforge_id` |
| `GENERIC_BASE_LORE_KEY` | `valmora_generic_base_lore` | `REFORGE_POOL_KEY` | `reforge_pool` |
| `FURNACE_OWNER_KEY` | `valmora_furnace_owner` | `REFORGE_DISPLAY_KEY` | `reforge_display` |
| `ALCHEMY_EFFECT_ID` | `alchemy_effect_id` | `PET_ID_KEY` | `pet_id` |
| `ALCHEMY_EFFECT_LEVEL` | `alchemy_effect_level` | `PET_XP_KEY` | `pet_xp` |
| `ALCHEMY_DURATION` | `alchemy_duration` | `PET_LEVEL_KEY` | `pet_level` |
| `ALCHEMY_IS_SPLASH` | `alchemy_is_splash` | `SLAYER_BOSS_KEY` | `slayer_boss` |
| `ALCHEMY_LEVEL_MODIFIED` | `alchemy_level_modified` | `BACKPACK_CONTENTS_KEY` | `backpack_contents` |
| `ALCHEMY_DURATION_MODIFIED` | `alchemy_duration_modified` | `BACKPACK_SIZE_KEY` | `backpack_size` |

PDC keys are the canonical way Valmora identifies its items/mobs/GUI buttons — never display-name checks (`AGENTS.md` §11.12).

### 7.2 `Formatter` — `util/Formatter.java`

The single text helper, built on a shared `MiniMessage` instance whose `postProcessor` forces `ITALIC = FALSE` on every component (`Formatter.java:11`) — this kills the auto-italic that otherwise appears on item lore.

```java
Formatter.format(String)            // MiniMessage → Component
Formatter.formatList(List<String>)  // stream map
Formatter.capitalize(String)        // "abc" → "Abc"
```

All Valmora display text goes through MiniMessage (`AGENTS.md` §7.5) — no `ChatColor`/`§`.

---

## 8. Configuration

### 8.1 `plugin.yml` — `src/main/resources/plugin.yml`

| Key | Value |
|---|---|
| `name` | `Valmora` |
| `version` | `${version}` (interpolated by Gradle) |
| `main` | `org.nakii.valmora.Valmora` |
| `api-version` | `1.21` |
| `authors` | `[nakii]` |
| `depend` | `[packetevents]` |
| `description` | `A MMORPG engine for creating anything one can imagine.` |

Commands (see §9) with inline `permission:` — `valmora.admin` on `/item`, `/mob`, `/potion`, `/eco`, `/zone`, `/npc`, `/valmora`; `valmora.admin.gui` on `/gui` (`plugin.yml:9-69`).

### 8.2 `config.yml` — root config (auto-saved on first run)

| Section | Key | Type | Default | Lines |
|---|---|---|---|---|
| `database` | `type` | `sqlite` \| `mysql` | `sqlite` | `config.yml:5-12` |
| `database.mysql` | `host` / `port` / `database` / `username` / `password` / `use-ssl` | string/int/bool | `localhost` / `3306` / `valmora` / `root` / *(empty)* / `false` | `config.yml:17-28` (commented out) |
| `economy` | `autosave-interval-seconds` | long | `60` | `config.yml:33-38` |
| `profiles` | `max-profiles` | int | `4` | `config.yml:43-45` |
| `profiles` | `default-name` | string | `Earth` | `config.yml:47-48` |
| `profiles` | `planet-names` | string list | 12 names (Mars…Europa) | `config.yml:50-63` |
| `time` | `world` | string | `world` | `config.yml:68-70` |
| `time` | `start-year` / `start-season` / `start-phase` / `start-day` | — | `1` / `SPRING` / `EARLY` / `1` | `config.yml:72-76` |
| `time` | `season-names` / `phase-names` | string lists | `[Spring, Summer, Autumn, Winter]` / `[Early, Mid, Late]` | `config.yml:79-80` |
| `time` | `scoreboard-enabled` | bool | `true` | `config.yml:82-83` |
| `combat` | `health-stat` … `luck-stat` | stat IDs | `health`, `mana`, `damage`, `strength`, `defense`, `crit_chance`, `crit_damage`, `speed`, `health_regen`, `mana_regen`, `luck` | `config.yml:90-101` |
| `mining` | `mining-fortune-stat` / `mining-speed-stat` / `breaking-power-stat` / `mining-spread-stat` | stat IDs | `mining_fortune` / `mining_speed` / `breaking_power` / `mining_spread` | `config.yml:106-110` |
| `npc-skin-server` | `enabled` / `port` / `host` | bool/int/string | `false` / `2525` / *(blank, auto-detect)* | `config.yml:115-122` |
| `alchemy` | `splash-radius` / `tick-interval` / `max-active-effects` | double/int | `4.0` / `20` / `10` | `config.yml:127-135` |

The `combat:`/`mining:` sections map engine-internal roles to `stats/*.yml` stat IDs — renaming a core stat requires updating these mappings too (`config.yml:86-88` comment).

### 8.3 `ui.yml` — UI root config (loaded by the `ui` module)

All text supports MiniMessage + `$variable$` tokens (`$player.*$`, `$time.*$`, `$zone.*$`, `$economy.*$`, `$server.*$`, `$world.*$`, `$stat.*$`) (`ui.yml:4-6`).

| Key | Default | Lines |
|---|---|---|
| `scoreboard.title` | `<gold><bold>VALMORA RPG` | `ui.yml:10` |
| `scoreboard.lines` | 9 lines, incl. a literal `"$dynamic$"` placeholder for the dynamic section (combat lock, dialogue, …) | `ui.yml:14-23` |
| `action-bar.default` | `<red>❤ $player.hp$/$player.max_hp$ <dark_gray>\| <green>❈ $player.stat.defense$ Defense <dark_gray>\| <aqua>⛨ $player.mana$/$player.max_mana$ Mana` | `ui.yml:30` |
| `tab.header` / `tab.footer` | `<gold><bold>VALMORA</bold></gold>` / `<gray>Players online: <white>$server.online$` | `ui.yml:36-37` |

---

## 9. Commands

All command executors are registered **in `Valmora.onEnable()` after `enableModules()`** (`Valmora.java:227-259`) — never inside a module (`AGENTS.md` §6.3). Feature-module commands are wired here too; the per-module design docs hold their deep details.

| Command | Executor | Wired at | Extra wiring |
|---|---|---|---|
| `/quest` | `QuestCommand` | `Valmora.java:228` | — |
| `/npc` | `NpcCommand` | `Valmora.java:229-231` | `setTabCompleter` |
| `/valmora` | `ValmoraCommand` | `Valmora.java:232` | — |
| `/profile` | `ProfileCommand` | `Valmora.java:233-235` | `setTabCompleter` |
| `/stat` | `StatCommand` | `Valmora.java:236` | — |
| `/item` | `ItemCommand` | `Valmora.java:237` | — |
| `/mob` | `MobCommand` | `Valmora.java:238` | — |
| `/skill` | `SkillCommand` | `Valmora.java:239` | — |
| `/gui` | `GuiCommand` | `Valmora.java:240` | — |
| `/time` | `TimeCommand` | `Valmora.java:241` | — |
| `/eco` | `EcoCommand` | `Valmora.java:242` | **no `setTabCompleter`** (see §10) |
| `/potion` | `PotionCommand` | `Valmora.java:243` | — |
| `/effects` | `EffectsCommand` | `Valmora.java:244` | — |
| `/warp` | `WarpCommand` | `Valmora.java:245` | — |
| `/zone` | `ZoneCommand` | `Valmora.java:246-248` | `setTabCompleter` |
| `/collections` | `CollectionCommand` | `Valmora.java:249` | — |
| `/accessories` | lambda | `Valmora.java:250-254` | `accessoryModule.openAccessoryBag(player)` |
| `/quiver` | lambda | `Valmora.java:255-259` | `quiverModule.openQuiver(player)` |

### 9.1 `ValmoraCommand` — the engine master command — `ValmoraCommand.java`

- **`/valmora npc-choice <index>`** (`ValmoraCommand.java:29-37`) — internal route used by dialogue GUIs; **no permission**, **player-only**. Parses `args[1]` as an int and calls `plugin.getDialogueManager().handleChoice(player, index)`. A `NumberFormatException` is silently swallowed.
- **`/valmora reload`** (`ValmoraCommand.java:49-54`) — requires `valmora.admin`; calls `plugin.getModuleManager().reloadModules()`.
- **`/valmora variable get <path>`** (`ValmoraCommand.java:56-61`, `67-85`) — wraps the path in `$`…`$`, builds a `SimpleExecutionContext` from the sender's player/location, and prints `ScriptModule.getVariableResolver().resolve(...)`. Reports `<red>Variable <gray><path> <red>is null or not found.` when null.
- Tab completion (`ValmoraCommand.java:87-108`): `reload`, `variable` → `get` → registered variable-provider keys (from `getVariableProviderRegistry().getKeys()`).
- No-permission and help paths (`ValmoraCommand.java:39-42`, `110-114`).

---

## 10. Unfinished Things / TODOs

Core-scope items:

- **`/eco` tab completion is dead code.** `EcoCommand` implements a full `onTabComplete` (`EcoCommand.java:112-132`), but `Valmora.java:242` calls only `setExecutor(...)` — `setTabCompleter` is never invoked.
- **`/npc reload` reloads *all* modules.** `NpcCommand.cmdReload` (`NpcCommand.java:514-518`) calls `moduleManager.reloadModules()`, not `reloadModule("npcs")` — heavier than its name implies.
- **Shutdown is synchronous per player.** `Valmora.onDisable()` blocks on `dataStore.savePlayer(player).join()` in a loop over all online sessions (`Valmora.java:270-275`); at scale this stalls shutdown (the economy module's batched flush is the pattern this could follow).
- **Hard `depend: [packetevents]`** (`plugin.yml:6`) — the plugin will not load without the PacketEvents plugin installed; no soft dependency or fallback.
- **`saveAllResources` uses a JAR walk** (`Valmora.java:456-490`) — relies on `getCodeSource().getLocation()` pointing at a real file; in a dev/exploded environment (`jarFile.isFile()` false) it silently returns (`Valmora.java:459`), which is fine but means first-run extraction only happens from a real jar.
- **Docs drift:** `AGENTS.md` §5 lists the *documentation* module order (`script → stat → player → ui → ability → item → mob → skill → combat → gui → recipe → enchant`); the actual `Valmora.java` order (§3.3) is longer and different. `docs/UNFINISHED_FEATURES.md` §1-§12 catalog feature-level gaps; `docs/todo.md` lists the roadmap.

Roadmap items from `docs/todo.md:3-21` that touch core: reword the plugin DSL with better user feedback (`plugin`), and `docs/todo.md:92` lists **Folia support** as a last-stage item — relevant because `Valmora.java` uses global `Bukkit` schedulers indirectly through modules and a blocking `join()` shutdown.

---

## 11. Possible Improvements / Changes

- **Batched shutdown save.** Replace the per-player `savePlayer(...).join()` loop (`Valmora.java:270-275`) with the economy's batched-transaction pattern (`saveEconomyBatch` style) to avoid O(n) blocking shutdown.
- **Wire `/eco` tab completion** with `getCommand("eco").setTabCompleter(new EcoCommand(economyModule))` in `Valmora.java:242`.
- **Make `ModuleManager.reloadModules()` order-aware.** It currently disables in reverse order and enables in forward order, which is correct; but a per-module dependency validation at registration time (detecting cycles / forward references) would turn silent load-order bugs into startup errors.
- **`ValmoraAPI` accessor consistency.** Several `Valmora` getters lack `@Override` where the interface declares them (e.g. `getEnchantModule` `Valmora.java:349`), and some feature getters exist only on `Valmora` (not on the interface) — aligning the surface would make the API easier to mock in tests.
- **Permission centralization.** There is no `permissions:` block in `plugin.yml`; every permission is declared inline per command plus re-checked in code. A central block (with descriptions) would make `valmora.admin` / `valmora.admin.gui` discoverable by permission plugins.
- **`Formatter.capitalize` is not null/empty-safe** (`Formatter.java:21-23`) — `""` throws `StringIndexOutOfBoundsException`. Guard or document the precondition.
- **Soft-dependency the PacketEvents binding** (optional dependency + feature-gated init) if the engine should ever boot without it.
- **Test coverage of the boot sequence.** Existing tests cover `SQLDataStore` (real temp SQLite: schema stamping, idempotent init, pre-versioning migration, economy round-trips, batch upsert, empty-batch no-op), `EconomyData`, `CoinExpressionParser`, `Expression`, and `YamlConfigLoadTest` — but there is no unit test that asserts the module registration order or that every module ID is unique/lowercase.

---

## Tests

Core-relevant test files (all under `src/test/java/org/nakii/valmora/`):

| Test | Covers |
|---|---|
| `database/SQLDataStoreTest.java` (`@Tag("database")`) | Real temporary SQLite: `init` schema + version stamp, idempotent init, pre-versioning migration, economy round-trip, economy batch upsert, empty-batch no-op |
| `module/economy/EconomyDataTest.java` | Concurrency/atomicity of `EconomyData` (16-thread increment, clamp-at-zero, atomic deposit/withdraw) |
| `module/economy/CoinExpressionParserTest.java` | Coin-expression grammar and error cases |
| `module/script/expression/ExpressionTest.java` | Canonical `ValmoraAPI.setProvider(mock(...))` setup pattern (`AGENTS.md` §9) |
| `config/YamlConfigLoadTest.java` | Root YAML config loading |

No tests require a live server; `ValmoraAPI` is mocked via `ValmoraAPI.setProvider(mockApi)` in `@BeforeEach`.
