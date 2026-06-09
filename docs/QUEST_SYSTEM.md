# Valmora Quest System — Complete Author's Reference

This document covers every YAML key the quest system parses. It is written for server content authors and designers — no Java knowledge needed. Every single field, every allowed value, every flag, and every script action is described here with examples.

---

## Table of Contents

1. [Core Concepts](#1-core-concepts)
2. [Folder Layout & Package Detection](#2-folder-layout--package-detection)
3. [quest.yml — Package Manifest](#3-questyml--package-manifest)
4. [events: — Named Action Lists](#4-events--named-action-lists)
5. [conditions: — Named Conditions](#5-conditions--named-conditions)
6. [objectives: — Named Objectives](#6-objectives--named-objectives)
7. [Objective Types — Full Reference](#7-objective-types--full-reference)
8. [Objective Flags — Full Reference](#8-objective-flags--full-reference)
9. [quests: — Quest Definitions](#9-quests--quest-definitions)
10. [conversations: — NPC Dialogue Trees](#10-conversations--npc-dialogue-trees)
11. [notifications: — Display Categories](#11-notifications--display-categories)
12. [player_hider: — Conditional Visibility](#12-player_hider--conditional-visibility)
13. [Script Events — All Quest Actions](#13-script-events--all-quest-actions)
14. [Variables — Reading Quest Data](#14-variables--reading-quest-data)
15. [Points System](#15-points-system)
16. [Templates](#16-templates)
17. [Cross-Package References](#17-cross-package-references)
18. [In-Game Commands](#18-in-game-commands)
19. [Full Worked Example — The Forgotten Mine](#19-full-worked-example--the-forgotten-mine)
20. [Common Mistakes & Gotchas](#20-common-mistakes--gotchas)

---

## 1. Core Concepts

| Term | What it is |
|------|-----------|
| **Package** | A folder containing `quest.yml`. Every `.yml` file inside (not in sub-packages) belongs to this package. |
| **Quest** | A named task with one or more objectives. Starts, tracks, and completes per player. |
| **Objective** | A single trackable action (kill N mobs, collect N items, etc.) that belongs to a quest. |
| **Named event** | A reusable list of script actions with a short name, referenced anywhere in the package. |
| **Named condition** | A reusable condition string with a short name, required in conversation nodes. |
| **Conversation** | A branching NPC dialogue tree. Independent of quests but can trigger quest actions. |
| **Points** | Free-form per-player numeric counters (e.g. `currency`, `reputation`). |
| **Notification category** | A named display preset (`io: title`, `io: actionbar`, etc.) used by `notify` actions. |
| **Player hider** | A rule that makes one player invisible to another based on conditions. |
| **Template** | A shared package in `templates/` whose features are inherited by other packages. |

---

## 2. Folder Layout & Package Detection

### What makes a package

A folder becomes a **package** the moment it contains a file named exactly `quest.yml`.  
The engine scans all directories inside `plugins/Valmora/quests/` recursively. Every directory that has `quest.yml` is loaded as its own package.

Sub-folders that also contain `quest.yml` are **separate, independent packages** and are not included in the parent's file scan.

```
plugins/Valmora/quests/
├── forgotten_mine/          ← PACKAGE (has quest.yml)
│   ├── quest.yml            ← required marker
│   ├── quests.yml           ← any .yml files you like
│   ├── conversations.yml
│   └── notifications.yml
│
├── side_content/            ← NOT a package (no quest.yml); sub-folders scanned
│   └── blacksmith/          ← PACKAGE (has quest.yml)
│       ├── quest.yml
│       └── quests.yml
│
└── main_story/              ← PACKAGE
    ├── quest.yml
    ├── chapter1/            ← SEPARATE PACKAGE (own quest.yml; not part of main_story scan)
    │   └── quest.yml
    └── chapter2.yml         ← part of main_story (no quest.yml in chapter2 folder)
```

### File scanning

All `.yml` files inside the package directory are read, **including** those inside sub-folders that do **not** have their own `quest.yml`. You can split your content across as many files as you want with any names you choose.

Every file can contain any combination of: `events:`, `conditions:`, `objectives:`, `quests:`, `conversations:`, `notifications:`, `player_hider:`. Keys within a file type are merged into the package namespace.

### Package path

The package's internal path uses `-` to join folder names from the `quests/` root:

```
quests/forgotten_mine          →  forgotten_mine
quests/main_story/chapter1     →  main_story-chapter1
```

Used in cross-package references (see §17).

---

## 3. quest.yml — Package Manifest

`quest.yml` is the **only required file**. It must exist in a folder for the folder to be treated as a package. It can also be the only file — all other section types (`events:`, `quests:`, etc.) can be placed here too.

```yaml
# ── Package settings ──────────────────────────────────────────────
package:
  enabled: true            # optional; default true
  templates:               # optional; list of template package names to inherit
    - common_rewards
    - shared_conditions

# ── NPC → Conversation bindings ───────────────────────────────────
npc_conversations:
  thorin: thorin_main      # <npc_id>: <conversation_id>
  bjorn:  bjorn_main
```

### `package` fields

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `true` / `false` | `true` | When `false`, the entire package is **skipped** at load time. Use to disable work-in-progress content without deleting files. |
| `templates` | string list | `[]` | Names of template packages (folders under `plugins/Valmora/templates/`). Their events, conditions, objectives, quests, conversations, and notifications are merged into this package. Template values are added with **putIfAbsent** — the package's own definitions always win. |

### `npc_conversations` fields

Binds an NPC ID to a conversation ID defined anywhere in this package.

| Key | Value | Description |
|-----|-------|-------------|
| `<npc_id>` | `<conversation_id>` | When the player right-clicks the NPC with this ID, the named conversation starts. Overrides any default conversation set on the NPC definition itself. |

---

## 4. events: — Named Action Lists

Named events are reusable lists of script actions. Define them once; reference them by name in objectives, quests, and conversations.

### Defining events

```yaml
events:

  # Single action as a quoted string
  startQuest: "quest_start forgotten_mine"

  # Multiple actions as a YAML list
  reward_coins_large:
    - "point currency add 500"
    - "sound player entity.player.levelup"
    - "notify <gold>You earned 500 coins! category:quest_complete"

  # Multiple actions as a comma-separated string
  quick_reward: "point currency add 100, sound player entity.experience_orb.pickup"

  # Folder event — expands to the concatenated action lists of other named events
  # Only one level of expansion is performed (no recursive folders)
  big_reward: "folder reward_coins_large, startQuest"
```

### Parsing rules

| Value format | How it is parsed |
|---|---|
| `"single action string"` | Treated as one action, **or** multiple actions split on `,`. |
| YAML list (`- "..."`) | Each list entry is one action. |
| `"folder <name1>, <name2>"` | Expands to the concatenated action lists of the named events. One level only — the referenced events must not themselves be folder events. |

### Referencing named events

Wherever an `events:` field is accepted (objective `events:`, conversation `events:`), you can put either:

- **A named event key** — the engine looks it up and expands the full action list.
- **An inline DSL string** — if no named event matches, the value is executed directly as a script action.

```yaml
# All of these are valid:
events: "reward_coins_large"              # named event
events: "point currency add 100"          # inline DSL (no matching named event → executed directly)
events:
  - "reward_coins_large"                  # named event in a list
  - "sound player entity.player.levelup"  # inline DSL in the same list
```

> **Tip:** A typo in a named event name silently becomes an invalid DSL action. Double-check your names.

---

## 5. conditions: — Named Conditions

Named conditions are single condition strings stored under a short name. They are **required** in conversation nodes (inline DSL is rejected there with a warning). In objective `conditions:` fields, you can use either named conditions or inline DSL.

### Defining conditions

```yaml
conditions:

  in_mine:          "zone coal_mine"
  is_day:           "$time.is_day$ == true"
  mine_not_done:    "!quest forgotten_mine completed"
  mine_done:        "tag forgotten_mine.done"
  has_currency:     "$point.currency$ >= 100"
  not_in_progress:  "!quest blacksmith_hub in_progress"
```

The value is a **condition DSL string** (see the full condition syntax in §10 and §9 of VALMORA_DOCUMENTATION.md).

### Negation in references

Prefix the name with `!` when referencing it to invert the result:

```yaml
# In a conversation node:
conditions: "!mine_done"           # equivalent to NOT tag forgotten_mine.done
conditions: "mine_not_done, is_day"  # both must be true (AND)
```

### Where conditions are used

| Location | Named only? | Behaviour |
|----------|-------------|-----------|
| Objective `conditions:` | No — inline DSL accepted | All must be true for a player action to count. |
| `NPC_options` node `conditions:` | **Yes — named only** | All must be true for this node to pass in `first:` resolution. |
| `player_options` node `conditions:` | **Yes — named only** | All must be true for this reply to be shown to the player. |

---

## 6. objectives: — Named Objectives

Standalone named objectives live under the top-level `objectives:` key. They are defined with the DSL compact format and can be referenced by name or used across quests. They are also used for `auto-once` background tracking that is independent of any quest.

> Most of the time you will define objectives **inline inside a quest** (see §9). Use top-level `objectives:` when you need to share or reference an objective by name across multiple quests, or when using `auto-once`.

### DSL compact format

```
<TYPE> <target> <amount> [flags...]
```

```yaml
objectives:
  mine_coal:      "BLOCK_BREAK COAL_ORE 20 conditions:in_mine notify:5"
  kill_wolves:    "KILL WOLF 8 events:reward_coins_large notify:2"
  login_tracker:  "LOGIN login 1 auto-once persistent"
```

All flags are space-separated tokens appended after `<TYPE> <target> <amount>`.

Full flag list in DSL format:

| DSL token | Meaning |
|-----------|---------|
| `conditions:<n1>,<n2>` | Named or inline conditions, comma-separated (no spaces). All must be true. |
| `events:<n1>,<n2>` | Named events or inline DSL, comma-separated. Fired on objective completion. |
| `persistent` | After completion, reset progress to 0 and continue tracking. |
| `auto-once` | Activate automatically on first player join; never re-activated. |
| `notify` | Send a progress notification on every step. |
| `notify:<n>` | Send a progress notification every `n` steps. |

See §7 for all objective types and §8 for detailed flag descriptions.

---

## 7. Objective Types — Full Reference

Every objective has three required fields: `type`, `target`, and (optionally) `amount`.

---

### `KILL` — Kill entities

Fires when the player (as the killer) kills a living entity.

```yaml
type: KILL
target: cave_spider    # custom mob ID checked first; falls back to Bukkit EntityType name
amount: 5
```

**Target values:**
- A Valmora custom mob ID (lowercase, from your `mobs/` YAML files). These are checked first via the PDC `mob_id` key.
- A vanilla Bukkit `EntityType` name in **UPPERCASE** (e.g. `ZOMBIE`, `SKELETON`, `CREEPER`, `WOLF`, `COW`).

---

### `COLLECT` — Pick up items

Fires when the player touches and picks up an item entity from the ground. Does **not** fire for items obtained through crafting, trading, or commands.

```yaml
type: COLLECT
target: COAL           # Valmora item ID or Bukkit Material name
amount: 10
```

**Target values:**
- A Valmora custom item ID (checked first via the PDC `item_id` key).
- A Bukkit `Material` name in **UPPERCASE** (e.g. `COAL`, `IRON_ORE`, `DIAMOND`).

The amount from the item stack is added to progress — picking up a stack of 5 coal counts as 5.

---

### `REACH_ZONE` — Enter a zone

Fires when the player enters a named zone (defined in your `zones/` files).

```yaml
type: REACH_ZONE
target: coal_mine      # zone ID, case-insensitive
amount: 1
```

**Target values:** The `id` of any zone defined in `plugins/Valmora/zones/`. Case-insensitive.

---

### `TALK_TO_NPC` — Interact with an NPC

Fires when the player right-clicks an NPC.

```yaml
type: TALK_TO_NPC
target: thorin         # NPC ID, case-insensitive
amount: 1
```

**Target values:** The `id` of any NPC defined in `plugins/Valmora/npcs/`. Case-insensitive.

---

### `CRAFT` — Craft an item

Fires when the player crafts an item through any crafting mechanism.

```yaml
type: CRAFT
target: IRON_PICKAXE   # Valmora item ID or Bukkit Material name
amount: 1
```

---

### `BLOCK_BREAK` — Break a block

Fires when the player breaks a block.

```yaml
type: BLOCK_BREAK
target: COAL_ORE       # Bukkit Material name, UPPERCASE
amount: 20
```

**Common target values:** `STONE`, `COAL_ORE`, `DEEPSLATE_COAL_ORE`, `OAK_LOG`, `GRASS_BLOCK`, `SAND`.

---

### `BLOCK_PLACE` — Place a block

Fires when the player places a block.

```yaml
type: BLOCK_PLACE
target: STONE          # Bukkit Material name, UPPERCASE
amount: 10
```

---

### `FISH` — Catch something while fishing

Fires when the player successfully reels in a catch (state `CAUGHT_FISH`).

```yaml
type: FISH
target: any            # entity/item type of the catch, or "any" for any catch
amount: 3
```

**Target values:** Use `any` to match any successful catch, or a specific caught entity type name.

---

### `SHEAR` — Shear an entity

Fires when the player shears an entity with shears.

```yaml
type: SHEAR
target: SHEEP          # Bukkit EntityType name, UPPERCASE
amount: 5
```

---

### `BREED` — Breed animals

Fires when the player successfully breeds two animals.

```yaml
type: BREED
target: COW            # Bukkit EntityType name, UPPERCASE
amount: 3
```

---

### `TAME` — Tame an animal

Fires when the player tames an animal.

```yaml
type: TAME
target: WOLF           # Bukkit EntityType name, UPPERCASE
amount: 1
```

---

### `DRINK_POTION` — Consume a potion

Fires when the player consumes any item whose Bukkit material name contains the word `POTION`.

```yaml
type: DRINK_POTION
target: POTION                   # or SPLASH_POTION, LINGERING_POTION
amount: 3
```

**Target values:** `POTION`, `SPLASH_POTION`, `LINGERING_POTION`.

---

### `DIE` — Player death

Fires every time the player dies.

```yaml
type: DIE
target: die            # always the literal string "die" — the value is required but ignored
amount: 3              # die 3 times
```

---

### `LOGIN` — Log in to the server

Fires every time the player joins the server.

```yaml
type: LOGIN
target: login          # always the literal string "login" — required but ignored
amount: 5              # log in 5 total times
```

Commonly combined with `auto-once: true` and `persistent: true` for daily login tracking.

---

### `LEVEL_SKILL` — Level up a skill

Fires when the player gains a level in the named skill.

```yaml
type: LEVEL_SKILL
target: mining         # skill ID, case-insensitive
amount: 10             # reach 10 total level-ups in mining
```

**Target values:** The `id` of any skill defined in your `skills/` YAML files.

---

### `EXP_GAIN` — Accumulate skill experience

Fires each time the player earns XP in a skill. The XP amount (rounded up to the nearest integer) is added to objective progress.

```yaml
type: EXP_GAIN
target: mining         # skill ID, case-insensitive
amount: 1000           # accumulate 1000 total mining XP
```

---

### `STAT_REACH` — Reach a stat threshold

Triggers when the player's current stat value is at or above the required amount. Checked on player join and when the listener evaluates `STAT_REACH` objectives.

```yaml
type: STAT_REACH
target: HEALTH         # stat ID, case-insensitive
amount: 100            # stat must be >= 100 to complete
```

**Target values:** Any stat ID (e.g. `HEALTH`, `STRENGTH`, `DEFENSE`).

---

### `DELAY` — Wait for a duration

Completes after a fixed time elapses from when the quest starts. Fires `events:` once the delay expires. The objective ID is used internally as the trigger target — it must be non-null.

**Structured format:**

```yaml
waitDay:
  type: DELAY
  delay: 1440      # amount; unit is seconds by default
  ticks: false     # set to true to treat delay as Minecraft ticks instead of seconds
  interval: 5      # optional: if set, fires in steps of this many ticks (events fire once at the end)
  events: "resetDaily"
```

**DSL compact format:**

```yaml
# 1440 seconds (≈ 24 real minutes)
waitDay:   "delay 1440 events:resetDaily"

# 1000 ticks (50 seconds) checked every 5 ticks
wait50sec: "delay 1000 ticks interval:5 events:failQuest"
```

When `interval` is set, the scheduler fires every `interval` ticks and accumulates until `delay / interval` iterations complete (i.e., the total wall-time equals `delay`). Without `interval`, a single one-shot task is scheduled for the full `delay`.

⚠️ *DELAY tasks are in-memory and are lost if the server restarts or the plugin is reloaded before they expire. They also keep running after a player logs out and resume progress when `progressObjective` is called — if the player is offline when the timer fires, the call is silently ignored.*

---

### `ENCHANT` — Enchant an item

⚠️ *Defined in the system but the in-game listener is not yet fully implemented. The objective will be registered but may not automatically track player enchanting actions.*

```yaml
type: ENCHANT
target: SHARPNESS
amount: 1
```

---

### `SMELT` — Smelt items in a furnace

⚠️ *Player attribution is limited.* The `FurnaceSmeltEvent` does not directly expose which player placed the item. Attribution currently only works if the player who placed the item in the furnace is tracked through prior inventory interactions on that server session.

```yaml
type: SMELT
target: IRON_INGOT     # the resulting material, UPPERCASE
amount: 10
```

---

### `BREW` — Brew potions

⚠️ *Defined in the system; in-game listener attribution is pending implementation.*

```yaml
type: BREW
target: POTION
amount: 5
```

---

### `VARIABLE` — Custom variable gate

⚠️ *Defined in the system; the dedicated listener is not yet implemented. Progress cannot be automatically tracked.*

```yaml
type: VARIABLE
target: my_custom_var
amount: 1
```

---

### `JUMP` — Count jumps

⚠️ *Defined in the system; listener is not yet implemented.*

```yaml
type: JUMP
target: any
amount: 100
```

---

### `LOCATION` — Reach a coordinate

⚠️ *Defined in the system; listener is not yet implemented.*

```yaml
type: LOCATION
target: "100;64;-200;world"    # x;y;z;worldName
amount: 1
```

---

## 8. Objective Flags — Full Reference

These apply to both the **structured format** (inside a quest's `objectives:` map) and the **DSL compact format** (standalone `objectives:` map or single-line value).

### `type` *(required)*

The objective type. Must be one of the values listed in §7 (uppercase). If the value is unrecognised the objective is skipped with a warning.

---

### `target` *(required)*

What the player must interact with. The meaning depends on the `type`. See §7 for the exact accepted values per type.

---

### `amount`

**Type:** integer  
**Default:** `1`

How many times the action must occur before the objective completes. Progress is capped at this value — no over-counting.

```yaml
amount: 20     # must mine 20 coal ore blocks
```

---

### `conditions`

**Type:** string or list  
**Default:** *(none — all actions count)*

One or more condition strings (named or inline DSL). **All** conditions must be true at the moment the action occurs for progress to be counted.

```yaml
# Structured format — one named condition:
conditions: "in_mine"

# Structured format — multiple conditions as a list:
conditions:
  - "in_mine"
  - "$time.is_day$ == true"

# DSL compact format:
"KILL cave_spider 5 conditions:in_mine,is_day"
```

In the DSL compact format, conditions are comma-separated with no spaces, and can be named conditions or inline DSL strings.

---

### `events`

**Type:** string or list  
**Default:** *(none)*

Script actions to fire when the objective **completes** (progress reaches `amount`). For `persistent` objectives this fires every time the cycle ends.

```yaml
# Structured format:
events: "reward_coins_small"           # named event
events:
  - "reward_coins_small"
  - "sound player entity.player.levelup"

# DSL compact format:
"BLOCK_BREAK any 50 events:reward_coins_small,notify_player"
```

Named event references are expanded to their full action lists. Unrecognised names are treated as inline DSL.

---

### `persistent`

**Type:** boolean  
**Default:** `false`  
**DSL token:** `persistent`

When `true`, after the objective completes (progress reaches `amount`), the progress is **reset to 0** and tracking continues. The objective fires its `events` each cycle. Persistent objectives are **ignored** when the engine checks whether all objectives are done — the quest completes when all **non-persistent** objectives are finished.

Use case: repeating bonus tasks ("mine 50 blocks for a reward, then again, then again…").

```yaml
persistent: true
```

---

### `auto-once`

**Type:** boolean  
**Default:** `false`  
**DSL token:** `auto-once`

When `true`, the objective is **automatically activated** for the player the first time they join. The activation is guarded by a profile tag (`<questId>.auto-once-<objectiveId>`), so it only ever happens once per player lifetime — not once per session.

After a reload, the guard tag is checked, so players who already have the objective active are not re-activated.

To manually reset a player's `auto-once` guard: `tag remove <questId>.auto-once-<objectiveId>`.

```yaml
auto-once: true
```

---

### `notify`

**Type:** integer  
**Default:** `0` (no notifications)  
**DSL tokens:** `notify` (every step) or `notify:<n>` (every N steps)

Sends a progress notification to the player's action bar when progress milestones are hit. Uses the `info` notification category (which can be overridden in `notifications:`).

```yaml
notify: 5     # notify at progress 5, 10, 15, 20 (and always at completion)
notify: 1     # notify on every single progress step
```

The notification format is: `<target> (<current>/<required>)`.

In DSL:
```
notify         # every step
notify:5       # every 5 steps
```

---

## 9. quests: — Quest Definitions

Quests are defined under the `quests:` top-level key in any YAML file inside the package.

```yaml
quests:

  forgotten_mine:
    name: "<yellow>A Miner's Burden"

    objectives:

      meet_thorin:
        type: TALK_TO_NPC
        target: thorin
        amount: 1

      mine_coal:
        type: BLOCK_BREAK
        target: COAL_ORE
        amount: 20
        notify: 5

      kill_spiders:
        type: KILL
        target: cave_spider
        amount: 5
        conditions: "in_mine"

      reach_mine:
        type: REACH_ZONE
        target: coal_mine
        amount: 1
        events:
          - "notify <green>Quest complete! category:quest_complete"
          - "point currency add 250"
          - "tag add forgotten_mine.done"
          - "sound player entity.player.levelup"
          - "give COAL:32"
```

Reward and start-notification events live inside objective `events:` blocks — there is no separate `rewards:` or `on-start-events:` field on a quest. Put completion rewards on the last meaningful objective; put start notifications in whatever event or dialogue node triggers `quest_start`.

### Quest field reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | No (defaults to quest ID) | Display name shown in the Quest Journal and completion messages. Supports MiniMessage colour tags. |
| `objectives` | map | No | Map of `objectiveId: <objective>`. Values are either a **structured section** (§8 fields) or a **DSL compact string** (§6 format). Objective IDs must be unique within the quest. |

### Quest statuses

A quest is always in one of four states:

| Status | Meaning |
|--------|---------|
| `not_started` | Default. The player has never started this quest. |
| `in_progress` | Quest has been started. Objectives are being tracked. |
| `completed` | All non-persistent objectives are done. Rewards have been given. |
| `failed` | Quest was failed via `quest_fail`. |

### Completion logic

The quest completes automatically when every **non-persistent** objective has reached its `amount`. Persistent objectives are never checked for completion — they run indefinitely in the background.

A quest with **zero non-persistent objectives** (only persistent ones, or none at all) completes immediately on start.

### Objective IDs are your keys

Objective IDs are the keys in the `objectives:` map. Progress is stored using these IDs. **Renaming an objective ID after players have started the quest will reset their progress for that objective.** Choose stable names upfront.

---

## 10. conversations: — NPC Dialogue Trees

Conversations are branching dialogue trees displayed when a player interacts with an NPC. Define them under the `conversations:` top-level key.

```yaml
conversations:
  thorin_main:
    quester: "<gold><bold>Elder Thorin"
    stop: true
    first:
      - greeting_new
      - greeting_progress
      - greeting_done

    final_events:
      - "sound player entity.villager.no"

    NPC_options:

      greeting_new:
        text: "<yellow>The mine has gone silent. Will you help us?"
        conditions: "mine_not_started, mine_not_completed"
        events: "some_named_event"
        pointers:
          - accept_quest
          - decline_quest

    player_options:

      accept_quest:
        text: "<white>I will investigate."
        events: "start_forgotten_mine"
        pointers:
          - quest_accepted_msg
```

### Top-level conversation fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `quester` | string | conversation ID | The NPC's display name shown above each dialogue line. Supports MiniMessage tags. |
| `stop` | boolean | `false` | When `true`, right-clicking elsewhere closes the conversation. |
| `first` | string / list | `"start"` | Ordered list of **NPC node IDs** to evaluate when the conversation opens. The first node whose `conditions` all pass is displayed. |
| `final_events` | string / list | *(none)* | Script actions fired when the conversation ends for **any** reason (player closes it, runs out of nodes, etc.). Accepts named events or inline DSL. |
| `NPC_options` | map | *(none)* | NPC speech nodes. See below. |
| `player_options` | map | *(none)* | Player reply nodes. See below. |

---

### NPC option nodes

```yaml
NPC_options:
  greeting_new:
    text: "<yellow>The mine has gone silent. Will you help us?"
    conditions: "mine_not_started, mine_not_completed"
    events: "log_npc_speak"
    pointers:
      - accept_quest
      - decline_quest
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `text` | string | `""` | The NPC's spoken line. Supports MiniMessage tags. |
| `conditions` | string / list | *(none)* | **Named condition references only** (from this package's `conditions:` blocks). Comma-separated or as a list. All must pass for this node to be selected in `first:` evaluation. Inline DSL here produces a warning and is ignored. |
| `events` | string / list | *(none)* | Script actions fired when this node is shown. Accepts named events or inline DSL. |
| `pointers` | string list | *(none)* | Ordered list of node IDs this NPC node leads to. Usually these are player option IDs — they become the clickable replies. If a pointer exactly matches a player option key (without the `player.` prefix), it is resolved automatically. |

---

### Player option nodes

```yaml
player_options:
  accept_quest:
    text: "<white>I will investigate the mine."
    conditions: "mine_not_started"
    events: "start_forgotten_mine"
    pointers:
      - quest_accepted_msg
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `text` | string | `""` | The player reply text shown as a clickable button. Supports MiniMessage tags. |
| `conditions` | string / list | *(none)* | **Named condition references only.** All must pass for this reply to be visible. Inline DSL is rejected with a warning. |
| `events` | string / list | *(none)* | Script actions fired when the player clicks this reply. Accepts named events or inline DSL. |
| `pointers` | string list | *(none)* | NPC nodes to display after this reply is chosen. Usually a single pointer. If empty or all targets are unresolvable, the conversation ends. |

---

### How `first:` resolution works

On conversation open, the engine walks the `first:` list **top to bottom**. The **first NPC node whose `conditions` all pass** is displayed. If no node passes, the conversation does not open.

```yaml
first:
  - greeting_new        # passes when: mine_not_started AND mine_not_completed
  - greeting_progress   # passes when: mine_in_progress
  - greeting_done       # passes when: mine_completed
  - idle_chat           # no conditions — always passes (fallback)
```

> **Always put the most specific conditions first.** A node with no conditions will always match and must go last.

### Pointer auto-resolution

When a pointer name in `NPC_options.pointers` or the `first:` list matches a key from `player_options` (without the `player.` prefix), it is automatically prefixed:

```yaml
pointers:
  - accept_quest    # stored internally as player.accept_quest
```

You can also write `player.accept_quest` explicitly — both work.

### Conversation flow summary

```
Player right-clicks NPC
   └─ first: list evaluated top-to-bottom
      └─ First passing NPC node displayed (text shown, events fired)
         └─ Player clicks a reply (player option)
            └─ Player option events fired
               └─ Player option pointers resolve to next NPC node
                  └─ Next NPC node shown ... (repeats)
                     └─ No more pointers → conversation ends → final_events fire
```

---

## 11. notifications: — Display Categories

Notification categories define *how* messages are displayed. Reference a category in any `notify` action with `category:<name>`.

```yaml
notifications:

  quest_complete:
    io: title
    fadeIn: "10"
    stay: "60"
    fadeOut: "20"

  quest_progress:
    io: actionbar

  # Override the built-in "info" category (used by objective progress notifications)
  info:
    io: actionbar
```

### Structure

Each key under `notifications:` is the **category name** (case-insensitive). Reference it in actions:

```yaml
"notify <green>Quest complete! category:quest_complete"
"notify <yellow>5 more coal needed! category:quest_progress"
```

### Per-category fields

| Field | Type | Description |
|-------|------|-------------|
| `io` | string | Display type. See the IO type table below. |
| `fadeIn` | string (ticks) | For `title` only: ticks to fade in. Default `10`. |
| `stay` | string (ticks) | For `title` only: ticks to stay visible. Default `70`. |
| `fadeOut` | string (ticks) | For `title` only: ticks to fade out. Default `20`. |

### `io` types

| Value | Display method |
|-------|---------------|
| `actionbar` | Shown in the action bar above the hotbar. Replaces previous action bar messages. |
| `title` | Large screen title. Supports `fadeIn`, `stay`, `fadeOut`. |
| `chat` | Sent as a regular chat message. |
| `subtitle` | Subtitle line beneath the main title. |

### Built-in categories

These exist by default and can be overridden per-package by defining them in your `notifications:` block:

| Category | Default IO |
|----------|-----------|
| `info` | `actionbar` — used by the automatic objective progress notifications |

### The `notify` action syntax

```yaml
"notify <message>"                                    # uses "info" category
"notify <message> category:<categoryName>"            # uses named category
"notify <message> io:<ioType>"                        # inline IO type
"notify <message> io:title fadeIn:10 stay:60 fadeOut:20"  # inline with extra keys
```

---

## 12. player_hider: — Conditional Visibility

The player hider makes certain players invisible to other players based on conditions. It re-evaluates every 20 ticks (1 second).

```yaml
player_hider:

  hide_outside_mine:
    source_player:          # conditions on the OBSERVER (who cannot see the target)
      - "zone coal_mine"    # observer must be in the mine
    target_player:          # conditions on the TARGET (who is hidden)
      - "!zone coal_mine"   # target must NOT be in the mine

  pvp_hider:
    source_player:
      - "zone pvp_arena"
    target_player:
      - "!tag pvp_participant"
```

### Structure

```yaml
player_hider:
  <hider_id>:
    source_player:    # list of inline condition DSL strings evaluated on the OBSERVER
      - "<condition>"
    target_player:    # list of inline condition DSL strings evaluated on the TARGET
      - "<condition>"
```

| Field | Type | Description |
|-------|------|-------------|
| `<hider_id>` | string | Unique ID for this rule (any name). |
| `source_player` | string list | Inline condition DSL strings. Evaluated on the **observer** (the player who will not see the target). Empty list matches all players. |
| `target_player` | string list | Inline condition DSL strings. Evaluated on the **target** (the player being hidden). Empty list matches all players. |

When **all** source conditions pass for player A **and** all target conditions pass for player B, player B is hidden from player A's view.

> Unlike conversation conditions, player hider conditions accept **inline DSL directly** — named conditions are not required.

---

## 13. Script Events — All Quest Actions

These are the action strings you write in objective `events:`, conversation `events:`, and named `events:` blocks.

---

### Quest lifecycle

```yaml
"quest_start <questId>"
```
Start a quest for the player. If already `in_progress` or `completed`, this is silently ignored. Resets all objective progress to 0 and schedules any `DELAY` objectives.

```yaml
"quest_complete <questId>"
```
Force-complete a quest. Marks status `completed` regardless of current objective progress.

```yaml
"quest_cancel <questId>"
```
Reset a quest back to `not_started`. Clears all objective flags and progress.

```yaml
"quest_fail <questId>"
```
Mark a quest as `failed`. Clears objective flags. Sends a failure message to the player.

---

### Standalone objective lifecycle

```yaml
"objective_start <objectiveId>"
```
Activate a named objective independently of any quest. Sets its progress to 0 and marks it active.

```yaml
"objective_delete <objectiveId>"
```
Deactivate and remove a named objective. Clears its progress and active flag.

---

### Quest Journal

```yaml
"journal open"
```
Open the Quest Journal inventory for the player. Shows all known quests with their status and progress bars.

---

### Points

```yaml
"point <category> add <amount>"    # increase by amount (can't go below 0)
"point <category> take <amount>"   # decrease by amount (floors at 0)
"point <category> set <amount>"    # set to exact value
```

`category` is any string you choose (e.g. `currency`, `reputation`, `kills`). It is case-insensitive and stored lowercase.

---

### Notifications

```yaml
"notify <message> category:<categoryName>"
"notify <message> io:<ioType>"
"notify <message>"                              # uses the "info" category
```

MiniMessage tags are supported in `<message>`:

```yaml
"notify <green>Quest complete! <bold>Well done! category:quest_complete"
```

---

### Tags (from the base script system)

```yaml
"tag add <tagName>"
"tag remove <tagName>"
```

Tags are profile-level boolean flags stored as strings. They persist across sessions.

---

### Items (from the base script system)

```yaml
"give <MATERIAL>:<amount>"
"give <customItemId>:<amount>"
```

---

### Sounds (from the base script system)

```yaml
"sound player <sound.key>"       # plays to the caster only
"sound world <sound.key>"        # plays at the caster's location for nearby players
```

Examples of sound keys: `entity.player.levelup`, `entity.villager.yes`, `entity.villager.no`, `block.brewing_stand.brew`.

---

### Variables (from the base script system)

```yaml
"variable set <path> <value>"
"variable add <path> <number>"
"variable remove <path>"
```

---

### Event suffixes

Any action can have `delay:<ticks>` appended to defer execution:

```yaml
"give DIAMOND:1 delay:100"       # fires after 100 ticks (5 seconds)
```

---

## 14. Variables — Reading Quest Data

Variables are embedded in any string using `$namespace.path$` syntax. They resolve to live data at execution time.

### Quest status

```
$quest.<questId>.status$
```

Returns: `not_started`, `in_progress`, `completed`, or `failed`.

```yaml
conditions:
  quest_done:     "$quest forgotten_mine.status$ == completed"
  quest_active:   "$quest forgotten_mine.status$ == in_progress"
```

### Objective progress

```
$quest.<questId>.objective.<objectiveId>.progress$
```

Returns: current progress integer (0 to `amount`).

```
$quest.<questId>.objective.<objectiveId>.required$
```

Returns: the `amount` field value — how many are required.

```yaml
# Example: check if at least half done
conditions:
  halfway: "$quest forgotten_mine.objective.mine_coal.progress$ >= 10"
```

### Legacy index-based progress

```
$quest.<questId>.progress.<index>$
```

Returns the progress for the objective at zero-based position `index` in the definition order. Prefer named objective IDs.

### Objective active flag

```
$quest.objective.<objectiveId>.active$
```

Returns: `true` or `false`. Whether the named standalone objective is currently active for the player.

### Points

```
$point.<category>$
```

Returns: integer — the player's current point total in that category.

```yaml
conditions:
  can_afford:   "$point.currency$ >= 500"
  wealthy:      "$point.currency$ >= 10000"
```

### Player variables (from base system)

| Variable | Returns |
|----------|---------|
| `$player.name$` | Player display name |
| `$player.stat.<STAT_ID>$` | Stat value (double) |
| `$player.skill.<skillId>.level$` | Skill level (int) |
| `$player.var.<path>$` | Custom profile variable |

### Time variables (from base system)

| Variable | Returns |
|----------|---------|
| `$time.is_day$` | `true` / `false` |
| `$time.hour$` | 0–23 (int) |
| `$time.season$` | Season name |

---

## 15. Points System

Points are per-player numeric counters. Each counter is identified by a **category** name you choose freely — there are no predefined categories. Values are stored in the player's profile and persist across sessions.

### Actions

```yaml
"point currency add 250"      # += 250 (floors at 0)
"point currency take 100"     # -= 100 (floors at 0 — never negative)
"point currency set 0"        # set to exactly 0
"point reputation add 10"
```

### Conditions (using the base condition system)

```yaml
conditions:
  can_afford:   "$point.currency$ >= 500"
  respected:    "$point.reputation$ > 50"
```

Or using the shorthand condition (if registered):

```yaml
- "point currency 500"        # player has at least 500 currency
```

### Variables

```yaml
"$point.currency$"            # resolves to the integer count
"$point.reputation$"
```

### Example — shop purchase gate

```yaml
conditions:
  can_buy_sword: "$point.currency$ >= 200"

# In a conversation player option:
accept_purchase:
  text: "Buy the iron sword for 200 coins."
  conditions: "can_buy_sword"
  events:
    - "point currency take 200"
    - "give IRON_SWORD:1"
    - "notify <green>Purchased! category:quest_complete"
```

---

## 16. Templates

Templates let you define shared events, conditions, objectives, and quests in one place and inherit them in multiple packages.

### Template folder location

```
plugins/Valmora/
└── templates/
    ├── common_rewards/          ← each sub-folder is one template
    │   ├── events.yml           ← can contain events:, conditions:, quests:, etc.
    │   └── conditions.yml
    └── shared_conditions/
        └── conditions.yml
```

Template folders follow the exact same YAML format as regular packages. They do **not** need a `quest.yml` to be detected — every direct sub-folder of `templates/` is loaded automatically.

### Using a template

In your package's `quest.yml`:

```yaml
package:
  templates:
    - common_rewards          # matches the folder name (case-insensitive)
    - shared_conditions
```

### Merge rules

| Scenario | Result |
|----------|--------|
| Package defines `events.reward_full` | Package version used |
| Template defines `events.reward_full`, package does not | Template version used |
| Both define `events.reward_full` | **Package wins** (`putIfAbsent`) |

Templates are defaults, not overrides. Multiple templates are merged in the order listed; the first one to define a key wins among templates.

---

## 17. Cross-Package References

You can reference events from another package using the `>` separator.

### Event reference from another package

```yaml
# Inside an objective's events:
events:
  - "forgotten_mine>reward_coins_large"      # absolute package path
  - "main_story-chapter1>start_chapter_two"  # nested package path
```

### Relative paths

Use `_` to go up one level and `-<name>` to go into a child:

```yaml
"_>sibling_event"          # sibling package (same parent folder)
"-child>child_event"       # child package
"_-uncle>uncle_event"      # go up one, go into uncle
```

The source package's path is used as the base for relative resolution.

---

## 18. In-Game Commands

| Command | Who can use | Description |
|---------|-------------|-------------|
| `/quest` | All players | Opens the Quest Journal. |
| `/quest journal` | All players | Same as `/quest`. |
| `/valmora reload` | `valmora.admin` | Hot-reloads all modules. Quest packages are re-scanned and re-loaded. Player progress in profiles is preserved. |

---

## 19. Full Worked Example — The Forgotten Mine

This is a complete, self-contained two-quest package demonstrating every major feature.

### Folder layout

```
plugins/Valmora/quests/
└── forgotten_mine/
    ├── quest.yml           ← package manifest + NPC bindings
    ├── quests.yml          ← events, conditions, quest definitions
    ├── conversations.yml   ← NPC dialogue trees
    └── notifications.yml   ← display category overrides
```

---

### `quest.yml`

```yaml
package:
  enabled: true

npc_conversations:
  thorin: thorin_main    # Elder Thorin NPC → thorin_main conversation
  bjorn:  bjorn_main     # Bjorn the Blacksmith → bjorn_main conversation
```

---

### `notifications.yml`

```yaml
notifications:

  quest_complete:
    io: title
    fadeIn: "10"
    stay: "80"
    fadeOut: "20"

  quest_progress:
    io: actionbar

  info:
    io: actionbar
```

---

### `quests.yml`

```yaml
# ── Named events ──────────────────────────────────────────────────────────

events:
  start_forgotten_mine:     "quest_start forgotten_mine"
  start_blacksmith_request: "quest_start blacksmith_request"

  reward_coins_small:
    - "point currency add 100"
    - "sound player entity.experience_orb.pickup"

  reward_coins_large:
    - "point currency add 500"
    - "sound player entity.player.levelup"
    - "notify <gold>You earned 500 coins! category:quest_complete"

  bonus_mining_reward: "folder reward_coins_small"   # expands reward_coins_small


# ── Named conditions ──────────────────────────────────────────────────────

conditions:
  in_mine:             "zone coal_mine"
  is_day:              "$time.is_day$ == true"

  mine_not_started:    "!quest forgotten_mine in_progress"
  mine_not_completed:  "!quest forgotten_mine completed"
  mine_in_progress:    "quest forgotten_mine in_progress"
  mine_completed:      "quest forgotten_mine completed"

  smith_not_started:   "!quest blacksmith_request in_progress"
  smith_not_completed: "!quest blacksmith_request completed"
  smith_in_progress:   "quest blacksmith_request in_progress"
  smith_completed:     "quest blacksmith_request completed"


# ── Quest definitions ─────────────────────────────────────────────────────

quests:

  # ── Quest 1: A Miner's Burden ──────────────────────────────────────────
  forgotten_mine:
    name: "<yellow>A Miner's Burden"

    objectives:

      meet_thorin:
        type: TALK_TO_NPC
        target: thorin
        amount: 1

      mine_coal:
        type: BLOCK_BREAK
        target: COAL_ORE
        amount: 20
        notify: 5                    # notify at 5, 10, 15, 20

      kill_spiders:
        type: KILL
        target: cave_spider          # custom mob ID
        amount: 5
        conditions: "in_mine"        # only counts kills inside coal_mine zone

      reach_mine:
        type: REACH_ZONE
        target: coal_mine
        amount: 1
        events:
          - "notify <green>You have completed A Miner's Burden! category:quest_complete"
          - "point currency add 250"
          - "tag add forgotten_mine.done"
          - "sound player entity.player.levelup"
          - "give COAL:32"

  # ── Quest 2: The Blacksmith's Request ─────────────────────────────────
  blacksmith_request:
    name: "<gold>The Blacksmith's Request"

    objectives:

      meet_bjorn:
        type: TALK_TO_NPC
        target: bjorn
        amount: 1

      craft_pickaxe:
        type: CRAFT
        target: IRON_PICKAXE
        amount: 1

      smelt_iron:
        type: SMELT
        target: IRON_INGOT
        amount: 10
        notify: 2

      catch_fish:
        type: FISH
        target: any
        amount: 3
        conditions: "is_day"         # only during daytime
        events:
          - "notify <gold>You have completed The Blacksmith's Request! category:quest_complete"
          - "point currency add 500"
          - "tag add blacksmith_request.done"
          - "sound player entity.player.levelup"
          - "give IRON_PICKAXE:1"

  # ── Quest 3: Daily Mining Bonus (persistent + auto-once) ──────────────
  daily_mining:
    name: "<gray>Daily Mining Bonus"

    objectives:
      bonus_mining:
        type: BLOCK_BREAK
        target: any                  # any block material
        amount: 50
        persistent: true             # resets after each 50 blocks — never ends
        auto-once: true              # activates automatically on first join
        notify: 10                   # notify every 10 blocks
        events: "bonus_mining_reward"
```

---

### `conversations.yml`

```yaml
conditions:
  mine_not_started_c:    "!quest forgotten_mine in_progress"
  mine_not_completed_c:  "!quest forgotten_mine completed"
  mine_in_progress_c:    "quest forgotten_mine in_progress"
  mine_completed_c:      "quest forgotten_mine completed"
  smith_not_started_c:   "!quest blacksmith_request in_progress"
  smith_not_completed_c: "!quest blacksmith_request completed"
  smith_in_progress_c:   "quest blacksmith_request in_progress"
  smith_completed_c:     "quest blacksmith_request completed"
  mine_done_c:           "tag forgotten_mine.done"

conversations:

  # ── Elder Thorin ───────────────────────────────────────────────────────
  thorin_main:
    quester: "<gold><bold>Elder Thorin"
    stop: true
    first:
      - greeting_new        # shown when quest not yet started
      - greeting_progress   # shown when quest in progress
      - greeting_done       # shown when quest complete

    final_events:
      - "sound player entity.villager.no"

    NPC_options:

      greeting_new:
        text: "<yellow>The mine has gone silent. Our coal supply dwindles and something lurks in the tunnels. Will you help us?"
        conditions: "mine_not_started_c, mine_not_completed_c"
        pointers:
          - accept_quest
          - decline_quest

      greeting_progress:
        text: "<yellow>How goes your work in the mine? We are counting on you."
        conditions: "mine_in_progress_c"
        pointers:
          - report_progress
          - say_goodbye

      greeting_done:
        text: "<green>You have done the village a great service. If you seek more work, speak to Bjorn the blacksmith."
        conditions: "mine_completed_c"
        pointers:
          - say_goodbye

      quest_accepted:
        text: "<yellow>Excellent! Head east past the old oak — the mine entrance is marked by a lantern."
        pointers:
          - say_goodbye

      quest_declined:
        text: "<gray>I understand. Return when you are ready."
        pointers:
          - say_goodbye

    player_options:

      accept_quest:
        text: "<white>I will investigate the mine and deal with whatever lurks there."
        events: "start_forgotten_mine"
        pointers:
          - quest_accepted

      decline_quest:
        text: "<white>I am not ready for that yet."
        pointers:
          - quest_declined

      report_progress:
        text: "<white>I am working on it."
        pointers:
          - greeting_progress

      say_goodbye:
        text: "<white>Farewell, Elder."


  # ── Bjorn the Blacksmith ───────────────────────────────────────────────
  bjorn_main:
    quester: "<red><bold>Bjorn"
    stop: true
    first:
      - bjorn_offer_quest    # only after mine quest done; not yet offered smith quest
      - bjorn_in_progress    # smith quest in progress
      - bjorn_done           # smith quest complete
      - bjorn_idle           # fallback if mine quest not done

    final_events:
      - "sound player entity.villager.no"

    NPC_options:

      bjorn_offer_quest:
        text: "<red>The Elder said you cleared out the mine. Good. I need coal ore, smelted iron, a pickaxe, and some fish. Interested?"
        conditions: "mine_done_c, smith_not_started_c, smith_not_completed_c"
        pointers:
          - accept_blacksmith
          - decline_blacksmith

      bjorn_in_progress:
        text: "<red>Still gathering my order? I need the ore, iron ingots, the pickaxe. Oh — and fish. Wife's recipe. Don't ask."
        conditions: "smith_in_progress_c"
        pointers:
          - say_goodbye_bjorn

      bjorn_done:
        text: "<red>My forge burns bright thanks to you. Come back anytime."
        conditions: "smith_completed_c"
        pointers:
          - say_goodbye_bjorn

      bjorn_idle:
        text: "<red>I am busy. Come back after you've proven yourself out there."
        pointers:
          - say_goodbye_bjorn

      bjorn_quest_accepted:
        text: "<red>Don't take all day. Catch the fish during daylight — they taste better."
        pointers:
          - say_goodbye_bjorn

      bjorn_quest_declined:
        text: "<red>Suit yourself."
        pointers:
          - say_goodbye_bjorn

    player_options:

      accept_blacksmith:
        text: "<white>Consider it done, Bjorn."
        events: "start_blacksmith_request"
        pointers:
          - bjorn_quest_accepted

      decline_blacksmith:
        text: "<white>Not right now."
        pointers:
          - bjorn_quest_declined

      say_goodbye_bjorn:
        text: "<white>See you around."
```

---

### Quest flow diagram

```
Player joins server
  └─ daily_mining auto-once activates
       └─ Every 50 blocks mined: +100 currency (repeats indefinitely)

Player right-clicks Elder Thorin
  └─ first: evaluated → greeting_new passes (not started, not completed)
       └─ NPC: "The mine has gone silent..."
            ├─ [Accept] → quest_start forgotten_mine (start events in dialogue node)
            │     └─ Objectives tracking:
            │          ✓ TALK_TO_NPC thorin           (1/1)
            │          ✓ BLOCK_BREAK COAL_ORE × 20   (notify every 5)
            │          ✓ KILL cave_spider × 5          (only inside coal_mine zone)
            │          ✓ REACH_ZONE coal_mine           (1/1) → fires events: 250 currency, 32 coal, tag
            └─ [Decline] → quest_declined node

Player right-clicks Bjorn
  └─ greeting_new passes only if mine_done tag set → offers quest
       └─ [Accept] → quest_start blacksmith_request
              ✓ TALK_TO_NPC bjorn
              ✓ CRAFT IRON_PICKAXE × 1
              ✓ SMELT IRON_INGOT × 10   (notify every 2)
              ✓ FISH any × 3            (only during daytime) → fires events: 500 currency, iron pickaxe, tag
```

---

## 20. Common Mistakes & Gotchas

### Conversation conditions must be named

```yaml
# ✗ Wrong — inline DSL in a conversation condition (produces a warning and is ignored)
NPC_options:
  greeting:
    conditions: "$quest wolf_hunt.status$ == not_started"

# ✓ Correct — define a named condition, then reference it
conditions:
  wolf_not_started: "$quest wolf_hunt.status$ == not_started"

NPC_options:
  greeting:
    conditions: "wolf_not_started"
```

---

### `first:` order always matters

The first node whose conditions pass is shown. Put the most specific cases first:

```yaml
# ✗ Wrong — idle has no conditions, so it always matches first
first:
  - idle_chat        # no conditions — always matches → completed and progress nodes never show
  - quest_complete
  - quest_progress

# ✓ Correct — specific to general
first:
  - quest_complete   # most specific
  - quest_progress
  - idle_chat        # fallback with no conditions last
```

---

### Objective IDs are permanent keys

Once a player has started a quest, objective progress is stored using the objective ID as the storage key. Renaming an objective ID resets progress for that objective for all players who have started the quest.

---

### Persistent-only quests complete immediately

A quest whose only objectives all have `persistent: true` has **zero non-persistent objectives**. The completion check passes immediately when the quest starts. If you want the quest to actually complete at some point, include at least one non-persistent objective.

---

### `final_events` not `final_actions`

The parser looks for **`final_events`** in conversations. The field name `final_actions` is not recognised and will silently do nothing.

---

### Objective on-complete field is `events:` only

Objectives use `events:` for their on-complete script actions. Any key named `actions:` is not recognised. Always use `events:`.

---

### Material names are case-sensitive and must be uppercase

```yaml
target: COAL_ORE     # ✓ correct
target: coal_ore     # ✗ wrong — will not match
target: Coal_Ore     # ✗ wrong — will not match
```

Custom mob IDs and NPC IDs are case-insensitive.

---

### `auto-once` is per-player, permanent

Once activated (guarded by a profile tag), an `auto-once` objective never re-activates for that player — even across reloads and restarts. To reset it for a player: remove the tag `<questId>.auto-once-<objectiveId>` using the `tag remove` action.

---

### Named events are expanded before DSL execution

If you write `events: "my_event"` and `my_event` is not defined in your package, the string `"my_event"` is sent to the script executor as a raw DSL action. A typo in a named event name produces no warning — it just fails silently as an invalid action. Always verify event names match exactly.

---

### The `smelt` objective has player attribution limits

The `FurnaceSmeltEvent` does not expose which player placed the item. Only the player who placed the raw material into the furnace during the current server session will receive credit. Items smelted from a furnace loaded by a different player or before the player joined will not count.

---

### Notify fires at completion regardless of interval

Even if `notify: 5` is set, a notification is always sent when progress reaches `amount` (completion). The notify interval controls intermediate updates only.

---

*For the scripting DSL, variable system, and condition syntax in full depth, see `docs/VALMORA_DOCUMENTATION.md`. For the NPC module's YAML format (entity spawning, on-click actions), see `docs/USER_DOCS.md`.*
