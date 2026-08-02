# Zone Module — Design & Code

> **Module ID:** `zone` | **Display name:** "Zone System" | **Package:** `org.nakii.valmora.module.zone`
> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21

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

The Zone module defines **named world regions** and turns them into gameplay containers. A zone is an axis-aligned bounding box (plus optional extra sub-boxes) attached to one world. Everything else in Valmora that is *location-scoped* reads zones:

- **Membership & notifications** — which zone a player is in, enter/exit detection, an action-bar name on entry, and scripted `enter-actions` / `exit-actions` (`ZoneListener.java:74-93`).
- **Region flags** — PvP, natural mob spawning, block breaking/placing, hunger, entry, teleportation, leaf decay (`ZoneFlags.java:3-12`), enforced by `ZoneListener` and consumers.
- **Resource nodes** — each zone can map block materials to `ZoneResourceConfig` (regen delay, required Breaking Power, staged drops). The **Resource module** consumes these at break time; the Zone module only defines/parses them (`ZoneLoader.java:101-152`).
- **Mob spawners** — per-zone periodic mob spawners with per-spawner interval, alive cap, and counting radius, plus a "mob home" behavior task that returns tagged mobs to their spawner anchor (`ZoneManager.java:123-251`).
- **Fishing linkage** — an optional `fishing-loot-table` string that the **Fishing module** uses to pick a loot table (`ZoneDefinition.java:89`, `FishingManager.java:41-45`).
- **Script integration** — a `zone` variable namespace (`ZoneVariableProvider.java`), a `zone` condition read by the script engine (`ZoneCondition.java`), and teleport gating (`TeleportEventFactory.java:44-48`).
- **Quest integration** — `ZoneEnterEvent` feeds the `REACH_ZONE` quest objective (`QuestListener.java:108-112`).
- **Scoreboard** — a "Zone:" line showing the current zone's display name or `<green>Wilderness` (`ScoreboardUI.java:201-206`).

The module is self-contained as a `ReloadableModule`, registered 16th in the load order — after `enchant`, before `resource` (`Valmora.java:205`; order documented in `MODULE_DEVELOPMENT.md:511-517`). It declares no enable-time dependency, but at runtime it reaches into the mob module (`getMobManager()`, spawner ticks), the item module (mob-ID validation), the UI module (action-bar), and the script module (enter/exit actions).

Zones are stored as YAML files in `plugins/Valmora/zones/`, loaded by `YamlLoader` on enable/reload, and **rewritten to disk by admin commands** (`ZoneManager.saveZoneToFile`, `ZoneManager.java:341-392`). There is no database involvement.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/zone/
├── ZoneModule.java            # ReloadableModule lifecycle (58 lines)
├── ZoneManager.java           # Core engine: lookup, membership, spawners, CRUD, particles (515)
├── ZoneDefinition.java        # Immutable zone model — box + extras + all payloads (104)
├── ZoneLoader.java            # YAML parsing via YamlLoader (171)
├── ZoneCommand.java           # /zone command + tab completion (390)
├── ZoneFlags.java             # Immutable region-flag record (16)
├── ZoneRegistry.java          # SimpleRegistry<ZoneDefinition> subclass (6)
├── ZoneListener.java          # Bukkit event enforcement + enter/exit handling (148)
├── ZoneWandListener.java      # PlayerInteractEvent handling of the zone wand (51)
├── ZoneVariableProvider.java  # Script variable namespace "zone" (31)
├── ZoneMobSpawner.java        # Immutable spawner model (46)
├── ZoneResourceConfig.java    # Per-material resource config: regen, power, stages (25)
├── ZoneResourceDrop.java      # Single drop entry: item id, min/max, chance (22)
├── ResourceStage.java         # One break stage: drops + next material (22)
└── event/
    ├── ZoneEnterEvent.java    # Custom event fired on zone entry (23)
    └── ZoneExitEvent.java     # Custom event fired on zone exit (23)

src/main/resources/zones/
├── test_zones.yml             # Demo: mine (ores), forest, mob_spawn, test_site
└── shardworks.yml             # Demo: Shardworks mining zone (custom drops + power gate)

src/test/java/org/nakii/valmora/module/zone/
└── ZoneResourceConfigTest.java # Verifies 2-arg ctor defaults requiredPower to 0
```

Wiring outside the package:

- **`Valmora.java:167`** — `this.zoneModule = new ZoneModule(this);` (field at `Valmora.java:106`).
- **`Valmora.java:205`** — `moduleManager.registerModule(zoneModule);` (16th).
- **`Valmora.java:246-248`** — `/zone` executor + tab completer registered *after* all modules enable (per AGENTS.md §6.3, never inside the module).
- **`Valmora.java:382-385`** — `getZoneManager()`; **`Valmora.java:387-389`** — `getZoneModule()` (concrete-class accessor).
- **`ValmoraAPI.java:53`** — `ZoneManager getZoneManager();` on the public interface.
- **`plugin.yml:46-49`** — command declaration, `permission: valmora.admin`.
- **`Valmora.java:472`** — `saveAllResources()` ships `zones/` from the JAR on first run (only if the target file does not already exist, `Valmora.java:481`).

---

## Architecture & Key Classes

### 3.1 Module lifecycle — `ZoneModule.java`

Implements `ReloadableModule` (`ZoneModule.java:7`). The constructor only stores the plugin and builds the registry + loader (`ZoneModule.java:16-20`); everything else is created in `onEnable()` per AGENTS.md §6.1.

| Method | Behavior | Lines |
|---|---|---|
| `onEnable()` | Logs; `loader.loadZones()`; creates `ZoneManager`, `ZoneListener`, `ZoneWandListener`; registers both listeners; registers the `ZoneVariableProvider` with the script module; starts the four scheduler tasks (spawner, mob-home, visualization, selection) | `ZoneModule.java:22-36` |
| `onDisable()` | Stops all four tasks; `HandlerList.unregisterAll` both listeners; `registry.clear()`; nulls the manager — idempotent and hot-reload safe | `ZoneModule.java:38-51` |
| `getId()` | `"zone"` | `ZoneModule.java:53` |
| `getName()` | `"Zone System"` | `ZoneModule.java:54` |
| `getZoneManager()` | Returns the live `ZoneManager` (null between disable/enable) | `ZoneModule.java:56` |
| `getZoneRegistry()` | Returns the `ZoneRegistry` | `ZoneModule.java:57` |

### 3.2 The zone model — `ZoneDefinition.java`

Immutable value object (`ZoneDefinition.java:10`). Every field is final and the "with" methods return new instances:

| Field | Type | Purpose | Lines |
|---|---|---|---|
| `id` | `String` | Unique lowercase ID, also the registry key and the YAML file name | `:11` |
| `displayName` | `String` | MiniMessage text shown in the action bar on entry and on the scoreboard | `:12` |
| `worldName` | `String` | Bukkit world the zone lives in | `:13` |
| `minX/minY/minZ/maxX/maxY/maxZ` | `int` | Primary inclusive bounding box | `:14` |
| `extraBoxes` | `List<int[]>` | Additional sub-boxes, each `{minX,minY,minZ,maxX,maxY,maxZ}` | `:15-16` |
| `flags` | `ZoneFlags` | Region flags record | `:17` |
| `fishingLootTable` | `String` | Fishing table ID consumed by the Fishing module | `:18` |
| `mobSpawners` | `List<ZoneMobSpawner>` | Periodic mob spawners | `:19` |
| `resourceBlocks` | `Map<Material, ZoneResourceConfig>` | Resource-node definitions per material | `:20` |
| `enterActions` | `List<String>` | Script DSL lines run on entry | `:21` |
| `exitActions` | `List<String>` | Script DSL lines run on exit | `:22` |

Key behavior:

- **`contains(Location)`** (`ZoneDefinition.java:55-63`) — world-name check first, then primary box membership (`x >= minX && x <= maxX ...`), then each extra box. Bounds are **inclusive**.
- **`volume()`** (`ZoneDefinition.java:65-67`) — `(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1)` as `long`; used as the smallest-volume tie-breaker in zone lookup.
- **`getAllBoxes()`** (`ZoneDefinition.java:70-75`) — primary box first, then extras; used for particle visualization.
- `isPvpEnabled()` is a convenience alias for `flags.pvp()` (`ZoneDefinition.java:88`).
- `withFlags(ZoneFlags)` and `withSpawners(List)` (`ZoneDefinition.java:95-103`) are used by the CRUD methods to produce updated registry entries.

### 3.3 Registry — `ZoneRegistry.java`

`extends SimpleRegistry<ZoneDefinition>` (`ZoneRegistry.java:5`). Keys are stored lowercase (case-insensitive lookups per the project's `Registry` contract). Populated by `ZoneLoader.loadZones()` in `onEnable()`, cleared in `onDisable()` (`ZoneModule.java:49`).

### 3.4 Zone lookup — `ZoneManager.getZoneAt()`

```java
public Optional<ZoneDefinition> getZoneAt(Location loc) {
    return registry.values().stream()
            .filter(z -> z.contains(loc))
            .min(Comparator.comparingLong(ZoneDefinition::volume));
}
```
(`ZoneManager.java:68-72`)

Returns the **smallest-volume** zone containing the location — nested zones resolve to the innermost one (documented cross-module in `docs/modules/design/fishing.md:122`). Ties are broken by registry insertion order. Because it streams the whole registry on every call, it is the single hottest method in the module; every flag check, membership check, resource lookup, and mob count funnels through it.

### 3.5 Player membership — `playerZones` + transition checks

- `Map<UUID, String> playerZones` (`ZoneManager.java:42`) is the in-memory "current zone ID" cache. `getCurrentZone(Player)` resolves the ID back to a `ZoneDefinition` (`ZoneManager.java:74-78`).
- **Join:** `onPlayerJoin` seeds the cache from the spawn location without firing enter events (`ZoneManager.java:84-86`, wired at `ZoneListener.java:68-69`).
- **Quit:** `onPlayerQuit` removes the UUID from both `playerZones` and `visualizingPlayers` (`ZoneManager.java:88-91`, wired at `ZoneListener.java:71-72`).
- **Transition:** `checkTransition(Player)` (`ZoneManager.java:93-110`) compares the cached zone ID to `getZoneAt(player.getLocation())`; on a change it fires `ZoneExitEvent` for the old zone and `ZoneEnterEvent` for the new zone via `callEvent`. Called from:
  - `PlayerMoveEvent` at `MONITOR` when the block coordinate changes (`ZoneListener.java:55-61`),
  - `PlayerTeleportEvent` at `MONITOR`, deferred one tick via `runTask` (`ZoneListener.java:63-66`),
  - player join (seeding only).

### 3.6 Custom events — `event/ZoneEnterEvent.java`, `event/ZoneExitEvent.java`

Plain `Event` subclasses (`ZoneEnterEvent.java:8-23`, `ZoneExitEvent.java:8-23`) carrying `Player` + `ZoneDefinition`, each with a static `HandlerList` and `getHandlerList()`. They are:

- Listened to by `ZoneListener` itself for the action bar + enter/exit scripts (`ZoneListener.java:74-93`).
- Listened to by the **Quest module** for `REACH_ZONE` objectives (`QuestListener.java:108-112`).
- Emitted from `ZoneManager.checkTransition` (`ZoneManager.java:100-106`).

Neither event exposes cancellation (enter/exit cannot be vetoed), and they are *not* fired for a player who joins already inside a zone.

### 3.7 Region flags — `ZoneFlags.java`

Immutable record (`ZoneFlags.java:3-12`) of eight booleans:

| Field | Semantics |
|---|---|
| `pvp` | `true` = player-vs-player damage allowed |
| `naturalMobSpawning` | `true` = `NATURAL`/`SLIME_SPLIT`/`SPAWNER` creature spawns allowed |
| `blockBreaking` | `true` = players may break blocks |
| `blockPlacing` | `true` = players may place blocks |
| `hunger` | `true` = hunger depletes normally; `false` = cancel `FoodLevelChangeEvent` |
| `entry` | `true` = open to all; `false` = players are pushed back on entry |
| `teleportation` | `true` = teleport works; `false` = blocked (see caveat below) |
| `leafDecay` | `true` = leaves decay normally; `false` = cancel `LeavesDecayEvent` |

`defaults()` (`ZoneFlags.java:13-15`) = `(false, false, false, false, true, true, true, true)` — i.e. everything *disallowed* except hunger, entry, teleportation, and leaf decay. Note this is **not** "everything allowed": a freshly created zone has PvP, natural spawning, breaking, and placing all **off** (`ZoneManager.createZone` calls `ZoneFlags.defaults()`, `ZoneManager.java:289`).

**Enforcement matrix** (`ZoneListener.java`):

| Flag | Handler | Behavior | Lines |
|---|---|---|---|
| `entry` | `onMoveEntryCheck` | If the destination is a different zone with `entry() == false`, `event.setTo(from.clone())` pushes the player back | `ZoneListener.java:42-53` |
| `pvp` | `onPvp` | Cancels `EntityDamageByEntityEvent` when both entities are players and the victim's zone has `pvp() == false` | `ZoneListener.java:95-102` |
| `blockBreaking` | `onBlockBreak` | Cancels the break unless it is a configured resource block or a tracked intermediate stage | `ZoneListener.java:104-115` |
| `blockPlacing` | `onBlockPlace` | Cancels placement | `ZoneListener.java:117-123` |
| `naturalMobSpawning` | `onCreatureSpawn` | Cancels `NATURAL`, `SLIME_SPLIT`, `SPAWNER` spawns (`BLOCKED_SPAWN_REASONS`, `ZoneListener.java:30-32`) | `ZoneListener.java:125-132` |
| `hunger` | `onHunger` | Cancels `FoodLevelChangeEvent` | `ZoneListener.java:134-140` |
| `leafDecay` | `onLeavesDecay` | Cancels `LeavesDecayEvent` | `ZoneListener.java:142-147` |
| `teleportation` | *(no Bukkit listener)* | Only honored by the script engine's `teleport` event factory — `TeleportEventFactory.java:44-48` refuses and sends `<red>Teleportation is disabled in this area.` Warps and plugin teleports are **not** blocked | — |

### 3.8 Resource blocks — `Map<Material, ZoneResourceConfig>`

Defined per zone in YAML and parsed in `ZoneLoader.parse` (`ZoneLoader.java:101-152`); stored on `ZoneDefinition.resourceBlocks`. The model classes:

- **`ZoneResourceConfig`** (`ZoneResourceConfig.java:5-25`) — `regenDelayTicks`, ordered `List<ResourceStage>`, `requiredPower`. The 2-arg constructor defaults `requiredPower` to `0.0` (`ZoneResourceConfig.java:10-12`), which is the legacy-compat contract verified by `ZoneResourceConfigTest.java:17-26`.
- **`ResourceStage`** (`ResourceStage.java:6-22`) — the drops rolled for this stage plus `nextMaterial` (the block material after mining; `null` ⇒ `AIR`, then regen starts immediately).
- **`ZoneResourceDrop`** (`ZoneResourceDrop.java:3-22`) — `itemId`, `minAmount`, `maxAmount`, `chance`; `rollAmount()` returns `min` when `min >= max`, else a uniform `min..max` int (`ZoneResourceDrop.java:18-21`).

The Zone module only **parses and stores** these. The actual mining lifecycle (Breaking Power gate, Fortune scaling, staging, regen, AOE spread) is implemented in the **Resource module** (`ResourceManager.handleBlockBreak`, `ResourceManager.java:49-112`) — see `docs/modules/design/resource.md` §3.2. The Zone module's own contribution to breaking behavior is the exemption in `ZoneListener.onBlockBreak` (`ZoneListener.java:104-115`): resource blocks (and blocks currently tracked mid-progression) remain breakable even when `block-breaking: false`.

### 3.9 Mob spawners — `ZoneMobSpawner` + spawner task

**Model** — `ZoneMobSpawner` (`ZoneMobSpawner.java:7-46`) is immutable: `id`, `mobId`, anchor `x/y/z`, `spawnIntervalTicks`, `maxAlive`, `radius` (counting radius), `spawnRadius` (placement scatter). `getLocation(worldName)` returns the anchor with `+0.5` X/Z offsets (`ZoneMobSpawner.java:41-45`).

**Tick** — `startSpawnerTask` runs a 20-tick `runTaskTimer` on the main thread (`ZoneManager.java:114-117`); `tickSpawners` (`ZoneManager.java:123-153`):

1. Increments `tickCount` by 20 (`ZoneManager.java:124`) — an internal tick counter, not server time.
2. For each zone → each spawner, computes key `"<zoneId>:<spawnerId>"` and skips until `tickCount - lastSpawn >= spawnIntervalTicks` (`ZoneManager.java:127-129`).
3. Resolves the zone's world (skip if missing), builds the anchor center, and counts alive mobs via `countMobs(center, mobId, radius)` (`ZoneManager.java:131-136`; `countMobs` at `:155-165` scans nearby `LivingEntity`s for a matching `Keys.MOB_ID_KEY` PDC tag).
4. Skips if `alive >= maxAlive` (`ZoneManager.java:136`).
5. Resolves the `MobDefinition` via `plugin.getMobManager().getMobDefinition(...)`; skip if unknown (`ZoneManager.java:138-139`).
6. Finds a safe spot with `findSafeSpawnLocation` (`ZoneManager.java:141`; impl `:167-195`) — up to 20 attempts at a solid-ground, non-liquid, air-feet/air-head block within `spawnRadius`, occupied-position check included; falls back to the exact anchor.
7. Spawns via `plugin.getMobManager().spawnMob(def, spawnLoc)` and, on success, tags the entity with **`Keys.MOB_HOME_KEY`** (`valmora:mob_home`, `Keys.java:61`) — a comma string `"<x>,<y>,<z>,<wanderRadius>,<worldName>"` where the coords are the spawner anchor and `wanderRadius = max(spawnRadius * 2, 4)` (`ZoneManager.java:143-150`). Then records the spawn tick.

**Interval units are ticks.** The loader default is 200 (`ZoneLoader.java:93`); the command default is 400 (`ZoneCommand.java:273`).

### 3.10 Mob home behavior task — `tickMobHomes`

`startMobHomeTask` runs a 40-tick main-thread timer (`ZoneManager.java:199-202`). `tickMobHomes` (`ZoneManager.java:208-251`) iterates **every living mob in every loaded world** and:

1. Reads `MOB_HOME_KEY`; skips mobs without it (`ZoneManager.java:214-215`).
2. Parses the 5 fields; skips malformed strings (`ZoneManager.java:217-225`).
3. **Zone containment:** resolves the home zone via `getZoneAt(home)`; if the mob has left that zone, clears its target, stops pathfinding, and orders it back home at speed 1.3 (`ZoneManager.java:231-238`).
4. **Idle wandering:** for target-less, path-less mobs, 1-in-4 chance per tick to wander to a safe point inside the zone at speed `0.6 + random*0.3` (`ZoneManager.java:240-247`).

Uses Paper's `Pathfinder` API (`mob.getPathfinder()`), not NMS navigation (AGENTS.md §11.2). Mob-level tagging (`MOB_HOME_KEY`) is written only by the spawner task — mobs spawned any other way have no home and are skipped.

### 3.11 Wand selection — in-memory corners

Admin workflow: `/zone wand` gives a golden axe tagged `Keys.ZONE_WAND_KEY` (`valmora:zone_wand`, `Keys.java:62`) (`ZoneCommand.java:76-88`). `ZoneWandListener.onInteract` (`ZoneWandListener.java:23-50`) filters to the main hand, a PDC-verified wand, and a clicked block, then sets Pos1 (left click) / Pos2 (right click) and cancels the interaction. State lives in `ZoneManager` maps:

- `selectionPos1` / `selectionPos2`: `Map<UUID, int[]>` of `{x,y,z}` (`ZoneManager.java:49-50`).
- `selectionWorld`: `Map<UUID, String>` — the world the selection was made in (`ZoneManager.java:51`).
- Accessors: `setPos1`/`setPos2` (`:255-265`), `clearSelection` (`:267-272`), `getPos1`/`getPos2`/`getSelectionWorld` (`:274-276`), `hasFullSelection` (`:278-280`).

`/zone pos1`/`/zone pos2` set the corners at the player's feet instead of at a clicked block (`ZoneCommand.java:90-101`). `hasFullSelection` + same-world validation gates `/zone create` (`ZoneCommand.java:113-122`).

### 3.12 CRUD + persistence — `ZoneManager`

| Method | Behavior | Lines |
|---|---|---|
| `createZone(...)` | Normalizes min/max corners, applies `ZoneFlags.defaults()`, registers, saves a fresh YAML file, returns the zone | `ZoneManager.java:284-296` |
| `deleteZone(id)` | Unregisters and deletes `zones/<id>.yml` | `ZoneManager.java:298-305` |
| `setZoneFlags(id, flags)` | `withFlags`, re-registers, saves | `ZoneManager.java:307-314` |
| `addSpawner(zoneId, spawner)` | `withSpawners`, re-registers, saves | `ZoneManager.java:316-325` |
| `removeSpawner(zoneId, spawnerId)` | Case-insensitive remove; re-registers, saves, clears that spawner's last-spawn tick | `ZoneManager.java:327-339` |
| `saveZoneToFile(zone)` | Writes a full `zones/<id>.yml` from the definition (see §3.14) | `ZoneManager.java:341-392` |

### 3.13 Visualization — particle box drawing

- **Zone borders:** `/zone visualize` toggles the player in `visualizingPlayers` (`toggleVisualization`, `ZoneManager.java:407-412`). A 40-tick task draws each zone (within 200 blocks of the player) as a **yellow** DUST-particle wireframe (`tickVisualization`, `ZoneManager.java:414-432`; `drawBox`/`drawLine`, `:481-512`).
- **Selection preview:** a 10-tick task draws Pos1 **blue**, Pos2 **red**, and the bounding selection box **green** (filled with `drawPoint`, `:470-479`) for any player with an active selection in their current world (`tickSelectionVisualization`, `ZoneManager.java:445-466`).

Particles are client-side `spawnParticle(Particle.DUST, ...)` with `DustOptions`; no display entities are used.

### 3.14 `saveZoneToFile` — what is (and isn't) persisted

Writes `plugins/Valmora/zones/<id>.yml` (`ZoneManager.java:341-392`):

- Always: `display-name`, `world`, `min` (3-list), `max` (3-list), and all eight `allow.*` flags (`:347-359`).
- `extra-boxes` as a list of `{min: [...], max: [...]}` maps, only if non-empty (`:361-370`).
- `mob-spawners` as `<id> -> {mob, x, y, z, spawn-interval, max-alive, radius, spawn-radius}` (`:372-385`). Spawner IDs default to `spawner_<i>` when empty.
- On `IOException` it logs `[Zones] Failed to save zone '<id>': ...` (`:387-391`).

**Not written:** `fishing-loot-table`, `resource-blocks`, `enter-actions`, `exit-actions`. Because every CRUD command re-saves the whole file, calling `/zone flag` or `/zone spawner add` on a *hand-edited* zone will rewrite that zone's file and **drop those keys** — see [Unfinished Things](#unfinished-things--todos).

---

## Configuration (YAML)

Zones live in `plugins/Valmora/zones/*.yml`. Each **top-level key is a zone ID**; one file may hold many zones (`YamlLoader.load` iterates every key of every `.yml` file — `YamlLoader.java:37-73`). Loading is `ZoneLoader.loadZones()` → `new YamlLoader<>(plugin, "zones", "Zones").load(this::parse, reg::register)` (`ZoneLoader.java:24-28`). Parse failures are collected and logged as a batch warning with file paths (`YamlLoader.java:113-123`).

### 4.1 Full schema with defaults

```yaml
<zone-id>:                         # lowercase; becomes registry key + file name
  display-name: "<green><id>"      # MiniMessage; default "<green><id>"
  world: world                     # Bukkit world; default "world"
  min: [x, y, z]                   # required — inclusive min corner
  max: [x, y, z]                   # required — inclusive max corner

  allow:                           # optional; fallback: pvp-enabled key (legacy)
    pvp: false                     # PvP damage allowed
    natural-mob-spawning: false    # natural/slime/spawner spawns allowed
    block-breaking: false          # players may break blocks
    block-placing: false           # players may place blocks
    hunger: true                   # hunger depletes
    entry: true                    # players may enter
    teleportation: true            # teleports allowed
    leaf-decay: true               # leaves decay

  extra-boxes:                     # optional list of extra sub-regions
    - min: [x, y, z]
      max: [x, y, z]

  fishing-loot-table: <table-id>   # optional; consumed by the Fishing module

  mob-spawners:                    # optional per-spawner map
    <spawner-id>:
      mob: zombie                  # Valmora mob ID; default "zombie"
      x: 0                         # anchor; default 0
      y: 64                        # default 64
      z: 0                         # default 0
      spawn-interval: 200          # ticks between attempts; default 200
      max-alive: 5                 # alive cap in radius; default 5
      radius: 20.0                 # alive-count radius; default 20.0
      spawn-radius: 3              # placement scatter; default 3

  resource-blocks:                 # optional per-material map
    <MATERIAL>:                    # e.g. DEEPSLATE_IRON_ORE, AMETHYST_CLUSTER
      regen-delay: 600             # ticks to regen; default 600
      required-power: 0.0          # min Breaking Power; default 0.0
      stages:                      # ordered break stages
        - drops:                   # items rolled at this stage
            - item: COBBLESTONE    # custom item id OR vanilla material; default "COBBLESTONE"
              min: 1               # min amount; default 1
              max: 1               # max amount; default 1
              chance: 1.0          # probability 0.0–1.0; default 1.0
          next: STONE              # material after this stage; default null ⇒ AIR

  enter-actions: []                # optional script DSL lines run on entry
  exit-actions: []                 # optional script DSL lines run on exit
```

### 4.2 Option-by-option reference

| Key | Default | Type | Meaning |
| --- | --- | --- | --- |
| `<zone-id>` | — | map key | The zone's ID. `ZoneRegistry` lowercases it. It becomes the registry key and the output file name (`ZoneManager.saveZoneToFile`, `ZoneManager.java:344`). |
| `display-name` | `"<green><id>"` | string (MiniMessage) | Shown in the action bar on entry for 60 ticks (`ZoneListener.java:78`), in `/zone info`, and on the scoreboard "Zone:" line (`ScoreboardUI.java:202-205`). |
| `world` | `"world"` | string | Bukkit world name. Membership/flag checks reject locations from other worlds (`ZoneDefinition.contains` world check, `ZoneDefinition.java:56`). |
| `min` | — (**required**) | list of 3 ints | Inclusive minimum corner `[x, y, z]`. Fewer than 3 entries ⇒ load failure `"missing min/max bounds"` (`ZoneLoader.java:53-56`). |
| `max` | — (**required**) | list of 3 ints | Inclusive maximum corner `[x, y, z]`. |
| `allow.pvp` | `false` | bool | If `false`, `EntityDamageByEntityEvent` between two players at the victim's location is cancelled (`ZoneListener.java:95-102`). |
| `allow.natural-mob-spawning` | `false` | bool | If `false`, `NATURAL`, `SLIME_SPLIT`, and `SPAWNER` spawns are cancelled (`ZoneListener.java:30-32`, `:125-132`). Custom spawns (commands, zone spawner task, scripts, fishing, slayers) are unaffected. |
| `allow.block-breaking` | `false` | bool | If `false`, `BlockBreakEvent` is cancelled — **except** configured resource blocks and tracked intermediate stages, which always remain breakable (`ZoneListener.java:104-115`). |
| `allow.block-placing` | `false` | bool | If `false`, `BlockPlaceEvent` is cancelled (`ZoneListener.java:117-123`). |
| `allow.hunger` | `true` | bool | If `false`, `FoodLevelChangeEvent` is cancelled — hunger freezes (`ZoneListener.java:134-140`). |
| `allow.entry` | `true` | bool | If `false`, players whose move would enter this zone are pushed back to their previous position (`ZoneListener.java:42-53`). |
| `allow.teleportation` | `true` | bool | If `false`, only the script engine's `teleport` event refuses to fire (`TeleportEventFactory.java:44-48`); warps and other teleports are **not** blocked. |
| `allow.leaf-decay` | `true` | bool | If `false`, `LeavesDecayEvent` is cancelled (`ZoneListener.java:142-147`). |
| *(legacy)* `pvp-enabled` | `false` | bool | Read only when the `allow:` section is absent, to preserve old configs; the other seven flags fall back to their defaults (`ZoneLoader.java:50`). |
| `extra-boxes` | *(none)* | list of maps | Additional boxes to make non-rectangular shapes. Each entry needs `min: [x,y,z]` and `max: [x,y,z]` (3+ ints each); malformed entries are silently skipped (`ZoneLoader.java:62-81`). Membership is primary box OR any extra box (`ZoneDefinition.java:55-63`). |
| `fishing-loot-table` | *(none)* | string | Table ID resolved by `FishingManager.getTableForPlayer` (`FishingManager.java:41-45`); absent ⇒ `"default"` fallback. |
| `mob-spawners.<id>.mob` | `"zombie"` | string | Valmora mob ID. Unknown IDs are skipped each tick and by `/zone spawner add` validation (`ZoneManager.java:138-139`, `ZoneCommand.java:265-269`). |
| `mob-spawners.<id>.x/.y/.z` | `0 / 64 / 0` | int | Spawner anchor block. The **home** tag written to spawned mobs uses these coords, and the mob-home task returns mobs here (`ZoneManager.java:145-148`). |
| `mob-spawners.<id>.spawn-interval` | `200` | int (ticks) | Ticks between spawn attempts (20 ticks = 1s). |
| `mob-spawners.<id>.max-alive` | `5` | int | Cap on simultaneously-alive mobs with the same `MOB_ID_KEY` within `radius` (`countMobs`, `ZoneManager.java:155-165`). |
| `mob-spawners.<id>.radius` | `20.0` | double | Counting radius for the alive cap (not the placement radius). |
| `mob-spawners.<id>.spawn-radius` | `3` | int | Placement scatter radius around the anchor; also drives the wander radius (`max(spawnRadius*2, 4)`, `ZoneManager.java:145`). |
| `resource-blocks.<MATERIAL>` | — | material key | Block type turned into a resource node. Uppercased and resolved via `Material.matchMaterial`; unknown materials log `[Zones] Unknown material: <name>` and are skipped (`ZoneLoader.java:105-106`). |
| `resource-blocks.<MATERIAL>.regen-delay` | `600` | int (ticks) | Delay after the final stage is mined before the original material is restored. **Ticks**, not seconds (see `docs/modules/design/resource.md` §4 note). |
| `resource-blocks.<MATERIAL>.required-power` | `0.0` | double | Minimum Breaking Power stat to mine. Below it the break is cancelled with `<red>This ore requires a more powerful tool.` (`ResourceManager.java:71-73`, `ResourceListener.java:27-30`). |
| `resource-blocks.<MATERIAL>.stages` | *(none)* | list of maps | Ordered progression. If absent/empty, the **legacy flat format** is used: the top-level `drops` list is wrapped as a single stage with `next = null` (block → AIR → regen) (`ZoneLoader.java:136-148`). |
| `stages[].drops[].item` | `"COBBLESTONE"` | string | Custom Valmora item id (resolved via `ItemRegistry.createItemStack`, lowercased) or a vanilla `Material` name (uppercased, translated). `null` if neither matches ⇒ nothing given (`ResourceManager.java:156-169`). |
| `stages[].drops[].min` | `1` | int | Minimum rolled amount for this drop. |
| `stages[].drops[].max` | `1` | int | Maximum rolled amount (`ZoneResourceDrop.rollAmount`, `ZoneResourceDrop.java:18-21`). |
| `stages[].drops[].chance` | `1.0` | double | Per-drop roll probability 0.0–1.0 (`ResourceManager.java:78-79`). |
| `stages[].next` | *(none)* | string (material) | Block material after this stage is mined; absent ⇒ `AIR` (`ResourceManager.java:87`). On the last stage it is the depleted appearance while the regen timer runs. |
| `enter-actions` | *(none)* | list of strings | Script DSL lines executed on `ZoneEnterEvent` with `new SimpleExecutionContext(player, location, null)` (`ZoneListener.java:79-82`). |
| `exit-actions` | *(none)* | list of strings | Script DSL lines executed on `ZoneExitEvent` with the same context (`ZoneListener.java:86-92`). |

### 4.3 Shipped config — `src/main/resources/zones/test_zones.yml`

Four demo zones in world `world`:

- **`mine`** (`test_zones.yml:1-262`) — flags: PvP/breaking/placing/natural-spawning/hunger/leaf-decay all `false`, entry+teleportation `true`. Contains the full ore set as two-stage resource blocks (`COAL_ORE`, `IRON_ORE`, `COPPER_ORE`, `GOLD_ORE`, `REDSTONE_ORE`, `LAPIS_ORE`, `DIAMOND_ORE`, `EMERALD_ORE`, plus all eight deepslate variants, `:21-263`): first stage drops the raw ore and becomes `COBBLESTONE`/`COBBLED_DEEPSLATE`, second stage drops cobble and becomes `BEDROCK`, regen-delay 200 ticks.
- **`forest`** (`test_zones.yml:264-283`) — minimal zone, no payloads; `leaf-decay: true`.
- **`mob_spawn`** (`test_zones.yml:284-322`) — two zone spawners (`test_zombie_1` → mob `test_zombie`, `test_skeleton_2` → mob `test_skeleton`), interval 400, max-alive 2, radius 3.0, spawn-radius 3 (`:304-322`).
- **`test_site`** (`test_zones.yml:323-342`) — large 200×40×200 test box.

### 4.4 Shipped config — `src/main/resources/zones/shardworks.yml`

The **Shardworks** mining zone (`shardworks.yml:8-68`), the demo around the Resource module:

- AABB `[-200,0,-200]` → `[-100,60,-100]` (`:11-12`), `block-breaking: true` (`:17`).
- Three custom resource nodes, each with `required-power` gating custom Valmora drops and a single stage (`:24-47`):

| Material | regen-delay | required-power | Drop (custom item) | next |
|---|---|---|---|---|
| `DEEPSLATE_IRON_ORE` | 400 | 7 | `raw_ferrite` 2–4 | `DEEPSLATE` |
| `AMETHYST_CLUSTER` | 600 | 8 | `raw_lumicite` 1–3 | `BUDDING_AMETHYST` |
| `ANCIENT_DEBRIS` | 900 | 9 | `raw_aetherium` 1–2 | `BLACKSTONE` |

- Two spawners (`:49-68`): `cave_guardian_1` → `shardworks_cave_guardian` (interval 400, max-alive 3, radius 40.0), `crystal_wraith_1` → `shardworks_crystal_wraith` (interval 600, max-alive 2, radius 30.0).

The file header comment notes the AABB/spawner coords must match a cavern actually built in-world; the plugin only defines behaviors (`shardworks.yml:1-6`).

---

## Data Model / Persistence

- **No database.** The Zone module never touches `DataStore`, DAOs, or the async executor. All runtime state is in-memory and rebuilt on every `onEnable()`.
- **Zone definitions** live in `plugins/Valmora/zones/*.yml` and are re-read on every enable/reload (`ZoneLoader.java:24-28`). Zone files created/edited by admin commands are re-serialized with `YamlConfiguration` (`ZoneManager.java:341-392`).
- **Player membership** is a transient `Map<UUID, String>` (`ZoneManager.java:42`) populated on join/move/teleport and cleared on quit. It is not persisted (correctly — it is derived state).
- **Selections** are transient per-player `int[]` corners (`ZoneManager.java:49-51`); lost on reload/restart.
- **Spawner timing** is a transient `Map<String, Long>` keyed `"zoneId:spawnerId"` plus a monotonically increasing in-memory `tickCount` (`ZoneManager.java:45-46`); reset on reload.
- **Entity-side tags** written by this module:
  - `Keys.MOB_HOME_KEY` → `valmora:mob_home` (`Keys.java:61`) — `x,y,z,wanderRadius,world` string on spawner-spawned mobs, consumed by the mob-home task (`ZoneManager.java:145-148`, `:214-251`).
  - `Keys.ZONE_WAND_KEY` → `valmora:zone_wand` (`Keys.java:62`) — boolean marker on the selection wand item (`ZoneCommand.java:84`, `ZoneWandListener.java:31-33`).
- **No block-level persistence** for resource nodes: mid-progression state lives in the Resource module's in-memory tracker map (`ResourceManager.java:34`), keyed `"world:x:y:z"`.
- **Disable semantics:** tasks stopped, listeners unregistered, registry cleared, manager nulled (`ZoneModule.java:38-51`). Because `deleteZone` removes the file on disk, a deleted *shipped* zone is re-copied from the JAR on the next startup by `saveAllResources` (`Valmora.java:481`).

---

## API Exposed

`ValmoraAPI.getZoneManager()` (`ValmoraAPI.java:53`) is the stable public surface, implemented at `Valmora.java:382-385`. The concrete `Valmora` class additionally exposes `getZoneModule()` (`Valmora.java:387-389`).

**`ZoneManager` public surface:**

| Method | Signature | Purpose | Lines |
|---|---|---|---|
| `getZoneAt` | `Optional<ZoneDefinition> getZoneAt(Location)` | Smallest-volume zone containing a location (primary box or extra box) | `ZoneManager.java:68-72` |
| `getCurrentZone` | `Optional<ZoneDefinition> getCurrentZone(Player)` | Zone from the cached membership map; empty if the player is outside any zone or untracked | `ZoneManager.java:74-78` |
| `getRegistry` | `ZoneRegistry` | Full registry (case-insensitive) | `ZoneManager.java:80` |
| `onPlayerJoin` | `void onPlayerJoin(Player)` | Seed membership without events | `ZoneManager.java:84-86` |
| `onPlayerQuit` | `void onPlayerQuit(UUID)` | Clear membership + visualization | `ZoneManager.java:88-91` |
| `checkTransition` | `void checkTransition(Player)` | Fire enter/exit events on zone change | `ZoneManager.java:93-110` |
| `createZone` | `ZoneDefinition createZone(String id, String displayName, int x1, int y1, int z1, int x2, int y2, int z2, String worldName)` | Register + persist a new zone with default flags | `ZoneManager.java:284-296` |
| `deleteZone` | `boolean deleteZone(String)` | Unregister + delete file | `ZoneManager.java:298-305` |
| `setZoneFlags` | `ZoneDefinition setZoneFlags(String, ZoneFlags)` | Replace flags + persist | `ZoneManager.java:307-314` |
| `addSpawner` | `ZoneDefinition addSpawner(String, ZoneMobSpawner)` | Append spawner + persist | `ZoneManager.java:316-325` |
| `removeSpawner` | `boolean removeSpawner(String, String)` | Remove spawner by ID + persist | `ZoneManager.java:327-339` |
| `saveZoneToFile` | `void saveZoneToFile(ZoneDefinition)` | Serialize a zone to `zones/<id>.yml` | `ZoneManager.java:341-392` |
| `toggleVisualization` | `boolean toggleVisualization(Player)` | Toggle particle borders; returns new state | `ZoneManager.java:407-412` |
| `getVisualizingPlayers` | `Set<UUID>` | Currently visualizing players | `ZoneManager.java:514` |
| Selection accessors | `setPos1/setPos2/clearSelection/getPos1/getPos2/getSelectionWorld/hasFullSelection` | Wand selection state | `ZoneManager.java:255-280` |
| Task controls | `startSpawnerTask/stopSpawnerTask/startMobHomeTask/stopMobHomeTask/startVisualizationTask/stopVisualizationTask/startSelectionTask/stopSelectionTask` | Main-thread timers | `ZoneManager.java:114-121`, `:199-206`, `:396-404`, `:436-443` |

**Events (subscribable):** `ZoneEnterEvent` / `ZoneExitEvent` in `org.nakii.valmora.module.zone.event`, each `getPlayer()` + `getZone()`.

**Script integration:**
- `ZoneVariableProvider` (`ZoneVariableProvider.java:10-30`) registers namespace `"zone"` with the script module (`ZoneModule.java:31`): `$zone.id$` → zone ID, `$zone.current$` / `$zone.name$` → display name (fallback `"<green>Wilderness"`), `$zone.pvp$` → `isPvpEnabled()` boolean. `null` when there is no player caster.
- The **script `zone` condition** (`ZoneCondition.java`) matches against `PlayerState.getCurrentZoneId()` — **which is never populated** (see [Unfinished Things](#unfinished-things--todos)).

**Concrete-class extras:** `ZoneModule.getZoneRegistry()` (`ZoneModule.java:57`). `Valmora.getZoneModule()` (`Valmora.java:387-389`).

---

## Dependencies & Consumers

### Load order

Registered 16th (`Valmora.java:205`), after `enchant`, before `resource`/`fishing` (`MODULE_DEVELOPMENT.md:511-517`). It is positioned early enough that later modules (resource, fishing, warp, quest) can depend on it, and it does **not** depend on any module at enable time — every dependency below is reached lazily at runtime through `ValmoraAPI` / the plugin instance.

### Dependencies (runtime)

| Dependency | Why |
|---|---|
| `mob` (`MobManager`) | Spawner ticks resolve and spawn `MobDefinition`s and count live mobs by `MOB_ID_KEY` (`ZoneManager.java:138-142`, `:155-165`); `/zone spawner add` validates mob IDs and tab-completes them (`ZoneCommand.java:265-269`, `:354`). |
| `item` | Loot definition for resource blocks uses custom item ids/`ItemTranslator` — but only inside the Resource module (`ResourceManager.java:156-169`); the Zone module itself does not build items. |
| `ui` (`UIManager`) | `onZoneEnter` shows the display name via `getActionBar().showTemporary(...)` for 60 ticks (`ZoneListener.java:78`). |
| `script` (`ScriptModule`) | `enter-actions`/`exit-actions` are compiled and executed via `getEventParser().parseList(...)` (`ZoneListener.java:81`, `:91`); the `zone` variable provider is registered with it (`ZoneModule.java:31`). |
| `resource` (`ResourceModule`) | `ZoneListener.onBlockBreak` exempts tracked resource blocks from the `block-breaking` flag by calling `rm.getResourceManager().isTrackedResource(...)` (`ZoneListener.java:111-113`). |

### Consumers

| Consumer | How it uses the Zone module |
|---|---|
| `resource` (`ResourceManager`) | Resolves the zone for a broken block and pulls `zone.getResourceBlocks().get(type)` (`ResourceManager.java:63-66`); `getResourceConfigAt` does the same for AOE adjacency (`ResourceManager.java:125-129`). Imports `ZoneResourceConfig`, `ResourceStage`, `ZoneResourceDrop`. |
| `fishing` (`FishingManager`) | `getZoneAt(player.getLocation())` → `zone.getFishingLootTable()` to select the loot table (`FishingManager.java:40-45`). |
| `item` (`LootListener`) | Defers vanilla mining drops for configured resource blocks and tracked stages via `getZoneAt` + `getResourceBlocks().containsKey` (`LootListener.java:41-48`). |
| `quest` (`QuestListener`) | `REACH_ZONE` objective triggered on `ZoneEnterEvent` (`QuestListener.java:108-112`). |
| `ui` (`ScoreboardUI`) | "Zone:" scoreboard line from `getCurrentZone(...).getDisplayName()` (`ScoreboardUI.java:201-206`). |
| `script` (`TeleportEventFactory`) | Blocks `teleport` DSL events when `teleportation()` is `false` (`TeleportEventFactory.java:44-48`). |
| `script` (`ZoneCondition`) | `zone <id>` condition — but see the dead `currentZoneId` note below. |
| `mob` (module docs) | Zone spawners are one of the four spawn callers; `MOB_HOME_KEY` behavior task complements the mob module's lack of AI (`docs/modules/design/mob.md:413`, `:432`). |
| command layer | `/zone` executor + tab completer wired in `Valmora.java:246-248`, declared `plugin.yml:46-49`. |

---

## Unfinished Things / TODOs

- **`ZoneCondition` never matches.** `ZoneCondition.java:17-21` reads `vp.getActiveProfile().getPlayerState().getCurrentZoneId()`, but `setCurrentZoneId` is **never called anywhere** (grep across `src/main/java` finds only the setter itself at `PlayerState.java:35`). The `zone` script condition therefore always evaluates `false`. `currentZoneId` is also `transient` (`PlayerState.java:11`), so even wiring it up would need a live update hook (e.g. from `ZoneEnterEvent`/`ZoneExitEvent`).
- **`teleportation` flag is only honored by the script engine.** There is no `PlayerTeleportEvent` listener for it (`ZoneListener` handles move, join, quit, pvp, block, spawn, hunger, leaves-decay — `ZoneListener.java:42-147`). Warps (`WarpManager.teleport`), `player.teleportAsync`, and other teleports are unaffected by `allow.teleportation: false`. The plan (`ZONE_MODULE_PLAN.md`) lists teleportation among the flags to manage.
- **`saveZoneToFile` drops hand-written keys.** `fishing-loot-table`, `resource-blocks`, `enter-actions`, and `exit-actions` are not serialized (`ZoneManager.java:347-385`). Any `/zone flag` or `/zone spawner` command on a YAML-edited zone rewrites the file and silently loses those keys. A reload after editing `resource-blocks` in YAML followed by an admin command would purge them.
- **Deleted shipped zones come back.** `deleteZone` removes `zones/<id>.yml` (`ZoneManager.java:302-303`), but `saveAllResources` re-copies `zones/*` from the JAR on the next startup whenever the file is missing (`Valmora.java:472-484`).
- **Tab-completion only lists 4 of 8 flags.** `FLAGS` at `ZoneCommand.java:24-25` covers `pvp`, `natural-mob-spawning`, `block-breaking`, `block-placing`, while the `flag` sub-command accepts all eight (`ZoneCommand.java:219-228`). `hunger`, `entry`, `teleportation`, `leaf-decay` complete as free text only.
- **`/zone spawner add` radius formula is inconsistent with the YAML default.** The command builds `radius = spawnRadius * 4.0` (`ZoneCommand.java:283`), while the loader default is a flat `20.0` (`ZoneLoader.java:95`); spawner IDs are `mobId_<count+1>` (`ZoneCommand.java:281`) and can collide after removals (no uniqueness check in `addSpawner`).
- **Planned flags from `ZONE_MODULE_PLAN.md` not implemented** — fall damage, ability use, healing, cold damage, crop growth, block regeneration for farms/trees (the plan's Hub/Forest/Mine feature list). `docs/todo.md:33` ("manage other flags for zones like hunger, teleportation, entry, leaf decay") is now mostly done for hunger/entry/leaf-decay but `teleportation` remains partial (above).
- **No `extra-boxes` in-game editing.** The parser supports multiple boxes (`ZoneLoader.java:62-81`) and `saveZoneToFile` writes them (`ZoneManager.java:361-370`), but there is no `/zone` command to add/remove them; `docs/todo.md:20` ("zones to have multiple boxes...") is only half-addressed.
- **`getZoneAt` is O(n) per call** — streams the whole registry on every flag check, membership check, resource lookup, and mob count. With many zones this is the module's hot path.
- **`tickMobHomes` iterates all living entities in all worlds** every 40 ticks (`ZoneManager.java:210-211` via `world.getLivingEntities()`), regardless of whether any spawner-tagged mob exists — a scaling concern on large servers.
- **No `natural-mob-spawning`/time-of-day/capacity logic** — `docs/todo.md:32` ("flesh out mob spawning in zones"). Spawners are purely interval-based; the plan's "smart spawning at night up to a capacity" is not implemented.
- **Spawner interval is measured against an internal `tickCount`** incremented by 20 per task tick (`ZoneManager.java:124`), which is fine for a 20-tick timer but makes the timing depend on the task being scheduled exactly every 20 ticks.
- **Entry push-back only guards `entry()` zones.** `onMoveEntryCheck` also ignores Y-coordinate-only movement changes? No — it compares block X/Y/Z (`ZoneListener.java:45-47`), so falling into a blocked zone *is* caught. However, `/zone entry` blocks the *destination* box even if the player was already inside via a nested setup; reference-equality comparison `toZone != fromZone` (`ZoneListener.java:50`) works because `getZoneAt` returns registry instances.
- **No zone tests beyond `ZoneResourceConfigTest`.** Membership, lookup precedence, spawner tick, and flag logic have no unit coverage (project guidance in AGENTS.md §9).

---

## Possible Improvements / Changes

- **Wire up `currentZoneId`.** Set `PlayerState.setCurrentZoneId` from `ZoneEnterEvent`/`ZoneExitEvent` (e.g. a `MONITOR` listener) so the script `zone` condition, quest conditions, and any future system agree on the current zone. Alternatively have `ZoneCondition` use `ZoneManager.getCurrentZone` directly (`ZoneManager.java:74-78`).
- **Honor `teleportation` flag globally** — add a `PlayerTeleportEvent` handler (cancelling only when the *destination* is in a `teleportation() == false` zone), consistent with the other six flags.
- **Round-trip the full schema in `saveZoneToFile`:** serialize `fishing-loot-table`, `resource-blocks`, `enter-actions`, and `exit-actions` so admin commands never destroy hand-written config.
- **Add `/zone box` (add/remove extra boxes), `/zone resource` (attach a `resource-blocks` entry to a selected block), and `/zone fishing`** subcommands, mirroring the existing spawner flow and reusing `saveZoneToFile`.
- **Spatial index for `getZoneAt`** (e.g. chunk-keyed buckets or an interval tree) to avoid the per-call registry stream; short-circuit on empty registry.
- **Optimize `tickMobHomes`** — iterate only spawner-tagged mobs (track UUIDs from the spawner task) or restrict to zones/worlds that actually have spawners.
- **Unify spawner defaults/radius:** use one radius definition (loader default vs `spawnRadius * 4.0`) and a collision-safe spawner ID generator (`spawner_<timestamp>` or first free index).
- **Persistence for mid-progress zones** — track spawner tick state and player membership in the database so a `/valmora reload` doesn't reset spawner phase (currently `spawnerLastSpawnTick`/`tickCount` reset on every reload).
- **Extra-box support in the wand flow** — extend selection to a multi-box list so admins can build non-cuboid shapes entirely in-game.
- **Config-driven defaults:** hoist the default flag set and the default `"COBBLESTONE"` drop item into `config.yml` or constants so docs and code stay in sync (the 600-tick `regen-delay` default already drifts from `USER_DOCS.md`'s "seconds" language).
- **Night/day + capacity spawning** for the plan's graveyard-style "smart spawning" (`ZONE_MODULE_PLAN.md`), using the Time module's calendar (`ValmoraAPI.getTimeManager()`).
