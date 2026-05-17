# Valmora Quest System — Author's Guide

This document covers everything you need to create quests, conversations, notification categories, and player-hider rules using Valmora's quest system. It is written for server content authors, not developers.

---

## Table of Contents

1. [Package Overview](#1-package-overview)
2. [quest.yml — Package Header](#2-questyml--package-header)
3. [Quest Definitions](#3-quest-definitions)
4. [Objective Types](#4-objective-types)
5. [Objective Suffixes](#5-objective-suffixes)
6. [Conditions Reference](#6-conditions-reference)
7. [Events (Actions) Reference](#7-events-actions-reference)
8. [Variables Reference](#8-variables-reference)
9. [NPC Conversations](#9-npc-conversations)
10. [NPCs](#10-npcs)
11. [Notification Categories](#11-notification-categories)
12. [Points System](#12-points-system)
13. [Player Hider](#13-player-hider)
14. [Templates](#14-templates)
15. [Cross-Package References](#15-cross-package-references)
16. [Commands](#16-commands)
17. [Full Example — The Forgotten Mine](#17-full-example--the-forgotten-mine)

---

## 1. Package Overview

All quest content lives inside **packages** — folders inside `plugins/Valmora/quests/`. A folder becomes a package the moment it contains a `quest.yml` file. You can have as many YAML files inside a package as you want; the engine reads all of them.

```
plugins/Valmora/
  quests/
    forgotten_mine/           ← one package
      quest.yml               ← required — marks the folder as a package
      quests.yml              ← quest definitions (any file name works)
      conversations.yml       ← NPC conversations
      notifications.yml       ← custom notification presets
      items/                  ← sub-folder — still part of this package
        rare_drops.yml
    daily_tasks/              ← a second, independent package
      quest.yml
      tasks.yml
```

**Sub-packages:** If a sub-folder also has its own `quest.yml`, it is treated as a completely separate package, not a child of the parent.

Everything defined in a package (quests, conditions, events, conversations) shares the same namespace — IDs must be unique within the package. Two different packages can use the same IDs without conflict.

---

## 2. quest.yml — Package Header

The `quest.yml` file at the root of a package folder configures the package itself.

```yaml
package:
  enabled: true          # set false to skip loading this package on startup
  templates:             # optional — list of template names to inherit from
    - rewardTemplate
    - mobDropTemplate
```

The `templates:` list references folders inside `plugins/Valmora/templates/`. Template features are merged into the package with package features taking priority (templates are defaults, not overrides).

---

## 3. Quest Definitions

Quests are defined inside any YAML file in the package under a `quests:` key.

```yaml
quests:

  my_quest_id:
    name: "<yellow>My Quest"

    objectives:
      obj_id_1:
        type: KILL
        target: zombie
        amount: 10
        notify: 2

      obj_id_2:
        type: COLLECT
        target: ROTTEN_FLESH
        amount: 5
        conditions:
          - "zone graveyard"
        actions:
          - "sound player entity.experience_orb.pickup"

    rewards:
      - "point currency add 100"
      - "give DIAMOND:1"
      - "sound player entity.player.levelup"
      - "tag add my_quest.done"

    on-start-actions:
      - "notify <yellow>Quest started! Head to the graveyard. category:quest_progress"
      - "sound player entity.villager.yes"
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | yes | Display name shown in the journal (MiniMessage) |
| `objectives` | yes | Map of objective ID → objective definition |
| `rewards` | no | Action list run when all objectives are complete |
| `on-start-actions` | no | Action list run the moment the quest starts |

---

## 4. Objective Types

Each objective needs a `type`, a `target`, and an `amount`.

| Type | Target | Fires when… |
|------|--------|-------------|
| `KILL` | Entity type ID (e.g. `zombie`, `cave_spider`) | Player kills that entity |
| `COLLECT` | Material name (e.g. `IRON_ORE`) | Player picks up that item |
| `BLOCK_BREAK` | Material name or `any` | Player breaks a block of that type |
| `BLOCK_PLACE` | Material name or `any` | Player places a block of that type |
| `REACH_ZONE` | Zone ID | Player enters a defined zone |
| `TALK_TO_NPC` | NPC ID | Player right-clicks that NPC |
| `CRAFT` | Material name (e.g. `IRON_PICKAXE`) | Player crafts that item |
| `SMELT` | Material name (e.g. `IRON_INGOT`) | A furnace produces that item |
| `BREW` | Potion type name | A brewing stand produces that potion |
| `FISH` | Fish type or `any` | Player catches a fish |
| `ENCHANT` | Material name or `any` | Player enchants an item |
| `TAME` | Entity type ID | Player tames that entity |
| `BREED` | Entity type ID | Player breeds that entity |
| `SHEAR` | Entity type ID (usually `sheep`) | Player shears that entity |
| `DIE` | `any` | Player dies (target is ignored) |
| `JUMP` | `any` | Player jumps (counts each jump) |
| `LOCATION` | `x;y;z;world` | Player walks within 3 blocks of that point |
| `VARIABLE` | Variable path | Player's variable equals a specific value |

---

## 5. Objective Suffixes

These fields are added alongside `type`, `target`, and `amount`:

### `notify` / `notify: N`

Sends a progress message to the player. With a number, it fires every `N` units of progress (e.g. every 5 kills). Without a number it fires on every point of progress.

```yaml
mine_coal:
  type: BLOCK_BREAK
  target: COAL_ORE
  amount: 20
  notify: 5          # message sent at 5, 10, 15, 20
```

### `conditions`

A list of condition strings (see [§6](#6-conditions-reference)). Progress on this objective is only counted when ALL conditions pass.

```yaml
kill_spiders:
  type: KILL
  target: cave_spider
  amount: 5
  conditions:
    - "zone coal_mine"              # must be inside this zone
    - "health 10"                   # must have at least 10 HP
```

### `actions`

A list of script events (see [§7](#7-events-actions-reference)) that fire each time the objective completes. For `persistent` objectives this fires every cycle.

```yaml
bonus_ore:
  type: BLOCK_BREAK
  target: any
  amount: 50
  persistent: true
  actions:
    - "point currency add 25"
    - "notify <gray>+25 coins for mining! category:info"
```

### `persistent`

When `true`, the objective **restarts** after completion instead of locking. The quest never completes from this objective alone. Use it for repeating bonus tasks.

```yaml
persistent: true
```

### `auto-once`

When `true`, this objective is automatically activated for every player exactly once (tracked by a tag). They do not need to start a quest to get it — it activates on join and after reloads.

```yaml
auto-once: true
```

---

## 6. Conditions Reference

Conditions appear in:
- Objective `conditions:` lists
- Conversation option `conditions:` lists
- Event instruction `conditions:` suffix (inline)

All conditions in a list are combined with AND. Prefix any condition with `!` to negate it.

### `tag <name>`
Player has the named profile tag.
```yaml
- "tag forgotten_mine.done"
- "!tag pvp.disabled"    # negated — player does NOT have this tag
```

### `quest <questId> <status>`
Quest is in the given status. Valid statuses: `not_started`, `in_progress`, `completed`, `failed`.
```yaml
- "quest forgotten_mine completed"
- "!quest blacksmith_request in_progress"
```

### `objective <objectiveId>`
The named objective is currently active for the player.
```yaml
- "objective mine_coal"
```

### `zone <zoneId>`
Player is currently inside the named zone.
```yaml
- "zone coal_mine"
```

### `health <amount>`
Player's current health is at least `amount`.
```yaml
- "health 15"    # at least 15 HP (half-hearts * 2)
```

### `hunger <level>`
Player's food level is at least `level` (0–20).
```yaml
- "hunger 10"
```

### `point <category> <amount>`
Player has at least `amount` points in the named category.
```yaml
- "point currency 100"
```

### `location <x;y;z;world> <radius>`
Player is within `radius` blocks of the given location.
```yaml
- "location 100;64;-200;world 5"
```

### `variable <path> <operator> <value>`
A resolved variable matches the condition. Operators: `==`, `!=`, `>`, `<`, `>=`, `<=`.
```yaml
- "variable $player.skill.mining.level$ >= 10"
- "variable $quest.forgotten_mine.status$ == completed"
```

### Expression conditions
Any expression not matching a keyword above is treated as a boolean expression. See the Script DSL reference for full expression syntax.
```yaml
- "$time.is_day$ == true"
- "$player.stat.HEALTH$ > 20"
```

---

## 7. Events (Actions) Reference

These strings go in `rewards`, `on-start-actions`, objective `actions`, NPC option `actions`, or player option `actions`.

### Quest events
```yaml
- "quest_start <questId>"          # start a quest for the player
- "quest_complete <questId>"       # force-complete a quest (runs rewards)
- "quest_cancel <questId>"         # cancel — resets to not_started
- "quest_fail <questId>"           # mark as failed
- "objective_start <objectiveId>"  # activate a standalone objective
- "objective_delete <objectiveId>" # deactivate and remove an objective
```

### Notification events
```yaml
- "notify <message>"
- "notify <message> category:<categoryName>"
- "notify <message> io:<ioType> [key:value ...]"
- "notifyall <message>"            # sends to ALL online players
```

IO types: `chat`, `actionbar`, `title`, `subtitle`, `bossbar`, `sound`, `advancement`

### Point events
```yaml
- "point <category> add <amount>"
- "point <category> set <amount>"
- "point <category> take <amount>"   # floors at 0
```

### Tag events
```yaml
- "tag add <tagName>"
- "tag remove <tagName>"
```

### Item events
```yaml
- "give <MATERIAL>:<amount>"
- "give <customItemId>:<amount>"
```

### Sound events
```yaml
- "sound player <sound.key>"         # plays to the caster only
- "sound world <sound.key>"          # plays at the caster's location
```

### GUI events
```yaml
- "gui open <guiId>"
```

### Dialogue events
```yaml
- "dialogue start <conversationId>"
```

### Variable events
```yaml
- "variable set <path> <value>"
- "variable add <path> <number>"
- "variable remove <path>"
```

### Journal events
```yaml
- "journal open"                     # opens the quest journal for the player
```

### Event suffixes
Any event can have these appended:
```yaml
- "give DIAMOND:1 delay:100"                          # runs after 100 ticks (5 s)
- "give DIAMOND:1 conditions:tag vip,point currency 100"  # only if conditions pass (comma-separated)
```

---

## 8. Variables Reference

Variables are resolved inside any action string or condition expression using the `$namespace.path$` syntax.

### Quest variables
| Variable | Returns |
|----------|---------|
| `$quest.<id>.status$` | `not_started`, `in_progress`, `completed`, or `failed` |
| `$quest.<id>.objective.<objId>.progress$` | Current progress int |
| `$quest.<id>.objective.<objId>.required$` | Required count int |
| `$objective.<objId>.active$` | `true` / `false` |

### Point variables
| Variable | Returns |
|----------|---------|
| `$point.<category>$` | Current point total (int) |

### Player variables
| Variable | Returns |
|----------|---------|
| `$player.name$` | Player display name |
| `$player.stat.<STAT_ID>$` | Stat value (double) |
| `$player.skill.<skillId>.level$` | Skill level (int) |
| `$player.var.<path>$` | Profile variable |

### Time variables
| Variable | Returns |
|----------|---------|
| `$time.is_day$` | `true` / `false` |
| `$time.hour$` | 0–23 (int) |
| `$time.season$` | Season name (String) |

---

## 9. NPC Conversations

Conversations are defined in any YAML file within a quest package under a `conversations:` key.

```yaml
conversations:

  my_conversation:
    quester: "<gold>NPC Name"    # display name shown above NPC speech
    stop: false                  # true = freeze player movement during chat
    final_actions:               # run when conversation ends for any reason
      - "sound player entity.villager.no"

    # firstOptions: list of NPC option IDs tried in order.
    # The first one whose conditions pass is shown.
    first:
      - option_a
      - option_b
      - option_fallback

    NPC_options:

      option_a:
        text: "<yellow>Hello, new adventurer!"
        conditions:
          - "!tag met_npc"    # only shown the first time
        actions:
          - "tag add met_npc"
        pointers:
          - player.choice_1
          - player.choice_2

      option_b:
        text: "<yellow>Welcome back."
        conditions:
          - "tag met_npc"
        pointers:
          - player.choice_2

      option_fallback:
        text: "<yellow>..."    # shown if no other option qualifies
        pointers:
          - player.goodbye

      npc_response_yes:
        text: "<yellow>Wonderful! Here is your reward."
        actions:
          - "give DIAMOND:1"
        pointers:
          - player.goodbye

      npc_response_no:
        text: "<yellow>Very well."
        pointers:
          - player.goodbye

    player_options:

      choice_1:
        text: "<white>I will help you."
        actions:
          - "quest_start my_quest"
        conditions: []        # shown to everyone (no condition = always visible)
        pointers:
          - npc_response_yes

      choice_2:
        text: "<white>Not right now."
        pointers:
          - npc_response_no

      goodbye:
        text: "<white>Farewell."
        # no pointers = ends the conversation
```

### Conversation flow rules

1. When a player right-clicks the NPC, the engine iterates `first:` and picks the first NPC option whose `conditions:` all pass.
2. It displays the NPC option's `text` and `actions` fire immediately.
3. The `pointers:` list on an NPC option names **player options** to show as clickable choices.
4. When the player picks a player option, its `actions` fire and the engine follows the option's `pointers:` to the next NPC option.
5. If a player option has no `pointers:`, or all pointers point to nothing, the conversation ends.
6. `final_actions` always fire when the conversation ends.

### Cross-conversation pointers

A pointer can jump to a conversation in another package:
```yaml
pointers:
  - otherConversation.npcOptionId        # same package, different conversation
  - otherPackage>otherConv.npcOptionId   # cross-package reference
```

---

## 10. NPCs

NPCs are defined in `plugins/Valmora/npcs/` (not inside quest packages — they need world coordinates).

```yaml
thorin:
  display-name: "<gold>Elder Thorin"
  entity-type: VILLAGER          # any Bukkit EntityType name
  world: world
  x: -8.5
  y: 65.0
  z: 8.5
  yaw: 90                        # facing direction (degrees, 0 = south)
  on-right-click: []             # action list (overridden by conversation binding)
  on-left-click:
    - "sound player entity.villager.ambient"

# Bind conversations loaded from quest packages.
# Conversation takes priority over on-right-click actions.
npc_conversations:
  thorin: thorin_main
  bjorn: bjorn_main
```

Alternatively, set `conversation:` directly on the NPC:
```yaml
thorin:
  display-name: "<gold>Elder Thorin"
  entity-type: VILLAGER
  world: world
  x: -8.5
  y: 65.0
  z: 8.5
  yaw: 90
  conversation: thorin_main     # inline binding
  on-right-click: []
  on-left-click: []
```

---

## 11. Notification Categories

Categories define how notifications are delivered. Define them per-package.

```yaml
notifications:

  # Override the built-in "info" category (defaults to chat)
  info:
    io: actionbar

  # Custom category — used in: notify <msg> category:quest_complete
  quest_complete:
    io: title
    fadeIn: "10"       # ticks
    stay: "60"
    fadeOut: "20"

  boss_warning:
    io: bossbar
    barColor: RED      # RED, BLUE, GREEN, YELLOW, PURPLE, PINK, WHITE
    barStyle: SOLID    # SOLID, SEGMENTED_6, SEGMENTED_10, SEGMENTED_12, SEGMENTED_20
    stay: "200"        # ticks before the bar disappears

  level_up_sound:
    io: sound
    soundKey: "entity.player.levelup"
    soundVolume: "1.0"
    soundPitch: "1.0"
```

**Built-in categories** (can be overridden per-package):
- `info` → `chat`
- `error` → `actionbar`

**IO types:**

| IO | Key | Description |
|----|-----|-------------|
| `chat` | — | Chat message |
| `actionbar` | — | Action bar above hotbar |
| `title` | `fadeIn`, `stay`, `fadeOut` | Title text; put NPC lines in the message |
| `subtitle` | `fadeIn`, `stay`, `fadeOut` | Subtitle only |
| `bossbar` | `barColor`, `barStyle`, `stay` | Boss bar; auto-hides after `stay` ticks |
| `sound` | `soundKey`, `soundVolume`, `soundPitch`, `soundCategory` | Sound only (message ignored) |
| `advancement` | — | Falls back to actionbar (Paper API limitation) |

---

## 12. Points System

Points are per-player counters stored per category. Use them for currency, reputation, faction standing, etc.

**DSL:**
```yaml
- "point currency add 250"
- "point currency take 100"
- "point currency set 0"
```

**Condition:**
```yaml
- "point currency 500"     # player has at least 500 currency points
```

**Variable:**
```yaml
"$point.currency$"         # resolves to the int count
```

Points are persisted in the player's profile JSON — no extra database table needed.

---

## 13. Player Hider

Player hiders conditionally hide players from each other. Define them in any YAML file in a package under `player_hider:`.

```yaml
player_hider:

  hide_in_mine:
    # Players matching source_player conditions will have target_player-matching
    # players hidden from their view. Evaluated every 20 ticks.
    source_player:
      - "zone coal_mine"
    target_player:
      - "!zone coal_mine"       # target is NOT in the mine

  pvp_zone_hider:
    source_player:
      - "zone pvp_arena"
    target_player:
      - "!tag pvp_participant"
```

Both `source_player` and `target_player` accept any condition from [§6](#6-conditions-reference). Leave a list empty to match all players.

---

## 14. Templates

Templates are packages stored in `plugins/Valmora/templates/` that other packages can inherit from.

```
plugins/Valmora/
  templates/
    reward_template/
      quest.yml       ← package header (must exist)
      rewards.yml     ← named event lists and conditions
  quests/
    my_quest/
      quest.yml       ← package:  templates: [reward_template]
```

Package features **always win** over template features — templates supply defaults, not overrides. Merging uses `putIfAbsent`, so if you define `events.reward_coins_small` in both the template and the package, the package version is used.

---

## 15. Cross-Package References

Any feature from another package can be referenced using the `>` separator.

**Event reference in an action list:**
```yaml
rewards:
  - "forgotten_mine>reward_coins_large"    # runs the named event from another package
```

**Conversation pointer:**
```yaml
pointers:
  - otherPackagePath>otherConversation.npcOptionId
```

**Package path syntax:**
- `forgotten_mine` — absolute path (folder name directly under `quests/`)
- `forgotten_mine-dungeon` — nested: `quests/forgotten_mine/dungeon/` (sub-package)
- `_-sibling` — relative: go up one level, then into `sibling/`

---

## 16. Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/quest` | — | Opens the Quest Journal |
| `/quest journal` | — | Same as above |
| `/valmora reload` | `valmora.admin` | Reloads all modules including quests |
| `/npc list` | `valmora.admin` | Lists all registered NPCs |
| `/npc reload` | `valmora.admin` | Reloads all modules |

**Script commands (in-game, for debugging):**
You can trigger quest events manually by running them through an NPC action or a `/valmora` admin tool once that is implemented.

---

## 17. Full Example — The Forgotten Mine

The full example package ships with Valmora and lives at:
```
plugins/Valmora/quests/forgotten_mine/
```

It demonstrates:
- Two chained quests (`forgotten_mine` → `blacksmith_request`)
- A persistent auto-once daily bonus quest (`daily_mining`)
- All major objective types (KILL, BLOCK_BREAK, CRAFT, SMELT, FISH, TALK_TO_NPC, REACH_ZONE)
- Objective conditions (zone gate, time-of-day gate)
- Per-objective notifications
- Named events and conditions
- Full NPC conversations with `firstOptions` context switching
- Custom notification categories (title, actionbar)
- Points as a currency reward

### Package layout
```
forgotten_mine/
  quest.yml           ← package header
  quests.yml          ← quest + objective definitions, named events/conditions
  conversations.yml   ← thorin_main and bjorn_main conversations
  notifications.yml   ← quest_complete (title) and quest_progress (actionbar) categories
```

### Quest chain overview

```
[Player joins server]
       │
       ▼
daily_mining activates (auto-once, persistent)
       │  +25 coins every 50 blocks mined
       │
       ▼
Right-click Elder Thorin (NPC: thorin)
       │  firstOptions: greeting_new → greeting_progress → greeting_done
       │  Player accepts → quest_start forgotten_mine
       │
       ▼
Objectives (all required, any order):
  ✓ TALK_TO_NPC thorin           (met Thorin)
  ✓ BLOCK_BREAK COAL_ORE × 20   (notify every 5)
  ✓ KILL cave_spider × 5         (only in zone coal_mine)
  ✓ REACH_ZONE coal_mine         (entering the zone)
       │
       ▼
Rewards: 250 currency, 32 coal, level-up sound, tag forgotten_mine.done
       │
       ▼
Right-click Bjorn (NPC: bjorn)
       │  firstOptions: bjorn_offer_quest (gated by tag)
       │  Player accepts → quest_start blacksmith_request
       │
       ▼
Objectives:
  ✓ TALK_TO_NPC bjorn
  ✓ CRAFT IRON_PICKAXE × 1
  ✓ SMELT IRON_INGOT × 10        (notify every 2)
  ✓ FISH any × 3                 (only during daytime)
       │
       ▼
Rewards: 500 currency, 1 iron pickaxe, tag blacksmith_request.done
```

### NPC file (`npcs/hub.yml`)

```yaml
thorin:
  display-name: "<gold>Elder Thorin"
  entity-type: VILLAGER
  world: world
  x: -8.5
  y: 65.0
  z: 8.5
  yaw: 90
  on-right-click: []
  on-left-click: []

bjorn:
  display-name: "<red>Bjorn"
  entity-type: VILLAGER
  world: world
  x: 8.5
  y: 65.0
  z: 8.5
  yaw: 270
  on-right-click: []
  on-left-click: []

npc_conversations:
  thorin: thorin_main
  bjorn: bjorn_main
```

### Conversation flow for Elder Thorin

```
Player right-clicks Thorin
   │
   ├── conditions pass?  !quest forgotten_mine in_progress
   │                     !quest forgotten_mine completed
   │   YES → greeting_new
   │           "The mine has gone silent..."
   │           choices: [Accept] [Decline]
   │               Accept → quest_start forgotten_mine → quest_accepted node
   │               Decline → quest_declined node
   │
   ├── conditions pass?  quest forgotten_mine in_progress
   │   YES → greeting_progress
   │           "How goes your work in the mine?"
   │
   └── conditions pass?  quest forgotten_mine completed
       YES → greeting_done
               "You have done the village a great service..."
```

---

*For scripting internals (EventFactory, VariableProvider, ConditionParser), see `docs/VALMORA_DOCUMENTATION.md` and `docs/MODULE_DEVELOPMENT.md`.*
