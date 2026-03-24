🛡️ Valmora Ability System - Final Architecture Blueprint

1. Core Data Models
   AbilityTrigger (Enum): Defines when an ability fires.
   Values: RIGHT_CLICK, LEFT_CLICK, PASSIVE, EQUIP, UNEQUIP.
   AbilityDefinition (Class): Represents the parsed YAML ability.
   Fields: String id, String name, AbilityTrigger trigger, double targetRange, double cooldown, double manaCost, List<String> description, List<ConfiguredMechanic> mechanics.
   ConfiguredMechanic (Class): A wrapper that holds a resolved AbilityMechanic and its parsed YAML ConfigurationSection parameters.
2. The Extensible API (Mechanic System)
   AbilityMechanic (Interface): The contract for all effects.
   code
   Java
   public interface AbilityMechanic {
   String getId();
   void execute(Player caster, LivingEntity target, ConfigurationSection params);
   }
   MechanicRegistry (Class): A dedicated registry class (just like ItemRegistry) initialized inside AbilityManager. Add-on developers will call Valmora.getInstance().getAbilityManager().getMechanicRegistry().register(...).
   Default Mechanics (Implementations):
   DamageMechanic (Bypasses vanilla, uses your DamageApplier).
   HealMechanic (Hooks into your PlayerState.heal()).
   ApplyEffectMechanic (Adds Bukkit PotionEffects).
3. Management & Execution
   CooldownManager (Class): A fast, memory-efficient map storing Map<UUID, Map<String, Long>> to track when a player's ability cooldown expires. Includes utility methods to check and format remaining time.
   AbilityManager (Class): The central hub initializing the registries, default mechanics, and holding the CooldownManager.
   AbilityListener (Class): Listens to Bukkit events.
   Intercepts PlayerInteractEvent.
   Checks the item in hand for RIGHT_CLICK / LEFT_CLICK abilities.
   Performs the Raycast (using player.getTargetEntity(range)) if target-range > 0.
   Validates Mana & Cooldowns.
   Executes the configured mechanics sequentially.
4. System Integrations (Modifying Existing Code)
   ItemDefinition & ItemDefinitionParser: Will be upgraded to parse the abilities: YAML block. It will resolve mechanics via the MechanicRegistry during server load (fail-fast architecture).
   ItemFactory: Will automatically generate the ability lore. It will replace variables (like {mana-cost}) and append a beautiful, standardized ability block to the bottom of the item's lore.
   PlayerListener: Inside recalculate(Player), we will loop through the player's equipped/held items to apply PASSIVE abilities (infinite potion effects) and remove effects from unequipped items. We will also fire EQUIP and UNEQUIP abilities here.
   The Execution Flow (Locked)
   Server Boot: MechanicRegistry loads default mechanics -> ItemLoader parses items.yml -> Looks up mechanics in registry -> Creates ItemDefinitions.
   Player Action: Player right-clicks with "Edge Katana".
   Raycast & Validate: AbilityListener checks raycast. If no target is found, it sends an action bar error. If a target is found, it checks PlayerState for Mana and CooldownManager for time.
   Deduction: 100 Mana is removed from PlayerState, UI updates automatically, Cooldown is set to 10s.
   Execution: The DamageMechanic, HealMechanic, and ApplyEffectMechanic all fire instantly, interacting cleanly with your custom health/damage systems.
