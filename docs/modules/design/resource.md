# Resource Module — Design & Code

> **Module ID:** `resource` | **Display name:** "Resource System" | **Package:** `org.nakii.valmora.module.resource`
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

The Resource Module is the **regenerating mining-block engine** of Valmora. It turns ordinary world blocks inside a zone into *resource nodes* that:

- can be **gated behind a Breaking Power threshold** (`required-power`),
- drop **configurable loot** (custom Valmora items or vanilla materials) with per-drop chance and min/max amounts,
- go through **multi-stage progression** (each break transitions the block to a `next` material) before fully depleting,
- **regenerate** back to their original material after a configurable delay,
- participate in **Mining Fortune** drop-quantity scaling and **Mining Spread** (AOE) mining.

Crucially, the module **owns no configuration of its own**. All resource-node definitions live in the **Zone module's** `zones/*.yml` files under the `resource-blocks:` key (parsed by `ZoneLoader`). The Resource Module is a *behavior engine* that reads those zone definitions at break time and drives the block lifecycle. This is why there is no `resources/` config folder or loader inside `module/resource/`.

Because all state (which block is mid-progression, when it regenerates) is held in an in-memory map keyed by block coordinates, nothing is persisted to the database and everything is restored/cleared on module disable.

---

## 2. Code Structure

```
src/main/java/org/nakii/valmora/module/resource/
├── ResourceModule.java     # ReloadableModule lifecycle wrapper (onEnable / onDisable)
├── ResourceManager.java    # Core logic: break handling, tracking, regen, drops, stats
└── ResourceListener.java   # BlockBreakEvent entry point; dispatches into ResourceManager
```

Supporting classes owned by **other** modules that the resource engine depends on:

```
src/main/java/org/nakii/valmora/module/zone/
├── ZoneDefinition.java       # Holds Map<Material, ZoneResourceConfig> resourceBlocks
├── ZoneResourceConfig.java   # regen-delay, required-power, stages
├── ResourceStage.java        # per-stage drops + nextMaterial transition
├── ZoneResourceDrop.java     # item id, min/max amount, chance
└── ZoneLoader.java           # Parses resource-blocks from zones/*.yml (lines 101-152)

src/main/java/org/nakii/valmora/module/item/impl/
└── AoeMineMechanic.java      # "aoe_mine" AbilityMechanic + static mineRadius() helper
```

The module follows the standard `XModule` / `XListener` convention from AGENTS.md §3, but it has **no** `XRegistry`/`XLoader` of its own because it consumes the Zone module's registry.

---

## 3. Architecture & Key Classes

### 3.1 Lifecycle — `ResourceModule`

`ResourceModule` is a plain `ReloadableModule` (`ResourceModule.java:7`):

- **Constructor** stores only the plugin reference (`ResourceModule.java:13-15`) — all state is created in `onEnable()`, per AGENTS.md §6.1.
- **`onEnable()`** (`ResourceModule.java:18-23`): logs, builds a new `ResourceManager`, builds a new `ResourceListener`, registers the listener with `plugin.getServer().getPluginManager()`.
- **`onDisable()`** (`ResourceModule.java:26-30`): calls `resourceManager.cancelAll()` (restores all mined blocks and cancels all pending regen tasks), nulls the manager, and unregisters the listener via `HandlerList.unregisterAll(listener)` — mandatory per AGENTS.md §6.2 to avoid duplicate handlers after `/valmora reload`.
- **`getId()`** → `"resource"`, **`getName()`** → `"Resource System"` (`ResourceModule.java:32-33`).
- **`getResourceManager()`** getter (`ResourceModule.java:35`).

### 3.2 The break pipeline — `ResourceManager`

`ResourceManager` (`ResourceManager.java:21`) owns:

- `Map<String, ResourceTracker> trackedBlocks` (`ResourceManager.java:34`) — the only runtime state. Keys are `"world:x:y:z"` via `locationKey()` (`ResourceManager.java:171-174`).

**`BreakResult` enum** (`ResourceManager.java:24-31`) is the return contract used by the listener:

| Constant | Meaning |
| --- | --- |
| `NOT_TRACKED` | Block is not a configured resource block in this zone → vanilla handling applies. |
| `INSUFFICIENT_POWER` | Block is a resource block but the player's Breaking Power is below `required-power`. |
| `HANDLED` | Mined successfully; drops generated and block progressed/regenerated. |

**`handleBlockBreak(Player, Block)`** (`ResourceManager.java:49-112`) is the heart of the module:

1. **Look up tracker** (`ResourceManager.java:50-69`). If a `ResourceTracker` already exists for the location and `tracker.stageIndex >= config.getStageCount()`, the block is *depleted / awaiting regen* and the method short-circuits to `HANDLED` without dropping anything (`ResourceManager.java:58`).
2. **Resolve zone config** (`ResourceManager.java:63-68`). If no tracker exists, it looks up the zone via `plugin.getZoneManager().getZoneAt(location)` and pulls `zone.getResourceBlocks().get(block.getType())`. If there is no zone or no config for that block material → `NOT_TRACKED`. The zone lookup uses the smallest-volume containing zone (`ZoneManager.java:68-72`).
3. **Breaking Power gate** (`ResourceManager.java:71-73`). If `getPlayerBreakingPower(player) < config.getRequiredPower()` → `INSUFFICIENT_POWER`.
4. **Roll drops** (`ResourceManager.java:75-84`). For each drop in the current stage, if `Math.random() < drop.getChance()` the amount is rolled via `drop.rollAmount()` (`ZoneResourceDrop.java:18-21`), scaled by Mining Fortune, then the item is built and added directly to the player inventory with `player.getInventory().addItem(item)`.
5. **Progress / regenerate** (`ResourceManager.java:86-109`):
   - The next block material is `stage.getNextMaterial()` or `AIR` if null (`ResourceManager.java:87`; `ResourceStage.java:17-21`).
   - Any existing regen task for this block is cancelled first (`ResourceManager.java:90`).
   - The block type is set on the main thread via `runTask(plugin, ...)` with physics disabled: `block.setType(nextMat, false)` (`ResourceManager.java:94`).
   - A regen task is scheduled with `runTaskLater(..., config.getRegenDelayTicks())`; when it fires it restores `finalOriginal` material (physics off) and removes the tracker from the map (`ResourceManager.java:96-99`).
   - The tracker's `stageIndex` advances to `stageIndex + 1`, or to the "depleted sentinel" `config.getStageCount()` on the last stage (`ResourceManager.java:101-102`). A fresh tracker is created when none existed before (`ResourceManager.java:104-109`).

**Stat reads** follow the standard profile path (`ResourceManager.java:114-120`, `142-148`):
`ValmoraAPI.getInstance().getPlayerManager().getSession(uuid)` → `.getActiveProfile()` → `profile.getStatManager().getStat(...)`. Missing session/profile → `0.0`. The stat keys come from `SystemStats` (`SystemStats.java:58-61`) — i.e. the `config.yml` → `mining.*-stat` mappings.

- **Breaking Power** → `getSystemStats().getBreakingPower()` (`ResourceManager.java:119`)
- **Mining Fortune** → `getSystemStats().getMiningFortune()` (`ResourceManager.java:147`)

**Mining Fortune formula** — `applyFortune(base, fortune)` (`ResourceManager.java:150-154`):
`multiplier = 1.0 + fortune / 100.0`, result `max(base, round(base * multiplier))`, truncated to `int`. So 100 Fortune doubles the rolled amount; the result never goes below the base.

**Item creation** — `createItem(itemId, amount)` (`ResourceManager.java:156-169`):
1. Try the custom item registry: `plugin.getItemManager().getItemRegistry().createItemStack(itemId.toLowerCase())` (`ItemRegistry.java:25-27`). If found, set the amount.
2. Fall back to vanilla: `Material.matchMaterial(itemId.toUpperCase())`, build a plain `ItemStack`, then run it through `plugin.getItemManager().getItemTranslator().translate(vanilla)` (`ItemTranslator.java:23-51`) so it gains Valmora PDC metadata (item type, rarity, stat tags).
3. Returns `null` if neither a custom item nor a matching material exists.

**Auxiliary helpers:**
- `isTrackedResource(Location)` (`ResourceManager.java:40-42`) — used by other listeners to defer to this module.
- `getResourceConfigAt(Location)` (`ResourceManager.java:125-129`) — resolves the zone config for the block *at* a location; used by `AoeMineMechanic` adjacency checks.
- `cancelAll()` (`ResourceManager.java:131-140`) — cancels every regen task, immediately restores each tracked block to its `originalMaterial` (physics off, world-guarded), and clears the map. Called from `onDisable()`.

### 3.3 The event entry point — `ResourceListener`

`ResourceListener` (`ResourceListener.java:12`) handles `BlockBreakEvent` at `EventPriority.HIGH` with `ignoreCancelled = true` (`ResourceListener.java:20`), then switches on `BreakResult`:

- `NOT_TRACKED` → do nothing, vanilla handling applies (`ResourceListener.java:26`).
- `INSUFFICIENT_POWER` → `event.setCancelled(true)` and sends the MiniMessage text `<red>This ore requires a more powerful tool.` (`ResourceListener.java:27-30`).
- `HANDLED` → `event.setDropItems(false)` (the module already put drops in the inventory), then reads the **Mining Spread** stat (`ResourceListener.java:42-48`), computes `radius = (int) Math.floor(spread)`, and if `radius > 0` calls `AoeMineMechanic.mineRadius(resourceManager, player, event.getBlock(), radius)` (`ResourceListener.java:31-38`).

### 3.4 AOE mining — `AoeMineMechanic`

`AoeMineMechanic` (`AoeMineMechanic.java:18`) implements `AbilityMechanic` and is registered in `AbilityManager.registerMechanics()` as `"aoe_mine"` (`AbilityManager.java:64`).

- **`execute(ExecutionContext)`** (`AoeMineMechanic.java:26-36`) — the ability-driven path. Requires a player caster and a location; grabs the `ResourceManager` from `Valmora.getInstance().getResourceModule()` (null-safe), uses `context.getInt("radius", 1)` clamped to `>= 1`, and calls `mineRadius(...)`. This is currently the only intended future use (e.g. an active "burst mine" item ability); today the module is driven through the static helper below.
- **`mineRadius(ResourceManager, Player, Block origin, int count)`** (`AoeMineMechanic.java:45-67`) — the passive **Mining Spread** path. Steps:
  1. Resolves the origin's config + material; returns immediately if not a resource block (`AoeMineMechanic.java:47-49`).
  2. Iterates the 26-neighbourhood around the origin (`dx, dy, dz ∈ [-1,1]`, skipping the center) until `count` blocks have been mined (`AoeMineMechanic.java:52-66`).
  3. For each candidate with the same material **and** its own resource config, calls `resourceManager.handleBlockBreak(player, candidate)` directly (not via the event, so no recursion). Only `HANDLED` results increment `mined`; blocks the player lacks Breaking Power for are silently skipped (the required-power gate still applies per block, as does Fortune).

---

## 4. Configuration (YAML)

The Resource Module reads **no YAML of its own**. Every option lives in the Zone module's `zones/*.yml` files (`plugins/Valmora/zones/`, loaded by `ZoneLoader` — `ZoneLoader.java:24-28`), under the `resource-blocks:` map of each zone. Defaults are applied in `ZoneLoader.parse()` (`ZoneLoader.java:101-152`).

> **Units note:** `regen-delay` is in **ticks** (20 ticks = 1 second). The earlier `docs/USER_DOCS.md` schema comment calls it "seconds"; the code (`ZoneLoader.java:109`) treats it as ticks.

### 4.1 Full schema (with defaults)

```yaml
<zone-id>:
  resource-blocks:
    <MATERIAL>:                      # e.g. DEEPSLATE_IRON_ORE — matched via Material.matchMaterial(upper)
      regen-delay: 600               # (int, ticks) default 600 → time before the original block is restored
      required-power: 0.0            # (double) default 0.0 → min Breaking Power stat to mine this block
      stages:                        # ordered list of break stages
        - drops:                     # items rolled when this stage is mined
            - item: "COBBLESTONE"    # (string) default "COBBLESTONE" → custom item-id OR vanilla material name
              min: 1                 # (int) default 1 → minimum rolled amount
              max: 1                 # (int) default 1 → maximum rolled amount
              chance: 1.0            # (double) default 1.0 → probability 0.0–1.0
          next: "STONE"              # (string) default null → material the block becomes after this stage; null ⇒ AIR
```

### 4.2 Option-by-option reference

| Key | Default | Type | Meaning |
| --- | --- | --- | --- |
| `resource-blocks.<MATERIAL>` | — | material key | Block type in the zone that becomes a resource node. Key is uppercased and resolved via `Material.matchMaterial` (`ZoneLoader.java:105`); unknown materials log `[Zones] Unknown material: ...` and are skipped (`ZoneLoader.java:106`). |
| `regen-delay` | `600` | int (ticks) | Delay after the final stage is mined before the block is restored to its original material. 20 ticks = 1s (`ZoneLoader.java:109`, `ResourceManager.java:96-99`). |
| `required-power` | `0.0` | double | Breaking Power stat threshold. Below it the break is cancelled (`ResourceManager.java:71-73`). |
| `stages` | — | list of maps | Ordered progression. Empty/missing ⇒ the **legacy flat format** is used (`ZoneLoader.java:136-148`): the top-level `drops` list is wrapped as a single stage with `next = null` (block → AIR → regen). |
| `stages[].drops` | — | list of maps | Drops rolled for this stage. Each is rolled independently with `Math.random() < chance` (`ResourceManager.java:78-79`). |
| `stages[].drops[].item` | `"COBBLESTONE"` | string | Custom Valmora item id (resolved via `ItemRegistry.createItemStack`, lowercased) or a vanilla `Material` name (uppercased, then run through `ItemTranslator`). `null` returned if neither matches → nothing is given (`ResourceManager.java:156-169`). |
| `stages[].drops[].min` | `1` | int | Minimum rolled amount for this drop. |
| `stages[].drops[].max` | `1` | int | Maximum rolled amount. `rollAmount()` returns `min` when `min >= max`, else uniform `min..max` (`ZoneResourceDrop.java:18-21`). |
| `stages[].drops[].chance` | `1.0` | double | Probability the drop rolls (0.0–1.0). |
| `stages[].next` | `null` | string (material) | The block material after this stage is mined. `null`/absent ⇒ the block becomes `AIR` (`ResourceManager.java:87`). On the **last** stage this determines the "depleted" appearance while the regen timer runs (e.g. `BEDROCK`, `COBBLESTONE`). |

### 4.3 Sample — multi-stage node (`src/main/resources/zones/test_zones.yml:23-37`)

```yaml
COAL_ORE:
  regen-delay: 200
  stages:
    - drops:
        - item: COAL
          min: 1
          max: 1
          chance: 1.0
      next: COBBLESTONE
    - drops:
        - item: COBBLESTONE
          min: 1
          max: 1
          chance: 1.0
      next: BEDROCK
```

First break drops COAL and becomes COBBLESTONE; second break drops COBBLESTONE and becomes BEDROCK (depleted); after 200 ticks it regenerates to COAL_ORE.

### 4.4 Sample — custom drops + power gate (`src/main/resources/zones/shardworks.yml:24-31`)

```yaml
DEEPSLATE_IRON_ORE:
  regen-delay: 400
  required-power: 7
  stages:
    - drops:
        - { item: raw_ferrite, min: 2, max: 4, chance: 1.0 }
      next: DEEPSLATE
```

Requires Breaking Power ≥ 7, drops the custom item `raw_ferrite`, becomes DEEPSLATE, regenerates after 400 ticks.

### 4.5 Relevant stat configuration (not resource-specific)

The mining stats consumed by this module are declared in the stat system:

- `config.yml` → `mining:` block (`config.yml:106-110`) maps logical stat names to stat ids (`mining-fortune-stat`, `mining-speed-stat`, `breaking-power-stat`, `mining-spread-stat`).
- `stats/core.yml`:
  - `mining_fortune` — default `0`, max `500` ("Multiplies the quantity of drops when mining resource blocks.") (`stats/core.yml:85-91`).
  - `breaking_power` — default `0`, max `20` ("Minimum tool power required to break certain ores.") (`stats/core.yml:197-203`).
  - `mining_spread` — default `0`, max `10` ("Number of additional adjacent matching blocks mined at once.") (`stats/core.yml:205-211`).
  - `mining_speed` (`stats/core.yml:93-99`) is not read by the resource module (it maps to the vanilla `block_break_speed` attribute).

**Important:** a tool's Breaking Power comes from its `BREAKING_POWER` stat in the item definition (see `src/main/resources/items/shardworks_pickaxes.yml:17`, values 7/8/10), because the module reads the player's profile stat (`ResourceManager.java:114-120`), which `StatModule` populates from item stats. `ItemFactory.getBreakingPower(Material)` (`ItemFactory.java:215-222`) is only used for the "Breaking Power N" **lore line** on PICKAXE/SHOVEL/AXE/HOE items (`ItemFactory.java:114-125`), not by the resource engine. Vanilla tools translated by `ItemTranslator.mapVanillaStats()` (`ItemTranslator.java:62-86`) receive mining speed but **no** breaking power, so they cannot mine nodes with `required-power > 0` — exactly as the comment in `shardworks_pickaxes.yml:1-6` describes.

---

## 5. Data Model / Persistence

- **All runtime state is in-memory:** the `trackedBlocks` map (`ResourceManager.java:34`) holding `ResourceTracker` objects (`ResourceManager.java:176-190`): `originalMaterial`, `ZoneResourceConfig config`, `Location location`, `int stageIndex`, `BukkitTask regenTask`.
- **Key:** `"<world>:<blockX>:<blockY>:<blockZ>"` (`ResourceManager.java:171-174`).
- **No database involvement.** The module never touches `DataStore`, DAOs, or the async executor. There is no block PDC tagging — a block is "tracked" purely because it is present in the map.
- **Regeneration** is implemented as vanilla block state changes on the main thread (`block.setType(...)`) with scheduled `BukkitTask`s (`ResourceManager.java:94-99`).
- **Disable semantics:** `cancelAll()` (`ResourceManager.java:131-140`) restores every tracked block immediately and clears the map, so a hot reload or plugin disable leaves the world in its original state. On a hard crash (no clean disable), mid-progress blocks would be left in their intermediate material and, since the tracker is lost, would simply behave as plain blocks afterwards.

---

## 6. API Exposed

The module exposes a **concrete-class-only** API. It is **not** part of the `ValmoraAPI` interface (`ValmoraAPI.java:9-70` has no `getResourceModule()`), which is a notable gap — consumers must go through the concrete plugin instance.

**Accessor chain:**
- `Valmora.getResourceModule()` → `ResourceModule` (`Valmora.java:391-393`).
- `ResourceModule.getResourceManager()` → `ResourceManager` (`ResourceModule.java:35`).

**`ResourceManager` public surface:**
- `BreakResult handleBlockBreak(Player, Block)` — `ResourceManager.java:49`.
- `boolean isTrackedResource(Location)` — `ResourceManager.java:40`.
- `ZoneResourceConfig getResourceConfigAt(Location)` — `ResourceManager.java:125`.
- `void cancelAll()` — `ResourceManager.java:131`.
- `BreakResult` enum — `ResourceManager.java:24`.
- `Valmora getPlugin()` (package-private) — `ResourceManager.java:122`.

**`AoeMineMechanic` public surface:**
- `static void mineRadius(ResourceManager, Player, Block origin, int count)` — `AoeMineMechanic.java:45` (usable by any mechanic).
- Registered mechanic id `"aoe_mine"` — `AoeMineMechanic.java:22`, registered at `AbilityManager.java:64`.

---

## 7. Dependencies & Consumers

### Dependencies (load-order relevant)

Registered after the Zone module (`Valmora.java:205` zone → `:206` resource → `:207` fishing). Per `MODULE_DEVELOPMENT.md:512`, `resource` depends on `zone`. It also transitively requires:

- **zone** — `ZoneManager.getZoneAt` / `ZoneDefinition.getResourceBlocks()` (`ResourceManager.java:63-66`).
- **player (profile)** — `ValmoraPlayer.getActiveProfile()` for stat reads (`ResourceManager.java:115`, `143`; `ResourceListener.java:43-44`).
- **stat** — `SystemStats` keys for Breaking Power / Mining Fortune / Mining Spread (`ResourceManager.java:119`, `147`; `ResourceListener.java:47`).
- **item** — `ItemRegistry.createItemStack` + `ItemTranslator.translate` for drop creation (`ResourceManager.java:158`, `168`).

### Consumers

| Consumer | Location | How it interacts |
| --- | --- | --- |
| `ZoneListener.onBlockBreak` | `ZoneListener.java:105-115` | Skips the zone's `block-breaking` restriction for configured resource blocks and currently tracked blocks (tracked via `ResourceModule.isTrackedResource`). |
| `LootListener.onBlockBreak` | `LootListener.java:35-60` | The generic mining-drop / telekinesis handler defers to the resource module: if the block is a configured resource block or a tracked block, the vanilla drop pipeline returns early so the resource module owns drops. |
| `SkillListener.onBlockBreak` | `SkillListener.java:43-53` | Grants mining-skill XP (`BLOCK_BREAK` source) keyed on the broken block material. Since a `HANDLED` resource break does **not** cancel the event, XP still accrues for resource nodes. |
| `CollectionListener.onBlockBreak` | `CollectionListener.java:40-44` | Tracks `BLOCK_BREAK` by material at `MONITOR` for collection progression. |
| `QuestListener.onBlockBreak` | `QuestListener.java:132-135` | Feeds `BLOCK_BREAK` quest objectives (e.g. the Forgotten Mine `mine_coal` objective) using the block material name. |
| `AoeMineMechanic` | `AoeMineMechanic.java:45-67` | Passive Mining Spread AOE mining on `HANDLED` breaks; also the `"aoe_mine"` ability mechanic path. |
| `EnchantModule` → `FortuneLogic` | `FortuneLogic.java:14` | The `valmora:fortune` enchant adds `+10.0 * level` Mining Fortune modifiers, indirectly boosting resource drops. |
| **Progression (Geomancy)** | `src/main/resources/progression/geomancy.yml`, `guis/geomancy_tree.yml` | Grants `mining_fortune` / `mining_spread` / `mining_speed` per node, feeding the stats the resource engine reads. |
| **Demo content** | `zones/shardworks.yml`, `items/shardworks_pickaxes.yml`, `items/shardworks_armor.yml` | The Shardworks mining zone and its tiered tool/armor set are built entirely around this module. |

---

## 8. Unfinished Things / TODOs

- **TODO (todo.md:14):** *"resource: add custom drops support and create a draven mines demo."* Custom drop support (item ids + vanilla materials) is now implemented in `createItem` (`ResourceManager.java:156-169`), but the **Draven Mines demo** zone has not been built.
- **No `ValmoraAPI` exposure:** `getResourceModule()` lives only on the concrete `Valmora` class (`Valmora.java:391-393`), forcing consumers to depend on the plugin class rather than the API interface.
- **No in-game editing commands:** `ZoneCommand` (`ZoneCommand.java:28`) exposes `create|delete|info|list|wand|pos1|pos2|clear|flag|spawner|visualize` but no `resource` subcommand. Resource blocks can only be defined by hand-editing `zones/*.yml` + reload.
- **Doc mismatch:** `docs/USER_DOCS.md` documents `regen-delay` in seconds, but the code uses ticks (`ZoneLoader.java:109`).
- **XP orbs still drop:** on a `HANDLED` break only `setDropItems(false)` is called (`ResourceListener.java:32`); vanilla block XP (`expToDrop`) is not zeroed, so XP orbs can spawn from a "mined" node.
- **Depleted blocks are still breakable:** a block whose tracker has `stageIndex >= stageCount` (`ResourceManager.java:58`) returns `HANDLED` with no drops; the event is not cancelled, so the player can keep breaking the depleted intermediate block (it then becomes air and the pending regen task restores the original).
- **No crash persistence:** mid-progress state is lost on an unclean shutdown; blocks are left as intermediate materials (see §5).
- **No environmental triggers:** the module only reacts to player `BlockBreakEvent`. Explosions, pistons, and other block changes do not interact with the tracking or regeneration logic.
- **No AOE feedback:** Mining Spread silently skips neighbors the player lacks power for (`AoeMineMechanic.java:59-63`) and there are no sounds/particles on AOE or regen.

---

## 9. Possible Improvements / Changes

- **Expose through `ValmoraAPI`:** add `ResourceModule getResourceModule()` to `ValmoraAPI.java` (following `getZoneManager()` pattern, `ValmoraAPI.java:53`) so `AoeMineMechanic` and other modules don't reach into the concrete class.
- **Persist tracker state:** save `(world, x, y, z, stageIndex, originalMaterial, regenAt)` to the existing database/DAO layer so mid-progress blocks survive restarts and chunks can unload safely.
- **Handle chunk/world unload:** persist or restore tracked blocks on `ChunkUnloadEvent`/world unload to avoid relying on a live world in regen tasks.
- **Add `/zone resource` commands:** in-zone block selection to add/remove resource configs (like the existing spawner subcommands) and write them back via `ZoneManager.saveZoneToFile` (`ZoneManager.java:341-392`).
- **Zero out block XP** in the `HANDLED` branch (`event.setExpToDrop(0)`) for consistent loot-only mining.
- **Cancel depleted-block breaks** (or keep them breakable but purely cosmetic) so the "awaiting regen" visual isn't destructible for free.
- **Per-player yields:** allow `drops` entries to respect the Looting/Magic Find style stats, and support a `global-chance`/`rolls` field per stage.
- **Block-hit stages:** add a "hit points" per stage so nodes take multiple hits before progressing (paired with the `mining_speed` stat, which is currently unused by this module).
- **Environment integration:** listen for explosions/pistons and treat affected tracked blocks as mined (or cancel their destruction).
- **Feedback polish:** play break/regenerate sounds and spawn `Particle` effects, and use `TextDisplay` entities for node names/health (per AGENTS.md §11.17, never ArmorStands).
- **Extract config constants:** hoist the `"COBBLESTONE"` default item (`ZoneLoader.java:124`) and 600-tick default into documented constants to keep `ZoneLoader` and this doc in sync.
