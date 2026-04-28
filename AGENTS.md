# AGENTS.md — Valmora Development Guide for AI Agents

> This file is the **primary orientation document** for any AI agent working on this codebase.
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
8. [Database Layer](#8-database-layer)
9. [Testing](#9-testing)
10. [Common Mistakes to Avoid](#10-common-mistakes-to-avoid)
11. [Paper API Hard Topics](#11-paper-api-hard-topics)

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

- **`docs/MODULE_DEVELOPMENT.md`** — complete lifecycle guide for creating, registering, enabling, and hot-reloading modules. Covers `ReloadableModule`, `ModuleManager`, listener registration, and inter-module communication with code examples.
- **`docs/VALMORA_DOCUMENTATION.md`** — 1300+ lines covering every subsystem: items, mobs, skills, abilities, GUI, scripting, execution context, and YAML schemas. If a system exists in Valmora, its contract is documented here.

These two files are ground truth. If this AGENTS.md ever conflicts with them, the specific doc wins.

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
│   │   │   │   ├── item/
│   │   │   │   ├── mob/
│   │   │   │   ├── skill/
│   │   │   │   ├── combat/
│   │   │   │   ├── script/
│   │   │   │   └── ...
│   │   │   └── infrastructure/
│   │   │       └── config/         # YamlLoader lives here
│   │   └── resources/
│   │       ├── plugin.yml
│   │       ├── config.yml
│   │       ├── items/*.yml
│   │       ├── mobs/*.yml
│   │       ├── gui/*.yml
│   │       └── skills/*.yml
│   └── test/
│       └── java/org/nakii/valmora/
├── docs/
│   ├── MODULE_DEVELOPMENT.md       ← READ THIS
│   └── VALMORA_DOCUMENTATION.md    ← READ THIS
└── plugins/Valmora/                # Runtime data (generated, not committed)
    ├── config.yml
    ├── database.db
    ├── items/
    ├── mobs/
    ├── gui/
    └── skills/
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
script → stat → player → ui → ability → item → mob → skill → combat → gui → recipe → enchant
```

Later modules may depend on earlier ones (e.g. `skill` can access `stat`). Earlier modules must not depend on later ones. If you add a new module, insert it at the correct position — document the reason in `Valmora.java`.

**Accessing modules at runtime:**

```java
ValmoraAPI api = ValmoraAPI.getInstance();
ItemManager items = api.getItemManager();
SkillManager skills = api.getSkillManager();
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

Generic config loader at `org.nakii.valmora.infrastructure.config.YamlLoader`. Use it for all YAML loading — do not write custom FileConfiguration boilerplate.

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

All display text uses **MiniMessage** (Adventure). Never use `ChatColor` or `§` codes. Never use `LegacyComponentSerializer` for new code.

```java
// Correct
Component msg = MiniMessage.miniMessage().deserialize("<red>You took <bold>10</bold> damage!");
player.sendMessage(msg);

// Wrong — do not do this
player.sendMessage(ChatColor.RED + "You took 10 damage!"); // ❌
```

---

## 8. Database Layer

- **Default:** SQLite (`plugins/Valmora/database.db`)
- **Optional:** MySQL via `config.yml` → `database.type: mysql`
- **Pool:** HikariCP 5.1.0
- All queries run through the async executor. Follow the async safety rule in §7.4.

When adding a new table or query, follow the existing DAO pattern in the `infrastructure` layer. Do not write raw JDBC in module classes.

---

## 9. Testing

Tests live in `src/test/java/org/nakii/valmora/`. The project uses JUnit 5 + Mockito.

- Mock `ValmoraAPI` and its sub-modules with `mock(ValmoraAPI.class)`.
- Call `ValmoraAPI.setProvider(mockApi)` in `@BeforeEach`.
- See `ExpressionTest.java` as the canonical example — it shows the correct mock setup pattern.
- Do **not** spin up a live server in unit tests. Use `DummyExecutionContext` stubs for `ExecutionContext`.

Run tests: `./gradlew test`

---

## 10. Common Mistakes to Avoid

| Mistake                                                            | Correct Approach                                                          |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------- |
| Registering listeners without unregistering in `onDisable()`       | Always call `HandlerList.unregisterAll(listener)`                         |
| Registering commands inside a module's `onEnable()`                | Register commands only in `Valmora.onEnable()` after modules are enabled  |
| Calling Bukkit API from async threads                              | Schedule back to main thread via `runTask()`                              |
| Using `ChatColor` or `§` for text formatting                       | Use MiniMessage / Adventure `Component`                                   |
| Storing `ExecutionContext` as a field                              | Use it only within the current invocation scope                           |
| Accessing a module that loads after the current one at enable-time | Check module load order; restructure dependencies if needed               |
| Writing raw JDBC outside the infrastructure layer                  | Use the existing DAO/executor pattern                                     |
| Putting mutable state in the constructor instead of `onEnable()`   | Always init state in `onEnable()`, reset in `onDisable()`                 |
| Using `Registry.get(key)` with mixed case                          | Always lowercase keys, or use the Registry's own case-insensitive helpers |

---

## 11. Paper API Hard Topics

> This section documents Paper API areas where **AI training data is stale or wrong** due to breaking changes in 1.20.5–1.21.x. If you are generating code in these areas, read this section first. These are the exact patterns agents most commonly get wrong.

---

### 11.1 Packets

**TODO — fill in with project-specific detail.**

Placeholder notes for the maintainer to expand:

**ProtocolLib vs PacketEvents vs Paper native packet API:**
Paper 1.20.5+ introduced significant internal packet structure changes when Mojang switched to data-driven items. The recommended approach for 1.21 is **PacketEvents 2.x** (not ProtocolLib, which has lagged on modern versions). If this project sends raw packets, document which library is used and link to its API here.

Key areas where agents get this wrong:

- Using `ProtocolLib`'s `PacketAdapter` when it is not a dependency in this project.
- Referencing old NMS class paths (e.g. `net.minecraft.server.v1_21_R1`) — Paper 1.21 uses the unified `net.minecraft` package (no version suffix).
- Assuming `PlayerConnection` field names match old reflective references — use proper mappings or Paper's mapped API.

**Example of the NMS path change agents must know:**

```java
// WRONG — old versioned path, does not exist in Paper 1.21
net.minecraft.server.v1_21_R1.EntityPlayer ep = ...;

// CORRECT — unified path in Paper 1.21+
net.minecraft.server.level.ServerPlayer sp =
    ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle();
```

---

### 11.2 Entity Pathfinding / Navigation

**TODO — fill in with project-specific detail.**

Placeholder notes for the maintainer to expand:

Paper 1.21 exposes a first-class `Pathfinder` API on `Mob`. Agents trained before ~1.20 default to raw NMS navigation, which is fragile and version-sensitive. **Use the Paper API wherever possible.**

```java
// CORRECT — Paper Pathfinder API (1.20+)
Mob mob = (Mob) entity;
Pathfinder pathfinder = mob.getPathfinder();

// Move to a location
pathfinder.moveTo(targetLocation, 1.2); // speed multiplier

// Move to an entity
pathfinder.moveTo(targetEntity, 1.5);

// Stop pathfinding
pathfinder.stopPathfinding();

// Check if currently moving
boolean moving = pathfinder.hasPath();
```

**Custom goals — agents frequently get this wrong:**
Paper 1.21 provides `PathfinderGoal` wrappers via the `io.papermc.paper.entity.ai` package for adding custom goals without NMS. If you need a fully custom goal that has no Paper wrapper, document it with a comment explaining why NMS is required.

**NEVER do this in 1.21:**

```java
// WRONG — NMS direct navigation, breaks across minor versions
PathNavigation nav = ((CraftMob) mob).getHandle().getNavigation();
nav.moveTo(x, y, z, speed); // ❌ fragile NMS
```

---

### 11.3 Adventure / Component API (Text)

Agents trained before 1.19 often generate Bukkit string APIs that are **deprecated or removed** in Paper 1.21.

**Item display names:**

```java
// WRONG — setDisplayName takes a String with § codes, deprecated in Paper 1.20.5+
ItemMeta meta = item.getItemMeta();
meta.setDisplayName(ChatColor.RED + "My Item"); // ❌

// CORRECT — use Adventure Component
meta.displayName(
    MiniMessage.miniMessage().deserialize("<red>My Item")
);
```

**Player messages:**

```java
// WRONG
player.sendMessage(ChatColor.GREEN + "Hello!"); // ❌

// CORRECT
player.sendMessage(
    Component.text("Hello!", NamedTextColor.GREEN)
);
```

**Sending titles:**

```java
// WRONG — old Bukkit title API removed in modern Paper
player.sendTitle("Title", "Subtitle", 10, 70, 20); // ❌

// CORRECT
player.showTitle(Title.title(
    Component.text("Title"),
    Component.text("Subtitle"),
    Title.Times.times(
        Duration.ofMillis(500),
        Duration.ofSeconds(3),
        Duration.ofMillis(1000)
    )
));
```

---

### 11.4 Scheduler (Folia-Aware Patterns)

Agents often generate old `BukkitScheduler` calls without considering thread-safety. This plugin currently targets standard Paper (not Folia), but use patterns that document the main-thread requirement explicitly.

```java
// Standard Paper — still valid, but always note thread context
Bukkit.getScheduler().runTask(plugin, () -> { /* main thread */ });
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { /* async — no Bukkit API */ });
Bukkit.getScheduler().runTaskLater(plugin, () -> { /* main thread */ }, 20L); // 20 ticks = 1s
```

**Do not use `runTaskTimerAsynchronously` for anything that touches Bukkit state** — this is the most common scheduler mistake agents make.

---

### 11.5 ItemStack and PersistentDataContainer

PDC (PersistentDataContainer) is the correct way to attach custom data to items and entities. **Never use item lore or NBT string hacks** to store data.

```java
NamespacedKey key = new NamespacedKey(plugin, "my_data");

// Writing
ItemMeta meta = item.getItemMeta();
meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "value");
item.setItemMeta(meta);

// Reading
String value = meta.getPersistentDataContainer()
    .get(key, PersistentDataType.STRING);
```

**In 1.20.5+, `ItemStack` has component-level access.** Agents may generate `ItemMeta` patterns that predate component adoption. If you see agents using `ItemStack.setType()` + full meta rebuild for every operation, flag it — use `ItemStack.withType()` or component methods where available.

---

### 11.6 Command Registration (Brigadier API)

Paper 1.21 introduced a native Brigadier command API under `io.papermc.paper.command.brigadier`. The old `plugin.yml` command registration + `onCommand()` override still works but is considered legacy.

**New style (preferred for complex commands with tab completion and argument types):**

```java
// In onEnable — Paper 1.21 lifecycle commands
LifecycleEventManager<Plugin> mgr = plugin.getLifecycleManager();
mgr.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
    Commands commands = event.registrar();
    commands.register(
        Commands.literal("mycommand")
            .requires(src -> src.getSender().hasPermission("myplugin.cmd"))
            .executes(ctx -> {
                ctx.getSource().getSender().sendMessage(Component.text("Hello!"));
                return Command.SINGLE_SUCCESS;
            })
            .build()
    );
});
```

**IMPORTANT for Valmora:** The current project registers commands in `Valmora.onEnable()` after modules are enabled. If you migrate a command to Brigadier, keep it in that same location — do not move command registration into module `onEnable()`.

---

### 11.7 Entity Spawning

```java
// Preferred pattern — spawn with consumer for immediate setup (no half-initialized entity ticking)
Zombie zombie = world.spawn(location, Zombie.class, entity -> {
    entity.setCustomName(Component.text("Custom Zombie"));
    entity.setCustomNameVisible(true);
    entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
    entity.setHealth(40.0);
});

// Avoid — spawn then configure, entity ticks for 1 tick uninitialized
Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE); // ❌ prefer above
zombie.setHealth(40.0);
```

---

### 11.8 Events Agents Commonly Get Wrong in 1.21

| Incorrect / Old Usage                              | Correct 1.21 Pattern                                                                       |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `PlayerInteractEvent` → check `getItem() != null`  | Also check `event.getHand() == EquipmentSlot.HAND` to avoid double-firing                  |
| `EntityDamageByEntityEvent` to get attacker        | Use `event.getDamageSource()` for full context including projectiles                       |
| `BlockPhysicsEvent` for structural changes         | Use `BlockBreakEvent`, `BlockPlaceEvent`, or `StructureGrowEvent` depending on intent      |
| `EntityRegainHealthEvent`                          | Extends `EntityEvent` — still valid, but check `RegainReason` enum values have not changed |
| Cancelling `FoodLevelChangeEvent` to freeze hunger | Still works, but verify the event fires on saturation drain as well                        |
| `AsyncChatEvent` for chat handling                 | Use `io.papermc.paper.event.player.AsyncChatEvent` (Paper-specific, not Spigot's)          |

---

#### 11.9 The Great 1.21 Enum Renames (Registries)

_AI models constantly use old enum names for Enchantments, Attributes, and Potion Effects that will instantly cause compilation failures in 1.21._

**Add this to your docs:**
**Registries & Enums:** In 1.21, Bukkit aligned its naming with Vanilla Minecraft. Many legacy enums were removed or deprecated.

- **Attributes:** Do not use `Attribute.GENERIC_MAX_HEALTH` or `GENERIC_ATTACK_DAMAGE`. They are now `Attribute.MAX_HEALTH`, `Attribute.ATTACK_DAMAGE`, etc.
- **Potion Effects:** Do not use `PotionEffectType.INCREASE_DAMAGE` or `FAST_DIGGING`. Use `PotionEffectType.STRENGTH`, `PotionEffectType.HASTE`, `PotionEffectType.SLOWNESS`, etc.
- **Enchantments:** Do not use `Enchantment.DAMAGE_ALL`. Use `Enchantment.SHARPNESS`. Always prefer `Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"))` for dynamic lookups.
- **Damage Types:** Do not use `EntityDamageEvent.DamageCause`. Use the modern `DamageSource` and `DamageType` APIs (see §11.10).

#### 11.10 Modern Damage API (DamageSource)

_AI defaults to `entity.damage(10.0)` or creates fake `EntityDamageEvent` instances. In 1.21, Paper requires proper `DamageSource` tracking._

**Add this to your docs:**
**Dealing Damage:** Never use `entity.damage(amount)` without a `DamageSource` in modern Paper. Always build a `DamageSource` to properly trigger death messages, armor reductions, and custom mechanics.

```java
// WRONG - AI default
target.damage(10.0, attacker); // ❌ Lacks proper DamageType context

// CORRECT - Paper 1.21 DamageSource API
DamageSource source = DamageSource.builder(DamageType.MAGIC)
    .withDirectEntity(spellEntity)
    .withCausingEntity(caster)
    .build();
target.damage(10.0, source);
```

#### 11.11 Item Components (Food, Tool, Jukebox)

_AI tries to use NMS or Custom NBT for things that are now native Item Components in 1.21._

**Add this to your docs:**
**Data-Driven Item Components:** As of 1.20.5+, items are component-based. Bukkit exposes these via specific `ItemMeta` interfaces or component methods. Never use NBT strings to make an item edible or change its tool properties.

```java
// CORRECT - Making any item edible
ItemMeta meta = item.getItemMeta();
FoodComponent food = meta.getFood();
food.setNutrition(5);
food.setSaturation(0.6f);
food.setCanAlwaysEat(true);
meta.setFood(food);
item.setItemMeta(meta);
```

#### 11.12 GUI / Inventory Interaction Safety

_AI loves to check the display name of an item to see if a player clicked a specific button in a GUI. This is a massive security/bug risk._

**Add this to your docs:**
**GUI Item Identification:** Never check `ItemMeta.getDisplayName()` or `ItemMeta.displayName()` inside an `InventoryClickEvent` to identify a button. Players can forge item names with anvils.

**Always use PDC (PersistentDataContainer) for GUI buttons:**

```java
// Writing the button ID when creating the GUI
meta.getPersistentDataContainer().set(
    new NamespacedKey(plugin, "gui_action"),
    PersistentDataType.STRING,
    "confirm_trade"
);

// Reading in InventoryClickEvent
String action = meta.getPersistentDataContainer().get(
    new NamespacedKey(plugin, "gui_action"),
    PersistentDataType.STRING
);
if ("confirm_trade".equals(action)) { /* ... */ }
```

#### 11.13 Entity Schedulers (Paper Specific)

_AI will default to `Bukkit.getScheduler().runTaskLater(...)` for entities. In Paper 1.21, you must use the Entity's own scheduler so tasks safely pause or cancel if the entity is unloaded or teleports across dimensions._

**Add this to your docs:**
**Entity-Specific Tasks:** If a scheduled task interacts with an Entity, **do not** use the global Bukkit scheduler. Use Paper's `EntityScheduler`.

```java
// WRONG - May execute after entity unloads, causing memory leaks
Bukkit.getScheduler().runTaskLater(plugin, () ->{ entity.setFireTicks(0); }, 100L); // ❌

// CORRECT - Paper EntityScheduler
entity.getScheduler().runDelayed(plugin, task ->{
    entity.setFireTicks(0);
}, null, 100L); // Task automatically cancels if entity unloads/dies
```

#### 11.14 Custom Player Skulls (No NMS)

_AI models will frequently generate huge NMS/Reflection reflection blocks (`GameProfile`, `Property`) to create custom textured heads._

**Add this to your docs:**
**Custom Skulls/Heads:** Never use NMS or `GameProfile` reflection to set a skull's texture. Paper has a native `PlayerProfile` API.

```java
// CORRECT - Paper API for Custom Skulls
PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
PlayerTextures textures = profile.getTextures();
try {
    textures.setSkin(new URL("https://textures.minecraft.net/texture/your_base64_decoded_url_here"));
} catch (MalformedURLException e) { ... }
profile.setTextures(textures);

SkullMeta meta = (SkullMeta) item.getItemMeta();
meta.setPlayerProfile(profile);
item.setItemMeta(meta);
```

#### 11.15 Tags instead of Hardcoded Materials

_AI will write massive `if/else` statements checking if a block is `OAK_LOG`, `SPRUCE_LOG`, `BIRCH_LOG`, etc._

**Add this to your docs:**
**Checking Block/Material Types:** Never hardcode lists of materials to check for generic concepts (like "is this a log?" or "is this dirt?"). Use Bukkit `Tag`s.

```java
// WRONG
if (mat == Material.OAK_LOG || mat == Material.SPRUCE_LOG ...) // ❌

// CORRECT
if (Tag.LOGS.isTagged(mat)) { /* ... */ }
```

---

#### 11.16 Attribute Modifiers (Breaking Change in 1.21)

_Vanilla Minecraft completely removed UUIDs from Attribute Modifiers in 1.21. Modifiers are now identified by `NamespacedKey`. AI will almost always try to generate the old UUID-based constructor, which will fail to compile._

**Add this to your docs:**

> **Attribute Modifiers:** In 1.21, `AttributeModifier` no longer uses a `UUID` or a generic string name. It requires a `NamespacedKey`. Do not generate UUID-based modifiers.
>
> ```java
> // WRONG - AI default (Pre-1.21)
> AttributeModifier mod = new AttributeModifier(UUID.randomUUID(), "generic.attack_damage", 5.0, Operation.ADD_NUMBER); // ❌ COMPILE ERROR
>
> // CORRECT - Paper 1.21
> NamespacedKey key = new NamespacedKey(plugin, "bonus_damage");
> AttributeModifier mod = new AttributeModifier(key, 5.0, AttributeModifier.Operation.ADD_NUMBER);
> entity.getAttribute(Attribute.ATTACK_DAMAGE).addModifier(mod);
> ```

#### 11.17 Floating Text / Holograms (Display Entities)

_AI will default to spawning invisible `ArmorStand` entities to create floating text or holograms. This is a massive performance drain and an obsolete 1.8-1.19 practice._

**Add this to your docs:**

> **Holograms & Floating Text:** Never use `ArmorStand` for floating text, item displays, or block displays. Use 1.19.4+ `Display` entities (`TextDisplay`, `ItemDisplay`, `BlockDisplay`). They are infinitely more performant and purely visual.
>
> ```java
> // WRONG - The old 1.19 way
> ArmorStand stand = world.spawn(loc, ArmorStand.class);
> stand.setVisible(false);
> stand.setCustomNameVisible(true); // ❌ Horrible for client/server performance
>
> // CORRECT - Modern Paper API
> TextDisplay display = world.spawn(loc, TextDisplay.class, entity -> {
>     entity.text(MiniMessage.miniMessage().deserialize("<gold>Floating Text!"));
>     entity.setBillboard(Display.Billboard.CENTER);
>     entity.setDefaultBackground(false);
> });
> ```

#### 11.18 Potions and Tipped Arrows (Removal of PotionData)

_Because potions became data-driven in 1.20.5, the old `PotionData` class was completely removed. AI will try to use it to set base potions._

**Add this to your docs:**

> **Potion Meta:** Do not use `PotionData`. It was removed in modern Paper. Use `setBasePotionType()` directly on the `PotionMeta`.
>
> ```java
> // WRONG - Removed in 1.20.5+
> PotionMeta meta = (PotionMeta) item.getItemMeta();
> meta.setBasePotionData(new PotionData(PotionType.STRENGTH)); // ❌ COMPILE ERROR
>
> // CORRECT - Modern Paper API
> PotionMeta meta = (PotionMeta) item.getItemMeta();
> meta.setBasePotionType(PotionType.STRENGTH);
> item.setItemMeta(meta);
> ```

#### 11.19 Teleportation (Paper Async Chunk Loading)

_AI will use standard `entity.teleport(Location)`. If the target chunk isn't loaded, this halts the server's main thread while the chunk generates/loads. Paper has a specific API to fix this._

**Add this to your docs:**

> **Teleportation:** Always use Paper's `teleportAsync()` for players and entities, especially when teleporting across the map or between dimensions. Never use the synchronous `.teleport()` unless you have explicitly verified the chunk is already loaded.
>
> ```java
> // WRONG - Blocks the main thread if chunk is unloaded
> player.teleport(distantLocation); // ❌
>
> // CORRECT - Non-blocking Paper API
> player.teleportAsync(distantLocation).thenAccept(success -> {
>     if (success) {
>         player.sendMessage(Component.text("Woosh!", NamedTextColor.AQUA));
>     }
> });
> ```

#### 11.20 Smithing Recipes (Armor Trims / Templates)

_AI will try to create custom Smithing Recipes using two items (Base + Addition). As of 1.20, Smithing tables require 3 slots (Template + Base + Addition)._

**Add this to your docs:**

> **Custom Smithing Recipes:** The old `SmithingRecipe` class constructor is obsolete. You must use `SmithingTransformRecipe` or `SmithingTrimRecipe` and provide a Template `RecipeChoice`.
>
> ```java
> // WRONG - Old 1.19 2-slot smithing
> Bukkit.addRecipe(new SmithingRecipe(key, result, baseChoice, additionChoice)); // ❌ COMPILE ERROR
>
> // CORRECT - Modern 1.20+ 3-slot smithing
> RecipeChoice template = new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
> Bukkit.addRecipe(new SmithingTransformRecipe(key, result, template, baseChoice, additionChoice));
> ```

#### 11.21 Inventory `getView()` and Titles

_In 1.21, Bukkit overhauled the InventoryView API to support the new modern component-based UI titles. AI will use deprecated methods._

**Add this to your docs:**

> **Inventory Titles:** Do not use `player.getOpenInventory().getTitle()`. `InventoryView` is now an interface, and titles are returned as Adventure `Component`s, not Strings.
>
> ```java
> // WRONG
> String title = event.getView().getTitle(); // ❌ Deprecated / Removed
>
> // CORRECT
> Component title = event.getView().title();
> // Or check PDC instead of title! (See §11.12)
> ```

---

_Last updated: see git history. Maintainer: fill in the Packets (§11.1) and Pathfinding (§11.2) TODO sections with project-specific implementation details as those systems are built out._
