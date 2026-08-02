# Fishing Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `fishing` | **Source:** `src/main/java/org/nakii/valmora/module/fishing/`

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

The Fishing module replaces vanilla fishing loot with **zone-scoped weighted loot tables**. When a player finishes a fishing cast (`PlayerFishEvent` in `CAUGHT_FISH` state), the module:

1. Removes the naturally spawned caught entity and cancels the event (`FishingListener.java:18-19`).
2. Resolves which loot table applies to the player's current location via the **Zone module** (`FishingManager.java:40-45`).
3. Rolls a sea-creature spawn, or rolls a weighted loot entry and places the item in the player's inventory (`FishingManager.java:22-38`).

It is a deliberately small module — a `ReloadableModule` wrapping a `FishingManager` (loot-table registry + catch resolution) plus a single event listener. There is **no persistence**, **no commands**, and **no public `ValmoraAPI` surface** (only a concrete-class getter on `Valmora`).

The module depends on the **Items** module (custom item resolution), the **Mobs** module (sea-creature spawning), and the **Zones** module (which zone's loot table applies). Skill XP for fishing is granted independently by the **Skill** module, not by this module (see [Dependencies & Consumers](#dependencies--consumers)).

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/fishing/
├── FishingModule.java        # ReloadableModule — lifecycle (enable/disable/getId)
├── FishingManager.java       # Catch resolution, table lookup, item creation, registry
├── FishingLootTable.java     # Immutable loot table + weighted roll() logic
├── FishingLootEntry.java     # Immutable single loot entry (item + weight + amount range)
├── FishingLoader.java        # YAML loader/parser via YamlLoader<FishingLootTable>
└── FishingListener.java      # PlayerFishEvent handler — intercepts CAUGHT_FISH

src/main/resources/fishing/
└── hub_fishing.yml           # Default shipped loot table
```

Test coverage:

```
src/test/java/org/nakii/valmora/module/fishing/FishingLootTableTest.java
```

`src/test/java/org/nakii/valmora/config/YamlConfigLoadTest.java:43` also validates that `/fishing/hub_fishing.yml` parses without exceptions.

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `FishingModule.java`

Implements `ReloadableModule` (see `docs/MODULE_DEVELOPMENT.md` §2).

| Method | Behavior | Lines |
|---|---|---|
| `onEnable()` | Logs, constructs a new `FishingManager`, runs `FishingLoader.load()` against the manager's registry, constructs `FishingListener` and registers it with the plugin manager | `FishingModule.java:18-24` |
| `onDisable()` | Unregisters the listener via `HandlerList.unregisterAll`, nulls it; clears the registry and nulls the manager | `FishingModule.java:27-31` |
| `getId()` | `"fishing"` | `FishingModule.java:33` |
| `getName()` | `"Fishing System"` | `FishingModule.java:34` |
| `getFishingManager()` | Returns the live `FishingManager` (may be null between disable/enable) | `FishingModule.java:36` |

Following the project's hot-reload contract (`AGENTS.md` §6, §10), all mutable state is created inside `onEnable()` and torn down in `onDisable()` — the constructor only stores the plugin reference (`FishingModule.java:13-15`).

### 3.2 Catch Flow — `FishingManager.handleCatch(Player)` (`FishingManager.java:22-38`)

```
handleCatch(player)
  │
  ├─ table = getTableForPlayer(player)
  │    └─ if table == null → return false          (FishingManager.java:24)
  │
  ├─ sea-creature check:
  │    if table.getSeaCreatureMobId() != null
  │       AND Math.random() < table.getSeaCreatureChance()
  │         → mobDef = mobManager.getMobDefinition(mobId)
  │         → if mobDef != null: mobManager.spawnMob(mobDef, player.getLocation())
  │         → return true                            (FishingManager.java:26-30)
  │
  ├─ loot roll:
  │    entry = table.roll()
  │    if entry == null → return false               (FishingManager.java:32-33)
  │
  └─ grant item:
       item = createItem(entry.getItemId(), entry.rollAmount())
       if item != null: player.getInventory().addItem(item)
       return true                                   (FishingManager.java:35-37)
```

Important behavioral details:

- **Sea creatures spawn at the player, not the bobber** — `spawnMob(def, player.getLocation())` (`FishingManager.java:28`). If the configured mob ID does not exist in the mob registry, the catch is still consumed (`return true`) but nothing spawns and no loot is granted.
- **Item resolution is best-effort** (`createItem`, `FishingManager.java:47-55`):
  1. Try the custom item registry first: `plugin.getItemManager().getItemRegistry().createItemStack(itemId.toLowerCase())` — wrapped in `try/catch` (any exception is ignored).
  2. If present, `stack.setAmount(amount)` is applied and returned.
  3. Otherwise try a vanilla material: `Material.matchMaterial(itemId.toUpperCase())`.
  4. If both fail → returns `null`, and `handleCatch` still returns `true` (the catch is consumed with no reward).

### 3.3 Loot Table Resolution — `getTableForPlayer(Player)` (`FishingManager.java:40-45`)

```java
ZoneDefinition zone = plugin.getZoneManager().getZoneAt(player.getLocation()).orElse(null);
String tableId = zone != null ? zone.getFishingLootTable() : null;
if (tableId == null) tableId = "default";
return registry.get(tableId).orElse(registry.get("default").orElse(null));
```

Resolution rules:

1. Look up the zone containing the player's location. `ZoneManager.getZoneAt()` returns the **smallest-volume** zone containing the location (`ZoneManager.java:68-72`) — nested zones resolve to the innermost one.
2. Use that zone's `fishing-loot-table` value (parsed in `ZoneLoader.java:34`), or `"default"` if the player is outside any zone / the zone defines no table.
3. Look the ID up in the fishing registry; **fall back to a table literally named `default`** if the requested ID is absent; otherwise `null`.

Because registries are case-insensitive (`SimpleRegistry` lowercases keys — `SimpleRegistry.java:20-22`), table IDs can be configured in any case but are stored lowercased.

### 3.4 Registry — `FishingManager.registry`

`private final Registry<FishingLootTable> registry = new SimpleRegistry<>();` (`FishingManager.java:14`).

- Exposed via `getRegistry()` (`FishingManager.java:20`).
- Populated in `onEnable()` by `FishingLoader.load()`, cleared in `onDisable()` (`FishingModule.java:21,30`).
- `SimpleRegistry` is thread-safe (`synchronized` on mutation) and case-insensitive — see `SimpleRegistry.java:15-58` and the `Registry<T>` contract in `src/main/java/org/nakii/valmora/api/registry/Registry.java`.

### 3.5 Weighted Roll — `FishingLootTable.roll()` (`FishingLootTable.java:22-33`)

- Empty entry list → returns `null`.
- Sums all entry weights; `totalWeight <= 0` → returns `null`.
- Draws `roll = (int)(Math.random() * totalWeight)` and walks entries cumulatively; the first entry whose cumulative weight exceeds the roll wins.
- Defensive fallback: if the loop completes without a match, the **last** entry is returned.

Rarity is expressed purely through relative `weight` (higher = more common); the total does not need to sum to 100.

### 3.6 Amount Roll — `FishingLootEntry.rollAmount()` (`FishingLootEntry.java:18-21`)

- If `minAmount >= maxAmount` → returns `minAmount` (fixed amount).
- Otherwise returns a uniform integer in `[min, max]` inclusive: `min + (int)(Math.random() * (max - min + 1))`.

### 3.7 Event Handling — `FishingListener.onFish(PlayerFishEvent)` (`FishingListener.java:15-21`)

```java
@EventHandler
public void onFish(PlayerFishEvent event) {
    if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
    if (event.getCaught() != null) event.getCaught().remove();
    event.setCancelled(true);
    fishingManager.handleCatch(event.getPlayer());
}
```

- Only reacts to the `CAUGHT_FISH` state; every other state is ignored.
- The vanilla caught entity is **removed** and the event **cancelled**, so the player never picks up vanilla loot — the module's loot table output is the only reward.
- **Interaction with the Skill module (important):** the Skill module also listens to `PlayerFishEvent` in `SkillListener.onFish` (`SkillListener.java:70-88`). Because Bukkit delivers cancelled events to listeners that do not declare `ignoreCancelled = true`, both listeners run. The Skill listener reads the *vanilla caught item's material* from `event.getCaught().getItemStack().getType()` for XP lookup — not the loot-table-rolled item. The two systems are fully decoupled (see [Dependencies & Consumers](#dependencies--consumers)).
- **`handleCatch` returning `false` is silent:** the event has already been cancelled and the caught entity removed, so an unresolvable table or empty roll leaves the player with nothing and no notification.

### 3.8 Config Loading — `FishingLoader.java`

- `load()` (`FishingLoader.java:23-27`): clears the registry, then `new YamlLoader<FishingLootTable>(plugin, "fishing", "Fishing Tables").load(this::parse, table -> registry.register(table.getId(), table))`.
- Uses `YamlLoader.load(...)` (per-file, one section per top-level key) — `YamlLoader.java:37-73`. Folder `plugins/Valmora/fishing/` is auto-created if missing, and each `*.yml` file contributes one table per top-level key.
- Failures are reported through `LoadResult.failure(...)` and surfaced as warnings by `YamlLoader.reportErrors(...)` (`YamlLoader.java:113-123`). A malformed table does not abort the rest of the load.
- `parse()` default values (see [Configuration (YAML)](#configuration-yaml)).

### 3.9 Startup Wiring — `Valmora.java`

- Field declaration: `Valmora.java:108`
- Instantiation: `new FishingModule(this)` — `Valmora.java:169`
- Registration: `moduleManager.registerModule(fishingModule)` — `Valmora.java:207` (after `resourceModule`, before `npcModule`)
- Enablement runs in registration order via `moduleManager.enableModules()` — `Valmora.java:225`
- Concrete getter: `getFishingModule()` — `Valmora.java:395-397`
- Default resource shipping: `saveAllResources()` copies anything under `fishing/` out of the JAR on first run — `Valmora.java:472`

---

## Configuration (YAML)

Config lives in `plugins/Valmora/fishing/*.yml` (source defaults in `src/main/resources/fishing/`). Each top-level key defines one **loot table**. One shipped example exists: `src/main/resources/fishing/hub_fishing.yml`.

### Table-level options

| Key | Type | Default | Explanation |
|---|---|---|---|
| `<table-id>` | section | — | Unique table ID. The YAML key becomes the table ID (used by zone `fishing-loot-table` and the `"default"` fallback). Case-insensitive in the registry. |
| `sea-creature-chance` | double | `0.0` | Probability (0.0–1.0) that a successful catch spawns a sea creature instead of loot. Checked with `Math.random() < chance` (`FishingLoader.java:31`). |
| `sea-creature-mob` | string | `null` | Valmora **mob definition ID** to spawn as the sea creature (`FishingLoader.java:32`). Must exist in `mobs/*.yml`; if it does not, the catch is silently consumed with no spawn. |
| `entries` | list of maps | `[]` | The loot table. Each list item is one `FishingLootEntry`. |

### Entry-level options (`entries[].*`)

| Key | Type | Default | Explanation |
|---|---|---|---|
| `item` | string | `"COD"` | Item identifier. Resolved first as a Valmora **custom item ID** (`items/*.yml`), then as a vanilla **Material name** (`FishingManager.java:47-55`). |
| `weight` | int | `10` | Relative selection weight. Higher = more common. Total does not need to equal 100 (`FishingLoader.java:38`). |
| `min` | int | `1` | Minimum stack amount granted (`FishingLoader.java:39`). |
| `max` | int | `1` | Maximum stack amount granted. Amount rolled uniformly in `[min, max]`; if `min >= max` the amount is fixed at `min` (`FishingLoader.java:40`, `FishingLootEntry.java:18-21`). |

### Default resource — `hub_fishing.yml` (shipped, verbatim)

```yaml
hub_fishing:
  sea-creature-chance: 0.05
  sea-creature-mob: squid
  entries:
    - item: COD
      weight: 40
      min: 1
      max: 2
    - item: SALMON
      weight: 25
      min: 1
      max: 1
    - item: TROPICAL_FISH
      weight: 15
      min: 1
      max: 1
    - item: PUFFERFISH
      weight: 10
      min: 1
      max: 1
    - item: NAUTILUS_SHELL
      weight: 5
      min: 1
      max: 1
    - item: HEART_OF_THE_SEA
      weight: 1
      min: 1
      max: 1
```

Relative probabilities for this table: COD ≈ 41.7%, SALMON ≈ 26.0%, TROPICAL_FISH ≈ 15.6%, PUFFERFISH ≈ 10.4%, NAUTILUS_SHELL ≈ 5.2%, HEART_OF_THE_SEA ≈ 1.0%.

> **Note:** the shipped table references `sea-creature-mob: squid`, but no `squid` mob definition exists in the shipped `mobs/*.yml` files (verified via grep over `src/main/resources/mobs/`). With the default config, sea creatures never actually spawn.

### Zone linkage — `zones/*.yml`

A zone opts into a loot table with the optional key `fishing-loot-table: <table-id>` (parsed at `ZoneLoader.java:34`, stored on `ZoneDefinition.getFishingLootTable()` — `ZoneDefinition.java:89`). Players outside any zone, or in a zone without this key, fall back to the table literally named `"default"` (`FishingManager.java:43-44`).

---

## Data Model / Persistence

**No persistence layer.** The fishing module keeps all state in memory:

- `FishingLootTable` — immutable, holds `id`, `List<FishingLootEntry> entries`, `double seaCreatureChance`, `String seaCreatureMobId` (`FishingLootTable.java:6-9`).
- `FishingLootEntry` — immutable, holds `itemId`, `weight`, `minAmount`, `maxAmount` (`FishingLootEntry.java:4-7`).

There is no DAO, no database table, no SQLite/MySQL usage, and nothing is written back to disk at runtime. On hot reload (`/valmora reload`) or plugin disable, all tables are dropped from memory (`FishingModule.java:30`) and re-parsed from `fishing/*.yml` on the next enable. Custom loot tables therefore have **no server-side per-player state** — the module is entirely stateless between players.

---

## API Exposed

### Public surface (concrete class only)

- `Valmora.getFishingModule()` → `FishingModule` — `Valmora.java:395-397`.
- `FishingModule.getFishingManager()` → `FishingManager` — `FishingModule.java:36`.
- `FishingManager.getRegistry()` → `Registry<FishingLootTable>` — `FishingManager.java:20`.
- `FishingManager.handleCatch(Player)` → `boolean` — `FishingManager.java:22` (main entry point).

### Not in `ValmoraAPI`

`getFishingModule()` is **not** declared on the `ValmoraAPI` interface (`src/main/java/org/nakii/valmora/api/ValmoraAPI.java`). External consumers holding only a `ValmoraAPI` reference cannot reach the fishing registry; they would need a cast to the concrete `Valmora` class. The `MODULE_DEVELOPMENT.md` §8 "expose via API" step has **not** been done for this module.

---

## Dependencies & Consumers

### Dependencies (used at runtime)

| Dependency | Used for | Reference |
|---|---|---|
| **Items module** (`ItemManager`) | Custom-item resolution in loot rolls | `FishingManager.java:49` → `plugin.getItemManager().getItemRegistry().createItemStack(...)` |
| **Mobs module** (`MobManager`) | Sea-creature spawning | `FishingManager.java:27-28` → `getMobDefinition(...)` / `spawnMob(def, player.getLocation())` |
| **Zones module** (`ZoneManager`) | Determining which loot table applies | `FishingManager.java:41` → `plugin.getZoneManager().getZoneAt(player.getLocation())` |
| **Skill module** (`SkillModule`) | Fishing XP — *granted elsewhere, not by this module* | `SkillListener.java:70-88`, `skills/fishing.yml` |

### Skill XP interplay (cross-module contract)

Fishing XP is awarded by the **Skill** module, not the Fishing module. `SkillListener.onFish` (`SkillListener.java:70-88`):

1. Fires on `PlayerFishEvent` `CAUGHT_FISH` (no priority override → default `NORMAL`).
2. Reads the vanilla caught entity's material: `item.getItemStack().getType().name()` (`SkillListener.java:78-80`).
3. Looks up XP per skill via `skill.getSourceXp("FISHING", caughtId)` (`SkillListener.java:83`) against `skills/fishing.yml` sources (`SkillDefinition.java:90-119`, `SkillDefinition.java:122`).
4. Awards via `profile.getSkillManager().addXp(...)`.

Consequences:

- XP is keyed on the **vanilla catch type** (e.g. `COD`, `SALMON`), not on the loot-table-rolled item. Catching a custom Valmora item from a loot table gives XP for whatever vanilla fish the vanilla roll produced, or none.
- The `TREASURE: 1000.0` XP source in `skills/fishing.yml:15` can never match — XP lookups use a material name, and no material is named `TREASURE`.
- Listener ordering is not enforced between `SkillListener.onFish` and `FishingListener.onFish`. `FishingListener` removes the caught entity (`FishingListener.java:18`), but the `Item` entity object retains its `ItemStack`, so XP lookup still succeeds regardless of order.

### Consumers

**None.** A grep across `src/` finds `getFishingModule()` referenced only in `Valmora.java` (`Valmora.java:395-397`) and `getFishingManager()` only in `FishingModule.java:36`. No other module, quest objective, command, or script calls into the fishing registry. (The quest objective / objective-doc mentions of "fish" — `docs/Objective_list.md:243-265`, `docs/QUEST_SYSTEM.md:367` — refer to a separately documented `FISH` objective; verify whether it reads `PlayerFishEvent` directly rather than this module.)

---

## Unfinished Things / TODOs

| Item | Source | Notes |
|---|---|---|
| "Proper fishing" (sea creatures, hot spots, treasures, drops, rod parts) is listed as unfinished | `docs/todo.md:6` | The current module implements only zone-scoped weighted loot + a single sea-creature spawn roll. No hot spots, bite indicator, treasures pool, or rod parts exist in code. |
| Fishing bag / bait | `docs/todo.md:71` | Not implemented anywhere. |
| Sea-creature mob missing for default config | `src/main/resources/fishing/hub_fishing.yml:3` vs `src/main/resources/mobs/` | `sea-creature-mob: squid` has no matching mob definition; default config silently never spawns sea creatures. |
| `ValmoraAPI` exposure missing | `src/main/java/org/nakii/valmora/api/ValmoraAPI.java` | `getFishingModule()` exists only on the concrete `Valmora` class. |
| `TESTING_GUIDE.md` expectations partially unmet | `docs/TESTING_GUIDE.md:170-177` | TC-FISH expects bite indicator particles/sound (FISH-01) and sea-creature spawning near the **bobber** (FISH-03) — no particle/sound or bobber targeting exists in code. |

---

## Possible Improvements / Changes

- **Expose via `ValmoraAPI`** — add `getFishingModule()` (or `FishingManager`) to `ValmoraAPI` following `MODULE_DEVELOPMENT.md` §8, so other modules/plugins can access tables without casting to `Valmora`.
- **Spawn sea creatures at the bobber** — `handleCatch` currently spawns at `player.getLocation()` (`FishingManager.java:28`); the event provides `getHook()` (`PlayerFishEvent.getHook()`) for a natural spawn point.
- **Graceful failure instead of silent consumption** — when `createItem` returns `null` or a sea-creature mob ID is unresolvable, log a warning (and optionally notify the player) rather than consuming the catch (`FishingManager.java:26-37`).
- **Decouple skill XP from vanilla catch** — award `FISHING` XP based on the rolled loot entry's item (or a per-table XP value) so custom loot and treasures grant meaningful XP. The current XP path (`SkillListener.java:83`) cannot see the rolled item.
- **Make the `default` fallback explicit** — `FishingManager.java:43-44` silently falls back to a table named `default`; consider a config flag to toggle or log the fallback.
- **Full fishing feature set** — per `docs/todo.md:6`: hot spots, bite indicators, treasure pools, rod parts/upgrades, and lava/enchant-type fishing. These are roadmap items, not current behavior.
- **Fix the shipped default** — add a real `squid` (or similar) mob definition or remove `sea-creature-mob` from `hub_fishing.yml` so the default config behaves as documented.
- **More tests** — existing coverage (`FishingLootTableTest.java`) targets the roll math; `handleCatch`/`getTableForPlayer`/`createItem` have no unit tests (would require mocking `ValmoraAPI` per `AGENTS.md` §9).
