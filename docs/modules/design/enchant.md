# Enchant Module — Design & Code

> **Version:** 0.2 (post enchant-overhaul) | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `enchants` | **Package:** `org.nakii.valmora.module.enchant`
> **Load order:** after `machine`/`modifier`, before `zone` — see CLAUDE.md §5.
> **Status:** implemented — structured PDC storage with legacy-CSV migration-on-write, a fluent
> Java builder API, a full script-module bridge (`variables:`/`combat:`/`triggers:`), a two-tier
> state engine (in-memory per-attacker transient counters + PDC-backed persistent counters), a
> `stats:` block wired into `StatManager`, and enchanting-table XP cost + server-side level-cap
> enforcement. `EnchantmentLogic` (the pre-overhaul Java hook interface) is preserved and still
> fires alongside anything YAML-declared — nothing forces a migration off it.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Why This Is Not Just the Modifier Framework](#2-why-this-is-not-just-the-modifier-framework)
3. [Code Structure](#3-code-structure)
4. [Storage — `EnchantStateStore`](#4-storage--enchantstatestore)
5. [The Script Bridge](#5-the-script-bridge)
6. [Combat Hook — `combat:`](#6-combat-hook--combat)
7. [Trigger Dispatch — `triggers:`](#7-trigger-dispatch--triggers)
8. [The State Engine — `state:`](#8-the-state-engine--state)
9. [Stats — `stats:`](#9-stats--stats)
10. [The Legacy `EnchantmentLogic` Hook](#10-the-legacy-enchantmentlogic-hook)
11. [Full YAML Schema](#11-full-yaml-schema)
12. [GUI / Anvil / Enchanting-Table Integration](#12-gui--anvil--enchanting-table-integration)
13. [Data Model / Persistence](#13-data-model--persistence)
14. [API Exposed](#14-api-exposed)
15. [Dependencies & Consumers](#15-dependencies--consumers)
16. [Testing](#16-testing)
17. [Possible Improvements / Known Gaps](#17-possible-improvements--known-gaps)

---

## 1. Overview

The Enchant module is Valmora's **custom RPG enchantment system** — a data-driven `ReloadableModule`
that loads enchantment definitions from `plugins/Valmora/enchants/*.yml`, stores applied enchants on
the item's `PersistentDataContainer`, renders them as a lore block + fake glint, and feeds their
runtime effects into three places: the **stat system** (passive bonuses), the **combat damage
pipeline** (pre-hit modifiers and post-hit triggers), and the **enchanting table / anvil** (cost and
level-cap enforcement).

Before the overhaul (`enchant-overhaul` branch, four phases) an enchant's *behavior* could only be
Java: a `logic:` key pointed at one of a handful of hardcoded `EnchantmentLogic` implementations, and
adding new behavior meant writing and compiling a new Java class. The overhaul turned enchant
authoring into the same kind of YAML-driven authoring the GUI, item-ability, and combat-pipeline
systems already offer — an admin can now give an enchant conditional damage multipliers, a
per-attacker stacking debuff, a combo counter, an on-kill heal, or a stat bonus **entirely from
YAML**, using the same condition/expression/event DSL those other systems already use — while the
old `logic:` mechanism keeps working unchanged for anything not (yet) migrated. Both paths run for
the same enchant instance if both are declared; this is a deliberate hybrid, not an either/or.

The module remains **stateless as far as persistence goes** — there is no database table, and the
only runtime state that isn't item-bound (the transient combo/stacking counters — see §8) lives in a
plain in-memory map that resets on `/valmora reload` or restart, by design.

---

## 2. Why This Is Not Just the Modifier Framework

The Modifier Framework (`module/modifier/`, `docs/modules/design/modifier.md`) already solves "an
attachable item component that grants stats/abilities/effects" generically, and structurally the two
systems look alike: both parse YAML content into an immutable definition object, both store
structured per-instance data in the item's PDC, both drive `StatManager.recalculateStats`, and both
compile condition/event blocks through the same script-module primitives.

They are nonetheless **two independent implementations**, by deliberate decision, not an oversight:

- Enchants have their own storage (`EnchantStateStore`, its own PDC keys), their own registry
  (`EnchantmentRegistry`), their own trigger enum (`EnchantTrigger`), and their own dispatcher
  (`EnchantDispatcher`) — none of it shares code with `ModifierComponentStore`/`ModifierEngine`.
- The *in-game application* is genuinely different: enchants are levelled (I, II, III, …), have
  `etable-max-level`/`absolute-max-level` ceilings, conflict lists, and an enchanting-table/anvil
  cost model; modifiers are tiered, rarity-scaled, and have their own exclusivity/stacking/capacity
  rules. Forcing one engine to serve both would mean the engine either grows enchant-specific
  branches (exactly what the modifier framework's design doc forbids: "no group-specific Java in the
  engine") or the enchant-specific concepts (levels, conflicts, the etable cost curve) get bolted
  onto the modifier schema as an awkward special case.
- Only **generic, already-multi-consumer** `api/`-level infrastructure is shared: `HookBus`,
  `ExecutionContext`, `ConditionParser`, `EventParser`, `VariableProvider`. That's not a violation of
  "don't duplicate the modifier framework" — GUI, combat, mob, and fishing all already share exactly
  this same infra without being "the same system".

If you're deciding whether new content belongs in enchants or in the modifier framework: **anything
levelled with an enchant-shaped apply/remove/conflict/cost story is an enchant; anything tiered,
rarity-scaled, or reforge/gemstone-shaped is a modifier.**

---

## 3. Code Structure

```
src/main/java/org/nakii/valmora/module/enchant/
├── EnchantModule.java              # ReloadableModule — lifecycle, YAML parser, builtin logic registry
├── EnchantmentHelper.java          # Static utility — apply/remove/lore, thin wrapper over EnchantStateStore
├── EnchantmentDefinition.java      # Immutable definition + fluent Builder (mirrors ModifierDefinition.Builder)
├── EnchantmentLogic.java           # Legacy Java hook interface (5 default no-op methods) — still supported
├── EnchantmentRegistry.java        # SimpleRegistry<EnchantmentDefinition>
├── EnchantStateStore.java          # Structured PDC read/write + legacy-CSV migration-on-write
├── EtableCostCalculator.java       # Pure XP-level cost function for the enchanting table
├── EnchantTrigger.java             # enum ON_ATTACK_POST, ON_DEFEND_POST, ON_KILL, ON_DEATH
├── EnchantTriggerBlock.java        # record(conditions, actions, failActions) — one compiled `triggers.<T>:` block
├── EnchantTriggerStage.java        # HookBus PipelineStage wrapping one EnchantTriggerBlock
├── EnchantCombatHook.java          # Compiles/applies `combat.modify-attack`/`modify-defend` into DamageModifierContext
├── EnchantDispatcher.java          # Central per-hit orchestrator: runs legacy logic, then the compiled trigger
├── EnchantKillListener.java        # EntityDeathEvent listener firing ON_KILL for the killer's weapon
├── EnchantVariableProvider.java    # $enchant.level$ / $enchant.state.<key>$
├── EnchantLevelVariableProvider.java # bare $level$ (alias for $enchant.level$, used throughout formulas)
├── EnchantCalcVariableProvider.java  # $calc.<name>$ — pre-evaluated `variables:` formulas
├── HitVariableProvider.java        # $hit.damage$ / $hit.is_crit$ / $hit.damage_type$
├── event/
│   └── EnchantStateEventFactory.java # `enchant_state <increment|add|set|reset> <key> [amount]` DSL event
├── state/
│   ├── EnchantStateType.java          # enum HIT_COUNTER, INTEGER — schema tag for state: entries
│   ├── TransientStateDefinition.java  # parsed state.transient.<key>: entry
│   ├── PersistentStateDefinition.java # parsed state.persistent.<key>: entry
│   ├── TransientStateTracker.java     # in-memory per-attacker counter tracker + cleanup sweep
│   └── EnchantStateEngine.java        # facade: resolves/mutates whichever tier a key belongs to
└── logic/
    ├── StatBonusLogic.java         # Generic: +per-level of any stat (parameterized)
    ├── DamageMultiplierLogic.java  # Generic: +% damage of a DamageType per level (parameterized)
    ├── DefenseReductionLogic.java  # Generic: reduce victim defense by % per level (parameterized)
    ├── ExecuteLogic.java           # (superseded by `execute`'s combat: migration, kept for hybrid use)
    ├── FirstStrikeLogic.java       # (superseded by `first_strike`'s full migration, kept for hybrid use)
    ├── LethalityLogic.java         # (superseded by `lethality`'s full migration, kept for hybrid use)
    ├── LifeStealLogic.java         # (superseded by `life_steal`'s full migration, kept for hybrid use)
    ├── RespiteLogic.java           # (superseded by `respite`'s stats: migration, kept for hybrid use)
    └── ThornsLogic.java            # still the ONLY implementation of `thorns` — see §7's recursion note

src/main/resources/enchants/
└── example_enchantments.yml        # Shipped example file — see §11's worked examples and the file's own comments

src/main/resources/guis/
└── enchanting.yml                  # Enchanting Table GUI definition (consumer, not module code)
```

**A class you will not find:** `org.nakii.valmora.module.recipe.EnchantingTableMachineHandler`. It
was confirmed dead code (the shipped `guis/enchanting.yml` never referenced it — the GUI's own
`enchant_apply`/`enchant_select`/`enchant_remove`/`enchant_back` events, owned by the `gui` module,
are the actual apply path) and was deleted as part of the overhaul rather than left unreferenced.

---

## 4. Storage — `EnchantStateStore`

`EnchantStateStore` replaced `EnchantmentHelper`'s original flat CSV PDC string
(`"sharpness:5,growth:3"` under `Keys.ENCHANTS_CONTAINER_KEY`) with a structured format, needed
because an enchant instance can now carry its own persistent state (a per-item counter — see §8),
which a flat `id:level` string has no room for.

**New format:** `Keys.ENCHANTS_STATE_CONTAINER_KEY` holds one `TAG_CONTAINER_ARRAY`, one nested
container per enchant instance with three fixed fields — `id` (STRING), `level` (INTEGER), and an
optional `state` (STRING) holding every declared persistent counter as one delimited blob, e.g.
`"kills=1542,combo=3"`. This mirrors the *shape* of `ModifierComponentStore` (one PDC key, one
container per instance) but not its implementation — critically, it uses a single fixed STRING field
for all persistent state rather than one dynamic `NamespacedKey` per state-key, specifically so
`EnchantStateStore` never needs a live plugin instance just to construct a key (this also turned out
to matter for testability — see §16).

**Migration-on-write.** `EnchantStateStore.load(...)` checks the new key first; if absent, it falls
back to parsing the legacy CSV format **read-only** (no write-back). `EnchantStateStore.save(...)`
always writes the new format and deletes the legacy key. Consequently, any code path that *mutates*
an item's enchants (apply, remove, anvil merge) transparently upgrades that item to the new format as
a side effect of the very next write — there is no batch migration script, and an item nobody
re-enchants keeps working indefinitely through the legacy-read fallback.

`EnchantmentHelper` is now a thin wrapper: every one of its public methods (`applyEnchantment`,
`getEnchantments`, `removeEnchantment`, `hasValmoraEnchants`, `createEnchantedBook`, …) is unchanged
in signature and delegates internally to `EnchantStateStore`. One real behavior fix rode along with
this rewrite: `applyEnchantment` gained a 4-arg overload
(`applyEnchantment(item, id, level, enforceEtableCap)`) — the 3-arg overload (still used by the
anvil/admin path) clamps to `absoluteMaxLevel` as before, but the enchanting-table GUI's apply event
now passes `enforceEtableCap = true`, closing a bug where the table only ever *offered* in-range
level buttons but never actually rejected an out-of-range level server-side.

`EnchantmentDefinition.builder(id)` is a fluent Java builder (mirroring `ModifierDefinition.Builder`)
for registering a custom enchant purely in Java, without a YAML file — every field the YAML parser
can set has a corresponding builder method.

---

## 5. The Script Bridge

Every YAML-driven capability described in §6-9 rides on the same generic primitive: `ExecutionContext`'s
namespaced key-value attachment map (see `api/execution/ExecutionContext.java`), by convention
`"enchant:id"` / `"enchant:level"` / `"enchant:instance"` / `"enchant:item"` / `"calc:<name>"` /
`"hit:*"`. No enchant-specific `ExecutionContext` subclass exists — `DamageCalculator` already builds
one `SimpleExecutionContext` per hit for its own `dmg:*` variables (`buildFormulaContext`); the
enchant dispatch code just attaches more keys onto that same context before running conditions and
actions against it.

New `VariableProvider`s (registered by `EnchantModule.onEnable()`) expose these attachments as
script variables:

| Variable | Resolves | Notes |
|---|---|---|
| `$enchant.level$` | `context.get("enchant:level")` | The level of whichever enchant instance is currently dispatching. |
| `$level$` | same as `$enchant.level$` | A bare alias — every worked example in the overhaul's spec writes `$level$`, not `$enchant.level$`. |
| `$enchant.state.<key>$` | `EnchantStateEngine.resolve(context, key)` | Transient or persistent, whichever tier the enchant declared `<key>` under — see §8. |
| `$calc.<name>$` | `context.get("calc:<name>")` | One entry per `variables:` formula, pre-evaluated once per dispatch (see below). |
| `$hit.damage$` / `$hit.is_crit$` / `$hit.damage_type$` | `context.get("hit:*")` | Attached by `DamageCalculator` right after the `DamageResult` is computed — only meaningful in `ON_ATTACK_POST`/`ON_DEFEND_POST`. |
| `$target.hp_percent$` / `$target.missing_hp_percent$` | extends the existing `TargetVariableProvider` | Added for `execute`/`first_strike`-style conditions — not a new provider, since `target` was already owned. |

A `variables:` block declares named formulas, `$level$`-scoped, evaluated **once per dispatch** (not
once per reference) and attached as `$calc.<name>$` — this exists so a combat modifier and a trigger
action can share one derived number (e.g. `first_strike`'s `bonus_multiplier`) without re-deriving it
twice or drifting if the formula is edited in only one place.

```yaml
variables:
  bonus_multiplier: "1.0 + (0.25 * $level$)"
```

---

## 6. Combat Hook — `combat:`

`combat.modify-attack:` / `combat.modify-defend:` compile (via `EnchantCombatHook`) into a
`conditions:` gate plus a `modifiers:` map, applied against `DamageModifierContext` using the same
composition rules the pre-overhaul `EnchantmentLogic` implementations already used for that field —
so a hybrid Java+YAML enchant can mix a Java `modifyAttack` and a YAML `combat.modify-attack` without
their contributions clobbering each other:

| Modifier key | Side | Composition | Meaning |
|---|---|---|---|
| `damage-multiplier` | attack or defend | multiplicative — `ctx.setDamageMultiplier(ctx.getDamageMultiplier() * value)` | the formula is the **full factor**, e.g. `"1.0 + (0.05 * $level$)"` for +5%/level |
| `crit-chance` / `crit-damage` | attack | additive | a delta added to the running total |
| `defense-shred-percent` | attack | additive, accumulator | reduces the victim's *effective* defense before mitigation — `DamageCalculator` computes `effectiveDefense = defense * (1 - shred/100)` |
| `damage-reduction-percent` | defend | additive, accumulator | a flat percentage taken off the mitigated damage, applied **after** defense mitigation |

`combat:` is a separate mechanism from `triggers:` (§7) — it contributes numeric modifiers to the
damage formula itself, evaluated **pre-hit**, whereas triggers run pass/fail action lists **post-hit**.
An enchant can declare both; `sharpness`'s migration only needs `combat:`, while `first_strike` needs
both (a `combat.modify-attack` gated on the current combo count, plus a `triggers.ON_ATTACK_POST` that
increments it).

**Defend-side role swap.** A `modify-defend`/`ON_DEFEND_POST` condition or trigger action's
`@self`/`@target` (via `TargetResolver`) must mean "the wearer"/"the attacker", not "the attacker"/"the
victim" (which is what the shared attack-side context means). `DamageCalculator` builds a child
`SimpleExecutionContext` with `caster`/`target` swapped, parented to the shared hit context so it
still inherits every `dmg:*`/`hit:*` attachment via the parent chain — this is `defendContext`, used
for every defend-side dispatch (both the pre-hit `modify-defend` evaluation and the post-hit
`ON_DEFEND_POST` trigger).

---

## 7. Trigger Dispatch — `triggers:`

`triggers.<TRIGGER>:` blocks (`conditions:`/`actions:`/`fail-actions:`) compile into
`EnchantTriggerBlock` (an independent copy of `GuiEventBlock`'s shape — conditions gate, actions run
catching `ConditionAbortException` as an early interrupt into `fail-actions`) and register onto the
shared `HookBus` at point `"enchant:<id>:<trigger>"` via `EnchantTriggerStage`, exactly the same
"insertion point → conditions → pass/fail actions" pipeline GUI event blocks and the combat pipeline
already use.

```java
public enum EnchantTrigger { ON_ATTACK_POST, ON_DEFEND_POST, ON_KILL, ON_DEATH }
```

`EnchantDispatcher` is the single orchestrator every trigger point actually runs through:

1. Runs the legacy `EnchantmentLogic` hook first (`onPostAttack`/`onPostDefend`), unconditionally —
   hybrid coexistence, not either/or.
2. Checks `HookBus.hasStages(point)` — **zero-cost when nothing is registered** — before attaching
   anything further, so an unmigrated enchant with no `triggers:` block costs nothing extra per hit.
3. Attaches `enchant:id`/`enchant:level`/`enchant:instance` and every `variables:` formula (as
   `calc:*`) onto the dispatch context.
4. Runs `bus.runPoint("enchant:<id>:<trigger>", context)`.

`DamageCalculator` calls `EnchantDispatcher.dispatchPostAttack`/`dispatchPostDefend` from its
post-hit loops (per weapon enchant / per armor-piece enchant respectively); `EnchantKillListener` — a
dedicated `EntityDeathEvent` listener, separate from `MobDeathListener`'s custom-mob-only
`combat:on_death` point and `AbilityTriggerListener`'s item-ability-owned kill dispatch — fires
`ON_KILL` for the killer's main-hand weapon on any kill (vanilla mob, custom mob, or PvP).

`ON_ATTACK_POST`/`ON_DEFEND_POST`/`ON_KILL` dispatch also attaches `"enchant:item"` (the live
weapon/armor `ItemStack`) onto the context — this exists solely so a triggered `enchant_state` action
(§8) has something to save a persistent-state mutation back to.

**New generic script events**, added alongside this and registered by `ScriptModule` itself (not
enchant-private, since mob/item abilities and quest scripts can use them too):

| Event | DSL | Notes |
|---|---|---|
| `heal` | `heal <selector> <amount>` | Player targets heal through their profile; other entities heal via vanilla `setHealth`, clamped to max. |
| `damage` | `damage <selector> <amount> [type]` | Routes through `DamageCalculator`, same as the `DAMAGE` ability mechanic. |
| `strike_lightning` | `strike_lightning [selector]` | Visual/audio only (`World.strikeLightningEffect`) — damage is always a separate, explicit `damage` step, deliberately not bundled. |

**Why `thorns` is not migrated to `triggers.ON_DEFEND_POST` + `damage`.** `ThornsLogic` reflects
damage by directly mutating the attacker's health rather than routing back through
`DamageCalculator`, specifically to avoid the attacker's own armor re-triggering this same
`ON_DEFEND_POST` hook on itself (infinite recursion). The new `damage` event always goes through the
full pipeline, so migrating `thorns` this way would reintroduce exactly the bug the old code was
written to avoid — it's the one shipped enchant deliberately left on legacy `logic:` only.

---

## 8. The State Engine — `state:`

Two independent tiers, both readable via `$enchant.state.<key>$` and mutable via the
`enchant_state <increment|add|set|reset> <key> [amount]` event — `EnchantStateEngine` is the single
facade resolving/mutating whichever tier an enchant declared `<key>` under, so callers never need to
know which one they're touching.

```yaml
state:
  transient:
    <key>: { type: HIT_COUNTER, reset-after-seconds: N, reset-on-target-switch: bool, max-stacks: N }
  persistent:
    <key>: { type: INTEGER, default: N }
```

**Transient** (`TransientStateTracker`) — an in-memory counter, never PDC-persisted, keyed **per
attacker** (`(attacker UUID, enchant id, key)`), not per victim. This is the concrete fix for a real
pre-overhaul bug: `LethalityLogic`'s stack counter was keyed by victim UUID only, so two different
players attacking the same mob shared (and polluted) each other's stack count. Each entry also
remembers the attacker's last-hit victim, so `reset-on-target-switch` can zero the count the moment
that attacker's target changes, without needing a separate map entry per (attacker, victim) pair ever
fought. `cleanup()`, swept every 5 minutes by `EnchantModule`, purges entries idle past a generous
fixed window (15 minutes) — independent of any one enchant's own `reset-after-seconds` — which is the
fix for the pre-overhaul `FirstStrikeLogic`/`LethalityLogic` classes' unbounded map growth (an
attacker who stops fighting, or is simply never fought by anyone again, previously stayed in the map
forever).

**Persistent** (`EnchantStateStore`, §4) — a counter written into the item's PDC alongside `id`/
`level`, surviving across hits, sessions, and item transfers (e.g. a "kills with this weapon" tally).
A mutation reads the `"enchant:item"` context attachment (§7) to know which `ItemStack` to save back
to — a mutate call with no item attached (e.g. a hand-built test context) is a safe no-op rather than
a throw.

No new `Condition` keyword was needed for any of this — `$enchant.state.<key>$ >= N` already works
through `ConditionParser`'s existing bare-expression fallback once the variable resolves.

---

## 9. Stats — `stats:`

```yaml
stats:
  <statId>: "<$level$-scoped formula>"
```

Compiled at load time into a `Map<String, Expression>` on `EnchantmentDefinition`, and applied by
`StatManager.recalculateStats` **additively**, alongside (not instead of) the legacy
`EnchantmentLogic.applyStats` hook — the same hybrid-coexistence rule as everywhere else in this
module. A `stats:` entry can be an arbitrary expression, not just a flat `perLevel * level` — this is
how `respite`'s "bonus only while out of combat" gating became expressible declaratively, using a new
`$player.in_combat$` variable (added to `PlayerVariableProvider` specifically for this — nothing
previously exposed `PlayerState.isInCombat()` to scripts) and a ternary:

```yaml
stats:
  health_regen: "$player.in_combat$ ? 0 : (0.5 * $level$)"
```

**Caveat — hardcoded stat ids.** A `stats:` entry names a literal stat id (e.g. `"health"`), whereas
the pre-overhaul `StatBonusLogic`-family Java classes resolve their target stat through
`StatRoleRegistry` (`ValmoraAPI.getSystemStats().getHealth()`), which follows a server's own role
renames. The shipped `growth`/`protection`/`fortune`/`efficiency` migrations use the *default* role
ids and say so in a YAML comment — a server that renamed those roles should keep those specific
enchants on their original `logic:` instead of migrating them, since the declarative `stats:` block
has no way to follow a role rename the way the Java logic classes do.

---

## 10. The Legacy `EnchantmentLogic` Hook

Unchanged in shape from before the overhaul — a 5-method interface, every method defaulting to a
no-op:

| Hook | Signature | Invoked from |
|---|---|---|
| `applyStats` | `(LivingEntity, int level, StatManager)` | `StatManager.recalculateStats()` |
| `modifyAttack` | `(DamageModifierContext, attacker, victim, level)` | `DamageCalculator`, pre-hit, attacker's weapon |
| `modifyDefend` | `(DamageModifierContext, attacker, victim, level)` | `DamageCalculator`, pre-hit, victim's armor |
| `onPostAttack` | `(DamageResult, attacker, victim, level)` | `EnchantDispatcher.dispatchPostAttack`, post-hit |
| `onPostDefend` | `(DamageResult, attacker, victim, level)` | `EnchantDispatcher.dispatchPostDefend`, post-hit |

Every builtin `logic:` factory (`valmora:sharpness`, `valmora:growth`, `valmora:stat_bonus`,
`valmora:damage_multiplier`, `valmora:defense_reduction`, and the seven per-enchant classes in
`logic/`) stays registered in `EnchantModule.registerBuiltinLogics()` regardless of whether the
shipped example file still references them — deleting a still-functional, if now-unreferenced, Java
class is explicitly against the overhaul's hybrid-coexistence rule. A server admin who wrote their own
`logic:`-only enchant before the overhaul needs to change nothing.

---

## 11. Full YAML Schema

```yaml
<enchant-id>:
  name: "<display name>"
  description:
    - "<MiniMessage lore line>"
  targets: [SWORD]
  conflicts: ["other_enchant_id"]
  etable-max-level: 5
  absolute-max-level: 7

  # Legacy Java escape hatch — optional, coexists with everything below.
  logic: "valmora:sharpness"
  logic-params: { }

  # $level$-scoped formulas, evaluated once per dispatch, read back as $calc.<name>$.
  variables:
    bonus_multiplier: "1.0 + (0.25 * $level$)"

  # Pre-hit numeric modifiers into DamageModifierContext — see §6.
  combat:
    modify-attack:
      conditions: ["$target.hp_percent$ < 100"]
      modifiers:
        damage-multiplier: "1.0 + (0.002 * $level$ * $target.missing_hp_percent$)"
    modify-defend:
      modifiers:
        damage-reduction-percent: "2 * $level$"

  # Post-hit conditions -> actions/fail-actions, dispatched via the shared HookBus — see §7.
  triggers:
    ON_ATTACK_POST:
      conditions: ["$enchant.state.combo_counter$ < 3"]
      actions: ["enchant_state increment combo_counter"]
    ON_KILL:
      actions: ["heal @self 5"]

  # Two independent counter tiers, both readable as $enchant.state.<key>$ — see §8.
  state:
    transient:
      combo_counter: { type: HIT_COUNTER, reset-after-seconds: 10, reset-on-target-switch: true, max-stacks: 3 }
    persistent:
      kills: { type: INTEGER, default: 0 }

  # Additive stat bonuses, applied alongside logic.applyStats — see §9.
  stats:
    health: "10 * $level$"
```

Every block above is optional and independent — an enchant can use only `logic:`, only `stats:`,
only `combat:`+`triggers:`+`state:`, or any mix. Loading is lenient the same way it always was: an
unknown `logic:` id, unknown trigger name, or unknown `state:` `type:` logs a warning and skips just
that piece rather than failing the whole enchant.

---

## 12. GUI / Anvil / Enchanting-Table Integration

- **Enchanting Table GUI** (`guis/enchanting.yml`, machine id `enchanting_table`) — catalog + level
  list, owned by the `gui` module. `EnchantApplyEventFactory` (`enchant_apply`, in
  `module/gui/event/`) is the actual apply path: it now computes
  `EtableCostCalculator.cost(level)` (2 XP levels per level requested), rejects the apply with a
  message if the player can't afford it, applies via the 4-arg `applyEnchantment(..., true)`
  overload (server-side etable-cap enforcement), and only then deducts the XP. Previously this event
  charged nothing at all and only clamped to the *absolute* cap.
- **Unified anvil** (`module.recipe.AnvilMachineHandler`, machine id `anvil`) — the standard
  enchant+durability merge path needed **no functional change**: it already only calls
  `EnchantmentHelper`'s unchanged public API (`getEnchantments`/`applyEnchantmentMap`), and its
  book-vs-non-book level capping was already correct before this overhaul. See
  `docs/modules/design/recipe.md` for the anvil's full evaluation order (explicit
  `recipes/anvil/*.yml` recipes → the modifier framework's `APPLY_MODIFIER`/`REMOVE_MODIFIER` recipes
  → this standard combination engine).

---

## 13. Data Model / Persistence

No database persistence — enchant state is either item-bound (PDC) or, for the transient combo/
stacking counters, deliberately non-persistent in-memory bookkeeping.

**PDC keys** (`util/Keys.java`):

| Key | Type | Written by | Read by |
|---|---|---|---|
| `ENCHANTS_STATE_CONTAINER_KEY` | `TAG_CONTAINER_ARRAY` | `EnchantStateStore.save` | `EnchantStateStore.load` |
| `ENCHANT_INSTANCE_ID_KEY` / `ENCHANT_INSTANCE_LEVEL_KEY` / `ENCHANT_INSTANCE_STATE_KEY` | STRING / INTEGER / STRING | fields of each nested container above | same |
| `ENCHANTS_CONTAINER_KEY` (legacy) | STRING (`id:level,...`) | nothing writes this anymore | `EnchantStateStore.load`'s fallback path only |

---

## 14. API Exposed

```java
EnchantModule enchant = ValmoraAPI.getInstance().getEnchantModule();
enchant.getRegistry();       // EnchantmentRegistry — case-insensitive, get()/values()/getKeys()
enchant.getStateEngine();    // EnchantStateEngine — null before onEnable()/after onDisable()
enchant.getLogic(id);        // legacy direct-instance lookup
enchant.registerLogic(id, logic); // extension point; cleared on every onDisable(), must re-register after reload
```

**Static helper (`EnchantmentHelper`)** — unchanged public surface:
`canApplyEnchantment`, `applyEnchantment` (3-arg and 4-arg), `applyEnchantmentMap`,
`getEnchantments`, `getEnchantLevel`, `removeEnchantment`, `hasValmoraEnchants`, `loadEnchantMap`,
`updateItemLore`, `formatEnchants`, `createEnchantedBook`.

There is no dedicated enchant command; admin/player entry points remain `/item enchant` and
`/item enchantbook`.

---

## 15. Dependencies & Consumers

### Dependencies (loads-after)

| Dependency | Why |
|---|---|
| `script` | Every capability in §5-9 compiles through `ConditionParser`/`EventParser`/`ExpressionParser` and dispatches via `HookBus`, all owned by `ScriptModule`. |
| `item` | `ItemType` for target matching; `ItemFactory` lore rebuild for Valmora items. |
| `stat` | `StatManager.recalculateStats` drives both `logic.applyStats` and `stats:`. |
| `gui` | `GuiVariableProvider` and the enchant event factories live in the `gui` module. |
| `recipe` / `machine` / `modifier` | The anvil path (§12); no functional coupling beyond the unchanged public `EnchantmentHelper` API. |

### Consumers

| Consumer | How it uses enchants |
|---|---|
| Combat `DamageCalculator` | Pre-hit `modifyAttack`/`modifyDefend`/`combat:` modifiers; post-hit `EnchantDispatcher` calls. |
| Stat `StatManager` | `applyStats` + `stats:` per equipped/held item. |
| GUI `EnchantApplyEventFactory`/`EnchantSelectEventFactory`/`EnchantRemoveEventFactory`/`EnchantBackEventFactory` | Enchanting-table apply/select/remove/navigation. |
| Recipe `AnvilMachineHandler` | Anvil merge (unchanged). |

**Non-consumers (related but separate):** the Quest module's `enchant` objective and the Enchanting
skill both track **vanilla** enchant actions, not Valmora enchant definitions.

---

## 16. Testing

Every layer of this module has direct test coverage under `src/test/java/org/nakii/valmora/module/enchant/`:
`EnchantStateStoreTest` (round-trip + legacy-CSV migration), `EnchantmentHelperTest`,
`EnchantmentDefinitionBuilderTest`, `EnchantVariableProviderTest`/`HitVariableProviderTest`,
`EnchantCombatHookTest`, `EnchantTriggerStageTest`/`EnchantDispatcherTest`,
`state/TransientStateTrackerTest`/`state/EnchantStateEngineTest`,
`event/EnchantStateEventFactoryTest`, `EtableCostCalculatorTest`, `EnchantContentMigrationTest`
(loads the actual shipped `example_enchantments.yml` through a **real** `ScriptModule` — not a mock —
so a typo'd condition/formula/trigger name in that file fails the test the same way it would fail on
a real server), plus `module/gui/event/EnchantApplyEventFactoryTest` for the cost/cap enforcement.

One recurring gotcha worth knowing if you add more tests here: a bare Mockito `mock(Valmora.class)`
does **not** satisfy whatever Paper's real `NamespacedKey(Plugin, String)` constructor needs
internally, even with `getName()`/`getPluginMeta()` stubbed — it throws `NullPointerException:
this.namespace is null`. Build `Keys.*` fields directly off a real `MockBukkit.createMockPlugin(...)`
(`PluginMock`) instead of calling `Keys.init(mock(Valmora.class))` in any test touching PDC.

---

## 17. Possible Improvements / Known Gaps

- **`stats:`'s hardcoded-stat-id caveat** (§9) could be closed by letting a `stats:` value name a
  `StatRoleRegistry` role instead of a literal stat id, so a role rename doesn't strand a migrated
  enchant on the wrong stat.
- **`thorns` stays Java-only** (§7) — a non-recursive way to reflect damage through the real pipeline
  (e.g. a one-shot "already reflecting" guard flag on the context) would let it migrate too, but
  wasn't worth the added complexity for one enchant.
- **Transient state is lost on `/valmora reload`.** This is by design (it's genuinely ephemeral
  combat bookkeeping, not meant to survive a reload) but is worth calling out explicitly if it ever
  surprises someone mid-fight during a live reload.
- **External `registerLogic()` consumers are reload-fragile** — `logicMap` is cleared in
  `onDisable()`, so a plugin registering its own `EnchantmentLogic` must re-register after every
  `/valmora reload`. Same caveat as before the overhaul, unchanged.
