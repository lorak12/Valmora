# Collection Module — Design & Code

> **Module ID:** `"collections"` (see `CollectionModule.java:38` — note the *plural*; docs elsewhere say `"collection"`)
> **Package:** `org.nakii.valmora.module.collection`
> **API:** Paper 1.21.x | **Java:** 21 | **Runtime folder:** `plugins/Valmora/collections/`

---

## Overview

The Collection module implements a persistent, per-profile **collection tracking system**. Every player profile
carries a `CollectionManager` that stores raw counters keyed by collection ID. `CollectionListener` listens to
gameplay events (block break, mob kill, fishing, item pickup, crafting) and increments the matching counters. When a
counter crosses a configured threshold ("stage"), the stage's reward script list is executed.

The module is *definition-driven*: all categories and collections come from YAML files under
`plugins/Valmora/collections/`. There is no GUI code inside the module itself — the menu is a standard Gui module GUI
(`collections_categories`, `collections_list`, `collections_detail`), and `CollectionVariableProvider` feeds the
`$collection.*$` variables those GUIs consume.

Key properties:

- **Data ownership:** per-`ValmoraProfile` (not per-player). Switching the active profile switches collection progress
  (`ValmoraProfile.java:21`, `ValmoraProfile.java:66`).
- **Persistence:** serialized as a JSON `Map<String, Long>` (collectionId → count) in the `collections TEXT` column
  of the `valmora_profiles` table (`SQLDataStore.java:141`, load at `SQLDataStore.java:234-238`, save at
  `SQLDataStore.java:301`).
- **Rewards:** executed through the Script module's event parser when a stage boundary is crossed
  (`CollectionListener.java:104-114`). No claim mechanism; rewards fire automatically.
- **Hot-reload safe:** `onEnable()`/`onDisable()` clear the registry and unregister the listener
  (`CollectionModule.java:18-35`).

---

## Code Structure (file-by-file)

All files live in `src/main/java/org/nakii/valmora/module/collection/` (11 files).

### `CollectionModule.java` (44 lines)

The `ReloadableModule` entry point.

- Implements `ReloadableModule` (`CollectionModule.java:6`).
- Holds `Valmora plugin` and `CollectionRegistry registry`; `registry` is constructed in the constructor, not in
  `onEnable()` (`CollectionModule.java:12-15`). Note this is a **field final**, surviving reloads; only its *contents*
  are cleared on enable/disable.
- `onEnable()` (`CollectionModule.java:18-25`):
  1. `registry.clear()`
  2. `new CollectionLoader(plugin, registry).loadCollections()`
  3. `plugin.getScriptModule().registerProvider(new CollectionVariableProvider(plugin))` — registers the
     `$collection.*$` variable namespace with the Script module.
  4. Creates `CollectionListener` and registers it with the plugin manager.
- `onDisable()` (`CollectionModule.java:28-35`): `HandlerList.unregisterAll(listener)`, nulls it, clears the registry.
- `getId()` returns `"collections"` (`CollectionModule.java:38`) — **plural**. `getName()` returns `"Collection System"`
  (`CollectionModule.java:41`).
- Public accessor: `getRegistry()` (`CollectionModule.java:43`).

### `CollectionCategory.java` (20 lines)

Immutable value object for a category: `id`, `name`, `icon`, `description` with plain getters
(`CollectionCategory.java:9-19`). No logic.

### `CollectionDefinition.java` (48 lines)

Immutable value object for one collection.

- Fields: `id`, `categoryId`, `name`, `icon`, `trackSources` (`List<String>`, each `"EVENT_TYPE:IDENTIFIER"`),
  `stages` (`List<CollectionStage>`) (`CollectionDefinition.java:6-12`).
- `matches(String eventType, String identifier)` (`CollectionDefinition.java:24-26`): returns
  `trackSources.contains(eventType + ":" + identifier)` — an **exact, case-sensitive substring-free containment check**.
  The listener always feeds *uppercase* Bukkit enum names (e.g. `"BLOCK_BREAK:COAL_ORE"`) and *lowercase* custom ids
  (`"ITEM_PICKUP:custom:myitem"`), so YAML entries must use the same casing.
- `getStageForCount(long count)` (`CollectionDefinition.java:28-38`): returns the **highest stage number** whose
  `required <= count`, or `0` if none. Stages must be sorted ascending; the parser sorts them at load time
  (`CollectionDefinitionParser.java:43`).
- `getMaxStage()` returns `stages.size()` (`CollectionDefinition.java:40`).
- Plain getters for the rest.

### `CollectionStage.java` (19 lines)

Immutable value object: `number` (int), `required` (long), `rewards` (`List<String>`). Plain getters only
(`CollectionStage.java:10-18`).

### `CollectionRegistry.java` (45 lines)

Registry of categories and collections.

- Backed by two `LinkedHashMap`s (insertion-ordered), keys **lowercased** on register (`CollectionRegistry.java:7-8`,
  `10-16`).
- `getCategory(id)` / `getCollection(id)` return `Optional`, lowercasing the lookup key
  (`CollectionRegistry.java:18-24`).
- `getCategories()` / `getCollections()` return the map `values()` collections directly (not wrapped unmodifiable)
  (`CollectionRegistry.java:26-32`).
- `getCollectionsInCategory(categoryId)` (`CollectionRegistry.java:34-39`): streams all collections and filters by
  `def.getCategoryId().equalsIgnoreCase(lower)`. Does **not** validate the category exists.
- `clear()` empties both maps (`CollectionRegistry.java:41-44`).

### `CollectionManager.java` (32 lines)

Per-profile mutable counter store. **Not thread-safe; use on the main thread.**

- `counts`: `Map<String, Long>`, keys lowercased (`CollectionManager.java:7`).
- `getCount(id)` → `0L` default (`CollectionManager.java:9-11`).
- `addCount(id, amount)` → `merge(id.toLowerCase(), amount, Long::sum)` (`CollectionManager.java:13-15`).
- `getCurrentStage(id, def)` → null-safe `def.getStageForCount(getCount(id))`; `def == null` → `0`
  (`CollectionManager.java:17-20`).
- `loadData(Map<String, Long>)` clears and reloads, lowercasing keys (`CollectionManager.java:22-27`).
- `getSaveData()` returns a defensive copy (`CollectionManager.java:29-31`).

### `CollectionListener.java` (117 lines)

The event-driven incrementer. All handlers are `@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)`
(`CollectionListener.java:39`, `46`, `55`, `69`, `87`).

- `getProfile(Player)` (`CollectionListener.java:33-37`): `playerManager.getSession(uuid).getActiveProfile()`, or
  `null` if no session/profile. Every handler bails when the profile is null.
- `onBlockBreak` (`CollectionListener.java:40-44`): `trackEvent(player, profile, "BLOCK_BREAK",
  block.getType().name())`.
- `onEntityDeath` (`CollectionListener.java:47-53`): only when `entity.getKiller() != null`; feeds
  `"MOB_KILL"` + `entityType.name()`.
- `onFish` (`CollectionListener.java:56-67`): only for `PlayerFishEvent.State.CAUGHT_FISH` with a non-null caught
  entity. If the caught entity is an `Item`, uses the item stack's material name; **otherwise hardcodes `"COD"`**
  (`CollectionListener.java:62-64`) — a caught `Fish` entity (salmon, pufferfish…) is misattributed to COD.
  Feeds `"FISHING"`.
- `onItemPickup` (`CollectionListener.java:70-85`): players only. Feeds `"ITEM_PICKUP"` + item material name, and
  *additionally* feeds `"ITEM_PICKUP:custom:<id>"` when the item's PDC carries `Keys.ITEM_ID_KEY`
  (`CollectionListener.java:78-84`).
- `onCraft` (`CollectionListener.java:88-93`): feeds `"CRAFT"` + recipe result material name.
- `trackEvent(...)` (`CollectionListener.java:95-116`):
  1. Loops **every** collection in the registry (`CollectionListener.java:97`).
  2. Skips non-matching definitions.
  3. Captures `oldStage`, increments by 1, captures `newStage`
     (`CollectionListener.java:100-102`).
  4. If a boundary was crossed, builds a `SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration())`
     (empty params!) and executes the reward list of every stage with `number > oldStage && number <= newStage` and a
     non-empty rewards list via `plugin.getScriptModule().getEventParser().parseList(...).execute(ctx)`
     (`CollectionListener.java:104-114`).

### `CollectionLoader.java` (79 lines)

YAML discovery + parse orchestration. **Deviation:** does not use the shared `YamlLoader` (AGENTS.md §7.1); it
hand-rolls `File` + `YamlConfiguration`.

- `loadCollections()` (`CollectionLoader.java:19-37`):
  - Root folder = `new File(plugin.getDataFolder(), "collections")` (`CollectionLoader.java:20`).
  - Warns and returns if the folder does not exist (`CollectionLoader.java:21-24`).
  - Loads `collections/categories.yml` if present, else warns (`CollectionLoader.java:26-31`).
  - `loadCollectionsRecursive(folder)` walks all subdirectories (`CollectionLoader.java:33`).
  - Logs the loaded counts (`CollectionLoader.java:35-36`).
- `loadCategories(File)` (`CollectionLoader.java:39-51`): reads each top-level key of `categories.yml`, lowercases it,
  parses via `CollectionDefinitionParser.parseCategory`, and registers. Parse failures are caught per-key with a
  warning (`CollectionLoader.java:47-49`).
- `loadCollectionsRecursive(File)` (`CollectionLoader.java:53-63`): recurses into subdirectories; processes `.yml`
  files, **excluding any file literally named `categories.yml`** (`CollectionLoader.java:59`).
- `loadCollectionFile(File)` (`CollectionLoader.java:65-78`): each top-level key becomes a collection; lowercased id;
  per-key try/catch with file name in the warning.

### `CollectionDefinitionParser.java` (47 lines)

Static factory methods.

- `parseCategory(id, section)` (`CollectionDefinitionParser.java:11-16`):
  - `name` default → `id`
  - `icon` default → `"CHEST"`, `.toUpperCase()`
  - `description` default → `""`
- `parseCollection(id, section)` (`CollectionDefinitionParser.java:18-46`):
  - `category` default → `"misc"` (`CollectionDefinitionParser.java:19`)
  - `name` default → `id`
  - `icon` default → `"STONE"`, `.toUpperCase()`
  - `track` → `section.getStringList("track")` (`CollectionDefinitionParser.java:23`)
  - `stages`: iterates child keys, **skips non-integer keys** (`CollectionDefinitionParser.java:29-34`); each stage:
    `required` default `0` (long), `rewards` string list (`CollectionDefinitionParser.java:38-40`).
  - Stages are sorted ascending by number (`CollectionDefinitionParser.java:43`).

### `CollectionVariableProvider.java` (213 lines)

Script variable provider, namespace `"collection"` (`CollectionVariableProvider.java:28-30`), registered by
`CollectionModule.onEnable()`.

- `resolve(path, context)` (`CollectionVariableProvider.java:33-67`): resolves the player from
  `context.getPlayerCaster()`; bails if there is no player, no session profile, or no `CollectionModule` instance
  (`CollectionVariableProvider.java:36-46`). It obtains the module via the **concrete** `plugin.getCollectionModule()`,
  not `ValmoraAPI` (see API section). Detects `GuiSession` only when the context is a `GuiExecutionContext`
  (`CollectionVariableProvider.java:50-53`).
- All list values are returned as **Gson JSON strings**; the GUI paginated components consume them as lists.

| Variable | Method | Returns |
|---|---|---|
| `category_list` | `buildCategoryList` (`CollectionVariableProvider.java:71-90`) | JSON array of `{id, name, icon, description, total, completed}` — one object per category; `completed` counts fully-maxed collections inside it. |
| `item_list` | `buildItemList` (`CollectionVariableProvider.java:94-125`) | Requires session prop `selected_category`; JSON array of `{id, name, icon, count, stage, max_stage, next_required, status}`. `next_required` is the first stage threshold above the current stage; `status` is `"completed"` or `"in_progress"`. |
| `stage_list` | `buildStageList` (`CollectionVariableProvider.java:129-159`) | Requires session prop `selected_collection`; JSON array of `{number, required, rewards (lines joined with `\n`), status}` where `status` ∈ `completed` / `current` / `locked`. |
| `detail_name` | `CollectionVariableProvider.java:163-166` | `def.getName()` or `"?"`. |
| `detail_icon` | `CollectionVariableProvider.java:168-171` | `def.getIcon()` or `"BARRIER"`. |
| `detail_count` | `CollectionVariableProvider.java:173-178` | `manager.getCount(selected_collection)` (props required). |
| `detail_stage` | `CollectionVariableProvider.java:180-186` | `manager.getCurrentStage(id, def)` (0 = not started). |
| `detail_max_stage` | `CollectionVariableProvider.java:188-191` | `def.getMaxStage()` or `0`. |
| `detail_next_required` | `CollectionVariableProvider.java:193-205` | First stage threshold above current stage, else **the current count** (not `-1`, despite docs claiming `-1`). |

### `CollectionCommand.java` (34 lines)

`CommandExecutor` for `/collections`.

- Non-players get `"Only players can use this command."` (`CollectionCommand.java:20-23`).
- Null-checks `plugin.getGuiModule()` (`CollectionCommand.java:25-29`).
- Opens GUI `"collections_categories"` (`CollectionCommand.java:31`).
- No subcommands, no arguments, no permission check in code.

---

## Architecture & Key Classes

### Collection tracking flow

```
Gameplay event
  └─ CollectionListener (MONITOR, ignoreCancelled)
        └─ trackEvent(player, profile, EVENT_TYPE, IDENTIFIER)
              └─ for each def in registry:
                    matches(EVENT_TYPE + ":" + IDENTIFIER)?
                        ├─ oldStage = def.getStageForCount(count)
                        ├─ manager.addCount(def.id, 1)      ← profile-local counter
                        ├─ newStage = def.getStageForCount(count)
                        └─ if newStage > oldStage → execute reward scripts for crossed stages
```

### Tiers / stages

- Stages are **cumulative thresholds**, not per-stage increments. A stage with `required: 250` means *total* count
  must reach 250 (`CollectionDefinition.getStageForCount` walks the sorted stage list and keeps the highest reached
  number).
- Stage numbers are 1-indexed; `0` means "not started" (`CollectionVariableProvider.java:180-186`, GUI displays
  stage 0 as the "not started" baseline).
- The current stage is derived, never stored.

### Rewards

- Executed via the Script module: `getEventParser().parseList(stage.getRewards()).execute(ctx)`
  (`CollectionListener.java:109-112`).
- A single crossing can fire multiple stage rewards (bulk grant when several thresholds are passed at once,
  `CollectionListener.java:106-108`).
- The `ExecutionContext` is a bare `SimpleExecutionContext` with **empty params** and no target
  (`CollectionListener.java:105`). The caster is the player; `getPlayerCaster()` resolves for the reward events that
  need it.
- **Important caveat:** the shipped resource YAMLs (`coal.yml` etc.) put *MiniMessage display strings* like
  `"<gray>Novice Miner title"` in `rewards`, which are passed through the script parser and do not perform gameplay
  actions. Real executable reward events (e.g. `economy_add 100`) are supported by the engine but not shipped.

### GUI

There is no GUI code in the module. The three views are Gui-module definitions under
`src/main/resources/guis/`:

- `collections_categories.yml` — the `/collections` landing screen; `PAGINATED` component iterating
  `$collection.category_list$`; left-click opens `collections_list` with `selected_category=$cat.id$`
  (`collections_categories.yml:46-49`).
- `collections_list.yml` — per-category item browser; `PAGINATED` over `$collection.item_list$`; two states
  (`completed_state` when `$col.status$ == completed`, else `default`); click opens `collections_detail` with
  `selected_collection=$col.id$` (`collections_list.yml:37-63`).
- `collections_detail.yml` — summary icon (`I`, from `$collection.detail_*$`) plus a paginated stage list (`S`)
  with three states `completed_stage` / `current_stage` / `default` (`collections_detail.yml:45-76`).

Session props `selected_category` and `selected_collection` are passed between views by the `open_gui` script event
and read by `CollectionVariableProvider` (`CollectionVariableProvider.java:96`, `131`, `175`, `182`, `195`, `209`).

### Commands

Only `/collections`, registered centrally in `Valmora.onEnable()` (`Valmora.java:249`):
`getCommand("collections").setExecutor(new CollectionCommand(this))`. Declared in `plugin.yml:61-63` with no
permission, so **any player** can use it. There is no admin subcommand for inspecting/resetting counts.

---

## Configuration (YAML)

Runtime folder: `plugins/Valmora/collections/`. Defaults are shipped under
`src/main/resources/collections/` and copied out by `saveAllResources()` (`Valmora.java:475` — the `collections/`
prefix is in the auto-copy whitelist; existing files are never overwritten, `Valmora.java:480-483`).

### Category definition — `collections/categories.yml`

Single file at the collections root holding **all** categories as top-level keys
(`CollectionLoader.java:26-31`). (Note: this differs from older docs that described a per-folder `category.yml`.)

```yaml
<category-id>:
  name: "<MiniMessage name>"
  icon: <MATERIAL>
  description: "<single line string>"
```

| Key | Type | Default | Description |
|---|---|---|---|
| `<category-id>` | string (map key) | — | Category ID. Lowercased at load (`CollectionLoader.java:45`). Must be unique. |
| `name` | string (MiniMessage) | `id` | Display name shown in the category menu (`CollectionDefinitionParser.java:12`). |
| `icon` | string (Material) | `CHEST` | GUI icon material, uppercased (`CollectionDefinitionParser.java:13`). |
| `description` | string | `""` | Single-line lore text for the category icon (`CollectionDefinitionParser.java:14`). |

### Collection definition — any other `*.yml` under `collections/`

Recursively loaded, excluding files named `categories.yml` (`CollectionLoader.java:53-63`). Folder structure is
purely organizational.

```yaml
<collection-id>:
  category: <category-id>
  name: "<MiniMessage name>"
  icon: <MATERIAL>
  track:
    - "<EVENT_TYPE>:<IDENTIFIER>"
  stages:
    <int>:
      required: <long>
      rewards:
        - "<script event line>"
```

| Key | Type | Default | Description |
|---|---|---|---|
| `<collection-id>` | string (map key) | — | Collection ID. Lowercased at load (`CollectionLoader.java:71`). |
| `category` | string | `misc` | Category ID this collection belongs to. Not validated against loaded categories (`CollectionDefinitionParser.java:19`). |
| `name` | string (MiniMessage) | `id` | Display name in the collections GUI (`CollectionDefinitionParser.java:20`). |
| `icon` | string (Material) | `STONE` | GUI icon material, uppercased (`CollectionDefinitionParser.java:21`). |
| `track` | list of strings | `[]` | Track sources. Format `EVENT_TYPE:IDENTIFIER`. See below. (`CollectionDefinitionParser.java:23`) |
| `stages` | map | `{}` | Threshold tiers keyed by integer stage number. Non-integer keys are ignored (`CollectionDefinitionParser.java:26-42`). Stages sorted ascending after parse (`CollectionDefinitionParser.java:43`). |
| `stages.<n>.required` | long | `0` | **Cumulative** total count required for this stage (`CollectionDefinitionParser.java:38`). |
| `stages.<n>.rewards` | list of strings | `[]` | Script event lines executed when this stage is crossed (`CollectionDefinitionParser.java:39`). |

### Track source format

```
EVENT_TYPE:IDENTIFIER
```

Only the following event types are ever emitted by `CollectionListener`:

| Type | Emitted by | Identifier value | Example |
|---|---|---|---|
| `BLOCK_BREAK` | `onBlockBreak` (`CollectionListener.java:40-44`) | Uppercase `Material.name()` | `BLOCK_BREAK:COAL_ORE` |
| `MOB_KILL` | `onEntityDeath` (`CollectionListener.java:47-53`) | Uppercase `EntityType.name()`; requires a player killer | `MOB_KILL:ZOMBIE` |
| `FISHING` | `onFish` (`CollectionListener.java:56-67`) | Item material name, or `COD` fallback for non-item catches | `FISHING:COD` |
| `ITEM_PICKUP` | `onItemPickup` (`CollectionListener.java:70-85`) | Uppercase item material name | `ITEM_PICKUP:COAL` |
| `ITEM_PICKUP` (custom) | same handler, PDC branch (`CollectionListener.java:78-84`) | `custom:<lowercased valmora item id>` | `ITEM_PICKUP:custom:my_sword` |
| `CRAFT` | `onCraft` (`CollectionListener.java:88-93`) | Uppercase recipe result material name | `CRAFT:IRON_INGOT` |

Matching is exact and case-sensitive (`CollectionDefinition.matches`, `CollectionDefinition.java:24-26`), so YAML
entries must reproduce the emitted casing. Multiple track sources are cumulative — any matching source increments the
same counter. The same `EVENT_TYPE:IDENTIFIER` may appear in more than one collection, incrementing each.

### Shipped defaults

`src/main/resources/collections/` ships 16 files across 5 categories (defined in `categories.yml`):

| File | Collections |
|---|---|
| `categories.yml` | farming, mining, combat, fishing, foraging (5 categories) |
| `mining/coal.yml` | coal, iron_ingot |
| `mining/gems.yml` | diamond, emerald, lapis_lazuli, redstone, quartz, raw_gold, raw_copper, ancient_debris, amethyst_shard |
| `mining/blocks.yml` | cobblestone, obsidian, glowstone_dust, gravel, flint, sand, netherrack, end_stone, ice, clay_ball |
| `farming/wheat.yml` | wheat |
| `farming/crops.yml` | potato, pumpkin, melon_slice, red_mushroom, brown_mushroom, cocoa_beans, cactus, sugar_cane, nether_wart, beetroot |
| `farming/carrot.yml` | carrot |
| `farming/animals.yml` | feather, raw_chicken, leather, raw_beef, porkchop, mutton, wool, raw_rabbit, rabbit_hide, ink_sac, egg |
| `foraging/oak_log.yml` | oak_log |
| `foraging/logs.yml` | spruce_log, birch_log, jungle_log, acacia_log, dark_oak_log, mangrove_log, cherry_log, bamboo |
| `combat/zombie.yml` | zombie, skeleton |
| `combat/overworld.yml` | string, spider_eye, gunpowder, ender_pearl, slimeball, bone, rotten_flesh, arrow |
| `combat/ocean.yml` | prismarine_shard, prismarine_crystals, nautilus_shell, trident |
| `combat/nether.yml` | blaze_rod, blaze_powder, ghast_tear, magma_cream, wither_skeleton_skull |
| `fishing/fish.yml` | tropical_fish, pufferfish, lily_pad, ink_sac_fishing |
| `fishing/cod.yml` | cod, salmon |

---

## Data Model / Persistence

### Profile fields

- `ValmoraProfile` owns a `final CollectionManager collectionManager = new CollectionManager()`
  (`ValmoraProfile.java:21`) with getter `getCollectionManager()` (`ValmoraProfile.java:66-68`).
- The `CollectionManager` stores `Map<String, Long> counts` (lowercase keys) entirely **in memory** during the
  profile's session (`CollectionManager.java:7`). No per-stage granted-state is recorded.

### Database

- Column `collections TEXT` on `valmora_profiles` (`SQLDataStore.java:141`), added for pre-versioning databases via
  `addColumnIfMissing(conn, "valmora_profiles", "collections", "TEXT")` (`SQLDataStore.java:149`).
- **Save** (`SQLDataStore.java:287-301`): serialized as JSON via
  `gson.toJson(profile.getCollectionManager().getSaveData())` → `Map<String, Long>`.
- **Load** (`SQLDataStore.java:233-239`): `gson.fromJson(collectionsJson, collectionsType)` then
  `profile.getCollectionManager().loadData(collections)`. Wrapped in try/catch so a missing/malformed column does not
  abort profile loading.
- Saved on the async DB executor (`SQLDataStore.java:268`), as with all profile data; the module itself never touches
  the DB directly.
- On server shutdown, all sessions are saved via `dataStore.savePlayer(player).join()` (`Valmora.java:270-273`).

### Important semantics

- Only the **raw counter** is persisted. The current stage is always recomputed from the definition. If a collection's
  YAML stages change between restarts, already-crossed stages will fire **again** on the next matching event
  (because old/new stage are compared live, and rewards are not recorded anywhere).
- Counts are per-profile; profiles are independent (`CollectionManager` is instantiated per `ValmoraProfile`).

---

## API Exposed

The module is **not** exposed on the `ValmoraAPI` interface. Verified:

- `ValmoraAPI.java` has no `getCollectionModule()` declaration (interface covers players, items, mobs, stats, skills,
  economy, zones, npcs, warps, quests, points, notify, progression — `ValmoraAPI.java:19-70`).
- The concrete `Valmora` class does expose `public CollectionModule getCollectionModule()` (`Valmora.java:422`).
- Internal consumers therefore reach the module through the concrete class, e.g.
  `plugin.getCollectionModule()` in `CollectionVariableProvider.java:45`.

Access points available today:

| Path | Method |
|---|---|
| Registry | `plugin.getCollectionModule().getRegistry()` |
| Categories | `registry.getCategory(id)` / `registry.getCategories()` |
| Collections | `registry.getCollection(id)` / `registry.getCollections()` / `registry.getCollectionsInCategory(id)` |
| Per-profile counters | `profile.getCollectionManager()` → `getCount` / `getCurrentStage` / `addCount` / `loadData` / `getSaveData` |

The variable namespace `$collection.*$` is the primary *public* interface for GUIs.

---

## Dependencies & Consumers

### Dependencies (read at runtime)

| Dependency | How it is used |
|---|---|
| `PlayerManager` (`profile` module) | Session/profile lookup in `CollectionListener.getProfile` (`CollectionListener.java:33-37`) and `CollectionVariableProvider` (`CollectionVariableProvider.java:40-43`). Loads after it (register order 4 vs 24). |
| `ScriptModule` | `registerProvider(...)` for the variable namespace (`CollectionModule.java:22`) and `getEventParser().parseList(...)` for rewards (`CollectionListener.java:109-112`). |
| `GuiModule` | `CollectionCommand` opens `"collections_categories"` (`CollectionCommand.java:31`). Consumed only at command time. |
| `Keys.ITEM_ID_KEY` | Custom-item detection on pickup (`CollectionListener.java:80`, key defined in `Keys.java:43`). |
| `GuiExecutionContext` / `GuiSession` | `CollectionVariableProvider` reads session props (`CollectionVariableProvider.java:50-53`). |

### Registration position

`CollectionModule` is instantiated at `Valmora.java:175` and registered **24th** at `Valmora.java:213` — after
`notify` (23rd), before `hudItem` (25th). It requires `script` (1st), `player` (4th), and `gui` (12th) to be loaded
before it, all satisfied. (The `VALMORA_DOCUMENTATION.md` load-order list lists it as #23; the code places it #24.)

### Consumers

- **GUIs:** `collections_categories.yml`, `collections_list.yml`, `collections_detail.yml` consume `$collection.*$`.
- **`Valmora.java`:** owns the module field (`Valmora.java:114`) and command wiring (`Valmora.java:249`).
- **`ValmoraProfile`:** owns the per-profile `CollectionManager`.
- **`SQLDataStore`:** persists the counter map.
- No other *gameplay* module currently reads collection state (no achievements/quests/skills consume it yet).

---

## Unfinished Things / TODOs

There are no `TODO` comments in the module source, but several gaps exist relative to the documentation and the rest
of the codebase:

1. **Module ID mismatch:** `getId()` returns `"collections"` (`CollectionModule.java:38`) but `MODULE_DEVELOPMENT.md`
   and `VALMORA_DOCUMENTATION.md` §35 reference `"collection"`.
2. **Not on `ValmoraAPI`:** external plugins / other modules can only reach it via the concrete `Valmora` class.
3. **Reward strings vs script events:** shipped YAML `rewards` contain MiniMessage display strings (e.g.
   `coal.yml:13` `"<gray>Novice Miner title"`), not executable script events (`economy_add`, `give`, …). They are fed
   through the script parser and effectively no-op. The docs' example (VALMORA_DOCUMENTATION.md §35.5) shows real
   events, but no shipped file uses them.
4. **No reward idempotency:** crossed stages are not recorded. Config changes can re-fire rewards; there is no "already
   rewarded" tracking and no manual claim step.
5. **Docs/code drift on layout:** docs describe per-folder `category.yml` (§35.1-35.2); the loader only reads a single
   `collections/categories.yml` (`CollectionLoader.java:26`).
6. **Docs/code drift on `detail_next_required`:** docs say `-1` when maxed; code returns the current count
   (`CollectionVariableProvider.java:204`).
7. **Docs/code drift on track types:** docs §35.4 only list `BLOCK_BREAK` and `ITEM_PICKUP`; code also handles
   `MOB_KILL`, `FISHING`, `CRAFT`. Custom items must use the `ITEM_PICKUP:custom:<id>` prefix (`CollectionListener.java:82`);
   a bare `ITEM_PICKUP:<item_id>` (as §35.4 implies) will not match custom items.
8. **FISHING misattribution:** non-`Item` catches (fish entities) are hardcoded to `"COD"` (`CollectionListener.java:62`).
9. **No admin tooling:** no command to view/reset/force a player's collection counts; no permission nodes defined.
10. **Unvalidated `category`:** collections referencing a missing category default to `"misc"` silently
    (`CollectionDefinitionParser.java:19`); no load-time warning.
11. **Performance:** `trackEvent` iterates all collections on every event (`CollectionListener.java:97`); with many
    collections this is O(n) per event. No index by `EVENT_TYPE:IDENTIFIER`.
12. **No `YamlLoader` usage:** loader hand-rolls `YamlConfiguration` (`CollectionLoader.java:40`, `66`), deviating
    from the project-wide loader pattern (AGENTS.md §7.1).

---

## Possible Improvements / Changes

1. **Expose on `ValmoraAPI`:** add `CollectionModule getCollectionModule()` to the interface (mirroring
   `Valmora.java:422`) so sibling modules/plugins can consume it without the concrete class.
2. **Index track sources:** build a `Map<String /*EVENT_TYPE:ID*/, List<CollectionDefinition>>` at load time so
   `trackEvent` is O(matched) instead of O(all collections).
3. **Reward grant ledger:** persist per-stage grant state (e.g. extend the JSON blob to `{counts, grantedStages}` or a
   second column) so rewards fire exactly once even across config edits/reloads.
4. **Admin/player commands:** `/collections` subcommands (`view <player> <collection>`, `reset <player> <collection>`,
   `set <player> <collection> <count>`) gated by `valmora.admin`.
5. **Fix `FISHING`:** use `EntityType` for non-item caught entities instead of the `"COD"` fallback.
6. **Load-time validation:** warn when `category` does not reference a registered category; optionally fail the file.
7. **Case-insensitive matching:** lowercase both sides in `CollectionDefinition.matches` so YAML casing mistakes are
   tolerated.
8. **Align docs and code:** reconcile the module ID, category-file layout, track-source list, and
   `detail_next_required` semantics in `VALMORA_DOCUMENTATION.md`.
9. **Use `YamlLoader`:** port `CollectionLoader` to the shared loader for consistency and duplicate-key handling.
10. **Per-category GUI navigation props:** consider persisting `selected_category`/`selected_collection` in the
    `GuiSession` when opening, so the detail view can show a back button without re-passing props.
11. **Sample rewards:** replace shipped MiniMessage reward strings with real script events (or wire them to a Notify
    event) so stage completion produces visible gameplay feedback.
