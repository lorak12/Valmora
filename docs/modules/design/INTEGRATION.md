# Cross-Module Integration

> **Purpose:** This document describes how all Valmora modules interrelate at the code level. It covers dependency direction, shared APIs, data flows, reload semantics, and integration risks. Refer to this before adding or modifying cross-module interactions.
>
> Related: [MODULE_DEVELOPMENT.md](../../MODULE_DEVELOPMENT.md), [VALMORA_DOCUMENTATION.md](../../VALMORA_DOCUMENTATION.md)

---

## 1. Dependency Direction

Module registration order (see `Valmora.java`) enforces a strict layering. Lower modules are available to higher ones; no upward references are permitted:

```
script → stat → profile → combat → item → mob → npc → quest
       → economy → skill → zone → resource → fishing
       → alchemy → enchant → recipe → gui → ui
       → time → collection → notify → pet → reforge
       → accessory → backpack → quiver → hud
       → slayer → calendar → progression
```

### 1.1 Dependency Matrix

The tables below list **Uses** (modules this module depends on) and **Consumers** (modules that depend on this module).

#### 1.1.1 Core Modules

| Module  | Uses                                  | Consumers                              |
|---------|---------------------------------------|----------------------------------------|
| script  | (none)                                | stat, profile, combat, item, mob, skill, alchemy, enchant, quest, npc |
| stat    | script                                | profile, combat, item, mob, skill, economy, accessory, reforge |
| profile | stat, script                          | combat, item, mob, npc, economy, skill |
| combat  | stat, profile, script, mob            | item, skill, enchant, slayer, progression |

#### 1.1.2 Item & Equipment

| Module     | Uses                              | Consumers                             |
|------------|-----------------------------------|---------------------------------------|
| item       | stat, profile, script, mob        | combat, enchant, skill, npc, quest, accessory, backpack |
| enchant    | item, stat, script                | combat, skill                         |
| reforge    | item, stat, script                | accessory                             |
| accessory  | item, stat, reforge, script       | combat, skill                         |
| backpack   | item, stat, script                | player UI (storage access)            |
| quiver     | item, stat, script                | combat (ammo resolution)              |

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
| slayer   | combat, stat, script, mob         | progression                           |
| alchemy  | item, stat, script                | skill                                 |

#### 1.1.5 World & Economy

| Module      | Uses                              | Consumers                             |
|-------------|-----------------------------------|---------------------------------------|
| economy     | stat, profile, script             | npc, quest                            |
| zone        | stat, profile, script             | time, resource, fishing, slayer       |
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
| `getCombatManager()`      | `CombatManager`           | item, skill, slayer                        |
| `getQuestManager()`       | `QuestManager`            | progression, notify, npc                   |
| `getEconomyManager()`     | `EconomyManager`          | npc, quest                                 |
| `getGUIManager()`         | `GUIManager`              | quest, skill, npc                          |
| `getZoneManager()`        | `ZoneManager`             | resource, fishing, slayer                |
| `getEnchantManager()`     | `EnchantManager`          | combat, skill                              |
| `getPetManager()`         | `PetManager`              | combat, stat, progression                  |
| `getHudManager()`         | `HudManager`              | ui, profile                                |
| `getNotifyManager()`      | `NotifyManager`           | quest, combat                              |
| `getCollectionManager()`  | `CollectionManager`       | progression                                |
| `getSlayerManager()`      | `SlayerManager`           | progression                                |
| `getCalendarManager()`    | `CalendarManager`         | progression                                |
| `getProgressionManager()` | `ProgressionManager`      | (terminal consumer)                        |

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
6. SlayerManager grants slayer XP if applicable
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
6. EnchantManager applies enchant effects
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
8. SlayerManager tracks zone-specific slayer tasks
9. HudManager displays zone info
```

---

## 4. Reload Semantics

### 4.1 Reload Order

`ModuleManager.reloadModules()` (invoked via `/valmora reload`) performs:

1. **Disable phase** — All modules' `onDisable()` called in **reverse registration order**:
   ```
   progression → calendar → slayer → hud → quiver → backpack → accessory
   → reforge → pet → notify → collection → time → ui → gui
   → recipe → enchant → alchemy → fishing → resource → zone
   → economy → skill → quest → npc → mob → item → combat
   → profile → stat → script
   ```

2. **Re-enable phase** — `ModuleManager.enableModules()` called in **forward registration order** (as listed in Section 1).

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
| `EntityDamageEvent`             | combat           | stat, skill, slayer, notify            |
| `EntityDeathEvent`              | mob              | combat, quest, stat, progression       |
| `PlayerInteractEvent`           | item             | skill, gui, quest, npc                 |
| `InventoryClickEvent`           | gui              | item, enchant, reforge, backpack       |
| `PlayerJoinEvent` / `QuitEvent` | profile          | hud, pet, quest, progression           |
| `ChunkLoadEvent`                | zone             | resource, mob, npc                     |

### 5.2 Custom Events

Custom application events fired by modules:

| Event                     | Fired By  | Listened By                               |
|---------------------------|-----------|-------------------------------------------|
| `ScriptExecuteEvent`      | script    | stat, combat, skill, item, mob            |
| `ZoneEnterEvent`          | zone      | stat, resource, slayer, time, hud         |
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
