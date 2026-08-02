# Item Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.item` | **Module IDs:** `items` ("Item Engine") + `abilities` ("Ability System") | **Name:** "Item Engine" / "Ability System"
> **Dependencies:** `scriptModule` (mechanic expression conditions via `getExpressionEvaluator()`, `getVariableResolver()`), `statModule` (`StatRegistry`, `StatManager.saveStats`), `combatModule` (ON_HIT dispatch, `DamageCalculator`/`DamageType`), `profileModule` (`ValmoraProfile`, `CooldownManager`, `PlayerState` mana/heal), `economy` (GiveCoins/TakeCoins mechanics via `EconomyService`)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Code Structure](#2-code-structure)
3. [Architecture & Key Classes](#3-architecture--key-classes)
4. [Configuration (YAML)](#4-configuration-yaml)
5. [Data Model / Persistence](#5-data-model--persistence)
6. [API Exposed](#6-api-exposed)
7. [Dependencies & Consumers](#7-dependencies--consumers)
8. [Unfinished Things / TODOs](#8-unfinished-things--todos)
9. [Possible Improvements / Changes](#9-possible-improvements--changes)

---

## 1. Overview

The **Item Module** is the engine that defines, creates, and applies **custom RPG items**. Every custom item in Valmora — weapons, armor, wands, fishing rods, reforge stones, pet items, backpacks — is a YAML definition loaded into a case-insensitive registry and materialized into a Paper `ItemStack` carrying the item's identity, rarity, type, stats, and abilities as **PDC (PersistentDataContainer) data** (`AGENTS.md` §11.5).

The module is split into **two registered modules** living in the same package:

1. **`items`** (`ItemManager`, "Item Engine") — definitions, registry, factory, translation of vanilla items, set bonuses, and loot handling.
2. **`abilities`** (`AbilityManager`, "Ability System") — the ability/mechanic execution system that the item definitions reference (`abilities:` YAML section). It is registered before `items` (`Valmora.java:196-197`) and exposes a shared `mechanicRegistry` that other modules extend (e.g. `BackpackModule.java:35` registers `BackpackMechanic`).

Every item definition carries zero or more **abilities**, each gated by a **trigger** (`AbilityTrigger`), optional **conditions** (script expressions), a **cooldown**, a **mana cost**, and a list of **mechanics** (small parameterized effects like damage, heal, teleport, launch projectile, AoE mine). Mechanics are executed by the shared dispatcher `AbilityExecutor.fire(...)` (`AbilityExecutor.java:32-82`), which is called from every trigger source: click listeners, on-kill/on-sneak/on-shoot listeners, the combat module's ON_HIT hook, and the passive scan inside `StatManager.recalculateStats`.

The module also owns the **vanilla-item translation layer** (`ItemTranslator`): any non-custom item (e.g. `/item give diamond_sword`) is converted into a Valmora item with id `vanilla_<material>` plus mapped stats, so the stat/lore pipeline treats all items uniformly. `ItemManager.createItemStack(id)` (`ItemManager.java:69-84`) is the single entry point that prefers a registered custom item and falls back to vanilla translation.

---

## 2. Code Structure

The module lives in `src/main/java/org/nakii/valmora/module/item/` and is the largest in the codebase. It does not follow the single flat `XModule`/`XListener`/`XRegistry`/`XLoader` convention (`AGENTS.md` §3) — it has **two registered modules**, two sub-packages (`impl/` for the 14 concrete mechanics, `set/` for set-bonus support), and a thick supporting layer of data classes and services.

```
src/main/java/org/nakii/valmora/module/item/
├── ItemManager.java               # Module "items" — wiring, lifecycle, createItemStack()
├── ItemDefinition.java            # Immutable item definition + Builder
├── ItemDefinitionParser.java      # YAML section → ItemDefinition (with validation)
├── ItemRegistry.java              # Case-insensitive Registry<ItemDefinition>
├── ItemFactory.java               # Definition → ItemStack, PDC writes, lore regeneration
├── ItemLoader.java                # YamlLoader over items/*.yml
├── ItemTranslator.java            # Vanilla Material → Valmora item (id vanilla_<material>)
├── ItemType.java                  # Item type enum (SWORD, HELMET, ... NONE, ALL)
├── Rarity.java                    # Rarity enum (COMMON..DIVINE) with MiniMessage colors
├── ItemCommand.java               # /item give|info|list|reload|enchant|enchantbook
│
├── AbilityManager.java            # Module "abilities" — lifecycle, mechanic registration
├── AbilityDefinition.java         # Immutable ability definition + Builder
├── AbilityTrigger.java            # Trigger enum (RIGHT_CLICK, LEFT_CLICK, PASSIVE, ...)
├── AbilityMechanic.java           # Mechanic interface: getId() + execute(ExecutionContext)
├── AbilityExecutor.java           # Shared dispatcher: conditions→cooldown→mana→mechanics
├── AbilityListener.java           # RIGHT_CLICK/LEFT_CLICK from PlayerInteractEvent
├── AbilityTriggerListener.java    # ON_KILL/SNEAK/ON_SHOOT + projectile on-hit callbacks
├── ConfiguredMechanic.java        # Binds a mechanic to parsed params; execute/executeAt
├── MechanicParser.java            # List<Map> → List<ConfiguredMechanic>, throws UnknownMechanicException
├── MechanicRegistry.java          # Uppercase-keyed mechanic registry
├── CooldownManager.java           # Per-ability cooldown timing
├── CombatTracker.java             # Tracks last damage dealt → $player.last_damage$
├── ProjectileAbilityService.java  # Projectile→callback map (launch mechanics + on-hit)
├── TargetResolver.java            # @player/@target/@enemies_in_radius/@allies_in_radius/@cone
├── TemporaryStatService.java      # Timed temporary stat boosts (survive recalculation)
├── LootListener.java              # Converts block/mob/fish drops to custom items
│
├── impl/                          # 14 concrete mechanics
│   ├── DamageMechanic.java        # damage/amount + damage-type → DamageCalculator
│   ├── HealMechanic.java          # heal param → PlayerState.heal
│   ├── ApplyEffectMechanic.java   # PotionEffect via Registry.POTION_EFFECT_TYPE
│   ├── ScriptMechanic.java        # fires script events through script module
│   ├── ModifyStatMechanic.java    # permanent or timed stat modifier
│   ├── TeleportMechanic.java      # distance teleport with ray-trace
│   ├── PushEntitiesMechanic.java  # impulse away from caster
│   ├── PullEntitiesMechanic.java  # impulse toward caster
│   ├── GiveCoinsMechanic.java     # coins via economy
│   ├── TakeCoinsMechanic.java     # coins via economy
│   ├── IgniteMechanic.java        # setFireTicks
│   ├── LaunchPlayerMechanic.java  # launch caster/target
│   ├── LaunchProjectileMechanic.java  # configurable projectile + on-hit callback
│   └── AoeMineMechanic.java       # radius block mining
│
└── set/
    ├── SetBonusDefinition.java    # Immutable set bonus definition (record + Tier record)
    ├── SetBonusParser.java        # YAML → SetBonusDefinition (validates stats)
    ├── SetBonusRegistry.java      # Registry over set_bonuses/*.yml
    └── SetBonusService.java       # applyTo: counts worn pieces, applies cumulative tiers
```

Resources:

```
src/main/resources/items/*.yml          # 13 files, 734 top-level item definitions
src/main/resources/set_bonuses/*.yml    # 3 files: armor_sets.yml, shardworks_sets.yml, sets.yml
```

---

## 3. Architecture & Key Classes

### 3.1 Module lifecycle and wiring

Both modules implement `ReloadableModule` (`docs/MODULE_DEVELOPMENT.md` §2). They are instantiated in `Valmora.java:155-156` and registered at `Valmora.java:196-197` (ability before item, after `uiManager`, before `mobManager`). `onEnable()`/`onDisable()` are idempotent and hot-reload safe:

- `ItemManager.onEnable()` (`ItemManager.java:27-34`): `itemLoader.loadItems()`, `setBonusRegistry.load()`, registers `LootListener`.
- `ItemManager.onDisable()` (`ItemManager.java:37-41`): clears `itemRegistry` and `setBonusRegistry`.
- `AbilityManager.onEnable()` (`AbilityManager.java:20-27`): `registerMechanics()` (registers all 14 impl mechanics, `AbilityManager.java:50-65`), registers `AbilityListener` + `AbilityTriggerListener`.
- `AbilityManager.onDisable()` (`AbilityManager.java:30-39`): clears the mechanic registry and unregisters both listeners via `HandlerList.unregisterAll` (mandatory per `AGENTS.md` §6.2).

The `/item` command is registered in `Valmora.onEnable()` after all modules — `Valmora.java:237` — never inside a module (`AGENTS.md` §6.3). It requires `valmora.admin` (`plugin.yml:16`).

### 3.2 ItemDefinition / Builder

`ItemDefinition` (`ItemDefinition.java:10-21`) is immutable and built through a nested `Builder` (`ItemDefinition.java:51-84`). Fields: `id`, `name`, `material`, `rarity` (default COMMON), `itemType` (default NONE), `lore`, `loreTemplate` (supports `$item.stat.<id>$` tokens resolved at create time), `customModelData`, `reforgePool`, `set` (armor-set link), `stats` (LinkedHashMap, stat ids lowercased by the builder), and `abilities` (LinkedHashMap<String, AbilityDefinition>).

### 3.3 ItemDefinitionParser

`ItemDefinitionParser.parse(...)` (`ItemDefinitionParser.java:15-141`) turns one YAML section into an `ItemDefinition`, returning `LoadResult<ItemDefinition, String>` failures for invalid config. Validation rules:

- `material` is **required** and must resolve via `Material.matchMaterial` (`ItemDefinitionParser.java:24-32`).
- `rarity` must be a valid enum value (`ItemDefinitionParser.java:35-42`); error message lists valid options.
- `item-type` must be a valid `ItemType` (`ItemDefinitionParser.java:45-52`).
- `stats` values must be numeric **and** known to `StatRegistry` (`ItemDefinitionParser.java:80-92`); unknown stat ids fail the item.
- `abilities` parse each trigger (validated against `AbilityTrigger`), `target-range`, `cooldown`, `mana-cost`, `description`, `conditions`, and `mechanics` via `MechanicParser` — `UnknownMechanicException` is converted to a load failure (`ItemDefinitionParser.java:126-134`).

### 3.4 ItemRegistry / ItemLoader

`ItemRegistry` extends `SimpleRegistry<ItemDefinition>` (`ItemRegistry.java`), inheriting the case-insensitive lowercase-keyed behavior (`AGENTS.md` §7.2). It adds `createItemStack(id)` returning `Optional<ItemStack>` (`ItemRegistry.java:25-27`) and `getAllItemIds()`.

`ItemLoader` (`ItemLoader.java:15-24`) wraps `YamlLoader(plugin, "items", "items")` — folder `items/`, parser id `"items"` — and uses the standard `YamlLoader.load(parser, registerAction)` flow.

### 3.5 ItemFactory — the materializer

`ItemFactory.create(ItemDefinition)` (`ItemFactory.java:26-61`) is the single place a definition becomes a real `ItemStack`:

1. Builds the base `ItemStack` from `definition.getMaterial()`.
2. Writes identity PDC keys: `ITEM_ID_KEY`, `ITEM_TYPE_KEY`, `RARITY_KEY` (`ItemFactory.java:31-33`), plus `custom-model-data` and, for reforge stones, the comma-joined `reforge_pool` (`ItemFactory.java:49`).
3. Saves the stat map via `ValmoraAPI.getInstance().getStatModule().saveStats(meta, stats)` (`ItemFactory.java:53`) — the stats live in `STATS_CONTAINER_KEY`.
4. Calls `updateLore(item, meta)` to generate the full display lore.

`updateLore(ItemStack)` (`ItemFactory.java:63-68`) re-regenerates lore in place; `updateLore(item, meta)` (`ItemFactory.java:70-197`) composes: breaking-power line (`ItemFactory.java:114-125`), base lore, lore-template with `$item.stat.<id>$` token resolution (`resolveItemStatTokens`, `ItemFactory.java:199-213`), stats section, enchantments (via `EnchantmentHelper.formatEnchants`), ability descriptions, and the rarity tag. `getBreakingPower` (`ItemFactory.java:215-222`) derives breaking power from the material/tool.

### 3.6 ItemTranslator — vanilla → Valmora

`ItemTranslator.translate(ItemStack)` (`ItemTranslator.java:23-51`) converts any non-custom item into a Valmora item: skips items that already have `ITEM_ID_KEY`; sets type/rarity, writes a `vanilla_<material_lowercase>` id, derives stats via `mapVanillaStats` (`ItemTranslator.java:62-86` — weapon damage, mining speed, armor defense per material), and regenerates lore. `determineRarity` (`ItemTranslator.java:53-60`) maps vanilla equipment tiers to `Rarity`.

### 3.7 ItemType / Rarity enums

`ItemType` (`ItemType.java:6-28`) categorizes items (SWORD, AXE, PICKAXE, HOE, SHOVEL, BOW, CROSSBOW, HELMET, CHESTPLATE, LEGGINGS, BOOTS, WAND, STAFF, FISHING_ROD, TOOL, ACCESSORY, ... NONE, ALL) with a `PRIORITY_ORDER` list (`ItemType.java:30-33`) used for sorting, and `fromMaterial(material)` (`ItemType.java:39-47`) for vanilla inference. `Rarity` (`Rarity.java:3-27`) is `COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, DIVINE` with a MiniMessage color per tier.

### 3.8 The ability system

- **`AbilityTrigger`** (`AbilityTrigger.java:3-16`): `RIGHT_CLICK`, `LEFT_CLICK`, `PASSIVE`, `EQUIP`, `UNEQUIP`, `ON_HIT`, `ON_KILL`, `SNEAK`, `ON_SHOOT`, `ON_DAMAGE_TAKEN`, `ON_TELEPORT`. `ON_DAMAGE_TAKEN`/`ON_TELEPORT` are enumerated but commented as "wired in a later phase" (`AbilityTrigger.java:13-15`).
- **`AbilityDefinition`** (`AbilityDefinition.java`): id, name, trigger, targetRange, cooldown, manaCost, description, conditions (list of script expressions), mechanics (list of `ConfiguredMechanic`).
- **`AbilityMechanic`** interface (`AbilityMechanic.java:9,16`): `String getId()` and `void execute(ExecutionContext)`.
- **`ConfiguredMechanic`** (`ConfiguredMechanic.java:18-27`): binds a `MechanicRegistry` mechanic to its parsed params; `execute(ctx)` centers on `ctx.getLocation()` while `executeAt(loc, ctx)` supports impact-point origin for projectiles.
- **`MechanicRegistry`** (`MechanicRegistry.java:10-21`): stores mechanics keyed by **uppercase** id; `getMechanic` uppercases on lookup. Shared publicly via `AbilityManager.mechanicRegistry` (`AbilityManager.java:9`) — other modules (e.g. backpack) register their own mechanics here (`BackpackModule.java:35`).
- **`MechanicParser`** (`MechanicParser.java:33-56`): parses a list of maps into `ConfiguredMechanic`s, reading the `type` key and throwing `UnknownMechanicException` (`MechanicParser.java:19-23`) for unknown types. Shared with the mob/boss ability loader.

### 3.9 AbilityExecutor — the dispatcher

`AbilityExecutor.fire(player, definition, trigger, target, silent)` (`AbilityExecutor.java:32-82`) is the **single activation path** for all triggers. Order of checks:

1. Skip if no abilities or no matching trigger (`AbilityExecutor.java:34,42`).
2. If no target given and `targetRange > 0`, resolve via `player.getTargetEntity(...)`; show "No target in range!" action bar unless `silent` (`AbilityExecutor.java:45-51`).
3. Evaluate all `conditions` as boolean script expressions via `getExpressionEvaluator()` (`AbilityExecutor.java:98-106`).
4. Cooldown check via `ValmoraProfile.getCooldownManager()` (`AbilityExecutor.java:58-64`).
5. Mana cost check + `state.reduceMana(cost)` (`AbilityExecutor.java:66-72`).
6. Set cooldown (`AbilityExecutor.java:74-76`).
7. Execute each mechanic (`AbilityExecutor.java:78-80`).

The `silent` flag suppresses cooldown/mana/no-target action bars for high-frequency passive triggers (ON_HIT, ON_KILL). `fireHeld(player, trigger, target, silent)` (`AbilityExecutor.java:88-96`) reads the `ITEM_ID_KEY` off the player's main hand and fires its definition — used by the on-kill/on-sneak/on-shoot paths.

### 3.10 Trigger sources

| Trigger | Source | Notes |
|---|---|---|
| `RIGHT_CLICK` / `LEFT_CLICK` | `AbilityListener.onPlayerInteract` (`AbilityListener.java:24-50`) | Only main-hand (`EquipmentSlot.HAND` check, `AGENTS.md` §11.8); maps `RIGHT_CLICK_BLOCK`/`RIGHT_CLICK_AIR`→RIGHT_CLICK, `LEFT_CLICK_*`→LEFT_CLICK |
| `ON_KILL` | `AbilityTriggerListener.onKill` (`AbilityTriggerListener.java:27-32`) | `fireHeld(killer, ON_KILL, victim, silent=true)` |
| `SNEAK` | `AbilityTriggerListener.onSneak` (`AbilityTriggerListener.java:34-41`) | Fires held **and** armor pieces (see §3.11) |
| `ON_SHOOT` | `AbilityTriggerListener.onShoot` (`AbilityTriggerListener.java:43-47`) | `EntityShootBowEvent`, held only |
| `ON_HIT` | `CombatListener` (combat module, `CombatListener.java:51-59`) | `fireHeld(attacker, ON_HIT, victim, silent=true)` after damage; `CombatTracker.recordDamageDealt` backs `$player.last_damage$` (`PlayerVariableProvider.java:153-154`) |
| `PASSIVE` | `StatManager.recalculateStats` (`StatManager.java:117-126`) | Executes mechanics for every equipped+held item on each recalculation |

### 3.11 Armor-piece ability firing

`AbilityTriggerListener.fireArmor(...)` (`AbilityTriggerListener.java:91-100`) iterates the player's armor slots and fires any ability on the worn item matching the given trigger — this is how armor passive/triggered abilities work (e.g. set pieces with `SNEAK`). Projectile-on-hit callbacks flow through `ProjectileAbilityService` (`ProjectileAbilityService.java`): `LaunchProjectileMechanic` registers a projectile uuid→callback; `AbilityTriggerListener.onProjectileHit` (`AbilityTriggerListener.java:49-82`) consumes it and maps the projectile's damage type to a `DamageType` via `mapDamageType` (`AbilityTriggerListener.java:84-89`).

### 3.12 TargetResolver

`TargetResolver.resolve(selector, ctx)` (`TargetResolver.java:31-75`) parses target selectors (javadoc `TargetResolver.java:15-26`):

| Selector | Meaning |
|---|---|
| `@player` / `@self` | the caster |
| `@target` | context target (default when selector blank/unknown) |
| `@enemies_in_radius{r=X}` | hostile mobs (non-players) within X blocks |
| `@allies_in_radius{r=X}` | players within X blocks (includes caster) |
| `@cone{range=X, angle=Y}` | enemies in a forward-facing cone |

Radius/cone selectors center on `ctx.getLocation()` — the caster for normal abilities, or the **projectile impact point** for on-hit callbacks (`TargetResolver.java:34-37`).

### 3.13 Support services

- **`CombatTracker`** (`CombatTracker.java`): static `UUID → lastDamageDealt` map backing the `$player.last_damage$` variable.
- **`TemporaryStatService`** (`TemporaryStatService.java`): timed stat boosts. `ModifyStatMechanic` adds boosts here (`TemporaryStatService.java:46-50`) so they survive recalculation; `StatManager.recalculateStats` re-applies them (`StatManager.java:170`).
- **`CooldownManager`** (`CooldownManager.java:9-27`): `setCooldown/isOnCooldown/getRemainingCooldown/removeCooldown`.
- **`LootListener`** (`LootListener.java`): replaces raw drops with custom items. `onBlockBreak` (`LootListener.java:35-60`) cancels vanilla drops and routes each drop through `processLoot`; `onEntityDeath` (`LootListener.java:67-78`); `onFish` (`LootListener.java:81-95`). `processLoot` (`LootListener.java:101-113`) adds to inventory, and on a full inventory `handleFullInventory` (`LootListener.java:119-136`) spawns a **private glowing drop** visible/pickupable only by the player.

### 3.14 Set bonuses (`set/`)

- **`SetBonusDefinition`** (`SetBonusDefinition.java`): record of `id`, `name`, `List<Tier>`; `Tier` record = `piecesRequired` + `Map<String,Double> stats`. Tiers are **cumulative** (owning 3 of a 2/3/4-piece set grants both the 2- and 3-piece bonuses).
- **`SetBonusParser`** (`SetBonusParser.java:30-58`): parses `set_bonuses/*.yml` sections — `set-id` (defaults to section key), `name`, `bonuses` list of `{pieces-required, stats: {...}}` — validating each stat key against `StatRegistry` (`SetBonusParser.java:46`).
- **`SetBonusRegistry`** (`SetBonusRegistry.java:24-37`): loads via `YamlLoader(plugin, "set_bonuses", "set_bonuses")`, `get(id)` lowercases.
- **`SetBonusService`** (`SetBonusService.java:21-49`): `applyTo(player, StatManager)` counts matching worn pieces by PDC `ITEM_ID_KEY` → `set`, applies cumulative tiers.

---

## 4. Configuration (YAML)

### 4.1 Item definitions — `plugins/Valmora/items/*.yml`

Loaded by `ItemLoader`; each top-level section key is the item id. Reference schema (`items/example.yml`):

```yaml
my_sword:
  name: "My Sword"
  material: DIAMOND_SWORD        # required, Material.matchMaterial
  rarity: EPIC                    # COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE
  item-type: SWORD                # ItemType enum
  lore:
    - "A legendary blade"
  lore-template:                  # optional; $item.stat.<id>$ tokens resolved at create
    - "Damage: <green>$item.stat.damage$</green>"
  custom-model-data: 1001
  reforge-pool:                   # for reforge stones
    - fierce
    - sharp
  set: young_dragon               # links to set_bonuses/young_dragon
  stats:
    damage: 30.0
    strength: 5.0
  abilities:
    flame_wave:
      name: "Flame Wave"
      trigger: RIGHT_CLICK
      target-range: 12.0
      cooldown: 5.0
      mana-cost: 20.0
      description:
        - "Deals damage in a radius."
      conditions:
        - "$player.world_type$ == 'NORMAL'"   # script expressions, ItemDefinitionParser.java:121-123
      mechanics:
        - type: damage
          amount: 10
          damage-type: MAGIC
```

Abilities section parsing: `ItemDefinitionParser.java:94-138`. Mechanics parsing: `ItemDefinitionParser.java:126-134` via `MechanicParser`.

### 4.2 Set bonuses — `plugins/Valmora/set_bonuses/*.yml`

```yaml
young_dragon:
  name: "Young Dragon Armor"
  bonuses:
    - pieces-required: 2
      stats:
        speed: 30.0
    - pieces-required: 3
      stats:
        crit_chance: 10.0
```

See `SetBonusParser.java:30-58`. The shipped `sets.yml` header (`set_bonuses/sets.yml:1-13`) documents intentional omissions — only flat-stat bonuses are expressed; conditional auras, stacking, lightning, witherborn, etc. are not.

### 4.3 Stats

Item `stats:` keys must match `StatRegistry` (from `stats/core.yml`): `health`, `mana`, `damage`, `strength`, `defense`, `crit_chance`, `crit_damage`, `speed`, `health_regen`, `mana_regen`, `luck`, `mining_fortune`, `mining_speed`, `intelligence`, `ferocity`, `pet_luck`, `sea_creature_chance`, `fishing_speed`, `trophy_fish_chance`, `bonus_attack_speed`, `ability_damage`, `magic_find`, `true_defense`, `vitality`, `farming_fortune`, `foraging_fortune`, `breaking_power`, `mining_spread`. Unknown keys fail the item load (`ItemDefinitionParser.java:87-89`).

---

## 5. Data Model / Persistence

Custom items carry **no database state**; everything about an item lives in its PDC keys:

| PDC key (`Keys.java`) | Written at | Purpose |
|---|---|---|
| `ITEM_ID_KEY` (`Keys.java:43`) | `ItemFactory.java:31` | Item identity; read by `AbilityExecutor.fireHeld`, `LootListener`, `SetBonusService`, reforge/enchant modules |
| `ITEM_TYPE_KEY` (`Keys.java:45`) | `ItemFactory.java:32` | `ItemType` for mechanics/recipes |
| `RARITY_KEY` (`Keys.java:44`) | `ItemFactory.java:33` | Rarity for reforge cost/lore |
| `STATS_CONTAINER_KEY` (`Keys.java:46`) | `StatManager.saveStats` | Serialized stat map |
| `ENCHANTS_CONTAINER_KEY` (`Keys.java:48`) | enchant module | Custom enchantments |
| `REFORGE_POOL_KEY` (`Keys.java:66`) | `ItemFactory.java:49` | Reforge-stone candidate list (comma-joined) |
| `REFORGE_ID_KEY` / `REFORGE_DISPLAY_KEY` (`Keys.java:65,67`) | reforge module | Applied reforge |
| `MOB_ID_KEY` (`Keys.java:47`) | mob module | Spawned-mob identity (for ON_KILL/loot) |
| `PET_ID_KEY`/`PET_XP_KEY`/`PET_LEVEL_KEY` (`Keys.java:33-35`) | pet module | Pet items |

Shared static keys are initialized once in `Keys.init(plugin)` (`Keys.java:42`).

---

## 6. API Exposed

### 6.1 Via `ValmoraAPI`

- `getItemManager()` (`ValmoraAPI.java:23`) → `ItemManager`: `getItemRegistry()`, `getItemFactory()`, `getItemTranslator()`, `getSetBonusRegistry()`, `createItemStack(String id)`.
- `getAbilityManager()` (`ValmoraAPI.java:37`) → `AbilityManager`: `getMechanicRegistry()` (public field also accessible directly).

### 6.2 Public entry points

- `ItemManager.createItemStack(id)` (`ItemManager.java:69-84`): custom item first, then `Material.matchMaterial` + `ItemTranslator.translate` fallback.
- `AbilityExecutor.fire(...)` / `fireHeld(...)` (static): programmatic ability activation.
- `MechanicRegistry.registerMechanic(AbilityMechanic)`: extension point for other modules (used by backpack, `BackpackModule.java:35`).
- `SetBonusService.applyTo(player, StatManager)`, `TemporaryStatService.add/applyTo/removeForStat`.

### 6.3 Command — `/item`

Registered at `Valmora.java:237`, permission `valmora.admin` (`plugin.yml:16`). Subcommands in `ItemCommand.onCommand` (`ItemCommand.java:39-196`):

- `give <id> [amount]` (`ItemCommand.java:54-104`) — auto-generates reforge stones for ids ending in `_reforge_stone` (`ItemCommand.java:80-94`).
- `info <id>` / `info` (held) (`ItemCommand.java:106-124`, detail at `sendDefinitionInfo` `ItemCommand.java:198-243`, `sendHeldItemInfo` `ItemCommand.java:245-352`).
- `list [query]` (`ItemCommand.java:126-133`).
- `reload` (`ItemCommand.java:135-138`).
- `enchant` / `enchantbook` (`ItemCommand.java:140-168`, `170-188`).
- Tab-completion at `ItemCommand.java:355-376`.

---

## 7. Dependencies & Consumers

### 7.1 Depends on

- **scriptModule** — condition evaluation (`AbilityExecutor.java:100`), `ScriptMechanic`, `getVariableResolver()` in `SimpleExecutionContext`.
- **statModule** — `StatRegistry` validation (`ItemDefinitionParser.java:81-89`), `saveStats`, and `StatManager.recalculateStats` which drives `PASSIVE` abilities (`StatManager.java:117-126`) and set bonuses (`StatManager.java:164`).
- **profileModule** — `ValmoraProfile.getCooldownManager()` (`ValmoraProfile.java:74`), `PlayerState.getCurrentMana()/reduceMana()/heal()` (`PlayerState.java:44,46,62`).
- **combatModule** — `DamageMechanic` via `DamageCalculator`/`DamageType`; combat fires ON_HIT back into `AbilityExecutor.fireHeld` (`CombatListener.java:51-59`).
- **economy** — `GiveCoinsMechanic`/`TakeCoinsMechanic`.

### 7.2 Consumed by

- **combat** (`CombatListener.java:53-58`) — ON_HIT dispatch + `CombatTracker`.
- **stat** (`StatManager.java:164,170`) — `SetBonusService.applyTo`, `TemporaryStatService.applyTo`.
- **script** (`PlayerVariableProvider.java:153-154`) — `$player.last_damage$`.
- **reforge** (design/reforge.md:5) — base stats, item type/rarity PDC, lore regeneration via `ItemFactory.updateLore`.
- **enchant, recipe, gui, fishing, backpack, pet, mob** — read `ITEM_ID_KEY`/`ITEM_TYPE_KEY`/rarity PDC and consume the registry/mechanics.

### 7.3 Load-order notes

`abilities` registers before `items` (`Valmora.java:196-197`); both after `script`/`stat`/`player`/`ui`, before `combat`/`enchant`. Consumers (`combat`, `enchant`, `recipe`, `gui`, `reforge`, `backpack`) all register later and read item data at runtime via `ValmoraAPI`, never at enable-time — keeping reloads safe.

---

## 8. Unfinished Things / TODOs

- `ON_DAMAGE_TAKEN` and `ON_TELEPORT` triggers exist in the enum but are **not wired** — commented "wired in a later phase" (`AbilityTrigger.java:13-15`).
- `EQUIP`/`UNEQUIP` triggers are enumerated but have **no listener** — no armor-equip event hook currently dispatches them.
- `MechanicRegistry` is uppercase-keyed while item/mob registry keys are lowercase — a consistency trap for new mechanics (`MechanicRegistry.java:10-21`).
- `docs/UNFINISHED_FEATURES.md` flags several item/ability areas as unfinished (referenced set bonus auras, stacking rules, etc.).
- `AGENTS.md` §11.1 (packets) / §11.2 (pathfinding) TODOs remain unfilled — not item-module work but related to the broader engine.

---

## 9. Possible Improvements / Changes

- **Unify registry key case** — make `MechanicRegistry` lowercase-keyed (or case-insensitive) to match `SimpleRegistry` conventions (`AGENTS.md` §7.2).
- **Wire `EQUIP`/`UNEQUIP`/`ON_DAMAGE_TAKEN`/`ON_TELEPORT`** — add an armor-equip listener and a damage-taken hook in the combat pipeline.
- **Move mechanics out of the item module** — `MechanicRegistry` + `MechanicParser` are shared with mobs/bosses yet live under `module.item`; extracting them into the api/infrastructure layer would formalize the cross-module contract.
- **LootListener telekinesis** — the `onFish` handler notes telekinesis is "applied globally" (`LootListener.java:90`); scope it to an actual telekinesis stat/enchantment.
- **Conditional set-bonus auras / stacking** — currently intentionally flat-stats-only (`set_bonuses/sets.yml:1-13`); mechanics-backed set bonuses would be a natural extension of `SetBonusService`.
- **`ItemCommand` hardening** — `sendHeldItemInfo` and `sendDefinitionInfo` print raw config; consider MiniMessage-colored summary and paged `list` output for large registries.
- **Item component adoption** — prefer Paper 1.21 item-component methods over full `ItemMeta` rebuilds where available (`AGENTS.md` §11.11, §11.16).
