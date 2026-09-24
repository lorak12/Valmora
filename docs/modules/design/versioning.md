# Versioning, Template Drift & Reload Safety — Design & Code

Cross-cutting design doc (no module of its own). It covers how Valmora keeps persisted data,
existing items and entities, and admin files correct when:

- the **plugin version** changes (schema, data shapes, default files);
- admins **edit YAML** (template values change under things that already exist);
- content is **reloaded** (`/valmora reload`, `/item reload`, pack install/uninstall).

The motivating audit and the phased plan are in `docs/VERSIONING_AND_RELOAD_PLAN.md`. The
admin-facing summary is in `docs/modules/user/versioning.md`.

---

## 1. Principles

1. **Templates are read live, instances store only instance data.** An item, mob or profile keeps
   its id plus what is genuinely per-instance: enchants, modifier components, pet XP, storage
   contents, skill XP, quest progress. Everything the YAML defines (stats, rarity, type,
   name/lore, mob attributes, stat defaults) is resolved from the current definition. Stored
   copies are only a fallback when the definition is gone.
2. **Every persisted shape has a version and a migration ladder.** DB tables
   (`valmora_schema_version`), profile JSON blobs (`valmora_profiles.data_version`), items
   (`valmora:item_data_version`), `config.yml` (`config-version`), and default content files
   (`.resources_manifest`).
3. **Renames never orphan data.** `previous-ids:` aliases are followed everywhere an id is
   resolved. Unresolvable data is kept (quarantined) and never silently deleted.
4. **A bad edit never removes live content.** An entry that stops loading keeps its last working
   version, and the error is reported.
5. **Reload is a lifecycle, not a restart.** Nothing may capture state that a reload replaces. Tasks
   are owned and cancelled. World state is reconciled after the reload (`ValmoraReloadedEvent`).

---

## 2. Version ladders

| What | Stamp | Ladder | Code |
|---|---|---|---|
| DB tables | `valmora_schema_version.version` | `SQLDataStore.applyMigrations` → `migrateToVN`, each step and its stamp in one transaction (`runMigrationStep`) | `database/SQLDataStore.java` |
| Profile JSON blobs | `valmora_profiles.data_version` | `ProfileMigrator.migrate` (pure JSON rewrites) | `database/ProfileMigrator.java` |
| Item PDC | `valmora:item_data_version` | `ItemMigrator.migrate`, run lazily by `ItemRefresher` | `module/item/ItemMigrator.java` |
| `config.yml` | `config-version` | `ConfigUpdater.MIGRATIONS` (key moves) + missing-key merge | `infrastructure/config/ConfigUpdater.java` |
| Default content files | `.resources_manifest` (hash per file) | `ResourceManifest.sync` | `infrastructure/versioning/ResourceManifest.java` |

**Downgrades are refused, not guessed.**
- A DB schema newer than `LATEST_SCHEMA_VERSION` makes `init()` throw, and the plugin disables.
- A profile row with a newer `data_version` fails that player's load. They are kicked, and
  nothing is overwritten.
- A `config-version` newer than known leaves the file untouched.

**Current profile data versions:**
- v1 normalises the `player_state` and `collections` shapes.
- v2 wraps `skills` as `{xp, rewarded}` (the skill reward ledger).
- v3 changes the meaning of `stats`: it holds allocations (offsets from the current default), not
  absolute values. This is detected by row version in `loadPlayer`; there is no JSON step.

**Current item data version:** v1 converts the legacy enchant CSV to the structured format.

### Adding a step
- **DB:** bump `LATEST_SCHEMA_VERSION`, add `migrateToVN` (idempotent — MySQL auto-commits DDL), and
  add it to `applyMigrations`.
- **Profile blob:** bump `ProfileMigrator.LATEST_VERSION`, add `toVN` (pure JSON), and update
  hydration for the new shape only. A migration that needs live definitions goes in the post-load
  pass (`ProfileReferences`, or a manager's own `migrate…` method like
  `QuestManager.migrateLegacyProgressKeys`).
- **Item:** bump `ItemMigrator.LATEST_VERSION` and add `toVN`.
- **config.yml:** bump `ConfigUpdater.LATEST_VERSION` and `config-version` in the bundled file, and
  add renames to `MIGRATIONS`.

---

## 3. Default content files (`ResourceManifest`)

TSV manifest, one line per shipped file: `baseHash`, `offeredHash`, `path`. Hashes ignore `\r`.
The rules are in the class doc. In short:
- new files are installed;
- deleted files stay deleted;
- unedited files follow updates (`resources.auto-update-unmodified-defaults`);
- edited files get a `<file>.new` once per shipped version.

On legacy installs (`.resources_seeded` present, no manifest yet), a missing file is only
installed if its whole top-level folder never existed (this delivers `machines/`).

The seedable set is `Valmora.isSeedableResource`. `config.yml` is excluded; it goes through
`ConfigUpdater`.

---

## 4. Id aliases (`IdAliases`)

`IdAliases` is a per-content-type map from alias to canonical id. The type key is the
`YamlLoader` folder name, e.g. `items`, `modifiers/definitions`.

**Where aliases come from:**
- `YamlLoader.registerAliases` records every `previous-ids:` entry, plus the bare id of
  pack-namespaced content (so data saved before namespacing, or after the pack ledger was lost,
  still resolves).
- `CollectionLoader` registers its own, since it doesn't use `YamlLoader`.
- An alias claimed by two ids is dropped and logged.

**Where aliases are resolved (always "registry miss → alias"; a real id is never shadowed):**
- **`SimpleRegistry(aliasType)`:** items, mobs, skills, quests, enchants, progression trees, warps.
- **Hand-written registries:** modifiers, modifier groups, collections, pets.
- **Saved profiles:** `ProfileReferences.resolveAliases`, run by `SQLDataStore.loadPlayer` on every
  load, moves skill XP and ledger, collection counts and ledgers, `quest.<id>.*`,
  `progression.<tree>.*` and auto-once guard tags to the canonical id (merging with max).
- **Live mobs:** `MobLifecycleListener` re-tags renamed ids.

**Orphans:** data whose id resolves to nothing is kept and ignored. `OrphanReport` lists and
purges it (`/valmora orphans <player> [purge]`). Stats keep their original quarantine
(`ValmoraProfile.getQuarantinedStats`).

---

## 5. Items (`ItemView`, `ItemRefresher`)

- **`ItemView` is the single resolution path** for template data:
  - rarity key, type and definition come from the live definition, else the PDC copy, else the
    material;
  - `StatModule.loadStats` returns the definition's stats for defined items.
  - Callers: `ItemType.fromItemStack`, `ModifierEngine`, both anvils, `StatManager`, lore.
- **Rarity display** goes through `ItemRarities` (`rarities.yml`, with the enum as fallback). Custom
  rarity keys are valid on items.
- **Refresh:**
  - Every rendered item carries `valmora:item_render_epoch`, a fingerprint of all item-affecting
    content (`ItemRefresher.recomputeEpoch`, recomputed on item-module enable and after every
    reload).
  - `ItemRefresher.refresh` runs the migrator, then re-renders when the epoch differs:
    - pets via `PetModule.applyPetDisplay`;
    - potions via `AlchemyMachineHandler.rerenderPotion`;
    - enchanted books via `EnchantmentHelper.rerenderGenericLore`;
    - defined and `vanilla_` items via `ItemFactory.updateLore`.
  - Items with a deleted definition get a "Legacy item" line.
  - Refresh triggers are `ItemRefreshListener` (profile load, real-container open, pickup,
    held-item change) and `ModuleManager.afterReload` (every online player).
- **Per-instance presentation** lives in the PDC so re-renders keep it:
  - `custom_name` (player and anvil names);
  - `extra_lore` (anvil `add_lore`).
- **Clamps at read time:**
  - enchant level: `EnchantmentDefinition.effectiveLevel` against the absolute max;
  - modifier tier display;
  - pet level: `PetModule.levelOf`;
  - alchemy level: `AlchemyManager.applyEffect`.
- **Ghost modifiers** (definition deleted) are dropped when a new modifier is applied, so they
  never use up capacity or lock EXCLUSIVE groups.

---

## 6. Profiles and progress

- **Data safety (Phase 0):** see `docs/modules/design/profile.md` → *Async save pattern & thread
  safety*. It covers per-player lanes, snapshot on the caller thread, `DataLoadException`, the
  recovery log, join generations and autosave.
- **Skills:** `SkillManager` keeps `rewardedLevel` per skill. Rewards are granted for
  `(rewarded, current]` only. A missing entry (legacy save) means "rewarded up to the current level".
  Levels are capped at `max-level`.
- **Quests:**
  - Progress keys come from `QuestDefinition.progressKey(i)`: the explicit id, else
    `<type>_<n>`. `QuestManager.migrateLegacyProgressKeys` moves index keys.
  - Each objective records `…obj.<key>.started`. `QuestManager.resumeObjectives` initialises
    objectives added since the quest started and calls `ObjectiveHandler.onResume`.
  - TIMER counts online seconds from its saved progress. DELAY is wall-clock from `started`.
  - Handlers cancel on quit (`onPlayerQuit`) and module disable (`cancelAll`).
- **Collections:** the ledger is keyed by `CollectionStage.getKey()` (the stage `id`, else
  `req:<threshold>`). It is seeded once from the old number floor.
- **Stats:** saved as offsets from the current default (`getAllocationSaveData` /
  `loadAllocationsAndQuarantineUnrecognized`).
- **Progression:** `getNodeLevel` is capped at the node's current `max-level`. `resetTree` removes
  every `.level` key under the tree, including removed nodes.
- **Storage GUIs:** `SQLDataStore` and `ItemStorageCodec` never truncate.
  `GuiModule.settleStorageOverflow` returns overflow items and persists the truncation. Unreadable
  item-owned storage refuses to open.
- **Calendar:** `calendar_state.yml` stores the active set together with each event's on-end
  lines. `reconcileAgainstPreviousActive` ends events that were edited or deleted while active.

---

## 7. Reload lifecycle

- **`ModuleManager.reloadModules()`:**
  - Sets `isReloading()`, so `StatManager` doesn't cap HP or mana to understated maxima mid-reload.
  - Collects the `YamlLoader` report.
  - Runs `afterReload`: item epoch plus a refresh of online players, a stats recalculation for
    every online player, and `ValmoraReloadedEvent`.
  - Returns a `ReloadResult` (failed modules, content errors).
- **Last-known-good content (`YamlLoader` + `LastGoodStore`):**
  - Each successfully loaded entry's YAML is remembered per `(file, key)`, in memory and in
    `.last-good/<folder>.yml`.
  - On a YAML syntax error (now detected; `loadConfiguration` used to return empty), every
    remembered entry of that file is loaded instead.
  - On a parser failure, the remembered version of that entry is loaded instead.
  - Entries removed from files, or whose files were deleted, are forgotten.
- **Validation:** `YamlLoader.validateAll` re-runs every recorded parser over the files on disk.
  `YamlLoader.isValidating()` lets side-effecting parsers (smithing registration) skip.
- **Script events:**
  - An unregistered event name compiles to a lazy event that resolves its factory on first
    execution. This fixes load-order dependence (`warp_to`, `notify`, quest and point events).
  - `EventParser.reportUnresolved()` runs after all modules are enabled.
  - `delay:` tasks go through `DelayedEventTracker`, which cancels them on quit and on script
    disable and skips offline players.
- **Open enums** (`ItemType`, `MobCategory`, `DamageType`) call `resetToBuiltins()` before
  loading. The GUI condition cache is cleared when the GUI module disables. Smithing recipes are
  tracked and unregistered.
- **Packs:** reloading a pack also reloads the modules that own the shared configs it merged into
  (`PackContentFolders`).

### Rules for module authors
- Close anything player-facing that your listener guards (inventories, dialogues) in `onDisable`.
  An unguarded open inventory is a dupe.
- Never capture a `ValmoraProfile`, manager or `Player` in a delayed task or long-lived closure.
  Capture ids and re-resolve at run time (see `ModifyStatMechanic`, `WarpManager`, the timer
  handlers).
- Anything listening to join and needing the profile or inventory listens to
  `PlayerProfileLoadedEvent`, which fires after the inventory and stats are applied.
- World state that depends on content reconciles on `ValmoraReloadedEvent` and on
  `EntitiesLoadEvent`.

---

## 8. World state

- **`TransientEntities`:** presentation entities (damage indicators, pets) are tagged and
  non-persistent. `Sweeper` removes leftovers on chunk load, and a sweep also runs at startup.
- **`MobLifecycleListener`:** the template fingerprint (`MobFactory.fingerprint`) is re-applied
  when it changes, keeping the health fraction. It also re-tags aliased ids, calls
  `BossController.attach`/`unregister` on load and unload, and applies `mobs.orphan-policy`.
- **NPCs and holograms:** checks skip unloaded chunks.
- **Resources:** saves are debounced after each change. Unrestorable entries are kept (unloaded
  world) or reverted (config removed).
- **Players:**
  - `StatModule` tracks the attributes it touched (`valmora:touched_attributes`) and restores any
    whose mapping is gone. It resets all of them on quit and shutdown.
  - `PassiveEffects` removes only Valmora's infinite effects.
  - `TemporaryStatService` is cleared on quit and profile switch.
  - Alchemy effects persist in `PlayerState.SaveData.alchemyEffects`.
  - Pets are re-summoned after a reload.
  - Zone membership is carried across reloads without events.
  - The time module's catch-up events are deferred one tick, until every module is enabled.

---

## 9. Known limits
- Mob loot and equipment `ItemStack`s are still built when the mob YAML is parsed. They drop with
  old lore until `ItemRefresher` touches them on pickup. Stats are live regardless.
- Reload is per entry, not an atomic stage/commit across modules: a module whose `onEnable`
  throws can still be half-loaded (it is reported as such).
- Script `delay:` rewards pending at quit or restart are dropped, not queued for delivery.
- Modules that load YAML without `YamlLoader` (stats, rarities, pipelines, collections) don't get
  last-known-good fallback.
