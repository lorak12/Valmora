# Alchemy Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `alchemy` | **Source:** `src/main/java/org/nakii/valmora/module/alchemy/`

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

The Alchemy module is Valmora's **custom potion-brewing system**. It is a `ReloadableModule` that loads **alchemy effect definitions** (potions) from `plugins/Valmora/alchemy/*.yml`, exposes a **dynamic brewing handler** to the Recipe engine (`machine: alchemy`), applies **temporary active effects** to players/entities with stat integration, and ships two admin/player commands (`/potion`, `/effects`).

The module has three interlocking layers:

1. **Definitions & Registry** — `AlchemyEffect`s loaded from YAML (19 ship by default), plus a hand-coded `HardcodedAlchemyEffect` set that implements the runtime mechanics that YAML cannot express (instant healing, poison ticks, absorption, true damage, vanilla effect delegation). All live in `AlchemyManager`.
2. **Brewing pipeline** — `AlchemyMachineHandler` implements `DynamicMachineHandler` and plugs into `RecipeEngine` under the machine id `alchemy` (`AlchemyModule.java:51-52`). It produces the intermediate **Awkward Potion**, turns "Awkward Potion + ingredient" into brewed potions, and supports **level / duration / splash modifiers** (glowstone / redstone / gunpowder family).
3. **Runtime effect engine** — `AlchemyManager` stores per-entity `ActiveEffect`s in-memory (keyed by `UUID`), applies their stat modifiers into `StatManager` during recalculation (`AlchemyManager.java:142-155`), ticks them to expiry (`AlchemyManager.java:118-138`), and drives listener hooks for drinking, splash, water-breathing and burning (`AlchemyListener`).

All potion data lives on the `ItemStack` itself via six `ALCHEMY_*` **PersistentDataContainer (PDC)** keys (`Keys.java:52-57`) — level, duration, splash state, and modifier flags travel with the item. Active effects are **not persisted** to the database; they live only in memory.

Per the module load order (`docs/MODULE_DEVELOPMENT.md` §9), `AlchemyModule` is registered **after** `recipe` and `gui`, and **before** `enchant` (`Valmora.java:203`):

```
... → gui → recipe → alchemy → enchant → zone → ...
```

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/alchemy/
├── AlchemyModule.java              # ReloadableModule — lifecycle, effect/modifier loading, tick task
├── AlchemyManager.java             # Registry of effects/modifiers + active-effect store & ticking
├── AlchemyListener.java            # Drink / splash / drowning / burning event handlers
├── brewing/
│   └── AlchemyMachineHandler.java  # DynamicMachineHandler — the brewing pipeline
├── command/
│   ├── PotionCommand.java          # /potion give <effect_id> <level> [player]
│   └── EffectsCommand.java         # /effects — opens the active_effects GUI
├── effect/
│   ├── ActiveEffect.java           # record (effectId, level, expiresAtMs)
│   ├── AlchemyEffect.java          # Immutable potion definition POJO (with Tier record)
│   ├── AlchemyEffectLoader.java    # YAML → AlchemyEffect parser
│   ├── AlchemyEffectType.java      # enum BUFF | DEBUFF
│   ├── HardcodedAlchemyEffect.java # Code-driven effect interface (onApply/onExpire/onTick)
│   └── hardcoded/
│       ├── AbsorptionAlchemyEffect.java  # setAbsorptionAmount by level
│       ├── DamageAlchemyEffect.java      # instant true damage (5 × level)
│       ├── HealingAlchemyEffect.java     # instant heal (20/50/+50)
│       ├── PoisonAlchemyEffect.java      # damage-over-time tick (10 × level)
│       └── VanillaAlchemyEffect.java     # delegates to a vanilla PotionEffect
├── gui/
│   └── AlchemyVariableProvider.java # $alchemy.effects.count$ / $alchemy.effects.list$ script vars
└── modifier/
    ├── AlchemyModifier.java        # One item → LEVEL/DURATION/SPLASH potion transformation
    └── AlchemyModifierType.java    # enum LEVEL | DURATION | SPLASH

src/main/resources/
├── alchemy/
│   ├── effects.yml                 # 19 shipped potion definitions
│   ├── healing_boost.yml           # Legacy placeholder (effects moved to effects.yml)
│   └── modifiers.yml               # Glowstone / redstone / gunpowder modifier definitions
├── guis/alchemy.yml                # Alchemy Table GUI (machine: alchemy) — consumer
├── guis/active_effects.yml         # Active Effects GUI ($alchemy.effects.*$) — consumer
├── items/alchemy_ingredients.yml   # Enchanted tier-2/tier-3 ingredients + modifier items
├── recipes/alchemy.yml             # Static shapeless alchemy recipes (XP path) — consumer
└── skills/alchemy.yml              # Alchemy skill definition (XP rewards, no sources by default)
```

**Runtime wiring in `Valmora.java`:** module instantiated at `Valmora.java:165`, registered at `:203`, `/potion` + `/effects` executors set at `:243-244`, exposed via API at `:364-366`, and `alchemy/` folder auto-extracted from the jar at `:471`.

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `AlchemyModule.java`

Implements `ReloadableModule` (see `docs/MODULE_DEVELOPMENT.md` §2).

| Method | Behavior | Lines |
|---|---|---|
| constructor | Reads `alchemy.max-active-effects` from `config.yml` (default `10`) and constructs the `AlchemyManager` | `AlchemyModule.java:32-36` |
| `onEnable()` | `manager.clear()` → load effects via `YamlLoader` → register 8 hardcoded effects → load modifiers → register the `"alchemy"` machine handler → register `AlchemyVariableProvider` → register `AlchemyListener` → start the active-effect tick task | `AlchemyModule.java:39-65` |
| `onDisable()` | Cancels the tick task, unregisters the listener, `manager.clear()` | `AlchemyModule.java:68-82` |
| `getId()` | `"alchemy"` | `AlchemyModule.java:85` |
| `getName()` | `"Alchemy System"` | `AlchemyModule.java:88` |
| `getAlchemyManager()` | Returns the live `AlchemyManager` | `AlchemyModule.java:90` |

**Effect loading** (`AlchemyModule.java:44-45`): `YamlLoader<AlchemyEffect>` on folder `"alchemy"` with type name `"Alchemy Effect"`, parsing each top-level YAML key with `AlchemyEffectLoader.parser()` and registering via `alchemyManager::registerEffect`. Per `YamlLoader` semantics (`YamlLoader.java:113-123`) one malformed effect logs a warning but does not stop the rest.

**Tick task** (`AlchemyModule.java:59-64`): a repeating `BukkitScheduler.runTaskTimer` (main thread) with an initial 20-tick delay and period `alchemy.tick-interval` (default 20 = 1 s). Every tick it calls `alchemyManager.tick(player)` for every online player. This is the heartbeat that expires effects and runs `onTick` mechanics (poison). It uses the **global** Bukkit scheduler, not `EntityScheduler` — documented limitation, see §8.

**Modifier loading** (`loadModifiers`, `AlchemyModule.java:110-124`): unlike the effects (loaded through `YamlLoader`), modifiers are loaded manually — the file `alchemy/modifiers.yml` is copied from the jar if missing (`saveResource`), then read with a raw `YamlConfiguration`. Categories `level`, `duration`, `splash` are parsed by `loadModifierCategory` (`AlchemyModule.java:126-159`) and each entry registered as an `AlchemyModifier`. Malformed entries log a warning and are skipped (`AlchemyModule.java:155-157`). `countModifiers` (`AlchemyModule.java:161-168`) is used only for the "Loaded N alchemy modifier(s)" log line.

### 3.2 The Effect Model

#### `AlchemyEffect.java`

An immutable value object representing one potion type (`AlchemyEffect.java:8-74`):

| Field | Source YAML | Notes |
|---|---|---|
| `id` | top-level key | Registry key (stored lowercase in `AlchemyManager`). |
| `name` | `name` | MiniMessage display name; prefixed onto the item name plus a roman numeral. |
| `type` | `type` | `AlchemyEffectType` (`BUFF`/`DEBUFF`) — drives lore coloring and splash targeting. |
| `rarity` | `rarity` | Upper-cased string; rendered at the bottom of the lore with a color mapping. |
| `color` | `color` | Hex color applied to the potion item's `PotionMeta`. |
| `lore` | `lore` | MiniMessage lore lines above the stat block. |
| `tiers` | `tiers` | `List<Tier>`; each `Tier(ingredientKey, level)` maps one ingredient to the base level it produces. |
| `maxLevel` | `max-level` | Absolute ceiling including all level modifiers. |
| `durations` | `duration` | Per-level base duration list (seconds). |
| `stats` | `stats` | `Map<String, List<Double>>` — per-level value list for each stat id. |

Behavioral helpers:

- `getMaxBaseLevel()` (`AlchemyEffect.java:52-55`) — the highest level reachable *without* level modifiers: `max(tier.level())`, falling back to `1`. This is what `requires-max-base` modifiers compare against.
- `getTierForIngredient(String)` (`AlchemyEffect.java:57-59`) — case-insensitive tier lookup (currently only used implicitly via `AlchemyManager.registerEffect`).
- `getDuration(int level)` (`AlchemyEffect.java:61-64`) — list lookup clamped to the last index.
- `getStatValue(statId, level)` (`AlchemyEffect.java:68-73`) — per-level stat lookup, stat id lowercased, clamped to the last index.

#### `AlchemyEffectType.java`

A two-value enum: `BUFF`, `DEBUFF` (`AlchemyEffectType.java:3-5`). Used to decide lore sign/color, GUI dye material (`AlchemyVariableProvider.java:62`), and splash application rules (`AlchemyListener.java:116-121`).

#### `ActiveEffect.java`

A tiny record `ActiveEffect(String effectId, int level, long expiresAtMs)` (`ActiveEffect.java:3-12`) with two helpers: `isExpired()` (wall-clock compare, `:5-7`) and `remainingSeconds()` (`:9-11`). Expiry is based on `System.currentTimeMillis()`, i.e. real time, not ticks.

#### `AlchemyEffectLoader.java`

Static parser producing `LoadResult<AlchemyEffect, String>` from a `ConfigurationSection` (`AlchemyEffectLoader.java:37-123`). Key behavior:

- **Defaults:** `name` ← id (`:39`), `type` ← `BUFF` (`:40`), `rarity` ← `COMMON` (`:42`), color ← `Color.PURPLE` when no `color` key (`:44`).
- **Color** (`:44-50`): `#RRGGBB` hex → `Color.fromRGB`.
- **Tiers** (`:56-88`) — three formats, in preference order:
  1. `tiers:` as a `ConfigurationSection` map of sub-keys, each with `ingredient` + `level` (`:57-66`);
  2. `tiers:` as a **list of maps** (`ingredient`, `level`, `:68-80`);
  3. a single legacy `ingredient:` key, wrapped as a level-1 tier (`:83-88`).
  - If no tier/ingredient at all: `LoadResult.failure(...)` — the effect is rejected (`:90-92`).
- **`resolveIngredientKey`** (`:23-35`) canonicalizes an ingredient string:
  - `minecraft:<x>` → kept as-is lowercased;
  - any `Material.matchMaterial(...)` hit → `minecraft:<material>`;
  - otherwise treated as a **custom Valmora item ID** (matched against the item's `ITEM_ID_KEY` PDC at craft time).
- **`max-level`** default `1` (`:94`).
- **`duration`** list default `[60]` when absent or empty (`:96-101`).
- **`stats`** (`:103-117`): keys are lowercased and **silently dropped if not registered** in the `StatRegistry` (`:108-109`) — a typo'd stat id gives no warning. Values are per-level `List<Double>`.

### 3.3 Hardcoded Effects — `HardcodedAlchemyEffect.java`

The interface for code-driven mechanics that YAML can't express (`HardcodedAlchemyEffect.java:11-19`): `getEffectId()`, `onApply(entity, level, durationSeconds)`, `onExpire(entity, level)`, and a default no-op `onTick(player, level)`. Registered with `AlchemyManager.registerHardcodedEffect` — **the effect id must match an `AlchemyEffect` loaded from YAML** (`HardcodedAlchemyEffect.java:9`), because the runtime engine looks up hardcoded handlers by the same lowercase id that the YAML registry uses.

Registered in `registerHardcodedEffects()` (`AlchemyModule.java:94-106`):

| Id | Class | YAML counterpart | Effect |
|---|---|---|---|
| `jump_boost` | `VanillaAlchemyEffect(JUMP_BOOST, amplifierScales=true)` | `effects.yml:31` | Vanilla `JUMP_BOOST` at amplifier `level − 1`. |
| `night_vision` | `VanillaAlchemyEffect(NIGHT_VISION, false)` | `effects.yml:102` | Vanilla `NIGHT_VISION`, amplifier always 0. |
| `invisibility` | `VanillaAlchemyEffect(INVISIBILITY, false)` | `effects.yml:134` | Vanilla `INVISIBILITY`, amplifier 0. |
| `fire_resistance` | `VanillaAlchemyEffect(FIRE_RESISTANCE, false)` | `effects.yml:89` | Vanilla `FIRE_RESISTANCE`, amplifier 0. |
| `healing` | `HealingAlchemyEffect` | `effects.yml:44` | Instant heal: `{20, 50, 100, 150, 200, 250, 300, 350}` HP (`HealingAlchemyEffect.java:16`). |
| `poison` | `PoisonAlchemyEffect` | `effects.yml:57` | `onTick` deals `10 × level` true damage per tick interval (`PoisonAlchemyEffect.java:27`). |
| `absorption` | `AbsorptionAlchemyEffect` | `effects.yml:243` | Sets absorption HP `{20, 40, 60, 80, 100, 150, 200, 300}` (`AbsorptionAlchemyEffect.java:12`). |
| `damage` | `DamageAlchemyEffect` | `effects.yml:200` | Instant true damage `5 × level` on apply (`DamageAlchemyEffect.java:20`). |

Two more effects — `water_breathing` and `burning` — are **not** `HardcodedAlchemyEffect`s; they are implemented directly in `AlchemyListener` (see §3.7). All other shipped effects (`speed`, `strength`, `regeneration`, `weakness`, `slowness`, `haste`, `critical`, `resistance`, `mana`) are **pure stat effects** — their only runtime behavior is the stat modifier applied by `applyEffectsToStats`.

**`VanillaAlchemyEffect`** (`VanillaAlchemyEffect.java:13-40`) delegates to a real `PotionEffect` for the full duration. `amplifierScales` chooses `level − 1` vs `0`. `onApply` adds the effect (`:31-34`), `onExpire` removes it (`:37-39`). Particles/ambient are both false (`:33`).

**`HealingAlchemyEffect`** (`HealingAlchemyEffect.java:14-50`) heals players through the profile `PlayerState.heal(...)` + `syncVisualHealth` (`:41-49`); non-players get `setHealth(min(maxHealth, health + amount))` (`:27-30`).

**`PoisonAlchemyEffect`** (`PoisonAlchemyEffect.java:13-36`): `onApply`/`onExpire` are no-ops; `onTick` applies `10 × level` raw damage via `PlayerState.reduceHealth` for players (`:25-35`). Note it only ever ticks for **players** (`onTick` signature is `Player`), so a poisoned mob suffers no DOT.

**`AbsorptionAlchemyEffect`** (`AbsorptionAlchemyEffect.java:10-27`): sets `entity.setAbsorptionAmount(...)` on apply, resets to `0` on expire. Values clamped to the table length.

**`DamageAlchemyEffect`** (`DamageAlchemyEffect.java:13-36`): instant `5 × level` true damage through `PlayerState.reduceHealth` for players (`:21-28`), `setHealth` for other entities (`:29-31`). Bypasses defense entirely. Intended for splash potions (`:11`).

### 3.4 The Brewing Pipeline — `AlchemyMachineHandler.java`

`AlchemyMachineHandler implements DynamicMachineHandler` (`AlchemyMachineHandler.java:27`), registered for machine id `"alchemy"` in `AlchemyModule.onEnable()` (`AlchemyModule.java:51-52`). It is a **stateless engine**: `match(...)` inspects the `base` + `ingredient` inputs, builds the result `ItemStack`, and returns a `RecipeDefinition.dynamic(...)` whose `consumeHandler` decrements one of each input (`AlchemyMachineHandler.java:151-156`).

`match(Map, Player)` (`AlchemyMachineHandler.java:45-87`) is a three-priority cascade:

```
Priority 1: matchModifier(base, ingredient)   → level/duration/splash transformation
Priority 2: water bottle + NETHER_WART        → Awkward Potion
Priority 3: awkward potion + ingredient       → brewed potion (ingredient → effect tier)
```

#### Priority 2 — Awkward Potion

`isWaterBottle(base) && ingredient.getType() == NETHER_WART` → `buildAwkwardPotion()` (`AlchemyMachineHandler.java:58-64`). A water bottle is any `POTION` item with **no** `ITEM_ID_KEY` tag and a base potion type of `WATER` or none (`isWaterBottle`, `:248-260`). `buildAwkwardPotion` (`:160-176`) creates a `POTION` with base type `MUNDANE`, purple color `(100,80,150)`, `HIDE_ADDITIONAL_TOOLTIP`, `ITEM_ID_KEY = "awkward_potion"`, `RARITY_KEY = "COMMON"`, and name/lore text.

#### Priority 3 — Brewed potion

The base must be awkward (`isAwkwardPotion`, `AlchemyMachineHandler.java:262-272` — either the tagged `awkward_potion` or a **vanilla** `AWKWARD` potion from creative). The ingredient is canonicalized by `getItemKey` (`:234-242`):

- if the item carries a custom `ITEM_ID_KEY` that does **not** start with `vanilla_` or `alchemy:`, the raw item id is used (e.g. `enchanted_sugar`);
- otherwise `minecraft:<material>`.

That key is looked up in `AlchemyManager.getBrewTier` (`AlchemyMachineHandler.java:70`). On a match (`:73-86`):

1. `baseLevel = min(tier.level(), effect.getMaxLevel())` (`:75`) — a tier whose level exceeds `max-level` is silently clamped (see §8, poison has a tier level 5 vs `max-level: 4`).
2. `alchemyBonus = getAlchemyLevel(player)` (`:76`, `:281-294`) — the player's Alchemy **skill level**, resolved via `PlayerManager → profile → SkillManager.getXp("alchemy") → SkillRegistry.getProgressData("default", xp).currentLevel()`.
3. `alchemyMult = 1.0 + alchemyBonus * 0.01` (`:78`) — **+1% duration per Alchemy skill level**.
4. `duration = (int)(effect.getDuration(baseLevel) * alchemyMult)` (`:79`).
5. `buildPotionItem(effect, baseLevel, duration, false, false, false)` (`:81`) — drinkable, unmodified.

#### Priority 1 — Modifiers (`matchModifier`, `AlchemyMachineHandler.java:91-142`)

Checked **first** so that modifier ingredients (glowstone dust etc.) can never accidentally match a brew tier. The ingredient key must resolve to a registered `AlchemyModifier` (`:93`), and the base must carry `ALCHEMY_EFFECT_ID` (`:99-101`) and resolve to a known effect (`:103-104`). Current state is read from PDC: `currentLevel` (default 1), `currentDuration` (default `effect.getDuration(currentLevel)`), `isSplash`, `levelModified`, `durationModified` (`:106-110`).

| Modifier type | Conditions | Result | Lines |
|---|---|---|---|
| `LEVEL` | not `levelModified`; if `requiresMaxBase` then `currentLevel == getMaxBaseLevel()`; `newLevel = currentLevel + bonus` must not exceed `getMaxLevel()` | `buildPotionItem(effect, newLevel, currentDuration, isSplash, true, durationModified)` | `:115-122` |
| `DURATION` | not `durationModified`; `requiresMaxBase` check | `newDuration = isSplash ? (int)(seconds × getSplashMultiplier()) : seconds`; `buildPotionItem(..., currentLevel, newDuration, isSplash, levelModified, true)` | `:123-133` |
| `SPLASH` | not already splash; `requiresMaxBase` check | `splashDuration = (int)(currentDuration × durationMultiplier)`; `buildPotionItem(..., currentLevel, splashDuration, true, levelModified, durationModified)` | `:134-140` |

- Each category is applied **once** (`levelModified`/`durationModified` flags, `isSplash` guard). A potion can therefore carry at most one level modifier, one duration modifier, and one splash conversion.
- `requires-max-base` gates the modifier on the potion already being at its **max base level** (highest tier reachable from recipes, `AlchemyEffect.getMaxBaseLevel()`). The shipped config makes **only** `minecraft:glowstone_dust` exempt (`modifiers.yml:22-25`), so it can bump any base tier.
- `getSplashMultiplier()` (`:145-149`) is **hardcoded to `0.5`** with a comment noting it could be tracked per-potion via an extra PDC key. Used when a duration modifier is applied to an already-splash potion so the combination is order-independent.

#### Item building — `buildPotionItem` (`AlchemyMachineHandler.java:183-230`)

- Material `SPLASH_POTION` vs `POTION` (`:185`).
- `PotionMeta`: effect color if any (`:189`), base type `MUNDANE`, `HIDE_ADDITIONAL_TOOLTIP` (`:190-191`).
- Writes **six** alchemy PDC keys + `ITEM_ID_KEY = "alchemy:<effectId>"` (`:193-199`).
- Display name: `effect.getName() + " " + toRoman(level)` (`:202`).
- Lore (`:204-227`): effect lore lines → empty line → per-stat lines `StatDisplayName: ±value` colored green (BUFF) / red (DEBUFF) with the sign prefix for BUFFs (`:210-219`) → empty line → `Duration: <formatted>` (`:222`), `Type: Splash` when splash (`:223`) → rarity line in its color, italic, `effect.getRarity()` (`:225`). `getRarityColor` (`:305-313`): `UNCOMMON`→green, `RARE`→blue, `EPIC`→dark_purple, `LEGENDARY`→gold, default gray.
- `formatDuration` (`:296-303`): `Nm Ns` / `Nm` / `Ns`.
- `toRoman` (`:315-327`): I–VIII, then plain digits.

`buildPotion(effect, level, duration, isSplash, levelModified, durationModified)` (`:178-181`) is the **public** wrapper used by `PotionCommand`.

### 3.5 Modifiers — `AlchemyModifier.java` / `AlchemyModifierType.java`

`AlchemyModifier` (`AlchemyModifier.java:13-46`) is a plain value object: `itemId` (in `minecraft:<material>` or Valmora-item-id form), `type` (`LEVEL`/`DURATION`/`SPLASH`, `AlchemyModifierType.java:3-6`), `levelBonus`, `durationSeconds`, `durationMultiplier`, `requiresMaxBase`. Only one of `levelBonus`/`durationSeconds`/`durationMultiplier` is meaningful per type, set by the corresponding branch in `loadModifierCategory` (`AlchemyModule.java:139-152`):

- `LEVEL` → `new AlchemyModifier(itemId, type, bonus, 0, 1.0, requiresMaxBase)`
- `DURATION` → `new AlchemyModifier(itemId, type, 0, seconds, 1.0, requiresMaxBase)`
- `SPLASH` → `new AlchemyModifier(itemId, type, 0, 0, durationMultiplier, requiresMaxBase)`

Registered into `AlchemyManager.modifierRegistry` keyed by item id (`AlchemyManager.java:52-54`) and looked up in `matchModifier` via `getItemKey` (`AlchemyMachineHandler.java:92-94`).

### 3.6 Runtime Effect Engine — `AlchemyManager.java`

Holds five maps (`AlchemyManager.java:27-33`):

| Map | Type | Purpose |
|---|---|---|
| `activeEffects` | `ConcurrentHashMap<UUID, List<ActiveEffect>>` | Per-entity active potion effects. |
| `hardcodedEffects` | `HashMap<String, HardcodedAlchemyEffect>` | Code-driven mechanics, keyed by effect id. |
| `ingredientIndex` | `HashMap<String, BrewTier>` | Ingredient key → `BrewTier(effect, baseLevel)`; built by `registerEffect` (`:43-45`). |
| `effectRegistry` | `HashMap<String, AlchemyEffect>` | Loaded YAML effects, lowercase keys. |
| `modifierRegistry` | `HashMap<String, AlchemyModifier>` | Loaded modifiers, item-id keys. |

`BrewTier` is a nested record `BrewTier(AlchemyEffect effect, int baseLevel)` (`AlchemyManager.java:25`).

**Registry surface:** `registerEffect` (`:41-46`) stores the effect and indexes every tier into `ingredientIndex` (lowercased). `registerHardcodedEffect` (`:48-50`), `registerModifier` (`:52-54`), lookups `getEffect`/`getBrewTier`/`getModifier` (all `Optional`-returning, case-insensitive, `:56-66`), `getAllEffects` (unmodifiable, `:68-70`).

> **`clear()` only clears the three YAML-backed maps** (`effectRegistry`, `ingredientIndex`, `modifierRegistry`, `:72-76`). It does **not** clear `activeEffects` or `hardcodedEffects`. Consequence: **active effects survive `/valmora reload`**, and hardcoded effects keep their handlers (they are re-registered over the same keys each enable).

**`applyEffect(entity, effectId, level, durationSeconds)`** (`:80-97`):

1. Replaces any existing effect with the same id (`removeIf`, `:84`).
2. Applies the `maxActiveEffects` cap **only for players** and only when adding a *new* effect — `if (entity instanceof Player && effects.size() >= maxActiveEffects) return;` (`:86`). Re-applying the same effect (removed in step 1) always succeeds.
3. Computes `expiresAt = now + duration × 1000` and appends an `ActiveEffect` (`:88-89`).
4. Invokes `hardcodedEffects[effectId].onApply(entity, level, duration)` if present (`:91-92`).
5. For players, triggers `recalculatePlayerStats` (`:94-96`).

**`removeEffect`** (`:99-104`) removes by id and recalculates for players. **`getActiveEffects(UUID)`** (`:106-110`) returns an unmodifiable view. **`clearAllEffects(UUID)`** (`:112-114`) removes the whole entry — currently **dead code**, no caller exists (see §8).

**`tick(Player)`** (`:118-138`) — called from the module's repeating task for every online player. Iterates the player's effects; expired ones are removed and `onExpire` fired (`:124-130`); live ones get `onTick` (`:131-134`). If anything expired, recalculates stats (`:137`). Because expiry uses wall-clock time, a player who logs out mid-effect keeps their effect (memory) and it continues ticking once they log back in.

**`applyEffectsToStats(player, statManager)`** (`:142-155`) — the stat integration hook. For each non-expired effect, looks up the `AlchemyEffect` definition and adds every stat value (`def.getStatValue(statId, level)`) as a **modifier** via `statManager.addModifier` (`:150-153`). Skipped entirely when the value is `0` (`:152`). Invoked by `StatManager.recalculateStats` (`StatManager.java:138-141`).

**`recalculatePlayerStats`** (`:159-165`) — routes through `ValmoraAPI → PlayerManager → session → active profile → StatManager.recalculateStats(player)`.

### 3.7 Event Handling — `AlchemyListener.java`

Registered in `AlchemyModule.onEnable()` (`AlchemyModule.java:56-57`); unregistered on disable (`AlchemyModule.java:76-78`).

**`onDrink(PlayerItemConsumeEvent)`** — priority `HIGH` (`AlchemyListener.java:28-57`):

- Reads `ALCHEMY_EFFECT_ID`; if absent the event passes through to vanilla behavior (`:34-35`).
- A splash potion is **rejected here** — `ALCHEMY_IS_SPLASH == 1` → `setCancelled(true)` and return, because splash consumption is handled by `PotionSplashEvent` (`:38-42`).
- Level defaults to `1`, duration to `60` (`:44-45`).
- Cancels the vanilla consume, applies the effect, and **manually decrements the stack in the player's main hand** only if `hand.isSimilar(item)` (`:47-56`). Any consumption slot other than the main hand leaves the item un-decremented (see §8).

**`onEntityDamage(EntityDamageEvent)`** — `HIGH` (`AlchemyListener.java:60-74`): drowning only. For each active effect, `water_breathing` grants a `level × 15%` chance to cancel the drowning damage (`:66-70`).

**`onEntityDamageByEntity`** — `MONITOR`, `ignoreCancelled` (`AlchemyListener.java:76-97`): resolves the attacker either directly or through a `Projectile`'s shooter (`:80-87`); a `burning` effect sets the victim's fire ticks to `max(current, level × 2 × 20)` — 2 seconds of fire per level (`:89-96`).

**`onSplash(PotionSplashEvent)`** — `HIGH` (`AlchemyListener.java:99-123`): cancels the vanilla splash for Valmora potions and applies the effect directly to affected entities (`event.getAffectedEntities()`):

- `DEBUFF` → applied to **every** affected `LivingEntity` (`:117-118`);
- `BUFF` → applied to **players only** (`:119-121`).
- Effect type is read from the definition, defaulting to `BUFF` if the id no longer resolves (`:113-114`).

Note: there is **no distance/radius scaling** — every affected entity gets the full level and full duration, and `alchemy.splash-radius` from `config.yml` is not consumed anywhere (see §8).

There is also an **unused import** of `LingeringPotionSplashEvent` (`AlchemyListener.java:11`) — lingering potions are not handled at all.

### 3.8 Stat Integration

The recalc pipeline in `StatManager.recalculateStats` (`StatManager.java:83-193`) calls `alchemyManager.applyEffectsToStats(player, this)` at `StatManager.java:138-141` — after items/enchants and before accessory/pet/set-bonus/temporary services. Alchemy stat values are therefore **modifier-only** (like set bonuses and temporary buffs, per `docs/modules/design/stat.md` §7) — they are wiped and re-applied on every recalculation and never written to base stats. The active-effect lifecycles that trigger recalc are: apply, remove, and expiry (`AlchemyManager.java:96`, `:103`, `:137`).

The hardcoded HP-side mechanics (`healing`, `poison`, `damage`, `absorption`) bypass the stat system entirely and mutate `PlayerState` directly (`HealingAlchemyEffect.java:47`, `PoisonAlchemyEffect.java:33`, `DamageAlchemyEffect.java:27`), then sync the health bar via `PlayerManager.syncVisualHealth`.

### 3.9 GUI & Script Integration

**`AlchemyVariableProvider`** (`AlchemyVariableProvider.java:16-95`) — a `VariableProvider` under namespace `alchemy`, registered via `plugin.getScriptModule().registerProvider(...)` (`AlchemyModule.java:54`). Resolves:

- `$alchemy.effects.count$` → number of non-expired active effects (`:34-40`, `:37`).
- `$alchemy.effects.list$` → a **JSON array** (built with Gson) where each entry has `id`, `level`, `remaining` (seconds), and — when the definition still resolves — `name` (`"Name III"`), `type` (`BUFF`/`DEBUFF`), `material` (`LIME_DYE` for BUFF, `RED_DYE` for DEBUFF), `rarity`, and a `stats` array of `{name, value}` objects (`:49-87`). Unknown/removed effect ids fall back to `name = "<id> N"`, `type = "UNKNOWN"`, `material = GLASS_BOTTLE`, `rarity = COMMON` (`:76-82`).

This powers `guis/active_effects.yml`, a paginated GUI with per-state items driven by `$effect.type$` (`active_effects.yml:20-44`).

**GUI event factories** (owned by the `gui` module, registered in `GuiModule.onEnable()`, `GuiModule.java:50-51`):

- **`gui_alchemy_start`** (`AlchemyBrewStartEventFactory.java:36-129`) — called when a brew cycle begins. Finds the `ingredient` input slot and a sample `bottle` input slot from the GUI layout (`:60-99`), runs `engine.match(def.getMachine(), {base: sampleBottle, ingredient: ingredientItem}, player)` (`:101-107`), stores the matched recipe's result in the session prop `brew_result` (`:120-121`), **consumes one ingredient** from the I slot immediately (`:123-125`), and re-renders. Throws `ConditionAbortException` when no recipe matches, aborting the start sequence (`:76-78`, `:97-99`, `:108-110`).
- **`gui_alchemy_brew`** (`AlchemyBrewEventFactory.java:29-84`) — completes the brew at timer zero: takes `brew_result` out of the session props (no-op if absent), then **replaces every non-empty `bottle` slot** with a clone of the result (`:52-77`). It re-uses the GUI crafting lock (`session.setCraftingLocked`, `:48-49`, `:81`) to prevent item-swap race conditions.

The `guis/alchemy.yml` GUI wires these together with a `machine: alchemy` tag, an `update-interval: 20` tick, and `on-update` script that counts down `brew_time` (10 seconds) and fires `gui_alchemy_brew` / `gui_alchemy_start` (see §4 for the full walkthrough).

### 3.10 Commands

Registered **after** all modules enable in `Valmora.onEnable()` (`Valmora.java:243-244`) — never inside the module (per AGENTS.md §6.3).

**`PotionCommand`** (`PotionCommand.java:18-80`) — `/potion give <effect_id> <level> [player]`:

- Permission: `valmora.admin` (checked both in `plugin.yml:34` and inline at `PotionCommand.java:30`).
- Target resolution: optional `[player]` arg via `Bukkit.getPlayer` (`:44-50`), else the sender if they are a player (`:51-52`), else an error (`:53-56`).
- Level is parsed with `parseIntOrDefault(..., 1)` (`:42`, `:77-79`) and clamped to `[1, effect.getMaxLevel()]` (`:65`).
- Builds the item through a **fresh** `AlchemyMachineHandler` each call — `handler.buildPotion(effect, clampedLevel, effect.getDuration(clampedLevel), false, false, false)` (`:68-69`) — and adds it to the target's inventory (`:70`). Splash/level-modified/duration-modified all false.
- No tab completion is registered.

**`EffectsCommand`** (`EffectsCommand.java:11-35`) — `/effects`:

- Players only (`:21-24`).
- Requires a `active_effects` GUI in the `GuiModule` registry, else prints a hint to add `active_effects.yml` to the guis folder (`:26-30`).
- Opens the GUI with empty props: `plugin.getGuiModule().openGui(player, "active_effects", new HashMap<>())` (`:32`).

---

## Configuration (YAML)

Config lives in `plugins/Valmora/alchemy/*.yml` (auto-extracted from the jar's `alchemy/` folder by `Valmora.saveAllResources()`, `Valmora.java:471`, only when missing). Two files ship: `effects.yml` (potion definitions) and `modifiers.yml` (transformation items). `healing_boost.yml` is a legacy placeholder containing only a comment.

### 4.1 `config.yml` — `alchemy:` section (`config.yml:127-135`)

| Key | Default | Used by | Explanation |
|---|---|---|---|
| `alchemy.splash-radius` | `4.0` | **nobody** | Documented as "Block radius for splash potion area of effect". **Not referenced anywhere in Java** (verified: no consumer). See §8. |
| `alchemy.tick-interval` | `20` | `AlchemyModule.java:59` | Ticks between active-effect expiry checks and `onTick` hooks (20 = 1 s). Read in `onEnable()`; becomes the repeating task's period. |
| `alchemy.max-active-effects` | `10` | `AlchemyModule.java:34` | Max concurrent active effects per player (read in the module **constructor**, so it is *not* picked up on `/valmora reload` without a full restart). Enforced only for players, only when adding a *new* effect (`AlchemyManager.java:86`). |

### 4.2 Effect schema — `alchemy/effects.yml`

Each top-level key is the effect **ID** (registry key, stored lowercase). Parsed by `AlchemyEffectLoader.parse` (`AlchemyEffectLoader.java:37-123`).

```yaml
<effect-id>:
  name: "<MiniMessage display name>"   # default: the id
  type: BUFF | DEBUFF                  # default: BUFF
  rarity: COMMON|UNCOMMON|RARE|EPIC|LEGENDARY   # default: COMMON
  color: "#RRGGBB"                     # default: purple (255,85,255)
  lore: ["<line>", ...]                # default: []
  max-level: <int>                     # default: 1
  duration: [<sec_lvl1>, <sec_lvl2>, ...]  # default: [60]
  stats:                               # optional; unknown stat ids are silently dropped
    <stat_id>: [<value_lvl1>, <value_lvl2>, ...]
  tiers:                               # preferred; or list-of-maps; or single `ingredient:`
    - ingredient: <material|item_id>   #   "minecraft:x" | vanilla material | custom item id
      level: <int>                     #   base level this ingredient produces
```

**Field reference:**

| Field | Type | Default | Required | Parser site | Effect |
|---|---|---|---|---|---|
| *(top-level key)* | string | — | yes | `AlchemyEffectLoader.java:37` | Effect id; PDC value, registry key, lore, command argument. Stored lowercase. |
| `name` | MiniMessage string | the id | no | `AlchemyEffectLoader.java:39` | Item display prefix; rendered as `name + " " + roman(level)` (`AlchemyMachineHandler.java:202`). |
| `type` | `BUFF`/`DEBUFF` | `BUFF` | no | `AlchemyEffectLoader.java:40-41` | Lore color/sign (`AlchemyMachineHandler.java:213-218`), GUI dye (`AlchemyVariableProvider.java:62`), splash targeting (`AlchemyListener.java:116-121`). Invalid value → parse failure. |
| `rarity` | string | `COMMON` | no | `AlchemyEffectLoader.java:42` | Upper-cased; bottom lore line in a rarity color (`AlchemyMachineHandler.java:305-313`). |
| `color` | hex string | purple | no | `AlchemyEffectLoader.java:44-50` | Potion liquid color via `PotionMeta.setColor`. |
| `lore` | list | `[]` | no | `AlchemyEffectLoader.java:52` | MiniMessage lines rendered above the stat block. |
| `max-level` | int | `1` | no | `AlchemyEffectLoader.java:94` | Absolute level ceiling; clamps tier levels and `/potion` levels (`AlchemyMachineHandler.java:75`, `PotionCommand.java:65`) and caps level modifiers (`AlchemyMachineHandler.java:119`). |
| `duration` | list of ints | `[60]` | no | `AlchemyEffectLoader.java:96-101` | Base duration in **seconds** per level; `getDuration` clamps to last index. |
| `stats` | map | `{}` | no | `AlchemyEffectLoader.java:103-117` | Per-level stat values. Keys lowercased; **unknown stat ids silently dropped** (no warning). Used by `applyEffectsToStats` and item lore. |
| `tiers` | section \| list \| — | — | yes* | `AlchemyEffectLoader.java:56-88` | Ingredient → base-level mapping. `*` either `tiers` or a legacy single `ingredient` key is required or the effect fails to load (`:90-92`). |
| `tiers[].ingredient` | string | — | yes | `AlchemyEffectLoader.java:62`, `:72` | Canonicalized by `resolveIngredientKey` (`:23-35`). |
| `tiers[].level` | int | `1` | no | `AlchemyEffectLoader.java:63`, `:74-75` | Base level produced by that ingredient. |
| `ingredient` | string | — | no (legacy) | `AlchemyEffectLoader.java:83-88` | Legacy single ingredient, becomes a level-1 tier. |

#### Shipped effects — `effects.yml`

All 19 effects as shipped (`effects.yml`):

| ID | Type | Rarity | Color | max | Duration/level (s) | Stats/level | Base tier ingredients (level) |
|---|---|---|---|---|---|---|---|
| `speed` | BUFF | COMMON | `#FFFF44` | 8 | 60–130 (+10) | `speed` 5→40 (+5) | SUGAR (1), enchanted_sugar (3), enchanted_sugar_cane (5) |
| `jump_boost` | BUFF | COMMON | `#88FF88` | 4 | 60–90 (+10) | — | RABBIT_FOOT (1) |
| `healing` | BUFF | UNCOMMON | `#FF6666` | 8 | 1 (all) | — | SPIDER_EYE (1) |
| `poison` | DEBUFF | UNCOMMON | `#44AA44` | 4 | 30–45 (+5) | — | GLISTERING_MELON_SLICE (1), enchanted_glistering_melon (3), enchanted_blistering_melon (**5**, clamped to 4 — see §8) |
| `water_breathing` | BUFF | UNCOMMON | `#0088FF` | 6 | 120–180 | — | PUFFERFISH (1), enchanted_pufferfish (3) |
| `fire_resistance` | BUFF | RARE | `#FF8800` | 1 | 120 | — | MAGMA_CREAM (1) |
| `night_vision` | BUFF | UNCOMMON | `#1111AA` | 2 | 180, 240 | — | GOLDEN_CARROT (1) |
| `strength` | BUFF | UNCOMMON | `#AA0000` | 8 | 60–130 (+10) | `strength` 5, 12.5, 20, 30, 40, 50, 60, 75 | BLAZE_POWDER (1), enchanted_blaze_powder (3), enchanted_blaze_rod (5) |
| `invisibility` | BUFF | RARE | `#AAAAAA` | 1 | 60 | — | FERMENTED_SPIDER_EYE (1) |
| `regeneration` | BUFF | UNCOMMON | `#FF44FF` | 8 | 60–130 (+10) | `health_regen` 5→40 (+5) | GHAST_TEAR (1), enchanted_ghast_tear (3), concentrated_ghast_tear (5) |
| `weakness` | DEBUFF | UNCOMMON | `#776677` | 6 | 60–110 (+10) | `damage` −5→−30 (−5) | ROTTEN_FLESH (1), enchanted_rotten_flesh (3) |
| `slowness` | DEBUFF | COMMON | `#4488FF` | 6 | 60–110 (+10) | `speed` −5→−30 (−5) | TURTLE_SCUTE (1), enchanted_turtle_scute (3) |
| `damage` | DEBUFF | UNCOMMON | `#CC0000` | 6 | 1 (all) | — | CACTUS (1), enchanted_cactus (3) |
| `haste` | BUFF | COMMON | `#FFAA00` | 4 | 60–90 (+10) | `mining_speed` 50→200 (+50) | COAL (1) |
| `burning` | BUFF | UNCOMMON | `#FF4400` | 4 | 60–90 (+10) | — | RED_SAND (1) |
| `absorption` | BUFF | UNCOMMON | `#FFDD00` | 8 | 60–130 (+10) | — | GOLD_INGOT (1), enchanted_gold_ingot (3), enchanted_gold_block (5) |
| `critical` | BUFF | RARE | `#FFBB00` | 4 | 60–90 (+10) | `crit_chance` 10/15/30/25, `crit_damage` 10/20/30/40 | FLINT (1) |
| `resistance` | BUFF | UNCOMMON | `#44FF44` | 8 | 60–130 (+10) | `defense` 10→80 (+10) | NAUTILUS_SHELL (1), enchanted_nautilus_shell (3), hardened_nautilus_shell (5) |
| `mana` | BUFF | UNCOMMON | `#00FFFF` | 8 | 60–130 (+10) | `mana_regen` 1→8 (+1) | MUTTON (1), enchanted_mutton (3), enchanted_cooked_mutton (5) |

The tier-2/tier-3 custom ingredients are defined in `items/alchemy_ingredients.yml` (each an `item-type: NONE` Valmora item on the base vanilla material, rarities UNCOMMON/RARE). Note `docs/POTION_LIST.md` records the intended values and matches the shipped stat/duration tables, with the exception of the poison tier-5 clamp.

### 4.3 Modifier schema — `alchemy/modifiers.yml`

Loaded **outside** `YamlLoader` by `AlchemyModule.loadModifiers` (`AlchemyModule.java:110-124`) — plain `YamlConfiguration` reads of three list sections. Each entry is a map with an `item` key; unknown/invalid entries are skipped with a warning (`AlchemyModule.java:155-157`).

```yaml
level:                       # once per potion; +levels
  - item: "<item-id>"        #   "minecraft:<material>" or custom Valmora item id
    bonus: <int>             #   levels added (LEVEL only)
    requires-max-base: <bool>
duration:                    # once per potion; absolute duration override (seconds)
  - item: "<item-id>"
    seconds: <int>           #   DURATION only
    requires-max-base: <bool>
splash:                      # once per potion; converts to splash
  - item: "<item-id>"
    duration-multiplier: <double>   #   SPLASH only — multiplies current duration
    requires-max-base: <bool>
```

| Field | Type | Default | Required | Consumer | Effect |
|---|---|---|---|---|---|
| `item` | string | — | yes | `AlchemyModule.java:134-135` | Ingredient key matched by `getItemKey` (`AlchemyMachineHandler.java:234-242`). `minecraft:<material>` or a custom item id. |
| `bonus` | int | — | for `level` | `AlchemyModule.java:141-142` | Levels added; the result is clamped to `effect.getMaxLevel()` (`AlchemyMachineHandler.java:119`). |
| `seconds` | int | — | for `duration` | `AlchemyModule.java:145-146` | Absolute duration in seconds. Applied as-is, or × 0.5 when the potion is already splash (`AlchemyMachineHandler.java:128-130`, `:145-149`). |
| `duration-multiplier` | double | — | for `splash` | `AlchemyModule.java:149-150` | Multiplied against the current duration when converting to splash (`AlchemyMachineHandler.java:137`). |
| `requires-max-base` | bool | `false` | no | `AlchemyModule.java:137` | When `true`, only applies if `currentLevel == effect.getMaxBaseLevel()` (`AlchemyMachineHandler.java:117`, `:125`, `:136`). |

Shipped entries (`modifiers.yml:22-54`):

| Section | item | Value | requires-max-base |
|---|---|---|---|
| level | `minecraft:glowstone_dust` | bonus 1 | false (the only exception) |
| level | `enchanted_glowstone_dust` | bonus 2 | true |
| level | `enchanted_glowstone` | bonus 3 | true |
| duration | `minecraft:redstone` | 480 s (8 m) | true |
| duration | `enchanted_redstone` | 960 s (16 m) | true |
| duration | `enchanted_redstone_block` | 2400 s (40 m) | true |
| splash | `minecraft:gunpowder` | ×0.5 (−50%) | true |
| splash | `enchanted_gunpowder` | ×1.0 (no penalty) | true |

Because `requires-max-base: true` on every duration/splash/level-modifier except regular glowstone, the typical upgrade path is: brew to max base tier, then apply the powerful modifiers.

### 4.4 `healing_boost.yml`

Contains only `# Effects moved to effects.yml` — a leftover from when healing was a separate file. It parses to zero effects (no top-level sections).

### 4.5 Related config (owned by other modules)

- **`items/alchemy_ingredients.yml`** — the custom tier-2/tier-3 ingredients and the `enchanted_*` modifier items; defines the item ids referenced by `effects.yml`/`modifiers.yml`.
- **`guis/alchemy.yml`** — the Alchemy Table GUI (`machine: alchemy`, 6 rows, `update-interval: 20`). Layout legend: `B` = purple-glass border display, `I` = ingredient input, `T` = status display (uses `$prop.brew_status$` / `$prop.brew_time$`), `P` = bottle inputs (replaced with the result at completion), `C` = close button (`guis/alchemy.yml:7-20`, `:22-59`). Brew flow: `on-open` resets props and plays the brewing-stand sound (`:61-66`); `on-slot-update` resets status when nothing useful is present (`:68-76`); `on-update` counts `brew_time` down from 10 and fires `gui_alchemy_brew` at zero, or — when idle with ingredient + bottles present — fires `gui_alchemy_start` to validate/consume (`:78-102`). **No `command:` key** — the table cannot be opened by any stock command (see §8).
- **`guis/active_effects.yml`** — paginated Active Effects GUI driven by `$alchemy.effects.list$` with `buff`/`debuff`/`default` item states (`active_effects.yml:18-46`) and previous/next page arrows.
- **`recipes/alchemy.yml`** — 13 static SHAPELESS recipes on `machine: alchemy` (awkward, thick, healing, strength, fire resistance, night vision, swiftness, each with `_bottle` and `_potion` variants) that produce a plain `POTION` output and grant `player.var.alchemy_xp` on craft (`recipes/alchemy.yml:14-244`). These are a legacy XP path distinct from the dynamic pipeline.
- **`skills/alchemy.yml`** — the Alchemy skill (`max-level: 60`, per-level `coins = level*5`, milestones 10 → BLAZE_POWDER×5 and 30 → NETHER_WART×16) but with **no `sources`**, so brewing grants no XP out of the box (`SkillListener.java:118-130` hard-codes the `BREW_POTION`/`ANY` lookup).

---

## Data Model / Persistence

There is **no database persistence** for alchemy. Active effects are in-memory only (`AlchemyManager.activeEffects`, `ConcurrentHashMap<UUID, List<ActiveEffect>>`, `AlchemyManager.java:27`) and are lost on restart — and deliberately **not** cleared on module reload (§3.6). Brewed potions are fully self-contained items whose state lives in the PDC and travels through inventories/chests/trades like any other item.

**PDC keys used** (all defined in `util/Keys.java:16-21`, initialized at `Keys.java:52-57`):

| Key | NamespacedKey | Type | Written by | Read by |
|---|---|---|---|---|
| `ALCHEMY_EFFECT_ID` | `alchemy_effect_id` | STRING | `buildPotionItem` (`AlchemyMachineHandler.java:193`) | Listener apply/splash (`AlchemyListener.java:34`, `:105`), `matchModifier` (`AlchemyMachineHandler.java:100`) |
| `ALCHEMY_EFFECT_LEVEL` | `alchemy_effect_level` | INTEGER | `AlchemyMachineHandler.java:194` | `AlchemyListener.java:44`, `:110`; `matchModifier` (`:106`) |
| `ALCHEMY_DURATION` | `alchemy_duration` | INTEGER | `AlchemyMachineHandler.java:195` | `AlchemyListener.java:45`, `:111`; `matchModifier` (`:107`) |
| `ALCHEMY_IS_SPLASH` | `alchemy_is_splash` | BYTE (0/1) | `AlchemyMachineHandler.java:196` | `AlchemyListener.java:38`, `:108`; `matchModifier` (`:108`) |
| `ALCHEMY_LEVEL_MODIFIED` | `alchemy_level_modified` | BYTE (0/1) | `AlchemyMachineHandler.java:197` | `matchModifier` (`:109`) |
| `ALCHEMY_DURATION_MODIFIED` | `alchemy_duration_modified` | BYTE (0/1) | `AlchemyMachineHandler.java:198` | `matchModifier` (`:110`) |
| `ITEM_ID_KEY` | `valmora_item_id` | STRING (`alchemy:<id>` or `awkward_potion`) | `buildPotionItem` (`:199`), `buildAwkwardPotion` (`:166`) | `getItemKey`/`isWaterBottle`/`isAwkwardPotion` (`:236-241`, `:251-253`, `:264-266`) |
| `RARITY_KEY` | `rarity` | STRING | `buildAwkwardPotion` (`:167`) | Item system display |

**Effective timing model:** durations are wall-clock seconds (`System.currentTimeMillis()`, `AlchemyManager.java:88`, `ActiveEffect.java:5-11`), evaluated on the module's tick task (`AlchemyModule.java:60-64`). The `tick-interval` therefore controls how granularly expiry/`onTick` run, not how long an effect lasts.

---

## API Exposed

**Via `ValmoraAPI`** (`ValmoraAPI.java:51`, implemented at `Valmora.java:364-366`):

```java
AlchemyManager alchemy = ValmoraAPI.getInstance().getAlchemyManager();
```

`AlchemyManager` public surface:

- `Optional<AlchemyEffect> getEffect(String id)` — case-insensitive definition lookup (`AlchemyManager.java:56-58`).
- `Map<String, AlchemyEffect> getAllEffects()` — unmodifiable view (`AlchemyManager.java:68-70`).
- `Optional<BrewTier> getBrewTier(String ingredientKey)` (`AlchemyManager.java:60-62`) and `record BrewTier(AlchemyEffect effect, int baseLevel)` (`AlchemyManager.java:25`).
- `Optional<AlchemyModifier> getModifier(String itemId)` (`AlchemyManager.java:64-66`).
- `void applyEffect(LivingEntity, String effectId, int level, int durationSeconds)` — programmatic effect application (stat recalc + hardcoded `onApply` included) (`AlchemyManager.java:80-97`).
- `void removeEffect(LivingEntity, String effectId)` (`AlchemyManager.java:99-104`), `List<ActiveEffect> getActiveEffects(UUID)` (`:106-110`), `void clearAllEffects(UUID)` (`:112-114`, currently uncalled).
- `void registerEffect(AlchemyEffect)` / `registerHardcodedEffect(HardcodedAlchemyEffect)` / `registerModifier(AlchemyModifier)` — extension points. **Caveat:** `effectRegistry`/`ingredientIndex`/`modifierRegistry` are cleared on every module `clear()` (reload), so YAML-loaded registrations are rebuilt automatically but externally registered effects/modifiers would need re-registering after `/valmora reload`.
- `void tick(Player)` and `void applyEffectsToStats(Player, StatManager)` — internal hooks, public for the Stat/Skill integration.

**Item-building API:** `AlchemyMachineHandler.buildPotion(AlchemyEffect, int level, int durationSeconds, boolean isSplash, boolean levelModified, boolean durationModified)` (`AlchemyMachineHandler.java:178-181`) is public and is how `/potion` builds potions (`PotionCommand.java:68-69`).

**Script variables:** namespace `alchemy` → `$alchemy.effects.count$` and `$alchemy.effects.list$` (JSON) (`AlchemyVariableProvider.java:18-46`).

**Script events (registered by `GuiModule`, not the alchemy module):** `gui_alchemy_start` (`AlchemyBrewStartEventFactory.java:46`) and `gui_alchemy_brew` (`AlchemyBrewEventFactory.java:39`).

**Commands:** `/potion` (admin) and `/effects` (all players), registered in `Valmora.onEnable()` (`Valmora.java:243-244`, definitions in `plugin.yml:32-38`).

---

## Dependencies & Consumers

### Dependencies (loads-after)

| Dependency | Why |
|---|---|
| Recipe (`RecipeModule`/`RecipeEngine`) | Registers the `"alchemy"` `DynamicMachineHandler` (`AlchemyModule.java:51-52`); the `gui_alchemy_start` event validates brews through `RecipeEngine.match` (`AlchemyBrewStartEventFactory.java:106-107`). |
| Script (`ScriptModule`) | Registers `AlchemyVariableProvider` (`AlchemyModule.java:54`); GUI events are `EventFactory`s. |
| Stat (`StatModule`/`StatRegistry`/`StatManager`) | `StatRegistry` validates stats at parse time (`AlchemyEffectLoader.java:103-109`) and resolves display names in lore/GUI (`AlchemyMachineHandler.java:210-216`, `AlchemyVariableProvider.java:65-73`); `StatManager.recalculateStats` calls `applyEffectsToStats` (`StatManager.java:138-141`). |
| Skill (`SkillModule`/`SkillRegistry`) | Brew duration bonus reads the player's Alchemy skill level (`AlchemyMachineHandler.java:281-294`). |
| Profile (`PlayerManager`/`ValmoraProfile`/`PlayerState`) | `recalculatePlayerStats` (`AlchemyManager.java:159-165`); healing/poison/damage mutate `PlayerState` + `syncVisualHealth`. |
| Items (`ItemManager`/`ItemRegistry`) | `getItemKey` reads `ITEM_ID_KEY` (`AlchemyMachineHandler.java:236-239`); custom ingredients live in `items/alchemy_ingredients.yml`. |
| GUI (`GuiModule`) | `EffectsCommand` opens `active_effects` (`EffectsCommand.java:26-32`); the alchemy table GUI consumes the machine. |
| Config (`plugin.yml`/`config.yml`) | `alchemy.tick-interval`, `alchemy.max-active-effects`; command declarations. |

### Consumers (who calls the module)

| Consumer | How it uses alchemy | Sites |
|---|---|---|
| Stat `StatManager` | Applies active-effect stat modifiers during recalc | `StatManager.java:138-141` |
| GUI `guis/alchemy.yml` | Dynamic brewing machine; `gui_alchemy_start`/`gui_alchemy_brew` events validate through the handler | `guis/alchemy.yml:4`, `:84`, `:99` |
| GUI `guis/active_effects.yml` | `$alchemy.effects.list$` paginated display | `active_effects.yml:20` |
| Skill `SkillListener` | `BREW_POTION`/`ANY` XP award on vanilla `BrewEvent` (unrelated to Valmora potions in practice) | `SkillListener.java:118-130` |
| Recipe engine static recipes | Same `machine: alchemy` namespace, legacy XP path | `recipes/alchemy.yml:15` |
| `PotionCommand` | Builds admin potions via `buildPotion` | `PotionCommand.java:68-69` |
| Quest module | `QuestObjectiveTypes.BREW` is **declared but unimplemented** (no handler) | `QuestObjectiveTypes.java:21` |

---

## Unfinished Things / TODOs

- **`alchemy.splash-radius` is dead config.** `config.yml:129` documents a splash radius of `4.0`, but no Java code reads it. `onSplash` applies the full effect to every entity in `event.getAffectedEntities()` with no distance/radius scaling (`AlchemyListener.java:116-121`). `docs/USER_DOCS.md:825` and `docs/TESTING_GUIDE.md:97-104` describe splash-radius behavior that does not exist in code.
- **Unused `LingeringPotionSplashEvent` import.** `AlchemyListener.java:11` imports it but no handler references it — lingering potions fall through to vanilla behavior entirely.
- **No stock way to open the Alchemy Table GUI.** `guis/alchemy.yml` has no `command:` key (GUI commands are bound only via that key, `GuiModule.java:202-219`). It is only reachable through an `open_gui` action from another GUI — same gap as the enchanting table (see `docs/modules/design/enchant.md` §8).
- **Poison tier-5 clamp.** `effects.yml:71-72` gives `enchanted_blistering_melon` tier level 5 while `poison`'s `max-level` is 4; `baseLevel = min(tier.level(), maxLevel)` (`AlchemyMachineHandler.java:75`) silently produces a level-4 poison. `docs/POTION_LIST.md:6` documents the third ingredient at level 5, so the definition is internally inconsistent.
- **Consumption assumes the main hand.** `onDrink` decrements only `player.getInventory().getItemInMainHand()` when `hand.isSimilar(item)` (`AlchemyListener.java:53-56`). Drinking from the off-hand or via the GUI leaves the potion unconsumed.
- **Active effects are never cleared.** `AlchemyManager.clear()` (`:72-76`) skips `activeEffects` and `hardcodedEffects`, so a `/valmora reload` keeps all in-memory effects and their pending expiry/`onTick`. Intentional or not, it is undocumented behavior.
- **`maxActiveEffects` is constructor-bound.** Read in the `AlchemyModule` constructor (`AlchemyModule.java:34`) from `config.yml`, so editing `alchemy.max-active-effects` requires a restart — a reload won't pick it up.
- **Dead code:** `AlchemyManager.removeEffect` (`:99-104`) and `clearAllEffects` (`:112-114`) have no callers; `AlchemyEffect.getTierForIngredient` (`AlchemyEffect.java:57-59`) is unused (tier indexing happens in `registerEffect`).
- **Modifiers are one-shot and category-tracked by boolean flags only.** `getSplashMultiplier` is hardcoded to `0.5` (`AlchemyMachineHandler.java:145-149`) because which splash modifier was used isn't stored — the code comment itself flags this.
- **Poison only ticks for players.** `PoisonAlchemyEffect.onTick(Player, ...)` (`PoisonAlchemyEffect.java:25`) is called only from the player-scoped tick loop (`AlchemyManager.java:118-138`); a poisoned mob receives the `ActiveEffect` record (and DEBUFF splash applies to mobs, `AlchemyListener.java:117-118`) but takes no DOT damage.
- **Skill XP gap.** Brewing grants no Alchemy XP out of the box: `skills/alchemy.yml` defines no `sources`, and `SkillListener.onBrew` hard-codes identifier `"ANY"` (`SkillListener.java:120`) which can only match an `ANY` or `DEFAULT` entry. The static `recipes/alchemy.yml` does add `player.var.alchemy_xp` on craft, but that variable is unrelated to the skill system.
- **`BREW` quest objective is unimplemented.** `QuestObjectiveTypes.java:21` declares `BREW = "brew"` and `docs/Objective_list.md:85-89` documents it, but no objective handler/type class exists.
- **Static recipe path is degenerate.** The `recipes/alchemy.yml` SHAPELESS recipes output a plain `POTION` item (base type water) — a "swiftness" brew through the static path produces a water potion, not a Valmora potion. The dynamic handler is the only real brewing path.
- **Docs drift.** `docs/USER_DOCS.md:754-825` documents the old single-`ingredient` schema, uppercase stat keys (`HEALTH_REGEN`), and `alchemy.splash-radius` usage — none of which match the shipped `tiers` format, lowercase stat ids, or the unused radius. `docs/VALMORA_DOCUMENTATION.md:303` describes `getAlchemyManager()` as "Access alchemy recipes and brew state", which is loosely accurate but undersells the runtime effect engine.

---

## Possible Improvements / Changes

- **Wire `splash-radius` into `onSplash`** — scale level/duration by distance from impact (or pick entities by radius), and/or apply per-entity duration like vanilla splash does, instead of the all-or-nothing application in `AlchemyListener.java:116-121`.
- **Track which splash modifier was used** via an extra PDC key so `getSplashMultiplier` (`AlchemyMachineHandler.java:145-149`) returns the real multiplier instead of the hardcoded `0.5`.
- **Consume from the actual slot.** Store/restore `ItemStack` state in `onDrink` or use `event.getItem()` mutation + `event.setCancelled(true)` with a slot-aware decrement, removing the main-hand assumption (`AlchemyListener.java:53-56`).
- **Fix the poison tier.** Either raise `max-level` to 5 for `poison` or drop the level-5 tier in `effects.yml:71-72` so `POTION_LIST.md` matches runtime behavior.
- **Persist or explicitly clear active effects.** Either add a database table for active effects (surviving restart) or document + intentionally clear them on reload; right now the behavior is accidental.
- **Read `max-active-effects` in `onEnable()`** instead of the constructor so a reload honors config changes.
- **Allow `onTick` for non-player entities** — generalize `HardcodedAlchemyEffect.onTick` to `LivingEntity` (or a separate tick path) so poison/burning-style DOTs work on mobs.
- **Add tab completion and subcommands to `/potion`** (e.g. `list`, `clear`, `apply`, `splash` flag) and reuse a single `AlchemyMachineHandler` instance instead of constructing one per invocation (`PotionCommand.java:68`).
- **Give the Alchemy Table a command binding** (`command: alchemy` in `guis/alchemy.yml`) or register a vanilla brewing-stand override so players can actually reach it.
- **Wire brewing XP properly** — add `BREW_POTION` sources to `skills/alchemy.yml` and pass the brewed potion's effect id to `SkillListener.onBrew` instead of the hard-coded `"ANY"` (also tracked in `docs/modules/design/skill.md` §8).
- **Validate tier levels at parse time** — warn when `tier.level() > max-level` instead of silently clamping at brew time (`AlchemyEffectLoader.java:37-123`).
- **Add unit tests.** There are none for the alchemy module; the stat/`AlchemyVariableProvider`/`AlchemyMachineHandler` logic (esp. modifier combination order) is a good Mockito target following the `ExpressionTest` pattern (see AGENTS.md §9).
