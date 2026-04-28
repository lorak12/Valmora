# Valmora Refactor & Stabilization TODO

> Purpose: Provide a structured, incremental refactor plan for an AI agent.
> Mode: The agent MUST work step-by-step, validating each change before proceeding.

---

# 🔴 PHASE 1 — CRITICAL STABILITY (DO FIRST)

## 1. Session / Profile Null Safety

### Problem

Multiple systems directly call:
playerManager.getSession(uuid).getActiveProfile()

This assumes:

- session exists
- profile is loaded
- async loading is complete

This violates:

- defensive programming
- async-safe architecture

### Affected Areas (non-exhaustive)

- DamageApplier.applyDamage()
- PlayerVariableProvider
- TagEvent
- GiveXpEventFactory
- PlayerListener.onPlayerRespawn()

### Risks

- NullPointerExceptions in combat
- Script engine crashes
- Respawn crashes
- Hard-to-debug race conditions

### Required Fix

#### Step 1 — API Change

Introduce safe access:

Option A:
Optional<ValmoraPlayer> getSession(UUID uuid)

Option B:
Optional<ValmoraProfile> getActiveProfile(UUID uuid)

#### Step 2 — Refactor All Call Sites

Replace:
playerManager.getSession(uuid).getActiveProfile()

With:
playerManager.getSession(uuid)
.map(ValmoraPlayer::getActiveProfile)
.ifPresent(profile -> { ... });

#### Step 3 — Logging

If session missing:
log warning (NOT error)

---

## 2. Async Loading Contract

### Problem

Systems assume player data is available immediately after join/reload.

### Risks

- Race conditions
- Inconsistent state
- NPEs

### Required Fix

#### Step 1 — Add State Check

boolean isLoaded(UUID uuid)

#### Step 2 — Introduce Event

PlayerProfileLoadedEvent

#### Step 3 — Enforce Usage

- Block or skip logic if not loaded
- NEVER assume availability

---

# 🟠 PHASE 2 — CORE ARCHITECTURE FIXES

## 3. Machine / GUI / Recipe Unification

### Problem

Currently 3 competing systems:

- GUI logic executes crafting
- RecipeEngine handles static recipes
- Planned DynamicMachineHandler

This breaks:

- separation of concerns
- scalability

### Target Architecture

Create unified abstraction:

interface Machine {
Optional<RecipeResult> process(InputSnapshot input);
}

### Required Changes

- Move ALL logic into Machine implementations:
  - AnvilMachineHandler
  - EnchantMachineHandler
  - AlchemyMachineHandler

- GUI must ONLY:
  - display state
  - send input

- RecipeEngine becomes:
  dispatcher + registry

### Remove

- GUI-based crafting logic
- direct item mutation in GUI events

---

## 4. Dupe Protection

### Problem

Crafting preview and execution are not atomic.

### Exploit Risk

- Item swapping
- Shift-click duplication
- Inventory desync

### Required Fix

#### Step 1 — Snapshot System

Capture:
Map<String, ItemStack> snapshot

#### Step 2 — Validate on Craft

Before execution:
compare snapshot vs current inventory

#### Step 3 — Locking

Use:

- UUID
- hash
- or metadata tag

Cancel craft if mismatch.

---

## 5. Module Naming & Load Order Consistency

### Problem

Docs and code mismatch:

- SkillModule vs SkillManager
- GuiModule vs UIManager

### Risks

- Dependency confusion
- Incorrect initialization order

### Required Fix

#### Step 1 — Standardize Naming

| Type    | Naming    |
| ------- | --------- |
| Module  | XModule   |
| Service | XManager  |
| Data    | XRegistry |

#### Step 2 — Define Single Load Order Source

One authoritative list only.

#### Step 3 — Update Docs OR Code

They must match exactly.

---

# 🟡 PHASE 3 — SYSTEM COMPLETION

## 6. Enchant System Consolidation

### Problem

Split between:

- Event DSL
- GUI logic
- Planned machine system

### Required Fix

- Move logic into:
  EnchantMachineHandler

- Event DSL should:
  trigger actions ONLY

- Remove logic duplication

---

## 7. Skill System Inconsistency

### Problem

Docs say "not implemented"
Code partially implements

### Required Fix

Choose ONE:

- Fully implement and document
- OR disable until complete

---

## 8. Scoreboard System

### Problem

- Rendering loop commented out
- Feature exposed but non-functional

### Required Fix

Option A:

- Implement fully (recommended)

Option B:

- Remove from API

---

## 9. Economy Integration

### Problem

Hardcoded TODO in MobDeathListener

### Required Fix

Create:
interface EconomyService

Then:
api.getEconomy().addCoins(player, amount)

---

# 🟢 PHASE 4 — QUALITY & PERFORMANCE

## 10. DSL Validation

### Problem

String-based DSL has no validation

### Risks

- Silent failures
- runtime-only errors

### Required Fix

- Validate ALL scripts on load
- Fail fast with clear errors

---

## 11. Recipe Format Redesign

### Problem

Custom string format is fragile

Example:
crafting_table;" S "" S "" I "

### Required Fix

Replace with structured YAML:

machine: crafting_table
pattern:

- " S "
- " S "
- " I "
  ingredients:
  S: SUPER_INGOT:16
  I: STICK:1

---

## 12. Performance Optimization

### Problem

Heavy recomputation:

- GUI rendering
- enchant scanning

### Required Fix

Introduce caching:

Map<ItemHash, CachedData>

Invalidate on:

- slot update
- item change

---

# 📌 EXECUTION RULES FOR AI AGENT

1. NEVER modify multiple systems at once
2. ALWAYS finish one TODO before moving on
3. ALWAYS validate compilation after each change
4. NEVER assume behavior — verify from code
5. KEEP changes minimal and isolated
6. LOG every decision

---

# 📊 PRIORITY ORDER

1. Null safety
2. Async contract
3. Machine architecture
4. Dupe protection
5. Naming consistency
6. Enchant system
7. Skills
8. Scoreboard
9. Economy
10. DSL validation
11. Recipe format
12. Performance

---
