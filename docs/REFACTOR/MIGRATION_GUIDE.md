# Generic-Engine Refactor — Migration Guide

> For server admins/developers upgrading a Valmora install across the generic-engine refactor
> (completed — full session-by-session history in `docs/REFACTOR/PROGRESS.md`). See
> `docs/REFACTOR/CONFIG_REFERENCE.md` for the full schema of every new config file mentioned below.

## The short version

**You don't have to change anything.** Every new registry introduced by this refactor was built
default-first: if the corresponding new YAML file/section doesn't exist, the plugin behaves
exactly as it did before. This was verified with a dedicated unit test per registry (see
`docs/REFACTOR/PROGRESS.md` — every phase section lists its tests). Just drop in the new plugin JAR
and reload/restart as normal.

## What actually changed under the hood (informational, not actionable)

- **Damage types, mob categories, and item types** are no longer Java `enum`s — they're
  registry-backed classes that behave identically for every existing use (`DamageType.MELEE`,
  `.valueOf(...)`, `==` comparisons all still work) but can now have new values added via YAML
  (`damage_types/*.yml`, `mob_categories.yml`, `item_types.yml`) without a plugin recompile.
- **Combat math** (strength/crit/defense multipliers) is now expression-driven from
  `damage_formula.yml` instead of hardcoded Java arithmetic. The shipped defaults are the exact
  same formulas as before.
- **Skill XP curves and pet XP curves** are now data-driven (`skills/xp_curves.yml`,
  `pets/defaults.yml`) instead of a single hardcoded table/formula. The built-in `"default"` skill
  curve is the exact original 59-level table; the built-in pet formula is the exact original
  `100 * level²`.
- **Stat "roles"** (the indirection between e.g. "damage" and whatever stat id backs it) now come
  from a `StatRoleRegistry` with three fallback tiers — see `CONFIG_REFERENCE.md`'s `stat_roles:`
  section. If you previously set `combat.damage-stat` etc. in `config.yml`, that still works
  unchanged.
- **Anvil merge costs and reforge/forge costs** are now YAML-configurable
  (`recipes/anvil_templates.yml`, `enchant/forge_costs.yml`) instead of hardcoded constants. Same
  default values as before.
- **`valmora:sharpness`/`growth`/`fortune`/`efficiency` enchant logic** can now take
  `logic-params:` overrides (they used to be fixed). No existing `enchants/*.yml` needs any change
  — omitting `logic-params:` reproduces the old hardcoded per-level values exactly.
- **Unrecognized stat data is no longer silently dropped or misapplied.** If you ever delete or
  rename a custom stat role, any player who had that stat keeps the raw value quarantined in their
  save data (not affecting gameplay, not lost) — it comes back automatically if you re-add the
  role later.

## If you want to *use* the new flexibility

Read `docs/REFACTOR/CONFIG_REFERENCE.md` for the exact schema of each file below, then create it
under your `plugins/Valmora/` folder and run `/valmora reload` (or restart):

| Want to... | Create/edit |
|---|---|
| Change combat math (strength/crit/defense scaling) | `damage_formula.yml` |
| Add a new damage type (e.g. `ELECTRIC`) with its own color/on-hit effects | `damage_types/*.yml` |
| Add a new mob classification tag | `mob_categories.yml` |
| Add a new slayer target category with custom matching rules | `slayer_categories.yml` |
| Add a new item type | `item_types.yml` |
| Add a new stat role, or rename which stat backs an existing role | `stats/core.yml` → `stat_roles:` |
| Give a skill a non-default XP curve (formula or explicit table) | `skills/xp_curves.yml`, then set that skill's `xp_curve:` field |
| Change how fast pets level, globally or per-pet | `pets/defaults.yml`, or a pet's own `xp-formula:`/`max-level:` |
| Change anvil merge pricing | `recipes/anvil_templates.yml` |
| Change reforge/forge pricing per rarity | `enchant/forge_costs.yml` |
| Tune the built-in growth/fortune/efficiency/sharpness enchant logic per-enchant | that enchant's `logic-params:` in `enchants/*.yml` |

## For developers extending the plugin

- Prefer `ValmoraAPI.getInstance()` over any direct `Valmora` cast for module access — the API
  surface is now complete (every module the plugin constructs has an accessor). If a module you
  need still doesn't have a dedicated getter, use
  `ValmoraAPI.getInstance().getModuleManager().getModule("id", YourModuleClass.class)` (returns
  `Optional`, case-insensitive, reload-safe).
- If you're adding a per-invocation formula/curve/threshold table anywhere, follow the pattern
  established by `DamageFormulaRegistry`/`XpCurveRegistry`/`AnvilTemplateRegistry`: parse/compute
  once at `onEnable()`/config-load time, cache the result, never re-parse a raw string on a hot
  path (see CLAUDE.md rule #4, and `docs/REFACTOR/PROGRESS.md`'s Task 20 section for two real
  examples of this being fixed).
- If you're adding a new enum-like fixed vocabulary for a game concept (a new "type" or
  "category"), consider the registry-backed-class pattern used for `DamageType`/`MobCategory`/
  `ItemType` instead of a plain Java `enum` — see `docs/REFACTOR/PROGRESS.md`'s Phase 2/3/4
  sections for the exact template and its trade-offs (identity via static fields + `define()`/
  `valueOf()`/`find()`, mutated in place so `==` stays valid across a reload).
