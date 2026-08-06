# Extensible Pipeline System (HookBus) — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Class:** `org.nakii.valmora.api.pipeline.HookBus` | **Cross-cutting** — not owned by a single
> module; consumed by combat, resource, fishing, item, mob, and gui.
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

**Zero cost when unused:** every call site checks `HookBus.hasStages()`/`hasAnyStages()` before
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
| `combat:on_death` | In `MobDeathListener`, after XP/gold/loot are resolved for a custom mob's death. Only fires if there's a killer. | No effect — informational only. |
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

`item:*`/`mob:*` are **supplementary** to their domain's native gating (trigger matching, cooldown,
mana, boss timers/announcements) — none of that native machinery was replaced, only extended.
`gui:*` **is** the actual dispatch now (§6), not a supplement — see there for why that's safe.

## 3. YAML Stage Format

One file per domain, loaded from the plugin data folder (auto-copied on first run, never
overwritten): `combat_pipeline.yml`, `resource_pipeline.yml`, `fishing_pipeline.yml`,
`item_pipeline.yml`, `mob_pipeline.yml`. All optional — ship with `stages: []`. (GUI has no
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
