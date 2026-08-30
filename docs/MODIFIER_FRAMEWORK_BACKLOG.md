# Modifier Framework — Implementation Status & Backlog

Tracks what's actually built vs. deferred from `docs/Valmora_Modifier_Framework_Design.docx`, on
branch `modifier-framework`. Read the design doc first; this file is the delta against it.

## Built

- **Rarities are data-driven** (§4): `rarities.yml` + `RarityModule`/`RarityRegistry`/
  `RarityDefinition`, loaded before `item`/`modifier`. Keyed by the legacy `Rarity` enum constant
  name so the existing on-item PDC representation (`Keys.RARITY_KEY`) needs no migration.
  `RarityDefinition` also carries an open `extra` numeric property map for anything beyond
  `id`/`name`/`color`/`rank`/`power` a content author wants (e.g. `forge_cost`, §6.1).
- **Generic value resolution** (§5/§18): `ValueResolver` + `LiteralValue`/`ExpressionValue`/
  `RarityScaleValue`/`CustomValue`, parsed by `ValueParser`. Reused beyond modifier effect values —
  recipe costs (`ModifierRecipeDefinition.costCoins`/`costXpLevels`) are `ValueResolver`s too.
- **Core model** (§3/§6/§7): `ModifierGroupDefinition`/`ModifierDefinition`/`ModifierTier`/
  `ModifierStateDefinition`, `ModifierGroupRegistry`/`ModifierRegistry`, YAML parsers. No group-name
  special-casing anywhere in the engine (§23). `ModifierGroupDefinition.tierSource` (`INSTANCE`/
  `RARITY_RANK`) and `ModifierDefinition.targetItemTypes`/`weight` are generic additions beyond the
  original design doc, added to support the reforge migration losslessly (see below) — neither is
  reforge-specific.
- **Effect types** (§8): `StatEffect` (ADD reliably; MULTIPLY is a documented best-effort
  approximation — see Known Limitations), `AbilityEffect`, `EventEffect`, `StateEffect` (still
  attach-time only — see Deferred). `ModifierEffectRegistry` is the Java extension point for more
  types.
- **Component storage** (§3): `ModifierComponentStore` — one `TAG_CONTAINER_ARRAY` PDC key per
  group, one nested container per `ModifierInstance` (id/tier/count/state).
- **Application semantics** (§6/§15): `ModifierEngine.apply/remove/applyRandom` — EXCLUSIVE/
  STACKABLE/MULTIPLE, capacity, replacement, removal, id/tag conflicts, weighted random selection
  excluding what's already attached (the generic `forge_random` replacement).
- **Stat integration** (§21 task 7): `StatManager.recalculateStats` calls
  `ModifierEngine.contributeStats` for every equipped item, alongside the existing item/enchant/pet
  steps — no double-counting since this only reads the new `modifiers:<group>` components, never the
  legacy `Keys.STATS_CONTAINER_KEY` baked stats.
- **Full ability/event trigger dispatch.** `AbilityEffect`/`EventEffect` fire on every trigger, not
  just PASSIVE: `ModifierEngine.getGrantedAbilities`/`getGrantedEventActions` (condition-gated, tier-
  and rarity-aware) feed `AbilityExecutor.fireModifiersForItem`/`fireModifiersHeld`, wired into every
  existing trigger call site (`AbilityTriggerListener`: ON_KILL/SNEAK/ON_SHOOT/ON_TELEPORT/EQUIP/
  UNEQUIP; `CombatListener`: ON_HIT/ON_DAMAGE_TAKEN, both the main damage path and the
  no-attacker-entity fallback). ABILITY effects get full cooldown/mana/condition gating via a shared
  `AbilityExecutor.fireOne` extracted from the original item-ability `fire(...)`; EVENT effects fire
  unconditionally (their own `conditions:` aside) since they're not "abilities". PASSIVE still goes
  through the dedicated `ModifierEngine.applyPassiveAbilities` (no cooldown gating, matches the
  pre-existing item-passive behavior — mechanics run every stat recalculation).
- **Recipe integration** (§16): `APPLY_MODIFIER`/`REMOVE_MODIFIER` via `ModifierRecipeDefinition` +
  `ModifierAnvilHandler` (`DynamicMachineHandler` for machine id `custom_anvil`), fed from
  `modifiers/recipes/*.yml`. `guis/modifier_anvil.yml` is the front-end GUI. `addition:` is optional
  (a recipe without one matches the base item alone, e.g. a random reroll); `modifier.id: RANDOM` is
  a recognized sentinel for `applyRandom`; `priority:` (default 0, higher first, stable otherwise)
  controls match order when multiple recipes could match the same input.
- **Reload-time cross-reference validation** (§21 tasks 12–13, partial): `ModifierValidator` checks
  every modifier's `group:` reference, `conflicts.ids` references, and STATE-effect keys against the
  declared `state:` map; every recipe's `modifier.group`/`modifier.id`/`addition.item` references —
  logged as warnings (see Known Limitations for what this doesn't do).
- **Default content pack**: `modifiers/groups/gemstones.yml` (`gemstones` + a demo `traits` group),
  `modifiers/definitions/gemstones.yml` (tiered `ruby`/`sapphire`, §11), `modifiers/definitions/
  traits.yml` (rarity-scaled `fierce`, conditional-MULTIPLY `low_health_fury`, ability-granting
  `thundering` — §12/§17, and `thundering`'s ON_HIT now actually fires), `items/gemstones.yml`,
  `modifiers/recipes/gemstones.yml`.
- **Java extension points** (§20): `ModifierGroupRegistry`/`ModifierRegistry` are plain registries a
  plugin can populate directly — with a fluent builder to do it (`ModifierGroupDefinition.builder(id)`
  / `ModifierDefinition.builder(id, groupId)`, matching the doc's `ModifierGroup.builder(...)`
  illustration); `ModifierEffectRegistry` and `ModifierValueResolverRegistry` are dedicated static
  registries for custom effect types / value providers.
- **`$item.*$` variables**: `rarity.*` (id/name/color/rank/power), `type`, `id`, `stats.<statId>`
  (the item's own baked stat map — not dynamically-resolved effective stats, to avoid recursing into
  `contributeStats`), all served by
  `org.nakii.valmora.module.script.variable.providers.ItemAbilityVariableProvider` — see Known
  Limitations for why this lives there and not in the `modifier` package.
- **Reforges migrated, legacy module deleted.** `org.nakii.valmora.module.reforge` (Java
  `ReforgeModule`/`ReforgeDefinition`/`ReforgeCommand`/`ForgeCostRegistry`, the `reforge_anvil`/
  `forge_random` handlers, `Keys.REFORGE_ID_KEY`/`REFORGE_POOL_KEY`/`REFORGE_DISPLAY_KEY`,
  `ItemDefinition.reforgePool`/`reforge-pool:` YAML, `resources/reforges/*.yml`,
  `resources/enchant/forge_costs.yml`, `guis/reforge.yml`/`guis/reforge_anvil.yml`) is gone. Content
  moved to `modifiers/groups/reforges.yml` + `modifiers/definitions/reforges.yml` (one entry per
  legacy reforge, `tiers: 1..7` = rarity rank+1 via `TierSource.RARITY_RANK`, exact same per-rarity
  numbers as the old `stat-bonuses-by-rarity` tables — a lossless mechanical transcription, not a
  rebalance), `items/reforge_stones.yml` (static stone items replacing
  `ReforgeModule.createReforgeStone`), `modifiers/recipes/reforges.yml` (exact-apply per reforge +
  `random_reforge`). Rarity-scaled recipe costs replace `ForgeCostRegistry` (`rarities.yml`'s new
  `forge_cost` property). The admin `/reforge` command was replaced by a generic `/modifier`
  command. See `docs/modules/design/modifier.md` for full detail.

## Explicitly deferred (not done this pass)

1. **STATE effects are still attach-time only.** No `trigger:` field on `StateEffect` — the design
   doc's §13 `ON_KILL -> STATE ADD souls` pattern (mutating state on a combat trigger, not at
   attachment) isn't implemented. Doing it properly needs slot-aware item mutation: the trigger
   dispatch call sites (`AbilityExecutor.fireModifiersForItem`) receive a bare `ItemStack`, with no
   indication of which inventory slot it came from, so there's nowhere safe to persist a state
   mutation back to. ABILITY/EVENT effects don't have this problem since they don't need to write
   back to the item.
2. ~~**Item-upgrade `keep-data-on-upgrade` inheritance**~~ — **built** (machine-layer/anvil-rework
   pass, see `docs/modules/design/recipe.md`): `RecipeDefinition.keepDataOnUpgrade`/`upgradeFrom` +
   `org.nakii.valmora.module.recipe.ItemDataCarrier.carryForward(...)` copies enchants, every
   attached modifier-framework component (via `ModifierComponentStore.readAll`/`write`, generic
   across groups), durability, and custom display name from a source ingredient onto a recipe's
   output — usable by any crafting recipe and by the anvil's `type: UPGRADE` recipes
   (`AnvilRecipeDefinition`).

**Also done since the "Built" section above was last fully rewritten:** a fluent Java builder API —
`ModifierGroupDefinition.builder(id)...build()` / `ModifierDefinition.builder(id, groupId)...build()`
(§20), covered by `ModifierBuilderTest`. Only `ModifierStorage`/`Codec`-style custom persistence
extension points (§20's last bullet, "only needed if a plugin requires custom persistent data beyond
generic modifier state/metadata") remain unbuilt — no plugin need for that has come up.

## Known limitations

- `StatEffect.Operation.MULTIPLY` reads the *current* `StatManager` effective value for that stat at
  the moment this item's contribution is resolved and adds `current * (value - 1)`. This is
  order-dependent (which item/modifier resolves first affects the result) because `StatManager` has
  no multi-pass/final-multiplier resolution stage — the same limitation the pre-existing engine has
  for e.g. armor set bonuses. ADD is unaffected and is the primary case in the doc's own examples.
- `EVENT`/`TRIGGER` effect `actions:` only accepts plain DSL strings (`EventParser.parse`). The
  doc's §13 example nests structured `type: STATE` maps inside a trigger's `actions:` list; that
  shape isn't parsed (state mutation is a top-level `STATE` effect here instead — see Deferred #1).
- **`ItemAbilityVariableProvider` namespace collision (fixed, but worth flagging the pattern).**
  `ScriptModule.registerProvider` keys providers by namespace in a flat map
  (`SimpleRegistry`/`VariableResolverImpl` — no per-namespace multi-provider dispatch), so only ONE
  provider can ever serve `$item.*$`. An earlier pass of this framework registered a second,
  separate `item`-namespace provider from `ModifierModule` for `$item.rarity.*$`, which would have
  silently overwritten `ItemAbilityVariableProvider` (registered by `ScriptModule`, much earlier in
  module order) and broken `$item.ability_id$`/`$item.ability_trigger$` — caught before shipping,
  fixed by merging all `$item.*$` resolution into the one existing provider instead. If you're adding
  a new `$xyz.*$` namespace anywhere in this codebase, check `grep -rn 'getNamespace' ` first for an
  existing owner of that namespace.
- **Reload-time validation is a warning pass, not a transactional gate.** `ModifierValidator` runs
  *after* content is already loaded into the live registries (matching every other content loader in
  this codebase — none of them stage into a temporary registry and swap atomically either). The
  design doc's §23 hard constraint ("content reload must validate all definitions before replacing
  live registries") isn't fully met; doing so would mean restructuring `ModifierModule` (and every
  sibling module) to build into a staging registry first, which is out of scope here.
- `custom_anvil`'s match order is priority-then-load-order, and `YamlLoader`'s directory scan order
  is not itself guaranteed deterministic across platforms — two same-priority, addition-less recipes
  from different groups that could both match the same bare item still have an environment-dependent
  tie-break unless one is given an explicit higher `priority:`.
