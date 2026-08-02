# GUI Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.gui` | **Module ID:** `gui`
> **Files:** 36 Java files (~3181 lines) in `src/main/java/org/nakii/valmora/module/gui/` + 17 YAML definitions in `src/main/resources/guis/`
> **Config:** `plugins/Valmora/guis/*.yml` (runtime) — auto-copied from the jar on first run
> **Registration order:** registered at `Valmora.java:201` — after `combat` (:200), before `recipe` (:202). Later modules (`recipe`, `alchemy`, `enchant`, `collection`, `npc`, `warp`) depend on it.
> **Dependencies at enable time:** `script` (event factory registry, condition/expression evaluators, `VariableResolver`), `items` (`ItemManager`, `ItemTranslator`), `recipe` (`RecipeEngine`, `RecipeDefinition`, `RecipeIngredient`), `enchant` (`EnchantmentHelper`, `EnchantmentDefinition`), `profile` (`ValmoraPlayer`), `skill` (`SkillDefinition`). Lazy consumers of the session/props model: `economy`, `alchemy`, `progression`, `quest`.

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

The **GUI Module** is Valmora's menu system. It renders **fully YAML-driven GUIs** (menus, machine interfaces, bank, skill trees) and is the backbone of the crafting/alchemy/enchanting machines: a GUI definition can declare **input slots**, an **output slot**, and a **machine id**, at which point the `recipe` module takes over to show and craft matching recipes inside the open inventory.

The module is driven entirely by data — there are no hardcoded menus. Each GUI is a YAML file in `plugins/Valmora/guis/` containing a **layout of ASCII characters**, a **components table** mapping each character to a component type, and **script events** wired to clicks, states, and update ticks. The only Java that ships behavior is:

- **`GuiModule`** — the `ReloadableModule` facade: loads definitions, registers listeners/factories, owns the per-player session registry, opens/closes sessions, and registers dynamic per-GUI commands (`/skills`, `/collections`, `/geomancy`).
- **`GuiRenderer`** — renders a definition into a live `Inventory` at open time and on every re-render, resolving `$variables$` and pagination.
- **`GuiListener`** — routes every click/drag/close to the right component behavior, handles output crafting and slot locking.
- **`GuiDefinitionParser`** — turns YAML sections into `GuiDefinition` objects via the shared `YamlLoader`.

### The big picture

```
GuiModule.openGui(player, id, props)                       GuiModule.java:71
    │
    ├─ 1. Build a temp GuiSession, run the on-open event block
    ├─ 2. Create a Paper Inventory (rows×9) with a generated title
    ├─ 3. Store the session in openSessions[player UUID]
    ├─ 4. GuiRenderer.render(session): clear inv → layout chars → resolve $vars$ → paginate
    │        → snapshot input items → write output via RecipeEngine.match
    └─ 5. player.openInventory(inv)
            └─ schedule repeating update task (update-interval ticks, default: never)

player clicks ...                      GuiListener.onInventoryClick      GuiListener.java:33
    ├─ DISPLAY / state → run state actions (or base actions) on matching ClickType
    ├─ PAGINATED     → resolve list, run the matching state's actions with $iterator$ set
    ├─ PREVIOUS/NEXT → flip session.currentPage, re-render
    ├─ INPUT         → unlock slot, allow pickup, re-match recipe
    ├─ OUTPUT        → craft (single or mass), consume ingredients, re-render
    └─ close → on-close event block, refund input items, cancel update task   GuiListener.java:365
```

### Key design decisions

1. **GUIs are data, not code.** New screens are shipped as YAML; only genuinely reusable behaviors (pagination, recipe output, variable resolution) live in Java. The enhancement plan (`docs/GUI_MODULE_ENHANCEMENT_PLAN.md`) states Phases 1–4 complete; Phase 5 (docs/optimization) is still open.
2. **Single global session per player.** `openSessions` is keyed by `Player#getUniqueId()` (`GuiModule.java:44`); opening a new GUI replaces the old session in the map. This is a simple model but has a task-leak edge case (see §8).
3. **Recipe module owns output.** The GUI module never knows recipes. It exposes the input items as a `MachineContext` (via `GuiSession.buildInputSnapshot`) and lets `RecipeEngine` pick the result; the output slot is then re-filled by `updateOutputSlot`/`updateRecipeOutput`.
4. **Everything is main-thread.** No async DB calls, no `runTaskAsynchronously`, no executor. All rendering, matching, and event execution happens synchronously on the server thread (`GuiRenderer.render` → craft flow). This sidesteps the §7.4 async rule entirely.
5. **MiniMessage everywhere.** All display text goes through `GuiRenderer.createItemStack` → `Formatter.format`, never `ChatColor`/`§`. Item display names are set via the Adventure `ItemMeta#displayName` overload (AGENTS.md §11.3).

---

## 2. Code Structure

All 36 files live under `src/main/java/org/nakii/valmora/module/gui/`:

```
module/gui/
├── GuiModule.java            # ReloadableModule facade + session registry + dynamic commands  (245)
├── GuiDefinition.java        # Immutable GUI data model (54)
├── GuiSession.java           # Per-player open session: props, page, timers, input snapshot   (106)
├── GuiListener.java          # Inventory click / drag / close handling (447)
├── GuiItemStack.java         # record(material, name, lore, customModelData, amount) (15)
├── GuiEventBlock.java        # record(conditions, actions, failActions) — a script event block (11)
├── ClickHandler.java         # record(conditions, actions, failActions) — a click handler (11)
├── GuiExecutionContext.java  # ExecutionContext + GuiSession + loop-variable scope (35)
├── GuiVariableProvider.java  # `gui` namespace variables (274)
├── GuiCommand.java           # `/gui open <player> <id>` admin command (68)
├── GuiOpenCommand.java       # Dynamic per-GUI command from YAML `command:` key (46)
│
├── components/               # Component data model
│   ├── DisplayComponent.java   # display item + per-ClickType actions + states (35)
│   ├── InputComponent.java     # input slot id (15)
│   ├── OutputComponent.java    # output slot id (15)
│   ├── PaginatedComponent.java # list expression + iterator + states + sort (33)
│   ├── PaginatedState.java     # record(condition, displayItem, actions) (13)
│   └── PageButtonComponent.java# prev/next arrow + fallback (28)
│
├── parser/
│   ├── GuiDefinitionParser.java  # YAML → GuiDefinition (172)
│   └── ClickHandlerParser.java   # actions section → ClickHandler (28)
│
├── renderer/
│   └── GuiRenderer.java        # render pipeline, pagination, variable resolution, output (469)
│
├── event/                      # Script event DSL factories (13 files, ~943 lines)
│   ├── SoundEventFactory.java        # sound [player] <sound-id>
│   ├── OpenGuiEventFactory.java      # open_gui <gui-id> [key=value ...]
│   ├── CloseEventFactory.java        # close
│   ├── GiveXpEventFactory.java       # givexp player <SKILL> <amount>
│   ├── EnchantApplyEventFactory.java # enchant_apply <input> <enchant> <level>
│   ├── EnchantSelectEventFactory.java# enchant_select <input> <enchant>
│   ├── EnchantRemoveEventFactory.java# enchant_remove <input> <enchant>
│   ├── EnchantBackEventFactory.java  # enchant_back
│   ├── GuiForceCraftEventFactory.java# gui_force_craft
│   ├── AlchemyBrewStartEventFactory.java # gui_alchemy_start
│   ├── AlchemyBrewEventFactory.java      # gui_alchemy_brew
│   ├── OpenDialogInputEventFactory.java  # open_dialog_input <prop> ...
│   └── OpenSignInputEventFactory.java    # open_sign_input <prop> ... (⚠ NOT registered — dead code, §8)
│
└── sign/                       # Virtual sign input (⚠ NOT wired — dead code, §8)
    ├── SignInputManager.java   # per-UUID prop key + openVirtualSign (44)
    └── SignInputListener.java  # UncheckedSignChangeEvent handling (69)

src/main/resources/guis/         # 17 YAML files → 19 GUI definitions (§4)
```

`GuiModule` is the de-facto "manager" for this feature. There is **no** `GuiManager`/`hardcoded/` package in the codebase despite the enhancement plan naming one — the module itself fills that role.

---

## 3. Architecture & Key Classes

### 3.1 `GuiDefinition` — the data model

Immutable, constructor-injected value object (`GuiDefinition.java:12-53`). Fields:

| Field | Source | Notes |
|---|---|---|
| `id` | top-level YAML key | not normalized to lowercase (`GuiDefinitionParser.java:20`) |
| `title` | `title:` | default `"Inventory"`; MiniMessage formatted at open time (`GuiModule.java:94-98`) |
| `rows` | `layout:` list size | `rows:` YAML key is **ignored** — parser always uses `layout.size()` (`GuiDefinitionParser.java:29`) |
| `machineId` | `machine:` | default = `id`; handed to the recipe module for matching |
| `updateIntervalTicks` | `update-interval:` | default 0 = no repeating task |
| `components` | `components:` | map char-key → `GuiComponent` |
| `eventBlocks` | `on-open` / `on-close` / `on-slot-update` / `on-update` | `Map<String, GuiEventBlock>` |
| `command` / `commandPermission` | `command:` / `command-permission:` | optional dynamic open command |
| `displayMap` / `stateMap` | — | render-time only (built by `GuiRenderer`, not persisted) |

### 3.2 `GuiSession` — one open GUI

Per-player state object (`GuiSession.java`). Constructed with the definition, the player, and a **props map**. Responsibilities:

- **Props** (`props`, line 22): session-local variables, written by `open_gui key=value`, the enchanting flow, alchemy brew flow, and bank flow. Read by `PropVariableProvider` as `$prop.<key>$`.
- **Page state** (`currentPage`, line 25): pagination index, advanced by `PageButtonComponent`.
- **Update task** (`updateTask`, line 24): scheduled by `GuiModule.openGui` when `update-interval > 0`; cancelled in `closeGuiSession`.
- **Input snapshot** (`cachedInputSnapshot`): `Map<String, ItemStack>` produced by `snapshotInputs()` (line 59). `buildInputSnapshot()` (line ~72) publishes **each INPUT slot twice** — once under its component id (`"ingredient"`) and once under its zero-based layout index (`"0"`, `"1"`, …). The recipe engine reads `Map<String, ItemStack>` machine contexts.
- **Locking** (`craftingLocked`, `inputPending`, `inputPropKey`): guards refund-on-close and dialog input state.
- **`parent` (line 19)** is declared but **never set** — the `BACK` navigation the user-facing docs mention has no backing implementation (§8).

### 3.3 `GuiModule` — lifecycle

**`onEnable()` (`GuiModule.java:36`)** — idempotent, hot-reload safe:

1. Registers the `GuiVariableProvider` (`gui` namespace) into the script `VariableProviderRegistry`.
2. Registers 12 event factories (one per `event/*.java` except `OpenSignInputEventFactory` — see §8) so their DSL becomes available inside any GUI script.
3. Registers `GuiListener`.
4. `loadGuis()` — iterates `plugins/Valmora/guis/*.yml`, running each through `YamlLoader<GuiDefinition>` with `new GuiDefinitionParser()`; every top-level YAML key becomes a GUI id. Unknown component types log a warning and are skipped.
5. Registers dynamic per-GUI open commands for every definition that declares `command:` (`registerGuiCommand`).

**`onDisable()` (`GuiModule.java:59`)** — unregisters the listener, **closes every open session** (refunds inputs, runs `on-close`), unregisters dynamic commands, clears the registry. Closing sessions on disable is what makes `/valmora reload` not eat player items.

**Session management.** `openGui(player, id, props)` (`:71`):

1. Rejects unknown ids with a message (`getGuiRegistry().containsKey`).
2. Builds a **temporary** `GuiSession`, runs the `on-open` event block against it (so the block can seed props via `open_gui … key=value`).
3. Creates the `Inventory` (`def.getRows()*9`), builds the final `GuiSession`, **overwrites** `openSessions.put(uuid, session)` (`:113`).
4. `GuiRenderer.render(session)`, `player.openInventory(inv)`, then schedules the update task if `update-interval > 0`.

**Refund-on-close** (`closeGuiSession`, `:140`): cancels the update task, runs `on-close`, then returns every item still sitting in an **INPUT** component slot to the player's inventory (drop at feet if the player inventory is full). OUTPUT slots are never refunded — they are renderer-managed and cannot legitimately hold player items.

### 3.4 `GuiRenderer` — the render pipeline

`GuiRenderer.render(session)` (`GuiRenderer.java:30`) is the single entry point, called on open, on every re-render, on page change, and after output matching:

1. **Save** current INPUT/OUTPUT contents (`saveInputItems`), `session.snapshotInputs()`, then `inv.clear()`.
2. **Layout pass** — walk `def.getComponents()`:
   - `DisplayComponent` → `renderLayout`: build item from `display-item`, apply per-state display (`findMatchingState`), add PDC identity keys (item id + slot), set in inventory at each char position.
   - `InputComponent` → write PDC-tagged placeholder item; record slot.
   - `OutputComponent` → record slot; **skip** filling (filled later by recipe match).
   - `PaginatedComponent` → `renderPaginatedSlot`: resolve the list expression, slice by page size, resolve each entry, pick a state via `findMatchingState`.
   - `PageButtonComponent` → `renderPageButton`: enabled arrow or `fallback` item; disabled when no next/prev page.
3. **Restore** input items into their slots (`restoreInputItems`), clear the snapshot.
4. **Output pass** — `updateOutputSlot(session)` fills the output slot via the recipe engine (§3.6).

**Variable resolution** (`resolveVariables`, `:319`): replaces `$token$` with the resolved value. Special-cases:
- `$loop_item$` → the current paginated entry.
- `$iterator.key$` → for `Map` loop items.
- `$iterator.id$`, `$iterator.name$`, `$iterator.level$`, … → for `EnchantmentDefinition` loop items.
- Otherwise delegates to the shared `VariableResolver.resolveTemplate` chain (`$prop.*$`, `$gui.*$`, `$player.*$`, `$range.*$`, …).

**Sorting** (`sortList`): for `PaginatedComponent` with `sort: asc|desc` + `sort-key`, the list is sorted on the `sort-key`'s value (extracted from each map entry / definition) before slicing. `none` (default) keeps source order.

### 3.5 `GuiListener` — click, drag, close

`onInventoryClick` (`GuiListener.java:33`) is the dispatch hub:

- **Top-inventory clicks are always cancelled**; pickup/slot-clicks are re-allowed only for INPUT component slots once unlocked.
- **Component routing:**
  - `DisplayComponent` → if the component has *states*, evaluate the matching state's actions; else run the component's `actions` for the clicked `ClickType` (`LEFT`, `RIGHT`, `SHIFT_LEFT`, `MIDDLE`, …).
  - `PaginatedComponent` → `handlePaginatedClick`: recompute the list, index into the current page, look up the state, and execute its actions with the loop variable in scope (`GuiExecutionContext` carries `loopVars`).
  - `PageButtonComponent` → `handlePageButtonClick`: `currentPage ± 1` (clamped), `render()`.
  - `OutputComponent` → `handleOutputClick` (§3.6).
  - `InputComponent` → if locked (`isInputLocked` — during alchemy brew, slots with id `bottle` or `ingredient` are locked), cancel; otherwise unlock and allow pickup, then `triggerSlotUpdate`.
- **Bottom inventory** (player side) shift-clicks are cancelled so items can't bypass the machines.
- **Event `fail-actions`** run when the conditions block evaluates false — used heavily by alchemy (start brew) and enchanting (set selection).

`onInventoryClose` (`:365`): if the session is not awaiting input, `closeGuiSession` (refund + on-close). `onInventoryDrag` (`:376`): only INPUT component slots are draggable.

### 3.6 Output slot / recipe integration

Two collaborators keep the output slot live:

1. **`GuiRenderer.updateOutputSlot`** — after every render, asks `RecipeEngine` for a match on `MachineContext(session.getMachineId(), session.buildInputSnapshot())`. If found, the result item is placed in the output slot; the `output-display` isn't stored — the actual `ItemStack` is.
2. **`GuiListener` output click → `handleOutputClick`** — crafts the matched recipe:
   - **Mass craft**: shift-click loops up to 64 crafts while ingredients remain and output can fit.
   - Uses `RecipeEngine.consume` (custom recipes) or `consumeVanillaIngredients` (vanilla recipes), gated by `session.isCraftingLocked()` so the click handler can't be re-entered mid-craft.
   - Runs the definition's `on-craft`-style events through `GuiForceCraftEventFactory` where forced crafting is required (e.g. anvil / forge flows), then schedules `updateRecipeOutput` to re-render the output slot.

The **recipe-id lock** (`session`-scoped, stored on the session) prevents a player from swapping inputs mid-craft and receiving a different recipe's result.

### 3.7 Variable providers

- **`GuiVariableProvider`** (`GuiVariableProvider.java`) — namespace `gui`:
  - `gui.input.<id>.<prop>` → item property of the input slot (`id`, `item_type`, `material`, `amount`, `count` = filled slots for that component id, `available_enchants`).
  - `gui.enchanting.display_list` → builds the **two-phase enchanting catalog** (`List<Map<String,Object>>` of `EnchantmentDefinition` entries, or level-pages with `state` = `available`/`active`/`locked`) used by `enchanting.yml`'s paginated list.
  - `gui.enchanting.has_selection` → `true` while a `selected_enchant` prop is set.
  - `gui.viewed_skill.*` → level/progress/XP fields for the skill page from `viewed_skill` (a `ValmoraPlayer`).
- **`PropVariableProvider`** (script module) — namespace `prop`, session props, deep map/section access (`$prop.selected_enchant.level$`).
- **`RangeVariableProvider`** (script module) — namespace `range`; `$range.<start>.<end>$` yields an int list; the endpoint can itself be a variable path.
- **Loop variables** — `$iterator$`, `$iterator.key$`, `$iterator.<field>$`, `$loop_item$` injected by the renderer/click path.

### 3.8 The script event DSL

Registered into the script `EventFactory` registry in `GuiModule.onEnable` (`GuiModule.java:41-53`). Available tokens inside any `actions`/`fail-actions` list:

| Token | Syntax | Behavior | Factory |
|---|---|---|---|
| `sound` | `sound [player] <sound-id>` | play a sound (default `player` target) | `SoundEventFactory.java` |
| `open_gui` | `open_gui <gui-id> [key=value …]` | open another GUI, passing props | `OpenGuiEventFactory.java:29-41` |
| `close` | `close` | close the session (refund inputs, run on-close) | `CloseEventFactory.java:27` |
| `givexp` | `givexp player <SKILL> <amount>` | grant skill XP | `GiveXpEventFactory.java` |
| `enchant_apply` | `enchant_apply <inputSlotId> <enchantId> <level>` | apply enchant to item in slot | `EnchantApplyEventFactory.java` |
| `enchant_select` | `enchant_select <inputSlotId> <enchantId>` | store `selected_enchant` prop | `EnchantSelectEventFactory.java` |
| `enchant_remove` | `enchant_remove <inputSlotId> <enchantId>` | strip an enchant | `EnchantRemoveEventFactory.java` |
| `enchant_back` | `enchant_back` | clear selection / reset enchanting page | `EnchantBackEventFactory.java` |
| `gui_force_craft` | `gui_force_craft` | force craft via the recipe engine | `GuiForceCraftEventFactory.java` |
| `gui_alchemy_start` | `gui_alchemy_start` | begin a brew cycle (consume ingredient, set `brew_result`/`brew_running` props) | `AlchemyBrewStartEventFactory.java` |
| `gui_alchemy_brew` | `gui_alchemy_brew` | finish the cycle, place result | `AlchemyBrewEventFactory.java` |
| `open_dialog_input` | `open_dialog_input <prop> [title] [label] [placeholder …] [return=<gui-id>]` | modal one-line text input | `OpenDialogInputEventFactory.java` |
| `open_sign_input` | `open_sign_input <prop> [placeholder …]` | virtual sign input | `OpenSignInputEventFactory.java` ⚠ dead |

Additional cross-module tokens are available because all factories share one registry: `economy_deposit`/`economy_withdraw` (bank GUI), `progression_levelup`/`progression_unlock_tier`/`progression_reset` (geomancy tree), `quest_board_collect` (quest board).

### 3.9 Dynamic GUI open commands

`command:` + `command-permission:` in a GUI YAML cause `GuiModule.registerGuiCommand` (`GuiModule.java:154-170`) to register a `GuiOpenCommand` into the **root `CommandMap`** at runtime (`plugin.getServer().getCommandMap().register("valmora", cmd)`). On `onDisable`/reload, `unregisterGuiCommands` removes them from the map and syncs with clients via reflection. This is how `/skills`, `/collections`, and `/geomancy` exist without `plugin.yml` entries.

**Edge case:** if the command name collides with an existing `plugin.yml` command, `CommandMap.register` fails silently-ish (logs a warning). `/collections` is registered both ways — the `plugin.yml` executor (`Valmora.java:249`) wins, and the YAML-driven one effectively never fires. `/skills` (plural) does not collide with `/skill` (singular).

### 3.10 Dynamic GUIs

Two definitions are genuinely stateful and worth describing:

- **Enchanting table** (`enchanting.yml`): a `PaginatedComponent` whose list is served by `gui.enchanting.display_list`. Clicking a catalog entry fires `enchant_select` (stores `selected_enchant`), which flips the list into level-pages (states `available`/`active`/`locked`). `enchant_apply` applies the enchant to the item in the input slot and sets `available_enchants`; `enchant_back` returns to the catalog. Item identity and re-application are guarded via PDC checks (`GuiListener.java:367-378` region).
- **Alchemy table** (`alchemy.yml`): `on-open` seeds brew props; `on-slot-update` resets them when the ingredient slot empties; `on-update` ticks the timer and fires `gui_alchemy_brew` at zero. A failed conditions block on the craft state runs `gui_alchemy_start`, which locks the `bottle`/`ingredient` slots via `isInputLocked`.

---

## 4. Configuration (YAML)

Runtime config: `plugins/Valmora/guis/*.yml` (17 shipped files). Files are auto-copied from the jar on first boot (`Valmora.saveAllResources`, `Valmora.java:469`) and **never overwritten** afterwards; `/valmora reload` re-parses whatever is on disk. A GUI can be redefined by editing the file and reloading — no code change.

**Shipped definitions:**

| File | GUI ids | `command:` | machine |
|---|---|---|---|
| `stats.yml` | `stats` | — | — |
| `skills_list.yml` | `skills_list` | `skills` | — |
| `skills_details.yml` | `skill_details` | — | — |
| `shardworks_quest_board.yml` | `shardworks_quest_board` | — | — |
| `reforge_anvil.yml` | `reforge_anvil` | — | `reforge_anvil` |
| `reforge.yml` | `reforge` | — | `forge_random` |
| `geomancy_tree.yml` | `geomancy_tree` | `geomancy` | — |
| `forge.yml` | `forge` | — | `forge` |
| `enchanting.yml` | `enchanting_table` | — | `enchanting_table` |
| `crafting.yml` | `crafting_table` | — | `crafting_table` |
| `collections_list.yml` | `collections_list` | — | — |
| `collections_detail.yml` | `collections_detail` | — | — |
| `collections_categories.yml` | `collections_categories` | `collections` ⚠ collides | — |
| `bank.yml` | `bank`, `deposit`, `withdrawal` | — | — |
| `anvil.yml` | `anvil` | — | `anvil` |
| `alchemy.yml` | `alchemy_table` | — | `alchemy` |
| `active_effects.yml` | `active_effects` | — | — |

### 4.1 GUI-level keys

| Key | Type | Default | Notes |
|---|---|---|---|
| `<id>:` | map key | required | GUI id, **case-sensitive** (`GuiModule.java:106-110`; not normalized per §7.2) |
| `title:` | string | `"Inventory"` | MiniMessage, formatted at open time |
| `rows:` | int | **ignored** | parser always uses `layout.size()` (`GuiDefinitionParser.java:29`) |
| `layout:` | list[string] | required | row strings; each padded to 9 chars, row count = `rows` |
| `update-interval:` | int | `0` | ticks between repeating `on-update` runs; 0 disables |
| `machine:` | string | `= <id>` | recipe-module machine key |
| `command:` | string | — | registers a dynamic open command (see §3.9) |
| `command-permission:` | string | — | permission required for that command |
| `on-open:` | event block | — | runs once when the GUI opens (may seed props) |
| `on-close:` | event block | — | runs when the GUI closes |
| `on-slot-update:` | event block | — | runs when an input slot's contents change |
| `on-update:` | event block | — | runs every `update-interval` ticks |

An **event block** is `conditions:` (list of script expressions), `actions:` (list of DSL tokens), `fail-actions:` (list, run when `conditions` fails). One or more of the three may be absent.

### 4.2 Component keys (`components.<key>`)

A component key can be **a single character** (one layout slot) or **a multi-character string** — every character in the string maps to the same component. This is how `skills_details.yml` and the paginated pages cover 20+ slots with one definition (`GuiDefinitionParser.java:50-53`).

| Key | Type | Default | Notes |
|---|---|---|---|
| `type:` | string | `DISPLAY` | one of `DISPLAY`, `INPUT`, `OUTPUT`, `PAGINATED`, `PREVIOUS_PAGE`, `NEXT_PAGE`; unknown → skipped with warning |
| `display-item:` / `item:` | item section | AIR | `material` (or `item`), `name`, `lore` (list), `custom-model-data`, `amount` |
| `actions.<ClickType>:` | event block | — | handler for `LEFT`/`RIGHT`/`SHIFT_LEFT`/`MIDDLE`/… (any `org.bukkit.event.inventory.ClickType`) |
| `states.<name>:` | state section | — | `condition:` (default `"default"` = always), `display-item:`/flat item keys, `actions:` |

Per-type extras:

- **INPUT**: `id:` — the slot identifier used by `gui.input.<id>.*` and recipe input mapping.
- **OUTPUT**: `id:` — the output slot identifier (rendered by the recipe engine).
- **PAGINATED**: `list:` (variable path, e.g. `$gui.enchanting.display_list$`), `iterator:` (default `loop_item`), `destructure:` (parsed, **unused** — §8), `path:` (defaults to the component key when the key is multi-char; `indexInPage` is the char position), `sort:` (`none`/`asc`/`desc`, default `none`), `sort-key:` (field to sort on), `states:`.
- **PREVIOUS_PAGE / NEXT_PAGE**: `display-item:` + `fallback:` (item section) — the arrow and its disabled look.

---

## 5. Data Model / Persistence

- **No database tables.** The GUI module is fully in-memory. Sessions, props, and page state live only while the inventory is open.
- **No cross-reload persistence.** `onDisable` closes every session (running `on-close`, refunding input items to the player) and clears the registry. Props are lost on close — nothing is written to `ValmoraPlayer` or SQLite.
- **The only "data" that survives a session boundary** is whatever flows explicitly through `open_gui <id> key=value` (e.g. the bank's `deposit`/`withdrawal` GUIs re-open `bank` with `return=<bank>` after dialog input).
- **Items** inside INPUT slots are player items moved into the temporary inventory; they are returned by `closeGuiSession` (player inventory, else dropped at the player's location). `brew_result`/output items are clones produced by the recipe engine and never refunded.
- **Registry** is a plain `HashMap<String, GuiDefinition>` (`GuiModule.java:43`) — not a `Registry<T>`, so ids are case-sensitive and there is no lowercase normalization (§7.2 of AGENTS.md recommends `Registry<T>`; see §9).

---

## 6. API Exposed

The GUI module is **not** on `ValmoraAPI` — there are zero `gui` references in `ValmoraAPI.java`. Other modules reach it through the plugin instance: `plugin.getGuiModule()` (`Valmora.java:341`).

Public surface (`GuiModule`):

- `openGui(Player, String)` / `openGui(Player, String, Map<String,Object>)` — `GuiModule.java:136` / `:71`
- `closeGuiSession(Player)` — `:140`
- `getSession(UUID)` — `:181`
- `getGuiRegistry()` — `Map<String, GuiDefinition>` (checked before open by consumers)

Renderer helpers (`GuiRenderer`) are public so the listener and event factories can re-render: `render(GuiSession)`, `resolveVariables(...)`, `updateOutputSlot(...)`, `findMatchingState(...)`.

Event DSL: the 12 registered factories are public implementations of the script module's `EventFactory` contract and can be reused by other modules. `GuiExecutionContext` is the execution context subclass that `CollectionVariableProvider` etc. downcast to.

**Cross-module "API" is mostly the session props contract**: any module can read `$prop.*$` values produced by a GUI flow, and GUI scripts can call that module's event tokens. This is the documented, decoupled integration point (§6.4 of AGENTS.md — modules never hold `GuiSession` references directly).

---

## 7. Dependencies & Consumers

### Dependencies (loaded earlier: script, stat, player, economy, ui, ability, item, mob, skill, combat — `Valmora.java:188-200`)

| Dependency | Used for |
|---|---|
| `script` | variable registry, expression/condition evaluator, event factory registry, `VariableResolver` |
| `items` | `ItemManager.createItemStack`, `ItemTranslator` (vanilla recipe results) |
| `recipe` (loaded **after** gui, :202) | `RecipeEngine.match/consume`, `RecipeDefinition`, `MachineContext` — accessed lazily at render/click time, so ordering is safe |
| `enchant` (:204) | `EnchantmentHelper`, `EnchantmentDefinition` in enchant events + `display_list` |
| `profile` | `ValmoraPlayer` for `gui.viewed_skill.*` |
| `skill` | `SkillDefinition`/`SkillRegistry` for `givexp` and skill pages |
| `util` | `Keys` (PDC namespaces), `Formatter` |

### Consumers (open GUIs or depend on the module)

| Consumer | What it opens / uses |
|---|---|
| `recipe` | machine GUIs via `MachineContext`; output-slot crafting |
| `alchemy` | `EffectsCommand` opens `active_effects` (`EffectsCommand.java:26-32`); `AlchemyModule` runs the brew cycle; `PotionCommand` |
| `collection` | `CollectionCommand` opens `collections_categories` (`CollectionCommand.java:25`); `CollectionVariableProvider` downcasts to `GuiExecutionContext` |
| `npc` | `GuiOpenEventFactory` — the NPC dialogue event `gui <gui-id>` opens a GUI (`GuiOpenEventFactory.java:29`) |
| `warp` | `WarpCommand` opens `fast_travel` (`WarpCommand.java:28`) — **no default YAML ships for it** (§8) |
| `economy` | `economy_deposit`/`economy_withdraw` tokens used by `bank.yml` |
| `progression` | `progression_levelup`/`progression_unlock_tier`/`progression_reset` in `geomancy_tree.yml` |
| `quest` | `quest_board_collect` in `shardworks_quest_board.yml` |
| `profile` | profile command `gui` subcommand |

---

## 8. Unfinished Things / TODOs

- **Virtual sign input is dead code.** `OpenSignInputEventFactory`, `SignInputManager`, and `SignInputListener` are complete and compile, but **`GuiModule.onEnable` never registers the factory or the listener** (only 12 of 13 factories are registered). No reachable path can open a sign input today. Either wire it up or delete it. (`OpenSignInputEventFactory.java`, `sign/SignInputManager.java`, `sign/SignInputListener.java`)
- **`GuiSession.parent` is never assigned** (`GuiSession.java:19`). The documented `CLOSE`/`BACK` navigation has no `BACK` implementation — there is no `back` event token and no parent-session re-open. `enchant_back` only clears the enchanting selection.
- **`PaginatedComponent.destructure` is parsed but unused** (`GuiDefinitionParser.java:127`). The renderer and click handler ignore it.
- **Task leak on GUI-to-GUI open.** `openGui` overwrites `openSessions.put(uuid, …)` (`GuiModule.java:113`) **without cancelling the previous session's update task**. Every `open_gui` from within a GUI with `update-interval > 0` leaks a repeating Bukkit task until that old session is closed. `closeGuiSession` on the new session does not stop the old one.
- **`fast_travel` GUI is referenced but not shipped** (`WarpCommand.java:28`). `/warp` silently fails or misbehaves until an admin writes `fast_travel.yml`.
- **`collections_categories` `command:` collides** with the `/collections` plugin.yml command (`Valmora.java:249`); the YAML-driven command never runs. The `command:` key is otherwise fine.
- **`rows:` YAML key is dead** — the parser ignores it (always `layout.size()`). Harmless but misleading.
- **No GUI module tests.** Every other subsystem has at least one unit test under `src/test/java/.../module/`; the GUI module has none (its logic is heavily Bukkit-bound, but `GuiDefinitionParser`, `ClickHandlerParser`, and the pagination slicing are testable in isolation).
- **GUI ids are case-sensitive** (plain `HashMap`, `GuiModule.java:43`), diverging from AGENTS.md §7.2's `Registry<T>` convention.
- **Enhancement plan Phase 5 open** (`docs/GUI_MODULE_ENHANCEMENT_PLAN.md`): docs + optimization (paginated-list/NBT parsing caching, etc.) not yet done.
- `docs/todo.md:7` ("gui: add ability to register commands that open a gui") is effectively **done** via `command:`/`GuiOpenCommand` — the todo can be closed.

---

## 9. Possible Improvements / Changes

1. **Expose the module on `ValmoraAPI`** (`getGuiManager()`), replacing the `plugin.getGuiModule()` convention so it matches §6.4 decoupling and is reload-safe for API consumers.
2. **Cancel the previous session's update task in `openGui`** before overwriting the map entry — one line, removes the §8 leak.
3. **Wire the sign-input pipeline**: register `OpenSignInputEventFactory` + `SignInputListener`, and give `gui_alchemy_start`'s flow or a configurable button a `sign` action. This would also let banks use a real sign for amounts instead of `open_dialog_input`.
4. **Implement `BACK`** via `GuiSession.parent` + a `back` event token (or reuse `open_gui`), closing the documented-navigation gap.
5. **Use `Registry<GuiDefinition>`** (case-insensitive keys) and normalize ids in the parser for parity with every other registry (§7.2).
6. **Add parser/pagination unit tests** — `GuiDefinitionParser` and the pagination/sort slicing are pure enough to cover with JUnit 5 + Mockito following `ExpressionTest.java`'s `ValmoraAPI.setProvider` pattern.
7. **PDC-first button identity** (AGENTS.md §11.12): item identity is already keyed via PDC at render time, but click routing still depends on the layout char → component map; a hardened lookup could resolve purely from the clicked slot's PDC key.
8. **Ship a `fast_travel` default YAML** or guard `WarpCommand` when the GUI is absent.
9. **Phase 5 optimization**: cache resolved paginated lists per session+props signature, cache parsed NBT/display item stacks across re-renders (they're currently rebuilt on every `render()`).
10. **Remove dead keys/fields** (`rows:`, `destructure`) or implement them, and delete the unregistered sign classes if sign input is definitively dropped.
