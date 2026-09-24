package org.nakii.valmora.module.recipe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The unified anvil (coworker "Custom Anvil Engine Specification" note). Single machine id
 * ({@code anvil}), single GUI ({@code guis/anvil.yml}, slots {@code base}/{@code material}), one
 * evaluation pipeline matching the note's diagram exactly:
 *
 * <ol>
 *   <li>{@link AnvilRecipeDefinition} (explicit {@code UPGRADE}/{@code TRANSMUTE} content) — first
 *       match wins.</li>
 *   <li>The modifier framework's {@code APPLY_MODIFIER}/{@code REMOVE_MODIFIER} recipes (reforges,
 *       gemstones, any future group) — delegated to the existing {@code ModifierAnvilHandler}
 *       rather than reimplemented (CLAUDE.md's hard rule against group-specific engine code; this
 *       is also what the note called {@code STAT_MODIFIER}). Its own {@code ValueResolver}-based
 *       cost is untouched by this handler's work-penalty math.</li>
 *   <li>The "standard combination engine" — book+book / gear+book enchant merge (existing dynamic
 *       cap algorithm), gear+gear enchant+durability merge, gear+repair-material durability
 *       repair.</li>
 * </ol>
 *
 * Steps 1 and 3 share the "prior work penalty" cost curve ({@link AnvilCostCalculator}, PDC-tracked
 * via {@link Keys#ANVIL_WORK_COUNT_KEY}); step 2 keeps its own independent cost system unchanged.
 */
public class AnvilMachineHandler implements DynamicMachineHandler {

    private static final Map<String, Material> REPAIR_MATERIAL_HINTS = Map.ofEntries(
            Map.entry("NETHERITE", Material.NETHERITE_INGOT),
            Map.entry("DIAMOND", Material.DIAMOND),
            Map.entry("GOLD", Material.GOLD_INGOT),
            Map.entry("GOLDEN", Material.GOLD_INGOT),
            Map.entry("IRON", Material.IRON_INGOT),
            Map.entry("STONE", Material.COBBLESTONE),
            Map.entry("LEATHER", Material.LEATHER),
            Map.entry("TURTLE", Material.TURTLE_SCUTE)
    );

    private final Valmora plugin;
    private final AnvilTemplateRegistry templates;
    private final AnvilRecipeRegistry anvilRecipes;

    public AnvilMachineHandler(Valmora plugin, AnvilTemplateRegistry templates, AnvilRecipeRegistry anvilRecipes) {
        this.plugin = plugin;
        this.templates = templates;
        this.anvilRecipes = anvilRecipes;
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) {
        return match(inputs, null);
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
        ItemStack base = inputs.get("base");
        ItemStack material = inputs.get("material");
        if (isEmpty(base)) return Optional.empty();

        Optional<RecipeDefinition> explicit = matchExplicitRecipe(base, material, player);
        if (explicit.isPresent()) return explicit;

        Optional<RecipeDefinition> modifier = matchModifierRecipe(base, material, player);
        if (modifier.isPresent()) return modifier;

        return matchStandardCombination(base, material, player);
    }

    // ─── Step 1: explicit UPGRADE/TRANSMUTE recipes ───

    private Optional<RecipeDefinition> matchExplicitRecipe(ItemStack base, ItemStack material, @Nullable Player player) {
        for (AnvilRecipeDefinition recipe : anvilRecipes.values()) {
            if (!recipe.matches(base, material)) continue;

            int workA = readWorkCount(base);
            int workB = material != null ? readWorkCount(material) : 0;
            int cost = AnvilCostCalculator.totalXpCost(recipe.getCostXpLevels(), 0, workA, workB);
            int coins = recipe.getCostCoins();
            if (!checkCost(player, coins, cost)) continue;

            ItemStack output = buildExplicitResult(recipe, base);
            int resultWork = AnvilCostCalculator.resultWorkCount(workA, workB, recipe.isIncreaseWorkPenalty());
            stampWorkCount(output, resultWork);

            AnvilRecipeDefinition.ItemMatch addition = recipe.getAddition();
            return Optional.of(RecipeDefinition.dynamic("anvil", output, inp -> {
                deductCost(player, coins, cost);
                consumeAmount(inp, "base", 1);
                if (addition != null) consumeAmount(inp, "material", addition.amount());
            }));
        }
        return Optional.empty();
    }

    private ItemStack buildExplicitResult(AnvilRecipeDefinition recipe, ItemStack base) {
        AnvilRecipeDefinition.ResultSpec spec = recipe.getResult();
        ItemMeta baseMeta = base.hasItemMeta() ? base.getItemMeta() : null;
        List<net.kyori.adventure.text.Component> inheritedLore =
                baseMeta != null && baseMeta.lore() != null ? baseMeta.lore() : List.of();

        ItemStack fresh = buildBaseResultItem(spec);

        ItemStack output = recipe.getType() == AnvilRecipeDefinition.Type.UPGRADE && recipe.isKeepDataOnUpgrade()
                ? ItemDataCarrier.carryForward(plugin, base, fresh)
                : fresh;

        if (!spec.addEnchants().isEmpty()) {
            Map<String, Integer> merged = new HashMap<>(EnchantmentHelper.getEnchantments(output));
            for (Map.Entry<String, Integer> entry : spec.addEnchants().entrySet()) {
                String enchantId = stripPrefix(entry.getKey());
                merged.put(enchantId.toLowerCase(), Math.max(merged.getOrDefault(enchantId.toLowerCase(), 0), entry.getValue()));
            }
            EnchantmentHelper.applyEnchantmentMap(output, merged);
        }

        // Stamp PDC/stats/rarity and build the baseline lore (stats + enchants) BEFORE layering the
        // recipe's own name/add_lore on top — translate()/applyEnchantmentMap both route through
        // ItemFactory.updateLore, which would otherwise clobber a hand-built lore applied earlier.
        output = plugin.getItemManager().getItemTranslator().translate(output);

        // The recipe's name and add_lore are stored on the item (custom_name / extra_lore) rather
        // than painted onto the rendered lore, so the next re-render (an enchant, a reforge, a
        // content refresh) keeps them instead of wiping them.
        if (spec.name() != null || !spec.addLore().isEmpty()) {
            ItemMeta meta = output.getItemMeta();
            var pdc = meta.getPersistentDataContainer();
            if (spec.name() != null) {
                pdc.set(Keys.CUSTOM_NAME_KEY, PersistentDataType.STRING, spec.name());
            }
            if (!spec.addLore().isEmpty()) {
                List<String> lines = new ArrayList<>();
                String existingExtra = pdc.get(Keys.EXTRA_LORE_KEY, PersistentDataType.STRING);
                if (existingExtra != null && !existingExtra.isEmpty()) lines.add(existingExtra);
                for (String line : spec.addLore()) {
                    if ("{inherit_lore}".equals(line)) {
                        for (net.kyori.adventure.text.Component inherited : inheritedLore) {
                            lines.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(inherited));
                        }
                    } else {
                        lines.add(line);
                    }
                }
                pdc.set(Keys.EXTRA_LORE_KEY, PersistentDataType.STRING, String.join("\n", lines));
            }
            output.setItemMeta(meta);
            plugin.getItemManager().getItemFactory().updateLore(output);
        }

        if (!spec.addNbt().isEmpty()) {
            ItemMeta meta = output.getItemMeta();
            for (Map.Entry<String, Object> entry : spec.addNbt().entrySet()) {
                if ("CustomModelData".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number n) {
                    meta.setCustomModelData(n.intValue());
                } else {
                    var key = new org.bukkit.NamespacedKey(plugin, "anvil_nbt_" + entry.getKey().toLowerCase());
                    Object value = entry.getValue();
                    if (value instanceof Boolean b) {
                        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (b ? 1 : 0));
                    } else if (value instanceof Number n) {
                        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, n.intValue());
                    } else {
                        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.valueOf(value));
                    }
                }
            }
            output.setItemMeta(meta);
        }

        return output;
    }

    private ItemStack buildBaseResultItem(AnvilRecipeDefinition.ResultSpec spec) {
        if (spec.valmoraId() != null) {
            ItemStack item = plugin.getItemManager().createItemStack(stripPrefix(spec.valmoraId()));
            if (item != null) return item;
        }
        Material mat = spec.material() != null ? Material.matchMaterial(stripPrefix(spec.material())) : null;
        return new ItemStack(mat != null ? mat : Material.STONE);
    }

    private String stripPrefix(String id) {
        int idx = id.indexOf(':');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }

    // ─── Step 2: modifier framework recipes (delegated, not reimplemented) ───

    private Optional<RecipeDefinition> matchModifierRecipe(ItemStack base, ItemStack material, @Nullable Player player) {
        var modifierModule = plugin.getModifierModule();
        if (modifierModule == null || modifierModule.getAnvilHandler() == null) return Optional.empty();

        // ModifierAnvilHandler was written against the modifier_anvil GUI's slot ids — remap
        // rather than change that class, and keep the ItemStack instances shared so its
        // consumeHandler's in-place mutations still land on the real GUI inputs (same trick as
        // AlchemyBrewStartEventFactory's virtualInputs, see docs/modules/design/recipe.md).
        Map<String, ItemStack> remapped = new HashMap<>();
        remapped.put("base_item", base);
        remapped.put("addition_item", material);

        Optional<RecipeDefinition> inner = modifierModule.getAnvilHandler().match(remapped, player);
        if (inner.isEmpty()) return Optional.empty();

        RecipeDefinition innerRecipe = inner.get();
        return Optional.of(RecipeDefinition.dynamic("anvil", innerRecipe.getVanillaResult(), realInputs -> {
            Map<String, ItemStack> remappedReal = new HashMap<>();
            remappedReal.put("base_item", realInputs.get("base"));
            remappedReal.put("addition_item", realInputs.get("material"));
            innerRecipe.getConsumeHandler().accept(remappedReal);
        }));
    }

    // ─── Step 3: standard combination engine ───

    private Optional<RecipeDefinition> matchStandardCombination(ItemStack base, ItemStack material, @Nullable Player player) {
        if (isEmpty(material)) return Optional.empty();

        boolean baseIsBook = base.getType() == Material.ENCHANTED_BOOK;
        boolean matIsBook = material.getType() == Material.ENCHANTED_BOOK;
        boolean sameGearType = !baseIsBook && !matIsBook && base.getType() == material.getType();

        Material repairMat = !baseIsBook ? resolveRepairMaterial(base.getType()) : null;
        boolean isRepairAttempt = repairMat != null && material.getType() == repairMat && !sameGearType;

        if (isRepairAttempt) {
            return matchRepair(base, material, player);
        }
        if (baseIsBook || matIsBook || sameGearType) {
            return matchEnchantAndDurabilityMerge(base, material, player, baseIsBook, sameGearType);
        }
        return Optional.empty();
    }

    private Optional<RecipeDefinition> matchEnchantAndDurabilityMerge(ItemStack base, ItemStack material,
                                                                       @Nullable Player player, boolean isBook, boolean sameGearType) {
        Map<String, Integer> baseEnchants = EnchantmentHelper.getEnchantments(base);
        Map<String, Integer> matEnchants = EnchantmentHelper.getEnchantments(material);

        ItemType baseItemType = readItemType(base);
        Map<String, Integer> newEnchants = new HashMap<>(baseEnchants);
        int enchantLevelsAdded = 0;

        for (Map.Entry<String, Integer> entry : matEnchants.entrySet()) {
            String enchId = entry.getKey();
            int matLevel = entry.getValue();

            EnchantmentDefinition def = plugin.getEnchantModule().getRegistry().get(enchId).orElse(null);
            if (def == null) continue;
            if (!isBook && !def.canApplyTo(baseItemType)) continue; // target validation (note §3)

            boolean hasConflict = false;
            for (String existingId : newEnchants.keySet()) {
                if (existingId.equals(enchId)) continue;
                if (def.conflictsWith(existingId)) { hasConflict = true; break; } // base's enchant wins (note §3)
            }
            if (hasConflict) continue;

            int maxLevel = isBook ? def.getEtableMaxLevel() : def.getAbsoluteMaxLevel();
            int baseLevel = newEnchants.getOrDefault(enchId, 0);
            int finalLevel;
            if (baseLevel == 0) finalLevel = Math.min(matLevel, maxLevel);
            else if (baseLevel < matLevel) finalLevel = Math.min(matLevel, maxLevel);
            else if (baseLevel > matLevel) finalLevel = baseLevel;
            else finalLevel = baseLevel < maxLevel ? baseLevel + 1 : baseLevel;

            if (isBook && (baseLevel > def.getEtableMaxLevel() || finalLevel > def.getEtableMaxLevel())) continue;

            if (finalLevel != baseLevel) enchantLevelsAdded += finalLevel;
            newEnchants.put(enchId, finalLevel);
        }

        boolean enchantsChanged = !newEnchants.equals(baseEnchants);
        boolean willMergeDurability = sameGearType;
        if (!enchantsChanged && !willMergeDurability) return Optional.empty();

        int workA = readWorkCount(base);
        int workB = readWorkCount(material);
        int baseCost = templates.getMergeBaseCost();
        int enchantCost = enchantLevelsAdded * templates.getMergeCostPerLevel();
        int totalCost = AnvilCostCalculator.totalXpCost(baseCost, enchantCost, workA, workB);
        if (!checkCost(player, 0, totalCost)) return Optional.empty();

        ItemStack result = base.clone();
        if (enchantsChanged) EnchantmentHelper.applyEnchantmentMap(result, newEnchants);
        if (willMergeDurability) mergeDurability(result, base, material);

        int resultWork = AnvilCostCalculator.resultWorkCount(workA, workB, true);
        stampWorkCount(result, resultWork);

        return Optional.of(RecipeDefinition.dynamic("anvil", result, inp -> {
            deductCost(player, 0, totalCost);
            consumeAmount(inp, "base", 1);
            consumeAmount(inp, "material", 1);
        }));
    }

    private void mergeDurability(ItemStack result, ItemStack base, ItemStack material) {
        short maxDurability = result.getType().getMaxDurability();
        if (maxDurability <= 0) return;
        if (!(result.getItemMeta() instanceof Damageable) || !(base.getItemMeta() instanceof Damageable baseDmg)
                || !(material.getItemMeta() instanceof Damageable matDmg)) return;

        int durabilityA = maxDurability - baseDmg.getDamage();
        int durabilityB = maxDurability - matDmg.getDamage();
        double bonusPercent = templates.getDurabilityBonusPercent();
        int merged = Math.min(maxDurability, durabilityA + durabilityB + (int) Math.floor(maxDurability * bonusPercent));

        ItemMeta meta = result.getItemMeta();
        ((Damageable) meta).setDamage(maxDurability - merged);
        result.setItemMeta(meta);
    }

    private Optional<RecipeDefinition> matchRepair(ItemStack base, ItemStack material, @Nullable Player player) {
        if (!(base.getItemMeta() instanceof Damageable dmg)) return Optional.empty();
        short maxDurability = base.getType().getMaxDurability();
        if (maxDurability <= 0 || dmg.getDamage() <= 0) return Optional.empty();

        double percentPerUnit = templates.getRepairPercentPerUnit();
        int repairPerUnit = (int) Math.round(maxDurability * percentPerUnit);
        if (repairPerUnit <= 0) return Optional.empty();

        int neededUnits = Math.min(material.getAmount(), (dmg.getDamage() + repairPerUnit - 1) / repairPerUnit);
        if (neededUnits <= 0) return Optional.empty();

        int workA = readWorkCount(base);
        int totalCost = AnvilCostCalculator.totalXpCost(templates.getRepairBaseCost(), 0, workA, 0);
        if (!checkCost(player, 0, totalCost)) return Optional.empty();

        ItemStack result = base.clone();
        ItemMeta meta = result.getItemMeta();
        int newDamage = Math.max(0, dmg.getDamage() - neededUnits * repairPerUnit);
        ((Damageable) meta).setDamage(newDamage);
        result.setItemMeta(meta);
        stampWorkCount(result, AnvilCostCalculator.resultWorkCount(workA, 0, true));

        int units = neededUnits;
        return Optional.of(RecipeDefinition.dynamic("anvil", result, inp -> {
            deductCost(player, 0, totalCost);
            consumeAmount(inp, "base", 1);
            consumeAmount(inp, "material", units);
        }));
    }

    /**
     * HC-105: {@code anvil.repair-materials} lets a content pack map a custom tool-tier substring
     * (e.g. a custom alloy) to its repair material without touching {@link #REPAIR_MATERIAL_HINTS}.
     * Config entries are checked first (in declared order), then the built-in hint map as a fallback.
     */
    private Material resolveRepairMaterial(Material toolMaterial) {
        String name = toolMaterial.name();
        var section = plugin.getConfig().getConfigurationSection("anvil.repair-materials");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (name.contains(key.toUpperCase())) {
                    Material mat = Material.matchMaterial(section.getString(key, ""));
                    if (mat != null) return mat;
                }
            }
        }
        for (Map.Entry<String, Material> entry : REPAIR_MATERIAL_HINTS.entrySet()) {
            if (name.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    // ─── Shared cost / work-count helpers ───

    private int readWorkCount(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(Keys.ANVIL_WORK_COUNT_KEY, PersistentDataType.INTEGER, 0);
    }

    private void stampWorkCount(ItemStack item, int workCount) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.ANVIL_WORK_COUNT_KEY, PersistentDataType.INTEGER, workCount);
        item.setItemMeta(meta);
    }

    private boolean checkCost(@Nullable Player player, int coins, int xpLevels) {
        if (player == null) return true;
        if (coins > 0) {
            var eco = plugin.getEconomy();
            if (eco != null && !eco.hasCoins(player, coins)) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(Formatter.format("<red>You need <gold>" + coins + " Coins</gold> for this.")));
                return false;
            }
        }
        if (xpLevels > 0 && player.getLevel() < xpLevels) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Formatter.format("<red>You need <green>" + xpLevels + " XP levels</green> for this.")));
            return false;
        }
        return true;
    }

    private void deductCost(@Nullable Player player, int coins, int xpLevels) {
        if (player == null) return;
        if (coins > 0) {
            var eco = plugin.getEconomy();
            if (eco != null) eco.removeCoins(player, coins);
        }
        if (xpLevels > 0) player.setLevel(Math.max(0, player.getLevel() - xpLevels));
    }

    private ItemType readItemType(ItemStack item) {
        if (!item.hasItemMeta()) return ItemType.NONE;
        return org.nakii.valmora.module.item.ItemView.type(item);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() == 0;
    }

    private void consumeAmount(Map<String, ItemStack> inputs, String key, int amount) {
        ItemStack item = inputs.get(key);
        if (item != null) item.setAmount(Math.max(0, item.getAmount() - amount));
    }
}
