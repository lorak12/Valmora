# VALMORA REFACTOR SPECIFICATION (AI EXECUTION DOCUMENT)

## 1. PURPOSE

This document defines the **complete refactor strategy** for the Valmora plugin.

The goal is NOT to redesign gameplay systems, but to:

- stabilize architecture
- eliminate inconsistencies
- unify systems under shared abstractions
- prepare the plugin to scale into a full MMO engine

---

## 2. HARD CONSTRAINTS (NON-NEGOTIABLE)

### 2.1 Behavioral Integrity

- Existing gameplay logic MUST remain unchanged
- Output of systems (combat, items, GUI, etc.) must remain identical

### 2.2 Incremental Refactor Only

- No full rewrites
- Refactor must be step-by-step
- Each step must compile and function

### 2.3 No Feature Expansion

- Do NOT add new gameplay features
- Only restructure, optimize, and unify existing systems

---

## 3. TARGET ARCHITECTURE

## 3.1 Package Structure (MANDATORY)

```
org.nakii.valmora

├── api/
│   ├── ValmoraAPI.java
│   ├── registry/
│   ├── event/
│   ├── model/

├── core/
│   ├── engine/
│   ├── registry/
│   ├── context/
│   ├── reload/
│   ├── lifecycle/

├── module/
│   ├── items/
│   ├── combat/
│   ├── gui/
│   ├── mobs/
│   ├── skills/
│   ├── stats/

├── infrastructure/
│   ├── config/
│   ├── persistence/
│   ├── util/
```

### Rules:

- `api/` → ONLY public interfaces
- `core/` → shared engine logic
- `module/` → gameplay systems
- `infrastructure/` → low-level utilities only

---

## 4. CORE SYSTEMS TO IMPLEMENT

These must be implemented BEFORE refactoring modules.

---

## 4.1 REGISTRY SYSTEM

### Purpose

All major objects must be centrally registered.

### Required Registries:

- ItemRegistry
- AbilityRegistry
- MechanicRegistry
- VariableRegistry
- ConditionRegistry
- EventRegistry
- GUIRegistry

### Rules:

- Registries must:
  - support register/unregister
  - support reload (clear + re-register)
  - be thread-safe where necessary

- No direct instantiation bypassing registry

---

## 4.2 EXECUTION ENGINE (CRITICAL SYSTEM)

### Purpose

Unify:

- GUI actions
- Item abilities
- Quest events (future)
- Any scripted logic

---

### Core Flow

```
Context → Condition → Execution → Event → Result
```

---

### Components

#### ExecutionContext

Holds runtime data:

```
player
target
item
gui
location
custom variables
```

---

#### Condition

```
boolean evaluate(ExecutionContext ctx)
```

---

#### Event

```
void execute(ExecutionContext ctx)
```

---

#### Action Pipeline

- Validate conditions
- Execute events sequentially
- Support delays and chaining

---

### Requirements

- Must support YAML-defined logic
- Must support nested execution
- Must be reusable across ALL modules

---

## 4.3 VARIABLE SYSTEM

### Requirements

#### Syntax:

- `$var.path$`
- `${expression}`

#### Features:

- nested variables
- expression evaluation
- string + numeric operations

---

### Implementation Rules

- Variables must be:
  - resolved via VariableRegistry
  - cached if reused frequently

- Expressions must:
  - be parsed once
  - compiled (NOT interpreted each time)

---

## 4.4 CONDITION SYSTEM

### Requirements

- Config-defined conditions
- Reusable across systems

### Examples:

- permission
- stat check
- custom expressions

---

### Implementation:

- Registered via ConditionRegistry
- Evaluated using ExecutionContext

---

## 4.5 RELOAD SYSTEM

### Command:

```
/valmora reload <module|all>
```

---

### Requirements

Each module must implement:

```
interface ReloadableModule {
    void load();
    void unload();
    void reload();
}
```

---

### Reload Flow

```
unload()
→ clear registries
→ reload configs
→ rebuild objects
→ register again
```

---

### Critical Rules:

- No memory leaks
- No duplicate registrations
- Listeners must rebind safely

---

## 5. MODULE REFACTOR STRATEGY

---

## 5.1 GENERAL RULES

For EACH module:

- Extract:
  - definitions (data)
  - services (logic)
  - listeners (events)

- Remove:
  - direct config usage
  - duplicated logic

---

## 5.2 ITEMS MODULE

### Must Use:

- ItemDefinition
- ItemParser
- ItemRegistry
- ItemFactory

### Changes:

- Abilities must use Execution Engine
- No inline logic inside listeners

---

## 5.3 GUI MODULE

### Must:

- fully rely on execution engine
- no hardcoded behavior

### Replace:

- direct click handling → execution pipeline

---

## 5.4 COMBAT MODULE

### Keep:

- DamageCalculator
- DamageApplier

### Refactor:

- move logic into services
- ensure no listener-heavy logic

---

## 6. CONFIG PROCESSING PIPELINE

MANDATORY for ALL YAML:

```
Load File
→ Parse
→ Validate
→ Build Definition Object
→ Register
```

---

### DO NOT:

- use ConfigurationSection outside parser
- mix parsing with runtime logic

---

## 7. API DESIGN RULES

---

### Public API MUST:

- expose registries
- expose extension points
- be stable

---

### MUST NOT:

- expose internal classes
- expose implementation details

---

### Example:

```
ValmoraAPI.getItemRegistry()
ValmoraAPI.getMechanicRegistry()
```

---

## 8. PERFORMANCE RULES

---

### MUST:

- cache parsed configs
- cache compiled expressions
- minimize allocations in hot paths

---

### MUST NOT:

- parse YAML during runtime events
- evaluate strings repeatedly

---

## 9. EVENT HANDLING (PAPER API)

---

### References:

- https://docs.papermc.io/paper/dev/event-listeners
- https://hub.spigotmc.org/javadocs/spigot/

---

### Rules:

- listeners = orchestration only
- NO business logic inside listeners

---

### Example:

BAD:

```
@EventHandler
public void onClick(...) {
    // full logic here
}
```

GOOD:

```
listener → service → engine
```

---

## 10. REFACTOR EXECUTION PLAN (STEP-BY-STEP)

---

### STEP 1

- Create package structure
- Move classes accordingly

---

### STEP 2

- Extract API layer
- Define interfaces

---

### STEP 3

- Implement registry system

---

### STEP 4

- Implement execution engine

---

### STEP 5

- Implement reload system

---

### STEP 6

- Refactor ITEMS module

---

### STEP 7

- Refactor GUI module

---

### STEP 8

- Refactor COMBAT module

---

### STEP 9

- Remove duplicate logic

---

### STEP 10

- Optimize + cleanup

---

## 11. VALIDATION CRITERIA

Refactor is successful if:

- plugin behavior is unchanged
- systems are modular
- configs are unified
- reload works reliably
- API is clean and usable

---

## 12. OUTPUT EXPECTATIONS FOR AI

For EACH step:

- explain what is being changed
- explain why
- show updated code

---

## 13. FINAL GOAL

Transform Valmora into:

- a modular engine
- a unified DSL runtime
- a developer platform

NOT just a plugin.

---
