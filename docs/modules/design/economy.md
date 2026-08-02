# Economy Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `economy` | **Source:** `src/main/java/org/nakii/valmora/module/economy/`
> **Interface:** `org.nakii.valmora.api.economy.EconomyService`

---

## Table of Contents

1. [Overview](#overview)
2. [Code Structure](#code-structure)
3. [Architecture & Key Classes](#architecture--key-classes)
4. [Configuration (YAML)](#configuration-yaml)
5. [Data Model / Persistence](#data-model--persistence)
6. [API Exposed](#api-exposed)
7. [Dependencies & Consumers](#dependencies--consumers)
8. [Unfinished Things / TODOs](#unfinished-things--todos)
9. [Possible Improvements / Changes](#possible-improvements--changes)
10. [Tests](#tests)

---

## Overview

The Economy module provides **two balances per player** — a **purse** (spendable wallet) and a **bank** (storage), plus the script/GUI wiring to move money between them. It is the live implementation of the `EconomyService` interface (`EconomyService.java:5-9`); the old `NoOpEconomyService` stub has been deleted (see `docs/UNFINISHED_FEATURES.md` §1).

The backend is designed around three properties:

- **In-memory authoritative.** Every balance read/mutation is an O(1) operation against a `ConcurrentHashMap<UUID, EconomyData>` (`EconomyModule.java:39`). No transaction ever touches the database, so per-transaction cost is bounded by map/lock operations in memory, not by disk or network I/O (`EconomyModule.java:23-33`). This is what lets thousands of transactions/sec across up to ~10k cached players stay cheap.
- **Per-player atomicity, not a global lock.** `EconomyData`'s compound operations (add/remove/deposit/withdraw) are `synchronized` on the instance (`EconomyData.java:25-68`). Different players own different instances and never contend; same-player concurrent transactions serialize correctly instead of losing updates. Verified by `EconomyDataTest.addPurseNeverLosesUpdatesUnderConcurrentAccess` (`EconomyDataTest.java:20-51`).
- **Write-behind persistence.** Mutations mark the player's UUID dirty in a `ConcurrentHashMap.newKeySet()` (`EconomyModule.java:40`). A background task flushes every dirty balance in a single **batched transaction** (`saveEconomyBatch` — one connection, `executeBatch`) on a configurable interval (`economy.autosave-interval-seconds`, default 60s), again on quit, and once more as a full-cache flush on shutdown/reload. Never one DB round-trip per transaction, and never (at 10k cached players) one blocking round-trip per player.

The module is implemented entirely by `EconomyModule` (which implements both `ReloadableModule` and `EconomyService`, `EconomyModule.java:35`) — there is no separate service class. Supporting classes cover the `/eco` admin command, the `$economy.*$` script variables, the five economy script events, the coin-expression parser, and the join/quit/death listener.

A flat **death penalty** exists: on player death, half the purse is removed (`EconomyListener.java:28-35`). This is hardcoded — not configurable.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/economy/
├── EconomyModule.java              # ReloadableModule + EconomyService — cache, dirty-set, flush, ops
├── EconomyData.java                # Per-player purse/bank value object with instance-synchronized ops
├── EconomyListener.java            # PlayerJoinEvent / PlayerQuitEvent / PlayerDeathEvent wiring
├── EcoCommand.java                 # /eco admin command (get|set|add|remove) — TabExecutor
├── CoinExpressionParser.java       # k/m/b suffix + arithmetic expression parser for amounts
├── EconomyVariableProvider.java    # Registers the "$economy.*$" script variable namespace
└── event/
    ├── EconomyAddEventFactory.java         # "economy_add <amount>" script event
    ├── EconomyRemoveEventFactory.java      # "economy_remove <amount>" script event
    ├── EconomyDepositEventFactory.java     # "economy_deposit <amount|all|half>" script event
    ├── EconomyWithdrawEventFactory.java    # "economy_withdraw <amount|all|half|X%>" script event
    └── EconomyDepositAllEventFactory.java  # "economy_deposit_all" script event

Related (outside the module package):
├── api/economy/EconomyService.java         # The public 4-method interface
├── api/ValmoraAPI.java                     # getEconomy() / getEconomyModule() accessors
├── database/DataStore.java                 # loadEconomy / saveEconomy / saveEconomyBatch contract
├── database/SQLDataStore.java              # valmora_economy table, batch upsert implementation
├── database/DatabaseFactory.java           # SQLite WAL mode, MySQL/SQLite pool setup
└── src/main/resources/guis/bank.yml        # Bundled Bank of Valmora GUI (3 screens)
```

There is **no `XRegistry.java` or `XLoader.java`** in this module — the module has no content-definition YAML folder of its own (nothing to register/load). Its only config input is the `economy:` section of `config.yml`.

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `EconomyModule.java`

Implements `ReloadableModule` (see `docs/MODULE_DEVELOPMENT.md` §2) **and** `EconomyService`.

| Method | Behavior | Lines |
|---|---|---|
| `onEnable()` | Registers `EconomyListener`; registers the `EconomyVariableProvider` + the five event factories with `plugin.getScriptModule()`; pre-loads economy rows for already-online players (handles hot-reload); reads `economy.autosave-interval-seconds` (default 60) and starts the async periodic `flushDirty` timer | `EconomyModule.java:52-72` |
| `onDisable()` | Unregisters the listener; cancels the flush task; performs a **final full flush** — one `saveEconomyBatch` snapshotting the *entire* cache — then clears cache and dirty set | `EconomyModule.java:75-95` |
| `getId()` | `"economy"` | `EconomyModule.java:98` |
| `getName()` | `"Economy"` | `EconomyModule.java:101` |

Constructor only stores the plugin + `DataStore` references (`EconomyModule.java:46-49`) — all state is initialized in `onEnable()`, satisfying the idempotence requirement for hot-reload.

**Enable-time online-player preload** (`EconomyModule.java:63-67`): because a reload never kicks players, no `PlayerJoinEvent` fires for them; the module therefore re-reads the DB for everyone already online and populates the cache synchronously (`.join()` on the async load). This is the only blocking DB call in the module's hot path and is bounded by the online-player count.

**Shutdown flush** (`EconomyModule.java:85-95`): on reload/shutdown the module snapshots *every* cached player (even non-dirty ones, to catch in-flight-but-not-yet-dirty mutations) and persists them in a single batched transaction, rather than one blocking round-trip per player.

### 3.2 Per-Player Atomicity — `EconomyData.java`

A final value-object holding `purse` and `bank` (`EconomyData.java:9-11`). The constructor clamps both to `>= 0` (`EconomyData.java:13-16`), and **every mutator clamps at zero** so a balance can never go negative (`EconomyData.java:22-29`).

| Operation | Semantics | Lines |
|---|---|---|
| `getPurse()` / `getBank()` / `getTotal()` | Synchronized reads; `getTotal()` = purse + bank | `EconomyData.java:18-20` |
| `setPurse(v)` / `setBank(v)` | Clamped write (admin path) | `EconomyData.java:22-23` |
| `addPurse` / `removePurse` | Clamped add/subtract | `EconomyData.java:25-26` |
| `addBank` / `removeBank` | Clamped add/subtract | `EconomyData.java:28-29` |
| `hasPurse` / `hasBank` | Sufficiency check | `EconomyData.java:31-32` |
| `deposit(amount)` | Atomic purse→bank move. Returns `false` (no-op) if `amount <= 0` or purse short | `EconomyData.java:35-40` |
| `withdraw(amount)` | Atomic bank→purse move. Returns `false` (no-op) if `amount <= 0` or bank short | `EconomyData.java:43-48` |
| `depositAll()` | Moves entire purse to bank; returns amount moved (0 if purse empty) | `EconomyData.java:51-58` |
| `withdrawAll()` | Moves entire bank to purse; returns amount moved (0 if bank empty) | `EconomyData.java:61-68` |
| `snapshot()` | Consistent point-in-time `double[]{purse, bank}` for persistence | `EconomyData.java:71-73` |

All of these are `synchronized` on the instance — the standard per-key lock shape at scale (class Javadoc, `EconomyData.java:3-8`).

### 3.3 The Cache and the Dirty Set

`EconomyModule` holds two collections:

```java
private final Map<UUID, EconomyData> cache = new ConcurrentHashMap<>();
private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
```
(`EconomyModule.java:39-40`)

- `cache` maps UUID → current authoritative balance object. `getOrCreateData` uses `cache.computeIfAbsent(uuid, k -> new EconomyData(0, 0))` so concurrent first-touch is safe (`EconomyModule.java:240-246`).
- `dirty` is a concurrency-safe set of UUIDs whose balances need persisting. Reads (`getPurse`/`getBank`/`getTotal`) use `cache.getOrDefault(uuid, EMPTY)` where `EMPTY` is a shared static zero-balance instance (`EconomyModule.java:44`, `EconomyModule.java:148-150`).
- Every mutation path adds the UUID to `dirty` **only when the mutation actually changed something** (`EconomyModule.java:154-212`).

### 3.4 Batched Write-Behind Flush — `flushDirty()`

```java
private void flushDirty() {
    if (dirty.isEmpty()) return;
    Set<UUID> keys = new HashSet<>(dirty);
    dirty.removeAll(keys);
    Map<UUID, double[]> snapshot = new HashMap<>();
    for (UUID id : keys) {
        EconomyData data = cache.get(id);
        if (data != null) snapshot.put(id, data.snapshot());
    }
    if (!snapshot.isEmpty()) dataStore.saveEconomyBatch(snapshot);
}
```
(`EconomyModule.java:130-144`)

The **snapshot-and-clear-first** ordering is deliberate: any mutation that races with the flush will re-add its UUID to the dirty set and simply be picked up on the next flush, rather than being silently dropped (`EconomyModule.java:132-136` comment). The snapshot map is built from per-instance `snapshot()` calls so each player's row is a consistent point-in-time copy.

The task runs on the Bukkit **async** scheduler (`runTaskTimerAsynchronously`, `EconomyModule.java:70-71`) and calls `saveEconomyBatch` (itself a fire-and-forget async `CompletableFuture`). No Bukkit API is touched inside the flush path, so this is thread-safe per `AGENTS.md` §7.4.

### 3.5 Join / Quit Lifecycle — `EconomyListener` + `handleJoin` / `handleQuit`

`EconomyListener` is registered in `onEnable()` and unregistered in `onDisable()` (`EconomyModule.java:53-54`, `EconomyModule.java:76-79`), per `AGENTS.md` §6.2.

| Event | Handler | Behavior | Lines |
|---|---|---|---|
| `PlayerJoinEvent` | `handleJoin(uuid)` | If the UUID is already cached (quick rejoin this session) skip the DB read entirely. Otherwise load async, then `cache.putIfAbsent` back on the main thread | `EconomyListener.java:19-21`, `EconomyModule.java:105-113` |
| `PlayerQuitEvent` | `handleQuit(uuid)` | **Deliberately does NOT evict from cache** (keeps a couple of doubles per player, avoids a cache-miss race on rejoin). Immediately persists the current balances (`saveEconomy`) and removes the player from the dirty set | `EconomyListener.java:24-26`, `EconomyModule.java:115-126` |
| `PlayerDeathEvent` (MONITOR, `ignoreCancelled`) | inline | Removes `purse / 2.0` from the dead player's purse | `EconomyListener.java:28-35` |

### 3.6 The `/eco` Admin Command — `EcoCommand.java`

`/eco <get|set|add|remove> <player> [purse|bank] [amount]` (`EcoCommand.java:13-18`), implementing `TabExecutor`.

| Aspect | Detail | Lines |
|---|---|---|
| Permission | `valmora.admin` — checked in `onCommand` and in `onTabComplete` | `EcoCommand.java:21`, `EcoCommand.java:33`, `EcoCommand.java:113` |
| Target resolution | `Bukkit.getPlayerExact(args[1])` — **only online players**, rejects offline with a message | `EcoCommand.java:43-47` |
| `get` | Shows purse, bank, or both (default). Third arg `purse`/`bank` | `EcoCommand.java:51-66` |
| `set` | Clamps amount to `>= 0`; writes purse or bank | `EcoCommand.java:67-79` |
| `add` | Requires amount `> 0`; adds to purse or bank | `EcoCommand.java:80-92` |
| `remove` | Requires amount `> 0`; subtracts from purse or bank | `EcoCommand.java:93-105` |
| Amount parsing | `CoinExpressionParser.parse` — supports `2.5k`, `1m`, `1b`, arithmetic | `EcoCommand.java:134-136` |
| Display formatting | Compact `k/m/b` suffixes via private `fmt` | `EcoCommand.java:138-144` |

Tab completion suggests subcommands, online player names, `purse`/`bank`, and amount presets (`1000`/`1k`/`10k`/`100k`/`1m`) (`EcoCommand.java:112-132`). **Note:** the command is wired in `Valmora.java` with only `setExecutor(...)` — `setTabCompleter(...)` is never called, so the implemented `onTabComplete` is currently dead code (see [Unfinished Things / TODOs](#unfinished-things--todos)).

### 3.7 `CoinExpressionParser` — `CoinExpressionParser.java`

A tiny recursive-descent parser for coin expressions (`"2.5k"`, `"1m+500k"`, `"3k-1"`, `"(1k+500)*2"`) supporting `k/m/b` suffixes and `+ - * /` with parentheses (`CoinExpressionParser.java:3-8`). It **never throws** — any parse error returns `0.0` (`CoinExpressionParser.java:10-17`). Grammar: `expr → term (('+'|'-') term)*`, `term → factor (('*'|'/') factor)*`, `factor → ['-'] NUMBER [SUFFIX] | '(' expr ')'` (`CoinExpressionParser.java:27-85`). Division by zero yields `0` (`CoinExpressionParser.java:45`). Used by `/eco` and by the `economy_add`/`economy_remove`/`economy_deposit`/`economy_withdraw` script events.

### 3.8 Script Variable Provider — `EconomyVariableProvider.java`

Registered in `onEnable()` via `plugin.getScriptModule().registerProvider(...)` (`EconomyModule.java:56`). Namespace: **`economy`** (`EconomyVariableProvider.java:19-21`).

| Variable | Value | Lines |
|---|---|---|
| `$economy.purse$` | raw purse double | `EconomyVariableProvider.java:32` |
| `$economy.purse.formatted$` | `formatCoinsDisplay` output (e.g. `🪙 1.000.000`) | `EconomyVariableProvider.java:30-31` |
| `$economy.bank$` | raw bank double | `EconomyVariableProvider.java:34` |
| `$economy.total$` | purse + bank | `EconomyVariableProvider.java:35` |

Resolution requires a player caster (`context.getPlayerCaster()`); otherwise returns `null` (`EconomyVariableProvider.java:25-27`). Used by the bundled scoreboard (`ui.yml:23` — `$economy.purse.formatted$`) and the bank GUI (`guis/bank.yml:37,58,133-134,151-152,169,222-223,240-241,258,275`).

### 3.9 Script Events — `event/` subpackage

All five factories implement `EventFactory`, are registered in `onEnable()` (`EconomyModule.java:57-61`), and operate on the **caster** player (`context.getPlayerCaster()`). Amount arguments are resolved through the variable resolver first (so `$var$` tokens work), then parsed by `CoinExpressionParser`; a resolved amount `<= 0` is a silent no-op.

| Event | Args | Behavior | Lines |
|---|---|---|---|
| `economy_add` | `<amount>` | `addPurse(uuid, amount)` when `amount > 0` | `EconomyAddEventFactory.java:20`, `EconomyAddEventFactory.java:26-29` |
| `economy_remove` | `<amount>` | `removePurse(uuid, amount)` when `amount > 0` | `EconomyRemoveEventFactory.java:20`, `EconomyRemoveEventFactory.java:26-29` |
| `economy_deposit` | `<amount\|all\|half>` | `all` → `depositAll()`; `half` → `deposit(floor(purse/2))`; otherwise `deposit(amount)`. Sends `[Bank]` success/insufficient-funds messages | `EconomyDepositEventFactory.java:26`, `EconomyDepositEventFactory.java:38-63` |
| `economy_withdraw` | `<amount\|all\|half\|X%>` | `all` → `withdrawAll()`; `half` → `withdraw(floor(bank/2))`; `X%` → `withdraw(floor(bank*pct/100))`; otherwise `withdraw(amount)`. Sends `[Bank]` messages | `EconomyWithdrawEventFactory.java:26`, `EconomyWithdrawEventFactory.java:38-67` |
| `economy_deposit_all` | (none) | `depositAll()` with a "purse is empty" message when purse is 0 | `EconomyDepositAllEventFactory.java:22`, `EconomyDepositAllEventFactory.java:26-37` |

Both deposit/withdraw factories prefix user feedback with `[Bank]` (`EconomyDepositEventFactory.java:17`, `EconomyWithdrawEventFactory.java:17`, `EconomyDepositAllEventFactory.java:13`) and format amounts with `EconomyModule.formatCoins` (compact `k/m/b`).

### 3.10 Formatting Helpers — `EconomyModule.java:216-229`

- `formatCoins(double)` — compact form: `≥1b → "%.2fb"`, `≥1m → "%.2fm"`, `≥1k → "%.1fk"`, else rounded integer (`EconomyModule.java:216-222`). Used by the script events.
- `formatCoinsDisplay(double)` — dot-separated thousands (US locale, commas swapped to dots) prefixed with a coin emoji, e.g. `🪙 1.000.000` (`EconomyModule.java:225-229`). Used by the scoreboard (`ui.yml:23`) and `EconomyVariableProvider` for `$economy.purse.formatted$`.

### 3.11 Concurrency Model Summary

```
 Main thread (Bukkit)                Async pool (dbExecutor / timer)
 ──────────────────────              ─────────────────────────────
 PlayerJoinEvent ──► handleJoin ──► loadEconomy ──► putIfAbsent(cache)
 PlayerQuitEvent ──► handleQuit ──► saveEconomy (fire & forget)
 GiveCoins/quest/etc ──► addPurse  (synchronized on that player's EconomyData)
                                    flush timer ──► snapshot dirty ──► saveEconomyBatch
                                    (one conn, executeBatch)
```

- Same-player mutations serialize on the player's `EconomyData` instance lock.
- Cross-player mutations are lock-free on each other (distinct instances).
- The database is written only by: per-quit single saves, the periodic batched flush, and the full-cache batched flush on disable. SQLite uses **WAL mode** so the now-infrequent writer doesn't block readers (`DatabaseFactory.java:41`).

---

## Configuration (YAML)

The module reads exactly one key from `config.yml`:

| Key | Type | Default | Lines | Explanation |
|---|---|---|---|---|
| `economy.autosave-interval-seconds` | long | `60` | `config.yml:38` | How often (in seconds) dirty balances are flushed to the database in one batched transaction. Balances always live in memory and are safe between flushes; this only bounds how much progress could be lost on an unclean crash. A clean shutdown/reload always flushes everything immediately regardless. Read into ticks via `× 20` at enable time. |

The surrounding `economy:` block is at `config.yml:30-38`.

Indirectly relevant (affects the persistence backend the economy uses, not economy-specific):

| Key | Type | Default | Lines | Explanation |
|---|---|---|---|---|
| `database.type` | string | `sqlite` | `config.yml:12` | `sqlite` (local `plugins/Valmora/database.db`, WAL enabled) or `mysql` (multi-server sync). Read by `DatabaseFactory.createDataStore` (`DatabaseFactory.java:12-45`). |
| `database.mysql.*` | string/int | see `DatabaseFactory` | `config.yml:17-28`, `DatabaseFactory.java:20-33` | Host, port, database name, username, password, `use-ssl`. Only used when `type: mysql`. |

There is **no economy-specific YAML content folder** (no `plugins/Valmora/economy/`). All balances live in the `valmora_economy` database table.

---

## Data Model / Persistence

### 5.1 Table — `valmora_economy`

Created in the v1 migration (`SQLDataStore.java:154-160`):

```sql
CREATE TABLE IF NOT EXISTS valmora_economy (
    uuid VARCHAR(36) PRIMARY KEY,
    purse DOUBLE NOT NULL DEFAULT 0,
    bank  DOUBLE NOT NULL DEFAULT 0
)
```

| Column | Type | Notes |
|---|---|---|
| `uuid` | `VARCHAR(36)` | Primary key — player UUID string. |
| `purse` | `DOUBLE NOT NULL DEFAULT 0` | Spendable wallet balance. |
| `bank` | `DOUBLE NOT NULL DEFAULT 0` | Storage balance. |

One row per player; upserts never duplicate (see §5.3). The table is created by `migrateToV1` and exists from schema v1 onward; the current `LATEST_SCHEMA_VERSION` is 2 (`SQLDataStore.java:48`).

### 5.2 Read path — `loadEconomy`

`SELECT purse, bank FROM valmora_economy WHERE uuid = ?` (`SQLDataStore.java:438-452`). Returns `double[]{purse, bank}` or `null` if no row exists (`SQLDataStore.java:445`). The caller treats `null` as a fresh zero balance (`EconomyModule.java:66`, `EconomyModule.java:110`). Runs on the dedicated 4-thread DB executor (`SQLDataStore.java:34`, `SQLDataStore.java:439`).

### 5.3 Write paths

- **Single** — `saveEconomy(uuid, purse, bank)` uses an upsert, MySQL vs SQLite dialect (`SQLDataStore.java:455-472`). Used only by `handleQuit` (`EconomyModule.java:124`).
- **Batch** — `saveEconomyBatch(Map<UUID, double[]>)` opens **one connection**, `setAutoCommit(false)`, `addBatch()` per row, `executeBatch()`, then one `commit()` (`SQLDataStore.java:475-504`). An empty map short-circuits to a completed future without touching the pool (`SQLDataStore.java:476`). This is the write path for the periodic flush (`EconomyModule.java:143`) and the shutdown flush (`EconomyModule.java:93`).

Contract in `DataStore.java:16-26`; both methods are `CompletableFuture`-based (async).

### 5.4 WAL mode

For SQLite, the pool is configured with `PRAGMA journal_mode=WAL` at connection init so concurrent readers and the (now infrequent, batched) writer proceed without blocking each other (`DatabaseFactory.java:36-43`). MySQL gets prepared-statement caching pool properties (`DatabaseFactory.java:29-31`).

### 5.5 Lifecycle / crash semantics

- On a **clean reload/shutdown**, `onDisable()` does a full-cache batched flush, so nothing is lost (`EconomyModule.java:85-95`).
- On an **unclean crash**, up to `autosave-interval-seconds` (default 60s) of dirty, unflushed mutations per player can be lost. Quit-time saves reduce this for players who logged off normally.
- On a **quick rejoin within the same session**, the cached balance is reused and the DB read is skipped (`EconomyModule.java:106-108`) — the memory copy is authoritative until flushed.

---

## API Exposed

### 6.1 `EconomyService` (public interface) — `api/economy/EconomyService.java:5-9`

```java
public interface EconomyService {
    void addCoins(Player player, double amount);
    void removeCoins(Player player, double amount);
    double getCoins(Player player);
    boolean hasCoins(Player player, double amount);
}
```

All four methods operate on the player's **purse** (the spendable wallet) only — the interface has no bank concept. `EconomyModule` implements them by delegating to `addPurse`/`removePurse`/`getPurse`/`hasPurse` (`EconomyModule.java:233-236`).

### 6.2 `ValmoraAPI` accessors

- `EconomyService getEconomy()` — `ValmoraAPI.java:47`; returns `economyService` (`Valmora.java:369-371`), which is set to the `EconomyModule` instance in `onEnable()` (`Valmora.java:149-150`). **Swappable**: `Valmora.setEconomyService(EconomyService)` exists (`Valmora.java:378-380`) so a different implementation could be injected.
- `EconomyModule getEconomyModule()` — `ValmoraAPI.java:49`; returns the concrete module for bank/total access (`Valmora.java:374-376`).

### 6.3 Concrete `EconomyModule` methods (purse + bank)

Beyond the `EconomyService` four, the concrete class exposes (all UUID-keyed, all `EconomyModule.java`):

| Method | Lines |
|---|---|
| `getPurse(uuid)` / `getBank(uuid)` / `getTotal(uuid)` | `EconomyModule.java:148-150` |
| `setPurse(uuid, amt)` / `setBank(uuid, amt)` | `EconomyModule.java:154-162` |
| `addPurse(uuid, amt)` / `removePurse(uuid, amt)` / `hasPurse(uuid, amt)` | `EconomyModule.java:166-176` |
| `addBank(uuid, amt)` / `removeBank(uuid, amt)` | `EconomyModule.java:180-188` |
| `deposit(uuid, amt)` / `withdraw(uuid, amt)` (boolean success) | `EconomyModule.java:192-202` |
| `depositAll(uuid)` / `withdrawAll(uuid)` | `EconomyModule.java:204-212` |
| `getOrCreateData(uuid)` | `EconomyModule.java:240-242` |
| `static formatCoins(double)` / `static formatCoinsDisplay(double)` | `EconomyModule.java:216-229` |

### 6.4 Script DSL surface

- **Variables:** `$economy.purse$`, `$economy.purse.formatted$`, `$economy.bank$`, `$economy.total$` (see [§3.8](#38-script-variable-provider--economyvariableproviderjava)).
- **Events:** `economy_add`, `economy_remove`, `economy_deposit`, `economy_withdraw`, `economy_deposit_all` (see [§3.9](#39-script-events--event-subpackage)).

---

## Dependencies & Consumers

### 7.1 Wiring in `Valmora.java`

- `economyModule` is created **immediately after the database** (`Valmora.java:141-149`) and `economyService = economyModule` (`Valmora.java:150`).
- Registered **after `playerManager`** with the comment "Depends on playerManager for join/quit lifecycle" (`Valmora.java:191-192`). Registration order: `script → time → stat → player → economy → ...` (`Valmora.java:188-192`).
- `/eco` executor set after all modules enable (`Valmora.java:242`), per `AGENTS.md` §6.3 (commands never registered inside a module). Plugin-level permission `valmora.admin` declared in `plugin.yml:39-42`.

### 7.2 Consumers (runtime)

| Consumer | What it does with the economy | Reference |
|---|---|---|
| `MobDeathListener` | Grants the mob's `gold-reward` coins to the killer on death | `MobDeathListener.java:65-68` |
| `GiveCoinsMechanic` (`GIVE_COINS`) | `addCoins(player, amount)` to the caster (e.g. Raider Axe kill reward). Registered in `AbilityManager.java:59` | `GiveCoinsMechanic.java:23` |
| `TakeCoinsMechanic` (`TAKE_COINS`) | `removeCoins(player, amount)` from the caster (e.g. Crown of Greed cost). Registered in `AbilityManager.java:60` | `TakeCoinsMechanic.java:23` |
| `ReforgeModule` | Charges a coin cost to reforge an item (`checkAndNotifyCoins` + `deductCoins`); null-safe on `getEconomy()` | `ReforgeModule.java:271-287` |
| `SlayerStartEventFactory` (`slayer_start`) | Requires `hasCoins` and charges `removeCoins` for the tier activation cost; skipped if `getEconomy()` is null | `SlayerStartEventFactory.java:60-69` |
| `ScoreboardUI` | Renders `Purse: 🪙 …` line via `getEconomyModule().getPurse(uuid)` + `formatCoinsDisplay` | `ScoreboardUI.java:208-213` |
| `ProfileGui` | Profile-menu coin display via `getEconomyModule().getTotal(uuid)` | `ProfileGui.java:231-237` |
| `guis/bank.yml` | The bundled Bank of Valmora GUI — deposit/withdraw flows driven by `economy_deposit`/`economy_withdraw`/`economy_deposit_all` events and `$economy.*$` variables | `guis/bank.yml` (whole file) |
| `ui.yml` | Scoreboard default includes `$economy.purse.formatted$` | `ui.yml:23` |

**Not a consumer (discrepancy to be aware of):** the anvil machine handler (`AnvilMachineHandler.java:90-97`) charges a coin cost through the **profile variable** `player.var.coins` (`variable add player.var.coins -<cost>`), *not* the economy service. Likewise `docs/USER_DOCS.md` §7.5 still describes the old variable-based model (`player.var.coins`). The two "coins" concepts currently coexist: `player.var.coins` is a free-form profile variable; the economy module is the canonical `EconomyService` balance. `docs/YAML_DOCS.md:347` ("gold-reward ... TODO: Integrate with Economy system") is also **stale** — integration is live at `MobDeathListener.java:67`.

### 7.3 Load-order constraints

- `economyModule` registers providers/events through `plugin.getScriptModule()` at enable-time (`EconomyModule.java:56-61`) — `script` loads first (`Valmora.java:188`), so this is safe.
- It is registered after `playerManager` (join/quit lifecycle comment, `Valmora.java:192`); the module does **not** actually call into `PlayerManager` at enable-time — the dependency is ordering convention.
- Consumers like `mob`, `reforge`, and `slayer` load later and read the economy through `ValmoraAPI`/`Valmora` accessors at runtime, never holding a constructor-time reference to the module.

---

## Unfinished Things / TODOs

- **Bank interest / bank upgrades.** `docs/todo.md:4` lists "economy: bank upgrades, intrest" as outstanding. Nothing in `module/economy/` implements interest accrual or upgradable bank capacity.
- **Death-penalty configurability.** The 50% purse loss on death is hardcoded in `EconomyListener.java:28-35` — no config key, no toggle.
- **`/eco` tab completion is dead code.** `EcoCommand` implements a full `onTabComplete` (`EcoCommand.java:112-132`), but `Valmora.java:242` calls only `setExecutor(...)` and never `setTabCompleter(...)`.
- **No offline-player targeting.** `/eco` requires the target to be online (`EcoCommand.java:43-47`); there is no DB-backed balance editor for offline players.
- **Join race on first login.** `handleJoin` loads async and inserts with `cache.putIfAbsent` (`EconomyModule.java:109-112`). If a transaction for that player fires between join and the load completing, `getOrCreate` seeds a zero-balance entry and `putIfAbsent` then refuses to overwrite it — the DB-loaded balance would be eclipsed for the session (see [Possible Improvements](#9-possible-improvements--changes)).
- **`docs/USER_DOCS.md` §7.5 and `docs/YAML_DOCS.md:347` are stale** regarding the old `player.var.coins` / TODO-integration model (see §7.2).
- **No transaction ledger.** The bundled bank GUI has a "Recent Transactions" display (`guis/bank.yml:69-75`) that is decorative — there is no transaction history persisted.

---

## Possible Improvements / Changes

- **Seal the join race.** In `handleJoin`, populate the cache synchronously on the main thread for the joining player (join is rare, so the blocking cost is acceptable), or re-check `cache` inside the async completion and merge rather than `putIfAbsent`.
- **Configurable death penalty** — e.g. `economy.death-loss-percent` (default 50) replacing the hardcoded `purse / 2.0`.
- **Bank interest / upgrades** — a periodic tick adding interest to `bank`, plus per-player capacity (currently bank is unbounded).
- **Offline balance editing** — allow `/eco` (or a `--offline` flag) to operate on DB rows for offline players.
- **Wire tab completion** — `getCommand("eco").setTabCompleter(new EcoCommand(economyModule))` in `Valmora.java`.
- **Merge the `player.var.coins` anvil path** (`AnvilMachineHandler.java:94`) onto `EconomyService` so the economy is the single source of truth for coin costs.
- **Transaction ledger** — persist deposit/withdraw history so the bank GUI's "Recent Transactions" can show real data.
- **MySQL batch tuning** — the batch upsert is connection-per-batch; for multi-server setups, verify statement-batching overhead stays acceptable at 10k-player scale.
- **Read-through on first access** — optionally have `getOrCreateData` trigger an async load for players who joined without the pre-load completing (e.g. NPC-triggered transactions while offline-then-online).

---

## Tests

### 10.1 `module/economy/EconomyDataTest.java`

Pure concurrency/atomicity tests over `EconomyData` — no Bukkit, no Mockito:

| Test | Covers | Lines |
|---|---|---|
| `addPurseNeverLosesUpdatesUnderConcurrentAccess` | 16 threads × 5,000 `addPurse(1.0)` must total exactly 80,000 (per-instance lock serializes same-player mutations) | `EconomyDataTest.java:20-51` |
| `removePurseClampsAtZeroAndNeverGoesNegative` | 10 threads × 200 removals from 1000 clamps exactly at 0, never negative | `EconomyDataTest.java:53-70` |
| `depositAllMovesEntirePurseAtomically` | Whole purse moves to bank; repeat on empty purse is a true no-op | `EconomyDataTest.java:72-82` |
| `depositFailsWithoutMutatingWhenPurseIsShort` | Failed deposit leaves both balances untouched | `EconomyDataTest.java:84-90` |
| `withdrawFailsWithoutMutatingWhenBankIsShort` | Failed withdraw leaves both balances untouched | `EconomyDataTest.java:92-98` |

### 10.2 `module/economy/CoinExpressionParserTest.java`

15 tests covering plain numbers, `k/K/m/b` suffixes, additive/mixed-suffix expressions, multiply/divide, parentheses, and the error cases (empty string, null, non-numeric, whitespace) all resolving to `0.0` (`CoinExpressionParserTest.java:12-26`).

### 10.3 `database/SQLDataStoreTest.java` (economy-related, `@Tag("database")`)

Runs against a **real temporary SQLite file** (no server):

| Test | Covers | Lines |
|---|---|---|
| `initCreatesSchemaAndStampsVersion` | Migration framework stamps v2 and creates `valmora_economy.purse` | `SQLDataStoreTest.java:39-55` |
| `initIsIdempotent` | Second `init()` is a clean no-op | `SQLDataStoreTest.java:57-68` |
| `migratesPreVersioningDatabase` | Legacy tables upgraded in place (economy table included in v1 migration) | `SQLDataStoreTest.java:70-95` |
| `economyRoundTrip` | `loadEconomy` returns `null` for unknown player; `saveEconomy` then load returns both values; re-save upserts rather than duplicating | `SQLDataStoreTest.java:125-149` |
| `economyBatchRoundTripUpsertsAllRows` | Batch both inserts new rows and overwrites an existing one in one transaction | `SQLDataStoreTest.java:151-182` |
| `economyBatchWithEmptyMapIsNoOp` | Empty batch short-circuits without error | `SQLDataStoreTest.java:184-194` |

### 10.4 Manual/scripted coverage

`docs/TESTING_GUIDE.md` TC-ECO (`TESTING_GUIDE.md:72-81`) documents manual scenarios: set via `/eco`, deposit/withdraw via the bank GUI, "deposit all", and the insufficient-funds error path (ECO-05 — `economy_withdraw 9999` with 1500 bank must error and not change balances).
