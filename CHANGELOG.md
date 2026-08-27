# Changelog

All notable changes to Valmora are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Generic Modifier Framework** (`docs/Valmora_Modifier_Framework_Design.docx`): a data-driven
  engine for attachable item components that grant stats/abilities/other effects — reforges,
  gemstones, and any future system are now YAML content over one generic engine, not per-system Java.
  - Data-driven rarities (`rarities.yml`, `RarityModule`/`RarityRegistry`) with an open per-rarity
    property map for content-authoring values like `forge_cost`.
  - Generic value resolution (literal / expression / rarity-scaled / Java-custom) reused for both
    modifier effect values and recipe costs.
  - `ModifierGroupDefinition`/`ModifierDefinition` core model (exclusivity/stacking/capacity/
    replacement/removal/storage/display policy, requirements, conflicts, tiers, state), with a
    fluent Java builder API for plugin-registered custom groups/modifiers.
  - `STAT`/`ABILITY`/`EVENT`/`STATE` effect types, reusing the existing ability/mechanic/condition/
    event infrastructure — no second action language. `ABILITY`/`EVENT` effects fire on every
    trigger (ON_HIT, RIGHT_CLICK, ON_KILL, SNEAK, ON_SHOOT, ON_DAMAGE_TAKEN, ON_TELEPORT, EQUIP,
    UNEQUIP, PASSIVE), with full cooldown/mana/condition gating for abilities.
  - Generic PDC component storage and application semantics (EXCLUSIVE/STACKABLE/MULTIPLE,
    weighted random selection, conflicts).
  - `APPLY_MODIFIER`/`REMOVE_MODIFIER` recipes via a new `custom_anvil` machine (`modifier_anvil`
    GUI), with optional addition items, a `RANDOM` selection sentinel, rarity-scaled costs, and
    explicit `priority:` match ordering.
  - Reload-time cross-reference validation (`ModifierValidator`).
  - `$item.rarity.*$`/`$item.type$`/`$item.id$`/`$item.stats.<id>$` expression variables.
  - New generic `/modifier groups|list|apply|remove` admin command.
  - Default content: `reforges` (migrated, see Removed below) and a new `gemstones` group
    (`ruby`/`sapphire`, tiered) plus a `traits` demo group.
- `docs/modules/design/modifier.md` / `docs/modules/user/modifier.md`, `docs/MODIFIER_FRAMEWORK_BACKLOG.md`.
- `docs/V1_RELEASE_CHECKLIST.md` — production-readiness checklist tracking test coverage,
  config/data integrity, in-game QA, load, security, and ops items ahead of v1.
- Automated config/data integrity sweep (`ResourceIntegrityTest`): full shipped-YAML-tree parse
  check, duplicate top-level ID detection across items/mobs/guis/recipes, GUI↔recipe machine
  cross-reference, item-reference resolution for recipes and mob loot tables, and an
  ability-mechanic sweep across every item file (previously only 3 hand-picked files).
- First-ever test coverage for the `hud` module (`HudItemModuleTest`).
- Recipe engine precedence tests (`RecipeEngineTest`): `DynamicMachineHandler` > YAML recipe >
  vanilla, and the vanilla fallback's `crafting_table`-only scoping, proven against a real
  stubbed vanilla match rather than an absence of one.
- `GuiEventBlockStageTest` covering the `condition`/`fail-actions` short-circuit contract (CLAUDE.md
  §8.2/§10.2) directly, and `GuiForceCraftEventFactoryTest` covering the `craftingLocked`
  dupe-protection guard, including release-on-exception.
- `YamlLoader` now recurses into subfolders, making CLAUDE.md §9.3's documented
  `recipes/<subfolder>/` organization actually work (previously flat-only).

### Removed
- The legacy `org.nakii.valmora.module.reforge` package (`ReforgeModule`, `ReforgeDefinition`,
  `ReforgeCommand`, `ForgeCostRegistry`, `ReforgeVariableProvider`), the `reforge_anvil`/
  `forge_random` recipe handlers, `Keys.REFORGE_ID_KEY`/`REFORGE_POOL_KEY`/`REFORGE_DISPLAY_KEY`,
  `ItemDefinition.reforgePool`/`reforge-pool:` YAML, `resources/reforges/*.yml`,
  `resources/enchant/forge_costs.yml`, `guis/reforge.yml`/`guis/reforge_anvil.yml`, and the
  `/reforge` command — replaced by the Generic Modifier Framework (see Added). Existing reforge
  numbers/behavior are preserved exactly (see `docs/modules/design/modifier.md` §6.1); the
  admin-facing command is now `/modifier`.

### Changed
- `ItemFactory.updateLore` renders modifier prefix/suffix display text and merges modifier STAT
  contributions into the displayed stats block generically, instead of reading a reforge-specific
  PDC key.
- `AbilityExecutor.fire` was refactored to extract a shared per-ability cooldown/mana/condition/
  pipeline-hook path (`fireOne`), reused by both item abilities and modifier-granted abilities —
  no behavior change for existing item abilities.

### Fixed
- `items/swords.yml` and `items/wands.yml` both defined `fire_freeze_staff`/`fire_fury_staff`
  with diverging stats/mechanics/`item-type`; removed the stale duplicate from `swords.yml`.
- `recipes/crafting_table.yml`'s `wood_planks` recipe referenced `LOG`, which isn't a valid 1.13+
  Material — the recipe never matched anything at all; pinned to `OAK_LOG`.
- `PlayerVariableProvider`'s `$player.biome$` used the deprecated (marked for removal)
  `Enum#name()` on the now registry-backed `Biome` type; switched to `getKey().getKey()`.
- CLAUDE.md corrections: module registration order table was 16 modules behind `Valmora.java`;
  §14.1 claimed no packet usage despite a hard PacketEvents dependency already in use for NPC
  dialogue interception; version table said `0.1` instead of the real `1.0.0-beta1`.

## [1.0.0-beta1]

First public beta. Production-readiness hardening pass.

### Added
- Versioned database schema migration framework (`valmora_schema_version` table) so future
  schema changes apply automatically and in order. Pre-versioning databases are detected and
  upgraded in place.
- Automated database tests (`SQLDataStoreTest`): schema creation, idempotent re-init,
  pre-versioning upgrade, and economy persistence round-trip.
- MockBukkit-based persistence test (`ProfilePersistenceMockTest`): full player/profile
  save+load round-trip including real `ItemStack` inventory serialization. Runs in an isolated
  JVM via the dedicated `testMock` Gradle task (wired into `check`/`build`) to avoid Bukkit
  static-state pollution from the rest of the suite.
- Configurable MySQL SSL via `database.mysql.use-ssl` (default `false`).
- `README.md`, `CHANGELOG.md`, and a GitHub Actions build workflow.
- `LICENSE` (GNU AGPL-3.0); the project is open-sourced under AGPL-3.0 with a separate
  commercial license available from the copyright holder.

### Changed
- `plugin.yml` version is now sourced from the Gradle build version (single source of truth).
- Gson and HikariCP are relocated under `org.nakii.valmora.lib.*` in the shaded jar to avoid
  classpath collisions with the server and other plugins.

### Fixed
- Database errors are now logged at `SEVERE` instead of being swallowed by `printStackTrace()`.
  A failed schema initialization now disables the plugin instead of silently losing data.
- `/item` and `/mob` now require `valmora.admin`, and `/gui` requires `valmora.admin.gui`
  (these previously had no permission gate).
- Removed an unused duplicate XP-threshold table from `Skill`; `SkillRegistry` is the single
  source of truth for the level curve.

[Unreleased]: https://github.com/nakii/valmora/compare/v1.0.0-beta1...HEAD
[1.0.0-beta1]: https://github.com/nakii/valmora/releases/tag/v1.0.0-beta1
