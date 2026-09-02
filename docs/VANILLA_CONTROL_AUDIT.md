# Vanilla Minecraft Control Audit — Coverage Issues

> **Purpose:** This document enumerates **every vanilla Minecraft mechanic** a plugin must be able to
> observe and control to have "complete" control of vanilla behavior, with emphasis on the
> **obscure, non-obvious** mechanics (breaking, placing, moving, using items, cooldowns, mobs, and
> everything else). For each mechanic it records the canonical Paper 1.21.x `Event`/API hook, the
> specific pitfalls agents get wrong, and **whether Valmora already covers it** at the time of
> writing.
>
> Coverage legend:
> - ✅ **COVERED** — Valmora already intercepts/controls it (code-verified).
> - 🟡 **PARTIAL** — a hook exists but has blind spots / not fully wired.
> - ❌ **GAP** — no meaningful hook/control exists; flagged as an issue to cover.
>
> Related: [AGENTS.md §11](../AGENTS.md) (1.21 hard topics), the per-module design docs under
> `docs/modules/design/`, and `docs/modules/design/INTEGRATION.md`.

---

## Table of Contents

1. [Breaking Blocks](#1-breaking-blocks)
2. [Placing Blocks](#2-placing-blocks)
3. [Block State Changes, Growth & Physics](#3-block-state-changes-growth--physics)
4. [Fluids, Fire & Explosions](#4-fluids-fire--explosions)
5. [Redstone, Containers & Block Entities](#5-redstone-containers--block-entities)
6. [Player Movement & Locomotion](#6-player-movement--locomotion)
7. [Falling, Damage Sources & Environmental Damage](#7-falling-damage-sources--environmental-damage)
8. [World State: Time, Weather, GameRules, Difficulty](#8-world-state-time-weather-gamerules-difficulty)
9. [Death, Respawn & Persistence](#9-death-respawn--persistence)
10. [Teleportation & Dimensions](#10-teleportation--dimensions)
11. [Hunger, Exhaustion, Experience & Player State](#11-hunger-exhaustion-experience--player-state)
12. [Using Items, Cooldowns & Interaction Timing](#12-using-items-cooldowns--interaction-timing)
13. [Item Durability, Components & Repairs](#13-item-durability-components--repairs)
14. [Combat, Damage Calculation & Knockback](#14-combat-damage-calculation--knockback)
15. [Armor, Equipment & Enchantments](#15-armor-equipment--enchantments)
16. [Projectiles, Bows & Utility Items](#16-projectiles-bows--utility-items)
17. [Mob & Entity Spawning](#17-mob--entity-spawning)
18. [Mob AI, Targeting & Pathfinding](#18-mob-ai-targeting--pathfinding)
19. [Mob Behaviors, Conversions & Bosses](#19-mob-behaviors-conversions--bosses)
20. [Villagers, Raids & Special Entities](#20-villagers-raids--special-entities)
21. [Inventory & GUI World Interactions](#21-inventory--gui-world-interactions)
22. [Chunks, World Generation & Persistence](#22-chunks-world-generation--persistence)
23. [Priority Gap Summary](#23-priority-gap-summary)

---

## 1. Breaking Blocks

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Break progress / cracking animation | `BlockDamageEvent` on start, not completion; speed from tool + `Attribute.BLOCK_BREAK_SPEED` + Haste/Mining Fatigue | `BlockDamageEvent`; `player.getAttribute(Attribute.BLOCK_BREAK_SPEED)` | Break speed is an **attribute** now, not a method | ❌ GAP |
| Break-progress cancellation mid-way | Releasing click retains partial progress ~5 ticks; **no abort event** | Track start in `BlockDamageEvent` + absence of `BlockBreakEvent`; `PlayerAnimationEvent` proxy | Paper has no `BlockDamageAbortEvent` | ❌ GAP |
| Instant break (creative / Efficiency V + Haste II) | Skips cracking phase entirely | `BlockBreakEvent` still fires; do **not** assume `BlockDamageEvent` always precedes it | Common plugin bug | ❌ GAP |
| Break completion / drop suppression | `BlockBreakEvent` (player-initiated only) | `BlockBreakEvent`; `setExpToDrop(0)`; cancel to keep block | Piston/explosion/fluid/fire breaks **do not** fire this event | ✅ resource covers drops; verify |
| Drop distribution | Items spawn at block center with spread + XP orbs | `BlockDropItemEvent` (1.19.4+); modify/suppress the `List<Item>` | Do NOT spawn custom drops in `BlockBreakEvent` and suppress vanilla — use `BlockDropItemEvent` | 🟡 resource |
| Experience drop from ores / sculk | Ores drop XP orbs | `BlockExpEvent` (`setExpToDrop(0)`); sculk XP via `SculkCatalystBloomEvent` | Sculk catalyst XP is a separate event | ❌ GAP |
| Silk Touch / Fortune interaction | Final drop count/block-item computed server-side | Modify list in `BlockDropItemEvent` | `Enchantment.SILK_TOUCH` / `FORTUNE` names | 🟡 `resource` — Silk Touch respected for zone-configured resource blocks (`ResourceManager`, `resource.silk-touch.enabled`); vanilla (non-tracked) blocks already get real vanilla Silk Touch/Fortune since their drops are never intercepted; generic `BlockDropItemEvent`/`BlockExpEvent` control for arbitrary blocks still not attempted |
| Tile-entity (container) loot on break | Chest/barrel/shulker/furnace/hopper/dispenser/lectern/decorated-pot contents drop separately | Included in `BlockDropItemEvent` list | Decorated pots (1.20+) have inventories; shulker contents preserved via NBT | ❌ GAP (machine interaction) |
| Tool durability deduction / Unbreaking / Mending | 1 durability per break (2 for axes on wood) | `PlayerItemDamageEvent` (Paper) — cancel/`setDamage` | `ItemMeta.getDamage()`/`setMaxDamage()` (not `setMaxDurability`) | ❌ GAP |
| Adventure-mode `CanDestroy` | Only blocks matching the tool's tag break | No event; check `Tag.isTagged(Material)` — tags now data-driven | — | ❌ GAP |
| Tool-specific break speeds / shears | Axe on wood, pick on stone, etc. | `BlockDamageEvent.getDestroySpeed()`; `Attribute.BLOCK_BREAK_SPEED` modifier (`NamespacedKey`) | `Tag.SHEARS_MINEABLE` | ❌ GAP |
| Support-chain break | Breaking support breaks torches/flowers/string/end-rods/hanging-signs | `BlockPhysicsEvent` (cancel to float) → multiple `BlockBreakEvent` | Hanging signs (1.20) | ❌ GAP |
| Gravity blocks (sand/gravel/dragonegg/concrete powder) | Fall as `FallingBlock` entity, place on land; concrete on water | `EntitySpawnEvent`, `BlockPlaceEvent`, `BlockBreakEvent`, `BlockFormEvent` (water→concrete) | — | ❌ GAP |
| Leaf decay | Random-tick when no log within 6 blocks | `LeavesDecayEvent` (cancel) | `BlockBreakEvent` does NOT fire for natural decay | ❌ GAP |
| Cactus/bamboo adjacent-growth break | Growth into occupied space breaks block | `BlockGrowEvent` + `BlockBreakEvent` chain | Bamboo "section" property (1.21) | ❌ GAP |

---

## 2. Placing Blocks

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Placement face / replaceable checks | Rails/ladders/torches face rules; replaceable blocks (snow/tall grass/water) overwritten | `BlockCanBuildEvent` **deprecated** — use `BlockPlaceEvent` + `Material.isReplaceable()` | `BlockCanBuildEvent` deprecated since 1.19 | 🟡 zone (placement protection) |
| Rotation on place | Logs/stairs/slabs/pistons/observers rotate by facing | `BlockPlaceEvent` gives final `BlockData` | `facing`/`horizontal_facing` block-state props | ❌ GAP |
| Placement in fluids / waterlogging | Displaces fluid; some blocks waterlog | `BlockPlaceEvent`; `Waterloggable` interface check | — | ❌ GAP |
| Invalid placement targets | Internal checks prevent placing against certain blocks | `BlockPlaceEvent` | Use `BlockData`, not hardcoded materials | ❌ GAP |
| Multi-block placement (doors/beds/chests/portals) | One action places 2+ blocks | **`BlockMultiPlaceEvent`** (commonly missed — extends `BlockPlaceEvent`) | — | ❌ GAP |
| Delayed/placed-tick effects | Pistons extend after delay, TNT ignites | `EntitySpawnEvent` (TNT), `BlockPistonExtendEvent` | — | ❌ GAP |

---

## 3. Block State Changes, Growth & Physics

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Crop growth (wheat/etc.) | Random-tick growth; bonemeal forces | `BlockGrowEvent` (cancel); bonemeal via `BlockFertilizeEvent` | Pitcher plant / torchflower (1.20) | 🟡 `ZoneListener.onBlockGrow` gates by `blockPlacing`; `BlockFertilizeEvent` (bonemeal-specific) not separately hooked — bonemeal still routes through `BlockGrowEvent` so the zone gate applies either way |
| Sapling → tree, huge mushroom | Structure grows when space | `StructureGrowEvent` (`TreeType.CHERRY` etc.) | — | 🟡 `ZoneListener.onStructureGrow` gates by `blockPlacing` (origin zone only, not every resulting block) |
| Grass/mycelium spread | Random-tick, light > 9 | `BlockSpreadEvent` | — | 🟡 `ZoneListener.onBlockSpread` gates by `blockPlacing` |
| Mushroom spread / huge growth | Spread in dark; grow with bonemeal | `BlockSpreadEvent`; `StructureGrowEvent` (`TreeType.BROWN/RED_MUSHROOM`) | — | 🟡 same `onBlockSpread`/`onStructureGrow` gates |
| Vine / cave-vine / twisting/weeping vine growth | Random-tick downward/sideways | `BlockSpreadEvent` | Cave vines carry glow berries | 🟡 same `onBlockSpread` gate |
| Sculk spreading | Catalyst blooms on mob death | `SculkCatalystBloomEvent` (Paper) | Partially data-driven in 1.21 | ❌ GAP |
| Coral death | Dies without water | `BlockFadeEvent` | — | ❌ GAP |
| Ice melting / snow decay | Melts with light > 11 | `BlockFadeEvent` | — | ❌ GAP |
| Water → ice / snow accumulation | Cold biomes at night | `BlockFormEvent`; `EntityBlockFormEvent` (Frost Walker) | — | ❌ GAP |
| Redstone ore glow | Glows on click/neighbor update, fades | `BlockFadeEvent` + `PlayerInteractEvent` | — | ❌ GAP |
| Snow layer accumulation/decay | Layers 1–8 | `BlockFormEvent` / `BlockFadeEvent` | `layers` property | ❌ GAP |
| Bed / respawn-anchor dimension explosion | Explode in wrong dimension | `BlockExplodeEvent`, `ExplosionPrimeEvent` | Not `BlockBreakEvent` | ❌ GAP |
| End portal frame activation | 12 frames + eyes | `PlayerInteractEvent`, `BlockPlaceEvent` | `eye_of_ender` property | ❌ GAP |
| Dragon egg teleport | Teleports on click; falls as gravity | `PlayerInteractEvent`, `BlockPhysicsEvent` | — | ❌ GAP |
| Oxidation (copper) / copper bulb | 4 weathering stages; bulb output | `BlockFadeEvent` (oxidation), `BlockRedstoneEvent` (bulb) | `weathering`/`oxidation` props | ❌ GAP |
| Amethyst / budding amethyst growth | Clusters grow from budding | `BlockGrowEvent` (`age` 0–3) | — | ❌ GAP |
| Pointed dripstone drip / stalactite fall | Drips into cauldrons; falls as damage | `BlockFromToEvent`, `BlockGrowEvent` | Tick-based | ❌ GAP |
| Piston extend/retract + slime/honey chain + destruction | Push/pull up to 12 blocks; destroy un-pushable | `BlockPistonExtendEvent` / `BlockPistonRetractEvent` (`getBlocks()`) | `MOVING_PISTON`/`PISTON_HEAD` technical blocks | 🟡 `ZoneListener.onPistonExtend`/`onPistonRetract` cancels the whole action if a moved block's zone disallows `blockBreaking` or its destination zone disallows `blockPlacing`; `resource.ResourceEnvironmentListener` separately protects tracked resource nodes regardless of zone |
| General physics check | Adjacent support checks on removal | `BlockPhysicsEvent` (`getCause()`) | Some physics moved client-side | ❌ GAP |
| Entity standing/collision effects | Pressure plates, sculk sensors, cobweb slow, honey/slime | `EntityMoveEvent` (Paper); no damage event for cactus/berry/powder | Block-contact damage has no Bukkit event | ❌ GAP |
| Mob griefing (creeper/enderman/silverfish/wither/ravager) | Mobs place/break blocks | `EntityExplodeEvent`, `EntityChangeBlockEvent`, `EntityBreakBlockEvent` (Paper), `GameRule.MOB_GRIEFING` | `GameRule.WITHER_BREAK_BLOCKS` | 🟡 `world_rules` config already passes `GameRule.MOB_GRIEFING`/`WITHER_BREAK_BLOCKS` through server/world-wide; `ZoneListener.onEntityChangeBlock` adds a per-zone `blockBreaking` override on top |

---

## 4. Fluids, Fire & Explosions

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Water/lava flow breaking blocks | Flow replaces torches/crops/etc. (no `BlockBreakEvent`) | `BlockFromToEvent` (cancel) | `level` 1–8, `waterlogged` prop | 🟡 `ZoneListener.onBlockFromTo` gates by `blockBreaking`, skipping the zone lookup entirely when the flow target is air/water/lava |
| Lava igniting / converting to obsidian/cobblestone | Contact mechanics | `BlockFormEvent`, `BlockIgniteEvent`, `BlockFromToEvent` | — | ❌ GAP |
| Infinite water source creation | Two adjacent sources create a source | `BlockFormEvent` | — | ❌ GAP |
| Bucket fill/empty (incl. fish/axolotl/powder snow) | Pick up and place fluids + entities | `PlayerBucketFillEvent` / `PlayerBucketEmptyEvent` (`getFluidBucket()`) | — | ❌ GAP |
| Frost Walker freezing | Freezes water under boots | `EntityBlockFormEvent` + `BlockFadeEvent` (melt) | — | ❌ GAP |
| Cauldron fill/empty (water/lava/powder snow) | Bucket + dripstone + rain fill | `PlayerBucketFillEvent`/`EmptyEvent`, `BlockFormEvent`, `BlockFromToEvent` | `level`/`fill_level` props | ❌ GAP |
| Fire spread / block burn | Fire spreads to flammable, burns blocks | `BlockIgniteEvent` (`IgniteCause`), `BlockBurnEvent`, `BlockSpreadEvent` | Soul fire doesn't spread to non-soulammable | 🟡 `ZoneListener.onBlockIgnite`/`onBlockBurn` gate ignition/consumption by `blockPlacing`/`blockBreaking`; `BlockSpreadEvent` for fire itself not separately distinguished from the vegetation-spread gate (same flag, same effect) |
| TNT / creeper / wither / end-crystal explosions | Radius + block destruction | `EntitySpawnEvent` (TNT), `ExplosionPrimeEvent`, `EntityExplodeEvent` (`blockList()`), `BlockExplodeEvent` (bed/anchor) | `GameRule.EXPLOSION_DROP_RULE` (1.21) | 🟡 combat maps damage; `ZoneListener.onEntityExplode`/`onBlockExplode` now filter `blockList()` by `blockBreaking` for every explosion source, not just bed/anchor (§9); drop-content control (`EXPLOSION_DROP_RULE`) still not attempted |
| Explosion drop override | `ExplosionDropRules` gamerule | Modify `EntityExplodeEvent`/`BlockExplodeEvent` block list | `GameRule.EXPLOSION_DROP_RULE` | ❌ GAP |
| Resonance / charged-creeper head drops | Creature killed by charged creeper drops disc | `EntityDeathEvent`, `EntityExplodeEvent` | — | ❌ GAP |
| Lightning strike + block conversion | Converts sand→glass, cobble→stone | `LightningStrikeEvent` (cancel) | — | ❌ GAP |
| Lightnight rod attraction | 128-block radius attraction | `LightningStrikeEvent` | — | ❌ GAP |

---

## 5. Redstone, Containers & Block Entities

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Redstone signal updates / comparator read modes | Output changes on state change | `BlockRedstoneEvent` (`getOldCurrent`/`getNewCurrent`) | `mode` (compare/subtract) prop | ❌ GAP |
| Redstone torch burnout | Toggle fast >8x in 60 ticks → burnout | No dedicated event (only `BlockRedstoneEvent`) | — | ❌ GAP |
| Observer detection / target block | Detect front change / redirect signal | `BlockRedstoneEvent` | `facing`/`power` props | ❌ GAP |
| Hopper / `InventoryMoveItemEvent` | Items move between inventories | `InventoryMoveItemEvent` (`getSource`/`getDestination`) | — | ❌ GAP (machine?) |
| Dispenser/dropper special dispensing | Fire charges, arrows, buckets have special logic | No single event; `InventoryMoveItemEvent`/`BlockDispenseEvent` | Complex; covers most actions only partially | ❌ GAP |
| Jukebox play/eject + redstone | Plays disc, redstone out | `JukeboxPlayEvent` / `JukeboxEjectEvent` | `has_record`/`is_playing` props | ❌ GAP |
| Note block play / instrument | Instrument from block below | `NotePlayEvent` | Instruments data-driven | ❌ GAP |
| Monster spawner | Timer spawn in 9×3×9, player within 16 | `SpawnerSpawnEvent` | — | 🟡 mob (zone spawners separate) |
| Beacon beam / GUI | Pyramid calc, GUI open | `PlayerInteractEvent`, `BlockPlaceEvent`/`BreakEvent`, `InventoryOpenEvent` | — | ❌ GAP |
| Enchanting table / lectern | Book faces player; lectern holds book | `PrepareItemEnchantEvent`, `EnchantItemEvent`, `PlayerInteractEvent` | — | 🟡 enchant (own engine) |
| Command/structure/jigsaw blocks | Creative GUIs, redstone execution | `PlayerInteractEvent`, `BlockRedstoneEvent` | Server-side | ❌ GAP |
| Campfire cooking | Cooks 3 items on tick; no furnace event | `InventoryDragEvent`/`ClickEvent`, `BlockBreakEvent` | `FurnaceSmeltEvent` does **not** fire | ❌ GAP |

---

## 6. Player Movement & Locomotion

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Walk/run/block-edge movement | `PlayerMoveEvent` on every packet | `PlayerMoveEvent` — early-out when block coords unchanged | Fires on yaw-only change; never touch entity state async | ✅ zone entry/exit only |
| Jump-vs-walk-off-edge detection | Differentiate jump from falling | `PlayerMoveEvent` Y-delta + ground check (no `isOnGround()` reliably) | Compute from `to.clone().subtract(0,.5,0).getBlock()` | ❌ GAP (no jump triggers) |
| Velocity / knockback vector | Server applies velocity | `PlayerVelocityEvent` (Paper), `EntityKnockbackEvent` (Paper) | Valmora abilities set velocity directly, bypassing events | 🟡 (abilities only) |
| Swimming / crawl (1.5-high) | Pose `SWIMMING`; no swim event | `player.isSwimming()`, `getPose()`, `EntityAirChangeEvent` | No `PlayerSwimEvent` in Bukkit | ❌ GAP |
| Fly / sprint double-jump / elytra | Flight toggle | `PlayerToggleFlightEvent`, `EntityToggleGlideEvent`, `setAllowFlight` | Re-assert `setAllowFlight`/`setFlying` periodically | ❌ GAP |
| Movement-speed attribute | `GENERIC_MOVEMENT_SPEED` | `Attribute.GENERIC_MOVEMENT_SPEED` + `NamespacedKey` modifier | Overwriting baseValue clobbers boot/enchant bonuses | 🟡 stat (walk speed only; fly/sprint/sneak/swim GAP) |
| Levitation / frozen / slowness effect gating | Effects alter movement | `EntityPotionEffectEvent` (cancellable); freeze via `setFreezeTicks` (no event) | — | 🟡 alchemy applies effects; no `EntityPotionEffectEvent` filter |
| Vehicle enter/exit & minecart/boat | Mount physics | `PlayerVehicleEnterEvent`, `PlayerVehicleExitEvent`, `VehicleEntityCollisionEvent` | — | ❌ GAP |

---

## 7. Falling, Damage Sources & Environmental Damage

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Fall damage / fall distance | Scales with `getFallDistance()` | `EntityDamageEvent` `FALL`; `NoFallDamageGuard` one-shot cancel | Full-cancel only, not proportional reduction | ✅ combat + one-shot guard |
| Feather Falling / boot reduction | Enchant reduces baked-in before event | Re-read `getFallDistance()`/`setDamage` | Original pre-enchant damage not recoverable | 🟡 (no reduction hook) |
| Void damage / void platform | Below `getMinHeight()` | `EntityDamageEvent` `VOID`; reposition in `PlayerMoveEvent` | Per-dimension min height (-64 overworld/0 nether) | 🟡 combat maps type only; ❌ no platform/reposition |
| Drowning / air bubbles / water breathing | Air depletes → `DROWNING` | `EntityAirChangeEvent` (`setAmount`), `EntityDamageEvent` `DROWNING` | Cancel alone won't restore; call `setAmount` | ✅ drowning+alchemy breathing; ❌ no air-bar control |
| Fire ticks / burning / extinguishing | Burning entity | `EntityIgniteEvent` (Paper, `Cause`), `EntityCombustEvent`, `setFireTicks(0)` | Prefer `EntityIgniteEvent` over `EntityCombustEvent` | ✅ fire immunity extinguishes; ❌ ignite interception |
| Lava movement / nether immunity | Lava damages/slows | `EntityDamageEvent` `LAVA` | — | ✅ mapped; ❌ no movement/resistance logic |
| Freeze / powder snow / Frost Walker | Freeze ticks; powder snow damage | `EntityFreezeEvent` (Paper); `EntityDamageEvent` `FREEZE` | — | ❌ GAP |
| Damage-type classification completeness | `LIGHTNING`/`FREEZE` must not map to MELEE | `CombatListener.mapCauseToType` | **Bug:** `LIGHTNING` and `FREEZE` currently fall through to `MELEE` | ❌ GAP (miscategorized) |

---

## 8. World State: Time, Weather, GameRules, Difficulty

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Time manipulation / freeze | `world.setTime()`; freeze sun | `setGameRule(DO_DAYLIGHT_CYCLE,false)` + tick `setTime` | Client moves the sun itself — must re-set server-side every tick | ❌ GAP (time module read-only by design) |
| Rain/snow/clear + thunder | World weather state | `WeatherChangeEvent`, `ThunderChangeEvent`, `world.setStorm`/`setThundering` | No weather-change event in some paths — poll | ❌ GAP |
| Night skip / sleeping percentage | Skip when % sleeping | `GameRule.PLAYERS_SLEEPING_PERCENTAGE`; poll dawn | — | ❌ GAP |
| Lightning entities | Strikes | `LightningStrikeEvent` | — | ❌ GAP |
| GameRules wholesale | Dozens affect RPG (keepInventory, doFireTick, naturalRegeneration, fall/fire/drowning damage, doMobSpawning, doMobLoot, etc.) | `World.setGameRule(GameRule.*)` | **None are set anywhere in Valmora** — biggest environmental cluster | ❌ GAP |
| Difficulty | Affects damage/hunger/special spawns | `World.setDifficulty(Difficulty.*)` | — | ❌ GAP |
| World border | Limit + damage | `WorldBorder` API; `EntityDamageEvent` `WORLD_BORDER` | — | 🟡 border damage mapped; ❌ no border control |
| Biome rules / temperature | Biome-specific effects | `Registry.BIOME`, `Block.getTemperature()` | `Biome` enum removed → `Registry.BIOME` | ❌ GAP |

---

## 9. Death, Respawn & Persistence

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Death message / custom death | Default localized message | `PlayerDeathEvent.deathMessage(Component)` (Adventure) | Needs `DamageSource` for localization keys, or block + inject MiniMessage | ✅ `death` module (`DeathMessageService`) — built from Valmora's own DamageType/attacker/weapon resolution, not `DamageSource` (deliberate — see docs/modules/design/death.md §3.2) |
| keepInventory / keepExperience | Vanilla drops armor/XP | `PlayerDeathEvent.setKeepInventory`/`setKeepLevel` | `setKeepInventory(true)` still populates `getDrops()` | ✅ `death` module (`DeathPolicyResolver`/`DeathListener`) — global config default + per-zone `ZoneFlags` override; the `getDrops()` pitfall is explicitly handled |
| Respawn location / bed vs anchor | `PlayerRespawnEvent.setRespawnLocation` | `setRespawnLocation`, `isBedSpawn`/`isAnchorSpawn`; `PlayerBedEnterEvent` | Death-screen duration client-side (Paper `PlayerDeathScreenEvent`) | 🟡 heal/respawn handled (stat); optional per-zone `death.zone-respawn-overrides` location override added; vanilla bed/anchor/world-spawn resolution otherwise left untouched (already correct); **death-screen duration has no hook in this Paper version (1.21.11)** — `PlayerDeathScreenEvent` doesn't exist here, confirmed via the shipped API jar |
| Totem of undying trigger | Protects from lethal | `EntityResurrectEvent` (Paper) | — | ✅ `TotemProtectionService` (`module/combat/`) — was silently non-functional (vanilla's own totem check never ran against this plugin's virtual-health damage pipeline); fixed by intercepting inside `DamageApplier` before the fatal `setHealth(0)`, firing a real `EntityResurrectEvent` for compatibility. Known limitation: the client's totem pop-up animation doesn't play (see design doc) |
| Respawn anchor explosion (wrong dim) | Explodes | `RespawnAnchorExplodeEvent` / `BlockExplodeEvent` | This Paper version has no dedicated `RespawnAnchorExplodeEvent`/`BedExplodeEvent` — both fire as `BlockExplodeEvent` (confirmed via the shipped API jar) | 🟡 `death` module (`RespawnAnchorListener`) — zone block-protection filtering only (reuses `ZoneFlags.blockBreaking`); player damage already routed correctly via existing `BLOCK_EXPLOSION` → `DamageType.EXPLOSION` mapping; general explosion-control (§4) not attempted |
| End void death / platform | Drops through End | reposition / platform | — | 🟡 death message added (`death.messages.VOID`); rescue-platform/reposition explicitly declined (user decision) — void kills exactly like vanilla |
| Bed enter/leave/wake + esc-kick | Sleep, respawn point, leave | `PlayerBedEnterEvent`, `PlayerBedLeaveEvent`, `PlayerWakeUpEvent`, `BedExplodeEvent` | — | 🟡 `death` module (`BedListener`) — `PlayerBedEnterEvent` gated by new `ZoneFlags.sleeping`; wrong-dimension explosion handled by `RespawnAnchorListener` (same `BlockExplodeEvent`, see above); `PlayerBedLeaveEvent`/`PlayerWakeUpEvent` deliberately left alone (vanilla already correct) |
| Phantom countdown (insomnia) | Spawns after 3 nights awake | `Statistic.TIME_SINCE_REST`, `PhantomPreSpawnEvent` (Paper) | Valmora controls time but not phantoms | ✅ `death` module (`PhantomInsomniaListener`) — global `death.phantoms-enabled` toggle + reused `ZoneFlags.naturalMobSpawning`; `Statistic.TIME_SINCE_REST` itself left vanilla |

---

## 10. Teleportation & Dimensions

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Async teleportation | Non-blocking chunk load | `player.teleportAsync(loc)` returns `CompletableFuture<Boolean>` | Never sync `.teleport()` to unloaded chunks | ✅ used across warp/npc/dialogue |
| Teleport cause gate | All causes | `PlayerTeleportEvent` (`TeleportCause`); zone gate blocks all | — | ✅ zone teleportation flag |
| Cross-world / dimension change | `PlayerChangedWorldEvent` | — | Not handled anywhere | ❌ GAP |
| Portal create / enter / exit | Nether/end portals | `PlayerPortalEvent`, `PortalCreateEvent`, `EntityPortalExitEvent`, `EntityPortalEnterEvent` | — | ❌ GAP |
| Ender-pearl / chorus / end-gateway | Cause-specific | `PlayerTeleportEvent` cause | Per-cause handling | 🟡 (all causes blocked by zone, no per-cause) |

---

## 11. Hunger, Exhaustion, Experience & Player State

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Food level / saturation / exhaustion | Health regen from satiation | `FoodLevelChangeEvent`, `EntityRegainHealthEvent` (`SATIATED`) | Zone-hunger cancel + SATIATED-regen cancel exist | 🟡 zone-hunger + regen-cancel; ❌ exhaustion/saturation management |
| Hunger (starving) damage | `STARVATION` | `EntityDamageEvent` `STARVATION` | — | ✅ mapped |
| XP curve / levels / level-up | Hardcoded curve | `PlayerExpChangeEvent`, `PlayerLevelChangeEvent` | No vanilla-level abstraction in Valmora (profile XP separate) | ❌ GAP |
| XP orbs / bottle o' enchanting / mending/offhand | Orb pickup routes to mending | `PlayerPickupExperienceEvent`, `PlayerExpChangeEvent` | Mending reroutes transparently | ❌ GAP |
| Enchanting-table/anvil XP cost | Uses player levels | `PrepareItemEnchantEvent`, `PrepareAnvilEvent`, `EnchantItemEvent` | Valmora enchant engine is its own system; vanilla tables still work on non-Valmora items | 🟡 enchant |
| Natural regeneration | Gamerule-driven | `GameRule.NATURAL_REGENERATION` | Not set | ✅ starvation canceled; ❌ regen control |
| Player abilities (flight/invulnerability/mayfly) | `setAllowFlight`/`setInvulnerable` | `PlayerToggleFlightEvent` | — | ❌ GAP |

---

## 12. Using Items, Cooldowns & Interaction Timing

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Interaction action/type dispatch + double-fire | One click can fire BLOCK+AIR + OFF_HAND | `PlayerInteractEvent`; check `getHand()==HAND` (null for PHYSICAL) | `getItem()!=null` alone is insufficient | ✅ AbilityTrigger main-hand |
| Offhand ability trigger | Use in offhand | `getHand()==OFF_HAND` | **AbilityListener explicitly skips OFF_HAND** | ❌ GAP |
| PHYSICAL (trampling) | Walk on farmland | `PlayerInteractEvent` `Action.PHYSICAL` (`getHand()==null`) | — | ✅ TrampleListener |
| Per-player vanilla item cooldown bar | `Player.setCooldown(Material, ticks)` | Keyed by Material, not PDC. Valmora `CooldownManager` is ability-ID keyed → **no vanilla cooldown bar** | — | 🟡 (ability cooldowns only) |
| Custom attack cooldown (1.9 swing charge) | Charge → damage scaling | `Attribute.ATTACK_SPEED`, `player.resetCooldown()`; no charge-progress event | `ATTACK_SPEED` name; `NamespacedKey` modifiers | ✅ `AttackCooldownService` (`module/combat/`) — reproduces vanilla's charge->multiplier curve (`0.2 + progress²×0.8`) against `Attribute.ATTACK_SPEED`, applied post-calculation in `CombatListener` for player melee hits (`combat.attack-cooldown.*`); `player.resetCooldown()`/the client HUD indicator itself untouched (cosmetic only, already correct) |
| Swing / arm-animation | `PlayerAnimationEvent` | Cancel to reuse click | — | ❌ GAP |
| `getItemInUse()` / hold-to-charge | Bow/fishing rod/trident charge | `Player#getItemInUse()`, `PlayerUseItemEvent` (start, cancellable) | Distinct from `PlayerItemConsumeEvent` | ❌ GAP |
| Eating/drinking animation & hold-time | ~1.6s eat; consume fires at completion | `PlayerItemConsumeEvent` (`getHand()` may be null); cancel `PlayerInteractEvent` to stop start | `FoodComponent` (item component), not `FoodMeta` | 🟡 alchemy consume only; ❌ custom food duration |
| Food nutrition/saturation properties | `FoodComponent` | `FoodComponent#setNutrition/setSaturation/setCanAlwaysEat` | No custom food definitions | ❌ GAP |
| Milk (clears effects) | Hard-coded clear, not potion-rule | `PlayerItemConsumeEvent` + `getActivePotionEffects()` | — | ❌ GAP |
| Honey-bottle poison-immunity window | Poison suppressed while drinking | Track via `PlayerItemConsumeEvent` + `PotionSplashEvent` | — | ❌ GAP |
| Mid-eat cancellation (damage/move) | Certain actions cancel eating | Track use-state + cancel on damage/swap | — | ❌ GAP |
| Tool block-actions via item (strip/shovel/hoe/flint&steel/bonemeal/bucket/shear) | `PlayerInteractEvent` RIGHT_CLICK_BLOCK branches-block | `BlockFertilizeEvent` (bonemeal), `PlayerShearEntityEvent` | Custom hoe/axe/scythe tools unsupported | ❌ GAP |
| `PlayerInteractEntityEvent` (feed/tame/saddle/nametag/dye/milk/leash) | Umbrella entity-interact | `PlayerInteractEntityEvent`, `PlayerShearEntityEvent`, `PlayerLeashEntityEvent` | **No `PlayerInteractEntityEvent` handler anywhere** | ❌ GAP (major) |
| F key swap / hotbar change | `PlayerSwapHandItemsEvent`, `PlayerItemHeldEvent` | Stat recalc only; no ability triggers | — | 🟡 stat recalc |
| Drop (Q) / pickup delay / despawn | 500ms pickup delay, 5min despawn | `PlayerDropItemEvent`, `PlayerAttemptPickupItemEvent` (Paper), `ItemDespawnEvent` | Only HUD item drop handled | 🟡 |

---

## 13. Item Durability, Components & Repairs

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Durability loss / unbreakable / Mending | Loss on use; `PlayerItemDamageEvent`/`PlayerItemMendEvent` | Cancel/`setDamage` | **Never hooked anywhere** | ❌ GAP |
| Attribute modifiers on held/equipped | Apply while equipped | `AttributeModifier` (`NamespacedKey`, `Operation`) | OFF `UUID`/string name constructor | ✅ verified — `StatModule.recalculateAttributes`'s only two `new AttributeModifier(...)` call sites both use the `NamespacedKey` constructor (`MINING_SPEED_MOD_KEY`/`ATTACK_SPEED_MOD_KEY`), removed and re-added on every recalc |
| Anvil repair / combine / XP cost / enchant preserve | Upgrade + cost cap (40 levels) | `AnvilRepairEvent`, `PrepareAnvilEvent` | Smithing is separate (`SmithingTransformRecipe` 3-slot) | 🟡 modifier anvil step; ❌ full cost/preserve |
| Item stacks / PDC / custom model data / components | PDC + (1.20.5+) components | `ItemStack#withType`, component access | Avoid `setType()`+full-meta rebuild | ✅ extensive PDC |
| Shield block cooldown / axe-disable / damage | 0.6s cooldown; axe disables 5s | `EntityDamageBlockedEvent` (**never hooked**), `setCooldown(Material.SHIELD)` | — | ❌ GAP |
| Brush / archaeology (suspicious sand/gravel) | Brush reveals loot | `PlayerInteractEvent` (RIGHT_CLICK_BLOCK with `Material.BRUSH`) | Very new (`dusted` 0–3) | ❌ GAP |
| Wind charge / mace smash (1.21 new) | Launch / fall-scaled smash | `PlayerInteractEvent`, `ProjectileLaunchEvent`, `EntityDamageByEntityEvent` | **New in 1.21** — old training lacks it | ❌ GAP |

---

## 14. Combat, Damage Calculation & Knockback

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Melee damage | `EntityDamageByEntityEvent`; attacker from `DamageSource` | HIGHEST, zeroed + pipeline (attacker resolution incl. projectile shooter) | — | ✅ MELEE |
| Projectile damage attribution | Shooter resolution | Same event; projectile→shooter | Egg/snowball classify oddly as MELEE | 🟡 |
| Armor reduction order | armor→protection→resistance potion→absorption→iframes | Valmora overrides: base→strength→crit→defense `100/(def+100)` | Vanilla stack not used; ❌ armor value/toughness **unconsumed** | 🟡 |
| Resistance potion / absorption hearts | Flat % / shield layer | Absorption via `setAbsorptionAmount`; no pipeline layer | Absorption hearts sit **outside** virtual HP — not spent first | 🟡 |
| Invulnerability i-frames / hurt resistance | `getNoDamageTicks` gate | `CombatListener` gate + `setNoDamageTicks(20)` | DoT/pipeline re-triggers gate | ✅ |
| Shield blocking | Block/reduce damage | `EntityDamageBlockedEvent` — **never hooked** | — | ❌ GAP |
| Knockback model | `Entity.knockback` accel; KB resistance | No attacker KB suppression/enchant-KB; mobs only resist | 1.21 uses acceleration not vector hacks | 🟡 `CombatKnockbackListener` (`EntityKnockbackEvent`, `Cause.ENTITY_ATTACK`) now lets an enchant `modify-attack`/`modify-defend` `knockback-multiplier` modifier suppress/scale a specific hit's knockback (`DamageModifierContext`/`EnchantCombatHook`/`KnockbackModifierTracker`); mob/player KB-resistance via vanilla attributes was already correct and untouched (`MobDefinition.knockback-resistance` → `KNOCKBACK_RESISTANCE`; any stat mapped to a vanilla attribute already flows through `StatModule`); no combat-pipeline `knockback_multiply` event yet — enchants only |
| Critical / sprint attack / sweep / backstab | Jump crit, sprint bonus, sweep width | Valmora random-% crit; ❌ sprint/sweep/backstab not modeled | 21 sweep tag not consumed | 🟡 crit; ❌ others |
| Attack cooldown integration | Charge scaling | `bonus_attack_speed` stat exists but **never consumed** | — | ✅ see §12 row above — `AttackCooldownService` now consumes it (via the `Attribute.ATTACK_SPEED` value it already fed) for the actual damage-scaling behavior |
| Modern `DamageSource`/`DamageType` builder + death keys | Localized death/kill tags | `DamageSource.builder(DamageType.*)...build()` | **Never used anywhere** | 🟡 deliberately still unused — custom death messages were the only concrete need for it and are now solved a different way (`death` module's `DeathMessageService`, built from Valmora's own DamageType/attacker resolution instead — see §9 and docs/modules/design/death.md §3.2). Vanilla `DamageSource`-based localization/attribution remains a real gap if some *other* feature needs it later |
| Vanilla-enchant interception | Protection/Unbreaking/Mending/FireAspect/KB/Looting/Sweeping | **None — vanilla enchants outside the pipeline** | — | ❌ GAP |

---

## 15. Armor, Equipment & Enchantments

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Armor equip/unequip / offhand swap / hotbar | Paper events | `PlayerArmorChangeEvent`, `PlayerSwapHandItemsEvent`, `PlayerItemHeldEvent` | Stat recalc + EQUIP/UNEQUIP triggers | ✅ |
| Armor set bonus | None vanilla | `SetBonusService` cumulative tiers | — | ✅ |
| Armor material tier / toughness / trims | Material-gated attributes + trims | **GAP** — only flat `defense`/`true_defense`; no toughness/material gating/trims | Trims are item components | ❌ GAP |
| Helmet head-block (mob) | Skull-block logic | No `EntityTargetEvent` alteration | — | ❌ GAP |
| ArmorStand / ItemFrame hidden equipment | Entity metadata/inventory | — | — | ❌ GAP |
| Vanilla enchants in pipeline | Prot/Unbreaking/Mending/etc. | Not replicated nor intercepted | — | ❌ GAP |
| Reach / entity-interaction range | Attack range | `ENTITY_INTERACTION_RANGE` attribute not hooked (only `getTargetEntity` targeting) | — | ❌ GAP |
| XP for enchanting/anvil levels | Player levels | Covered only via own enchant GUI `EtableCostCalculator` | — | 🟡 |
| Merchant/villager trades | Trade UI + inventory | `MerchantTradeEvent`, `TradeList` on `Villager` | **No trade interception** | ❌ GAP |
| Brewing stand fuel/ingredient | Brew process | `BrewEvent` (fire + skill XP only) | No ingredient/fuel control | 🟡 |
| Furnace smelt + fuel | Smelt economy | No `FurnaceSmeltEvent`/fuel hooks | recipe `machine` handlers only | ❌ GAP |
| Anvil repair cost (vanilla) | Level cost | Only modifier combine step | vanilla repairs unmapped | 🟡 |

---

## 16. Projectiles, Bows & Utility Items

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Bow draw / arrow types / tipped / spectral | Charge-force damage, potion arrows | `EntityShootBowEvent` (`getForce`, `getConsumable`); `Arrow` potion via `setBasePotionType` | `PotionData` removed (§11.18) | 🟡 ON_SHOOT trigger only |
| Crossbow load/charge/piercing/multishot | Charge+loaded state; 3 arrows on multishot | `EntityShootBowEvent` + `PlayerInteractEvent`; loaded state in item NBT | — | ❌ GAP |
| Trident throw / Riptide / model predicate | Thrown trident; proc with water+rain | `ProjectileLaunchEvent`, `PlayerRiptideEvent` (Paper) | Riptide consumes durability | ❌ GAP |
| Potion splash / lingering / drink / dragon's breath | 3 paths + AreaEffectCloud | `PotionSplashEvent`, `LingeringPotionSplashEvent`, `PlayerItemConsumeEvent` | Use `setBasePotionType` | ✅ alchemy covers |
| Elytra start/cancel + firework boost | Double-jump glide; rocket boost | `PlayerToggleGlideEvent` (Paper), `PlayerInteractEvent` | Firework boost doesn't consume normally | ❌ GAP |
| Fishing cast/bite/catch + hook entity | `PlayerFishEvent` states | `CAUGHT_FISH`/`BITE` loot; ❌ `CAUGHT_ENTITY` (entity hooking) | — | 🟡 fishing loot only |
| Projectile hit block/entity | Arrow stick, fireball ignite, trident lightning | `ProjectileHitEvent` (`getHitBlock`/`getHitEntity`) | Channeling summon via `LightningStrikeEvent` | ❌ GAP (block interaction) |
| Ghast fireball deflection | Hit to reflect | `ProjectileHitEvent`, `EntityDamageByEntityEvent` | — | ❌ GAP |

---

## 17. Mob & Entity Spawning

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Natural spawn rules + caps | Biome category caps, light/block checks | `CreatureSpawnEvent` (`SpawnReason.NATURAL`), `MobSpawnEvent` (Paper) | Light rules changed 1.18+ (surface ≤0, caves ≤15) | 🟡 zone can disable; ❌ custom spawn logic |
| Spawn placement constraints (per-type) | 2×1×2 spider, sky access phantom, etc. | `SpawnPlacements` NMS-only; pre-validate in `CreatureSpawnEvent` | — | ❌ GAP |
| Despawn distance / persistence | 128-block despawn; `removeWhenFarAway` | `EntityRemoveEvent` (Paper, `DESPAWN`), `Mob.setPersistent` | 128/32 hardcoded in NMS | 🟡 `MobDefinition.persistent`; ❌ custom distance/reaction |
| Vanilla MobSpawner block | Timer + range + conditions | `SpawnerSpawnEvent` | — | ✅ verified — `SpawnerSpawnEvent extends CreatureSpawnEvent` in this Paper version, so `VanillaSpawnUpgradeListener`'s existing `CreatureSpawnEvent` handler already upgrades spawner-produced entities the same as natural/egg spawns; zone spawners remain a separate system |
| Structure spawning / siege / reinforcements | Outposts, fortresses, zombie siege/reinforcements | `CreatureSpawnEvent` (`STRUCTURE`/`REINFORCEMENTS`) | — | ❌ GAP |
| Spawn egg / command / plugin spawning of Valmora mobs | Applies stats/PDC | **No `CreatureSpawnEvent` listener to apply mob defs** — eggs produce vanilla mobs | — | 🟡 `/mob spawn` only; ❌ eggs |
| Riding / passenger system | Skeletons ride spiders, piglins ride striders | `EntityAddPassengerEvent`/`EntityRemovePassengerEvent` (Paper) | — | ❌ GAP |

---

## 18. Mob AI, Targeting & Pathfinding

> **A single `EntityTargetEvent` listener is the highest-leverage hook** for RPG mob AI — it unlocks
> aggro range, targeting tables/factions, and most "custom behavior" without NMS.

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Aggro range / target selection | Per-type ranges, LoS, attack-triggered | `EntityTargetEvent` / `EntityTargetLivingEntityEvent` (`TargetReason`) | **No `EntityTargetEvent` listener anywhere** | ❌ GAP (critical) |
| Custom pathfinder goals | 40+ `PathfinderGoal` types | `Mob.getPathfinder()` (Paper), `io.papermc.paper.entity.ai.goal.Goal` | mob.md: "no custom AI" | 🟡 zone mob-home uses `getPathfinder()` only |
| Leash / lead / fence-post anchor | Follow + break distance | `PlayerLeashEntityEvent`, `UnleashEntityEvent` | — | ❌ GAP |
| Call for help / reinforcements | Adjacent mobs aggro | `CreatureSpawnEvent` (`REINFORCEMENTS`) | — | ❌ GAP |
| Flee behaviors | Piglins flee, villagers flee zombies | Internal pathfinding; `EntityTargetEvent` `PANIC` | — | ❌ GAP |
| Door interaction (villagers/iron golems) + zombie door breaking | Schedule / break on hard | `BlockBreakEvent` (door) | — | ❌ GAP |
| Movement/swim/climb/fly speed | Per-type | `Attribute.MOVEMENT_SPEED`/`FLYING_SPEED` | Valmora sets movement speed only | 🟡 (speed only) |
| Wandering / idle / ravine avoidance | Stroll/fly/swim goals | Paper goal API / `getPathfinder()` | — | 🟡 zone wander; ❌ per-definition |
| Tamed wolf pack / tamed follow-attack | Owner targeting | `EntityTargetEvent` `OWNER_ATTACKED_TARGET` / `COLLEAGUE_ATTACKED` | — | ❌ GAP |
| Team/faction/friendly-fire | Scoreboard team collision/name rules | `Scoreboard` `Team.Option.*` | **No team system** — all entities default team | ❌ GAP |

---

## 19. Mob Behaviors, Conversions & Bosses

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Embodied armor equipment / loot | Player-killed vs env-killed | `EntityDeathEvent` (`getDrops`, `getDroppedExp`, `getLootingLevel`) | — | ✅ mob loot (🟡 double XP: vanilla orbs + Valmora XP) |
| Mob conversions (drown, burn, cure) | Husk↔zombie↔drowned, zombie-villager cure | `EntityTransformEvent`, `EntityConvertEvent` (Paper), `CureZombieVillagerEvent` | **PDC lost on conversion → custom mobs become vanilla** | ❌ GAP (critical) |
| Baby growth / breeding / taming | Love mode, growth, tame chance | `EntityBreedEvent`, `PlayerInteractEntityEvent`, `EntityTameEvent`, `Ageable` | Skill named "taming" has no mechanic behind it | 🟡 baby flag; ❌ grow/breed/tame |
| Breeding-food / pregnancy / follow-parent | Per-species food, turtle eggs, cat kittens | `EntityBreedEvent`, `EntitySpawnEvent`, `BlockGrowEvent` (eggs) | — | ❌ GAP |
| Milking / shearing / feeding | Entity interact | `PlayerShearEntityEvent`, `PlayerInteractEntityEvent` | `PlayerShearEntityEvent` includes tool+drops (Paper) | ❌ GAP |
| Enderman teleport / block pickup-place | Teleport on damage/look; place/pick blocks | `EntityTeleportEvent`, `EntityChangeBlockEvent` | — | ❌ GAP |
| Wither ritual / skull attacks / block break / boss bar | Soul-sand T-shape, blue skulls | `WitherPrepareSpawnEvent` (Paper), `EntityExplodeEvent`, `BossBar` | Access `BossController` (custom bars only) | 🟡 boss bar custom; ❌ wither specifics |
| Dragon fight / breath / crystals | State machine, crystal healing | `DragonFireballEvent` (Paper), `EntityExplodeEvent`, `getPhase`/`setPhase` | — | ❌ GAP |
| Villager professions / workstations / gossip / trades | Professions, gossip graph, trade lists | `VillagerAcquireTradeEvent`, `VillagerReputationEvent` (Paper) | `Profession` vanilla names | ❌ GAP (NPC is `setAI(false)`) |
| Raids (bad omen → waves) | Wave illagers + boss bar + Hero | `RaidTriggerEvent`, `RaidSpawnWaveEvent`, `RaidFinishEvent` (Paper) | — | ❌ GAP |
| Iron golem village spawn / flower offering / snow-golem snow trail | Panic spawn, poppy, trail | `CreatureSpawnEvent`, `EntityInteractEvent`, `BlockPlaceEvent` | — | ❌ GAP |
| Creeper charging / chain / head drops | Lightning-charge, larger blast | `CreatureSpawnEvent` (`LIGHTNING`), `EntityExplodeEvent`, `EntityDeathEvent` | — | 🟡 explosion damage; ❌ charging |
| Phantom / silverfish / evoker / shulker teleport / slime split | Niche spawn + behaviors | `PhantomPreSpawnEvent`, `CreatureSpawnEvent` (`SILVERFISH_BLOCK`/`SLIME_SPLIT`), `EntityTransformEvent` | — | ❌ GAP |
| Axolotl play-dead / warden sonic boom | Regen self / armor-ignoring beam | `EntityDamageByEntityEvent`, `DamageSource` (`SONIC_BOOM`) | sonic boom mapped; ward no behavior | 🟡 type mapped; ❌ behavior |
| Holograms / floating text / displays | Display entities (1.19.4+) | `TextDisplay`/`ItemDisplay` (`Display.Billboard`) — **never ArmorStands** | §11.17 | 🟡 damage indicators; ❌ general display/hologram API |

---

## 20. Villagers, Raids & Special Entities

Many special entities were covered in §17–19. Cross-cutting items worth repeating:

- **Player/entity interaction ranged behaviors** (passive-mob damage rules, baby-damage reduction, pet-from-fall immunity) are **not** intercepted — `NpcListener` blocks NPC damage only. ❌ GAP
- **Entity cramming** (`DamageCause.CRAMMING`) not intercepted. ❌ GAP
- **Boat/minecart physics & interaction** — no vehicle hooks. ❌ GAP
- **Allay item delivery** (`EntityPickupItemEvent`) — not intercepted. ❌ GAP

---

## 21. Inventory & GUI World Interactions

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Slot click / shift-click / drag / swap | GUI interactions | `InventoryClickEvent`, `InventoryDragEvent`, `PlayerSwapHandItemsEvent` | — | ✅ GUI engine + recalc |
| Full inventory overflow (no space → drop) | Drop extras | `InventoryPickupItemEvent`-blocking / overflow queue | Only loot-specific private drops; ❌ general overflow | 🟡 |
| Crafting matrix / recipe shift | Shift-craft | `CraftItemEvent` (+ recipe module) | — | ✅ recipe module |
| Inventory view titles | Adventure `Component` | `event.getView().title()` (not `getTitle()` String) | §11.21 | ✅ where used |

---

## 22. Chunks, World Generation & Persistence

| Mechanic | Vanilla behavior | Hook(s) | Paper 1.21 pitfalls | Coverage |
|---|---|---|---|---|
| Chunk load/generate/unload | Loading events | `ChunkLoadEvent`, `ChunkUnloadEvent`, `ChunkPopulateEvent` | Paper async chunk load — events may fire async | 🟡 zone/resource/mob/npc use ChunkLoad; ❌ gen |
| Spawn chunks / force-load tickets | Keep loaded | `Chunk#addPluginChunkTicket`, `setForceLoaded` | `GameRule.SPAWN_CHUNK_RADIUS` (1.21) | ❌ GAP |
| Random-tick processing | Random block ticks | `GameRule.RANDOM_TICK_SPEED`; events per state change | Default 3 | ❌ GAP |
| Entity persistence across restart | Saved to region file | — | Boss instance state not persisted (mob.md TODO) | ❌ GAP |
| Chunk-unload entity despawn | Non-persistent removed | `EntityRemoveEvent` (`UNLOADED_CHUNK`) | — | 🟡 persistent flag; ❌ reaction |

---

## 23. Priority Gap Summary

### Critical (foundational to "full vanilla control")
1. **`EntityTargetEvent`** listener — unlocks aggro range, targeting tables/factions, most custom mob AI. No module has it.
2. **`PlayerInteractEntityEvent`** — no custom entity-interact-through-item mechanics at all (feeding/taming/saddling/custom shears).
3. **Mob conversion interception** — `EntityTransformEvent`/`EntityConvertEvent` must carry over mob PDC, or custom mobs silently revert to vanilla on drown/burn/cure.
4. ~~**Modern `DamageSource`/`DamageType` + custom death messages**~~ — **done**, via a different
   route than originally scoped: the `death` module's `DeathMessageService` builds real custom death
   messages from Valmora's own DamageType/attacker/weapon resolution rather than vanilla
   `DamageSource` (a deliberate choice — see §9 and docs/modules/design/death.md §3.2). The
   `DamageSource.builder(...)` API itself remains genuinely unused if some future feature needs
   vanilla-localized attribution specifically.
5. **Vanilla-enchant & durability interception** — no `PlayerItemDamageEvent`/`PlayerItemMendEvent`; Protection/Unbreaking/Mending/Fire Aspect/KB/Looting/Sweeping all outside the pipeline.
6. **Shield system** — `EntityDamageBlockedEvent` never hooked; no block chance, cooldown, or axe-disable.

### High (essential for an RPG)
7. ~~**Natural spawning system**~~ — **done**, `VanillaSpawnUpgradeListener` (§17); also covers vanilla monster spawners for free since `SpawnerSpawnEvent extends CreatureSpawnEvent`.
8. ~~**Damage-type miscategorization**~~ — **done**, `LIGHTNING`/`FREEZE` explicitly mapped.
9. ~~**Attack-cooldown / swing charge integration**~~ — **done**, `AttackCooldownService` (§12/§14); sprint-attack/sweep/backstab modeling remains a separate, still-open item.
10. **GameRules wholesale** — none set (keepInventory, doFireTick, doDaylightCycle, doWeatherCycle, naturalRegeneration, fall/fire/drowning damage, playersSleepingPercentage, doImmediateRespawn).
11. **Weather & time manipulation lock** — time module is read-only by design; no `WeatherChangeEvent`/`ThunderChangeEvent`, no `setTime` freeze.
12. ~~**Block state change family**~~ — **partially done** (see `VANILLA_CONTROL_AUDIT_PROGRESS.md`
    third pass): `BlockGrowEvent`/`BlockSpreadEvent`/`StructureGrowEvent`/`LeavesDecayEvent` now
    zone-gated. Still open: `BlockFadeEvent`/`BlockFormEvent` (deliberately skipped, cosmetic-only),
    `BlockPhysicsEvent`, `BlockMultiPlaceEvent`, `BlockDropItemEvent`/`BlockExpEvent` (general, beyond
    the resource-module Silk Touch fix).
13. ~~**Explosions & fire control**~~ — **partially done**: `EntityExplodeEvent`/`BlockExplodeEvent`
    block destruction and `BlockIgniteEvent`/`BlockBurnEvent` fire spread now zone-gated. Explosion
    *drop*-content control (`GameRule.EXPLOSION_DROP_RULE`) still not attempted.
14. ~~**Sleeping/beds/phantoms**~~ — **mostly done**: `death` module's `BedListener` (zone-gated
    `PlayerBedEnterEvent`) and `PhantomInsomniaListener` (global + zone-gated `PhantomPreSpawnEvent`).
    Night-skip detection and `Statistic.TIME_SINCE_REST` control remain untouched (vanilla already
    correct; `playersSleepingPercentage` is settable via the already-shipped `world_rules` GameRule
    pass-through).
15. **Custom pathfinder goals** — extend Paper `Pathfinder`/goal API beyond zone mob-home movement.
16. ~~**Held/equipped `AttributeModifier`**~~ — **verified**, `StatModule` already uses the `NamespacedKey` constructor exclusively.

### Medium (RPG content depth)
17. **Breeding / taming / baby growth** — pet/companion systems; "taming" skill is currently XP-only.
18. **Villager trades / professions / raids** — `MerchantTradeEvent`, `VillagerReputationEvent`, `RaidSpawnWaveEvent`.
19. **Item hold-to-charge / `getItemInUse()` / offhand triggers** — hold-charge abilities, cancel-ongoing-use.
20. **Void platform / anti-void** control — still open, deliberately declined for this pass (see §9).
    Respawn-location gained an optional per-zone override (`death.zone-respawn-overrides`); death-screen
    duration has no hook to control it on in this Paper version (confirmed, not just undone).
21. **Player XP curve / level abstractions** — `PlayerExpChangeEvent`/`PlayerLevelChangeEvent`; mending/bottle/o' enchanting.
22. ~~**Knockback model**~~ — **partially done**: `CombatKnockbackListener` now lets enchant
    `knockback-multiplier` modifiers suppress/scale a hit's knockback; mob/player KB-resistance was
    already correct via vanilla attributes. A combat-pipeline knockback event (mirroring
    `multiply_damage`) is not yet wired.
23. **Armor toughness / material gating / trims** — only flat `defense`/`true_defense`.
24. **Cross-world / portal events** — `PlayerPortalEvent`, `PortalCreateEvent`, `PlayerChangedWorldEvent`.

---

## Verification & authorship notes

- Coverage judgments verified against current code (not stale design docs). Where a design doc
  contradicts the live code, the **code wins** (e.g. `CombatListener.mapCauseToType` now maps
  SUICIDE/CONTACT/STARVATION/DRAGON_BREATH/SONIC_BOOM/OUTSIDE_BORDER; `DamageType` is registry-backed
  with `isIgnoresDefense`).
- Cross-cutting correctness requirements for any new listener:
  1. Register in `onEnable()`, **always unregister** in `onDisable()` (`HandlerList.unregisterAll`) — several existing listeners (`MobDeathListener`, `LootListener`, `AbilityTriggerListener`) are registered but not unregistered per the docs; fix those too.
  2. Use Paper `EntityScheduler` (§11.13) for any per-entity delayed effect.
  3. Resolve projectile→shooter rather than casting `getDamager()` to `LivingEntity`.
  4. All text through MiniMessage/Adventure only (§7.5/§11.3).
  5. Never store `ExecutionContext`; never touch Bukkit from async threads (§7.4).

_Last updated: 2026-09-03. Generated from a multi-agent audit of vanilla Paper 1.21.11 mechanics vs. the current Valmora module set; coverage cells updated in place as gaps are closed — see `docs/VANILLA_CONTROL_AUDIT_PROGRESS.md` for what shipped on which branch._
