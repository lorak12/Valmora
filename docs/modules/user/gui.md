# GUI Module — User Documentation

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Config:** `plugins/Valmora/guis/*.yml` (17 shipped files) | **Module ID:** `gui`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Player Guide](#2-player-guide)
3. [Admin Guide](#3-admin-guide)
4. [Configuration Reference](#4-configuration-reference)

---

## 1. Overview

The **GUI module** is Valmora's menu system. Every screen in the plugin — your stats page, the skill tree, the collections browser, the bank, and all crafting machines (crafting table, anvil, forge, enchanting table, alchemy table, reforge) — is a **GUI defined entirely in YAML** and rendered live into your inventory.

Nothing is hardcoded. An administrator can redesign any menu, change button behavior, add new pages, or point buttons at other GUIs by editing a file and running `/valmora reload`.

The module also connects the **recipe system** to the physical machines: when a GUI declares a `machine:` id and has input/output slots, the plugin automatically shows the correct crafted result in the output slot as you place ingredients, and lets you craft by clicking it.

GUIs understand **script events** (sounds, opening other GUIs, giving XP, applying enchants, starting alchemy brews), **conditions** (only do something if a condition holds, otherwise run a fallback), and **variables** (`$player.$`, `$prop.$`, `$gui.input.$` tokens) that get filled in live.

---

## 2. Player Guide

### 2.1 Opening GUIs

Menus open through a few entry points, depending on how the server is set up:

- **Commands** — `/skills`, `/collections`, `/geomancy` (permission-gated; see §3.4).
- **NPC dialogue** — an NPC dialogue can open a GUI as its response.
- **Machines** — right-click a machine block (alchemy table, anvil, forge, crafting table, enchanting table) or interact with its NPC.
- **Admin command** — an admin can open any GUI on you with `/gui open <yourname> <gui-id>`.

### 2.2 Navigation

- **Buttons** are display items (glass panes, heads, custom items). Left-click usually triggers them; some GUIs use right-click, shift-click, or middle-click — check the server's menu descriptions.
- **Arrow buttons** (`«` `»`) flip between pages in multi-page menus (skill details, collections, enchanting catalog).
- **Closing** — press <kbd>Esc</kbd> or click the close button. **Anything you placed into a machine's input slots is automatically returned to your inventory** (or dropped at your feet if your inventory is full). You can never lose items by closing a machine.

### 2.3 Crafting machines

Machines with a `machine:` id use the recipe system:

1. **Place ingredients** into the input slots.
2. The **output slot** immediately shows what the machine can craft from your current ingredients.
3. **Left-click** the output to craft **one**. **Shift-click** to craft **as many as possible** (up to a stack of 64).
4. Swap the result elsewhere and repeat — the output updates as the inputs change.

The recipe shown is locked for the duration of the craft, so your ingredients can't be swapped mid-craft to cheat a different result.

### 2.4 The enchanting table

The enchanting GUI is **two pages**:

1. A **catalog** of enchantments. Click one to select it (it becomes highlighted).
2. A **level list** for that enchantment. Available levels show the cost; you can apply an available level to the item in the input slot, remove an active enchant, or go **back** to the catalog.

Which levels are `available`, `active`, or `locked` depends on the item you placed and your setup.

### 2.5 The alchemy table

1. Place a **bottle** and an **ingredient** in the input slots.
2. Start the brew (click the appropriate button — if the conditions for the result aren't met, the GUI attempts to start a brew anyway and shows the failure feedback).
3. During the brew, the input slots **lock** so you can't swap ingredients mid-brew.
4. When the timer finishes, the result appears in the output slot.

### 2.6 The bank

The bank GUI (opened from its NPC) shows your balance. **Deposit** and **withdraw** open a small dialog where you type an amount; the flow returns you to the main bank screen afterward. If you click "deposit"/"withdraw" without a pending value, nothing happens — use the dialog.

### 2.7 Other pages

- **`/skills`** — skill list; click a skill to open its **details page** (level, progress bar, XP needed, per-level values).
- **`/collections`** — categories → collection list → collection detail (progress and rewards).
- **`/geomancy`** — the geomancy progression tree with tier nodes; spend points with the progression buttons.
- **`/effects`** — your active potion effects.

---

## 3. Admin Guide

### 3.1 File locations and reload

- **Authoring location:** `plugins/Valmora/guis/` — one YAML file per GUI, but a file may define **several** GUIs (e.g. `bank.yml` defines `bank`, `deposit`, and `withdrawal`).
- **Defaults:** the 17 files ship inside the plugin jar and are copied to the folder on first run. **They are never overwritten** — edit the on-disk copy freely.
- **Reload:** `/valmora reload` re-parses all GUI files and rebuilds every open menu. Players with a machine open at reload keep their items (they're refunded and re-opened).

### 3.2 Anatomy of a GUI definition

```yaml
bank:
  title: "<dark_aqua>Grand Bank"
  rows: 4
  layout:
    - "aaaaaaaaaaa"
    - "abcdefghijk"
    - "aaaaaaaaaaa"
  components:
    a:
      type: DISPLAY
      display-item:
        material: GRAY_STAINED_GLASS_PANE
        name: "<gray> "
    b:
      type: INPUT
      id: deposit_value
    ...
  on-open:
    actions:
      - "open_dialog_input pending_amount ..."
```

Key ideas:

- **`layout:`** is a list of strings. Each string is one inventory row; every character is one slot. The GUI height equals the **number of layout rows** (the `rows:` key is currently ignored). Long component keys can span many characters — every character maps to the same component.
- **`components:`** maps each character (or multi-char key) to a component describing what that slot does.
- **`on-open` / `on-close` / `on-slot-update` / `on-update`** attach script blocks to GUI lifecycle events.

### 3.3 Component types

| `type` | Purpose |
|---|---|
| `DISPLAY` (default) | A static or state-aware button. Has `display-item`, optional `actions` per click type, and optional `states`. |
| `INPUT` | A player-facing slot. `id:` is used by recipes and by `$gui.input.<id>.$` variables. |
| `OUTPUT` | The auto-filled craft result slot. `id:` names it. |
| `PAGINATED` | A dynamic list rendered across many slots (`list:`, `iterator:`, `sort:`, `states:`). See §3.6. |
| `PREVIOUS_PAGE` / `NEXT_PAGE` | Page-turn buttons with a `display-item` and a `fallback` for the disabled look. |

### 3.4 Permissions

| Permission | Effect |
|---|---|
| `valmora.admin` | `/valmora reload` (required to apply GUI edits) |
| `valmora.admin.gui` | `/gui open <player> <gui-id>` and its tab completion |
| `valmora.admin.gui.<something>` | base pattern — see note below |

**Dynamic commands:** if a GUI declares `command:` and `command-permission:`, the command is registered at runtime and gated by that permission. Shipped examples: `/skills`, `/collections`, `/geomancy`. If the command name collides with a `plugin.yml` command (e.g. `/collections`), the `plugin.yml` one wins — use a non-colliding name.

### 3.5 Click actions, conditions, states

**Actions per click type.** Every `actions` section is keyed by a Bukkit `ClickType`: `LEFT`, `RIGHT`, `SHIFT_LEFT`, `SHIFT_RIGHT`, `MIDDLE`, `DOUBLE_CLICK`, …

```yaml
b:
  type: DISPLAY
  display-item:
    material: PLAYER_HEAD
    name: "<gold>Back to bank"
  actions:
    LEFT:
      conditions:
        - "$prop.pending_amount$ != ''"
      actions:
        - "open_gui bank"
        - "sound player CLICK"
      fail-actions:
        - "sound player VILLAGER_NO"
        - "open_dialog_input pending_amount Return to bank"
```

- `conditions:` — script expressions; all must be true for `actions` to run.
- `actions:` — run when conditions pass.
- `fail-actions:` — run when conditions fail.

**States** let one slot change appearance and behavior dynamically (used heavily by enchanting and paginated pages):

```yaml
states:
  default:
    condition: "default"
    display-item: { material: GLASS_PANE, name: "<gray>Not selectable" }
  selected:
    condition: "$prop.selected_enchant$ == 'sharpness'"
    display-item: { material: GLOWSTONE_DUST, name: "<gold>Sharpness" }
    actions:
      LEFT:
        actions: [ "enchant_apply input sharpness 1" ]
```

The `condition` is either the literal `default` (always matches) or a script expression. For **paginated** lists the condition is evaluated per list entry, so you can render each row differently.

### 3.6 Pagination

A `PAGINATED` component turns a list of values into a set of slots:

```yaml
p:
  type: PAGINATED
  list: "$gui.enchanting.display_list$"   # or any $var$ resolving to a JSON list
  iterator: loop_item                     # optional; loop_item is the default
  sort: none                              # none | asc | desc
  sort-key: ""                            # field to sort on (map/object field)
  path: abcdefghijklmnopqrstuvwxy         # defaults to the component key
  states:
    available:
      condition: "$iterator.state$ == 'available'"
      display-item: { material: ENCHANTED_BOOK, name: "<green>Level $iterator.level$" }
      actions:
        LEFT:
          actions: [ "enchant_select input $iterator.id$ $iterator.level$" ]
    locked:
      condition: "$iterator.state$ == 'locked'"
      display-item: { material: BARRIER, name: "<dark_red>Locked" }
```

- `list` resolves through the variable system (typically a `$…$` token whose value is a JSON array).
- The `iterator` variable holds the current entry. For JSON objects use `$iterator.<field>$`; for maps `$iterator.key$` gives the key.
- Combined with `PREVIOUS_PAGE`/`NEXT_PAGE` slots, the same component definition renders each page; the current page is tracked per player.

### 3.7 Machines and the output slot

```yaml
forge:
  title: "<gold>Forge"
  layout: ["abcdefghijklmnopqrstu"]
  machine: forge                     # must match recipe definitions' machine id
  components:
    c: { type: INPUT,  id: base }
    d: { type: INPUT,  id: extra }
    e: { type: OUTPUT, id: result }
```

When `machine:` is present and a recipe with that machine id matches the inputs, the result appears in every `OUTPUT` slot automatically. Clicking the output crafts it (shift-click mass-crafts). The `machine` id defaults to the GUI id when omitted.

### 3.8 Script events usable in GUIs

Tokens accepted inside `actions`/`fail-actions` (plus every other event token registered by other modules — economy deposit/withdraw, progression level-up, quest-board collect, etc.):

| Token | Example | Effect |
|---|---|---|
| `sound` | `sound player CLICK` | play a sound (target defaults to `player`) |
| `open_gui` | `open_gui bank` / `open_gui skill_details skill=mining` | open another GUI, optionally passing props (`key=value`) |
| `close` | `close` | close the GUI (refunds inputs) |
| `givexp` | `givexp player MINING 50` | grant skill XP |
| `enchant_select` | `enchant_select input sharpness 5` | select an enchantment/level (enchanting page 2) |
| `enchant_apply` | `enchant_apply input sharpness 5` | apply the enchant to the item in the slot |
| `enchant_remove` | `enchant_remove input sharpness` | remove an enchant |
| `enchant_back` | `enchant_back` | return to the enchantment catalog |
| `gui_force_craft` | `gui_force_craft` | force a recipe craft (used by anvil/forge flows) |
| `gui_alchemy_start` | `gui_alchemy_start` | begin the brew cycle (consumes ingredient, locks slots) |
| `gui_alchemy_brew` | `gui_alchemy_brew` | finish the brew and place the result |
| `open_dialog_input` | `open_dialog_input amount How much? Amount 100 return=bank` | open a typed-amount dialog, stores the value in `$prop.amount$`, reopens `bank` on submit |

### 3.9 Variables available in GUIs

| Token | Meaning |
|---|---|
| `$prop.<key>$` | session prop set by `open_gui … key=value` or dialog input (deep keys like `$prop.selected_enchant.level$`) |
| `$gui.input.<id>.material$` / `.item_type$` / `.id$` / `.amount$` | the item in an input slot (`.count$` = number of filled slots for that input) |
| `$gui.input.<id>.available_enchants$` | enchantment list for the item in the slot |
| `$gui.enchanting.display_list$` | the enchanting catalog/level list (JSON) |
| `$gui.enchanting.has_selection$` | true while an enchantment is selected |
| `$gui.viewed_skill.level$`, `.xp$`, `.next_level$`, `.progress$`, `.xp_in_level$`, `.xp_required$` | the skill page currently being viewed |
| `$range.<start>.<end>$` | list of integers (end may be a variable) |
| `$iterator$`, `$iterator.key$`, `$iterator.<field>$`, `$loop_item$` | current entry in a paginated list |
| any other plugin variable | `$player.*$`, `$time.*$`, `$stat.*$`, etc. via the shared resolver |

---

## 4. Configuration Reference

### 4.1 GUI-level keys

| Key | Type | Default | Description |
|---|---|---|---|
| `<gui-id>:` | map key | **required** | The GUI's id (case-sensitive). Used by `/gui open`, `open_gui`, and NPC dialogue. |
| `title:` | string | `Inventory` | Inventory title; MiniMessage formatting supported. |
| `rows:` | int | `layout` length | **Currently ignored** — the number of `layout` rows is always used. |
| `layout:` | list of strings | **required** | One string per inventory row; each is padded to 9 columns; the component-key characters live here. |
| `update-interval:` | int | `0` | Ticks between `on-update` runs (`0` disables the repeating task). |
| `machine:` | string | the gui id | Recipe-engine machine id for output matching. |
| `command:` | string | — | Registers a runtime command that opens this GUI. |
| `command-permission:` | string | — | Permission required to run `command:`. |
| `on-open:` | event block | — | Runs when the GUI opens (can seed props via `open_gui … key=value`). |
| `on-close:` | event block | — | Runs when the GUI closes (after input refund). |
| `on-slot-update:` | event block | — | Runs whenever an input slot's contents change. |
| `on-update:` | event block | — | Runs every `update-interval` ticks. |

**Event block:** `conditions:` (list), `actions:` (list), `fail-actions:` (list). Any subset may be omitted.

### 4.2 Item section (used by `display-item`, `item`, `fallback`)

| Key | Type | Default | Description |
|---|---|---|---|
| `material` / `item` | string | `AIR` | Bukkit material name (`material` preferred; `item` is a fallback). |
| `name` | string | none | MiniMessage display name. |
| `lore` | list of strings | none | MiniMessage lore lines. |
| `amount` | int | `1` | Stack size. |
| `custom-model-data` | int | `0` | Custom model data for textured packs. |

### 4.3 Component keys

All components share:

| Key | Type | Default | Description |
|---|---|---|---|
| `type:` | string | `DISPLAY` | `DISPLAY`, `INPUT`, `OUTPUT`, `PAGINATED`, `PREVIOUS_PAGE`, `NEXT_PAGE`. |
| `display-item:` | item section | AIR | Static item (DISPLAY/PAGE_BUTTON). |
| `actions.<click-type>:` | event block | — | Handler for that `ClickType`. |
| `states.<name>:` | state section | — | State-dependent item + actions. |

**INPUT**

| Key | Type | Description |
|---|---|---|
| `id:` | string | Slot id used by recipes and `$gui.input.<id>.…$`. |

**OUTPUT**

| Key | Type | Description |
|---|---|---|
| `id:` | string | Output slot id. |

**PAGINATED**

| Key | Type | Default | Description |
|---|---|---|---|
| `list:` | string | — | Variable path resolving to a JSON list (e.g. `$gui.enchanting.display_list$`). |
| `iterator:` | string | `loop_item` | Variable holding the current list entry. |
| `destructure:` | bool | `false` | **Parsed but unused.** |
| `path:` | string | component key (if multi-char) | The char range covering the page's slots. |
| `sort:` | string | `none` | `none`, `asc`, or `desc`. |
| `sort-key:` | string | — | Field name to sort by when `sort` is set. |
| `states:` | state section | — | Rendered per list entry; condition sees `$iterator…$`. |

**PREVIOUS_PAGE / NEXT_PAGE**

| Key | Type | Description |
|---|---|---|
| `display-item:` | item section | The arrow when the page exists. |
| `fallback:` | item section | The arrow look when there is no page in that direction. |

### 4.4 Shipped GUI inventory

| GUI id(s) | File | Notes |
|---|---|---|
| `stats` | `stats.yml` | player stats page |
| `skills_list`, `skill_details` | `skills_list.yml`, `skills_details.yml` | skill browser; `/skills` |
| `collections_categories`, `collections_list`, `collections_detail` | `collections_categories.yml`, `collections_list.yml`, `collections_detail.yml` | collection browser; `/collections` |
| `geomancy_tree` | `geomancy_tree.yml` | progression tree; `/geomancy` |
| `bank`, `deposit`, `withdrawal` | `bank.yml` | bank flow |
| `enchanting_table` | `enchanting.yml` | enchanting machine |
| `alchemy_table` | `alchemy.yml` | alchemy machine |
| `anvil` | `anvil.yml` | anvil machine |
| `forge` | `forge.yml` | forge machine |
| `crafting_table` | `crafting.yml` | crafting machine |
| `reforge`, `reforge_anvil` | `reforge.yml`, `reforge_anvil.yml` | reforge flow |
| `shardworks_quest_board` | `shardworks_quest_board.yml` | quest board |
| `active_effects` | `active_effects.yml` | opened by `/effects` |

### 4.5 Known limitations

- GUI ids are **case-sensitive**; always use the exact id shown in the file.
- A dynamic `command:` name that collides with a `plugin.yml` command is silently won by the `plugin.yml` one.
- The `rows:` key and `destructure:` are accepted but have no effect.
- `fast_travel` (used by `/warp`) has **no shipped definition** — create `guis/fast_travel.yml` or `/warp` has nothing to open.
