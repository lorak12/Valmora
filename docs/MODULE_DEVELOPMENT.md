# Valmora Module Development Guide

> **Version:** 0.1 | **API:** Paper 1.21.x | **Java:** 21

---

## Table of Contents

1. [Module System Architecture](#1-module-system-architecture)
2. [Core Interfaces](#2-core-interfaces)
3. [Creating a New Module](#3-creating-a-new-module)
4. [YAML Configuration Loading](#4-yaml-configuration-loading)
5. [Registry Pattern](#5-registry-pattern)
6. [Inter-Module Communication](#6-inter-module-communication)
7. [Event Handling](#7-event-handling)
8. [Exposing Module via API](#8-exposing-module-via-api)
9. [Module Load Order](#9-module-load-order)
10. [Common Patterns & Examples](#10-common-patterns--examples)

---

## 1. Module System Architecture

Valmora uses a **lifecycle-aware module system** where each subsystem implements `ReloadableModule`. The `ModuleManager` handles:
- Registration (associating a module ID with its instance)
- Enablement (calling `onEnable()` in registration order)
- Disablement (calling `onDisable()` in reverse order)
- Hot-reloading (disable + enable)

```
Valmora.onEnable()
    │
    ├── 1. ModuleManager created
    ├── 2. All modules instantiated (fields in Valmora.java)
    ├── 3. All modules registered (moduleManager.registerModule)
    ├── 4. All modules enabled (moduleManager.enableModules)
    │       └─ Each module.onEnable() runs in registration order
    └── 5. Commands registered
```

**Key Principle:** Modules are **plugins within the plugin**. Each module should be:
- Self-contained (manage its own state)
- Independently reloadable (clean up in `onDisable()`)
- Idempotent (safe to call `onEnable()` multiple times)

---

## 2. Core Interfaces

### ReloadableModule

```java
package org.nakii.valmora.api;

public interface ReloadableModule {

    void onEnable();
    // Initialize: load configs, register listeners, start tasks

    void onDisable();
    // Cleanup: unregister listeners, cancel tasks, clear caches

    String getId();
    // Unique lowercase ID (e.g., "skills", "combat", "items")

    default String getName() {
        return getId();
    }
}
```

### ModuleManager

```java
package org.nakii.valmora.module;

public class ModuleManager {

    public void registerModule(ReloadableModule module);
    // Adds to registry. Does NOT enable. Key = module.getId().toLowerCase()

    public void enableModules();
    // Calls onEnable() on each module in registration order
    // Exceptions caught per-module — one failure doesn't stop others

    public void disableModules();
    // Calls onDisable() in REVERSE registration order

    public void reloadModules();
    // Calls disableModules() then enableModules()

    public ReloadableModule getModule(String id);
    // Returns module by ID, or null if not found
}
```

### ValmoraAPI

```java
package org.nakii.valmora.api;

public interface ValmoraAPI {
    static void setProvider(ValmoraAPI provider);
    static ValmoraAPI getInstance();

    // Access to all modules:
    ModuleManager getModuleManager();
    PlayerManager getPlayerManager();
    ItemManager getItemManager();
    MobManager getMobManager();
    StatModule getStatModule();
    UIManager getUIManager();
    SkillManager getSkillManager();
    AbilityManager getAbilityManager();
    DamageIndicatorManager getDamageIndicatorManager();
    ScriptModule getScriptModule();
}
```

---

## 3. Creating a New Module

### Step 1: Create the Module Class

```java
package org.nakii.valmora.module.myfeature;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class MyFeatureModule implements ReloadableModule {

    private final Valmora plugin;
    private MyFeatureListener listener;
    private MyFeatureRegistry registry;
    private MyFeatureLoader loader;

    public MyFeatureModule(Valmora plugin) {
        this.plugin = plugin;
        this.registry = new MyFeatureRegistry();
        this.loader = new MyFeatureLoader(plugin, registry);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling My Feature Module...");
        
        // 1. Load YAML configurations
        this.loader.load();
        
        // 2. Register event listeners
        this.listener = new MyFeatureListener(plugin, registry);
        plugin.getServer().getPluginManager()
            .registerEvents(listener, plugin);
        
        // 3. Start recurring tasks (if needed)
        // new MyFeatureTask(plugin).runTaskTimerAsynchronously(...);
        
        plugin.getLogger().info("My Feature Module enabled.");
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling My Feature Module...");
        
        // 1. Unregister ALL listeners
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }
        
        // 2. Cancel all BukkitTasks started by this module
        // (track task IDs and cancel them here)
        
        // 3. Clear caches (if any)
        // registry.clear();
        
        plugin.getLogger().info("My Feature Module disabled.");
    }

    @Override
    public String getId() {
        return "myfeature";  // lowercase, unique
    }

    @Override
    public String getName() {
        return "My Feature";
    }

    // Getter for other modules to access your features
    public MyFeatureRegistry getRegistry() {
        return registry;
    }
}
```

### Step 2: Register in Valmora.java

```java
// In Valmora.java - add as a field:
private MyFeatureModule myFeatureModule;

// In onEnable() - instantiate BEFORE module registration:
this.myFeatureModule = new MyFeatureModule(this);

// Register with ModuleManager:
moduleManager.registerModule(myFeatureModule);
```

### Step 3: Expose via API (Optional but Recommended)

```java
// In ValmoraAPI interface:
MyFeatureModule getMyFeatureModule();

// In Valmora.java implementation:
@Override
public MyFeatureModule getMyFeatureModule() {
    return myFeatureModule;
}
```

---

## 4. YAML Configuration Loading

Valmora provides `YamlLoader<T>` for loading `.yml` files as typed objects.

### BasicLoader Pattern

```java
package org.nakii.valmora.module.myfeature;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.infrastructure.config.YamlLoader;

public class MyFeatureLoader {

    private final Valmora plugin;
    private final MyFeatureRegistry registry;
    private final YamlLoader<MyFeatureDefinition> loader;

    public MyFeatureLoader(Valmora plugin, MyFeatureRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        
        // Loads all .yml files from plugins/Valmora/myfeature/
        this.loader = new YamlLoader<>(
            plugin,
            "myfeature",      // folder name
            "myfeature"      // subfolder (creates plugins/Valmora/myfeature/)
        );
    }

    public void load() {
        // Clear existing entries before reload
        registry.clear();
        
        // Load files as ConfigurationSections
        loader.loadFilesAsSections(
            (id, section, filePath) -> MyFeatureParser.parse(
                id, section, filePath
            ),
            definition -> registry.register(definition.getId(), definition)
        );
    }
}
```

### Using a Section Parser

```java
package org.nakii.valmora.module.myfeature;

import org.bukkit.configuration.ConfigurationSection;

public class MyFeatureParser {

    public static MyFeatureDefinition parse(
        String id,
        ConfigurationSection section,
        String filePath
    ) {
        // Parse fields from section
        String name = section.getString("name", id);
        int tier = section.getInt("tier", 1);
        
        // Return parsed definition
        return new MyFeatureDefinition(id, name, tier);
    }
}
```

### Example YAML File

File: `plugins/Valmora/myfeature/weapons.yml`

```yaml
fire_sword:
  name: "Flame Blade"
  tier: 5
  damage: 150

ice_staff:
  name: "Frost Staff"
  tier: 3
  damage: 80
```

---

## 5. Registry Pattern

### SimpleRegistry Implementation

```java
package org.nakii.valmora.module.myfeature;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class MyFeatureRegistry {

    private final Map<String, MyFeatureDefinition> entries = new LinkedHashMap<>();

    public void register(String id, MyFeatureDefinition entry) {
        entries.put(id.toLowerCase(), entry);
    }

    public Optional<MyFeatureDefinition> get(String id) {
        return Optional.ofNullable(entries.get(id.toLowerCase()));
    }

    public boolean contains(String id) {
        return entries.containsKey(id.toLowerCase());
    }

    public Collection<MyFeatureDefinition> values() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
```

---

## 6. Inter-Module Communication

### Accessing Other Modules

```java
// Get another module via API
ValmoraAPI api = ValmoraAPI.getInstance();

// Get specific module manager/registry
ItemManager itemManager = api.getItemManager();
SkillManager skillManager = api.getSkillManager();

// Example: Spawn a custom item
ItemStack item = itemManager.createItemStack("my_custom_sword");

// Example: Give XP to a player
skillManager.addXp(player, Skill.COMBAT, 50.0);
```

### Calling Multiple Services

```java
public class MyFeatureService {

    public void processPlayer(Player player) {
        ValmoraAPI api = ValmoraAPI.getInstance();
        
        // Access multiple modules
        PlayerManager pm = api.getPlayerManager();
        StatModule statModule = api.getStatModule();
        ItemManager itemManager = api.getItemManager();
        
        // Get player's active profile
        pm.getProfile(player).ifPresent(profile -> {
            // Do something with the profile
        });
    }
}
```

---

## 7. Event Handling

### Registering Listeners

```java
public class MyFeatureListener implements Listener {

    private final Valmora plugin;

    public MyFeatureListener(Valmora plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Handle player join
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Handle combat damage
    }
}
```

### Unregistering Listeners (Critical!)

```java
@Override
public void onDisable() {
    // MUST unregister ALL listeners
    if (listener != null) {
        org.bukkit.event.HandlerList.unregisterAll(listener);
        listener = null;
    }
}
```

---

## 8. Exposing Module via API

### Step 1: Add to ValmoraAPI Interface

```java
// In ValmoraAPI.java:
ModuleManager getModuleManager();
MyFeatureModule getMyFeatureModule();  // Add this
```

### Step 2: Implement in Valmora Class

```java
// In Valmora.java:
@Override
public MyFeatureModule getMyFeatureModule() {
    return myFeatureModule;
}
```

### Step 3: Access from External Code

```java
// From another plugin or module:
ValmoraAPI api = ValmoraAPI.getInstance();
MyFeatureModule myModule = api.getMyFeatureModule();
MyFeatureRegistry registry = myModule.getRegistry();
```

---

## 9. Module Load Order

Modules are enabled in **registration order**. The current order is:

```
1.  PlayerManager     (profiles, player data)
2.  StatModule       (stats, stat calculations)
3.  UIManager        (chat, action bar, scoreboard)
4.  AbilityManager  (mechanics registry)
5.  ItemManager     (custom items)
6.  MobManager      (custom mobs)
7.  SkillModule    (skills, XP)
8.  CombatModule   (damage, combat)
9.  ScriptModule  (expressions, conditions, events)
10. GuiModule      (inventory GUIs)
11. RecipeModule  (crafting recipes)
12. EnchantModule (enchantments)
```

### Choosing Load Order

If your module **depends on** another:
- Load AFTER the dependency
- Example: ItemManager depends on AbilityManager → loads after it

If your module is **independent**:
- Load late (near bottom) to ensure other systems are ready

---

## 10. Common Patterns & Examples

### Pattern 1: Simple Static Module

For modules with no YAML config:

```java
public class SimpleModule implements ReloadableModule {

    private final Valmora plugin;

    public SimpleModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager()
            .registerEvents(new Listener() {
                // Anonymous listener class
            }, plugin);
    }

    @Override
    public String getId() {
        return "simple";
    }
}
```

### Pattern 2: Module with Task Scheduler

```java
public class MyTaskModule implements ReloadableModule {

    private final Valmora plugin;
    private BukkitRunnable task;

    @Override
    public void onEnable() {
        // Schedule repeating task
        task = new BukkitRunnable() {
            @Override
            public void run() {
                // Task logic
            }
        };
        task.runTaskTimerAsynchronously(plugin, 0, 20);  // Every 1 second
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public String getId() {
        return "mytask";
    }
}
```

### Pattern 3: Module with Multiple Listeners

```java
public class MyMultiListenerModule implements ReloadableModule {

    private final Valmora plugin;
    private Listener inventoryListener;
    private Listener playerListener;
    private Listener entityListener;

    @Override
    public void onEnable() {
        inventoryListener = new InventoryListener();
        playerListener = new PlayerListener();
        entityListener = new EntityListener();

        plugin.getServer().getPluginManager()
            .registerEvents(inventoryListener, plugin);
        plugin.getServer().getPluginManager()
            .registerEvents(playerListener, plugin);
        plugin.getServer().getPluginManager()
            .registerEvents(entityListener, plugin);
    }

    @Override
    public void onDisable() {
        if (inventoryListener != null) {
            HandlerList.unregisterAll(inventoryListener);
            inventoryListener = null;
        }
        if (playerListener != null) {
            HandlerList.unregisterAll(playerListener);
            playerListener = null;
        }
        if (entityListener != null) {
            HandlerList.unregisterAll(entityListener);
            entityListener = null;
        }
    }

    @Override
    public String getId() {
        return "multilistener";
    }
}
```

### Pattern 4: Module Requiring Other Modules

```java
public class DependentModule implements ReloadableModule {

    private final Valmora plugin;
    private ItemManager itemManager;
    private StatModule statModule;

    public DependentModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        // Get dependencies from API
        ValmoraAPI api = ValmoraAPI.getInstance();
        this.itemManager = api.getItemManager();
        this.statModule = api.getStatModule();

        // Use dependencies
        // Example: itemManager.createItemStack(...)
    }

    @Override
    public String getId() {
        return "dependent";
    }
}
```

### Pattern 5: Adding Custom Script Variables

```java
public class MyVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "myfeature";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        
        if (path[0].equalsIgnoreCase("value")) {
            return context.getPlayerCaster()
                .map(p -> getCustomValue(p))
                .orElse(0);
        }
        
        return null;
    }

    private int getCustomValue(Player player) {
        // Custom logic
        return 42;
    }
}
```

Register in module's `onEnable()`:

```java
ValmoraAPI.getInstance().getScriptModule()
    .getVariableProviderRegistry()
    .register("myfeature", new MyVariableProvider());
```

Now `$myfeature.value$` works in all script expressions.

---

## Checklist: Adding a New Module

- [ ] Create module class implementing `ReloadableModule`
- [ ] Add field in `Valmora.java`
- [ ] Instantiate in `onEnable()` before module registration
- [ ] Register with `moduleManager.registerModule()`
- [ ] Implement `onEnable()`: load configs, register listeners
- [ ] Implement `onDisable()`: unregister listeners, cancel tasks
- [ ] Expose via API interface (if needed)
- [ ] Choose correct load order
- [ ] Test hot-reload: `/valmora reload`