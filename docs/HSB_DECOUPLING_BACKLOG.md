# HSB Decoupling Backlog — Hardcoded Hypixel SkyBlock Logic to Genericize

> **This is a temporary working document, not part of the permanent doc set.** It exists to catalog
> every place the engine still hardcodes logic that was built for one specific Hypixel SkyBlock
> (HSB) feature instead of exposing a reusable, YAML-driven system — per the standing project
> direction: *if something is a 1:1 copy of an HSB mechanic, that's a reason to remove/replace it
> with a generic system usable for more than that one feature, unless it's already just YAML data.*
> Delete this file once the backlog below is empty.
>
> **Scope note (read first):** most of the "HSB-ish" surface in this repo is *content*, not
> *code* — item/mob YAML files named `hyperion`, `necrons_blade`, `livid_dagger`,
> `revenant_thrall`, etc. (`src/main/resources/items/catacombs_swords.yml`,
> `src/main/resources/mobs/slayer_bosses.yml`, `armor_sets.yml`, ...). Those are **not** an
> architecture problem: the engine already treats them as pure data through the generic
> ability/mechanic/mob systems (confirmed — `grep` across `src/main/java` for HSB proper nouns
> like `hyperion`, `necron`, `livid`, `goldor`, `sadan`, `bonzo`, `kuudra` returns **zero** Java
> hits). Renaming/reflavoring that content is a copyright/flavor decision for the user to make
> later, not an engine refactor — it is **out of scope for this backlog**. This backlog is only
> for Java code that special-cases an HSB mechanic instead of being data-driven.
>
> **How to use this:** pick one `[ ]` item, read the module's `docs/modules/design/<module>.md`,
> implement following the pattern already established by `XpCurveRegistry` /
> `ForgeCostRegistry` / `DamageFormulaRegistry` (load from YAML, keep the old hardcoded numbers as
> the in-code default so a server with no override file sees zero behavior change), verify with
> `./gradlew compileJava && ./gradlew test`, then check the box with a one-line note.

---

## Confirmed already generic (no action — listed so nobody re-investigates them)

These were previously hardcoded 1:1 copies of HSB numeric tables and have **already** been moved
to YAML-overridable registries in earlier refactor passes. Pattern to copy for the open items below:

- [x] Skill XP curves — `XpCurveRegistry` loads `skills/xp_curves.yml`; old hardcoded 59-level
      table kept only as the `"default"` curve's fallback.
- [x] Reforge stone costs by rarity — `ForgeCostRegistry` loads `enchant/forge_costs.yml`; old
      `RARITY_COST` `EnumMap` kept only as `DEFAULTS`.
- [x] Combat damage formula (strength/crit/defense multipliers) — `DamageFormulaRegistry`.
- [x] Damage-type void/drowning/fall exclusion list — now data on `DamageType` enum, not an
      `if` chain.
- [x] Stat "roles" (the 15 fixed stat-name `String` fields) — `StatRoleRegistry`.
- [x] Anvil upgrade templates — `AnvilTemplateRegistry`.
- [x] Reforge/Pet/Enchant *mechanics* — already fully YAML-defined via `ReforgeDefinition` /
      `PetAbilityDefinition` / `enchant/logic/*Logic` (the `Logic` classes implement a generic
      trigger contract keyed by enchant id from YAML, not hardcoded per-enchant `if`s).
- [x] Slayer bosses / dungeon mobs — defined entirely in `mobs/*.yml` via the generic mob+ability
      engine; no Java special-casing found.

---

## Open items

### Alchemy hardcoded per-level effect tables

`module/alchemy/effect/hardcoded/*.java` is a direct, undocumented port of HSB's Alchemy potion
effects (Absorption/Healing/Poison/Damage splash potions). Unlike every other numeric table in the
codebase (see confirmed section above), these were **not** migrated to a YAML-backed registry —
the per-level value arrays are still `private static final double[]` literals baked into the class:

- [ ] **`AbsorptionAlchemyEffect`** — `ABSORPTION_VALUES = {20, 40, 60, 80, 100, 150, 200, 300}`
      hardcoded per-level. Move to a `alchemy/effect_curves.yml` (or reuse the existing
      `alchemy.yml` effect definitions if they already have a per-tier value slot — check
      `AlchemyEffect`/`AlchemyManager` first) so server admins can retune levels without a
      recompile, following the `XpCurveRegistry` load-with-fallback-default pattern.
- [ ] **`HealingAlchemyEffect`** — `HEAL_VALUES = {20, 50, 100, 150, 200, 250, 300, 350}` hardcoded
      per-level. Same treatment.
- [ ] **`PoisonAlchemyEffect`** — `10.0 * level` true-damage-per-tick hardcoded formula (no array,
      but still an un-configurable magic number). Move the `10.0` coefficient into YAML.
- [ ] **`DamageAlchemyEffect`** — `5.0 * level` true-damage-on-apply hardcoded formula. Same
      treatment as Poison.
- [ ] Once the four above are YAML-driven, consider whether `HardcodedAlchemyEffect` as an
      interface is even still needed for these four, or whether they can become plain
      YAML-configured entries like `VanillaAlchemyEffect` already is (it already takes its
      `PotionEffectType`/`amplifierScales` as constructor params rather than being one class per
      effect — the four true-damage/absorption effects should converge on the same shape: a single
      generic "scaling value effect" class parameterized by curve + operation, not four near-duplicate
      classes). This is the actual generalization the CLAUDE.md direction is asking for — go from
      "4 classes hardcoded to 4 specific HSB potions" to "1 class + YAML data usable for any future
      scaling potion effect."

### Documentation-only (lower priority, not code)

- [ ] `docs/ABILITIES_DUMP.md` / `docs/ABILITIES_DUMP_2.md` are raw HSB wiki data dumps kept as a
      reference source for filling in ability descriptions. They are not shipped in
      `src/main/resources` and are not read by any code — no engine impact — but if the goal is a
      clean break from HSB-sourced reference material, these two files are candidates for deletion
      or an explicit "external reference, not canon" disclaimer once the item catalog they were
      seeded from is fully authored in `items/*.yml`.

---

## Explicitly out of scope for this backlog

Recorded here so a future pass doesn't re-flag them — these are content, not code, and the
generic engine already handles them as data:

- `items/catacombs_swords.yml`, `items/armor_sets.yml`, `items/individual_pieces.yml`,
  `items/new_items.yml`, `items/wands.yml`, `items/swords.yml` — item names/lore/abilities that
  happen to match HSB item names 1:1 (Hyperion, Necron's Blade, Livid Dagger, Bonzo's Mask, etc.).
  Pure YAML data consumed by the generic ability/mechanic engine (`module/item`, `module/script`).
  Renaming is a content decision, not a refactor.
- `mobs/slayer_bosses.yml` — `revenant_thrall`, `alpha_pack_wolf`, etc. Same: generic mob+ability
  YAML, no Java special-casing.
- `docs/modules/user/collection.md`'s "SkyBlock-style Collections" phrasing and similar doc prose
  — descriptive language only.
