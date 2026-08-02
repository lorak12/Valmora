# Pet Module — Design & Code

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21
> **Package:** `org.nakii.valmora.module.pet`
> **Module ID:** `pets` | **Load order:** after `reforge`, before `slayer`
> **Status:** implemented — summon/unsummon, stat bonuses, XP/levels, milestone scripts, ability triggers, `pet` script variables ✅; **pet-item distribution, pet menu shortcut, and follow AI are NOT implemented** (see [Unfinished Things / TODOs](#unfinished-things--todos))

---

## Overview

The Pet module lets a player **summon a pet entity next to them** by right-clicking any
`ItemStack` in their main hand that carries the `valmora:pet_id` PDC tag. While summoned, the
pet contributes **stat modifiers** to the player's `StatManager` on every stat recalculation,
and the pet gains **experience** from kills and skill XP, which is stored **on the pet item
itself** (PDC). At exact level thresholds the pet fires **milestone script events**; at combat
moments it fires **ability script events** (`ON_KILL`, `ON_HIT`, `ON_DEFEND`).

**Key facts to understand the design:**

- **Pets are data-defined in YAML** (`plugins/Valmora/pets/*.yml`), loaded via `YamlLoader`
  into a `Map<String, PetDefinition>` registry. There is exactly one default file,
  `src/main/resources/pets/baby_wolf.yml`, which ships three pets (Baby Wolf, Baby Sheep,
  Ender Dragon).
- **The active pet is the item, not a player field.** Identity, level, and XP live in three
  PDC keys on the pet `ItemStack` (`valmora:pet_id`, `valmora:pet_level`,
  `valmora:pet_xp`). The pet follows its item around the player's inventory and is saved
  whenever the inventory/profile is saved — there is **no pet database table**.
- **Summon state is in-memory only.** `activePetSlot` (player UUID → inventory slot) and
  `activePetEntity` (player UUID → spawned entity) are plain maps cleared on disable. Pets
  are **not persisted across restarts** and are despawned on quit / module disable.
- **Pets do not follow.** The spawned entity has `setAI(false)` (`PetModule.java:134`) and no
  movement or follow logic exists. Pets are stationary decorative companions (see
  [Architecture & Key Classes](#architecture--key-classes) and
  [Unfinished Things / TODOs](#unfinished-things--todos)).
- **Pet stat bonuses are transient modifiers.** `applyPetStats` adds `baseStats + statsPerLevel
  × level` as modifiers on each `recalculateStats` pass; it relies on `StatManager` rebuilding
  `effectiveStats` from scratch each time, so modifiers never accumulate across recalcs
  (`StatManager.java:88-89`).
- **Module has no command and no permissions.** There is no `/pet` command in `plugin.yml`
  and no `pet.*` permission. Interaction is purely event-driven (right-click on a pet item),
  and the module is reached programmatically only through the concrete `Valmora` class
  (`Valmora.java:426`) — it is **not** on the `ValmoraAPI` interface.

---

## Code Structure

```
src/main/java/org/nakii/valmora/module/pet/
├── PetModule.java            # ReloadableModule — registry, summon/unsummon, XP, stats, YAML loading
├── PetListener.java          # Listener — right-click summon, kill/hit/defend abilities, XP-on-kill, quit cleanup
├── PetDefinition.java        # Immutable pet definition + stat computation + XP curve
├── PetAbilityDefinition.java # (trigger, CompiledEvent) pair for a pet ability
├── PetAbilityTrigger.java    # enum — ON_KILL / ON_HIT / ON_DEFEND
└── PetVariableProvider.java  # VariableProvider — exposes the $pet.*$ script namespace
```

This follows the project convention (`XModule.java` + `XListener.java`), but note there is
**no separate `XRegistry`/`XLoader`** class — the registry is a plain `HashMap` field inside
`PetModule` and loading/parsing are private methods of the module itself.

| File | Role |
| --- | --- |
| `PetModule.java` | Module lifecycle (`onEnable`/`onDisable`/`getId`), `definitions` registry, active-pet maps, `toggleSummon`/`unsummon`, `gainPetXp`/`fireMilestones`, `applyPetStats`, `loadDefinitions`/`parseDefinition`/`parseStatMap`. |
| `PetListener.java` | `PlayerInteractEvent` (summon toggle), `EntityDeathEvent` (+10 pet XP + `ON_KILL`), `EntityDamageByEntityEvent` (`ON_HIT` when attacking, `ON_DEFEND` when attacked), `SkillXpGainEvent` (+10% of skill XP), `PlayerQuitEvent` (despawn). |
| `PetDefinition.java` | Immutable holder for id/name/entityType/baseStats/statsPerLevel/abilities/milestones; `computeStats(level)`; static `xpForLevel(level)`. |
| `PetAbilityDefinition.java` | Pairs a `PetAbilityTrigger` with a compiled `CompiledEvent` list (from the script module's `EventParser`). |
| `PetAbilityTrigger.java` | The three supported ability triggers. |
| `PetVariableProvider.java` | Registers the `pet` namespace so `$pet.id$`, `$pet.name$`, `$pet.level$`, `$pet.xp$`, `$pet.max_xp$`, `$pet.active$` resolve in script expressions. |

**Wiring in `Valmora.java`:**

- Import: `Valmora.java:57`
- Field declaration: `Valmora.java:118` (`private PetModule petModule;`)
- Instantiation: `Valmora.java:179` (`this.petModule = new PetModule(this);`)
- Module registration: `Valmora.java:217`
  `moduleManager.registerModule(petModule);          // Depends on scriptModule + statModule`
  — registered **after** `reforgeModule` and **before** `slayerModule`. Both dependencies are
  registered much earlier (`scriptModule` at `Valmora.java:188`, `statModule` at
  `Valmora.java:190`), so the enable-time dependency is satisfied.
- Concrete getter: `Valmora.java:426` — `getPetModule()` (note: **not** on the `ValmoraAPI`
  interface — see [API Exposed](#api-exposed)).
- **No command is registered** for pets (per project rule `AGENTS.md` §6.3 there is also
  nothing to move into `Valmora.onEnable` — the feature is purely event-driven).

**PDC keys** (`src/main/java/org/nakii/valmora/util/Keys.java:33-35`, initialized at
`Keys.java:69-71`):

| Key | NamespacedKey | Type | Default when missing |
| --- | --- | --- | --- |
| `PET_ID_KEY` | `valmora:pet_id` | `STRING` | — |
| `PET_XP_KEY` | `valmora:pet_xp` | `DOUBLE` | `0.0` |
| `PET_LEVEL_KEY` | `valmora:pet_level` | `INTEGER` | `1` |

---

## Architecture & Key Classes

### 1. Pet definitions — `PetModule` registry + `PetDefinition`

`PetModule` keeps three state maps (`PetModule.java:28-31`):

```java
private final Map<String, PetDefinition> definitions = new HashMap<>();
private final Map<UUID, Integer> activePetSlot = new HashMap<>();
private final Map<UUID, Entity> activePetEntity = new HashMap<>();
```

- `definitions` is the **pet registry**, keyed case-sensitively on the YAML section id. The
  public lookup `getDefinition(String id)` lowercases (`PetModule.java:71`), so lookups are
  case-insensitive; the registry itself does **not** pre-lowercase keys at insert time.
- `activePetSlot` maps a player's UUID to the **inventory slot index** holding their active
  pet item.
- `activePetEntity` maps a player's UUID to the **spawned Bukkit `Entity`** (cast from
  `LivingEntity` at spawn, `PetModule.java:131`).

`PetDefinition` (`PetDefinition.java:9-38`) is an immutable holder:

| Field | Type | Purpose |
| --- | --- | --- |
| `id` | `String` | YAML section key (the pet's id, e.g. `baby_wolf`). |
| `name` | `String` | Display name; defaults to the id in the parser. |
| `entityType` | `EntityType` | Bukkit entity type to spawn; defaults to `WOLF`. |
| `baseStats` | `Map<String, Double>` | Flat stat bonuses granted at any level. Keys lowercased at parse time. |
| `statsPerLevel` | `Map<String, Double>` | Stat bonus per level (`value × level`, added on top of `baseStats`). |
| `abilities` | `List<PetAbilityDefinition>` | Trigger + compiled event list pairs. |
| `milestones` | `TreeMap<Integer, List<String>>` | Exact level → raw DSL event lines (compiled at execution time, not load time). |

**Stat computation** — `PetDefinition.computeStats(int level)` (`PetDefinition.java:40-46`):

```java
public Map<String, Double> computeStats(int level) {
    Map<String, Double> result = new java.util.HashMap<>(baseStats);
    for (Map.Entry<String, Double> entry : statsPerLevel.entrySet()) {
        result.merge(entry.getKey(), entry.getValue() * level, Double::sum);
    }
    return result;
}
```

Each stat is `base + perLevel × level`. A pet that has a stat in `base-stats` but not in
`stats-per-level` (or vice versa) simply contributes the half it defines. The resulting keys
are the `StatRegistry` ids (e.g. `strength`, `crit_chance`, `defense`, `health`, `damage`,
`crit_damage`, `ability_damage` — all present in `src/main/resources/stats/core.yml`).

**XP curve** — `PetDefinition.xpForLevel(int level)` (`PetDefinition.java:48-50`):

```java
public static long xpForLevel(int level) {
    return 100L * level * level;
}
```

XP required to advance **out of** level *L* is `100 × L²`. Progression is capped at level
**200** in `gainPetXp` (`PetModule.java:180`).

### 2. YAML loading & parsing — `PetModule.loadDefinitions` / `parseDefinition`

`loadDefinitions` (`PetModule.java:220-223`) uses the standard `YamlLoader`:

```java
YamlLoader<PetDefinition> loader = new YamlLoader<>(plugin, "pets", "Pet");
loader.load(this::parseDefinition, def -> definitions.put(def.getId(), def));
```

- Folder: `plugins/Valmora/pets/` (populated from the jar by `Valmora.saveAllResources` —
  `Valmora.java:477` includes `pets/` in the auto-save list).
- `YamlLoader.load` iterates every `.yml` file, and for each top-level key parses a
  `ConfigurationSection` (`infrastructure/config/YamlLoader.java:37-73`). Errors are logged by
  the loader with the relative path; the loader also prints
  `Successfully loaded N Pet.` (`YamlLoader.java:113-123`).

`parseDefinition(String id, ConfigurationSection section, String filePath)`
(`PetModule.java:225-262`):

| YAML key | Parse behavior |
| --- | --- |
| `name` | `section.getString("name", id)` — defaults to the section key. |
| `entity-type` | `EntityType.valueOf(raw.toUpperCase())`; a bad value is **silently ignored** and the default `EntityType.WOLF` is kept (`PetModule.java:228-232`). |
| `base-stats` | `parseStatMap(section.getConfigurationSection("base-stats"))` — every key lowercased, value via `getDouble` (`PetModule.java:264-271`). A missing section yields an empty map. |
| `stats-per-level` | Same `parseStatMap` treatment. |
| `abilities` | `section.getMapList("abilities")` — a list of maps. Each entry reads `trigger` (string) and `events` (list of DSL strings). Unknown triggers are **skipped** (`continue`, `PetModule.java:243`). Known triggers are compiled immediately via `plugin.getScriptModule().getEventParser().parseList(evList)` into a `PetAbilityDefinition` (`PetModule.java:238-247`). |
| `milestones` | `section.getConfigurationSection("milestones")` — keys parsed as `Integer`, values as `getStringList(key)` into a `TreeMap<Integer, List<String>>`. Non-numeric keys are skipped (`PetModule.java:249-256`). **Raw DSL strings** — compiled only when the milestone fires. |

Any `Exception` during parsing yields `LoadResult.failure(...)` (`PetModule.java:259-261`),
which `YamlLoader` logs as `[<path>] Failed to parse pet '<id>': <message>` and excludes from
the registry.

### 3. Summoning / despawning — `toggleSummon` / `unsummon`

`toggleSummon(Player player, int slot)` (`PetModule.java:107-146`) is the only entry point
into summoning. Flow:

```java
Integer currentSlot = activePetSlot.get(uid);
if (currentSlot != null && currentSlot != slot) {
    player.sendMessage(Formatter.format("<red>You already have a pet active. Unsummon it first."));
    return;
}
if (currentSlot != null) { unsummon(player); return; }
```

1. If the player already has **a different** pet active → refusal message, no action.
2. If the player already has **this** pet active → `unsummon(player)` and stop (toggle-off).
3. Otherwise the item in `slot` is read; it must have `valmora:pet_id` and the id must exist
   in `definitions` (`PetModule.java:121-127`), else the method silently returns.

Spawn (`PetModule.java:129-134`):

```java
Location loc = player.getLocation().add(1, 0, 0);
LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(loc, def.getEntityType());
entity.customName(Formatter.format("<gold>" + def.getName()));
entity.setCustomNameVisible(true);
entity.setAI(false);
```

- Spawns **1 block east** of the player (`getLocation().add(1, 0, 0)`) using the raw
  `EntityType` via the vanilla `spawnEntity(location, EntityType)` overload — **not** the
  consumer-based spawn from `AGENTS.md` §11.7, and with no cap on the default spawn-limit
  crowd. A spawned `ENDER_DRAGON` (Ender Dragon pet) is a full dragon entity.
- Name tag: `<gold>` + pet name, always visible.
- **`setAI(false)` — the pet never moves or follows.** There is no follow task, no teleport
  sync, and no leash/owner binding anywhere in the module. The pet is a static companion that
  stands where it spawned. See [Unfinished Things / TODOs](#unfinished-things--todos).
- The spawn is wrapped in `try/catch`; a failed spawn logs
  `Failed to spawn pet entity <TYPE>: <message>` (`PetModule.java:143-145`) and the maps are
  **not** updated.

On success the maps are populated (`PetModule.java:136-137`), the player is told
`<green>You summoned your <gold>NAME <green>(Lvl LEVEL)` (level read from the item,
`PetModule.java:139-141`), and `triggerStatRecalc(player)` is called so the pet's stat
bonuses apply immediately (`PetModule.java:142`).

`unsummon(Player player)` (`PetModule.java:148-155`) removes both map entries, removes the
entity if still valid, sends `<yellow>Pet unsummoned.`, and triggers a stat recalc.

### 4. Following — deliberately absent

**There is no following logic in the module.** The summoned entity has `setAI(false)`
(`PetModule.java:134`), no `EntityScheduler` task, no periodic teleport, and no
`PlayerMoveEvent` handler. Pets are static. If a player moves away, the pet stays behind.
Documented here explicitly because the topic is otherwise easy to assume — see
[Possible Improvements / Changes](#possible-improvements--changes).

### 5. Stat bonuses — `applyPetStats` and the `StatManager` hook

`applyPetStats(Player, StatManager)` (`PetModule.java:157-164`):

```java
public void applyPetStats(Player player, StatManager statManager) {
    PetDefinition def = getActivePetDefinition(player);
    if (def == null) return;
    int level = getActivePetLevel(player);
    for (Map.Entry<String, Double> entry : def.computeStats(level).entrySet()) {
        statManager.addModifier(entry.getKey(), entry.getValue());
    }
}
```

- The active pet is resolved by reading `valmora:pet_id` from the item in the stored slot
  (`getActivePetDefinition`, `PetModule.java:79-87`), so the bonus tracks the **item**, not a
  cached definition.
- `addModifier` (`module/stat/StatManager.java:65-68`) is a pure accumulator on
  `effectiveStats`.
- The integration point is `StatManager.recalculateStats` (`module/stat/StatManager.java:157-161`):

```java
// Pet stat bonuses (applied by PetModule if a pet is summoned)
var valmora = org.nakii.valmora.Valmora.getInstance();
if (valmora != null && valmora.getPetModule() != null) {
    valmora.getPetModule().applyPetStats(player, this);
}
```

Because `recalculateStats` clears `effectiveStats` and restores `baseStats` first
(`StatManager.java:88-89`), pet modifiers are **re-derived from scratch every recalculation** —
no dedup/removal bookkeeping is needed, and repeated recalc calls never stack the bonus. This
is also why `applyPetStats` is safe to call on every recalc (summon, level-up, item re-parse,
etc.). `StatManager` reaches the module through the concrete `Valmora.getInstance()` rather
than `ValmoraAPI`, because the pet module is not on the API interface.

`triggerStatRecalc` (`PetModule.java:213-218`) resolves the live session and active profile
and calls `recalculateStats`:

```java
ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
if (session != null && session.getActiveProfile() != null) {
    session.getActiveProfile().getStatManager().recalculateStats(player);
}
```

### 6. XP & leveling — `gainPetXp` / `fireMilestones`

`gainPetXp(Player, double amount)` (`PetModule.java:166-197`):

```java
int level = meta.getPersistentDataContainer().getOrDefault(Keys.PET_LEVEL_KEY, PersistentDataType.INTEGER, 1);
double xp = meta.getPersistentDataContainer().getOrDefault(Keys.PET_XP_KEY, PersistentDataType.DOUBLE, 0.0);
xp += amount;

while (level < 200) {
    long needed = PetDefinition.xpForLevel(level);
    if (xp >= needed) { xp -= needed; level++; fireMilestones(player, level, slot); ... }
    else break;
}
meta.getPersistentDataContainer().set(Keys.PET_LEVEL_KEY, ...level);
meta.getPersistentDataContainer().set(Keys.PET_XP_KEY, ...xp);
petItem.setItemMeta(meta);
if (level > initialLevel) triggerStatRecalc(player);
```

- Reads/writes level + XP **on the item in the stored slot** (`PetModule.java:167-170`).
- Applies `amount`, then loops level-ups while `level < 200`, consuming `100 × level²` XP per
  level-up (`PetDefinition.xpForLevel`). Multi-level ups in one grant work because the loop
  continues after `level++`.
- Each level-up fires `fireMilestones` and sends `<gold>✦ Pet leveled up to <yellow>Level N<gold>!`
  (`PetModule.java:186`).
- After writing back, a recalc is triggered **only if** the level actually changed
  (`PetModule.java:196`).

`fireMilestones(Player, int level, int slot)` (`PetModule.java:199-211`):

```java
List<String> events = def.getMilestones().get(level);
if (events == null || events.isEmpty()) return;
var ctx = new SimpleExecutionContext(player, player.getLocation(), new YamlConfiguration());
plugin.getScriptModule().getEventParser().parseList(events).execute(ctx);
```

- Milestones fire **only at the exact level** (exact key lookup on the `TreeMap`), so skipping
  a milestone level by multi-leveling past it skips that milestone too.
- Events are raw DSL strings compiled **at fire time** and executed against a fresh
  `SimpleExecutionContext` with the player as caster, the player's location, and empty params
  (`api/execution/SimpleExecutionContext.java:27-29`). The milestone DSL used by shipped pets
  is `stat_modify` (see `module/script/event/impl/StatModifyEventFactory.java:14-19` for the
  `stat_modify add|set|reset` grammar) and `notify chat`.

**XP sources** (all in `PetListener`):

| Source | Amount | Location |
| --- | --- | --- |
| Any mob kill by the player (killer present) | flat `10.0` | `PetListener.java:52` |
| Any `SkillXpGainEvent` (skill XP earned) | `event.getXp() * 0.1` (10%) | `PetListener.java:100` |

### 7. Ability triggers — `PetListener` event handlers

Abilities are compiled `CompiledEvent` lists gated on the `PetAbilityTrigger` enum
(`PetAbilityTrigger.java:3-6`):

```java
public enum PetAbilityTrigger { ON_KILL, ON_HIT, ON_DEFEND }
```

Firing points:

| Trigger | Event handler | Condition | Location |
| --- | --- | --- | --- |
| `ON_KILL` | `onKill(EntityDeathEvent)` | `getEntity().getKiller()` is a player with a pet active; fires **after** `gainPetXp(player, 10.0)` | `PetListener.java:46-63` |
| `ON_HIT` | `onAttack(EntityDamageByEntityEvent)` | `event.getDamager()` is a player with a pet active | `PetListener.java:66-78` |
| `ON_DEFEND` | `onDefend(EntityDamageByEntityEvent)` | `event.getEntity()` is a player with a pet active | `PetListener.java:81-93` |

Each handler builds a fresh `SimpleExecutionContext(player, player.getLocation(),
new YamlConfiguration())` and executes every matching ability's `CompiledEvent`
(`PetListener.java:57-61`, `:72-76`, `:87-91`). Note both damage handlers listen to the same
`EntityDamageByEntityEvent` and are disambiguated by role: `getDamager()` → `ON_HIT`,
`getEntity()` → `ON_DEFEND`.

The shipped abilities use `notify chat` (e.g. Baby Wolf's `<green>+5 HP from Baby Wolf!`,
`baby_wolf.yml:13`) and, for Ender Dragon, a temporary `stat_modify` buff with a delayed
revert via `run_script delay:200` (`baby_wolf.yml:62-65`).

### 8. Summon interaction & quit cleanup — `PetListener`

`onInteract(PlayerInteractEvent)` (`PetListener.java:28-43`) is the summon toggle:

```java
if (event.getHand() != EquipmentSlot.HAND) return;   // main hand only
if (!event.getAction().isRightClick()) return;       // right-click only
...
String petId = item.getItemMeta().getPersistentDataContainer()
        .get(Keys.PET_ID_KEY, PersistentDataType.STRING);
if (petId == null) return;
event.setCancelled(true);
module.toggleSummon(player, player.getInventory().getHeldItemSlot());
```

Guard chain: main hand (avoids the double-fire described in `AGENTS.md` §11.8), any
right-click action (`isRightClick()` covers both `RIGHT_CLICK_AIR` and `RIGHT_CLICK_BLOCK`),
non-air item with item meta, and the presence of `valmora:pet_id`. The event is cancelled only
when a real pet item is found, and the slot is taken from `getHeldItemSlot()` (the hotbar slot
index actually used to trigger, since `PlayerInteractEvent` on the main hand always reports the
held slot).

`onQuit(PlayerQuitEvent)` (`PetListener.java:104-111`):

```java
if (module.hasPetActive(player)) {
    var entity = module.getActivePetEntities().remove(player.getUniqueId());
    if (entity != null && entity.isValid()) entity.remove();
    // Don't call unsummon() — that sends a message to the offline player
}
```

Despawns the entity on quit without messaging. **Notable side effect:** only
`activePetEntity` is removed — `activePetSlot` is **left stale** (see
[Unfinished Things / TODOs](#unfinished-things--todos)).

### 9. Script variables — `PetVariableProvider`

Registered in `onEnable` via `plugin.getScriptModule().registerProvider(new PetVariableProvider(this))`
(`PetModule.java:48`; the API is `module/script/ScriptModule.java:73-75`). Namespace:
`"pet"` (`PetVariableProvider.java:16`). Resolved against `context.getPlayerCaster()`
(`PetVariableProvider.java:21-22`); without a player caster, every variable resolves to `null`.

| Variable | Resolution | Location |
| --- | --- | --- |
| `$pet.id$` | Active pet's definition id, or `"none"` | `PetVariableProvider.java:25-28` |
| `$pet.name$` | Active pet's display name, or `"None"` | `PetVariableProvider.java:29-32` |
| `$pet.level$` | `module.getActivePetLevel(player)` (default 1) | `PetVariableProvider.java:33` |
| `$pet.xp$` | `module.getActivePetXp(player)` (default 0.0) | `PetVariableProvider.java:34` |
| `$pet.max_xp$` | `PetDefinition.xpForLevel(currentLevel)` — XP needed for the next level | `PetVariableProvider.java:35` |
| `$pet.active$` | `module.hasPetActive(player)` (boolean) | `PetVariableProvider.java:36` |

All values are derived from the item PDC / maps, so they are always consistent with what
`gainPetXp` writes.

### 10. Lifecycle — `onEnable` / `onDisable`

`onEnable()` (`PetModule.java:40-49`) is idempotent: clears all three maps, reloads
definitions, creates and registers a fresh `PetListener`, and registers the
`PetVariableProvider`. `onDisable()` (`PetModule.java:52-63`) removes every active pet entity,
unregisters the listener via `HandlerList.unregisterAll(listener)` (mandatory cleanup per
`AGENTS.md` §6.2 — without it hot reloads accumulate duplicate handlers), and clears the maps.

---

## Configuration (YAML)

**Location:** `plugins/Valmora/pets/*.yml` (generated from
`src/main/resources/pets/baby_wolf.yml` by `Valmora.saveAllResources`, `Valmora.java:477`).

**Structure:** the file is a map of pet sections. Each section key is the **pet id**; the
module registers it verbatim and looks it up lowercased. All options are optional except a
meaningful `name`/`entity-type` — every key has a default (see below).

### Per-pet option reference

| Key | Type | Default | Explanation |
| --- | --- | --- | --- |
| `name` | `String` | the pet id | Display name. Used for the summon name tag (`<gold>` + name), the summon message, and `$pet.name$`. MiniMessage codes are supported (the shipped pets use plain names). |
| `entity-type` | `String` | `WOLF` | Bukkit `EntityType` name (case-insensitive, uppercased at parse). Spawned next to the player on summon. Invalid values are silently ignored and fall back to `WOLF`. |
| `base-stats` | map of `String → Double` | empty | Flat stat bonuses granted at every level. Keys are `StatRegistry` ids (lowercased). |
| `stats-per-level` | map of `String → Double` | empty | Per-level stat bonus; contributes `value × level` in addition to `base-stats`. |
| `abilities` | list of maps | empty | Pet abilities. Each entry: `trigger` (one of `ON_KILL`, `ON_HIT`, `ON_DEFEND`) + `events` (list of script DSL strings). Unknown triggers are skipped. |
| `milestones` | map of `String → list of String` | empty | Exact-level triggers. Keys are integer levels; values are DSL event lists executed once when the pet **hits** that level. Non-integer keys are skipped. |

**Hardcoded values that cannot be configured:**

| Hardcoded value | Location |
| --- | --- |
| XP per level-up: `100 × level²` | `PetDefinition.java:49` |
| Level cap: `200` | `PetModule.java:180` |
| XP per mob kill: `10.0` | `PetListener.java:52` |
| XP share of skill XP: `10%` (`× 0.1`) | `PetListener.java:100` |
| Spawn offset: 1 block east | `PetModule.java:129` |
| Name tag color: `<gold>` | `PetModule.java:132` |
| `setAI(false)` (pets never move) | `PetModule.java:134` |
| Summon/level-up/unsummon/refusal messages | `PetModule.java:112, :140-141, :153, :186` |
| Variable namespace `pet` | `PetVariableProvider.java:16` |

### Shipped pets — `src/main/resources/pets/baby_wolf.yml`

#### `baby_wolf` — "Baby Wolf"

| Option | Value |
| --- | --- |
| `entity-type` | `WOLF` |
| `base-stats` | `strength: 5`, `crit_chance: 2` |
| `stats-per-level` | `strength: 0.5`, `crit_chance: 0.1` |
| `abilities` | `ON_KILL` → `notify chat <green>+5 HP from Baby Wolf!`; `ON_DEFEND` → `notify chat <gray>Baby Wolf growls!` |
| `milestones` | `25` → `stat_modify add crit_chance 5` + notify; `50` → `stat_modify add strength 15` + notify; `100` → `stat_modify add strength 25`, `stat_modify add crit_damage 20` + notify |

#### `baby_sheep` — "Baby Sheep"

| Option | Value |
| --- | --- |
| `entity-type` | `SHEEP` |
| `base-stats` | `defense: 8`, `health: 15` |
| `stats-per-level` | `defense: 0.4`, `health: 0.8` |
| `abilities` | `ON_DEFEND` → `notify chat <green>Baby Sheep shields you!` |
| `milestones` | `25` → `stat_modify add defense 10` + notify; `50` → `stat_modify add health 30` + notify |

#### `ender_dragon_pet` — "Ender Dragon"

| Option | Value |
| --- | --- |
| `entity-type` | `ENDER_DRAGON` |
| `base-stats` | `strength: 50`, `crit_damage: 30`, `damage: 20` |
| `stats-per-level` | `strength: 2.0`, `crit_damage: 1.0`, `damage: 1.0` |
| `abilities` | `ON_KILL` → `stat_modify add strength 2`, `run_script delay:200 stat_modify add strength -2` (temporary +2 Strength buff for ~10s on kill) |
| `milestones` | `50` → `stat_modify add strength 50` + notify; `100` → `stat_modify add ability_damage 100` + notify |

**Note:** at level 200 (cap) the Ender Dragon's permanent stats would be `strength 450`,
`crit_damage 230`, `damage 220` (`base + perLevel × 200`). The milestone bonuses are applied
**on top of** `computeStats` because `stat_modify add` writes to the player's **base** stats
(`StatModifyEventFactory.java:45` → `StatManager.addStat`), whereas pet passives are
modifiers — so milestone grants survive even after the pet is unsummoned.

---

## Data Model / Persistence

### In-memory state

| Map | Type | Lifecycle |
| --- | --- | --- |
| `definitions` | `Map<String, PetDefinition>` | Reloaded every `onEnable`; cleared on disable/reload. |
| `activePetSlot` | `Map<UUID, Integer>` | Populated on summon, cleared on unsummon / module disable. **Not** cleared on player quit (see TODOs). |
| `activePetEntity` | `Map<UUID, Entity>` | Populated on summon, removed on unsummon / quit / module disable. |

### Persistent state — on the item, via PDC

There is **no pet table and no profile field**. The pet's persistent identity and progress
travel with the `ItemStack` that carries the PDC keys `valmora:pet_id`, `valmora:pet_level`,
`valmora:pet_xp` (`Keys.java:69-71`):

- **Identity** (`pet_id`, `STRING`): which `PetDefinition` this item summons. Set by whatever
  created the item — the module itself **never writes `pet_id`** (see
  [Unfinished Things / TODOs](#unfinished-things--todos)).
- **Level** (`pet_level`, `INTEGER`): default 1; written by `gainPetXp` (`PetModule.java:192`).
- **XP** (`pet_xp`, `DOUBLE`): default 0.0; written by `gainPetXp` (`PetModule.java:193`).

Because level/XP live on the item, persistence is inherited from the normal **item /
inventory save pipeline** (`ValmoraProfile` inventory → `SQLDataStore`), not from this module.
A pet item sitting in a profile's inventory is saved/loaded exactly like any other item, so
level and XP survive restarts **as long as the item survives**. If the item is dropped,
destroyed, or given away, the progress goes with it.

### What is NOT persisted

- The **summoned entity** — despawned on quit (`PetListener.java:104-111`) and on module
  disable (`PetModule.java:52-55`). A player who logs back in must re-summon.
- The **active slot binding** — it is runtime-only; after quit it is stale but the entity map
  is empty, so the pet is not summoned (see TODOs).

### Test coverage

There are **no unit tests** for the pet module in `src/test` (verified — no `*Pet*` test
files exist). The module's behavior depends on Bukkit `Player`/`ItemStack`/`Entity` and the
script `EventParser`, so no mock-based test currently covers it.

---

## API Exposed

| Accessor / method | Where | Visibility |
| --- | --- | --- |
| `PetModule getPetModule()` | `Valmora.java:426` | Concrete `Valmora` class only |
| `getDefinition(String id)` | `PetModule.java:71` | Public — case-insensitive lookup |
| `getDefinitions()` | `PetModule.java:72` | Public — collection of all loaded pets |
| `getActivePetEntities()` | `PetModule.java:73` | Public — live `Map<UUID, Entity>` |
| `hasPetActive(Player)` | `PetModule.java:75` | Public — entity-map presence check |
| `getActivePetDefinition(Player)` | `PetModule.java:79` | Public — reads item PDC, resolves def |
| `getActivePetLevel(Player)` | `PetModule.java:89` | Public — default `1` |
| `getActivePetXp(Player)` | `PetModule.java:98` | Public — default `0.0` |
| `toggleSummon(Player, int slot)` | `PetModule.java:107` | Public |
| `unsummon(Player)` | `PetModule.java:148` | Public |
| `applyPetStats(Player, StatManager)` | `PetModule.java:157` | Public — called by `StatManager` |
| `gainPetXp(Player, double)` | `PetModule.java:166` | Public — grants XP and levels the item |
| Script variables `$pet.id$` … `$pet.active$` | `PetVariableProvider.java:24-38` | Registered in script module |
| `Keys.PET_ID_KEY / PET_XP_KEY / PET_LEVEL_KEY` | `Keys.java:33-35` | Public `NamespacedKey`s |

**Important:** the Pet module is **not** on the `ValmoraAPI` interface. `ValmoraAPI.java` has
no `getPetModule()`. The one cross-module consumer — `StatManager` — reaches it through the
concrete `Valmora.getInstance()` (`StatManager.java:158-160`). External modules can also use
`ValmoraAPI.getInstance().getModuleManager().getModule("pets")` and cast.

---

## Dependencies & Consumers

**Dependencies (consumed by the pet module):**

| Dependency | How used | Location |
| --- | --- | --- |
| `ScriptModule` | `getEventParser().parseList(...)` for abilities and milestones; `registerProvider(...)` for `$pet.*$` | `PetModule.java:48, :210, :237, :246` |
| `StatManager` | Target of `applyPetStats` modifiers (`addModifier`) | `PetModule.java:162` |
| `PlayerManager` | Resolve live session → active profile for stat recalc | `PetModule.java:214` |
| `ValmoraPlayer` / `ValmoraProfile` | `getSession(...).getActiveProfile().getStatManager()` | `PetModule.java:214-217` |
| `Keys` | The three pet PDC keys | `PetModule.java:84, :94, :103, :123, :173-193, :202` |
| `Formatter` | MiniMessage formatting of all pet messages/names | `PetModule.java:112, :132, :140-141, :153, :186` |
| `YamlLoader` / `LoadResult` | Definition loading | `PetModule.java:221-222, :258-260` |
| `SimpleExecutionContext` | Milestone + ability execution contexts | `PetModule.java:208`, `PetListener.java:57, :72, :87` |
| `VariableProvider` | Script variable registration contract | `PetVariableProvider.java:7` |
| `SkillXpGainEvent` | 10% skill-XP share | `PetListener.java:96-101` |

**Consumers:**

| Consumer | How it uses the module | Location |
| --- | --- | --- |
| `StatManager` | Applies pet stat modifiers during every stat recalculation | `module/stat/StatManager.java:157-161` |
| `Valmora.onEnable` | Registers the module in order | `Valmora.java:217` |
| `Valmora` (concrete) | Exposes `getPetModule()` | `Valmora.java:426` |

**Load order:** registered at `Valmora.java:217` with the comment
`Depends on scriptModule + statModule`. `scriptModule` (`:188`) and `statModule` (`:190`) are
registered first, so both dependencies are live when `onEnable` runs. No other module
registers after it except `slayer`, `accessory`, `backpack`, `quiver`, and `progression` —
none of which reference the pet module.

**Related but not a consumer:** the `Taming` skill
(`src/main/resources/skills/taming.yml:22-25`) grants `player.var.pet_luck` per level, and the
`pet_luck` stat exists (`src/main/resources/stats/core.yml:116-121`, "Increases the chance of
finding rare pets"), but **nothing in the pet module reads `pet_luck`** — it is currently
dormant flavor. Similarly, `zombie.yml` collection reward text says "Zombie Pet unlocked"
(`src/main/resources/collections/combat/zombie.yml:30`) but that is descriptive-only.

---

## Unfinished Things / TODOs

1. **No pet-item distribution.** Nothing in the codebase creates an item carrying
   `valmora:pet_id` — the module only *reads* the key. `ItemFactory`
   (`module/item/ItemFactory.java:31-49`) writes `ITEM_ID_KEY`, `RARITY_KEY`, etc., but never
   `PET_ID_KEY`, and no item definition in `src/main/resources/items/*.yml` sets it. Admins
   must currently tag an item by hand. `docs/todo.md:73` lists "pets module and shortcut in
   the menu" as an outstanding menu feature.

2. **No follow AI.** `setAI(false)` (`PetModule.java:134`) means pets are static statues;
   there is no follow task, owner binding, or movement sync. Spawned via the raw
   `spawnEntity` overload (`PetModule.java:131`) rather than the consumer pattern from
   `AGENTS.md` §11.7.

3. **Stale `activePetSlot` after quit.** `onQuit` removes the entity but **not** the slot
   entry (`PetListener.java:107-108`). After a re-login, `hasPetActive` returns false (entity
   map is empty), but the stale slot means the player's *next* right-click on that pet item
   triggers `unsummon` (toggle-off) instead of summoning (`PetModule.java:116-119`), and
   right-clicking a *different* pet is refused as "You already have a pet active"
   (`PetModule.java:111-114`). A restart or `/valmora reload` clears it. The stale entry should
   be removed on quit.

4. **Only one pet active, slot-bound.** `toggleSummon` allows a single active pet keyed to a
   specific inventory slot (`PetModule.java:111-119`). Moving the pet item to another slot
   breaks the binding (the maps still reference the old index), and there is no "summon from
   anywhere / pet menu" UX.

5. **Leveling can skip milestones.** Milestones fire only on the *exact* level
   (`def.getMilestones().get(level)`, `PetModule.java:206`). A large XP grant that crosses
   multiple milestone levels in one call (`PetModule.java:180-190` *does* call
   `fireMilestones` per level, so this is mostly covered — but an external consumer calling
   `gainPetXp` is the only path) — effectively this is handled; the remaining gap is that a
   milestone skipped by a *different* XP source (none today) would be lost.

6. **Milestone grants are permanent base-stat changes.** `stat_modify add` writes to base
   stats (`StatModifyEventFactory.java:45`), which persist after the pet is unsummoned or
   re-summoned, unlike the passive `applyPetStats` modifiers. Whether milestone stat grants
   should be reverted on unsummon is an unrecorded design decision.

7. **`pet_luck` is unused.** The stat and Taming integration exist
   (`stats/core.yml:116-121`, `skills/taming.yml:24`) but no pet-drop/loot code consumes it.

---

## Possible Improvements / Changes

1. **Implement follow AI.** Add a Paper `EntityScheduler`-based task
   (`AGENTS.md` §11.13) or teleport-sync on `PlayerMoveEvent` so pets trail their owner; use
   the Paper `Pathfinder` API (`AGENTS.md` §11.2) if movement is desired instead of teleport.
   Pets currently stand still after summon.

2. **Expose on `ValmoraAPI`.** Add `getPetModule()` to the interface (as
   `MODULE_DEVELOPMENT.md` §8 recommends) instead of requiring the concrete
   `Valmora.getInstance()` pattern (`StatManager.java:158-160`, `Valmora.java:426`).

3. **Distribute pet items properly.** Wire pet items into the item system (a `pet_id` item
   property or a `/pet give` admin command) so the "Zombie Pet unlocked" collection reward
   and the menu shortcut in `docs/todo.md:73` can actually grant a working pet item.

4. **Fix quit stale-state.** Remove the `activePetSlot` entry in `onQuit`
   (`PetListener.java:104-111`) alongside the entity removal, so re-login + right-click
   summons instead of toggling off.

5. **Configurable XP/level curve.** `xpForLevel` (`PetDefinition.java:49`), the level cap
   (`PetModule.java:180`), per-kill XP (`PetListener.java:52`), and the skill-XP share
   (`PetListener.java:100`) are hardcoded. Moving them to `config.yml` or a pet-level section
   would let servers tune progression.

6. **Decouple the active binding from the inventory slot.** Storing the pet item reference
   (or its UUID) instead of an index would survive item moves and support "summon active pet"
   commands/menus. Alternatively track the slot and re-locate it when the inventory changes.

7. **Use the consumer-based spawn.** Replace the raw `spawnEntity` call
   (`PetModule.java:131`) with `world.spawn(loc, EntityType, consumer)` so pet setup happens
   in one tick and spawning an `ENDER_DRAGON` is handled as a proper boss-capable spawn.

8. **Reuse `getDefinitions()`/`getActivePetEntities()`.** Both accessors exist
   (`PetModule.java:72-73`) but are currently unused outside the module — a pets menu/GUI
   could consume them for the shortcut in `docs/todo.md:73`.
