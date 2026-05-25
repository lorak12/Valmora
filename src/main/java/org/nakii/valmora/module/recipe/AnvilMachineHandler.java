package org.nakii.valmora.module.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AnvilMachineHandler implements DynamicMachineHandler {

    private final Valmora plugin;

    public AnvilMachineHandler(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) {
        ItemStack base = inputs.get("base");
        ItemStack material = inputs.get("material");

        if (base == null || base.getType() == Material.AIR) return Optional.empty();
        if (material == null || material.getType() == Material.AIR) return Optional.empty();

        Map<String, Integer> baseEnchants = EnchantmentHelper.getEnchantments(base);
        Map<String, Integer> matEnchants = EnchantmentHelper.getEnchantments(material);

        // Both items must carry Valmora enchantments to use this machine
        if (matEnchants.isEmpty()) return Optional.empty();

        boolean isBook = base.getType() == Material.ENCHANTED_BOOK;

        Map<String, Integer> newEnchants = new HashMap<>(baseEnchants);

        for (Map.Entry<String, Integer> entry : matEnchants.entrySet()) {
            String id = entry.getKey();
            int matLevel = entry.getValue();

            EnchantmentDefinition def = plugin.getEnchantModule().getRegistry().get(id).orElse(null);
            if (def == null) continue;

            // Books: reject inputs that are already above the enchanting-table ceiling
            if (isBook && matLevel > def.getEtableMaxLevel()) return Optional.empty();

            // Conflict check
            boolean hasConflict = false;
            for (String existingId : newEnchants.keySet()) {
                if (existingId.equals(id)) continue;
                if (def.conflictsWith(existingId)) {
                    hasConflict = true;
                    break;
                }
            }
            if (hasConflict) continue;

            int maxLevel = isBook ? def.getEtableMaxLevel() : def.getAbsoluteMaxLevel();

            if (newEnchants.containsKey(id)) {
                int baseLevel = newEnchants.get(id);

                // Books: base side must also be within the enchanting-table ceiling
                if (isBook && baseLevel > def.getEtableMaxLevel()) return Optional.empty();

                int finalLevel;
                if (baseLevel == matLevel) {
                    finalLevel = baseLevel + 1;
                } else {
                    finalLevel = Math.max(baseLevel, matLevel);
                }

                // Books: cancel if combining would exceed the enchanting-table ceiling
                if (isBook && finalLevel > def.getEtableMaxLevel()) return Optional.empty();

                newEnchants.put(id, Math.min(finalLevel, maxLevel));
            } else {
                newEnchants.put(id, Math.min(matLevel, maxLevel));
            }
        }

        // Nothing actually changed — no valid merge
        if (newEnchants.equals(baseEnchants)) return Optional.empty();

        ItemStack result = base.clone();
        EnchantmentHelper.applyEnchantmentMap(result, newEnchants);

        // Calculate cost: 10 coins per level of the merged enchants
        int totalLevel = newEnchants.values().stream().mapToInt(Integer::intValue).sum();
        int cost = totalLevel * 10;

        String script = "variable add player.var.coins -" + cost;
        org.nakii.valmora.api.scripting.CompiledEvent onCraft = plugin.getScriptModule().getEventParser().parseList(java.util.List.of(script));

        return Optional.of(RecipeDefinition.vanilla(result, onCraft));
    }
}
