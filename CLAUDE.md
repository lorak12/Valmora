# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> This is the **primary orientation document** for any AI agent working on this codebase.
> Read it fully before touching any code. Then consult the referenced docs for deeper detail.

---

## Table of Contents

1. [Project Identity](#1-project-identity)
2. [Mandatory Reading](#2-mandatory-reading)
3. [Repository Layout](#3-repository-layout)
4. [Build & Dev Workflow](#4-build--dev-workflow)
5. [Architecture Overview](#5-architecture-overview)
6. [Module System — How to Work With It](#6-module-system--how-to-work-with-it)
7. [Critical Patterns You Must Follow](#7-critical-patterns-you-must-follow)
8. [GUI & Machine System](#8-gui--machine-system)
9. [Recipe Engine](#9-recipe-engine)
10. [Script DSL Reference](#10-script-dsl-reference)
11. [Database Layer](#11-database-layer)
12. [Testing](#12-testing)
13. [Common Mistakes to Avoid](#13-common-mistakes-to-avoid)
14. [Paper API Hard Topics](#14-paper-api-hard-topics)

---

## 1. Project Identity

| Property             | Value                                           |
| -------------------- | ----------------------------------------------- |
| Plugin name          | **Valmora**                                     |
| Group / package root | `org.nakii.valmora`                             |
| Version              | `0.1`                                           |
| Target server        | **Paper 1.21.11** (not Spigot, not CraftBukkit) |
| Java version         | **21** (required)                               |
| Build tool           | Gradle with Shadow + run-paper plugins          |

This is a **modular RPG plugin**. Every major feature lives in its own `ReloadableModule`. The plugin is not a monolith — treat it like a collection of mini-plugins wired together through a shared API.

---

## 2. Mandatory Reading

Before implementing any feature, open and read these two files:

- **`docs/MODULE_DEVELOPMENT.md`** — complete lifecycle guide for creating, registering, enabling, and hot-reloading modules.
- **`docs/VALMORA_DOCUMENTATION.md`** — 1600+ lines covering every subsystem: items, mobs, skills, abilities, GUI, scripting, execution context, and YAML schemas.

These two files are ground truth. If this CLAUDE.md ever conflicts with them, the specific doc wins.

---

## 3. Repository Layout

```
valmora/
├── build.gradle                    # Gradle build — deps, Shadow, run-paper
├── src/
│   ├── main/
│   │   ├── java/org/nakii/valmora/
│   │   │   ├── Valmora.java        # Plugin entry point — wires all modules
│   │   │   ├── api/                # Public interfaces (ValmoraAPI, ReloadableModule, etc.)
│   │   │   ├── module/             # One sub-package per module
│   │   │   │   ├── gui/            # GUI/machine system (see §8)
│   │   │   │   ├── item/
│   │   │   │   ├── mob/
│   │   │   │   ├── recipe/         # Recipe engine (see §9)
│   │   │   │   ├── script/         # Script DSL (see §10)
│   │   │   │   ├── skill/
│   │   │   │   ├── time/           # RPG calendar (TimeModule, TimeManager, TimeSnapshot)
│   │   │   │   ├── combat/
│   │   │   │   └── ui/             # Scoreboard, ActionBar, Chat
│   │   │   └── infrastructure/
│   │   │       └── config/         # YamlLoader lives here
│   │   └── resources/
│   │       ├── plugin.yml
│   │       ├── config.yml
│   │       ├── items/*.yml
│   │       ├── mobs/*.yml
│   │       ├── guis/*.yml
│   │       ├── recipes/*.yml
│   │       └── skills/*.yml
│   └── test/
│       └── java/org/nakii/valmora/
├── docs/
│   ├── MODULE_DEVELOPMENT.md       ← READ THIS
│   └── VALMORA_DOCUMENTATION.md    ← READ THIS
└── plugins/Valmora/                # Runtime data (generated, not committed)
    ├── config.yml
    ├── database.db
    ├── time.yml                    # Persisted day-offset for TimeModule
    └── ...
```

The `module/` sub-packages follow a consistent internal structure: `XModule.java`, `XListener.java`, `XRegistry.java`, `XLoader.java`. Keep new modules consistent with this convention.

---

## 4. Build & Dev Workflow

```bash
# Compile and produce the shaded JAR
./gradlew build

# Run unit tests
./gradlew test

# Start a Paper 1.21.11 dev server with the plugin auto-loaded
./gradlew runServer
```

- `build` depends on `shadowJar` — the output is always the fat JAR.
- Java 21 is required. The build will fail on earlier JDKs.
- The dev server from `runServer` uses run-paper to download Paper automatically on first run.

**Hot reload** (while server is running): `/valmora reload` — requires the `valmora.admin` permission. This calls `ModuleManager.reloadModules()`, which disables all modules in reverse order and re-enables them in forward order.

---

## 5. Architecture Overview

```
Valmora.onEnable()
    │
    ├── 1. ModuleManager created
    ├── 2. All modules instantiated (as fields in Valmora.java)
    ├── 3. All modules registered (moduleManager.registerModule)
    ├── 4. All modules enabled   (moduleManager.enableModules)
    │       └─ Each module.onEnable() runs in registration order
    └── 5. Commands registered   ← NEVER register commands inside a module
```

**Module registration order** (must be preserved):

```
script → time → stat → player → ui → ability → item → mob → skill → combat → gui → recipe → enchant
```

Later modules may depend on earlier ones (e.g. `skill` can access `stat`). Earlier modules must not depend on later ones. If you add a new module, insert it at the correct position — document the reason in `Valmora.java`.

**Accessing modules at runtime:**

```java
ValmoraAPI api = ValmoraAPI.getInstance();
ItemManager items = api.getItemManager();
SkillManager skills = api.getSkillManager();
TimeManager time = api.getTimeManager();
// etc. — see ValmoraAPI interface for full list
```

---

## 6. Module System — How to Work With It

> Full details in `docs/MODULE_DEVELOPMENT.md`. This section is a working summary.

### 6.1 The `ReloadableModule` Contract

Every module implements three methods:

```java
void onEnable();   // Load configs, register listeners, start tasks
void onDisable();  // Unregister listeners, cancel tasks, clear caches
String getId();    // Unique lowercase ID, e.g. "items", "combat"
```

`onEnable()` must be **idempotent** — it can be called more than once (hot reload). Always fully initialize state inside `onEnable()`, never in the constructor.

### 6.2 Listener Registration and Cleanup

Register listeners in `onEnable()`, unregister in `onDisable()`. **Failure to unregister causes duplicate event handling after reload.**

```java
// onEnable
this.listener = new MyListener(plugin);
plugin.getServer().getPluginManager().registerEvents(listener, plugin);

// onDisable  — MANDATORY
HandlerList.unregisterAll(listener);
this.listener = null;
```

### 6.3 Never Register Commands in a Module

Commands are registered **after** all modules are enabled, directly in `Valmora.onEnable()`. If you need a new command, add it there — do not call `getCommand(...).setExecutor(...)` inside any module's `onEnable()`.

### 6.4 Accessing Other Modules from Within a Module

Use `ValmoraAPI.getInstance()`. Do not hold direct references to sibling module instances; go through the API. This keeps modules decoupled and reload-safe.

---

## 7. Critical Patterns You Must Follow

### 7.1 YamlLoader

Generic config loader at `org.nakii.valmora.infrastructure.config.YamlLoader`. Use it for all YAML loading — do not write custom `FileConfiguration` boilerplate.

```java
YamlLoader<GuiDefinition> loader = new YamlLoader<>(plugin, "guis", "GUIs");
loader.load(parser::parse, def -> registry.put(def.getId(), def));
```

### 7.2 Registry

`Registry<T>` stores keys **case-insensitively** (stored lowercase). Always retrieve with `.get(key.toLowerCase())` if you bypass the registry helper. Registries are populated during `onEnable()` and cleared in `onDisable()`.

### 7.3 ExecutionContext

Passed to all mechanics, scripting, and ability systems. Always access entities and variables through it:

```java
LivingEntity caster = context.getCaster();
Optional<LivingEntity> target = context.getTarget();
VariableResolver vars = context.getVariableResolver();
ConfigurationSection params = context.getParams();
```

Never store `ExecutionContext` beyond the scope of a single mechanic invocation. It is not thread-safe.

### 7.4 Async Operations

Database calls use HikariCP with a dedicated executor — they are async. Do **not** touch Bukkit API (entities, worlds, blocks) from async context. Schedule any Bukkit callbacks back to the main thread:

```java
// After async DB work:
plugin.getServer().getScheduler().runTask(plugin, () -> {
    // Safe Bukkit API access here
});
```

### 7.5 MiniMessage for Text

All display text uses **MiniMessage** (Adventure). Never use `ChatColor` or `§` codes. Use `Formatter.format(String)` from `org.nakii.valmora.util.Formatter` — it wraps MiniMessage and suppresses italic by default.

```java
// Correct — via Formatter
Component msg = Formatter.format("<red>You took <bold>10</bold> damage!");
player.sendMessage(msg);

// Also correct — raw MiniMessage
Component msg = MiniMessage.miniMessage().deserialize("<red>...");

// Wrong — do not do this
player.sendMessage(ChatColor.RED + "You took 10 damage!"); // ❌
```

---

## 8. GUI & Machine System

The GUI module (`module/gui/`) implements a data-driven inventory UI system driven by YAML definitions in `resources/guis/`. Each YAML file defines one GUI.

### 8.1 Component Types

| Type | Behavior |
|------|----------|
| `INPUT` | Accepts player items; protected from accidental removal; snapshot captured before re-render |
| `OUTPUT` | Displays matched recipe result; players can take items; re-populated after render |
| `DISPLAY` | Static/read-only item; can have click `actions` keyed by `ClickType` enum name |
| `PAGINATED` | Iterates a list variable; renders one item per page element; used with `PREVIOUS_PAGE`/`NEXT_PAGE` |
| `PREVIOUS_PAGE` / `NEXT_PAGE` | Modifies `GuiSession.currentPage` and triggers a re-render |

### 8.2 Event Blocks

Each GUI definition supports four event blocks:

| Block | Trigger |
|-------|---------|
| `on-open` | Fires once when the player opens the GUI |
| `on-close` | Fires once when the player closes the GUI |
| `on-slot-update` | Fires when an INPUT slot changes (item placed or removed) |
| `on-update` | Fires every `update-interval` ticks on a repeating timer |

Event blocks contain `actions` and optional `fail-actions`. The first `condition` action short-circuits the remaining actions if the condition is false and jumps to `fail-actions`.

### 8.3 Input Snapshot Mechanism

Before re-rendering, `GuiSession.snapshotInputs()` copies INPUT slot contents into a map. This snapshot is available to variable resolvers during the render pass so that `$gui.input.<id>$` variables still resolve to the items even after the inventory slots are cleared for re-population.

**Variable access to GUI slots:**
- `$gui.input.<componentId>.id$` — item ID (Valmora item ID or vanilla material name)
- `$gui.input.<componentId>.amount$` — stack size
- `$gui.input.<componentId>.material$` — Bukkit material name
- `$prop.<key>$` — per-session property bag; writable via `variable set prop.key value`

### 8.4 gui_force_craft Event

The `gui_force_craft` script event (defined in `GuiForceCraftEventFactory`) is the unified craft trigger:

1. Takes a live snapshot of INPUT slots
2. Calls `RecipeEngine.craft()` — see §9
3. Places output into OUTPUT slots
4. Re-renders the GUI

Dupe protection: `GuiSession.craftingLocked` is set to `true` during the craft pipeline and cleared afterwards. A second `gui_force_craft` while locked is a no-op.

### 8.5 Machine Handler Extensibility

`DynamicMachineHandler` is an interface that allows fully custom craft logic for a named machine. Register via:

```java
recipeModule.getRecipeEngine().registerHandler("machine_id", new MyMachineHandler(plugin));
```

Handlers are checked before YAML recipes, so they can override or augment matching behaviour.

---

## 9. Recipe Engine

The `RecipeEngine` (`module/recipe/RecipeEngine.java`) runs a three-step match for every craft attempt:

1. **Dynamic handler** — check `DynamicMachineHandler` registered for this machine ID (custom logic)
2. **YAML recipes** — match against static recipes loaded from `resources/recipes/*.yml`
3. **Vanilla** — fall back to standard Bukkit recipes (used for crafting table passthrough)

### 9.1 Recipe Types

| Type | Input key format | Matching |
|------|-----------------|----------|
| `EXACT_SLOT` | String slot ID (matches INPUT component `id`) | Exact map equality |
| `SHAPED` | `"0"` – `"8"` (3×3 grid index, left→right, top→bottom) | Position matters |
| `SHAPELESS` | List of items (any order) | Bag matching; ignores slot |

### 9.2 YAML Recipe Format

```yaml
my_recipe_id:
  machine: alchemy          # must match GUI's machine: field
  type: SHAPELESS
  inputs:
    - item: NETHER_WART
      amount: 1
    - item: GLASS_BOTTLE
      amount: 1
  outputs:
    result:
      item: custom_item_id  # Valmora item ID or vanilla material
      amount: 1
  on-craft:
    - "sound player block.brewing_stand.brew"
```

### 9.3 Recipe Folder Structure

Recipes live in `resources/recipes/`. Sub-folders are supported for organisation:
- `recipes/crafting/` — crafting table recipes
- `recipes/alchemy/` — alchemy machine recipes
- `recipes/anvil/` — anvil machine recipes

---

## 10. Script DSL Reference

The scripting system (`module/script/`) provides a mini-language for YAML configs. It is used in skill rewards, GUI event blocks, and item abilities.

### 10.1 Event String Syntax

```
<eventName> <arg1> <arg2> ... [notify] [delay:<ticks>]
```

- `notify` — sends a confirmation message to the player caster
- `delay:<ticks>` — schedules the event N ticks in the future

**Built-in events:**

| Event | Syntax | Effect |
|-------|--------|--------|
| `give` | `give <Material>:<amount>` | Gives item to player |
| `variable` | `variable set\|add\|remove <path> <value>` | Reads/writes a variable |
| `tag` | `tag add\|remove <tagName>` | Adds/removes a profile tag |
| `sound` | `sound <player\|world> <sound.key>` | Plays a sound |
| `gui_force_craft` | `gui_force_craft` | Triggers craft pipeline in current GUI |

### 10.2 Condition Syntax

Conditions appear as event strings starting with `condition`:

```yaml
- "condition $player.stat.HEALTH$ > 50"
- "condition $gui.input.ingredient.id$ != null"
- "condition $time.season$ == Summer"
```

When a `condition` fails, execution jumps to `fail-actions` (if present) and skips remaining `actions`.

**Operators:** `==`, `!=`, `>`, `<`, `>=`, `<=`

### 10.3 Expression & Variable Syntax

Variables are embedded in strings with `$namespace.path.path...$`:

```
$player.name$           → Player's display name
$player.stat.HEALTH$    → Health stat value (double)
$player.skill.mining.level$ → Skill level (int)
$player.var.strength$   → Custom profile variable
$time.season$           → Current season name (String)
$time.hour$             → Current hour 0–23 (int)
$time.is_day$           → true/false (boolean)
$gui.input.<id>.id$     → Valmora item ID in INPUT slot
$prop.<key>$            → Per-GUI session variable
$param.<key>$           → Mechanic/ability parameter
$range.<min>.<max>$     → Random int in range
$system.time$           → System.currentTimeMillis()
$server.online$         → Online player count
```

Expressions support arithmetic: `$param.level$*10`, `$player.stat.HEALTH$ + 50`.

### 10.4 Adding New Events or Variables

**New event factory:**
```java
public class MyEvent implements EventFactory {
    @Override public String getName() { return "my_event"; }
    @Override public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> { /* impl */ };
    }
}
// Register in ScriptModule.onEnable():
registerEvent(new MyEvent());
```

**New variable provider:**
```java
public class MyProvider implements VariableProvider {
    @Override public String getNamespace() { return "mynamespace"; }
    @Override public Object resolve(String[] path, ExecutionContext context) { /* ... */ }
}
// Register in ScriptModule.onEnable():
registerProvider(new MyProvider());
```

---

## 11. Database Layer

- **Default:** SQLite (`plugins/Valmora/database.db`)
- **Optional:** MySQL via `config.yml` → `database.type: mysql`
- **Pool:** HikariCP 5.1.0
- All queries run through the async executor. Follow the async safety rule in §7.4.

When adding a new table or query, follow the existing DAO pattern in the `infrastructure` layer. Do not write raw JDBC in module classes.

---

## 12. Testing

Tests live in `src/test/java/org/nakii/valmora/`. The project uses JUnit 5 + Mockito.

- Mock `ValmoraAPI` and its sub-modules with `mock(ValmoraAPI.class)`.
- Call `ValmoraAPI.setProvider(mockApi)` in `@BeforeEach`.
- See `ExpressionTest.java` as the canonical example — it shows the correct mock setup pattern.
- Do **not** spin up a live server in unit tests. Use `DummyExecutionContext` stubs for `ExecutionContext`.

Run tests: `./gradlew test`

---

## 13. Common Mistakes to Avoid

| Mistake | Correct Approach |
|---------|-----------------|
| Registering listeners without unregistering in `onDisable()` | Always call `HandlerList.unregisterAll(listener)` |
| Registering commands inside a module's `onEnable()` | Register commands only in `Valmora.onEnable()` after modules are enabled |
| Calling Bukkit API from async threads | Schedule back to main thread via `runTask()` |
| Using `ChatColor` or `§` for text formatting | Use `Formatter.format()` or raw MiniMessage |
| Storing `ExecutionContext` as a field | Use it only within the current invocation scope |
| Accessing a module that loads after the current one at enable-time | Check module load order; restructure if needed |
| Writing raw JDBC outside the infrastructure layer | Use the existing DAO/executor pattern |
| Putting mutable state in the constructor instead of `onEnable()` | Always init state in `onEnable()`, reset in `onDisable()` |
| Using `Registry.get(key)` with mixed case | Always lowercase keys, or use the Registry's own case-insensitive helpers |
| Checking item display name in `InventoryClickEvent` to identify a GUI button | Store button identity in PDC — players can forge item names via anvil |

---

## 14. Paper API Hard Topics

This section documents Paper API areas where **AI training data is stale or wrong** due to breaking changes in 1.20.5–1.21.x. Read the relevant subsection before generating code in these areas.

---

### 14.1 Packets

Paper 1.20.5+ introduced significant internal packet structure changes when Mojang switched to data-driven items. This project does not currently use raw packets. If needed, prefer **PacketEvents 2.x** over ProtocolLib (which has lagged on modern versions).

NMS class path changed in Paper 1.21 — there is no version suffix:

```java
// WRONG — old versioned path
net.minecraft.server.v1_21_R1.EntityPlayer ep = ...; // ❌

// CORRECT — unified path in Paper 1.21+
net.minecraft.server.level.ServerPlayer sp =
    ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle();
```

---

### 14.2 Entity Pathfinding / Navigation

Paper 1.21 exposes a first-class `Pathfinder` API on `Mob`. Use it instead of raw NMS navigation:

```java
Mob mob = (Mob) entity;
Pathfinder pathfinder = mob.getPathfinder();
pathfinder.moveTo(targetLocation, 1.2); // speed multiplier
pathfinder.stopPathfinding();
boolean moving = pathfinder.hasPath();
```

Never touch `PathNavigation` via NMS directly — it breaks across minor versions.

---

### 14.3 Adventure / Component API (Text)

```java
// Item display names
meta.displayName(MiniMessage.miniMessage().deserialize("<red>My Item")); // ✓
meta.setDisplayName(ChatColor.RED + "My Item"); // ❌ deprecated

// Titles
player.showTitle(Title.title(
    Component.text("Title"),
    Component.text("Subtitle"),
    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(1000))
));
player.sendTitle("Title", "Subtitle", 10, 70, 20); // ❌ removed
```

---

### 14.4 Scheduler

```java
Bukkit.getScheduler().runTask(plugin, () -> { /* main thread */ });
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { /* async — no Bukkit API */ });
Bukkit.getScheduler().runTaskLater(plugin, () -> { /* main thread */ }, 20L);
```

Do **not** use `runTaskTimerAsynchronously` for anything that touches Bukkit state.

For entity-bound tasks use Paper's `EntityScheduler` so the task auto-cancels if the entity unloads:

```java
entity.getScheduler().runDelayed(plugin, task -> {
    entity.setFireTicks(0);
}, null, 100L);
```

---

### 14.5 ItemStack and PersistentDataContainer

PDC is the correct way to attach custom data to items and entities. Never use item lore or NBT string hacks to store data.

```java
NamespacedKey key = new NamespacedKey(plugin, "my_data");
meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "value");
String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
```

---

### 14.6 Command Registration (Brigadier API)

Paper 1.21 introduced a native Brigadier API under `io.papermc.paper.command.brigadier`. The old `plugin.yml` + `onCommand()` pattern still works. All Valmora commands are registered in `Valmora.onEnable()` after modules are enabled — keep new commands there too.

---

### 14.7 Entity Spawning

```java
// Preferred — spawn with consumer; entity is fully configured before first tick
Zombie zombie = world.spawn(location, Zombie.class, entity -> {
    entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
    entity.setHealth(40.0);
});
```

---

### 14.8 Events Agents Commonly Get Wrong in 1.21

| Incorrect / Old Usage | Correct 1.21 Pattern |
|-----------------------|----------------------|
| `PlayerInteractEvent` → check `getItem() != null` | Also check `event.getHand() == EquipmentSlot.HAND` to avoid double-firing |
| `EntityDamageByEntityEvent` to get attacker | Use `event.getDamageSource()` for full context including projectiles |
| `AsyncChatEvent` for chat handling | Use `io.papermc.paper.event.player.AsyncChatEvent` (Paper-specific) |

---

### 14.9 The Great 1.21 Enum Renames (Registries)

- **Attributes:** `Attribute.MAX_HEALTH`, `Attribute.ATTACK_DAMAGE` (not `GENERIC_*`)
- **Potion Effects:** `PotionEffectType.STRENGTH`, `PotionEffectType.HASTE` (not `INCREASE_DAMAGE`, `FAST_DIGGING`)
- **Enchantments:** `Enchantment.SHARPNESS` (not `DAMAGE_ALL`); prefer `Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"))` for dynamic lookups

---

### 14.10 Modern Damage API

Never use `entity.damage(amount)` without a `DamageSource`. Always build one:

```java
DamageSource source = DamageSource.builder(DamageType.MAGIC)
    .withDirectEntity(spellEntity)
    .withCausingEntity(caster)
    .build();
target.damage(10.0, source);
```

---

### 14.11 Item Components (Food, Tool, Jukebox)

As of 1.20.5+, items are component-based. Never use NBT strings to make an item edible:

```java
ItemMeta meta = item.getItemMeta();
FoodComponent food = meta.getFood();
food.setNutrition(5);
food.setSaturation(0.6f);
food.setCanAlwaysEat(true);
meta.setFood(food);
item.setItemMeta(meta);
```

---

### 14.12 Attribute Modifiers (Breaking Change in 1.21)

`AttributeModifier` no longer uses UUID. It requires a `NamespacedKey`:

```java
// WRONG — pre-1.21
new AttributeModifier(UUID.randomUUID(), "generic.attack_damage", 5.0, Operation.ADD_NUMBER); // ❌

// CORRECT
NamespacedKey key = new NamespacedKey(plugin, "bonus_damage");
AttributeModifier mod = new AttributeModifier(key, 5.0, AttributeModifier.Operation.ADD_NUMBER);
entity.getAttribute(Attribute.ATTACK_DAMAGE).addModifier(mod);
```

---

### 14.13 Floating Text / Holograms (Display Entities)

Never use `ArmorStand` for floating text. Use 1.19.4+ `Display` entities:

```java
TextDisplay display = world.spawn(loc, TextDisplay.class, entity -> {
    entity.text(MiniMessage.miniMessage().deserialize("<gold>Floating Text!"));
    entity.setBillboard(Display.Billboard.CENTER);
    entity.setDefaultBackground(false);
});
```

---

### 14.14 Potions (Removal of PotionData)

`PotionData` was removed in 1.20.5+. Use `setBasePotionType()` directly:

```java
PotionMeta meta = (PotionMeta) item.getItemMeta();
meta.setBasePotionType(PotionType.STRENGTH);
item.setItemMeta(meta);
```

---

### 14.15 Teleportation (Paper Async Chunk Loading)

Always use Paper's `teleportAsync()` when the target chunk may not be loaded:

```java
player.teleportAsync(distantLocation).thenAccept(success -> {
    if (success) player.sendMessage(Component.text("Woosh!", NamedTextColor.AQUA));
});
```

---

### 14.16 Smithing Recipes

As of 1.20, Smithing tables require three slots (Template + Base + Addition):

```java
RecipeChoice template = new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
Bukkit.addRecipe(new SmithingTransformRecipe(key, result, template, baseChoice, additionChoice));
```

---

### 14.17 Inventory Titles

`InventoryView` is now an interface in 1.21. Use the Component-based title method:

```java
Component title = event.getView().title(); // ✓
String title = event.getView().getTitle(); // ❌ deprecated/removed
```

Prefer checking PDC over matching titles to identify GUI windows (see §13).

---

### 14.18 Checking Material Types

Never hardcode lists of materials. Use Bukkit `Tag`s:

```java
if (Tag.LOGS.isTagged(mat)) { /* ... */ } // ✓
if (mat == Material.OAK_LOG || mat == Material.SPRUCE_LOG ...) { } // ❌
```

---

_Last updated: see git history._
