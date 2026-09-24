package org.nakii.valmora.module.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.stat.StatDefinition;
import org.nakii.valmora.module.stat.StatRegistry;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemFactory {

    private final Valmora plugin;

    public ItemFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(ItemDefinition definition) {
        ItemStack item = new ItemStack(definition.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set the ID first so other methods can find it
            meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, definition.getId());
            
            if (definition.getItemType() != null) {
                 meta.getPersistentDataContainer().set(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING, definition.getItemType().name());
            }

            // Set custom properties. The rarity/type/stats copies stored here are only a fallback
            // for when the definition is gone — live reads go through ItemView.
            meta.getPersistentDataContainer().set(Keys.RARITY_KEY, PersistentDataType.STRING, definition.getRarityKey());
            ItemMigrator.stampCurrent(meta);

            // Custom model data
            if (definition.getCustomModelData() > 0) {
                meta.setCustomModelData(definition.getCustomModelData());
            }

            // Container GUI (e.g. a backpack opens its own storage GUI instead of stacking)
            if (definition.getContainerGui() != null) {
                meta.getPersistentDataContainer().set(Keys.CONTAINER_GUI_KEY, PersistentDataType.STRING, definition.getContainerGui());
            }

            // Add all stats to the stats map
            plugin.getStatModule().saveStats(meta, definition.getStats());

            item.setItemMeta(meta);
            
            // Now update the lore properly
            updateLore(item);
        }
        return item;
    }

    public void updateLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        updateLore(item, meta);
        item.setItemMeta(meta);
    }

    public void updateLore(ItemStack item, ItemMeta meta) {
        ItemLoreLayout layout = ItemLoreLayout.load(plugin);
        String itemId = meta.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        
        // Try to get definition
        var definitionOpt = itemId != null ? plugin.getItemManager().getItemRegistry().getItem(itemId) : java.util.Optional.<ItemDefinition>empty();

        String rarityKey;
        String rarityColor;
        String name;
        List<String> baseLore = new ArrayList<>();

        if (definitionOpt.isPresent()) {
            ItemDefinition definition = definitionOpt.get();
            // Keep the stored copies in step with the live definition (see ItemView).
            ItemView.syncStoredCopies(meta);
            rarityKey = definition.getRarityKey();
            name = definition.getName();
            if (definition.getLore() != null) baseLore.addAll(definition.getLore());
        } else {
            // Fallback for translated vanilla items (and items whose definition was deleted)
            String stored = meta.getPersistentDataContainer().get(Keys.RARITY_KEY, PersistentDataType.STRING);
            rarityKey = stored != null ? stored : Rarity.COMMON.name();
            // Use capitalized material name if it's a translated vanilla item
            name = (itemId != null && itemId.startsWith("vanilla_")) ? Formatter.capitalize(item.getType().name().replace("_", " ")) : null;
        }
        // Colour/name from rarities.yml (falling back to the legacy enum) — see ItemRarities.
        rarityColor = ItemRarities.color(rarityKey);

        // A player/anvil-given name survives re-renders (it used to be overwritten here by the
        // template name on the next enchant, reforge or content refresh).
        String customName = meta.getPersistentDataContainer().get(Keys.CUSTOM_NAME_KEY, PersistentDataType.STRING);
        if (customName != null && !customName.isEmpty()) {
            name = customName;
        }

        // Prepend/append modifier display text (docs/Valmora_Modifier_Framework_Design.docx §19) —
        // e.g. a reforge's "Fierce " prefix. Generic: works for ANY PREFIX/SUFFIX-format group, not
        // just the migrated "reforges" group.
        var modifierModule = plugin.getModifierModule();
        String prefixText = null;
        String suffixText = null;
        if (modifierModule != null && modifierModule.getEngine() != null && item.hasItemMeta()) {
            prefixText = modifierModule.getEngine().getDisplayText(item, org.nakii.valmora.module.modifier.DisplayFormat.PREFIX).orElse(null);
            suffixText = modifierModule.getEngine().getDisplayText(item, org.nakii.valmora.module.modifier.DisplayFormat.SUFFIX).orElse(null);
        }
        if (name != null) {
            if (prefixText != null) name = prefixText + name;
            if (suffixText != null) name = name + suffixText;
        }

        // Set Display Name
        if (name != null) {
            meta.displayName(Formatter.format(rarityColor + name));
        }

        // Assemble Lore — each block below is built independently into its own line list, keyed by
        // ItemLoreLayout.Section, then stitched together in the configured order at the bottom
        // (config.yml `items.lore` — ItemLoreLayout). This lets admins reorder, hide, or reformat
        // any block without touching Java.
        Map<ItemLoreLayout.Section, List<Component>> sections = new java.util.EnumMap<>(ItemLoreLayout.Section.class);

        // BREAKING_POWER (first line for mining tools)
        List<Component> breakingPowerLines = new ArrayList<>();
        String typeTagRaw = meta.getPersistentDataContainer().get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
        if (typeTagRaw != null) {
            try {
                ItemType itemType = ItemType.valueOf(typeTagRaw.toUpperCase());
                if (itemType == ItemType.PICKAXE || itemType == ItemType.SHOVEL
                        || itemType == ItemType.AXE || itemType == ItemType.HOE) {
                    // Show the Breaking Power the resource gate actually checks — the item's own
                    // breaking_power stat. It used to print the material tier instead, so a power-7
                    // custom pickaxe read "4" and a vanilla one read "4" while mining as 0.
                    Double stored = org.nakii.valmora.Valmora.getInstance() != null
                            ? org.nakii.valmora.Valmora.getInstance().getStatModule().loadStats(meta)
                                .get(org.nakii.valmora.Valmora.getInstance().getStatModule().getSystemStats().getBreakingPower())
                            : null;
                    int bp = stored != null ? (int) Math.round(stored) : 0;
                    breakingPowerLines.add(Formatter.format(layout.getBreakingPowerFormat().replace("{power}", String.valueOf(bp))));
                }
            } catch (IllegalArgumentException ignored) {}
        }
        sections.put(ItemLoreLayout.Section.BREAKING_POWER, breakingPowerLines);

        // BASE_LORE
        List<Component> baseLoreLines = new ArrayList<>();
        if (!baseLore.isEmpty()) {
            baseLoreLines.addAll(Formatter.formatList(baseLore));
        }
        // Per-instance extra lines (e.g. an anvil recipe's add_lore) follow the template's own lore,
        // so they survive every re-render instead of being wiped by the next one.
        String extraLore = meta.getPersistentDataContainer().get(Keys.EXTRA_LORE_KEY, PersistentDataType.STRING);
        if (extraLore != null && !extraLore.isEmpty()) {
            baseLoreLines.addAll(Formatter.formatList(List.of(extraLore.split("\n"))));
        }
        sections.put(ItemLoreLayout.Section.BASE_LORE, baseLoreLines);

        // LORE_TEMPLATE (resolved against item's own stats)
        List<Component> loreTemplateLines = new ArrayList<>();
        if (definitionOpt.isPresent()) {
            List<String> loreTemplate = definitionOpt.get().getLoreTemplate();
            if (!loreTemplate.isEmpty()) {
                Map<String, Double> itemStats = plugin.getStatModule().loadStats(meta);
                for (String line : loreTemplate) {
                    loreTemplateLines.add(Formatter.format(resolveItemStatTokens(line, itemStats)));
                }
            }
        }
        sections.put(ItemLoreLayout.Section.LORE_TEMPLATE, loreTemplateLines);

        // STATS — merges baked item stats with dynamically-resolved modifier STAT effects
        // (reforges/gemstones/traits — docs/Valmora_Modifier_Framework_Design.docx), so a
        // reforge/gemstone's bonus shows in the same list rather than needing its own lore block.
        List<Component> statsLines = new ArrayList<>();
        Map<String, Double> stats = new java.util.LinkedHashMap<>(plugin.getStatModule().loadStats(meta));
        if (modifierModule != null && modifierModule.getEngine() != null && item.hasItemMeta()) {
            modifierModule.getEngine().contributeStats(item, null, (statId, value) ->
                    stats.merge(statId, value, Double::sum));
        }
        if (!stats.isEmpty()) {
            StatRegistry statRegistry = plugin.getStatModule().getStatRegistry();
            for (Map.Entry<String, Double> entry : stats.entrySet()) {
                StatDefinition def = statRegistry.get(entry.getKey()).orElse(null);
                String statText = def != null
                        ? def.format(entry.getValue())
                        : "<white>" + entry.getKey() + ": +" + entry.getValue().intValue();
                statsLines.add(Formatter.format(layout.getStatLineFormat().replace("{stat}", statText)));
            }
        }
        sections.put(ItemLoreLayout.Section.STATS, statsLines);

        // MODIFIERS — attached LORE-format modifiers (e.g. gemstones), one line per instance naming
        // the modifier and its effective tier; the stat numbers themselves are already folded into
        // the STATS block above rather than repeated here.
        List<Component> modifierLines = new ArrayList<>();
        if (modifierModule != null && modifierModule.getEngine() != null && item.hasItemMeta()) {
            var loreEntries = modifierModule.getEngine().getLoreEntries(item);
            for (var entry : loreEntries) {
                var def = entry.getKey();
                int tier = entry.getValue();
                String label = def.getDisplayName(tier) != null ? def.getDisplayName(tier) : def.getId();
                modifierLines.add(Formatter.format(layout.getModifierLineFormat().replace("{label}", label)));
            }
        }
        sections.put(ItemLoreLayout.Section.MODIFIERS, modifierLines);

        // ENCHANTMENTS
        List<Component> enchantLines = new ArrayList<>();
        Map<String, Integer> enchants = org.nakii.valmora.module.enchant.EnchantmentHelper.loadEnchantMap(meta.getPersistentDataContainer());
        if (!enchants.isEmpty()) {
            enchantLines.addAll(org.nakii.valmora.module.enchant.EnchantmentHelper.formatEnchants(enchants));
        }
        sections.put(ItemLoreLayout.Section.ENCHANTMENTS, enchantLines);

        // ABILITIES (only if definition present)
        List<Component> abilityLines = new ArrayList<>();
        if (definitionOpt.isPresent()) {
            ItemDefinition definition = definitionOpt.get();
            if (definition.getAbilities() != null && !definition.getAbilities().isEmpty()) {
                for (AbilityDefinition ability : definition.getAbilities().values()) {
                    if (ability.getDisplayMode() == AbilityDefinition.DisplayMode.SIMPLE) {
                        // Clean, header-less display: just the description lines, nothing else
                        // (no "Ability: <name> <TRIGGER>" banner, no Mana Cost/Cooldown footer).
                        if (!ability.getDescription().isEmpty()) {
                            abilityLines.addAll(Formatter.formatList(ability.getDescription()));
                        }
                        continue;
                    }

                    String abilityName = (ability.getName() != null && !ability.getName().isEmpty())
                            ? ability.getName() : ability.getId();
                    String triggerText = ability.getTrigger().name().replace("_", " ");
                    abilityLines.add(Formatter.format(layout.getAbilityHeaderFormat()
                            .replace("{name}", abilityName).replace("{trigger}", triggerText)));
                    if (!ability.getDescription().isEmpty()) {
                        abilityLines.addAll(Formatter.formatList(ability.getDescription()));
                    }
                    if (ability.getManaCost() > 0) {
                        abilityLines.add(Formatter.format(layout.getAbilityManaCostFormat()
                                .replace("{mana}", String.valueOf((int) ability.getManaCost()))));
                    }
                    if (ability.getCooldown() > 0) {
                        abilityLines.add(Formatter.format(layout.getAbilityCooldownFormat()
                                .replace("{cooldown}", String.valueOf(ability.getCooldown()))));
                    }
                    abilityLines.add(Component.empty()); // separator between multiple FULL abilities
                }
                trimTrailingBlank(abilityLines);
            }
        }
        sections.put(ItemLoreLayout.Section.ABILITIES, abilityLines);

        // RARITY_TAG (e.g. EPIC SWORD)
        List<Component> rarityLines = new ArrayList<>();
        String typeName = meta.getPersistentDataContainer().get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING);
        String typeDisplay = (typeName != null && !typeName.equalsIgnoreCase("NONE")) ? " " + typeName.toUpperCase() : "";
        rarityLines.add(Formatter.format(layout.getRarityTagFormat()
                .replace("{color}", rarityColor).replace("{rarity}", ItemRarities.displayName(rarityKey).toUpperCase()).replace("{type}", typeDisplay)));
        sections.put(ItemLoreLayout.Section.RARITY_TAG, rarityLines);

        // Stitch sections together in configured order, inserting a spacer line between any two
        // consecutive non-empty sections (unless items.lore.spacer-between-sections is false).
        List<Component> finalLore = new ArrayList<>();
        for (ItemLoreLayout.Section section : layout.getSectionOrder()) {
            List<Component> lines = sections.get(section);
            if (lines == null || lines.isEmpty()) continue;
            if (layout.isSpacerBetweenSections() && !finalLore.isEmpty()) finalLore.add(Component.empty());
            finalLore.addAll(lines);
        }

        meta.lore(finalLore);

        // Mark the item as rendered against the current content, so ItemRefresher skips it.
        String epoch = ItemRefresher.currentEpoch();
        if (!epoch.isEmpty()) {
            meta.getPersistentDataContainer().set(Keys.ITEM_RENDER_EPOCH_KEY, PersistentDataType.STRING, epoch);
        }
    }

    /** Strips trailing {@link Component#empty()} lines so a section never ends on a blank line —
     *  spacing between sections is the assembly loop's job (items.lore.spacer-between-sections). */
    private void trimTrailingBlank(List<Component> lines) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).equals(Component.empty())) {
            lines.remove(lines.size() - 1);
        }
    }

    private String resolveItemStatTokens(String line, Map<String, Double> stats) {
        StringBuilder result = new StringBuilder(line);
        int start;
        while ((start = result.indexOf("$item.stat.")) != -1) {
            int end = result.indexOf("$", start + 1);
            if (end == -1) break;
            String statId = result.substring(start + 11, end);
            Double val = stats.get(statId.toLowerCase());
            String replacement = val != null
                    ? (val == Math.floor(val) ? String.valueOf((long) val.doubleValue()) : String.valueOf(val))
                    : "0";
            result.replace(start, end + 1, replacement);
        }
        return result.toString();
    }

    /** HC-040: {@code items.breaking-power} — tool tier substring -> breaking power, so a custom
     *  tool tier added by a content pack doesn't need a recompile to get a matching power value. */
    public static int tierBreakingPower(Material material) {
        String name = material.name();
        var plugin = org.nakii.valmora.Valmora.getInstance();
        org.bukkit.configuration.ConfigurationSection section = plugin != null
                ? plugin.getConfig().getConfigurationSection("items.breaking-power") : null;
        if (section != null) {
            if (name.contains("NETHERITE")) return section.getInt("netherite", 5);
            if (name.contains("DIAMOND")) return section.getInt("diamond", 4);
            if (name.contains("IRON")) return section.getInt("iron", 3);
            if (name.contains("STONE")) return section.getInt("stone", 2);
            return section.getInt("wood", 1);
        }
        if (name.contains("NETHERITE")) return 5;
        if (name.contains("DIAMOND")) return 4;
        if (name.contains("IRON")) return 3;
        if (name.contains("STONE")) return 2;
        return 1;
    }
}
