# Cross-Module Integration

> **Purpose:** This document describes how all Valmora modules interrelate at the code level. It covers dependency direction, shared APIs, data flows, reload semantics, and integration risks. Refer to this before adding or modifying cross-module interactions.
>
> Related: [MODULE_DEVELOPMENT.md](../../MODULE_DEVELOPMENT.md), [VALMORA_DOCUMENTATION.md](../../VALMORA_DOCUMENTATION.md)

---

## 1. Dependency Direction

Module registration order (see `Valmora.java`) enforces a strict layering. Lower modules are available to higher ones; no upward references are permitted:

```
script → time → rarity → stat → player → economy
       → ui → ability → item → mob → skill → combat → gui → recipe → machine → modifier
       → alchemy → enchant → zone → resource → fishing → npc → warp
       → points → notify → quest → collection → hud → calendar → pet → progression → pack
```

> **Note:** `machine` (module id `machine`) sits between `recipe` and `modifier` — it loads the
> `machines/*.yml` machine-definition layer (which GUI opens for which machine id, open-triggers,
> input/output slot shapes) consulted by `MachineOpenListener`. See CLAUDE.md §5/§9.4.

> **Note:** `pack` (module id `pack`) is registered **last**, deliberately — it only orchestrates
> other modules' existing reload machinery (`ModuleManager.reloadModules(Set<String>)`, a new
> targeted-subset overload — see §4.1) and must never be a dependency of anything else. Its
> `YamlLoader` content-id-namespacing hook is **not** tied to its own `onEnable()`/`onDisable()` —
> see `docs/modules/design/pack.md` §3 for why that would silently break on every `/valmora reload`,
> and how `Valmora.onEnable()`/`onDisable()` install/uninstall it directly instead, at plugin
> lifetime rather than module lifetime.

> **Note:** `accessory`, `backpack`, `quiver`, and `slayer` are **no longer modules** — they were
> removed and rebuilt as plain data (items + GUI `STORAGE` components, and quest packages). See
> `docs/modules/design/backpack.md` and `docs/modules/design/slayer.md`. There is no `pipeline`
> registration slot either — `HookBus` is a shared cross-cutting primitive, not a `ReloadableModule`
> (see `docs/modules/design/pipeline.md`).

### 1.1 Dependency Matrix

The tables below list **Uses** (modules this module depends on) and **Consumers** (modules that depend on this module).

#### 1.1.1 Core Modules

| Module  | Uses                                  | Consumers                              |
|---------|---------------------------------------|----------------------------------------|
| script  | (none)                                | stat, profile, combat, item, mob, skill, alchemy, enchant, quest, npc |
| stat    | script                                | profile, combat, item, mob, skill, economy, modifier |
| profile | stat, script                          | combat, item, mob, npc, economy, skill |
| combat  | stat, profile, script, mob            | item, skill, enchant, progression (slayer content consumes combat indirectly via quest KILL objectives, not as a module) |

#### 1.1.2 Item & Equipment

| Module     | Uses                              | Consumers                             |
|------------|-----------------------------------|---------------------------------------|
| item       | stat, profile, script, mob        | combat, enchant, skill, npc, quest, gui |
| enchant    | item, stat, script                | combat, skill, gui (enchanting-table apply/select/remove events), recipe (anvil merge) |
| modifier   | item, stat, script, rarity, recipe | item (lore rendering), stat (STAT effect contributions) — generic modifier framework replacing the old reforge module, docs/Valmora_Modifier_Framework_Design.docx |

Accessories/backpacks are `item-type` tags + a GUI `STORAGE` component, not modules — see §2.1.1
and `docs/modules/design/backpack.md`. Quiver has no current implementation.

#### 1.1.3 Mobs, NPCs, and Quests

| Module  | Uses                              | Consumers                             |
|---------|-----------------------------------|---------------------------------------|
| mob      | stat, profile, item, script       | combat, skill, quest, npc             |
| npc      | profile, item, script, mob        | quest, economy                        |
| quest    | profile, item, mob, script, stat  | progression, notify                   |

#### 1.1.4 Skills & Combat

| Module   | Uses                              | Consumers                             |
|----------|-----------------------------------|---------------------------------------|
| skill    | stat, profile, script, item, mob  | combat, enchant, alchemy              |
| alchemy  | item, stat, script                | skill                                 |

Slayer content is quest packages + a GUI, not a module — see `docs/modules/design/slayer.md`.

#### 1.1.5 World & Economy

| Module      | Uses                              | Consumers                             |
|-------------|-----------------------------------|---------------------------------------|
| economy     | stat, profile, script             | npc, quest                            |
| zone        | stat, profile, script             | time, resource, fishing               |
| resource    | zone, stat, script                | collection                            |
| fishing     | zone, stat, item, script          | collection                            |

#### 1.1.6 UI & Display

| Module  | Uses                              | Consumers                             |
|---------|-----------------------------------|---------------------------------------|
| gui     | item, profile, script, mob        | quest, skill, npc                     |
| ui      | profile, gui                      | hud, quest, skill                     |
| hud     | profile, ui, stat                 | (player-facing only)                  |
| notify  | profile, script                   | quest, combat                         |

#### 1.1.7 Utilities & Progression

| Module        | Uses                              | Consumers                             |
|---------------|-----------------------------------|---------------------------------------|
| time          | zone, stat, script                | resource, fishing                     |
| collection    | resource, fishing, stat, script   | progression                           |
| calendar      | time, stat, script                | progression                           |
| progression   | quest, stat, script               | (none — terminal)                     |
| pack          | (none — reaches other modules only via `ModuleManager.reloadModules`, never a direct compile-time dependency) | (none — terminal, nothing may depend on it) |

---

## 2. Shared Infrastructure

All modules interact through a common set of APIs and utilities defined in `api/` and `infrastructure/`.

### 2.1 ValmoraAPI Accessors

| API Accessor              | Interface                 | Used By                                    |
|---------------------------|---------------------------|--------------------------------------------|
| `getScriptManager()`      | `ScriptManager`           | All modules needing parameter resolution   |
| `getStatManager()`        | `StatManager`             | combat, item, mob, skill, etc.             |
| `getProfileManager()`     | `ProfileManager`          | combat, item, mob, npc, quest              |
| `getItemManager()`        | `ItemManager`             | combat, enchant, npc, quest, gui           |
| `getMobManager()`         | `MobManager`              | combat, skill, quest                       |
| `getSkillManager()`       | `SkillManager`            | combat, alchemy                            |
| `getCombatManager()`      | `CombatManager`           | item, skill                                |
| `getQuestManager()`       | `QuestManager`            | progression, notify, npc                   |
| `getEconomyManager()`     | `EconomyManager`          | npc, quest                                 |
| `getGUIManager()`         | `GUIManager`              | quest, skill, npc                          |
| `getZoneManager()`        | `ZoneManager`             | resource, fishing                |
| `getEnchantModule()`      | `EnchantModule`           | combat, skill, gui, recipe                 |
| `getPetManager()`         | `PetManager`              | combat, stat, progression                  |
| `getHudManager()`         | `HudManager`              | ui, profile                                |
| `getNotifyManager()`      | `NotifyManager`           | quest, combat                              |
| `getCollectionManager()`  | `CollectionManager`       | progression                                |
| `getCalendarManager()`    | `CalendarManager`         | progression                                |
| `getProgressionManager()` | `ProgressionManager`      | (terminal consumer)                        |

### 2.1.1 HookBus — Cross-Cutting Extension Points

`ValmoraAPI.getInstance().getHookBus()` is a shared dispatch primitive (not tied to any one
module's registration slot) that lets combat, resource, fishing, item, mob, and gui insert
YAML-defined or Java-registered logic at named points in their otherwise-hardcoded sequences
(e.g. `combat:pre_damage`, `gui:<id>:on_open`). Full reference: `docs/modules/design/pipeline.md`.

### 2.2 Common Data Structures

- **Registry<T>** — Case-insensitive keyed storage; see `infrastructure/config/Registry.java`. Populated in `onEnable()`, cleared in `onDisable()`.
- **ExecutionContext** — Passed to mechanics, scripting, and abilities; see §7.3 of AGENTS.md.
- **VariableResolver** — Resolves script variables; provided via `ValmoraAPI.getScriptManager().getVariableResolver()`.
- **YamlLoader** — Central YAML loader at `infrastructure.config.YamlLoader`.

### 2.3 Persistent Data

| Layer       | Manager           | Data Managed                          |
|-------------|-------------------|---------------------------------------|
| Player      | `ProfileManager`  | Stats, currency, inventory, progress  |
| World       | `ZoneManager`     | Zone definitions, active events       |
| Items       | `ItemManager`     | Custom item types, templates          |
| Mobs        | `MobManager`      | Mob templates, spawn configs          |
| Quests      | `QuestManager`    | Quest definitions, player progress    |
| Skills      | `SkillManager`    | Skill definitions, cooldowns          |
| Database    | `DatabaseManager` | HikariCP pool (SQLite/MySQL)          |

---

## 3. Data Flows

### 3.1 Combat Damage Flow

```
1. Player/Mob attacks → EntityDamageEvent
2. CombatManager intercepts → resolves attacker/defender profiles
3. StatManager calculates final damage:
   a. Base damage from weapon/item
   b. Attacker stats (attack power, crit)
   c. Defender stats (defense, resistance)
   d. Enchant modifiers
4. SkillManager applies on-hit skills (via ExecutionContext)
5. ProfileManager updates defender's health
6. (Slayer content, if any, tracks its own progress via the quest module's KILL objectives — no dedicated manager)
7. NotifyManager sends damage indicators
8. ProgressionManager triggers damage-dealt achievements
```

### 3.2 Item Interaction Flow

```
1. Player interacts with item → PlayerInteractEvent
2. ItemManager identifies custom item via PDC
3. ScriptManager resolves item parameters (scripts, variables)
4. ProfileManager updates player state (if consumable)
5. StatManager applies stat modifiers (if equipment)
6. EnchantModule applies enchant effects (logic: hooks and/or compiled combat:/triggers:/stats: — see docs/modules/design/enchant.md)
7. SkillManager triggers skills (if on-use)
8. HudManager updates UI if relevant
```

### 3.3 Quest Progression Flow

```
1. Player action occurs (kill mob, talk to NPC, collect item)
2. Event propagates to QuestManager
3. QuestManager checks objective conditions:
   a. ProfileManager provides player progress data
   b. ZoneManager provides location context
   c. ItemManager verifies item possession
   d. MobManager verifies entity type
4. If objective complete → QuestManager updates progress
5. NotifyManager sends completion feedback
6. Reward application:
   a. StatManager applies stat rewards
   b. ItemManager grants item rewards
   c. EconomyManager grants currency
   d. ProgressionManager updates achievement state
7. ProfileManager persists changes
```

### 3.4 Zone Event Flow

```
1. Player enters zone → custom event
2. ZoneManager triggers zone-specific scripts
3. ScriptManager resolves event parameters
4. StatManager applies zone modifiers (damage, speed, etc.)
5. ResourceManager starts/stop resource nodes
6. FishingManager updates fishing loot tables
7. TimeManager adjusts local time/weather
9. HudManager displays zone info
```

---

## 4. Reload Semantics

### 4.1 Reload Order

`ModuleManager.reloadModules()` (invoked via `/valmora reload`) performs:

1. **Disable phase** — All modules' `onDisable()` called in **reverse registration order** — the
   exact reverse of the Section 1 chain:
   ```
   pack → progression → pet → calendar → hud → collection → quest → notify → points
   → warp → npc → fishing → resource → zone → enchant → alchemy → modifier → machine
   → recipe → gui → combat → skill → mob → item → ability → ui
   → economy → player → stat → rarity → time → script
   ```

2. **Re-enable phase** — `ModuleManager.enableModules()` called in **forward registration order** (as listed in Section 1).

**Targeted subset reload:** `ModuleManager.reloadModules(Set<String> moduleIds)` — added for the
content pack manager (`docs/modules/design/pack.md`) — reloads only the named modules, still
respecting the same reverse-disable/forward-enable ordering restricted to that subset. Installing a
pack that only ships items and quests reloads only the `items` and `quest` modules, not the whole
server. Unlike the no-arg `reloadModules()`, this overload is not currently exposed via
`/valmora reload` (only `PackManager` calls it) — see `ModuleManager.reloadModule(String)` for the
older single-module primitive this generalizes.

### 4.2 Reload Responsibilities

Each module must:

- **Reset internal state** in `onDisable()` — clear caches, unregister listeners via `HandlerList.unregisterAll(listener)`, cancel tasks.
- **Reload configuration** in `onEnable()` — re-read YAML files, repopulate registries.
- **Re-establish listeners** in `onEnable()` — register fresh event handlers.
- **Not assume prior state exists** — `onEnable()` must be fully idempotent.

### 4.3 Cross-Module State During Reload

- API accessors (`ValmoraAPI.getInstance()`) remain valid during reload.
- Registries are cleared and repopulated — modules must not cache registry lookups across reload boundaries.
- Player profiles are preserved in memory (managed by `ProfileManager`) and reloaded from disk.

---

## 5. Event Integration Points

### 5.1 Standard Bukkit Events Used

| Event                           | Primary Module   | Secondary Consumers                    |
|---------------------------------|------------------|----------------------------------------|
| `EntityDamageEvent`             | combat           | stat, skill, notify            |
| `EntityDeathEvent`              | mob              | combat, quest, stat, progression       |
| `PlayerInteractEvent`           | item             | skill, gui, quest, npc                 |
| `InventoryClickEvent`           | gui              | item, enchant, modifier, storage components (accessory/backpack) |
| `PlayerJoinEvent` / `QuitEvent` | profile          | hud, pet, quest, progression           |
| `ChunkLoadEvent`                | zone             | resource, mob, npc                     |

### 5.2 Custom Events

Custom application events fired by modules:

| Event                     | Fired By  | Listened By                               |
|---------------------------|-----------|-------------------------------------------|
| `ScriptExecuteEvent`      | script    | stat, combat, skill, item, mob            |
| `ZoneEnterEvent`          | zone      | stat, resource, time, hud         |
| `QuestObjectiveUpdateEvent` | quest   | notify, progression, ui                   |
| `CombatStartEvent`        | combat    | stat, skill, pet, hud, notify             |
| `ItemUseEvent`            | item      | skill, stat, enchant, quest               |
| `MobSpawnEvent`           | mob       | combat, stat, quest, zone                 |

---

## 6. Integration Risks & Mitigations

### 6.1 Circular Dependency Risk

**Risk:** A module references a module later in the registration order, creating a cycle.

**Mitigation:**
- Module registration order is enforced in `Valmora.java`.
- Code reviews must verify no upward API calls.
- If `ProfileManager` needs data from `QuestManager` (which loads later), use event-driven communication instead of direct calls.

### 6.2 Listener Leak After Reload

**Risk:** Event listeners registered in `onEnable()` are not unregistered in `onDisable()`, causing duplicate handling.

**Mitigation:**
- Every module stores its listener as a field and calls `HandlerList.unregisterAll(listener)` in `onDisable()`.
- See AGENTS.md §7.1 for the mandatory pattern.

### 6.3 Stale Registry References

**Risk:** A module caches a registry lookup result (e.g., a `StatTemplate`) and continues using it after reload, even though the registry was cleared.

**Mitigation:**
- Always retrieve objects through the registry at use-time, never cache them as fields.
- Registries are cleared in `onDisable()`.

### 6.4 Profile State During Reload

**Risk:** A player logs out during reload; their profile data may be inconsistent.

**Mitigation:**
- `ProfileManager` serializes profiles to disk on `PlayerQuitEvent`.
- On reload, all profiles are unloaded and re-read from disk on next login.

### 6.5 Script Variable Scope Across Modules

**Risk:** Scripts reference variables that are undefined in certain cross-module contexts.

**Mitigation:**
- `ExecutionContext` always provides a `VariableResolver` scoped to the current invocation.
- Modules adding new script variable prefixes must register them with `ScriptManager.registerVariableProvider()`.
