# Valmora: versioning, template drift and reload safety

> Status (2026-09-24): **Phases 0–6 implemented** on branch `claude/peaceful-turing-v7u4tn`.
> None of it has been compiled or run yet, because the build environment couldn't reach the Paper
> repository. What was built is documented in `docs/modules/design/versioning.md` (design) and
> `docs/modules/user/versioning.md` (admins). Where it differs from this plan:
> - **Phase 4** is per-entry "last known good" in `YamlLoader`, plus lazy script-event resolution,
>   instead of a full stage/validate/commit split in every module. Content never disappears on a
>   bad edit, and good edits in the same reload still apply.
> - **Mob loot and equipment stacks** are still built at parse time. Lore refreshes on pickup;
>   stats are live.
> - **Delayed script rewards** are cancelled on quit or restart, not queued for delivery.
> - **Progression levels above a lowered cap** are clamped, not refunded.

## Context
This plan covers what happens to persisted content when (a) the plugin version changes, (b) admins edit YAML, and (c) things already exist in the world (items, mobs, profiles, and so on), and how to remove every reload problem across all modules. Three read-only audits covered items, persisted player data, and world state plus the reload lifecycle. Summary of the current state:

- **Plugin upgrades:** DB tables have a real migration ladder (`database/SQLDataStore.java`, `LATEST_SCHEMA_VERSION = 8`). Nothing else is versioned:
  - JSON blobs in `valmora_profiles` have their shape guessed at load time.
  - Items have no data-version stamp.
  - `config.yml` has no version and no key merge.
  - Content seeding runs only once per install (`.resources_seeded`, `Valmora.saveAllResources`), so new default files never reach existing servers. `machines/` is missing from the seed whitelist, so even fresh installs get no machines.
- **YAML edits:**
  - Items copy stats, rarity, type, name and lore into PDC/meta at creation (`StatModule.saveStats`, `ItemFactory`) and never refresh them. Rarity and type in the PDC drift from the live definition, and the modifier engine reads the stale copy.
  - Potions (alchemy), pet names, anvil names and mob attributes set at spawn are frozen the same way.
  - Profiles reference YAML content by fragile keys: quest objectives by list index, collection stages by number, skills with no reward ledger (a curve change grants rewards twice or skips them), and stat base values that store the old default.
  - A renamed or deleted id is silently orphaned everywhere. Stats are the one exception: unknown stats are quarantined.
- **Reload:**
  - `ModuleManager.reloadModules()` is disable-all, then enable-all, with no validation before the swap, no rollback, and "success" always reported. A bad YAML entry deletes that content live.
  - Critical leaks:
    - Open GUIs become plain chests (item dupe).
    - Dialogue sessions keep freezing players.
    - Stats are recalculated mid-reload with most modules still off, so HP and mana get capped down.
    - Bosses lose their controllers.
    - Quest TIMER/DELAY objectives die.
    - HUD items overwrite whatever is in their slots.
    - Script events from later modules (`warp_to`, `notify`, quest events, `point`, `dialogue`) are compiled as no-ops in earlier modules' YAML. The `warp_to` buttons in `guis/fast_travel.yml` are dead today.
- **Data safety:**
  - A failed profile or economy load is treated as a new player, and for economy that overwrites the real balance.
  - There is no profile autosave.
  - Saves serialize live maps off the main thread.
  - A quick rejoin can load data before the quit save has committed.
  - Quiver refills never persist (arrow dupe).
  - `decodeItem` failures silently delete items.

**Decisions:**
- **Items:** live values plus lazy refresh. Items hold only their id and per-instance data; derived values come from the YAML at use time, and the display is re-rendered when a template hash changes.
- **Reload:** staged and atomic. Everything is parsed and validated first, and the swap happens only if there are no errors.
- **Delivery:** phased PRs on `claude/peaceful-turing-v7u4tn`, starting with phase 0.

---

## Phase 0: critical data-loss and dupe fixes (first PR, small and independent)
1. **GUI (`module/gui/GuiModule.java`):**
   - In `onDisable`, call `player.closeInventory()` after `closeGuiSession`.
   - Give GUI inventories a `GuiHolder implements InventoryHolder` that holds the session, so the listener can recognize any Valmora inventory, including stale ones, and cancel interaction with it.
2. **Failed load is not a new player:**
   - `SQLDataStore.loadPlayer` returns a result type {FOUND, NOT_FOUND, ERROR}.
   - `PlayerManager.handleJoin` treats ERROR as: kick the player with a "profile failed to load, try again" message and never create or save a profile.
   - Economy `loadEconomy` does the same: never cache 0/0 after an error; mark the account unloaded and refuse writes.
3. **`decodeItem` failure:** log with the slot and profile, and keep the original bytes (write back the raw blob instead of an empty slot).
4. **Snapshot on the main thread:**
   - `savePlayer` builds an immutable snapshot on the main thread: copied maps and items already serialized to bytes.
   - Only JDBC runs on `dbExecutor`.
   - Catch `Throwable` and log from the futures.
5. **Per-player operation ordering:** a per-UUID `CompletableFuture` chain in `PlayerManager` so that load waits for any pending save of the same UUID. Drop a finished load if the player has already quit (ghost session).
6. **Profile autosave:** a dirty flag plus an interval task (`profiles.autosave-interval-seconds`) that captures the live inventory on the main thread.
7. **Quiver:** `QuiverListener.refillIfEmpty` persists through `saveStorage` (same path as the GUI storage).
8. **HUD (`HudItemModule.giveHudItems`):**
   - If the target slot holds a non-HUD item, move it to a free slot, or drop it at the player's feet.
   - On join/reload, remove HUD-tagged items whose definition or slot no longer matches.
9. **Dialogue:** `NpcModule.onDisable` ends every `DialogueManager` session: cancel the freeze and action-bar tasks and restore the player.
10. **Seeding:** add `machines/` to `seedResourceIfSeedable`. The real fix is in Phase 1 (manifest seeding), which also delivers it to existing installs.
11. **Zone spawner:** skip spawns if `world.isChunkLoaded` is false for the spawn chunk. Use non-loading block lookups.
12. **Quest join:**
    - Move `QuestListener`'s join handling and `HudItemListener`'s join handling to `PlayerProfileLoadedEvent`.
    - Fix the auto-once key mismatch (`QuestManager` writes `obj.<type>` but reads `obj.<index>`) by using one key function.
13. **Economy flush:** on failure, re-add the players to the dirty set. On disable, wait for an in-flight flush before the final save.
14. **DB migrations:**
    - Wrap each `migrateToVN` step and its version stamp in a transaction.
    - `addColumnIfMissing` swallows only "duplicate column" errors.
    - A database schema newer than the plugin supports disables the plugin instead of only warning.

## Phase 1: versioning foundation
- **Config:**
  - Add `config-version:` to `config.yml`.
  - New `infrastructure/config/ConfigUpdater` (Paper's `YamlConfiguration` keeps comments since 1.18) adds any key missing from the jar default, keeps admin values, writes a backup, and runs versioned key renames/moves (`config_migrations` ladder in code).
  - Apply the same updater to the single-file configs: `ui.yml`, `rarities.yml`, `*_pipeline.yml`, `alchemy/modifiers.yml`.
- **Content seeding manifest:**
  - Replace `.resources_seeded` with `.resources_manifest.yml` (path → hash of the shipped file + the plugin version that shipped it).
  - On startup:
    - A new shipped file that isn't in the manifest is seeded.
    - A file the admin deleted stays deleted, because the manifest remembers it.
    - A shipped file that changed upstream, while the admin copy still matches the old hash, is updated in place.
    - If the admin edited that file, the new version is written as `<file>.new` and a warning is logged.
  - Existing installs with the old marker get a manifest built from their current files the first time this runs.
- **Profile data version:**
  - Schema v9 adds a `data_version INT` column to `valmora_profiles`.
  - A `ProfileMigrator` ladder transforms the JSON blobs in memory at load time. It replaces the `player_state` array-vs-object and `collections` shape guessing.
  - Load `variables` with Gson `ToNumberPolicy.LONG_OR_DOUBLE` so integers stay integers.
- **Item data version:**
  - New PDC key `valmora:item_data_version` (int) plus an `ItemMigrator` ladder that runs in the Phase 2 refresh hook.
  - Its first step converts legacy CSV enchants to the structured format (so the read-only fallback in `EnchantStateStore` can eventually be removed).
- **Id aliases (renames without orphaning):**
  - Every definition may declare `previous-ids: [..]`.
  - A central `IdAliasRegistry` (per content type) is filled during load.
  - All persisted-reference resolution goes through it: item/pet/modifier/enchant ids on items; skill, quest, collection, progression and stat keys in profiles.
  - Pack namespacing registers the bare id as an alias of `<pack>:<id>`, so losing the pack ledger no longer orphans data.
- **Orphan quarantine (generalized):** the stat quarantine already in `SQLDataStore` becomes a shared rule. Unresolved keys in skills, collections, variables and modifiers are kept as-is (never deleted), ignored at runtime, and listed by `/valmora orphans [player]` with an option to remap or purge them.

## Phase 2: live items plus lazy refresh
- **What items store:**
  - Keep: `valmora_item_id`, instance data (enchants and their state, modifier components, pet level/xp/instance, storage contents, anvil work count, alchemy effect id/level/duration, `custom_name`).
  - New: `template_hash`, `item_data_version`.
  - Stop treating stats, rarity and type as sources of truth.
- **Live reads:**
  - `StatManager` (around line 164) reads base item stats from `ItemDefinition` by id, falling back to the PDC only for untemplated or translated vanilla items.
  - `ModifierEngine` (around lines 452/459), `ItemType.fromItemStack` and `AnvilMachineHandler` read rarity and type from the definition.
  - Put this behind a single `ItemView.of(stack)` helper so every caller shares one resolution path.
- **Rarity:**
  - Items use `RarityRegistry` (`rarities.yml`) instead of the hardcoded `Rarity` enum.
  - Delete the duplicate switch in `AlchemyMachineHandler` (around line 305).
  - `ItemDefinitionParser` validates rarity against the registry.
- **Refresh:** `ItemRefresher.refresh(stack)` runs the migrator ladder, then re-renders the name and lore through `ItemFactory.updateLore` if `template_hash` differs from the definition's current hash. The hash covers the definition, the lore layout, the relevant enchant/modifier/rarity definitions and the `items.lore` config. It runs:
  - on join, for the whole inventory;
  - when a player opens any inventory (container contents);
  - on held-item change and on pickup;
  - on `ReloadCompletedEvent`, for all online inventories.
  - Cheap path: compare the hash and exit early.
- **Names and lore that must survive a refresh:**
  - Player and anvil names go into a `custom_name` PDC field that `updateLore` respects (fixes renames being wiped at ItemFactory:116).
  - Anvil `add_lore` goes into an `extra_lore` field that is rendered after the template lore.
  - Enchanted books and other untemplated items rebuild lore from `GENERIC_BASE_LORE` plus live enchant descriptions.
- **Clamping at read time:**
  - Enchant level to its current `max-level`.
  - Modifier tier (also for display).
  - Pet level to its max.
  - Alchemy level and duration to the current effect definition.
- **Ghost references:**
  - A modifier or enchant id that doesn't resolve (after trying aliases) doesn't use up capacity and doesn't block EXCLUSIVE groups.
  - It is rendered as a `<gray>Unknown (<id>)` line.
  - It can be removed through the anvil REMOVE path or `/item strip-unknown`.
- **Deleted item template:** the item keeps its last lore, gets a "Legacy item" line, and gets no stats or abilities. Log once per id.
- **`ItemDataCarrier`:** also carries enchant state (fixes `mergeLevels` creating empty `Map.of()` state), `storage_contents`, `container_gui`, `anvil_work_count`, `custom_name`. This fixes backpack contents being lost on upgrade.
- **Pets:** the display name comes from the definition plus the level from the PDC, and is updated on level-up (PetModule:252).

## Phase 3: stable references in profile progress
- **Skills:**
  - Persist `rewardedLevel` per skill.
  - Level-up rewards are granted only for levels in (rewardedLevel, currentLevel], so a harder curve never re-grants and an easier curve grants the skipped levels on the next XP event or on load.
  - `getLevel` is capped at `max-level`.
- **Quests:**
  - Objective progress keys use the objective `id`.
  - The parser generates a stable fallback id (`<type>_<n>`, counted among objectives of the same type) and logs a warning recommending explicit ids.
  - The `ProfileMigrator` step moves old index keys to id keys based on the definition at migration time.
  - Completed-quest status resolves through aliases.
  - Clean up `objective.*.active` flags for quests that no longer exist.
- **Quest TIMER/DELAY:**
  - Persist an absolute deadline (`quest.<id>.obj.<key>.deadline` in epoch ms).
  - Handlers resume from the deadline on `PlayerProfileLoadedEvent` and after a reload, and are cancelled on quit.
  - Newly added objectives in an in-progress quest get their start hook on load.
- **Collections:**
  - The granted-stage ledger stores a stable stage key (explicit `id`, else the threshold), not the stage number.
  - A migrator step converts existing data.
- **Stats:**
  - Store only admin allocations as a delta (`base = YAML default + delta`).
  - A migrator step computes `delta = stored − current default`.
- **Progression:**
  - Clamp levels to `max-level`.
  - Refund points for levels above the cap using the recorded `spent` amounts.
  - `resetTree` also purges keys for nodes that no longer exist (through the orphan list).
- **Storage GUIs:** when the slot count shrinks, return the overflow items to the player or a pending-delivery list instead of truncating (`SQLDataStore` around lines 622–631).
- **Calendar:**
  - Persist the active event set in `calendar_state.yml`.
  - On load or reload, run `on-end` for events that were active but are now inactive, edited or deleted.

## Phase 4: staged atomic reload
- **New lifecycle** (in `api/ReloadableModule` or a new `StagedModule`):
  - `registerApi(ApiRegistry)`: script event factories, variable providers and condition types. Runs for **all** modules before any content is parsed, so fixes the event-ordering bug (`warp_to` and others).
  - `Staged stage(ReloadContext)`: parses YAML into new registry objects with no side effects.
  - `validate(ReloadContext)`: checks cross-references against the staged set: item ids in mobs, loot, recipes and shops; GUI/machine links; script event names; quest/NPC/dialogue refs; aliases.
  - `commit(Staged)`: swaps the registry references on the main thread. Listeners, tasks, sessions, bosses and timers **stay alive**; only the definitions change.
- **`ModuleManager.reload()`:**
  - Stage and validate every module, and on any error abort with the old content untouched.
  - Otherwise commit in order and fire `ReloadCompletedEvent`.
  - `/valmora reload --dry-run` (also `/valmora validate`) reports per-module errors and counts.
  - `ValmoraCommand` reports the real outcome instead of always saying success.
- **After commit, driven by `ReloadCompletedEvent`:**
  - Recalculate stats for all online players once, without `capToMax` during the swap.
  - Refresh items.
  - Re-open or re-render open GUI sessions whose definition changed; close them if it was deleted, refunding their INPUT items.
  - Reapply live mobs (Phase 5).
  - Reconcile the calendar.
  - Rebuild the zone membership map.
- **Parse-time `ItemStack`s:** mob loot/equipment and smithing results store the item id and build the stack on use, so a reload never serves stale stacks. Smithing recipes removed from YAML are unregistered by diffing old and new keys.
- **Static registries** (`MobCategory`, `DamageType`, `ItemType` REGISTRY, `GuiRenderer.CONDITION_CACHE`) move into staged module state.
- **Pack partial reload:**
  - Modules declare `dependsOnContent()`, and `PackManager.reloadAffectedModules` includes dependents.
  - Shared config files map to their owning modules.
  - Reinstall is install-then-swap (stage the new pack, and uninstall the old one only when the new one validates).
  - Wire in the dead `PackValidator.validateReferences` code.
- **Migration path:** modules not yet converted keep the legacy disable/enable path inside a fallback adapter. Convert in dependency order: script → stat/rarity → item → mob → recipe/machine → gui → quest/npc/dialogue → the rest.

## Phase 5: world state hygiene
- **Tag everything Valmora spawns** with `valmora:owner_module` and make it non-persistent where possible:
  - Damage indicator `TextDisplay`s (`DamageIndicatorManager`): tagged and `setPersistent(false)`.
  - Pets: tagged, non-persistent, invulnerable, no drops; the active mapping is cleared when a pet dies.
- **Startup sweep:** extends the existing NPC sweep (`NpcManager` around lines 82–89) to all tagged transient entities, and also runs on `EntitiesLoadEvent`.
- **Mobs:**
  - Store a `mob_template_hash` on the entity.
  - On `EntitiesLoadEvent` and `ReloadCompletedEvent`, reapply attributes, name and equipment when the hash changes, keeping the current-HP ratio.
  - A deleted template follows the config setting `mobs.orphan-policy: remove|vanilla|keep`.
- **Bosses:** `BossController` re-attaches to live boss entities (identified by PDC) on `EntitiesLoadEvent` and after reload.
- **NPCs and holograms:** respawn only when the chunk is loaded and the entity is really missing (check PDC in the loaded chunk), never force-load the chunk.
- **Resource module:**
  - Save state on every deplete (debounced 2s) instead of every 30s.
  - `loadState` keeps entries for unloaded worlds or removed configs (it restores the original block when the world loads) instead of deleting the file.
- **Script `delay:`:**
  - Tasks are tracked per owner (player UUID plus module), cancelled on quit and reload, and check that the player is still online.
  - A delayed `give` that is cancelled is sent to a persistent pending-delivery list.
- **Player attributes and effects:**
  - Remove the `valmora:*` attribute modifiers and restore changed base values on disable and on quit.
  - Potion-effect cleanup removes only effects Valmora applied (tracked), not every effect longer than 1 hour.
  - `TemporaryStatService` is keyed by UUID plus profile, cleared on quit and profile switch, and never holds the profile object.
  - Alchemy active effects are persisted in the profile `player_state` with an absolute expiry.
- **Time catch-up events** run in a post-enable hook after all modules are enabled, instead of in `TimeManager.onEnable`.
- **Warp warmup:** re-resolve the warp and profile when the warmup finishes, and charge at the same time as the check.
- **Summoned pets** are re-summoned after a reload; the active pet instance id is kept in memory.

## Phase 6: tests and docs
- **Unit tests** (JUnit 5 + Mockito; `ExpressionTest` is the setup pattern):
  - `ItemMigrator` / `ProfileMigrator` ladders.
  - Skill reward ledger for harder and easier curves.
  - Quest index→id migration.
  - Collection stage-key migration.
  - `ConfigUpdater` merge, including comments.
  - Manifest seeding cases: new, deleted, unchanged-upstream-changed, admin-edited.
  - Staged reload: a broken YAML leaves the old registry live, and validate catches an unknown script event.
  - `IdAliasRegistry` resolution.
- **In-game QA:** a `/test reload` scenario alongside `/test enchant` that opens a GUI, starts a dialogue and a timed quest, spawns a boss, reloads, and checks that each one survives or is cleaned up.
- **Docs:**
  - A new `docs/modules/design/versioning.md` covering the data-version ladders, aliases, orphans, the reload lifecycle and the refresh hooks.
  - Updated `MODULE_DEVELOPMENT.md` (the new stage/validate/commit contract) and CLAUDE.md §6.
  - Fix the `stat.md:302` autosave doc drift.

## Critical files
- `module/ModuleManager.java`, `api/ReloadableModule.java`, `Valmora.java` (seeding, onEnable ordering), `ValmoraCommand.java`
- `database/SQLDataStore.java`, `module/profile/PlayerManager.java`, `module/economy/EconomyModule.java`
- `module/item/ItemFactory.java`, `module/stat/StatModule.java`, `module/stat/StatManager.java`, `module/modifier/ModifierEngine.java`, `module/enchant/EnchantmentHelper.java` + `EnchantStateStore.java`, `module/recipe/ItemDataCarrier.java` + `AnvilMachineHandler.java`, `module/alchemy/AlchemyMachineHandler.java`, `module/rarity/*`
- `module/gui/GuiModule.java` + `GuiListener.java`, `module/npc/dialogue/DialogueManager.java`, `module/hud/HudItemModule.java`, `module/item/QuiverListener.java`
- `module/quest/QuestManager.java` + timer/delay handlers, `module/skill/SkillManager.java`, `module/collection/*`, `module/progression/ProgressionManager.java`, `module/calendar/CalendarEventModule.java`
- `module/mob/MobFactory.java` + `BossController.java`, `module/zone/ZoneManager.java`, `module/resource/ResourceManager.java`, `module/combat/DamageIndicatorManager.java`, `module/pet/PetModule.java`, `module/script/event/EventParser.java`
- New: `infrastructure/config/ConfigUpdater.java`, `infrastructure/versioning/{ItemMigrator,ProfileMigrator,IdAliasRegistry,ResourceManifest}.java`, `module/item/ItemRefresher.java`, `ItemView.java`

## Verification (per PR)
- `./gradlew build test` is green.
- `./gradlew runServer` manual scenarios:
  - Phase 0:
    - Open a storage GUI, `/valmora reload`, and confirm the menu closes and nothing can be taken.
    - Kill the DB connection and join: the player is kicked and the profile isn't overwritten.
    - Use the quiver, relog, and confirm no dupe.
    - Place an item in a HUD slot, reload, and confirm the item is preserved.
    - Reload mid-dialogue and confirm the player isn't frozen.
  - Phase 1–2:
    - Edit an item's stats, rarity and lore layout, reload, and check that the item in inventory updates while keeping its anvil name, enchants and reforge.
    - Rename an id using `previous-ids`, and check that existing items and progress resolve.
  - Phase 3:
    - Make the skill curve harder or easier and confirm no duplicate or missed rewards.
    - Reorder quest objectives and confirm progress is kept.
    - Restart mid-timer and confirm the timer resumes.
  - Phase 4:
    - Break a YAML file, reload, and confirm the old content stays live with the errors reported.
    - `fast_travel` `warp_to` buttons work.
  - Phase 5:
    - Crash the server with damage indicators and a pet out, restart, and confirm there are no orphans.
    - Edit a boss's HP and confirm the live boss updates while keeping its boss bar.
