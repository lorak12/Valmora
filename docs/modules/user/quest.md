# Quest Module — User Documentation

## Overview

The Quest module is a comprehensive quest system for Valmora that provides players with a
rich set of mechanics for tracking objectives, managing progression, earning points,
and interacting with quest boards and NPC dialogue. It includes a built-in points
currency system and journal interface for quest tracking.

### What Players Can Do

- Accept and track quests from quest boards and NPC interactions
- Progress through quest objectives including kills, collections, crafting, and more
- View active quests and their progress in the quest journal (`/quest journal`)
- Track one active quest on a sidebar scoreboard (`/quest track <quest_id>`)
- Earn and spend **Quest Points** — a shared currency across the quest system
- Complete dialogue trees with NPCs to unlock or progress quests

### Module ID

```
quest
```

The module loads at position 21 in the module registration order (`Valmora.java:228`).
It is registered after `recipe` and before `enchant`.

The `points` module (load order position 21.5, registered at `Valmora.java:211`) runs
in parallel with the quest module and provides the shared points economy.

---

## Getting Started

### Viewing Available Quests

The primary command for players is:

```
/quest
```

Running this command opens the **Quest GUI** — a main menu showing all available
quests from registered quest boards. From this menu you can:

- See all quests you have discovered
- Check your current Quest Points balance
- Access your quest journal
- Track/untrack quests

### Quest Journal

To view your full quest log:

```
/quest journal
```

The journal displays all active quests, their objectives, and current progress.
Completed quests are listed separately.

### Tracking a Quest

To track a quest on your sidebar scoreboard:

```
/quest track <quest_id>
```

To stop tracking:

```
/quest track none
```

The tracked quest's objectives and progress will be displayed on the right side
of your screen while you play.

### Quest Points

Quest Points are a currency earned by completing quests. Check your balance:

```
/quest points
```

Or view the leaderboard:

```
/quest points top [page]
```

Points can be spent on rewards configured per quest board or used for global
progression unlocks.

---

## Quest Structure

### What Is a Quest?

A quest in Valmora is defined by a combination of:

1. **A package group** — which quest board it belongs to
2. **A quest card** — the visual card shown in the GUI
3. **Quest definitions** — the mechanical definition of objectives
4. **Optional dialogue** — NPC conversation that unlocks the quest

Each quest has:

- An ID (e.g., `shardworks/armory_upgrade`)
- A display name
- A description (flavor text)
- A tier (Beginner, Intermediate, Expert, or Master)
- A category (combat, exploration, crafting, etc.)
- Required level and prerequisites
- Multiple objectives (stages that must be completed)
- Rewards (items, points, experience)

### Packages

Quests are organized into **packages** — thematic collections that belong to a
specific quest board. For example, the `shardworks` board has packages like
`armory_upgrade`, `mine_expedition`, `crystal_hunter`, `deep_calls`, and
`the_foundry`.

Each package is a directory under `resources/quests/`:

```
resources/quests/
├── shardworks/
│   ├── armory_upgrade/
│   │   ├── armory_upgrade.yml
│   │   ├── armory_upgrade_card.yml
│   │   └── armory_upgrade_dialogue.yml (optional)
│   ├── mine_expedition/
│   │   └── ...
│   └── ...
└── forgotten_mine/
    ├── ancient_forge/
    └── ...
```

### Quest Tiers

| Tier       | Color   | Description                          |
|------------|---------|--------------------------------------|
| Beginner    | White   | Low-level quests, simple objectives  |
| Intermediate| Yellow  | Moderate difficulty, some prerequisites |
| Expert      | Red     | High-level content, complex chains    |
| Master      | Purple  | End-game quests, challenging          |

### Quest Categories

| Category     | Description                             |
|--------------|------------------------------------------|
| Combat        | Kill mobs, defeat bosses                 |
| Exploration   | Visit locations, find items               |
| Crafting      | Craft items, smelt materials              |
| Collection    | Gather or collect specified items         |
| Dialogue      | Talk to NPCs, make choices               |
| Activity      | Unique activity-based objectives           |

---

## Objectives

Quests are made up of **objectives** — individual tasks you must complete. Each
objective has a type that determines how it progresses.

### Objective Types

#### SMELT

Requires you to smelt a certain quantity of items in a furnace. Simply place the
items in a furnace and wait for them to smelt. Progress is tracked automatically.

#### GIVE

Requires you to hand in specific items. When you have the required items in your
inventory and interact with the quest giver (NPC or board), the items will be
consumed and the objective marked complete.

#### MOB_KILL

Requires you to kill a specified number of a particular mob type. Progress
updates in real-time as you defeat mobs. This objective tracks kills across all
worlds and respects mob scaling from the combat module.

#### ITEM_DROP

Requires you to obtain a specific item as a drop. This can be from mob kills,
chest looting, or fishing, depending on configuration. Progress is automatic.

#### ENTITY_INTERACT

Requires you to interact with (right-click) a specific NPC or entity. The NPC
must be one that has been configured with the appropriate interaction trigger.

#### BLOCK_BREAK

Requires you to break a specific block type a number of times. Progress is
tracked globally across all worlds.

#### ITEM_USE

Requires you to use (right-click with) a specific item a certain number of
times. For example, drinking potions or using a special tool.

#### RECIPE_UNLOCK

Requires you to learn (unlock) a specific crafting recipe. This is tracked
through the recipe module's unlocked recipes system.

#### DISTANCE_WALK

Requires you to walk or run a certain distance (in blocks). Progress is tracked
via movement events and persists across sessions.

#### FISHING

Requires you to catch a certain number of fish. Any fishing activity counts
toward this objective, including treasure catches.

#### BREW

Requires you to brew a specific potion. The objective completes when you
successfully create the specified potion in a brewing stand.

#### CUSTOM

A flexible objective type that triggers on custom plugin events. Server
administrators can configure these to track any event broadcast by other
plugins or systems.

#### WAIT

Requires you to wait for a specified duration (in ticks or real time) after
starting the objective. This is commonly used for timed quest elements or
cooldowns between quest stages.

### Objective Visibility

Objectives can be hidden until certain conditions are met:

- **HIDDEN** — Completely invisible until unlocked by a previous objective
  or quest state.
- **UNLOCKED** — Visible but shows as "???", revealing details only after
  some trigger (e.g., talking to an NPC).
- **VISIBLE** — Always shown with full details.

NPCs and the quest board GUI will indicate which objectives are locked or hidden.

---

## Quest Boards

### What Are Quest Boards?

Quest boards are special blocks (typically item frames on walls or displayed
items) that serve as quest givers. They display quests from their associated
package and allow you to accept new quests.

### Interacting with Quest Boards

Approach a quest board and right-click it. This opens a GUI showing all available
quests from that board's packages. Each quest appears as a card with:

- The quest name and tier (colored by difficulty)
- A brief description or flavor text
- Current progress (if already started)
- Requirements (level, prerequisites, etc.)

Click the card to view full details and accept the quest.

### Available Quest Boards

| Board ID       | Location         | Packages                          |
|----------------|------------------|------------------------------------|
| `shardworks`    | Shardworks Hub    | armory_upgrade, mine_expedition, crystal_hunter, deep_calls, the_foundry |
| `forgotten_mine`| Forgotten Mine    | ancient_forge, (more planned)      |

Each board is configured in `resources/quest_boards/` with its own YAML file
(e.g., `shardworks.yml`, `forgotten_mine.yml`).

### Quest Board GUI

The board's GUI shows quest cards arranged in a grid. The top row contains
navigation:

- **Player Head** (top-left): Your Quest Points balance
- **Clock** (top-middle): Quest journal access
- **Ender Eye** (top-right): Close/exit

Below the navigation row are the quest cards. Hovering over a card shows its
full details in a tooltip. Locked quests appear faded with a lock icon.

---

## NPC Dialogue

### Talking to NPCs

Many quests are unlocked or progressed through dialogue with NPCs. Right-click
an NPC (non-hostile) to open a dialogue window.

### Dialogue Choices

Dialogue presents you with choices that affect:

- Which quest you accept
- Story direction or quest variants
- Information revealed about objectives
- Reputation or standing with factions

Each choice leads to a different dialogue node. The dialogue system is fully
branched — you can revisit some NPCs to explore alternate paths.

### Prerequisites

Some dialogue options are locked behind:

- Minimum player level
- Prerequisite quests completed
- Required items in inventory
- Specific scoreboard values or faction standing

Locked options appear grayed out with a reason shown on hover.

### Quest-Giving NPCs

NPCs that offer quests will have a golden border around their nameplate and a
glowing effect when you are near. Right-clicking them opens their dialogue tree.

---

## Quest Variables and Conditions

### Variables

Quests can reference internal variables that track player state:

- `player_level` — Your current character level
- `player_xp` — Your current experience points
- `quest_points` — Your total Quest Points balance
- `completed_count` — Number of quests you have completed
- `category_<name>_count` — Number of quests completed in a category

These variables are used in conditions to gate quest availability or dialogue
options.

### Conditions

Before you can accept a quest, you may need to meet conditions such as:

- Reach a minimum player level
- Have completed a specific quest
- Have not completed a specific quest (to prevent repeat completion)
- Hold specific items in your inventory
- Meet criteria on a scoreboard objective

If you don't meet the requirements, the quest card will show a red "Locked"
status with the specific requirement listed.

---

## Quest Points (Points Module)

Quest Points are a shared currency earned through quest completion. The points
module is loaded separately from the quest module (`Valmora.java:211`) but
integrates seamlessly.

### Earning Points

You earn Quest Points by:

- Completing quests (amount varies by quest tier)
- Reaching milestones (e.g., 10 quests completed = bonus 50 points)
- Participating in special events on quest boards

Points are added to your balance automatically upon quest completion.

### Spending Points

Points can be spent on:

- Purchasing premium quest cards from certain boards
- Buying additional quest tracking slots
- Unlocking higher-tier quests earlier
- Purchasing cosmetic rewards from the points shop

### Viewing Your Points

```
/quest points
```

This opens a GUI displaying your current balance and a history of recent
gains/losses.

### Leaderboard

```
/quest points top [page]
```

View the top 10 players by Quest Points balance, ranked highest to lowest.

### Points Commands

| Command                  | Permission            | Description                          |
|--------------------------|-----------------------|--------------------------------------|
| `/quest points`          | `points.view`         | Open points balance GUI              |
| `/quest points top [n]`  | `points.top`          | View points leaderboard              |
| `/quest points give <player> <amount>` | `points.give` (admin) | Give points to a player       |
| `/quest points take <player> <amount>` | `points.take` (admin) | Remove points from a player   |
| `/quest points set <player> <amount>`  | `points.set` (admin)  | Set a player's points              |

---

## Commands

All quest-related commands are registered in `Valmora.onEnable()` at
`Valmora.java:228`. The base command is `/quest`.

### `/quest`

Opens the main Quest GUI — a menu showing available quests from all boards,
your Quest Points balance, and quick access to the journal.

### `/quest journal`

Opens the quest journal GUI. This displays:

- **Active Quests** — quests you are currently working on
- **Completed Quests** — quests you have finished
- **Available Quests** — quests you can accept
- **Locked Quests** — quests you cannot yet access

### `/quest track <quest_id>`

Sets the specified quest as your tracked quest. Progress for this quest's
objectives will be displayed on your sidebar scoreboard.

Use `/quest track none` to stop tracking any quest.

### `/quest abandon <quest_id>`

Abandons the specified quest. All progress on current objectives will be
reset. You can re-accept the quest later if it meets availability criteria.

**Warning:** Some quests, especially those in branching dialogue trees, may
not be re-acquirable after abandonment.

### `/quest points`

Opens the Quest Points GUI (requires the `points` module to be enabled).

### `/quest points top [page]`

Displays the Quest Points leaderboard. Each page shows 10 players ranked by
total points.

### `/points`

Alias for `/quest points`. Opens the points GUI directly.

---

## Rewards

### Quest Completion Rewards

When you complete a quest, you receive:

- **Quest Points** — the amount varies by quest tier:
  - Beginner: 10-25 points
  - Intermediate: 30-60 points
  - Expert: 75-150 points
  - Master: 200+ points

- **Item rewards** — configured per quest, ranging from common materials
  to rare equipment

- **Experience** — added to your character's XP total

- **Variable unlocks** — some quests unlock new dialogue options, hidden
  objectives, or access to new quest boards

- **Achievement progress** — certain quests count toward meta-achievements

### Reward Delivery

Rewards are delivered automatically when you complete the final objective of
a quest. For item rewards, they will be placed in your inventory. If your
inventory is full, the rewards will be dropped on the ground at your location.

---

## Quest Journal Interface

### Opening the Journal

From the main Quest GUI or by running `/quest journal`.

### Journal Layout

The journal is divided into tabs:

1. **Active** — Quests you are currently working on
2. **Completed** — All quests you have finished
3. **Available** — Quests you can accept (not yet started)
4. **Locked** — Quests that are not yet available

### Active Quest View

When viewing an active quest in the journal, you see:

- Full quest description and flavor text
- Tier and category
- All objectives with:
  - Current progress (e.g., "5/10 Zombies slain")
  - Completion status (checkmarks for done objectives)
  - Visibility state (hidden objectives show as "???")
- Required level and prerequisites
- Rewards preview (hover to see details)

### Right-Click Options

- **Track** — Set as your tracked quest (sidebar display)
- **Abandon** — Give up the quest (confirms with a prompt)
- **Pin** — Mark as important to sort to the top of the Active tab

---

## Points Management (Admin)

### Configuring Point Sources

Point rewards are configured in the quest definition YAML files under the
`rewards` section. Server administrators can also add custom point grants
through plugin message triggers.

### Database Storage

Quest progress and Quest Points are stored in the Valmora database (SQLite
by default, MySQL optional). Data includes:

- Completed quest IDs per player
- Current objective progress
- Quest Points balance
- Unlocked dialogue options
- Tracked quest preference

All database operations are asynchronous (`Valmora.java:415-454` provides
async-safe accessors).

---

## Integration with Other Modules

### Combat Module

`MOB_KILL` objectives integrate with the combat module's mob system. Kills
are tracked through the combat module's damage and death events, which means
custom mobs added by the combat module will properly register kill counts
for quest objectives.

### Skill Module

Quest completion can trigger skill point awards configured per quest. The
skill module provides the `skill_points` variable used in quest conditions.

### Recipe Module

`RECIPE_UNLOCK` objectives check against the recipe module's unlocked
recipes system. Completing such a quest requires having learned the
specified recipe through normal crafting progression.

### Stat Module

Player level and experience tracked by the stat module are used in quest
conditions (e.g., "Level 15+ required"). The journal reads level data from
the stat module's player data cache.

### Script Module

Dialogue conditions and objective variables can be augmented by custom
scripts defined in the script module. For example, a script might set a
variable used in a quest's condition.

### UI Module

The quest journal and board GUIs use the UI module's GUI framework for
consistent rendering, animations, and click handling.

### Ability Module

Certain quest rewards grant abilities (e.g., temporary potion effects,
custom ability unlocks). These are delivered through the ability module's
reward system.

---

## Troubleshooting

### Quest Progress Not Updating

- Ensure you are on the correct server (quest data is per-world-set)
- Check that you haven't abandoned the quest (which resets progress)
- Some objectives require server restart or relog to refresh display

### Points Not Awarded

- Quest Points are awarded on final objective completion — make sure you
  have completed ALL objectives in the quest
- Check if your inventory was full — item rewards may have dropped on the
  ground instead

### Cannot Accept Quest

- Verify you meet all prerequisites (level, prior quests, items)
- Some quests are only available at certain times or after story events
- Check the quest card's tooltip for specific requirement details

### Dialogue Options Locked

- Hover over grayed-out options to see the requirement
- Some dialogue paths require items you must bring to the NPC
- Certain options unlock only after completing other quests

---

## For Server Administrators

### Adding New Quests

1. Create a package directory under `resources/quests/<board_id>/<quest_id>/`
2. Add a quest definition YAML file
3. Add a quest card YAML file for the GUI appearance
4. (Optional) Add a dialogue YAML file for NPC conversation

See `docs/QUEST_SYSTEM.md` for the full YAML schema reference.

### Configuring Quest Boards

Quest boards are defined in `resources/quest_boards/`. Each board YAML file
specifies:

- Which packages belong to the board
- The board's display item and lore
- Spawn locations for board NPCs
- Point reward multipliers

### Reload Command

```
/valmora reload
```

This reloads all modules including quests and points. Quest progress and
points balances are preserved.

### Permissions

| Permission           | Description                          |
|----------------------|--------------------------------------|
| `quest.use`          | Access `/quest` command (default: true) |
| `quest.admin`        | Use quest admin subcommands          |
| `points.view`        | View points balance                  |
| `points.top`         | View points leaderboard              |
| `points.give`        | Give points to players (admin)       |
| `points.take`        | Take points from players (admin)     |
| `points.set`         | Set a player's points (admin)          |

---

## Source Files Reference

### Core Module

| File | Path | Purpose |
|------|------|---------|
| QuestModule | `org.nakii.valmora.module.quest.QuestModule` | Main module class; initializes all quest subsystems (`QuestModule.java:14`) |
| QuestManager | `org.nakii.valmora.module.quest.QuestManager` | Central quest state manager (`QuestManager.java:16`) |
| QuestConfig | `org.nakii.valmora.module.quest.QuestConfig` | Loads and validates quest packages (`QuestConfig.java:15`) |
| QuestGUI | `org.nakii.valmora.module.quest.QuestGUI` | Main quest GUI menu (`QuestGUI.java:18`) |
| QuestJournalGUI | `org.nakii.valmora.module.quest.QuestJournalGUI` | Journal interface (`QuestJournalGUI.java:19`) |
| QuestTracker | `org.nakii.valmora.module.quest.QuestTracker` | Sidebar scoreboard tracking (`QuestTracker.java:17`) |
| QuestLoader | `org.nakii.valmora.module.quest.QuestLoader` | Loads quest definitions from YAML (`QuestLoader.java:22`) |
| QuestRegistry | `org.nakii.valmora.module.quest.QuestRegistry` | Stores active quest data (`QuestRegistry.java:14`) |

### Objective System

| File | Path | Purpose |
|------|------|---------|
| QuestObjective | `org.nakii.valmora.module.quest.objective.QuestObjective` | Base objective class (`QuestObjective.java:16`) |
| ObjectiveType | `org.nakii.valmora.module.quest.objective.ObjectiveType` | Enum of all objective types (`ObjectiveType.java:11`) |
| ObjectiveProgress | `org.nakii.valmora.module.quest.objective.ObjectiveProgress` | Tracks per-player progress (`ObjectiveProgress.java:15`) |
| ObjectiveHandler | `org.nakii.valmora.module.quest.objective.ObjectiveHandler` | Dispatches objective events (`ObjectiveHandler.java:24`) |
| ObjectiveActiveCondition | `org.nakii.valmora.module.quest.objective.ObjectiveActiveCondition` | Checks if objectives should be active (`ObjectiveActiveCondition.java:13`) |

### Quest Packaging

| File | Path | Purpose |
|------|------|---------|
| QuestPackage | `org.nakii.valmora.module.quest.pkg.QuestPackage` | Represents a quest package group (`QuestPackage.java:18`) |
| QuestPackageManager | `org.nakii.valmora.module.quest.pkg.QuestPackageManager` | Manages all packages (`QuestPackageManager.java:24`) |
| PackageLoader | `org.nakii.valmora.module.quest.pkg.PackageLoader` | Loads package JSON/YAML (`PackageLoader.java:28`) |
| PackageVariableProvider | `org.nakii.valmora.module.quest.pkg.PackageVariableProvider` | Provides variables for conditions (`PackageVariableProvider.java:15`) |
| PackageConfig | `org.nakii.valmora.module.quest.pkg.PackageConfig` | Package configuration holder (`PackageConfig.java:12`) |
| PackageQuest | `org.nakii.valmora.module.quest.pkg.PackageQuest` | Defines a quest within a package (`PackageQuest.java:17`) |

### Quest Board System

| File | Path | Purpose |
|------|------|---------|
| BoardManager | `org.nakii.valmora.module.quest.board.BoardManager` | Manages quest boards (`BoardManager.java:22`) |
| BoardConfig | `org.nakii.valmora.module.quest.board.BoardConfig` | Board configuration (`BoardConfig.java:14`) |
| BoardLoader | `org.nakii.valmora.module.quest.board.BoardLoader` | Loads board configs (`BoardLoader.java:18`) |
| BoardGUI | `org.nakii.valmora.module.quest.board.BoardGUI` | Board selection GUI (`BoardGUI.java:16`) |
| BoardData | `org.nakii.valmora.module.quest.board.BoardData` | Runtime board state (`BoardData.java:12`) |

### Points System

| File | Path | Purpose |
|------|------|---------|
| PointsManager | `org.nakii.valmora.module.points.PointsManager` | Manages player points (`PointsManager.java:20`) |
| PointsConfig | `org.nakii.valmora.module.points.PointsConfig` | Points configuration (`PointsConfig.java:15`) |
| PointsCommand | `org.nakii.valmora.module.points.PointsCommand` | `/quest points` command (`PointsCommand.java:18`) |
| PointsGUI | `org.nakii.valmora.module.points.PointsGUI` | Points balance GUI (`PointsGUI.java:16`) |
| PointEvent | `org.nakii.valmora.module.points.PointEvent` | Event for point changes (`PointEvent.java:18`) |
| PointCondition | `org.nakii.valmora.module.points.PointCondition` | Points-based conditions (`PointCondition.java:14`) |
| PointVariableProvider | `org.nakii.valmora.module.points.PointVariableProvider` | Variable resolver for points (`PointVariableProvider.java:12`) |

### NPC Dialogue

| File | Path | Purpose |
|------|------|---------|
| DialogueManager | `org.nakii.valmora.module.quest.DialogueManager` | Manages dialogue trees (`DialogueManager.java:22`) |
| DialogueDefinition | `org.nakii.valmora.module.quest.DialogueDefinition` | Dialogue tree model (`DialogueDefinition.java:19`) |
| DialogueNode | `org.nakii.valmora.module.quest.DialogueNode` | A single dialogue node (`DialogueNode.java:18`) |
| DialogueChoice | `org.nakii.valmora.module.quest.DialogueChoice` | Player choice in dialogue (`DialogueChoice.java:16`) |
| DialogueGUI | `org.nakii.valmora.module.quest.DialogueGUI` | Dialogue interface (`DialogueGUI.java:20`) |

### NPC System

| File | Path | Purpose |
|------|------|---------|
| NpcManager | `org.nakii.valmora.module.quest.NpcManager` | Manages quest NPCs (`NpcManager.java:18`) |
| NpcDefinition | `org.nakii.valmora.module.quest.NpcDefinition` | NPC definition model (`NpcDefinition.java:20`) |
| NpcLoader | `org.nakii.valmora.module.quest.NpcLoader` | Loads NPC configs (`NpcLoader.java:16`) |
| NpcDialogueHandler | `org.nakii.valmora.module.quest.NpcDialogueHandler` | Handles NPC right-click events (`NpcDialogueHandler.java:24`) |

### Hider System

| File | Path | Purpose |
|------|------|---------|
| PlayerHiderManager | `org.nakii.valmora.module.quest.hider.PlayerHiderManager` | Manages hidden quest elements (`PlayerHiderManager.java:14`) |
| PlayerHider | `org.nakii.valmora.module.quest.hider.PlayerHider` | Per-player hider state (`PlayerHider.java:12`) |

### Cross-Module Integration

| File | Path | Purpose |
|------|------|---------|
| ObjectiveHandler | `org.nakii.valmora.api.quest.ObjectiveHandler` | External plugin objective handler API (`ObjectiveHandler.java:9`) |
| QuestStatusCondition | `org.nakii.valmora.module.quest.condition.QuestStatusCondition` | Checks quest completion status for conditions |
| ProgressionModule | `org.nakii.valmora.module.quest.progression.ProgressionModule` | Integrates with stat/skill progression |
| ProgressionManager | `org.nakii.valmora.module.quest.progression.ProgressionManager` | Manages progression triggers |

### Event System

| File | Path | Purpose |
|------|------|---------|
| NotifyEvent | `org.nakii.valmora.module.quest.NotifyEvent` | Notification event type |
| NotifyManager | `org.nakii.valmora.module.quest.NotifyManager` | Manages notifications to players |

---

## YAML Configuration Reference

### Quest Definition Format

Quest definitions are stored in `resources/quests/<board>/<package>/<quest_id>.yml`.
See `docs/QUEST_SYSTEM.md` for the full schema.

Example (abbreviated):

```yaml
id: shardworks/armory_upgrade
display_name: "<gold>Armory Upgrade"
tier: INTERMEDIATE
category: CRAFTING
description:
  - "<gray>The armory needs a new weapon rack."
  - "<gray>Craft a diamond sword for the guards."
level_requirement: 10
objectives:
  - type: CRAFT
    material: diamond_sword
    amount: 1
    hidden: false
rewards:
  points: 45
  items:
    - material: emerald
      amount: 3
  experience: 150
```

### Quest Card Format

Cards are stored alongside quest definitions as `<quest_id>_card.yml`.

Example:

```yaml
quest_id: shardworks/armory_upgrade
display_item: diamond_sword
display_name: "<gold>Armory Upgrade"
lore:
  - "<yellow>Tier: <gold>Intermediate"
  - "<yellow>Category: <aqua>Crafting"
  - ""
  - "<gray>Craft a diamond sword for the guards."
tier_color: gold
category_color: aqua
rarity: RARE
```

### Board Configuration Format

Board configs are stored in `resources/quest_boards/<board_id>.yml`.

Example:

```yaml
id: shardworks
display_item: cartography_table
display_name: "<gold>Shardworks Quest Board"
packages:
  - armory_upgrade
  - mine_expedition
  - crystal_hunter
  - deep_calls
  - the_foundry
point_multiplier: 1.0
```

### Package Format

Packages are defined as directories containing quest definition files. A
package may also include a `package.yml` with metadata:

```yaml
id: shardworks/armory_upgrade
name: "Armory Upgrade"
description: "Quests related to upgrading the Shardworks armory."
quests:
  - armory_upgrade
  - weapon_rack
  - armor_stand
```

---

## Additional Resources

- `docs/QUEST_SYSTEM.md` — Full quest system design document
- `docs/UNFINISHED_FEATURES.md` — Planned features and TODOs (Section 11: Quest system)
- `docs/MODULE_DEVELOPMENT.md` — Module lifecycle and development guide
- `docs/VALMORA_DOCUMENTATION.md` — Complete Valmora API and subsystem reference

For in-game help, use:

```
/quest help
```
