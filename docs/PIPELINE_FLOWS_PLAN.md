# Pipeline Flows — Plan: Move Hardcoded Sequences Into the `*_pipeline.yml` Files

> **Status:** Phases 0–5 implemented (2026-09-27, uncommitted on `feature/loader-diagnostics-dsl`);
> Phase 6 not started. What was built and where it differs from this plan: §10.
> **Written:** 2026-09-24 against branch `feature/loader-diagnostics-dsl`.
> **Scope:** `combat_pipeline.yml`, `death_pipeline.yml`, `fishing_pipeline.yml`,
> `resource_pipeline.yml`, `item_pipeline.yml`, `mob_pipeline.yml`. GUI lifecycle blocks and
> enchant trigger points already run through `HookBus` from their own YAML, so they're out of scope.
> **Read first:** `docs/modules/design/pipeline.md` (how HookBus works today),
> `docs/modules/design/versioning.md` §3 (how default files are installed and updated).

---

## 1. Goal

Right now every `*_pipeline.yml` ships with `stages: []`. The actual sequence (hit → calculate →
cooldown scaling → shield → apply → indicator → abilities, or break → power gate → silk touch →
drops → stage advance → regen) is hardcoded in Java. The YAML can only add stages at a few fixed
insertion points (`pre_damage`, `post_catch`, ...).

After this work, each file ships with **the full default sequence written out as YAML**. Every step
that's hardcoded today becomes one entry in a list. An admin can:

- **reorder** steps (for example, apply shield blocking before attack-cooldown scaling);
- **disable** a step (`enabled: false`), for example turning off silk-touch handling or the
  mob-kill gold reward;
- **gate** a step with `conditions:` (for example, run `shield_block` only outside `zone pvp_arena`);
- **tune** a step through its `params:` (the magic numbers and messages that are hardcoded or
  scattered through `config.yml` today);
- **replace** a built-in step with script DSL, or **insert** script stages anywhere in the list,
  not only at the old fixed insertion points.

**Hard requirement:** with the shipped default files, behavior must be **identical** to today.
Every phase in §7 ends with an equivalence check.

---

## 2. Core design

### 2.1 Vocabulary

| Term | Meaning |
|---|---|
| **Flow** | An ordered, named list of steps for one lifecycle, for example `combat` → `entity_hit`. One file can hold several flows. |
| **Built-in step** | A Java-implemented step, registered by the owning module under a stable id (`calculate_damage`, `roll_drops`). This is where today's hardcoded code moves. It takes typed `params:`. |
| **Script step** | Exactly today's `CompiledPipelineStage` (`conditions`/`on-pass`/`on-fail`), placed anywhere in the flow. |
| **Anchor step** | Runs a named `HookBus` point (`bus.runPoint("combat:pre_damage", ctx)`). The current insertion points become anchors, so Java addon hooks registered through `HookBus.registerHook` and legacy `stages:` with `when:` keep working unchanged. |
| **Flow state** | A per-domain mutable object (for example `HitState`, `CatchState`) that built-in steps read and write, replacing the local variables in today's methods. It's stored on the `ExecutionContext` and exposed to scripts through the existing `$dmg.*$`/`$fishing.*$`/... variables. |

### 2.2 YAML shape (version 2)

```yaml
version: 2

flows:
  catch:                                  # flow id — fixed per domain (see §5)
    - builtin: resolve_table
    - anchor: pre_catch                   # == HookBus point "fishing:pre_catch"
    - builtin: roll_sea_creature
      params:
        chance: "$fishing.table.sea_creature_chance$"   # expression, compiled at load
    - builtin: roll_loot
      conditions:                         # optional gate — skipped (not failed) when false
        - "$fishing.sea_creature$ == null"
    - builtin: give_catch
    - anchor: post_catch

# Legacy section — still supported. Each stage is spliced in at the anchor matching its `when:`.
stages: []
```

Per-step keys (validated with `ConfigReader.knownKeys`):

| Key | Applies to | Meaning |
|---|---|---|
| `builtin: <id>` / `anchor: <point>` / `id: <id>` + `on-pass` | exactly one of these | Step kind. A script step is recognized by `on-pass`/`on-fail`. |
| `id:` | all | Optional for built-ins and anchors (defaults to the builtin/anchor name). Must be unique inside a flow. Shown by `/valmora pipeline list`. |
| `enabled:` | all | Default `true`. `false` keeps the step in the file for documentation but skips it. |
| `conditions:` | all | `ConditionParser` list. When false, a built-in or anchor is **skipped** and the flow continues. A script step runs `on-fail`, as it does today. |
| `params:` | built-in | Map validated by that built-in's own `ConfigReader` (`knownKeys`, `requireX`, "did you mean" hints). Numeric params accept expressions, compiled at load time, never per call. |
| `stop-on-fail:` | built-in | Default comes from the built-in. For gate steps (`breaking_power_gate`, `cooldown_gate`), stopping the flow is their entire job. |

### 2.3 Runtime model

```
CompiledFlow            (immutable array of FlowStep, built at load, swapped atomically on reload)
  └─ FlowStep.run(ctx) → StepOutcome { CONTINUE, STOP }
        ├─ BuiltinFlowStep   : conditions? → BuiltinStep.run(ctx, state, params)
        ├─ AnchorFlowStep    : hookBus.runPoint(point, ctx) == false → STOP
        └─ ScriptFlowStep    : wraps CompiledPipelineStage; INTERRUPT → STOP
CompiledFlow.run(ctx) → FlowResult { COMPLETED, STOPPED(stepId) }
```

The caller maps `STOPPED` onto its existing outcome (`return false` from `handleCatch`,
`BreakResult.INTERRUPTED`, a cancelled hit, ...). The existing `interrupt` event keeps working:
it sets `pipeline:interrupted`, which the script and anchor steps already translate into `STOP`.

### 2.4 Performance rule (combat is the hot path)

Today the zero-cost guard is `hasAnyStages(...)`: with no stages, no context gets built. After this
change a flow **always** runs, because it *is* the logic. That's acceptable if:

1. The flow is a pre-built `FlowStep[]`. There's no map lookup, string parsing or list allocation
   per call.
2. One `SimpleExecutionContext` and one state object are allocated per hit. That's the same cost as
   today when the pipeline is active, and `DamageCalculator` already allocates two contexts per hit.
3. An anchor step checks `bus.hasStages(point)` before calling `runPoint`, so empty anchors stay
   essentially free.
4. Built-in steps without `conditions:` skip condition evaluation entirely (a `null` check).
5. Before/after microbenchmark for `entity_hit` in Phase 5 (§7). The budget is at most 10% extra
   time per hit versus the current `pipelineActive == false` path.

---

## 3. New and changed code (Phase 0 infrastructure)

| # | File | Kind | What |
|---|---|---|---|
| 0.1 | `api/pipeline/flow/FlowStep.java` | new | `interface FlowStep { String id(); StepOutcome run(ExecutionContext ctx); }` |
| 0.2 | `api/pipeline/flow/StepOutcome.java`, `FlowResult.java` | new | Enums/records from §2.3. |
| 0.3 | `api/pipeline/flow/BuiltinStep.java` | new | `interface BuiltinStep<S> { StepOutcome run(ExecutionContext ctx, S state); }`, produced by a `BuiltinStepFactory<S>` that has `id()`, `Class<S> stateType()`, `BuiltinStep<S> compile(ConfigReader params)` and `boolean required()`. |
| 0.4 | `api/pipeline/flow/FlowDomain.java` | new | Declares a domain: file name, HookBus point prefix, flow ids, state class, a state factory, and the builtin factories. There's one instance per domain, owned by that domain's module. |
| 0.5 | `api/pipeline/flow/CompiledFlow.java` | new | Step array plus `run(ctx)`. Stores the state on the context under `flow:state` and writes the debug trace (`DebugManager` `script` channel: step id, outcome, elapsed ns). |
| 0.6 | `api/pipeline/flow/FlowRegistry.java` | new | Held by `ScriptModule` next to `HookBus`. `register(FlowDomain)`, `get(domain, flowId) → CompiledFlow`. Each flow sits in an `AtomicReference` so a reload swaps it atomically; in-flight calls finish on the old array. Cleared in `ScriptModule.onDisable`. |
| 0.7 | `module/script/pipeline/FlowYamlLoader.java` | new | Parses `version`/`flows:` and splices legacy `stages:` into anchors. Diagnostics go through `LoadSession`/`LoadScope`, same as `PipelineYamlLoader`. |
| 0.8 | `module/script/pipeline/PipelineYamlLoader.java` | change | Its per-stage compile becomes the script-step compiler that `FlowYamlLoader` reuses. `loadFile` becomes a thin wrapper: it compiles the legacy `stages:` and registers them on the bus. Anchors call `runPoint`, so legacy stages keep firing through the bus with no extra splicing code. |
| 0.9 | `module/script/event/impl/PipelineSetEventFactory.java` | new | `pipeline_set <key> <expression>` writes into the current flow state through the domain's **allow-listed** setter map (for example `fishing.sea_creature_chance`, `dmg.final_damage`). An unknown key is reported at load time. This generalizes `multiply_damage`, which stays as a shorthand. |
| 0.10 | `ValmoraCommand` `pipeline` subcommand | change | `/valmora pipeline flows [domain]` prints each flow's steps in order with kind, `enabled` and a condition marker. The existing `list` keeps showing HookBus points. |
| 0.11 | `infrastructure/config/diag/BuiltinContentKinds` | change | Add a `PIPELINE_STEP` kind so built-in ids show up in cross-reference validation and "did you mean" hints. |

### 3.1 Validation rules (in `FlowYamlLoader`)

| Problem | Severity | Result |
|---|---|---|
| Unknown `builtin:` id or `anchor:` point | ERROR (with hint) | That **flow** fails and the last good compiled flow is kept (same rule as `YamlLoader`). On first load, the bundled default is used instead (§4). |
| A `required()` built-in is missing or `enabled: false` (for example `apply_damage` if removing it would leave vanilla damage zeroed with nothing applied) | ERROR | Same as above. The required list is kept short, and each required built-in says in its javadoc why it's required. |
| Duplicate step `id` in a flow | ERROR | Same as above. |
| Bad `params:` value | WARN or ERROR according to the built-in's `ConfigReader` rules | A broken optional param falls back to its default. A broken required param fails the flow. |
| A flow id that isn't declared for the domain | WARN | Ignored. |
| A declared flow missing from the file | INFO | That flow uses the bundled default (§4). |
| Legacy `stages:` entry with `when:` naming no anchor present in the flow | WARN | The stage still registers on the bus, but nothing runs it. The warning says so. |

---

## 4. Upgrade / compatibility strategy

This is the riskiest part: flows move logic **into** a user-editable file, so a file that lacks
a flow must never mean "that logic silently disappears."

1. **The bundled file is the single source of truth for defaults.** `FlowYamlLoader` always parses
   the jar's copy (`plugin.getResource("fishing_pipeline.yml")`) too. Any flow missing from the
   data-folder file, or failing validation on first load, uses the bundled flow. There's no
   separate Java "default flow" constant that could drift from the shipped YAML.
2. **Existing installs:**
   - Unedited `*_pipeline.yml` (still `stages: []`): `ResourceManifest` auto-updates it to the new
     full file (`resources.auto-update-unmodified-defaults`, default `true`).
   - Edited file (the admin added stages): it gets `<file>.new`. The old file has no `flows:`, so
     every flow comes from the bundled defaults, and the admin's `stages:` still splice in at the
     anchors. Behavior is unchanged, and a one-time INFO in the load report points at the `.new` file.
   - Deleted file: stays deleted (manifest rule). Every flow comes from the bundled defaults.
     Deleting the file means "use defaults," the same as today.
3. **`config.yml` keys stay honored.** Each built-in param resolves in this order: YAML `params:`,
   then the existing `config.yml` key (for example `combat.iframe-threshold-factor`,
   `resource.silk-touch.enabled`, `mobs.combat-skill-id`), then the literal default. The shipped
   YAML **omits** params that already have a `config.yml` key and lists them in a comment, so an
   admin's existing `config.yml` tuning still wins. Deprecating those `config.yml` keys is a
   separate, later decision (§8).
4. **`version:`** — a missing value means 1 (legacy `stages:`-only). `version: 2` enables `flows:`.
   A number newer than the loader supports is an ERROR, and the bundled defaults are used.
   Downgrades are refused, not guessed, as in `versioning.md`.
5. **Java addon hooks** (`HookBus.registerHook`) keep working because every current point is still
   present as an anchor in the default flows. If an admin **removes** an anchor that has registered
   Java hooks, `/valmora validate` reports a WARN that names the orphaned hook ids.

---

## 5. Per-domain step inventory

Each table lists **every** step in the default flow, in order, and where its code comes from
today. Anchors (↳) are the existing HookBus points. "Params" are the values that become tunable.

### 5.1 Fishing: `fishing_pipeline.yml` (Phase 1, pilot)

**State:** `CatchState { Player player; Location hook; FishingLootTable table; double seaCreatureChance; String seaCreatureId; FishingLootEntry entry; int amount; ItemStack item; boolean caught; }`

Flow `catch` (from `FishingManager.handleCatch`):

| Step | From | Params |
|---|---|---|
| `resolve_table` | `getTableForPlayer` | `fallback-table: default` |
| ↳ `pre_catch` | existing anchor | – |
| `roll_sea_creature` | `Math.random() < table.getSeaCreatureChance()` | `chance` (expression, default `$fishing.table.sea_creature_chance$`) |
| `spawn_sea_creature` | `spawnMob(def, hookLocation)` | `at: hook\|player` |
| `roll_loot` | `table.roll()` + `rollAmount()`; conditioned on no sea creature | – |
| `give_catch` | `player.getInventory().addItem(item)` | `overflow: drop\|discard` (today it's lost silently; the default keeps that) |
| ↳ `post_catch` | existing anchor | – |

Flow `bite` (from `FishingManager.playBiteFeedback`) is a script-only flow with no built-ins. The
default file writes it with the `sound` event plus a **new** `particle` event, which also gets
reused by resource feedback (§5.2):

```yaml
  bite:
    - id: bite_feedback
      on-pass:
        - "sound world entity.fishing_bobber.splash 0.6 1.2"
        - "particle FISHING 10 0.2 0.1 0.2 0.02"
```

Notes:
- **Bug to fix in this phase:** `handleCatch` calls `entry.rollAmount()` twice. The amount that
  gets given and the amount written to `fishing:amount` are separate random rolls. `CatchState.amount`
  holds one roll, which both uses read.
- `sound` currently takes `player|world` plus a key. Extending it to take volume and pitch
  arguments is a prerequisite. Check `SoundEventFactory`'s `maxArgs`.

### 5.2 Resource mining: `resource_pipeline.yml` (Phase 2)

**State:** `BreakState { Player player; Block block; ResourceTracker tracker; ZoneResourceConfig config; int stageIndex; Material original; ResourceStage stage; double fortune; List<ItemStack> drops; BreakResult result; }`

Flow `break` (from `ResourceManager.handleBlockBreak`, lines ~69–176):

| Step | From | Params | Notes |
|---|---|---|---|
| `resolve_resource` | tracker/zone lookup | – | **required**. Sets `result = NOT_TRACKED`/`DEPLETED` and STOPs. |
| `breaking_power_gate` | `getPlayerBreakingPower < required` | `stat: breaking_power` | STOP with `INSUFFICIENT_POWER` |
| ↳ `pre_break` | existing anchor | – | STOP → `INTERRUPTED` |
| `silk_touch` | silk-touch branch | `enabled` (falls back to `resource.silk-touch.enabled`) | Sets `drops` to 1× the original block and marks drops resolved |
| `roll_drops` | chance loop + `applyFortune` | `fortune-stat: mining_fortune` | Skipped if drops are already resolved |
| `give_drops` | `addItem` loop | `overflow: drop\|discard` | |
| ↳ `post_break` | existing anchor | – | |
| `advance_stage` | `block.setType(nextMat)` + tracker update | – | **required**. Without it the block never depletes. |
| `schedule_regen` | `runTaskLater` + `clampRegenDelay` | `min-delay-ticks` (falls back to `resource.limits.min-regen-delay-ticks`) | **required** |

Flows `mine_feedback`, `denied_feedback` and `regen_feedback` (from `playMineFeedback`,
`playDeniedFeedback`, `playRegenFeedback`) are script-only, built from `sound` + `particle`, with
`$resource.material$` available.

### 5.3 Player death: `death_pipeline.yml` (Phase 3)

**State:** `DeathState { PlayerDeathEvent event; ZoneDefinition zone; boolean keepInventory; boolean keepExperience; }`, `RespawnState { PlayerRespawnEvent event; ZoneDefinition zone; Location override; }`

Flow `death` (from `DeathListener.onDeath`):

| Step | From | Params |
|---|---|---|
| `death_message` | `DeathMessageService.build` + consume | `broadcast` (falls back to the existing `DeathMessageService.broadcastEnabled()` key) |
| `resolve_keep_policy` | `DeathPolicyResolver.resolve` | `keep-inventory-default`, `keep-experience-default` (fall back to `death.*`) |
| `apply_keep_policy` | `setKeepInventory`/`clearDrops`/`setDroppedExp(0)` | – |
| ↳ `on_death` | existing anchor (`player:on_death`) | – |

Flow `respawn` (from `DeathListener.onRespawn`):

| Step | From | Params |
|---|---|---|
| `zone_respawn_override` | `resolveRespawnOverride` | – (still reads `death.zone-respawn-overrides`) |
| ↳ `on_respawn` | existing anchor | – |

**Deliberately not moved in this phase:** the purse-loss penalty (`EconomyListener.onDeath`,
`MONITOR`) and ON_DEATH item abilities (`AbilityTriggerListener`, `HIGH`). They run at different
event priorities on purpose (they read the already-resolved keep policy). Pulling them into this
flow would change ordering relative to other plugins' listeners. They're tracked as Phase 6
candidates (§7), where each would become a `purse_penalty` / `fire_death_abilities` built-in
appended after `apply_keep_policy`, with the original listener removed in the same commit.

### 5.4 Item abilities: `item_pipeline.yml` (Phase 4)

**State:** `AbilityCastState { Player player; ValmoraProfile profile; PlayerState pstate; AbilityDefinition ability; AbilityTrigger trigger; LivingEntity target; boolean silent; }`

Flow `cast` (from `AbilityExecutor.fireOne`, lines ~118–196):

| Step | From | Params |
|---|---|---|
| `resolve_target` | `getTargetEntity(range)` | `no-target-message: "<red>No target in range!"` |
| `ability_conditions` | `conditionsPass` | – |
| `cooldown_gate` | `isOnCooldown` | `message: "<red>Ability on cooldown: $ability.cooldown_remaining$s"` |
| `mana_gate` | `getCurrentMana() < cost` | `message: "<aqua>Not enough Mana!"` |
| `consume_mana` | `reduceMana` | – |
| `start_cooldown` | `setCooldown` | – |
| ↳ `pre_ability` | existing anchor | – |
| `run_mechanics` | `ConfiguredMechanic.execute` loop | – (**required**) |
| ↳ `post_ability` | existing anchor | – |

The "on cooldown", "not enough mana" and "no target" messages become params. That closes the
matching items in `HARDCODED_VALUES_AUDIT.md`; update `HARDCODED_VALUES_DECISIONS.md` when done.
Messages still respect `silent`.

### 5.5 Boss abilities: `mob_pipeline.yml` (Phase 4)

**State:** `BossCastState { BossInstance instance; MobAbility ability; LivingEntity target; }`

Flow `cast` (from `BossController.fire`, lines ~193–247):

| Step | From | Params |
|---|---|---|
| `cooldown_gate` | `cooldownExpiry` check + set | – (**required**; the return value feeds the timer logic) |
| `resolve_target` | `findNearestPlayer` | – |
| `announce` | `nearbyPlayers(announceRadius)` loop | `radius` (falls back to the current `announceRadius` source) |
| ↳ `pre_ability` | existing anchor | – |
| `run_mechanics` | mechanic loop | – (**required**) |
| ↳ `post_ability` | existing anchor | – |

Flow `death` (the mob-death half of `MobDeathListener.onMobDeath`) lives in `mob_pipeline.yml`
because it's about mobs, not hits. The `combat:on_death` **anchor point name is kept** for
compatibility:

| Step | From | Params |
|---|---|---|
| `boss_on_death` | `bossController.onDeath` | – |
| `resolve_luck` | `luck + pet_luck` | `stats: [luck, pet_luck]` |
| `grant_skill_xp` | `addXp(combatSkillId, xpReward)` | `skill` (falls back to `mobs.combat-skill-id`) |
| `grant_gold` | `economy.addCoins(goldReward)` | – |
| `roll_loot` | `LootTable` loop into `event.getDrops()` | – |
| ↳ `combat:on_death` | existing anchor (only when a killer exists; the anchor step keeps that condition as a default `conditions:`) | – |

### 5.6 Combat: `combat_pipeline.yml` (Phase 5, last because it's the hot path)

**State:** `HitState { EntityDamageEvent event; LivingEntity attacker, victim; DamageType type; double rawDamage; DamageResult result; }`. `HitState` **is** the source for `$dmg.*$`: `DamageVariableProvider` reads from it when present, so today's manual `pipelineCtx.set("dmg:final_damage", ...)` sync lines go away.

Flow `entity_hit` (from `CombatListener.onEntityDamageByEntity`):

| Step | From | Params |
|---|---|---|
| `iframe_gate` | `noDamageTicks > max * factor` → cancel | `threshold-factor` (falls back to `combat.iframe-threshold-factor`) |
| `resolve_attacker` | damager / projectile shooter | – (**required**; STOPs, vanilla damage untouched, when there's no living attacker, as today) |
| `zero_vanilla_damage` | `event.setDamage(0)` | – (**required**) |
| `classify_damage_type` | `instanceof Projectile ? PROJECTILE : MELEE` | `projectile: PROJECTILE`, `melee: MELEE` |
| ↳ `pre_damage` | existing anchor | – |
| `calculate_damage` | `DamageCalculator.calculateDamage(..., ctx)` | – (formulas stay in `damage_formula.yml`; see note) |
| `attack_cooldown_scaling` | `AttackCooldownService` block | `enabled` (falls back to the current `isEnabled()` key); conditioned on melee in the default file |
| `shield_block` | `ShieldBlockService.resolve` block | – (existing `ShieldBlockService` config) |
| ↳ `post_calculation` | existing anchor | – |
| `record_death_context` | `DeathContextCache.record` | – |
| `apply_damage` | `damageResult.apply()` | – (**required**) |
| `set_knockback` | `KnockbackModifierTracker.set` | – |
| `spawn_indicator` | `DamageIndicatorManager.spawnIndicator` | – |
| ↳ `post_application` | existing anchor | – |
| `fire_on_hit_abilities` | `CombatTracker.recordDamageDealt` + ON_HIT `fireHeld`/`fireModifiersHeld` | – |
| `fire_on_damage_taken_abilities` | ON_DAMAGE_TAKEN held + armor (`fireArmorOnDamageTaken`) | – |
| `boss_triggers` | `bossController.onAttack`/`onDamaged` | – |
| ↳ `on_dmg_dealt` | existing anchor | – |

Flow `environment_damage` (from `CombatListener.onEntityDamage`):

| Step | From | Params |
|---|---|---|
| `iframe_gate` | same built-in, reused | as above |
| `zero_vanilla_damage` | `setDamage(0)`; STOP if raw damage ≤ 0 | – (**required**) |
| `map_cause` | `mapCauseToType` | `mapping:` map, defaulting to the current switch. `combat.cause-mapping.*` still wins. `fallback` (falls back to `combat.environment.fallback-damage-type`) |
| ↳ `pre_environment_damage` | **new** anchor | – |
| `calculate_environment_damage` | `DamageCalculator.calculateDamage(victim, type, base)` | `multiplier` (falls back to `combat.environment-damage-multiplier`) |
| `record_death_context` | same built-in | – |
| `apply_damage` | same built-in | – (**required**) |
| `clear_immune_burn` | `setFireTicks(0)` / `setFreezeTicks(0)` when immune | `types: [FIRE, LAVA]`, `freeze-types: [FREEZE]` |
| `spawn_indicator` | same built-in | – |
| `fire_on_damage_taken_abilities` | same built-in | – |
| ↳ `post_environment_damage` | **new** anchor | – |

**Note on `DamageCalculator`:** its internals (strength → crit → enchant multiplier → pipeline
multiplier → defense shred → defense → reduction% → resistances → rounding) are **not** split into
flow steps in this plan. They're already data-driven through `damage_formula.yml` and the enchant
hooks, and splitting them would duplicate that file's job and put ~10 extra steps on the hottest
path. A follow-up could expose a `damage_formula` flow if a real need shows up (§8).

---

## 6. Procedure for converting one domain (repeat per phase)

This is the exact order of work for each domain in §5, one commit per numbered step:

1. **Characterization tests first.** Before touching the domain, add tests that pin down today's
   behavior of the method being converted: inputs (mocked player, stats, table or config) in,
   observable effects out (items given, `BreakResult`, damage applied, messages sent). They use the
   existing Mockito + `ValmoraAPI.setProvider` pattern (`ExpressionTest`, `DamageCalculatorTest`).
   Tests must pass on the unmodified code.
2. **Introduce the state class** (`CatchState`, ...) and the `FlowDomain` declaration.
   Point the domain's variable provider (`$fishing.*$`, `$resource.*$`, `$dmg.*$`, ...) at the state
   object when one is on the context, and keep the old attachment keys readable.
3. **Extract built-ins.** Move each block of the method into a `BuiltinStepFactory` class under
   `module/<domain>/flow/`, **verbatim**, with no logic changes apart from reading and writing the state
   instead of locals, plus reading params. Each built-in documents its params and falls back to the
   existing `config.yml` key as in §4.3.
4. **Write the default flow** into `src/main/resources/<domain>_pipeline.yml`: `version: 2`,
   the full `flows:` block in the §5 order, a header comment explaining every step and param, and
   `stages: []` kept at the bottom. Params already covered by `config.yml` go in comments, not
   values (§4.3).
5. **Switch the call site.** Replace the method body with: build the context and state, then
   `flowRegistry.get(domain, flow).run(ctx)`, then map the `FlowResult` to the old return type.
   Delete the now-dead inline `hasAnyStages`/`runPoint` code, since anchors do that now.
6. **Register the domain** in the module's `onEnable()` (registration plus file load, replacing
   `XPipelineLoader.load()`) and clear it in `onDisable()`. The old `XPipelineLoader` class shrinks to
   the `FlowDomain` declaration or gets deleted.
7. **Run the characterization tests from step 1 unchanged.** They must pass against the default
   flow. Add flow-specific tests:
   - reordering two steps changes the outcome in the expected way;
   - `enabled: false` skips the step;
   - `conditions:` false skips the step without stopping the flow;
   - a script step inserted between two built-ins sees the state and can change it through `pipeline_set`;
   - a legacy `stages:` entry with `when: pre_x` still fires at the anchor;
   - a file without `flows:` produces the bundled flow; an unknown builtin fails the flow and keeps the last good one.
8. **Manual check on `./gradlew runServer`:** `/valmora pipeline flows <domain>` shows the flow.
   Exercise the feature in game. Edit the file to disable one step, run `/valmora reload`, and confirm
   the change. Break the file on purpose and confirm the load report error and that the last good
   flow is kept.
9. **Docs:** update `docs/modules/design/pipeline.md` (the §2 point table becomes "anchors", plus a
   new "Flows" section), `docs/modules/design/<domain>.md`, `docs/modules/user/<domain>.md`,
   `website/src/content/docs/pipelines.mdx`, and the `HARDCODED_VALUES_DECISIONS.md` rows this closes.
   Add a CHANGELOG entry.

---

## 7. Phases and order

| Phase | Content | Why this order | Exit criteria |
|---|---|---|---|
| **0** | Infrastructure (§3), `particle` event, `sound` volume/pitch args, `pipeline_set`, `/valmora pipeline flows`, validation (§3.1), bundled-default fallback (§4) | Everything depends on it | Unit tests for `CompiledFlow`, `FlowYamlLoader` (every §3.1 row), and reload atomicity. No domain converted yet, so there's no behavior change. |
| **1** | Fishing (§5.1) | Smallest (one method, low frequency). Pilot for the §6 procedure. | §6 steps 1–9 green. Double-`rollAmount` bug fixed with a regression test. |
| **2** | Resource mining (§5.2) | Self-contained, medium size, exercises gate/STOP built-ins and scheduler-touching steps | §6 green. Regen persistence (`saveState`/`loadState`) unchanged. |
| **3** | Player death/respawn (§5.3) | Small, but touches Bukkit event mutation (keep-inventory pitfall) | §6 green. Keep-inventory still clears drops. |
| **4** | Item + boss abilities (§5.4, §5.5) and mob death | Same shape in both (gate → consume → mechanics), done together to share `run_mechanics`/`resolve_target` patterns | §6 green. Cooldown/mana messages configurable. `combat:on_death` anchor still fires. |
| **5** | Combat hit + environment (§5.6) | Hot path and the most coupling (enchants, abilities, bosses, knockback, death cache) | §6 green, plus the §2.4 microbenchmark within budget, plus a manual PvE/PvP/projectile/shield/environment pass on `runServer`. |
| **6** (optional) | Move `EconomyListener` purse penalty and ON_DEATH abilities into the `death` flow. Decide whether to deprecate the `config.yml` keys duplicated by params. | Needs Phases 3–5 settled first, and involves event-priority changes | Separate plan/ADR. Not required for the goal. |

Each phase is one PR off `master` and can ship on its own. Unconverted domains keep today's
`stages:`-only behavior because `FlowYamlLoader` treats a domain that isn't registered as a legacy
`PipelineYamlLoader` file.

---

## 8. Open questions / out of scope

- **Deprecating duplicated `config.yml` keys** (`combat.iframe-threshold-factor`,
  `combat.environment-damage-multiplier`, `resource.silk-touch.enabled`, `mobs.combat-skill-id`,
  `death.keep-*-default`, ...): keep both for now (params win only when set in YAML). Revisit
  in Phase 6 with a `ConfigUpdater` migration if removing them.
- **Content packs:** should a pack be able to ship a `*_pipeline.yml` that replaces or extends a
  flow? Not in scope. `PackContentFolders` doesn't list pipeline files today. If wanted later,
  the natural shape is packs contributing script steps at anchors, not replacing whole flows.
- **Splitting `DamageCalculator`** into flow steps: deliberately not done (§5.6 note).
- **GUI blocks and enchant triggers:** already YAML-driven through HookBus. Unchanged.
- **Java API for flows:** addons keep using `HookBus.registerHook` at anchors. A public
  `FlowRegistry.insertStep(domain, flow, afterStepId, FlowStep)` API could be added later. It's not
  needed for the goal and would lock in step ids as public API, so wait for a real request.

---

## 9. Risks checklist

| Risk | Mitigation |
|---|---|
| Admin deletes a required step and breaks core gameplay | `required()` built-ins are validated (§3.1). The flow fails and the last good or bundled flow is used. |
| Edited legacy files lose built-in behavior on upgrade | Bundled-default fallback per missing flow (§4.1–4.2). |
| Combat hot-path regression | Pre-built arrays, `hasStages` guard on anchors, and a benchmark gate (§2.4, Phase 5). |
| Behavior drift during extraction | Characterization tests written **before** extraction and run unchanged after (§6.1, §6.7). Built-in code moved verbatim (§6.3). |
| Reload mid-hit or mid-break | `AtomicReference` swap. In-flight calls finish on the old array. Built-ins hold no per-call state outside the state object. |
| `ExecutionContext` or state captured in delayed tasks (for example `schedule_regen`) | Built-ins copy only the values they need (`Block`, `Material`, key) into the task, per CLAUDE.md §6.3a. Never the context or state. |
| Anchors removed while Java hooks are registered | `/valmora validate` WARN listing the orphaned hook ids (§4.5). |

---

## 10. As built (2026-09-27)

Phases 0–5 are done. The reference description is now `docs/modules/design/pipeline.md` §8.

**Tests.** Each domain got characterization tests. They were written first, passed on the old code,
and pass unchanged on the flows: `FishingCatchFlowTest` (7), `ResourceBreakFlowTest` (9),
`DeathFlowTest` (6), `AbilityCastFlowTest` (10), `MobFlowTest` (9), `CombatFlowTest` (13). Two more
test classes cover the loader rules and the shipped files: `FishingFlowFileTest` (16: disable,
conditions, reorder, `pipeline_set`, interrupt, expression params, fallbacks, versioning, warnings)
and `BundledPipelinesTest` (12). The full suite passed with 1238 tests and 0 failures.

**Differences from the plan:**

| Plan | As built | Why |
|---|---|---|
| New `particle` DSL event, plus volume/pitch arguments for `sound` (§5.1) | Generic `play_sound` and `spawn_particle` built-in steps (`FeedbackSteps`) | Fewer moving parts. The feedback flows need block-data particles at an offset, which a one-line event expresses badly. |
| `pipeline_set` keys checked at load time | Checked at load time only inside pipeline files (thread-local compiling domain). Anywhere else they're checked at run time and reported once. | An event factory doesn't know which flow it will run in. |
| Anchors: "`interrupt` means STOP" | `AnchorSpec.stopsOnInterrupt`, which is false for the informational points. Can be overridden per step with `stop-on-interrupt:`. | Keeps the old rule that an interrupt at `post_*` points has no effect. |
| (not in plan) | `pipeline:interrupted` is cleared around each anchor and script step. | Before, the flag leaked into later points' stages. |
| (not in plan) | `FlowResult.SKIPPED` when a flow never loaded. Callers then fall back to vanilla (resource → `NOT_TRACKED`, combat → vanilla damage). | A missing flow must not mean "a break that gives nothing". |
| (not in plan) | `FlowContext`, a context with a settable target. | Abilities resolve their target inside the flow. |
| (not in plan) | New `$death.*$` namespace (`DeathVariableProvider`). | The old `player:is_bed_spawn` attachments were never readable from scripts. They're still set for Java hooks. |
| Breaking-power message as a gate param | Stays in `ResourceListener`. | Mining Spread runs the same flow for neighbour blocks and must not message the player. |
| `resolve_resource` / `advance_stage` / `schedule_regen` | As planned, and all three are required. `schedule_regen` creates the tracker itself if it runs first. | Robust to reordering. |
| Microbenchmark: at most 10% of per-hit time | Mock-based full-hit timing wasn't usable, because Mockito records every call. The flow machinery was measured on the `entity_hit` shape: about 16 ns per hit over direct calls (36 vs 20 ns). A real hit is a few microseconds, but it hasn't been measured on a server. | See `pipeline.md` §8.4. |
| §6 step 8: manual check with `./gradlew runServer` | **Not done.** Needs an interactive server. | Still to do. |

**Bug fixed on the way:** fishing rolled the amount twice, so the amount given and
`$fishing.amount$` could differ.

**Also changed:** `/valmora pipeline flows [domain]`, the `pipeline_set` event, two new combat
anchors (`pre_environment_damage` and `post_environment_damage`), and writable keys per flow
(listed in each file's header). Docs updated: `pipeline.md`, `death.md`,
`HARDCODED_VALUES_DECISIONS.md` (HC-046 done), `CHANGELOG.md`, the website pages (pipelines,
fishing, death, resource-nodes, developer-hookbus, file-layout, scripting) and the reference files
(commands, events, variables).
