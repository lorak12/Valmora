# Collection Module — User Documentation

> **What this module does:** tracks how many of each resource/action a player has gathered and unlocks staged rewards
> at milestone thresholds. Think of SkyBlock-style "Collections".
>
> **Command:** `/collections`
> **Permission:** none (available to all players by default)

---

## Overview

The Collection system is a **progression tracker** organized as:

```
Categories (e.g. Mining, Farming, Combat)
   └─ Collections (e.g. Coal, Wheat, Zombie)
        └─ Stages (e.g. 50 collected → 250 → 1,000 …)
```

Every time a player does something the system recognizes — breaking a block, killing a mob, fishing up a fish,
picking up an item, or crafting — the matching collection's **count** goes up by one. When a count reaches a stage's
`required` number, the rewards for that stage are granted automatically.

Progress is saved per **profile** (not per player) and stored in the database, so it survives restarts. Rewards are
granted instantly when a threshold is crossed — there is no "claim" step.

---

## Player Guide

### Opening the menu

Run `/collections`. This opens the **Collections** category browser.

### Menu layout

1. **Category browser** (`/collections`) — shows every category (Farming, Mining, Combat, Fishing, Foraging…). Each
   category icon shows how many of its collections you have fully completed (`Collections: completed/total`).
   Click a category to open its collection list.

2. **Collection list** (within a category) — every collection in that category, showing:
   - your current **Stage** (`Stage: 2/5`)
   - your **Count** and the next milestone (`Count: 137 / 250`)
   - a green `✔ Fully Completed!` label once you've finished the last stage.
   Click a collection to see its stages.

3. **Stage detail** — shows the collection's summary icon and every stage as a colored pane:
   - **Green** — completed stage
   - **Yellow** — your current (in-progress) stage, with live progress (`Progress: 137/250`)
   - **Gray** — locked stage (requires more progress than you have)

### How progress works

- Counts are **cumulative**. A stage that says `required: 250` means *250 total* of that resource — not 250 more
  after the previous stage.
- Multiple actions can feed the same collection. For example, the Coal collection counts breaking `COAL_ORE` **and**
  breaking `DEEPSLATE_COAL_ORE` **and** picking up `COAL` — they all add to the same counter.
- The current stage is always the highest stage you've reached. Your count never decreases.

### Example: Coal collection (as shipped)

| Stage | Total Coal required | Reward |
|---|---|---|
| 1 | 50 | Novice Miner title |
| 2 | 250 | +1 coal per vein |
| 3 | 1,000 | Coal Forging recipe unlocked |
| 4 | 10,000 | +5% smelting speed |
| 5 | 100,000 | Coal Baron title |

Reach 250 total and stage 1 **and** stage 2 rewards both trigger (stages are granted as you pass each threshold).

### Commands

| Command | Description |
|---|---|
| `/collections` | Opens the Collections menu. |

There are currently **no** admin or subcommands for collections.

---

## Admin Guide

### Where collections are defined

All collections live in the plugin data folder:

```
plugins/Valmora/collections/
├── categories.yml          ← All categories (required)
├── mining/                 ← folder structure is only for organization
│   ├── coal.yml
│   └── gems.yml
├── farming/
└── ...any subfolders...
```

On first startup the plugin copies the default files here. It will **not** overwrite files you edit afterwards, so you
can freely change values. After editing, run `/valmora reload` (requires `valmora.admin`) to apply changes.

### Defining a category

Add a block to `collections/categories.yml`:

```yaml
<category-id>:
  name: "<MiniMessage name>"
  icon: <MATERIAL>
  description: "<short description>"
```

Example (from the shipped file):

```yaml
mining:
  name: "<gray><bold>Mining</bold></gray>"
  icon: DIAMOND_PICKAXE
  description: "<gray>Ores, stones, and underground resources."
```

- The **key** (`mining`) is the category ID used by collections' `category` field.
- `name` and `description` accept MiniMessage formatting.
- `icon` is any Bukkit Material name.

### Defining a collection

Create a `.yml` file anywhere under `collections/` (a file per collection is a good convention):

```yaml
<collection-id>:
  category: <category-id>      # must match a category
  name: "<MiniMessage name>"
  icon: <MATERIAL>
  track:
    - "<EVENT_TYPE>:<IDENTIFIER>"
  stages:
    1:
      required: 50
      rewards:
        - "<script event line>"
    2:
      required: 250
      rewards:
        - "<script event line>"
```

### Track sources (what counts)

Each `track` entry is `EVENT_TYPE:IDENTIFIER`. Supported event types:

| Event type | What increments it | Identifier value |
|---|---|---|
| `BLOCK_BREAK` | Breaking a block | Block material name, e.g. `COAL_ORE` |
| `MOB_KILL` | Killing a mob (as the killer) | Mob type name, e.g. `ZOMBIE` |
| `FISHING` | Catching something while fishing | Item material name, e.g. `COD` |
| `ITEM_PICKUP` | Picking up an item from the ground | Item material name, e.g. `COAL` |
| `ITEM_PICKUP` (custom items) | Picking up a Valmora custom item | `custom:<valmora_item_id>`, e.g. `custom:my_sword` |
| `CRAFT` | Crafting an item | Result material name, e.g. `IRON_INGOT` |

Notes:

- Matching is **exact and case-sensitive** — write identifiers in the same case as shown above.
- Multiple sources are cumulative: several `track` lines all add to the same collection counter.
- One action can count toward several collections at once if they share a track source.
- For Valmora custom items always use the `custom:` prefix (`ITEM_PICKUP:custom:<item_id>`), not the bare item ID.

### Stages & rewards

- Stage keys must be positive **integers** (1, 2, 3, …). Non-integer keys are ignored.
- `required` is the **cumulative total** needed for that stage.
- `rewards` is a list of **script event lines**. When the stage threshold is crossed, these execute automatically
  (granting money, items, titles, running GUIs, etc. — whatever your script engine supports, e.g. `economy_add 100`,
  `give DIAMOND_PICKAXE:1 notify`).
- Rewards fire the moment the counter crosses the threshold. If several thresholds are crossed at once, all their
  rewards fire together.

### Complete example

```yaml
# plugins/Valmora/collections/mining/coal.yml
coal:
  category: mining
  name: "<dark_gray>Coal"
  icon: COAL
  track:
    - "BLOCK_BREAK:COAL_ORE"
    - "BLOCK_BREAK:DEEPSLATE_COAL_ORE"
    - "ITEM_PICKUP:COAL"
  stages:
    1:
      required: 50
      rewards:
        - "economy_add 100"
        - "notify Stage 1 unlocked!"
    2:
      required: 250
      rewards:
        - "economy_add 500"
    3:
      required: 1000
      rewards:
        - "give DIAMOND_PICKAXE:1 notify"
```

### Permissions

| Permission | Description |
|---|---|
| *(none)* | `/collections` is open to all players. |
| `valmora.admin` | Required for `/valmora reload`, which is how collection config changes are applied. |

---

## Configuration Reference

Every key read by the module, with defaults and explanations. Defaults come from
`CollectionDefinitionParser.java`.

### `collections/categories.yml`

| Key | Type | Default | Description |
|---|---|---|---|
| `<category-id>` | string (map key) | — | Category ID. Must be unique; referenced by collections' `category` field. |
| `name` | MiniMessage string | the category ID | Display name in the collections menu. |
| `icon` | Material name | `CHEST` | Category icon shown in the menu. |
| `description` | string | `""` | Short description shown under the category name. |

### Collection files (any other `*.yml` in `collections/`)

| Key | Type | Default | Description |
|---|---|---|---|
| `<collection-id>` | string (map key) | — | Collection ID. Must be unique. |
| `category` | string | `misc` | Category this collection belongs to. No load-time check is performed, so verify it matches a category. |
| `name` | MiniMessage string | the collection ID | Display name in the collections menu. |
| `icon` | Material name | `STONE` | Collection icon shown in the menu. |
| `track` | list of strings | `[]` | Track sources in `EVENT_TYPE:IDENTIFIER` form. Supported types: `BLOCK_BREAK`, `MOB_KILL`, `FISHING`, `ITEM_PICKUP`, `CRAFT`. |
| `stages` | map of int → stage | `{}` | Stage definitions, keyed by stage number (1, 2, 3, …). |
| `stages.<n>.required` | long | `0` | Cumulative total collected needed to reach this stage. |
| `stages.<n>.rewards` | list of strings | `[]` | Script event lines executed when this stage is reached. |

### Custom-item track note

| Track entry | Matches |
|---|---|
| `ITEM_PICKUP:<material>` | Only vanilla items of that material |
| `ITEM_PICKUP:custom:<id>` | Only Valmora custom items with that item ID |

---

## Troubleshooting

- **"No collections/ folder found" (console warning)** — the `collections` folder does not exist. Restart the server
  once so default files are copied, or create it yourself.
- **"collections/categories.yml not found" (console warning)** — without this file no categories load and the menu
  will appear empty.
- **Changes don't appear in-game** — run `/valmora reload` (requires `valmora.admin`). Note that reloads re-parse the
  YAML and re-register the listener.
- **Collection appears under the wrong category / not at all** — check the `category` value matches a key in
  `categories.yml` exactly.
- **Progress "reset" after switching profile** — counts are stored per profile. Switch back to the profile that made
  the progress.
