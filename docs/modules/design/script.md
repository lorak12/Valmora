# Script Module — Design & Code

## Overview

The **Script module** (`ScriptModule`, id: `"script"`) is the foundational DSL
(engine) of the Valmora RPG plugin. It is registered **first** in
`Valmora.java:188` and enabled first in the module enablement order
(`Valmora.java:125–225`), because every other module may depend on it.

The script engine provides four intertwined subsystems:

1. **Expression parser & evaluator** — a recursive-descent parser that turns
   strings like `"$player.stat.HEALTH$ * 0.3 + floor($target.level$ / 2)"`
   into an AST that can be evaluated against an `ExecutionContext`.
2. **Variable resolver** — resolves `$namespace.path.subpath$` tokens by
   delegating to registered `VariableProvider` implementations.
3. **Condition parser** — turns condition strings (tag checks, expression
   comparisons, zone/quest checks, etc.) into `Condition` objects that return
   booleans. Multiple conditions are combined with AND logic via
   `ConditionGroup`.
4. **Event DSL parser** — parses side-effect strings like
   `give DIAMOND:5 notify delay:20` into `CompiledEvent` objects that can be
   executed against an `ExecutionContext`.

All four subsystems are wired through `ExecutionContext`
(`api/execution/ExecutionContext.java:16`), the context object that carries the
caster, target, location, parameters, variable resolver, and tag service for a
single execution invocation.

### ExecutionContext Lifetime

`ExecutionContext` is **not thread-safe** and must not be stored beyond the scope
of a single mechanic/event invocation (per AGENTS.md §7.3). It is created
fresh for each ability fire
(`AbilityExecutor.java:53`), each GUI lifecycle
(`GuiExecutionContext`, `GuiModule.java:82/112/145`), each quest objective
completion (`QuestManager.java:217`), and so on.

---

## Code Structure

The module lives in `src/main/java/org/nakii/valmora/module/script/` and is
organized into six sub-packages. Below is every file, its role, and key line
references.

### Core (root package)

| File | Lines | Role |
|------|-------|------|
| `ScriptModule.java` | 24–135 | Module entry point. Creates `VariableResolverImpl`, `ExpressionParser`, `ExpressionEvaluatorImpl`, `ConditionParser`, `EventParser`. Registers 9 built-in variable providers and 10 built-in event factories. Exposes all via getters. Cleared on `onDisable`. |

### Sub-package: `expression/`

| File | Lines | Role |
|------|-------|------|
| `ExpressionEvaluatorImpl.java` | 10–22 | Default `ExpressionEvaluator`. Delegates to `ExpressionParser.parse()` then `Expression.evaluate()`. |
| `ExpressionParser.java` | 19–187 | Recursive-descent parser. Tokenizes via regex, then parses with precedence: ternary → logical-or → logical-and → comparison → addition → multiplication → primary. Supports `$var$`, numbers, `"strings"`, booleans, arithmetic, comparison, logical operators, ternary, and function calls. |
| `expression/nodes/LiteralNode.java` | 9–15 | AST node for constant values (Number, String, Boolean, null). Record. |
| `expression/nodes/VariableNode.java` | 10–16 | AST node for `$variable$` references. Delegates to `VariableResolver.resolve()` at evaluation time. Record. |
| `expression/nodes/BinaryOpNode.java` | 11–55 | AST node for binary operations. Handles `+`, `-`, `*`, `/`, `==`, `!=`, `>`, `<`, `>=`, `<=`, `and`/`&&`, `or`/`\|\|`. Numeric comparison uses 0.0001 epsilon for `==`/`!=`. Falls back to `Objects.equals` for non-numeric `==`/`!=`. Record. |
| `expression/nodes/TernaryNode.java` | 9–17 | AST node for `condition ? trueVal : falseVal`. Record. |
| `expression/nodes/FunctionNode.java` | 12–57 | AST node for math functions: `floor`, `ceil`, `round`, `abs`, `sqrt`, `log10`, `log`, `pow`, `min`, `max`. All arguments coerced to double. Record. |

### Sub-package: `variable/`

| File | Lines | Role |
|------|-------|------|
| `VariableProvider.java` | 8–22 | Interface. `getNamespace()` + `resolve(String[] path, ExecutionContext)`. |
| `VariableResolverImpl.java` | 11–80 | Default `VariableResolver`. Strips `$`, splits on `.`, delegates to provider. Falls back to `GuiExecutionContext` loop vars and `ConfigurationSection` params. |
| `providers/PlayerVariableProvider.java` | 19–168 | Namespace `"player"`. Resolves name, world, ping, biome, stats, skills, hp, max_hp, health_percent, missing_hp_percent, mana, max_mana, profile, last_damage, weapon_damage, var.* |
| `providers/SystemVariableProvider.java` | 9–27 | Namespace `"system"`. Resolves `time` (Unix ms). |
| `providers/WorldVariableProvider.java` | 9–31 | Namespace `"world"`. Resolves `name`, `dimension`. |
| `providers/ServerVariableProvider.java` | 7–25 | Namespace `"server"`. Resolves `online`, `max_players`, `motd`. |
| `providers/PropVariableProvider.java` | 8–32 | Namespace `"prop"`. Resolves GUI session properties (`GuiExecutionContext`). |
| `providers/ParamVariableProvider.java` | 7–31 | Namespace `"param"`. Resolves values from `ExecutionContext.getParams()`. |
| `providers/RangeVariableProvider.java` | 11–69 | Namespace `"range"`. Generates `List<Integer>` via `range.<start>.<end>` where end can be a dynamic variable. |
| `providers/TimeVariableProvider.java` | 9–40 | Namespace `"time"`. Resolves season, phase, hour, minute, day, year, etc. from `TimeManager`. |
| `providers/TargetVariableProvider.java` | 20–45 | Namespace `"target"`. Resolves target entity type, health, max_health, level, name. |

### Sub-package: `condition/`

| File | Lines | Role |
|------|-------|------|
| `ConditionParser.java` | 10–111 | Parses condition strings. Supports `!` negation prefix. Keywords: `tag`, `health`, `hunger`, `location`, `zone`, `variable`, `objective`, `quest`, `point`. Falls back to expression. `parseList()` → AND group. `parseInlineList()` for comma-separated inline tokens. |
| `ConditionGroup.java` | 11–18 | `record` implementing `Condition` with AND logic over a list. Empty group = true. |
| `ExpressionCondition.java` | 10–17 | Wraps an `Expression`, returns true only if result is `Boolean(true)`. Record. |
| `TagCondition.java` | 11–21 | Checks if the player's active profile has a given tag. Record. |
| `HealthCondition.java` | 7–17 | Checks `player.getHealth() >= required`. Record. |
| `HungerCondition.java` | 7–17 | Checks `player.getFoodLevel() >= required`. Record. |
| `LocationCondition.java` | 9–41 | Checks proximity to a `x;y;z;world` location within a radius. Parses the `x;y;z;world` DSL format. Record. |
| `ZoneCondition.java` | 9–24 | Checks if player's current zone ID matches. Reads from `PlayerState.getCurrentZoneId()`. Record. |
| `QuestStatusCondition.java` | 10–26 | Checks `QuestManager.getStatus()` equals expected. Requires `QuestManager` non-null. Record. |
| `ObjectiveActiveCondition.java` | 10–25 | Checks `QuestManager.isObjectiveActive()`. Record. |
| `VariableCondition.java` | 6–37 | Resolves `$path$` and compares to a literal value (numeric or string). Record. |

> **Note:** `PointCondition` is not in the script package. It lives at
> `module/quest/points/PointCondition.java:8` and is instantiated directly by
> `ConditionParser.java:79`.

### Sub-package: `event/`

| File | Lines | Role |
|------|-------|------|
| `EventFactory.java` | 8–22 | Interface. `getName()` + `compile(String[] args, EventOptions)` → `CompiledEvent`. |
| `EventParser.java` | 14–112 | Parses raw event strings. Splits on spaces, extracts `notify` and `delay:<n>` options, optionally `conditions:` token. Looks up factory by name. Wraps with inline condition guard if present. Delays execution via `Bukkit.getScheduler().runTaskLater()`. |
| `EventOptions.java` | 6–8 | `record(int delay, boolean notifyPlayer)`. Has `DEFAULT` constant. |
| `ConditionAbortException.java` | 3–7 | `RuntimeException` with suppressed stack trace. Thrown by `ConditionEvent` and GUI event factories to abort the remaining action list and trigger fail-actions. Intentionally not caught in `EventParser.parseList()`. |
| `tag/TagServiceImpl.java` | 14–54 | Implementation of `TagService`. Reads/writes `ValmoraProfile.getTags()`. Fires `TagAddedEvent` (Bukkit event) on add. |

### Sub-package: `event/impl/`

| File | Lines | Event Name | DSL |
|------|-------|------------|-----|
| `ConditionEvent.java` | 15–40 | `condition` | `condition <expression>` — Throws `ConditionAbortException` when false. |
| `GiveEvent.java` | 13–48 | `give` | `give <MATERIAL>:<amount>` — Gives items to caster. `notify` sends a chat message. |
| `VariableEvent.java` | 14–92 | `variable` | `variable <set\|add\|remove> <path> <value>` — Sets/adds/removes profile variables (`player.var.X`) or GUI session props (`prop.X`). Supports `$var$` interpolation in value. |
| `TagEvent.java` | 16–51 | `tag` | `tag <add\|remove> <tagName>` — Adds/removes tag on profile. Fires `TagAddedEvent` on add. |
| `TeleportEventFactory.java` | 25–97 | `teleport` | `teleport warp:<id>` / `teleport @look <blocks>` / `teleport <x> <y> <z>` / `teleport <world> <x> <y> <z>` — Checks zone teleport flags. Uses `player.teleportAsync()`. |
| `SpawnMobEventFactory.java` | 21–77 | `spawn_mob` | `spawn_mob <mob_id> [count] [radius:<r>]` — Spawns custom mobs near caster location. |
| `StatModifyEventFactory.java` | 21–65 | `stat_modify` | `stat_modify <add\|set\|reset> <stat_id> [value]` — Modifies base stats. Value supports `$variable$` interpolation. |
| `ForeachEventFactory.java` | 26–85 | `foreach` | `foreach @all <event>` / `foreach @nearby:<radius> <event>` — Creates a fresh `SimpleExecutionContext` per targeted player. |
| `RunScriptEventFactory.java` | 23–70 | `run_script` | `run_script <interval_ticks> <times> <event...>` — Schedules inner event to fire repeatedly via `Bukkit.getScheduler().runTaskTimer()`. |

---

## Architecture & Key Classes

### 4.1 Startup Wiring

`ScriptModule.onEnable()`
(`ScriptModule.java:41–71`) creates all four sub-components and registers
them. The module is instantiated at `Valmora.java:160` and registered at
`Valmora.java:188` (first in registration order). On `onDisable()`
(`ScriptModule.java:82–86`), both registries are cleared, ensuring clean
state on hot-reload.

### 4.2 Expression Evaluation Pipeline

```
YAML string
  │
  ├─ ExpressionParser.parse(raw)          // ExpressionParser.java:35
  │     └─ Tokenizes → recursive descent → Expression AST
  │     └─ Nodes: LiteralNode, VariableNode, BinaryOpNode,
  │              TernaryNode, FunctionNode
  │
  └─ Expression.evaluate(context)         // each node implements this
        └─ VariableNode → VariableResolver.resolve(path, context)
              └─ VariableResolverImpl → delegates to VariableProvider
```

`ExpressionEvaluator.evaluate(raw, context)`
(`ExpressionEvaluatorImpl.java:19`) is a convenience that parses + evaluates
in one step. Performance-critical code paths use `ExpressionParser.parse()`
once at load time and reuse the compiled `Expression` object (see
`ConditionEvent.java:32`, `ProgressionManager.java:193`).

### 4.3 Variable Resolution Pipeline

```
"player.stat.HEALTH"  (without $ delimiters)
  │
  └─ VariableResolverImpl.resolve(path, context)    // VariableResolverImpl.java:20
        ├─ Strips surrounding $ if present
        ├─ Splits on "." → namespace="player", remaining=["stat","HEALTH"]
        ├─ Looks up provider in variableProviderRegistry
        └─ Delegates: provider.resolve(remainingPath, context)
              ├─ PlayerVariableProvider → profile.getStatManager().getStat("health")
              └─ Fallback (if no provider):
                    1. GuiExecutionContext loopVars
                    2. ExecutionContext params (ConfigurationSection)
```

The `$...$` delimiters are part of the expression parser's token pattern
(`ExpressionParser.java:22`), but the `VariableResolver.resolve()` method
strips them if present.

**Template resolution** (`VariableResolver.java:22–47`): The default
`resolveTemplate()` method scans a string for `$...$` tokens and replaces
each with its resolved value. Numbers with no fractional part render as
integers (e.g., `100.0` → `"100"`).

### 4.4 Condition Evaluation Pipeline

```
"tag quest_started"  or  "$player.stat.HEALTH$ > 50"
  │
  └─ ConditionParser.parse(raw)            // ConditionParser.java:23
        ├─ Checks keyword prefix (tag, health, hunger, location, zone,
        │   variable, objective, quest, point, !)
        └─ Falls back to ExpressionCondition(expressionParser.parse(clean))

ConditionParser.parseList(list)            // ConditionParser.java:90
  └─ Returns ConditionGroup (AND logic, empty = true)

ConditionParser.parseInlineList(raw)      // ConditionParser.java:102
  └─ Splits on commas, parses each token
```

Conditions are used heavily in:
- Item abilities (`AbilityExecutor.java:98–106`)
- GUI lifecycle blocks (`GuiDefinitionParser.java:167`)
- GUI click handlers (`ClickHandlerParser.java:22`)
- GUI component rendering states (`GuiRenderer.java:279`)
- NPC hologram visibility (`NpcManager.java:188`)
- GUI event conditions

### 4.5 Event Execution Pipeline

```
"give DIAMOND:5 notify delay:20"
  │
  └─ EventParser.parse(raw)                // EventParser.java:27
        ├─ Splits on spaces
        ├─ Extracts options: notify (EventParser.java:43-44)
        │                   delay:N (EventParser.java:45-48)
        │                   conditions:X (EventParser.java:49-54)
        ├─ Looks up EventFactory by name
        ├─ Compiles: factory.compile(args, options)
        ├─ (Optional) Wraps with inline condition guard
        └─ (Optional) Wraps with delay via runTaskLater

EventParser.parseList(list)               // EventParser.java:98
  └─ Returns single CompiledEvent executing all in sequence
```

**ConditionAbortException handling (`EventParser.java:104–110`)**: The
exception is intentionally **not caught** in `parseList()`. It propagates to
callers (GuiModule, GuiListener, QuestManager, etc.) which catch it to
trigger `fail-actions`. This is the mechanism behind GUI conditional
click handling and quest objective conditions.

### 4.6 Tag Service

`TagService`
(`api/scripting/TagService.java:6`) provides `hasTag`, `addTag`,
`removeTag`. The implementation (`TagServiceImpl.java:14`) reads/writes
`ValmoraProfile.getTags()` — a `Set<String>` persisted in the database
(`valmora_profiles` table, see VALMORA_DOCUMENTATION.md §12).
`addTag()` fires a Bukkit `TagAddedEvent`
(`TagAddedEvent.java`), allowing other modules to listen for tag changes.

The `ExecutionContext.getTagService()` method
(`ExecutionContext.java:51`) creates a **new** `TagServiceImpl` per call
(`SimpleExecutionContext.java:58`), because each service needs a reference to
the current context.

### 4.7 GuiExecutionContext

`GuiExecutionContext`
(`module/gui/GuiExecutionContext.java:11`) extends
`SimpleExecutionContext` and adds:
- `GuiSession session` — the GUI session being rendered
- `Map<String, Object> loopVars` — per-iteration variables for PAGINATED
  components

It is constructed whenever GUI lifecycle events or click actions are
evaluated (`GuiModule.java:82/112/145`, `GuiListener.java:122/250/348/419`,
`GuiRenderer.java:262/323/453`). The `VariableResolverImpl` checks
`instanceof GuiExecutionContext` to access loop vars as a fallback
(`VariableResolverImpl.java:39`).

---

## Configuration

The Script module itself loads no YAML configuration files — it is
entirely programmatic. However, it **defines the DSL grammar** that all other
modules' YAML configs use. The grammar is documented in detail in the
user-facing documentation (`docs/modules/user/script.md`) and in
VALMORA_DOCUMENTATION.md §32–§33. Below is the grammar summary relevant to
the code.

### Variable Syntax

```
$namespace.path.subpath$
```

- Whitespace-free tokens separated by `.`
- Namespace must match a registered `VariableProvider.getNamespace()`
- Resolution falls through to params/loop-vars/GUI-props as fallbacks
- Numbers render as integers in template substitution if whole

### Condition Syntax

```
[!] <keyword> <args>          — keyword conditions
[!] <expression>               — expression conditions (anything not matching a keyword)
```

Keywords: `tag`, `health`, `hunger`, `location`, `zone`, `variable`,
`objective`, `quest`, `point`. Negation via `!` prefix wraps the inner
condition with `!inner.evaluate()`.

Lists combine with AND logic (`ConditionGroup`). Inline comma-separated
tokens use `parseInlineList()`.

### Expression Syntax

```
<number> | "string" | true | false | $variable$ | <func>(<args>)
```

Operators (precedence high→low):
1. Primary (literals, variables, grouped, function calls)
2. `*` `/`
3. `+` `-`
4. `== != > < >= <=`
5. `and` `&&` / `or` `||`
6. Ternary `? :`

Functions: `floor`, `ceil`, `round`, `abs`, `sqrt`, `log10`, `log`, `pow`,
`min`, `max` — all coerce arguments to `double`.

### Event DSL Syntax

```
<eventName> <arg1> [<arg2> ...] [notify] [delay:<ticks>] [conditions:<inline>]
```

Options are parsed out before args are passed to the `EventFactory`.

---

## Data Model / Persistence

The Script module itself persists **no data** to the database. All state it
manipulates belongs to other modules:

- **Variables**: `VariableEvent` writes to
  `ValmoraProfile.getVariables()` (`PlayerState` section in
  VALMORA_DOCUMENTATION.md §12), which is a `Map<String, Object>` persisted as
  JSON in the `valmora_profiles.player_state` column.
- **Tags**: `TagEvent` / `TagServiceImpl` writes to
  `ValmoraProfile.getTags()` (`Set<String>`), also persisted as JSON in
  `valmora_profiles.player_state`.
- **Stats**: `StatModifyEventFactory` delegates to
  `StatManager.addStat()` / `setStat()` / `resetStat()`, which modifies the
  profile's base stats (persisted in `valmora_profiles.stats` JSON column).

The module registries (`variableProviderRegistry`, `eventFactoryRegistry`)
are **not** persisted — they are rebuilt from code in `onEnable()`.

### Hot-Reload Safety

`ScriptModule.onEnable()`
(`ScriptModule.java:41–71`) re-creates all components from scratch on every
call, making it fully idempotent. `onDisable()`
(`ScriptModule.java:82–86`) clears both registries. This ensures `/valmora
reload` (which calls `ModuleManager.reloadModules()`,
`Valmora.java:51`) cleanly resets all state.

Note: `VariableResolverImpl`, `ExpressionParser`, etc. are **not** registered
as ReloadableModule instances themselves — they are plain objects held as
fields on `ScriptModule` and re-created on each `onEnable()`.

---

## API Exposed

`ScriptModule` exposes the following to `ValmoraAPI.getInstance().getScriptModule()`:

| Method | Returns | Lines |
|--------|---------|-------|
| `getVariableProviderRegistry()` | `Registry<VariableProvider>` | `ScriptModule.java:101` |
| `getEventFactoryRegistry()` | `Registry<EventFactory>` | `ScriptModule.java:108` |
| `getVariableResolver()` | `VariableResolver` | `ScriptModule.java:112` |
| `getExpressionParser()` | `ExpressionParser` | `ScriptModule.java:120` |
| `getExpressionEvaluator()` | `ExpressionEvaluator` | `ScriptModule.java:124` |
| `getConditionParser()` | `ConditionParser` | `ScriptModule.java:128` |
| `getEventParser()` | `EventParser` | `ScriptModule.java:132` |
| `getValmora()` | `Valmora` | `ScriptModule.java:116` |

Key API interfaces:
- `VariableProvider` (`variable/VariableProvider.java:8`)
- `VariableResolver` (`api/scripting/VariableResolver.java:8`)
  — includes default `resolveTemplate()`
- `ExpressionEvaluator` (`api/scripting/ExpressionEvaluator.java:9`)
- `Expression` (`api/scripting/Expression.java:8`)
- `Condition` (`api/scripting/Condition.java:8`)
- `TagService` (`api/scripting/TagService.java:6`)
- `CompiledEvent` (`api/scripting/CompiledEvent.java:8`)
- `EventFactory` (`event/EventFactory.java:8`)
- `ExecutionContext` (`api/execution/ExecutionContext.java:16`)
  — includes default `resolveDouble()`, `resolveInt()`, `resolveString()` helpers
- `SimpleExecutionContext` (`api/execution/SimpleExecutionContext.java:13`)

---

## Dependencies & Consumers

ScriptModule is the **most depended-on module**. It loads first and is
accessed via `ValmoraAPI.getInstance().getScriptModule()` or
`Valmora.getScriptModule()` throughout the codebase.

### Modules that register VariableProviders

| Module | On | Namespace | Lines |
|--------|----|-----------|-------|
| **Built-in** | `ScriptModule.onEnable` | `player`, `system`, `world`, `server`, `prop`, `param`, `range`, `time`, `target` | `ScriptModule.java:51–59` |
| `EconomyModule` | `onEnable` | `economy` | `EconomyModule.java:56` |
| `GuiModule` | `onEnable` | `gui` | `GuiModule.java:40` |
| `CollectionModule` | `onEnable` | `collection` | `CollectionModule.java:22` |
| `ZoneModule` | `onEnable` | `zone` | `ZoneModule.java:31` |
| `WarpModule` | `onEnable` | `warp` | `WarpModule.java:23` |
| `QuestModule` | `onEnable` | `quest`, `questboard` | `QuestModule.java:54,60` |
| `PointsModule` | `onEnable` | `point` | `PointsModule.java:20` |
| `PetModule` | `onEnable` | `pet` | `PetModule.java:48` |
| `AlchemyModule` | `onEnable` | `alchemy` | `AlchemyModule.java:54` |
| `ProgressionModule` | `onEnable` | `progression` | `ProgressionModule.java:36` |

### Modules that register EventFactories

| Module | On | Events Registered | Lines |
|--------|----|-------------------|-------|
| **Built-in** | `ScriptModule.onEnable` | `condition`, `give`, `variable`, `tag`, `teleport`, `spawn_mob`, `stat_modify`, `foreach`, `run_script` | `ScriptModule.java:62–70` |
| `NotifyModule` | `onEnable` | `notify`, `notifyall` | `NotifyModule.java:29–30` |
| `EconomyModule` | `onEnable` | `economy_add`, `economy_deposit`, `economy_withdraw`, `economy_deposit_all`, `economy_remove` | `EconomyModule.java:58–61` |
| `GuiModule` | `onEnable` | `sound`, `open_gui`, `close`, `give_xp`, `enchant_apply`, `enchant_select`, `enchant_remove`, `enchant_back`, `gui_force_craft`, `alchemy_brew_start`, `alchemy_brew`, `open_dialog_input`, `open_sign_input` | `GuiModule.java:41–53` |
| `NpcModule` | `onEnable` | `dialogue`, `gui` | `NpcModule.java:30–31` |
| `QuestModule` | `onEnable` | `quest_start`, `quest_complete`, `quest_cancel`, `quest_fail`, `objective_start`, `objective_delete` | `QuestModule.java:53` |
| `QuestModule` (journal) | `onEnable` | `journal` | `QuestModule.java:61` |
| `QuestModule` (board) | `onEnable` | `quest_board_assign`, `quest_board_collect` | `QuestModule.java:59` |
| `PointsModule` | `onEnable` | `point` | `PointsModule.java:19` |
| `SlayerModule` | `onEnable` | `slayer_start` | `SlayerModule.java:32` |
| `WarpModule` | `onEnable` | `warp_to` | `WarpModule.java:22` |
| `ProgressionModule` | `onEnable` | `progression_levelup`, `progression_unlock_tier`, `progression_reset` | `ProgressionModule.java:37` |

### Modules that consume script engine services (read-only)

| Consumer | Service Used | How |
|----------|-------------|-----|
| `AbilityExecutor` | ExpressionEvaluator | Evaluates ability condition strings | `AbilityExecutor.java:100` |
| `ScriptMechanic` | EventParser | Parses `events` param from `SCRIPT` mechanic, executes list | `ScriptMechanic.java:22–25` |
| `ValmoraCommand` | VariableResolver, VariableProviderRegistry | `/valmora variable get <path>` command | `ValmoraCommand.java:78,102` |
| `GuiDefinitionParser` | ConditionParser, EventParser | Parses `on-open`, `on-update`, `on-close` condition/action/fail-action blocks | `GuiDefinitionParser.java:167–169` |
| `ClickHandlerParser` | ConditionParser, EventParser | Parses click handler `conditions`, `actions`, `fail-actions` | `ClickHandlerParser.java:16,22–24` |
| `GuiRenderer` | VariableResolver, ConditionParser | Resolves `$variable$` in component titles/lore; evaluates state conditions | `GuiRenderer.java:279,322,452` |
| `QuestManager` | EventParser, ConditionParser | Executes objective `events`; parses quest conditions | `QuestManager.java:217,304` |
| `DialogueManager` | EventParser, ConditionParser | Executes dialogue node/choice events and conditions | `DialogueManager.java:127,179,186,328,448` |
| `PlayerHiderManager` | ConditionParser | Parses player-hider conditions | `PlayerHiderManager.java:75` |
| `ZoneListener` | EventParser | Executes zone `enter-actions` and `exit-actions` | `ZoneListener.java:81,91` |
| `SlayerListener` | EventParser | Executes slayer tier `completion-events` | `SlayerListener.java:103` |
| `SkillDefinitionParser` | EventParser | Parses `rewards.per-level` and `rewards.milestones` | `SkillDefinitionParser.java:41,50` |
| `RecipeDefinitionParser` | EventParser | Parses `on-craft` event list | `RecipeDefinitionParser.java:73` |
| `AnvilMachineHandler` | EventParser | Parses single `on-craft` script string into event | `AnvilMachineHandler.java:95` |
| `CollectionListener` | EventParser | Executes collection `rewards` list | `CollectionListener.java:109` |
| `CalendarEventModule` | EventParser | Parses `on-start`, `on-end`, `recurring-daily` | `CalendarEventModule.java:111` |
| `HudItemModule` | EventParser | Parses `on-right-click`, `on-left-click` event lists | `HudItemModule.java:130` |
| `PetModule` | EventParser | Executes pet events and parses pet action scripts | `PetModule.java:210,237` |
| `ProgressionManager` | ExpressionParser | Parses cost-curve expressions (e.g., `"5 * $level$ * $level$"`) | `ProgressionManager.java:193` |
| `NpcManager` | ConditionParser, EventParser | Parses NPC hologram conditions and right/left-click events | `NpcManager.java:188,357,366` |
| `ScoreboardUI` | VariableResolver | Resolves `$variable$` in scoreboard title/templates | `ScoreboardUI.java:226,228` |
| `ActionBarUI` | VariableResolver | Resolves `$variable$` in action bar template | `ActionBarUI.java:59` |
| `ExpressionCondition` | Expression | Wraps compiled expressions for condition evaluation | `ExpressionCondition.java` |

### Economy module sub-consumers

The EconomyModule's event factories use `VariableResolver`
directly within their compiled lambdas:
`EconomyAddEventFactory.java:33`, `EconomyDepositEventFactory.java:68`,
`EconomyRemoveEventFactory.java:33`, `EconomyWithdrawEventFactory.java:72`.

### GUI module sub-consumers

Several GUI event factories cast `ExecutionContext` to
`GuiExecutionContext` to access `GuiSession` props, then use
`VariableResolver.resolve()` for template substitution:
`OpenGuiEventFactory.java:38`, `EnchantApplyEventFactory.java:64`,
`EnchantSelectEventFactory.java:47`, `EnchantRemoveEventFactory.java:59`.

---

## Unfinished Things / TODOs

1. **Parser error handling is silent** — `ExpressionParser.parse()`
   (`ExpressionParser.java:45–51`) catches all exceptions and returns a
   `LiteralNode(null)` on parse failure. No logging or error reporting. This
   means malformed expressions silently evaluate to `null`, making debugging
   difficult. Same pattern in `ConditionParser` which falls back to
   `ExpressionCondition` with the same silently-failing parser.

2. **EventParser token splitting is naive** — `EventParser.parse()` splits
   on raw spaces (`EventParser.java:30`). Event arguments containing spaces
   (e.g., item names with spaces, or quoted strings) are not supported. The
   `GiveEvent` handles `Material:amount` but cannot handle display names.

3. **VariableEvent path scoping is limited** — `VariableEvent`
   (`VariableEvent.java:54`) only supports `player.var.*` and `prop.*`
   paths. Other player-scoped variable namespaces are not writable via the
   DSL. The `rawValue` interpolation only handles single `$variable$` tokens
   — no expression evaluation (unlike `resolveDouble` on `ExecutionContext`).

4. **No variable setter for stat-based or other module variables** — The
   `stat_modify` event can add/set/reset stats but cannot set arbitrary
   profile variables. There is no generic `variable set player.stat.*` —
   stat modification goes through `StatManager` directly.

5. **PointCondition is cross-module** — `ConditionParser` imports
   `PointCondition` from `module/quest/points/`
   (`ConditionParser.java:4`), creating a dependency from the script module
   to the quest module. Since quest loads after script
   (`Valmora.java:210`), this works at runtime but creates a fragile
   compile-time coupling in the core module.

6. **`ForeachEventFactory` creates throwaway ExecutionContexts** —
   `ForeachEventFactory`
   (`ForeachEventFactory.java:82–84`) creates a new
   `SimpleExecutionContext` per player with a fresh `YamlConfiguration` as
   params. This means inner events in a `foreach` cannot access the original
   params or the original caster's identity (the caster becomes the
   per-target player).

7. **No caching of parsed expressions in YAML-driven systems** —
   `GuiRenderer.java:279` calls `getConditionParser().parse(condStr)`
   **every render tick** for PAGINATED component states. Similarly,
   `GuiDefinitionParser` compiles conditions/actions once at load time
   (correct), but `GuiRenderer` re-parses per-render for dynamic state
   conditions. This is a performance concern for GUIs with update intervals.

8. **`RunScriptEventFactory` captures ExecutionContext in closures** —
   `RunScriptEventFactory`
   (`RunScriptEventFactory.java:58–68`) captures the `context` in a
   repeating `BukkitTask` lambda. If the original player logs out or the
   context becomes invalid, the task continues to reference stale state. The
   task is not tracked or cancelled on context loss.

9. **`SimpleExecutionContext` creates a new `TagServiceImpl` per call** —
   `SimpleExecutionContext.getTagService()`
   (`SimpleExecutionContext.java:57–59`) returns a new `TagServiceImpl`
   each time, creating a minor allocation overhead in any mechanic that
   calls it more than once.

10. **Token pattern regex may mis-tokenize** — `ExpressionParser.TOKEN_PATTERN`
    (`ExpressionParser.java:21–30`) does not handle negative number literals
    (e.g., `-5` is tokenized as `-` then `5`). The parser handles this via
    subtraction, but a standalone negative literal at the start of an
    expression or after `(` will fail. This is an edge case not covered by
    tests.

11. **`$player.missing_hp_percent$` returns Integer, not Double** —
    `PlayerVariableProvider.java:144` returns
    `100 - (int)(...)` which is an `int`, not a `double`. When used in
    expressions, `BinaryOpNode` will still handle it (via `Number`
    interface), but the type inconsistency with `$player.hp$` (which returns
    `double`) could cause subtle issues with `resolveTemplate()` formatting.

---

## Possible Improvements / Changes

1. **Parse-time error reporting** — Add a `ParseException` or result type
   to `ExpressionParser.parse()` and `EventParser.parse()` so consumers can
   report malformed strings in YAML configs with file paths and line numbers.
   At minimum, log warnings (as `EventParser` already does for unknown events
   at `EventParser.java:66`).

2. **Space-safe event argument parsing** — Use a proper tokenizer for the
   event DSL that respects quoted strings, or allow multi-argument event
   factories to receive a pre-split array with quote awareness.

3. **Expression caching layer** — Provide a `CachedEvaluator` or
   `ExpressionCache` that memoizes `parse()` results by string, keyed by the
   loading module's lifecycle. This would benefit `GuiRenderer` state
   conditions without changing call-site code.

4. **Compile-time variable validation** — A `VariableProvider` could expose
   the set of valid sub-paths it supports, enabling compile-time warnings for
   misspelled variables (e.g., `$player.stt.HEALTH$` instead of
   `$player.stat.HEALTH$`).

5. **Expression-based VariableEvent values** — Allow `variable set
   player.var.X $player.stat.DAMAGE$ * 2` (full expression evaluation for
   the value) rather than only `$variable$` interpolation. This would unify
   the semantics with `ExecutionContext.resolveDouble()`.

6. **PointCondition decoupling** — Move `point` condition parsing into a
   factory or service-loaded `Condition` parser to avoid the script module
   depending on `module/quest/points`. Alternatively, make
   `ConditionParser` extensible (registry of condition keyword → factory)
   so other modules can register condition keywords without modifying the
   core parser.

7. **ForeachEventFactory context preservation** — Allow `foreach` to
   optionally inherit the original caster's params or provide a way to
   reference the original caster (e.g., `@self` within the inner event).

8. **TagService caching** — Cache the `TagServiceImpl` instance on
   `SimpleExecutionContext` instead of allocating a new one per call.

9. **Unit test coverage for all condition types and event factories** —
   Currently only `ExpressionParser` (ExpressionTest),
   `RangeVariableProvider` (RangeVariableProviderTest), and
   `TimeVariableProvider` (TimeVariableProviderTest) have tests. The 11
   condition implementations, the 10 event factories, and
   `VariableResolverImpl` have zero test coverage.

10. **Negative literal support in parser** — Handle unary minus for
    standalone negative number literals in `parsePrimary()`.
