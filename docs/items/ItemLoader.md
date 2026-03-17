Design Document: Extensible YAML Item Loader

1.  Overview
    This document outlines the design for an item loading system that parses item definitions from YAML files, validates the data, and stores them in a central registry. The system is designed with extensibility in mind, allowing for the easy addition of new item properties in the future. A primary focus is on providing clear, concise, and user-friendly error messages for configuration issues, avoiding verbose stack traces for common mistakes.
2.  Core Components
    The system will be built around a few key components:
    ItemLoader: A service class responsible for discovering and reading all .yml files from the /items directory. It orchestrates the parsing and validation process and populates the ItemRegistry.
    ItemRegistry: A singleton or service class that holds all the successfully loaded ItemDefinition objects, indexed by a unique ID (e.g., testSword). It provides methods to retrieve item definitions and create ItemStack objects from them.
    ItemDefinition: A Plain Old Java Object (POJO) that represents the deserialized data from the YAML file for a single item. This class will use the Builder Pattern for its construction to enhance readability and extensibility.[1]
    ItemDefinitionParser: A class responsible for taking a Bukkit ConfigurationSection and attempting to parse it into an ItemDefinition. This is where the primary validation logic will reside.
    ItemFactory: A class that takes an ItemDefinition and creates a fully functional ItemStack with all the specified metadata, stats, and other properties.
    LoadResult: A custom wrapper class that will contain either the successfully loaded items or a list of user-friendly error messages. This helps in avoiding exceptions for predictable configuration errors.
3.  YAML File Structure & Parsing
    The loader will process all .yml files within the /items directory of the plugin's data folder. Each file can contain one or more item definitions. The top-level keys in each file will serve as the unique ID for the item.
    Example swords.yml:
    code
    Yaml
    testSword:
    name: "<gold>Test Sword"
    material: "DIAMOND_SWORD"
    rarity: "RARE"
    item-type: "SWORD"
    lore: - "<gray>A sword for testing." - "<gray>It's quite pointy."
    stats:
    DAMAGE: 10
    STRENGTH: 5
    CRIT_CHANCE: 20
    CRIT_DAMAGE: 50
    Parsing Process:
    The ItemLoader will iterate through all .yml files in the specified directory.
    For each file, it will use Bukkit's YamlConfiguration.loadConfiguration(file) method to get a FileConfiguration object.[2][3]
    It will then iterate over the top-level keys (testSword in the example). Each key represents an item to be loaded.
    The corresponding ConfigurationSection for each item is passed to the ItemDefinitionParser.
4.  Data Validation and Error Handling
    This is a critical part of the design, aiming for "Rust-style" error reporting. Instead of throwing exceptions for invalid data, the ItemDefinitionParser will return a Result-like object.
    LoadResult Class:
    A simple implementation could look like this:
    code
    Java
    public class LoadResult<T, E> {
    private final T value;
    private final E error;

        private LoadResult(T value, E error) {
            this.value = value;
            this.error = error;
        }

        public static <T, E> LoadResult<T, E> success(T value) {
            return new LoadResult<>(value, null);
        }

        public static <T, E> LoadResult<T, E> failure(E error) {
            return new LoadResult<>(null, error);
        }

        // Getters, isSuccess(), etc.

    }
    This allows methods to return a value on success or a detailed error message on failure, which can then be aggregated and presented to the user.
    Validation within ItemDefinitionParser:
    The parser will validate each field and generate specific error messages.
    material: The value will be checked against Material.matchMaterial().
    Error: [items/swords.yml] In item 'testSword': Invalid material 'DIAMOND_SORD'. Did you mean 'DIAMOND_SWORD'?
    name / lore: Strings will be checked for validity, but generally are permissive. They will be parsed for color codes.
    Enums (rarity, item-type): The parser will try to match the string to an enum constant.
    Error: [items/swords.yml] In item 'testSword': Invalid rarity 'RARETY'. Valid options are: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY.
    stats: The stat names will be validated against a predefined list or enum of valid stats. The values will be checked to ensure they are numbers.
    Error: [items/swords.yml] In item 'testSword': Unknown stat 'CRITICAL_CHANCE'. Did you mean 'CRIT_CHANCE'?
    Error: [items/swords.yml] In item 'testSword': Stat 'DAMAGE' must be a number, but found 'ten'.
    At the end of the loading process, if any errors were collected, they will be neatly printed to the console in a formatted block, and the plugin can choose to disable itself or proceed without the invalid items.
    Example Console Output:
    code
    Code
    [YourPlugin] Failed to load some items. Please check your configuration files.

---

- [items/swords.yml] In item 'testSword': Invalid material 'DIAMOND_SORD'. Did you mean 'DIAMOND_SWORD'?
- [items/bows.yml] In item 'ghostBow': Missing required field 'material'.
- [items/bows.yml] In item 'flameBow': Unknown stat 'FIRE_POWER'. Valid stats are: DAMAGE, STRENGTH, ...

---

5.  Item Creation and Registry
    ItemDefinition and the Builder Pattern
    The ItemDefinition class will hold the validated and parsed data. The Builder pattern makes instantiation clean and supports many optional fields.[1][4]
    code
    Java
    public class ItemDefinition {
    private final String id;
    private final String name;
    private final Material material;
    private final Rarity rarity;
    // ... other fields

        private ItemDefinition(Builder builder) {
            this.id = builder.id;
            // ...
        }

        // Getters for all fields

        public static class Builder {
            private final String id;
            private String name;
            private Material material;
            // ...

            public Builder(String id) {
                this.id = id;
            }

            public Builder name(String name) { this.name = name; return this; }
            public Builder material(Material material) { this.material = material; return this; }
            // ... other builder methods

            public ItemDefinition build() {
                // Optional: Final validation before creating the object
                return new ItemDefinition(this);
            }
        }

    }
    ItemFactory
    The ItemFactory is responsible for converting an ItemDefinition into an ItemStack.
    It creates a new ItemStack of the specified material.
    It retrieves the ItemMeta from the ItemStack.
    It sets the display name and lore, translating color codes.
    Custom Data: Stats and other custom properties (rarity, item-type) should be stored in the item's PersistentDataContainer (PDC). This is the modern, robust way to attach custom data to items.
    The ItemFactory will get the PDC from the ItemMeta.
    It will loop through the stats in the ItemDefinition and store them, e.g., pdc.set(new NamespacedKey(plugin, "stat_damage"), PersistentDataType.INTEGER, 10);.
    It applies any other vanilla properties (enchants, flags, etc.) to the ItemMeta.
    Finally, it sets the modified ItemMeta back on the ItemStack and returns it.
    ItemRegistry
    This class will simply be a Map<String, ItemDefinition>. It will expose methods like:
    registerItem(ItemDefinition definition): Adds a loaded item to the registry.
    getItem(String id): Returns an Optional<ItemDefinition> for the given ID.
    createItemStack(String id): A convenience method that gets the definition and uses the ItemFactory to return a new ItemStack.

6.  Extensibility
    The design allows for easy extension:
    Adding New Fields:
    Add the new field to the ItemDefinition and its Builder.
    In the ItemDefinitionParser, add a new parsing and validation step for the new field. For example, to add an unbreakable boolean field, you would add builder.unbreakable(section.getBoolean("unbreakable", false));.
    In the ItemFactory, add the logic to apply this new property to the ItemStack (e.g., meta.setUnbreakable(definition.isUnbreakable());).
    Adding New Stats:
    If you have a Stat enum, simply add the new enum constant to it. The ItemDefinitionParser will automatically recognize it as a valid stat.
    The ItemFactory will already be looping through all provided stats, so no changes are needed there.
    Adding New Item Types with Special Logic:
    You could introduce an ItemType enum (SWORD, BOW, ARMOR).
    The ItemFactory can have different logic based on the ItemType from the ItemDefinition, allowing you to apply type-specific attributes.
    By centralizing the parsing logic in ItemDefinitionParser and the creation logic in ItemFactory, you create a clean and maintainable system that is easy to extend without modifying the core loading loop.
7.  Example Usage in Plugin
    code
    Java
    public class YourPlugin extends JavaPlugin {

        private ItemRegistry itemRegistry;
        private ItemLoader itemLoader;

        @Override
        public void onEnable() {
            this.itemRegistry = new ItemRegistry();
            this.itemLoader = new ItemLoader(this, itemRegistry);

            // Load all items on startup
            itemLoader.loadItems();

            // Example of giving an item to a player
            getCommand("givetestsword").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    itemRegistry.createItemStack("testSword").ifPresent(item -> {
                        player.getInventory().addItem(item);
                    });
                }
                return true;
            });
        }

    }
