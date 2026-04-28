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

        ItemStack result = base.clone();
        Map<String, Integer> baseEnchants = EnchantmentHelper.getEnchantments(base);
        Map<String, Integer> matEnchants = EnchantmentHelper.getEnchantments(material);

        if (matEnchants.isEmpty() && material.getType() != base.getType()) {
             // If no Valmora enchants on material and it's not the same type, maybe it's not a valid anvil merge
             // (Vanilla anvil also handles repairs, but let's focus on Valmora enchants for now)
             return Optional.empty();
        }

        Map<String, Integer> newEnchants = new HashMap<>(baseEnchants);

        for (Map.Entry<String, Integer> entry : matEnchants.entrySet()) {
            String id = entry.getKey();
            int matLevel = entry.getValue();

            EnchantmentDefinition def = plugin.getEnchantModule().getRegistry().get(id).orElse(null);
            if (def == null) continue;

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

            if (newEnchants.containsKey(id)) {
                int baseLevel = newEnchants.get(id);
                int finalLevel;
                if (baseLevel == matLevel) {
                    finalLevel = Math.min(baseLevel + 1, def.getAbsoluteMaxLevel());
                } else {
                    finalLevel = Math.max(baseLevel, matLevel);
                }
                newEnchants.put(id, finalLevel);
            } else {
                newEnchants.put(id, matLevel);
            }
        }

        // Apply new enchants to the result
        for (Map.Entry<String, Integer> entry : newEnchants.entrySet()) {
            EnchantmentHelper.applyEnchantment(result, entry.getKey(), entry.getValue());
        }

        // Check if anything actually changed
        if (result.isSimilar(base) && newEnchants.equals(baseEnchants)) {
            // No change, but maybe it's a repair?
            // For now, let's return empty if no enchants were merged
            if (matEnchants.isEmpty()) return Optional.empty();
        }

        // Calculate cost: 10 coins per level of the merged enchants
        int totalLevel = newEnchants.values().stream().mapToInt(Integer::intValue).sum();
        int cost = totalLevel * 10;
        
        String script = "variable add player.var.coins -" + cost;
        org.nakii.valmora.api.scripting.CompiledEvent onCraft = plugin.getScriptModule().getEventParser().parseList(java.util.List.of(script));
        
        return Optional.of(RecipeDefinition.vanilla(result, onCraft));
    }
}
