# Skill Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Module ID:** `skills` | **Source:** `src/main/java/org/nakii/valmora/module/skill/`

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

---

## Overview

The Skill module is a **levelled progression system** for player characters. Players accumulate **XP per skill** (stored as a flat `Map<String, Double>` per profile), and XP thresholds convert that total XP into a **level**. Nine skills ship by default (`combat`, `farming`, `fishing`, `mining`, `foraging`, `carpentry`, `alchemy`, `enchanting`, `taming`), each fully data-driven from YAML in `plugins/Valmora/skills/`.

The module is a `ReloadableModule` wrapping five collaborating classes:

```
SkillModule (lifecycle)
  ├── SkillLoader        — reads skills/*.yml via YamlLoader
  ├── SkillRegistry      — case-insensitive registry + XP-curve math
  ├── SkillManager       — per-profile XP map + XP/level/reward flow
  ├── SkillListener      — listens for in-game actions → grants XP
  └── SkillCommand       — /skill list|get|give|set (registered in Valmora.java)
```

XP is not granted by this module alone. Several other systems push XP in directly (combat kills via `MobDeathListener`, GUI `givexp` events), while `SkillListener` derives XP from vanilla events by matching against each skill's configured **sources**. Level-ups fire two custom Bukkit events (`SkillXpGainEvent`, `SkillLevelUpEvent`) and execute per-level/milestone **script rewards** defined in YAML.

The module has no persistence of its own — the per-profile `SkillManager` is serialized by `SQLDataStore` into the `valmora_profiles.skills` JSON column.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/skill/
├── SkillModule.java            # ReloadableModule — lifecycle (enable/disable/getId), holds registry/manager/loader/listener
├── SkillManager.java           # Per-profile XP map; addXp/setXp/getLevel; fires events; runs level rewards
├── SkillRegistry.java          # extends SimpleRegistry<SkillDefinition>; DEFAULT_XP_THRESHOLDS curve + level math
├── SkillDefinition.java        # Immutable skill: meta + parsed sources (exact/pattern/tag/default) + rewards
├── SkillDefinitionParser.java  # YAML section → SkillDefinition (via ScriptModule event parser)
├── SkillLoader.java            # YamlLoader<SkillDefinition> over plugins/Valmora/skills/*.yml (filename = id)
├── SkillListener.java          # Vanilla-event XP sources + XP/level-up notifications
├── SkillCommand.java           # /skill list|get|give|set — TabExecutor, registered in Valmora.java
├── Skill.java                  # Legacy 8-value enum (COMBAT…ENCHANTING) with display meta
├── SkillXpGainEvent.java       # Custom sync Event (player, skill, xp)
└── SkillLevelUpEvent.java      # Custom sync Event (player, skill, oldLevel, newLevel)

src/main/resources/skills/
├── combat.yml  ├── mining.yml  ├── farming.yml  ├── foraging.yml  ├── fishing.yml
├── alchemy.yml ├── carpentry.yml ├── enchanting.yml └── taming.yml

src/main/resources/guis/
├── skills_list.yml     # /skills menu (GUI id "skills_list")
└── skills_details.yml  # per-skill level/reward view (GUI id "skill_details")

Test coverage:
src/test/java/org/nakii/valmora/module/skill/
├── SkillDefinitionTest.java
├── SkillManagerXpTest.java   (uses the package-private SkillManager(SkillRegistry) constructor)
└── SkillRegistryTest.java
src/test/java/org/nakii/valmora/config/YamlConfigLoadTest.java:108   (skills/*.yml have max-level + xp-curve)
```

---

## Architecture & Key Classes

### 3.1 Module Lifecycle — `SkillModule.java`

Implements `ReloadableModule` (see `docs/MODULE_DEVELOPMENT.md` §2).

| Method | Behavior | Lines |
|---|---|---|
| constructor | Creates `SkillRegistry`, `SkillLoader`, `SkillManager`, `SkillListener` | `SkillModule.java:14-20` |
| `onEnable()` | Logs; calls `skillLoader.loadSkills()`; registers `skillListener` with the plugin manager | `SkillModule.java:22-27` |
| `onDisable()` | `HandlerList.unregisterAll(skillListener)` — the listener is the only registered handler | `SkillModule.java:29-33` |
| `getId()` | `"skills"` | `SkillModule.java:36-38` |
| `getName()` | `"Skill System"` | `SkillModule.java:40-43` |
| `getSkillManager()` | Returns the live manager | `SkillModule.java:45-47` |
| `getSkillRegistry()` | Returns the live registry | `SkillModule.java:49-51` |

Note: `onDisable()` does **not** clear the registry — `SkillLoader.loadSkills()` clears it at the start of the next load (`SkillLoader.java:20`), which makes `onEnable()` idempotent across `/valmora reload`.

**Wiring in `Valmora.java`:**
- Instantiated at `Valmora.java:158` (`new SkillModule(this)`).
- Registered after `mobManager`, before `combatModule` — `Valmora.java:199`. Load order rationale: skills read the profile module (already loaded) and are read by combat, fishing, warp, quest, etc. (see AGENTS.md §5 order).
- `/skill` executor registered at `Valmora.java:239` (`getCommand("skill").setExecutor(new SkillCommand(this, playerManager))`). Note: no tab completer is attached in `Valmora.java`, even though `SkillCommand` implements `TabExecutor`.
- Exposed via API: `Valmora.java:318-320` (`getSkillManager()`), plus concrete `getSkillModule()` at `Valmora.java:322-324`. Interface declaration: `ValmoraAPI.java:35`.

### 3.2 Skill Definition Model — `SkillDefinition.java`

Immutable record-like class holding everything YAML can express about a skill:

| Field | Meaning | Lines |
|---|---|---|
| `id` | Lowercase skill id (from the filename) | `SkillDefinition.java:17` |
| `name` | Display name (MiniMessage-formatted, e.g. `<gold><bold>⛏ Mining</bold></gold>`) | `SkillDefinition.java:18` |
| `description` | Multi-line description (YAML list joined with `\n`) | `SkillDefinition.java:19` |
| `material` | GUI icon material (default `BOOK`) | `SkillDefinition.java:20` |
| `maxLevel` | Level cap; `addXp` refuses XP past this | `SkillDefinition.java:21` |
| `xpCurve` | Curve id from YAML (`xp-curve: default`) — see §8 for the fact it is currently ignored | `SkillDefinition.java:22` |
| `exactMatches` / `patterns` / `tagMatches` / `defaults` | Compiled source lookup structures, keyed by uppercased source type | `SkillDefinition.java:25-28` |
| `perLevelReward` | Compiled `CompiledEvent` run on every level gained | `SkillDefinition.java:30` |
| `milestoneRewards` | `Map<level, CompiledEvent>` run when a specific level is crossed | `SkillDefinition.java:31` |

**Source parsing** (`parseSources`, `SkillDefinition.java:47-79`) splits each source-type entry into four buckets:

| YAML key form | Bucket | Example |
|---|---|---|
| `DEFAULT` | `defaults[type]` | catch-all for any identifier |
| `#<tag>` | `tagMatches[type]` → `NamespacedKey` | `#minecraft:logs` |
| contains `*` | `patterns[type]` → glob regex (`*` → `.*`, `.` escaped) | `DEEP*` |
| anything else | `exactMatches[type]` | `ZOMBIE`, `IRON_ORE` |

**Lookup** (`getSourceXp(sourceType, identifier)`, `SkillDefinition.java:90-119`) resolves in strict order: **exact match → first pattern match → tag match → type default**. Both arguments are uppercased before lookup.

**Tag resolution** (`checkTags`, `SkillDefinition.java:121-158`):
- `BLOCK_BREAK` / `FISHING`: `Material.matchMaterial(identifier)`, checked against `Tag.REGISTRY_BLOCKS` then `Tag.REGISTRY_ITEMS` (`SkillDefinition.java:122-133`).
- `MOB_KILL`: first matches the Valmora `MobCategory` enum name (from `module/mob/MobCategory`) against the tag key (`SkillDefinition.java:134-143`), then tries `EntityType.valueOf(identifier)` against `Bukkit.getTag("entity_types", …)` (`SkillDefinition.java:147-155`).

### 3.3 Registry & Curve Math — `SkillRegistry.java`

Extends the case-insensitive `SimpleRegistry<SkillDefinition>` (`api/registry/SimpleRegistry.java`; `register` lowercases keys at `SimpleRegistry.java:20-22`).

- `registerSkill` / `getSkill` are thin wrappers (`SkillRegistry.java:19-25`).
- **`DEFAULT_XP_THRESHOLDS`** — the universal curve: 60 cumulative XP thresholds from 10 to 10,000,000 (`SkillRegistry.java:9-17`).
- `getLevelFromXp(curveId, xp)` — returns the first index whose threshold exceeds `xp`; `0` at 0 XP, `60` past the last threshold (`SkillRegistry.java:27-36`). **`curveId` is accepted but ignored** — every skill uses the single hard-coded curve.
- `getXpForLevel(curveId, level)` — `thresholds[level-1]`, `0` for `level <= 0`, capped to the last threshold (`SkillRegistry.java:38-43`).
- `getProgressData(curveId, totalXp)` — record `ProgressData(currentLevel, nextLevel, xpInLevel, xpRequired, percent)` used by commands/GUIs/variables (`SkillRegistry.java:45-59`). At max threshold, `percent` is forced to `100`.

Because `getLevelFromXp(0)` returns **0**, players sit at level 0 until they cross the first threshold (10 XP). Level 1 starts at 10 XP, level 2 at 20, etc. (confirmed by `SkillRegistryTest.java:71-78`).

### 3.4 Per-Profile XP State & Leveling — `SkillManager.java`

One `SkillManager` exists **per `ValmoraProfile`** (`ValmoraProfile.java:20,62-64`). It is a plain in-memory map, not a Bukkit singleton.

| Method | Behavior | Lines |
|---|---|---|
| `SkillManager()` | Public ctor, `injectedRegistry = null` → resolves via `Valmora.getInstance()` | `SkillManager.java:19-21,41-44` |
| `SkillManager(SkillRegistry)` | **Package-private** — test-only injection to avoid the Valmora singleton | `SkillManager.java:24-26` |
| `loadData(Map)` | Clears the map, re-inserts all keys lowercased | `SkillManager.java:28-35` |
| `getSaveData()` | Defensive copy of the XP map (serialized by the DB layer) | `SkillManager.java:37-39` |
| `getXp(String)` / `getXp(Skill)` | `0.0` when unknown; keys lowercased | `SkillManager.java:46-52` |
| `getLevel(String)` | `0` when the skill isn't in the registry; otherwise `registry.getLevelFromXp(curve, xp)` | `SkillManager.java:54-61` |
| `setXp(String/Skill, double)` | Raw set (used by `/skill set`) | `SkillManager.java:71-77` |
| `addXp(String, double, Player)` | The XP pipeline (below) | `SkillManager.java:79-132` |
| `addXp(Skill, double, Player)` | Enum overload, delegates by `skill.name()` | `SkillManager.java:134-136` |

**`addXp` pipeline** (`SkillManager.java:79-132`):

```
addXp(skillId, amount, player)
  │
  ├─ skill = registry.getSkill(skillId); if null → return      (unknown skill = no-op)
  ├─ oldLevel = getLevelFromXp(curve, currentXp)
  ├─ if oldLevel >= skill.maxLevel → return                     (XP capped at max level)
  ├─ newXp = currentXp + amount; skillXp.put(id, newXp)
  ├─ fire SkillXpGainEvent(player, skill, amount)               (fired AFTER the XP is written)
  ├─ newLevel = getLevelFromXp(curve, newXp); cap to maxLevel
  └─ if newLevel > oldLevel:
       ├─ fire SkillLevelUpEvent(player, skill, oldLevel, newLevel)
       ├─ build MemoryConfiguration params + SimpleExecutionContext(player, player, location, params)
       └─ for lvl in (oldLevel+1 .. newLevel):                  (multi-level jump support)
            params.set("level", lvl)                            ← $param.level$ for reward scripts
            execute perLevelReward (if set)
            execute milestoneRewards.get(lvl) (if set)
```

**Known quirk:** the same `MemoryConfiguration` is mutated per loop iteration and shared by every `CompiledEvent`, so after the loop all reward executions see the **final** level, not their own. `SkillManagerXpTest.java:99-114` documents this explicitly (both captured contexts report `level == 2` even though level 1's reward also ran). Reward authors should treat `$param.level$` as "the level you ended up at".

The events are plain (non-cancellable) sync Bukkit events and are `callEvent()`-ed directly (`SkillXpGainEvent` at `SkillManager.java:98`, `SkillLevelUpEvent` at `SkillManager.java:110`). Both extend `Event` and expose player/skill getters and a static `getHandlerList()` (`SkillXpGainEvent.java:7-41`, `SkillLevelUpEvent.java:7-46`).

### 3.5 XP Sources — `SkillListener.java`

Registers `@EventHandler` methods mapped to YAML source types. Every handler resolves the active profile via `plugin.getPlayerManager().getSession(uuid).getActiveProfile()` and returns early when there's no profile (`getProfile`, `SkillListener.java:25-29`). Each iterates **all** registered skills and asks `skill.getSourceXp(TYPE, identifier)`; only `xp > 0` results are applied.

| Handler | Event | Source type | Identifier passed | Lines |
|---|---|---|---|---|
| `onBlockBreak` (`HIGH`, ignoreCancelled) | `BlockBreakEvent` | `BLOCK_BREAK` | `block.getType().name()` | `SkillListener.java:42-53` |
| `onCropHarvest` (`HIGH`, ignoreCancelled) | `BlockBreakEvent` | `CROP_HARVEST` | `block.getType().name()` | `SkillListener.java:90-101` |
| `onEntityDeath` | `EntityDeathEvent` (killer != null) | `MOB_KILL` | `entity.getType().name()` | `SkillListener.java:55-68` |
| `onFish` | `PlayerFishEvent` (`CAUGHT_FISH`) | `FISHING` | caught item `ItemStack.getType().name()`, default `"COD"` | `SkillListener.java:70-88` |
| `onCraftItem` | `CraftItemEvent` | `CRAFT_ITEM` | `recipe.getResult().getType().name()` | `SkillListener.java:103-115` |
| `onBrew` | `BrewEvent` | `BREW_POTION` | hard-coded `"ANY"` | `SkillListener.java:117-130` |
| `onTame` | `EntityTameEvent` | `TAME_MOB` | `entity.getType().name()` | `SkillListener.java:132-144` |
| `onEnchant` | `EnchantItemEvent` | `ENCHANT_ITEM` | `item.getType().name()` | `SkillListener.java:146-156` |

Notes:
- `onBlockBreak` and `onCropHarvest` both listen to `BlockBreakEvent`, so a single broken block can feed two different source types; shipped configs keep them disjoint (crops vs. mushroom blocks/cactus in `farming.yml`).
- `onBrew` awards XP to **every player viewer** of the brewing stand (`SkillListener.java:122-127`), and since `BREW_POTION` looks up identifier `"ANY"`, only an exact `ANY` entry or a `DEFAULT` entry will ever match. The shipped `alchemy.yml` defines **no sources**, so brewing grants nothing out of the box.
- `onFish` can only ever pass material names; the `TREASURE: 1000.0` entry in `fishing.yml` is therefore unreachable as shipped (no material is named `TREASURE`).

**Notifications** (also in `SkillListener`):
- `onSkillXpGain` → action bar `<aqua>+<yellow><xp> <aqua><skill-name> XP` for 20 ticks via `UIManager.getActionBar().showTemporary` (`SkillListener.java:31-35`, `ActionBarUI.java:35-38`).
- `onSkillLevelUp` → multi-line chat announcement via `UIManager.getChat().sendLevelUp` (`SkillListener.java:37-40`, `ChatUI.java:21-27`).

### 3.6 Command Layer — `SkillCommand.java`

`TabExecutor` for the `/skill` command, constructed with `PlayerManager` + `SkillModule` (`SkillCommand.java:24-27`). Dispatched on `args[0]` (`SkillCommand.java:30-46`):

| Subcommand | Syntax | Permission | Behavior | Lines |
|---|---|---|---|---|
| `list` | `/skill list` | anyone | Header + `name (id)` per registered skill | `SkillCommand.java:48-55` |
| `get` | `/skill get <player> <skill>` | anyone | Level/total XP/`ProgressData` for an online player's active profile | `SkillCommand.java:57-92` |
| `give` | `/skill give <player> <skill> <xp>` | `valmora.admin` | `profile.getSkillManager().addXp(skillId, amount, target)` | `SkillCommand.java:94-134` |
| `set` | `/skill set <player> <skill> <xp\|level> <value>` | `valmora.admin` | `setXp` raw, or `getXpForLevel` then `setXp` for `level` | `SkillCommand.java:136-185` |

All admin subcommands re-check `sender.hasPermission("valmora.admin")` inline (`SkillCommand.java:95-98,137-140`), and require the target player to be **online** with a loaded active profile. `get`/`give`/`set` print red error messages for unknown skills, missing players, or bad numbers. Tab completion covers `list|get|give|set`, online player names, registered skill ids, and the `xp|level` discriminator (`SkillCommand.java:198-228`).

> The shipped docs (`docs/VALMORA_DOCUMENTATION.md` §28, lines 1479-1488) describe different subcommands (`info`, `givexp`, `setlevel`) — the actual code implements `list`/`get`/`give`/`set`. The doc is stale (see §8).

### 3.7 GUI — `/skills` menu

The module defines no GUI code, but the Gui module ships two skill GUIs and a `/skills` command (the GUI `command:` field registers it at runtime via `GuiModule.registerGuiCommand`, `GuiModule.java:211-221`):

- `guis/skills_list.yml` — title "✦ Your Skills", 6 rows, refresh every 40 ticks. A `PAGINATED` component iterates `$player.skill.list$`, sorts by `name`, and renders each skill's material/name/lore (`<yellow>Level: $skill.level$ / $skill.max_level$`). Left-click runs `open_gui skill_details target_skill=$skill$`, routing the whole skill object as a prop (`skills_list.yml:1-41`).
- `guis/skills_details.yml` — dynamic detail view reading `$prop.target_skill.*$` and `$gui.viewed_skill.*$` (supplied by `GuiVariableProvider.resolveViewedSkill`, `GuiVariableProvider.java:88-117`). Renders a progress panel and a paginated 1..max_level strip with three states: `completed` (`$gui.viewed_skill.level$ >= $lvl$`, lime), `current` (`== lvl - 1`, yellow), `locked` (red), plus Bestiary/Slayer/Back decorations and paging arrows (`skills_details.yml:6-119`).

---

## Configuration (YAML)

Files live in `plugins/Valmora/skills/` (auto-copied from the jar by `Valmora.saveAllResources`, `Valmora.java:469-484`). `SkillLoader` uses `YamlLoader.loadFilesAsSections` — the **filename without `.yml` is the skill id**, and the whole file is parsed as one section (`SkillLoader.java:19-25`, `YamlLoader.java:78-111`).

### 4.1 Top-level keys

| Key | Type | Default | Explanation |
|---|---|---|---|
| `id` | string | *(unused — filename wins)* | Present in every shipped file but **not read by the parser**; the id always comes from the filename. |
| `name` | string | `id` | Display name; MiniMessage tags are honored (`SkillDefinitionParser.java:16`). |
| `description` | list of strings | empty | Rendered as a multi-line lore block, joined with `\n` (`SkillDefinitionParser.java:17`). |
| `material` | string | `BOOK` | GUI icon; `Material.matchMaterial` (`SkillDefinitionParser.java:18`). Invalid names yield `null` material. |
| `max-level` | int | `60` | Level cap; XP past it is refused by `addXp` (`SkillDefinitionParser.java:19`, `SkillManager.java:89-91`). |
| `xp-curve` | string | `"default"` | Curve id — **ignored at runtime**; `SkillRegistry` always uses `DEFAULT_XP_THRESHOLDS` (`SkillDefinitionParser.java:20`, `SkillRegistry.java:28`). |
| `sources` | section | none | Source-type → identifier → XP mappings (see §4.2). |
| `rewards.per-level` | list of strings | none | Script commands executed on **every** level gained (`SkillDefinitionParser.java:40-41`). |
| `rewards.milestones.<level>` | list of strings | none | Script commands executed when the specific level is crossed (`SkillDefinitionParser.java:44-54`). |

`YamlConfigLoadTest.java:108-115` enforces that `max-level` and `xp-curve` exist on the shipped skill files.

### 4.2 `sources` schema

```
sources:
  <SOURCE_TYPE>:          # case-insensitive; see §3.5 for supported types
    <IDENTIFIER>: <xp>    # exact material/mob name, "#tag", glob pattern, or DEFAULT
```

Identifier resolution (see §3.2) runs **exact → pattern → tag → DEFAULT**, so overlapping entries resolve to the most specific match.

### 4.3 Shipped files (`src/main/resources/skills/`)

| File | Source types | Notable values | Rewards |
|---|---|---|---|
| `combat.yml` | `MOB_KILL` | ZOMBIE 5.0, `test_zombie` 15.0, ENDER_DRAGON 1000.0 | per-level: `+strength 1`, `coins $param.level$*10`; milestones 10 → IRON_SWORD:1, 25 → tag `tier2_combat_unlocked` |
| `mining.yml` | `BLOCK_BREAK` | STONE 1.0, DEEPSLATE 1.2, ores 10–180, ANCIENT_DEBRIS 500.0 | per-level: `+defense 0.5`, `coins $param.level$*15`; milestones 10 → IRON_PICKAXE, 30 → DIAMOND_PICKAXE |
| `farming.yml` | `CROP_HARVEST`, `BLOCK_BREAK` | WHEAT/CARROTS/POTATOES/BEETROOTS 3.0, NETHER_WART 5.0, MELON/PUMPKIN/COCOA 4.0, SUGAR_CANE 2.0, BAMBOO 1.0, TORCHFLOWER/PITCHER 10.0; mushroom blocks 3.0, CACTUS 2.0 | per-level: `+farming_fortune 0.5`, `coins $param.level$*10`; milestones 10 → GOLDEN_HOE, 25 → DIAMOND_HOE, 50 → NETHERITE_HOE |
| `foraging.yml` | `BLOCK_BREAK` | all 8 log + 8 wood types @ 15.0 | per-level: `+strength 0.2`, `coins $param.level$*12`; milestones 10 → IRON_AXE, 30 → DIAMOND_AXE |
| `fishing.yml` | `FISHING` | COD 50.0, SALMON 70.0, PUFFERFISH/TROPICAL_FISH 150.0, TREASURE 1000.0 *(unreachable — see §3.5)* | per-level: `+mana 0.3`, `coins $param.level$*25`; milestones 15 → tag `master_angler`, 25 → ENCHANTED_BOOK |
| `alchemy.yml` | *(none)* | — | per-level: `coins $param.level$*5`; milestones 10 → BLAZE_POWDER:5, 30 → NETHER_WART:16 |
| `carpentry.yml` | `CRAFT_ITEM` | CRAFTING_TABLE 2.0, planks 1.0, STICK 0.5, CHEST/BARREL 5.0, BOOKSHELF 8.0, stairs 3.0, doors/trapdoors 5.0, wooden tools 4.0, BOW 10.0, SHIELD 15.0, etc. | per-level: `+ability_damage 0.2`, `coins $param.level$*12`; milestones 10 → OAK_LOG:32, 25 → CHEST:8, 50 → SHULKER_BOX:1 |
| `enchanting.yml` | `ENCHANT_ITEM` | swords/pickaxes 10–100, armor 8–130, BOW 30, CROSSBOW 35, TRIDENT 80, FISHING_ROD 20, BOOK 5.0 | per-level: `+magic_find 0.3`, `coins $param.level$*20`; milestones 10 → BOOK:3, 25 → EXPERIENCE_BOTTLE:16, 50 → ENCHANTED_BOOK:1 |
| `taming.yml` | `TAME_MOB` | WOLF 100.0, CAT 80.0, HORSE 200.0, DONKEY/MULE 150.0/175.0, LLAMA 150.0, PARROT 120.0, FOX 200.0, AXOLOTL 120.0, CAMEL 180.0 | per-level: `+pet_luck 0.5`, `coins $param.level$*15`; milestones 5 → BONE:16, 20 → LEAD:4, 40 → SADDLE:1 |

### 4.4 Reward scripting context

Rewards are script commands compiled through `plugin.getScriptModule().getEventParser().parseList(...)` (`SkillDefinitionParser.java:41,50-51`) and executed with an `ExecutionContext` whose caster and player are the leveling player, and whose params expose `$param.level$` (see the quirk in §3.4).

---

## Data Model / Persistence

### 5.1 In-memory model

```
ValmoraPlayer (online player)
  └── Map<UUID, ValmoraProfile>          (multi-profile / characters)
        └── ValmoraProfile
              ├── SkillManager           ← per-character XP state
              │     └── Map<String, Double> skillXp   (lowercased skill id → total XP)
              ├── StatManager
              └── ... (PlayerState, CooldownManager, tags, variables)
```

`ValmoraProfile` owns `new SkillManager()` (`ValmoraProfile.java:20`) exposed via `getSkillManager()` (`ValmoraProfile.java:62-64`). Skills are therefore **per-profile** — switching profiles via `/profile switch` switches skill XP too.

### 5.2 Database

- Column `valmora_profiles.skills TEXT` in the v1 schema (`SQLDataStore.java:137`).
- Load: `gson.fromJson(rsProfiles.getString("skills"), Map<String, Double>)` then `profile.getSkillManager().loadData(skills)` (`SQLDataStore.java:194,212-213`). Missing/null JSON is tolerated.
- Save: `gson.toJson(profile.getSkillManager().getSaveData())` (`SQLDataStore.java:297`) written in the per-profile upsert inside the transaction (`SQLDataStore.java:286-331`, bind index 5 on insert and 16 on update). `ON CONFLICT(id) DO UPDATE` (SQLite) / `ON DUPLICATE KEY UPDATE` (MySQL).
- Load is async via `dbExecutor`; Bukkit-safe only because the resulting map is loaded into the profile object before any UI reads it. No Bukkit calls happen on the async thread (per AGENTS.md §7.4).

No standalone skill table exists; XP is wholly embedded in the profile row.

---

## API Exposed

- **`ValmoraAPI.getSkillManager()`** → the singleton `SkillManager` owned by `SkillModule` (`ValmoraAPI.java:35`, `Valmora.java:318-320`).
- **`Valmora.getSkillModule()`** → concrete `SkillModule` (not on the interface) with `getSkillManager()` / `getSkillRegistry()` (`Valmora.java:322-324`, `SkillModule.java:45-51`).

`SkillManager` public surface (`SkillManager.java`): `loadData`, `getSaveData`, `getSkillRegistry`, `getXp(String)` / `getXp(Skill)`, `getLevel(String)` / `getLevel(Skill)`, `getLevelFromXp(String,double)`, `setXp(String/Skill,double)`, `addXp(String,double,Player)` / `addXp(Skill,double,Player)`.

`SkillRegistry` public surface (`SkillRegistry.java` + `SimpleRegistry`): `registerSkill`, `getSkill`, `getLevelFromXp`, `getXpForLevel`, `getProgressData`, plus registry ops `get`/`contains`/`values`/`getKeys`/`size`/`clear`/`unregister` (`SimpleRegistry.java:19-57`).

**Events** (for other modules/listeners/quests):
- `SkillXpGainEvent(player, skill, xp)` — fired after XP is applied (`SkillManager.java:97-98`).
- `SkillLevelUpEvent(player, skill, oldLevel, newLevel)` — fired before reward scripts run (`SkillManager.java:109-110`).

---

## Dependencies & Consumers

### Upstream dependencies (loads before skills)

| Dependency | Why | Evidence |
|---|---|---|
| `profile` / `PlayerManager` | Resolves the active `ValmoraProfile` for XP writes | `SkillListener.java:25-29`, `SkillCommand.java:77-81` |
| `script` | Compiles and executes `rewards.per-level` / `milestones` | `SkillDefinitionParser.java:41,50-51`, `SkillManager.java:112-130` |
| `mob` | `MobCategory` used in tag matching | `SkillDefinition.java:134-143` |
| `ui` | Action bar + chat level-up notifications | `SkillListener.java:33,39`, `ActionBarUI.java:35-38`, `ChatUI.java:21-27` |

### Downstream consumers (read/write skill state)

| Consumer | What it uses | Evidence |
|---|---|---|
| `combat` via `MobDeathListener` | Grants `"combat"` XP from `MobDefinition.getXpReward()` on custom-mob kills — bypasses `sources` config | `MobDeathListener.java:62-63` |
| `quest` | `LEVEL_SKILL` (+1 per `SkillLevelUpEvent`) and `EXP_GAIN` (`ceil(xp)`) objective types | `QuestListener.java:280-292`, `QuestObjectiveTypes.java:27,29`; schema in `docs/QUEST_SYSTEM.md:457-479` |
| `gui` events | `givexp player <SKILL> <amount>` script event → `addXp` (uses the `Skill` enum) | `GiveXpEventFactory.java:23-41` |
| `gui` variables | `$gui.viewed_skill.*$` progress data in `skill_details` GUI | `GuiVariableProvider.java:88-117` |
| `script` variables | `$player.skill.<id>.level$`, `.xp$`, `$player.skill.list$` JSON for skills_list GUI | `PlayerVariableProvider.java:89-137` |
| `warp` | `skill:<id>:<level>` unlock conditions | `WarpManager.java:38-50` |
| `alchemy` | Alchemy-machine crafting gated by `getAlchemyLevel(player)` from alchemy XP | `AlchemyMachineHandler.java:281-294` |
| `profile` GUI | "⚔ Skill Level" = sum of all skill levels in the profile list | `ProfileGui.java:196-202` |
| Gui module config | `/skills` and `skill_details` GUIs drive player-facing display | `guis/skills_list.yml`, `guis/skills_details.yml` |

---

## Unfinished Things / TODOs

1. **`xp-curve` is dead config.** `SkillDefinition.getXpCurve()` is plumbed through but `SkillRegistry` hard-codes `DEFAULT_XP_THRESHOLDS` and ignores the argument (`SkillRegistry.java:28,39,49`). All nine shipped skills say `default`. No custom curves are possible today.
2. **`Skill` enum is out of sync with the data.** The enum (`Skill.java:3-12`) has 8 values — `CRAFTING` (not `carpentry`) and **no `TAMING`**. `GiveXpEventFactory` does `Skill.valueOf(args[1].toUpperCase())` (`GiveXpEventFactory.java:29`), so `givexp` for `carpentry` or `taming` silently no-ops, while `CRAFTING` references a skill id that doesn't exist in the YAML. The enum's `maxLevel` is also unused (level math lives in the registry).
3. **XP-threshold table in `docs/VALMORA_DOCUMENTATION.md` §27 (lines 1413-1430) is stale** — it lists 10/50/100/250/… thresholds and a "last defined threshold at level 28" claim that don't match `DEFAULT_XP_THRESHOLDS` (which defines all 60 levels through 10,000,000).
4. **`/skill` command docs are stale** — §28 documents `info`/`givexp`/`setlevel`; the code implements `list`/`get`/`give`/`set` (`SkillCommand.java:37-42`).
5. **`/skill` has no tab completer wired** in `Valmora.java:239` (only `setExecutor`), even though `SkillCommand` implements `TabExecutor`.
6. **Dead XP entries in shipped config:** `fishing.yml` `TREASURE: 1000.0` can never match (listener passes material names only, `SkillListener.java:78-80`); `alchemy.yml` has no `sources`, so `onBrew`/`BREW_POTION` (`SkillListener.java:117-130`) grants nothing out of the box.
7. **`onDisable()` doesn't clear the registry** — it relies on `loadSkills()` clearing before reload (`SkillLoader.java:20`). Harmless today, but a future `onDisable` that stops at `unregisterAll` would leak definitions if the loader path changes.
8. **No XP curve editor, no reset/wipe subcommand**, no way to grant XP to an offline player (all `/skill` admin ops require an online target, `SkillCommand.java:63-67,105-109,147-151`).

---

## Possible Improvements / Changes

1. **Real per-skill XP curves** — store thresholds in YAML (`xp-curve: {1: 10, 2: 20, ...}` or a named-curve registry) and have `SkillRegistry` actually look them up; makes `getLevelFromXp(curveId, …)` meaningful.
2. **Reconcile or delete the `Skill` enum.** Either generate the `givexp` event factory from registry ids (string-based) or add `TAMING` and rename `CRAFTING`→`CARPENTRY`. Dropping the enum entirely would remove the duplicate-metadata drift (see §8-2).
3. **Fix the `$param.level$` quirk** in `SkillManager.java:117-130` — give each reward execution its own params copy so per-level rewards see their own level instead of the final one.
4. **Make the events cancellable/`isCancelled`-aware** so quests or other modules can veto an XP grant before it lands (currently the XP is already written when `SkillXpGainEvent` fires, `SkillManager.java:93-98`).
5. **Per-source XP multipliers / modifiers** — e.g. a global `xp-multiplier` per skill or per-source-type to allow "2× mining XP weekend" config without editing every entry.
6. **Offline-safe admin grants** — queue XP grants to a `UUID` target that apply on profile load, or write directly to the DB.
7. **Attach the tab completer** for `/skill` in `Valmora.java:239`.
8. **Wire `BREW_POTION` to something meaningful** — pass the brewed potion's base type as the identifier instead of `"ANY"` so brewing XP can be granular like the other sources.
9. **Add a `SkillManager.clearXp(skillId)`/reset helper** and maybe a `/skill reset` admin subcommand for server admins (and a `$player.skill.<id>.next_level$`-style complement already exists in `ProgressData.nextLevel`).
10. **Reusable source-matching utilities** — `SkillDefinition.checkTags` (`SkillDefinition.java:121-158`) duplicates material/entity-tag plumbing; a shared "source resolver" could also power quest `EXP_GAIN`/`LEVEL_SKILL` matching consistently.
