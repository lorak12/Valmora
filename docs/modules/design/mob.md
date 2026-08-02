# Mob Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `mobs` | **Source:** `src/main/java/org/nakii/valmora/module/mob/`

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

The Mob module is the **custom-mob engine**. Every mob is a YAML definition in `plugins/Valmora/mobs/*.yml` that describes an entity type, stats (health, damage, speed, defense, strength, crit), damage-type resistances, equipment, loot, behavior flags, and — for bosses — a boss bar and a set of **abilities** that reuse the item-ability mechanic system.

Important structural note: **there is no `MobModule` class.** `MobManager` itself implements `ReloadableModule` (`MobManager.java:11`), so the module ID is `"mobs"` (`MobManager.java:45`) and its display name is `"Mob Engine"` (`MobManager.java:51`). There are also **no `event/`, `logic/`, or `nodes/` subpackages** — the only subpackage is `mob/ability/`. The module is 16 Java files (~1,500 lines).

The module does **not** implement natural spawning or custom AI. Mobs are spawned on demand by four callers: the `/mob` command, zone spawners, the slayer boss hook, and script `spawn_mob` events (plus fishing sea creatures). Combat is entirely driven by the **combat module** (`DamageCalculator`, `CombatListener`), which reads `MobDefinition` values out of the registry via the entity's PDC tag.

Design decisions:

- **Definitions only, no runtime registry of living instances.** `MobManager` holds a `MobRegistry` of `MobDefinition` objects (`MobManager.java:14`) plus a `BossController` (`MobManager.java:18`) that tracks *only* boss mobs at runtime. Non-boss mobs cost nothing to track.
- **Entity identity via PDC.** Every spawned mob gets `Keys.MOB_ID_KEY` (`valmora:valmora_mob_id`, `Keys.java:47`) storing the definition ID (`MobFactory.java:27`). All other modules (combat, quest, slayer, zone) recognize "a Valmora mob" purely by this tag.
- **Shared mechanic system.** Boss abilities parse mechanics through the shared `MechanicParser`/`MechanicRegistry` from the item/ability module (`MobDefinitionParser.java:217-227`), so a boss ability and an item ability use identical `type`/`params` YAML.
- **Level scaling is linear.** `getScaledDamage() = baseDamage + (level - 1)` and `getXpReward() = baseXp * level` (`MobDefinition.java:120-126`).

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/mob/
├── MobManager.java          # ReloadableModule lifecycle + public facade (spawn, visuals, definition lookup)
├── MobRegistry.java         # Registry<MobDefinition> wrapper over SimpleRegistry
├── MobLoader.java           # YamlLoader wrapper — loads plugins/Valmora/mobs/*.yml
├── MobDefinition.java       # Immutable mob definition + Builder
├── MobDefinitionParser.java # YAML ConfigurationSection → MobDefinition (LoadResult)
├── MobFactory.java          # Spawns entities: PDC, attributes, flags, equipment, nameplate, boss registration
├── MobDeathListener.java    # EntityDeathEvent rewards + loot; EntityCombustEvent sun-burn guard
├── MobCommand.java          # /mob spawn|list|reload|info (TabExecutor, wired in Valmora.java)
├── MobCategory.java         # Enum of 10 category tags
├── LootTable.java           # Ordered list of LootEntry
├── LootEntry.java           # item + amount range + chance + luck flag
├── BossController.java      # Runtime driver for tracked boss instances (abilities + boss bar)
├── BossBarConfig.java       # Immutable boss-bar color/overlay/range config
└── ability/
    ├── MobAbility.java          # Immutable boss ability (trigger, timers, announce, mechanics)
    ├── MobAbilityTrigger.java   # Enum: ON_TIMER/ON_HEALTH/ON_ATTACK/ON_DAMAGED/ON_SPAWN/ON_DEATH
    └── MobAbilityParser.java    # abilities: YAML → List<MobAbility> via shared MechanicParser

src/main/resources/mobs/
├── test_mobs.yml            # test_zombie + test_skeleton (flat-key legacy schema)
├── test_boss.yml            # forge_titan (boss, stats/resistances/abilities) + forge_imp minion
└── shardworks_mobs.yml      # shardworks_cave_guardian + shardworks_crystal_wraith (item-ID loot)
```

Tests (`src/test/java/org/nakii/valmora/module/mob/`):

- `MobDefinitionTest.java` — scaled damage, XP reward, builder defaults (pure unit tests, no server).
- `LootEntryTest.java` — luck-adjusted chance, random amount bounds, luck-affected filtering.

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `MobManager.java`

| Method | Behavior | Lines |
|---|---|---|
| Constructor | Builds `BossController`, `MobFactory`, `MobRegistry`, `MobLoader`, `MobDeathListener` in that order (factory needs the controller; loader needs the registry) | `MobManager.java:20-27` |
| `onEnable()` | Logs start, registers `deathListener` via the plugin manager, calls `mobLoader.loadMobs()`, starts the `BossController` task | `MobManager.java:29-35` |
| `onDisable()` | Stops the boss controller, clears the registry | `MobManager.java:37-42` |
| `getId()` | `"mobs"` | `MobManager.java:45` |
| `getName()` | `"Mob Engine"` | `MobManager.java:50` |
| `spawnMob(def, loc)` | Delegates to `mobFactory.spawnMob` | `MobManager.java:54-56` |
| `updateVisuals(entity)` | Re-applies the nameplate from the entity's PDC mob ID after damage | `MobManager.java:58-60` |
| `getMobDefinition(id)` | `null`-safe registry lookup (`getMob(id).orElse(null)`); `null` for unknown IDs | `MobManager.java:62-65` |

Hot-reload safety: all mutable state (registry contents, boss task) is torn down in `onDisable()` and rebuilt in `onEnable()`. `onDisable()` does **not** unregister the death listener (a minor gap — the listener is registered once in `onEnable` but never unregistered; see [Unfinished Things](#unfinished-things--todos)).

### 3.2 Registry — `MobRegistry.java`

Extends `SimpleRegistry<MobDefinition>` (`MobRegistry.java:8`), so lookups are case-insensitive and stored lowercase (`SimpleRegistry.java:21`). Adds four helpers:

| Method | Behavior | Lines |
|---|---|---|
| `registerMob(def)` | `register(def.getId(), def)` | `MobRegistry.java:12-14` |
| `getMob(id)` | Optional lookup | `MobRegistry.java:16-18` |
| `getMobCount()` | `size()` | `MobRegistry.java:20-22` |
| `getAllMobIds()` | Unmodifiable key set (used by `/mob list` and tab completion) | `MobRegistry.java:24-26` |

### 3.3 Loading — `MobLoader.java`

```java
this.loader = new YamlLoader<>(plugin, "mobs", "mobs");          // folder "mobs", type label "mobs"
loader.load((id, section, filePath) -> MobDefinitionParser.parse(id, section, filePath, plugin.getItemManager()),
            registry::registerMob);
```

(`MobLoader.java:12-24`.) The standard `YamlLoader` flow (`YamlLoader.java:37-73`) scans every `*.yml` in `plugins/Valmora/mobs/`, treats each **top-level key as the mob ID**, parses each section, and registers successes. Parse failures are batched and logged with their file paths (`YamlLoader.java:113-123`). The loader depends on the **item module** (`plugin.getItemManager()`) for equipment and loot item resolution.

The `mobs/` folder is auto-copied from the JAR on first run by `Valmora.saveAllResources()` (`Valmora.java:469-484`), skipping files that already exist so server edits survive.

### 3.4 Definition Model — `MobDefinition.java`

Immutable value object built via a `Builder`. Fields and accessors:

| Field | Type | Default | Accessor | Lines |
|---|---|---|---|---|
| `id` | `String` | (ctor arg) | `getId()` | `MobDefinition.java:14`, `:80` |
| `name` | `String` | `null` | `getName()` | `MobDefinition.java:15`, `:81` |
| `category` | `MobCategory` | `null` | `getCategory()` | `MobDefinition.java:16`, `:82` |
| `entityType` | `EntityType` | `null` | `getEntityType()` | `MobDefinition.java:17`, `:83` |
| `health` | `double` | `0.0` | `getHealth()` | `MobDefinition.java:18`, `:84` |
| `baseDamage` | `double` | `5.0` | `getBaseDamage()` | `MobDefinition.java:19`, `:85` |
| `speed` | `double` | `0.0` | `getSpeed()` | `MobDefinition.java:20`, `:86` |
| `defense` | `double` | `0.0` | `getDefense()` | `MobDefinition.java:22`, `:87` |
| `strength` | `double` | `0.0` | `getStrength()` | `MobDefinition.java:23`, `:88` |
| `critChance` | `double` | `0.0` | `getCritChance()` | `MobDefinition.java:24`, `:89` |
| `critDamage` | `double` | `0.0` | `getCritDamage()` | `MobDefinition.java:25`, `:90` |
| `resistances` | `Map<DamageType, Double>` | empty `EnumMap` | `getResistances()` / `getResistance(type)` | `MobDefinition.java:27`, `:91`, `:111-113` |
| `armor` | `ItemStack[4]` | `null` | `getArmor()` | `MobDefinition.java:28`, `:92` |
| `weapon` | `ItemStack` | `null` | `getWeapon()` | `MobDefinition.java:29`, `:93` |
| `offHand` | `ItemStack` | `null` | `getOffHand()` | `MobDefinition.java:30`, `:94` |
| `level` | `int` | `1` | `getLevel()` | `MobDefinition.java:31`, `:95` |
| `baseXp` | `int` | `2` | `getBaseXp()` | `MobDefinition.java:32`, `:96` |
| `goldReward` | `int` | `0` | `getGoldReward()` | `MobDefinition.java:33`, `:97` |
| `damageType` | `DamageType` | `MELEE` | `getDamageType()` | `MobDefinition.java:34`, `:98` |
| `lootTable` | `LootTable` | `LootTable.empty()` | `getLootTable()` | `MobDefinition.java:35`, `:99` |
| `abilities` | `List<MobAbility>` | empty list | `getAbilities()` | `MobDefinition.java:37`, `:100` |
| `bossBar` | `BossBarConfig` | `BossBarConfig.disabled()` | `getBossBar()` | `MobDefinition.java:38`, `:101` |
| `knockbackResistance` | `double` | `-1.0` (leave vanilla) | `getKnockbackResistance()` | `MobDefinition.java:40`, `:102` |
| `noAi` | `boolean` | `false` | `isNoAi()` | `MobDefinition.java:41`, `:103` |
| `silent` | `boolean` | `false` | `isSilent()` | `MobDefinition.java:42`, `:104` |
| `glowing` | `boolean` | `false` | `isGlowing()` | `MobDefinition.java:43`, `:105` |
| `persistent` | `boolean` | `false` | `isPersistent()` | `MobDefinition.java:44`, `:106` |
| `baby` | `boolean` | `false` | `isBaby()` | `MobDefinition.java:45`, `:107` |
| `preventSunBurn` | `boolean` | `false` | `isPreventSunBurn()` | `MobDefinition.java:46`, `:108` |

Derived values:

| Method | Formula | Lines |
|---|---|---|
| `getScaledDamage()` | `baseDamage + (level - 1)` — the value actually written to `ATTACK_DAMAGE` | `MobDefinition.java:120-122` |
| `getXpReward()` | `baseXp * level` — combat XP granted to the killer | `MobDefinition.java:124-126` |
| `getResistance(type)` | `resistances.getOrDefault(type, 0.0)` — 0 when not configured | `MobDefinition.java:111-113` |
| `isBoss()` | `true` if the mob has ≥1 ability **or** an enabled boss bar | `MobDefinition.java:115-118` |

Builder defaults are set in the `Builder(String id)` constructor (`MobDefinition.java:159-167`): `baseDamage = 5.0`, `level = 1`, `baseXp = 2`, `goldReward = 0`, `damageType = MELEE`, `lootTable = LootTable.empty()`, `bossBar = BossBarConfig.disabled()`, `knockbackResistance = -1.0`. Note `name`, `category`, `entityType`, and `health` have **no default** — the parser guarantees `category` and `type`, but `name` and `health` can be left unset (see [Unfinished Things](#unfinished-things--todos)).

### 3.5 Parsing — `MobDefinitionParser.java`

Static `parse(sectionId, section, fileName, itemManager)` returns a `LoadResult<MobDefinition, String>` (`MobDefinitionParser.java:23`). Failure paths return `LoadResult.failure(...)` with a `[fileName] In mob '<id>': <reason>` message. Validation summary:

- `category` — **required**; parsed via `MobCategory.valueOf(categoryStr.toUpperCase())` (`MobDefinitionParser.java:32-42`).
- `type` — **required**; parsed via `EntityType.valueOf(typeStr.toUpperCase())` (`MobDefinitionParser.java:45-55`).
- Stats are read from the nested `stats:` block first, then flat legacy keys (`health`, `base-damage`, `speed`, `defense`) override them (`MobDefinitionParser.java:57-81`).
- `resistances` — each key parsed as `DamageType.valueOf(key.toUpperCase())`; value clamped to `[0.0, 1.0]` (`MobDefinitionParser.java:84-98`).
- `equipment` — `helmet`→armor[3], `chestplate`→armor[2], `leggings`→armor[1], `boots`→armor[0], `main-hand`, `off-hand`; values resolved through `itemManager.createItemStack(...)` (`MobDefinitionParser.java:138-177`).
- `loot-table.drops` — list of maps, each parsed by `parseLootEntry` (`MobDefinitionParser.java:244-268`). Drop `item` is first tried as a `Material`, then falls back to `itemManager.createItemStack(id)`; `min-amount` (default 1), `max-amount` (defaults to `min-amount`), `chance` (default 1.0), `luck-affected` (default false).
- `boss-bar` — only parsed if `enabled: true`; `color` default `RED`, `style` default `PROGRESS`, `range` default `40.0` (`MobDefinitionParser.java:201-215`). Overlay names are normalized by `parseOverlay` (`MobDefinitionParser.java:233-242`) which accepts both Adventure names (`PROGRESS`, `NOTCHED_6/10/12/20`) and Bukkit-style aliases (`SOLID`, `SEGMENTED_6/10/12/20`).
- `abilities` — resolved through `ValmoraAPI.getInstance().getAbilityManager().getMechanicRegistry()` and parsed by `MobAbilityParser`; a `MobAbilityParser.ParseException` becomes a load failure (`MobDefinitionParser.java:217-227`).

### 3.6 Spawning — `MobFactory.java`

`spawnMob(def, location)` (`MobFactory.java:92-102`) performs a raw `world.spawnEntity(location, def.getEntityType())` (not the Paper consumer form — the entity is half-initialized for one tick), then runs three passes:

1. **`applyData`** (`MobFactory.java:25-49`) — writes the PDC `MOB_ID_KEY`; sets `MAX_HEALTH` attribute base to `def.getHealth()` and sets the entity's current health; sets `ATTACK_DAMAGE` to `def.getScaledDamage()`; sets `MOVEMENT_SPEED` to `def.getSpeed()`; then `applyFlags`.
2. **`applyFlags`** (`MobFactory.java:51-66`) — `KNOCKBACK_RESISTANCE` only when `>= 0`; `setAI(false)` for `no-ai`; `setSilent(true)`; `setGlowing(true)`; `persistent` → `setRemoveWhenFarAway(false)` + `Mob.setPersistent(true)`; `baby` → `Ageable.setBaby()` (no randomized baby chance).
3. **`applyEquipment`** (`MobFactory.java:68-81`) — `setArmorContents`, `setItemInMainHand`, `setItemInOffHand`.
4. **`applyVisuals`** (`MobFactory.java:84-90`) — builds the nameplate string and calls `entity.customName(...)` + `setCustomNameVisible(true)`. Nameplate template:

   ```
   <gray>[<white>Lv.<level></white>]</gray><white><Name> <currentHP></white><gray>/</gray><white><maxHP></white><red>❤</red>
   ```

   `Name` is `Formatter.capitalize(def.getName())` — see the NPE caveat below.

Finally, if `def.isBoss()` the entity is handed to `bossController.register(entity, def)` (`MobFactory.java:98-100`).

`applyVisuals` is also called externally by `DamageApplier` after every mob hit (`DamageApplier.java:44`) so the live HP on the nameplate stays in sync.

### 3.7 Boss Runtime — `BossController.java`

A single main-thread repeating task (every `TICK_PERIOD = 10L` ticks, `BossController.java:37`) drives all tracked bosses. Only `MobDefinition.isBoss()` mobs are tracked (`register` early-returns otherwise, `BossController.java:67`).

| Method | Behavior | Lines |
|---|---|---|
| `start()` / `stop()` | Starts/cancels the repeating task; `stop()` hides every bar and clears instances | `BossController.java:49-63` |
| `register(entity, def)` | Creates a `BossInstance`, builds an Adventure `BossBar` if enabled, fires `ON_SPAWN` abilities | `BossController.java:66-86` |
| `unregister(uuid)` / `isTracked(uuid)` | Remove (hiding bar) / membership check | `BossController.java:88-97` |
| `onAttack(boss, target)` | Fires `ON_ATTACK` abilities (called from `CombatListener` when the boss damages someone) | `BossController.java:100-102` |
| `onDamaged(boss, attacker)` | Fires `ON_DAMAGED` abilities (called when the boss is hit) | `BossController.java:104-107` |
| `onDeath(boss)` | Fires `ON_DEATH` abilities then unregisters (called from `MobDeathListener`) | `BossController.java:110-113` |

**The tick** (`BossController.java:125-165`) iterates instances, removes dead/invalid entities, accumulates `ticksAlive`, and for each ability:

- `ON_TIMER` — fires when `ticksAlive - lastFired >= intervalTicks` **and** `Math.random() < chance` (`BossController.java:141-149`).
- `ON_HEALTH` — fires once (tracked in `firedHealthAbilities`) when `healthPercent <= healthPercent` (`BossController.java:150-158`).
- Other triggers are skipped here (event-driven elsewhere).
- Then `updateBar(instance)` refreshes the boss bar.

**`fire(instance, ability, providedTarget)`** (`BossController.java:171-196`) enforces the per-ability cooldown (millisecond wall-clock), resolves a target via `findNearestPlayer` within `targetRange` when none is provided, broadcasts `announce` (MiniMessage) to all players within `ANNOUNCE_RADIUS = 40.0` blocks (`BossController.java:39`), and executes each `ConfiguredMechanic` via `mechanic.execute(instance.entity, target)` (`BossController.java:192-194`) — the boss is the caster, target resolution is `null`-safe.

**Boss bar update** (`BossController.java:198-220`) shows/hides the Adventure `BossBar` per player based on `range` and tracks viewers per instance (`barViewers`). `hideBar` (`BossController.java:222-229`) hides it from every viewer on unregister/death.

`BossInstance` (`BossController.java:269-283`) holds per-entity state: entity, definition, `BossBar`, `ticksAlive`, `lastFiredTick` map, `firedHealthAbilities` set, `cooldownExpiry` map (ms), and `barViewers` set.

### 3.8 Death, Rewards & Loot — `MobDeathListener.java`

`onMobDeath(EntityDeathEvent)` (`MobDeathListener.java:30-85`):

1. Reads the PDC mob ID; returns early if the entity is not a Valmora mob or the definition is missing.
2. If the entity is a tracked boss, calls `bossController.onDeath(entity)` (fires `ON_DEATH` abilities + unregisters).
3. If there is a player killer, resolves the killer's active `ValmoraProfile`, reads the **Luck** stat (`StatManager.getStat(sys.getLuck())`), grants `definition.getXpReward()` combat XP via `profile.getSkillManager().addXp("combat", xp, killer)` (`MobDeathListener.java:62-63`), and pays `goldReward` coins via `plugin.getEconomy().addCoins(killer, goldReward)` when `> 0` (`MobDeathListener.java:65-68`).
4. Rolls the loot table: for each entry, `effectiveChance = luckAffected ? entry.getEffectiveChance(luck) : entry.getChance()`; on success, `event.getDrops().add(entry.createDroppedItem())` (`MobDeathListener.java:73-84`).

`onMobCombust(EntityCombustEvent)` at `HIGH` priority, `ignoreCancelled=true` (`MobDeathListener.java:88-105`) cancels **ambient** combustion (sunlight) for mobs with `prevent-sun-burn: true`. It deliberately ignores `EntityCombustByBlockEvent` / `EntityCombustByEntityEvent` so lava/fire sources still burn (`MobDeathListener.java:91-93`).

### 3.9 Command — `MobCommand.java`

`TabExecutor` for `/mob`, instantiated in `Valmora.onEnable()` (`Valmora.java:238`). Requires `valmora.admin` (plugin.yml:20). Subcommands (`MobCommand.java:42-115`):

| Subcommand | Behavior | Lines |
|---|---|---|
| `spawn <mob> [player]` | Looks up the definition, resolves target player (self if omitted), `mobManager.spawnMob(def, target.getLocation())` | `MobCommand.java:43-67` |
| `list` | Prints all registered mob IDs | `MobCommand.java:69-77` |
| `reload` | `ValmoraAPI.getInstance().getModuleManager().reloadModule("mobs")` — disables/enables just the mob module | `MobCommand.java:79-82` |
| `info` | Raycast-targeted entity within 10 blocks (`player.getTargetEntity(10, false)`); prints ID, name, entity type, level | `MobCommand.java:84-110` |

Tab completion covers subcommands, then mob IDs, then online player names for `spawn` (`MobCommand.java:118-138`).

### 3.10 Ability Types — `ability/` subpackage

**`MobAbilityTrigger`** (`MobAbilityTrigger.java:6-19`): `ON_TIMER`, `ON_HEALTH`, `ON_ATTACK`, `ON_DAMAGED`, `ON_SPAWN`, `ON_DEATH`.

**`MobAbility`** (`MobAbility.java:13-46`): immutable; fields `id`, `name`, `trigger` (default `ON_TIMER`), `intervalTicks` (default 100), `chance` (default 1.0), `healthPercent` (default 50.0), `targetRange` (default 0.0), `cooldownSeconds` (default 0.0), `announce` (default null), `mechanics` (default empty).

**`MobAbilityParser`** (`MobAbilityParser.java:27-69`): iterates the `abilities:` section keys; throws `ParseException` on an invalid trigger or an unknown mechanic type. Mechanic parsing delegates to the shared `MechanicParser.parse(abSec.getMapList("mechanics"), registry)` (`MobAbilityParser.java:57`), which converts each `{type, params}` map into a `ConfiguredMechanic` (`MechanicParser.java:33-56`). Mechanics currently registered (from `AbilityManager.registerMechanics()`, `AbilityManager.java:50-65`): `DAMAGE`, `HEAL`, `APPLY_EFFECT`, `SCRIPT`, `MODIFY_STAT`, `TELEPORT`, `PUSH_ENTITIES`, `PULL_ENTITIES`, `GIVE_COINS`, `TAKE_COINS`, `IGNITE`, `LAUNCH_PLAYER`, `LAUNCH_PROJECTILE`, `AOE_MINE`.

### 3.11 Loot — `LootTable.java` / `LootEntry.java`

`LootTable` holds an ordered `List<LootEntry>` (`LootTable.java:9-15`); `empty()` returns a no-entry table (`LootTable.java:27-29`). `getLuckAffectedEntries()` (`LootTable.java:17-25`) is currently **unused** by the death listener (which iterates `getEntries()` directly).

`LootEntry` (`LootEntry.java:12-58`): `getEffectiveChance(luck)` = `chance + (luck/100.0) * chance` when luck-affected and `luck > 0` (`LootEntry.java:40-45`); `getRandomAmount()` uniform between min/max (`LootEntry.java:47-52`); `createDroppedItem()` clones and sets a random amount (`LootEntry.java:54-58`).

---

## Configuration (YAML)

Folder: `plugins/Valmora/mobs/` (any number of `*.yml`; each top-level key becomes the mob ID).

### Top-level mob fields

| Key | Type | Required | Default | Notes |
|---|---|---|---|---|
| `<mob-id>` | map key | **yes** | — | Lowercased by the registry at registration (`SimpleRegistry.java:21`). |
| `name` | String (MiniMessage) | no | *(none)* | Shown on the nameplate (capitalized). Omitting it triggers a `NullPointerException` at spawn — see [Unfinished Things](#unfinished-things--todos). |
| `category` | String enum | **yes** | — | One of `UNDEAD`, `ENDER`, `NETHER`, `BEAST`, `AQUATIC`, `ARTHROPOD`, `ILLAGER`, `GOLEM`, `BOSS`, `OTHER` (`MobCategory.java:3-13`). |
| `type` | String enum | **yes** | — | Any Bukkit `EntityType` name, e.g. `ZOMBIE`, `IRON_GOLEM`, `VEX`. |
| `level` | Integer | no | `1` | Shown on the nameplate; feeds `getScaledDamage()` and `getXpReward()`. |
| `base-xp` | Integer | no | `2` | Combat XP **per level** — actual reward is `base-xp * level`. |
| `gold-reward` | Integer | no | `0` | Coins paid to the killer on death (0 = none). |
| `damage-type` | String enum | no | `MELEE` | One of `DamageType` (`MELEE`, `PROJECTILE`, `FALL`, `DROWNING`, `FIRE`, `LAVA`, `MAGIC`, `VOID`, `POISON`, `WITHER`, `EXPLOSION`). **Parsed but currently unused by any combat logic.** |
| `knockback-resistance` | Double | no | `-1.0` | Attribute base value; `-1`/absent leaves the vanilla default. |
| `no-ai` | Boolean | no | `false` | `entity.setAI(false)`. |
| `silent` | Boolean | no | `false` | `entity.setSilent(true)`. |
| `glowing` | Boolean | no | `false` | `entity.setGlowing(true)` (glowing outline). |
| `persistent` | Boolean | no | `false` | `setRemoveWhenFarAway(false)` + `Mob.setPersistent(true)` — mob never despawns. |
| `baby` | Boolean | no | `false` | `Ageable.setBaby()` for ageable entity types. |
| `prevent-sun-burn` | Boolean | no | `false` | Cancels **ambient** (sunlight) combustion; lava/block/entity fire still burns. |

### `stats:` block (canonical) and flat legacy keys

Values inside `stats:` (`MobDefinitionParser.java:59-68`); flat keys (`health`, `base-damage`, `speed`, `defense`) override them when both are present (`MobDefinitionParser.java:69-81`).

| Key | Type | Default | Notes |
|---|---|---|---|
| `stats.health` / `health` | Double | `0.0` | Max HP + starting HP. **No default — always set it.** |
| `stats.damage` / `base-damage` | Double | `5.0` | Base attack damage before level scaling (`getScaledDamage`). |
| `stats.speed` / `speed` | Double | `0.0` | Vanilla `MOVEMENT_SPEED` attribute base (0.25 ≈ normal walk). |
| `stats.defense` / `defense` | Double | `0.0` | Feeds the player defense formula `100/(defense+100)` when the mob is the victim. |
| `stats.strength` | Double | `0.0` | Feeds `damage × (1 + strength/100)` when the mob is the attacker. |
| `stats.crit-chance` | Double | `0.0` | Mob critical hit chance (%). |
| `stats.crit-damage` | Double | `0.0` | Mob critical damage bonus (%). |

### `resistances:` block

Map of `DamageType` (uppercase key, e.g. `FIRE`, `LAVA`, `EXPLOSION`, `MAGIC`) → fraction `0.0–1.0` (`MobDefinitionParser.java:84-98`). Applied in `DamageCalculator` as `mitigated *= (1.0 - resistance)`; `1.0` means full immunity (`DamageCalculator.java:113-120`). `FIRE`/`LAVA` immunity also clears `fireTicks` after the hit (`CombatListener.java:93-96`).

### `equipment:` block

Values are either a Bukkit `Material` name or a **Valmora item ID** resolved via `ItemManager.createItemStack` (`MobDefinitionParser.java:138-177`).

| Key | Armor index | Notes |
|---|---|---|
| `equipment.helmet` | 3 | |
| `equipment.chestplate` | 2 | |
| `equipment.leggings` | 1 | |
| `equipment.boots` | 0 | |
| `equipment.main-hand` | — | Weapon. |
| `equipment.off-hand` | — | Off-hand. |

### `loot-table:` block

`loot-table.drops` is a **list** of drop maps (`MobDefinitionParser.java:179-198`, `parseLootEntry` at `:244-268`). Note: `loot-table` without a `drops` key is silently ignored.

| Key | Type | Default | Notes |
|---|---|---|---|
| `item` | String | **required** | `Material` name first, else a Valmora item ID. If neither resolves, the entry is skipped. |
| `min-amount` | Integer | `1` | |
| `max-amount` | Integer | `= min-amount` | |
| `chance` | Double | `1.0` | Rolled per entry with `Math.random() < chance`. |
| `luck-affected` | Boolean | `false` | If true, chance becomes `chance + (luck/100)*chance` using the killer's Luck stat. |

### `boss-bar:` block

Only parsed when `enabled: true` (`MobDefinitionParser.java:201-215`).

| Key | Type | Default | Notes |
|---|---|---|---|
| `boss-bar.enabled` | Boolean | `false` | Enabling this makes `isBoss()` return true (bar shown to nearby players). |
| `boss-bar.color` | String enum | `RED` | Adventure `BossBar.Color`: `RED`, `BLUE`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE`, `PINK`. |
| `boss-bar.style` | String enum | `PROGRESS` | Adventure overlay name or Bukkit alias: `PROGRESS`/`SOLID`, `NOTCHED_6`/`SEGMENTED_6`, `NOTCHED_10`/`SEGMENTED_10`, `NOTCHED_12`/`SEGMENTED_12`, `NOTCHED_20`/`SEGMENTED_20`. |
| `boss-bar.range` | Double | `40.0` | Blocks within which the bar is shown to players. |

### `abilities:` block

Map of ability-ID → ability config (`MobAbilityParser.java:27-69`). Any ability makes `isBoss()` true.

| Key | Type | Default | Notes |
|---|---|---|---|
| `<ability-id>` | map key | — | Used in cooldown/once-fired tracking. |
| `name` | String | ability id | Display name. |
| `trigger` | String enum | `ON_TIMER` | `ON_TIMER`, `ON_HEALTH`, `ON_ATTACK`, `ON_DAMAGED`, `ON_SPAWN`, `ON_DEATH`. |
| `interval` | Integer (ticks) | `100` | `ON_TIMER` only: min ticks between eligible firings. |
| `chance` | Double | `1.0` | `ON_TIMER` only: per-eligible-tick roll chance. |
| `health-percent` | Double | `50.0` | `ON_HEALTH` only: fires once when HP drops to/below this percent. |
| `target-range` | Double | `0.0` | `0` = no target resolution. Else nearest non-spectator player within this range. |
| `cooldown` | Double (seconds) | `0.0` | Wall-clock cooldown between firings. |
| `announce` | String (MiniMessage) | *(none)* | Broadcast to players within 40 blocks on fire. |
| `mechanics` | List | empty | List of `{type: <ID>, params: {…}}` maps; types are the shared item-ability mechanics (see §3.10). |

### Full example (from shipped files)

`forge_titan` (`src/main/resources/mobs/test_boss.yml:3-73`) demonstrates everything: nested `stats`, `resistances`, `knockback-resistance`, `persistent`, `glowing`, `boss-bar`, and three abilities (`ground-slam` ON_TIMER with `DAMAGE` + `APPLY_EFFECT`, `enrage` ON_HEALTH with a `SCRIPT` that runs `spawn_mob forge_imp 3 radius:4`, `death-blast` ON_DEATH with a `DAMAGE` mechanic). `shardworks_cave_guardian` (`shardworks_mobs.yml:5-18`) shows loot using Valmora item IDs (`raw_ferrite`).

---

## Data Model / Persistence

- **No database usage.** The mob module never touches `DataStore`. All mob state is in-memory definitions (`MobRegistry`) plus per-entity PDC tags.
- **Entity PDC tags written by this module:**
  - `Keys.MOB_ID_KEY` → `valmora:valmora_mob_id` (`Keys.java:47`) — definition ID (`MobFactory.java:27`).
  - Zone spawners additionally write `Keys.MOB_HOME_KEY` (`valmora:mob_home`, `Keys.java:61`) — `x,y,z,wanderRadius,worldName` — used by the zone behavior task (`ZoneManager.java:145-148`).
  - The slayer module writes `Keys.SLAYER_BOSS_KEY` onto a spawned boss to bind it to a slayer task (`SlayerListener.java:116`).
- **Registry lifecycle:** populated in `MobLoader.loadMobs()` (`onEnable`), cleared in `MobManager.onDisable()` (`MobManager.java:41`). Hot-reload via `/mob reload` or `/valmora reload` re-runs both.
- **Spawned mobs are not persisted.** They despawn on chunk unload unless `persistent: true`, and are not restored on restart.

---

## API Exposed

`ValmoraAPI.getMobManager()` (`ValmoraAPI.java:25`, implemented `Valmora.java:303-305`) returns the concrete `MobManager`. Public surface:

| Method | Signature | Purpose |
|---|---|---|
| `spawnMob` | `LivingEntity spawnMob(MobDefinition, Location)` | Spawn a configured mob at a location (applies data/equipment/visuals, registers bosses). |
| `updateVisuals` | `void updateVisuals(LivingEntity)` | Refresh the HP nameplate from the entity's PDC ID. |
| `getMobDefinition` | `MobDefinition getMobDefinition(String id)` | Case-insensitive lookup; `null` if unknown. |
| `getMobRegistry` | `MobRegistry` | Registry with `getMob`, `getMobCount`, `getAllMobIds`. |
| `getMobFactory` | `MobFactory` | Direct spawn/apply access. |
| `getMobLoader` | `MobLoader` | Re-run YAML loading. |
| `getBossController` | `BossController` | `isTracked`, `onAttack`, `onDamaged`, `onDeath` hooks for the combat module. |

The `ReloadableModule` ID `"mobs"` is registered at `Valmora.java:198` and reloadable in isolation via `ModuleManager.reloadModule("mobs")` (`ModuleManager.java:87-98`).

---

## Dependencies & Consumers

### Load order (why it sits where it does)

Registered after `script`, `time`, `stat`, `player`, `economy`, `ui`, `ability`, `item` and before `skill`, `combat`, `gui` (`Valmora.java:188-210`; order also documented in `MODULE_DEVELOPMENT.md:504`). It depends on the modules loaded before it and is consumed by modules after it.

### Dependencies (loaded before `mobs`)

| Dependency | Why |
|---|---|
| `item` (`ItemManager`) | Equipment and loot items resolved by ID (`MobLoader.java:21`, `MobDefinitionParser.java:23`). |
| `ability` (`AbilityManager`) | `getMechanicRegistry()` used to parse boss abilities at load time (`MobDefinitionParser.java:220`). |
| `combat` (`DamageType`) | `MobDefinition` embeds `org.nakii.valmora.module.combat.DamageType` (`MobDefinition.java:5`). |
| `economy` (`EconomyService`) | `getEconomy().addCoins(...)` on gold rewards (`MobDeathListener.java:67`). |
| `player`/`stat` | Death rewards read the killer's profile, Luck stat, and combat skill XP (`MobDeathListener.java:54-63`). |

### Consumers (loaded after `mobs`)

| Consumer | How it uses the mob module |
|---|---|
| `combat` (`DamageCalculator`) | Reads `MobDefinition` for mob attackers (scaled damage, strength, crit) and mob victims (defense, resistances) via `mobOf()` PDC lookup (`DamageCalculator.java:42-64`, `:112-120`, `:201-209`). |
| `combat` (`CombatListener`) | Fires `BossController.onAttack`/`onDamaged` when a tracked boss deals/takes damage (`CombatListener.java:61-68`); applies fire/lava immunity cleanup (`CombatListener.java:93-96`). |
| `combat` (`DamageApplier`) | Calls `updateVisuals` on every mob hit to refresh the nameplate HP (`DamageApplier.java:42-45`). |
| `zone` (`ZoneManager`) | Spawns mobs from configured zone spawners every interval, tags them with `MOB_HOME_KEY`, counts alive mobs by PDC ID (`ZoneManager.java:123-165`). |
| `zone` (`ZoneCommand`) | Validates mob IDs when adding a spawner and tab-completes mob IDs (`ZoneCommand.java:266-269`, `:354`). |
| `fishing` (`FishingManager`) | Spawns a sea-creature mob from a fishing table (`FishingManager.java:26-30`). |
| `slayer` (`SlayerListener`) | Spawns the slayer boss mob and tags it with `SLAYER_BOSS_KEY` (`SlayerListener.java:109-116`). |
| `quest` (`QuestListener`) | Kills and interactions target a custom mob ID (PDC) falling back to the entity type name (`QuestListener.java:86-92`, `:433-441`). |
| `script` (`SpawnMobEventFactory`) | DSL `spawn_mob <id> [count] radius:<r>` uses `getMobDefinition` + `spawnMob` (`SpawnMobEventFactory.java:29-76`). |
| command layer | `/mob` executor (`Valmora.java:238`). |

---

## Unfinished Things / TODOs

- **`name` is optional in YAML but required at spawn time.** `MobFactory.applyVisuals` calls `Formatter.capitalize(definition.getName())` unguarded (`MobFactory.java:87`); with `name` omitted (`Formatter.capitalize(null)` → NPE, `Formatter.java:21-23`), every spawn of that mob throws. The parser should default `name` to the mob ID (as `BossController.register` already does defensively at `BossController.java:73`).
- **`health` has no default** (builder leaves `0.0`), so an unset `stats.health` yields a mob with 0 max HP.
- **`damage-type` is dead config.** Parsed (`MobDefinitionParser.java:126-135`) and stored (`MobDefinition.java:98`) but never read anywhere in `src/main/java`. Boss `DAMAGE` mechanics, however, specify their own `params.type`.
- **`getDamageType()` unused** (grep across `src/main/java` confirms no consumer) — the `damage-type` YAML field currently documents a no-op.
- **`LootTable.getLuckAffectedEntries()` is unused** — the death listener iterates all entries (`MobDeathListener.java:76`).
- **Death listener is never unregistered.** `MobManager.onDisable()` clears the registry and stops the boss controller but never calls `HandlerList.unregisterAll(deathListener)`, unlike the pattern in `docs/MODULE_DEVELOPMENT.md` §7. After a `/mob reload`, a second listener is registered, so `EntityDeathEvent`/`EntityCombustEvent` handlers may run twice.
- **`spawnMob` uses `world.spawnEntity(...)` then configures**, not the Paper consumer form (`MobFactory.java:93`) — the entity ticks for one frame half-initialized (AGENTS.md §11.7 prefers the consumer overload).
- **No custom AI / behaviors.** No pathfinding goals, target selection, aggro range, leash, or wander logic lives in this module. Zone spawners stamp a `MOB_HOME_KEY` for the zone module's behavior task, but the mob module itself applies none.
- **No natural spawning.** Mobs only exist when explicitly spawned by command/zone/script/slayer/fishing.
- **No randomized baby chance** — `baby` is binary only.
- **`prevent-sun-burn` only handles the ambient combust path** (`MobDeathListener.java:89-105`); it does not suppress `EntityCombustByBlockEvent`/`EntityCombustByEntityEvent` by design (so lava/ignite still works), but there is no config for "immune to all fire".
- **`MobDefinition.isBoss()` makes any ability-holder a boss** — including a single harmless `ON_SPAWN` ability — which still costs a `BossController` slot and a per-tick iteration.
- **Docs drift:** `docs/VALMORA_DOCUMENTATION.md` §24 documents an older schema (flat `health`/`damage`/`speed`, no `stats:` block, no `resistances`/`boss-bar`/`abilities`/`gold-reward`/`base-xp`), and its nameplate example shows the raw mob ID where the code shows the capitalized display name (`MobFactory.java:87`). The code is authoritative.

---

## Possible Improvements / Changes

- **Default `name` to the mob ID** in the parser and **default `health` to a sane value** (e.g. 20.0 per the old docs) to eliminate the two silent-`null`/zero traps.
- **Unregister the death listener in `onDisable()`** to make hot-reload fully idempotent (AGENTS.md §6.2).
- **Use the Paper spawn-consumer** (`world.spawn(location, clazz, entity -> ...)`) in `MobFactory.spawnMob` to avoid the one-tick half-initialized entity.
- **Consume `damage-type`** in combat (e.g. mob-vs-player attack classification) or remove the field to avoid confusion.
- **Wire `LootTable.getLuckAffectedEntries()`** into the death listener, or drop the unused method.
- **Custom behaviors via the Paper `Pathfinder` API** (AGENTS.md §11.2): aggro radius, leash range, wander radius, flee-on-hurt, summon-on-death. The zone module's `MOB_HOME_KEY` mechanism is a candidate to move into the mob module proper.
- **Natural spawning** integration (spawner configs, spawn rules per world/zone) so mobs populate the world without explicit scripted calls.
- **Instance persistence** — persist boss HP/state so a half-killed boss survives a restart, or at minimum persist which boss mobs are alive.
- **Per-player Loot/strength scaling of rewards** beyond the current flat luck roll, and a configurable loot reroll/cap.
- **Boss phase scripting** — the ON_HEALTH trigger fires once per ability; multi-threshold phases would need a per-boss phase counter.
- **Entity scheduler usage** for boss tasks (`entity.getScheduler()` per AGENTS.md §11.13) would make timed abilities safe across chunk unloads rather than relying on the global task + `isValid()` pruning.
