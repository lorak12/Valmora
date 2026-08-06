# Valmora Combat Pipeline Analysis & Customization Design

> **Version:** 0.1 | **Target:** Paper 1.21.x | **Java:** 21

> **Status: implemented, including the retrofit.** This document is the original proposal/analysis
> and is kept for design rationale, but it no longer describes the shipped shape exactly — e.g. the
> shared bus is called `HookBus` (not a combat-only `CombatPipeline` class), and it's now wired into
> every trigger-dispatch system in the engine: combat, resource mining, fishing, item abilities, mob
> (boss) abilities, and GUI event blocks (which now run *through* the bus instead of their own inline
> dispatch — see §6 of the reference doc). Most of §9.2/§9.3's event/variable wishlist shipped too
> (`counter`, `entity`, `apply_potion`, `give_coins`/`take_coins`, `$math.random$`) — `roll_loot`,
> `$nearby_enemies$`, and PDC-backed entity counters (for counting hits landed on a non-player
> target) did not, and remain future work. **See `docs/modules/design/pipeline.md` for the as-built
> reference.**

---

## Table of Contents

1. [Current Combat Pipeline — From Hit to Drop](#1-current-combat-pipeline--from-hit-to-drop)
2. [What Is Already YAML-Customizable](#2-what-is-already-yaml-customizable)
3. [What Cannot Be Changed Without Code](#3-what-cannot-be-changed-without-code)
4. [Proposed: `config.yml` → `combat-pipeline` Section](#4-proposed-configyml--combat-pipeline-section)
5. [Benefits and Disadvantages of a Fully Customizable Damage Pipeline](#5-benefits-and-disadvantages-of-a-fully-customizable-damage-pipeline)
6. [What This Level of Freedom Enables](#6-what-this-level-of-freedom-enables)
7. [Four Examples](#7-four-examples)
8. [Cross-System Synergies](#8-cross-system-synergies)
9. [Implementation Requirements](#9-implementation-requirements)

---

## 1. Current Combat Pipeline — From Hit to Drop

The current pipeline is entirely in Java code. Here is the complete flow, with file:line references:

### Stage 1 — Entity Damage Event Fires

**File:** `CombatListener.java:18-69` (`onEntityDamageByEntity`, `EventPriority.HIGHEST`)

1. **Skip non-LivingEntity victims** — early return if `entity` is not a `LivingEntity`.
2. **Invulnerability check** — if `victim.getNoDamageTicks() > getMaximumNoDamageTicks() / 2.0F`, cancel the event (prevents hit spamming). *(Hardcoded threshold.)*
3. **Determine attacker** — `LivingEntity` directly, or the `LivingEntity` shooter of a `Projectile`.
4. **Zero vanilla damage** — `event.setDamage(0)` so Bukkit's built-in damage math is fully bypassed.
5. **Map damage type** — if the Bukkit `DamageSource` is `ARROW` or `MOB_PROJECTILE` → `DamageType.PROJECTILE`, else `DamageType.MELEE`. *(Hardcoded only-two-way mapping.)*
6. **Calculate damage** — `DamageCalculator.calculateDamage(attacker, victim, damageType)` → produces a `DamageResult`.
7. **Apply damage** — `damageResult.apply()` → `DamageApplier` reduces health / PlayerState health, syncs visual hearts, sets combat timer, sets `noDamageTicks = 20`.
8. **Spawn damage indicator** — `DamageIndicatorManager.spawnIndicator(result)` spawns a floating `TextDisplay` for 1 second (20 ticks). *(Hardcoded format: `✧ DAMAGE ✧` for crits, colored number for normal hits.)*
9. **Record for ON_HIT abilities** — if attacker is a `Player`: `CombatTracker.recordDamageDealt(uuid, finalDamage)` and `AbilityExecutor.fireHeld(attackerPlayer, AbilityTrigger.ON_HIT, victim, silent=true)`.
10. **Boss ability triggers** — if attacker or victim is a tracked boss, `bossController.onAttack()` / `onDamaged()` fire.

**File:** `CombatListener.java:72-100` (`onEntityDamage`, `EventPriority.HIGH`)

Environmental damage (fall, fire, lava, drowning, etc.) goes through this second handler:
- Same invulnerability check as above.
- Vanilla base damage is **scaled by 5.0x** (hardcoded in `DamageCalculator.calculateDamage(victim, damageType, baseVanillaDamage)`).
- Same `DamageCalculator` → `apply()` → `spawnIndicator()` chain.
- Immunity cleanup for fire/lava on immune mobs.

### Stage 2 — Damage Calculation

**File:** `DamageCalculator.java:22-185`

This is the heart of damage math. The flow is:

1. **Read attacker stats:**
   - If `Player`: reads from `StatManager` (DAMAGE, STRENGTH, CRIT_CHANCE, CRIT_DAMAGE).
   - If custom `Mob`: reads from `MobDefinition` (scaled damage, strength, crit).
   - If unnamed mob: base damage defaults to 1.0.
2. **Build `DamageModifierContext`** — a mutable struct carrying `baseDamage`, `strength`, `critChance`, `critDamage`, `defense`, `damageType`, and a `damageMultiplier` (starts at 1.0).
3. **Weapon enchant `modifyAttack` hooks** — iterates enchants on the attacker's main-hand item. Each enchant's `EnchantmentLogic.modifyAttack()` can adjust the `DamageModifierContext`.
4. **Armor enchant `modifyDefend` hooks** — same pattern for the victim's armor contents.
5. **Critical hit roll** — `Math.random() < critChance / 100.0`. Boolean result stored on `DamageResult`.
6. **Formula evaluation** (Phase 2 data-driven): `DamageFormulaRegistry` holds pre-compiled `Expression` ASTs from `damage_formula.yml` (`damage_multiplier`, `crit_multiplier`, `defense_multiplier`).
7. **`DamageModifierContext.damageMultiplier`** is applied as a flat multiplier (enchants can bump this).
8. **Mob resistance table** — `MobDefinition.getResistance(damageType)` returns 0..1. `mitigated *= (1 - mobResistance)`. If `>= 1.0`, `DamageResult.isImmune = true`.
9. **PDC resistance component** — `DamageResistanceComponent.getResistance(victim, damageType)` reads from the entity's `PersistentDataContainer`. Stacks multiplicatively with mob resistance. Applies to **any** entity, including players.
10. **Floor** — `finalDamage = Math.floor(mitigated)`.
11. **`DamageType.fireOnHit(context)`** — fires the pre-compiled on-hit script events from `damage_types/*.yml`.
12. **Weapon enchant `onPostAttack`** callbacks.
13. **Armor enchant `onPostDefend`** callbacks.

### Stage 3 — Damage Application

**File:** `DamageApplier.java:19-49`

- **Player victim:** `PlayerState.reduceHealth(finalDamage)`, `PlayerManager.syncVisualHealth()`, `PlayerState.setInCombat()` (3-second combat timer, hardcoded), `setNoDamageTicks(20)`.
- **Mob victim:** `victim.setHealth(max(0, currentHealth - finalDamage))`, `MobManager.updateVisuals(victim)`.

### Stage 4 — Damage Indicator

**File:** `DamageIndicatorManager.java:40-81`

- Rate-limited: max 1 indicator every 400ms per entity UUID.
- Spawns a `TextDisplay` entity at the victim's eye location with a small random offset.
- Lives 20 ticks (1 second), then is removed.
- **Format:** If critical: `✧ <color><bold>DAMAGE ✧` (gold `✧` border). Otherwise: `<color>DAMAGE` where `<color>` comes from `DamageType.getColor()`.

### Stage 5 — ON_HIT Item Abilities

**File:** `CombatListener.java:51-58` (inline in `onEntityDamageByEntity`)

- If attacker is `Player`: `CombatTracker.recordDamageDealt(uuid, finalDamage)` and `AbilityExecutor.fireHeld(attackerPlayer, AbilityTrigger.ON_HIT, victim, silent=true)`.
- If attacker or victim is a tracked boss: `bossController.onAttack()` / `onDamaged()`.

### Stage 6 — Mob Death & Loot

**File:** `MobDeathListener.java:31-84` (`onMobDeath`, `EntityDeathEvent`)

1. Reads `MOB_ID_KEY` from entity PDC. If null, returns — vanilla death applies.
2. Looks up `MobDefinition` from `MobRegistry`.
3. Fires `ON_DEATH` boss abilities (via `BossController.onDeath`).
4. Awards XP: `definition.getXpReward()` = `baseXp * level`.
5. Awards gold: `definition.getGoldReward()`.
6. Rolls loot: iterates `LootTable.getEntries()`, chance-based drops with luck scaling.
7. Vanilla XP-orb drop, XP collection, and inventory drop mechanics run after Valmora's handler finishes.

---

## 2. What Is Already YAML-Customizable

The engine has been progressively externalized (see `docs/REFACTOR/PROGRESS.md`). Here is everything controllable without Java changes:

### 2.1 Damage Types — `damage_types/*.yml`

**Loaded by:** `DamageTypeLoader.java` into the registry-backed `DamageType` class.

| Property | YAML Key | Description |
|---|---|---|
| Color | `color` | MiniMessage color tag used in damage indicators and lore. |
| Defense bypass | `ignores-defense` | If `true`, victim defense is skipped entirely (replaces hardcoded VOID/DROWNING/FALL exclusion). |
| On-hit effects | `on-hit` | List of script DSL event strings fired once per hit of this type. **Pre-compiled at load time.** |

**New damage types can be added** — defining `electric` in a YAML file makes it immediately usable in `mobs/*.yml` and item mechanics.

### 2.2 Damage Formulas — `damage_formula.yml`

**Loaded by:** `DamageFormulaRegistry.java` at `CombatModule.onEnable()`.

| Formula Key | Default Expression | Purpose |
|---|---|---|
| `damage_multiplier` | `1 + $dmg.strength$ / 100` | Multiplies base damage before crit. |
| `crit_multiplier` | `1 + $dmg.crit_damage$ / 100` | Applied only on critical hits. |
| `defense_multiplier` | `100 / ($dmg.defense$ + 100)` | Mitigates final damage. |

Variables: `$dmg.base_damage$`, `$dmg.strength$`, `$dmg.crit_chance$`, `$dmg.crit_damage$`, `$dmg.defense$`. Pre-compiled, never re-parsed per hit.

### 2.3 Mob Definitions — `mobs/*.yml`

**Loaded by:** `MobLoader` → `MobDefinitionParser`. Full schema includes:

| Field | Customizability |
|---|---|
| `category`, `type`, `level` | Entity type and classification. |
| `name` | MiniMessage-formatted nameplate. |
| `stats` block | `health`, `damage`, `speed`, `defense`, `strength`, `crit-chance`, `crit-damage` — all data-driven. |
| `resistances` | Per-damage-type resistance (0..1, 1.0 = immune). |
| `equipment` | Helmet/chestplate/leggings/boots/main-hand/off-hand — vanilla materials or Valmora item IDs. |
| `base-xp`, `gold-reward` | Drop rewards on death. |
| `loot-table` | List of drops with `item`, `min-amount`, `max-amount`, `chance`, `luck-affected`. |
| `boss-bar` | Boss bar with color, style, range. |
| `abilities` | Boss abilities with triggers (`ON_TIMER`, `ON_HEALTH`, `ON_ATTACK`, `ON_DAMAGED`, `ON_SPAWN`, `ON_DEATH`), intervals, chances, cooldowns, announcements, and mechanics. |
| Behavior flags | `knockback-resistance`, `no-ai`, `silent`, `glowing`, `persistent`, `baby`, `prevent-sun-burn`. |

### 2.4 Item Definitions — `items/*.yml`

| Field | Customizability |
|---|---|
| `name`, `material`, `rarity` | Display name (MiniMessage), Bukkit Material, rarity tier. |
| `stats` | Stat bonuses stored in PDC as JSON. |
| `abilities` | Full ability system with `trigger` (RIGHT_CLICK, LEFT_CLICK, PASSIVE, ON_HIT, ON_KILL, SNEAK, ON_SHOOT, etc.), `target-range`, `cooldown`, `mana-cost`, `conditions` (pre-compiled), and `mechanics` list. |

### 2.5 Ability Mechanics — Registered in `AbilityManager`

The `MechanicRegistry` holds all built-in mechanics, each with a lowercase ID. Used in both item abilities and boss abilities via shared `MechanicParser`:

| Mechanic ID | Params | What It Does |
|---|---|---|
| `DAMAGE` | `damage`, `damage-type`, `target`, `ticks`, `interval` | Deals damage (with optional DoT). |
| `HEAL` | `heal`, `target`, `ticks`, `interval` | Heals a player target (with optional HoT). |
| `APPLY_EFFECT` | `effect`, `duration`, `amplifier`, `hide-particles`, `target` | Applies a potion effect. |
| `SCRIPT` | `events: [list of DSL strings]` | Runs arbitrary script events. |
| `MODIFY_STAT` | `stat`, `operation`, `value` | Modifies a player's base stat. |
| `TELEPORT` | `target`, `location` | Teleports a target. |
| `PUSH_ENTITIES` | `power`, `height`, `target`, `radius` | Knockback in an AoE. |
| `PULL_ENTITIES` | `power`, `target`, `radius` | Pull entities toward caster. |
| `GIVE_COINS` / `TAKE_COINS` | `amount`, `target` | Economy transactions. |
| `IGNITE` | `ticks`, `target` | Sets target on fire. |
| `LAUNCH_PLAYER` / `LAUNCH_PROJECTILE` | `power`, `direction`, `target` | Velocity-based knockback. |
| `AOE_MINE` | `radius`, `destroy` | Breaks blocks in a radius. |

### 2.6 Script DSL — Conditions, Events, Variables

All built on the `ScriptModule` (id: `"script"`), loaded first.

**Conditions** (via `ConditionParser`): `tag`, `health`, `hunger`, `location`, `zone`, `variable`, `objective`, `quest`, `point`, and raw expressions.

**Events** (via `EventParser` into `CompiledEvent`): `give`, `tag`, `variable`, `condition`, `teleport`, `spawn_mob`, `stat_modify`, `run_script`, `foreach`. All support `notify`, `delay:<ticks>`, and `conditions:<inline>`.

**Variable namespaces** (via `VariableProvider`):
- `$player.*` — name, world, ping, biome, `$player.stat.<STAT_ID>`, `$player.skill.<id>.*`, `$player.hp`, `$player.max_hp`, `$player.health_percent`, `$player.mana`, `$player.var.<key>`, `$player.last_damage`, `$player.weapon_damage`.
- `$target.*` — type, health, max_health, level, name.
- `$dmg.*` — base_damage, strength, crit_chance, crit_damage, defense (from `DamageModifierContext`).
- `$prop.*` — GUI session properties (transient, lost on close).
- `$param.*``, `$system.time`, `$world.*`, `$server.*`, `$time.*`, `$curve.level`, `$range.<start>.<end>`.

**ExecutionContext key-value store** — `get(key)`, `set(key, value)`, `remove(key)`, parent-child inheritance. `DamageCalculator.buildFormulaContext()` already uses this (`dmg:*` keys). `SimpleExecutionContext` has a 5-arg constructor for child contexts.

### 2.7 Stat Roles — `stats/core.yml` → `stat_roles:` section

Maps semantic role names to actual stat IDs. 15 original roles default to identity mapping. New roles can be added via YAML.

### 2.8 XP Curves — `skills/xp_curves.yml`

Each curve is either explicit `thresholds: [...]` or `formula: "<expr>"` + `max-level`. `$curve.level$` variable available in formulas.

### 2.9 Enchant Logic Factories — `enchants/*.yml`

Enchants define `logic: valmora:<factory-id>` with optional `logic-params:`. Factories: `stat_bonus`, `damage_multiplier`, `defense_reduction`. Enchants fire `modifyAttack`, `modifyDefend`, `onPostAttack`, `onPostDefend` hooks on the `DamageModifierContext`.

### 2.10 Reforge/Forge Costs — `enchant/forge_costs.yml`

Maps rarity name → coin cost for the reforge machine.

---

## 3. What Cannot Be Changed Without Code

Despite all the data-driven infrastructure above, these aspects are still hardcoded in Java:

### 3.1 Pipeline Structure Itself

The **sequence of stages** in `CombatListener.onEntityDamageByEntity()` is fixed Java code. You cannot insert a new stage, remove the invulnerability check, reorder stages, or skip the vanilla damage-zeroing or the 5.0x environmental scaling.

### 3.2 Damage Type Mapping (Attacker Hit)

`CombatListener.java:42-44` — only two-way mapping: `ARROW`/`MOB_PROJECTILE` → `PROJECTILE`, else `MELEE`. Cannot add mappings for "trident = PIERCING" without Java.

### 3.3 Environmental Damage Scaling

`DamageCalculator.java:193` — `double multiplier = 5.0;` is hardcoded. No YAML config.

### 3.4 Invulnerability Frames

- `CombatListener.java:26-29` — `noDamageTicks > maxNoDamageTicks / 2.0F` check is hardcoded.
- `DamageApplier.java:48` — `setNoDamageTicks(20)` is hardcoded.

### 3.5 Combat Timer

- `DamageApplier.java:39` — `setInCombat()` uses a hardcoded 3-second duration.
- `RegenTask.java:39` — health regen disabled during combat via `isInCombat()`.

### 3.6 Damage Indicator Format

- `DamageIndicatorManager.java:71-81` — text is hardcoded (`✧` symbols for crits). The color comes from `DamageType.getColor()`, but the format template is not configurable. 400ms rate-limit and 1-second lifetime are hardcoded constants.

### 3.7 Critical Hit Logic

- `DamageCalculator.java:98` — `Math.random() < critChance / 100.0`. No "guaranteed crit after 3 hits" or "crit on every 5th attack."
- No hit counter stored on entities. `CombatTracker` only stores the last damage dealt per player UUID.

### 3.8 No Pre-Damage or Post-Damage Pipeline Hooks

There is **no YAML-defined list of stages** that fire before damage calculation, after damage application, or on mob death, beyond the existing `damage_types/*.yml` on-hit events (which fire during calculation for all hits of that type).

### 3.9 No Hit Counting or Hit-Based State on Entities

No system tracks:
- How many times a mob has been hit by a specific player.
- How many consecutive hits a player has landed.
- Hit streaks or combo counters on the target.

### 3.10 Loot Table Limitations

`LootEntry` is a simple struct: `item`, `min-amount`, `max-amount`, `chance`, `luck-affected`. No conditions, no formula-based amounts, no NBT customization, no XP-orb customization beyond `baseXp * level`.

### 3.11 No Damage Reflection / Absorption / Transfer

`DamageResistanceComponent` supports setting resistance percentages, but there is **no mechanic** for reflecting damage, absorbing damage up to a cap, or transferring damage to nearby enemies.

### 3.12 No Ability to Cancel or Modify Damage from YAML

Once `EntityDamageByEntityEvent` fires at `HIGHEST` priority, Valmora always applies damage. No `conditions:` check in the combat pipeline can cancel a hit, reduce damage to a flat value, or swap damage types based on conditions.

### 3.13 ON_HIT Only on Items, Not Mob Attacks

The `CombatListener` fires `ON_HIT` only for the **attacker player's held item**. Non-boss mobs have **zero** ability triggers — they cannot apply on-hit effects, gain temporary buffs, or trigger scripted events on damage.

### 3.14 ON_DAMAGE_TAKEN Not Wired

`AbilityTrigger.ON_DAMAGE_TAKEN` exists in the enum (`AbilityTrigger.java:14`) but is explicitly marked "wired in a later phase." The `CombatListener` does not fire it.

### 3.15 Stat Modification During Combat

`StatManager.recalculateStats()` is called on equipment changes, join, and respawn — **not** on every stat modification. A "gain +10 strength on hit" effect won't be reflected in the next hit's damage until re-equip/rejoin.

### 3.16 No Event Bus for Custom Combat Hooks

No Bukkit event for "valmora damage calculated" or "valmora damage applied." External plugins cannot listen for Valmora's custom damage pipeline events.

---

## 4. Proposed: `config.yml` → `combat-pipeline` Section

The proposal is to add a new top-level section to `config.yml` that defines a list of **pipeline stages** — things that happen at specific insertion points during a mob hit:

```yaml
combat-pipeline:
  stages:
    - id: pre_damage_1
      when: pre_damage
      conditions:
        - "tag berserker_activated"
        - "$player.stat.HEALTH$ < $player.stat.HEALTH$ * 0.25"
      on-pass:
        - "variable set prop.dmg_mult 1.5"
        - "notify <red>Berserker Rage! Damage increased!</red>"
      on-fail:
        - "variable set prop.dmg_mult 1.0"
      after:
        - "variable remove prop.dmg_mult"

    - id: combo_counter
      when: on_dmg_dealt
      conditions:
        - "caster is player"
      on-pass:
        - "counter increment player.var.combo on caster"

    - id: post_application_buff
      when: post_application
      conditions:
        - "$player.var.combo$ >= 10"
      on-pass:
        - "variable set player.var.combo 0"
        - "apply_potion strength 1 100"
        - "notify <gold>Combo x10! Strength for 5s!</gold>"

    - id: death_rewards
      when: on_death
      on-pass:
        - "stat_modify add combat $target.xp_reward$"
        - "give EMERALD:3 notify"
        - "spawn_mob ash_dragon 1 radius:8"
```

### Insertion Points

| `when:` value | Where it fires | Variables available |
|---|---|---|
| `pre_damage` | Before `DamageCalculator.calculateDamage` | `$player.*`, `$target.*`, `$dmg.*`, `$prop.*` |
| `post_calculation` | After `DamageResult` is created, before `apply()` | All above + `$dmg.final_damage$`, `$dmg.is_critical$`, `$dmg.damage_type$` |
| `post_application` | After `DamageApplier.applyDamage()` | All above + `$target.current_health$`, `$target.is_alive$` |
| `on_dmg_dealt` | After damage dealt by attacker (parallel to ON_HIT) | Same as post_application, plus `$player.last_damage$` |
| `on_death` | In `MobDeathListener` after loot rolls | `$mob.id$`, `$mob.level$`, `$target.max_health$` |

### How It Would Wire Into the Existing Pipeline

The `CombatListener` would gain a new method call at each insertion point:

```java
// In CombatListener.onEntityDamageByEntity, refactored:
// 1. Run pre_damage stages (may interrupt/cancel)
pipeline.runStages("pre_damage", context);
if (context.has("prop.cancel_damage") && (boolean)context.get("prop.cancel_damage")) return;

// 2. Calculate damage (existing code)
DamageResult result = DamageCalculator.calculateDamage(attacker, victim, damageType);

// 3. Run post_calculation stages
result.apply();  // apply damage
// Now run post_application stages

// etc.
```

A single `ExecutionContext` is built per hit, seeded with attacker/victim params, and `$dmg.*$` / `$prop.*`$ attachments are populated as the pipeline progresses.

### What Would Need to Be Added

#### New Variable Providers

| Variable | Namespace | Purpose |
|---|---|---|
| `$dmg.final_damage$` | `dmg` | `DamageResult.getFinalDamage()` set after calculation. |
| `$dmg.is_critical$` | `dmg` | `DamageResult.isCritical()`. |
| `$dmg.damage_type$` | `dmg` | `DamageResult.getDamageType().getId()`. |
| `$dmg.is_immune$` | `dmg` | `DamageResult.isImmune()`. |
| `$attacker.*` | `attacker` | Mirror of `$target.*` but for the attacker entity. |
| `$target.current_health$` | `target` | Post-application health (needs post_application hook). |
| `$target.is_alive$` | `target` | Boolean, post-application. |
| `$mob.id$` | `mob` | The victim mob's Valmora ID (read from PDC). |
| `$mob.level$` | `mob` | The victim mob's level. |
| `$combat.hit_count$` | `combat` | Hit counter stored on the entity or in context. |

#### New Event Factories

| Event | DSL Syntax | Purpose |
|---|---|---|
| `notify` | `notify "<message>"` | Sends a MiniMessage chat message to the player. |
| `counter` | `counter increment/reset <var> on <target> [delay:<ticks>]` | Increments or resets a named counter stored via PDC or context. |
| `entity` | `entity set <target> <property> <value>` | Sets entity properties (health, name, etc.). |
| `damage` | `damage <target> <amount> type:<type>` | Deals damage to a target (recursively, if within the pipeline). |
| `apply_potion` | `apply_potion <effect> <amplifier> <duration> [on <target>]` | Applies potion effects from the event DSL. |
| `damage_indicator` | `damage_indicator show "<text>" at <target> for <ticks>` | Spawns custom floating text. |
| `interrupt` | `interrupt` | Cancels the current hit (sets a flag checked by the pipeline dispatcher). |
| `fire_held_ability` | `fire_held_ability <trigger>` | Fires item abilities for a specific trigger. |
| `boss` | `boss <action>` | Triggers boss controller methods (`on_attack`, `on_damaged`, `on_death`). |

---

## 5. Benefits and Disadvantages of a Fully Customizable Damage Pipeline

### Benefits

| Benefit | Explanation |
|---|---|
| **Server admin game design freedom** | A server can redefine the entire combat "feel" — turn it into turn-based, bullet-hell, or tactical combat — without writing Java. |
| **Rapid iteration** | Combat designers tweak numbers, add conditions, and experiment by editing YAML + `/valmora reload`. No recompile cycle. Follows the same pattern as `damage_formula.yml`. |
| **Cross-system synergy** | Pipeline stages use the same `ConditionParser` and `EventParser` as items, quests, and GUIs, so admin-created triggers interact with tags, variables, stats, skills, economy, NPCs, quests, pets, collections, reforges. |
| **No new mechanic classes needed for common patterns** | "Grant a buff when below 30% HP" is a 3-line YAML condition+event. |
| **Hit-counting and stateful combat** | A `$combat.hit_count$` counter enables "after 3 hits, stun" or "on the 5th hit, deal 300% damage." |
| **Player-driven combat customization** | Items could grant "pipeline stage" modifiers via a new `EXECUTE` mechanic. |
| **Backward compatibility** | If `combat-pipeline.stages` is empty or absent, the current hardcoded logic runs unchanged. |

### Disadvantages

| Disadvantage | Explanation |
|---|---|
| **Increased hot-path complexity** | `EntityDamageByEntityEvent` at `HIGHEST` priority is the most performance-critical listener. Adding N conditional evaluations per hit adds CPU cost proportional to server size × hit frequency. **Mitigation:** stages are pre-compiled at load time; conditions short-circuit. |
| **Debugging difficulty** | A misbehaving YAML stage (infinite-loop `run_script`, feedback loops) can silently break combat with no clear traceback. **Mitigation:** `EventParser` already logs unknown events; a `dry-run` mode or verbose logging option would help. |
| **Risk of feedback loops** | If stage A spawns a mob that attacks and stage B modifies a variable stage A reads, the system could loop. **Mitigation:** stages are one-directional (pre → post → death), and the `ExecutionContext` is per-hit. |
| **Configuration sprawl** | An admin debugging combat wrongness must read through `damage_types/*.yml`, `damage_formula.yml`, `config.yml` → `combat-pipeline.stages`, plus item/ability on-hit mechanics, plus enchant logic — all interacting. **Mitigation:** same sprawl already exists; pipeline centralizes one more axis. |
| **Testing burden** | Every new stage type needs integration testing for reload-safety and combat behavior. **Mitigation:** `ConditionGroup` and `CompiledEvent` are already unit-tested. |
| **Partial implementation creates confusion** | If the pipeline covers player→mob attacks but not environmental damage, or pre-damage but not post-death, admins expect symmetry that doesn't exist. |
| **Conflict with existing enchants** | `modifyAttack`/`modifyDefend` hooks run during `DamageCalculator.calculateDamage()`. If a pipeline `pre_damage` stage also modifies `DamageModifierContext`, the interaction ordering must be defined. |

---

## 6. What This Level of Freedom Enables

With a fully customizable combat pipeline, server admins can create things that currently require Java code or are impossible:

### Custom Combat Mechanics
- **Stance system** — "if attacker has tag `stance_ferocious`, convert 50% of damage to TRUE type."
- **Elemental reactions** — "if attacker is on fire and hits with MELEE, apply Fire Resistance to attacker."
- **Positional combat** — "if attacker is above target, +25% crit chance; if below, +25% damage."
- **Weapon-type specific procs** — "swords have 10% chance to apply `bleed` DoT; axes have 15% chance to cleave."
- **Status effect stacks** — "this mob gains 1 `vulnerability_stack` per hit, 10% damage taken per stack, cap 5, expires 10s after last hit."

### Dynamic Mob Behaviors
- **Phase-based bosses** — "at 70%, 40%, 20% health, the mob's `pre_damage` stage adds +50% damage and spawns minions."
- **Mob-specific combat rules** — "this mob takes 50% reduced damage from non-poison attacks; poison damage is amplified by 200%."
- **Player-state-dependent mob behavior** — "if the attacker has tag `slayer.boss_zombie_king`, this mob takes 3x damage."

### Cross-System Combo Mechanics
- **Skill → Combat pipeline** — "if player's Combat skill is level 50+, the `post_application` stage grants 5% chance per hit to spawn a lightning bolt on the target."
- **Quest → Combat pipeline** — "if player has active quest `hunter_preparation`, the `pre_damage` stage adds +50% damage vs animals."
- **Pet → Combat pipeline** — "if player has a pet equipped, the `on_dmg_dealt` stage has 3% chance to heal the pet for 10% of damage dealt."
- **Economy → Combat pipeline** — "if player has >10000 coins, the `pre_damage` stage grants +5% damage per 10000 coins (capped at 50%)."
- **Collection → Combat pipeline** — "if player has collected 100 zombie flesh, the `post_application` stage has 50% chance to double XP from zombie kills."

### Full Re-skinning of Combat
- **Turn-based combat** — A `pre_damage` stage that checks `$prop.turn_phase$` and only allows damage during the player's turn.
- **Resource-cost combat** — "consuming 10 mana per attack" — a `pre_damage` stage that checks mana and interrupts if insufficient.
- **Combo system** — `counter increment combo_count on target` in `on_dmg_dealt`, with conditions that trigger special attacks at combo counts 3, 5, 10.

---

## 7. Four Examples

### Example 1: Current Pipeline Reimplemented

This is the **exact current behavior** expressed as a combat-pipeline YAML section. If the implementation is correct, this should produce **zero behavioral change** from the current hardcoded pipeline.

```yaml
# combat-pipeline.stages in config.yml
# Faithfully reproduces the current hardcoded CombatListener +
# DamageCalculator + DamageApplier + DamageIndicatorManager + MobDeathListener logic.
stages:

  # --- Stage 1: Invulnerability frame check ---
  # CombatListener.java:26-29 — noDamageTicks > maxNoDamageTicks / 2 → cancel
  - id: check_invuln
    when: pre_damage
    conditions:
      - "$target.no_damage_ticks$ > $target.max_no_damage_ticks$ / 2.0"
    on-pass:
      - "interrupt"                        # Cancel the damage event entirely

  # --- Stage 2: Zero vanilla damage ---
  # CombatListener.java:40 — event.setDamage(0) to bypass Bukkit's damage
  - id: zero_vanilla_damage
    when: pre_damage
    on-pass:
      - "variable set prop.vanilla_damage 0"

  # --- Stage 3: Damage type mapping ---
  # CombatListener.java:42-44 — ARROW/MOB_PROJECTILE → PROJECTILE, else MELEE
  - id: map_damage_type
    when: pre_damage
    conditions:
      - "$event.damage_source$ == ARROW"
      - "$event.damage_source$ == MOB_PROJECTILE"
    on-pass:
      - "variable set prop.dmg_type PROJECTILE"
    on-fail:
      - "variable set prop.dmg_type MELEE"

  # --- Stage 4: Post-calculation state capture ---
  # After DamageCalculator.calculateDamage produces DamageResult
  - id: capture_result
    when: post_calculation
    on-pass:
      - "variable set prop.final_damage $dmg.final_damage$"
      - "variable set prop.is_critical $dmg.is_critical$"
      - "variable set prop.dmg_type $dmg.damage_type$"
      - "variable set prop.target_health_after $target.current_health$"
      - "variable set prop.attacker_health $player.hp$"

  # --- Stage 5: Damage application ---
  # DamageApplier.java: damage is applied here (Java, can't be YAML-only)
  # The pipeline reads results after application

  # --- Stage 6: Combat timer ---
  # DamageApplier.java:39 — setInCombat() with 3s timer
  - id: set_combat_timer
    when: post_application
    conditions:
      - "target is player"
    on-pass:
      - "variable set target.in_combat true"
      - "variable set target.combat_timer 60"   # 60 ticks = 3 seconds at 20 TPS

  # --- Stage 7: ON_HIT item abilities ---
  # CombatListener.java:51-58 — CombatTracker + AbilityExecutor.fireHeld(ON_HIT)
  - id: on_hit_abilities
    when: post_application
    conditions:
      - "caster is player"
    on-pass:
      - "variable set prop.last_damage_dealt $dmg.final_damage$"
      - "fire_held_ability ON_HIT"

  # --- Stage 8: Boss ability triggers ---
  # CombatListener.java:62-68
  - id: boss_triggers
    when: post_application
    on-pass:
      - "boss on_attack"
      - "boss on_damaged"

  # --- Stage 9: Damage indicator ---
  # DamageIndicatorManager.java:40-81
  - id: spawn_indicator
    when: post_application
    conditions:
      - "$dmg.is_critical$ == false"
    on-pass:
      - "damage_indicator show \"$dmg.damage_type.color$$dmg.final_damage$\" at target for 20"

  - id: spawn_critical_indicator
    when: post_application
    conditions:
      - "$dmg.is_critical$ == true"
    on-pass:
      - "damage_indicator show \"<gold>✧ <b>$dmg.final_damage$</b> ✧</gold>\" at target for 20"

  # --- Stage 10: Mob death & loot ---
  # MobDeathListener.java:31-84
  - id: on_mob_death
    when: on_death
    on-pass:
      - "stat_modify add combat $mob.xp_reward$"
      - "give_coins $mob.gold_reward$"
      - "roll_loot $mob.loot_table$"
      - "boss on_death"
```

> **Note on what's feasible:** Several pseudo-events above (`interrupt`, `damage_indicator show`, `fire_held_ability`, `boss on_attack`, `roll_loot`, `give_coins`, `$event.damage_source$`, `$target.no_damage_ticks$`, `$target.current_health$`, `$target.is_alive$`) require new Java infrastructure. The key point is the **structure**: every stage has a `when:` insertion point, `conditions:` evaluated with the existing `ConditionParser`, and `on-pass:` / `on-fail:` / `after:` event lists evaluated with the existing `EventParser`. The existing YAML-driven systems (`DamageType.fireOnHit()`, `DamageFormulaRegistry`, `damage_types/*.yml` on-hit events) already prove this architecture works — the pipeline just extends the pattern to the full hit lifecycle.

### Example 2: Enhanced Pipeline with Optional Additions

This builds on the current pipeline by adding **optional stages** that only fire under specific conditions, leaving the base pipeline intact otherwise. An admin can append these to their config without touching the core logic.

```yaml
combat-pipeline:
  stages:

    # --- Existing pipeline (unchanged — same as Example 1) ---

    # --- NEW: Berserker rage (pre-damage, conditional amplification) ---
    # When the attacker is below 25% HP, they deal 1.5x damage.
    - id: berserker_rage
      when: pre_damage
      conditions:
        - "caster is player"
        - "$player.hp$ < $player.max_hp$ * 0.25"
      on-pass:
        - "variable set prop.dmg_mult 1.5"
        - "notify <red>Berserker Rage! +50% damage below 25% HP!</red>"

    # --- NEW: Thorn coat (post-calculation, damage reflection) ---
    # If the target has the 'thorn_coat' tag and took >50 damage, reflect 30%.
    - id: thorn_coat
      when: post_calculation
      conditions:
        - "tag target has thorn_coat"
        - "$dmg.final_damage$ > 50"
      on-pass:
        - "damage $attacker$ $dmg.final_damage$ * 0.3 type:THORNS"
        - "notify <dark_green>Thorn Coat reflected damage!</dark_green>"

    # --- NEW: Combo counter (on damage dealt) ---
    # Track consecutive hits using a PDC-stored counter on the attacker.
    - id: combo_increment
      when: on_dmg_dealt
      conditions:
        - "caster is player"
        - "target is mob"
      on-pass:
        - "counter increment player.var.combo_count on caster"

    # --- NEW: Combo explosion on 10th hit ---
    - id: combo_explosion
      when: on_dmg_dealt
      conditions:
        - "caster is player"
        - "$player.var.combo_count$ >= 10"
      on-pass:
        - "variable set player.var.combo_count 0"
        - "apply_potion strength 1 200"           # 10 seconds of Strength I
        - "notify <gold>COMBO x10! Strength for 10s!</gold>"

    # --- NEW: Streak-based rare drops (on death) ---
    - id: streak_loot
      when: on_death
      conditions:
        - "$player.var.kill_streak$ >= 5"
        - "tag player has streak_master"
      on-pass:
        - "give NETHER_STAR:1"
        - "notify <light_purple>Epic drop! Streak mastery confirmed!</light_purple>"

    # --- NEW: XP multiplier on death (on death) ---
    - id: xp_boost
      when: on_death
      conditions:
        - "tag player has xp_boost_active"
      on-pass:
        - "stat_modify add combat $mob.xp_reward$ * 0.5"

    # --- NEW: Fancy critical hit indicator ---
    - id: fancy_crit_indicator
      when: post_application
      conditions:
        - "$dmg.is_critical$ == true"
      on-pass:
        - "damage_indicator show \"<gold>✧ <b>$dmg.final_damage$</b> ✧</gold>\" at target for 30"

    # --- NEW: Reset combat timer on hit (prevents regen cheese) ---
    - id: refresh_combat_timer
      when: post_application
      conditions:
        - "target is player"
      on-pass:
        - "variable set target.in_combat true"
        - "variable set target.combat_timer 60"
```

**What this enables:**

- **`berserker_rage`** reads `$player.hp$` and `$player.max_hp$` (via `PlayerVariableProvider`) and sets `prop.dmg_mult` as a context variable. If the `DamageFormulaRegistry` formula is updated to `\"(1 + $dmg.strength$ / 100) * $prop.dmg_mult$\"`, the amplification flows through the existing formula system.
- **`thorn_coat`** uses `$attacker$` (a new variable provider mirroring `$target.*` for the attacker entity) to deal reflection damage — impossible in the current system without an enchant logic.
- **`combo_increment`** uses a `counter` event factory that stores a count on the player via PDC, enabling "every 10th hit does X" patterns.
- **`streak_loot`** checks `$player.var.kill_streak$` — if a separate `ON_KILL` ability or another pipeline stage increments this, it persists across fights.

### Example 3: Completely Different Pipeline (Turn-Based Tactical Combat)

This example transforms Valmora's real-time combat into a **turn-based system** where players and mobs take turns. All existing damage formula, resistance, and stat scaling still apply — only the *timing* and *permission* to deal damage changes.

```yaml
combat-pipeline:
  # Global state: $prop.turn_phase$ tracks whose turn it is.
  # Values: "player_turn", "enemy_turn", "resolving"

  stages:

    # --- Phase 1: Block player damage during enemy turn ---
    - id: turn_gate_player
      when: pre_damage
      conditions:
        - "$prop.turn_phase$ != player_turn"
        - "caster is player"
      on-pass:
        - "interrupt"
        - "notify <gray>It's not your turn!</gray>"

    # --- Phase 2: Block mob damage during player turn ---
    - id: turn_gate_mob
      when: pre_damage
      conditions:
        - "$prop.turn_phase$ != enemy_turn"
        - "caster is mob"
      on-pass:
        - "interrupt"   # Mobs can't act on the player's turn

    # --- Phase 3: Enforce one action per player turn ---
    - id: action_limit
      when: pre_damage
      conditions:
        - "$prop.turn_phase$ == player_turn"
        - "caster is player"
        - "$player.var.turn_actions$ >= 1"
      on-pass:
        - "interrupt"
        - "notify <gray>You've already acted this turn!</gray>"

    # --- Phase 4: Register the player's action ---
    - id: register_action
      when: pre_damage
      conditions:
        - "$prop.turn_phase$ == player_turn"
        - "caster is player"
      on-pass:
        - "counter increment player.var.turn_actions on caster"
        - "notify <white>Your turn. Action 1/1 used.</white>"

    # --- Phase 5: End player turn after action ---
    - id: end_player_turn
      when: post_application
      conditions:
        - "caster is player"
      on-pass:
        - "variable set prop.turn_phase enemy_turn"
        - "notify <dark_red>Enemy's turn...</dark_red>"
        # Schedule turn reset after a short delay (creates pace in real-time)
        - "run_script 20 1 variable set prop.turn_phase player_turn"
        - "run_script 20 1 variable set player.var.turn_actions 0"

    # --- Phase 6: Enemy AI turn — auto-attack nearest player ---
    - id: enemy_ai
      when: pre_damage
      conditions:
        - "$prop.turn_phase$ == enemy_turn"
        - "caster is mob"
      on-pass:
        - "notify <gray>The enemy attacks!</gray>"
        # Mob deals its normal damage — pipeline doesn't interfere further

    # --- Phase 7: End enemy turn ---
    - id: end_enemy_turn
      when: post_application
      conditions:
        - "caster is mob"
      on-pass:
        - "variable set prop.turn_phase player_turn"
        - "notify <green>Your turn!</green>"

    # --- Phase 8: Turn reset on mob death ---
    - id: turn_reset_on_kill
      when: on_death
      on-pass:
        - "variable set prop.turn_phase player_turn"
        - "variable set player.var.turn_actions 0"
        - "notify <gold>Turn reset! Your turn.</gold>"

    # --- Phase 9: Player death — enemy turn continues ---
    - id: player_down
      when: post_application
      conditions:
        - "target is player"
        - "$target.current_health$ <= 0"
      on-pass:
        - "variable set prop.turn_phase resolving"
        - "notify <red>You were defeated!</red>"
```

**Design rationale:**

- The `turn_gate_player` stage cancels (`interrupt`) any player-initiated damage outside of `player_turn`, making combat strictly turn-based.
- The `action_limit` stage enforces one action per turn using a `player.var.turn_actions` counter.
- The `run_script 20 1` event (existing factory from `RunScriptEventFactory.java`) schedules a 1-second-delayed turn reset, creating a pseudo turn-based rhythm within the real-time engine.
- Enemy AI is simulated: `caster is mob` condition + `enemy_turn` phase gate means mobs only act during their turn.
- On mob death, the turn resets to the player — creating a natural flow.
- All damage calculation, resistance, and stat scaling still use the existing `damage_formula.yml` and `DamageResistanceComponent` — only the **timing** and **permission** to deal damage changes.

### Example 4: "Crazy" Pipeline — Maximum Freedom Demonstration

This example doesn't need to make game-design sense. It's designed to push the pipeline system to its absolute limits, combining every feature: conditional damage type conversion, hit-counters, random proc chains, stat modification, entity spawning, cross-entity state sharing, and recursive stage triggering.

```yaml
combat-pipeline:
  stages:

    # --- Stage A: Damage Type Transmutation ---
    # If the attacker is holding an item with the "chaos_orb" tag,
    # randomly convert the damage type on every hit using $system.time$ as a seed.
    - id: chaos_transmute
      when: pre_damage
      conditions:
        - "caster has tag chaos_orb_wielder"
        - "$player.stat.HEALTH$ > 50"
        - "tag target does_not_have chrono_locked"
      on-pass:
        - "variable set prop.original_type $dmg.damage_type$"
        - "condition $system.time$ % 4 == 0"
        - "variable set prop.dmg_type FIRE"
        - "condition $system.time$ % 4 == 1"
        - "variable set prop.dmg_type POISON"
        - "condition $system.time$ % 4 == 2"
        - "variable set prop.dmg_type MAGIC"
        # Remainder stays as original type
        - "notify <gradient:red,orange,yellow>Chaos Bolt!</gradient>"
      on-fail:
        - "variable set prop.dmg_type $prop.original_type$"

    # --- Stage B: Dual Hit Counter with State Decay ---
    # Increment a PDC-stored hit counter on both attacker AND target.
    # If the target has been hit 5+ times in the last 10 seconds, apply vulnerability.
    - id: dual_counter
      when: pre_damage
      conditions:
        - "caster is player"
      on-pass:
        - "counter increment combat:hits_on_target on target"
        - "counter set combat:last_hit_time on target to $system.time$"
        - "counter increment combat:hits_by_player on caster"
      - id: vulnerability_check
        when: pre_damage
        conditions:
          - "$combat.hits_on_target$ >= 5"
          - "$system.time$ - $combat.last_hit_time$ < 10000"
        on-pass:
          - "apply_potion vulnerability 1 100"   # 5 seconds
          - "notify <dark_red>$target.name$ is vulnerable!</dark_red>"
          - "counter reset combat:hits_on_target on target"
          - "tag add target hit_streak_done"

    # --- Stage C: Desperation Damage Scaling ---
    # The lower the attacker's health, the more damage they deal.
    - id: desperation
      when: post_calculation
      conditions:
        - "caster is player"
        - "$player.hp$ < $player.max_hp$ * 0.5"
      on-pass:
        - "variable set prop.desperation_mult 2.0 - ($player.hp$ / $player.max_hp$)"
        - "variable set prop.final_damage $dmg.final_damage$ * $prop.desperation_mult$"
        - "damage_indicator show \"<dark_red>DESPERATION! x$prop.desperation_mult$</dark_red>\" at caster for 20"
      on-fail:
        - "variable set prop.desperation_mult 1.0"

    # --- Stage D: Recursive Echo Proc ---
    # On a critical hit, ~33% chance to trigger a secondary "echo" hit
    # that deals 30% damage of the same type. Uses tag-based immunity to prevent
    # infinite recursion.
    - id: echo_proc
      when: post_calculation
      conditions:
        - "$dmg.is_critical$ == true"
        - "$system.time$ % 3 == 0"
        - "tag target does_not_have echo_immune"
      on-pass:
        - "tag add target echo_immune"
        - "damage $target$ $dmg.final_damage$ * 0.3 type:$prop.dmg_type$"
        - "run_script 5 1 tag remove target echo_immune"   # Remove immunity after 5 ticks
        - "notify <light_purple>Echo Damage!</light_purple>"

    # --- Stage E: Stolen Strength on Hit ---
    # Steal 5% of the damage dealt as temporary strength (capped at +100 strength).
    - id: strength_drain
      when: on_dmg_dealt
      conditions:
        - "caster is player"
        - "$dmg.final_damage$ > 100"
      on-pass:
        - "counter add player.var.stolen_strength $dmg.final_damage$ * 0.05"
        - "stat_modify add strength $dmg.final_damage$ * 0.05"
        - "condition $player.var.stolen_strength$ > 100"
        - "stat_modify set strength 100"
        - "notify <gold>Stolen strength! +$dmg.final_damage$ * 0.05$ STR</gold>"

    # --- Stage F: Mob Evolution on Critical Hit Chain ---
    # If a mob takes 3 critical hits within 30 seconds, it "evolves":
    # gains +50% max health, +25% damage, and a golden name prefix.
    - id: mob_evolution
      when: on_dmg_dealt
      conditions:
        - "target is mob"
        - "$dmg.is_critical$ == true"
        - "$mob.id$ != null"
      on-pass:
        - "counter increment combat:crit_hits on target"
        # Timer ticks every second via run_script
        - "run_script 20 1 counter increment combat:evolution_timer on target"
        - "condition $combat.evolution_timer$ <= 600 && $combat.crit_hits$ >= 3"
        - "entity set target.max_health target.max_health * 1.5"
        - "entity set target.damage_multiplier 1.25"
        - "entity set target.name \"<gold>Elite $target.original_name$</gold>\""
        - "counter reset combat:crit_hits on target"
        - "counter reset combat:evolution_timer on target"
        - "notify <gold>$target.name$ has evolved!</gold>"
        - "give FIRE_CHAR:1"

    # --- Stage G: Phoenix Rebirth on Death ---
    # If a mob "dies" but has the "phoenix" tag, it resurrects with 50% health
    # instead of dropping loot. Uses $math.random$ for a 75% success chance.
    - id: phoenix_rebirth
      when: on_death
      conditions:
        - "tag target has phoenix"
        - "tag target does_not_have reborn"
        - "$math.random$ < 0.75"
      on-pass:
        - "entity set target.health target.max_health_current * 0.5"
        - "entity set target.prevent_death true"   # Prevents actual death flag
        - "tag add target reborn"
        - "apply_potion resistance 2 200"
        - "notify <gold>$target.name$ has reborn!</gold>"

    # --- Stage H: Chaos Death Explosion ---
    # On death, if the mob had the "chaos_core" tag, explode with random effects:
    # random damage type, random area effect, random loot.
    - id: chaos_death
      when: on_death
      conditions:
        - "tag target has chaos_core"
      on-pass:
        - "condition $system.time$ % 3 == 0"
        - "damage $nearby_enemies$ $dmg.final_damage$ * 0.5 type:FIRE"
        - "condition $system.time$ % 3 == 1"
        - "damage $nearby_enemies$ $dmg.final_damage$ * 0.5 type:POISON"
        - "condition $system.time$ % 3 == 2"
        - "damage $nearby_enemies$ $dmg.final_damage$ * 0.5 type:MAGIC"
        - "spawn_mob firework_rocket 1 radius:0"   # Visual: explosion particle effect
        - "give NETHER_STAR:1"
        - "give DIAMOND:1"
        - "notify <red><b><shake>CHAOS EXPLOSION!</shake></b></red>"

    # --- Stage I: Player Transformation Cascade ---
    # Landing a killing blow while the attacker has "transformation" active:
    # gain a random buff, spawn a minion, increase a permanent stat,
    # and recursively check if the minion's spawn triggers another stage.
    - id: transformation_cascade
      when: on_death
      conditions:
        - "caster has tag transformation_active"
        - "$player.var.transformations_this_life$ < 10"
      on-pass:
        - "variable set prop.random_buff $system.time$ % 5"
        - "condition $prop.random_buff$ == 0"
        - "apply_potion strength 1 6000"            # 5 minutes
        - "condition $prop.random_buff$ == 1"
        - "apply_potion speed 1 6000"
        - "condition $prop.random_buff$ == 2"
        - "apply_potion resistance 1 6000"
        - "condition $prop.random_buff$ == 3"
        - "apply_potion jump_boost 1 6000"
        - "stat_modify add DAMAGE 5"
        - "counter increment player.var.transformations_this_life on caster"
        - "spawn_mob loyal_minion 1 radius:2"
        # Check if the minion trigger should fire recursively
        - "notify <gradient:gold,yellow>TRANSFORMATION! (x$player.var.transformations_this_life$)</gradient>"

    # --- Stage J: Reality Anchor (Global Safety) ---
    # Prevents infinite loops by capping total recursive damage in one pipeline execution.
    - id: reality_anchor
      when: pre_damage
      conditions:
        - "$prop.recursion_depth$ > 10"
      on-pass:
        - "interrupt"
        - "notify <dark_gray>Reality stabilizes...</dark_gray>"
      default:
        - "counter increment prop.recursion_depth"
```

**What this example demonstrates:**

- **Conditional damage type conversion** (Stage A) — `$dmg.damage_type$` read before calculation, `$prop.dmg_type$` written to affect the formula.
- **Hit counting with state decay** (Stage B) — `combat:hits_on_target` counter on the target PDC, `combat:last_hit_time` timer, vulnerability debuff applied.
- **Dynamic damage scaling** (Stage C) — `$player.hp$` relative to `$player.max_hp$` produces a desperation multiplier applied to `$dmg.final_damage$`.
- **Recursive damage** (Stage D) — the `damage` event factory triggers a new hit, which re-enters the pipeline. The `echo_immune` tag + `run_script` delay prevents infinite recursion.
- **Stat modification** (Stage E) — `stat_modify add strength` from within the pipeline, with a cap enforced by a `condition`.
- **Mob evolution** (Stage F) — `counter increment combat:crit_hits on target`, `entity set target.max_health`, `entity set target.name` — all from YAML.
- **Phoenix rebirth** (Stage G) — `$math.random$` (a new variable) + tag-based state to prevent double-resurrection.
- **Chaos death explosion** (Stage H) — `$nearby_enemies$` selector + random damage type selection via `$system.time$ % 3`.
- **Recursive cascade** (Stage I) — `spawn_mob loyal_minion` inside the death stage, which (if the minion has its own pipeline stages) could trigger further recursion.
- **Safety net** (Stage J) — `reality_anchor` caps recursion depth at 10 to prevent stack overflow / infinite loops.

The `$prop.recursion_depth$` counter is incremented at the start of every `pre_damage` stage. If 10 recursive damage calls happen within a single hit's pipeline, the anchor fires `interrupt` to break the chain.

---

## 8. Cross-System Synergies

The combat pipeline, because it uses the same `ConditionParser` and `EventParser` as every other system, automatically integrates with:

| System | Integration Point | Example Use |
|---|---|---|
| **Items** | `ON_HIT` abilities on held weapon | A sword that adds its own `on_dmg_dealt` stage: "on hit, 10% chance to apply Bleed." |
| **Enchants** | `modifyAttack` / `onPostAttack` hooks | A custom enchant that intercepts pipeline stages: "this enchant adds a pre_damage stage that converts 20% damage to heal." |
| **Skills** | `$player.skill.combat.level$` variable | "Combat 50+: +15% crit damage on all pipeline hits." |
| **Quests** | `$quest.<id>.status$` condition | "While quest `dragon_slayer` is active: +100% damage vs dragons." |
| **Pets** | `$pet.equipped$` variable | "While pet is equipped: `on_dmg_dealt` stage heals pet for 5% of damage." |
| **Collections** | `stat_modify` and `give` events | "Zombie Flesh collection 100: `post_calculation` stage adds +5% damage vs undead." |
| **Economy** | `give_coins` / `take_coins` events | "Each critical hit has 5% chance to drop 10-50 coins." |
| **Reforges** | Custom reforge that adds pipeline stages | "The [Vengeful] reforge adds: on damage taken, 30% chance to deal 50% damage back." |
| **Slayer system** | `$slayer.active_badge$` variable | "While a slayer quest is active, `pre_damage` converts 25% of damage to true damage vs the target." |
| **Progression** | `stat_modify` events | "Each kill in a combo adds +2 permanent strength (capped)." |
| **Alchemy** | Potion effects | "While under the effect of Battle Elixir, the `post_calculation` stage adds +20% damage." |
| **Zones** | `zone <name>` condition | "In the Crimson Arena, `pre_damage` doubles all damage." |

---

## 9. Implementation Requirements

### 9.1 Minimal Viable Implementation (MVP)

To implement the core pipeline concept with just the existing infrastructure:

1. **New `CombatPipeline` class** — loads `config.yml` → `combat-pipeline.stages`, pre-compiles each stage's `conditions:` into `ConditionGroup` and `on-pass:`/`on-fail:`/`after:` into `CompiledEvent` lists (same pattern as `DamageFormulaRegistry`).
2. **New insertion points in `CombatListener`** — 3 method calls: `pipeline.runStages("pre_damage", ctx)`, `pipeline.runStages("post_calculation", ctx)`, `pipeline.runStages("post_application", ctx)`.
3. **Extend `DamageVariableProvider`** — add `final_damage`, `is_critical`, `damage_type`, `is_immune` to the `dmg:*` context keys.
4. **New `interrupt` event factory** — sets a flag on the context that `CombatListener` checks after `pre_damage` stages.
5. **New `notify` event factory** — sends a MiniMessage chat message to the player.

This MVP alone unlocks: conditional damage amplification, on-hit effect triggers, death-trigger effects, combo counters (with existing `ON_HIT`), and stat modification — all without new mechanic classes.

### 9.2 Extended Implementation

Adds:
- `on_dmg_dealt`, `on_death` insertion points in `CombatListener` / `MobDeathListener`.
- New variable providers: `$attacker.*`, `$mob.*`, `$combat.*`.
- New event factories: `counter`, `entity`, `apply_potion`, `damage`, `damage_indicator`, `fire_held_ability`, `boss`.
- `$player.var.*` persistence via PDC (already partially supported by `PlayerVariableProvider`).
- `$math.random$` variable provider for RNG-based procs.
- `$nearby_enemies$` selector variable for AoE effects.
- `$target.no_damage_ticks$` / `$target.max_no_damage_ticks$` for iframe manipulation.

### 9.3 Full Implementation

Adds:
- `$target.current_health$` / `$target.is_alive$` — requires post-application hook in `DamageApplier`.
- `$event.damage_source$` — requires Bukkit event details exposed to the pipeline context.
- `damage_indicator show` event factory — spawns custom `TextDisplay` entities.
- `roll_loot` event factory — rolls loot table entries as script events.
- `give_coins` event factory — economy transactions in the DSL.
- `run_script` with `delay:` on individual events within a stage's event list (already supported by `EventOptions`).
- `entity set` event factory — sets entity properties (health, name, attributes) from script.

Each of these builds on existing infrastructure (the `EventFactory` interface, `EventParser`, `CompiledEvent`, `ConfigurationSection` params) and follows the established pattern of one factory class per DSL command, registered in `ScriptModule.onEnable()`.

---

*Generated from analysis of the Valmora codebase (August 2026). File references are to `src/main/java/` and `src/main/resources/` paths within the plugin. See also `docs/REFACTOR/PROGRESS.md` for the broader refactor context (the original `REFACTOR_BLUEPRINT.md` planning doc was removed once the refactor it planned was complete). For the shipped shape of the system this document proposed, see `docs/modules/design/pipeline.md`.*