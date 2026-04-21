# Module: GUIs

## Overview

The GUI module provides a configuration-driven inventory interface system. It supports static displays, dynamic paginated lists, input/output slots tied to machines, and interactive components powered by the Valmora Script Engine. It manages its own update loops for real-time rendering.

## YAML Structure

```yaml
<gui_id>:
  title: "<dark_gray><bold>⚙ Valmora Anvil</bold></dark_gray>"
  rows: 6
  machine: anvil
  update-interval: 10
  layout:
    - "BBBBBBBBB"
    - "BGGGGGGGB"
  components:
    B:
      type: DISPLAY
      display-item:
        material: BLACK_STAINED_GLASS_PANE
        name: " "
    I:
      type: INPUT
      id: base_item
  on-open:
    actions:
      - "sound player block.anvil.place"
```

## Configuration Options

### `title`

- **Type:** String (MiniMessage formatted)
- **Default:** `"Inventory"`
- **Description:** The title of the inventory window.
- **Code Reference:** `GuiDefinitionParser.java` / `Bukkit.createInventory`
- **Behavior:** Parsed through `Formatter.format()`. Applied when the inventory is created in `GuiModule.openGui`.

### `rows`

- **Type:** Integer
- **Default:** Derived from `layout` size
- **Description:** The number of rows in the inventory.
- **Code Reference:** `GuiDefinitionParser.java`
- **Behavior:** Multiplied by 9 to determine the total inventory size.

### `update-interval`

- **Type:** Integer
- **Default:** `0`
- **Description:** Tick interval at which the GUI re-renders.
- **Code Reference:** `GuiModule.java`
- **Behavior:** If `> 0`, `GuiModule` schedules a `BukkitRunnable` to call `GuiRenderer.render(session)` every X ticks.

### `machine`

- **Type:** String
- **Default:** `<gui_id>`
- **Description:** Associates the GUI with a specific crafting machine ID.
- **Code Reference:** `GuiDefinitionParser.java` / `GuiListener.java`
- **Behavior:** Passed to `RecipeEngine.match()` to filter and validate recipes specific to this GUI's input/output layout.

### `layout`

- **Type:** List of Strings
- **Default:** `[]`
- **Description:** A grid representation of the GUI. Each character maps to a component.
- **Code Reference:** `GuiDefinitionParser.java`
- **Behavior:** Strings are padded to 9 characters if they are shorter. Iterated over in `GuiRenderer` to map components to exact slot indexes `(row * 9 + col)`.

### `components.<char>.type`

- **Type:** String
- **Default:** `"DISPLAY"`
- **Description:** Defines the behavior of the slot mapped to `<char>`.
- **Code Reference:** `GuiDefinitionParser.parseComponent()`
- **Behavior:**
  - `DISPLAY`: Static/Clickable item. Protects item from being moved.
  - `INPUT`: Accepts player items for recipes.
  - `OUTPUT`: Used to retrieve crafted items.
  - `PAGINATED`: Resolves a script variable list and iterates over it.
  - `PREVIOUS_PAGE` / `NEXT_PAGE`: Modifies `GuiSession.currentPage`.

### `components.<char>.display-item`

- **Type:** ConfigurationSection
- **Default:** `null`
- **Description:** Defines the visual representation of the component.
- **Code Reference:** `GuiDefinitionParser.parseItemStack()`
- **Behavior:** Accepts `material`, `name` (MiniMessage/Variables supported), `lore` (List), `custom-model-data`, and `amount`. Parsed into `GuiItemStack`.

### `components.<char>.actions.<click_type>`

- **Type:** ConfigurationSection
- **Default:** Empty
- **Description:** Script events triggered when the slot is clicked. `<click_type>` matches `org.bukkit.event.inventory.ClickType` (e.g., `LEFT`, `RIGHT`, `SHIFT_LEFT`).
- **Code Reference:** `ClickHandlerParser.java`
- **Behavior:** Evaluates `conditions`. If true, executes `actions`. If false, executes `fail-actions`.

### `components.<char>.id` (For `INPUT` / `OUTPUT`)

- **Type:** String
- **Default:** `null`
- **Description:** A unique identifier for the specific input or output slot.
- **Code Reference:** `InputComponent.java` / `OutputComponent.java`
- **Behavior:** Included in the `Map<String, ItemStack> inputSnapshot` passed to the `RecipeEngine` for Exact Slot matching.

### `components.<char>.list` & `components.<char>.states` (For `PAGINATED`)

- **Type:** String / ConfigurationSection
- **Default:** `null`
- **Description:** Iterates over a script variable list (e.g., `$player.profiles$`). `states` map item variants based on loop conditions.
- **Code Reference:** `GuiRenderer.renderPaginatedSlot()`
- **Behavior:** Evaluates the `listExpression`. Matches the current list item against the `condition` string in `states` (using `"default"` as fallback) to determine which `display-item` to render.

### `on-open` / `on-close`

- **Type:** ConfigurationSection (Script Event Block)
- **Default:** `null`
- **Description:** Script logic executed when the inventory opens or closes.
- **Code Reference:** `GuiModule.java`
- **Behavior:** `on-open` is executed _before_ inventory creation. If its `conditions` fail, the GUI is never opened, and `fail-actions` trigger instead.

## Notes / Edge Cases

- **Anti-Dupe Logic:** `GuiModule.closeGuiSession` safely refunds items left in `INPUT` slots directly to the player's inventory or drops them on the floor to prevent race-condition dupes.
- **Variable Injection:** Text inside `display-item.name` and `lore` resolves script variables dynamically during `GuiRenderer.render()`.
- **Drag Protection:** `GuiListener.onInventoryDrag` utilizes a strict whitelist, instantly cancelling drags across any slots that are not defined as `INPUT` components.

---

# Module: Items

## Overview

The Item Engine handles the creation, stat-binding, and active/passive ability execution for custom items. Items are serialized to `ItemStack` objects with embedded NBT/PersistentData mapping to Valmora attributes.

## YAML Structure

```yaml
glacial_staff:
  name: "Staff of Glacial Flux"
  material: BLAZE_ROD
  item-type: "NONE"
  rarity: "EPIC"
  stats:
    MANA: 250
    MANA_REGEN: 15
  abilities:
    frost_bolt:
      trigger: "RIGHT_CLICK"
      target-range: 15.0
      cooldown: 2.5
      mana-cost: 45.0
      mechanics:
        - type: "DAMAGE"
          params:
            damage: 80.0
            damage-type: "MAGIC"
            target: "@target"
```

## Configuration Options

### `name`

- **Type:** String (MiniMessage)
- **Default:** `null`
- **Description:** Display name.
- **Code Reference:** `ItemFactory.create()`
- **Behavior:** Prefixed automatically with the `Rarity` color code before applying MiniMessage formats.

### `material`

- **Type:** String
- **Default:** _Required_
- **Description:** The underlying Vanilla material.
- **Code Reference:** `ItemDefinitionParser.java`
- **Behavior:** Checked against `Material.matchMaterial()`. Fails loading if invalid.

### `rarity`

- **Type:** String
- **Default:** `"COMMON"`
- **Description:** Defines the item's rarity tier (`COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`, `MYTHIC`).
- **Code Reference:** `ItemDefinitionParser.java`
- **Behavior:** Dictates the color of the item's name and appends a standardized rarity tag at the bottom of the generated lore. Saved to PDC key `valmora:rarity`.

### `item-type`

- **Type:** String
- **Default:** `"NONE"`
- **Description:** `SWORD`, `BOW`, `ARMOR`, or `NONE`.
- **Code Reference:** `ItemDefinitionParser.java`
- **Behavior:** Stored in the item's Persistent Data Container (PDC). Used contextually by other systems.

### `stats.<STAT_NAME>`

- **Type:** Double
- **Default:** `0.0`
- **Description:** Binds RPG attributes to the item (e.g., `DAMAGE`, `STRENGTH`, `CRIT_CHANCE`).
- **Code Reference:** `ItemDefinitionParser.java` / `StatModule.saveStats()`
- **Behavior:** Embedded into a nested `TAG_CONTAINER` inside the item's PDC. Automatically injected into the item's Lore during `ItemFactory.create()`. Recalculated live by `StatManager` when equipped or held.

### `abilities.<id>.trigger`

- **Type:** String
- **Default:** `null`
- **Description:** How the ability is activated (`RIGHT_CLICK`, `LEFT_CLICK`, `PASSIVE`, `EQUIP`, `UNEQUIP`).
- **Code Reference:** `AbilityListener.java` / `StatManager.recalculateStats()`
- **Behavior:** `RIGHT_CLICK`/`LEFT_CLICK` are handled by `PlayerInteractEvent`. `PASSIVE` is executed continuously during stat recalculations.

### `abilities.<id>.target-range`

- **Type:** Double
- **Default:** `0.0`
- **Description:** Maximum distance to raytrace for a valid `LivingEntity` target.
- **Code Reference:** `AbilityListener.java`
- **Behavior:** If `> 0`, uses `player.getTargetEntity()`. If no target is found, aborts execution and sends a missing target actionbar message.

### `abilities.<id>.cooldown` / `mana-cost`

- **Type:** Double
- **Default:** `0.0`
- **Description:** Resource constraints for the ability.
- **Code Reference:** `AbilityListener.java`
- **Behavior:** Checked against `PlayerState` and `CooldownManager`. Fails gracefully with ActionBar notifications if conditions aren't met.

### `abilities.<id>.mechanics`

- **Type:** List of Maps
- **Default:** `[]`
- **Description:** Ordered list of mechanics executed by the ability.
- **Code Reference:** `AbilityDefinition.Builder`
- **Behavior:** Looks up the `type` string in the `MechanicRegistry`. Passes the `params` map as a `MemoryConfiguration` to the mechanic's `ExecutionContext`.

#### Mechanic Options (Nested inside `params`)

- **`DAMAGE` Mechanic:**
  - `damage` (Double, default 1.0): Base damage dealt.
  - `type` (String, default "MAGIC"): Passed to `DamageCalculator`. Maps to `DamageType` enum.
  - `target` (String): Usually `"@target"`.
- **`HEAL` Mechanic:**
  - `heal` (Double, default 0.0): Amount restored to virtual health.
  - `target` (String, default "@player"): Validates if the target is a Player, interacts with `PlayerState.heal()`.
- **`APPLY_EFFECT` Mechanic:**
  - `effect` (String): PotionEffectType namespaced key (e.g., `"slowness"`).
  - `duration` (Double, default 5.0): Duration in seconds. If `-1`, converted to `PotionEffect.INFINITE_DURATION` (useful for `PASSIVE` triggers).
  - `amplifier` (Integer, default 1): Potion level (Config uses 1-based index, shifted to 0-based index internally).
  - `hide-particles` (Boolean, default false): Controls particle visibility.

## Notes / Edge Cases

- **Stat Recalculation:** Active `PASSIVE` mechanics execute their target logic directly on the `@player` during `StatManager.recalculateStats()` to apply persistent auras or effects.
- **Memory Optimization:** Custom mechanics parse their properties via `ExecutionContext.getDouble()` with defined fallbacks rather than failing outright.

---

# Module: Mobs

## Overview

The Mobs module defines custom entities with scaled stats, equipped items, and weighted loot tables. It leverages vanilla entity spawning but overrides behaviors, attributes, and drops.

## YAML Structure

```yaml
test_zombie:
  category: UNDEAD
  type: ZOMBIE
  level: 5
  base-damage: 5
  health: 30.0
  damage-type: MELEE
  base-xp: 2
  gold-reward: 5
  equipment:
    helmet: IRON_HELMET
    main-hand: IRON_SWORD
  loot-table:
    drops:
      - item: DIAMOND
        min-amount: 1
        max-amount: 1
        chance: 0.01
        luck-affected: true
```

## Configuration Options

### `category` / `type`

- **Type:** String
- **Default:** _Required_
- **Description:** `category` dictates the mob family (e.g., `UNDEAD`, `BOSS`). `type` dictates the underlying `EntityType`.
- **Code Reference:** `MobDefinitionParser.java`
- **Behavior:** `type` dictates the physical entity spawned via `World.spawnEntity()`.

### `health` / `base-damage` / `speed`

- **Type:** Double
- **Default:** `health: 0.0`, `base-damage: 5.0`, `speed: 0.0`
- **Description:** Core combat stats.
- **Code Reference:** `MobFactory.applyData()`
- **Behavior:** Injected directly into the entity's Bukkit `AttributeInstance` at spawn time. `base-damage` scales internally: `baseDamage + (level - 1)`.

### `level` / `base-xp` / `gold-reward`

- **Type:** Integer
- **Default:** `level: 1`, `base-xp: 2`, `gold: 0`
- **Description:** Dictates scaling and kill rewards.
- **Code Reference:** `MobDeathListener.java`
- **Behavior:** Total XP yielded is `baseXp * level`. Rewarded to the killer via `SkillXpGainEvent (COMBAT)`.

### `equipment.<slot>`

- **Type:** String
- **Default:** `null`
- **Description:** Defines gear. Slots: `helmet`, `chestplate`, `leggings`, `boots`, `main-hand`, `off-hand`.
- **Code Reference:** `MobDefinitionParser.java`
- **Behavior:** Fetched via `ItemManager.createItemStack()`. Supports both vanilla materials and Valmora custom item IDs. Applied in `MobFactory.applyEquipment()`.

### `loot-table.drops`

- **Type:** List of ConfigurationSections
- **Default:** `[]` (Empty List)
- **Description:** A list of items that the mob can drop upon death, including probability and quantity bounds.
- **Code Reference:** `MobDefinitionParser.parseLootEntry()`
- **Behavior:** Evaluated during `MobDeathListener.onMobDeath()`. Validates `item` against vanilla materials first, then against custom Valmora item IDs via `ItemManager`.

#### Loot Entry Options (Nested inside `drops`)

- **`item`** (String, _Required_): The material name or custom item ID to drop.
- **`min-amount`** (Integer, default `1`): The minimum stack size.
- **`max-amount`** (Integer, default `min-amount`): The maximum stack size. `getRandomAmount()` calculates a random integer between min and max.
- **`chance`** (Double, default `1.0`): The base probability of the drop occurring (0.0 to 1.0).
- **`luck-affected`** (Boolean, default `false`): If true, the killer's `LUCK` stat is factored into the drop chance.
  - _Calculation:_ `chance + (luck / 100.0) * chance`

## Notes / Edge Cases

- **Scaling:** A mob's `base-damage` scales with its `level` directly. The formula `baseDamage + (level - 1)` is injected into the entity attribute upon spawning.
- **Custom Name Overrides:** The engine forcibly overrides the entity's custom name in `MobFactory.applyVisuals()` to display its level, category, and health pool dynamically (e.g., `[Lv.5] Test Zombie 30.0/30.0❤`).
- **Economy Integration:** `gold-reward` is parsed and extracted but currently explicitly marked as `// TODO: Integrate with Economy system` in `MobDeathListener.java`.

---

# Module: Recipes

## Overview

The Recipe module is a flexible crafting engine that operates independently of vanilla Bukkit recipes. Recipes are bound to specific `machine` identifiers, allowing them to bridge directly with `GUI` module instances. It supports `SHAPED`, `SHAPELESS`, and `EXACT_SLOT` matching algorithms, fully supporting custom Valmora item ingestion and script execution upon successful crafts.

## YAML Structure

```yaml
test_sword_craft:
  machine: crafting_table
  type: SHAPED
  inputs:
    "0": { item: DIAMOND, amount: 1 }
    "3": { item: EMERALD, amount: 1 }
    "6": { item: STICK, amount: 1 }
  outputs:
    result: { item: testSword, amount: 1 }
  on-craft:
    - "sound player block.anvil.use"
    - "give EMERALD:1 notify"
```

## Configuration Options

### `machine`

- **Type:** String
- **Default:** _Required_
- **Description:** Associates the recipe with a specific GUI interface.
- **Code Reference:** `RecipeDefinitionParser.java` / `RecipeEngine.match()`
- **Behavior:** When a player alters items in a GUI marked with `machine: crafting_table`, the `RecipeEngine` only iterates through recipes that share this `machine` ID.

### `type`

- **Type:** String
- **Default:** `"EXACT_SLOT"`
- **Description:** Defines the pattern matching algorithm used for validation (`EXACT_SLOT`, `SHAPELESS`, `SHAPED`).
- **Code Reference:** `RecipeDefinitionParser.java` / `RecipeEngine.java`
- **Behavior:**
  - `EXACT_SLOT`: Strictly checks that items are placed in the exact slot index defined by the key (e.g., Anvils, Forges). Fails if extra items exist in unmapped slots.
  - `SHAPELESS`: Ignores slot placement entirely. Checks if the total payload of the inventory contains the required items and amounts.
  - `SHAPED`: Translates the numeric slot keys into a mathematical 2D grid offset. The engine scans the input inventory for the relative shape, allowing a 2x2 recipe to be crafted in any corner of a 3x3 GUI.

### `inputs`

- **Type:** ConfigurationSection or List of Maps
- **Default:** _Required_
- **Description:** The required ingredients to fulfill the recipe.
- **Code Reference:** `RecipeDefinitionParser.java`
- **Behavior:**
  - For `SHAPED` and `EXACT_SLOT`, this is a ConfigurationSection where the key is the string representation of the slot index (e.g., `"0"`, `"1"`, `"input1"`).
  - For `SHAPELESS`, this is evaluated as a List of Maps containing `item` and `amount` (slot indexes are irrelevant).
  - Ingredient parsing utilizes `ItemManager` cross-referencing. It evaluates `item` as a Valmora Item ID first, then falls back to Vanilla Material checking.

### `outputs`

- **Type:** ConfigurationSection
- **Default:** `null`
- **Description:** Defines the items produced when the recipe is crafted.
- **Code Reference:** `RecipeDefinitionParser.java` / `GuiListener.updateRecipeOutput()`
- **Behavior:** Iterates over sub-keys (usually `"result"`). The resulting item is injected into the GUI slot configured as the `OUTPUT` component.

### `on-craft`

- **Type:** List of Strings (Script Event DSL)
- **Default:** `null`
- **Description:** A list of Valmora Script Engine commands executed immediately after the craft completes.
- **Code Reference:** `RecipeDefinitionParser.java` / `GuiListener.handleOutputClick()`
- **Behavior:** Evaluated by `EventParser.parseList()`. Dispatched with the crafting player set as the `ExecutionContext` caster. Triggered per-craft (e.g., shift-clicking to craft 64 items will trigger the script 64 times).

## Notes / Edge Cases

- **Shift-Click Safety Limits:** `GuiListener.handleOutputClick()` manages mass-crafting (shift-click). The engine locks the `Recipe ID` at the start of the loop. If materials run out and the remaining items accidentally map to a _different_ valid recipe, the engine aborts to prevent unintentional morphing (e.g., crafting a block, running out of materials, and accidentally crafting a slab with the leftovers).
- **Inventory Overflow Protection:** The recipe engine pre-calculates available inventory space using `canFit()` before committing a mass craft. If the player's inventory fills up, the loop aborts without consuming further materials.
- **Vanilla Integration Override:** If the `RecipeEngine` fails to match a custom recipe for a specific grid layout, it falls back to checking Bukkit's vanilla recipe registry (`org.bukkit.Bukkit.getCraftingRecipe()`) seamlessly.

---

# Cross-Module Interactions Overview

The Valmora Engine relies on tight cross-module interoperability:

1. **Items + Mobs:** The `Mobs` module relies entirely on the `Items` registry to generate entity armor, weapons, and custom loot drops (`ItemManager.createItemStack()`).
2. **GUIs + Recipes:** `GUIs` are strictly visual and slot-management wrappers. When a slot mapped as `INPUT` changes via `GuiListener`, the GUI module requests the `Recipes` module (`RecipeEngine.match()`) to calculate valid outputs for its `machine` ID.
3. **Script Engine + Everything:** The Script Engine acts as the central nervous system. It binds UI elements (Pagination logic via `$player.skill.X$`), executes Item Mechanics (`APPLY_EFFECT`, `DAMAGE`), and handles GUI interactivity (`on-open`, `on-click`, `on-craft`).
