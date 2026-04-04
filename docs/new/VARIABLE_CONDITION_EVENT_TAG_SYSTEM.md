VALMORA VARIABLE, CONDITION, EVENT & TAG SYSTEM
Full Architecture & Implementation Plan

1. HIGH-LEVEL SYSTEM OVERVIEW

This system introduces a fully YAML-driven scripting layer built on top of the existing Execution Engine.

It consists of four tightly integrated subsystems:

1. Variable System
   Resolves dynamic values from multiple sources (player, system, world)
   Supports expressions and nested paths
   Fully integrated with ExecutionContext
2. Expression Engine
   Parses and evaluates mathematical and logical expressions
   Compiled into AST at load time
   Supports variables, arithmetic, comparison, ternary
3. Condition System
   Evaluates boolean expressions
   Used across all modules (items, mobs, UI, future quests)
4. Event System
   DSL → Compiled → Mechanic pipeline
   Executes actions using ExecutionContext
   Supports chaining, delay, notify
5. Tag System
   Lightweight boolean flags on players
   Used in conditions and events
   Core Principle

EVERYTHING is parsed → compiled → executed via ExecutionContext

No runtime string parsing.

2. DSL SPECIFICATION
   2.1 Variables

Syntax:

$namespace.path.to.value$

Examples:

$player.name$
$player.stat.HEALTH$
$player.skill.COMBAT.level$
$player.inventory.mainHand$
$player.var.reputation$
$system.time$
$world.name$
2.2 Expressions

Supported:

- - - /
      == != > < >= <=
      ? :

Examples:

$player.stat.HEALTH$ \* 2
$player.var.rep$ + 10
$hp$ > 50 ? "healthy" : "low"
2.3 Conditions

Single string expression:

"$player.stat.HEALTH$ > 100"
"$player.inventory.mainHand$ == TESTSWORD"
"tag dungeon_complete"

Lists:

conditions:

- "$player.stat.HEALTH$ > 100"
- "tag dungeon_complete"

Evaluation:
→ AND logic

2.4 Events

Syntax:

<eventName> <args> <options>

Examples:

give STONE:40
give TESTSWORD:1 notify
variable add player.var.rep 100
tag add dungeon_complete

Options:

notify
delay:5 3. VARIABLE SYSTEM
3.1 Architecture
Core Interfaces
interface VariableResolver {
Object resolve(String path, ExecutionContext context);
}
interface VariableProvider {
String getNamespace(); // "player", "system", etc.
Object resolve(String[] path, ExecutionContext context);
}
3.2 Registry
Registry<VariableProvider> variableProviderRegistry;
3.3 Resolution Flow
$player.stat.HEALTH$

→ split: ["player", "stat", "HEALTH"]
→ provider = registry.get("player")
→ provider.resolve(["stat","HEALTH"], context)
3.4 Built-in Providers
PlayerVariableProvider

Handles:

player.name
player.stat._
player.skill._
player.inventory._
player.var._

Uses:

PlayerManager
StatModule
SkillModule
SystemVariableProvider
system.time
WorldVariableProvider
world.name
world.dimension
3.5 Custom Variables (player.var.\*)

Stored in:

class ValmoraProfile {
Map<String, Object> variables;
}

Persistence:
→ JSON (same as stats/skills)

4. EXPRESSION ENGINE
   4.1 Core Structure
   interface Expression {
   Object evaluate(ExecutionContext context);
   }
   4.2 Node Types
   LiteralNode
   VariableNode
   BinaryOpNode
   TernaryNode
   4.3 Example AST
   $player.stat.HEALTH$ \* 2 + 10

Becomes:

AddNode
├─ MultiplyNode
│ ├─ VariableNode(player.stat.HEALTH)
│ └─ LiteralNode(2)
└─ LiteralNode(10)
4.4 Evaluation Rules
Auto-cast numbers
Invalid operations:
→ warning
→ safe fallback 5. CONDITION SYSTEM
5.1 Interface
interface Condition {
boolean evaluate(ExecutionContext context);
}
5.2 Implementations
ExpressionCondition

Wraps compiled Expression

TagCondition
tag dungeon_complete
5.3 ConditionGroup
class ConditionGroup {
List<Condition> conditions;

    boolean evaluate(context) {
        return conditions.stream().allMatch(c -> c.evaluate(context));
    }

} 6. EVENT SYSTEM
6.1 Core Interface
interface CompiledEvent {
void execute(ExecutionContext context);
}
6.2 Event Registry
Registry<EventFactory>
interface EventFactory {
String getName();
CompiledEvent compile(String[] args, EventOptions options);
}
6.3 Execution Pipeline
for (CompiledEvent event : events) {
event.execute(context);
}
6.4 Delay Handling

Inside CompiledEvent:

if (delay > 0) {
Bukkit.getScheduler().runTaskLater(plugin, () -> executeNow(context), delayTicks);
} else {
executeNow(context);
}
6.5 Built-in Events
GiveEvent

DSL:

give STONE:40
VariableEvent
variable add player.var.rep 100

Supports:

add
set
remove
TagEvent
tag add dungeon_complete
tag remove dungeon_complete 7. TAG SYSTEM
7.1 Storage
class ValmoraProfile {
Set<String> tags;
}
7.2 Access
interface TagService {
boolean hasTag(String tag);
void addTag(String tag);
void removeTag(String tag);
}
7.3 Integration

ExecutionContext exposes TagService.

8. EXECUTION CONTEXT EXTENSION

Extend:

interface ExecutionContext {

    VariableResolver getVariableResolver();
    ExpressionEvaluator getExpressionEvaluator();
    TagService getTagService();

} 9. PARSING SYSTEM
9.1 Tokenizer

Shared tokenizer:

identifiers
operators
literals
$...$
9.2 Parsers
ExpressionParser

→ builds AST

ConditionParser

→ builds Condition

EventParser

→ builds CompiledEvent

10. MODULE STRUCTURE
    New Module: ScriptModule

Implements:

ReloadableModule
Responsibilities:
Load variables.yml
Initialize registries
Parse DSL
Provide services 11. FILE STRUCTURE
module/script/

├── ScriptModule.java

├── variable/
│ ├── VariableResolverImpl.java
│ ├── VariableProvider.java
│ ├── VariableProviderRegistry.java
│ ├── providers/
│ ├── PlayerVariableProvider.java
│ ├── SystemVariableProvider.java
│ ├── WorldVariableProvider.java

├── expression/
│ ├── Expression.java
│ ├── ExpressionParser.java
│ ├── nodes/
│ ├── LiteralNode.java
│ ├── VariableNode.java
│ ├── BinaryOpNode.java
│ ├── TernaryNode.java

├── condition/
│ ├── Condition.java
│ ├── ConditionParser.java
│ ├── ExpressionCondition.java
│ ├── TagCondition.java
│ ├── ConditionGroup.java

├── event/
│ ├── CompiledEvent.java
│ ├── EventParser.java
│ ├── EventFactory.java
│ ├── EventRegistry.java
│ ├── impl/
│ ├── GiveEvent.java
│ ├── VariableEvent.java
│ ├── TagEvent.java

├── tag/
│ ├── TagService.java
│ ├── TagServiceImpl.java 12. INTEGRATION EXAMPLES
Item Config
name: "$player.name$'s Sword"

lore:

- "HP: $player.stat.HEALTH$"
- "Reputation: $player.var.reputation$"
  Ability
  on-hit:
  conditions: - "$player.stat.HEALTH$ > 50"

events: - "give STONE:10 notify" - "variable add player.var.rep 5"
Mob Skill
on-attack:
events: - "tag add combat_active" 13. IMPLEMENTATION RULES
MUST FOLLOW
No static singletons
Use registries for:
variables
events
All evaluation via ExecutionContext
No YAML access inside logic classes
PERFORMANCE RULES
Precompile everything on reload
No string parsing during runtime
Cache providers and registries
SAFETY RULES
All failures → warning, not crash
Null-safe evaluation everywhere 14. EXECUTION FLOW SUMMARY
Example:
events:

- "variable add player.var.rep 10"
  Runtime:
  YAML loaded
  Parsed → CompiledEvent
  Stored in registry/module
  Trigger occurs
  ExecutionContext created
  Event executed
  Expression evaluated
  Variable updated
  FINAL RESULT

You now have:

Fully modular scripting system
Aligned with ExecutionContext architecture
Fully YAML-driven
Extensible via registries
Safe and performant for MMO scale
