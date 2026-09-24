# Changelog

All notable changes to Valmora are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Content diagnostics.** Every content problem is now a structured diagnostic (severity, file,
  entry, key path, message, "did you mean" hint) collected into one load report:
  - one summary line per content type on every load (`[Mobs] 57 loaded from 4 files in 21 ms — 1 error,
    2 warnings`) plus a final `Content: …` line; the full list goes to
    `plugins/Valmora/last-load-report.txt`;
  - `/valmora reload` shows errors *and* warnings; new `/valmora report [errors|warnings|all] [filter]
    [page]`; `/valmora validate` now also checks cross-references and treats content that is new on
    disk as valid;
  - startup and pack (partial) reloads are reported too — partial reloads no longer leak errors into
    the next report;
  - every content loader reports this way, including the single-file ones (rarities, stats, stat
    roles, damage types/formulas, item types, mob and entity categories, XP curves, pipelines, quest
    packages, collections, alchemy modifiers, pet defaults, `ui.yml`) — a YAML syntax error is
    reported instead of silently loading an empty file;
  - new `diagnostics:` section in `config.yml` (console line cap, unknown-key and unknown-variable
    warnings, report file).
- **Cross-reference safeguards** (`ReferenceValidator`): once all modules are enabled, every id content
  points at is checked — recipe ingredients/outputs and machines, mob drops/equipment, zone spawners and
  resource drops, fishing/block-loot items, NPC conversations, machine GUIs, item container GUIs, and
  ids inside scripts (`give`, `open_gui`, `gui open`, `dialogue start`, `spawn_mob`, `warp_to`,
  `teleport warp:`, `stat_modify`, `zone`/`quest` conditions). Dangling ones are warnings; content
  stays loaded. The machine-GUI and modifier checks now report into the same place, and content packs
  get a generic reference checker.
- **Within-file checks**: dialogue pointers to missing nodes, progression prerequisites/tier nodes,
  collection categories, GUI layout letters without components (and unused components), recipe
  pattern letters never used, swapped zone corners, duplicate stat ids/rarity ranks, decreasing XP
  curve thresholds, unknown keys in mobs/items/recipes/zones/NPCs/GUIs/quests/stats ("did you mean").
- **`ConfigReader`** parsing helper (typed, self-reporting required/optional values, enums, materials,
  ranges, lists, sub-sections, unknown-key detection).
- **Script DSL:**
  - Conditions: combine keyword conditions and expressions with `and`/`or`/`not`/`!` and parentheses
    (`tag vip or (zone hub and health 10)`); `all:`/`any:`/`none:` groups; custom keywords via
    `ConditionParser.registerKeyword`.
  - Expressions: `%`, `!`/`not`, unary minus as a real operator, short-circuit `and`/`or`,
    single-quoted strings and escapes, text joining with `+`, and new functions (`clamp`, `sign`,
    `sin`, `cos`, `random`, `randint`, `contains`, `startsWith`, `endsWith`, `lower`, `upper`,
    `trim`, `len`, `replace`, `str`, `num`, `isnull`, `default`); add-ons can register functions.
  - Events: `delay:` accepts `20t`/`1.5s`/`1m`; `\"` inside quoted arguments; argument counts checked
    against each event's usage; `variable set x $a$ + 1` works without quotes.
  - Load-time diagnostics for unknown events/functions/variable namespaces, bad arguments, bad delays
    and expression syntax errors — with file, entry and line index.

### Changed
- Scripts in zones, NPCs (clicks, hologram conditions), dialogues, quest objectives, quest-board
  rewards, pets, progression, collections and the player hider are compiled once at load instead of
  on every execution; a failing script is logged once per minute with its source instead of throwing
  into the event listener.
- `ExpressionParser` is stateless and thread-safe; parsed expressions are cached.
- A mob with one unknown drop item now loads without that drop (was: the whole mob failed); unknown
  item stats and bad resistances/boss-bar values are warnings instead of failing the entry.
- `skills/` loading reports YAML syntax errors, keeps the last good version, and no longer treats
  `xp_curves.yml` as a skill.

### Fixed
- `npc_conversations` in `npcs/` was reported as a load error named "skip".
- Registering the same script event twice now warns (the double `notify` registration is explicit).
- `/valmora validate` had no permission check.

### Added (earlier)
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
