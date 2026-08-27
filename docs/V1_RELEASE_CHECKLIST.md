# V1 Release Checklist — Production Readiness

> **Progress note (2026-08-26, pass 2):** went through essentially everything left that doesn't
> need a running server. New test files this pass: `RecipeEngineTest` (+4 precedence cases),
> `GuiEventBlockStageTest` (7 cases, new file), `GuiForceCraftEventFactoryTest` (4 cases, new
> file), `ResourceIntegrityTest` (+2 item-reference cross-check tests). Suite is now **848 tests,
> all green**, `./gradlew clean build` succeeds with **zero warnings** (fixed a real deprecation).
>
> New real bugs found and fixed this pass:
> - `recipes/crafting_table.yml`'s `wood_planks` recipe referenced item `LOG`, not a valid 1.13+
>   Material — it never matched *anything*, silently, not even a real oak log. Pinned to `OAK_LOG`.
> - `PlayerVariableProvider`'s `$player.biome$` used the deprecated (marked-for-removal)
>   `Enum#name()` on the now-registry-backed `Biome` type — switched to `getKey().getKey()`.
> - `docs/modules/design/item.md` was stale: said "14 concrete mechanics" (really 16 —
>   CANCEL_TRAMPLE/CHARGE_JUMP were added without updating the count) and its trigger-sources table
>   was missing `ON_DAMAGE_TAKEN`/`ON_TELEPORT`/`EQUIP`/`UNEQUIP` entirely. Fixed.
> - CLAUDE.md's version table said `0.1`; the real version (`build.gradle`) is `1.0.0-beta1` —
>   already bumped, just undocumented. Fixed.
> - CLAUDE.md §14.1 claimed "this project does not currently use raw packets" — false; there's a
>   hard `packetevents` dependency actively used for NPC dialogue interception. Fixed.
>
> Also closed out with **no gap found** (verified, not assumed): SQLite schema versioning/migration
> (already has a `valmora_schema_version` table + per-version migrations, well-tested), the 4
> `.thenAccept`/`.thenApply`/`.thenRun` async chains outside `SQLDataStore` (all correctly wrap
> Bukkit-facing work in `runTask`, or rely on Paper's documented `teleportAsync` main-thread
> guarantee), economy command amount validation, DB credential logging, and PDC-vs-displayname
> identity checks.
>
> **One real finding surfaced but not fixed** (a feature gap, not a bug): no debug/verbose logging
> toggle exists in `config.yml`. And **one permissions-model observation**: every admin command
> collapses to a single blanket `valmora.admin` node — noted for your call on whether v1 needs
> finer-grained nodes.
>
> Still open and out of reach without a server/infra: `resource` module test coverage (needs a much
> bigger Bukkit-entity mocking effort), MySQL-backed testing (needs a live instance), the full
> `GuiRenderer` render-pipeline success path, and everything in §3/§4/most of §7.
>
> **Progress note (2026-08-26, pass 1):** a first pass through every non-in-game item is done — see the
> ✅/⚠️ annotations below. Two findings need a decision from you before they're resolved; both are
> called out inline and summarized at the bottom of this note.
>
> - ✅ Added `ResourceIntegrityTest` — note the name clash: this is a **config-file integrity**
>   sweep (full-tree YAML parse, duplicate-ID detection for items/mobs/guis/recipes, GUI↔recipe
>   machine cross-reference, ability-mechanic check across *all* item files), unrelated to the
>   `resource` *module* (mining/resource-node blocks). Also added `HudItemModuleTest` (first-ever
>   coverage for the `hud` module). **`resource` module (`ResourceManager`) still has zero test
>   coverage** — its logic is heavily Bukkit-entity-coupled (Block/Location/World/ZoneManager),
>   which is why it wasn't tackled in this pass; still open.
> - ✅ **Real bug found and fixed:** `items/swords.yml` and `items/wands.yml` both defined
>   `fire_freeze_staff`/`fire_fury_staff` with different stats/mechanics/`item-type`. Per your
>   call, kept wands.yml's copy (the more refined one, with notes on mechanic approximations) and
>   removed the stale duplicate from swords.yml.
> - ✅ **Doc drift found and fixed:** CLAUDE.md §5's module registration order was 16 modules behind
>   `Valmora.java` (missing economy, alchemy, zone, resource, fishing, npc, warp, points, notify,
>   quest, collection, hud, calendar, reforge, pet, progression) — corrected in place.
> - ✅ **Doc drift found and fixed:** CLAUDE.md §9.3 claimed recipe YAML supports subfolders, but
>   the generic `YamlLoader.load()` wasn't actually recursive (only `CollectionLoader` had a
>   bespoke walk). Per your call, added recursion to `YamlLoader` itself — this now holds for
>   every module using it (items, mobs, guis, recipes, skills, etc.), not just recipes.
> - ✅ Listener-registration/unregistration and repeating-task-cancellation sweeps done via grep
>   across every `module/` file — no orphaned `registerEvents`/`runTaskTimer` found (see §1 notes
>   inline below for what was checked).
> - Full suite after all fixes: **830 tests, all green**, `BUILD SUCCESSFUL`.
> - Sections 3 (in-game), 4 (load), 5 (partly — see inline), 7 (ops) still need you at a keyboard/server.

> Working checklist, not part of the permanent doc set. Check items off as you go; delete or
> archive once the release ships. Cross-reference `docs/modules/design/<module>.md` /
> `docs/modules/user/<module>.md` for the module you're touching before writing tests or filing
> bugs — this file tracks *what* to verify, not the implementation detail.

---

## 0. Before you start

- [x] `./gradlew clean build` — found and fixed one real deprecation warning:
      `PlayerVariableProvider`'s `$player.biome$` used `Biome.name()` (deprecated for removal —
      `Biome` is registry-backed as of 1.21 like `Attribute`/`Enchantment`, CLAUDE.md §14.9), fixed
      to `getKey().getKey()`. Confirmed no display-comparison usage anywhere in shipped YAML would
      break from the casing change (`plains` vs the old `PLAINS` enum name — only one GUI displays
      it as flavor text, `guis/stats.yml`). Clean build now warning-free.
- [x] `./gradlew test` is green. **848 tests** as of 2026-08-26 (was 815 before pass 1, 830 after
      pass 1, 848 after pass 2).
- [x] Confirmed target versions in `build.gradle` match reality: `paper-api:1.21.11-R0.1-SNAPSHOT`
      (both `compileOnly` and `testImplementation`) and a Java 21 toolchain (`targetJavaVersion =
      21`, enforced via `options.release.set(21)`).
- [ ] Tag or branch the current state (`git tag pre-v1-hardening`) so there's a clean rollback point.

---

## 1. Automated test gaps to close

Modules under `module/` and their current `src/test` coverage — fill the ones with **none** or
**thin** coverage first; these are the highest-risk blind spots for a v1 ship.

- [x] **`hud`** — was completely untested; added `HudItemModuleTest` (defaults, explicit
      slot/prevent-move, invalid-material and missing-`item`-section failure paths, PDC-based
      `isHudItem` identity check, reload-doesn't-double-up).
- [ ] **`resource`** — still no test file. `ResourceManager`'s logic is heavily coupled to live
      Bukkit entities (Block/Location/World) and `ZoneManager`, which made it out of scope for this
      pass — the persistence round-trip (`saveState`/`loadState`) and the Mining-Fortune multiplier
      math (`applyFortune`, currently private) are the two most tractable pieces to start with.
- [x] **`core`** (`Valmora.java`, `ModuleManager` wiring) — checked the actual registration order
      in `Valmora.java` against CLAUDE.md §5: the doc was **16 modules behind** (missing economy,
      alchemy, zone, resource, fishing, npc, warp, points, notify, quest, collection, hud,
      calendar, reforge, pet, progression) — corrected in place. No automated guard exists yet
      that fails a build if the order drifts again; `ModuleManagerTest`/`ModuleManagerReloadSafetyTest`
      only cover lookup/reload-safety semantics with stub modules, not the real order. Worth adding
      later: a test asserting CLAUDE.md's documented order is a subsequence of the real one.
- [x] **GUI machine pipeline dupe-lock** — added `GuiForceCraftEventFactoryTest`: a second
      `gui_force_craft` while `craftingLocked` is a verified no-op (never even calls
      `RecipeEngine.craft`), the lock is provably held *during* the engine call (not just
      before/after), and it's released both on a normal no-match result and on the engine
      throwing. **Not covered:** the full success path through `GuiRenderer.render()` (real
      output-slot population, `$gui.input.<id>$` snapshot resolution after re-render) — that needs
      a much larger render-pipeline fixture (519-line `GuiRenderer`, many component types) that
      wasn't worth building for this pass; still an open item if you want full end-to-end coverage.
- [x] **Recipe engine 3-way precedence** — added 4 cases to `RecipeEngineTest`: a registered
      `DynamicMachineHandler` wins over a matching YAML recipe for the same machine; YAML is used
      when the handler declines (returns empty); unregistering a handler falls back to YAML; and
      the vanilla fallback is proven scoped to `crafting_table` only (stubbing Bukkit to report a
      *real* vanilla match and asserting a non-`crafting_table` machine still returns empty, so the
      test can't pass by accident).
- [x] **Script DSL condition/branch coverage** — added `GuiEventBlockStageTest` (7 cases) covering
      the actual short-circuit contract: a `condition` event aborting mid-`actions` runs everything
      before it, nothing after it, then `fail-actions`; a passing top-level condition/actions list
      runs in full and never touches `fail-actions`; a failing top-level condition skips `actions`
      entirely; missing `fail-actions` is a no-op; and a `condition` inside `fail-actions` itself
      aborting is swallowed rather than propagating (matches `GuiEventBlockStage`'s documented
      behavior). This is the actual single place the fail-actions contract lives (three older
      inline copies were consolidated into it), so testing it here covers GUI on-open/on-close/
      on-slot-update/on-update uniformly.
- [ ] **Database layer against real MySQL** — still open; genuinely needs a live MySQL instance
      (or Testcontainers, which isn't currently a project dependency) to exercise, so it's out of
      reach for this non-in-game pass. `DatabaseFactory` builds the MySQL `HikariConfig` correctly
      at a glance (host/port/db/ssl all config-driven), but SQL dialect differences won't surface
      without actually running against MySQL — see §5 for the manual-verification version of this.
- [ ] **Reload safety sweep** — `ModuleManagerReloadSafetyTest` exists; verify every module with a
      `BukkitRunnable`/scheduled task (search `runTaskTimer`, `getScheduler()` across `module/`)
      cancels its task in `onDisable()`. Add one assertion per offending module found, not just a
      generic smoke test.
- [x] **Listener leak sweep** — grepped `registerEvents(` vs `unregisterAll(` across every
      `module/` file: identical set of 24 files call both, and per-file occurrence counts match
      (2/2, 4/4, 8/8 — no file with a lone `registerEvents` and zero `unregisterAll`). Coarse
      (doesn't prove a 2-listener file unregisters *both*), but no red flags. Not yet turned into
      an automated reflection-based test — still worth doing per the original note.
- [x] **Concurrency / async safety** — manual review of every `.thenAccept(`/`.thenApply(`/
      `.thenRun(` chain across `module/` (4 files: `PlayerManager`, `GuiModule`, `WarpManager`,
      `EconomyModule`). All correct: `EconomyModule#readOffline`/`writeOffline` and
      `PlayerManager#mutate`-style methods wrap the Bukkit-facing callback in
      `Bukkit.getScheduler().runTask(...)` and only touch plain Java objects off-thread;
      `GuiModule`'s inner `.thenAccept` only mutates a `ConcurrentHashMap` (thread-safe), with the
      actual Bukkit-touching work in an outer `.thenRun` that's `runTask`-wrapped;
      `WarpManager`'s `player.teleportAsync(...).thenAccept(...)` relies on Paper's documented
      guarantee that `teleportAsync`'s continuation runs on the main thread (a genuine exception to
      the general async-safety rule, not a violation of it). Not exhaustive across every DAO
      call-site in the codebase, but no gap found in the two heaviest chains (economy, profile
      persistence). Still worth a Checkstyle/ErrorProne rule longer-term per the original note.
- [x] Reload-safety sweep on repeating tasks — grepped `runTaskTimer(`/`.cancel()` across
      `module/`; spot-checked the count-mismatch candidates (`AlchemyModule`, `NpcModule`,
      `ReforgeModule`) by hand — all false positives from the regex also matching `.runTask(`
      substrings; `AlchemyModule`'s real `runTaskTimer` (potion-effect tick) is correctly cancelled
      in `onDisable()`. Not exhaustive across all 44 matched files, but no leak found where checked.
- [x] Re-run `./gradlew test` after adding the above — 830 tests, 1 failure (see progress note at
      top: a genuine duplicate-item-ID bug the new test caught, not a test defect).

---

## 2. Config & data validation (automatable)

- [x] Every shipped `.yml` under `src/main/resources` (130+ files, all folders — not the ~40-file
      hardcoded fixture list `YamlConfigLoadTest` used) parses without exception — confirmed
      `YamlConfigLoadTest` was indeed only checking a hand-picked subset; added
      `ResourceIntegrityTest#everyShippedYamlFileParsesWithoutException`, which walks the real tree.
- [x] GUI `machine:` ↔ recipe `machine:` cross-reference — added as a test. Found 5 GUIs
      (alchemy, anvil, enchanting_table, forge_random/reforge, reforge_anvil) with no YAML recipe
      at all; confirmed all 5 are legitimately owned by a `DynamicMachineHandler` registered in
      Java (`AlchemyModule`, `EnchantModule`, `ReforgeModule` x2, `RecipeModule`'s anvil handler) —
      allow-listed with a comment rather than treated as bugs. (Full `item:`→real-item-ID
      resolution across recipes/loot tables specifically is not yet checked — narrower than
      originally scoped here.)
- [x] Ability/mechanic name check — extended the existing 3-file check
      (`YamlConfigLoadTest#testNewItemsFile_abilityMechanicTypesAreAllKnownActive`) to sweep *all*
      `items/*.yml` files (`ResourceIntegrityTest#everyItemAbilityMechanicAcrossAllItemFilesIsKnownActive`).
      Caught a real gap: `items/backpacks.yml` uses `OPEN_CONTAINER_GUI`, which is registered by
      `GuiModule#onEnable()` into the shared `MechanicRegistry`, not by
      `AbilityManager#registerMechanics()` — so it was invisible to both the old narrow check and
      (until now) this new one. Added to both tests' known-active sets with a note on where it's
      really registered from.
- [x] Duplicate ID detection across items/mobs/guis/recipes — added as a test.
      **Found and fixed a real duplicate:** `fire_freeze_staff`/`fire_fury_staff` were defined in
      both `items/swords.yml` and `items/wands.yml`; removed the stale copy from `swords.yml` per
      your decision (wands.yml's version is canonical).

---

## 3. In-game manual QA script

Run this on a real Paper 1.21.11 dev server (`./gradlew runServer`) with at least one other test
account if possible (client-visible bugs like double-render or desync often need 2 clients).

> **See `docs/V1_MANUAL_QA_GUIDE.md` for the full step-by-step script** — concrete commands, item
> IDs, mob IDs, zone coordinates, and exact triggers for every module, plus the one-time setup
> (MySQL test config, second test account, etc.) needed to reach content the default config alone
> doesn't expose. The subsections below are the condensed version; the other file is the one to
> actually work through.

### 3.1 Core lifecycle
- [ ] Fresh install: delete `plugins/Valmora/`, start server, confirm default configs/DB generate
      cleanly with no console errors/stack traces.
- [ ] `/valmora reload` — run it 5+ times in a row while a player has GUIs open, is mid-combat, and
      has active potion/ability effects. Confirm no duplicate event firing, no doubled scoreboard
      lines, no leaked BossBars/ActionBars.
- [ ] Stop/start the whole server (not just reload) and confirm all persisted state (profiles,
      economy balances, quest/collection progress, warps, reforge state) survives intact.
- [ ] Induce a hard crash (kill -9 equivalent / force-stop) mid-session and restart; confirm no DB
      corruption (SQLite) and no player data loss beyond the last autosave interval.

### 3.2 Per-module smoke pass
Go module by module using `docs/modules/user/<module>.md` as the feature list, and for each:
- [ ] Every command listed in that doc runs without error for an op and (where relevant) is
      correctly permission-gated for a non-op.
- [ ] Every GUI listed opens, renders correctly, and closes cleanly (no lingering held server state).

Specific modules worth extra attention given their complexity:
- [ ] **`gui`/`recipe`** — craft in every machine type (`EXACT_SLOT`, `SHAPED`, `SHAPELESS`) at
      least once; try to dupe by rapid double-click / shift-click / closing the GUI mid-craft-anim.
- [ ] **`combat`** — melee, ranged (bow/crossbow), and any magic/ability damage source; verify
      damage numbers match the documented formula (`DamageFormulaRegistry`) at a few known
      stat/gear combinations; test PvP if enabled, and mob-vs-mob if relevant.
- [ ] **`alchemy`** — brew each potion type, confirm effects apply/expire/stack correctly on both
      players and (per the flagged design-doc caveat) non-player entities; confirm effects clear on
      `/valmora reload` rather than persisting stale.
- [ ] **`enchant`/`reforge`** — apply every enchant and reforge at least once; verify anvil template
      upgrades (`AnvilTemplateRegistry`) and forge costs (`ForgeCostRegistry`) match config.
- [ ] **`pet`** — spawn, level via XP, and unequip every pet; confirm pet abilities fire and pet XP
      formula matches `PetXpFormulaTest` expectations at a couple of milestone levels.
- [ ] **`quest`/`collection`/`progression`** — complete a full quest chain including any
      point/collection rewards; confirm rewards grant exactly once (no double-grant on reload or
      relog mid-quest).
- [ ] **`skill`** — grind XP on at least one skill past a level-up boundary; confirm level-up
      rewards/messages fire once and the XP curve matches `XpCurveRegistry`.
- [ ] **`zone`** — enter/exit every defined zone; confirm zone-gated resource nodes/effects apply
      only inside boundaries, including at chunk borders.
- [ ] **`time`/`calendar`** — let an in-game day cycle fully; confirm season/hour-dependent content
      (recipes, spawns, calendar events) triggers at the right boundaries, and that `time.yml`
      day-offset persists across a restart.
- [ ] **`mob`** — spawn every custom mob category at least once; confirm loot tables
      (`LootEntryTest` logic) drop the right items/rates over a reasonably large sample, and that
      abilities (ON_DAMAGE_TAKEN/ON_TELEPORT/etc. per recent commits) actually fire in-game.
- [ ] **`economy`** — earn and spend coins through every path (mob kill, quest, shop/GUI); confirm
      balances never go negative unexpectedly and coin-reward abilities (per the latest commit)
      display correctly.
- [ ] **`npc`/`warp`** — talk through every dialogue tree to a terminal node; use every warp,
      including permission-restricted ones from a non-permitted account (should be denied cleanly).
- [ ] **`notify`/`hud`/`ui`** — trigger every notify channel (chat/actionbar/title/subtitle/sound)
      and confirm the scoreboard/HUD updates live without flicker or stale values after
      stat/skill/economy changes.
- [ ] **`fishing`** — fish long enough to sample multiple loot table tiers; confirm rod/bait
      modifiers (if any) affect odds as documented.

### 3.3 Edge cases / abuse testing
- [ ] Multiple players interacting with the same GUI/machine instance simultaneously (shared
      machine block, if that's a supported pattern) — confirm no cross-player item leakage.
- [ ] Player disconnects mid-GUI-session, mid-trade, mid-craft — reconnect and confirm no item
      loss/duplication and no orphaned `GuiSession`.
- [ ] Inventory-full edge cases: complete a quest/craft/loot-drop with a full inventory — item
      should drop on the ground or the action should be blocked, never silently vanish.
- [ ] Anvil-forged display-name spoofing attempt on GUI buttons — confirm buttons are identified by
      PDC (CLAUDE.md §13), not display name, so a renamed item can't be used to fake a GUI action.
- [ ] Command injection / bad input: feed malformed args to every custom command
      (`/valmora ...`, module subcommands) — confirm graceful error messages, no stack traces to
      console or chat.
- [ ] Permission boundary sweep: as a player with **no** `valmora.*` permissions, attempt every
      admin action (reload, give, zone edit, warp create, etc.) — all must be denied.

---

## 4. Performance & load

- [ ] TPS/MSPT check with 1 player idle vs. actively using GUIs/combat/abilities — confirm no
      module's `on-update`/repeating GUI timer or per-tick listener causes measurable TPS drop.
- [ ] Simulate higher player count if possible (spare accounts, or a load-test plugin) — watch for
      per-player scheduled tasks that aren't cancelled on quit (memory/CPU leak over a long-running
      server).
- [ ] Big-loot-table / large-inventory stress: fully populate a paginated GUI (`PAGINATED`
      component) with a large backing list and confirm pagination doesn't lag or misrender.
- [ ] DB query volume under load — watch HikariCP pool metrics/logs for exhaustion or long-running
      queries during a busy combat/economy session.
- [ ] Long-soak test: leave the dev server running with a bot/player idling for several hours;
      check for memory growth (leaked listeners, uncancelled tasks, growing static maps/caches) via
      `/timings` or a profiler.

---

## 5. Persistence & migration

- [x] Confirm SQLite schema has a versioning/migration mechanism — it does: `SQLDataStore` tracks
      a `valmora_schema_version` table, currently at `LATEST_SCHEMA_VERSION = 7`, with a
      `migrateToVN` step per version bump and a "newer than this plugin supports" guard for
      downgrades. Already well covered by `SQLDataStoreTest`. No action needed.
- [ ] Back up strategy documented: is `plugins/Valmora/database.db` expected to be backed up by the
      server owner, or does the plugin need an export/backup command before v1?
- [ ] If MySQL is a supported `database.type`, do a full manual pass against a real MySQL instance
      (not just SQLite) covering profile create/read/write, economy, and quest state.
- [ ] Verify `time.yml` day-offset and any other flat-file state (not just the SQL DB) survives
      reload and restart.

---

## 6. Security & permissions

- [x] Audited `plugin.yml` permission nodes. **Finding (by design, not a bug, but worth knowing):**
      every admin command collapses to the single blanket `valmora.admin` node (only `/gui` gets
      its own `valmora.admin.gui`) — a server owner cannot grant e.g. `/eco` to a trusted helper
      without also granting `/npc`, `/zone`, `/mob`, etc. Base commands with a mix of public and
      admin subcommands (`time`, `quest`, `collections`) correctly gate the admin-only subcommands
      in code even though the top-level command itself carries no `permission:` in plugin.yml — no
      gap found there, just less granular than a per-command model would be. Not changed — this is
      a permissions-model decision, not a defect; flagging for your call on whether v1 needs finer
      nodes (e.g. `valmora.admin.eco`, `valmora.admin.zone`, ...).
- [x] Confirmed no command or event handler trusts client-supplied display names/lore for identity
      decisions — grepped `getDisplayName()`/`hasDisplayName()` across `src/main/java`: zero hits.
      GUI buttons are already identified via PDC per CLAUDE.md §13.
- [x] Confirmed economy commands validate amounts: `/eco set` rejects `< 0`, `/eco add`/`/eco
      remove` reject `<= 0`, and `remove` clamps the result to `Math.max(0, ...)` so a balance can
      never go negative. Malformed/non-numeric amounts through `CoinExpressionParser` resolve to
      `0.0` (tested in `CoinExpressionParserTest`), which then fails the same `<= 0` check rather
      than propagating a `NaN`/exception. No integer-overflow concern since balances are `double`,
      not a bounded integer type. Item-give amount validation (`/item give`) not audited — lower
      priority since it's already `valmora.admin`-gated.
- [x] Confirmed MySQL credentials — `DatabaseFactory` reads `database.mysql.password` straight from
      config into `HikariConfig.setPassword(...)`; grepped for `password`/logging around it and in
      `SQLDataStore` — no logger call ever includes it or the full JDBC URL with embedded auth
      (username/password are set separately via Hikari setters, not concatenated into the URL
      string that migration-log lines print). No file-permissions guidance for `config.yml` exists
      in the docs yet — worth a one-line addition to the ops doc but not a code fix.

---

## 7. Operational readiness

- [ ] Startup/shutdown logs are clean and informative — no stack traces on a normal `/stop`.
- [ ] `/valmora reload` failure path: intentionally break one YAML file (bad syntax) and confirm
      the reload reports a clear error for that file and doesn't leave the server in a half-enabled
      module state.
- [ ] Document minimum/recommended server specs (RAM, Java flags) given plugin footprint.
- [x] Confirmed the only real inter-plugin dependency is `packetevents` (a hard `depend`, not
      `softdepend` — already declared in `plugin.yml`). See the Vault finding below for the one
      other candidate, which turned out to be a documentation comment, not real coupling.
- [ ] **Finding: no debug-mode toggle exists in `config.yml`.** Grepped for `debug`/`verbose`
      keys — none found. Not fixed here (adding one is a small feature, not an audit fix) — worth
      deciding whether v1 needs one before shipping, given `logger.info(...)` calls throughout
      loaders/managers that would otherwise always be on in production.
- [x] Confirmed no undocumented soft-dependency: grepped for Vault/PlaceholderAPI class imports —
      the only hit is a comment in `ValmoraAPIImpl` describing a *hypothetical* future Vault
      economy bridge extensibility point, not an actual runtime coupling. No `softdepend` needed.

---

## 8. Documentation & release hygiene

- [x] Spot-checked `docs/modules/design/item.md` against the last ~10 commits (CANCEL_TRAMPLE,
      CHARGE_JUMP, ON_DAMAGE_TAKEN/ON_TELEPORT/EQUIP/UNEQUIP triggers). **Found and fixed real
      drift:** the doc said "14 concrete mechanics" (now 16 — CANCEL_TRAMPLE/CHARGE_JUMP were
      never added to the count) and its §3.10 trigger-sources table was missing all 4 of the newer
      triggers entirely. Added the 4 missing rows (sourced from `AbilityTriggerListener`/
      `CombatListener`) and corrected the mechanic count in 3 places. Other modules' docs
      (alchemy, gui, quest, calendar, reforge, zone — also touched recently) not individually
      spot-checked in this pass; still open if you want the same treatment applied there.
- [ ] `docs/VALMORA_DOCUMENTATION.md` §1–20 general architecture reviewed for drift since it's
      explicitly called out as not fully migrated. Not attempted this pass — it's explicitly the
      lowest-priority doc per CLAUDE.md's own note ("prefer the per-module docs... this file is
      kept for content not yet migrated") and a full review is a large, open-ended task better
      suited to its own pass than folding into this one.
- [x] `docs/HSB_DECOUPLING_BACKLOG.md` — 6 items still open, all architecture/generality
      preferences (de-hardcoding HSB-specific numeric tables into YAML-driven registries), not
      correctness bugs — the doc's own framing confirms this ("a reason to remove/replace", not
      "broken"). **Decision: not a v1 blocker** — ship v1 with these open and keep tracking them
      as post-v1 follow-up work in the same file.
- [x] CHANGELOG — already exists (`CHANGELOG.md`) and is well-maintained (Keep a Changelog format,
      a real `[1.0.0-beta1]` entry documenting a prior hardening pass). Added an `[Unreleased]`
      entry for this session's work.
- [x] Version — already bumped: `build.gradle` is at `1.0.0-beta1`, `plugin.yml` sources its
      version from Gradle (single source of truth per the existing changelog entry). CLAUDE.md's
      §1 table still said `0.1` — corrected.
- [x] `plugin.yml` `api-version: "1.21"` correctly covers the targeted Paper 1.21.11.
      `depend: [packetevents]` is accurate — the dependency is real and actively used
      (`module/npc/dialogue/intercept/ConversationPacketManager.java`) — but CLAUDE.md §14.1
      claimed "this project does not currently use raw packets", which was stale; corrected to
      document the real PacketEvents usage. No `softdepend` currently declared.
- [x] `README.md`, `LICENSE`, and `.github/workflows/build.yml` all already exist per the
      1.0.0-beta1 changelog entry — confirmed present, not re-audited in detail.

---

## 9. Final gate before tagging v1

- [ ] All boxes above checked or explicitly deferred with a written reason.
- [ ] `./gradlew clean build test` green on a clean checkout (not just your local incremental build).
- [ ] Fresh-install + reload + restart smoke pass done one more time immediately before tagging.
- [ ] Tag the release (`git tag v1.0.0`) and archive/delete this checklist per the note at the top.
