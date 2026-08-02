# Unfinished / Incomplete Features

This document catalogs functionality that is scaffolded, partially built, or explicitly
deferred in the current codebase — gathered by scanning for `TODO`, `DESCRIPTION-ONLY`,
`not implemented`, `deferred`, and stub/no-op patterns across `src/` and cross-checking
against `docs/`. Items are grouped by subsystem, most impactful first.

> **Note:** several items below (mechanic engine, armor sets) turned out to already be
> implemented in the working tree since this document was first drafted — the YAML comments
> describing them as unimplemented are stale. Marked ✅ where confirmed fixed/already-done.
> ✅-DONE items were resolved without needing a design decision from you; the rest still need
> one (backend choice, formula/mechanic design, scope of a new subsystem, etc.) before they
> can be implemented.

---

## 1. Economy System — implemented ✅ DONE (hyper-optimized backend)

**Files:** `module/economy/EconomyModule.java`, `EconomyData.java`, `database/DataStore.java`,
`database/SQLDataStore.java`, `database/DatabaseFactory.java`

`EconomyModule` (backed by the `valmora_economy` table) is the live `EconomyService` — the
`NoOpEconomyService` stub this section used to describe has been deleted (dead code, fully
superseded). Redesigned for high-concurrency throughput (thousands of transactions/sec,
~10k cached players):

- **In-memory-authoritative:** every read/mutation is an O(1) op against a
  `ConcurrentHashMap<UUID, EconomyData>` — no transaction ever touches the database, so
  per-transaction cost is bounded by memory, not disk/network I/O.
- **Per-player atomicity, not a global lock:** `EconomyData`'s add/remove/deposit/withdraw
  methods are `synchronized` on the *instance*, so different players never contend and
  same-player concurrent transactions serialize correctly instead of losing updates
  (covered by `EconomyDataTest`'s concurrent-increment test).
- **Write-behind persistence:** mutations mark the player dirty; a background task flushes
  every dirty balance in a single **batched transaction** (one connection, `executeBatch`)
  on a configurable interval (`economy.autosave-interval-seconds` in `config.yml`, default
  60s) — not one DB round-trip per transaction.
- **Fast join/quit/shutdown at scale:** rejoining within the same session skips the DB read
  entirely (cache retained, not evicted on quit); shutdown/reload flushes the *entire* cache
  in one batched transaction instead of one blocking round-trip per player (previously O(n)
  sequential `.join()` calls — would have stalled a reload for seconds with a large cached
  player set).
- **SQLite WAL mode** enabled (`DatabaseFactory`) so the (now infrequent) writer doesn't
  block concurrent readers.

Covered by `SQLDataStoreTest` (batch upsert round-trip, empty-batch no-op) and
`EconomyDataTest` (16-thread concurrent-increment correctness, clamp-at-zero, atomic
deposit/withdraw failure semantics).

---

## 2. Item Ability Mechanic Engine — mostly implemented now ✅ (partially stale doc)

The `new_items.yml` header comment (written early) said only `DAMAGE`, `HEAL`,
`APPLY_EFFECT`, `MODIFY_STAT`, `TELEPORT`, `PUSH_ENTITIES`, `PULL_ENTITIES`, `SCRIPT` were
supported. That's now out of date — `module/item/impl/` also has working implementations for:

- `LAUNCH_PROJECTILE` (`LaunchProjectileMechanic.java`)
- `LAUNCH_PLAYER` (`LaunchPlayerMechanic.java`)
- `GIVE_COINS` / `TAKE_COINS` (`GiveCoinsMechanic.java` / `TakeCoinsMechanic.java`) — wired,
  and now backed by a real economy (§1), so coins granted actually persist
- `IGNITE` (`IgniteMechanic.java`)
- `AOE_MINE` (`AoeMineMechanic.java`)

**Still genuinely missing:** `BEAM`, returning-projectile ("Bonemerang"), `EXPLODE`,
`ADD_STACK`, `CANCEL_TRAMPLE`, `CHARGE_JUMP`. Items relying solely on these remain marked
`# DESCRIPTION-ONLY:` in `catacombs_swords.yml`, `slayer_swords.yml`, `swords.yml`, and
`bows.yml`. Many *other* description-only abilities in those files may now be implementable
since their dependency (`LAUNCH_PROJECTILE`/`LAUNCH_PLAYER`) exists — worth re-auditing which
`DESCRIPTION-ONLY` markers are stale.

**Not fixed here:** flipping specific items from description-only to wired, and building the
remaining mechanic types, both require deciding exact behavior per ability (damage curves,
stack caps, bounce rules) — a design pass, not a mechanical one.

---

## 3. Armor Set Bonuses — already implemented ✅ (stale doc)

`module/item/set/SetBonusService.java` exists and is wired into
`StatManager.recalculateStats()`: it counts worn pieces per `set` id (from `ItemDefinition`)
and applies each tier's stat bonuses once the piece-count threshold is met. The
`catacombs_swords.yml` comments calling this "not implemented" are stale.

What's *not* covered by this generic system: dungeon-specific "class detection/adaptation"
behavior some Catacombs items describe — that's a different, dungeon-mode-specific concept
layered on top of set bonuses. See §7.

---

## 4. Damage-over-Time (DoT) ticks — already implemented ✅ (stale doc)

`DamageMechanic.java` and `HealMechanic.java` already schedule repeated ticks via
`runTaskTimer` when `ticks > 1`, honoring the `interval` parameter (first burst immediate,
remainder on the timer). The `new_items.yml:24-25` comment claiming single-burst-only is
stale — no fix needed.

---

## 5. `DAMAGE_MULTIPLIER` stat modifier — recorded but inert

**File:** `src/main/resources/items/new_items.yml:692-693` (Warden Helmet's "Brute Force")

`DamageCalculator` already has a generic `damageMultiplier` hook
(`DamageModifierContext.getDamageMultiplier()`, applied at `DamageCalculator.java:103`), but
nothing currently *sets* it from a player stat — today it's only fed by enchant logic
(`modifyAttack`). The Warden Helmet ability text ("+20% damage per 25 Speed") needs a
formula that reads current Speed and feeds that multiplier; the ability's YAML doesn't even
declare a `DAMAGE_MULTIPLIER` mechanic today, only the Speed-halving `MODIFY_STAT`.

**Needs a decision before implementing:** is the +20%/25-Speed scaling meant to update live
as Speed changes, or snapshot at cast/equip time? That changes the implementation shape
(continuous recalculation hook vs. one-shot). Not fixed here.

---

## 6. Kill-counter stacking buffs — not implemented

Referenced repeatedly across `catacombs_swords.yml` and `slayer_swords.yml` (e.g. "kill
counter + stacking buff", "next-hit buff"). No per-player kill-streak counter or stacking
temporary-buff mechanism currently exists.

**Needs a decision before implementing:** stack cap, decay/reset rules (time-based? on-hit
reset?), and whether stacks persist through death — these vary per item and aren't specified
anywhere in code today.

---

## 7. Dungeon/Catacombs-specific systems — largely unbuilt

Several abilities assume systems that don't exist anywhere in the codebase yet:

- Dungeon ability cooldown reduction
- Dungeon "class" detection and per-class bonus adaptation
- Soul collection + soul summoning (Reaper-type abilities)
- Attunement state toggle + shield-state delayed heal/repeat "ticker" state machines
  (a large recurring family in `slayer_swords.yml`)

This looks like an entire planned feature (a Catacombs/Dungeons mode with player classes)
that items were authored against ahead of the system being built.

**Needs a decision before implementing:** this is a full subsystem (class selection,
dungeon-session concept, state-machine abilities), not a small mechanic — needs scoping
before any code gets written.

---

## 8. Projectile behaviors — ricochet, homing, bounce, returning

`LAUNCH_PROJECTILE` now exists (§2), but the more advanced projectile behaviors described in
item lore — ricochet/bounce between enemies, homing/guided flight, returning-to-caster with
backstab detection — aren't implemented by it yet.

**Needs a decision before implementing:** targeting/homing behavior (search radius, turn
rate) and bounce-selection rules aren't specified — a design pass on `LaunchProjectileMechanic`.

---

## 9. Missing-HP damage scaling — variable added ✅ DONE

**File:** `src/main/java/org/nakii/valmora/module/script/variable/providers/PlayerVariableProvider.java`

Added `$player.missing_hp_percent$` (alongside the existing `health_percent`), usable in
formula strings the same way other player variables are (e.g.
`damage: "$player.stat.damage$ * (1 + $player.missing_hp_percent$ * 0.02)"`). This unblocks
authoring for abilities that scale off missing health, but the abilities themselves still
need their YAML mechanics updated to use it — that's a per-item content change, not covered
here.

---

## 10. Quiver — ammo storage implemented ✅ DONE (ability-side "quiver resource" still separate)

**Files:** `module/quiver/` (`QuiverModule`, `QuiverListener`, `QuiverInventoryHolder`),
`module/profile/ValmoraProfile.java`, `database/SQLDataStore.java`

Added a `/quiver` menu (27 slots, arrow-type items only — enforced via `Tag.ITEMS_ARROWS`)
backed by a new profile field (`quiverItems`) that's fully persisted to the database (v2
schema migration adds a `quiver` column; unlike the Accessory Bag, this one doesn't have the
"looks persistent but isn't" gap noted in §3 of this doc's history). Bows/crossbows always
draw from the player's normal inventory first — vanilla's own ammo check/consumption is
untouched; only when the inventory has zero arrows does `QuiverListener.onBowUse` (a
`PlayerInteractEvent` hook, since a truly-out-of-ammo bow won't even fire an
`EntityShootBowEvent` to hook into) loan a single arrow from the quiver into the inventory
before vanilla's ammo check runs, so the rest of the draw/fire/consume flow proceeds exactly
as it would for an inventory arrow.

**Not covered by this:** the *different* "quiver" concept referenced in some `bows.yml`
ability descriptions (`# DESCRIPTION-ONLY: quiver consumption to double shot damage`, etc.)
— that's an ability-resource cost (spend N arrows for a damage buff), not ammo storage, and
still needs the mechanic-engine work described in §2.

---

## 11. Quest `SMELT` objective — implemented ✅ DONE

**Files:** `module/quest/QuestListener.java`, `util/Keys.java`

`FurnaceSmeltEvent` carries no player reference, so smelt-based quest objectives couldn't be
attributed to a player. Fixed by tagging furnace/blast-furnace/smoker blocks with their
placer's UUID (via PDC on the `TileState`) in `onBlockPlace`, then reading that tag in a new
`onSmelt(FurnaceSmeltEvent)` handler to attribute the smelt and fire
`QuestObjectiveTypes.SMELT`. Furnaces placed before this change (or never PDC-tagged, e.g.
world-generated ones) won't attribute smelts — only newly-placed furnaces are tracked.

---

## 12. Enchantment lore cleanup — fixed ✅ DONE

**Files:** `module/enchant/EnchantmentHelper.java`, `util/Keys.java`

Re-enchanting a **generic** (non-Valmora) item used to re-append a full enchant-lore block on
top of the previous one every time (since `meta.lore()` already contained the prior block),
accumulating stale entries. Fixed by snapshotting the item's lore *before* any enchant block
existed (stored once in PDC via `GENERIC_BASE_LORE_KEY`, MiniMessage-serialized) and always
rebuilding lore from that snapshot plus the current enchant map, instead of the live
(already-mutated) lore. Valmora items were already unaffected since their lore is fully
rebuilt via `ItemFactory.updateLore`.

---

## Summary Table

| # | Area | Status |
|---|------|--------|
| 1 | Economy backend | ✅ **Fixed** — write-behind, batched, concurrency-tested |
| 2 | Mechanic engine | Mostly implemented; `BEAM`/`EXPLODE`/`ADD_STACK`/bonemerang/etc. still missing |
| 3 | Armor set bonuses | ✅ Already implemented (`SetBonusService`) |
| 4 | DoT ticking | ✅ Already implemented |
| 5 | DAMAGE_MULTIPLIER | Parsed, not applied — needs formula decision |
| 6 | Kill-counter stacking buffs | Not implemented — needs stack-rule decision |
| 7 | Dungeon/Catacombs systems | Not implemented — needs subsystem scoping |
| 8 | Advanced projectile behavior | Not implemented — needs targeting-rule decision |
| 9 | Missing-HP scaling variable | ✅ **Fixed** — `$player.missing_hp_percent$` added |
| 10 | Quiver (ammo storage) | ✅ **Fixed** — `/quiver`, inventory-first fallback; ability-side resource cost still open |
| 11 | Quest SMELT objective | ✅ **Fixed** — furnace-placer attribution |
| 12 | Enchant lore cleanup on generic items | ✅ **Fixed** — base-lore snapshot |

Remaining items (2 partial, 5, 6, 7, 8) all hinge on a design/scope decision — pick
whichever you want to tackle and we can talk through the approach.
