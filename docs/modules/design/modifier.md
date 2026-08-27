# Modifier Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.modifier` (+ `org.nakii.valmora.module.rarity`)
> **Module ID:** `modifier` (rarity metadata lives in a separate small module, ID `rarity`)
> **Load order:** `rarity` right after `time`; `modifier` after `recipe` (registers the
> `custom_anvil` `DynamicMachineHandler`), before `alchemy`
> **Status:** implemented — generic modifier groups/definitions, PDC component storage,
> application semantics (EXCLUSIVE/STACKABLE/MULTIPLE), full-trigger STAT/ABILITY/EVENT effect
> dispatch + attach-time STATE, rarity-scaled/expression/literal value resolution (including recipe
> costs), `APPLY_MODIFIER`/`REMOVE_MODIFIER`/random-reroll recipes with explicit priority ordering,
> reload-time cross-reference validation, `$item.*$` expression variables (rarity/type/id/stats),
> item lore/display integration, default reforges+gemstones content pack (reforges fully migrated
> off the legacy Java module). **Trigger-bound STATE effects are NOT implemented** (STATE still only
> applies at attachment time) — see [Unfinished Things / TODOs](#unfinished-things--todos) and
> `docs/MODIFIER_FRAMEWORK_BACKLOG.md`.

Design source: `docs/Valmora_Modifier_Framework_Design.docx`. This doc verifies the actual
implementation against that design and records where they diverge.

---

## 1. Overview

The Modifier module is a **generic engine for attachable item components that grant stats,
abilities, or other effects** — reforges, gemstones, and any future system (infusions, runes,
curses, ...) are all *content* (YAML), not Java. There is no `Reforge` class, no `Gemstone` class,
and no core-engine branch that special-cases a group by name. The legacy
`org.nakii.valmora.module.reforge` package (Java `ReforgeModule`/`ReforgeDefinition`, the
`reforge_anvil`/`forge_random` `DynamicMachineHandler`s, and the `REFORGE_*` PDC keys) was **deleted**
when this module shipped — reforges are now shipped content over this engine
(`modifiers/groups/reforges.yml`, `modifiers/definitions/reforges.yml`).

**Key facts to understand the design:**

- **Rarity is a separate, data-driven layer** (`rarity` module, `rarities.yml`) — not baked into the
  modifier engine. The legacy `org.nakii.valmora.module.item.Rarity` enum is still the on-item PDC
  representation (`Keys.RARITY_KEY`, unchanged); `RarityRegistry` is a metadata layer keyed by that
  same enum-constant name (`"LEGENDARY"` etc.) so nothing about existing items needed to migrate.
  Each `RarityDefinition` carries `id`/`name`/`color`/`rank`/`power` plus an open-ended `extra` map
  for any other numeric property a content author wants (e.g. `forge_cost`, used by the reforge
  recipes' rarity-scaled cost — see §6).
- **A modifier group defines attachment rules; a modifier definition defines effects.** Groups
  (`ModifierGroupDefinition`) own exclusivity/stacking/capacity/replacement/removal/storage/display
  policy and an optional item-type restriction. Modifiers (`ModifierDefinition`) belong to exactly
  one group, and carry display text, requirements, tags/conflicts, optional `state`, optional
  `tiers`, and a `weight` (used only by random-selection application).
- **Attachments are generic PDC components, not baked stats.** `ModifierComponentStore` writes one
  `TAG_CONTAINER_ARRAY` PDC key per group (`modifiers_<group_id>`, key created on the fly, not in
  `Keys.java`), one nested container per `ModifierInstance` (id/tier/count/state). Nothing about a
  modifier's *effect* is ever baked into the item — `ModifierEngine.contributeStats` resolves STAT
  effects fresh every `StatManager.recalculateStats` pass, exactly like enchants/pets/set-bonuses.
- **Effects reuse existing engine primitives, not a new action language.** `ABILITY` effects hold an
  ordinary `AbilityDefinition` parsed via the same `MechanicParser`/`ConditionParser` code path as
  item abilities. `EVENT` effects hold `CompiledEvent`s from the same `EventParser` used everywhere
  else. `STAT`/`STATE` conditions are ordinary `ConditionGroup`s.
- **Values are generic providers, never per-rarity maps.** `ValueResolver` (literal / expression /
  rarity-scale / Java-custom) is the single pipeline every numeric field in the framework goes
  through — modifier effect values, and (as of the reforges migration) recipe costs too.
- **Tier can come from the instance OR be derived from rarity.** `TierSource.INSTANCE` (default) —
  the tier chosen at application time is stored and used as-is (gemstones: which gem you used).
  `TierSource.RARITY_RANK` — the tier is recomputed from the carrying item's *current* rarity rank
  every time effects are resolved, ignoring whatever tier is stored (reforges: re-rarity-ing an item
  automatically changes its reforge's granted stats). This is how the migrated reforges reproduce
  the old `stat-bonuses-by-rarity` tables **losslessly**, as `tiers: 1..7` (COMMON=1..DIVINE=7)
  instead of a banned rarity-name-keyed map — see §6.

---

## 2. Architecture & Key Classes

### 2.1 `org.nakii.valmora.module.rarity`

| Class | Role |
|---|---|
| `RarityDefinition` | Immutable value object: `key` (enum-constant-style, matches legacy `Rarity`), `id`, `name`, `color`, `rank`, `power`, `extra` (open numeric property map). `getProperty(name)` is the single lookup used by `RarityScaleValue`/expressions. |
| `RarityRegistry` | Key/id lookup + `getOrdered()` (ascending rank). |
| `RarityModule` | `ReloadableModule`, ID `rarity`. Loads `rarities.yml` directly (single root-level file, not a `YamlLoader` folder scan) — reads the `rarities:` section, one sub-section per rarity; any key beyond `id`/`name`/`color`/`rank`/`power` that's numeric goes into `extra`. |

### 2.2 `org.nakii.valmora.module.modifier` — core model

| Class | Role |
|---|---|
| `ModifierGroupDefinition` | `displayFormat` (PREFIX/SUFFIX/LORE/NONE) + `displayOrder`, `applicationMode` (EXCLUSIVE/STACKABLE/MULTIPLE) + `max`/`replacement`/`removal`, `targetItemTypes` (empty = any), `storageMode` (SINGLE/STACKED/INSTANCES — informational, see §3), `tierSource`. |
| `TierSource` | `INSTANCE` (default) or `RARITY_RANK` — see §1. |
| `ModifierDefinition` | `groupId`, display (`name`/`prefix`/`suffix`/`lore`), `requirements` (`ConditionGroup`), `tags`/`conflictIds`/`conflictTags`, `state` (`Map<String, ModifierStateDefinition>`), `tiers` (`Map<Integer, ModifierTier>`, empty for untiered), `weight`, and an additional `targetItemTypes` that **narrows** the group's own restriction (e.g. within the `reforges` group, `fierce` only applies to SWORD/AXE even though the group allows armor too). `getEffects(tier)`/`getDisplayName(tier)` fall back to base (untiered) content when `tiers` is empty. |
| `ModifierTier` | One tier's `displayNameOverride` + full effect list (tiers **replace**, not add to, each other). |
| `ModifierStateDefinition` | `default`/`min`/`max` for one state field. |
| `ModifierGroupRegistry` / `ModifierRegistry` | Simple case-insensitive id maps, `ModifierRegistry.valuesInGroup(id)` for iteration. |
| `ModifierGroupParser` / `ModifierDefinitionParser` | `YamlLoader.SectionParser` implementations — see §5 for the exact YAML shape used (note: it differs slightly from the design doc's illustrative wrapper syntax). |

### 2.3 `org.nakii.valmora.module.modifier.effect`

| Class | Role |
|---|---|
| `ModifierEffect` | Marker interface: `getType()` + `getConditions()`. |
| `StatEffect` | `stat`, `operation` (ADD/MULTIPLY), `value` (`ValueResolver`). |
| `AbilityEffect` | Wraps a full `AbilityDefinition`, parsed the same way `ItemDefinitionParser` parses item abilities (`AbilityDefinition.Builder`, `MechanicParser.parse`, `ConditionParser.parseList`). |
| `EventEffect` | `trigger` (`AbilityTrigger`), `actions` (`List<CompiledEvent>` via `EventParser.parse`). |
| `StateEffect` | `operation` (ADD/SET), `key`, `value`. |
| `ModifierEffectParser` | Dispatches `type:` to the four built-ins, or to a Java-registered `ModifierEffectFactory` via `ModifierEffectRegistry` for anything else. |
| `ModifierEffectRegistry` | Static registry, namespaced ids (`"my_plugin:soul_harvest"`), the §20 extension point. |

### 2.4 `org.nakii.valmora.module.modifier.value`

| Class | Role |
|---|---|
| `ValueResolver` | `resolve(RarityDefinition, tier, ExecutionContext)`. |
| `LiteralValue` | Fixed number. |
| `ExpressionValue` | Delegates to `ScriptModule.getExpressionEvaluator()` — needs a non-null context. |
| `RarityScaleValue` | `base` + `{property, operation(ADD/MULTIPLY), factor}` against `RarityDefinition.getProperty(...)` — the §5 default. |
| `CustomValue` | Delegates to a Java-registered `ValueResolver` in `ModifierValueResolverRegistry` by id. |
| `ValueParser` | Parses a raw YAML node (number / numeric string / map) into the right resolver type. Handles both a real `ConfigurationSection` (loaded YAML) and a synthetic `Map` (deep-converted via `ConfigurationSection.createSection`, needed because `MemoryConfiguration.set(key, rawMap)` does **not** auto-convert nested maps to sections — a real gotcha hit during implementation). |

### 2.5 Storage & the engine itself

| Class | Role |
|---|---|
| `ModifierInstance` | `groupId`, `modifierId`, `tier`, `count`, `state` (`Map<String,Integer>`). Pure data, no behavior. |
| `ModifierComponentStore` | PDC read/write. One `NamespacedKey` per group (`modifiers_<group>`, cached, created via `new NamespacedKey(plugin, ...)` — **not** pre-registered in `Keys.java`, since groups are dynamic content). Each instance is a nested `PersistentDataContainer` inside a `TAG_CONTAINER_ARRAY`: fields `id` (STRING), `tier`/`count` (INTEGER), `state` (nested `TAG_CONTAINER` of INTEGERs). `readAll(meta, groupRegistry)` scans every known group. |
| `ModifierEngine` | The resolver. `apply`/`remove` implement application semantics (see §3); `applyRandom` does a weighted pick excluding what's already attached (the generic `forge_random` replacement); `contributeStats`/`applyPassiveAbilities` are the per-item-per-recalculation hooks called from `StatManager`; `getGrantedAbilities`/`getGrantedEventActions` feed `AbilityExecutor.fireModifiersForItem` for every non-passive trigger (see §7); `getDisplayText`/`getLoreEntries` feed `ItemFactory.updateLore`; `effectiveTier(...)` is the private helper implementing `TierSource`. |
| `ModifierModule` | `ReloadableModule`, ID `modifier`. Loads `modifiers/groups/*.yml`, `modifiers/definitions/*.yml`, `modifiers/recipes/*.yml`; runs `ModifierValidator`; registers the `custom_anvil` `DynamicMachineHandler`. Deliberately does **not** register an `item`-namespace variable provider — see `ItemAbilityVariableProvider`'s javadoc for why that would silently clobber the ability-pipeline one `ScriptModule` already registers. |
| `ModifierCommand` | Generic `/modifier groups\|list\|apply\|remove` admin command — replaces the old group-specific `/reforge`. |

### 2.6 `org.nakii.valmora.module.modifier.recipe`

| Class | Role |
|---|---|
| `ModifierRecipeDefinition` | `operation` (APPLY_MODIFIER/REMOVE_MODIFIER), `baseItemTypes`, `additionItemId`/`additionAmount` (addition is **optional** — a recipe that omits it matches on the base item alone, e.g. a random reroll), `modifierGroup`/`modifierId`/`modifierTier`, `costXpLevels`/`costCoins` as `ValueResolver`s (rarity-scaled cost is a first-class feature, not a hack), and `priority` (YAML `priority:`, default 0 — higher tried first; see §4). |
| `ModifierRecipeParser` | YAML parser; `modifier.id: RANDOM` is a recognized sentinel (see §4). |
| `ModifierAnvilHandler` | `DynamicMachineHandler` for machine id `custom_anvil`. Input slots `base_item`/`addition_item`. Iterates every registered `ModifierRecipeDefinition` in priority order (see §4) and returns the first match. |

---

## 3. Application Semantics (`ModifierEngine.apply`/`remove`)

`apply(item, groupId, modifierId, tier)`:

1. Resolve the group and definition; fail fast on unknown group/modifier or a definition/group id
   mismatch.
2. Check `group.appliesTo(itemType) && def.appliesToItemType(itemType)` — **both** must pass; the
   group sets the outer boundary, the definition can narrow it further (used by reforges — see §6).
3. Evaluate `def.getRequirements()` against a minimal `ExecutionContext` carrying only the item's
   rarity (`ItemAbilityVariableProvider.RARITY_ATTACHMENT_KEY`) — no caster/target, since application isn't
   tied to a player action in every call path (e.g. admin force-apply).
4. Check conflicts: every *other* attached modifier across **every** group on the item is compared
   against `conflictIds`/`conflictTags` in both directions.
5. Apply per `ApplicationMode`:
   - `EXCLUSIVE`: replace the sole existing instance (if `replacement` allowed) or fail.
   - `STACKABLE`: find an existing instance of the *same modifier id and tier* and increment its
     `count`, respecting `group.max` as a total-applications cap across all instances in the group;
     otherwise add a new instance.
   - `MULTIPLE`: replace-or-add by modifier id, respecting `group.max` as an entry-count cap.
6. Clone the item, write the updated component list, return the clone. **Never mutates the input
   `ItemStack` in place** — callers (recipe handlers, `ModifierCommand`) are responsible for applying
   the result.

`remove(item, groupId, modifierId)`: `modifierId == null` removes every instance in the group
(used by `REMOVE_MODIFIER` recipes with no `modifier.id`); otherwise removes just that modifier id.
Fails if the group disallows removal or nothing matches.

`applyRandom(item, groupId)`: computes the eligible set (every definition in the group whose
item-type restriction passes and that isn't already attached), does a weight-proportional roll
(`ModifierDefinition.getWeight()`, default 1.0 — same algorithm as the old `ReforgeModule
.pickWeighted`), then delegates to `apply(...)` with tier `1` (irrelevant for `RARITY_RANK` groups,
which ignore the stored tier anyway).

---

## 4. Recipe Integration (`custom_anvil`)

Machine id `custom_anvil` replaces both the gemstone-oriented `STAT_MODIFIER` concept from the
design doc and the legacy `reforge_anvil`/`forge_random` machines. `ModifierAnvilHandler.match(...)`
walks every loaded `ModifierRecipeDefinition` in order and returns the first one whose base item type
and (if declared) addition item match:

- **Exact application** (`operation: APPLY_MODIFIER`, `addition.item: <id>` present): base item +
  the named addition item → applies `modifier.id` at `modifier.tier`.
- **Random application** (`operation: APPLY_MODIFIER`, `modifier.id: RANDOM`, no `addition:`): base
  item alone → `ModifierEngine.applyRandom`. This is how `random_reforge`
  (`modifiers/recipes/reforges.yml`) reproduces `forge_random`.
- **Removal** (`operation: REMOVE_MODIFIER`): base item alone (no addition check at all) → removes
  `modifier.id` from `modifier.group`, or the whole group if `modifier.id` is omitted.

**Ordering:** the handler is "first match wins" across *all* recipes regardless of group.
`ModifierRecipeDefinition.priority` (YAML `priority:`, default `0`) controls match order —
recipes are tried highest-priority-first, with a stable sort so same-priority recipes keep their
YAML load order relative to each other (`YamlLoader`'s directory scan order is not itself guaranteed
deterministic across platforms, so two same-priority addition-less recipes that could both match the
same bare item are still an environment-dependent tie — give one an explicit higher `priority:` to
resolve it). In the shipped content this never comes up — `reforges.yml` deliberately ships **no**
`REMOVE_MODIFIER` recipe (see the comment in that file) specifically because `random_reforge` should
always win the race for a bare reforged item, exactly reproducing the legacy behavior (which also had
no anvil-based removal, only the admin command) — so no priority override is needed there either.

Cost (`cost.coins`/`cost.xp_levels`) is a `ValueResolver`, resolved against `engine.readRarity
(baseItem)` at match time — see §6 for the reforge migration's use of this.

---

## 5. YAML Shape — a Deliberate Deviation From the Design Doc

The design doc's illustrative snippets wrap group/modifier entries under a root key:

```yaml
groups:
  reforges:
    ...
modifiers:
  fierce:
    ...
```

This codebase's `YamlLoader` (used by every other content type — items, mobs, reforges before this
migration, recipes, ...) instead treats **each top-level key in a file as one entity id directly**.
Shipped content here follows that existing convention instead of the doc's wrapper, i.e.:

```yaml
# modifiers/groups/reforges.yml
reforges:
  display: {...}
  application: {...}
```

This is noted explicitly in `ModifierModule`'s class javadoc and in every shipped content file's
header comment, so it doesn't read as an oversight.

---

## 6. Default Content Pack: Reforges (Migrated) & Gemstones

### 6.1 Reforges

`modifiers/groups/reforges.yml` — `EXCLUSIVE`/`SINGLE`/`PREFIX` group, `tier-source: RARITY_RANK`,
targeting the union of every reforge's own applicable types (SWORD/AXE/BOW/CROSSBOW/HELMET/
CHESTPLATE/LEGGINGS/BOOTS).

`modifiers/definitions/reforges.yml` — one entry per legacy reforge (`fierce`, `sharp`, `fabled`,
`heroic`, `rapid`, `fortified`, `reinforced`, `titanic`), each with:
- `targets.item_types` narrowing to that reforge's original `applicable-types` (definition-level
  restriction — see §3 step 2).
- `tiers: 1..7`, one tier per rarity rank (COMMON=1 .. DIVINE=7), each tier's `effects:` holding the
  **exact same numbers** as the old `stat-bonuses-by-rarity` table for that rarity. This is a
  mechanical, lossless transcription (see the generation script's intent in the migration commit) —
  not a rebalance. It deliberately does **not** use `RarityScaleValue`, because the legacy curves are
  non-linear/hand-tuned per reforge (e.g. `fierce`'s strength bonus goes 5→12→20→32→48→65→85, roughly
  an accelerating curve, not `base × rarity.power`) — reproducing them losslessly needs per-rarity
  literal values, and `tiers:` (keyed by numeric rank+1, not rarity name) is the generic mechanism
  the framework already has for exactly this, without recreating a banned name-keyed map.

`items/reforge_stones.yml` — one static `AMETHYST_SHARD` item per reforge (`<id>_reforge_stone`),
replacing the old dynamically-generated stone (`ReforgeModule.createReforgeStone`).

`modifiers/recipes/reforges.yml` — one exact-apply recipe per reforge (base + stone → that reforge),
plus `random_reforge` (base alone → `applyRandom`, weight defaults to 1.0 for all eight since the
legacy config never set a non-default `weight:` either). Cost is `{ base: 1, scaling: { type:
RARITY, property: forge_cost, operation: MULTIPLY } }` — `forge_cost` is a new `rarities.yml` `extra`
property (250/500/1000/2500/5000/10000/15000 for COMMON..DIVINE) reproducing the old
`enchant/forge_costs.yml` table, now expressed as ordinary rarity metadata instead of a bespoke
`ForgeCostRegistry` class (which was deleted along with the rest of the reforge package).

### 6.2 Gemstones

Genuinely new content (gemstones didn't exist before this framework) — `modifiers/groups/
gemstones.yml` (`STACKABLE`/`INSTANCES`/`LORE`, max 5, `tier-source: INSTANCE`) and `modifiers/
definitions/gemstones.yml` (`ruby`, `sapphire`, 3 tiers each, straightforward literal ADD values —
no rarity scaling, since a gemstone's strength is chosen by *which gem item* you use, not the item's
rarity). Plus a `traits` demo group (`modifiers/definitions/traits.yml`) showing rarity-scaled
(`fierce`), conditional-MULTIPLY (`low_health_fury`), and ability-granting (`thundering`) content —
kept deliberately separate from `reforges`/`gemstones` so it doesn't read as "the" canonical example
of either.

---

## 7. Ability/Event Trigger Dispatch

Every trigger an item ability can use, a modifier-granted `AbilityEffect`/`EventEffect` can use too —
this is the piece that makes `AbilityEffect`'s "no second ability language" claim (§1) actually true
end to end, not just at parse time.

- `ModifierEngine.getGrantedAbilities(item, trigger, caster)` — every `AbilityEffect`'s
  `AbilityDefinition` attached to `item` whose trigger matches and whose *effect-level* `conditions:`
  pass (evaluated against a full `$item.*$`/`$player.*$`-capable context). The ability's *own*
  `conditions:`/cooldown/mana are **not** checked here — that's `AbilityExecutor`'s job once it has
  the definition, exactly like an item ability.
- `ModifierEngine.getGrantedEventActions(item, trigger, caster)` — every `EventEffect`'s compiled
  actions attached to `item` whose trigger/conditions match, flattened into one list.
- `AbilityExecutor.fireModifiersForItem(player, item, trigger, target, silent)` — the single call
  site every trigger listener uses: runs every returned ability through the same `fireOne` cooldown/
  mana/pipeline-hook logic item abilities use (extracted from the original `fire(...)` into a shared
  private method + a new public `fireAbility` for a single already-trigger-matched definition), then
  executes every returned event action unconditionally. `fireModifiersHeld` is the main-hand
  convenience wrapper, mirroring `fireHeld`.
- **Call sites**: `AbilityTriggerListener` (ON_KILL, SNEAK + armor, ON_SHOOT, ON_TELEPORT + armor,
  EQUIP/UNEQUIP via `fireItem`) and `CombatListener` (ON_HIT on the attacker's main hand,
  ON_DAMAGE_TAKEN on the victim's main hand + every armor piece, both the main damage-by-entity path
  and the no-attacking-entity fallback) each call the item-ability path and the modifier path
  side by side.
- **PASSIVE is intentionally different**: it doesn't go through `AbilityExecutor` at all —
  `ModifierEngine.applyPassiveAbilities` runs a PASSIVE ability's mechanics directly, unconditionally,
  every `StatManager.recalculateStats` pass, with no cooldown/mana check — matching how item-defined
  PASSIVE abilities already worked before this framework existed (a passive is a standing effect, not
  a discrete "fire event").
- **Not covered**: STATE effects still only apply at attachment time — see
  [Unfinished Things / TODOs](#unfinished-things--todos) item 1 for why trigger-bound state mutation
  needs more plumbing than ABILITY/EVENT did.

---

## 8. Item Lore/Display Integration

`ItemFactory.updateLore` (unchanged method signature) now additionally:

1. Calls `ModifierEngine.getDisplayText(item, PREFIX)`/`getDisplayText(item, SUFFIX)` and
   prepends/appends the result to the item's display name — replacing the old direct
   `Keys.REFORGE_DISPLAY_KEY` read.
2. Merges `ModifierEngine.contributeStats(item, null, sink)` into the same `Map<String,Double>` used
   to render the "Stats Section" — a reforge/gemstone bonus shows in the same list as baked item
   stats, not a separate block. (Passing `player = null` here is safe: the only place `player` is
   used inside `contributeStats` is the `MULTIPLY` operation's *current effective stat* read, which
   falls back to `0` with no player — acceptable for a lore preview, since `MULTIPLY` stat effects
   are rare and this only affects the *displayed* number, not actual combat math, which always runs
   with the real player.)
3. Calls `ModifierEngine.getLoreEntries(item)` (every `LORE`-format modifier, ordered by group
   `displayOrder` then attachment order) and renders one `◆ <name>` line per instance — the numeric
   stat contribution itself is already covered by step 2, so this line is purely identification (e.g.
   distinguishing "which gemstones are socketed" when several contribute to the same stat).

---

## 9. Unfinished Things / TODOs

See `docs/MODIFIER_FRAMEWORK_BACKLOG.md` for the full list with rationale. Summary:

1. **STATE effects are still attach-time only.** `StateEffect` has no `trigger:` field, so it always
   applies once when the modifier is attached (`ModifierEngine.applyStateEffectsOnAttach`). The
   design doc's §13 `ON_KILL -> STATE ADD souls` pattern (state mutating on a combat trigger, not at
   attachment) isn't implemented — doing it properly needs slot-aware item mutation (the trigger
   dispatch call sites pass a bare `ItemStack`, not "this came from the player's main hand" vs. "this
   is the chestplate slot", so there's nowhere safe to write the mutated state back to) that the
   current `AbilityExecutor.fireModifiersForItem`/`ModifierEngine.getGrantedEventActions` plumbing
   doesn't provide. ABILITY and EVENT effects don't have this problem since they don't mutate the
   item itself.
2. **No fluent Java builder** for `ModifierGroupDefinition`/`ModifierDefinition` (design doc §20's
   `ModifierGroup.builder(...)`) — a plugin calls `.register(...)` on the registries directly with a
   hand-built object today.
3. **Item-upgrade `keep-data-on-upgrade` inheritance** doesn't exist anywhere in the codebase (not
   modifier-specific — confirmed greenfield during the original framework implementation).

**Done since the initial pass** (kept here for anyone cross-referencing an older read of this file):
non-passive ability trigger dispatch (`AbilityExecutor.fireModifiersForItem`/`fireModifiersHeld`,
wired into every existing trigger listener — `AbilityTriggerListener`, `CombatListener`), EVENT
effect dispatch (same call), reload-time cross-reference validation (`ModifierValidator`), the
`custom_anvil` recipe-ordering ambiguity (`ModifierRecipeDefinition.priority`), and the full
`$item.*$` variable provider (`type`/`id`/`stats.<id>` added alongside `rarity.*`, all served by
`org.nakii.valmora.module.script.variable.providers.ItemAbilityVariableProvider`).

Related but pre-existing, not introduced by this module: the recon that led to this module found no
`ComponentStore` abstraction elsewhere in the codebase to reuse — `ModifierComponentStore` is new
infrastructure, not a refactor of an existing generic component system (the design doc implied one
already existed; it didn't).
