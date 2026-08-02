# Recipe Module — Design & Code

> **Module ID:** `recipe` | **Name:** `Recipe System` | **Package:** `org.nakii.valmora.module.recipe`
> **Dependencies:** `script` (event parser), `items` (item registry / translator), `enchant` (anvil handler)
> **Consumers:** `gui`, `alchemy`, `reforge`

---

## Table of Contents

1. [Overview](#overview)
2. [Code Structure](#code-structure-file-by-file)
3. [Architecture & Key Classes](#architecture--key-classes)
4. [Configuration (YAML)](#configuration-yaml)
5. [Data Model / Persistence](#data-model--persistence)
6. [API Exposed](#api-exposed)
7. [Dependencies & Consumers](#dependencies--consumers)
8. [Unfinished Things / TODOs](#unfinished-things--todos)
9. [Possible Improvements / Changes](#possible-improvements--changes)

---

## Overview

The Recipe module is the **generic machine/recipe matching engine** for Valmora. It is *not* a Bukkit `Recipe` registry and it never calls `Bukkit.addRecipe()`. Instead it:

1. Loads **static YAML recipes** from `plugins/Valmora/recipes/*.yml` and groups them by **machine ID**.
2. Lets other modules plug **dynamic machine handlers** (`DynamicMachineHandler`) into the engine per machine ID (anvil, alchemy, reforge). Dynamic handlers are consulted **first**, before static YAML, before vanilla recipes.
3. Matches a set of `input` items against a recipe via three matching strategies: `EXACT_SLOT`, `SHAPELESS`, `SHAPED`.
4. Builds the output `ItemStack` (custom Valmora item, vanilla material, or a pre-built dynamic item) and always passes it through `ItemManager.getItemTranslator().translate()` so every item leaving a machine is Valmora-formatted.
5. Consumes the correct ingredients, optionally running an `on-craft` script event list.
6. Falls back to **vanilla crafting** via `Bukkit.getCraftingRecipe(...)` when nothing matches.

There is **no persistence layer** — recipes are pure configuration loaded at `onEnable()`. There is also **no `/recipe` command**; the module is consumed entirely through the GUI module's `machine:` definition (see `docs/modules/user/recipe.md` for the player/admin view).

The engine lives at `RecipeEngine.java:19`. The machine/recipe data model is `RecipeDefinition.java:11`. The module lifecycle wrapper is `RecipeModule.java:12`.

---

## Code Structure (file-by-file)

All files: `src/main/java/org/nakii/valmora/module/recipe/` (9 files, ~643 lines).

### `RecipeModule.java` (69 lines)

The `ReloadableModule` wrapper. Holds the two pieces of state:

- `machineRecipes: Map<String, List<RecipeDefinition>>` — static YAML recipes grouped by machine ID (`RecipeModule.java:15`).
- `recipeEngine: RecipeEngine` — the matching engine (`RecipeModule.java:16`).

`onEnable()` (`RecipeModule.java:23`):

1. Creates a **fresh** `RecipeEngine(plugin)` (`RecipeModule.java:24`). Because the engine is rebuilt every enable, dynamic handlers from a previous lifecycle are discarded automatically — this keeps reload idempotent.
2. Registers the built-in `anvil` dynamic handler: `registerHandler("anvil", new AnvilMachineHandler(plugin))` (`RecipeModule.java:27`).
3. Loads the static YAML recipes via `loadRecipes()` (`RecipeModule.java:29`).

`loadRecipes()` (`RecipeModule.java:47`):

- Clears `machineRecipes`.
- Uses `new YamlLoader<RecipeDefinition>(plugin, "recipes", "Recipe")` — folder `plugins/Valmora/recipes/`, type name `"Recipe"` used in the log summary (`RecipeModule.java:49`).
- Each recipe is parsed by `RecipeDefinitionParser` and registered with `machineRecipes.computeIfAbsent(recipe.getMachine(), k -> new ArrayList<>()).add(recipe)` (`RecipeModule.java:51`). Note: recipes whose `machine` key is missing/`null` are still stored, under a `null` key.

`onDisable()` (`RecipeModule.java:43`) only clears `machineRecipes`. There are no listeners to unregister. The engine is *not* nulled here — it is replaced wholesale on the next `onEnable()`.

Public surface:

- `getRecipeEngine()` (`RecipeModule.java:32`)
- `registerHandler(String machineId, DynamicMachineHandler handler)` (`RecipeModule.java:36`) — delegates to `recipeEngine.registerHandler` (no-op if engine not yet created).
- `getRecipesForMachine(String machineId)` (`RecipeModule.java:66`) — returns the stored list or an empty `ArrayList`.

`getId()` returns `"recipe"` (`RecipeModule.java:57`); `getName()` returns `"Recipe System"` (`RecipeModule.java:62`).

### `RecipeType.java` (7 lines)

Enum with exactly three values (`RecipeType.java:3`):

| Value | Meaning |
|---|---|
| `EXACT_SLOT` | Each named input slot must be present and match exactly; the *number* of non-empty input stacks must equal the number of recipe inputs. Used by 2-input machines (forge, anvil) and dynamic recipes. |
| `SHAPELESS` | A set of required ingredients that may occupy any slots, in any order. Used by alchemy and ore-refining recipes. |
| `SHAPED` | A spatial 3×3 grid pattern (slots `0`–`8`) that may be translated anywhere within the input bounding box. Used by crafting-table recipes. |

### `RecipeDefinition.java` (77 lines)

Immutable recipe data model (`RecipeDefinition.java:11`). Fields (`RecipeDefinition.java:12`):

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Unique recipe ID (top-level YAML key, or `vanilla:`/`dynamic:` prefixed for generated recipes). |
| `machine` | `String` | Machine ID this recipe belongs to (`crafting_table`, `forge`, `alchemy`, …). |
| `type` | `RecipeType` | Matching strategy. |
| `inputMap` | `Map<String, RecipeIngredient>` | Named/keyed inputs (EXACT_SLOT + SHAPED grid slots). |
| `inputList` | `List<RecipeIngredient>` | Ordered list of required ingredients (SHAPELESS). |
| `outputs` | `Map<String, RecipeIngredient>` | Result items keyed by name (`result`). Only the **first** value is ever used. |
| `onCraft` | `CompiledEvent` | Script event list executed when a craft is completed. May be `null`. |
| `isVanilla` | `boolean` | True for recipes synthesized from `Bukkit.getCraftingRecipe` or from the anvil/dynamic handlers. |
| `vanillaResult` | `ItemStack` | Pre-built output item for vanilla/dynamic recipes (preserves NBT/enchantments). |
| `consumeHandler` | `Consumer<Map<String, ItemStack>>` | Custom ingredient consumption logic for dynamic recipes. |

Static factories:

- `vanilla(ItemStack result)` (`RecipeDefinition.java:49`) and `vanilla(ItemStack result, CompiledEvent onCraft)` (`RecipeDefinition.java:53`) — builds a `SHAPELESS` recipe on machine `crafting_table` whose `outputs` map is `{ "result": RecipeIngredient(typeName, amount) }`.
- `dynamic(String machineId, ItemStack result, Consumer<Map<String, ItemStack>> consumeHandler)` (`RecipeDefinition.java:61`) — builds an `EXACT_SLOT` recipe with a `dynamic:<machine>:<nanoTime>` ID, a pre-built output item, and custom consumption. Used by the alchemy and reforge handlers.

### `RecipeIngredient.java` (7 lines)

A `record RecipeIngredient(String item, int amount)` (`RecipeIngredient.java:3`). `item` is either a **Valmora custom item ID** (e.g. `reinforced_ingot`) or a **Bukkit Material name** (e.g. `DIAMOND`). `amount` is the per-slot requirement / output quantity.

### `CraftResult.java` (7 lines)

`record CraftResult(ItemStack output, RecipeDefinition recipe, CompiledEvent onCraft)` (`CraftResult.java:6`) — returned by `RecipeEngine.craft(...)`.

### `RecipeEngine.java` (405 lines)

The core engine. See [Architecture & Key Classes](#architecture--key-classes).

### `RecipeDefinitionParser.java` (82 lines)

Turns one top-level YAML section into a `RecipeDefinition` or a `LoadResult.failure(...)`. See [Configuration (YAML)](#configuration-yaml). The parse signature is `LoadResult<RecipeDefinition, String> parse(String id, ConfigurationSection section, String filePath)` (`RecipeDefinitionParser.java:21`).

### `DynamicMachineHandler.java` (18 lines)

Functional-style interface for modules that generate recipes at runtime (`DynamicMachineHandler.java:10`):

```java
Optional<RecipeDefinition> match(Map<String, ItemStack> inputs);                       // line 12
default Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
    return match(inputs);                                                              // line 15
}
```

The two-arg overload exists for context-sensitive recipes (skill bonuses, coin checks). Implementers must return `Optional.empty()` when the inputs do not produce a valid output.

### `AnvilMachineHandler.java` (99 lines)

The built-in dynamic handler for machine `anvil`. See [Architecture & Key Classes](#architecture--key-classes).

---

## Architecture & Key Classes

### Wiring in `Valmora.java`

- Field: `private org.nakii.valmora.module.recipe.RecipeModule recipeModule;` (`Valmora.java:99`).
- Instantiated at `Valmora.java:164`.
- Registered with `moduleManager.registerModule(recipeModule);` at `Valmora.java:202`.
- Registration order around recipe: `gui` (`Valmora.java:201`) → `recipe` (`:202`) → `alchemy` (`:203`) → `enchant` (`:204`) → … → `reforge` (`:216`). `ReforgeModule` carries the comment *"Depends on recipeModule (registers handler)"* (`Valmora.java:216`).
- No recipe command is registered — commands are only registered in `Valmora.onEnable()` after modules are enabled (`Valmora.java:227`–`259`).
- `saveAllResources()` copies `recipes/` files into the data folder on first run without overwriting edits (`Valmora.java:470`).

### The matching pipeline — `RecipeEngine.match(...)`

`match(String machineId, Map<String, ItemStack> inputs)` (`RecipeEngine.java:76`) delegates to `match(machineId, inputs, null)` (`RecipeEngine.java:80`). Priority order:

1. **Dynamic handlers** — `dynamicHandlers.get(machineId.toLowerCase())`; if a handler returns a present match it wins immediately (`RecipeEngine.java:82`–`86`).
2. **Static YAML recipes** — `plugin.getRecipeModule().getRecipesForMachine(machineId)` iterated in load order; first `matches(...)` wins (`RecipeEngine.java:89`–`95`).
3. **Vanilla recipes** — `matchVanillaRecipe(inputs)` (`RecipeEngine.java:98`). **This is *not* gated by machine ID** — it runs for any machine whose handler/static recipes didn't match.

`matches(...)` dispatches on `recipe.getType()` (`RecipeEngine.java:106`):

| Type | Method | Rules |
|---|---|---|
| `EXACT_SLOT` | `matchExact` (`:114`) | Non-AIR stack count must equal `inputMap.size()` (`:124`); each named key must be present and `isSameItem` + amount ≥ required (`:126`–`132`). |
| `SHAPELESS` | `matchShapeless` (`:136`) | Non-AIR stack count must equal `inputList.size()` (`:146`); each required ingredient maps to a **distinct** physical slot (`:148`–`160`). A single slot can never satisfy two requirements, even with a large amount. |
| `SHAPED` | `matchShaped` (`:268`) | Builds numeric `0..8` grid maps of inputs and recipe (non-numeric keys ignored, `:281`–`296`). Empty input or recipe → no match (`:298`). Counts must match (`:300`). Computes each side's bounding box (slots are `x = slot % 3`, `y = slot / 3`) and tests every translation offset via `matchesPatternAt` (`:330`–`336`). |

`matchesPatternAt` (`RecipeEngine.java:341`) verifies each recipe slot against the corresponding input slot at the given offset, checking `isSameItem` and amount.

### Item identity — `isSameItem` (`RecipeEngine.java:394`)

An input matches a recipe ingredient if either:

1. The input's `ItemMeta` PDC contains `Keys.ITEM_ID_KEY` with a value equal (case-insensitive) to the target ID — i.e. **Valmora custom item IDs** (`RecipeEngine.java:397`–`400`).
2. `Material.matchMaterial(targetId)` resolves and equals the stack's type — i.e. **vanilla Material names** (`RecipeEngine.java:402`–`403`).

This is what lets recipes mix custom items (`reinforced_ingot`) and vanilla materials (`DIAMOND`) freely.

### Output building — `buildOutput(...)` (`RecipeEngine.java:53`)

- Vanilla/dynamic recipes: `recipe.getVanillaResult().clone()` (`:56`).
- Static recipes with empty `outputs`: returns `null` → craft aborts (`:57`).
- Otherwise takes the **first** value of `outputs` (`:60`); if it names a valid `Material` builds `new ItemStack(mat, amount)` (`:66`), else resolves it as a custom item via `plugin.getItemManager().createItemStack(item)` and sets the amount (`:63`).
- AIR results are rejected (`:70`).
- **Every** output is passed through `plugin.getItemManager().getItemTranslator().translate(output)` (`RecipeEngine.java:73`), which stamps PDC keys (`ITEM_TYPE_KEY`, `RARITY_KEY`, `ITEM_ID_KEY`, stats) and regenerates lore — see `ItemTranslator.java:23`.

### The unified craft — `craft(...)` (`RecipeEngine.java:37`)

`craft(String machineId, Map<String, ItemStack> inputs)` (`:37`) → `craft(machineId, inputs, @Nullable Player player)` (`:41`). The three steps happen atomically on the calling thread:

1. `match(machineId, inputs, player)` (`:42`) — if empty, return `Optional.empty()`.
2. `buildOutput(recipe)` (`:46`) — if null/AIR, return empty (ingredients are **not** consumed in this failure case).
3. `consume(recipe, inputs)` (`:49`) — consume ingredients, then wrap output as `CraftResult(output, recipe, recipe.getOnCraft())` (`:50`).

### Consumption — `consume(...)` (`RecipeEngine.java:163`)

Dispatch order:

1. `consumeHandler` present (dynamic recipes) → handler consumes (`:164`).
2. `isVanilla` → `consumeVanilla(inputs)` (`:168`): subtracts **1** from every numeric-keyed non-AIR input (`:256`–`266`).
3. `EXACT_SLOT` → subtract each named ingredient's amount from its exact slot (`:172`–`179`).
4. `SHAPELESS` → iterate numeric-keyed inputs only (`Integer.parseInt` guard, `:184`); greedily match against a mutable copy of `inputList`, subtracting per stack (`:188`–`195`).
5. `SHAPED` → recompute the winning bounding-box offset and subtract exactly from the physical slots that matched (`:199`–`251`).

`consume` returns `true` on success, `false` only for a `SHAPED` recipe that somehow re-failed the offset scan (`:253`).

### Vanilla fallback — `matchVanillaRecipe(...)` (`RecipeEngine.java:362`)

Builds a `ItemStack[9]` matrix from numeric-keyed inputs in slots `0..8` (`:363`–`375`), then calls `Bukkit.getCraftingRecipe(matrix, world)` using the **first world** from `plugin.getServer().getWorlds()` (`RecipeEngine.java:379`–`380`). On success returns `RecipeDefinition.vanilla(result)` (machine `crafting_table`, SHAPELESS). Because this fallback is unconditional, vanilla recipes can surface in *any* machine GUI that exposes numeric input slots (see [Possible Improvements](#possible-improvements--changes)).

### Dynamic handlers in practice

Registered handlers (all lowercase keys):

| Machine ID | Handler | Registered by |
|---|---|---|
| `anvil` | `AnvilMachineHandler` | `RecipeModule.onEnable()` (`RecipeModule.java:27`) |
| `alchemy` | `AlchemyMachineHandler` | `AlchemyModule` (`AlchemyModule.java:51`–`52`) |
| `reforge` | `ReforgeModule` itself | `ReforgeModule` (`ReforgeModule.java:54`) |
| `reforge_anvil` | anonymous handler → `matchReforgeAnvil` | `ReforgeModule` (`ReforgeModule.java:55`–`60`) |
| `forge_random` | anonymous handler → `matchForgeRandom` | `ReforgeModule` (`ReforgeModule.java:63`–`68`) |

The `enchanting_table` machine has **no** handler (`guis/enchanting.yml:4`) — it only sees vanilla/static matches (see [Unfinished Things](#unfinished-things--todos)).

### `AnvilMachineHandler` behavior (`AnvilMachineHandler.java:13`)

`match(inputs)` (`:22`) expects keys `base` and `material`:

- Both must be non-AIR (`:26`–`27`).
- Reads the Valmora enchant map of each item via `EnchantmentHelper.getEnchantments` (`:29`–`30`).
- The **material** must carry Valmora enchantments, otherwise no match (`:33`).
- `isBook = base.getType() == ENCHANTED_BOOK` (`:35`).
- Merges each material enchant into the base map:
  - Enchantment definitions come from `plugin.getEnchantModule().getRegistry()` (`:43`); unknown IDs are skipped (`:44`).
  - **Books:** inputs at or above the enchant-table ceiling (`def.getEtableMaxLevel()`) are rejected (`:47`, `:66`, `:76`).
  - Conflicts are skipped (`:50`–`57`).
  - Same level → level + 1, otherwise max of the two, capped by ceiling (`:69`–`78`).
  - `maxLevel` is the table ceiling for books, absolute max otherwise (`:60`).
- If nothing changed → no match (`:85`).
- Result is `base.clone()` with the merged map applied (`:87`–`88`).
- **Cost:** `totalLevel * 10` coins, injected as the on-craft script `variable add player.var.coins -<cost>` (`RecipeEngine` cost math at `AnvilMachineHandler.java:91`–`94`, event parse at `:95`).
- Returns `RecipeDefinition.vanilla(result, onCraft)` (`:97`).

### Machine input key conventions

`GuiSession.buildInputSnapshot()` (`GuiSession.java:82`–`104`) publishes every `INPUT` component's item **twice**: under the component's `id` (e.g. `ingredient`, `base`, `material`, `base_item`, `reforge_stone`, `input1`, `input2`, `bottle`) and under a zero-indexed numeric key (`"0"`, `"1"`, …) in layout scan order (`GuiSession.java:97`–`98`). This is why:

- EXACT_SLOT forge recipes use `input1`/`input2` (`recipes/forge.yml:15`–`16`).
- Anvil handler reads `base`/`material` (`guis/anvil.yml:44`/`48`).
- Reforge handlers read `base_item`/`reforge_stone` (`guis/reforge_anvil.yml:42`/`57`, `guis/reforge.yml:42`).
- SHAPED/SHAPELESS and vanilla matching use the numeric aliases.

---

## Configuration (YAML)

Folder: `plugins/Valmora/recipes/` (auto-copied from `src/main/resources/recipes/`). Loaded by `YamlLoader.load(...)` (`YamlLoader.java:37`) which iterates every `.yml` file in the folder and passes each top-level key as a recipe ID.

### Top-level keys

| Key | Required | Default | Type | Description |
|---|---|---|---|---|
| `<recipe-id>` | Yes | — | `String` (map key) | Unique recipe identifier; also the recipe ID used in logs/errors. |
| `machine` | Yes* | `null` | `String` | Machine ID this recipe is registered under (`RecipeDefinitionParser.java:23`). *No default — if omitted the recipe is stored under a `null` machine key and will never be matched by the GUI.* |
| `type` | No | `EXACT_SLOT` | `String` | One of `EXACT_SLOT`, `SHAPELESS`, `SHAPED`; parsed case-insensitively via `RecipeType.valueOf(...toUpperCase())` (`RecipeDefinitionParser.java:24`). |
| `inputs` | Yes | — | section or list | Depends on `type` — see below. |
| `outputs` | No | — | section | Result items. Only the first entry is used by `buildOutput` (`RecipeEngine.java:60`). |
| `on-craft` | No | — | `List<String>` | Script event lines executed on successful craft via `plugin.getScriptModule().getEventParser().parseList(...)` (`RecipeDefinitionParser.java:71`–`74`). |

### `inputs` by type

**SHAPELESS** — a YAML **list of maps** (`RecipeDefinitionParser.java:29`–`33`), e.g.:

```yaml
inputs:
  - { item: NETHER_WART, amount: 1 }
  - { item: GLASS_BOTTLE, amount: 1 }
```

| Sub-key | Required | Default | Description |
|---|---|---|---|
| `item` | Yes | — | Valmora custom item ID or Bukkit Material name. |
| `amount` | Yes (in this form) | — | Per-slot quantity required. A missing value throws (`(int) input.get("amount")`, `RecipeDefinitionParser.java:32`). |

**EXACT_SLOT / SHAPED** — a section keyed by slot name (`RecipeDefinitionParser.java:34`–`51`), e.g.:

```yaml
inputs:
  input1: { item: IRON_INGOT, amount: 2 }
  input2: { item: enchanted_diamond, amount: 1 }
```

For SHAPED, keys are grid slots `"0"`…`"8"` (`0 1 2 / 3 4 5 / 6 7 8`). For 2×2 recipes only the needed slots are listed (see `recipes/crafting_table.yml:5`–`10`).

| Sub-key | Required | Default | Description |
|---|---|---|---|
| `<slot>` | Yes | — | Slot key: named (`input1`) for EXACT_SLOT, numeric (`"0"`–`"8"`) for SHAPED. |
| `item` | Yes | — | Valmora custom item ID or Bukkit Material name. |
| `amount` | No | `1` | Quantity required in that slot (`RecipeDefinitionParser.java:45`). |

Both the nested-section form (`"1": { item: DIAMOND, amount: 2 }`) and the dot-path form (`inputs.get("1.item")`) are accepted (`RecipeDefinitionParser.java:39`–`47`).

### `outputs`

A section keyed by result name (`RecipeDefinitionParser.java:53`–`69`):

```yaml
outputs:
  result: { item: DIAMOND_SWORD, amount: 1 }
```

| Sub-key | Required | Default | Description |
|---|---|---|---|
| `<name>` | Yes | — | Result key; only the **first** is read (`RecipeEngine.java:60`). |
| `item` | Yes | — | Valmora custom item ID (built via `ItemManager.createItemStack`) or Bukkit Material name (`new ItemStack`). |
| `amount` | No | `1` | Output quantity (`RecipeDefinitionParser.java:64`). |

### `on-craft`

List of script event strings, e.g.:

```yaml
on-craft:
  - "sound player entity.witch.celebrate"
  - "variable add player.var.alchemy_xp 5"
```

Compiled by the script module's `EventParser` (`RecipeDefinitionParser.java:73`). Executed with a `GuiExecutionContext` after a successful craft — see `GuiListener.handleOutputClick` (`GuiListener.java:251`) and `GuiForceCraftEventFactory` (`GuiForceCraftEventFactory.java:71`).

### Machine IDs used by shipped configs + GUIs

| Machine ID | Recipe files | GUI | Dynamic handler |
|---|---|---|---|
| `crafting_table` | `crafting_table.yml`, `shardworks_recipes.yml` | `guis/crafting.yml:15` | — (vanilla fallback applies) |
| `forge` | `forge.yml` | `guis/forge.yml:16` | — |
| `alchemy` | `alchemy.yml` | `guis/alchemy.yml:4` | `AlchemyMachineHandler` |
| `anvil` | — | `guis/anvil.yml:16` | `AnvilMachineHandler` |
| `enchanting_table` | — | `guis/enchanting.yml:4` | **none** |
| `reforge_anvil` | — | `guis/reforge_anvil.yml:17` | Reforge anvil handler |
| `forge_random` | — | `guis/reforge.yml:15` | Reforge random handler |
| `reforge` | — | (no GUI) | alias for reforge anvil logic |

### Shipped recipe reference

**`recipes/crafting_table.yml`** — SHAPED demo recipes on `crafting_table`: `diamond_sword`, `iron_pickaxe`, `crafting_table_block`, `stone_sword`, plus SHAPELESS `wood_planks` (1×`LOG` → 4×`PLANKS`) and SHAPED `test_sword_craft` (2×`reinforced_ingot` + `STICK` → `testSword`).

**`recipes/forge.yml`** — EXACT_SLOT on `forge`: `reinforced_ingot` (2×`IRON_INGOT` + `enchanted_diamond`), `forged_blade` (`IRON_SWORD` + 2×`reinforced_ingot`), `enchant_diamond` (`DIAMOND` + 3×`emerald`).

**`recipes/alchemy.yml`** — SHAPELESS on `alchemy`; every effect exists in `_bottle` (uses `GLASS_BOTTLE`) and `_potion` (uses `POTION`) variants; each produces 1×`POTION` and grants `player.var.alchemy_xp` via `on-craft`. Effects: `awkward_potion` (NETHER_WART, +5), `thick_potion` (GLOWSTONE_DUST, +3), `healing_potion` (GLISTERING_MELON_SLICE, +15), `strength_potion` (BLAZE_POWDER, +12), `fire_resistance_potion` (MAGMA_CREAM, +10), `night_vision_potion` (GOLDEN_CARROT, +8), `swiftness_potion` (SUGAR, +10).

**`recipes/shardworks_recipes.yml`** — `crafting_table`: SHAPELESS ore refining (`raw_ferrite`×2 → `ferrite_ingot`, `raw_lumicite`×2 → `lumicite_crystal`, `raw_aetherium`×2 → `aetherium_ingot`), SHAPED tiered pickaxes and full ferrite/lumicite/aetherium armor sets (helmet/chestplate/leggings/boots). All outputs reference custom item IDs defined in `items/shardworks_*.yml` / `items/example.yml`.

---

## Data Model / Persistence

- **No database tables, no `DataStore` usage.** The recipe module is fully config-driven; all state lives in the `machineRecipes` map (`RecipeModule.java:15`) and the `dynamicHandlers` map (`RecipeEngine.java:22`).
- `machineRecipes` is rebuilt on every `onEnable()` / `/valmora reload` (`RecipeModule.java:47`); the `RecipeEngine` is recreated so handler registrations start fresh (`RecipeModule.java:24`).
- Crafting output items get their identity/persistence from PDC keys stamped by `ItemTranslator.translate(...)` (`ItemTranslator.java:23`) — `ITEM_TYPE_KEY`, `RARITY_KEY`, `ITEM_ID_KEY`, stats container, lore. Custom-item outputs come from `ItemManager.createItemStack(...)` (`ItemManager.java:69`).
- `on-craft` scripts write to player profile variables (e.g. `player.var.alchemy_xp`), which are persisted by the player/stat modules, not by recipe itself.
- `GuiSession` snapshots (`GuiSession.java:59`–`105`) are transient; machine state like `brew_result`, `brew_running` lives in session `props` (see `AlchemyBrewStartEventFactory.java:121`).

---

## API Exposed

- **`RecipeModule` is *not* part of the `ValmoraAPI` interface.** `ValmoraAPI.java:19`–`69` has no `getRecipeModule()`. Access is only through the concrete class: `((Valmora) ValmoraAPI.getInstance()).getRecipeModule()` or `plugin.getRecipeModule()` (`Valmora.java:345`).
- Public methods: `getRecipeEngine()` (`RecipeModule.java:32`), `registerHandler(...)` (`RecipeModule.java:36`), `getRecipesForMachine(...)` (`RecipeModule.java:66`).
- `RecipeEngine` public surface: `registerHandler` (`RecipeEngine.java:28`), `craft(machineId, inputs)` (`:37`), `craft(machineId, inputs, player)` (`:41`), `match(...)` (`:76`/`:80`), `consume(recipe, inputs)` (`:163`), `consumeVanilla(inputs)` (`:256`).
- `RecipeDefinition` factories: `vanilla(...)` (`RecipeDefinition.java:49`/`:53`), `dynamic(...)` (`RecipeDefinition.java:61`).
- Inter-module contract: **`DynamicMachineHandler`** (`DynamicMachineHandler.java:10`) is the extension point modules implement to plug custom machines into the engine.

---

## Dependencies & Consumers

### Dependencies (loaded before `recipe`)

- `script` — `EventParser` for `on-craft` (`RecipeDefinitionParser.java:73`, `AnvilMachineHandler.java:95`).
- `items` — `ItemManager.createItemStack` / `getItemTranslator` for outputs (`RecipeEngine.java:63`/`:73`).
- `enchant` — `EnchantmentHelper`/`EnchantmentDefinition` for the anvil handler (`AnvilMachineHandler.java:6`–`7`, `:29`–`30`, `:43`).

### Consumers (loaded after `recipe`)

- **`gui`** — drives everything:
  - `GuiListener.handleOutputClick` (`GuiListener.java:186`) — on OUTPUT click: re-match, mass-craft loop (shift-click up to 64, guarded by `canFit` `:262` and a recipe-ID lock to prevent mid-loop recipe mutation `:215`–`228`), consume via `engine.consume` (`:245`) or `consumeVanillaIngredients` (`:274`), then execute `onCraft` with a `GuiExecutionContext` (`:250`–`251`).
  - `GuiListener.updateRecipeOutput` (`GuiListener.java:291`) — re-matches and places the output into the OUTPUT slot; vanilla results are translated before display (`:301`–`308`).
  - `GuiRenderer.updateOutputSlot`/`matchRecipe` (`GuiRenderer.java:84`/`:123`).
  - `GuiForceCraftEventFactory` (`gui_force_craft` script event) — dupe-protected (`session.setCraftingLocked`) unified `engine.craft(...)` (`GuiForceCraftEventFactory.java:43`–`55`).
  - `AlchemyBrewStartEventFactory` (`gui_alchemy_start`) — builds `virtualInputs` with `base` + `ingredient` keys, matches, stores result in `brew_result` prop, consumes the ingredient (`AlchemyBrewStartEventFactory.java:101`–`128`).
- **`alchemy`** — registers the `alchemy` machine handler (`AlchemyModule.java:51`–`52`).
- **`reforge`** — implements `DynamicMachineHandler`, registers `reforge`, `reforge_anvil`, `forge_random` (`ReforgeModule.java:54`–`68`), and consumes items/coins through `ReforgeModule.matchReforgeAnvil`/`matchForgeRandom` (`ReforgeModule.java:100`/`:136`).

### Tests

`src/test/java/org/nakii/valmora/recipe/RecipeEngineTest.java` (JUnit 5 + Mockito, `@Tag("recipe")`, `RecipeEngineTest.java:26`). Covers EXACT_SLOT correctness (`:88`–`153`), SHAPELESS order-independence and duplicate-ingredient handling (`:158`–`228`), SHAPED translation (`:233`–`306`, with a mocked Bukkit via `withBukkitVanillaNoMatch` `:73`), and `consume` amount math (`:311`–`356`).

---

## Unfinished Things / TODOs

- **No `/recipe` command** — nothing recipe-related is registered in `Valmora.onEnable()` (`Valmora.java:227`–`259`). Admins edit YAML and `/valmora reload`.
- **`RecipeModule` not exposed via `ValmoraAPI`** — external code must cast to `Valmora` (`Valmora.java:345`).
- **`enchanting_table` has no dynamic handler** — `guis/enchanting.yml:4` declares the machine but only vanilla fallback (and any static recipes) will ever match. Enchanting is currently handled by script events (`enchant_apply`/`enchant_select`/`enchant_back`, see `enchanting.yml:94`/`110`/`124`), not the recipe engine.
- **Vanilla fallback is machine-agnostic** — `matchVanillaRecipe` runs in step 3 of `match()` for *every* machine (`RecipeEngine.java:97`–`101`). Because `buildInputSnapshot` publishes numeric aliases, a forge/alchemy GUI can surface vanilla results that were never intended for that machine.
- **Static `alchemy.yml` recipes only fire for GLASS_BOTTLE bases.** The dynamic `AlchemyMachineHandler` shadows them for `POTION` bases (dynamic handlers checked first, `RecipeEngine.java:82`). This is *coherent* with the file header comment (`recipes/alchemy.yml:4`–`9`) but means the `_potion` variants are effectively unreachable through the GUI's dynamic path.
- **`onDisable` does not unregister dynamic handlers** — it's fine today because `onEnable` rebuilds the engine (`RecipeModule.java:24`), but nothing enforces it if the engine ever becomes persistent.
- **Anvil cost is a coin-script, not the economy service** — `variable add player.var.coins -<cost>` (`AnvilMachineHandler.java:94`) vs. Reforge's `plugin.getEconomy().removeCoins(...)` (`ReforgeModule.java:283`–`287`). Inconsistent and dependent on profile-var wiring.
- **Anvil has no durability/XP cost** and no charge-limit book handling beyond the table ceiling.
- **Only the first output is honored** — `buildOutput` reads `recipe.getOutputs().values().iterator().next()` (`RecipeEngine.java:60`); multi-output definitions silently ignore everything after the first.
- **SHAPELESS `amount` is required** in the list-map form — a missing `amount` throws and fails the whole file's parse (`RecipeDefinitionParser.java:32`).
- **SHAPELESS duplicate ingredients require distinct slots** — one stack can never satisfy two identical requirements, even with enough amount (see test at `RecipeEngineTest.java:211`–`228`).
- **No smithing support** — AGENTS.md §11.20 documents the modern 3-slot `SmithingTransformRecipe`/`SmithingTrimRecipe`; the recipe engine has no smithing recipe type.

---

## Possible Improvements / Changes

- **Expose `RecipeModule` on `ValmoraAPI`** (add `getRecipeModule()` alongside the other accessors in `ValmoraAPI.java:19`–`69`).
- **Gate the vanilla fallback to the `crafting_table` machine** (or behind a YAML `allow-vanilla: true` flag) so forge/alchemy machines stop leaking vanilla outputs.
- **Add a `DynamicMachineHandler` for `enchanting_table`** so enchanting can use the engine like anvil/alchemy instead of pure script events.
- **Replace the anvil coin script with `EconomyService`** (`ValmoraAPI.getEconomy()`), matching `ReforgeModule.deductCoins` (`ReforgeModule.java:283`).
- **Add a recipe command** (e.g. `/recipe list <machine>`, `/recipe reload`) registered in `Valmora.onEnable()` per AGENTS.md §6.3.
- **Support recipe priorities / deterministic ordering** — currently first-match-wins in file-iteration order (`RecipeModule.java:51`), which is implicit and fragile.
- **Multi-output recipes** — honor every entry of `outputs` rather than only the first (`RecipeEngine.java:60`).
- **Enforce handler unregistration** in `onDisable` (or keep the recreate-engine pattern but document it as the contract).
- **Add a `smithing` machine + SHAPED-style template handling** aligned with AGENTS.md §11.20.
- **Support crafting requirements** (skill level, permission, item rarity) in `RecipeDefinition` for recipe-gated content — matches the design intent visible in ABILITIES_DUMP.md ("Crafting Recipe | Requires …").
