# Vanilla Control Audit — Progress (feature/vanilla-control-audit-coverage)

> Tracks what this branch actually implemented against `docs/VANILLA_CONTROL_AUDIT.md`, and what's
> deliberately left for a follow-up pass. The audit lists on the order of 100 individual gaps across
> 22 sections — this branch covers the "Critical" tier plus a few cheap, high-confidence "High" items.
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

## Explicitly NOT done — still open

Everything else in the audit, notably (see the audit doc for full detail):

- **§4 critical — mending/durability/vanilla-enchant interception.** No `PlayerItemDamageEvent`/
  `PlayerItemMendEvent` hook exists. Left alone deliberately: Valmora items already bypass vanilla
  enchants via the custom enchant module, and durability for non-Valmora items works exactly as
  vanilla intends. Revisit only if a concrete need shows up (e.g. a custom max-durability component).
- **§10/§14 critical — modern `DamageSource`/`DamageType` builder + real death messages.** Not
  attempted. This interacts directly with the fact that player health is virtualized
  (`PlayerState.currentHealth`, not vanilla `getHealth()`) and mob health is force-set via
  `setHealth()` rather than through vanilla's own damage pipeline — vanilla's own death-message
  attribution may already be unreliable as a result. Needs a design decision on whether to build a
  fully custom death-message system (attacker/weapon/damage-type driven) or just repair
  `DamageSource` attribution; flagging rather than guessing.
- **§8 high — time/weather write-lock.** `time` module is explicitly documented as read-only by
  design elsewhere in the codebase; left untouched rather than silently reversing that decision.
- **§3/§4/§5 high — the entire block-state-change family** (`BlockFadeEvent`, `BlockFormEvent`,
  `BlockGrowEvent`, `BlockSpreadEvent`, `LeavesDecayEvent`, `BlockPhysicsEvent`,
  `BlockMultiPlaceEvent`, `BlockDropItemEvent`, `BlockExpEvent`, piston chains, explosions/fire
  control) — zero coverage, sizable standalone effort.
- **§15 — custom pathfinder goals**, **§9 — beds/sleep/phantoms**, **§11 — XP curve/orbs/mending
  routing**, **§16 — crossbow/trident/elytra/fishing-entity-hook specifics**, **§19/§20 — villager
  trades/professions/raids/breeding/taming**, **§22 — chunk/worldgen/persistence** — all untouched.

Re-run this audit's methodology (or re-grep the code) before assuming any of the above changed.
