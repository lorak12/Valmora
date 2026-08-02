# Combat Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `combat` | **Source:** `src/main/java/org/nakii/valmora/module/combat/`

---

## Table of Contents

1. [Overview](#overview)
2. [Code Structure](#code-structure)
3. [Architecture & Key Classes](#architecture--key-classes)
4. [Configuration (YAML)](#configuration-yaml)
5. [Data Model / Persistence](#data-model--persistence)
6. [API Exposed](#api-exposed)
7. [Dependencies & Consumers](#dependencies--consumers)
8. [Unfinished Things / TODOs](#unfinished-things--todos)
9. [Possible Improvements / Changes](#possible-improvements--changes)

---

## Overview

The Combat module is Valmora's **central damage pipeline and combat-feel layer**. It is the `ReloadableModule` that:

1. **Owns the damage calculation formula** (`DamageCalculator`) — the single place where a hit's damage is derived from base damage, strength, crit chance/damage, defense, enchant modifiers, and per-mob damage-type resistances.
2. **Intercepts vanilla Minecraft damage events** (`CombatListener`) — both entity-vs-entity attacks and environmental damage (fall, fire, lava, drowning, void, explosions, poison, wither, magic, projectiles). The vanilla event is zeroed and the Valmora pipeline takes over.
3. **Applies the computed damage** (`DamageResult.apply()` → `DamageApplier`) — to the profile's **virtual health** for players (mirrored to vanilla hearts at a 10-heart scale) or directly to entity health for mobs/others.
4. **Renders floating damage numbers** (`DamageIndicatorManager`) using Paper **`TextDisplay` entities** (per AGENTS.md §11.17) — color-coded per damage type, with a gold "✧" treatment for critical hits and a 400 ms per-victim rate limiter.
5. **Provides the out-of-combat health/mana regeneration heartbeat** (`RegenTask`) — scheduled by the **PlayerManager**, not by the combat module itself, but the task lives in this package.

The module is deliberately **thin as a lifecycle**: it registers one listener (`CombatListener`) and owns the indicator manager. The heavy logic is all in `public static` utility classes and data objects in the same package, which other modules (item abilities, projectile abilities, enchant logic) call **directly**, not through `ValmoraAPI`.

**Packet note (AGENTS.md §11.1):** the combat module uses **no packet code whatsoever**. There is no ProtocolLib, no PacketEvents API usage, and no NMS/`net.minecraft` reflection here. PacketEvents is bootstrapped plugin-wide in `Valmora.onLoad()`/`onEnable()` (`Valmora.java:80-83`, `:129`) and used by the NPC **dialogue** module's `intercept/` package (`module/npc/dialogue/intercept/ConversationPacketManager.java`), but the combat engine both **detects hits via Bukkit events** and **renders indicators via real `TextDisplay` entities** — no packets are sent or intercepted for damage.

Per the module registration order (`Valmora.java:188-222`, documented in `docs/MODULE_DEVELOPMENT.md` §9), `combat` is registered **after** `skill` and before `gui`:

```
... → mob → skill → combat → gui → recipe → enchant → ...
```

`CombatModule` is instantiated at `Valmora.java:159`, registered at `Valmora.java:200`.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/combat/
├── CombatModule.java             # ReloadableModule — lifecycle, owns listener + indicator manager
├── CombatListener.java           # Bukkit Listener — intercepts damage events at HIGHEST/HIGH
├── DamageCalculator.java         # Static damage pipeline (3 overloads) + mob PDC lookup
├── DamageModifierContext.java    # Mutable pre-hit context passed to enchant logic
├── DamageResult.java             # Immutable hit result + apply() → DamageApplier
├── DamageApplier.java            # Applies a DamageResult to a player/mob victim
├── DamageType.java               # Damage type enum with per-type MiniMessage colors
├── DamageIndicatorManager.java   # TextDisplay floating damage numbers
└── RegenTask.java                # 1s out-of-combat health/mana regen (scheduled by PlayerManager)

src/test/java/org/nakii/valmora/module/combat/
└── DamageCalculatorTest.java     # 5 tests for the damage formula (mocked ValmoraAPI)
```

**Note on the planned `hider/`, `intercept/`, `renderer/` subpackages:** these do **not** exist in the combat module (checked against the actual tree). The only `intercept/` package in the codebase belongs to the NPC dialogue module (`module/npc/dialogue/intercept/`). All indicator rendering lives in the single `DamageIndicatorManager` class, and there is no "hit hiding" or packet-interception subsystem. Any doc reference to those subpackages should be treated as stale.

### File-by-file

**`CombatModule.java`** (44 lines) — the `ReloadableModule`. The constructor (`CombatModule.java:12-16`) builds the `DamageIndicatorManager` and `CombatListener` eagerly; `onEnable()` (`CombatModule.java:18-22`) registers the listener with the plugin manager; `onDisable()` (`CombatModule.java:24-29`) unregisters the listener via `HandlerList.unregisterAll(...)` **and** calls `damageIndicatorManager.cleanup()`. `getId()` returns `"combat"` (`CombatModule.java:31-34`); `getName()` returns `"Combat Engine"` (`CombatModule.java:36-39`). The only public accessor is `getDamageIndicatorManager()` (`CombatModule.java:41-43`). Note there is **no** public accessor for `CombatListener` or the calculator — the calculator is a static API.

**`CombatListener.java`** (117 lines) — the entry point that turns Bukkit damage events into Valmora hits. Two handlers:

- `onEntityDamageByEntity(EntityDamageByEntityEvent)` at **`EventPriority.HIGHEST`**, `ignoreCancelled = true` (`CombatListener.java:18`):
  - Requires the victim to be a `LivingEntity` (`CombatListener.java:22-24`).
  - **Invulnerability gate:** if `victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2.0F` the event is cancelled outright — no damage, no indicator, no ability triggers (`CombatListener.java:26-29`).
  - Resolves the attacker: a direct `LivingEntity` damager, or a `Projectile` whose shooter is a `LivingEntity` (`CombatListener.java:31-37`).
  - If an attacker resolved: zeroes the vanilla event (`event.setDamage(0)`, `CombatListener.java:40`), maps the damage source to `PROJECTILE` for `ARROW`/`MOB_PROJECTILE` damage types or `MELEE` otherwise (`CombatListener.java:42-44`), calls `DamageCalculator.calculateDamage(attacker, victim, damageType)` (`CombatListener.java:46`), applies it (`CombatListener.java:47`), spawns an indicator (`CombatListener.java:49`), records the hit in `CombatTracker` and fires the weapon's **ON_HIT** abilities (`CombatListener.java:51-59`), and finally fires boss `ON_ATTACK`/`ON_DAMAGED` hooks (`CombatListener.java:61-68`).
- `onEntityDamage(EntityDamageEvent)` at **`EventPriority.HIGH`**, `ignoreCancelled = true` (`CombatListener.java:72`) — the **environmental** path:
  - Rejects `EntityDamageByEntityEvent` subtypes so entity-vs-entity damage is never double-handled (`CombatListener.java:74-76`).
  - Same invulnerability gate (`CombatListener.java:79-82`).
  - Uses the **vanilla** damage as input: `event.getDamage()`, returning early if `<= 0` (`CombatListener.java:84-85`), zeroes the event (`CombatListener.java:87`), maps `DamageCause` → `DamageType` via `mapCauseToType` (`CombatListener.java:89`), calls the environmental `DamageCalculator` overload (`CombatListener.java:90`), applies it (`CombatListener.java:91`), and if the victim is fire/lava-immune stops them burning (`setFireTicks(0)`, `CombatListener.java:94-96`), then spawns an indicator (`CombatListener.java:98`).
  - `mapCauseToType` (`CombatListener.java:102-116`) maps `FALL→FALL`, `FIRE/FIRE_TICK→FIRE`, `LAVA→LAVA`, `DROWNING→DROWNING`, `MAGIC→MAGIC`, `POISON→POISON`, `VOID→VOID`, `WITHER→WITHER`, `ENTITY_EXPLOSION/BLOCK_EXPLOSION→EXPLOSION`, `PROJECTILE→PROJECTILE`, and **everything else → `MELEE`** (a deliberate catch-all default).

**`DamageCalculator.java`** (210 lines) — the formula (see §3.1). All methods are `public static`.

**`DamageModifierContext.java`** (72 lines) — mutable pre-hit context (see §3.2).

**`DamageResult.java`** (56 lines) — immutable hit result (see §3.3).

**`DamageApplier.java`** (50 lines) — applies a result to a victim (see §3.4).

**`DamageType.java`** (27 lines) — the enum of damage types with their MiniMessage color strings (see §3.5).

**`DamageIndicatorManager.java`** (82 lines) — floating damage numbers (see §3.6).

**`RegenTask.java`** (55 lines) — out-of-combat regen task (see §3.7).

---

## Architecture & Key Classes

### 3.1 Damage Calculation Pipeline — `DamageCalculator.java`

Three static overloads:

| Overload | Signature | Used by |
|---|---|---|
| ① Full | `calculateDamage(LivingEntity attacker, LivingEntity victim, DamageType, double baseDamageOverride)` | `DamageMechanic`, projectile callbacks, and internally by ② |
| ② Convenience | `calculateDamage(attacker, victim, damageType)` — delegates to ① with `0.0` | `CombatListener` entity-vs-entity path (`CombatListener.java:46`) |
| ③ Environmental | `calculateDamage(LivingEntity victim, DamageType, double baseVanillaDamage)` | `CombatListener` environmental path (`CombatListener.java:90`) |

#### ① — The full attacker/victim pipeline (`DamageCalculator.java:20-156`)

**Stat sourcing.**

- If the attacker is a `Player` and has a loaded session/profile, base damage defaults to the player's **Damage** stat (`sys.getDamage()`) when `baseDamageOverride <= 0`, and Strength/Crit Chance/Crit Damage are read from the profile's `StatManager` (`DamageCalculator.java:30-40`). When `baseDamageOverride > 0`, the override **replaces** the Damage stat but strength/crit still apply (`DamageCalculator.java:34`).
- If the attacker is a custom mob (has a `MOB_ID_KEY` PDC tag), base damage defaults to `mob.getScaledDamage()` (`= baseDamage + level − 1`, `MobDefinition.java:120-122`) when no override, and the mob's optional offensive stats (`getStrength()`, `getCritChance()`, `getCritDamage()`) feed the same player formula (`DamageCalculator.java:41-50`).
- A non-player, non-mob attacker with no override uses `baseDamage = 1.0` (`DamageCalculator.java:51-53`).
- Defense is read from the **victim** — the player's **Defense** stat, or a mob's `getDefense()`; vanilla entities contribute `0.0` (`DamageCalculator.java:56-64`).

**Enchant modifiers.** A `DamageModifierContext` is built from all of the above (`DamageCalculator.java:66`).

- **`modifyAttack`** — for each Valmora enchant on the attacker's main-hand weapon, guarded by `def != null && def.getLogic() != null` (`DamageCalculator.java:68-79`).
- **`modifyDefend`** — for each Valmora enchant on each of the victim player's armor pieces (`DamageCalculator.java:81-94`).
- Enchants with an unregistered logic (`logic == null`) are silently skipped.

**Resolution** (`DamageCalculator.java:96-103`):

```
isCritical = Math.random() < (critChance / 100.0)
fullDamage = baseDamage * (1 + strength / 100)          // strength scaling
if crit:   fullDamage *= (1 + critDamage / 100)         // crit multiplier
fullDamage *= damageMultiplier                           // enchant pre-hit modifiers
```

**Defense mitigation** (`DamageCalculator.java:105-110`):

```
defenseMultiplier = 100 / (defense + 100)   // applied for all types EXCEPT VOID, DROWNING, FALL
mitigated = fullDamage * defenseMultiplier
```

**Mob resistances** (`DamageCalculator.java:112-120`): if the victim is a custom mob, `mob.getResistance(damageType)` (a 0..1 fraction, `MobDefinition.java:110-113`) reduces damage by `(1 − resistance)`; a resistance `>= 1.0` marks the result `immune` (and, for FIRE/LAVA, causes the burning victim to be extinguished in the listener).

**Floor + result** (`DamageCalculator.java:122-125`): `finalDamage = Math.floor(mitigated)`, wrapped in a `DamageResult`.

**Post-hit hooks** (`DamageCalculator.java:127-153`): after the result is built, `onPostAttack` runs for every weapon enchant and `onPostDefend` for every armor enchant — same null-guards as the pre-hit phase. Note the post hooks receive the **already-resolved** `DamageResult`; they cannot change `finalDamage` (it is a `final` field).

#### ③ — The environmental pipeline (`DamageCalculator.java:162-199`)

Applies a hardcoded **`5.0 ×` multiplier** to the incoming vanilla damage (`DamageCalculator.java:164-165`), then reads the victim's defense (player profile or mob definition, `DamageCalculator.java:167-181`), applies the same `100/(defense+100)` mitigation — again **except** for `VOID`, `DROWNING`, `FALL` (`DamageCalculator.java:170`) — applies mob resistances (`DamageCalculator.java:186-193`), floors, and builds a `DamageResult` with `isCritical = false` and `attacker = null` (`DamageCalculator.java:196`).

#### `mobOf(...)` (`DamageCalculator.java:201-209`)

Reads `Keys.MOB_ID_KEY` (`valmora_mob_id`, `Keys.java:47`) from the entity's PDC and resolves it through `ValmoraAPI.getInstance().getMobManager().getMobDefinition(mobId)`. Returns `null` for vanilla entities. Used to detect mob attackers and victims.

### 3.2 Modifier Context — `DamageModifierContext.java`

A **mutable** carrier handed to enchant `modifyAttack`/`modifyDefend` so each logic can tweak the upcoming hit (`DamageModifierContext.java:4-19`):

| Field | Getter/Setter | Notes |
|---|---|---|
| `baseDamage` | `getBaseDamage()`/`setBaseDamage()` | Base stat/override value |
| `strength` | `getStrength()`/`setStrength()` | Offensive scaling stat |
| `critChance` | `getCritChance()`/`setCritChance()` | Percent; crit roll happens later |
| `critDamage` | `getCritDamage()`/`setCritDamage()` | Percent |
| `defense` | `getDefense()`/`setDefense()` | Victim defense, **mutable** (e.g. `DefenseReductionLogic`) |
| `damageMultiplier` | `getDamageMultiplier()`/`setDamageMultiplier()` | Starts at `1.0`; multiplicative |
| `damageType` | `getDamageType()` | `final` — immutable once constructed |

Only the **attacker-side** hooks (`modifyAttack`) and **victim-side** hooks (`modifyDefend`) receive this. It exists purely as the enchant-extension seam of the pipeline — no other subsystem mutates it.

### 3.3 Hit Result — `DamageResult.java`

Immutable-ish value object (`DamageResult.java:8-22`): `finalDamage` (already floored), `damageType`, `isCritical`, `attacker`, `victim` — plus a mutable `immune` flag (`DamageResult.java:14`, set via `setImmune`).

`apply()` (`DamageResult.java:32-35`) builds a `DamageApplier` by casting `ValmoraAPI.getInstance()` to `Plugin` — legal at runtime because `Valmora extends JavaPlugin` and is the API provider (`Valmora.java:75`) — and runs it. Callers never construct `DamageApplier` directly.

### 3.4 Applying Damage — `DamageApplier.java`

`applyDamage()` (`DamageApplier.java:19-49`):

- **Player victim** (`DamageApplier.java:21-39`): requires a loaded session (`ValmoraPlayer`); otherwise logs a warning and **returns without applying damage** (`DamageApplier.java:23-27`). Reads the active profile, then:
  - `state.reduceHealth(finalDamage)` — subtracts from **virtual** health (`PlayerState.reduceHealth`, `PlayerState.java:52-54`).
  - `syncVisualHealth(player, state, statManager)` — maps virtual health onto a fixed **10-heart (20 HP) scale** and pushes it to the client (`PlayerManager.syncVisualHealth`, `PlayerManager.java:243-267`); if virtual health hits `<= 0` it calls `player.setHealth(0)` to trigger a real vanilla death.
  - `state.setInCombat()` — stamps the combat timer (`PlayerState.java:38`), which gates out-of-combat regen for **3 seconds** (`PlayerState.isInCombat`, `PlayerState.java:39-41`).
- **Non-player victim** (`DamageApplier.java:41-45`): `victim.setHealth(max(0, health − finalDamage))` directly, then `MobManager.updateVisuals(victim)` so the mob's health bar / nameplate reflects the new health (`MobManager.java:58-60`). This branch also covers **vanilla mobs** (no custom mob definition).
- Finally `victim.setNoDamageTicks(20)` (`DamageApplier.java:48`) — a full second of vanilla i-frames that, combined with the listener's `> max/2` gate, throttles damage to roughly **one accepted hit every ~0.5 s** per victim.

### 3.5 Damage Types & Colors — `DamageType.java`

`DamageType` (`DamageType.java:3-14`) with per-type MiniMessage colors used by the indicator renderer:

| Type | Color | Used for |
|---|---|---|
| `MELEE` | `<white>` | Weapon/melee hits; default catch-all |
| `PROJECTILE` | `<gray>` | Arrow/mob-projectile hits |
| `FALL` | `<dark_gray>` | Fall damage |
| `DROWNING` | `<blue>` | Drowning |
| `FIRE` | `<#FF8C00>` | Fire/fire-tick |
| `LAVA` | `<dark_red>` | Lava |
| `MAGIC` | `<aqua>` | Magic/instant damage |
| `VOID` | `<black>` | Void |
| `POISON` | `<green>` | Poison |
| `WITHER` | `<black>` | Wither effect |
| `EXPLOSION` | `<red>` | Entity/block explosions |

`getColor()` returns the raw MiniMessage tag string (`DamageType.java:22-24`); the renderer concatenates it directly into the format string.

### 3.6 Damage Indicators — `DamageIndicatorManager.java`

The only combat class exposed through `ValmoraAPI` (`ValmoraAPI.java:39`, implemented at `Valmora.java:298-300`).

- **State:** a `Random` for jitter, `Map<UUID, Long> lastIndicatorSpawned` (rate-limit bookkeeping), and `List<TextDisplay> activeIndicators` for cleanup (`DamageIndicatorManager.java:18-21`).
- **`cleanup()`** (`DamageIndicatorManager.java:27-34`): removes every tracked `TextDisplay` and clears the list; called from `CombatModule.onDisable()` so reloads never leak entities.
- **`spawnIndicator(DamageResult)`** (`DamageIndicatorManager.java:40-69`):
  - **Rate limiter:** at most one indicator per victim per **400 ms** (`now − last < 400` → early return; timestamp updated otherwise) (`DamageIndicatorManager.java:42-49`). This is specifically to keep DoT ticks from spamming entities.
  - Positions at the victim's **eye location** with a small random `±0.25`-block offset per axis so stacked hits don't overlap perfectly (`DamageIndicatorManager.java:51-57`).
  - Spawns a **`TextDisplay`** entity (`DamageIndicatorManager.java:59`), sets its text via `getIndicatorComponent`, makes it a `Billboard.CENTER` display with a fully transparent background (`DamageIndicatorManager.java:60-62`), tracks it in `activeIndicators`, and removes it after **20 ticks (1 second)** via the Bukkit scheduler (`DamageIndicatorManager.java:65-68`).
- **`getIndicatorComponent(DamageResult)`** (`DamageIndicatorManager.java:71-81`): the number is `(int) finalDamage`. Crits render as `<gold>✧ <color><bold>N<gold> ✧` (bold, gold ✧ accents); normal hits render as `color + N` (plain, colored by type).

The design choice — real `TextDisplay` entities rather than packets or armor stands — matches AGENTS.md §11.17 (Display entities are the sanctioned hologram mechanism; the global Bukkit scheduler is used because the display is *not* entity-scoped logic).

### 3.7 Health/Mana Regeneration — `RegenTask.java`

A `Runnable` run by the global scheduler **every 20 ticks (1 s)**, scheduled from **`PlayerManager.onEnable()`** (`PlayerManager.java:39-43`, cancelled in `onDisable()` at `PlayerManager.java:107-110`) — it is *owned* by the player module even though it lives in the combat package.

Per online, alive, sessioned player (`RegenTask.java:20-54`):
- If health is below max and **not** in combat, heal by the **Health Regen** stat (`RegenTask.java:39-43`).
- If mana is below max, restore by the **Mana Regen** stat — regen works **in combat** too (`RegenTask.java:45-48`).
- Re-syncs visual health only when health actually changed (`RegenTask.java:50-52`).

Combat gating comes from `PlayerState.isInCombat()` — true for **3 seconds** after the last `setInCombat()` call (`PlayerState.java:38-41`), which is stamped by `DamageApplier` for every player hit (`DamageApplier.java:39`).

### 3.8 Hit Detection — how a "hit" happens

There is **no custom hitbox / raycast / packet hit detection**. Hits are detected by intercepting Bukkit's own events at high priority:

1. `EntityDamageByEntityEvent` → melee or projectile hit. The vanilla event is cancelled/zeroed and the pipeline runs.
2. `EntityDamageEvent` (non-by-entity) → environmental damage.

Both paths zero the vanilla damage first (`CombatListener.java:40`, `:87`), so **all** damage a victim takes is Valmora-computed. The per-victim invulnerability gate (`> max/2`, `CombatListener.java:26-29`, `:79-82`) plus `setNoDamageTicks(20)` (`DamageApplier.java:48`) form the anti-spam / DoT throttle.

### 3.9 Downstream effects of a resolved hit (`CombatListener.java:46-68`)

Beyond damage + indicator, a successful entity-vs-entity hit also:

- **Records last damage** for the scripting layer: `CombatTracker.recordDamageDealt(attackerUuid, finalDamage)` (`CombatListener.java:52-54`) — this backs the `$player.last_damage$` variable (`CombatTracker.java:7-10`).
- **Fires the attacker's ON_HIT item abilities**: `AbilityExecutor.fireHeld(attackerPlayer, AbilityTrigger.ON_HIT, victim, true)` (`CombatListener.java:55-58`) — the item module's `AbilityTriggerListener` explicitly notes ON_HIT is dispatched from the combat pipeline, not from its own listener (`AbilityTriggerListener.java:20-24`).
- **Fires boss hooks**: `BossController.onAttack` (when the attacker is a tracked boss) and `BossController.onDamaged` (when the victim is) (`CombatListener.java:61-68`), which trigger the boss's `ON_ATTACK`/`ON_DAMAGED` abilities (`BossController.java:100-107`).

---

## Configuration (YAML)

The combat module has **no dedicated YAML folder** (no `plugins/Valmora/combat/`, no `combat/*.yml`). All combat tuning lives in two places:

1. **`config.yml` → `combat:` stat-name mapping** (see below) — read by `SystemStats.load`.
2. **The Stat definitions** (`plugins/Valmora/stats/core.yml`) — the *values* the formula reads.
3. **Mob definitions** (`plugins/Valmora/mobs/*.yml`) — per-mob offense/defense/resistances.

### 3.1 `config.yml` — `combat:` section

Read by `SystemStats.load(FileConfiguration)` (`SystemStats.java:45-63`), each key maps the engine's internal role to a **stat ID** defined in `stats/*.yml`. Defaults are hardcoded in `SystemStats.java:47-56` and mirrored in `config.yml:90-101`:

| Key | Default | Meaning |
|---|---|---|
| `combat.health-stat` | `health` | Stat whose effective value is max health (used for regen caps, visual sync, mob damage sinks). |
| `combat.mana-stat` | `mana` | Stat whose effective value is max mana (regen cap). |
| `combat.damage-stat` | `damage` | **Base attack power** when a player attacks (and no override is supplied). |
| `combat.strength-stat` | `strength` | Offensive stat in `base × (1 + strength/100)`. |
| `combat.defense-stat` | `defense` | Victim mitigation via `100/(defense+100)` (except VOID/DROWNING/FALL). |
| `combat.crit-chance-stat` | `crit_chance` | Percent chance of a critical hit (capped at 100 via stat `max-value`, `core.yml:43`). |
| `combat.crit-damage-stat` | `crit_damage` | Percent extra damage on crits (`× (1 + critDamage/100)`). |
| `combat.speed-stat` | `speed` | Movement speed (mapped to vanilla attribute; not read by combat damage). |
| `combat.health-regen-stat` | `health_regen` | Out-of-combat HP restored per second. |
| `combat.mana-regen-stat` | `mana_regen` | Mana restored per second (always, even in combat). |
| `combat.luck-stat` | `luck` | Loot-quality stat (not read by combat damage). |

Changing a key is how you rename/swap a core combat stat without touching code.

### 3.2 Stat defaults that drive the formula (`stats/core.yml`)

| Stat | Default | Cap | Role in combat |
|---|---|---|---|
| `damage` | `5.0` | — | Base player damage |
| `strength` | `0.0` | — | Strength scaling |
| `defense` | `0.0` | — | Damage reduction |
| `crit_chance` | `30.0` | `100.0` | Crit chance |
| `crit_damage` | `50.0` | — | Crit damage |
| `health` | `100.0` | `10000.0` | Max health pool |
| `mana` | `100.0` | `5000.0` | Max mana pool |
| `health_regen` | `1.0` | — | Regen/s out of combat |
| `mana_regen` | `2.0` | — | Regen/s |

Full list at `core.yml:1-211`.

### 3.3 Mob-defined combat fields (`mobs/*.yml`)

Consumed by `MobDefinition`/`MobFactory` and read by the calculator:

| Key | Default | Consumer |
|---|---|---|
| `base-damage` | `5.0` (`MobDefinition.java:161`) | Base of `getScaledDamage()` |
| `level` | `1` | `getScaledDamage() = baseDamage + level − 1` (`MobDefinition.java:120-122`) |
| `damage-type` | `MELEE` (`MobDefinition.java:165`) | The mob's own damage type (used for its attacks) |
| `stats.damage` / `stats.defense` / `stats.strength` / `stats.crit-chance` / `stats.crit-damage` | `0.0` each | Offensive/defensive stats mirroring the player formula (`DamageCalculator.java:41-50`, `:62-64`) |
| `resistances.<TYPE>` | none (`0.0` default, `MobDefinition.java:110-113`) | 0..1 fraction of a `DamageType` reduced; `1.0` = immunity. Example in `test_boss.yml:19-23` |

**No combat-specific config exists** for the formula constants — the `5.0` environmental multiplier (`DamageCalculator.java:164`), the `100/(defense+100)` formula, the `400 ms` indicator rate limit, the `20-tick` indicator lifetime, the `20` `NoDamageTicks`, and the `3 s` combat-tag duration (`PlayerState.java:40`) are all hardcoded.

---

## Data Model / Persistence

The combat module is **stateless at the database level** — no tables, no DAOs, no SQL. Combat data is either transient or derived:

| Data | Storage | Persisted? |
|---|---|---|
| Player virtual **health/mana** | `PlayerState.currentHealth/currentMana` (`PlayerState.java:8-9`) | **Yes** — via `PlayerState.getSaveData()` → `{health, mana}` (`PlayerState.java:74-76`, `loadData` at `:78-83`), part of the profile saved by the player module's `DataStore`. |
| Combat tag / last-combat timestamp | `PlayerState.lastCombatTime` — `transient` (`PlayerState.java:10`) | No — reset on reload/restart |
| `DamageResult` | Plain object, single-use | No |
| `DamageModifierContext` | Plain object, per-hit | No |
| `lastIndicatorSpawned` / `activeIndicators` | `DamageIndicatorManager` fields (`DamageIndicatorManager.java:20-21`) | No — indicators are 1-second entities; manager is cleaned up on disable |
| `CombatTracker` last-damage map | `static ConcurrentHashMap<UUID, Double>` (`CombatTracker.java:14`) | No — in-memory script variable source |

**PDC keys used** (all in `util/Keys.java`):

| Key | NamespacedKey | Written by | Read by |
|---|---|---|---|
| `MOB_ID_KEY` | `valmora_mob_id` (`Keys.java:47`) | Mob module (`MobFactory`) | `DamageCalculator.mobOf` (`DamageCalculator.java:206`) |

---

## API Exposed

**Via `ValmoraAPI`** (`ValmoraAPI.java:39`, implemented at `Valmora.java:298-300`):

```java
DamageIndicatorManager indicators = ValmoraAPI.getInstance().getDamageIndicatorManager();
```

Only the **indicator manager** is exposed through the API. `CombatModule` itself is **not** in the `ValmoraAPI` interface, and neither is the calculator.

**Public API surface of the package** (used directly by other modules — documented in §7):

| Class | Public surface | Consumer |
|---|---|---|
| `DamageCalculator` | `static DamageResult calculateDamage(...)` × 3 overloads | `CombatListener`, `DamageMechanic`, `AbilityTriggerListener` |
| `DamageResult` | getters + `apply()` + `setImmune()` | post-hit enchant hooks, callers |
| `DamageModifierContext` | getters/setters for all hit fields | `EnchantmentLogic` implementations |
| `DamageType` | enum + `getColor()` | `EnchantmentLogic`, `DamageMechanic`, indicators |
| `DamageIndicatorManager` | `spawnIndicator(DamageResult)`, `cleanup()` | via API |
| `CombatTracker` (in the **item** package, `module/item/CombatTracker.java`) | static record/get/clear of last damage dealt | script variable `$player.last_damage$` |

There is **no combat command** and **no combat permission** — the module has no player/admin-facing commands of its own. Admin interaction is limited to `/valmora reload` (`ValmoraCommand.java:49-54`, `valmora.admin`).

---

## Dependencies & Consumers

### Dependencies (loads-after, per `Valmora.java:188-222`)

| Dependency | Why | Enable-time? |
|---|---|---|
| Stat (`StatModule`/`SystemStats`) | Formula reads stat IDs via `SystemStats` (`DamageCalculator.java:22`, `:35-39`, `:60`); `StatManager` holds effective values | Yes (via `plugin.getStatModule()` in `RegenTask`) |
| Player (`PlayerManager`) | Sessions/profiles hold the stats and virtual health; `syncVisualHealth` (`DamageApplier.java:36`); `RegenTask` iterates sessions (`RegenTask.java:27-31`) | Yes (via `plugin.getPlayerManager()`) |
| Item (`ItemManager`, `CombatTracker`, `AbilityExecutor`) | ON_HIT ability dispatch on weapon hits (`CombatListener.java:55-58`) | No (lazy per-hit) |
| Mob (`MobManager`) | `mobOf` lookup (`DamageCalculator.java:208`), `updateVisuals` (`DamageApplier.java:44`), boss hooks (`CombatListener.java:62-67`) | No (lazy per-hit) |
| Enchant (`EnchantModule`, `EnchantmentHelper`) | Pre/post hit logic hooks (`DamageCalculator.java:68-153`) | No (lazy per-hit) |

`combat` is registered **before** `enchant` (`Valmora.java:200` vs `:204`) but only ever touches enchants lazily at hit time, so the ordering is safe.

### Consumers (who calls the combat module)

| Consumer | How it uses combat | Sites |
|---|---|---|
| `CombatListener` (self) | Detects hits, drives pipeline + indicators | `CombatListener.java:46-49`, `:90-98` |
| Item `DamageMechanic` (`module/item/impl/DamageMechanic.java`) | Ability mechanic `damage` — calls the full pipeline with an explicit `amount` override per target, applies + spawns indicators, supports DoT scheduling | `DamageMechanic.java:58-60` |
| Item `AbilityTriggerListener` (`module/item/AbilityTriggerListener.java`) | Projectile impact callbacks — resolves the struck entity and deals `callback.damage()` through the pipeline with indicator | `AbilityTriggerListener.java:63-67` |
| Item `CombatTracker` | Written by `CombatListener` on every player hit (`CombatListener.java:52-54`); read by the script variable provider for `$player.last_damage$` | `CombatListener.java:52-54`, `CombatTracker.java:22-24` |
| Enchant logic (`module/enchant/...`) | Implement `EnchantmentLogic` hooks against `DamageModifierContext`/`DamageResult` | `SharpnessLogic.java:17-22`, `DefenseReductionLogic.java:17-21`, `DamageMultiplierLogic.java:25-29`, `EnchantmentLogic.java:12-18` |
| Mob `BossController` | Receives `onAttack`/`onDamaged` callbacks from the listener | `CombatListener.java:61-68`, `BossController.java:100-107` |
| Player `PlayerManager` | Schedules `RegenTask` from this package; drives `syncVisualHealth` | `PlayerManager.java:43`, `:243-267` |
| Tests `DamageCalculatorTest` | Mocks `ValmoraAPI` + friends and asserts the formula (melee, crit control, defense, sharpness, mob-vs-player) | `DamageCalculatorTest.java:130-232` |

---

## Unfinished Things / TODOs

- **CombatModule never registers `RegenTask`.** The task is scheduled by `PlayerManager.onEnable()` (`PlayerManager.java:43`), so the combat module has no control over its lifecycle despite owning the file. On `/valmora reload` the PlayerManager re-schedules it, but a standalone `CombatModule` is inert on regen.
- **`CombatListener` constructor is empty** (`CombatListener.java:15-16`) — it takes `Valmora` but never stores it, so any future listener needs the plugin reference would require a refactor.
- **Null-safety gaps in the pipeline:**
  - `DamageCalculator.java:33` calls `vPlayer.getActiveProfile()` with no null check — a session whose profile list is empty yields an NPE instead of falling through to default stats.
  - `DamageApplier.java:28-29` silently returns when the profile is `null` (a player with no active profile takes **no damage** and is not even logged).
  - `DamageApplier.java:21` doesn't distinguish "vanilla mob" from "custom mob" — both go through the direct `setHealth` path, but only custom mobs get `updateVisuals` visuals.
- **No `onDisable()` reset for indicator rate-limit state.** `DamageIndicatorManager.cleanup()` removes entities but does not clear `lastIndicatorSpawned` (`DamageIndicatorManager.java:20`) — after reload the 400 ms throttle survives, which is harmless but inconsistent with the module contract (state should be reset in `onDisable`).
- **`CombatTracker` entries are never evicted** (`CombatTracker.java:14`) — the per-UUID last-damage map grows for the life of the server (no `clear()` on player quit).
- **Environmental default maps to `MELEE`** (`CombatListener.java:114`) — unknown `DamageCause` values (e.g. `SUICIDE`, `CONTACT`, `STARVATION`, `DRAGON_BREATH`, `SONIC_BOOM`, `OUTSIDE_BORDER`) are treated as melee and get full defense mitigation rather than a faithful type.
- **Hardcoded tunables** (documented in §4) — `5.0` environmental multiplier, `100/(defense+100)` formula, `400 ms` indicator limiter, `20`-tick lifetime, `20` `NoDamageTicks`, `3 s` combat window, `10`-heart visual scale (`PlayerManager.java:251-259`) all have no config surface.
- **The `immune` flag is mostly decorative.** Only the fire/lava extinguishing behavior uses it (`CombatListener.java:94-96`); mobs immune to e.g. EXPLOSION still receive the explosion **indicator** showing damage that was already reduced to ~0 (indicators don't check immunity).
- **No attack-speed / cooldown integration.** `bonus_attack_speed` exists as a stat (`core.yml:146-151`) but the combat pipeline doesn't consume it; vanilla attack cooldowns are untouched.
- **DamageType colors are hardcoded in code**, not configurable (`DamageType.java:4-14`).
- **`docs/UNFINISHED_FEATURES.md`** tracks broader combat plans (see `docs/todo.md` for the overall backlog).

---

## Possible Improvements / Changes

- **Expose the pipeline through the API.** Add `CombatModule` (or a `DamageService`) to `ValmoraAPI` so consumers stop importing package classes directly (`DamageMechanic`, `AbilityTriggerListener`, and enchant logic all reach into `module.combat` today).
- **Make the formula configurable.** Move `environmental-multiplier`, `defense-formula`, indicator timing, and combat-window duration into `config.yml` `combat:` keys (with the current values as defaults) so admins can tune combat feel without code.
- **Harden null paths.** Null-check `getActiveProfile()` in `DamageCalculator`/`DamageApplier`, and make "no session/profile" produce a logged, consistent no-op (or default stats) instead of silently skipping damage.
- **Move `RegenTask` ownership** into `CombatModule.onEnable()/onDisable()` (scheduling it there) so the combat package fully owns its regen behavior and reload is self-consistent.
- **Distinguish vanilla vs custom mob victims** in `DamageApplier` so `updateVisuals` is only called for mobs that have visuals, and give vanilla mobs their own armor/defense path if desired.
- **Faithful environmental types.** Map the remaining `DamageCause` values to existing or new `DamageType`s instead of defaulting to `MELEE`.
- **Fix "double-tap" indicators on immunes.** Skip `spawnIndicator` (or render an `Immune` label) when `result.isImmune()`.
- **Per-victim DoT aggregation.** The 400 ms limiter drops excess indicators silently; a batched/stacked number (e.g. `-12`, `-34`) would preserve feedback without entity spam.
- **Add an entity scheduler alternative for indicators.** `spawnIndicator` uses the global `BukkitScheduler` (`DamageIndicatorManager.java:65`); per AGENTS.md §11.13 an entity-scoped scheduler would auto-cancel if the victim unloads between spawn and removal.
- **Add combat unit tests** beyond `DamageCalculatorTest` (e.g. `DamageType` mapping, `DamageModifierContext` mutation ordering, indicator formatting, `PlayerState` combat-window semantics).
