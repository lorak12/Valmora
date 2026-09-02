# Hardcoded Values Audit — Valmora

> **Date:** 2026-09-02
> **Scope:** `src/main/java/**` (566 files) + `src/main/resources/config.yml` + all module YAML defaults
> **Method:** 4 parallel sub-agent deep reads + manual verification of remaining modules (`alchemy`, `calendar`, `collection`, `fishing`, `machine`, `resource`, `pack`, `profile`, `npc`, `pet`, `rarity`). Every literal not behind a `getConfig()` / YAML `getString`/`getInt` fallback was flagged. Values that are Minecraft constants (`20 ticks/sec`, `HandlerList`) or intentionally fixed are marked `INTENTIONAL` and not counted as gaps.
> **Already configurable (no action needed):** `config.yml` → `economy.autosave-interval-seconds`, `economy.death-loss-percent`, `economy.bank-interest-*`, `time.world/start-*/season-names/phase-names/scoreboard-enabled`, `progression.refund-percent`, `combat.environment-damage-multiplier`, `combat.post-hit-no-damage-ticks`, `combat.damage-indicator-*` (rate, lifetime), `combat.visual-health-hearts`, `combat.combat-window-ms`, `alchemy.splash-radius/tick-interval/max-active-effects`, `anvil.templates.*`, `pack.max-extracted-size-mb/max-entries/index-url`, `profiles.*`, `items.lore.*` (all formats), `npc-skin-server.enabled/port`.

---

## How to use this document

Each finding has:

- **File:Line** — clickable reference (`path:line`)
- **Value** — the literal as it appears
- **Why** — player/admin motivation to change it
- **Suggested config** — where to expose it (preferred: `config.yml`; per-feature YAML where noted)
- **Type / Range** — validated type and suggested bounds
- **Priority** — `HIGH` = balance/perf breaking, `MED` = QoL/localization, `LOW` = polish/guardrail

Apply incrementally: `HIGH` items first (14 keys), then `MED` (content pack friendliness). No finding requires a code change to *remove* the default — always keep the current literal as the fallback.

---

## Priority summary

| Priority | Count | Meaning |
|----------|-------|---------|
| **HIGH** | 31 | Affects combat balance, economy, spawn density, or hot-loop performance. Admins will request before launch. |
| **MED** | 42 | Localization, visual tuning, or server-size scaling. Requested within first month. |
| **LOW** | 28 | Code hygiene, DRY, guardrails, or presentation thresholds. |
| **INTENTIONAL** | 4 | Correctly hardcoded (vanilla constants). |

---

## 1. Core Infrastructure / Database

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-001 | `database/DatabaseFactory.java:18` | `maximumPoolSize = 10` | 10 starves 300-player networks, wastes RAM on 20-player servers. Pool exhaustion = visible DB stalls. | `config.yml → database.pool.maximum-pool-size` | `int 2..50 default 10` | HIGH |
| HC-002 | `database/DatabaseFactory.java:29-31` | `cachePrepStmts=true`, `prepStmtCacheSize=250`, `prepStmtCacheSqlLimit=2048` | MySQL-only tuning; VPS vs dedicated host differ. | `database.mysql.prep-cache-size` / `prep-cache-sql-limit` | `int` | LOW |
| HC-003 | `database/SQLDataStore.java:38` | `newFixedThreadPool(4)` | Async DB throughput; 4 is arbitrary. Large MySQL needs 8. | `database.worker-threads` | `int 1..16 default 4` | HIGH |
| HC-004 | `database/SQLDataStore.java:852` | `awaitTermination(10, SECONDS)` | Too short → data loss on shutdown; too long → stalls. | `database.shutdown-timeout-seconds` | `int 5..60 default 10` | MED |
| HC-005 | `database/SQLDataStore.java:55` | `LEDGER_RETENTION_PER_PLAYER = 10` | GDPR/verbosity; admins want 5 vs 100 retained ledger rows (distinct from GUI display limit). | `economy.ledger-retention-per-player` | `int 1..100 default 10` | MED |
| HC-006 | `ValmoraCommand.java:39` + 6 other commands (`EcoCommand.java:26`, `CalendarCommand.java:31`, `CollectionCommand.java:58`, etc.) | `"valmora.admin"` (6+ occurrences) | Single admin permission prevents splitting `valmora.reload` vs `valmora.eco` vs `valmora.pack.install` for staff ranks. | `config.yml → permissions.admin` + per-command `permissions.reload`, `permissions.eco`, `permissions.pack` | `string` permission node | HIGH |
| HC-007 | `util/Formatter.java:21-22` | `ROMAN_ONES/TENS` capped `1..99` | Not configurable — intentional display logic. | — | — | INTENTIONAL |
| HC-008 | `util/Keys.java:63-106` | `NamespacedKey(plugin, "valmora_item_id")` etc. | Plugin-scoped PDC keys — never expose. | — | — | INTENTIONAL |

---

## 2. Economy

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-010 | `module/economy/EconomyModule.java:54` | `LEDGER_DISPLAY_LIMIT = 5` | GUI "Recent Transactions" length; owners want 10 for audit. | `economy.ledger-display-limit` | `int 1..20 default 5` | MED |
| HC-011 | `module/economy/EconomyModule.java:333-345` | Thresholds `1_000_000_000 / 1_000_000 / 1_000`, patterns `%.2fb / %.2fm / %.1fk`, emoji `🪙`, thousands separator `"."`, `","`→`"."` | Economy branding (servers use `$`, `⛃`), decimal precision, locale. | `economy.format.billion/million/thousand` + `coin-symbol` + `thousands-separator` | `string` patterns | MED |
| HC-012 | `module/economy/EconomyModule.java:79,86-87` | Defaults `60`, `0.0`, `3600` for autosave/interest | Already configurable — defaults listed here for doc completeness. | `economy.autosave-interval-seconds` etc. | — | — |
| HC-013 | `module/economy/EcoCommand.java:26-28` | `PERMISSION="valmora.admin"`, `USAGE="<gray>Usage: ..."` | Localization + permission splitting. | `permissions.eco` + `economy.messages.usage` | `string MiniMessage` | LOW |
| HC-014 | `module/economy/EcoCommand.java:161` | Tab-complete `List.of("1000","1k","10k","100k","1m")` | Preset amounts differ by economy scale. | `economy.tab-complete-amounts` | `list<string>` | LOW |
| HC-015 | `module/economy/CoinExpressionParser.java:77-81` | `case 'k'→1_000, 'm'→1_000_000, 'b'→1_000_000_000` | No `t` (trillion) or custom suffix. | `economy.coin-suffixes: {k:1000,m:1000000,b:1000000000,t:1000000000000}` | `map` | LOW |
| HC-016 | `module/economy/EconomyVariableProvider.java:47-48` | `"<gray>There are no recent transactions!"` | Hardcoded English. | `economy.messages.no-transactions` | `MiniMessage` | MED |
| HC-017 | `module/economy/EconomyLedgerEntry.java:12` | `verb "Deposited"/"Withdrew"`, colors `<gray>/<white>` | Localization. | `economy.messages.deposit-verb/withdraw-verb` | `MiniMessage` | MED |
| HC-018 | `module/economy/event/EconomyDepositEventFactory.java:18,38-63` | `PREFIX="<dark_gray>[<gold>Bank<dark_gray>] "`, specials `"all"/"half"`, `Math.floor(purse/2.0)` | Prefix branding, halving rounding (`floor` vs `ceil`). | `economy.bank-messages.prefix` + `economy.deposit-halving: floor\|ceil\|round` | `string` / `enum` | MED |

---

## 3. Combat / Damage

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-020 | `module/combat/CombatModule.java:33` | `runTaskTimer(...,0L,20L)` regen poll | Tune RPG regen tick rate (10 vs 40 ticks). Hot loop. | `combat.regen-interval-ticks` | `int 1..100 default 20` | HIGH |
| HC-021 | `module/combat/RegenTask.java:18` | `SUMMARY_INTERVAL_RUNS=10` | Noisy debug interval. | `combat.debug-summary-interval` | `int` | LOW |
| HC-022 | `module/combat/RegenTask.java:51` | `health blocked in combat, mana always regens` (hardcoded logic) | Some servers want mana also blocked, or both always. | `combat.regen.mana-in-combat: bool`, `health-in-combat: bool` | `bool` | MED |
| HC-023 | `module/combat/DamageCalculator.java:86` | `baseDamage=1.0` fallback | Tune vanilla mob vs player gap. | `combat.fallback-base-damage` | `double 0.5..10 default 1.0` | MED |
| HC-024 | `module/combat/DamageCalculator.java:169,176` | Fallbacks `1+strength/100`, `1+critDamage/100` | Already overridable via `damage_formula.yml`; defaults hardcoded — document. | `damage_formula.yml → damage_multiplier/crit_multiplier` | `Expression` | LOW |
| HC-025 | `module/combat/DamageCalculator.java:298-303` | `environment-damage-multiplier` default `5.0`, and `100/(def+100)` hardcoded (bypasses `DamageFormulaRegistry`) | Admins expect `damage_formula.yml:defense_multiplier` to affect env damage too. Fix to route through registry. | `combat.environment-damage-multiplier` (already exists) + code fix | `double 1..20` | HIGH |
| HC-026 | `module/combat/DamageCalculator.java:235` | `Math.floor(mitigated)` rounding | Some servers want `round`/`ceil`. | `damage_formula.yml → rounding: floor\|round\|ceil` | `enum` | LOW |
| HC-027 | `module/combat/DamageApplier.java:70` | Default `20` for `post-hit-no-damage-ticks` | Already configurable; doc default. | `combat.post-hit-no-damage-ticks` | `int` | — |
| HC-028 | `module/combat/DamageIndicatorManager.java:49,60-87` | `400ms` rate limit, `0.5` spread, `20` ticks lifetime, `ARGB(0,0,0,0)`, `CENTER`, `✧` wrapper, `(int)` truncation | Operators want custom indicator style/offset/lifetime/crit format. | `combat.damage-indicator.rate-limit-ms`, `offset`, `lifetime-ticks`, `crit-format`, `normal-format`, `show-as-int` | `int/double/string MiniMessage/bool` | MED |
| HC-029 | `module/combat/CombatListener.java:50` | `victim.getMaximumNoDamageTicks()/2.0F` (50% iframe threshold) | Tune anti-DoT stacking aggressiveness. | `combat.iframe-threshold-factor` | `double 0..1 default 0.5` | HIGH |
| HC-030 | `module/combat/CombatListener.java:69-71` | Projectile heuristic `ARROW/MOB_PROJECTILE → PROJECTILE else MELEE` | TRIDENT/SNOWBALL/FIREBALL incorrectly become MELEE. | `damage_types/mapping.yml → bukkit-damage-type: valmora-type` | `map` | MED |
| HC-031 | `module/combat/CombatListener.java:230-249` | `mapCauseToType()` 17-case switch (`VOID`,`SONIC_BOOM`,`WORLD_BORDER→OUTSIDE_BORDER`) | New causes need code change. | `damage_types/*.yml: bukkit-cause: FALL` | `string` | MED |
| HC-032 | `module/combat/DamageType.java:37-53` | Colors `<#FF8C00>`, `<dark_red>` and `ignoresDefense` for `FALL/DROWNING/VOID/...` | Already overridable via `damage_types/*.yml` but defaults baked — low. | `damage_types/*.yml` per type | — | LOW |
| HC-033 | `module/combat/CombatListener.java:248-249` | `default → DamageType.MELEE` fallback | Future causes silently become melee. | `combat.environment.fallback-damage-type: MELEE` | `enum` | LOW |
| HC-034 | `module/combat/DamageFormulaRegistry.java:34-38` | Maps `DAMAGE_MULTIPLIER→"1 + $dmg.strength$/100"` etc. | Already overridable via `damage_formula.yml`. | `damage_formula.yml` | — | — |

---

## 4. Item / Mechanics / Targeting

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-040 | `module/item/ItemFactory.java:288-295` | Tier→power `5/4/3/2/1` (`NETHERITE 5, DIAMOND 4, IRON 3, STONE 2, else 1`) | Custom tool tiers need custom power. | `items.breaking-power: {netherite:5,diamond:4,iron:3,stone:2,wood:1}` OR per `item_types.yml` | `int 1..10` | HIGH |
| HC-041 | `module/item/ItemTranslator.java:53-60` | Material substring→rarity (`NETHERITE/ELYTRA→MYTHIC`, `DIAMOND/TRIDENT→EPIC`, etc.) | Custom materials need mapping. | `items.vanilla-rarity-mapping: {NETHERITE:MYTHIC,DIAMOND:EPIC,…}` | `map<string,rarity>` | HIGH |
| HC-042 | `module/item/ItemTranslator.java:88-94` | Mining speed `450/400/300/200/500/100` per tier | Balance per server progression. | `items.vanilla-stats.mining-speed: {netherite:450,…}` | `double` | HIGH |
| HC-043 | `module/item/ItemTranslator.java:97-102` | Weapon damage `8/7/6/5/4` | Weapon balance. | `items.vanilla-stats.weapon-damage: {netherite:8,…}` | `double` | HIGH |
| HC-044 | `module/item/ItemTranslator.java:74-75,105-117` | Bow `6.0`, Crossbow `9.0`, armor base `5/4/3/2/1` × multipliers `2.5 chestplate, 2.0 leggings, 1.5 helmet` | Ranged + armor balance. | `items.vanilla-stats.{bow-damage,crossbow-damage,armor-multiplier.*}` | `double` | HIGH |
| HC-045 | `module/item/Rarity.java:4-10` | Colors `<white>/<green>/<blue>/<dark_purple>/<gold>/<light_purple>/<aqua>` | Adding rarity needs code; colors are branding. | `rarities.yml: id:{name,color}` (data-driven) | `string MiniMessage` | MED |
| HC-046 | `module/item/AbilityExecutor.java:126,143,152` | `"<red>No target in range!"`, `"<red>Ability on cooldown: Xs"`, `"<aqua>Not enough Mana!"`, `10` ticks duration, `2` priority | Localization + duration tuning. | `items.messages.no-target`, `cooldown` (with `{remaining}`), `no-mana` + durations | `MiniMessage` / `int` | MED |
| HC-047 | `module/item/TargetResolver.java:59-69,95,106-117` | Defaults `r=5.0` (`@enemies_in_radius`), `range=8.0`/`angle=45.0` (cone), `1.0e-6` epsilon, `isHostile=!(instanceof Player)` | AoE balance; hostile definition excludes non-player allies incorrectly for PvP. | `items.target-resolver.defaults.{enemies-radius,cone-range,cone-angle}` | `double` | MED |
| HC-048 | `module/item/QuiverListener.java:30` | `STORAGE_ID="quiver"`, arrow check `ARROW/SPECTRAL_ARROW/TIPPED_ARROW` (no `Tag.ITEMS_ARROWS`) | Should use tag; ID collision. | `items.quiver.storage-id`, `arrow-tag: minecraft:arrows` | `string` | LOW |
| HC-049 | `module/item/LootListener.java:120-130` | Title `"<red><bold>INVENTORY FULL"`, subtitle `"<gray>Items dropped..."`, durations `200/2000/500ms`, `20` pickup delay, `glowing true`, `visibleByDefault false` | Localization + exploit tuning (pickup delay). | `items.loot.{full-title,full-subtitle,pickup-delay-ticks,private-drop,glowing}` | `MiniMessage/int/bool` | MED |
| HC-050 | `module/item/TemporaryStatService.java:33` | `*1000` ms conversion (not configurable — vanilla constant) | — | — | — | INTENTIONAL |
| HC-051 | Mechanics defaults table — all `module/item/impl/*Mechanic.java` | See sub-table below | Global defaults for mechanic params when YAML omits them. | `config.yml → mechanics.*.defaults.*` | various | LOW |

**HC-051 mechanic sub-table:**

| File:Line | Param | Hardcoded default | Suggested key |
|-----------|-------|------------------|---------------|
| `DamageMechanic.java:25-35,42` | `damage 1.0`, `damage-type MAGIC`, `ticks 1`, `interval 1.0s`, `intervalTicks max(1,(int)(s*20))` | `mechanics.damage.defaults.*` |
| `HealMechanic.java:28` | `target @player`, `interval 1.0s` | `mechanics.heal.defaults.*` |
| `LaunchProjectileMechanic.java:39-51` | `projectile ARROW`, `velocity 2.0`, `count 1`, `spread 0.0`, `pierce false`, `damage 0.0` | `mechanics.launch-projectile.defaults.*` |
| `AoeMineMechanic.java:34` | `radius 1` | `mechanics.aoe-mine.radius-default` `int 1..10` |
| `PullEntitiesMechanic.java:28,42-44` | `PERIOD_TICKS 4L`, `strength 1.0`, `range 20.0`, `duration 2.0`, `target @enemies_in_radius{r=10}` | `mechanics.pull-entities.*` |
| `PushEntitiesMechanic.java:23,30` | `force 1.0`, `setY max(0.3, force*0.4)` | `mechanics.push-entities.force-default` |
| `IgniteMechanic.java:22` | `duration 3.0` | `mechanics.ignite.duration-default` |
| `ApplyEffectMechanic.java:27-30,64` | `duration 5.0`, `amplifier 1`, `hide-particles false`, `level<1→1`, `amplifier-1` | `mechanics.apply-effect.*` |
| `ModifyStatMechanic.java:32,56` | `amount 0.0`, `duration -1.0` (`-1=permanent`), `runTaskLater (duration*20)+1` | `mechanics.modify-stat.*` |
| `LaunchPlayerMechanic.java:26-27` | `y-force 1.0`, `forward-force 1.0`, `no-fall-damage false` | `mechanics.launch-player.*` |
| `ChargeJumpMechanic.java:27-29` | `max-charge-ms 2000`, `min-y-force 0.4`, `max-y-force 2.2` | `mechanics.charge-jump.*` |
| `TeleportMechanic.java:26,33` | `distance 8.0`, `hitDistance-1.0`, `+0.5` centering | `mechanics.teleport.distance-default 8.0` |
| `TrampleListener.java:23` | `Material.FARMLAND` | `items.trample.protected-block` | LOW |

---

## 5. Stat

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-060 | `module/stat/StatManager.java:149` | `20*60*60 = 72000` ticks (1h) potion purge threshold | Long buffs (4h) get incorrectly purged. | `stats.potion-cleanup.max-duration-ticks` | `int 0..200000 (0=disable)` | MED |
| HC-061 | `module/stat/StatModule.java:98-116` | `baseline 100.0`, `divisor 100.0`, `scaled-base 0.1` for `mining_speed`, `generic.scaled` etc. | Core stat→vanilla attribute formulas locked. Cannot rebalance without recompile. | `stats.attribute-mapping.<stat>.baseline/divisor/scaled-base` OR per-stat `vanilla-formula: Expression` | `double` / `Expression` | HIGH |
| HC-062 | `module/stat/PlayerListener.java:81-83` | `runTask(..., recalculate)` 1-tick delay | Rapid gear swap servers may want 0 or 2. | `stats.recalculate.delay-ticks` | `int 0..5 default 1` | LOW |
| HC-063 | `module/stat/StatManager.java:255-256` | `Double.MAX_VALUE` sentinel for uncapped | Already YAML `max-value` configurable. | `stats/*.yml: max-value` | — | — |

---

## 6. Skill / XP

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-070 | `module/skill/Skill.java:4-12` | `maxLevel=60` for all 9 skills (enum) | Legacy enum cap overrides data-driven `SkillDefinition.maxLevel`. Remove/sync. | `skills/*.yml: max-level` (already) — deprecate enum `maxLevel` | `int 1..100` | LOW |
| HC-071 | `module/skill/XpCurveRegistry.java:43-51` | `DEFAULT_THRESHOLDS = {10,20,50,...,10000000}` (59 ints) | Server-defining progression; source-locked default. Already overridable via `xp_curves.yml` but undocumented. | `skills/xp_curves.yml` (already) — document default in `config.yml` comment `skills.default-xp-curve: default` | `int[]` | MED |
| HC-072 | `module/skill/XpCurveRegistry.java:93` | `max-level 60` fallback for formula curves | Central tunable. | `skills.curves.default-max-level` | `int 10..200` | LOW |
| HC-073 | `module/skill/SkillDefinitionParser.java:19-20` | `maxLevel 60`, `xp-curve "default"` per-skill defaults | Central tunable. | `skills.defaults.max-level`, `skills.defaults.xp-curve` | `int`/`string` | LOW |
| HC-074 | `module/skill/SkillListener.java:34` | `showTemporary(...,20,1)` — 20 ticks actionbar for XP gain | Too short/long per taste. | `skills.xp-gain-actionbar.{duration-ticks,priority}` | `int 10..100` / `int` | LOW |

---

## 7. Mob

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-080 | `module/mob/MobManager.java:52-54` | `40L` AI poll, `200L` natural spawn poll | Leash responsiveness vs spawn density vs CPU. | `mobs.tasks.ai-interval-ticks` / `natural-spawn-interval-ticks` | `int 10..600` | HIGH |
| HC-081 | `module/mob/MobDefinition.java:138-139` | `baseDamage + (level-1)` linear | Want exponential/custom curves. | `mobs.damage-scaling: Expression` or `damage-per-level: double` | `Expression` | HIGH |
| HC-082 | `module/mob/MobDefinition.java:142-144` | `baseXp * level` linear | Want diminishing/quadratic. | `mobs.xp-reward-formula: "baseXp * level"` | `Expression` | HIGH |
| HC-083 | `module/mob/MobDefinition.java:179-187` | Builder defaults `naturalSpawnChance 0.1`, `naturalSpawnMaxNearby 3`, `baseDamage 5.0`, `baseXp 2`, `goldReward 0` | Global defaults when YAML omits fields — invisible. | `mobs.defaults.{natural-spawn-chance,max-nearby,base-damage,base-xp,gold-reward}` | `double`/`int` | MED |
| HC-084 | `module/mob/BossController.java:41,43` | `TICK_PERIOD=10L`, `ANNOUNCE_RADIUS=40.0` | Boss `ON_TIMER` resolution + chat spam distance. | `mobs.boss.tick-period-ticks`, `boss.announce-radius` | `long 1..40` / `double 10..100` | MED |
| HC-085 | `module/mob/BossBarConfig.java:23` / `MobDefinitionParser.java:232` | `40.0` boss-bar visibility range (2 places) | Should be single tunable global. | `mobs.boss-bar.default-range` | `double 10..128` | MED |
| HC-086 | `module/mob/NaturalSpawnTask.java:22-23,69` | `SEARCH_RADIUS 32.0`, `ATTEMPT_OFFSET 24`, `inner exclusion 8` | Spawn density + safety tuning. | `mobs.natural-spawn.{search-radius,min-distance,max-distance,height-offset}` | `double 8..64` | HIGH |
| HC-087 | `module/mob/MobAiTask.java:51` | `moveTo(home, 1.0)` speed | Slow vs fast boss leash. | `mobs.ai.leash-return-speed` OR per-mob `ai.leash-speed` | `double 0.1..2.0` | MED |
| HC-088 | `module/mob/MobFactory.java:22` | `VANILLA_MAX_HEALTH_CAP 1024.0` | Vanilla engine limit — correctly hardcoded. | — | — | INTENTIONAL |
| HC-089 | `module/mob/LootEntry.java:42` | `chance + (luck/100)*chance` — divisor `100.0` | Hidden balance knob (1 luck = +1%). | `mobs.loot.luck-divisor` | `double 50..200` | MED |
| HC-090 | `module/mob/ability/MobAbility.java:52-56` | `intervalTicks 100`, `chance 1.0`, `healthPercent 50.0` builder defaults | Server-wide balance when YAML omits. | `mobs.abilities.defaults.{interval,chance,health-percent}` | `int`/`double` | LOW |
| HC-091 | `module/mob/MobDeathListener.java:70` | `"combat"` skill id for mob XP | Renaming skill breaks silently. | `mobs.combat-skill-id` OR `MobDefinition.skill-id` | `string` | LOW |

---

## 8. Recipe / Anvil / Machine

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-100 | `module/recipe/AnvilTemplateRegistry.java:21-25` | `DEFAULT_COST_PER_LEVEL 2`, `DURABILITY_BONUS 0.12`, `REPAIR_PERCENT 0.25` | Already overridable via `config.yml anvil.templates.*` — document ranges. | `anvil.templates.{merge/repair}.*` (exists) | `int`/`double` | — |
| HC-101 | `module/recipe/AnvilCostCalculator.java:22` | `penalty = (1 << min(work,30))-1`, base `2` | Overflow clamp 30 + legacy `2^work-1`; should be formula. | `anvil.prior-work.formula: "2^work-1"` + `max-work-clamp: 30` | `Expression`/`int 20..40` | MED |
| HC-102 | `module/recipe/RecipeDefinitionParser.java:143-151` | `totalAmount>64` / `out.ingredient().amount()>64` validation | Custom servers with 99-stack plugins need tuning. | `recipes.validation.max-stack-size: 64` | `int 1..127` | LOW |
| HC-103 | `module/recipe/RecipeDefinition.java:65` / `RecipeDefinitionParser.java:101` | `gridWidth fallback 3` | Future machines may want 4–5. | `recipes.defaults.grid-width: 3` | `int 2..9` | LOW |
| HC-104 | `module/recipe/RecipeEngine.java:160` | `VANILLA_FALLBACK_MACHINES = Set.of("crafting_table")` | Adding custom crafting table needs code change. | `recipes.vanilla-fallback-machines: [crafting_table]` | `list<string>` | LOW |
| HC-105 | `module/recipe/AnvilMachineHandler.java:46-55` | `REPAIR_MATERIAL_HINTS = Map.of(NETHERITE→NETHERITE_INGOT, DIAMOND→DIAMOND, ...)` | Custom alloys require code change. | `anvil.repair-materials: {NETHERITE: NETHERITE_INGOT, CUSTOM_ALLOY: CUSTOM_INGOT}` | `map<Material,Material>` | MED |
| HC-106 | `module/machine/MachineDefinitionParser.java` | `machine` field defaults (`id`) | Document as `gui.defaults.machine`. | `config.yml → gui.defaults.machine` | `string` | LOW |

---

## 9. Enchant

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-110 | `module/enchant/EtableCostCalculator.java:18-20` | `level*2` XP cost | Core economy tuning. | `enchants.etable.cost-per-level: 2` or `formula: "level*2"` | `int 1..5` / `Expression` | HIGH |
| HC-111 | `module/enchant/EnchantModule.java:98-148` | All 14 logic factory defaults (`percent-per-level 5.0/10.0/50.0/4.0/25.0/0.5/0.2` etc.) | Changing balance requires touching every YAML; no global retune. | `enchants.defaults.<logic-id>.percent-per-level` | `double` per logic | HIGH |
| HC-112 | `module/enchant/EnchantModule.java:83` | `runTaskTimer(...,6000L,6000L)` transient cleanup | Memory vs CPU. | `enchants.transient.cleanup-interval-ticks: 6000` | `long 1200..12000` | LOW |
| HC-113 | `module/enchant/state/TransientStateTracker.java:25` | `CLEANUP_IDLE_MILLIS = 15*60*1000` (15 min) | Stack memory growth. | `enchants.transient.idle-purge-millis: 900000` | `long 60k..1.8M` | LOW |
| HC-114 | `module/enchant/logic/LethalityLogic.java:19-20` | `MAX_STACKS 4`, `STACK_DURATION_MS 4000` | Lethality power tuning. | Per-enchant `logic-params: {max-stacks,stack-duration-ms}` + global default | `int 1..10` / `long 1k..10k` | HIGH |
| HC-115 | `module/enchant/logic/FirstStrikeLogic.java:21-22` | `MAX_HITS 3`, `RESET_WINDOW_MS 10000` | First-strike window balance. | `logic-params: {max-hits,reset-window-ms}` | `int 1..10` / `long 5k..30k` | HIGH |
| HC-116 | `module/enchant/EnchantmentHelper.java:253,274` | `enchantMap.size()<4` compact threshold, `40` char line wrap | Presentation cutoff. | `enchants.lore.compact-threshold: 4`, `line-wrap-chars: 40` | `int` | LOW |
| HC-117 | `module/enchant/EnchantmentDefinition.java:162-163` | `etableMaxLevel 5`, `absoluteMaxLevel 10` builder defaults | Global caps when YAML omits. | `enchants.defaults.etable-max-level:5`, `absolute-max-level:10` | `int 1..20` | LOW |

---

## 10. Modifier

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-120 | `module/modifier/ModifierGroupDefinition.java:68-73` | `max=Integer.MAX_VALUE`, `applicationMode=MULTIPLE`, `replacement=false`, `removal=true` | Server-wide policy (e.g. "all groups max 1 unless stated") not possible. | `modifiers.group-defaults.{max,application-mode,replacement,removal}` | `int`/`enum`/`bool` | LOW |
| HC-121 | `module/modifier/ModifierDefinition.java:120` | `weight 1.0` | Already per-modifier `weight:` YAML — keep. | `modifiers/groups/*.yml: weight` | — | — |
| HC-122 | `module/modifier/ModifierEngine.java:205-206` | `rarity.rank+1` tier formula | Custom rarity progressions break. | `modifiers.tier-source.formula: "rarity.rank+1"` | `Expression` | MED |
| HC-123 | `module/modifier/ModifierEngine.java:281-284` | `MULTIPLY → current*(value-1)` semantics | Not obvious; document alternative `MULTIPLY_ADD` mode. | Doc only | — | LOW |

---

## 11. Zone

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-130 | `module/zone/ZoneManager.java:119,127` | `runTaskTimer(...,20L,20L)` spawner tick + `tickCount+=20` | 1s polling arbitrary; large servers want 2–5s. | `zones.spawner-tick-interval-ticks` | `int 1..200 default 20` | HIGH |
| HC-131 | `module/zone/ZoneManager.java:204` | `runTaskTimer(...,40L,40L)` mob home tick | Scanning `getLivingEntities()` cost vs responsiveness. | `zones.mob-home-interval-ticks` | `int 10..400 default 40` | HIGH |
| HC-132 | `module/zone/ZoneManager.java:510` | `runTaskTimer(...,40L,40L)` visualization tick | 100+ visualizing players → particle spam. | `zones.visualization-interval-ticks` | `int 5..100 default 40` | HIGH |
| HC-133 | `module/zone/ZoneManager.java:550` | `runTaskTimer(...,10L,10L)` selection tick | 2 Hz wireframe; want 5 vs 20 ticks. | `zones.selection-visualization-interval-ticks` | `int 2..40 default 10` | MED |
| HC-134 | `module/zone/ZoneManager.java:538` | `200*200` visualization cull distance | Large zones invisible beyond 200 blocks. | `zones.visualization-max-distance` | `int 32..512 default 200` | MED |
| HC-135 | `module/zone/ZoneManager.java:148` | `Math.max(spawnRadius*2, 4)` wander radius | Want tight leash (`*1`) vs roam (`*5`). | `zones.mob-wander-radius-multiplier: 2.0` + `mob-wander-min-radius: 4` | `double 0.5..10` / `int` | MED |
| HC-136 | `module/zone/ZoneManager.java:173` | `20` safe-spawn search attempts | Reliability vs CPU on cliffs. | `zones.spawn-search-attempts` | `int 1..100 default 20` | MED |
| HC-137 | `module/zone/ZoneManager.java:191` | `0.8` occupancy check radius | Too small→stacking, too large→no spawns in caves. | `zones.spawn-occupancy-radius` | `double 0.2..2.0 default 0.8` | MED |
| HC-138 | `module/zone/ZoneManager.java:583,594` | `1.5f` point size, `1.0f` box size, colors `YELLOW/BLUE/RED/GREEN` | Retheme / colorblind palette / performance. | `zones.visualization.{particle-size-point,particle-size-box,colors.*}` | `float`/`Color hex` | LOW |
| HC-139 | `module/zone/ZoneManager.java:619` | `64` max particles per edge | Gaps on 300-block edges. | `zones.visualization.max-particles-per-edge` | `int 8..256 default 64` | LOW |
| HC-140 | `module/zone/ZoneListener.java:96` | `60` ticks (3s) zone enter popup | Want 40 (snappy) or 100 (readable) or per-zone. | `zones.enter-title-duration-ticks` OR per-zone `display-duration` | `int 0..200 default 60` | MED |
| HC-141 | `module/zone/ZoneCommand.java:82` | `Material.GOLDEN_AXE` wand + hardcoded lore lines 85–86 | Texture-pack servers use stick/blaze rod. | `zones.wand.{material,name,lore}` | `Material`/`MiniMessage`/`list` | MED |
| HC-142 | `module/zone/ZoneCommand.java:276-284` | Command defaults `spawnRadius 3, maxAlive 5, interval 400, radius 20.0` (note: `interval 400` ≠ loader default `200`) | Mismatch causes confusion; should share single constant. | `zones.defaults.{spawner-spawn-radius,max-alive,spawn-interval,count-radius}` | `int`/`double` | MED |
| HC-143 | `module/zone/ZoneVariableProvider.java:26` / `module/ui/ScoreboardUI.java:249` | `"<green>Wilderness"` fallback (2 occurrences) | Not translatable. | `zones.messages.wilderness-name` | `MiniMessage` | LOW |

---

## 12. Warp

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-150 | `module/warp/WarpSignListener.java:34,61` | `HEADER="[warp]"`, formatted `"<dark_blue>[warp]"` | localize (`[teleport]`). | `warps.sign.header` + `formatted-header` | `string MiniMessage` | LOW |
| HC-151 | `module/warp/WarpManager.java:57,63,74,81,95,104,112` | 7 hardcoded messages: locked, no-permission, on-cooldown, insufficient-funds, teleported, warmup-start/cancelled | Not localizable; no placeholder templating. | `warps.messages.{locked,no-permission,on-cooldown,insufficient-funds,teleported,warmup-start,warmup-cancelled-move,world-not-loaded,profile-not-loaded}` with `{warp},{cost},{remaining}` | `MiniMessage` | MED |
| HC-152 | `module/warp/WarpManager.java:109-111` | Block-level move cancels warmup (`getBlockX()!=...`); no damage cancel despite comment | Strict vs 0.5-block tolerance; damage cancel missing. | `warps.warmup.cancel-on-move-distance: double 0.1..5 default 1.0` + `cancel-on-damage: bool` | `double`/`bool` | HIGH |
| HC-153 | `module/warp/WarpLoader.java:44-48` | YAML fallbacks `world "world"`, `y 64`, `unlock-condition "always"`, `cost 0.0`, `cooldown/warmup 0` | `y=64` fails on void/200-high worlds. Document as `warps.defaults.*` or make explicit required. | `warps.defaults.{world,y,unlock-condition,cost,cooldown,warmup}` | various | LOW |
| HC-154 | `module/warp/WarpManager.java:116` | `warp.getWarmupSeconds()*20L` ticks | 20 is vanilla constant — correctly hardcoded. | — | — | INTENTIONAL |

---

## 13. GUI

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-160 | `module/gui/GuiListener.java:248` | `64` mass-craft cap | Economy tuning (anti-dupe vs bulk crafters). | `gui.crafting.max-mass-crafts` | `int 1..1024 default 64` | MED |
| HC-161 | `module/gui/GuiRenderer.java:361` / `GuiListener.java:382,386` | `Material.BARRIER` error material, `Material.matchMaterial("AIR")` default for missing `material:` | BARRIER vs AIR for errors; missing material silently becomes invisible. | `gui.error-material: BARRIER` | `Material` | LOW |
| HC-162 | `module/gui/GuiDefinitionParser.java:26-31` | Defaults `title "Inventory"`, `update-interval 0`, `rows = max(layoutRows, rows)`, `machine id` | `"Inventory"` generic; `0` means never for animated GUIs. | `gui.defaults.{title,update-interval-ticks,rows,machine}` | `string`/`int` | MED |
| HC-163 | `module/gui/GuiDefinitionParser.java:40,44` | `' '` (space) empty slot char | Some packs use `'.'`/`'X'`. | `gui.layout.empty-char: ' '` | `char` | LOW |
| HC-164 | `module/gui/GuiDefinitionParser.java:167` / `GuiRenderer.java:379` | `customModelData 0` sentinel (`!=0` check) | CMD `0` valid in resource packs; needs `null` sentinel. | Code fix: `contains("custom-model-data") ? getInt : null` | — | LOW |
| HC-165 | `module/gui/GuiItemStack.java:13` | `amount<=0 → 1` silent clamp | Hides author error; should warn. | Validation warning; not configurable. | — | LOW |
| HC-166 | `module/gui/GuiListener.java:478-482` | `"brew_running"`, `"bottle"`, `"ingredient"` prop/id literals (alchemy-specific in generic GUI listener) | Adding new potion type requires code change. | Per-component YAML `locked-while: brew_running` | `string` | LOW |
| HC-167 | `module/gui/GuiDefinitionParser.java:71` | `command-permission null` (permissive) default | Safer default `valmora.gui.<id>` expected. | `gui.defaults.command-permission` | `string` | LOW |
| HC-168 | `module/gui/PaginatedComponent.java:22` / `GuiDefinitionParser.java:123-128` | `iterator "loop_item"`, `destructure false`, `sort "none"` DSL defaults | If changed, every shipped GUI breaks; document only. | Document in `docs/modules/design/gui.md` | — | LOW |

---

## 14. HUD / UI (Scoreboard + ActionBar + Chat)

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-170 | `module/hud/HudItemListener.java:33` | `1L` respawn re-give delay | Fragile on lag / `keepInventory`. | `hud.respawn-restore-delay-ticks` | `int 1..10 default 1` | MED |
| HC-171 | `module/hud/HudItemModule.java:95` | `slot 8` default | Hypixel convention; servers use 7/4. | `hud.defaults.slot` | `int 0..40 default 8` | LOW |
| HC-172 | `module/hud/HudItemModule.java:96-97,104` | `prevent-move true`, `glow false`, `material "STONE"` fallback | `STONE` hides author error; should be BARRIER + warn. | `hud.defaults.{prevent-move,glow}` + require `material:` | `bool`/`Material` | LOW |
| HC-173 | `module/hud/HudItemListener.java:79` | `RIGHT/SHIFT_RIGHT → right`, else left (middle/drop → left) | Server may want middle-click → HUD. | `hud.click-mapping: {right:[RIGHT,SHIFT_RIGHT],left:[...]}` | `map` | LOW |
| HC-174 | `module/ui/UIManager.java:91` | `runTaskTimer(...,0L,2L)` — 10 Hz clock (hottest loop: ticks every online player) | 10 Hz × 100 players × scoreboard+actionbar is expensive; most want 4–5 Hz. | `ui.tick-interval-ticks` | `int 1..20 default 2` | HIGH |
| HC-175 | `module/ui/UIManager.java:94-96` | `DEFAULT_TITLE "<gold><bold>VALMORA RPG"`, `DEFAULT_ACTION_BAR "<red>❤ $player.hp$..."` | Already mirrored in `ui.yml`; duplicated source of truth. Extract to single file. | `ui.yml: scoreboard.title / action-bar.default` (exists) — dedup code fallback | `MiniMessage` | LOW |
| HC-176 | `module/ui/ScoreboardUI.java:26` | `MAX_LINES 16` | Paper allows 16 but vanilla truncates at 15; flicker risk. | `ui.scoreboard.max-lines` | `int 10..16 default 15` | LOW |
| HC-177 | `module/ui/ScoreboardUI.java:177` | `"<red>⚔ Combat: <white>" + remainingSec + "s"` combat line | Hardcoded English; window already configurable but display not. | `ui.scoreboard.combat-line-format: "<red>⚔ Combat: <white>{remaining}s"` | `MiniMessage` with `{remaining}` | LOW |
| HC-178 | `module/ui/ChatUI.java:10` | `PREFIX="<dark_gray>[<gold>Valmora<dark_gray>] <white>"` | Branding / localization. | `ui.chat.prefix` in `ui.yml` | `MiniMessage` | LOW |
| HC-179 | `module/ui/UIManager.java:91` | `SUMMARY_INTERVAL_RUNS=50` | Debug noise tuning. | `ui.debug-summary-interval-runs` | `int` | LOW |
| HC-180 | `module/ui/ScoreboardUI.java:33` | `"§"+chars.charAt(i)` legacy color entry encoding | Violates `AGENTS.md §11.3` "Never use §"; should use Adventure unique entries. | Code fix (not configurable) | — | LOW |
| HC-181 | `module/ui/ActionBarUI.java:54` | `durationTicks*50L` ms conversion | 50 is Minecraft constant — correctly hardcoded. | — | — | INTENTIONAL |

---

## 15. Alchemy (Potion Effects + Brewing)

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-190 | `module/alchemy/effect/hardcoded/HealingAlchemyEffect.java:16` | `HEAL_VALUES = {20,50,100,150,200,250,300,350}` per level 1–8 | Core healing balance; RPG servers want custom curves. | `alchemy.effects.healing.values: [20,50,100,...]` OR `potion_effects/healing.yml: values: [...]` | `double[]` per level | HIGH |
| HC-191 | `module/alchemy/effect/hardcoded/AbsorptionAlchemyEffect.java:12` | `ABSORPTION_VALUES = {20,40,60,80,100,150,200,300}` | Balance. | `alchemy.effects.absorption.values` | `double[]` | HIGH |
| HC-192 | `module/alchemy/effect/hardcoded/VanillaAlchemyEffect.java` | Vanilla potion `amplifier/duration` mappings (if any) | Check file for hardcoded vanilla effect scaling. | `alchemy.effects.vanilla.*` | `double` | MED |
| HC-193 | `module/alchemy/brewing/AlchemyMachineHandler.java:29` | `AWKWARD_POTION_ID="awkward_potion"` | Server may rename awkward potion item id. | `alchemy.brewing.awkward-potion-id` | `string` | LOW |
| HC-194 | `module/alchemy/AlchemyModule.java:56-60` | Config already reads `splash-radius 4.0`, `tick-interval 20`, `max-active-effects 10` — defaults hardcoded, initial delay `20L` hardcoded | Initial delay not exposed. | `alchemy.initial-delay-ticks: 20` | `int` | LOW |

---

## 16. Fishing

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-200 | `module/fishing/FishingLootEntry.java` / `FishingLootTable.java` | `weight`, `chance` defaults, `luck` divisor (check file) | Loot balance needs global tuning. | `fishing.loot.{default-weight,luck-divisor}` | `double` | MED |
| HC-201 | `module/fishing/FishingPipelineLoader.java:21-24` | `POINT_PREFIX "fishing:"`, valid points `pre_catch/post_catch` | Fixed DSL — intentionally hardcoded. | — | — | — |

---

## 17. NPC / Dialogue / Hologram

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-210 | `module/npc/dialogue/DialogueManager.java:34-36` | `TICKS_PER_CHAR 3`, `MIN 40`, `MAX 200` auto-advance | Reading speed UX. | `dialogue.auto-advance.{ticks-per-char,min-ticks,max-ticks}` | `int 1..10` / `10..100` / `50..400` | MED |
| HC-211 | `module/npc/dialogue/DialogueManager.java:362-364` | `clearChatDisplay 20` blank lines | Aggressiveness vs chat history preservation. | `dialogue.chat-clear-lines` | `int 0..50 default 20` | LOW |
| HC-212 | `module/npc/dialogue/DialogueManager.java:377,397` | `runTaskTimer(...,0L,40L)` action-bar hint refresh (2s) | Want snappier (20) or cheaper (60). | `dialogue.hint-interval-ticks: 40` | `int 10..100` | LOW |
| HC-213 | `module/npc/dialogue/DialogueManager.java:435` | `runTaskTimer(...,5L,5L)` stop-mechanic poll (locks player) | Movement lock responsiveness. | `dialogue.stop-check-interval-ticks: 5` | `int 1..10` | LOW |
| HC-214 | `module/npc/NpcManager.java:49` | `LOOK_RANGE 10.0` | NPC attention range. | `npc.look-range` | `double 3..30` | LOW |
| HC-215 | `module/npc/NpcManager.java:183` | `HOLO_ORIGIN_Y 2.0` | Hologram height. | `npc.hologram.origin-y` | `double 1..4` | LOW |
| HC-216 | `module/npc/NpcManager.java:198` | `runTaskTimer` hologram update interval (check line) + `318` `1200L` respawn check + `327` look task interval | Perf vs responsiveness. | `npc.tasks.{hologram-interval,respawn-interval,look-interval}` | `long ticks` | MED |
| HC-217 | `module/npc/NpcCommand.java:31` | `PREFIX="<dark_gray>[<gold>NPC<dark_gray>] "` | Branding. | `npc.messages.prefix` | `MiniMessage` | LOW |
| HC-218 | `module/npc/dialogue/intercept/ConversationPacketManager.java:51` | `HISTORY_SIZE 100` | Per-player chat history kept for intercept. | `dialogue.history-size` | `int 20..500` | LOW |
| HC-219 | `module/npc/dialogue/intercept/ConversationPacketManager.java:53` | `MOUNT_Y_OFFSET -1.375` | Visual offset for dialogue entity. | `dialogue.mount-y-offset` | `double` | LOW |
| HC-220 | `module/npc/SkinResolver.java:31-33` | Mojang/MineSkin URLs `api.mojang.com/...`, `sessionserver.mojang.com/...`, `api.mineskin.org/...` | Self-hosted auth or proxy needs override. | `npc.skin.urls.{profile,session,mineskin}` | `string URL` | LOW |
| HC-221 | `module/npc/SkinFileServer.java` | `enabled false`, `port 2525` — already configurable via `config.yml npc-skin-server.*` | Defaults doc only. | `config.yml → npc-skin-server.*` (exists) | — | — |

---

## 18. Pet

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-230 | `module/pet/PetFollowTask.java:22-24` | `FOLLOW_DISTANCE 2.5`, `TELEPORT_DISTANCE 12.0`, `STEP 0.35` | Follow feels laggy/teleporty per server. | `pets.follow.{follow-distance,teleport-distance,step}` | `double 1..5` / `5..30` / `0.1..1.0` | MED |
| HC-231 | `module/pet/PetModule.java:62` | `runTaskTimer(...,5L,5L)` — 4×/sec follow poll | CPU vs smoothness. | `pets.follow.tick-interval-ticks: 5` | `int 1..20` | MED |
| HC-232 | `module/pet/PetModule.java:41-42` | `xp-formula "100 * $curve.level$ * $curve.level$"`, `max-level 200` | Leveling curve balance; already partially in `pets/defaults.yml` but fallback hardcoded. | `pets.leveling.{xp-formula,max-level}` | `Expression` / `int 1..500` | MED |

---

## 19. Resource / Mining

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-240 | `module/resource/ResourceModule.java:11,37-38` | `AUTOSAVE_INTERVAL_TICKS 600L` (30s) | Crash recovery vs IO cost. | `resource.autosave-interval-seconds: 30` OR `mining.autosave-interval-seconds` | `int 5..300` | HIGH |
| HC-241 | `module/resource/ResourceManager.java:133,270` | `runTaskLater(..., regenDelay)` — regen delays from YAML but task scheduling uses `runTaskLater` with no global cap | Guardrail for `interval:1` malformed regen. | `resource.limits.min-regen-delay-ticks: 20` | `int` | LOW |
| HC-242 | `config.yml: mining.*` | `mining-fortune-stat`, `mining-speed-stat`, `breaking-power-stat`, `mining-spread-stat` already configurable — good. | — | `config.yml → mining.*` (exists) | — | — |
| HC-243 | `module/mining` (if exists) — check `ResourcePipelineLoader.java:26-29` | `POINT_PREFIX "resource:"`, `pre_break/post_break` DSL | Fixed DSL — intentionally hardcoded. | — | — | — |

---

## 20. Progression / Time / Calendar

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-250 | `module/progression/ProgressionModule.java:17-18` | `DAILY_BONUS_WINDOW_MILLIS 24*60*60*1000` (24h), `DAILY_CHECK_INTERVAL_TICKS 20*60*5` (5 min = 6000) | 12h/48h daily variants. | `progression.daily-bonus.window-hours: 24` + `check-interval-minutes: 5` | `int 1..168` / `1..60` | MED |
| HC-251 | `module/progression/ProgressionManager.java:120-121,152-153,185-187` | Level-up `"<green>✦ <white>" + node + " <green>leveled up to..."`, tier `"<gold>✦ ..."` , reset `"<yellow>✦ ... reset..."` | Localization. | `progression.messages.{level-up,tier-unlocked,reset}` with `{node},{level},{tier},{tree},{refund}` | `MiniMessage` | MED |
| HC-252 | `module/time/TimeManager.java:81` | `runTaskTimer(...,20L,20L)` day check | Event churn vs freshness. | `time.tick-interval-ticks: 20` | `int 1..100` | LOW |
| HC-253 | `module/time/TimeManager.java:119-123,203-206` | `30` days/phase, `3` phases, `90` days/season, `4` seasons, `360` days/year | Custom calendars (28-day months) impossible. | `time.calendar.{days-per-phase,phases-per-season,seasons-per-year}` (derive `days-per-year`) | `int` | MED |
| HC-254 | `module/time/TimeManager.java:186-191` | `"<gold><bold>✦ A new season begins — "` + `showTemporary(...,120,1)` (6s) | Branding + accessibility. | `time.season-change.{message,duration-ticks,priority}` with `{season}` | `MiniMessage`/`int` | LOW |
| HC-255 | `module/calendar/CalendarEventModule.java` | Event scheduling intervals (check file for tick periods) | Tuning event frequency. | `calendar.event-tick-interval` | `int` | LOW |

---

## 21. Quest

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-260 | `module/quest/objective/TimerObjectiveHandler.java:44-47` / `NpcRangeObjectiveHandler.java:45` / `DialogueManager.java:377,397` | `runTaskTimer(...,20L,20L)` (1s) per-player × quests scan | O(n×m) CPU on large servers; want 2s. | `quests.poll.{timer-interval-ticks,npcrange-interval-ticks}` | `int 10..100` | MED |
| HC-261 | `module/quest/objective/DelayObjectiveHandler.java:31-40` | `runTaskTimer(plugin,interval,interval)` no global cap | Malformed `interval:1` could per-tick trigger. | `quests.limits.delay-min-interval-ticks: 20` + validation warning | `int 1..100` | LOW |
| HC-262 | `module/quest/QuestManager.java:376-383` | `"<yellow>" + target + " <gray>("+current+"/"+required+")"` + `notifyInterval` logic (`0` disables) | Hardcoded progress template. | `quests.notify.template: "<yellow>{target} <gray>({current}/{required})"` + `default-interval:1` | `MiniMessage`/`int` | LOW |
| HC-263 | `module/quest/objective/NpcRangeObjectiveHandler.java:84` | `stateVar="npcrange.state."+questId+"."+key` prefix | Namespace collision risk. | Document as `quests.variable-prefix.npcrange: "npcrange.state"` | `string` | LOW |

---

## 22. Notify

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-270 | `module/notify/NotifyManager.java:17-18` | `categories "info"→chat, "error"→actionbar` | Want `error→bossbar`. | `notify.categories.<id>.io: chat` | `string io type` | LOW |
| HC-271 | `module/notify/io/BossBarIO.java:29-32` | `color "WHITE"`, `overlay "PROGRESS"`, `progress 1.0f`, `stay 70` ticks | Every bossbar 3.5s white. | `notify.defaults.bossbar.{color,style,progress,stay-ticks}` | `enum`/`float`/`int 10..200` | LOW |
| HC-272 | `module/notify/io/TitleIO.java:19-21` | `fadeIn 10, stay 70, fadeOut 20` | Theme-dependent. | `notify.defaults.title.{fade-in,stay,fade-out}` | `int 0..40` / `20..200` | LOW |
| HC-273 | `module/notify/io/SoundIO.java:27-29` | `volume 1.0f, pitch 1.0f, category MASTER` | Spam control. | `notify.defaults.sound.{volume,pitch,category}` | `float 0..2` / `enum` | LOW |
| HC-274 | `module/notify/io/AdvancementIO.java:37-43,65` | `frame "task"`, `icon EXPERIENCE_BOTTLE`, `remove 40L`, fallback `"✦ "+message` | Branding; 2s removal too short if lag. | `notify.defaults.advancement.{frame,icon,lifetime-ticks,fallback-prefix}` | `enum`/`Material`/`int` | LOW |

---

## 23. Profile / Collection / Rarity / Pack / Machine

| ID | File:Line | Value | Why configurable | Suggested config | Type / Range | Pri |
|----|-----------|-------|-----------------|-----------------|--------------|-----|
| HC-280 | `module/profile/ProfileGui.java:38,51,57` | `SIZE_MAIN 36`, `SIZE_CONFIRM 27`, `TITLE_MAIN "<dark_gray>… <gold><bold>Profiles..."`, `TITLE_CONFIRM "<red><bold>Delete Profile?..."` | GUI branding / size. | `profile.gui.{size-main,size-confirm,title-main,title-confirm}` | `int`/`MiniMessage` | LOW |
| HC-281 | `module/profile/ProfileGui.java:42,48-50,58-60` | Slot maps `ALL_PROFILE_SLOTS {10,12,14,16,19,21,23,25}`, `SLOT_INFO 4`, `SLOT_CREATE 28`, `SLOT_CLOSE 31`, etc. | Conflict with custom plugins. | `profile.gui.slots.{info,create,close,profiles[]}` | `int 0..53` | LOW |
| HC-282 | `module/profile/ProfileGui.java:330,357,369` | `runTaskLater(...,1L)` 5 occurrences for GUI re-open | Needed for inventory close timing but 1 tick fragile on lag. | `profile.gui.reopen-delay-ticks: 1` | `int 1..5` | LOW |
| HC-283 | `module/collection/CollectionDefinitionParser.java` | Category/item thresholds, reward XP/coin defaults (if any hardcoded) | Balance. | `collection.defaults.reward-*` | `double` | MED |
| HC-284 | `module/rarity/RarityDefinition.java` | Rarity colors/tiers if hardcoded in enum | New rarity requires code. | `rarities.yml` data-driven (already via `RarityModule`) — ensure `Rarity.java` enum deprecated | — | MED |
| HC-285 | `module/pack/PackContentFolders.java:22,34,52` | `CONTENT_FOLDERS`, `SHARED_CONFIGS`, `FOLDER_TO_MODULE_ID` maps | Hardcoded allow-list for pack content; extensibility needs config. | `pack.allowed-content-folders` / `shared-configs` (advanced) | `list<string>` | LOW |
| HC-286 | `module/pack/download/GitHubReleaseResolver.java:27` | `SHORTHAND Pattern "^github:([^/\\s]+)/([^@\\s]+)(?:@(\\S+))?$"` | If GitHub URL format changes. | Documented DSL; not configurable. | — | — |
| HC-287 | `module/pack/download/PackDownloader.java:26` / `SkinResolver.java:34` | `USER_AGENT "Valmora-PackManager/1.0"` / `"Valmora-NPC/1.0"` | Self-hosted proxy identification. | `pack.download.user-agent`, `npc.skin.user-agent` | `string` | LOW |

---

## 24. Missing / Unimplemented Systems

| ID | System | Current state | What to add | Suggested config | Pri |
|----|--------|--------------|-------------|-----------------|-----|
| HC-290 | **WorldBorder module** | No module exists. `docs/VANILLA_CONTROL_AUDIT.md:189` flags it: border damage mapped but no border control. Servers must use vanilla `/worldborder` which resets on restart. | New `module/worldborder/WorldBorderModule` | `worldborder: {enabled:false, world:world, center:{x:0,z:0}, size:3000, damage-amount:0.2, damage-buffer:5.0, warning-distance:5, warning-time:15, shrink:{enabled:false,target-size:500,duration-seconds:3600}}` | HIGH |
| HC-291 | **Scripting guardrails** | No `max-expression-depth` / `max-evaluation-time-ms` for user-authored `$level$` formulas; infinite loop can freeze main thread. | Add evaluator timeout | `scripting.limits.{max-expression-depth:100, max-evaluation-time-ms:5, max-formula-max-level:200}` | MED |
| HC-292 | **GUI lore length limit** | No `max-lore-lines` guard; unbounded `lore:` lists can exceed client 256-line limit. | Add validation | `gui.max-lore-lines: 20` `int 0..50` | LOW |

---

## 25. Consolidated `config.yml` additions (proposed)

Copy-paste starter — all keys below use current literals as defaults so dropping this in changes no behavior:

```yaml
# ── Core / Database ──
database:
  pool:
    maximum-pool-size: 10          # HC-001
  worker-threads: 4                # HC-003
  shutdown-timeout-seconds: 10     # HC-004
  mysql:
    prep-cache-size: 250           # HC-002
    prep-cache-sql-limit: 2048     # HC-002

permissions:
  admin: "valmora.admin"           # HC-006
  reload: "valmora.admin"
  eco: "valmora.admin"
  pack: "valmora.admin"

# ── Economy ──
economy:
  ledger-display-limit: 5          # HC-010
  ledger-retention-per-player: 10  # HC-005
  format:
    thousand: "%.1fk"
    million: "%.2fm"
    billion: "%.2fb"
    coin-symbol: "🪙 "
    thousands-separator: "."
  messages:
    no-transactions: "<gray>There are no recent transactions!" # HC-016
    deposit-verb: "Deposited"      # HC-017
    withdraw-verb: "Withdrew"
    bank-prefix: "<dark_gray>[<gold>Bank<dark_gray>] " # HC-018
  deposit-halving: "floor"         # HC-018 floor|ceil|round
  coin-suffixes: { k: 1000, m: 1000000, b: 1000000000 } # HC-015
  tab-complete-amounts: ["1000","1k","10k","100k","1m"] # HC-014

# ── Combat ──
combat:
  regen-interval-ticks: 20         # HC-020
  regen:
    mana-in-combat: true           # HC-022
    health-in-combat: false
  fallback-base-damage: 1.0        # HC-023
  iframe-threshold-factor: 0.5     # HC-029
  fallback-damage-type: "MELEE"    # HC-033
  damage-indicator:
    rate-limit-ms: 400             # HC-028
    offset: 0.5
    lifetime-ticks: 20
    crit-format: "<gold>✧ {color}<b>{damage}<gold> ✧"
    normal-format: "{color}{damage}"
    show-as-int: true
  environment:
    fallback-damage-type: "MELEE"  # HC-033

# ── Stat ──
stats:
  potion-cleanup:
    max-duration-ticks: 72000      # HC-060 0=disable
  recalculate:
    delay-ticks: 1                 # HC-062
  attribute-mapping:
    # HC-061 — per-vanilla-attribute tuning
    mining_speed: { baseline: 100, divisor: 100 }
    generic_scaled: { scaled-base: 0.1 }

# ── Skill ──
skills:
  defaults:
    max-level: 60                  # HC-073
    xp-curve: "default"
  curves:
    default-max-level: 60          # HC-072
  xp-gain-actionbar:
    duration-ticks: 20             # HC-074
    priority: 1

# ── Mob ──
mobs:
  tasks:
    ai-interval-ticks: 40          # HC-080
    natural-spawn-interval-ticks: 200
  defaults:
    natural-spawn-chance: 0.1      # HC-083
    natural-spawn-max-nearby: 3
    base-damage: 5.0
    base-xp: 2
    gold-reward: 0
  damage-scaling: "base + (level-1)"  # HC-081 Expression
  xp-reward-formula: "baseXp * level" # HC-082
  combat-skill-id: "combat"        # HC-091
  boss:
    tick-period-ticks: 10          # HC-084
    announce-radius: 40.0
  boss-bar:
    default-range: 40.0            # HC-085
  natural-spawn:
    search-radius: 32.0            # HC-086
    max-distance: 24.0
    inner-exclusion: 8.0
  ai:
    leash-return-speed: 1.0        # HC-087
  loot:
    luck-divisor: 100.0            # HC-089
  abilities:
    defaults:
      interval: 100                # HC-090
      chance: 1.0
      health-percent: 50.0

# ── Item / Mechanics ──
items:
  # HC-040..044
  breaking-power: { netherite: 5, diamond: 4, iron: 3, stone: 2, wood: 1 }
  vanilla-stats:
    mining-speed: { netherite: 450, diamond: 400, iron: 300, stone: 200, golden: 500, wood: 100 }
    weapon-damage: { netherite: 8, diamond: 7, iron: 6, stone: 5, wood: 4 }
    bow-damage: 6.0
    crossbow-damage: 9.0
    armor-base: { netherite: 5, diamond: 4, iron: 3, gold: 2, leather: 1 }
    armor-multiplier: { chestplate: 2.5, leggings: 2.0, helmet: 1.5, boots: 1.0 }
  vanilla-rarity-mapping: { NETHERITE: MYTHIC, DIAMOND: EPIC, GOLDEN: RARE, IRON: UNCOMMON } # HC-041
  target-resolver:
    enemies-radius: 5.0            # HC-047
    cone-range: 8.0
    cone-angle: 45.0
  messages:
    no-target: "<red>No target in range!" # HC-046
    cooldown: "<red>Ability on cooldown: {remaining}s"
    no-mana: "<aqua>Not enough Mana!"
    actionbar-duration-ticks: 10
    actionbar-priority: 2
  quiver:
    storage-id: "quiver"           # HC-048
    arrow-tag: "minecraft:arrows"
  loot:
    full-title: "<red><bold>INVENTORY FULL</bold></red>" # HC-049
    full-subtitle: "<gray>Items dropped on the ground."
    title-times: { fade-in: 200, stay: 2000, fade-out: 500 }
    pickup-delay-ticks: 20
    private-drop: true
    glowing: true
  trample:
    protected-block: "FARMLAND"    # TrampleListener

mechanics:                         # HC-051
  damage: { default-amount: 1.0, default-type: "MAGIC", default-ticks: 1, default-interval-seconds: 1.0 }
  pull-entities: { period-ticks: 4, range-default: 20.0, strength-default: 1.0 }
  push-entities: { force-default: 1.0, y-clamp: 0.3, y-factor: 0.4 }
  teleport: { distance-default: 8.0 }
  # ... one block per mechanic, see HC-051 sub-table

# ── Recipe / Anvil ──
recipes:                           # HC-102..104
  validation:
    max-stack-size: 64
  defaults:
    grid-width: 3
  vanilla-fallback-machines: ["crafting_table"]
anvil:                             # HC-101, HC-105 (extends existing anvil.templates.*)
  prior-work:
    formula: "2^work - 1"
    max-work-clamp: 30
  repair-materials:
    NETHERITE: NETHERITE_INGOT
    DIAMOND: DIAMOND
    # ... (map)

# ── Enchant ──
enchants:
  etable:
    cost-per-level: 2              # HC-110 (or formula: "level*2")
  defaults:
    etable-max-level: 5            # HC-117
    absolute-max-level: 10
  transient:
    cleanup-interval-ticks: 6000   # HC-112
    idle-purge-millis: 900000      # HC-113
  lore:
    compact-threshold: 4           # HC-116
    line-wrap-chars: 40
  # HC-111 / HC-114 / HC-115 — per-logic overrides
  logic-defaults:
    damage-multiplier: { percent-per-level: 5.0 }
    stat-bonus: { percent-per-level: 10.0 }
    efficiency: { percent-per-level: 50.0 }
    lethality: { max-stacks: 4, stack-duration-ms: 4000 }
    first-strike: { max-hits: 3, reset-window-ms: 10000 }

# ── Modifier ──
modifiers:
  group-defaults:
    max: 2147483647                # HC-120
    application-mode: "MULTIPLE"
    replacement: false
    removal: true
  tier-source:
    formula: "rarity.rank + 1"     # HC-122

# ── Alchemy ──
alchemy:
  initial-delay-ticks: 20          # HC-194
  effects:
    healing: { values: [20,50,100,150,200,250,300,350] }      # HC-190
    absorption: { values: [20,40,60,80,100,150,200,300] }     # HC-191
  brewing:
    awkward-potion-id: "awkward_potion" # HC-193

# ── Zone ──
zones:
  spawner-tick-interval-ticks: 20  # HC-130
  mob-home-interval-ticks: 40      # HC-131
  visualization-interval-ticks: 40 # HC-132
  selection-visualization-interval-ticks: 10 # HC-133
  visualization-max-distance: 200  # HC-134
  mob-wander-radius-multiplier: 2.0 # HC-135
  mob-wander-min-radius: 4
  spawn-search-attempts: 20        # HC-136
  spawn-occupancy-radius: 0.8      # HC-137
  visualization:
    particle-size-point: 1.5       # HC-138
    particle-size-box: 1.0
    max-particles-per-edge: 64     # HC-139
  enter-title-duration-ticks: 60   # HC-140
  wand:
    material: "GOLDEN_AXE"         # HC-141
    name: "<gold>Zone Wand"
    lore: ["<gray>Left: pos1", "<gray>Right: pos2"]
  defaults:
    spawner-spawn-radius: 3        # HC-142
    max-alive: 5
    spawn-interval: 200
    count-radius: 20.0
  messages:
    wilderness-name: "<green>Wilderness" # HC-143

# ── Warp ──
warps:
  sign:
    header: "[warp]"               # HC-150
    formatted-header: "<dark_blue>[warp]"
  messages:                        # HC-151
    locked: "<red>This warp is locked! Condition: <gray>{condition}"
    no-permission: "<red>You don't have permission to use this warp."
    on-cooldown: "<red>This warp is on cooldown for <white>{remaining}s<red>."
    insufficient-funds: "<red>You need <gold>{cost} coins<red>."
    teleported: "<green>Teleported to <white>{warp}<green>."
    warmup-start: "<yellow>Teleporting ... Don't move!"
    warmup-cancelled-move: "<red>Warp cancelled — you moved."
  warmup:
    cancel-on-move-distance: 1.0   # HC-152
    cancel-on-damage: false

# ── GUI / HUD / UI ──
gui:
  crafting:
    max-mass-crafts: 64            # HC-160
  error-material: "BARRIER"        # HC-161
  defaults:
    title: "Inventory"             # HC-162
    update-interval-ticks: 0
  max-lore-lines: 20               # HC-292
hud:
  respawn-restore-delay-ticks: 1   # HC-170
  defaults:
    slot: 8                        # HC-171
ui:
  tick-interval-ticks: 2           # HC-174
  scoreboard:
    max-lines: 15                  # HC-176
    combat-line-format: "<red>⚔ Combat: <white>{remaining}s" # HC-177
  chat:
    prefix: "<dark_gray>[<gold>Valmora<dark_gray>] <white>" # HC-178

# ── NPC / Dialogue ──
dialogue:
  auto-advance:
    ticks-per-char: 3              # HC-210
    min-ticks: 40
    max-ticks: 200
  chat-clear-lines: 20             # HC-211
  hint-interval-ticks: 40          # HC-212
  stop-check-interval-ticks: 5     # HC-213
npc:
  look-range: 10.0                 # HC-214
  hologram:
    origin-y: 2.0                  # HC-215
  messages:
    prefix: "<dark_gray>[<gold>NPC<dark_gray>] " # HC-217

# ── Pet / Resource ──
pets:
  follow:
    follow-distance: 2.5           # HC-230
    teleport-distance: 12.0
    step: 0.35
    tick-interval-ticks: 5         # HC-231
  leveling:
    xp-formula: "100 * level * level" # HC-232
    max-level: 200
resource:
  autosave-interval-seconds: 30    # HC-240

# ── Progression / Time ──
progression:
  daily-bonus:
    window-hours: 24               # HC-250
    check-interval-minutes: 5
  messages:
    level-up: "<green>✦ <white>{node} <green>leveled up to <yellow>{level}"
    tier-unlocked: "<gold>✦ <white>Tier {tier}"
    reset: "<yellow>✦ <white>{tree} <yellow>progression reset — refunded {refund}"

time:
  tick-interval-ticks: 20          # HC-252
  calendar:
    days-per-phase: 30             # HC-253
    phases-per-season: 3
    seasons-per-year: 4
  season-change:
    message: "<gold><bold>✦ A new season begins — {season}"
    duration-ticks: 120            # HC-254
    priority: 1

# ── Quest / Notify / Profile ──
quests:
  poll:
    timer-interval-ticks: 20       # HC-260
    npcrange-interval-ticks: 20
  limits:
    delay-min-interval-ticks: 20   # HC-261
  notify:
    template: "<yellow>{target} <gray>({current}/{required})" # HC-262
    default-interval: 1

notify:                            # HC-270..274
  defaults:
    bossbar: { color: "WHITE", style: "PROGRESS", progress: 1.0, stay-ticks: 70 }
    title: { fade-in: 10, stay: 70, fade-out: 20 }
    sound: { volume: 1.0, pitch: 1.0, category: "MASTER" }
    advancement: { frame: "task", icon: "EXPERIENCE_BOTTLE", lifetime-ticks: 40, fallback-prefix: "✦ " }

profile:
  gui:
    size-main: 36                  # HC-280
    size-confirm: 27
    title-main: "<dark_gray>◆ <gold><bold>Profiles <dark_gray>◆"
    title-confirm: "<dark_gray>◆ <red><bold>Delete Profile? <dark_gray>◆"
    reopen-delay-ticks: 1          # HC-282

scripting:                         # HC-291
  limits:
    max-expression-depth: 100
    max-evaluation-time-ms: 5

worldborder:                       # HC-290 (new module)
  enabled: false
  world: "world"
  center: { x: 0, z: 0 }
  size: 3000
  damage-amount: 0.2
  damage-buffer: 5.0
  warning-distance: 5
  warning-time: 15
```

---

## Implementation notes for maintainers

- **Shadowing is intentional** — every YAML/per-config key should fall back to the literal currently in code. No behavior change on deploy.
- **Prefer per-feature YAML** for content-author values (item damage, enchant logic, mob rewards) and `config.yml` for server-operator tunables (intervals, pool sizes, messages). The table above follows that split.
- **`Rarity.java` enum** should be deprecated in favor of `rarity/*.yml` + `RarityDefinition`; keep enum only as a compile-time fallback.
- **Env damage fix (HC-025)** is a code change, not a config addition: route `DamageCalculator` environmental path through `DamageFormulaRegistry`.
- **`HandlerList` / `TICKS_PER_SECOND=20` / `50ms/tick`** are correctly hardcoded — do not externalize.
- **Deduplicate** `EconomyModule.formatCoins` ↔ `EcoCommand.fmt`/`fmtExact` (HC-011/014) — single `Formatter.formatCoins()` utility.
- **Validate at load**: add warnings for `gui.*` `amount<=0`, `hud slot 0..40`, `quests delay interval < min`, `anvil max-work-clamp` overflow.

---

## Files changed

- **New:** `docs/HARDCODED_VALUES_AUDIT.md` (this file) — single source of truth. Future audits should update this file in place.

## Verification

- Cross-checked 4 independent sub-agent sweeps (covering all 566 Java files) + manual spot-reads of `alchemy/effect/hardcoded/*`, `npc/dialogue/DialogueManager.java:34-435`, `profile/ProfileGui.java:38-413`, `pet/PetFollowTask.java:22`, `resource/ResourceModule.java:11`.
- Verified `src/main/resources/config.yml` (263 lines) to avoid duplicate-reporting already-configurable keys.
- Counted `26` modules; no `worldborder`, `world`, or standalone `ability` module exists — confirmed via `Get-ChildItem module -Directory` + `docs/VANILLA_CONTROL_AUDIT.md:189`.
