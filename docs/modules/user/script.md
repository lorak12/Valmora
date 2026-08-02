# Script Module — User Documentation

## Overview

The **Script module** is Valmora's built-in Domain-Specific Language (DSL)
engine. It provides three subsystems that you use when writing YAML
configuration files for quests, GUIs, items, NPCs, skills, collections,
slayers, zones, calendar events, and more:

1. **Script variables** — Read dynamic values like player stats, skill
   levels, time, and more using `$namespace.path$` syntax. Used in
   conditions, expressions, and text templates (lore, notifications,
   action bar, scoreboard).

2. **Script conditions** — Gate content behind checks (health thresholds,
   quest progress, tags, custom variables, etc.). Used in GUI click
   conditions, quest objective conditions, NPC hologram visibility, skill
   gating, and more.

3. **Script events** — Trigger side effects like giving items, modifying
   stats, starting quests, adjusting economy balances, etc. Used in quest
   rewards, collection stage rewards, GUI click actions, NPC interactions,
   calendar event callbacks, and more.

All three subsystems work together through the **ExecutionContext** — an
internal object that carries the current player (caster), target, location,
and parameters. You do not need to create or manage this yourself; the
engine handles it when your scripts are evaluated.

---

## Player Guide

As a player, you interact with the script engine indirectly — it powers the
dynamic behavior of items, quests, GUIs, and world features. Here is what
scripts do for you in-game:

- **Dynamic item abilities**: Right-click a staff to fire a frost bolt that
  deals damage scaled by your `DAMAGE` stat and slows enemies. The damage
  formula is a script expression evaluated at cast time.
- **Quest progression**: Completing tasks triggers script events that give
  rewards, set quest tags, and unlock the next quest stage. Quest
  availability is gated by script conditions (e.g., "player must have 50
  reputation points").
- **GUI interactivity**: Menus respond to your input. Clicking a button runs
  script events (give items, open another GUI, start a crafting process).
  The contents of menus update dynamically — e.g., an enchanting table
  GUI shows available enchants filtered by your held item's type.
- **Scoreboard & action bar**: Displays real-time stats like health, mana,
  zone name, and time of day, all resolved from script variables every
  tick.
- **World features**: Zones run script events when you enter (e.g., a
  welcome message or a debuff). Calendar events fire scripts at specific
  phases (e.g., a daily bonus reward).
- **Collections**: Reaching kill-count milestones triggers script reward
  events (e.g., "give DIAMOND:10").
- **Slayer system**: Completing slayer tiers triggers script events
  (e.g., "economy_add 250").
- **Economy**: In-game transactions (shop purchases, quest payments) use
  script conditions to check balances and script events to add/remove coins.

### What players see

Players see the **results** of scripts, not the scripts themselves:
- Floating damage numbers that reflect stat-scaled calculations
- Action bar messages like `"<aqua>Not enough Mana!"` or `"<red>Ability on cooldown: 2.5s"`
- Scoreboard lines showing dynamic values (time, zone, profile, purse)
- Quest journal entries that update based on conditions
- GUI menus with dynamic item slots, conditional visibility, and interactive buttons

---

## Admin Guide

### Writing Conditions

Conditions are the "gating" mechanism. They appear in YAML as:

```yaml
conditions:
  - "<condition string>"
  - "<condition string>"
```

All conditions in the list must be true (AND logic). A single `!` prefix
negates one condition.

#### Condition Types

**Tag condition** — Checks if the player's profile has a specific tag
(string flag). Tags are set by the `tag` event.

```yaml
conditions:
  - "tag quest_started"
  - "!tag quest_complete"
```

**Expression condition** — Evaluates any expression. The result must be
`true` or `false` (or any boolean-coercible value).

```yaml
conditions:
  - "$player.stat.HEALTH$ > 50"
  - "$player.stat.MANA$ == 0"
  - "$player.var.coins$ >= 100"
  - "$player.stat.HEALTH$ > 50 and $player.stat.SPEED$ < 200"
```

**Health condition** — Checks if the player has at least N HP.

```yaml
conditions:
  - "health 20"
```

**Hunger condition** — Checks if the player has at least N food level.

```yaml
conditions:
  - "hunger 15"
```

**Location condition** — Checks if the player is within a radius of a
specific coordinate. Format: `x;y;z;world`.

```yaml
conditions:
  - "location 100;64;200;world 10"
```

**Zone condition** — Checks if the player is currently in a named zone.

```yaml
conditions:
  - "zone pvp_arena"
```

**Variable condition** — Compares a resolved variable to a literal value.

```yaml
conditions:
  - "variable player.stat.HEALTH > 100"
  - "variable player.var.mode == active"
```

**Quest condition** — Checks a quest's status.

```yaml
conditions:
  - "quest my_quest COMPLETED"
  - "quest my_quest ACTIVE"
```

**Objective condition** — Checks if a specific objective is active.

```yaml
conditions:
  - "objective my_objective"
```

**Point condition** — Checks if the player has at least N points in a
category.

```yaml
conditions:
  - "point reputation 50"
```

### Writing Expressions

Expressions are used in:
- Expression conditions (anything not matching a keyword above)
- `ExecutionContext.resolveDouble()` / `resolveInt()` / `resolveString()`
  in mechanics that accept formula strings (e.g., `amount: "$player.stat.DAMAGE$ * 2"`)
- Ability condition strings in `AbilityExecutor`

#### Expression Grammar

| Element | Syntax | Example |
|---------|--------|---------|
| Number literal | `123` or `123.45` | `10`, `3.14` |
| String literal | `"text"` | `"hello"` |
| Boolean literal | `true` / `false` | `true` |
| Variable | `$namespace.path$` | `$player.stat.HEALTH$` |
| Arithmetic | `+`, `-`, `*`, `/` | `10 + 5`, `$stat$ * 2` |
| Comparison | `==`, `!=`, `>`, `<`, `>=`, `<=` | `$hp$ > 50` |
| Logical AND | `and`, `&&` | `a > 0 and b > 0` |
| Logical OR | `or`, `\|\|` | `a or b` |
| Ternary | `condition ? value : value` | `$hp$ > 50 ? "safe" : "danger"` |
| Grouping | `( ... )` | `(a + b) * c` |
| Math functions | `floor(x)`, `ceil(x)`, `round(x)`, `abs(x)`, `sqrt(x)`, `log10(x)`, `log(x)`, `pow(a, b)`, `min(a, b, ...)`, `max(a, b, ...)` | `floor($player.stat.DAMAGE$ / 2)` |

**Operator precedence** (highest to lowest):
1. Primary (literals, variables, grouped, function calls)
2. `*` and `/`
3. `+` and `-`
4. `==`, `!=`, `>`, `<`, `>=`, `<=`
5. `and` / `or`
6. `?` `:` (ternary, right-associative)

**Number formatting in templates**: When a variable is used in a MiniMessage
text template (like a notification message), numbers with no fractional part
are rendered as integers (`100.0` → `"100"`, `100.5` → `"100.5"`).

### Writing Script Events

Events are side-effect strings used in YAML lists (e.g., `rewards:`,
`actions:`, `on-start:`, `events:`):

```yaml
rewards:
  - "tag add quest_done"
  - "give DIAMOND:5 notify"
  - "economy_add 250 delay:20"
```

#### Two options can be appended to any event:

- **`notify`** — Sends a notification message to the player when the event
  executes. What counts as "notification" depends on the event (e.g., `give`
  sends a chat message listing the items received).
- **`delay:<ticks>`** — Delays execution by N server ticks (20 ticks = 1
  second).

#### Built-in Events

**`give`** — Give vanilla items to the caster player.

```
give <MATERIAL>:<amount>
give DIAMOND:5
give STONE:64 notify
```

**`tag`** — Add or remove a persistent string flag on the player's profile.

```
tag add <tagName>
tag remove <tagName>
tag add quest_started
tag remove tutorial_lock
```

Tags are simple strings stored on the profile and persist across sessions
(saved in the database). They can be checked with the `tag` condition.

**`variable`** — Set, add, or remove custom variables.

```
variable set player.var.<name> <value>
variable add player.var.<name> <number>
variable set prop.<name> <value>
variable add prop.<name> <number>
variable remove <path>
```

- `player.var.*` variables are **persisted** to the database and accessible
  via `$player.var.<name>$`.
- `prop.*` variables are **transient GUI session properties** and accessible
  via `$prop.<name>$`. They are lost when the GUI is closed.
- Values support MiniMessage-style `$variable$` interpolation: `variable set player.var.last_hit
  $target.type$` resolves `$target.type$` at execution time.
- Numbers are stored as `Double`, `"true"`/`"false"` as `Boolean`,
  everything else as `String`.

```
variable set player.var.coins 100
variable add player.var.kill_streak 1
variable add prop.brew_time -1
variable remove player.var.tutorial_step
```

**`condition`** — Gate event execution. If the expression is false,
`ConditionAbortException` is thrown, skipping all remaining events in the
list and triggering `fail-actions`.

```
condition $player.stat.HEALTH$ > 50
```

**`teleport`** — Teleport the player.

```
teleport warp:<warpId>
teleport @look <blocks>
teleport <x> <y> <z>
teleport <world> <x> <y> <z>
```

- `warp:<id>` uses a registered warp point (respects unlock conditions).
- `@look <blocks>` teleports the player forward in their look direction.
- Absolute coordinates use the caster's current world unless a world name
  is specified as the first argument.
- Teleportation is blocked if the player's current zone has `teleportation: false`.

**`spawn_mob`** — Spawn custom mobs near the caster.

```
spawn_mob <mobId>
spawn_mob <mobId> <count>
spawn_mob <mobId> <count> radius:<r>
```

**`stat_modify`** — Modify base stats on the player's active profile.

```
stat_modify add <statId> <value>
stat_modify set <statId> <value>
stat_modify reset <statId>
```

- The value supports `$variable$` interpolation.
- Example: `stat_modify add SPEED $target.level$` adds the target's level
  to the player's base Speed stat.

**`foreach`** — Execute an inner event for multiple players.

```
foreach @all <event...>
foreach @nearby:<radius> <event...>
```

- `@all` targets all online players.
- `@nearby:<N>` targets all players within N blocks of the caster.
- The inner event's caster becomes each targeted player.

**`run_script`** — Schedule a repeating task that fires an inner event.

```
run_script <intervalTicks> <times> <event...>
```

- `intervalTicks` — ticks between each firing (20 = 1 second).
- `times` — number of times to fire.
- The inner event is re-parsed each tick, so variables update dynamically.

```
run_script 20 5 spawn_mob zombie_minion 1
```

### Condition-Gated Action Blocks

Many YAML sections use the three-part pattern: `conditions`, `actions`,
`fail-actions`.

```yaml
on-open:
  conditions:
    - "tag quest_started"
    - "$player.stat.HEALTH$ > 20"
  actions:
    - "give DIAMOND:1 notify"
  fail-actions:
    - "notify <red>You don't meet the requirements. io:actionbar"
```

- **conditions** — All must be true for `actions` to run.
- **actions** — Executed if conditions pass.
- **fail-actions** — Executed if any condition fails (via
  `ConditionAbortException`). If a `fail-actions` block itself throws
  `ConditionAbortException` (e.g., from a nested `condition` event), it is
  silently ignored.

### Inline Conditions

Some event strings support a `conditions:` suffix for per-event gating:

```
give DIAMOND:1 conditions:$player.stat.HEALTH$ > 0
```

This is parsed by `EventParser.parse()` and wraps the compiled event with a
condition check. If the condition fails, the event is skipped (it does **not**
throw `ConditionAbortException` — it just doesn't fire).

---

## Configuration Reference

### Script Condition Keywords

| Keyword | Syntax | Description |
|---------|--------|-------------|
| `tag` | `tag <tagName>` | True if the player has the tag. `!` prefix negates. |
| `health` | `health <number>` | True if player's current HP >= number. |
| `hunger` | `hunger <number>` | True if player's food level >= number. |
| `location` | `location <x>;<y>;<z>;<world> <radius>` | True if player is within radius blocks of the coordinate. |
| `zone` | `zone <zoneId>` | True if player is in the named zone. |
| `variable` | `variable <path> <op> <value>` | Compares resolved `$path$` to value. Operators: `==`, `!=`, `>`, `<`, `>=`, `<=`. |
| `objective` | `objective <id>` | True if the quest objective is currently active. |
| `quest` | `quest <questId> <status>` | True if quest status matches (NOT_STARTED, ACTIVE, COMPLETED). |
| `point` | `point <category> <amount>` | True if player has at least `<amount>` points in the category. |
| *(expression)* | `<`any expression`>` | Evaluates the expression; must return true/false. |

### Script Variables (Complete Reference)

All variables use the syntax `$namespace.path$` and return one of: String,
Double, Integer, Boolean, Long, or a JSON string (for lists/objects used in
PAGINATED GUI components).

#### Built-in: `$player.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$player.name$` | String | Player's Minecraft name. |
| `$player.world$` | String | Current world name. |
| `$player.ping$` | Integer | Player's ping in milliseconds. |
| `$player.biome$` | String | Biome name at player's location. |
| `$player.stat.<STAT>$` | Double | Effective stat value (after equipment bonuses). Any stat ID: `DAMAGE`, `HEALTH`, `STRENGTH`, `DEFENSE`, `CRIT_CHANCE`, `CRIT_DAMAGE`, `SPEED`, `MANA`, `HEALTH_REGEN`, `MANA_REGEN`. |
| `$player.stat.list$` | JSON String | Array of all stat objects (id, name, material, value, max, display_value). Used in PAGINATED GUIs. |
| `$player.skill.list$` | JSON String | Array of all skill objects. Used in skill GUIs. |
| `$player.skill.<skillId>.xp$` | Integer | Total XP in the skill. |
| `$player.skill.<skillId>.level$` | Integer | Current level. |
| `$player.skill.<skillId>.next_level$` | Integer | XP for next level. |
| `$player.skill.<skillId>.progress$` | Double | Progress percent (0–1) in current level. |
| `$player.skill.<skillId>.xp_in_level$` | Integer | XP earned in current level. |
| `$player.skill.<skillId>.xp_required$` | Integer | XP needed for next level. |
| `$player.hp$` | Double | Current health. |
| `$player.max_hp$` | Double | Maximum health (effective). |
| `$player.health_percent$` | Integer | Current health as percentage of max (0–100, truncated). |
| `$player.missing_hp_percent$` | Integer | Missing health as percentage (0–100, truncated). |
| `$player.mana$` | Double | Current mana. |
| `$player.max_mana$` | Double | Maximum mana. |
| `$player.profile$` | String | Active profile name. |
| `$player.last_damage$` | Double | Most recent damage the player dealt (set by the combat pipeline). |
| `$player.weapon_damage$` | Double | Base weapon DAMAGE stat of the held item. |
| `$player.var.<varName>$` | Object | Custom profile variable. |

#### Built-in: `$system.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$system.time$` | Long | Current Unix timestamp in milliseconds. |

#### Built-in: `$world.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$world.name$` | String | Current world name. |
| `$world.dimension$` | String | `"NORMAL"`, `"NETHER"`, or `"THE_END"`. |

#### Built-in: `$server.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$server.online$` | Integer | Number of online players. |
| `$server.max_players$` | Integer | Server max player slots. |
| `$server.motd$` | String | Server MOTD. |

#### Built-in: `$time.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$time.hour$` | Integer | 0–23. |
| `$time.minute$` | Integer | 0–59. |
| `$time.day$` | Integer | Day of the current phase. |
| `$time.phase$` | String | Phase name (e.g., `"Early"`). |
| `$time.season$` | String | Season name (e.g., `"Spring"`). |
| `$time.year$` | Integer | RPG year. |
| `$time.total_days$` | Long | Total days since epoch. |
| `$time.total_minutes$` | Long | Total minutes since epoch. |
| `$time.is_day$` | Boolean | True during daytime. |
| `$time.time_of_day$` | String | E.g., `"☀ Day"` or `"☾ Night"`. |
| `$time.formatted_time$` | String | E.g., `"14:30"`. |
| `$time.emote$` | String | Time-of-day emote character. |
| `$time.color$` | String | MiniMessage color tag for the time of day. |

#### Built-in: `$target.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$target.type$` | String | Bukkit entity type (e.g., `ZOMBIE`). |
| `$target.name$` | String | Entity's custom or default name. |
| `$target.health$` | Double | Current health. |
| `$target.max_health$` | Double | Max health attribute. |
| `$target.level$` | Integer | Currently always `1` (custom mob levels not yet wired). |

#### Built-in: `$prop.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$prop.<key>$` | Object | Transient GUI session property. Set via `variable set prop.<key>`. Lost on GUI close. |

#### Built-in: `$param.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$param.<key>$` | Object | Value from `ExecutionContext.getParams()` (the YAML params section of the current mechanic/event). |

#### Built-in: `$range.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$range.<start>.<end>$` | List\<Integer> | Generates a list of integers from start to end (inclusive). End can be a literal number or a variable path (without `$`). Also supports reverse ranges. |
| `$range.<start>.<end>$` | List\<Integer> | Example: `$range.1.10$` → `[1, 2, ..., 10]`. `$range.1.$prop.max_level$` → resolved dynamically. |

#### External: `$economy.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$economy.purse$` | Double | Player's coin purse balance. |
| `$economy.purse.formatted$` | String | Formatted coin display (e.g., `"1.2K"`). |
| `$economy.bank$` | Double | Player's bank balance. |
| `$economy.total$` | Double | Purse + bank. |

#### External: `$quest.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$quest.<questId>.status$` | String | `"NOT_STARTED"`, `"ACTIVE"`, or `"COMPLETED"`. |
| `$quest.<questId>.objective.<objId>.progress$` | Integer | Current progress count for the objective. |
| `$quest.<questId>.objective.<objId>.required$` | Integer | Required count to complete. |
| `$quest.objective.<objId>.active$` | Boolean | Whether the objective is currently active. |

#### External: `$questboard.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$questboard.<boardId>.slot.<n>.quest_id$` | String | Quest ID in board slot N. |
| `$questboard.<boardId>.slot.<n>.name$` | String | Display name of the quest. |
| `$questboard.<boardId>.slot.<n>.status$` | String | `"empty"` if no quest, else the quest status. |

#### External: `$collection.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$collection.category_list$` | JSON String | Array of category IDs. |
| `$collection.item_list$` | JSON String | Array of collection IDs in the selected category. |
| `$collection.stage_list$` | JSON String | Array of stage numbers. |
| `$collection.detail_name$` | String | Display name of the selected collection. |
| `$collection.detail_icon$` | String | Material name of the collection icon. |
| `$collection.detail_count$` | Integer | Player's current count. |
| `$collection.detail_stage$` | Integer | Current stage (0 = not started). |
| `$collection.detail_max_stage$` | Integer | Total stages. |
| `$collection.detail_next_required$` | Integer | Count needed for next stage (`-1` if maxed). |

*Requires session props `selected_category` and `selected_collection` to be set via `variable set prop.selected_category <id>`.*

#### External: `$gui.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$gui.input.<id>.id$` | String | Valmora item ID or material name in the INPUT slot. |
| `$gui.input.<id>.material$` | String | Bukkit material name in the INPUT slot (`"null"` if empty). |
| `$gui.input.<id>.amount$` | Integer | Stack size in the INPUT slot. |
| `$gui.input.<id>.count$` | Integer | Number of filled INPUT slots with this component ID. |
| `$gui.input.<id>.available_enchants$` | List | Enchantment definitions applicable to the held item. |
| `$gui.viewed_skill.<field>$` | Various | Skill data for the viewed skill (level, xp, progress, etc.). |
| `$gui.enchanting.display_list$` | JSON String | Unified display list for the enchanting GUI. |
| `$gui.enchanting.has_selection$` | Boolean | Whether an enchant is currently selected. |

#### External: `$pet.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$pet.id$` | String | Active pet definition ID (`"none"` if no pet). |
| `$pet.name$` | String | Active pet display name (`"None"` if no pet). |
| `$pet.level$` | Integer | Active pet level. |
| `$pet.xp$` | Double | Active pet XP. |
| `$pet.max_xp$` | Integer | XP needed for next level. |
| `$pet.active$` | Boolean | Whether a pet is currently active. |

#### External: `$warp.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$warp.<warpId>.name$` | String | Display name of the warp. |
| `$warp.<warpId>.unlocked$` | Boolean | Whether the player has unlocked the warp. |

#### External: `$zone.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$zone.id$` | String | Current zone ID (`null` in wilderness). |
| `$zone.name$` | String | Zone display name (`<green>Wilderness` if none). |
| `$zone.pvp$` | Boolean | Whether PvP is enabled in the current zone. |

#### External: `$alchemy.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$alchemy.effects.count$` | Integer | Number of active (non-expired) alchemy effects. |
| `$alchemy.effects.list$` | JSON String | Array of effect objects (id, name, level, remaining, type, material, rarity, stats). |

#### External: `$progression.*$`

| Variable | Returns | Description |
|----------|---------|-------------|
| `$progression.<treeId>.tier$` | Integer | Highest unlocked tier. |
| `$progression.<treeId>.tier.next$` | Integer | Unlock cost of the next tier (`0` if none). |
| `$progression.<treeId>.tier.next.unlock_cost$` | Integer | Same as `.tier.next` (explicit form). |
| `$progression.<treeId>.<nodeId>.level$` | Integer | Current node level. |
| `$progression.<treeId>.<nodeId>.max_level$` | Integer | Max level for the node. |
| `$progression.<treeId>.<nodeId>.next_cost$` | Integer | Cost to level up. |
| `$progression.<treeId>.<nodeId>.unlocked$` | Boolean | Whether the node is unlocked. |

---

### Script Events (Complete Reference)

| Event | DSL Syntax | Module | Description |
|-------|------------|--------|-------------|
| `condition` | `condition <expression>` | Script | Throws `ConditionAbortException` if false, skipping remaining events and triggering fail-actions. |
| `give` | `give <MATERIAL>:<amount>` | Script | Gives items to the caster. `notify` sends a chat message. |
| `variable` | `variable <set\|add\|remove> <path> <value>` | Script | Sets/adds/removes profile variables (`player.var.*`) or GUI props (`prop.*`). |
| `tag` | `tag <add\|remove> <tagName>` | Script | Adds/removes a persistent profile tag. Fires `TagAddedEvent` Bukkit event on add. |
| `teleport` | `teleport warp:<id>` \| `teleport @look <N>` \| `teleport <x> <y> <z>` \| `teleport <w> <x> <y> <z>` | Script | Teleports the player. Blocked by zone `teleportation: false` flag. |
| `spawn_mob` | `spawn_mob <mobId> [<count>] [radius:<r>]` | Script | Spawns custom mobs near the caster. |
| `stat_modify` | `stat_modify <add\|set\|reset> <statId> [value]` | Script | Modifies base player stats. Value supports `$variable$`. |
| `foreach` | `foreach @all <event>` \| `foreach @nearby:<r> <event>` | Script | Executes an inner event for each targeted player. |
| `run_script` | `run_script <ticks> <times> <event...>` | Script | Schedules a repeating task. |
| `notify` | `notify <message> [category:<name>] [io:<type>] [key:value ...]` | Notify | Sends a typed notification. Categories: `info` (chat, default), `error` (actionbar). IO types: `chat`, `actionbar`, `title`, `subtitle`, `bossbar`, `sound`, `advancement`. |
| `notifyall` | `notifyall <message> [category:<name>] [io:<type>] [key:value ...]` | Notify | Same as `notify` but broadcasts to all online players. |
| `economy_add` | `economy_add <amount>` | Economy | Adds coins to the player's purse. Amount supports `$variable$`. |
| `economy_remove` | `economy_remove <amount>` | Economy | Removes coins from the player's purse. Amount supports `$variable$`. |
| `economy_deposit` | `economy_deposit <amount>` | Economy | Deposits coins from purse into bank. |
| `economy_withdraw` | `economy_withdraw <amount>` | Economy | Withdraws coins from bank into purse. |
| `economy_deposit_all` | `economy_deposit_all` | Economy | Deposits all purse coins into bank. |
| `point` | `point <category> <add\|set\|take> <amount>` | Points | Modifies per-player point counters. |
| `quest_start` | `quest_start <questId>` | Quest | Starts a quest for the player. |
| `quest_complete` | `quest_complete <questId>` | Quest | Completes a quest (triggers rewards). |
| `quest_cancel` | `quest_cancel <questId>` | Quest | Cancels a quest (no rewards). |
| `quest_fail` | `quest_fail <questId>` | Quest | Marks a quest as failed. |
| `objective_start` | `objective_start <questId> <objectiveId>` | Quest | Starts a specific quest objective. |
| `objective_delete` | `objective_delete <questId> <objectiveId>` | Quest | Removes an objective from tracking. |
| `journal` | `journal open` | Quest | Opens the quest journal GUI. |
| `quest_board_assign` | `quest_board_assign <boardId>` | Quest Board | Assigns a random quest to a board slot. |
| `quest_board_collect` | `quest_board_collect <boardId>` | Quest Board | Collects the reward for a completed board quest. |
| `slayer_start` | `slayer_start <slayerId> <tier>` | Slayer | Starts a slayer tier (deducts cost, tracks kills). |
| `warp_to` | `warp_to <warpId>` | Warp | Teleports the player to a registered warp. |
| `gui` | `gui <guiId>` | NPC | Opens a GUI from an NPC interaction. |
| `dialogue` | `dialogue <dialogueId>` | NPC | Starts an NPC dialogue. |
| `sound` | `sound <soundId>` | GUI | Plays a sound for the player. |
| `open_gui` | `open_gui <guiId>` | GUI | Opens a GUI. |
| `close` | `close` | GUI | Closes the current GUI. |
| `give_xp` | `give_xp <amount>` | GUI | Gives XP (used in enchanting contexts). |
| `enchant_apply` | `enchant_apply <slot> <enchantId> <level>` | GUI | Applies a Valmora enchantment. |
| `enchant_select` | `enchant_select <enchantId>` | GUI | Selects an enchant for preview. |
| `enchant_remove` | `enchant_remove <slot>` | GUI | Removes an enchantment. |
| `enchant_back` | `enchant_back` | GUI | Returns to the enchant catalog. |
| `gui_force_craft` | `gui_force_craft` | GUI | Forces a recipe match and consumption for the current GUI's machine. |
| `alchemy_brew_start` | `alchemy_brew_start <ingredient>` | GUI | Starts brewing. |
| `alchemy_brew` | `alchemy_brew` | GUI | Completes brewing. |
| `open_dialog_input` | `open_dialog_input <propKey> <message>` | GUI | Opens a text input dialog and stores result in `prop.<key>`. |
| `open_sign_input` | `open_sign_input <propKey>` | GUI | Opens a sign-style input and stores result in `prop.<key>`. |
| `progression_levelup` | `progression_levelup <treeId> <nodeId>` | Progression | Levels up a progression node. |
| `progression_unlock_tier` | `progression_unlock_tier <treeId>` | Progression | Unlocks the next tier of a progression tree. |
| `progression_reset` | `progression_reset <treeId>` | Progression | Resets all progress in a progression tree. |

### Event Options

| Option | Description |
|--------|-------------|
| `notify` | Sends a notification to the player on execution (behavior is event-specific — e.g., `give` sends a chat message, `notify` is the notification itself). |
| `delay:<ticks>` | Delays the event by N server ticks (20 ticks = 1 second). Uses `Bukkit.getScheduler().runTaskLater()`. |
| `conditions:<inline>` | Comma-separated inline condition tokens. If any is false, the event is skipped (not an exception — just no-op). |

---

## Examples

### Expression in a mechanic amount

```yaml
mechanics:
  - type: "DAMAGE"
    params:
      amount: "$player.stat.DAMAGE$ * 2 + floor($target.level$ / 2)"
      type: "MAGIC"
```

### Ability with expression conditions

```yaml
abilities:
  execute:
    name: "Execute"
    trigger: "RIGHT_CLICK"
    target-range: 10.0
    cooldown: 8.0
    mana-cost: 30.0
    conditions:
      - "$target.health$ < $target.max_health$ * 0.3"
    mechanics:
      - type: "DAMAGE"
        params:
          amount: "$player.stat.DAMAGE$ * 5"
          type: "TRUE"
```

### Quest with conditions, actions, and fail-actions

```yaml
quests:
  hunter_rank_1:
    name: "Novice Hunter"
    description:
      - "<gray>Prove yourself by slaying beasts."
    conditions:
      - "point reputation 10"
    on-start:
      - "tag add hunter_quest_active"
      - "notify <gold>Hunter Quest started! Slay 10 zombies. io:chat"
    objectives:
      kill_zombies:
        type: "ENTITY_KILL"
        target: "ZOMBIE"
        count: 10
        events:
          - "notify <gray>Killed 1 zombie. <gray>($player.var.kill_zombies$ / 10) io:actionbar"
    on-complete:
      - "tag remove hunter_quest_active"
      - "economy_add 500"
      - "give DIAMOND_SWORD:1 notify"
      - "point reputation add 15 delay:10 notify"
```

### GUI with condition-gated click actions

```yaml
components:
  A:
    item: "DIAMOND"
    name: "<aqua>Claim Reward"
    click:
      left:
        conditions:
          - "tag reward_claimed"
        fail-actions:
          - "give DIAMOND:1 notify"
          - "tag add reward_claimed"
      right:
        conditions:
          - "variable player.stat.HEALTH > 100"
        actions:
          - "notify <red>Your health is too high for this! io:actionbar"
```

### Dynamic GUI with PAGINATED components using variables

```yaml
components:
  L:
    type: "PAGINATED"
    list: "$player.skill.list$"
    iterator: "{skill}"
    states:
      default:
        item: "{skill.material}"
        name: "{skill.name}"
        lore:
          - "<gray>Level: <yellow>{skill.level}"
          - "<gray>XP: <yellow>{skill.xp}"
          - "<gray>Progress: <yellow>{skill.progress}%"
          - "<gray>Next: <yellow>{skill.xp_in_level}/{skill.xp_required}"
```

### Stat formula using player variables

```yaml
stats:
  DAMAGE: 25
  STRENGTH: 10
abilities:
  shield_bash:
    name: "Shield Bash"
    trigger: "RIGHT_CLICK"
    target-range: 5.0
    cooldown: 5.0
    mechanics:
      - type: "DAMAGE"
        params:
          amount: "$player.stat.DAMAGE$ * 0.5 + $player.stat.DEFENSE$ * 0.3"
          type: "MELEE"
      - type: "APPLY_EFFECT"
        params:
          effect: "slowness"
          duration: "$player.stat.DEFENSE$ / 50"
          amplifier: 2
          target: "@target"
```

### Using `run_script` for a damage-over-time effect

```yaml
mechanics:
  - type: "DAMAGE"
    params:
      amount: 10
      type: "MAGIC"
      ticks: 5
      interval: 20
  - type: "run_script"
    params:
      events:
        - "damage $caster 5 MAGIC delay:20"
```

### Range variable for PAGINATED slot rendering

```yaml
list: "$range.1.$player.skill.mining.max_level$"
```

This generates a list of integers `[1, 2, ..., maxLevel]` dynamically,
useful for rendering level-up trees or tier displays.

### Variable event with interpolation

```yaml
on-complete:
  - "variable set player.var.last_quest_completed $system.time$"
  - "variable add player.var.quest_streak 1"
  - "notify <gray>Quest streak: <yellow>$player.var.quest_streak$"
```

Note: the `notify` message uses `$player.var.quest_streak$` which is
resolved via template substitution, displaying the integer value (e.g.,
`5`, not `5.0`).
