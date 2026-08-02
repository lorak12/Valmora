# Stat Module — Design & Code

> **Module ID:** `stats` | **Package:** `org.nakii.valmora.module.stat`
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

The Stat Module is the **foundational stat aggregation system** of Valmora. Every numeric player stat — Health, Damage, Strength, Defense, Crit Chance, Speed, Mining Fortune, Breaking Power, and many more — is a **data-driven definition** rather than a hardcoded enum. The module provides:

- **Stat definitions** — loaded from `stats/*.yml` into a case-insensitive `StatRegistry` (`StatLoader.java:24-46`). Each definition carries a display name, default value, optional max cap, color, GUI icon, description, a "pool" flag, and an optional vanilla-attribute binding.
- **Item stat (de)serialization** — stats are stored on items as a nested `PersistentDataContainer` under `Keys.STATS_CONTAINER_KEY` (`StatModule.java:104-114`). This is the single source of truth for gear stats consumed by the aggregation pipeline.
- **Vanilla attribute mapping** — a stat with `vanilla-attribute` is pushed onto the player's Bukkit `Attribute` on every recalculation (`StatModule.java:71-99`). Currently wired for `speed → movement_speed` and `mining_speed → block_break_speed`.
- **Recalculation triggers** — a `PlayerListener` hooks join, respawn, armor/off-hand clicks, drags, held-slot changes, hand swaps, and armor changes, then schedules a 1-tick-delayed `recalculateStats(player)` (`PlayerListener.java:39-47`).
- **SystemStat aliasing** — `SystemStats` maps the engine's *logical* roles (health, mana, damage, luck, mining fortune, …) to the actual stat ids configured in `config.yml` under `combat:` and `mining:` (`SystemStats.java:45-63`), so a server can rename/replace a core stat without touching engine code.

Crucially, the module does **not** own the per-player stat state. The mutable `StatManager` lives on `ValmoraProfile` (`ValmoraProfile.java:19`, `:58-60`); the stat module provides definitions, serialization, mapping, and the recalculation algorithm, while the profile holds the numbers. This mirrors the "mini-plugin wired through a shared API" architecture described in `AGENTS.md` §1.

The module registers **third** in `Valmora.java` (`script → time → stat → player → …`; `Valmora.java:188-192`), before any consumer that reads stats, and exposes three accessors on `ValmoraAPI`: `getStatModule()`, `getStatRegistry()`, `getSystemStats()` (`ValmoraAPI.java:27-31`).

---

## 2. Code Structure

```
src/main/java/org/nakii/valmora/module/stat/
├── StatModule.java       # ReloadableModule lifecycle; item-stat PDC I/O; vanilla attribute mapping; SystemStats holder
├── StatRegistry.java     # Case-insensitive registry of StatDefinition
├── StatManager.java      # Per-profile aggregation: base vs effective stats, recalculateStats pipeline
├── SystemStats.java      # config.yml → logical-stat-id mapping (combat.* / mining.*)
├── StatCommand.java      # /stat list|add|remove command (TabExecutor)
├── StatLoader.java       # Loads stats/*.yml into the StatRegistry
├── StatDefinition.java   # Immutable stat definition + display formatting
└── PlayerListener.java   # Recalc triggers (join/respawn/equip/held/swap) + vanilla regen cancellation
```

Supporting classes owned by **other** packages that the module depends on:

```
src/main/java/org/nakii/valmora/util/Keys.java
└── STATS_CONTAINER_KEY           # PDC key for the nested item-stats container (Keys.java:46)

src/main/resources/stats/core.yml # Ships the default 28 stat definitions (copied to plugins/Valmora/stats/)
src/main/resources/config.yml     # combat: / mining: SystemStats mapping (config.yml:90-110)
src/main/resources/guis/stats.yml # Stats GUI driven by $player.stat.list$ (guis/stats.yml:21-88)
```

Per `AGENTS.md` §3, the module keeps the `XModule` / `XListener` naming convention; the `XRegistry` / `XLoader` pair is `StatRegistry` / `StatLoader`.

---

## 3. Architecture & Key Classes

### 3.1 Lifecycle — `StatModule`

`StatModule` (`StatModule.java:20`) is a `ReloadableModule` whose module id is `"stats"` (`StatModule.java:53-55`).

- **Constructor** (`StatModule.java:28-30`) stores only the plugin reference; `statRegistry` is a final field initialized inline (`StatModule.java:23`). All other state is created in `onEnable()`, per `AGENTS.md` §6.1.
- **`onEnable()`** (`StatModule.java:33-41`):
  1. Builds a new `StatLoader` and loads `stats/*.yml` into the registry (`StatModule.java:35-36`).
  2. Loads `SystemStats` from the plugin config (`StatModule.java:37` → `SystemStats.load(plugin.getConfig())`).
  3. Registers a fresh `PlayerListener` (`StatModule.java:39-40`).
- **`onDisable()`** (`StatModule.java:44-50`) unregisters the listener via `HandlerList.unregisterAll` and clears the registry (`StatModule.java:49`) — mandatory per `AGENTS.md` §6.2. **Note:** it does *not* touch any `StatManager`; those live on profiles and keep their `baseStats` across a reload.
- **`getStatRegistry()`** / **`getSystemStats()`** accessors (`StatModule.java:57-63`).

### 3.2 Stat definitions — `StatDefinition`

`StatDefinition` (`StatDefinition.java:3`) is an immutable value object with: `id` (lowercased at load time, `StatLoader.java:54`), `displayName`, `defaultValue`, `maxValue`, `color`, `icon`, `description`, `pool`, and `vanillaAttribute` (`StatDefinition.java:5-13`, getters `:29-37`).

- `getFormattedName()` returns `color + displayName` (`StatDefinition.java:39-41`) — MiniMessage tags are resolved downstream by `Formatter.format(...)`.
- `format(double)` renders `<color><displayName>: <+|-><int value>` (`StatDefinition.java:43-45`); values are truncated to `int` (unit-tested in `StatDefinitionTest.java:39-44`).

### 3.3 The registry — `StatRegistry`

`StatRegistry` (`StatRegistry.java:8`) is a small case-insensitive registry backed by a `LinkedHashMap` (preserves YAML order, which the stats GUI relies on):

- `register(def)` — keyed by `def.getId().toLowerCase()` (`StatRegistry.java:12-14`).
- `get(id)` → `Optional<StatDefinition>` (`:16-18`), `values()` → insertion-ordered collection (`:20-22`), `getKeys()` → key set (`:24-26`, used by `/stat` tab-completion), `contains(id)` (`:28-30`), `clear()` (`:32-34`).

### 3.4 The loader — `StatLoader`

`StatLoader` (`StatLoader.java:12`) scans `plugins/Valmora/stats/` for `.yml` files (`StatLoader.java:26-36`) and, for every top-level key, parses a `StatDefinition` (`StatLoader.java:48-70`). Field defaults (applied when the key is absent):

| YAML key | Default | Notes |
| --- | --- | --- |
| `display-name` | the (lowercased) id | `StatLoader.java:55` |
| `default-value` | `0.0` | `StatLoader.java:56` |
| `max-value` | `Double.MAX_VALUE` | only if the key is **present** (`StatLoader.java:57`); absence means "uncapped" |
| `color` | `<white>` | `StatLoader.java:58` |
| `icon` | `PAPER` | a Bukkit `Material` name used by the stats GUI (`StatLoader.java:59`) |
| `description` | `""` | shown in the stats GUI lore (`StatLoader.java:60`) |
| `pool` | `false` | marks a resource pool (currently only used for Health/Mana display, see §3.8) (`StatLoader.java:61`) |
| `vanilla-attribute` | `null` | optional Bukkit attribute name, see §3.6 (`StatLoader.java:62`) |

Missing `stats/` folder or no `.yml` files produce a `[StatLoader]` warning and zero definitions (`StatLoader.java:27-30`, `:32-36`); a per-file count is logged on success (`StatLoader.java:45`).

### 3.5 The per-profile aggregator — `StatManager`

`StatManager` (`StatManager.java:19`) is **owned by `ValmoraProfile`** (`ValmoraProfile.java:19`), not by the stat module. It holds two maps:

- `baseStats` (`StatManager.java:22`) — the profile's *persisted* stat values. Seeded from registry defaults in the constructor (`StatManager.java:24-30`), mutated by `addStat` / `reduceStat` / `setStat` / `resetStat` (`:43-63`), saved via `getSaveData()` (`:32-34`), restored via `loadData()` (`:36-41`, which lowercases keys to absorb legacy uppercase data).
- `effectiveStats` (`StatManager.java:21`) — `baseStats` **plus every transient modifier** (gear, enchants, alchemy, accessories, pets, set bonuses, progression, temporary buffs). Rebuilt from scratch on every `recalculateStats` call. Read via `getStat(id)` (`:70-72`) and `getStatIds()` (`:74-76`).

Note that `addStat`/`reduceStat`/`setStat` each trigger an immediate `recalculateStats(player)` (`:46`, `:52`, `:57`) so the effective values follow the base mutation instantly.

### 3.6 The recalculation pipeline — `StatManager.recalculateStats`

`recalculateStats(Player)` (`StatManager.java:83-193`) rebuilds `effectiveStats` in this exact order:

1. **Reset** — `effectiveStats` is cleared and re-seeded from `baseStats` (`StatManager.java:88-89`).
2. **Purge long potions** — removes any active potion effect with `duration > 72000` ticks (1 hour) to stop passive effects from stacking across reloads (`StatManager.java:91-95`).
3. **Collect gear** — main hand, off-hand, and armor slots `[boots, leggings, chestplate, helmet]` (`StatManager.java:97-101`).
4. **Per-item processing** (`StatManager.java:103-136`):
   - Item stat PDC via `statModule.loadStats(...)` → `addModifier` per stat (`:106-111`).
   - If the item carries an `ITEM_ID_KEY` (a Valmora custom item), run every `PASSIVE`-triggered ability mechanic with `mechanic.execute(player, player)` (`:113-127`). Passive mechanics must apply themselves through `addModifier` (see `ModifyStatMechanic` duration `-1` path, §7).
   - Read Valmora enchantments via `EnchantmentHelper.getEnchantments(item)`; for each, call `enchantDef.getLogic().applyStats(player, level, this)` (`:129-135`).
5. **Alchemy effects** — `alchemyManager.applyEffectsToStats(player, this)` (`StatManager.java:138-141`).
6. **Accessory bag** — every accessory in the profile's `getAccessoryItems()` contributes its PDC stats (`:143-155`).
7. **Pet bonuses** — `petModule.applyPetStats(player, this)` for the summoned pet (`:157-161`).
8. **Armor set bonuses** — `SetBonusService.applyTo(player, this)` (cumulative per set-tier, `:163-164`).
9. **Progression-tree bonuses** — `ProgressionStatService.applyTo(player, this)` (`:166-167`).
10. **Temporary buffs** — `TemporaryStatService.applyTo(uuid, this)` re-applies timed ability buffs (`:169-170`).
11. **Cap to `maxValue`** — for any definition with a finite `maxValue`, clamp the effective value (`:172-178`). This is what keeps `crit_chance` / `luck` / `mining_spread` etc. capped.
12. **Vanilla attribute sync** — `statModule.recalculateAttributes(player, this)` (`:180-181`).
13. **Health/Mana sync** — if the player is **not** in combat, `capToMax` clamps current health/mana to the new max; finally `syncVisualHealth` maps the custom HP onto the 20-heart vanilla bar (`:183-192`).

Because the pipeline runs on the main thread (scheduled by `PlayerListener.recalculate`, `PlayerListener.java:39-47`), `recalculateStats` is safe to touch Bukkit entities/inventories.

### 3.7 Vanilla attribute mapping — `StatModule.recalculateAttributes`

`recalculateAttributes(Player, StatManager)` (`StatModule.java:71-99`) iterates every definition with a `vanilla-attribute`:

- Resolves the attribute key: `NamespacedKey.fromString(def.getVanillaAttribute())`, falling back to `NamespacedKey.minecraft(lowercased)` (`StatModule.java:75-76`), then `Registry.ATTRIBUTE.get(attrKey)` (`StatModule.java:78`). Missing attributes are skipped.
- **`block_break_speed`** special-case (`StatModule.java:86-94`): `100` is the vanilla baseline. The stat is converted to a *bonus* via `bonus = max(0, (statValue − 100) / 100)`, applied with a fixed `AttributeModifier` keyed by the static `valmora:mining_speed_bonus` `NamespacedKey` (`StatModule.java:68`), operation `ADD_NUMBER`. The previous modifier is removed first so values never accumulate.
- **Everything else** — the attribute's **base value** is overwritten with `0.1 × statValue / 100` (`StatModule.java:96`). For `movement_speed` this means `speed = 100` → base `0.1` (vanilla walking speed).

### 3.8 SystemStats aliasing

`SystemStats` (`SystemStats.java:5`) is a flat holder of 15 string fields (`:7-21`) loaded from `config.yml` (`SystemStats.java:45-63`) with hardcoded fallback ids. Getters (`:65-79`):

| Getter | config key | default id |
| --- | --- | --- |
| `getHealth()` | `combat.health-stat` | `health` |
| `getMana()` | `combat.mana-stat` | `mana` |
| `getDamage()` | `combat.damage-stat` | `damage` |
| `getStrength()` | `combat.strength-stat` | `strength` |
| `getDefense()` | `combat.defense-stat` | `defense` |
| `getCritChance()` | `combat.crit-chance-stat` | `crit_chance` |
| `getCritDamage()` | `combat.crit-damage-stat` | `crit_damage` |
| `getSpeed()` | `combat.speed-stat` | `speed` |
| `getHealthRegen()` | `combat.health-regen-stat` | `health_regen` |
| `getManaRegen()` | `combat.mana-regen-stat` | `mana_regen` |
| `getLuck()` | `combat.luck-stat` | `luck` |
| `getMiningFortune()` | `mining.mining-fortune-stat` | `mining_fortune` |
| `getMiningSpeed()` | `mining.mining-speed-stat` | `mining_speed` |
| `getBreakingPower()` | `mining.breaking-power-stat` | `breaking_power` |
| `getMiningSpread()` | `mining.mining-spread-stat` | `mining_spread` |

Consumers should **always** read stats through `SystemStats` keys rather than hardcoding ids — e.g. `RegenTask.java:34-46`, `DamageCalculator.java:35-60`, `ResourceManager.java:119/147`. This is what makes the `combat:`/`mining:` mapping section of `config.yml` meaningful.

### 3.9 Item stat storage — `StatModule.saveStats` / `loadStats` / `getStat`

- `saveStats(ItemMeta, Map<String,Double>)` (`StatModule.java:104-114`) builds a nested `PersistentDataContainer` via `getAdapterContext().newPersistentDataContainer()`, writes each stat as a `DOUBLE` under a plugin namespaced key (stat id lowercased, `:109`), and stores the whole container on the item under `Keys.STATS_CONTAINER_KEY` (`:113`).
- `loadStats(ItemMeta)` (`StatModule.java:120-141`) reads the container back, but **only returns stats whose id is registered** — unknown ids are dropped (`StatModule.java:132-138`). Returns an empty map if the container key is absent (`:124-126`).
- `getStat(ItemMeta, String)` (`StatModule.java:146-156`) is a convenience single-key lookup that defaults to `0.0`.

Writers of item stats: `ItemFactory.create` (`ItemFactory.java:53`), `ItemTranslator.translate` for vanilla gear (`ItemTranslator.java:41`), and `ReforgeModule.buildReforgedItem` which merges base + reforge bonuses before saving (`ReforgeModule.java:186-191`).

### 3.10 Recalc triggers — `PlayerListener`

`PlayerListener` (`PlayerListener.java:24`) is registered/unregistered by `StatModule` (`StatModule.java:39-40`, `:46-48`). The private `recalculate(Player)` helper (`PlayerListener.java:39-47`) defers the work by **1 tick** (`runTask`) so inventory state is settled, then resolves the profile and calls `recalculateStats`.

Handlers (all at `EventPriority.MONITOR`, `ignoreCancelled = true` unless noted):

| Handler | Event | Effect |
| --- | --- | --- |
| `onPlayerJoin` (`:54-57`) | `PlayerJoinEvent` | Recalculates on join (the profile pipeline already recalcs on profile load too — `PlayerManager.java:84-85`). |
| `onPlayerRespawn` (`:62-82`) | `PlayerRespawnEvent` | Heals the player to full max-health (from the `health` SystemStat), syncs visual health, then recalcs (the comment notes the delay matters so the respawn inventory is applied). |
| `onVanillaRegen` (`:84-96`, `EventPriority.HIGHEST`) | `EntityRegainHealthEvent` | For players, cancels vanilla regen reasons `SATIATED`, `MAGIC_REGEN`, and `REGEN` so only the stat-driven regen (`RegenTask`) governs; `CUSTOM` heals (API) pass through. |
| `onInventoryClick` (`:106-123`) | `InventoryClickEvent` | Recalculates when an armor slot or the off-hand slot (index 40) is clicked in the player's own inventory. |
| `onInventoryDrag` (`:128-149`) | `InventoryDragEvent` | Recalculates if the drag touches raw player slots 36–39 (armor) or 40 (off-hand). |
| `onItemHeldChange` (`:156-159`) | `PlayerItemHeldEvent` | Recalculates on hotbar scroll. |
| `onHandSwap` (`:164-167`) | `PlayerSwapHandItemsEvent` | Recalculates on the default `F` off-hand swap. |
| `onEquipmentChange` (`:172-175`) | `PlayerArmorChangeEvent` (Paper) | Recalculates on armor equip/unequip. |

### 3.11 The `/stat` command — `StatCommand`

`StatCommand` (`StatCommand.java:16`) is a `TabExecutor` constructed with the `PlayerManager` and wired in `Valmora.onEnable()` (`Valmora.java:236`) — commands are never registered inside a module (`AGENTS.md` §6.3). It is players-only (`StatCommand.java:26-29`) and requires an active profile (`:44-48`). Subcommands:

- `list` — prints `<gold><bold>Stats for profile: <name>` then every registered stat as `formattedName: <int value>` (`StatCommand.java:54-59`). No permission check.
- `add <statId> <value>` — requires `valmora.admin` (`:61-64`); validates the stat exists and parses a double, then `statManager.addStat(...)` (`:60-82`).
- `remove <statId> <value>` — requires `valmora.admin` (`:84-87`); validates, then `statManager.reduceStat(...)` (`:83-105`).
- Unknown subcommand → usage text (`StatCommand.java:106`).

Tab completion offers `list|add|remove` then the registry's stat ids for `add`/`remove` (`StatCommand.java:112-125`). The command itself has no permission node in `plugin.yml` (`plugin.yml:12-13`).

---

## 4. Configuration (YAML)

### 4.1 `stats/*.yml` — stat definitions

Every `.yml` file in `plugins/Valmora/stats/` (one or more) contributes definitions. The shipped default is `src/main/resources/stats/core.yml` (28 definitions), copied to the data folder by `Valmora.saveAllResources` (`Valmora.java:471`, `:481-483`).

```yaml
<stat-id>:                        # lowercased at load time (StatLoader.java:54)
  display-name: "Health"          # (string) default: the stat id   — shown in GUI/commands
  default-value: 100.0            # (double) default: 0.0           — starting / reset value
  max-value: 10000.0              # (double) default: uncapped (Double.MAX_VALUE if key absent)
  color: "<red>"                  # (string) default: "<white>"     — MiniMessage color for display
  icon: "APPLE"                   # (string) default: "PAPER"       — Bukkit Material for the stats GUI
  description: "Your maximum health pool."  # (string) default: ""
  pool: true                      # (bool)   default: false         — "resource pool" flag
  vanilla-attribute: "movement_speed"       # (string) default: null — optional Bukkit attribute binding
```

Full default inventory (`stats/core.yml`):

| id | display-name | default-value | max-value | color | icon | pool | vanilla-attribute |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `health` | Health | 100.0 | 10000.0 | `<red>` | APPLE | true | — |
| `mana` | Mana | 100.0 | 5000.0 | `<aqua>` | LAPIS_LAZULI | true | — |
| `damage` | Damage | 5.0 | — | `<yellow>` | IRON_SWORD | false | — |
| `strength` | Strength | 0.0 | — | `<dark_red>` | BLAZE_POWDER | false | — |
| `defense` | Defense | 0.0 | — | `<green>` | SHIELD | false | — |
| `crit_chance` | Crit Chance | 30.0 | 100.0 | `<gold>` | GOLD_NUGGET | false | — |
| `crit_damage` | Crit Damage | 50.0 | — | `<gold>` | GLOWSTONE_DUST | false | — |
| `speed` | Speed | 100.0 | — | `<white>` | SUGAR | false | `movement_speed` |
| `health_regen` | Health Regen | 1.0 | — | `<light_purple>` | GHAST_TEAR | false | — |
| `mana_regen` | Mana Regen | 2.0 | — | `<dark_aqua>` | PRISMARINE_CRYSTALS | false | — |
| `luck` | Luck | 0.0 | 100.0 | `<gold>` | RABBIT_FOOT | false | — |
| `mining_fortune` | Mining Fortune | 0.0 | 500.0 | `<gold>` | GOLD_NUGGET | false | — |
| `mining_speed` | Mining Speed | 100.0 | — | `<white>` | IRON_PICKAXE | false | `block_break_speed` |
| `intelligence` | Intelligence | 0.0 | — | `<aqua>` | BOOK | false | — |
| `ferocity` | Ferocity | 0.0 | 100.0 | `<dark_red>` | BLAZE_ROD | false | — |
| `pet_luck` | Pet Luck | 0.0 | — | `<gold>` | BONE | false | — |
| `sea_creature_chance` | Sea Creature Chance | 20.0 | 100.0 | `<blue>` | FISHING_ROD | false | — |
| `fishing_speed` | Fishing Speed | 0.0 | — | `<aqua>` | FISHING_ROD | false | — |
| `trophy_fish_chance` | Trophy Fish Chance | 0.0 | 100.0 | `<gold>` | TROPICAL_FISH | false | — |
| `bonus_attack_speed` | Bonus Attack Speed | 0.0 | — | `<yellow>` | GOLDEN_SWORD | false | — |
| `ability_damage` | Ability Damage | 0.0 | — | `<light_purple>` | BLAZE_POWDER | false | — |
| `magic_find` | Magic Find | 0.0 | — | `<aqua>` | NETHER_STAR | false | — |
| `true_defense` | True Defense | 0.0 | — | `<white>` | SHIELD | false | — |
| `vitality` | Vitality | 0.0 | — | `<red>` | GHAST_TEAR | false | — |
| `farming_fortune` | Farming Fortune | 0.0 | 500.0 | `<green>` | WHEAT | false | — |
| `foraging_fortune` | Foraging Fortune | 0.0 | 500.0 | `<dark_green>` | OAK_LOG | false | — |
| `breaking_power` | Breaking Power | 0.0 | 20.0 | `<gray>` | NETHERITE_PICKAXE | false | — |
| `mining_spread` | Mining Spread | 0.0 | 10.0 | `<aqua>` | AMETHYST_SHARD | false | — |

**Negative values are valid** and are honored by `StatDefinition.format` (a `-` prefix) and by the aggregation pipeline (`StatManager.java:106-111` adds them as modifiers, reducing the effective total).

---

## 5. Data Model / Persistence

### 5.1 Storage model

Stat values are persisted **in two places**:

1. **On items** — each stat is a `DOUBLE` inside a nested `PersistentDataContainer` under `Keys.STATS_CONTAINER_KEY` (`StatModule.java:104-114`). Item stats survive as long as the item stack does; they are **not** stored in the database.
2. **On the player profile** — the mutable `StatManager` is a field of `ValmoraProfile` (`ValmoraProfile.java:19`). Its `baseStats` map is serialized to JSON as part of the profile's `stats` column and restored on load.

### 5.2 Profile persistence flow

```
SQLDataStore.saveProfile()                         (SQLDataStore.java:210)
    └─ gson.toJson(profile.getStatManager().getSaveData())
         → {"health":100.0,"mana":100.0, ...}      (written to `profiles.stats` column)

SQLDataStore.loadProfile()                         (SQLDataStore.java:296)
    └─ profile.getStatManager().loadData(jsonMap)
         → baseStats rebuilt (lowercased keys)      (StatManager.java:36-41)
```

- `StatManager.getSaveData()` (`StatManager.java:32-34`) returns a **deep copy** of `baseStats` (new `HashMap`), so serialization cannot mutate live state.
- `loadData()` (`StatManager.java:36-41`) clears `baseStats`, then for each key: lowercases it, resolves the `StatDefinition` from the registry, and stores the double. **Unknown/missing definitions are skipped** (`:40`), so a config that removes a stat also drops the stale value — no orphan columns.

### 5.3 Save timing

The stat module does not save profiles itself. The profile pipeline saves on: logout, regular autosave ticks, and when the profile's dirty flag is set. Because `addStat`/`reduceStat`/`setStat` mutate `baseStats` in place (the same map object saved by `getSaveData`), any subsequent save picks the change up automatically — there is no separate "flush" step. As with all Valmora DB work, these queries run on the async HikariCP executor (`AGENTS.md` §7.4, §8); Bukkit-API callbacks are re-scheduled to the main thread (`ValmoraProfile.java:58-60` constructs the manager, `PlayerManager.java:84-85` recalcs on the main thread after profile load).

### 5.4 Vanilla attribute sync (memory only)

`StatModule.recalculateAttributes` writes to the Bukkit `Attribute` instances on the `Player` — these live **only in memory** for the session and are re-applied on every recalc (and on join via `PlayerListener.onPlayerJoin`). They are never serialized. See §3.7 for the two mapping modes.

---

## 6. API Exposed

The stat module is exposed through `ValmoraAPI` (the `getInstance()` singleton, `ValmoraAPI.java:27-31`):

```java
ValmoraAPI api = ValmoraAPI.getInstance();

StatModule    stats   = api.getStatModule();      // module accessor
StatRegistry  registry = api.getStatRegistry();    // stat definitions
SystemStats   sys     = api.getSystemStats();      // logical-id mapping
```

### 6.1 Public surface of `StatModule`

| Member | Signature | Notes |
| --- | --- | --- |
| module id | `getId()` | `"stats"` (`StatModule.java:53-55`) |
| registry access | `getStatRegistry()` | `StatModule.java:57-59` |
| system stats | `getSystemStats()` | `StatModule.java:61-63` |
| save item stats | `saveStats(ItemMeta, Map<String,Double>)` | `StatModule.java:104-114` |
| load item stats | `loadStats(ItemMeta)` → `Map<String,Double>` | only registered ids (`StatModule.java:120-141`) |
| single item stat | `getStat(ItemMeta, String)` → `double` | default `0.0` (`StatModule.java:146-156`) |
| attribute sync | `recalculateAttributes(Player, StatManager)` | `StatModule.java:71-99` |

### 6.2 Public surface of `StatManager`

`StatManager` is a public class but is **not** a singleton — each `ValmoraProfile` owns one (`ValmoraProfile.java:19`). Reach it via `api.getPlayerManager().getProfile(uuid).getStatManager()`.

| Member | Signature | Notes |
| --- | --- | --- |
| base-value read | `getBaseStat(id)` → `Optional<Double>` | `StatManager.java:40-42` |
| effective-value read | `getStat(id)` → `double` | `StatManager.java:70-72` |
| id enumeration | `getStatIds()` | `StatManager.java:74-76` |
| mutation | `addStat(id, value)` / `reduceStat(id, value)` / `setStat(id, value)` / `resetStat(id)` | each triggers recalc (`:43-63`) |
| modifiers | `addModifier(id, double)` | transient, folded into `effectiveStats` (`StatManager.java:66-68`) |
| recalc | `recalculateStats(Player)` | full pipeline (§3.6), `StatManager.java:83-193` |
| persistence | `getSaveData()` / `loadData(Map)` | `StatManager.java:32-41` |

### 6.3 Recalc-trigger etiquette for other modules

If another module mutates stats or changes a value that feeds the pipeline, it must trigger a recalc. The three idiomatic ways used today:

- **Mutation APIs** — call `addStat`/`reduceStat`/`setStat` on the profile's `StatManager`; these recalc internally (`StatManager.java:46`, `:52`, `:57`). Used by `StatCommand`, `ResourceManager`, `ProgressionStatService`.
- **Modifier-only services** — during `recalculateStats`, the pipeline itself re-invokes the service, so the service only needs to *implement* `applyTo(player, statManager)`. `SetBonusService.applyTo` (`SetBonusService.java`), `ProgressionStatService.applyTo`, `TemporaryStatService.applyTo` (`TemporaryStatService.java:11-13`), and `AlchemyManager.applyEffectsToStats` all follow this pattern and must **not** call recalc themselves (would recurse).
- **Naked pushes** — a timed buff that wants to be *transient and not persisted* calls `addModifier` from inside its `TemporaryStatService.applyTo` re-application (`TemporaryStatService.java:22-28`) rather than touching `baseStats`.

---

## 7. Dependencies & Consumers

### 7.1 Dependencies of the stat module (must load **before** it)

Per registration order in `Valmora.java` (`script → time → stat → …`; `Valmora.java:188-192`):

| Dependency | Why |
| --- | --- |
| **time** (`TimeModule`) | None (it precedes `stat` for order history). No runtime dependency. |
| `ValmoraProfile` / `PlayerManager` | `StatCommand` and `PlayerListener` need the profile pipeline (`Valmora.java:236` wires the command with the `PlayerManager`). |

Because `StatModule.onEnable()` only loads definitions and `SystemStats`, it has **no** forward dependencies at enable-time; all cross-module access happens at runtime through `ValmoraAPI`.

### 7.2 Modules/services that **consume** the stat module

| Consumer | How it uses stats | Reference |
| --- | --- | --- |
| **combat** — `DamageCalculator` | Reads `damage`, `strength`, `crit_chance`, `crit_damage`, `defense`, `true_defense`, `luck`, `bonus_attack_speed`, `ferocity`, `ability_damage`, `vitality` via `SystemStats` keys | `DamageCalculator.java:35-60` |
| **combat** — `RegenTask` | Health/Mana regen tick from `health_regen` / `mana_regen` + max pools | `RegenTask.java:34-46` |
| **combat** — `ResourceManager` | Max health/mana consumption & capping | `ResourceManager.java:119`, `:147` |
| **player** — `ProgressionStatService` | Applies progression-tree stat bonuses in the pipeline | `ProgressionStatService.java` |
| **ui** — `ProfileGui` | Health/Mana bars and stat display | `ProfileGui.java` |
| **ui** — `ActionBarUI` | Dynamic stat readout (health, mana, speed, …) | `ActionBarUI.java` |
| **stat-adjacent** — `SetBonusService` | Armor set bonuses via `addModifier` inside pipeline | `SetBonusService.java` |
| **stat-adjacent** — `TemporaryStatService` | Timed buffs (e.g. ability-cast buffs) re-applied in pipeline | `TemporaryStatService.java` |
| **ability** — `ModifyStatMechanic` | `stat: X amount: Y duration: -1` → permanent `addModifier` at cast time | `ModifyStatMechanic.java` |
| **alchemy** — `AlchemyManager` | Potion effects modify stats through `applyEffectsToStats` | `AlchemyManager.java` |
| **script** — `PlayerVariableProvider` | Exposes `$player.stat.<id>$` variables to the scripting engine | `PlayerVariableProvider.java` |
| **item** — `ItemFactory` / `ItemTranslator` | Write item PDC stats at creation/translation | `ItemFactory.java:53`, `ItemTranslator.java:41` |
| **reforge** — `ReforgeModule` | Merges base + reforge stat bonuses onto items | `ReforgeModule.java:186-191` |
| **mob** — `MobDeathListener` | Mob stat drops / loot scaling | `MobDeathListener.java` |
| **quest** — `QuestListener` | Quest requirements/objectives gated on stats | `QuestListener.java` |
| **gui** — `guis/stats.yml` | The stats GUI is purely `$player.stat.*$`-driven | `guis/stats.yml:21-88` |
| **enchant** — `EnchantmentHelper`/enchant logics | `applyStats(player, level, statManager)` folded into pipeline | `StatManager.java:129-135` |
| **pet** — pet module | `applyPetStats(player, statManager)` in pipeline | `StatManager.java:157-161` |

> **Decoupling note (AGENTS.md §6.4):** `StatManager.recalculateStats` reaches every one of those services through `ValmoraAPI.getInstance().getX()` and `Optional` guards (`StatManager.java:138-171`), so a missing/unloaded module is a no-op rather than a crash.

---

## 8. Unfinished Things / TODOs

- **`combat` `temporary-stat`** config is parsed (`SystemStats.java:63`) but **not exposed** via a getter and **not consumed** anywhere — `TemporaryStatService` hardcodes its key instead of using a SystemStat id. (`SystemStats.java:5-63`, `TemporaryStatService.java`.) This looks like a half-wired intent to make the temp-stat configurable; as-is, renaming the temp stat in config has no effect.
- **Health/Mana "pool" flag** is only a display hint. `pool` is read at load (`StatLoader.java:61`) and surfaced via `isPool()` (`StatDefinition.java:37`), but the only in-code consumer is `ProfileGui`/GUI presentation. There is no engine-side health-pool or mana-pool abstraction keyed on the flag.
- **`health_regen`/`mana_regen` aren't capped** — the default definitions omit `max-value` (uncapped), which is fine, but there is no server-wide hard cap guard beyond the per-definition `max-value`.
- **No stat-modification event** (`StatModifyEvent`) — `StatModifyEventFactory` exists but a direct `EventManager.callEvent(...)` from the stat module is **not** wired in this module's sources (see §9). Other systems listen for a stat-modify event, but the trigger must originate from the services that mutate stats.
- **`breakStatEnchants` / reforge-only flow** — untouched; `ReforgeModule` is the only reforge consumer today.

## 9. Possible Improvements / Changes

1. **Wire the stat-modify event.** `StatModifyEventFactory` (`StatModifyEventFactory.java`) creates a `StatModifyEvent` carrying the player, stat id, old and new values. Emitting it from `StatManager`'s mutation methods (`addStat`/`reduceStat`/`setStat`) and from `ModifyStatMechanic` would let quest/audit systems react to stat changes without polling. Care: firing Bukkit events from within the pipeline is main-thread-safe (the pipeline already runs on the main thread), but must not recurse into recalc.
2. **Consume `temporary-stat`.** Expose `SystemStats.getTemporaryStat()` and teach `TemporaryStatService` to read the configured id, making the temp-stat renameable like every other core stat.
3. **Add `pool` semantics.** Use the `pool` flag to drive max-health/max-mana capping (`ResourceManager`) instead of the hardcoded `health`/`mana` `SystemStats` keys, so pool behavior follows config.
4. **Clamp attributes, not just effective values.** `recalculateAttributes` clamps nothing — a server that raises `speed` above the definition cap will see vanilla attribute behavior change anyway. Consider clamping `speed`/`mining_speed` in the attribute mapping to the same `max-value`.
5. **Skip empty-attribute fast-path.** `StatModule.recalculateAttributes` overwrites the attribute base value on every recalc even when the stat is unchanged; comparing the previous effective value and skipping would avoid attribute-changed noise for other listeners.
6. **Unit-test the pipeline.** Only `StatDefinitionTest` and the `YamlConfigLoadTest` stat assertions exist. `StatManager.recalculateStats` has no test coverage; a `DummyExecutionContext`-style stub player + mocked `ValmoraAPI` consumers (per `ExpressionTest` pattern) would protect the §3.6 ordering from regressions.

