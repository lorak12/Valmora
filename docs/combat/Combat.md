## Design Document: Custom Combat System

### 1. Overview and Goals

The goal is to create a fully custom combat system that intercepts default Minecraft damage events, calculates damage based on a custom formula, applies it to the target, and provides clear visual feedback to the player.

**Core Principles:**

- **Decoupling:** Visuals (damage indicators) should be separate from logic (damage calculation).
- **Extensibility:** The system must be easy to extend with new damage types, stats, and mechanics.
- **Performance:** The system must be efficient, especially the visual components, to avoid server lag.
- **Clarity:** The system should provide clear and immediate feedback to the player about the outcome of their actions.

### 2. Core Components

The system will be built from several distinct, specialized components:

1.  **`CombatListener`**: The entry point. This class listens for `EntityDamageByEntityEvent` and orchestrates the entire process.
2.  **`DamageCalculator`**: A dedicated service class responsible for executing the damage formula. It takes in attacker and victim data and returns a `DamageResult`.
3.  **`DamageResult` (Data Class)**: A simple POJO to hold the outcome of a calculation before it's applied. This is crucial for passing data between components cleanly. It would contain:
    - `double finalDamage`
    - `DamageType damageType`
    - `boolean isCritical`
    - `Entity attacker`
    - `LivingEntity target`
4.  **`DamageApplier`**: A service that takes a `DamageResult` and applies the effects to the target (reducing health, playing sounds, etc.).
5.  **`DamageIndicatorManager`**: A service responsible for spawning, formatting, and managing the lifecycle of the floating text displays.
6.  **`DamageType` (Enum)**: An enum to define different types of damage, each holding associated properties like color.

### 3. The Combat Flow: Step-by-Step

This is the sequence of events for a single player-to-mob attack:

1.  **Event Interception**:
    - The `CombatListener` listens for `EntityDamageByEntityEvent` at a `HIGH` or `HIGHEST` priority.
    - It performs initial checks:
      - Is the damager a `Player`?
      - Is the entity a `LivingEntity` (and not another player, unless PvP is intended)?
      - Is the event already cancelled by another plugin? (`@EventHandler(ignoreCancelled = true)`)

2.  **Vanilla Damage Cancellation**:
    - Immediately call `event.setDamage(0.01);` or `event.setDamage(0);`.
    - **Why not `event.setCancelled(true)`?** Cancelling the event stops all subsequent effects, including the "red flash" visual on the mob, knockback, and sounds. Setting damage to a near-zero value (or zero in modern versions) preserves these crucial visual cues while preventing vanilla health reduction.

3.  **Data Gathering**:
    - The listener retrieves the attacker (`Player`) and the target (`LivingEntity`).
    - It fetches the necessary stats from your `ValmoraPlayer` profile for the attacker (e.g., DAMAGE, STRENGTH, CRIT_CHANCE, CRIT_DAMAGE).
    - It could also fetch stats from the target if you plan to implement mob-specific defenses.

4.  **Damage Calculation**:
    - The listener passes the attacker and target objects (or their relevant stats) to the `DamageCalculator`.
    - The `DamageCalculator` executes its formula (see Section 4) and determines the final damage, whether it's a critical hit, and the damage type.
    - It returns a populated `DamageResult` object.

5.  **Custom Damage Application**:
    - The listener passes the `DamageResult` to the `DamageApplier`.
    - The `DamageApplier` applies the damage to the target: `target.setHealth(target.getHealth() - damageResult.getFinalDamage());`.
    - **Important**: This bypasses all vanilla protections (armor, enchantments) because you've already factored them into your formula (or chosen to ignore them).

6.  **Visual Feedback**:
    - The listener passes the `DamageResult` to the `DamageIndicatorManager`.
    - The `DamageIndicatorManager` spawns a damage indicator at the target's location (see Section 5).

### 4. Damage Calculation Formula Design

The `DamageCalculator` should be flexible. A good starting point for a formula:

```
// 1. Determine if the hit is critical
isCritical = (random_chance < player.getStat(CRIT_CHANCE))

// 2. Calculate the base weapon/stat damage
baseDamage = player.getStat(DAMAGE) + (player.getStat(STRENGTH) / 5) // Example formula

// 3. Apply critical hit modifier if applicable
critMultiplier = isCritical ? (1 + player.getStat(CRIT_DAMAGE) / 100) : 1
damageAfterCrit = baseDamage * critMultiplier

// 4. Apply target's defense (optional but recommended)
// mobDefense = getMobDefense(target)
// defenseMultiplier = 1 - (mobDefense / (mobDefense + 100)) // Example RPG formula
// finalDamage = damageAfterCrit * defenseMultiplier

finalDamage = damageAfterCrit // Simplified version without defense
```

**`DamageType` Enum Implementation:**

```java
import net.kyori.adventure.text.format.TextColor;

public enum DamageType {
    PHYSICAL(TextColor.color(0xFFFFFF)), // White
    FIRE(TextColor.color(0xFFAA00)),     // Orange
    POISON(TextColor.color(0x00AA00)),   // Green
    MAGIC(TextColor.color(0x5555FF));    // Blue

    private final TextColor color;

    DamageType(TextColor color) {
        this.color = color;
    }

    public TextColor getColor() {
        return color;
    }
}
```

### 5. Damage Indicator System (Visuals)

This is a performance-critical component. The modern and best way to do this is with **`TextDisplay` entities**, not Armor Stands.

**Why `TextDisplay` entities?**

- **Performance:** They are far more lightweight than Armor Stands.
- **Flexibility:** You can control background color, alignment, and more.
- **Cleanliness:** They are designed specifically for this purpose.

**`DamageIndicatorManager` Logic:**

1.  **Spawning:**
    - Get the target's location: `target.getEyeLocation()`.
    - Add a random offset to prevent indicators from stacking perfectly:
      ```java
      Random random = new Random();
      double offsetX = (random.nextDouble() - 0.5) * 1.5; // Random value between -0.75 and +0.75
      double offsetY = (random.nextDouble() - 0.5) * 0.5;
      double offsetZ = (random.nextDouble() - 0.5) * 1.5;
      Location spawnLocation = target.getEyeLocation().add(offsetX, offsetY, offsetZ);
      ```
    - Spawn the `TextDisplay` entity: `world.spawn(spawnLocation, TextDisplay.class, display -> { ... });`

2.  **Formatting (using Adventure API):**
    - Inside the spawn consumer, format the text based on the `DamageResult`.
    - Use Adventure's `Component` builder, which is far superior to legacy chat codes.
    - **Example:**

      ```java
      display.text(createComponent(damageResult));

      private Component createComponent(DamageResult result) {
          String damageText = String.valueOf(Math.round(result.getFinalDamage()));
          TextColor color = result.getDamageType().getColor();

          if (result.isCritical()) {
              // Example: BOLD, bigger text with symbols
              return Component.text("✧ ", TextColor.color(0xFFFF55))
                     .append(Component.text(damageText, color, TextDecorations.BOLD))
                     .append(Component.text(" ✧", TextColor.color(0xFFFF55)));
          } else {
              // Normal hit
              return Component.text(damageText, color);
          }
      }
      ```

3.  **Lifecycle (Animation & Despawn):**
    - The indicator needs to float up and disappear.
    - Create a `BukkitRunnable` that runs every tick for about 1-1.5 seconds (20-30 ticks).
    - In the `run()` method:
      - Teleport the `TextDisplay` entity upwards slightly: `display.teleport(display.getLocation().add(0, 0.05, 0));`
      - Decrease its opacity over time (if desired).
      - After the duration, call `display.remove()` and `cancel()` the task.

### 6. Edge Cases and Further Considerations

This is what separates a good system from a great one.

- **Performance at Scale:** What happens when 10 players use Area of Effect (AoE) attacks on 50 mobs? Spawning 500 damage indicators can cause massive client-side FPS drops and some server load.
  - **Solution:** Implement a **rate limit**. Don't spawn an indicator for every single instance of damage. For rapid attacks (e.g., DoTs), maybe only show one indicator every 0.5 seconds that aggregates the damage. For AoE, consider showing one larger indicator at the center of the effect.

- **Damage Over Time (DoT):** Your `FIRE` and `POISON` types imply DoTs. The `EntityDamageByEntityEvent` only fires on the initial hit.
  - **Solution:** When a player applies a DoT, store it on the target (e.g., in a `Map<UUID, DoTInstance>`). Run a separate `BukkitRunnable` every second that iterates through active DoTs, applies damage via the `DamageApplier`, and spawns an indicator.

- **Final Hit & Kill Attribution:** Your `target.setHealth()` call might not correctly trigger `EntityDeathEvent` with the player as the killer if the damage is exactly equal to remaining health.
  - **Solution:** The robust way is to use `target.damage(finalDamage, attacker)`. However, this can re-trigger vanilla mechanics. The common compromise is to check `if (newHealth <= 0)` after your `setHealth` call and, if so, manually kill the mob: `target.setHealth(0);`. This usually attributes the kill correctly.

- **Plugin Compatibility:** Other plugins might modify damage.
  - **Solution:** Listen at a `HIGHEST` priority to have the "last word" on the damage value. Be aware that your system will override mechanics from plugins like McMMO or MythicMobs unless you specifically add compatibility hooks.

- **Non-Player Damage:** What about custom mobs attacking players? Or environment damage?
  - **Solution:** Your listener should be specific to `damager instanceof Player`. You can create separate logic or listeners for other damage scenarios if you want your custom stat system to apply defensively as well.

- **Invulnerability Frames:** `event.setDamage(0)` preserves vanilla invulnerability ticks (the brief period after being hit where a mob can't be hit again). If you want faster attacks, you may need to manage this manually, which is complex. For now, sticking with the default is recommended.
