package org.nakii.valmora.module.reforge;

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
import org.nakii.valmora.module.recipe.DynamicMachineHandler;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class ReforgeModule implements ReloadableModule, DynamicMachineHandler {

    private final Valmora plugin;
    private final Map<String, ReforgeDefinition> definitions = new HashMap<>();

    public ReforgeModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        definitions.clear();
        loadDefinitions();
        plugin.getRecipeModule().getRecipeEngine().registerHandler("reforge", this);
    }

    @Override
    public void onDisable() {
        definitions.clear();
    }

    @Override
    public String getId() { return "reforge"; }

    @Override
    public String getName() { return "Reforge System"; }

    public Collection<ReforgeDefinition> getDefinitions() {
        return definitions.values();
    }

    public ReforgeDefinition getDefinition(String id) {
        return definitions.get(id.toLowerCase());
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) {
        return Optional.empty(); // player context required
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
        ItemStack baseItem = inputs.get("base_item");
        ItemStack reforgeStone = inputs.get("reforge_stone");

        if (isEmpty(baseItem) || isEmpty(reforgeStone)) return Optional.empty();

        // Get the reforge pool from the stone's PDC
        String poolRaw = reforgeStone.getItemMeta() != null
                ? reforgeStone.getItemMeta().getPersistentDataContainer()
                    .get(Keys.REFORGE_POOL_KEY, PersistentDataType.STRING)
                : null;
        if (poolRaw == null || poolRaw.isBlank()) return Optional.empty();
        String[] pool = poolRaw.split(",");

        // Get the base item's type
        String itemTypeRaw = baseItem.getItemMeta() != null
                ? baseItem.getItemMeta().getPersistentDataContainer()
                    .get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING)
                : null;
        ItemType itemType = ItemType.NONE;
        if (itemTypeRaw != null) {
            try { itemType = ItemType.valueOf(itemTypeRaw.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }
        final ItemType finalItemType = itemType;

        // Collect eligible reforges from the pool that match the item type
        List<ReforgeDefinition> eligible = new ArrayList<>();
        for (String reforgeId : pool) {
            ReforgeDefinition def = definitions.get(reforgeId.trim().toLowerCase());
            if (def != null && def.appliesTo(finalItemType)) {
                eligible.add(def);
            }
        }
        if (eligible.isEmpty()) return Optional.empty();

        // Economy pre-check
        ReforgeDefinition chosen = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        if (player != null && chosen.getCost() > 0) {
            var eco = plugin.getEconomy();
            if (eco != null && !eco.hasCoins(player, chosen.getCost())) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(org.nakii.valmora.util.Formatter.format(
                        "<red>You need <gold>" + (int) chosen.getCost() + " coins</gold> to reforge this item.")));
                return Optional.empty();
            }
        }

        // Build the reforged output
        ItemStack output = buildReforgedItem(baseItem, chosen);

        final ReforgeDefinition finalChosen = chosen;
        final Player finalPlayer = player;
        return Optional.of(RecipeDefinition.dynamic("reforge", output, inp -> {
            // Consume both inputs
            ItemStack stone = inp.get("reforge_stone");
            if (stone != null) stone.setAmount(0);
            ItemStack base = inp.get("base_item");
            if (base != null) base.setAmount(0);
            // Deduct economy cost
            if (finalPlayer != null && finalChosen.getCost() > 0) {
                var eco = plugin.getEconomy();
                if (eco != null) eco.removeCoins(finalPlayer, finalChosen.getCost());
            }
        }));
    }

    private ItemStack buildReforgedItem(ItemStack baseItem, ReforgeDefinition reforge) {
        ItemStack output = baseItem.clone();
        ItemMeta meta = output.getItemMeta();
        if (meta == null) return output;

        // Get base stats from item definition (clean slate, no old reforge bonuses)
        String itemId = meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        Map<String, Double> baseStats = new HashMap<>();
        if (itemId != null) {
            plugin.getItemManager().getItemRegistry().getItem(itemId)
                    .map(ItemDefinition::getStats)
                    .ifPresent(baseStats::putAll);
        }

        // Merge base stats + reforge bonuses
        Map<String, Double> mergedStats = new HashMap<>(baseStats);
        for (Map.Entry<String, Double> entry : reforge.getStatBonuses().entrySet()) {
            mergedStats.merge(entry.getKey(), entry.getValue(), Double::sum);
        }

        // Write merged stats and reforge metadata
        plugin.getStatModule().saveStats(meta, mergedStats);
        meta.getPersistentDataContainer().set(Keys.REFORGE_ID_KEY, PersistentDataType.STRING, reforge.getId());
        meta.getPersistentDataContainer().set(Keys.REFORGE_DISPLAY_KEY, PersistentDataType.STRING, reforge.getName());
        output.setItemMeta(meta);

        // Rebuild the lore with the new stats + reforge name prefix
        plugin.getItemManager().getItemFactory().updateLore(output);
        return output;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() == 0;
    }

    private void loadDefinitions() {
        YamlLoader<ReforgeDefinition> loader = new YamlLoader<>(plugin, "reforges", "Reforge");
        loader.load(this::parseDefinition, def -> definitions.put(def.getId(), def));
    }

    private LoadResult<ReforgeDefinition, String> parseDefinition(String id, ConfigurationSection section, String filePath) {
        try {
            String name = section.getString("name", id);
            double cost = section.getDouble("cost", 0.0);

            List<ItemType> applicableTypes = new ArrayList<>();
            for (String typeStr : section.getStringList("applicable-types")) {
                try {
                    applicableTypes.add(ItemType.valueOf(typeStr.toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }

            Map<String, Double> statBonuses = new HashMap<>();
            ConfigurationSection statsSec = section.getConfigurationSection("stat-bonuses");
            if (statsSec != null) {
                for (String statKey : statsSec.getKeys(false)) {
                    statBonuses.put(statKey.toLowerCase(), statsSec.getDouble(statKey));
                }
            }

            return LoadResult.success(new ReforgeDefinition(id, name, applicableTypes, statBonuses, cost));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse reforge '" + id + "': " + e.getMessage());
        }
    }
}
