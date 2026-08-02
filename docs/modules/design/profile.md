# Profile Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21 | **Module ID:** `profiles`

---

## Overview

The Profile module is the **persistence backbone of Valmora**. It owns the player-session model and
the per-player save slots that nearly every other module reads and writes:

- A **session** (`ValmoraPlayer`) exists in memory for every online player. It holds all of that
  player's save slots and remembers which one is currently active.
- A **profile** (`ValmoraProfile`) is one complete "save slot": an independent set of stats, skill
  XP, tags, custom variables, collection counts, health/mana state, and inventory snapshots. Only
  one profile is active per player at a time, and switching profiles swaps all of that state.
- `PlayerManager` is the `ReloadableModule` that orchestrates the whole lifecycle: loading profiles
  from the database on join, creating a default profile for new players, applying/removing per-profile
  inventories, running the HP/mana regeneration tick, and saving everything back on quit, reload, and
  server shutdown.

The module sits near the top of the load order (after `stat`, before `economy`) because almost every
later module reaches through `PlayerManager.getSession(uuid).getActiveProfile()` to read the player's
stats, skills, tags, variables, or state.

Two **notable implementation choices**:

1. **Custom health/mana model.** Health and mana are *not* vanilla values — they live in
   `PlayerState` per profile, and `PlayerManager.syncVisualHealth()` (`PlayerManager.java:243-267`)
   projects them onto the vanilla health bar via Paper's health scaling (locked to 10 hearts).
2. **Per-profile inventories.** Each profile carries its own snapshot of the player's storage, armor,
   and offhand (plus a 45-slot accessory bag and a 27-slot quiver). Switching profiles swaps the live
   inventory in place (`PlayerManager.java:139-163`).

---

## Code Structure

All module code lives in `src/main/java/org/nakii/valmora/module/profile/`:

| File | Lines | Responsibility |
|---|---|---|
| `PlayerManager.java` | 269 | The `ReloadableModule` (`getId()` returns `"profiles"`). Session cache, join/quit orchestration, profile CRUD, per-profile inventory swap, health sync, and the regen task. |
| `PlayerConnectionListener.java` | 25 | Bukkit event handler that routes `PlayerJoinEvent` / `PlayerQuitEvent` into `PlayerManager.handleJoin` / `handleQuit`. |
| `ValmoraPlayer.java` | 43 | The online session model: a `UUID` plus an ordered `Map<UUID, ValmoraProfile>` and an `activeProfileId`. |
| `ValmoraProfile.java` | 99 | The save-slot data model. Owns `StatManager`, `SkillManager`, `CollectionManager`, `PlayerState`, `CooldownManager`, tags, variables, and the item-array fields. |
| `PlayerState.java` | 84 | Per-profile health/mana pool, combat-timer and zone-tracking fields, heal/reduce/restore/cap helpers. |
| `ProfileCommand.java` | 128 | `/profile` executor + tab completer (`create`, `delete`, `switch`, `list`, `info`, `gui`). |
| `ProfileGui.java` | 410 | The profile-management GUI: profile cards, switch-on-click, shift-click delete with a confirm dialog, create button. |
| `PlayerProfileLoadedEvent.java` | 37 | Bukkit `Event` fired on the main thread once a session is loaded into `activeSession` (after DB load + default-profile creation). |
| `event/TagAddedEvent.java` | 25 | Bukkit `Event` fired on the main thread when a tag is added to a player's active profile (from the scripting `tag` action or `TagService`). |

### Supporting types outside the module package

| Type | Location | Role in this module |
|---|---|---|
| `DataStore` | `database/DataStore.java:8-29` | The persistence interface the module talks to: `loadPlayer`, `savePlayer`, `deleteProfile`, plus the economy methods. |
| `SQLDataStore` | `database/SQLDataStore.java` | The concrete implementation (SQLite or MySQL). Owns the schema/migrations and the profile serialization. |
| `DatabaseFactory` | `database/DatabaseFactory.java:12-45` | Builds the `DataStore` from `config.yml` (`database.type` → sqlite/mysql). |
| `RegenTask` | `module/combat/RegenTask.java:12-55` | The 20-tick task started by `PlayerManager.onEnable`; regenerates HP/mana for every session's active profile. Lives in the combat package but is owned/started by this module. |
| `StatManager` | `module/stat/StatManager.java` | Per-profile effective/base stat computation (`recalculateStats`), recalculated on join, profile switch, and every stat mutation. |
| `StatModule.recalculateAttributes` | `module/stat/StatModule.java:71-99` | Maps profile stats onto vanilla attributes (health scale, movement speed, mining speed, etc.); called after join and switch. |
| `SystemStats` / `StatRegistry` | `module/stat/` | Resolves the engine's `health`/`mana` stat IDs used throughout the profile lifecycle (`SystemStats.load`, `StatModule.java:37`). |
| `SkillManager` | `module/skill/SkillManager.java` | Per-profile skill XP (`addXp`, `getLevel`, `getSaveData`/`loadData`). |
| `CollectionManager` | `module/collection/CollectionManager.java:6-30` | Per-profile collection counts (`counts`, `getCount`, `addCount`, `loadData`, `getSaveData`). |
| `CooldownManager` | `module/item/CooldownManager.java:6-28` | Per-profile item-ability cooldowns (in-memory only, **not persisted**). |
| `Valmora.java` | `:153`, `:191`, `:233-239`, `:264-276`, `:293-295` | Wiring: instantiation with `dataStore`, registration, `/profile` `/stat` `/skill` command wiring, shutdown save, API getter. |
| `ValmoraAPI` | `api/ValmoraAPI.java:21` | `getPlayerManager()` — the interface accessor every consumer uses. |

---

## Architecture & Key Classes

### Registration order and lifecycle

The module is instantiated in `Valmora.onEnable()` at `Valmora.java:153` **with the `DataStore`**
(one of only two modules constructed with the DB — the other is `EconomyModule`, `:149`):

```java
this.playerManager = new PlayerManager(this, dataStore);
```

and registered at `Valmora.java:191`:

```java
moduleManager.registerModule(playerManager);
// ... line 192: moduleManager.registerModule(economyModule); // Depends on playerManager for join/quit lifecycle
```

Load order context: `script` (188) → `time` (189) → `stat` (190) → **`player` (191)** → `economy`
(192) → `ui` (195) → … Because it loads after `stat` (which loads the `StatRegistry`/`SystemStats`
that `PlayerState` and `StatManager` read), and before everything that depends on player data, the
module is a classic early foundational dependency.

> **Naming discrepancy (known):** `AGENTS.md:131` and `docs/MODULE_DEVELOPMENT.md:499` refer to this
> module as `player`, but the actual `getId()` returns **`"profiles"`**
> (`PlayerManager.java:122-125`). The registration order position is `player` in the docs; the module
> ID is `profiles`. Any `moduleManager.getModule("profiles")` lookup works; `getModule("player")` does
> not.

### Module lifecycle (`PlayerManager`)

`onEnable()` (`PlayerManager.java:38-54`):

```java
if (regenTask != null) regenTask.cancel();
regenTask = Bukkit.getScheduler().runTaskTimer(plugin, new RegenTask(plugin), 0L, 20L); // :43

this.connectionListener = new PlayerConnectionListener(this);   // :45
plugin.getServer().getPluginManager().registerEvents(connectionListener, plugin); // :46

ProfileGui.register(plugin);                                     // :48

// Load existing players SYNCHRONOUSLY if this was a hot-reload to prevent async gap NPEs
for (Player online : Bukkit.getOnlinePlayers()) {
    handleJoin(online.getUniqueId(), true);                      // :51-53
}
```

- The regen task is the **only** module-owned scheduled task, ticked every 20 ticks (1s).
- `ProfileGui.register()` registers a **second** listener (the GUI's own static `GuiListener`).
- The online-player loop is the hot-reload safety net: on `/valmora reload` the DB reload is forced
  **synchronous** so sessions exist before any async gap can NPE.

`onDisable()` (`PlayerManager.java:103-120`):

```java
ProfileGui.unregister();                                       // :105
if (regenTask != null) { regenTask.cancel(); regenTask = null; } // :107-110
if (connectionListener != null) { HandlerList.unregisterAll(connectionListener); } // :112-114
for (ValmoraPlayer player : activeSession.values()) {
    dataStore.savePlayer(player).join();                       // :116-118 — blocking flush
}
activeSession.clear();                                         // :119
```

Everything is idempotent and reload-safe. Note that `onDisable` **blocks** on `.join()` so a
`/valmora reload` always persists current in-memory state before the DB rows are re-read by
`onEnable`. Server shutdown additionally saves in `Valmora.onDisable()` (`Valmora.java:270-274`)
and closes the data store (`:274`).

### Join / quit lifecycle

`handleJoin(UUID)` → `handleJoin(UUID, boolean sync)` (`PlayerManager.java:56-101`). The flow:

1. **Load** — `dataStore.loadPlayer(uuid)` (async via `thenAcceptAsync(processor)`, or synchronous
   `.join()` when `sync == true`).
2. **Default profile** — if the loaded session has no profiles (`:63`), a profile is created with the
   configurable default name (`profiles.default-name`, `:64`) and its health is set to max using the
   `stat` module's health stat ID (`:68-70`). A log line is emitted (`:73`).
3. **Finalize (main thread)** — the session is stored in `activeSession`, a
   `PlayerProfileLoadedEvent` fires (`:78`), and for an online player: `touchLastUsed`, the per-profile
   inventory is applied (`applyPlayerInventory`), then both `recalculateAttributes` and
   `recalculateStats` run (`:79-86`).

```java
Runnable finalize = () -> {
    activeSession.put(uuid, finalPlayer);
    new PlayerProfileLoadedEvent(uuid, finalPlayer).callEvent();
    Player bukkitPlayer = Bukkit.getPlayer(uuid);
    if (bukkitPlayer != null) {
        ValmoraProfile active = finalPlayer.getActiveProfile();
        active.touchLastUsed();
        applyPlayerInventory(bukkitPlayer, active);
        active.getStatManager().recalculateAttributes(bukkitPlayer);
        active.getStatManager().recalculateStats(bukkitPlayer);
    }
};
```

The non-sync path (`:99`) is `dataStore.loadPlayer(uuid).thenAcceptAsync(processor)` — the processor
runs on the DB executor thread, and only the `finalize` block is re-scheduled to the main thread via
`Bukkit.getScheduler().runTask` (`:92`). See [Unfinished Things](#unfinished-things--todos) for the
thread-safety nuance here.

`handleQuit(UUID)` (`PlayerManager.java:127-137`):

```java
Player player = Bukkit.getPlayer(uuid);
ValmoraPlayer vp = activeSession.get(uuid);
if (player != null && vp != null && vp.getActiveProfile() != null) {
    savePlayerInventory(player, vp.getActiveProfile());        // snapshot storage/armor/offhand
}
ValmoraPlayer stored = activeSession.remove(uuid);
if (stored != null) {
    dataStore.savePlayer(stored);                               // async, fire-and-forget
}
```

The quit save is **not joined**. Because `Valmora.onDisable()` only iterates the sessions still in
`activeSession`, a hard server stop immediately after a quit can drop the final save (see
[Possible Improvements](#possible-improvements--changes)).

### Session management

`activeSession` is a plain `HashMap<UUID, ValmoraPlayer>` (`PlayerManager.java:26`). All access is on
the main thread except the DB executor writes described above. `getSession(UUID)` (`:165-167`) returns
the session or `null` — this is the de-facto "is the player loaded?" check that consumers use before
touching `getActiveProfile()`. `isLoaded(UUID)` (`:169-171`) is a thin `containsKey` wrapper.
`getAllSessions()` (`:239-241`) is used by `Valmora.onDisable()` for the shutdown flush.

`ValmoraPlayer` (`ValmoraPlayer.java:7-43`) is deliberately small:

- `profiles`: `LinkedHashMap<UUID, ValmoraProfile>` — preserves creation order (`:9`).
- `activeProfileId`: the selected slot (`:10`).
- `addProfile` auto-promotes the first profile to active (`:16-21`); `setActiveProfile` ignores
  unknown IDs (`:27-31`); `getActiveProfile` returns `null` if `activeProfileId` no longer maps to a
  profile (`:33-35`) — which can happen if the active profile is deleted (see below).

### Profile CRUD (`PlayerManager.java:173-221`)

| Method | Lines | Behavior |
|---|---|---|
| `getMaxProfiles()` | `:173-175` | Reads `profiles.max-profiles` (default 4). |
| `pickNextProfileName(vp)` | `:177-188` | Picks a random unused name from `profiles.planet-names`; falls back to `"Profile " + (n+1)` when the pool is exhausted. |
| `createProfile(uuid, name)` | `:190-197` | Appends a new `ValmoraProfile(name)`; silently no-ops if the session is missing **or** the profile cap is reached; then async `dataStore.savePlayer(vp)`. Does **not** switch to the new profile. |
| `createNextProfile(uuid)` | `:199-203` | `createProfile` with `pickNextProfileName`. |
| `deleteProfile(uuid, profileId)` | `:205-211` | `removeProfile`, async `deleteProfile(profileId)` in the DB, then async `savePlayer(vp)`. **Does not repair `activeProfileId`** if the deleted profile was active. |
| `deleteProfile(uuid, name)` | `:214-221` | Name-based overload (case-insensitive, first match) used by `/profile delete`. |
| `switchProfile(player, name)` | `:139-148` | Name-based switch; case-insensitive first match; red error message if not found. |
| `switchProfile(player, profileId)` | `:150-163` | Saves the current profile's inventory, sets the new active profile, touches `lastUsed`, applies the new inventory, then recalculates stats and attributes. |

> **Deleting the active profile via `/profile delete <name>` is not guarded.** The GUI blocks it
> (`ProfileGui.java:335-343`), but the command path has no such check. Because `removeProfile` does
> not clear `activeProfileId` (`ValmoraPlayer.java:23-25`), the session is left with
> `getActiveProfile() == null` until it re-loads.

### Per-profile inventory

Two private helpers implement the profile ↔ inventory swap:

- `savePlayerInventory(player, profile)` (`PlayerManager.java:223-229`) — clones storage contents
  (36), armor contents (4), and the offhand (or `null` for air) into the profile.
- `applyPlayerInventory(player, profile)` (`:231-237`) — clears the live inventory and restores the
  profile's snapshot (skipping `null` parts, which is why "no snapshot yet → treated as empty").

Called from: join finalize (`:83`), profile switch (`:154`, `:159`), and quit (`:131`).

### Health / mana sync

`PlayerState` (`PlayerState.java:7-84`) holds `currentHealth` and `currentMana`. Its constructor
seeds defaults from the `StatRegistry` health/mana definitions (falling back to 100.0 if the registry
isn't loaded yet, `:13-32`). Helpers:

- `heal(amount, stats)` / `reduceHealth(amount)` — clamped to `[0, max]` via the stat manager (`:46-54`).
- `restoreMana(amount, stats)` / `reduceMana(amount)` (`:56-64`).
- `capToMax(stats)` — clamps both pools to their max stat values (`:66-72`).
- `getSaveData()` / `loadData(double[])` — the `[health, mana]` array persisted as `player_state`
  (`:74-83`).

Transient (in-memory-only) fields: `lastCombatTime` (`:10`) and `currentZoneId` (`:11`). `isInCombat()`
is a 3-second window after the last `setInCombat()` (`:38-41`). `ZoneCondition`
(`module/script/condition/ZoneCondition.java:17-19`) reads `currentZoneId`.

`syncVisualHealth(player, state, stats)` (`PlayerManager.java:243-267`):

- Computes `visualHealth = current / max * 20.0` (mapping the custom pool to 10 hearts).
- Enforces a 0.5 HP floor while alive so a player with custom health > 0 never looks dead (`:254-256`).
- Locks Paper's health scale to 20 (`:259-260`).
- Sets vanilla health to 0 when the custom pool hits 0 — deliberately triggering a real vanilla death
  event (`:262-266`), which the `stat` module then reacts to (respawn handling in
  `module/stat/PlayerListener.java`).

The `RegenTask` (`module/combat/RegenTask.java:12-55`) drives regen every second: heals while
`currentHealth < maxHealth && !isInCombat()` using the `health_regen` stat, restores mana using
`mana_regen`, and calls `syncVisualHealth` only when health actually moved.

### Commands

- **`/profile`** — `ProfileCommand` (`ProfileCommand.java:14-128`), registered at
  `Valmora.java:233-235` as both executor and tab completer. Subcommands: `gui` (opens `ProfileGui`,
  `:38-40`), `create <name>` (`:41-49`), `delete <name>` (`:50-58`), `switch <name>` (`:59-67`),
  `list` (`:68-77`), `info` (`:79-99`). Player-only (`:24-27`). No permission node. Tab completion
  offers subcommands and, for `delete`/`switch`, the session's profile names (`:110-127`).
  `/profile info` reads the active profile's `PlayerState`, the health/mana max from `SystemStats`
  and shows combat state (`:86-98`).
- **`/stat`** — `StatCommand` (registered `Valmora.java:236`) resolves the session and active profile
  via `playerManager.getSession(...)` (`StatCommand.java:37`, `:44`) before listing/adding/removing
  stats. `/stat add|remove` require `valmora.admin` (`:61`, `:84`).
- **`/skill`** — `SkillCommand` (registered `Valmora.java:239`) uses
  `playerManager.getSession(target).getActiveProfile()` for XP/level lookups and XP grants
  (`SkillCommand.java:77`, `:126`, `:169`).
- plugin.yml declares `/profile` with no permission (`plugin.yml:10-11`).

### Profile GUI

`ProfileGui` (`ProfileGui.java`) is a static utility class with its own static listener and player
tracking sets, registered/unregistered from `PlayerManager.onEnable/onDisable` (`:48`, `:105`):

- Main menu is 36 slots (`SIZE_MAIN`, `:38`), 4 profile slots at `{10,12,14,16}` (`:39`), info at 4,
  create at 28, close at 31 (`:40-42`).
- `open()` (`:85-136`) draws border panes, an info card, a `profileCard` per slot, a create button
  (LIME_DYE, or GRAY_DYE when at the profile cap), and a close button. Profile cards show
  health/mana (`:179-191`), total skill level (`:196-202`), coins via
  `EconomyModule.getTotal(uuid)` (`:205`, `:231-237`), and last-used (`:210-215`).
- Click handling (`handleMainClick`, `:301-363`): left-click a slot switches profile (blocked if it's
  already active); shift-click opens a 27-slot confirm dialog (`openConfirm`, `:140-167`) with
  ACCEPT/DENY at slots 12/14; deletion refuses the active profile and the last remaining profile
  (`:335-343`); the create button calls `pm.createNextProfile` then re-opens the menu next tick
  (`:310-323`).
- `onInventoryClose` (`:395-408`) clears the tracking sets and, after 3 ticks, clears
  `pendingDelete` if the player hasn't re-opened a menu.

---

## Configuration (YAML)

The module reads one `config.yml` section, `profiles:` (`src/main/resources/config.yml:40-63`):

| Key | Default | Type | Used at | Explanation |
|---|---|---|---|---|
| `profiles.max-profiles` | `4` | int | `PlayerManager.getMaxProfiles()` (`:173-175`), GUI cap | Maximum number of profiles a player may have. The GUI only renders 4 profile slots (`ProfileGui.java:39`), so raising this above 4 makes extra slots unreachable via the GUI even though `/profile create` would allow them. |
| `profiles.default-name` | `Earth` | string | `PlayerManager.handleJoin` (`:64`) | Name assigned to the first profile created automatically for a brand-new player. |
| `profiles.planet-names` | `[Mars, Venus, Jupiter, Saturn, Mercury, Neptune, Uranus, Pluto, Kepler-22b, Proxima b, Titan, Europa]` | string list | `PlayerManager.pickNextProfileName` (`:181-188`) | Pool of candidate names for the GUI "Create Profile" button; only unused names are offered; falls back to `"Profile N"`. |

There is **no** dedicated `profile/` resource folder and no per-profile YAML files — the profile
system is entirely database-backed.

The module also implicitly depends on config that belongs to other modules:
`database.*` (SQLite/MySQL connection, `DatabaseFactory.java:12-45`) and the `combat.*` stat-ID
mappings (`config.yml:88-101`) which `SystemStats.load` (`StatModule.java:37`) consumes and which
`PlayerState`/`syncVisualHealth` rely on for the health/mana pool.

---

## Data Model / Persistence

### Database schema (SQLite or MySQL, versioned)

`SQLDataStore` implements `DataStore` (`database/DataStore.java:8-29`). `LATEST_SCHEMA_VERSION = 2`
(`SQLDataStore.java:48`). A `valmora_schema_version` table tracks the applied version
(`ensureSchemaVersionTable`, `:74-81`; `getSchemaVersion` `:84-90`; `setSchemaVersion` `:92-101`);
`init()` (`:50-72`) compares and migrates, failing fast if the DB is *newer* than the plugin.

Migrations (`applyMigrations`, `:104-115`):

- **v1** (`migrateToV1`, `:123-161`):
  - `valmora_players (uuid VARCHAR(36) PRIMARY KEY, active_profile VARCHAR(36))`
  - `valmora_profiles (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36), name VARCHAR(255), stats TEXT, skills TEXT, player_state TEXT, tags TEXT, variables TEXT, collections TEXT, inventory TEXT)`
  - `valmora_economy (uuid VARCHAR(36) PRIMARY KEY, purse DOUBLE NOT NULL DEFAULT 0, bank DOUBLE NOT NULL DEFAULT 0)`
  - Plus idempotent `addColumnIfMissing` upgrades for `tags`, `variables`, `collections`,
    `inventory`, `created_at`, `last_used` (`:147-152`) so pre-versioning databases migrate in place.
- **v2** (`migrateToV2`, `:117-120`): adds `quiver TEXT` to `valmora_profiles`.

`addColumnIfMissing` (`:164-171`) swallows "already exists" errors so re-runs are safe.

### Profile serialization

`savePlayer` (`SQLDataStore.java:267-338`) runs on the 4-thread DB executor (`dbExecutor`, `:34`):

1. Opens one connection, `setAutoCommit(false)`.
2. Upserts `valmora_players` with the active profile ID (`:273-283`).
3. Batches an upsert for **every** profile in the session (`:290-331`), serializing each field with
   Gson: `stats` (`Map<String,Double>`), `skills`, `player_state` (`double[]`), `tags` (`Set<String>`),
   `variables` (`Map<String,Object>`), `collections` (`Map<String,Long>`), `inventory` (base64 JSON),
   `quiver` (base64 JSON), plus `created_at` (insert-only, preserves order) and `last_used`
   (updated every save).
4. `conn.commit()`.

`loadPlayer` (`:174-264`) is the mirror: reads `active_profile`, then all profiles ordered by
`created_at ASC, id ASC` (`:188-191`), and hydrates a fresh `ValmoraProfile` per row (Gson
deserialization for each column; item arrays via base64). The active profile ID is applied last
(`:254-256`). Returns `null` for unknown players (`:182`), which `PlayerManager` treats as a
new-player session.

`deleteProfile(UUID)` (`:341-351`): `DELETE FROM valmora_profiles WHERE id = ?`.

Item serialization helpers:

- `serializeInventory` / `deserializeInventory` (`:353-395`) — the 41-slot composite layout
  (slots 0-35 storage, 36-39 armor, 40 offhand) as a base64 JSON string array.
- `serializeItemArray` / `deserializeItemArray` (`:399-418`) — generic fixed-size `ItemStack[]`
  ↔ base64 JSON array, currently used only for the quiver.
- `encodeItem` / `decodeItem` (`:420-435`) — `ItemStack.serializeAsBytes()` / `deserializeBytes()`
  over Base64; air/null encode as `null`.

### Async save pattern & thread safety

- **DB reads/writes** always run on `dbExecutor` (`Executors.newFixedThreadPool(4)`,
  `SQLDataStore.java:34`), returning `CompletableFuture`s.
- **Join**: the DB load is async; the session-insert + inventory/stat application is scheduled back to
  the main thread (`PlayerManager.java:89-93`).
- **Quit**: `dataStore.savePlayer(stored)` is fire-and-forget (`:135`).
- **Disable/shutdown**: `onDisable` (`PlayerManager.java:116-118`) and `Valmora.onDisable()`
  (`Valmora.java:270-274`) both `.join()` the saves synchronously so nothing is lost on clean
  shutdown/reload, then `dataStore.close()` (`:274`) drains the executor (`SQLDataStore.java:507-521`).
- **SQLite WAL** is enabled in `DatabaseFactory` (`:41`) so the infrequent writer doesn't block readers.

> **Threading caveat:** in the async join path, the `processor` lambda
> (`PlayerManager.java:61-94`) runs on the DB executor thread (via `thenAcceptAsync`). It reads
> `plugin.getConfig()`, `plugin.getStatModule().getSystemStats()`, and constructs a `PlayerState`
> (which touches `ValmoraAPI.getInstance().getStatRegistry()`) off the main thread
> (`PlayerManager.java:64-72`). Only the `finalize` Runnable is guaranteed main-thread. This works in
> practice because the executor is fixed at 4 threads and those reads are effectively read-only, but
> it is not a strict application of the AGENTS §7.4 rule.

---

## API Exposed

### Via `ValmoraAPI`

```java
org.nakii.valmora.module.profile.PlayerManager getPlayerManager(); // ValmoraAPI.java:21
```
implemented at `Valmora.java:293-295`. This is the canonical entry point for every consumer.

### Public methods on `PlayerManager`

| Method | Signature | Location | Purpose |
|---|---|---|---|
| `handleJoin` | `void handleJoin(UUID)` / `void handleJoin(UUID, boolean sync)` | `:56-58`, `:60` | Load + activate a player session; `sync=true` blocks on the DB. |
| `handleQuit` | `void handleQuit(UUID)` | `:127` | Snapshot inventory, remove session, async save. |
| `switchProfile` | `void switchProfile(Player, String)` / `void switchProfile(Player, UUID)` | `:139`, `:150` | Swap the active profile and live inventory. |
| `getSession` | `ValmoraPlayer getSession(UUID)` | `:165` | The loaded session, or `null`. |
| `isLoaded` | `boolean isLoaded(UUID)` | `:169` | Whether a session exists. |
| `getMaxProfiles` | `int getMaxProfiles()` | `:173` | Config `profiles.max-profiles`. |
| `pickNextProfileName` | `String pickNextProfileName(ValmoraPlayer)` | `:177` | Random unused planet name. |
| `createProfile` | `void createProfile(UUID, String)` | `:190` | Add a new profile (silent cap/session no-ops). |
| `createNextProfile` | `void createNextProfile(UUID)` | `:199` | Create with the next random name. |
| `deleteProfile` | `void deleteProfile(UUID, UUID)` / `void deleteProfile(UUID, String)` | `:205`, `:214` | Remove a profile in memory + DB. |
| `getAllSessions` | `Collection<ValmoraPlayer> getAllSessions()` | `:239` | All live sessions (shutdown flush). |
| `syncVisualHealth` | `void syncVisualHealth(Player, PlayerState, StatManager)` | `:243` | Map custom health pool to the 10-heart bar. |

### Public data models

`ValmoraPlayer` (`ValmoraPlayer.java`): `addProfile` (`:16`), `removeProfile` (`:23`),
`setActiveProfile` (`:27`), `getActiveProfile` (`:33`), `getProfiles` (`:37`), `getUuid` (`:40`).

`ValmoraProfile` (`ValmoraProfile.java`): identity — `getId` (`:52`), `getName` (`:53`),
`getCreatedAt` (`:54`), `getLastUsed` (`:55`), `touchLastUsed` (`:56`); embedded managers —
`getStatManager` (`:58`), `getSkillManager` (`:62`), `getCollectionManager` (`:66`),
`getPlayerState` (`:70`), `getCooldownManager` (`:74`); free-form data — `getTags` (`:78`),
`getVariables` (`:82`); item stores — `getSavedInventory`/`getSavedArmor`/`getSavedOffhand` +
setters (`:86-92`), `getAccessoryItems`/`setAccessoryItems` (`:94-95`),
`getQuiverItems`/`setQuiverItems` (`:97-98`).

### Events

| Event | Location | Fired by | Consumed by |
|---|---|---|---|
| `PlayerProfileLoadedEvent` | `PlayerProfileLoadedEvent.java:9-37` | `PlayerManager.java:78` (main thread, after session insert) | No in-repo listener today (external hook point). |
| `TagAddedEvent` | `event/TagAddedEvent.java:8-25` | `TagServiceImpl.java:36` and `TagEvent.java:44` (scripting `tag` action, main thread) | `QuestListener.onTagAdded` → triggers `TAG` quest objectives (`QuestListener.java:445-448`). |

---

## Dependencies & Consumers

### What this module depends on

| Dependency | How it is used |
|---|---|
| `dataStore` (`DataStore`/`SQLDataStore`) | All persistence; injected in the constructor (`Valmora.java:153`). |
| `stat` module (`StatManager`, `StatRegistry`, `SystemStats`, `StatModule.recalculateAttributes`) | Default-profile health seeding (`PlayerManager.java:68-70`), attribute/stat recalculation on join/switch (`:84-85`, `:160-161`), `PlayerState` defaults (`PlayerState.java:13-32`). Drives the "loads after stat" ordering (`Valmora.java:190` vs `:191`). |
| `combat.RegenTask` | Owned and started by this module for HP/mana regen (`PlayerManager.java:43`); it is the cross-module link into combat's package. |
| `economy` (indirect) | `ProfileGui` displays per-player coins via `getEconomyModule().getTotal(uuid)` (`ProfileGui.java:205`, `:231-237`). The economy module is registered immediately after this one because of the join/quit lifecycle (`Valmora.java:192`). |
| `config.yml` | `profiles.*`, `database.*`, `combat.*` keys read throughout. |
| `util.Formatter` | MiniMessage rendering in `ProfileCommand` and `ProfileGui`. |

### What uses this module

Grep across `src/main/java` for `getPlayerManager().getSession(...)` /
`getActiveProfile()` shows the profile system is consumed by nearly every gameplay module:

| Consumer | Location | Use |
|---|---|---|
| **combat** | `module/combat/DamageCalculator.java:31,58,173`, `DamageApplier.java:23-28`, `RegenTask.java:27-51` | Reads attacker/victim active-profile `StatManager` for damage/defense math; regen on the active profile's `PlayerState`. |
| **stat** | `module/stat/StatManager.java:144-155,183-192`, `PlayerListener.java:41-45,66-71`, `StatCommand.java:37-50` | `recalculateStats` reads `getAccessoryItems`, caps health/mana via `capToMax`, and re-syncs visual health — the profile is where effective stats live. |
| **skill** | `module/skill/SkillListener.java:26-28`, `SkillCommand.java:77,126,169` | Routes XP gains to `profile.getSkillManager().addXp(...)`. |
| **item / abilities** | `module/item/AbilityExecutor.java:37`, `impl/ModifyStatMechanic.java:33`, `impl/HealMechanic.java:47` | Abilities operate on the caster/target's active profile (stat modifiers, heals, cooldowns). |
| **mob** | `module/mob/MobDeathListener.java:55-68` | On kill: reads luck, adds combat XP to `profile.getSkillManager()`, pays gold into the (shared) economy. |
| **alchemy** | `module/alchemy/AlchemyManager.java:160-162`, `brewing/AlchemyMachineHandler.java:284-286`, `effect/hardcoded/{Damage,Healing,Poison}AlchemyEffect.java` | Applies brew effects to / reads state from the active profile. |
| **resource** | `module/resource/ResourceListener.java:43-45`, `ResourceManager.java:115-117,143-145` | Profile-scoped resource stats/gains (e.g. mining fortune). |
| **quest** | `module/quest/QuestManager.java:318-319`, `QuestListener.java:481-505`, `QuestVariableProvider.java:33-35`, `quest/board/QuestBoardManager.java:105-106`, `QuestBoardVariableProvider.java:35-37`, `journal/JournalManager.java:33-35`, `objective/NpcRangeObjectiveHandler.java:59-61`, `points/PointsManager.java:39-40` | Quest status/objectives, tags, variables, journals, and points are all per-profile. `QuestListener` also listens for `TagAddedEvent` (`:445-448`). |
| **script** | `module/script/tag/TagServiceImpl.java:48-53`, `variable/providers/PlayerVariableProvider.java:40-44`, `module/gui/GuiVariableProvider.java:96-98`, `condition/{Tag,QuestStatus,ObjectiveActive,Zone}Condition.java`, `event/impl/{Tag,Variable,StatModifyEventFactory,GiveXpEventFactory}.java` | `$player.*$` variables, `tag`/`variable`/`stat_modify`/`give_xp` script actions, and most conditions resolve against the active profile. |
| **ui** | `module/ui/ActionBarUI.java:72-83`, `ScoreboardUI.java:196-197` | Shows live HP/mana and the active profile name on the HUD/scoreboard. |
| **warp** | `module/warp/WarpManager.java:31-33` | Reads profile variables for warp unlocks/behaviour. |
| **collection** | `module/collection/CollectionListener.java:34-36`, `CollectionVariableProvider.java:40-42` | Collection counts are stored per profile (`profile.getCollectionManager()`). |
| **slayer** | `module/slayer/SlayerListener.java:147-148`, `SlayerStartEventFactory.java:49-51` | Slayer progress and state on the active profile. |
| **pet** | `module/pet/PetModule.java:214-216` | Recalculates stats on summon/remove for the active profile. |
| **progression** | `module/progression/ProgressionManager.java:200-201`, `ProgressionModule.java:61-63` | Skill-point trees grant profile stats. |
| **accessory** | `module/accessory/AccessoryModule.java:76-85` | Bag open/save reads/writes `profile.getAccessoryItems()`. |
| **quiver** | `module/quiver/QuiverModule.java:125-126` | Ammo storage on `profile.getQuiverItems()`. |
| **gui** | `module/gui/GuiVariableProvider.java:96-98`, `gui/event/GiveXpEventFactory.java:33-35` | GUI scripts read profile variables / grant XP. |
| **zone** | `module/script/condition/ZoneCondition.java:17-19` | Reads `PlayerState.getCurrentZoneId()`. |

This list is exactly why the module is registered so early (`Valmora.java:191`) and why the
`PlayerProfileLoadedEvent` exists — the moment a session lands in `activeSession`, everything else can
safely read its active profile.

---

## Unfinished Things / TODOs

1. **`docs/todo.md:11` — "profile: add inventory changing between profiles" is now implemented.**
   `savePlayerInventory`/`applyPlayerInventory` (`PlayerManager.java:223-237`) and the switch flow
   (`:139-163`) deliver exactly this. The todo is stale. `todo.md:76` ("profile management") is also
   done via `/profile` + `ProfileGui`.

2. **Deleting the active profile via `/profile delete <name>` corrupts the session.**
   `ProfileCommand`'s `delete` case (`ProfileCommand.java:50-58`) has no active-profile guard (unlike
   the GUI, `ProfileGui.java:335-343`). `removeProfile` (`ValmoraPlayer.java:23-25`) leaves
   `activeProfileId` dangling, so `getActiveProfile()` returns `null` until the player re-joins or a
   reload re-hydrates the session.

3. **`createProfile` silently no-ops with no feedback.** When the cap is reached or the session is
   missing, `createProfile` just returns (`PlayerManager.java:192-193`), yet the command always
   replies "Profile '…' created." (`ProfileCommand.java:47-48`). The GUI does surface the cap
   (`ProfileGui.java:117-127`, `:313-318`).

4. **Active-profile ID is only persisted on quit/disable.** `switchProfile` updates in-memory state
   but never calls `dataStore.savePlayer` (`PlayerManager.java:150-163`); the new active ID reaches
   `valmora_players` only on the next quit/reload/shutdown save (`SQLDataStore.java:273-283`). A
   crash after a switch reverts the active profile.

5. **Quit save is fire-and-forget.** `handleQuit` calls `dataStore.savePlayer(stored)` without
   joining (`PlayerManager.java:135`), and `Valmora.onDisable` only flushes sessions still in
   `activeSession` (`Valmora.java:270-274`). A hard stop immediately after a quit can drop that
   player's final save.

6. **Thread-safety of the async join processor.** The `processor` lambda
   (`PlayerManager.java:61-94`) runs on the DB executor thread and reads plugin config and the stat
   registry there; only the `finalize` block is main-thread (`:92`). Fragile against any future
   config-access change.

7. **Module-ID / doc mismatch.** Docs (`AGENTS.md:131`, `MODULE_DEVELOPMENT.md:499`) call this module
   `player`; the code returns `"profiles"` (`PlayerManager.java:124`).

8. **`CooldownManager`, combat timer, and zone are transient.** Per-profile cooldowns
   (`ValmoraProfile.java:23`), `lastCombatTime`, and `currentZoneId` (`PlayerState.java:10-11`) are
   never serialized; a reload resets them all.

9. **No dedicated unit tests for the module.** The schema/migration behaviour is covered by
   `SQLDataStoreTest.java` (`src/test/java/org/nakii/valmora/database/SQLDataStoreTest.java` —
   `initCreatesSchemaAndStampsVersion`, `migratesPreVersioningDatabase`,
   `migratesV1DatabaseToAddQuiverColumn`), but there are no tests for `ValmoraPlayer`,
   `ValmoraProfile`, `PlayerState`, or `PlayerManager`.

10. **No profile name uniqueness enforcement.** `getProfiles()` is keyed by `UUID`
    (`ValmoraPlayer.java:9`), so duplicate names are possible; the name-based `switchProfile`/`delete`
    pick the first case-insensitive match (`PlayerManager.java:141-145`, `:217-220`).

11. **GUI slot count is hardcoded to 4.** `PROFILE_SLOTS = {10,12,14,16}` (`ProfileGui.java:39`) — the
    config comment calls this the "visual cap" (`config.yml:44`). Raising `profiles.max-profiles`
    above 4 yields profiles unreachable from the GUI.

---

## Possible Improvements / Changes

All suggestions are grounded in the code referenced above.

1. **Guard `/profile delete`.** Reuse the GUI's checks (`ProfileGui.java:335-343`) in the command path
   (`ProfileCommand.java:50-58`): refuse deleting the active profile or the last remaining profile,
   and if the active profile is ever removed, repoint `activeProfileId` to the oldest remaining
   profile (`ValmoraPlayer.removeProfile`, `:23-25`).

2. **Persist the active profile immediately on switch.** Call `dataStore.savePlayer(vp)` inside
   `switchProfile` (`PlayerManager.java:150-163`) or add a lightweight `saveActiveProfile` to
   `DataStore` so a crash never reverts the player's selection.

3. **Join the quit save.** `handleQuit` (`PlayerManager.java:127-137`) could schedule a deferred
   `.join()` on shutdown, or `Valmora.onDisable` could track recently-quit UUIDs and flush them —
   eliminating the quit-then-crash data-loss window (item 5 above).

4. **Move the whole join processor onto the main thread.** Do the DB load async but run *all* of the
   processor (including default-profile creation) inside `runTask` (`PlayerManager.java:61-101`), so
   no config/registry access ever happens off the main thread.

5. **Align the module ID with the docs.** Either change `getId()` to `"player"` (`PlayerManager.java:124`)
   or update `AGENTS.md`/`MODULE_DEVELOPMENT.md` to say `profiles` — today they disagree.

6. **Persist cooldowns and zone/combat flags** by folding them into `player_state` (e.g. a
   versioned JSON object instead of the bare `double[]`, `PlayerState.java:74-83`) or by adding a
   dedicated column — currently `/valmora reload` resets every cooldown.

7. **Feedback + name validation on create.** Return a result from `createProfile`
   (`PlayerManager.java:190-197`) so `ProfileCommand` can report "at cap" / "duplicate name" instead
   of always claiming success (`ProfileCommand.java:47-48`), and enforce name uniqueness.

8. **Make the GUI slot count derive from `max-profiles`.** Render `min(maxProfiles, 4)` profile cards
   (or paginate) in `ProfileGui.open` (`:85-136`) so raising `profiles.max-profiles` doesn't strand
   profiles.

9. **Add module unit tests.** `ValmoraPlayer`/`ValmoraProfile`/`PlayerState` are plain POJOs and can
   be tested without a server (like `SkillManagerXpTest`, `StatDefinitionTest`); `PlayerState` needs
   `ValmoraAPI.setProvider` mocked per AGENTS §9.

10. **Consider a batched quit-save flush** mirroring the economy module's `saveEconomyBatch`
    (`SQLDataStore.java:475-504`) so large player counts don't pay one connection/transaction per
    quitting player.
