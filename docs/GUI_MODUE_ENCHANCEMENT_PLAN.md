To complete the GUI module and fulfill the requirements for the overridden vanilla machines (Enchanting Table, Anvil, Alchemy Table), we need to introduce a few targeted architectural extensions. Instead of hardcoding logic into the `GuiListener`, we will extend existing abstractions (like the `RecipeEngine` and `VariableProvider` systems) to support dynamic transformations.

Here is the implementation plan and architectural roadmap to finish the GUI system.

---

### Phase 1: Core Framework Extensions

Currently, GUIs only support static recipes and fixed update intervals. We need to add immediate reactivity and dynamic recipe generation.

#### 1. Immediate Slot Update Triggers (`on-slot-update`)

**Problem:** Players shouldn't have to wait for the `update-interval` for a recipe or enchant list to appear.
**Implementation:**

- **`GuiDefinition.java` & `GuiDefinitionParser.java`:** Add an `on-slot-update` field of type `GuiEventBlock`.
- **`GuiListener.java`:** In `onInventoryClick` and `onInventoryDrag`, after modifying an `InputComponent` slot, asynchronously call the new `on-slot-update` block and immediately invoke `new GuiRenderer(plugin).render(session)` to force a frame update.

#### 2. Dynamic Machine Handlers in RecipeEngine

**Problem:** The Anvil and Enchanting Table do not have static YAML recipes. Their outputs depend on complex math, NBT data, and item states.
**Implementation:**

- Create a new interface `DynamicMachineHandler`:
  ```java
  public interface DynamicMachineHandler {
      Optional<RecipeDefinition> match(Map<String, ItemStack> inputs);
  }
  ```
- **`RecipeEngine.java`:** Add a `Map<String, DynamicMachineHandler> dynamicHandlers`. Update the `match()` method so that if a `machineId` exists in the dynamic handlers, it delegates to it. The handler will return a dynamically generated `RecipeDefinition` (using the existing `RecipeDefinition.vanilla()` or builder pattern) containing the exact output item and `on-craft` scripts.

#### 3. GUI Variable Provider (`$gui.input.*$`)

**Problem:** The enchanting GUI needs to loop over available enchantments based on what is placed in the input slot.
**Implementation:**

- Create `GuiVariableProvider` handling the `gui` namespace.
- Resolve paths like `$gui.input.<slotId>.item_type$` or `$gui.input.<slotId>.available_enchants$`.
- If `available_enchants` is requested, fetch the `ItemStack` from `session.getInputSnapshot()`, determine its `ItemType`, and return a `List<EnchantmentDefinition>` by filtering `EnchantModule.getRegistry()`.

---

### Phase 2: Enchanting Table Implementation

**Goal:** Loop over applicable enchants for the input item, allowing the player to click to apply them, costing XP/Mana.

**Code Plan:**

1.  **YAML Setup (`enchanting.yml`):**
    - Add `on-slot-update` to trigger a re-render.
    - Set the paginated list to `$gui.input.ingredient.available_enchants$`.
2.  **Enchant Event Factory (`enchant_apply`):**
    - Create `EnchantApplyEvent implements EventFactory`.
    - Syntax: `enchant_apply <inputSlotId> <enchantId> <level>`.
    - **Logic:** Reads the item from the session's input slot, deducts player XP/resources, calls `EnchantmentHelper.applyEnchantment()`, and forces a GUI re-render.
3.  **UI Iteration:**
    - Modify `GuiRenderer.java` to support injecting the properties of the resolved enchant object into the UI (e.g., `$enchant.name$`, `$enchant.description$`, `$enchant.etableMaxLevel$`).

---

### Phase 3: Anvil Implementation

**Goal:** Merge items, combine Valmora enchantments with specific math, support Enchanted Books, and deduct costs.

**Code Plan:**

1.  **`AnvilMachineHandler implements DynamicMachineHandler`:**
    - Registered to the `anvil` machine ID in `RecipeEngine`.
    - Reads `input1` (base) and `input2` (material/book) from the snapshot.
    - **Logic Flow:**
      - If `input2` is an Enchanted Book, extract its Valmora enchants via NBT.
      - Iterate enchants. Math rule: `If E1 == E2 -> Level + 1 (capped at absoluteMaxLevel). If E1 > E2 -> E1. If E1 < E2 -> E2.`
      - Check for enchantment conflicts (`EnchantmentDefinition.conflictsWith()`).
    - **Result:** Builds a clone of `input1`, applies the new enchants via `EnchantmentHelper`, and returns a dynamically constructed `RecipeDefinition` where the `output` is the merged item.
2.  **Anvil Cost & On-Craft:**
    - The dynamically generated `RecipeDefinition` will have an `on-craft` script injected (e.g., `variable add player.var.coins -50` or an XP deduction event).

---

### Phase 4: Alchemy Table (Brewing) Implementation

**Goal:** Support time-based crafting (brewing) that visually updates in the GUI.

**Code Plan:**

1.  **Stateful Session Variables:**
    - Utilize `GuiSession.getProps()` to store local GUI state (e.g., `brew_time_remaining: 100`).
2.  **Tick-Based Task Interaction:**
    - Create a `machine: alchemy` definition in YAML with an `update-interval: 20` (1 second).
    - **`on-update` Block:** Add an `on-update` event block to `GuiDefinition`. Every time the update task ticks, it evaluates this script.
    - **Script Logic (`alchemy.yml`):**
      ```yaml
      on-update:
        conditions:
          - "$gui.input.ingredient.material$ == NETHER_WART"
          - "$gui.input.base.material$ == WATER_BOTTLE"
          - "$prop.brew_time_remaining$ > 0"
        actions:
          - "variable add prop.brew_time_remaining -1"
      ```
3.  **Brew Completion Event:**
    - When `$prop.brew_time_remaining$ == 0`, a script command `gui_force_craft` moves the items to the output and resets the timer prop.
    - Add conditional display states to the UI components (e.g., progress bar panes turning green based on `$prop.brew_time_remaining$`).

---

### Phase 5: Documentation & Optimization Tasks

1.  **YAML Docs Updates (`docs/YAML_DOCS.md`):**
    - Add a section under `Module: GUIs` detailing the `on-slot-update` and `on-update` triggers.
    - Document the `$gui.input.*$` variable provider.
2.  **Script DSL Updates (`docs/VALMORA_DOCUMENTATION.md`):**
    - Add `enchant_apply` and `gui_force_craft` to the Event DSL Reference.
3.  **Optimization Considerations:**
    - **Pagination Caching:** Currently, `GuiRenderer` re-evaluates expressions heavily. When generating the Dynamic Recipe in the Anvil, ensure `EnchantmentHelper.getEnchantments()` is cached per-tick to avoid aggressive NBT string parsing on 0-tick slot updates.
    - **Dupe Protection:** Ensure `AnvilMachineHandler` locks the exact item UUIDs/amounts during the dynamic recipe preview so players can't swap the item instantly before `handleOutputClick` processes the craft. (Already mostly handled by the `RecipeEngine` snapshot pattern, but needs rigorous testing during mass-shift clicks).
