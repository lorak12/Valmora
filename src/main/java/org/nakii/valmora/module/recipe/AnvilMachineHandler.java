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
    private final AnvilTemplateRegistry templateRegistry;

    public AnvilMachineHandler(Valmora plugin, AnvilTemplateRegistry templateRegistry) {
        this.plugin = plugin;
        this.templateRegistry = templateRegistry;
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

        // Calculate cost: recipes/anvil_templates.yml -> templates.merge.cost-per-level (Phase 4.3)
        int totalLevel = newEnchants.values().stream().mapToInt(Integer::intValue).sum();
        int cost = totalLevel * templateRegistry.getMergeCostPerLevel();

        // Routed through EconomyService (like Reforge) instead of the player.var.coins script
        // variable — resolves the caster from the real execution context at craft time.
        org.nakii.valmora.api.scripting.CompiledEvent onCraft = context ->
                context.getPlayerCaster().ifPresent(p -> {
                    var eco = org.nakii.valmora.api.ValmoraAPI.getInstance().getEconomy();
                    if (eco != null) eco.removeCoins(p, cost);
                });

        return Optional.of(RecipeDefinition.vanilla(result, onCraft));
    }
}
