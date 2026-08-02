# Reforge Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.reforge` | **Module ID:** `reforge` | **Name:** "Reforge System"
> **Dependencies:** `recipeModule` (registers dynamic machine handlers), `itemModule` (base stats, item type/rarity PDC, lore regeneration), `statModule` (`saveStats`, `StatRegistry`/`StatDefinition` formatting), `economy` (coin costs via `EconomyService`)

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

The **Reforge Module** applies **rarity-scaled stat bonuses** to custom items. A *reforge* is a named modifier definition (e.g. "Fierce", "Sharp", "Fortified") that grants a fixed bonus per item rarity tier. Reforging **replaces** the previous reforge — bonuses never stack.

The module has **zero UI of its own**: players interact with reforging exclusively through two **GUI machines** defined by the GUI module (`guis/reforge_anvil.yml`, `guis/reforge.yml`). When the player presses the craft button in those GUIs, the `gui_force_craft` script action (`GuiForceCraftEventFactory.java:30-79`) routes a craft request to the recipe engine for the machine id, which in turn invokes this module's `DynamicMachineHandler` implementations. There are two entry paths:

1. **Reforge Anvil** (`machine: reforge_anvil`, `guis/reforge_anvil.yml:17`) — takes `base_item` + `reforge_stone`; the stone's embedded reforge id is applied deterministically. Both items are consumed.
2. **Random Forge** (`machine: forge_random`, `guis/reforge.yml:15`) — takes only `base_item`; a random valid reforge (excluding the item's current one) is applied. The item is consumed and replaced in-place by the reforged output.

Both paths deduct a **coin cost that scales with the item's rarity**, hard-coded in the `RARITY_COST` table (`ReforgeModule.java:29-39`). The craft only matches if the player has enough coins; insufficient funds produces a chat warning and the recipe simply does not match.

Module lifecycle follows the standard `ReloadableModule` contract (`docs/MODULE_DEVELOPMENT.md` §2) — `onEnable()`/`onDisable()` are idempotent and hot-reload safe via `/valmora reload`. Note that this module is **not** exposed through the `ValmoraAPI` interface; it is reachable only through the concrete `Valmora` plugin class (`Valmora.java:425`).

---

## 2. Code Structure

The module is the smallest of the codebase: **two classes** plus a single resource folder. It does not follow the usual `XModule` / `XListener` / `XRegistry` / `XLoader` layout (`AGENTS.md` §3) — there is no listener, no dedicated registry class, and no dedicated loader; definition storage and YAML parsing live directly inside the module class.

```
src/main/java/org/nakii/valmora/module/reforge/
├── ReforgeModule.java       # ReloadableModule + DynamicMachineHandler — state, matching, core logic, stone creation, YAML parsing
└── ReforgeDefinition.java   # Immutable data class — stat bonuses per rarity + appliesTo/fallback predicates

src/main/resources/reforges/
└── combat.yml               # Default reforges (copied to plugins/Valmora/reforges/)
```

### 2.1 `ReforgeModule.java` (341 lines)

`ReforgeModule.java:27` — `public class ReforgeModule implements ReloadableModule, DynamicMachineHandler`. The module class **is itself** the `DynamicMachineHandler` for the legacy `reforge` machine id; the two live machine ids use anonymous handler instances.

| Member | Line | Purpose |
|---|---|---|
| `RARITY_COST` (static `EnumMap<Rarity,Integer>`) | `ReforgeModule.java:30-39` | Fixed coin cost to reforge, keyed by item rarity (see §4.3). |
| `plugin` (final `Valmora`) | `ReforgeModule.java:41` | Plugin instance (recipe engine, item manager, stat module, economy). |
| `definitions` (`Map<String, ReforgeDefinition>` HashMap) | `ReforgeModule.java:42` | Reforge id → definition. **Keys are NOT lowercased at storage time** — see §5 / §8. |
| `onEnable()` | `ReforgeModule.java:48-69` | Clears + reloads definitions, then registers three dynamic machine handlers. |
| `onDisable()` | `ReforgeModule.java:71-74` | Clears the definition map only. Does **not** unregister the handlers (see §8). |
| `getId()` / `getName()` | `ReforgeModule.java:77` / `:80` | `"reforge"` / `"Reforge System"`. |
| `getDefinitions()` / `getDefinition(String)` | `ReforgeModule.java:82` / `:84` | Read access; `getDefinition` lowercases the lookup key. |
| `match(inputs)` | `ReforgeModule.java:88-91` | Returns empty — player context required. |
| `match(inputs, player)` | `ReforgeModule.java:93-96` | Delegates to `matchReforgeAnvil`. |
| `matchReforgeAnvil(inputs, player)` | `ReforgeModule.java:100-132` | Stone-based deterministic reforge. |
| `matchForgeRandom(inputs, player)` | `ReforgeModule.java:136-167` | Random reforge excluding the current one. |
| `buildReforgedItem(baseItem, reforge, rarity)` | `ReforgeModule.java:171-198` | Core stat-merge + PDC write + lore regeneration. |
| `createReforgeStone(def)` | `ReforgeModule.java:203-249` | Builds an `AMETHYST_SHARD` stone item. |
| `readRarity(item)` / `readItemType(item)` | `ReforgeModule.java:253-258` / `:260-265` | Reads PDC keys with safe fallbacks. |
| `isEmpty(item)` | `ReforgeModule.java:267-269` | Air/null/zero-amount check. |
| `checkAndNotifyCoins(player, cost)` / `deductCoins(player, cost)` | `ReforgeModule.java:271-281` / `:283-287` | Economy balance check + withdrawal. |
| `consumeItem(inputs, key)` | `ReforgeModule.java:289-292` | Sets the input stack's amount to 0. |
| `formatCoins(amount)` | `ReforgeModule.java:294-298` | `1.5K` / `2.0M` shorthand for display. |
| `loadDefinitions()` | `ReforgeModule.java:302-305` | `YamlLoader` bootstrap for the `reforges/` folder. |
| `parseDefinition(id, section, filePath)` | `ReforgeModule.java:307-340` | YAML → `ReforgeDefinition`, returns `LoadResult`. |

### 2.2 `ReforgeDefinition.java` (50 lines)

Immutable value object.

| Member | Line | Purpose |
|---|---|---|
| `id` / `name` | `ReforgeDefinition.java:12-13` | Reforge id (raw YAML key) and display name. |
| `applicableTypes` (`List<ItemType>`) | `ReforgeDefinition.java:14` | Item types this reforge can target. |
| `statBonusesByRarity` (`EnumMap<Rarity, Map<String,Double>>`) | `ReforgeDefinition.java:15` | Rarity tier → stat-id → bonus value. Copied defensively in the constructor (`:24`). |
| `generateStone` (boolean) | `ReforgeDefinition.java:16` | Whether `/item give <id>_reforge_stone` is allowed. |
| `getStatBonusesForRarity(rarity)` | `ReforgeDefinition.java:35-44` | Exact tier lookup, else nearest **lower** rarity, else empty map. |
| `appliesTo(type)` | `ReforgeDefinition.java:46-49` | Empty list → matches everything; otherwise matches `ALL` or the exact type. |

### 2.3 Wiring (`src/main/java/org/nakii/valmora/Valmora.java`)

| Step | Line | Code |
|---|---|---|
| Field declaration | `Valmora.java:117` | `private ReforgeModule reforgeModule;` |
| Instantiation | `Valmora.java:178` | `this.reforgeModule = new ReforgeModule(this);` |
| Registration | `Valmora.java:216` | `moduleManager.registerModule(reforgeModule); // Depends on recipeModule (registers handler)` |
| Public getter | `Valmora.java:425` | `public ReforgeModule getReforgeModule() { return reforgeModule; }` |
| Resource seeding | `Valmora.java:476` | `name.startsWith("reforges/")` in `saveAllResources()` — copies jar resources to `plugins/Valmora/reforges/` only if the file does **not** already exist (never overwrites server edits) |

The module is registered after `calendarEventModule` and before `petModule` (`Valmora.java:215-217`). The comment block at `Valmora.java:186-222` is the authoritative load order; `docs/MODULE_DEVELOPMENT.md` §9 (`MODULE_DEVELOPMENT.md:495-517`) is stale and predates most late modules.

**Important registration-order note:** `recipeModule` is enabled earlier (`Valmora.java:202`) than `reforgeModule`, so `plugin.getRecipeModule().getRecipeEngine()` is non-null by the time `ReforgeModule.onEnable()` runs. `reforge` must therefore always load after `recipe`. The current position satisfies that.

---

## 3. Architecture & Key Classes

### 3.1 How a reforge is applied (the full craft flow)

```
Player clicks "Apply Reforge!" button (guis/reforge_anvil.yml, actions gui_force_craft)
  │
  ├─ GuiForceCraftEventFactory (gui_force_craft) — GuiForceCraftEventFactory.java:30-79
  │     • sets crafting-lock (dupe protection, :43-44)
  │     • fresh input snapshot from GUI slots (:52)
  │     • engine.craft(machineId, inputs, player)  ← player IS passed (:55)
  │
  ├─ RecipeEngine.craft — RecipeEngine.java:41-51
  │     • match(machineId, inputs, player) → dynamic handler (RecipeEngine.java:80-86)
  │     • buildOutput: dynamic recipes are isVanilla → clones vanillaResult, then
  │       ItemTranslator.translate() (RecipeEngine.java:53-74, :46)
  │     • consume(recipe, inputs) → the dynamic consume handler (RecipeEngine.java:163-167)
  │
  ├─ ReforgeModule.matchReforgeAnvil — ReforgeModule.java:100-132
  │     • read base_item + reforge_stone, both required
  │     • stone id from REFORGE_POOL_KEY (first id of a comma-joined pool)
  │     • definition lookup, item-type gate, rarity, coin cost
  │     • checkAndNotifyCoins → reject (return empty) if the player is broke
  │     • buildReforgedItem(...) → output stack
  │     • returns RecipeDefinition.dynamic("reforge_anvil", output, consumeHandler)
  │
  └─ consume handler (ReforgeModule.java:127-131)
        • consumeItem(reforge_stone), consumeItem(base_item)  → both set to amount 0
        • deductCoins(player, cost)
```

The output is then placed into the GUI's **output slot** (`reforge_anvil.yml` has an `O`/`OUTPUT` component) by `GuiForceCraftEventFactory` (`:61-68`). For `forge_random`, the `reforge.yml` GUI has **no** output slot, so the factory falls back to the `base_item` input slot (`:62-65`, `GuiForceCraftEventFactory.java:95-107`) — the reforged item replaces the input in place.

### 3.2 `buildReforgedItem` — the stat-reroll core (`ReforgeModule.java:171-198`)

1. **Clone** the base item (`:172`) — material, name, enchantments, abilities survive; only the stat map is rewritten.
2. Read the item's registered id from `Keys.ITEM_ID_KEY` (`:177`).
3. **Base stats are reloaded fresh** from the `ItemDefinition` via `ItemRegistry.getItem(itemId).map(ItemDefinition::getStats)` (`:179-183`). This is what makes reforges **non-stacking**: any previous reforge's contribution to the PDC stat container is discarded, and the definition's clean stats are used as the new baseline.
   - If the item has no registered definition (vanilla-translated items, `itemId` absent or unknown), `baseStats` stays **empty** — the merged output contains **only** the reforge bonuses. See §8.
4. **Merge** reforge bonuses on top with `Double::sum` (`:186-189`). The bonus values come from `reforge.getStatBonusesForRarity(rarity)` (`ReforgeDefinition.java:35-44`), which falls back to the nearest lower rarity tier when the item's exact tier is not defined.
5. **Write the merged map** to the PDC stat container via `StatModule.saveStats(meta, mergedStats)` (`:191`; `StatModule.java:104-114`).
6. **Stamp the reforge identity** onto the item:
   - `Keys.REFORGE_ID_KEY` = reforge id (`:192`)
   - `Keys.REFORGE_DISPLAY_KEY` = reforge display name (`:193`)
7. **Regenerate lore** via `ItemFactory.updateLore(output)` (`:196`). `ItemFactory` prefixes the item display name with the reforge display (`ItemFactory.java:100-104`) and renders the stat section from `StatModule.loadStats(meta)` (`ItemFactory.java:146-158`).

### 3.3 The two machine matchers

#### `matchReforgeAnvil` (`ReforgeModule.java:100-132`) — deterministic, stone-driven

| Check | Lines | Behavior on failure |
|---|---|---|
| Both inputs present | `:104` | `Optional.empty()` |
| Stone carries `REFORGE_POOL_KEY` | `:107-111` | empty if missing/blank |
| Reforge id = first entry of comma-joined pool, lowercased | `:114` | — |
| Definition exists | `:115-116` | empty |
| `def.appliesTo(itemType)` | `:118-119` | empty |
| Coin balance (player context) | `:121-124` | `checkAndNotifyCoins` false → empty + "You need N Coins" message scheduled to main thread (`:276-277`) |
| Build output + dynamic recipe | `:126-131` | — |

Notes:
- `reforgeStones` produced by `createReforgeStone` always have exactly one id in the pool, so "first id" == the id. Custom items built with a `reforge-pool` of several ids (see §4.4) would only ever use the first — the other ids are dead data in the anvil path.
- Because the player overload is required, **no live output preview** is shown: `GuiListener.updateRecipeOutput` (`GuiListener.java:291-309`) and output-click handling (`GuiListener.java:218`) call `engine.match(machineId, inputs)` **without** the player, and both the module's `match(inputs)` (`:88-91`) and the anonymous handler's `match(inputs)` (`:56`) return empty.

#### `matchForgeRandom` (`ReforgeModule.java:136-167`) — random, current-excluding

| Check | Lines | Behavior on failure |
|---|---|---|
| `base_item` present | `:137-138` | empty |
| Read item type + rarity | `:140-141` | — |
| Current reforge from `REFORGE_ID_KEY` | `:144-147` | null if never reforged |
| Eligible = definitions that `appliesTo(itemType)` **and** `id != currentReforgeId` | `:149-155` | empty if none |
| Random selection via `ThreadLocalRandom` | `:157` | — |
| Coin balance | `:158-160` | as above |
| Build output + dynamic recipe | `:162-166` | consume handler only eats `base_item` (`:164`) |

Randomness is uniform over the eligible list; there is **no weighting** and no configurable probability. Note the current-reforge exclusion compares against `def.getId()` (`:151`) — raw YAML-key case. Since `definitions` stores raw keys and `REFORGE_ID_KEY` is written from the same raw id, this matches **only if** the YAML key is lowercase and never re-cased.

### 3.4 `createReforgeStone` (`ReforgeModule.java:203-249`) — reforge stone items

Stones are `Material.AMETHYST_SHARD` items:

- Display name: `"<aqua>" + def.getName() + " Reforge Stone"` (`:208`).
- Lore block 1: instruction line + applicable item types (capitalized, comma-joined, or `All` if empty) (`:211-219`).
- Lore block 2: per-rarity bonus summary. For every `Rarity` value that has a non-empty bonus map, a header line `"<rarity-color>RarityName (cost Coins):"` followed by one `" ◈ <stat>"` line per stat — formatted through `StatDefinition.format(...)` when the stat is registered, else a raw fallback `" ◈ +<value> <stat>"` (`:224-240`).
- Footer: `"<dark_purple><bold>REFORGE STONE"` (`:243`).
- PDC: `Keys.REFORGE_POOL_KEY` = `def.getId()` (`:246`).

The cost line inside the lore comes from the same hard-coded `RARITY_COST` table (`:228`), so the displayed cost always matches the charged cost.

### 3.5 Dynamic machine handler registration (`ReforgeModule.java:53-68`)

Three handlers are registered with `RecipeEngine.registerHandler` (`RecipeEngine.java:28-29`, lowercased keys):

| Machine id | Handler | Lines |
|---|---|---|
| `reforge` | **`this`** (the module) | `:54` |
| `reforge_anvil` | anonymous → `matchReforgeAnvil` (player overload only) | `:55-60` |
| `forge_random` | anonymous → `matchForgeRandom` (player overload only) | `:63-68` |

The `reforge` machine id is a **legacy/unused alias**: no GUI or recipe YAML in the repository references `machine: reforge` (only `reforge_anvil` and `forge_random` are wired — `guis/reforge_anvil.yml:17`, `guis/reforge.yml:15`). Its `match` overloads are byte-for-byte the anvil matcher, so it is a functional duplicate of `reforge_anvil`.

Handlers are (re)registered on every `onEnable()` — including reload — and are never explicitly unregistered in `onDisable()`. This is safe for a full `/valmora reload` only because `RecipeModule.onEnable()` reconstructs a **fresh** `RecipeEngine` (`RecipeModule.java:23-29`), discarding old handlers. It is **not** safe for a single-module reload of `recipe` (`ModuleManager.reloadModule("recipe")`, `ModuleManager.java:87-98`) — see §8.

---

## 4. Configuration (YAML)

Folder: `plugins/Valmora/reforges/` (seeded from the jar by `saveAllResources`, `Valmora.java:476`). Loaded by `YamlLoader` (`YamlLoader.java:37-73`): every `*.yml` file in the folder, every top-level key is one reforge. Parsing is in `ReforgeModule.parseDefinition` (`ReforgeModule.java:307-340`); a thrown exception yields a `LoadResult.failure` that is logged per-file and does not abort the other reforges.

### 4.1 Schema

```yaml
<reforge-id>:
  name: "<display name>"
  applicable-types:            # Item types this reforge can be applied to
    - SWORD
    - AXE
  generate-stone: true         # Whether a Reforge Stone item can be auto-created
  stat-bonuses-by-rarity:
    COMMON:
      <stat-id>: <value>       # Stat ids come from stats/*.yml (case-insensitive)
    UNCOMMON:
      <stat-id>: <value>
    # RARE, EPIC, LEGENDARY, MYTHIC, DIVINE — same shape
```

### 4.2 Per-reforge keys

| Key | Type | Default | Explanation | Parse reference |
|---|---|---|---|---|
| `<reforge-id>` | — | *(required)* | YAML top-level key. Used as the reforge id, the `REFORGE_ID_KEY`/`REFORGE_DISPLAY_KEY` stamp, the `/item give` suffix, and in load-error messages. **Stored with the raw YAML casing** — see §5 / §8. | `ReforgeModule.java:304`, `:336` |
| `name` | string | the reforge id | Display name. Shown on the stone (`createReforgeStone`, `:208`), prepended to the reforged item's display name (`ItemFactory.java:100-104`), and stored in `REFORGE_DISPLAY_KEY`. | `ReforgeModule.java:309` |
| `applicable-types` | list of `ItemType` | *(empty list)* | Item types this reforge can be applied to. Each entry is matched with `ItemType.valueOf(value.toUpperCase())`; invalid values are **silently ignored**. An empty/missing list means "applies to everything" (`ReforgeDefinition.appliesTo`, `:46-49`). `ALL` matches any type; `NONE` matches untyped items. | `ReforgeModule.java:312-317` |
| `generate-stone` | boolean | `false` | If `true`, `/item give <reforge-id>_reforge_stone` is enabled (`ItemCommand.java:81-94`) — it requires a reforge definition with `isGenerateStone() == true` and delegates to `createReforgeStone`. | `ReforgeModule.java:310` |
| `stat-bonuses-by-rarity` | section | *(empty)* | Rarity tier → stat-id → bonus value. Rarity keys are matched with `Rarity.valueOf(key.toUpperCase())`; unknown rarities are skipped. Stat ids are **lowercased** and stored as `Double`. Missing tiers are resolved at lookup time by falling back to the nearest **lower** rarity (`ReforgeDefinition.java:35-44`). Values are merged onto the item's base stats with `Double::sum` (`ReforgeModule.java:186-189`). | `ReforgeModule.java:319-334` |

**Stat id validation:** reforge stat ids are **not** validated against the `StatRegistry` at parse time (unlike item `stats:` — compare `ItemDefinitionParser.java:87-90`). An unknown id is still written into the PDC stat container by `StatModule.saveStats` (`StatModule.java:108-111`), but is filtered out by `StatModule.loadStats` (`StatModule.java:132-138`) and therefore never appears in lore or gameplay.

### 4.3 Rarity tier → coin cost (hard-coded, not configurable)

`ReforgeModule.java:29-39` — used for **both** machines, keyed by the *item's* rarity:

| Rarity | Cost to Reforge |
|---|---|
| `COMMON` | 250 |
| `UNCOMMON` | 500 |
| `RARE` | 1,000 |
| `EPIC` | 2,500 |
| `LEGENDARY` | 5,000 |
| `MYTHIC` | 10,000 |
| `DIVINE` | 15,000 |

Any rarity read from PDC that fails `Rarity.valueOf` falls back to `COMMON` (`readRarity`, `ReforgeModule.java:253-258`) → 250 coins. The same table drives the cost lines in the stone lore (`:228`) and the Anvil GUI's hard-coded lore (`guis/reforge_anvil.yml:71-77`).

### 4.4 Item-side configuration that feeds the module

- **`reforge-pool`** (item definitions, `items/*.yml`) — `ItemDefinitionParser.java:69-72` parses a list of reforge ids; `ItemFactory.create` (`ItemFactory.java:46-50`) joins them with commas into `Keys.REFORGE_POOL_KEY`. This makes a **custom item itself act as a multi-id stone**. The anvil matcher only ever reads the **first** id of the pool (`ReforgeModule.java:114`), so additional entries are inert in the current engine.
- **`item-type`** and **`rarity`** (item definitions) — written to `Keys.ITEM_TYPE_KEY` / `Keys.RARITY_KEY` by `ItemFactory.create` (`ItemFactory.java:33-39`) and read back by `readItemType` / `readRarity`. Vanilla-translated items get these keys from `ItemTranslator.translate` (`ItemTranslator.java:33-37`), so vanilla items are reforge-eligible too (subject to type gating).

### 4.5 Shipped defaults — `reforges/combat.yml` (all `generate-stone: true`)

Eight reforges ship by default. All define all seven rarity tiers, so no fallback fires with the shipped data.

| Reforge | `name` | `applicable-types` | Stat bonuses (per tier: COMMON → DIVINE) |
|---|---|---|---|
| `fierce` | Fierce | SWORD, AXE | `strength`: 5 / 12 / 20 / 32 / 48 / 65 / 85 — `crit_damage`: 3 / 6 / 10 / 15 / 22 / 30 / 40 (`combat.yml:1-28`) |
| `sharp` | Sharp | SWORD, AXE, BOW | `damage`: 5 / 10 / 18 / 28 / 42 / 58 / 75 — `crit_chance`: 2 / 3 / 5 / 7 / 10 / 14 / 18 (`combat.yml:30-58`) |
| `fabled` | Fabled | SWORD, AXE | `strength`: 3 / 7 / 12 / 18 / 28 / 40 / 55 — `damage`: 4 / 8 / 14 / 22 / 33 / 46 / 62 — `ferocity`: 1 / 2 / 4 / 6 / 9 / 13 / 18 (`combat.yml:60-94`) |
| `heroic` | Heroic | SWORD, AXE, BOW, CROSSBOW | `crit_chance`: 3 / 5 / 8 / 11 / 15 / 20 / 26 — `crit_damage`: 6 / 12 / 20 / 30 / 45 / 62 / 82 (`combat.yml:96-125`) |
| `rapid` | Rapid | BOW, CROSSBOW | `bonus_attack_speed`: 8 / 16 / 26 / 38 / 55 / 75 / 100 — `damage`: 4 / 8 / 14 / 22 / 33 / 46 / 62 (`combat.yml:127-154`) |
| `fortified` | Fortified | HELMET, CHESTPLATE, LEGGINGS, BOOTS | `defense`: 8 / 18 / 30 / 45 / 65 / 90 / 120 — `health`: 10 / 20 / 35 / 55 / 80 / 110 / 150 (`combat.yml:156-185`) |
| `reinforced` | Reinforced | HELMET, CHESTPLATE, LEGGINGS, BOOTS | `true_defense`: 3 / 6 / 10 / 15 / 22 / 30 / 40 — `defense`: 6 / 14 / 24 / 37 / 54 / 74 / 100 (`combat.yml:187-216`) |
| `titanic` | Titanic | HELMET, CHESTPLATE, LEGGINGS, BOOTS | `health`: 20 / 40 / 70 / 110 / 160 / 220 / 300 — `defense`: 4 / 8 / 14 / 20 / 30 / 42 / 56 (`combat.yml:218-247`) |

All stat ids referenced (`strength`, `crit_damage`, `damage`, `crit_chance`, `ferocity`, `bonus_attack_speed`, `defense`, `health`, `true_defense`) are registered in `stats/core.yml` (`core.yml:26-31`, `:48-53`, `:19-24`, `:40-46`, `:108-114`, `:146-151`, `:33-38`, `:1-8`, `:167-172`).

---

## 5. Data Model / Persistence

- **No database involvement.** The module never touches `DataStore`/DAO; `definitions` is purely in-memory (`ReforgeModule.java:42`).
- **Only persistent state is YAML on disk** — `plugins/Valmora/reforges/*.yml`, loaded on every `onEnable()` (server start and `/valmora reload`).
- **On-item state lives in the item PDC** (PersistentDataContainer), not the database. The reforged state of an item travels with the `ItemStack`:

| PDC key (`Keys.java`) | NamespacedKey string | Written where | Read where |
|---|---|---|---|
| `REFORGE_ID_KEY` | `valmora:reforge_id` (`Keys.java:65`) | `ReforgeModule.java:192` | `ReforgeModule.java:144-147` (random-forge exclusion) |
| `REFORGE_DISPLAY_KEY` | `valmora:reforge_display` (`Keys.java:67`) | `ReforgeModule.java:193` | `ItemFactory.java:101-104` (name prefix) |
| `REFORGE_POOL_KEY` | `valmora:reforge_pool` (`Keys.java:66`) | `ReforgeModule.java:246` (stones); `ItemFactory.java:49` (custom items) | `ReforgeModule.java:107-110` (anvil) |

- The **stat container** (`Keys.STATS_CONTAINER_KEY`, `valmora:item_stats_container`) is fully rewritten on every reforge by `StatModule.saveStats` (`StatModule.java:104-114`). Because base stats are re-derived from the `ItemDefinition` each time (`ReforgeModule.java:177-183`), reforging is **idempotent in effect**: reforging the same stone onto the same item twice yields identical stats, and reforging with a different stone replaces the previous reforge entirely.
- **Case-sensitivity quirk:** `definitions` keys are stored with the **raw YAML casing** (`definitions.put(def.getId(), def)`, `ReforgeModule.java:304`), while lookups lowercase the input (`getDefinition` `:84`, anvil pool id `:114`, random-forge comparison `:151`). This diverges from the `Registry` case-insensitive convention (`AGENTS.md` §7.2) and means any reforge whose YAML key is not already lowercase is effectively unreachable. All shipped ids are lowercase.

---

## 6. API Exposed

**Not exposed through the `ValmoraAPI` interface** — `api/ValmoraAPI.java:9-70` has no reforge getter. External code must use the concrete plugin class:

```java
Valmora plugin = Valmora.getInstance();                // Valmora.java:278-280
ReforgeModule reforge = plugin.getReforgeModule();     // Valmora.java:425
```

Public surface of the module:

| Member | Signature | Line |
|---|---|---|
| `getDefinitions()` | `Collection<ReforgeDefinition>` | `ReforgeModule.java:82` |
| `getDefinition(String id)` | `ReforgeDefinition` (nullable; lookup lowercased) | `ReforgeModule.java:84` |
| `createReforgeStone(ReforgeDefinition)` | `ItemStack` (the `AMETHYST_SHARD` stone) | `ReforgeModule.java:203` |

Plus the per-definition getters on `ReforgeDefinition.java:28-32` and the predicates `getStatBonusesForRarity(Rarity)` / `appliesTo(ItemType)` (`ReforgeDefinition.java:35-49`).

**Consumers of these methods:**
- `ItemCommand` (`ItemCommand.java:81-94`) — `/item give <id>_reforge_stone` calls `getDefinition(reforgeId)` and `createReforgeStone(def)`.
- `Valmora.getReforgeModule()` itself (`Valmora.java:425`) is the only other reference.

There is **no dedicated reforge command**. `/valmora reload` (permission `valmora.admin`) is how config changes are picked up; the Anvil/Forge GUIs are opened through the GUI module (`/gui open <player> <reforge_anvil|reforge>`, `GuiCommand.java:26-52`, permission `valmora.admin.gui` — the shipped GUI files define no `command:` key, so no auto-registered open command exists; see `GuiModule.registerGuiCommand`, `GuiModule.java:211-221`).

---

## 7. Dependencies & Consumers

### Dependencies (enable-time)

| Dependency | Access point | Used for |
|---|---|---|
| `recipeModule` | `plugin.getRecipeModule().getRecipeEngine()` (`ReforgeModule.java:54`, `:55`, `:63`) | Registers the three `DynamicMachineHandler`s; the recipe engine drives match/consume/output |
| `itemModule` | `plugin.getItemManager().getItemRegistry().getItem(...)` (`ReforgeModule.java:180-182`) | Clean base stats for re-rerolling |
| | `plugin.getItemManager().getItemFactory().updateLore(...)` (`ReforgeModule.java:196`) | Lore regeneration with the reforge name prefix + merged stats |
| `statModule` | `plugin.getStatModule().saveStats(meta, stats)` (`ReforgeModule.java:191`) | Writes the merged stat map to PDC |
| | `plugin.getStatModule().getStatRegistry().get(...)` (`ReforgeModule.java:234`) | Formats stat lines on the reforge stone |
| `economy` (soft) | `plugin.getEconomy()` (`ReforgeModule.java:273`, `:285`) | `EconomyService.hasCoins` / `removeCoins` (`EconomyService.java:9`, `:7`). **Soft dependency:** if `eco == null` the cost check passes and deduction is skipped (`:272-274`, `:284-286`) — reforging becomes free |
| `gui` + `script` (runtime, indirect) | `gui_force_craft` event (`GuiForceCraftEventFactory.java:30-79`) | The only runtime entry point that passes the `Player` so coin checks can run |

The hard Java-level dependency is `recipe`; `item`, `stat`, and `economy` are reached through managers. This is why the registration comment at `Valmora.java:216` names only `recipeModule`. Load order (`… → recipe:202 → … → reforge:216`) guarantees `recipe` is enabled first.

### The recipe-engine interaction in detail

- `RecipeEngine.match` (`RecipeEngine.java:80-104`) resolves in this priority: **dynamic handler** (with player) → static YAML recipes for the machine → vanilla recipes. The reforge handlers always win for their machine ids because they return a match before static/Vanilla fallbacks are consulted.
- Dynamic recipes are built with `RecipeDefinition.dynamic` (`RecipeDefinition.java:60-65`) — `isVanilla=true` with `vanillaResult = output`, and a custom `consumeHandler`. `RecipeEngine.buildOutput` (`RecipeEngine.java:53-74`) therefore clones `output` and runs it through `ItemTranslator.translate` (`ItemTranslator.java:23-51`), which is a no-op for reforged items because they already carry `Keys.ITEM_ID_KEY`.
- `RecipeEngine.consume` (`RecipeEngine.java:163-167`) invokes the dynamic `consumeHandler` (the lambda in `ReforgeModule.java:127-131` / `:163-166`), which zeroes the consumed inputs and deducts coins.

### Consumers

**None beyond `ItemCommand`.** A repo-wide grep for `ReforgeModule` / `getReforgeModule` matches only the module itself, `Valmora.java` wiring (`:56`, `:117`, `:178`, `:216`, `:425`), and `ItemCommand` (`ItemCommand.java:83`). The module is a leaf in the dependency graph. (`docs/todo.md:36` lists "reforge module" as an item in the roadmap.)

---

## 8. Unfinished Things / TODOs

- **Not in `ValmoraAPI`.** The module is reachable only via the concrete `Valmora` class (`Valmora.java:425`); `api/ValmoraAPI.java` has no `getReforgeModule()`. External plugins cannot reach it through the API (contrast `MODULE_DEVELOPMENT.md` §8).
- **No admin tooling.** No command to list reforges, preview a stone, force a reforge, or reset an item. `/item give <id>_reforge_stone` is the only generation path.
- **Legacy `reforge` machine id.** Registered at `ReforgeModule.java:54` and fully functional, but no GUI/recipe references `machine: reforge` — it is a dead alias of `reforge_anvil` and contradicts the comment's claim of a `reforge.yml` recipe.
- **Handler leak on single-module reload.** `onDisable()` (`ReforgeModule.java:71-74`) clears definitions but never unregisters the handlers. Full `/valmora reload` is safe only because `RecipeModule.onEnable()` rebuilds a fresh `RecipeEngine` (`RecipeModule.java:23-29`); `ModuleManager.reloadModule("recipe")` (`ModuleManager.java:87-98`) would silently drop the reforge handlers.
- **No live output preview.** The Anvil/Forge output slots stay empty until the player clicks the craft button, because the preview path (`GuiListener.java:291-309`) calls `match(machineId, inputs)` without the player and the handlers deliberately return empty for the no-player overload (`ReforgeModule.java:56`, `:64`, `:88-91`). Players cannot see the result before paying.
- **Case-sensitive definition storage.** `definitions` keys keep raw YAML casing (`ReforgeModule.java:304`) while lookups lowercase (`:84`, `:114`, `:151`), diverging from `AGENTS.md` §7.2. An uppercase reforge key is unreachable.
- **Vanilla items lose their base stats.** For an item without a registered `ItemDefinition`, `baseStats` is empty (`ReforgeModule.java:177-183`), so a reforged vanilla-translated weapon keeps only the reforge bonuses — its translated `damage`/`defense` (`ItemTranslator.java:39-43`) are wiped. Reforging a custom item preserves base stats correctly.
- **Multi-id pools are inert.** `reforge-pool` on custom items (`ItemDefinitionParser.java:69-72`, `ItemFactory.java:46-50`) is parsed and stored, but the anvil only reads the first id (`ReforgeModule.java:114`).
- **No stat-id validation.** Reforge stat ids are not checked against `StatRegistry` at load time (unlike `ItemDefinitionParser.java:87-90`); typos silently vanish from lore because `StatModule.loadStats` filters to registered stats (`StatModule.java:132-138`).
- **Coins cost is hard-coded** in `RARITY_COST` (`ReforgeModule.java:29-39`) and duplicated in the Anvil GUI lore (`guis/reforge_anvil.yml:71-77`) and stone lore builder (`ReforgeModule.java:228`) — three places to keep in sync.
- **No randomness weighting** for the Random Forge — uniform `ThreadLocalRandom` over the eligible set (`ReforgeModule.java:157`).
- **No unit tests** for the module (no test source under `src/test/...` covers it).
- **Documentation drift:** `docs/VALMORA_DOCUMENTATION.md:2111` states `/item give <reforge-id>_stone`, but the code checks the `_reforge_stone` suffix (`ItemCommand.java:81-82`).

---

## 9. Possible Improvements / Changes

- **Expose via `ValmoraAPI`** — add `getReforgeModule()` to the interface (`api/ValmoraAPI.java`) matching `MODULE_DEVELOPMENT.md` §8.
- **Deduplicate the machine registration** — drop the legacy `reforge` handler (`ReforgeModule.java:54`) or give `forge_random`/`reforge_anvil` distinct matchers only, and remove the anonymous-handler boilerplate by implementing both machines directly on the module class.
- **Unregister handlers in `onDisable()`** by removing the machine-id entries from `RecipeEngine.dynamicHandlers` (or add a `RecipeEngine.unregisterHandler`), making single-module reloads safe.
- **Normalize definition keys to lowercase** at storage time (`definitions.put(def.getId().toLowerCase(), def)`), aligning with `Registry` conventions and removing the case traps in §5/§8.
- **Preserve vanilla base stats** — when the item has no registered definition, retain the existing stats already present in the PDC stat container as the baseline instead of starting from an empty map (`ReforgeModule.java:177-183`).
- **Live preview support** — implement the no-player `match` overload by delegating to a matcher that skips the coin check, so the output slot previews the reforged result; the player-aware overload then adds the coin gate. Needs a `Player`-less guard so no coins are deducted on preview.
- **Honor the full reforge pool** — for multi-id pools, treat the stone as "any of these reforges" (deterministic per stone or random-within-pool) instead of silently using only the first id (`ReforgeModule.java:114`).
- **Validate reforge stat ids at parse time** against `StatRegistry` with a clear `LoadResult.failure`, mirroring `ItemDefinitionParser.java:87-90`.
- **Make costs configurable** — move `RARITY_COST` into `config.yml` (or the reforge file) and generate the Anvil GUI lore from it rather than hand-written YAML, eliminating the three-way duplication in §8.
- **Support weighted random forging** — optional per-reforge `weight` for the `forge_random` machine.
- **Add a `/reforge` admin command** (registered in `Valmora.onEnable()` per `AGENTS.md` §6.3) for listing definitions, previewing stats, granting stones, and clearing an item's reforge (removing `REFORGE_ID_KEY`/`REFORGE_DISPLAY_KEY` and re-deriving base stats).
- **Unit tests** following the `ExpressionTest` pattern (`AGENTS.md` §9): mock `ValmoraAPI`, drive `matchReforgeAnvil`/`matchForgeRandom` through a stub input map, and assert cost gating, fallback rarity selection, and base-stat re-derivation in `buildReforgedItem`.
