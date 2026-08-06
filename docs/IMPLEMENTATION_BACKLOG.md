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

- [ ] **Register `RegenTask` from `CombatModule`** instead of `PlayerManager` owning it — current split causes a reload/lifecycle mismatch.
- [ ] **Add null-safety guards**: `DamageCalculator.java:33` (no null-check on `getActiveProfile()`), `DamageApplier.java:28-29` (silently no-damages null-profile players, doesn't distinguish vanilla vs custom mobs).
- [ ] **Reset `DamageIndicatorManager` rate-limit state in `onDisable()`.**
- [ ] **Evict `CombatTracker`'s per-UUID map on player quit** — currently never cleared, unbounded memory growth.
- [ ] **Map unhandled `DamageCause`s** (SUICIDE, CONTACT, STARVATION, DRAGON_BREATH, SONIC_BOOM, OUTSIDE_BORDER) to a real `DamageType` instead of defaulting to MELEE.
- [ ] **Move hardcoded combat tunables to config**: environment-damage multiplier (5.0), defense formula constant, 400ms indicator rate limit, 20-tick indicator lifetime, 20 no-damage-ticks, 3s combat window, 10-heart visual scale, per-damage-type colors.
- [ ] **Make the `immune` flag suppress damage indicators**, not just damage.
- [ ] **Wire attack-speed/cooldown into the combat pipeline** — `bonus_attack_speed` stat currently unused.

---

## Mob module

- [ ] **Default `health` when `stats.health` is unset** (currently 0 max HP, likely instant-death mobs).
- [ ] **Guard against missing `name`** at spawn time (currently NPEs in `MobFactory.applyVisuals`).
- [ ] **Unregister the death listener in `onDisable()`** — currently at risk of double-registration on reload.
- [ ] **Switch `spawnMob` to Paper's consumer-form `world.spawn(...)`** instead of raw `spawnEntity` (avoids the one-tick half-initialized state).
- [ ] **Add basic custom AI/behaviors**: pathfinding, aggro range, leash range, wander logic — currently mobs have none beyond vanilla defaults.
- [ ] **Add natural spawning support** — currently mobs only exist via explicit `/mob spawn`/zone spawners/scripts.
- [ ] **Boss mob content**: `todo.md` "add bosses with mechanics" — most bosses are stat-scaled reskins today; give at least a few real telegraphed abilities.

---

## Notify module

- [ ] **Fix the quest/notify module load-order bug.** `questModule` registers before `notifyModule`, so `NotifyManager` is null during `QuestModule.onEnable()` — quest-package notification categories never actually load. Net effect: `category:quest_complete`/`quest_progress` silently fall back to default chat IO. Either reorder module registration or defer category registration to a later hook.
- [ ] **Implement a real `AdvancementIO`** (currently a stub rendering `✦ <message>` in the action bar, ignoring `frame`/`icon` — needs NMS/packet work for a true toast).
- [ ] **Fix `SoundIO`** — ignores message text (fine, expected) but should catch `Key.key()` parse failures on invalid `sound:` instead of throwing.
- [x] **Cancel `BossBarIO`'s auto-hide task on player quit.** *(2026-08-07: `BossBarIO` now tracks pending auto-hide `BukkitTask`s per player UUID; added `NotifyQuitListener` (registered/unregistered in `NotifyModule.onEnable`/`onDisable` per module convention) that cancels them and hides the bar on `PlayerQuitEvent`.)*
- [x] **Fix the shipped `pets/baby_wolf.yml` `notify chat ...` bug** — prints the literal word "chat"; should be `notify ... io:chat`. *(2026-08-07: fixed all occurrences in the file — `baby_wolf`, `baby_sheep`, and `ender_dragon_pet` all had the same bug, changed to `notify <message> io:chat`.)*
- [ ] **Decide whether to implement or drop the BetonQuest-inspired but never-built notify features** listed in `docs/QUEST_MODULE_OUTLINE.md` (marked as external reference, not a spec): comma-separated category lists, sound options on any IO, `advancement` frame/icon, a `totem` IO, `bossbar` flags/countdown, `soundlocation`/`soundplayeroffset`, the `duration` key.

---

## NPC module

- [x] **Remove dead `NpcType` enum** (SHOP/DIALOGUE/QUEST/WARP/BANK/SLAYER) — never referenced anywhere; the loader has no `type:` key. *(2026-08-07: confirmed zero references outside the file itself, deleted `NpcType.java`.)*
- [ ] **Fix `look` subcommand** — doesn't respawn the NPC, stays stale until next respawn/reload.
- [ ] **Fix hologram conditions evaluating with a null caster** — any player-referencing condition silently fails for holograms.
- [ ] **NPC shops** (todo.md "npc shops" — referenced for Hub/Gold Mine/Deep Cavern content but no shop mechanic exists yet).

---

## Pet module

- [ ] **Add a way to distribute pet items** — nothing currently creates an item carrying `valmora:pet_id`; admins must hand-tag items. Ties to a pets menu/shortcut (todo.md).
- [ ] **Add owner-follow AI** — pets are currently fully static (`setAI(false)`).
- [ ] **Fix stale `activePetSlot` after relog** — causes toggle-off instead of re-summon, and blocks summoning a different pet.
- [ ] **Support multiple/anywhere-summonable pets** instead of single slot-bound pet.
- [ ] **Decide whether milestone stat grants should revert on unsummon** (currently permanent — undocumented design gap).
- [ ] **Wire `pet_luck` stat** into loot/drop code (currently unused).
- [ ] **Expose `PetModule` via `ValmoraAPI`.**

---

## Profile module

- [ ] **Guard against deleting the active profile** via `/profile delete` (currently corrupts the session; the GUI already guards this, the command doesn't).
- [ ] **Fix `createProfile` silently no-op'ing at the profile cap** while the command still reports success.
- [ ] **Persist active-profile id on switch, not just on quit/disable** — a crash right after switching reverts the selection.
- [ ] **Make quit-save non-fire-and-forget**, or otherwise guard against a hard stop dropping the final save.
- [ ] **Serialize `CooldownManager`/combat timer/`currentZoneId`** — currently reset on every reload.
- [ ] **Enforce profile-name uniqueness.**
- [ ] **Raise or make configurable the hardcoded 4-slot `PROFILE_SLOTS` GUI limit** — profiles beyond 4 are unreachable from the GUI.

---

## Progression module

- [ ] **Add a dedicated `/progression` command** (currently only reachable via the `/geomancy` GUI command).
- [ ] **Add a join-triggered catch-up for the daily bonus** (currently relies solely on a 5-minute poll).
- [ ] **Guard `cost-curve` expression evaluation** — malformed expressions currently throw uncaught.
- [ ] **Add player-facing feedback** on level-up/tier-unlock/reset (no chat/actionbar/title/notify integration currently).
- [ ] **Add per-node scripting callbacks** (`on-level` actions).
- [ ] **Make refund percentage configurable** (currently always 100%).

---

## Quest module

- [ ] **Implement the `BREW` objective listener** — constant exists, no handler in `QuestListener`. (Source spec: `docs/Objective_list.md`'s `Brew` syntax, and the alchemy module's matching gap.)
- [ ] **Implement the `VARIABLE` objective listener** — constant exists, `QuestVariableProvider` reads variables but nothing triggers progress from them.
- [ ] **Wire cross-package event references (`>` separator) into `resolveEventRefs()`** — currently only the public `resolveEvent()` supports the syntax, but the parse pipeline never calls it, so cross-package refs fall through to raw DSL and warn/fail.
- [ ] **Add admin commands for quest management** (`QuestCommand` currently only supports `journal`) — at minimum force-complete/reset/inspect-progress for support/debugging.
- [ ] **Add a repeatable-quest / quest-cooldown system** (slayer content currently fakes this with `quest_cancel`+`quest_start`, per `docs/modules/design/slayer.md`).

---

## Recipe module

- [ ] **Add a `/recipe` command** and expose `RecipeModule` via `ValmoraAPI`.
- [ ] **Add a dynamic handler for the `enchanting_table` machine** (currently only vanilla fallback).
- [ ] **Scope the vanilla-recipe fallback per machine** — currently machine-agnostic, leaking unintended vanilla results into custom machine GUIs.
- [ ] **Unregister dynamic handlers in `onDisable()`.**
- [ ] **Route the anvil coin cost through `EconomyService`** instead of the `player.var.coins` script variable, for consistency with Reforge.
- [ ] **Support multiple recipe outputs** — currently only the first output in a definition is honored.
- [ ] **Fix SHAPELESS recipes**: make `amount` optional (currently a missing value fails the whole file) and support duplicate ingredients needing distinct slots.
- [ ] **Add smithing-recipe support** (3-slot Template+Base+Addition, per CLAUDE.md §14.16 — not yet wired into the recipe engine).

---

## Reforge module

- [ ] **Expose `ReforgeModule` via `ValmoraAPI`.**
- [x] **Remove the dead `reforge` machine-id alias** (duplicate of `reforge_anvil`). *(2026-08-07: confirmed no GUI/recipe uses machine id `reforge` — only `reforge_anvil` — removed the `registerHandler("reforge", this)` line from `ReforgeModule.onEnable()`. Left `ReforgeModule`'s own `DynamicMachineHandler` method implementations in place (harmless, and removing `implements DynamicMachineHandler` entirely was out of scope for this item).)*
- [ ] **Unregister reforge handlers from `RecipeEngine` in `onDisable()`** — currently leaks on single-module reload.
- [ ] **Add a live output preview** in the Anvil/Forge GUI (currently empty until craft click).
- [x] **Fix case-sensitive definition storage vs. lowercase lookups** — uppercase reforge keys become unreachable. *(2026-08-07: fixed alongside the cross-cutting case-sensitivity item above — see note there.)*
- [ ] **Preserve base stats on vanilla items when reforged** (currently lost — empty `baseStats` for unregistered items).
- [ ] **Support multi-id `reforge-pool` on custom items** — currently parsed but the anvil only reads the first id.
- [ ] **Validate stat ids at load time** (typos currently vanish silently from lore instead of warning).
- [ ] **Deduplicate the hardcoded coin-cost table** — currently defined in 3 places (`RARITY_COST`, Anvil GUI lore, stone lore builder).
- [ ] **Add weighting for Random Forge** (currently uniform random).

---

## Resource module

- [ ] **Build the Draven Mines demo zone** (todo.md — custom drop support is done, the demo content isn't).
- [ ] **Expose `ResourceModule` via `ValmoraAPI`.**
- [ ] **Add a `/zone resource` (or similar) in-game editing subcommand.**
- [ ] **Zero `expToDrop` on HANDLED resource breaks** — XP orbs still drop from vanilla on top of custom drops.
- [ ] **Cancel the break event for depleted blocks** — currently still breakable.
- [ ] **Add crash-safe persistence for mid-progress block state** — currently lost on unclean shutdown.
- [ ] **Hook environmental triggers** (explosions/pistons) into tracking/regen.
- [ ] **Add AOE feedback** for Mining Spread (currently silently skips neighbors lacking power — no sound/particle indication).

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
- [ ] **Fix the default config's missing sea-creature mob** — `hub_fishing.yml:3` references `sea-creature-mob: squid` with no matching mob definition, so it silently never spawns.
- [ ] **Add bite-indicator particles/sound** and spawn sea creatures at the bobber location instead of the player's location (per `TESTING_GUIDE.md` expectations).
- [ ] **Add a fishing bait bag** (todo.md).
- [ ] **Expose `FishingModule` via `ValmoraAPI`.**

---

## Economy module

- [ ] **Implement bank interest / bank upgrades** (todo.md).
- [ ] **Make the death-penalty percentage configurable** (currently hardcoded 50% purse loss).
- [x] **Wire `/eco`'s tab completer** (`setTabCompleter` currently never called). *(2026-08-07: `EcoCommand` already had a full `onTabComplete` implementation; added the explicit `getCommand("eco").setTabCompleter(...)` call in `Valmora.java`.)*
- [ ] **Support offline-player targeting for `/eco`.**
- [ ] **Fix the first-login cache race** (`cache.putIfAbsent` can eclipse a DB-loaded balance).
- [ ] **Add a real transaction ledger** — the bank GUI's "Recent Transactions" is currently decorative.

---

## Calendar module

- [ ] **Add offline catch-up** for events starting/ending while the server (or a player) was offline.
- [ ] **Validate the day window** (`day-start`/`day-end` should be clamped 1-30 with `dayStart <= dayEnd` enforced).
- [ ] **Expose `CalendarModule` via `ValmoraAPI`.**
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
