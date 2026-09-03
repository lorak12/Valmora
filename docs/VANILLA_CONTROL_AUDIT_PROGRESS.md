# Vanilla Control Audit — Progress (feature/vanilla-control-audit-coverage)

> Tracks what this branch actually implemented against `docs/VANILLA_CONTROL_AUDIT.md`, and what's
> deliberately left for a follow-up pass. The audit lists on the order of 100 individual gaps across
> 22 sections — this branch covers the "Critical" tier, a few cheap high-confidence "High" items, a
> full sweep of §9 Death, Respawn & Persistence, and (in later passes) a combat/resource follow-up
> batch (attack-cooldown scaling, a knockback-suppression hook, Silk Touch on resource blocks), a
> §3-5 zone block-protection sweep (fire, pistons, explosions, mob griefing, growth, flow), a
> fourth-pass cleanup of the leftovers from that sweep (support-chain breaks via `BlockPhysicsEvent`,
> plus two more items verified already covered), a fifth, user-directed pass turning two of the
> fourth pass's "deliberately skipped, cosmetic-only" items into real features: a `naturalBlockChanges`
> zone flag for ambient fade/form transitions, and a universal block-drop-to-Valmora-item translation
> catch-all, and a sixth, user-directed pass adding a brand-new global `block_loot` module for
> per-block-type loot *content* overrides (the piece the fifth pass explicitly left out on purpose).
> and a seventh pass (weather write-lock, bucket zone-gating, portal-creation zone-gating, plus
> verification of two already-covered items: spawn-egg/command mob upgrade and zombie-villager cure
> conversion).
> Everything else in the audit is still open; do not assume anything not listed below was addressed.

## Done this branch

| Audit item | What shipped |
|---|---|
| §18 critical #1 — `EntityTargetEvent` | `MobTargetListener` (`EntityTargetLivingEntityEvent`): `MobDefinition.ai.target-conditions` (condition-DSL list, evaluated with the candidate target as caster) and `ai.ignore-npcs` (default true) gate vanilla's own target selection. Not a replacement for vanilla AI/pathfinding — see §15 below, still open. |
| §19 critical #3 — mob conversion PDC loss | `MobConversionListener` (`EntityTransformEvent`): re-applies the source mob's `MobDefinition` (stats/equipment/visuals/boss registration) to the transformed entity, so e.g. a Valmora zombie drowning into a drowned no longer reverts to vanilla. |
| §12 critical #2 — `PlayerInteractEntityEvent` / off-hand trigger | New `AbilityTrigger.ON_INTERACT_ENTITY`; `AbilityListener` now handles `PlayerInteractEntityEvent` and no longer unconditionally skips `EquipmentSlot.OFF_HAND` on `PlayerInteractEvent`. |
| §14 critical #6 — shield blocking | `ShieldBlockService` + `CombatListener`: the custom damage pipeline now actually consults `Player#isBlocking()` + a frontal-arc check post-calculation (`combat.shield.*` config), including axe-disables-shield. Previously shields did nothing against Valmora damage. |
| §7/§14 high #8 — LIGHTNING/FREEZE → MELEE | `DamageType.LIGHTNING`/`FREEZE` added; `CombatListener.mapCauseToType` now maps both explicitly instead of falling through to the MELEE default. |
| §17 high — vanilla/egg/spawner spawns never got Valmora stats | `MobDefinition.natural-spawn.vanilla-default` (opt-in, one per `EntityType`) + `VanillaSpawnUpgradeListener` (`CreatureSpawnEvent`, skips anything already tagged by `MobFactory`). Toggle: `mobs.natural-spawn.vanilla-upgrade-enabled`. **User-confirmed decision**, see git log. |
| §8 high — no GameRules anywhere | New `world_rules` module (registered right after `script`): `world.gamerules.<NAME>` in config.yml applied to every loaded/loading world. Ships with every example commented out — **no default ruleset was chosen for you**, that's a game-design call. **User-confirmed decision.** |
| §9 — full Death, Respawn & Persistence pass | New `death` module (registered right after `zone`): custom death messages built from Valmora's own DamageType/attacker/weapon resolution — **not** vanilla `DamageSource` (**user-confirmed decision**); keepInventory/keepExperience via global config default + per-zone `ZoneFlags` override (**user-confirmed: zone-overridable**); a real fix for Totem of Undying, which was silently non-functional against this plugin's virtual-health damage pipeline (`TotemProtectionService` in `module/combat/`, intercepting `DamageApplier` before the fatal `setHealth(0)`) — **user confirmed fixing it plus adding general `ON_DEATH`/`ON_RESPAWN` ability triggers** (`AbilityTrigger`, dispatched by `AbilityTriggerListener`); optional per-zone custom respawn location (`death.zone-respawn-overrides`); zone-gated bed entry (`ZoneFlags.sleeping`) and respawn-anchor/bed wrong-dimension explosion block-protection (this Paper version has no dedicated `RespawnAnchorExplodeEvent`/`BedExplodeEvent` — both are a plain `BlockExplodeEvent`, confirmed against the shipped API jar); phantom-insomnia gating (`death.phantoms-enabled` + reused `ZoneFlags.naturalMobSpawning`). **User-confirmed decisions explicitly declined**: a vanilla-`DamageSource`-based message system, and a void-damage rescue/anti-void platform (void keeps killing exactly like vanilla, just with a proper message). See `docs/modules/design/death.md`. |

## Done this branch (second pass — combat/resource follow-up)

| Audit item | What shipped |
|---|---|
| §12/§14 High #9 — attack-cooldown / swing-charge integration | `AttackCooldownService` (`module/combat/`): reproduces vanilla's charge->damage-multiplier curve (`0.2 + progress² × 0.8`) against `Attribute.ATTACK_SPEED` (weapon base speed + `bonus_attack_speed`), applied by `CombatListener` to player melee hits after `DamageCalculator` — the value `bonus_attack_speed` fed into the attribute was previously only ever consumed for the client-side charge HUD, never for its actual damage consequence. `combat.attack-cooldown.*` config. Sprint-attack/sweep/backstab modeling remains a separate, still-open item. |
| §14 Medium #22 — knockback model | `CombatKnockbackListener` (`EntityKnockbackEvent`, `Cause.ENTITY_ATTACK` only) + `KnockbackModifierTracker` (short-lived UUID handoff between the damage event and the separate knockback event vanilla fires for the same hit) + a new `knockback-multiplier` modifier key on enchant `modify-attack`/`modify-defend` blocks (`DamageModifierContext`/`EnchantCombatHook`), so Valmora enchants can now suppress/scale a specific hit's knockback. Mob/player KB-resistance via vanilla attributes (`MobDefinition.knockback-resistance` → `KNOCKBACK_RESISTANCE`, or any stat mapped to a vanilla attribute) was already correct and untouched. No combat-pipeline knockback event yet (parity with `multiply_damage` left for later if needed). |
| §1 — Silk Touch / Fortune on resource blocks | `ResourceManager.handleBlockBreak` now checks the breaking tool for vanilla Silk Touch and, if present, gives exactly 1 of the current-stage block instead of the zone-configured loot table (matches vanilla: Fortune ignored too) — `resource.silk-touch.enabled` (default true). Vanilla (non-zone-tracked) blocks already got real Silk Touch/Fortune since their drops were never intercepted; general `BlockDropItemEvent`/`BlockExpEvent` control for arbitrary blocks is still not attempted. |
| §13/§15 #16 — `AttributeModifier` NamespacedKey | **Verification only, no code change needed.** `StatModule.recalculateAttributes`'s only two `new AttributeModifier(...)` call sites (`MINING_SPEED_MOD_KEY`, `ATTACK_SPEED_MOD_KEY`) already use the 1.21 `NamespacedKey` constructor. |
| §5/§17 — vanilla monster-spawner interception | **Verification only, no code change needed.** `SpawnerSpawnEvent extends CreatureSpawnEvent` in this Paper version, so the existing `VanillaSpawnUpgradeListener` (shipped in the first pass) already upgrades spawner-produced entities the same as natural/egg spawns — no separate `SpawnerSpawnEvent` listener was needed. |

## Done this branch (third pass — §3-5 zone-protection batch)

| Audit item | What shipped |
|---|---|
| §4 — fire spread / block burn ignoring zone protection | `ZoneListener.onBlockIgnite`/`onBlockBurn` (`BlockIgniteEvent`, `BlockBurnEvent`) — starting a fire is gated by `blockPlacing`, fire consuming an existing block is gated by `blockBreaking`. No new `ZoneFlags` field — reuses the same two flags already enforced for player mining/building. |
| §3 — piston push/pull across zone boundaries | `ZoneListener.onPistonExtend`/`onPistonRetract` (`BlockPistonExtendEvent`/`RetractEvent`) — cancels the whole piston action if any moved block sits in a `blockBreaking`-disabled zone, or its destination sits in a `blockPlacing`-disabled zone. Complements (does not replace) `resource.ResourceEnvironmentListener`'s pre-existing resource-node-specific piston protection, which is unrelated (node tracking, not zones) and keeps working unchanged. |
| §4 — general explosion block destruction | `ZoneListener.onEntityExplode`/`onBlockExplode` (`EntityExplodeEvent`/`BlockExplodeEvent`) — filters `blockList()` by `blockBreaking`, same logic as `death.RespawnAnchorListener.filterProtectedBlocks` (§9, respawn-anchor/bed-specific) but now applied to every explosion source (TNT, creeper, wither, end crystal, etc.), not just anchors/beds. The two listeners overlap harmlessly on bed/anchor explosions (same idempotent `removeIf`, no double-counting). |
| §3 — mob griefing (enderman/sheep/silverfish/ravager/wither block changes) | `ZoneListener.onEntityChangeBlock` (`EntityChangeBlockEvent`), gated by `blockBreaking`. `GameRule.MOB_GRIEFING`/`WITHER_BREAK_BLOCKS` already applied server/world-wide via the pre-existing generic `world_rules` config pass-through (verified, no code change needed) — this adds the finer-grained per-zone override on top. |
| §3 — crop/vine/amethyst/mushroom growth and grass/mycelium spread ignoring zone protection | `ZoneListener.onBlockGrow`/`onBlockSpread`/`onStructureGrow` (`BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent`), gated by `blockPlacing`. `StructureGrowEvent` is gated on the growth origin's zone only (a tree canopy can extend past a narrow boundary — same simplification other protection plugins make). `LeavesDecayEvent` was already covered by a pre-existing `ZoneFlags.leafDecay` flag (audit doc's ❌ for it was stale). |
| §4 — water/lava flow destroying blocks (torches, crops, etc.) with no `BlockBreakEvent` | `ZoneListener.onBlockFromTo` (`BlockFromToEvent`), gated by `blockBreaking`. Early-returns before any zone lookup when the flow target is air or already water/lava, to avoid a zone lookup on every ordinary flow tick. |

All six items land in the same file (`ZoneListener`) rather than new listener classes, following the existing pattern (`onLeavesDecay`, `onBlockBreak`, etc.) — no new `ZoneFlags` fields were added; every new gate reuses the existing `blockBreaking`/`blockPlacing` flags. Deliberately **not** attempted, as scoped when this batch was proposed: cosmetic-only mechanics with no protection angle (copper oxidation, dripstone drips, note blocks, redstone-torch burnout, sculk spread, snow-layer accumulation, coral death, ice melt, infinite-water-source formation) and `GameRule.EXPLOSION_DROP_RULE`-style drop-content control (only block *destruction* is gated, not what an explosion drops).

## Done this branch (fourth pass — block-protection cleanup)

| Audit item | What shipped |
|---|---|
| §1/§3 — support-chain breaks (`BlockPhysicsEvent`) | `ZoneListener.onBlockPhysics`: cancels the detach of a torch/sign/banner/button/pressure-plate/rail when its support is removed and the block sits in a `blockBreaking`-disabled zone — closes the gap where removing a support block *outside* a protected zone could still pop a protected decoration block with no `BlockBreakEvent` of its own. Deliberately narrow: block tags are resolved dynamically via `Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(...), Material.class)` (Mojang tag keys `torches`/`signs`/`banners`/`buttons`/`pressure_plates`/`rails`) rather than a blanket physics-event cancel, which would otherwise interfere with redstone/fence/stair shape updates that fire the same event constantly. Not a general physics/support-chain simulation — cave-vine/flower/string/end-rod-style breaks outside this tag set are still unguarded.
| §2 — `BlockMultiPlaceEvent` (doors/beds/portals) | **Verification only, no code change needed.** `BlockMultiPlaceEvent extends BlockPlaceEvent`, so the pre-existing `ZoneListener.onBlockPlace` `blockPlacing` gate already covers it. |
| §4 — `GameRule.EXPLOSION_DROP_RULE`-style explosion drop control | **Verification only, no code change needed.** The real vanilla gamerules here (`blockExplosionDropDecay`/`mobExplosionDropDecay`/`tntExplosionDropDecay`, 1.19+) already flow through the generic `world_rules` config pass-through (`GameRule.getByName(key)`), same as any other gamerule. |

Deliberately **not** attempted this pass: general `BlockDropItemEvent`/`BlockExpEvent` control for arbitrary block types (scoped as a separate content-authoring feature, not a protection gap) and `BlockFadeEvent`/`BlockFormEvent` (still cosmetic-only, no protection angle — copper oxidation, ice/snow melt, coral death, cauldron/frost-walker state changes).

## Done this branch (fifth pass — zone ambient-block flag + universal drop formatting)

**User-directed** (not audit-scoped): two of the fourth pass's "deliberately not attempted" items came back with a concrete use case and were done properly instead of left as cosmetic-only.

| Audit item | What shipped |
|---|---|
| §3 — `BlockFadeEvent`/`BlockFormEvent`/`EntityBlockFormEvent` zone control | New `ZoneFlags.naturalBlockChanges` field (default `true` = vanilla behavior) + `ZoneListener.onBlockFade`/`onBlockForm`/`onEntityBlockForm`. Reverses the fourth pass's "cosmetic-only, no protection angle" call now that there's a concrete use case: a themed zone (e.g. permanently snowed-in) that wants its ice to never melt and its water to keep freezing regardless of biome/light, set independently of `blockBreaking`/`blockPlacing` since this isn't a player-destruction concern. `EntityBlockFormEvent` needed its own handler despite extending `BlockFormEvent` — it has a separate `HandlerList`, so a `BlockFormEvent`-typed listener never receives it. New command flag `/zone flag <id> natural-block-changes <true\|false>`; `zones/*.yml` key `allow.natural-block-changes`. All existing `ZoneFlags` positional-constructor call sites (loader, command, and two test files) updated for the new 12th field. |
| §1/§4 — every block-break drop formatted as a Valmora item | New `LootListener.onItemSpawn` (`ItemSpawnEvent`), running `ItemTranslator#translate` on every spawned `Item` entity's stack. **Turned out the dominant case (normal player mining) was already fully covered** — `LootListener.onBlockBreak` has suppressed vanilla drops and translated its own computed list via `ItemTranslator` since before this branch; that was verified, not new. The actual gap was every *other* drop-producing cause (explosions, pistons, fire, mob-caused block changes, and anything else that spawns a raw `Item` entity) — none of these can be reached via `BlockDropItemEvent` (its constructor requires a `Player`, so Bukkit structurally never fires it for non-player causes), so instead of chasing each mechanic individually, `ItemSpawnEvent` catches all of them at the one point they all funnel through: the moment the item entity appears in the world. `translate()` is idempotent (no-ops on anything already carrying a Valmora item ID), so this is safe to layer on top of the existing translated paths (player mining, mob loot, fishing) without double-processing. |

Not attempted: per-block-type *loot content* authoring (e.g. "make every `STONE` block drop a custom item instead of cobblestone" server-wide) — that's a genuinely different, larger feature (its own config schema, its own authoring surface) than "format whatever already drops as a Valmora item," which is what shipped here. **Done in the sixth pass below.**

## Done this branch (sixth pass — global `block_loot` module)

**User-directed** (not audit-scoped, per the fifth pass's own explicit deferral): a new, standalone
`block_loot` module (id `"block_loot"`, registered right after `fishing`, before `npc`) for
server-wide, zone-independent per-block-type loot overrides — "make every `Material` X drop this
table instead of vanilla loot, everywhere," fully decoupled from the zone system.

| Item | What shipped |
|---|---|
| Global per-`Material` loot override | New `BlockLootConfig`/`BlockLootDrop`/`BlockLootRegistry`/`BlockLootLoader`/`BlockLootManager`/`BlockLootListener`/`BlockLootModule` (`module/blockloot/`), mirroring the `resource` module's `Manager`+`Listener`+`Registry`+`Loader`+`Module` shape but single-stage: no progression, no regen timer, no Breaking-Power gate — a configured block just breaks once, like vanilla, and drops the configured table. Config lives in `plugins/Valmora/block_loot/*.yml`, ships with a single fully-commented `example.yml` (zero real entries by default — installing/updating changes no vanilla behavior until an admin opts in). |
| Content-pack-safe YAML shape | The top-level YAML key is an arbitrary id, never the material name directly — `material:` is a required field *inside* the section instead. Caught during design review before any code was written: `YamlLoader` rewrites top-level keys through the content-pack namespacer, so a pack-authored file using the material as its key would silently fail to resolve (`Material.matchMaterial("somepack:STONE")` → `null`). Covered by dedicated loader tests. |
| Precedence with zone `resource-blocks:` | `item.LootListener.onBlockBreak` gained a third defer check (matching its two existing `resource`-module guards): if `block_loot` has a config for the broken block's material, it returns early — `BlockLootListener` (registered at `EventPriority.LOWEST`, same as `ResourceListener`) already fully handled it. Net precedence per block break: zone `resource-blocks:` (most specific) → `block_loot` (global override) → real vanilla drops (`LootListener`, translated). |
| Silk Touch / Mining Fortune parity | `BlockLootManager` mirrors `ResourceManager`'s existing Silk Touch (`block-loot.silk-touch.enabled`, default true — yields 1 of the block's own material, ignoring the configured table and Fortune, matching vanilla) and Mining-Fortune-scaled roll amounts. |
| Shared Fortune-math extraction | `ResourceManager`'s private `getPlayerMiningFortune`/`applyFortune` (previously the only copy of this formula in the codebase, verified during design review) extracted into a new `org.nakii.valmora.util.MiningFortune` utility; `ResourceManager` now delegates to it, behavior unchanged. |
| Overflow-safe give path | New `ItemManager.giveOrPrivateDrop(Player, ItemStack, Location)`, extracted from `item.LootListener`'s previously-private `processLoot`/`handleFullInventory` (add to inventory, else spawn a private glowing "INVENTORY FULL" pickup) — now reusable by `block_loot` without duplicating ~35 lines. `LootListener.processLoot` simplified to translate-then-delegate. |
| API wiring | `Valmora.getBlockLootModule()`, `ValmoraAPI.getBlockLootModule()`, `ValmoraAPIImpl.getBlockLootModule()` added, matching every other module's public-API-parity convention. |
| Docs | New `docs/modules/design/blockloot.md` + `docs/modules/user/blockloot.md`; `docs/modules/{design,user}/INTEGRATION.md` updated with the new module's family/dependency listing; `CLAUDE.md` §5 module order updated (and a pre-existing drift fixed in the same edit — `death` was missing from that list entirely despite being registered in code since the earlier `death`-module pass). |

Deliberately **not** attempted in this pass (see `docs/modules/design/blockloot.md`'s own
"Unfinished Things" section for detail): a general `BlockExpEvent`/XP-orb hook for configured
blocks; a per-block-entry Silk Touch override (one global toggle for now, matching `resource`'s
existing simplicity); fixing `ResourceManager`'s own pre-existing overflow-loss gap (its give path
still calls bare `addItem` and ignores leftovers) — `block_loot` avoids repeating it, but the
original gap was left untouched as out of scope.

## Done this branch (seventh pass — weather lock, bucket/portal zone gating, spawn/cure verification)

**User-directed batch** (`Proceed with #1–#3, add #5 if room`, from a proposed priority list): the
next three cheapest verified-open items from the audit, plus a bonus item that turned out to already
be fully covered.

| Audit item | What shipped |
|---|---|
| §8 — weather write-lock | New `WorldRulesModule.WeatherLock` enum (`CLEAR`/`RAIN`/`THUNDER`) + `world.weather-lock.<world-name>` config, same purely-config-driven-pass-through philosophy as the existing gamerule section. Applied on enable/`WorldLoadEvent` (`world.setStorm`/`setThundering`) and re-enforced by `WorldRulesListener.onWeatherChange`/`onThunderChange` (`WeatherChangeEvent`/`ThunderChangeEvent`), cancelling any transition — natural cycle, `/weather` command, another plugin — that would move a locked world away from its configured state. No default lock ships enabled. Deliberately simpler than the time module's read-only design: weather is server-authoritative (no client-side simulation to fight), so a one-shot set + event-cancel is sufficient, unlike time's documented need to re-assert every tick. |
| §4 — bucket fill/empty zone gating | `ZoneListener.onBucketFill`/`onBucketEmpty` (`PlayerBucketFillEvent`/`PlayerBucketEmptyEvent`) — fill (removes the source block) gated by `blockBreaking`, empty (creates a fluid/powder-snow block) gated by `blockPlacing`. Same two flags every other block-mutation event in this listener already reuses. Fish/axolotl bucket capture (`PlayerBucketEntityEvent`) deliberately out of scope — it's an entity-capture mechanic, not a block-protection one. |
| §10 — portal creation zone gating | `ZoneListener.onPortalCreate` (`PortalCreateEvent`) — cancels the whole creation (nether/end portal frame igniting, or Bukkit programmatically generating one) if any resulting block would land in a `blockPlacing`-disabled zone, mirroring `isPistonMoveProtected`'s any-block-blocks-the-whole-action approach. |
| §10 — portal *use* (`PlayerPortalEvent`) | **Verification only, no code change needed.** `PlayerPortalEvent extends PlayerTeleportEvent` in this Paper version, so `ZoneListener.onTeleportGate`'s existing `teleportation` flag already applies — same precedent as `BlockMultiPlaceEvent extends BlockPlaceEvent` (fourth pass). |
| §10 — cross-world tracking (`PlayerChangedWorldEvent`) | **Verification only, no code change needed.** `ZoneListener.onTeleport` already reschedules `zoneManager.checkTransition()` after every `PlayerTeleportEvent` (portals included, since `PlayerPortalEvent` is one), and `checkTransition` reads the player's live post-teleport/post-world-change location — there's no cross-world path a player can take that skips `PlayerTeleportEvent` first. |
| §17 — spawn-egg/command mob upgrade | **Verification only, no code change needed.** `VanillaSpawnUpgradeListener` (shipped in the first pass) has no `SpawnReason` filter at all — it upgrades *any* untagged `CreatureSpawnEvent` with a registered `vanilla-default`, which already includes `SPAWNER_EGG`/`COMMAND`/`PLUGIN` reasons alongside natural/spawner spawns. The audit's `❌ eggs` coverage cell (§17 table, row "Spawn egg / command / plugin spawning") was stale from before that listener shipped. |
| §19 — zombie-villager cure conversion | **Verification only, no code change needed.** This Paper API version (1.21.11) has no separate `CureZombieVillagerEvent`/`EntityConvertEvent` class — curing fires a plain `EntityTransformEvent` with `TransformReason.CURED` (confirmed via the shipped API jar), which `MobConversionListener` already handles unconditionally (no `TransformReason` filter). |

Not attempted this pass (explicitly out of scope per the proposal): #4, the one still-open
**critical**-tier item (vanilla durability/enchant interception) — deliberately held for its own
scoped pass per the proposal's own recommendation, since it needs an explicit decision on how far to
go (replicate vanilla enchants for non-Valmora items? guard against Valmora items somehow carrying
vanilla enchants? leave vanilla items fully vanilla?) before any code gets written.

## Explicitly NOT done — still open

Everything else in the audit, notably (see the audit doc for full detail):

- **§4 critical — mending/durability/vanilla-enchant interception.** No `PlayerItemDamageEvent`/
  `PlayerItemMendEvent` hook exists. Left alone deliberately: Valmora items already bypass vanilla
  enchants via the custom enchant module, and durability for non-Valmora items works exactly as
  vanilla intends. Revisit only if a concrete need shows up (e.g. a custom max-durability component).
- **§10/§14 critical — modern `DamageSource`/`DamageType` builder.** Resolved differently, not by
  building this: real custom death messages now exist (`death` module's `DeathMessageService`),
  built entirely from Valmora's own DamageType/attacker/weapon resolution instead of vanilla
  `DamageSource` — a deliberate, **user-confirmed** choice given player health is virtualized and
  mob health is force-set via `setHealth()` rather than through vanilla's own damage pipeline (making
  vanilla's own death-message attribution unreliable anyway). The `DamageSource.builder(...)` API
  itself remains genuinely unused; revisit only if some other feature needs vanilla-localized
  attribution specifically.
- **§8 high — time/weather write-lock.** `time` module is explicitly documented as read-only by
  design elsewhere in the codebase; left untouched rather than silently reversing that decision.
- **§3/§4/§5 block-state-change family — mostly done (third + fourth passes, see tables above).**
  `BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent`, `LeavesDecayEvent` (pre-existing),
  piston chains, general explosion block destruction, fire ignite/burn, `EntityChangeBlockEvent`
  (mob griefing), `BlockFromToEvent` (fluid flow), and a narrow tag-driven `BlockPhysicsEvent`
  support-chain protection (torches/signs/banners/buttons/pressure_plates/rails) are now all
  zone-gated. `BlockMultiPlaceEvent` and `GameRule.EXPLOSION_DROP_RULE`-style drop control were
  verified already covered with no code change needed (fourth-pass table above). Still zero
  coverage: `BlockFadeEvent`/`BlockFormEvent` (ice/snow/coral/copper/cauldron/frost-walker —
  cosmetic-only, no protection angle, deliberately skipped), general `BlockPhysicsEvent`
  support-chain coverage beyond that tag set (cave vines, flowers, string, end rods, hanging signs
  outside the `signs` tag if any exist). `BlockDropItemEvent`/`BlockExpEvent` got a narrow,
  resource-module-scoped fix earlier — Silk Touch on zone-tracked resource blocks (second-pass table
  above) — not a general per-block-type system.
- **§9 — void-damage rescue/anti-void platform.** Explicitly declined (**user-confirmed**) as part of
  the death pass above — void damage kills exactly like vanilla, just with a proper custom death
  message.
- **§15 — custom pathfinder goals**, **§11 — XP curve/orbs/mending routing**, **§16 —
  crossbow/trident/elytra/fishing-entity-hook specifics**, **§19/§20 — villager
  trades/professions/raids/breeding/taming**, **§22 — chunk/worldgen/persistence** — all untouched.

Re-run this audit's methodology (or re-grep the code) before assuming any of the above changed.
