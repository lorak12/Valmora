# Extensible Pipeline System (HookBus) — Design & Code

> **Version:** 0.2 (flows) | **API:** Paper 1.21.x | **Java:** 21
> **Classes:** `org.nakii.valmora.api.pipeline.HookBus`, `org.nakii.valmora.api.pipeline.flow.*`,
> `module/script/pipeline/FlowYamlLoader` | **Cross-cutting** — not owned by a single module;
> consumed by combat, death, resource, fishing, item, mob, and gui.
>
> **0.2:** the hardcoded sequences themselves now live in the `*_pipeline.yml` files as **flows**
> (§8). The insertion points below are still there — as `anchor:` steps inside those flows.
> Implementation plan and decisions: `docs/PIPELINE_FLOWS_PLAN.md`.
>
> Full original design rationale: `docs/COMBAT_PIPELINE_ANALYSIS.md` (kept for historical context —
> this document describes what actually shipped and is the authoritative reference going forward).

---

## 1. Why This Exists

Combat, resource mining, fishing, item abilities, mob (boss) abilities, and GUI lifecycle events
each have a hardcoded Java sequence (hit → calculate → apply, break → gate → drops, cast → roll →
grant, trigger → cooldown/mana → mechanics, open/close/update → conditions → actions). `HookBus`
is a single shared dispatch primitive that lets **named insertion points** inside those sequences be
extended without touching Java — either via YAML (`*_pipeline.yml` files, using the same
condition/event DSL as everything else) or via a Java hook registered through `ValmoraAPI`.

It deliberately reuses the exact `Condition`/`CompiledEvent` primitives GUI event blocks and
ability triggers already used before this system existed, rather than introducing yet another
bespoke trigger-dispatch implementation. `PipelineYamlLoader` is the one shared "read `stages:`,
compile `id`/`when`/`conditions`/`on-pass`/`on-fail`, register" implementation every
`*_pipeline.yml` loader delegates to; `GuiEventBlockStage` (§6) is the equivalent adapter for GUI's
pre-existing `on-open`/`on-close`/`on-slot-update`/`on-update` blocks, which now run through the
same bus instead of GUI's own separate inline dispatch code.

**Zero cost when unused (pre-flows wording, still true for the bus itself):** every anchor checks
`HookBus.hasStages()` before touching the bus; the flows that now carry the domain logic are
covered in §8.4. Originally every call site checked `hasStages()`/`hasAnyStages()` before
building an `ExecutionContext` or touching the bus (GUI's points are the one exception — see §6 for
why they always dispatch through the bus, and why that's still zero-cost). An install with no
`*_pipeline.yml` stages and no registered Java hooks runs the exact same code path as before this
system existed.

## 2. Registered Insertion Points

| Point | Fires | `interrupt` means |
|---|---|---|
| `combat:pre_damage` | Before `DamageCalculator.calculateDamage()`. | Cancels the hit entirely. |
| `combat:post_calculation` | After the `DamageResult` is known, before it's applied to health. | The hit was "rolled" but does not land (no health change, no indicator). |
| `combat:post_application` | After health is reduced and the damage indicator has spawned. | No effect (too late to matter) — informational only. |
| `combat:on_dmg_dealt` | Same timing as `post_application`, after `ON_HIT` item abilities / boss triggers fire. | No effect — informational only. |
| `combat:on_death` | Anchor of `mob_pipeline.yml`'s `death` flow, after XP/gold/loot are resolved for a custom mob's death. Only fires if there's a killer (a default `conditions:` on the anchor). Stages still live in `combat_pipeline.yml`. | No effect — informational only. |
| `combat:pre_environment_damage` | *(new with flows)* `environment_damage` flow — cause mapped, damage not yet calculated. | Cancels it (vanilla's damage is already zeroed). |
| `combat:post_environment_damage` | *(new with flows)* after environmental damage landed. | No effect — informational only. |
| `player:on_death` / `player:on_respawn` | `death_pipeline.yml`'s `death`/`respawn` flows. | No effect — informational only. |
| `resource:pre_break` | After the Breaking Power gate passes, before drops are rolled — **only for zone-configured resource blocks**, not ordinary block breaking. | Cancels the break (same as insufficient power, but silent — the stage should `notify` the player itself). |
| `resource:post_break` | After drops are granted and the block has progressed/regenerated. | No effect — informational only. |
| `fishing:pre_catch` | Before the sea-creature roll / loot table roll. | Cancels the catch — nothing is granted. |
| `fishing:post_catch` | After the reward (item or sea creature) is resolved. | No effect — informational only. |
| `item:pre_ability` | After an item ability's own trigger/condition/cooldown/mana gate passes, before its mechanics run. | Cancels just that firing — mechanics don't run, but the cooldown/mana already consumed stays consumed. |
| `item:post_ability` | After the ability's mechanics have run. | No effect — informational only. |
| `mob:pre_ability` | After a boss ability's own cooldown/interval/chance gate passes, before its mechanics run. | Cancels just that firing (e.g. a "silence" debuff) — the cooldown already consumed stays consumed. |
| `mob:post_ability` | After the ability's mechanics have run. | No effect — informational only. |
| `gui:<guiId>:on_open` | A GUI's `on-open` block. | Aborts opening the GUI — same as a failed `conditions:` gate always did. |
| `gui:<guiId>:on_close` | A GUI's `on-close` block. | No effect — the GUI is already closing regardless. |
| `gui:<guiId>:on_slot_update` | A GUI's `on-slot-update` block. | No effect — the GUI always re-renders after, regardless of outcome. |
| `gui:<guiId>:on_update` | A GUI's `on-update` (repeating timer) block. | No effect — the GUI always re-renders after, regardless of outcome. |

Since 0.2 each non-GUI point is an **anchor step** in its domain's flow (§8), so the "Fires" column
describes where the default flow puts it — an admin can move it. "No effect" points are anchors
with `stopsOnInterrupt = false`: an `interrupt` there never cuts the rest of the flow short
(overridable per anchor with `stop-on-interrupt:`). Trigger matching and boss timers stay native;
cooldown/mana/announce gating is now built-in flow steps.
`gui:*` **is** the actual dispatch now (§6), not a supplement — see there for why that's safe.

## 3. YAML Stage Format

One file per domain, loaded from the plugin data folder (default files follow updates through
`ResourceManifest` — see `versioning.md` §3): `combat_pipeline.yml`, `death_pipeline.yml`,
`resource_pipeline.yml`, `fishing_pipeline.yml`, `item_pipeline.yml`, `mob_pipeline.yml`. Each
ships its full default `flows:` (§8) and an empty legacy `stages:` list, described here. (GUI has no
separate pipeline file — its stages are the `on-open`/etc. blocks already defined per-GUI in
`guis/*.yml`, see `docs/modules/design/gui.md`.)

```yaml
stages:
  - id: berserker_rage                 # unique per file; shown by `/valmora pipeline list <point>`
    when: pre_damage                   # one of the point names from §2, without the domain prefix
    conditions:
      - "$player.hp$ < $player.max_hp$ * 0.25"   # same syntax as any other condition string
    on-pass:
      - "multiply_damage 1.5"
      - "notify <red>Berserker Rage! Fighting below 25% HP!</red>"
    on-fail: []
```

`conditions` uses `ConditionParser`, `on-pass`/`on-fail` use `EventParser` — exactly like GUI event
blocks. See `docs/modules/design/script.md` for the `interrupt`, `notify`, `multiply_damage`,
`counter`, `entity`, `apply_potion`, `give_coins`/`take_coins` events added specifically for
pipeline stages.

A mid-list `condition` event inside `on-pass` throwing `ConditionAbortException` falls back to
running `on-fail` — same as everywhere else in the engine — but the stage still reports `CONTINUE`
afterward, not `INTERRUPT`: a pipeline point is a list of independent stages, so one stage aborting
shouldn't by itself stop the rest of the list. Use the explicit `interrupt` event if a stage needs
to stop later stages at the same point.

## 4. Damage/Enchant Ordering Rule

`combat:pre_damage` stages run **before** `DamageCalculator` computes anything, so they can't touch
`DamageModifierContext` directly (that's still exclusively the enchant `modifyAttack`/`modifyDefend`
hooks — see `docs/modules/design/combat.md`). Instead, a stage calls `multiply_damage <factor>`,
which stacks a `dmg:pipeline_multiplier` context attachment (starting at `1.0`, multiplicative
across repeated calls within the same hit). `DamageCalculator` applies it at one fixed, documented
point:

```
fullDamage = baseDamage × (1 + strength/100)
if isCritical: fullDamage ×= (1 + critDamage/100)
fullDamage ×= enchantDamageMultiplier          # modifyAttack/modifyDefend hooks
fullDamage ×= pipelineMultiplier               # <-- multiply_damage contribution goes here
mitigated = fullDamage × (100 / (defense + 100))   # skipped if damage type ignores defense
finalDamage = floor(mitigated)
```

I.e. the pipeline multiplier is the outermost attacker-side buff — applied after strength/crit/
enchants (so a stage reacts to the fully-computed attacker output rather than racing enchants for
calculation order) but before defense mitigation (so it behaves like a strength/crit buff, not a
defense-bypass effect). Use a damage type with `ignores-defense: true` if a stage needs to bypass
defense entirely instead.

## 5. Registering a Java Hook (Addon Plugins)

No DSL required — any plugin (including first-party Valmora code) can react to a point directly:

```java
ValmoraAPI.getInstance().getHookBus().registerHook(
    "combat:pre_damage",
    "my-plugin:dodge-check",
    context -> {
        if (Math.random() < 0.1) {
            context.getPlayerCaster().ifPresent(p -> p.sendMessage("Dodged!"));
            return StageResult.INTERRUPT;
        }
        return StageResult.CONTINUE;
    }
);
```

Java hooks persist across `/valmora reload` (unlike YAML stages, which are cleared and recompiled
every reload) — the registering plugin is responsible for calling `unregisterHook(point, id)` on
its own shutdown if it registers from a non-Valmora plugin's `onDisable()`.

## 6. GUI Retrofit — `GuiEventBlockStage`

GUI event blocks predate `HookBus` and have their own long-standing semantics that don't match the
generic `CompiledPipelineStage` shape (§3) exactly:

- A **failed condition on `on-open`** must abort opening the GUI — that's a hard gate, not "run
  `on-fail` and move on."
- `on-close`/`on-slot-update`/`on-update` always continue to their own follow-up work (persisting
  storage, re-rendering) **regardless** of whether the block's condition passed or its actions
  aborted.

`GuiEventBlockStage` (`org.nakii.valmora.module.gui`) reproduces this exactly instead of forcing it
into the generic pass/continue contract: it reports `StageResult.INTERRUPT` whenever the block's own
condition failed or its actions aborted (`ConditionAbortException`), and `CONTINUE` otherwise. Each
GUI definition registers its (at most four) blocks onto the bus once at load time; the call sites in
`GuiModule`/`GuiListener` then do:

- **`on-open`:** `if (!hookBus.runPoint(point, ctx)) return;` — aborts the open, identical to the
  pre-retrofit inline dispatch.
- **`on-close` / `on-slot-update` / `on-update`:** `hookBus.runPoint(point, ctx);` with the return
  value ignored — always falls through to persist/re-render, identical to before.

Because each GUI only ever registers its own single block at each of its four points, this is
"zero cost when unused" in the sense that matters: no new conditions are evaluated that weren't
already being evaluated by the pre-retrofit inline dispatch. What's new is that a Java addon hook
registered at the same point (e.g. `"gui:shop:on_open"`) now runs *before* the GUI's own block and
can veto the open itself — something impossible before this retrofit — and `/valmora pipeline list`
can show what's registered on any GUI's lifecycle points.

One behavior actually changed by this retrofit, not just re-plumbed: `on-close` previously ignored
its own `conditions:`/`fail-actions:` entirely (only `actions:` ever ran, unconditionally) — an
inconsistency with the other three blocks. `GuiEventBlockStage` fixes this; `on-close` now honors
`conditions:`/`fail-actions:` the same way `on-open`/`on-update`/`on-slot-update` always did.

## 7. Inspecting the Bus at Runtime

`/valmora pipeline list [point]` — lists every registered stage/hook, optionally filtered to one
insertion point.

`/valmora pipeline flows [domain]` — lists every loaded flow (and whether it came from the file or
the bundled default), or one domain's flows step by step (kind, `[conditions]` marker).

## 8. Flows — the Domain Logic in YAML

Before 0.2 the files could only add stages at fixed points; the sequences themselves were Java
methods (`CombatListener.onEntityDamageByEntity`, `ResourceManager.handleBlockBreak`, ...). Now
each such method is a **flow**: a list of steps compiled from the file, and the method just builds
the flow's state and runs it.

### 8.1 Model (`api/pipeline/flow/`)

| Type | Role |
|---|---|
| `FlowDomain` | One file: id, file name, HookBus point prefix, its `FlowSpec`s, and the point names legacy `stages:` may use as `when:`. |
| `FlowSpec<S>` | One flow: id (key under `flows:`), the `BuiltinStepFactory`s and `AnchorSpec`s it may contain, the keys `pipeline_set` may write, and the current `CompiledFlow` in an `AtomicReference`. `static final` in each domain's `XFlows` class, so the last good flow survives a failed reload. |
| `BuiltinStepFactory<S>` | A built-in step type: id, `required()`, accepted `paramKeys()`, `compile(StepParams)` → `BuiltinStep<S>`. |
| `FlowState` | Per-run mutable state (`CatchState`, `BreakState`, `HitState`, ...). `publish(ctx)` copies its script-visible values onto the context; `write(key, value)` applies `pipeline_set`. |
| `CompiledFlow` | Immutable `FlowStep[]`; `run(ctx, state)` → `FlowResult` (`COMPLETED`, `stopped(stepId)`, or `SKIPPED` when the flow never loaded — callers then fall back to vanilla). |
| `BuiltinFlowStep` / `AnchorFlowStep` / `ScriptFlowStep` | The three step kinds. A false `conditions:` skips a built-in/anchor; a script step keeps `CompiledPipelineStage` semantics (on-fail) and `interrupt` → STOP. |
| `StepParams` / `NumberParam` | Param access with load-time reporting; numeric params may be expressions (compiled once); `NumberParam.config(path, def)` / `StepParams.configBool/configString` are the live `config.yml` fallbacks. |
| `FlowContext` | A context whose target a step can set (abilities resolve their target mid-flow). |
| `FlowRegistry` | Held by `ScriptModule` next to the bus; backs the command. |

Domains: `FishingFlows` (`catch`, `bite`), `ResourceFlows` (`break`, `mine_feedback`,
`denied_feedback`, `regen_feedback`), `DeathFlows` (`death`, `respawn`), `AbilityFlows` (`cast`),
`MobFlows` (`cast`, `death`), `CombatFlows` (`entity_hit`, `environment_damage`). Generic
`play_sound`/`spawn_particle` built-ins for feedback flows: `module/script/pipeline/FeedbackSteps`.
Each shipped file's comments document its built-ins and params; `BundledPipelinesTest` fails if a
shipped file doesn't load cleanly or leaves out a built-in.

### 8.2 Loading (`FlowYamlLoader`)

- The jar's copy of the file is the only source of defaults: a flow missing from the data-folder
  file — or the whole file missing, or in the old stages-only format — compiles from the bundled
  copy. There's no separate Java default to drift.
- A flow with an ERROR (unknown `builtin:`/`anchor:`, a `required()` built-in missing or
  disabled, duplicate step id, a broken required param) keeps its previous compiled version, or
  the bundled one on first load. `version:` newer than `SUPPORTED_VERSION` (2) is refused.
- Legacy `stages:` still compile onto the bus (`PipelineYamlLoader.registerStages`) and run at the
  matching anchor. A stage whose anchor no flow contains, and Java hooks sitting on a removed
  anchor, are reported as warnings.
- `pipeline_set` keys are checked at load time against the compiling domain's writable keys
  (`FlowYamlLoader.compilingDomain()`); elsewhere they're checked when they run.
- Content packs keep merging into `stages:` only (`SharedConfigMerger` appends list entries), which
  still works; packs can't replace flows.

### 8.3 Params and `config.yml`

A param set in the file wins; otherwise the pre-flow `config.yml` key (read live, as before)
applies, then the literal default. The shipped files leave those params out and name the
`config.yml` key in a comment, so existing tuning keeps working.

### 8.4 Cost

- A flow always runs now (it *is* the logic). Measured on the `entity_hit` shape (17 built-ins,
  4 empty anchors, one context allocation): ~36 ns per run vs ~20 ns for the same 17 calls made
  directly — ~16 ns per hit.
- State is published to the context **lazily** — only before conditions, script steps,
  anchors with registered stages, and expression params. A default flow with no script content
  writes no attachments. `calculate_damage` only passes the context to `DamageCalculator` (for
  the `multiply_damage` lookup) when something script-side has seen the hit.
- The `pipeline:interrupted` flag is cleared around every anchor and script step; before flows it
  stayed set on the shared combat context and could stop stages at a later point.
