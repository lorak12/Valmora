package org.nakii.valmora.module.gui;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.script.variable.VariableProvider;
import org.nakii.valmora.util.Keys;

import java.util.*;
import java.util.stream.Collectors;

public class GuiVariableProvider implements VariableProvider {

    private final Valmora plugin;

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public GuiVariableProvider(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNamespace() {
        return "gui";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (!(context instanceof GuiExecutionContext guiContext)) return null;
        GuiSession session = guiContext.getSession();
        if (session == null) return null;

        if (path.length >= 2 && path[0].equalsIgnoreCase("enchanting")) {
            return resolveEnchanting(path, session);
        }

        if (path.length < 3) return null;

        if (path[0].equalsIgnoreCase("input")) {
            String slotId = path[1];
            Map<String, ItemStack> inputs = session.getInputSnapshot();
            ItemStack item = inputs.get(slotId);
            if (item == null) return null;

            String property = path[2];
            return switch (property.toLowerCase()) {
                case "item_type" -> getItemType(item);
                case "material" -> item.getType().name();
                case "amount" -> item.getAmount();
                case "available_enchants" -> getAvailableEnchants(item);
                default -> null;
            };
        }

        return null;
    }

    // ── Enchanting namespace ────────────────────────────────────────────

    private Object resolveEnchanting(String[] path, GuiSession session) {
        String property = path[1];

        return switch (property.toLowerCase()) {
            case "display_list" -> buildDisplayList(session);
            case "has_selection" -> session.getProps().containsKey("selected_enchant");
            default -> null;
        };
    }

    /**
     * Returns the unified display list for the enchanting GUI.
     * Phase 1 (no selection): list of enchant entries.
     * Phase 2 (enchant selected): list of level entries with state info.
     */
    private List<Map<String, Object>> buildDisplayList(GuiSession session) {
        Map<String, ItemStack> inputs = session.getInputSnapshot();
        ItemStack item = inputs.get("ingredient");
        if (item == null) return Collections.emptyList();

        String selectedEnchant = (String) session.getProps().get("selected_enchant");

        if (selectedEnchant == null) {
            return buildEnchantCatalog(item);
        } else {
            return buildLevelList(item, selectedEnchant);
        }
    }

    /**
     * Phase 1: Returns available enchants as Map entries.
     */
    private List<Map<String, Object>> buildEnchantCatalog(ItemStack item) {
        ItemType type = getItemType(item);
        return plugin.getEnchantModule().getRegistry().values().stream()
                .filter(enchant -> enchant.getTargets().contains(ItemType.ALL) || enchant.getTargets().contains(type))
                .map(enchant -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("type", "enchant");
                    entry.put("id", enchant.getId());
                    entry.put("name", enchant.getName());
                    entry.put("description", String.join("\n", enchant.getDescription()));
                    entry.put("etableMaxLevel", enchant.getEtableMaxLevel());
                    entry.put("absoluteMaxLevel", enchant.getAbsoluteMaxLevel());
                    return entry;
                })
                .collect(Collectors.toList());
    }

    /**
     * Phase 2: Returns level entries for the selected enchant.
     * State logic:
     *   level < currentLevel → "locked"  (gray — already surpassed)
     *   level == currentLevel → "active" (yellow — click to remove)
     *   level > currentLevel → "available" (green — click to apply)
     *   No enchant present → all "available"
     */
    private List<Map<String, Object>> buildLevelList(ItemStack item, String enchantId) {
        EnchantmentDefinition def = plugin.getEnchantModule().getRegistry().get(enchantId).orElse(null);
        if (def == null) return Collections.emptyList();

        int currentLevel = EnchantmentHelper.getEnchantLevel(item, enchantId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (int level = 1; level <= def.getEtableMaxLevel(); level++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("enchantId", enchantId);
            entry.put("level", level);
            entry.put("name", def.getName() + " " + toRoman(level));
            entry.put("description", String.join("\n", def.getDescription()));

            if (currentLevel > 0 && level < currentLevel) {
                entry.put("type", "locked");
            } else if (level == currentLevel) {
                entry.put("type", "active");
            } else {
                entry.put("type", "available");
            }

            list.add(entry);
        }
        return list;
    }

    private String toRoman(int level) {
        if (level >= 1 && level < ROMAN.length) return ROMAN[level];
        return String.valueOf(level);
    }

    // ── Item type detection ─────────────────────────────────────────────

    private ItemType getItemType(ItemStack item) {
        // First check PDC for Valmora items — this is the authoritative source
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String pdcType = meta.getPersistentDataContainer()
                    .get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
            if (pdcType != null) {
                try {
                    return ItemType.valueOf(pdcType.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Fallback: infer from material name for vanilla items
        String material = item.getType().name();
        if (material.contains("SWORD")) return ItemType.SWORD;
        if (material.contains("PICKAXE")) return ItemType.PICKAXE;
        if (material.contains("AXE")) return ItemType.AXE;
        if (material.contains("SHOVEL")) return ItemType.SHOVEL;
        if (material.contains("HOE")) return ItemType.HOE;
        if (material.contains("HELMET")) return ItemType.HELMET;
        if (material.contains("CHESTPLATE")) return ItemType.CHESTPLATE;
        if (material.contains("LEGGINGS")) return ItemType.LEGGINGS;
        if (material.contains("BOOTS")) return ItemType.BOOTS;
        if (material.contains("BOW")) return ItemType.BOW;
        if (material.contains("CROSSBOW")) return ItemType.CROSSBOW;
        if (material.contains("TRIDENT")) return ItemType.TRIDENT;
        if (material.contains("FISHING_ROD")) return ItemType.FISHING_ROD;
        if (material.contains("SHIELD")) return ItemType.SHIELD;
        if (material.contains("ELYTRA")) return ItemType.ELYTRA;
        if (material.contains("SHEARS")) return ItemType.SHEARS;
        return ItemType.ALL;
    }

    private List<EnchantmentDefinition> getAvailableEnchants(ItemStack item) {
        ItemType type = getItemType(item);
        return plugin.getEnchantModule().getRegistry().values().stream()
                .filter(enchant -> enchant.getTargets().contains(ItemType.ALL) || enchant.getTargets().contains(type))
                .collect(Collectors.toList());
    }
}
