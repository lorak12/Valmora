# Modifier Framework — Implementation Status & Backlog

Tracks what's actually built vs. deferred from `docs/Valmora_Modifier_Framework_Design.docx`, on
branch `modifier-framework`. Read the design doc first; this file is the delta against it.

> **Update:** the legacy `org.nakii.valmora.module.reforge` package has since been deleted and
> reforges fully migrated onto this engine (`modifiers/groups/reforges.yml`, `modifiers/
> definitions/reforges.yml`, `items/reforge_stones.yml`, `modifiers/recipes/reforges.yml`). Item 3
> below (originally "Reforges are NOT migrated") is now **done** — see
> `docs/modules/design/modifier.md` §6.1 for how the old per-rarity tables were reproduced losslessly
> via `TierSource.RARITY_RANK` instead of a banned rarity-name-keyed map, and §4 for the `custom_anvil`
> ordering caveat this migration surfaced (documented there, not fixed).

## Built

- **Rarities are data-driven** (§4): `rarities.yml` + `RarityModule`/`RarityRegistry`/
  `RarityDefinition`, loaded before `item`/`modifier`. Keyed by the legacy `Rarity` enum constant
  name so the existing on-item PDC representation (`Keys.RARITY_KEY`) needs no migration.
- **Generic value resolution** (§5/§18): `ValueResolver` + `LiteralValue`/`ExpressionValue`/
  `RarityScaleValue`/`CustomValue`, parsed by `ValueParser`. `$item.rarity.*$` is exposed to
  expressions via `ItemRarityVariableProvider` (namespace-scoped to `rarity.*` only — see below).
- **Core model** (§3/§6/§7): `ModifierGroupDefinition`/`ModifierDefinition`/`ModifierTier`/
  `ModifierStateDefinition`, `ModifierGroupRegistry`/`ModifierRegistry`, YAML parsers. No group-name
  special-casing anywhere in the engine (§23).
- **Effect types** (§8): `StatEffect` (ADD reliably; MULTIPLY is a documented best-effort
  approximation — see Known Limitations), `AbilityEffect` (reuses `AbilityDefinition`/
  `MechanicRegistry` wholesale, §9), `EventEffect` (reuses `EventParser`/`CompiledEvent`), `StateEffect`
  (applied once at attachment). `ModifierEffectRegistry` is the Java extension point for more types.
- **Component storage** (§3): `ModifierComponentStore` — one `TAG_CONTAINER_ARRAY` PDC key per
  group, one nested container per `ModifierInstance` (id/tier/count/state).
- **Application semantics** (§6/§15): `ModifierEngine.apply/remove` — EXCLUSIVE/STACKABLE/MULTIPLE,
  capacity, replacement, removal, id/tag conflicts.
- **Stat integration** (§21 task 7): `StatManager.recalculateStats` calls
  `ModifierEngine.contributeStats` for every equipped item, alongside the existing item/enchant/pet
  steps — no double-counting since this only reads the new `modifiers:<group>` components, never the
  legacy `Keys.STATS_CONTAINER_KEY` baked stats.
- **PASSIVE ability integration**: `ModifierEngine.applyPassiveAbilities`, wired into the same
  `StatManager` pass that already fires item-defined PASSIVE mechanics.
- **Recipe integration** (§16): `APPLY_MODIFIER`/`REMOVE_MODIFIER` via `ModifierRecipeDefinition` +
  `ModifierAnvilHandler` (a `DynamicMachineHandler` for machine id `custom_anvil`, same shape as
  `ReforgeModule`'s `reforge_anvil`), fed from `modifiers/recipes/*.yml`. `guis/modifier_anvil.yml`
  is the front-end GUI.
- **Default content pack**: `modifiers/groups/gemstones.yml` (`gemstones` + a demo `traits` group),
  `modifiers/definitions/gemstones.yml` (tiered `ruby`/`sapphire`, §11), `modifiers/definitions/
  traits.yml` (rarity-scaled `fierce`, conditional-MULTIPLY `low_health_fury`, ability-granting
  `thundering` — §12/§17), `items/gemstones.yml` (the physical gem items), `modifiers/recipes/
  gemstones.yml`.
- **Java extension points** (§20): `ModifierGroupRegistry`/`ModifierRegistry` are plain registries a
  plugin can populate directly; `ModifierEffectRegistry` and `ModifierValueResolverRegistry` are
  dedicated static registries for custom effect types / value providers.
- **Reforges migrated, legacy module deleted.** `org.nakii.valmora.module.reforge` (Java
  `ReforgeModule`/`ReforgeDefinition`/`ReforgeCommand`/`ForgeCostRegistry`, the `reforge_anvil`/
  `forge_random` handlers, `Keys.REFORGE_ID_KEY`/`REFORGE_POOL_KEY`/`REFORGE_DISPLAY_KEY`,
  `ItemDefinition.reforgePool`/`reforge-pool:` YAML, `resources/reforges/*.yml`,
  `resources/enchant/forge_costs.yml`, `guis/reforge.yml`/`guis/reforge_anvil.yml`) is gone. Content
  moved to `modifiers/groups/reforges.yml` + `modifiers/definitions/reforges.yml` (one entry per
  legacy reforge, `tiers: 1..7` = rarity rank+1, exact same per-rarity numbers as the old
  `stat-bonuses-by-rarity` tables), `items/reforge_stones.yml` (static stone items replacing
  `ReforgeModule.createReforgeStone`), `modifiers/recipes/reforges.yml` (exact-apply per reforge +
  `random_reforge` using the new `applyRandom`/`RANDOM` sentinel + `custom_anvil`). Required two
  generic engine additions beyond the original design doc: `TierSource.RARITY_RANK` (a group-level
  policy — not reforge-specific — that derives a modifier's effective tier from the item's current
  rarity rank instead of the tier stored on the instance) and rarity-scaled recipe costs
  (`ModifierRecipeDefinition.costCoins`/`costXpLevels` are `ValueResolver`s, resolved against the
  base item's rarity — reused for `rarities.yml`'s new `forge_cost` `extra` property, replacing
  `ForgeCostRegistry`). The admin `/reforge` command was replaced by a generic `/modifier`
  command (works for any group). See `docs/modules/design/modifier.md` for full detail.

## Explicitly deferred (not done this pass)

1. **Non-passive ability trigger dispatch for modifier-granted abilities.** `AbilityEffect` parses
   and stores a full `AbilityDefinition`, but `AbilityExecutor.fire(...)` currently takes an
   `ItemDefinition`, not a bare ability list — ON_HIT/RIGHT_CLICK/EQUIP/etc. modifier abilities are
   not fired yet. Needs either an `AbilityExecutor` overload taking `List<AbilityDefinition>` (plus
   per-ability cooldown/mana key namespacing so a modifier ability doesn't collide with an item
   ability of the same id) or a synthetic `ItemDefinition` wrapper, then a hook in
   `AbilityTriggerListener` alongside the existing item-ability lookups.
2. **EVENT/STATE trigger-bound execution.** `EventEffect` stores a compiled action list but nothing
   currently fires it (would piggyback on the same trigger dispatch work as #1). `StateEffect` is
   applied once at attachment time; the doc's `ON_KILL -> STATE ADD souls` pattern (§13) additionally
   needs #1.
3. **Full `$item.*$` variable provider.** Only `$item.rarity.*$` is implemented
   (`ItemRarityVariableProvider`). `$item.type$`, `$item.stats.*$` etc. from the doc's examples are
   not implemented — `ExecutionContext` has no general notion of "the item this expression is about"
   yet; `ModifierEngine` currently attaches only rarity via a context key.
4. **`ModifierGroupRegistry`/`ModifierStorage`/`Codec` Java builder API surface** (§20's
   `ModifierGroup.builder(...)`) — groups/modifiers are populated by the YAML parsers only right now;
   a plugin can call `.register(...)` directly with a hand-built `ModifierGroupDefinition`, but there
   is no fluent builder yet.
5. **Item-upgrade `keep-data-on-upgrade` inheritance** (§21 task 11) — this system doesn't exist
   anywhere in the codebase yet (confirmed by recon — greenfield, not a modifier-specific gap).
6. **Migration/validation diagnostics** (§21 tasks 12–13: reload-time validation for unknown
   groups/modifiers/targets, duplicate-exclusive detection, malformed value providers, and a reload/
   serialization/upgrade-inheritance test suite) — only load-time parse errors are reported today
   (via the standard `YamlLoader` warning batch); no cross-reference validation pass.
7. **`custom_anvil` first-match-wins recipe ordering** across groups isn't deterministic when two
   addition-less recipes from different groups could both match the same bare item (see
   `docs/modules/design/modifier.md` §4) — avoided by construction in shipped content, not fixed in
   the engine.

## Known limitations

- `StatEffect.Operation.MULTIPLY` reads the *current* `StatManager` effective value for that stat at
  the moment this item's contribution is resolved and adds `current * (value - 1)`. This is
  order-dependent (which item/modifier resolves first affects the result) because `StatManager` has
  no multi-pass/final-multiplier resolution stage — the same limitation the pre-existing engine has
  for e.g. armor set bonuses. ADD is unaffected and is the primary case in the doc's own examples.
- `EVENT`/`TRIGGER` effect `actions:` only accepts plain DSL strings (`EventParser.parse`). The
  doc's §13 example nests structured `type: STATE` maps inside a trigger's `actions:` list; that
  shape isn't parsed (state mutation is a top-level `STATE` effect here instead, applied at
  attachment — see item 2 above for the trigger-bound case).
