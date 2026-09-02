# Vanilla Control Audit — Progress (feature/vanilla-control-audit-coverage)

> Tracks what this branch actually implemented against `docs/VANILLA_CONTROL_AUDIT.md`, and what's
> deliberately left for a follow-up pass. The audit lists on the order of 100 individual gaps across
> 22 sections — this branch covers the "Critical" tier, a few cheap high-confidence "High" items, a
> full sweep of §9 Death, Respawn & Persistence, and (in a later pass, see the second table below) a
> combat/resource follow-up batch: attack-cooldown scaling, a knockback-suppression hook, Silk Touch
> on resource blocks, plus two items that turned out already correct on verification.
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
- **§3/§4/§5 block-state-change family — partially done (third pass, see table above).**
  `BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent`, `LeavesDecayEvent` (pre-existing),
  piston chains, general explosion block destruction, fire ignite/burn, `EntityChangeBlockEvent`
  (mob griefing), and `BlockFromToEvent` (fluid flow) are now all zone-gated. Still zero coverage:
  `BlockFadeEvent`/`BlockFormEvent` (ice/snow/coral/copper/cauldron/frost-walker — cosmetic-only, no
  protection angle, deliberately skipped), `BlockPhysicsEvent` (support-chain breaks),
  `BlockMultiPlaceEvent` (doors/beds/portals — same protection outcome already achieved indirectly
  since the final block-count places still go through `BlockPlaceEvent`), and `GameRule.EXPLOSION_
  DROP_RULE`-style explosion *drop* control (only destruction is gated, not loot).
  `BlockDropItemEvent`/`BlockExpEvent` got a narrow, resource-module-scoped fix earlier — Silk Touch
  on zone-tracked resource blocks (second-pass table above) — not a general per-block-type system.
- **§9 — void-damage rescue/anti-void platform.** Explicitly declined (**user-confirmed**) as part of
  the death pass above — void damage kills exactly like vanilla, just with a proper custom death
  message.
- **§15 — custom pathfinder goals**, **§11 — XP curve/orbs/mending routing**, **§16 —
  crossbow/trident/elytra/fishing-entity-hook specifics**, **§19/§20 — villager
  trades/professions/raids/breeding/taming**, **§22 — chunk/worldgen/persistence** — all untouched.

Re-run this audit's methodology (or re-grep the code) before assuming any of the above changed.
