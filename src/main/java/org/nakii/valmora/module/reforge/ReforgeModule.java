package org.nakii.valmora.module.reforge;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.item.ItemDefinition;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.item.Rarity;
import org.nakii.valmora.module.recipe.DynamicMachineHandler;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.module.stat.StatDefinition;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ReforgeModule implements ReloadableModule, DynamicMachineHandler {

    /** Coin cost to randomly reforge an item, keyed by the item's rarity. */
    private static final Map<Rarity, Integer> RARITY_COST = new EnumMap<>(Rarity.class);
    static {
        RARITY_COST.put(Rarity.COMMON,    250);
        RARITY_COST.put(Rarity.UNCOMMON,  500);
        RARITY_COST.put(Rarity.RARE,     1000);
        RARITY_COST.put(Rarity.EPIC,     2500);
        RARITY_COST.put(Rarity.LEGENDARY,5000);
        RARITY_COST.put(Rarity.MYTHIC,  10000);
        RARITY_COST.put(Rarity.DIVINE,  15000);
    }

    private final Valmora plugin;
    private final Map<String, ReforgeDefinition> definitions = new HashMap<>();

    public ReforgeModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        definitions.clear();
        loadDefinitions();

        // Reforge anvil: item + specific stone → apply exact reforge, cost by item rarity
        plugin.getRecipeModule().getRecipeEngine().registerHandler("reforge", this);
        plugin.getRecipeModule().getRecipeEngine().registerHandler("reforge_anvil", new DynamicMachineHandler() {
            @Override public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) { return Optional.empty(); }
            @Override public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, Player player) {
                return matchReforgeAnvil(inputs, player);
            }
        });

        // Forge (random): single item input → random reforge excluding current, cost by item rarity
        plugin.getRecipeModule().getRecipeEngine().registerHandler("forge_random", new DynamicMachineHandler() {
            @Override public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) { return Optional.empty(); }
            @Override public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, Player player) {
                return matchForgeRandom(inputs, player);
            }
        });
    }

    @Override
    public void onDisable() {
        definitions.clear();
    }

    @Override
    public String getId() { return "reforge"; }

    @Override
    public String getName() { return "Reforge System"; }

    public Collection<ReforgeDefinition> getDefinitions() { return definitions.values(); }

    public ReforgeDefinition getDefinition(String id) { return definitions.get(id.toLowerCase()); }

    // ─── DynamicMachineHandler: "reforge" machine (reforge.yml — stone-based) ───

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) {
        return Optional.empty(); // player context required
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
        return matchReforgeAnvil(inputs, player);
    }

    // ─── "reforge_anvil" machine: item + specific stone → exact reforge ───

    private Optional<RecipeDefinition> matchReforgeAnvil(Map<String, ItemStack> inputs, @Nullable Player player) {
        ItemStack baseItem = inputs.get("base_item");
        ItemStack reforgeStone = inputs.get("reforge_stone");

        if (isEmpty(baseItem) || isEmpty(reforgeStone)) return Optional.empty();

        // The stone carries a single reforge ID in its pool
        String poolRaw = reforgeStone.getItemMeta() != null
                ? reforgeStone.getItemMeta().getPersistentDataContainer()
                    .get(Keys.REFORGE_POOL_KEY, PersistentDataType.STRING)
                : null;
        if (poolRaw == null || poolRaw.isBlank()) return Optional.empty();

        // Use first ID — auto-generated stones always have exactly one
        String reforgeId = poolRaw.split(",")[0].trim().toLowerCase();
        ReforgeDefinition def = definitions.get(reforgeId);
        if (def == null) return Optional.empty();

        ItemType itemType = readItemType(baseItem);
        if (!def.appliesTo(itemType)) return Optional.empty();

        Rarity rarity = readRarity(baseItem);
        int cost = RARITY_COST.getOrDefault(rarity, 250);

        if (!checkAndNotifyCoins(player, cost)) return Optional.empty();

        ItemStack output = buildReforgedItem(baseItem, def, rarity);
        return Optional.of(RecipeDefinition.dynamic("reforge_anvil", output, inp -> {
            consumeItem(inp, "reforge_stone");
            consumeItem(inp, "base_item");
            deductCoins(player, cost);
        }));
    }

    // ─── "forge_random" machine: single item → random reforge excluding current ───

    private Optional<RecipeDefinition> matchForgeRandom(Map<String, ItemStack> inputs, @Nullable Player player) {
        ItemStack baseItem = inputs.get("base_item");
        if (isEmpty(baseItem)) return Optional.empty();

        ItemType itemType = readItemType(baseItem);
        Rarity rarity = readRarity(baseItem);

        // Current reforge on the item (excluded from candidates)
        String currentReforgeId = baseItem.getItemMeta() != null
                ? baseItem.getItemMeta().getPersistentDataContainer()
                    .get(Keys.REFORGE_ID_KEY, PersistentDataType.STRING)
                : null;

        List<ReforgeDefinition> eligible = new ArrayList<>();
        for (ReforgeDefinition def : definitions.values()) {
            if (def.appliesTo(itemType) && !def.getId().equals(currentReforgeId)) {
                eligible.add(def);
            }
        }
        if (eligible.isEmpty()) return Optional.empty();

        ReforgeDefinition chosen = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        int cost = RARITY_COST.getOrDefault(rarity, 250);

        if (!checkAndNotifyCoins(player, cost)) return Optional.empty();

        ItemStack output = buildReforgedItem(baseItem, chosen, rarity);
        return Optional.of(RecipeDefinition.dynamic("forge_random", output, inp -> {
            consumeItem(inp, "base_item");
            deductCoins(player, cost);
        }));
    }

    // ─── Core reforge logic ───

    private ItemStack buildReforgedItem(ItemStack baseItem, ReforgeDefinition reforge, Rarity rarity) {
        ItemStack output = baseItem.clone();
        ItemMeta meta = output.getItemMeta();
        if (meta == null) return output;

        // Start from the item definition's clean base stats (no previous reforge)
        String itemId = meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        Map<String, Double> baseStats = new HashMap<>();
        if (itemId != null) {
            plugin.getItemManager().getItemRegistry().getItem(itemId)
                    .map(ItemDefinition::getStats)
                    .ifPresent(baseStats::putAll);
        }

        // Merge with rarity-scaled reforge bonuses
        Map<String, Double> mergedStats = new HashMap<>(baseStats);
        for (Map.Entry<String, Double> entry : reforge.getStatBonusesForRarity(rarity).entrySet()) {
            mergedStats.merge(entry.getKey(), entry.getValue(), Double::sum);
        }

        plugin.getStatModule().saveStats(meta, mergedStats);
        meta.getPersistentDataContainer().set(Keys.REFORGE_ID_KEY, PersistentDataType.STRING, reforge.getId());
        meta.getPersistentDataContainer().set(Keys.REFORGE_DISPLAY_KEY, PersistentDataType.STRING, reforge.getName());
        output.setItemMeta(meta);

        plugin.getItemManager().getItemFactory().updateLore(output);
        return output;
    }

    // ─── Reforge stone creation ───

    /** Creates a dynamic reforge stone ItemStack for the given definition. */
    public ItemStack createReforgeStone(ReforgeDefinition def) {
        ItemStack stone = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = stone.getItemMeta();
        if (meta == null) return stone;

        meta.displayName(Formatter.format("<aqua>" + def.getName() + " Reforge Stone"));

        List<Component> lore = new ArrayList<>();
        lore.add(Formatter.format("<gray>Place in the Reforge Anvil alongside an item."));

        // Applicable types
        List<String> typeNames = new ArrayList<>();
        for (ItemType t : def.getApplicableTypes()) {
            typeNames.add(Formatter.capitalize(t.name().replace("_", " ")));
        }
        String types = typeNames.isEmpty() ? "All" : String.join(", ", typeNames);
        lore.add(Formatter.format("<gray>Applicable to: <white>" + types));

        lore.add(Component.empty());
        lore.add(Formatter.format("<gray>Bonus stats by rarity:"));

        Map<Rarity, Map<String, Double>> byRarity = def.getStatBonusesByRarity();
        for (Rarity rarity : Rarity.values()) {
            Map<String, Double> bonuses = byRarity.get(rarity);
            if (bonuses == null || bonuses.isEmpty()) continue;
            int cost = RARITY_COST.getOrDefault(rarity, 250);
            StringBuilder line = new StringBuilder();
            line.append(rarity.getColor()).append(rarity.getName())
                .append(" <dark_gray>(").append(formatCoins(cost)).append(" Coins)<gray>:");
            lore.add(Formatter.format(line.toString()));
            for (Map.Entry<String, Double> entry : bonuses.entrySet()) {
                StatDefinition statDef = plugin.getStatModule().getStatRegistry().get(entry.getKey()).orElse(null);
                String statLine = statDef != null
                        ? " ◈ " + statDef.format(entry.getValue())
                        : " ◈ +" + entry.getValue().intValue() + " " + entry.getKey();
                lore.add(Formatter.format("<gray>" + statLine));
            }
        }

        lore.add(Component.empty());
        lore.add(Formatter.format("<dark_purple><bold>REFORGE STONE"));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.REFORGE_POOL_KEY, PersistentDataType.STRING, def.getId());
        stone.setItemMeta(meta);
        return stone;
    }

    // ─── Helpers ───

    private Rarity readRarity(ItemStack item) {
        if (!item.hasItemMeta()) return Rarity.COMMON;
        String raw = item.getItemMeta().getPersistentDataContainer().get(Keys.RARITY_KEY, PersistentDataType.STRING);
        if (raw == null) return Rarity.COMMON;
        try { return Rarity.valueOf(raw.toUpperCase()); } catch (IllegalArgumentException e) { return Rarity.COMMON; }
    }

    private ItemType readItemType(ItemStack item) {
        if (!item.hasItemMeta()) return ItemType.NONE;
        String raw = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
        if (raw == null) return ItemType.NONE;
        try { return ItemType.valueOf(raw.toUpperCase()); } catch (IllegalArgumentException e) { return ItemType.NONE; }
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() == 0;
    }

    private boolean checkAndNotifyCoins(@Nullable Player player, int cost) {
        if (player == null || cost <= 0) return true;
        var eco = plugin.getEconomy();
        if (eco != null && !eco.hasCoins(player, cost)) {
            final Player p = player;
            plugin.getServer().getScheduler().runTask(plugin, () ->
                p.sendMessage(Formatter.format("<red>You need <gold>" + formatCoins(cost) + " Coins</gold> to use the forge.")));
            return false;
        }
        return true;
    }

    private void deductCoins(@Nullable Player player, int cost) {
        if (player == null || cost <= 0) return;
        var eco = plugin.getEconomy();
        if (eco != null) eco.removeCoins(player, cost);
    }

    private void consumeItem(Map<String, ItemStack> inputs, String key) {
        ItemStack item = inputs.get(key);
        if (item != null) item.setAmount(0);
    }

    private String formatCoins(int amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000)    return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }

    // ─── Loading ───

    private void loadDefinitions() {
        YamlLoader<ReforgeDefinition> loader = new YamlLoader<>(plugin, "reforges", "Reforge");
        loader.load(this::parseDefinition, def -> definitions.put(def.getId(), def));
    }

    private LoadResult<ReforgeDefinition, String> parseDefinition(String id, ConfigurationSection section, String filePath) {
        try {
            String name = section.getString("name", id);
            boolean generateStone = section.getBoolean("generate-stone", false);

            List<ItemType> applicableTypes = new ArrayList<>();
            for (String typeStr : section.getStringList("applicable-types")) {
                try {
                    applicableTypes.add(ItemType.valueOf(typeStr.toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }

            Map<Rarity, Map<String, Double>> statBonusesByRarity = new EnumMap<>(Rarity.class);
            ConfigurationSection byRaritySection = section.getConfigurationSection("stat-bonuses-by-rarity");
            if (byRaritySection != null) {
                for (String rarityKey : byRaritySection.getKeys(false)) {
                    Rarity rarity;
                    try { rarity = Rarity.valueOf(rarityKey.toUpperCase()); }
                    catch (IllegalArgumentException ignored) { continue; }
                    ConfigurationSection statsSec = byRaritySection.getConfigurationSection(rarityKey);
                    if (statsSec == null) continue;
                    Map<String, Double> bonuses = new HashMap<>();
                    for (String statKey : statsSec.getKeys(false)) {
                        bonuses.put(statKey.toLowerCase(), statsSec.getDouble(statKey));
                    }
                    statBonusesByRarity.put(rarity, bonuses);
                }
            }

            return LoadResult.success(new ReforgeDefinition(id, name, applicableTypes, statBonusesByRarity, generateStone));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse reforge '" + id + "': " + e.getMessage());
        }
    }
}
