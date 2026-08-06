# Generic-Engine Refactor — Config Reference

> Complete reference for every YAML file/section introduced across Phases 1-5 of the
> generic-engine refactor (completed — full session-by-session history in `docs/REFACTOR/PROGRESS.md`).
> Every file here is optional — if it's missing, the plugin falls back to the exact pre-refactor
> hardcoded behavior described in each section. None of these require editing any existing config.

---

## `damage_types/*.yml` (Phase 2.1)

One or more `.yml` files under `plugins/Valmora/damage_types/`, each a flat map of damage-type-id
→ properties. Loaded by `DamageTypeLoader`, backing `DamageType` (see combat design doc).

```yaml
melee:
  color: "<white>"          # MiniMessage color used when this damage type is shown in lore/indicators
  ignores-defense: false    # true = victim DEFENSE stat never mitigates this type (like fall/void/drowning)
  on-hit: []                # script DSL events (docs/modules/design/script.md), fired once per hit of this type
```

Built-in ids (always present even with no file): `melee`, `projectile`, `fall`, `drowning`,
`fire`, `lava`, `magic`, `void`, `poison`, `wither`, `explosion`. Adding a new id here does **not**
require any Java change — mobs/enchants can reference it by name immediately.
Shipped default: `damage_types/core.yml`.

## `damage_formula.yml` (Phase 2.2)

Single file at `plugins/Valmora/damage_formula.yml`, root-level keys, each a math expression
(`docs/modules/design/script.md` §Expression syntax). Available variables: `$dmg.base_damage$`,
`$dmg.strength$`, `$dmg.crit_chance$`, `$dmg.crit_damage$`, `$dmg.defense$`.

```yaml
damage_multiplier: "1 + $dmg.strength$ / 100"    # applied to base damage before crit
crit_multiplier: "1 + $dmg.crit_damage$ / 100"   # applied only on a critical hit
defense_multiplier: "100 / ($dmg.defense$ + 100)" # applied unless the damage type ignores-defense
```

All three keys are optional individually — omit one to keep its default. Parsed once at
`/valmora reload` time, not per hit.

## `stats/core.yml` → `stat_roles:` (Phase 3.1)

New top-level section inside the existing `stats/*.yml` files (same folder as stat *definitions* —
`stat_roles:` is skipped when scanning for definitions, so it can coexist in the same file safely).
Maps a semantic role name (what Java code asks for) to the actual stat id backing it.

```yaml
stat_roles:
  damage: damage             # role "damage" -> stat id "damage" (identity, the default)
  mana_shield: intelligence  # a NEW role, reusing an existing stat id — no Java change needed
```

The original 15 roles (`health`, `mana`, `damage`, `strength`, `defense`, `crit_chance`,
`crit_damage`, `speed`, `health_regen`, `mana_regen`, `luck`, `mining_fortune`, `mining_speed`,
`breaking_power`, `mining_spread`) default to an identity mapping and are also still overridable
via the legacy `config.yml` → `combat.<role>-stat` / `mining.<role>-stat` keys (checked before this
section, so both mechanisms can coexist — `stat_roles:` wins if both are set for the same role).

## `skills/xp_curves.yml` (Phase 3.2)

```yaml
xp_curves:
  default:                              # always present even with no file — the original 59-level table
    thresholds: [10, 20, 50, ...]
  fast:
    formula: "50 * $curve.level$ ^ 1.8" # evaluated once per level, 1..max-level, at load time
    max-level: 60
```

A curve is either `thresholds:` (an explicit list, index 0 = level 1's requirement) or `formula:` +
`max-level:` (default 60 if omitted). A `SkillDefinition` opts in via its own `xp_curve:` field
(defaults to `"default"`).

## `pets/defaults.yml` (Phase 3.3)

```yaml
pet_defaults:
  xp-formula: "100 * $curve.level$ * $curve.level$"  # the exact pre-refactor formula
  max-level: 200
```

Per-pet override: add `xp-formula:` / `max-level:` directly to that pet's entry in `pets/<id>.yml`
alongside its existing `base-stats:`/`stats-per-level:`/`abilities:`/`milestones:` fields.

## `mob_categories.yml` (Phase 3.4)

```yaml
categories: []   # add new category ids here, e.g. ["DRAGON", "ELEMENTAL"]
```

The original 10 (`UNDEAD`, `ENDER`, `NETHER`, `BEAST`, `AQUATIC`, `ARTHROPOD`, `ILLAGER`, `GOLEM`,
`BOSS`, `OTHER`) are always available. This is the `category:` field on a `mobs/*.yml` entry, and
is also checked by per-category skill XP bonuses — unrelated to the slayer-category system below.

## `slayer_categories.yml` (Phase 3.4)

```yaml
categories:
  undead:
    match:
      type_contains: ["ZOMBIE", "SKELETON", "PHANTOM", "DROWNED", "WITHER", "STRAY", "HUSK"]
  monster:
    match:
      instanceof: "Monster"       # any org.bukkit.entity interface/class simple name
  boss_marked:
    match:
      has_pdc: "some_key"         # entity PDC contains a key with this short name (any namespace)
  all:
    match:
      always: true
```

Rules under one category's `match:` are OR'd together. A slayer tier's `target-category:` that
isn't a key in this file falls back to matching a specific Valmora mob id or vanilla `EntityType`
name verbatim (e.g. `target-category: zombie_king`). The shipped default file reproduces the
pre-refactor hardcoded `MONSTER`/`ILLAGER`/`ANIMAL`/`ALL`/`ANY`/`UNDEAD` categories exactly.

## `item_types.yml` (Phase 4.1)

```yaml
item_types: []   # add new type ids here, e.g. ["WAND", "INSTRUMENT"]
```

The original 21 (`SWORD`, `AXE`, `PICKAXE`, `SHOVEL`, `HOE`, `TRIDENT`, `BOW`, `CROSSBOW`,
`FISHING_ROD`, `SHEARS`, `SHIELD`, `ELYTRA`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`,
`HORSE_ARMOR`, `PET`, `ACCESSORY`, `BACKPACK`, `ALL`, `NONE`) are always available.

## `recipes/anvil_templates.yml` (Phase 4.3)

```yaml
templates:
  merge:
    cost-per-level: 10   # coins charged per total enchant level merged in one anvil operation
```

## `enchant/forge_costs.yml` (Phase 4.4)

**Note the singular `enchant/` folder** — deliberately not inside the existing plural `enchants/`
definitions folder, to avoid that folder's "one YAML section = one enchant" scanner picking up this
file's top-level key as a bogus enchant.

```yaml
forge_costs:
  COMMON: 250
  UNCOMMON: 500
  RARE: 1000
  EPIC: 2500
  LEGENDARY: 5000
  MYTHIC: 10000
  DIVINE: 15000
```

Any rarity omitted here keeps its listed default above.

## Enchant `logic:` factories (Phase 4.5, no new file — extends existing `enchants/*.yml`)

`enchants/*.yml` already supported parameterized logic via `logic-params:`. As of this refactor,
`valmora:sharpness`, `valmora:growth`, `valmora:fortune`, and `valmora:efficiency` are *also*
parameterized (previously fixed hardcoded classes) with defaults matching their old behavior:

```yaml
my_custom_fortune:
  logic: valmora:fortune
  logic-params:
    stat: mining_fortune   # defaults to the "mining_fortune" role's resolved stat id
    per-level: 10.0        # default 10.0 (the old hardcoded value)

my_custom_sharpness:
  logic: valmora:sharpness
  logic-params:
    type: MELEE             # any damage type id, or "ANY"
    percent-per-level: 5.0  # default 5.0 (the old hardcoded value)
```

`valmora:growth` → `stat` defaults to the `health` role, `valmora:efficiency` → `mining_speed`
role. `logic-params` is entirely optional; omitting it reproduces the exact pre-refactor behavior.

---

## Java-level additions (no YAML, but relevant if you're extending the plugin)

- **`ExecutionContext`** gained a generic namespaced key-value store: `get(key)`, `set(key, value)`,
  `remove(key)`, `has(key)`, `keySet()`, plus parent-context inheritance (`getParent()`/
  `setParent()`). Use this instead of ad-hoc fields when a mechanic needs to pass data to a nested
  invocation. See `SimpleExecutionContext`'s 5-arg constructor for building a child context.
- **`ValmoraAPI`** now exposes every module the plugin constructs (previously several were only
  reachable via a direct `Valmora` cast). `ValmoraAPI.getInstance().getModuleManager().getModule(id,
  Class)` is the generic, type-safe, reload-safe way to reach a module that doesn't have a
  dedicated `getXModule()` on the interface.
- **`ValmoraProfile.getQuarantinedStats()`** — if you rename/remove a custom stat role, existing
  player data for it is preserved here (and written back to the SQL row unchanged) instead of being
  silently dropped or incorrectly treated as live. Re-registering the role picks it back up
  automatically on the next profile load.
