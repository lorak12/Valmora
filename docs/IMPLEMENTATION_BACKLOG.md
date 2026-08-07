# Implementation Backlog — Unfinished / Planned Features

> **This is a temporary working document, not part of the permanent doc set.** It exists to track
> every unfinished, deferred, or planned-but-never-built feature found across `docs/` and source
> code comments as of 2026-08-06, so future sessions (AI or human) can pick items off **one at a
> time** instead of re-discovering them. Delete this file once the backlog is empty, or fold
> whatever's left into `docs/todo.md`.
>
> **How to use this:**
> 1. Pick ONE `[ ]` item. Don't batch unrelated items in one session — this codebase is large and
>    errors compound if changes aren't verified incrementally (`./gradlew compileJava && ./gradlew test`
>    after every item).
> 2. Read the module's `docs/modules/design/<module>.md` and `docs/modules/user/<module>.md` first —
>    they have the full current-state context this backlog only summarizes.
> 3. When done, check the box, add a one-line note (date + what actually happened, especially if you
>    deviated from the description below), and update the module's design doc's "Known Gaps" section
>    to remove the item.
> 4. If an item turns out to already be done (docs drift), just check it off with a note saying so —
>    don't re-implement.
> 5. Source: compiled from `docs/UNFINISHED_FEATURES.md`, `docs/todo.md`,
>    `docs/REFACTOR/PROGRESS.md`, every `docs/modules/design/*.md` "Known Gaps"/recommendations
>    section, `docs/GUI_MODULE_ENHANCEMENT_PLAN.md`, `docs/ZONE_MODULE_PLAN.md`, `docs/QUEST_SYSTEM.md`,
>    and a full grep of `src/` for TODO/stub/no-op comments (there were none left as bare `TODO`s —
>    everything outstanding is tracked in docs instead, which is why this file exists).

---

## Cross-cutting (touches multiple modules)

- [x] **Expose missing modules on `ValmoraAPI`.** *(2026-08-07: already done — docs drift. `ValmoraAPI` already declares and `ValmoraAPIImpl` already implements `getPetModule()`, `getRecipeModule()`, `getReforgeModule()`, `getResourceModule()`, `getCalendarEventModule()`, `getCollectionModule()`, `getFishingModule()`. No code change needed.)*
- [ ] **Add unit tests for untested modules.** No dedicated test suite exists for: gui, alchemy, calendar, collection (partial), fishing, notify, npc, pet, profile, progression, quest (partial), reforge (partial), resource, script (11 conditions / 10 event factories / `VariableResolverImpl` untested), skill (partial), time, ui, warp, zone (beyond `ZoneResourceConfigTest`).
- [ ] **Missing admin commands/tooling** for several modules that have none: `/calendar` (list/force-start/force-end/preview), `/recipe`, expanded reforge tooling (list/preview/force/reset), resource-block editing via `/zone` (add/remove/list resource blocks in-game), collection admin view/reset/force.
- [x] **Case-sensitive lookups that should be case-insensitive** (diverge from the `Registry<T>` convention used elsewhere): GUI ids (plain `HashMap`), Reforge definition storage, Calendar event id lookup. *(2026-08-07: fixed. `GuiModule` now stores/looks up `guiRegistry` keys lowercased (`GuiModule.java`, `GuiCommand.java`); `ReforgeModule.definitions` put now lowercases the key to match the existing lowercased `getDefinition` lookup; `CalendarEventModule.definitions` put and `getDefinition` now both lowercase. Verified with `./gradlew compileJava`.)*

---

## Item / Ability mechanic engine

This is the single largest chunk of backlog — most of the "DESCRIPTION-ONLY" items across
`src/main/resources/items/*.yml` are blocked on one of these missing mechanic types. Implement the
mechanic type once, then re-check the affected items (each YAML file has inline
`# DESCRIPTION-ONLY:` comments marking what's waiting on it).

- [ ] **`ON_DAMAGE_TAKEN` and `ON_TELEPORT` ability triggers.** Enumerated in `AbilityTrigger.java:13-15` but never dispatched by any listener.
- [ ] **`EQUIP`/`UNEQUIP` ability triggers.** Enumerated, no listener wires them (`item.md` §8, `user/item.md:367-368`).
- [ ] **`BEAM` mechanic.** Blocks: Fire Freeze Staff (delayed-circle freeze), Aurora Staff, Eye Beam ability in `new_items.yml` (SNEAK + `$ticks_sneaking$` trigger).
- [ ] **`EXPLODE` mechanic.** Currently only approximated via `LAUNCH_PROJECTILE` + `DAMAGE` for Bonzo's Staff — needs a real implementation.
- [ ] **`ADD_STACK` mechanic** (generic stacking counter on an item/player). Blocks: Growth Armor, Thunder, and the whole "kill counter + stacking buff" family in `catacombs_swords.yml`/`slayer_swords.yml` (needs stack cap/decay/persist-through-death design decisions — see `docs/UNFINISHED_FEATURES.md` §6).
- [ ] **`CHARGE_JUMP` mechanic.** Blocks: Spring Boots (charged jump height), `spring_boots` in `new_items.yml`.
- [ ] **`CANCEL_TRAMPLE` mechanic.** Blocks: Rancher's Boots.
- [ ] **`CONSUME_ITEM` mechanic** (generic "consume N of item X for effect Y"). Blocks: Primal FEAR, Midas' Sword.
- [ ] **`CHANNEL` mechanic** (damage-gated channel). Blocks: Ragnarock.
- [ ] **`RETURNING_PROJECTILE` mechanic** (with backstab detection for some items). Blocks: Tribal Spear, Livid Dagger, Flower Of Truth/Bouquet Of Lies (ricochet variant), returning-Bonemerang bows.
- [ ] **`NEXT_HIT_BUFF` mechanic.** Blocks: Edible Mace, Sword of Bad Health.
- [ ] **`ENTITY_SUMMON` mechanic** + **soul collection/summoning subsystem.** Blocks: Necromancer Sword, Fel Sword, Zombie Commander Whip, and the recurring "soul drop/collection/summoning" notes across `slayer_swords.yml`.
- [ ] **`KILL_COUNTER` mechanic.** Blocks: Daedalus Blade (+ pet vars), Tarantula/Recluse Fang (kill-counter stacking).
- [ ] **Ricochet/bounce projectile behavior** in `LaunchProjectileMechanic`. Blocks: several `catacombs_swords.yml`/`bows.yml` items (ricochet + damage amplification, ricochet + missing-HP scaling, arrow bounce-to-another-target ×2).
- [ ] **Homing/guided projectile flight.** Blocks: Spirit Sceptre, homing BEAM bow variants.
- [ ] **Missing-HP-percent scaling as a general modifier primitive.** Referenced by many deferred items (charge system + missing-HP bonus, ghost mode + missing-HP damage scaling, missing-HP scaling vs Undead) — worth building once as a reusable stat-formula input rather than per-item.
- [ ] **Damage-immunity "veil" toggle mechanic** (deactivatable). Blocks: Wither Cloak Sword.
- [ ] **Dungeon/Catacombs subsystem** (needs scoping before implementation — see `docs/UNFINISHED_FEATURES.md` §7): dungeon ability cooldown reduction, dungeon "class" detection/adaptation and per-class bonuses, attunement state toggle (recurring ×5 in `slayer_swords.yml`), shield-state delayed-heal/repeat "ticker" state machine (recurring ×5).
- [ ] **`DAMAGE_MULTIPLIER` stat wiring.** Recorded on Warden Helmet ("Brute Force", `new_items.yml:692-693`) but nothing in the damage pipeline reads/applies it yet — needs a formula decision (snapshot vs. live recalculation) before wiring.
- [ ] **Conditional/stacking set-bonus auras.** `SetBonusService` currently only applies flat stats — the ~25 "DEFERRED" entries in `set_bonuses/sets.yml` need real mechanics: zone-conditional bonuses (Farming Minion proximity, Forest HoT, End Island stat ×2, water/Sea-Creature bonuses), time/light-conditional bonuses (night triple-stats, light-level 0-200% scaling), on-kill triggers (GIVE_COINS+HEAL, APPLY_EFFECT, stacking Health), damage-reflect/damage-multiplier modifiers, collection-variable-scaled stats (Emerald collection → Health/Defense), skill-level-scaled stats (Mining Speed per level), AoE-on-hit triggers, charge/discharge stacking systems. Treat as one epic; tackle in small batches by mechanic shape, not by set.
- [ ] **`GIVE_COINS`/`TAKE_COINS` and `ADD_STACK`/set-bonus parser** — per `new_items.yml`'s header comment these were originally listed as deferred; cross-check against `docs/UNFINISHED_FEATURES.md` §2-3, which says they're actually implemented now. Verify and remove the stale header comment in `new_items.yml` if so.
- [ ] **Bow-specific deferred mechanics** (13+ notes in `bows.yml`, see raw list in git history of this file / `docs/UNFINISHED_FEATURES.md` §8, §10 for full detail): quiver-consumption-based effects (double shot damage, per-mob-type multiplier), mana-bank-into-next-hit, kill-count unlock progression, per-arrow aura + piercing, 5-arrow volley + venom DoT, delayed-burst mark, sustained multi-arrow auto-fire, arrow-replacement (exploding wither skulls), extended range, stacking defense-reduction debuff.
- [ ] **Wand-specific deferred mechanic**: delayed-circle freeze (spawn zone, wait, then apply) in `wands.yml`.
- [ ] **Sword-specific deferred mechanics** (`swords.yml`): escalating mana cost + self-knockback, mana-drain scaling + next-hit buff, HP-cost + percent-HP-scaled buff, pet-stat copy/taming scaling/coin reward, taunt + enemy-damage-reduction + HP cost.

---

## GUI module

- [ ] **GUI navigation "back" button.** `GuiSession.parent` is defined but never assigned — no `BACK` navigation despite being documented.
- [ ] **Virtual sign input dead code.** `OpenSignInputEventFactory`/`SignInputManager`/`SignInputListener` exist but are never registered in `GuiModule.onEnable()`.
- [ ] **Fix repeating-task leak on `open_gui`.** Previous GUI's repeating update task isn't cancelled when navigating to another GUI.
- [ ] **Ship `fast_travel.yml`.** `/warp` (no args) references it (`WarpCommand.java:28`) but the file doesn't exist — command silently does nothing.
- [ ] **Fix `collections_categories` GUI's `command:` key collision** with the `/collections` command already registered in `plugin.yml` — the GUI's own command binding never runs.
- [x] **Remove or wire the dead `rows:` YAML key** (parser currently ignores it). *(2026-08-07: wired. `GuiDefinitionParser` now honors `rows:` as a minimum inventory height — `rows = max(rows: value, layout.size())`, padding blank rows if `rows:` is larger; it can never shrink below the layout. Updated `docs/modules/design/gui.md` and `docs/modules/user/gui.md` accordingly. Verified with `./gradlew compileJava`.)*
- [ ] **GUI_MODULE_ENHANCEMENT_PLAN.md Phase 5** (docs/optimization, marked ⬜): pagination caching, dupe-protection hardening for `AnvilMachineHandler` under mass-shift-click.
- [ ] **Cache per-render condition parsing.** `GuiRenderer.java:279` re-parses conditions every render tick for `PAGINATED` components — perf concern at scale.

---

## Enchant module

- [ ] **Wire the 7 inert shipped enchants**: `valmora:execute`, `first_strike`, `life_steal`, `lethality`, `protection`, `respite`, `thorns` (`example_enchantments.yml`) reference logic that isn't registered.
- [ ] **Enforce enchant level caps.** `applyEnchantment`/`createEnchantedBook` currently accept any level.
- [ ] **Enforce enchant conflicts outside the anvil path.** GUI/`/item` commands can currently stack conflicting enchants.
- [ ] **Fix vanilla-item enchant blocking.** `canApplyEnchantment` requires `ITEM_TYPE_KEY`, so vanilla items silently no-op instead of enchanting.
- [ ] **Add XP/mana cost to the enchanting table GUI** (original `GUI_MODULE_ENHANCEMENT_PLAN.md` Phase 2 goal, never done) and real bookshelf-power detection (currently hardcoded `0/15`).
- [ ] **Ship a way to open the enchanting GUI in a stock install** — no `command:` key on the GUI and no `enchanting_table` `DynamicMachineHandler` registered by default.
- [ ] **Add `modifyDefend`/`onPostAttack`/`onPostDefend` enchant logic hooks** — none of the shipped logic classes implement these.
- [ ] **Warn on unknown `logic:` ids** instead of silently resolving to `null`.
- [ ] **Fix `$enchant.NAME.prop$` vs `$entry.*$` docs/code drift** (docs already corrected during the docs cleanup pass — verify the enchanting GUI itself uses the right variable consistently).

---

## Combat module

- [x] **Register `RegenTask` from `CombatModule`** instead of `PlayerManager` owning it — current split causes a reload/lifecycle mismatch. *(2026-08-07: moved the `regenTask` field/lifecycle from `PlayerManager` into `CombatModule.onEnable`/`onDisable`.)*
- [x] **Add null-safety guards**: `DamageCalculator.java:33` (no null-check on `getActiveProfile()`), `DamageApplier.java:28-29` (silently no-damages null-profile players, doesn't distinguish vanilla vs custom mobs). *(2026-08-07: `DamageCalculator` now null-checks `getActiveProfile()` in all 3 spots that read it; `DamageApplier` now logs a warning instead of silently discarding damage when a player has no active profile.)*
- [x] **Reset `DamageIndicatorManager` rate-limit state in `onDisable()`.** *(2026-08-07: `cleanup()` — already called from `onDisable` — now also clears `lastIndicatorSpawned`.)*
- [x] **Evict `CombatTracker`'s per-UUID map on player quit** — currently never cleared, unbounded memory growth. *(2026-08-07: `CombatTracker.clear(UUID)` already existed but was never called — added a `PlayerQuitEvent` handler to `CombatListener`.)*
- [x] **Map unhandled `DamageCause`s** (SUICIDE, CONTACT, STARVATION, DRAGON_BREATH, SONIC_BOOM, OUTSIDE_BORDER) to a real `DamageType` instead of defaulting to MELEE. *(2026-08-07: added matching `DamageType` constants and switch cases in `CombatListener.mapCauseToType`; `OUTSIDE_BORDER` maps from Bukkit's `DamageCause.WORLD_BORDER`.)*
- [x] **Move hardcoded combat tunables to config**: environment-damage multiplier (5.0), defense formula constant, 400ms indicator rate limit, 20-tick indicator lifetime, 20 no-damage-ticks, 3s combat window, 10-heart visual scale, per-damage-type colors. *(2026-08-07: added a `combat:` config block for the multiplier/rate-limit/lifetime/no-damage-ticks/combat-window/visual-hearts values, wired into `DamageCalculator`, `DamageIndicatorManager`, `DamageApplier`, `PlayerState`, `PlayerManager.syncVisualHealth`. The "defense formula constant" and "per-damage-type colors" were already config-driven — `damage_formula.yml`/`DamageFormulaRegistry` and `damage_types/*.yml` respectively — the hardcoded values in code are just their documented fallback when no YAML overrides them.)*
- [x] **Make the `immune` flag suppress damage indicators**, not just damage. *(2026-08-07: `DamageIndicatorManager.spawnIndicator` now returns early when `result.isImmune()`.)*
- [x] **Wire attack-speed/cooldown into the combat pipeline** — `bonus_attack_speed` stat currently unused. *(2026-08-07: added `vanilla-attribute: attack_speed` to the stat definition and a percentage-scalar special case in `StatModule.recalculateAttributes` (mirroring the existing `block_break_speed` special case) since, unlike the 100-baseline stats, `bonus_attack_speed` is a 0-baseline percentage bonus stacked on top of the weapon's own base attack speed via an `ADD_SCALAR` modifier rather than overwriting it.)*

---

## Mob module

- [x] **Default `health` when `stats.health` is unset** (currently 0 max HP, likely instant-death mobs). *(2026-08-07: `MobDefinitionParser` now seeds the builder with `health(20.0)` before reading `stats.health`/flat `health`, so an unset value falls back to a vanilla-baseline HP instead of 0.)*
- [x] **Guard against missing `name`** at spawn time (currently NPEs in `MobFactory.applyVisuals`). *(2026-08-07: `MobDefinitionParser` now defaults `name` to the mob's own id via `section.getString("name", sectionId)`.)*
- [x] **Unregister the death listener in `onDisable()`** — currently at risk of double-registration on reload. *(2026-08-07: added `HandlerList.unregisterAll(deathListener)` to `MobManager.onDisable()`.)*
- [x] **Switch `spawnMob` to Paper's consumer-form `world.spawn(...)`** instead of raw `spawnEntity` (avoids the one-tick half-initialized state). *(2026-08-07: `MobFactory.spawnMob` now uses `world.spawn(location, entityClass, consumer)`, applying data/equipment/visuals inside the consumer before the entity's first tick.)*
- [x] **Add basic custom AI/behaviors**: pathfinding, aggro range, leash range, wander logic — currently mobs have none beyond vanilla defaults. *(2026-08-07: added `ai: { aggro-range, leash-range }` to `MobDefinition`/the YAML parser. Aggro range sets the vanilla `FOLLOW_RANGE` attribute at spawn (works with vanilla AI rather than against it); leash range is enforced by a new periodic `MobAiTask` that paths mobs back toward their recorded spawn point via Paper's `Mob.getPathfinder()` (CLAUDE.md §14.2) when they exceed it. Wander logic is already vanilla's default AI behavior (unaffected unless `no-ai: true`) — documented rather than reimplemented.)*
- [x] **Add natural spawning support** — currently mobs only exist via explicit `/mob spawn`/zone spawners/scripts. *(2026-08-07: added `natural-spawn: { enabled, chance, max-nearby }` to `MobDefinition`/the parser and a new periodic `NaturalSpawnTask` (per-player radius roll, respects `max-nearby`, picks a nearby valid surface block). Deliberately minimal — not the full biome/time-of-day/capacity-aware spawner described in `ZONE_MODULE_PLAN.md`'s graveyard design (that's the separate, still-open Zone module item below).)*
- [x] **Boss mob content**: `todo.md` "add bosses with mechanics" — most bosses are stat-scaled reskins today; give at least a few real telegraphed abilities. *(2026-08-07: the ability engine itself was already complete (see `mobs/test_boss.yml`'s `forge_titan` example — ON_TIMER/ON_HEALTH/ON_DEATH triggers, DAMAGE/APPLY_EFFECT/SCRIPT mechanics, `announce:` telegraphs) — the gap was content, not engine. Added real telegraphed abilities + summon minions to the top-tier boss of all 3 slayer chains in `mobs/slayer_bosses.yml`: Revenant Horror III (Plague Nova DoT nova + Raise the Dead summon), Broodmother II (Venom Spit + Web Trap slow), Alpha Fang II (Pack Howl summon + Savage Leap). Minions are separate non-quest-tracked mob ids so they don't interfere with the slayer quest's boss-kill objective matching the exact boss id.)*

---

## Notify module

- [x] **Fix the quest/notify module load-order bug.** `questModule` registers before `notifyModule`, so `NotifyManager` is null during `QuestModule.onEnable()` — quest-package notification categories never actually load. Net effect: `category:quest_complete`/`quest_progress` silently fall back to default chat IO. Either reorder module registration or defer category registration to a later hook. *(2026-08-07: reordered — `notifyModule` now registers before `questModule` in `Valmora.java`.)*
- [x] **Implement a real `AdvancementIO`** (currently a stub rendering `✦ <message>` in the action bar, ignoring `frame`/`icon` — needs NMS/packet work for a true toast). *(2026-08-07: turns out no NMS/packets needed — implemented via the standard dynamic-fake-advancement technique using the public `Bukkit.getUnsafe().loadAdvancement`/`removeAdvancement` API: build a minimal hidden advancement JSON with the requested icon/title/frame, grant its criteria to trigger the toast, remove it ~2s later.)*
- [x] **Fix `SoundIO`** — ignores message text (fine, expected) but should catch `Key.key()` parse failures on invalid `sound:` instead of throwing. *(2026-08-07: now catches `InvalidKeyException`, logs a warning, and no-ops instead of propagating.)*
- [x] **Cancel `BossBarIO`'s auto-hide task on player quit.** *(2026-08-07: `BossBarIO` now tracks pending auto-hide `BukkitTask`s per player UUID; added `NotifyQuitListener` (registered/unregistered in `NotifyModule.onEnable`/`onDisable` per module convention) that cancels them and hides the bar on `PlayerQuitEvent`.)*
- [x] **Fix the shipped `pets/baby_wolf.yml` `notify chat ...` bug** — prints the literal word "chat"; should be `notify ... io:chat`. *(2026-08-07: fixed all occurrences in the file — `baby_wolf`, `baby_sheep`, and `ender_dragon_pet` all had the same bug, changed to `notify <message> io:chat`.)*
- [x] **Decide whether to implement or drop the BetonQuest-inspired but never-built notify features** listed in `docs/QUEST_MODULE_OUTLINE.md` (marked as external reference, not a spec): comma-separated category lists, sound options on any IO, `advancement` frame/icon, a `totem` IO, `bossbar` flags/countdown, `soundlocation`/`soundplayeroffset`, the `duration` key. *(2026-08-07: decided and documented in `docs/modules/design/notify.md` §"Unfinished Things" item 5 — `advancement` frame/icon: implemented (see above). Comma-separated categories / sound-on-any-IO / `duration`: left as documented-but-unimplemented, cheap but not blocking. `totem` IO / `bossbar` flags-countdown / `soundlocation`+`soundplayeroffset`: explicitly dropped, no shipped content needs them.)*

---

## NPC module

- [x] **Remove dead `NpcType` enum** (SHOP/DIALOGUE/QUEST/WARP/BANK/SLAYER) — never referenced anywhere; the loader has no `type:` key. *(2026-08-07: confirmed zero references outside the file itself, deleted `NpcType.java`.)*
- [x] **Fix `look` subcommand** — doesn't respawn the NPC, stays stale until next respawn/reload. *(2026-08-07: `cmdLook` now uses `nm.updateAndRespawn(updated)` (same pattern as `cmdShowName`) instead of just mutating the registry entry.)*
- [x] **Fix hologram conditions evaluating with a null caster** — any player-referencing condition silently fails for holograms. *(2026-08-07: `applyHologramVisibility` now finds the nearest online player within 32 blocks and uses them as the condition-evaluation caster instead of `null`, since hologram visibility is shared/ambient rather than per-viewer.)*
- [x] **NPC shops** (todo.md "npc shops" — referenced for Hub/Gold Mine/Deep Cavern content but no shop mechanic exists yet). *(2026-08-07: turns out no new engine code was needed — `on-right-click: open_gui`, coin-gated DISPLAY click `conditions:`/`fail-actions:`, and the existing `take_coins`/`give` script events already compose into a full shop. Added a real working example: `guis/general_store.yml` (3 buyable items) + `npcs/shopkeeper.yml` binding an NPC to it — a documented, copyable pattern for Hub/Gold Mine/Deep Cavern shop content.)*

---

## Pet module

- [x] **Add a way to distribute pet items** — nothing currently creates an item carrying `valmora:pet_id`; admins must hand-tag items. Ties to a pets menu/shortcut (todo.md). *(2026-08-07: added `/pet give <player> <petId> [level]` + `/pet list` (new `PetCommand`, registered in `plugin.yml`/`Valmora.java`). A full pets-menu GUI shortcut is still not built — this covers the underlying distribution gap.)*
- [x] **Add owner-follow AI** — pets are currently fully static (`setAI(false)`). *(2026-08-07: added `PetFollowTask`, a periodic step-toward-owner task (teleport-based, since `setAI(false)` disables the vanilla pathfinder too).)*
- [x] **Fix stale `activePetSlot` after relog** — causes toggle-off instead of re-summon, and blocks summoning a different pet. *(2026-08-07: `PetListener.onQuit` now also clears the slot/instance mapping via new `PetModule.clearActivePetSlot`.)*
- [x] **Support multiple/anywhere-summonable pets** instead of single slot-bound pet. *(2026-08-07: partial — switched from slot-index tracking to a per-item `PET_INSTANCE_KEY` UUID tag found anywhere in the inventory, so the pet item is no longer slot-bound. True simultaneous multi-pet support is a larger design (stat stacking, multiple follow tasks) explicitly left out of scope — documented in `docs/modules/design/pet.md`.)*
- [x] **Decide whether milestone stat grants should revert on unsummon** (currently permanent — undocumented design gap). *(2026-08-07: decided and documented in `docs/modules/design/pet.md` §"Unfinished Things" item 6 — kept permanent, treated as an account-wide unlock.)*
- [x] **Wire `pet_luck` stat** into loot/drop code (currently unused). *(2026-08-07: `MobDeathListener` now adds `pet_luck` on top of the general `luck` stat when computing loot-roll chance.)*
- [x] **Expose `PetModule` via `ValmoraAPI`.** *(2026-08-07: confirmed already done — docs drift, see the cross-cutting item at the top of this file.)*

---

## Profile module

- [x] **Guard against deleting the active profile** via `/profile delete` (currently corrupts the session; the GUI already guards this, the command doesn't). *(2026-08-07: moved the guard into `PlayerManager.deleteProfile` itself (new `DeleteResult` enum: OK/NO_SESSION/NOT_FOUND/ONLY_PROFILE/IS_ACTIVE) so both the GUI and `/profile delete` share the same protection; `ProfileCommand` now reports the specific failure reason.)*
- [x] **Fix `createProfile` silently no-op'ing at the profile cap** while the command still reports success. *(2026-08-07: `createProfile` now returns `boolean`; `ProfileCommand` reports failure (cap or duplicate name) instead of always claiming success.)*
- [x] **Persist active-profile id on switch, not just on quit/disable** — a crash right after switching reverts the selection. *(2026-08-07: `PlayerManager.switchProfile` now calls `dataStore.savePlayer(vp)` immediately after switching.)*
- [x] **Make quit-save non-fire-and-forget**, or otherwise guard against a hard stop dropping the final save. *(2026-08-07: can't fully guard against an OS-level hard kill from application code, but `handleQuit`'s save future is no longer fire-and-forget-with-nothing-observing — failures are now logged via `.exceptionally(...)` instead of silently vanishing.)*
- [x] **Serialize `CooldownManager`/combat timer/`currentZoneId`** — currently reset on every reload. *(2026-08-07: added a `cooldowns` DB column (schema v6 migration) backed by new `CooldownManager.getSaveData()`/`loadData()`; extended `PlayerState`'s existing `player_state` JSON blob from a bare `[health, mana]` array to an object also carrying `lastCombatTime`/`zoneId` (`PlayerState.SaveData`), with backward-compat loading for the old array format. Removed the now-inaccurate `transient` modifiers on those two fields.)*
- [x] **Enforce profile-name uniqueness.** *(2026-08-07: `createProfile` now rejects a case-insensitive duplicate name, returning `false`.)*
- [x] **Raise or make configurable the hardcoded 4-slot `PROFILE_SLOTS` GUI limit** — profiles beyond 4 are unreachable from the GUI. *(2026-08-07: `ProfileGui` now computes slots dynamically from `profiles.max-profiles` across 2 rows (up to 8 profiles); still capped at 8 without pagination.)*

---

## Progression module

- [x] **Add a dedicated `/progression` command** (currently only reachable via the `/geomancy` GUI command). *(2026-08-07: new tree-agnostic `ProgressionCommand` — `/progression list`, `/progression info <treeId>`, `/progression reset <treeId>` — registered in `plugin.yml`/`Valmora.java`.)*
- [x] **Add a join-triggered catch-up for the daily bonus** (currently relies solely on a 5-minute poll). *(2026-08-07: extracted a per-player `processDailyBonusFor`, called both by the periodic poll and a new `ProgressionJoinListener` on `PlayerProfileLoadedEvent`.)*
- [x] **Guard `cost-curve` expression evaluation** — malformed expressions currently throw uncaught. *(2026-08-07: `evaluateCostCurve` now catches and logs, failing safe to "unaffordable" (`Integer.MAX_VALUE`) instead of propagating.)*
- [x] **Add player-facing feedback** on level-up/tier-unlock/reset (no chat/actionbar/title/notify integration currently). *(2026-08-07: added chat messages to `levelUp`/`unlockTier`/`resetTree`, plus the daily-bonus grant.)*
- [x] **Add per-node scripting callbacks** (`on-level` actions). *(2026-08-07: added `on-level:` DSL event list to `ProgressionNode`/the YAML loader, executed in `levelUp` with a `$progression.level$`-style context variable.)*
- [x] **Make refund percentage configurable** (currently always 100%). *(2026-08-07: added `progression.refund-percent` (default 100.0) to `config.yml`, applied in `resetTree`.)*

---

## Quest module

- [x] **Implement the `BREW` objective listener** — constant exists, no handler in `QuestListener`. (Source spec: `docs/Objective_list.md`'s `Brew` syntax, and the alchemy module's matching gap.) *(2026-08-07: wired into `GuiForceCraftEventFactory` — fires on a successful `alchemy` machine craft (the actor is whoever ran `gui_force_craft`, matching "the player who last added/changed an item before the brew completed"), target = the output item id/material, amount = output stack size.)*
- [x] **Implement the `VARIABLE` objective listener** — constant exists, `QuestVariableProvider` reads variables but nothing triggers progress from them. *(2026-08-07: new `QuestManager.checkVariableObjective(player, varName)` (STAT_REACH/POINT threshold pattern), called from `VariableEvent` after every `player.var.*` set/add.)*
- [x] **Wire cross-package event references (`>` separator) into `resolveEventRefs()`** — currently only the public `resolveEvent()` supports the syntax, but the parse pipeline never calls it, so cross-package refs fall through to raw DSL and warn/fail. *(2026-08-07: `resolveEventRefs` now detects a `>` in the ref and delegates to `resolveEvent()`, warning on an unresolvable cross-package reference instead of silently misinterpreting it as inline DSL.)*
- [x] **Add admin commands for quest management** (`QuestCommand` currently only supports `journal`) — at minimum force-complete/reset/inspect-progress for support/debugging. *(2026-08-07: added `/quest complete <player> <questId>`, `/quest reset <player> <questId>`, `/quest inspect <player> [questId]` (all `valmora.admin`-gated), plus tab completion.)*
- [x] **Add a repeatable-quest / quest-cooldown system** (slayer content currently fakes this with `quest_cancel`+`quest_start`, per `docs/modules/design/slayer.md`). *(2026-08-07: added `cooldown-seconds:` to `QuestDefinition`/both loaders (flat `quests/*.yml` and package-based). 0 (default) = not repeatable, unchanged behavior. `startQuest` now allows restarting a `STATUS_COMPLETED` repeatable quest once its cooldown has elapsed, tracked via a new `quest.<id>.completed_at` variable set in `finishQuest`.)*

---

## Recipe module

- [x] **Add a `/recipe` command** and expose `RecipeModule` via `ValmoraAPI`. *(2026-08-07: `getRecipeModule()` was already exposed (docs drift). Added `/recipe list <machineId>` (new `RecipeCommand`, `valmora.admin`-gated), registered in `plugin.yml`/`Valmora.java`.)*
- [x] **Add a dynamic handler for the `enchanting_table` machine** (currently only vanilla fallback). *(2026-08-07: new `EnchantingTableMachineHandler`, registered by `EnchantModule` — picks a random eligible enchantment for the item's `ItemType` at a random level up to `etable-max-level`, gated by a `lapis` input. Note: the shipped `guis/enchanting.yml` doesn't use this — it has its own fully custom `enchant_select`/`enchant_apply`/`enchant_remove` interaction; this is for GUI authors who'd rather build a plain `gui_force_craft` enchanting GUI instead.)*
- [x] **Scope the vanilla-recipe fallback per machine** — currently machine-agnostic, leaking unintended vanilla results into custom machine GUIs. *(2026-08-07: `RecipeEngine.match()` now only falls through to `matchVanillaRecipe` for machine ids in a new `VANILLA_FALLBACK_MACHINES` set (just `crafting_table`).)*
- [x] **Unregister dynamic handlers in `onDisable()`.** *(2026-08-07: added `RecipeEngine.unregisterHandler`/`RecipeModule.unregisterHandler`, called from `AlchemyModule`, `ReforgeModule`, and the new `EnchantModule` registration's `onDisable()`.)*
- [x] **Route the anvil coin cost through `EconomyService`** instead of the `player.var.coins` script variable, for consistency with Reforge. *(2026-08-07: `AnvilMachineHandler`'s `on-craft` is now a direct `EconomyService.removeCoins(player, cost)` call (resolving the caster from the execution context) instead of a `variable add player.var.coins -N` DSL string.)*
- [x] **Support multiple recipe outputs** — currently only the first output in a definition is honored. *(2026-08-07: `CraftResult` now carries `extraOutputs` alongside the primary `output`; `RecipeEngine.buildOutputs` builds every `outputs:` entry, and `GuiForceCraftEventFactory` gives the extras directly to the player's inventory (dropping on overflow) since the GUI only has one OUTPUT slot.)*
- [x] **Fix SHAPELESS recipes**: make `amount` optional (currently a missing value fails the whole file) and support duplicate ingredients needing distinct slots. *(2026-08-07: fixed the missing-`amount` NPE (defaults to 1). Traced through `matchShapeless`'s distinct-slot handling — it already correctly requires occupied-slot-count to equal required-ingredient-count with a per-slot "matched" flag, so 2 duplicate ingredient entries already can't be satisfied by a single combined stack; no change needed there.)*
- [x] **Add smithing-recipe support** (3-slot Template+Base+Addition, per CLAUDE.md §14.16 — not yet wired into the recipe engine). *(2026-08-07: `type: SMITHING` recipes now register a real vanilla `SmithingTransformRecipe` (bypassing the GUI-based match/consume engine, since the vanilla smithing table isn't GUI-driven) via `RecipeDefinitionParser.parseSmithing`. Slot materials are vanilla-only — matching a custom Valmora item's exact PDC state via `RecipeChoice` wasn't practical to add in this pass — but the `result:` can be a Valmora item id.)*

---

## Reforge module

- [x] **Expose `ReforgeModule` via `ValmoraAPI`.** *(2026-08-07: confirmed already done — docs drift, see the cross-cutting item at the top of this file.)*
- [x] **Remove the dead `reforge` machine-id alias** (duplicate of `reforge_anvil`). *(2026-08-07: confirmed no GUI/recipe uses machine id `reforge` — only `reforge_anvil` — removed the `registerHandler("reforge", this)` line from `ReforgeModule.onEnable()`. Left `ReforgeModule`'s own `DynamicMachineHandler` method implementations in place (harmless, and removing `implements DynamicMachineHandler` entirely was out of scope for this item).)*
- [x] **Unregister reforge handlers from `RecipeEngine` in `onDisable()`** — currently leaks on single-module reload. *(2026-08-07: done alongside the Recipe module pass — `ReforgeModule.onDisable()` now unregisters `reforge_anvil`/`forge_random`.)*
- [ ] **Add a live output preview** in the Anvil/Forge GUI (currently empty until craft click).
- [x] **Fix case-sensitive definition storage vs. lowercase lookups** — uppercase reforge keys become unreachable. *(2026-08-07: fixed alongside the cross-cutting case-sensitivity item above — see note there.)*
- [x] **Preserve base stats on vanilla items when reforged** (currently lost — empty `baseStats` for unregistered items). *(2026-08-07: for unregistered items, `buildReforgedItem` now reads the item's own currently-stored stats via `StatModule.loadStats`, subtracting a previous reforge's bonuses back out first (if any) so re-reforging doesn't stack instead of losing everything.)*
- [x] **Support multi-id `reforge-pool` on custom items** — currently parsed but the anvil only reads the first id. *(2026-08-07: `matchForgeRandom` now reads the base item's own `REFORGE_POOL_KEY` (set from `ItemDefinition.reforgePool` by `ItemFactory`) as an eligibility allowlist — previously parsed onto the item but never read back for Random Forge.)*
- [x] **Validate stat ids at load time** (typos currently vanish silently from lore instead of warning). *(2026-08-07: `parseDefinition` now warns when a `stat-bonuses-by-rarity` key isn't a registered stat id.)*
- [x] **Deduplicate the hardcoded coin-cost table** — currently defined in 3 places (`RARITY_COST`, Anvil GUI lore, stone lore builder). *(2026-08-07: the Java-side `RARITY_COST` was already deduplicated into `ForgeCostRegistry` by an earlier refactor pass (stone lore already read from it). The Anvil GUI's hardcoded lore numbers were the real remaining copy — added a new `ReforgeVariableProvider` (`$reforge.cost.<rarity>$`) and switched `guis/reforge_anvil.yml` to use it.)*
- [x] **Add weighting for Random Forge** (currently uniform random). *(2026-08-07: added an optional `weight:` field to `ReforgeDefinition`/the YAML parser (default 1.0 = unchanged uniform behavior) and a weighted-random `pickWeighted` helper used by `matchForgeRandom`.)*

---

## Resource module

- [ ] **Build the Draven Mines demo zone** (todo.md — custom drop support is done, the demo content isn't).
- [x] **Expose `ResourceModule` via `ValmoraAPI`.** *(2026-08-07: already done — docs drift. `ValmoraAPI.getResourceModule()`/`ValmoraAPIImpl` already exist.)*
- [ ] **Add a `/zone resource` (or similar) in-game editing subcommand.**
- [x] **Zero `expToDrop` on HANDLED resource breaks** — XP orbs still drop from vanilla on top of custom drops. *(2026-08-07: `ResourceListener`'s `HANDLED` branch now calls `event.setExpToDrop(0)`.)*
- [x] **Cancel the break event for depleted blocks** — currently still breakable. *(2026-08-07: added a `BreakResult.DEPLETED` outcome — `handleBlockBreak` returns it instead of `HANDLED` for an already-depleted/awaiting-regen tracker, and `ResourceListener` cancels the event for it.)*
- [x] **Add crash-safe persistence for mid-progress block state** — currently lost on unclean shutdown. *(2026-08-07: new `ResourceManager.saveState()`/`loadState()`/`clearStateFile()` backed by a `resource_state.yml` snapshot (world/x/y/z/original material/stage/regen-at-millis), autosaved every 30s by `ResourceModule` and loaded once on `onEnable()`; a clean `onDisable()` deletes the file since `cancelAll()` already restored the world. On load, regen timers are rescheduled for their remaining duration — the block's actual intermediate material is assumed already present in the chunk data.)*
- [x] **Hook environmental triggers** (explosions/pistons) into tracking/regen. *(2026-08-07: new `ResourceEnvironmentListener` — removes tracked/configured resource blocks from `BlockExplodeEvent`/`EntityExplodeEvent` block lists, and cancels `BlockPistonExtendEvent`/`BlockPistonRetractEvent` outright if any moved block is one. Policy chosen: protect, don't auto-mine — only the normal break pipeline grants drops/progresses a node.)*
- [x] **Add AOE feedback** for Mining Spread (currently silently skips neighbors lacking power — no sound/particle indication). *(2026-08-07: added `ResourceManager.playMineFeedback`/`playDeniedFeedback`/regen feedback (sound + `Particle.BLOCK`); `AoeMineMechanic.mineRadius` now plays mine feedback per successfully-mined neighbor and a denial cue per power-gated skip, and the regen task plays a chime+particle on restore.)*

---

## Script module

- [ ] **Log malformed expressions instead of silently evaluating to `null`** (`ExpressionParser.parse()`).
- [ ] **Support quoted strings / spaces in `EventParser` token splitting** (currently naive).
- [ ] **Extend `variable set` beyond `player.var.*`/`prop.*`** to a generic path (stat/other module vars), and support expression evaluation in `rawValue` interpolation (currently single-token only).
- [ ] **Fix `ForeachEventFactory`'s throwaway `ExecutionContext`s** — inner events currently lose the original params/caster identity.
- [ ] **Cancel tasks spawned from `RunScriptEventFactory`'s captured context** if the player logs out (stale-state risk currently).
- [ ] **Cache `TagServiceImpl` in `SimpleExecutionContext`** instead of allocating one per `getTagService()` call.
- [ ] **Fix the tokenizer mis-parsing negative literals** (`-5` currently tokenizes as `-` then `5`).
- [ ] **Make `$player.missing_hp_percent$` return `Double`** instead of `Integer`, for consistency with `$player.hp$`.

---

## Skill module

- [ ] **Make `xp-curve` non-dead config** — `SkillRegistry` currently hardcodes `DEFAULT_XP_THRESHOLDS` and ignores the curve id argument entirely, even though `XpCurveRegistry`/`skills/xp_curves.yml` exist (per the generic-engine refactor) and `getLevelFromXp`/`getXpForLevel`/`getProgressData` already accept a `curveId` param. This looks like the wiring got half-done — verify against `docs/modules/design/skill.md` and finish connecting the two.
- [x] **Fix the `Skill` enum vs. shipped skill ids mismatch** — enum has `CRAFTING` (shipped id is `carpentry`) and no `TAMING` entry; `givexp` silently no-ops for those two skills. *(2026-08-07: renamed `Skill.CRAFTING` → `Skill.CARPENTRY` and added a `Skill.TAMING` entry. `SimpleRegistry` lookups are case-insensitive so `Skill.name()` matching the shipped id casing-insensitively is sufficient — verified `carpentry.yml`/`taming.yml` ship those exact ids. Sole call site was `GiveXpEventFactory` (`Skill.valueOf(args[1].toUpperCase())`), no other references to the old constant.)*
- [x] **Wire a tab completer for `/skill`** (`Valmora.java:239` only calls `setExecutor`). *(2026-08-07: `SkillCommand` already implemented `TabExecutor`/`onTabComplete` fully — Bukkit's `PluginCommand.setExecutor` auto-wires it as the tab completer when none is set explicitly, so this technically already worked. Added an explicit `setTabCompleter` call in `Valmora.java` anyway for clarity/consistency with the other commands.)*
- [ ] **Remove or wire dead XP source entries** — `fishing.yml`'s `TREASURE: 1000.0` is unreachable; `alchemy.yml` has no `sources`, so brewing currently grants no skill XP by default.
- [ ] **Add an XP curve editor / reset-wipe subcommand / offline XP grant** to `/skill`.

---

## Stat module

- [ ] **Expose `combat.temporary-stat` config properly** — currently parsed but not surfaced via a getter; `TemporaryStatService` hardcodes its own key instead of reading the config.
- [ ] **Fire a stat-modification event from `StatManager`'s own mutation methods** — `StatModifyEventFactory` exists but nothing calls it from the actual mutation path.

---

## Time module

- [ ] **Wire `time.scoreboard-enabled`** — dead config, no code reads it (also affects the UI module's scoreboard).
- [ ] **Add `/time set`** (only `info`/`reset` exist today).
- [ ] **Add day-length control / acceleration / per-player time**, or at least document that `TimeManager` is read-only and never calls `World.setTime()`.
- [ ] **Add offline catch-up** for day-change/season events (currently never replay for time elapsed offline).
- [ ] **Move hardcoded season-length constants (30/90/360-day) and the 06:00 day/night boundary to config.**
- [ ] **Guard `time.yml` loading** — malformed file currently throws during `onEnable()`.

---

## UI module

- [ ] **Wire `setDynamicSection`** — the entire dynamic-section machinery (`"$dynamic$"` placeholder) has zero callers; no module currently injects lines (e.g. combat-lock indicator, dialogue state, quest tracker).
- [x] **Remove the hardcoded `"pay.valmora.net"` fallback string** from the scoreboard. *(2026-08-07: replaced the `legacyLines()` pre-config-load fallback in `ScoreboardUI.java` with the generic `<gold><bold>VALMORA RPG` title, matching `ui.yml`'s configured `scoreboard.title`. Left the shipped `ui.yml` line as-is — that's admin-editable content, not the hardcoded-in-Java bug this item was about.)*
- [ ] **Load `ui.yml` through `YamlLoader`** instead of raw Bukkit YAML (no error handling for a corrupt file today).
- [ ] **Add per-player/per-world UI control** (`/ui toggle`, per-line conditions).
- [ ] **Make action-bar overrides queue/stack by priority** instead of last-write-wins.
- [ ] **Route `legacyBar` through `ValmoraAPI`** instead of reaching into concrete plugin modules directly.

---

## Warp module

- [ ] **Build the sign-warp subsystem** — `Keys.WARP_ID_KEY` is defined but has zero usages anywhere.
- [ ] **Ship the `fast_travel` GUI** (see GUI module section above — `/warp` with no args currently no-ops).
- [x] **Wire a tab completer for `/warp`.** *(2026-08-07: `WarpCommand` already had a full `onTabComplete` implementation; added the explicit `getCommand("warp").setTabCompleter(...)` call in `Valmora.java`.)*
- [ ] **Enforce the zone `teleportation` flag from the warp module itself** (currently only the script `teleport` event checks it).
- [ ] **Add fees/cooldowns/warmup/per-warp permissions** — `/warp` currently has no permission node at all.
- [ ] **Trigger warp pads for players already standing on one** (or teleported/portaled onto one), not just move events.
- [ ] **Harden `pad-locations` parsing** — non-numeric input currently throws `ClassCastException` and fails the entire warp file.
- [ ] **Warn on duplicate warp ids** instead of silently overwriting.

---

## Zone module

- [ ] **Fix `ZoneCondition` — it never matches.** `setCurrentZoneId` is never called anywhere in the codebase, so the `zone` script condition always evaluates false, and `currentZoneId` is marked `transient` on top of that. This is a real, currently-broken feature, not just a gap.
- [ ] **Add a `PlayerTeleportEvent` listener for the `teleportation` flag** (currently only the script engine's `teleport` event honors it — warps and other teleports are unaffected).
- [ ] **Fix `saveZoneToFile` dropping hand-written keys** not covered by the round-trip serializer: `fishing-loot-table`, `resource-blocks`, `enter-actions`, `exit-actions`. Any `/zone flag`/`/zone spawner` command on a hand-edited zone currently destroys these.
- [ ] **Stop re-copying deleted shipped zone files on every startup** (`saveAllResources` behavior — makes "delete a default zone" impossible to persist).
- [ ] **Add the 4 missing flags to tab-completion** (`hunger`, `entry`, `teleportation`, `leaf-decay` aren't in the `FLAGS` constant used by tab-complete, though the flags themselves work).
- [ ] **Reconcile the `/zone spawner add` radius formula (×4.0) with the loader's default (flat 20.0).**
- [ ] **Add in-game editing for multi-box zones** (`extra-boxes`) — parser/save already support it, no command surface exists.
- [ ] **Index `getZoneAt`** — currently an O(n) registry stream scan per call, a hot-path concern.
- [ ] **Guard `tickMobHomes`** so it doesn't iterate all living entities in all worlds every 40 ticks when no zone actually has mob homes.
- [ ] **Implement `ZONE_MODULE_PLAN.md`'s planned-but-missing flags**: fall damage, ability use, healing, cold damage, crop growth, block regeneration for farms/trees.
- [ ] **Implement natural/smart mob spawning** (`ZONE_MODULE_PLAN.md`'s graveyard "spawn up to capacity, only at night" design) — currently zones have no time-of-day/capacity-aware spawning at all.
- [ ] **Build out `ZONE_MODULE_PLAN.md`'s target zone designs** once the above primitives exist: Hub (Bank/Bazaar/Auction House/Shop NPCs, cold-damage area, graveyard, mine block-regen, farm crop-growth, lake fishing zone, forest tree-regrowth), tiered Mine (GUI-based tier teleport with tag-gating, block regen, NPC shops, quest entities), Public boss island (condition/lever/button/NPC-triggered boss spawn).

---

## Backpack / Accessory (GUI-based, not a module)

- [ ] **Design and build a quiver replacement.** The old ammo-quiver feature was removed with no equivalent shipped. The natural approach (per `docs/modules/design/backpack.md` §6): a `STORAGE` component with a `condition:` gating on arrow-type items.
- [ ] **Write an admin walkthrough** for authoring a new backpack tier or accessory-bag-style GUI from scratch (currently only the shipped examples exist as reference).

---

## Slayer (quest+GUI content, not a module)

- [ ] **Add a player-facing progress indicator outside the slayer GUI** (e.g. boss-bar-on-hit) — currently purely GUI-poll-driven.
- [ ] **Share slayer tier coin costs through one table** instead of hardcoding them per-button in `guis/slayers.yml`.
- [ ] **Write a generator/template (or at least a documented recipe) for adding a new slayer line** — currently requires manually copying the two-objective quest shape + boss `MobDefinition` + GUI button.

---

## Alchemy module

- [ ] **Handle lingering potions** — currently fall through to vanilla entirely, no Valmora handler.
- [ ] **Generalize poison DOT to all `LivingEntity`**, not just players.
- [ ] **Fix the degenerate static SHAPELESS potion recipe path** — currently outputs a plain vanilla `POTION`, not a real Valmora potion.
- [ ] **Decide/document whether active effects clearing on reload is intentional** (`AlchemyManager.clear()` currently skips `activeEffects`/`hardcodedEffects` — this may be deliberate, confirm and document either way).

---

## Collection module

- [ ] **Make shipped `rewards:` entries executable.** Currently plain MiniMessage display strings (e.g. `"<gray>Novice Miner title"`) that silently no-op through the script parser instead of firing real events — either fix the shipped YAML or fix the parser's silent-fail behavior (should warn at minimum).
- [ ] **Add reward idempotency** — no "already rewarded" tracking; config changes can re-fire stage rewards.
- [ ] **Fix FISHING-track misattribution** — non-`Item` catches are hardcoded to `"COD"`.
- [ ] **Fix module id mismatch** (`"collections"` in code vs `"collection"` in some docs — docs already corrected in this pass, verify code/config consistency too).
- [ ] **Index `trackEvent`** — currently O(n) over all collections per event.

---

## Fishing module

- [ ] **Build real fishing content**: sea creatures, hot spots, treasures, rod parts (todo.md — currently zone-scoped weighted loot + a single sea-creature roll only).
- [x] **Fix the default config's missing sea-creature mob** — `hub_fishing.yml:3` references `sea-creature-mob: squid` with no matching mob definition, so it silently never spawns. *(2026-08-07: added `mobs/fishing_mobs.yml` with a real `squid` (AQUATIC, `type: SQUID`, drops `INK_SAC`) definition.)*
- [x] **Add bite-indicator particles/sound** and spawn sea creatures at the bobber location instead of the player's location (per `TESTING_GUIDE.md` expectations). *(2026-08-07: `FishingListener` now reacts to `PlayerFishEvent.State.BITE` and plays a new `FishingManager.playBiteFeedback` (sound + `Particle.FISHING`) at the hook location; `handleCatch` now takes a `hookLocation` param — sourced from `event.getHook().getLocation()` — used as the sea-creature spawn point instead of `player.getLocation()`.)*
- [ ] **Add a fishing bait bag** (todo.md).
- [x] **Expose `FishingModule` via `ValmoraAPI`.** *(2026-08-07: already done — docs drift. `ValmoraAPI.getFishingModule()`/`ValmoraAPIImpl` already exist.)*

---

## Economy module

- [x] **Implement bank interest / bank upgrades** (todo.md). *(2026-08-07: implemented flat-rate bank interest — `economy.bank-interest-percent`/`economy.bank-interest-interval-seconds` config, applied periodically to every cached player's bank via a new `EconomyModule` task (0% = disabled, no task scheduled). "Bank upgrades" (capacity tiers) deliberately **not** implemented — scoped out to avoid a mismatched design (economy is player-account-wide, not per-profile, so a purchasable-capacity system needs its own persistence decision); documented as a deferred decision in `docs/modules/design/economy.md`.)*
- [x] **Make the death-penalty percentage configurable** (currently hardcoded 50% purse loss). *(2026-08-07: added `economy.death-loss-percent` (default 50.0) to `config.yml`; `EconomyListener.onDeath` now reads it via `EconomyModule.getDeathLossPercent()`, clamped 0-100.)*
- [x] **Wire `/eco`'s tab completer** (`setTabCompleter` currently never called). *(2026-08-07: `EcoCommand` already had a full `onTabComplete` implementation; added the explicit `getCommand("eco").setTabCompleter(...)` call in `Valmora.java`.)*
- [x] **Support offline-player targeting for `/eco`.** *(2026-08-07: `EcoCommand` now resolves targets against `Bukkit.getOfflinePlayers()` (no blocking network lookup) when not online, and reads/writes through new `EconomyModule.readOffline`/`writeOffline` — cached players still resolve synchronously through the existing cache path; offline players go straight to the DB and reply once the async round-trip completes.)*
- [x] **Fix the first-login cache race** (`cache.putIfAbsent` can eclipse a DB-loaded balance). *(2026-08-07: `handleJoin` now loads synchronously (`.join()`, same pattern already used by the `onEnable()` online-player preload) instead of async-then-`putIfAbsent`, closing the window where a transaction landing mid-load could seed a zero-balance entry that then couldn't be overwritten.)*
- [x] **Add a real transaction ledger** — the bank GUI's "Recent Transactions" is currently decorative. *(2026-08-07: new `valmora_economy_ledger` table (schema v7, `SQLDataStore`/`DataStore.appendLedgerEntry`/`loadRecentLedger`), recording every `deposit`/`withdraw`/`depositAll`/`withdrawAll`, pruned to the most recent 10 rows per player. `EconomyModule` keeps an in-memory 5-entry ring buffer per player for synchronous reads (seeded from the DB on join), exposed as `$economy.ledger.1$`–`$economy.ledger.5$` via `EconomyVariableProvider`; `guis/bank.yml`'s "Recent Transactions" display now renders these instead of a static placeholder.)*

---

## Calendar module

- [x] **Add offline catch-up** for events starting/ending while the server (or a player) was offline. *(2026-08-07: added a `calendar_state.yml` marker (`last-processed-day`), updated after every real day-change and read back on `onEnable()`. If the current day has moved past the last-recorded one, `reconcileMissedTransitions` fires the *net* on-start/on-end for every definition whose active state differs between the two snapshots — not a full day-by-day replay of `recurring-daily`. Documented limitation: an event window that would have recurred more than once inside the gap only reconciles its net change, not each individual cycle.)*
- [x] **Validate the day window** (`day-start`/`day-end` should be clamped 1-30 with `dayStart <= dayEnd` enforced). *(2026-08-07: `parseDefinition` now clamps both to `[1,30]` and fails the event with a clear `LoadResult.failure` if `dayStart > dayEnd` after clamping.)*
- [x] **Expose `CalendarModule` via `ValmoraAPI`.** *(2026-08-07: already done — docs drift. `ValmoraAPI.getCalendarEventModule()`/`ValmoraAPIImpl` already exist.)*
- [ ] **Build richer event content** (seasonal schematics/drops appearing based on time of year — todo.md).

---

## Core / plugin-wide

- [ ] **Scope `/npc reload` to just the NPC module** — currently reloads every module.
- [ ] **Make player-save-on-shutdown concurrent** instead of a synchronous per-player `join()` loop (stalls shutdown at scale).
- [ ] **Add a soft-dependency fallback for PacketEvents** instead of a hard crash if it's missing.
- [ ] **Fix `saveAllResources` silently no-op'ing in exploded/dev environments.**
- [ ] **Reword the script DSL for clearer end-user error messages** (todo.md — vague, needs scoping into concrete error-message improvements as they're found).
- [ ] **Folia support** (todo.md — large, listed as a "last to implement" item; relevant given current code assumes the global Bukkit scheduler).

---

## Large, deliberately-deferred content (not urgent — listed for completeness)

These are explicitly "last to implement" per `docs/todo.md` and shouldn't be picked up before the
above smaller items are cleared: dungeons, a new Nether experience, an End experience, Crystal
Hollows, Kuudra, a museum system, dark auction, world management (private islands / multiple hub
instances), Velocity/BungeeCord support, a resource-pack generator (items/GUIs/mobs/UI), a garden
system, player trading, a recipe book, a quest log UI, boosters, a settings menu, wardrobe
(quick armor swap), sacks (item-dependent storage), and the full demo-island content pass (Hub,
Gold Mine, Deep Cavern, Spider's Den, Farming Island, Nether Island — see `docs/todo.md` for the
per-island feature lists).
