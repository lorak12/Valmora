package org.nakii.valmora.module.item;

import org.bukkit.configuration.file.FileConfiguration;
import org.nakii.valmora.Valmora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Config-driven layout for item lore composition, read from {@code config.yml}'s {@code items.lore}
 * section. Controls which blocks of the generated lore appear, in what order, and — for the blocks
 * whose text isn't already driven by something else ({@link org.nakii.valmora.module.stat.StatDefinition#format},
 * a modifier's own display name, {@code EnchantmentHelper.formatEnchants}) — the exact line format.
 *
 * <p>Loaded fresh from {@link Valmora#getConfig()} on every {@link ItemFactory#updateLore} call
 * (not cached) — the same "read at point of use" convention the rest of the config follows (see
 * e.g. {@code DamageApplier}/{@code DamageCalculator}), which is cheap here since this isn't a
 * combat hot path and it means a plain {@code /valmora reload} (now paired with
 * {@code plugin.reloadConfig()} in {@code ValmoraCommand}) picks up edits immediately.
 */
public class ItemLoreLayout {

    /** One block of the generated lore, in the order it can appear. Config key: lowercase, '-' or '_'. */
    public enum Section {
        BREAKING_POWER, BASE_LORE, LORE_TEMPLATE, STATS, MODIFIERS, ENCHANTMENTS, ABILITIES, RARITY_TAG;

        static Section fromConfig(String raw) {
            return valueOf(raw.trim().toUpperCase().replace('-', '_'));
        }
    }

    private static final List<Section> DEFAULT_ORDER = List.of(
            Section.BREAKING_POWER, Section.BASE_LORE, Section.LORE_TEMPLATE, Section.STATS,
            Section.MODIFIERS, Section.ENCHANTMENTS, Section.ABILITIES, Section.RARITY_TAG);

    private final List<Section> sectionOrder;
    private final boolean spacerBetweenSections;
    private final String breakingPowerFormat;
    private final String statLineFormat;
    private final String modifierLineFormat;
    private final String abilityHeaderFormat;
    private final String abilityManaCostFormat;
    private final String abilityCooldownFormat;
    private final String rarityTagFormat;

    private ItemLoreLayout(List<Section> sectionOrder, boolean spacerBetweenSections,
                            String breakingPowerFormat, String statLineFormat, String modifierLineFormat,
                            String abilityHeaderFormat, String abilityManaCostFormat, String abilityCooldownFormat,
                            String rarityTagFormat) {
        this.sectionOrder = sectionOrder;
        this.spacerBetweenSections = spacerBetweenSections;
        this.breakingPowerFormat = breakingPowerFormat;
        this.statLineFormat = statLineFormat;
        this.modifierLineFormat = modifierLineFormat;
        this.abilityHeaderFormat = abilityHeaderFormat;
        this.abilityManaCostFormat = abilityManaCostFormat;
        this.abilityCooldownFormat = abilityCooldownFormat;
        this.rarityTagFormat = rarityTagFormat;
    }

    public static ItemLoreLayout load(Valmora plugin) {
        FileConfiguration config = plugin.getConfig();
        String base = "items.lore.";

        List<Section> order = new ArrayList<>();
        List<String> configuredOrder = config.getStringList(base + "sections");
        if (configuredOrder.isEmpty()) {
            order.addAll(DEFAULT_ORDER);
        } else {
            for (String raw : configuredOrder) {
                try {
                    order.add(Section.fromConfig(raw));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[items.lore.sections] Unknown lore section '" + raw
                            + "' - skipped. Valid values: " + Arrays.toString(Section.values()));
                }
            }
        }

        return new ItemLoreLayout(
                order,
                config.getBoolean(base + "spacer-between-sections", true),
                config.getString(base + "breaking-power.format", "<dark_gray>Breaking Power {power}"),
                config.getString(base + "stats.line-format", "<gray> ◈ {stat}"),
                config.getString(base + "modifiers.line-format", "<gray> ◆ {label}"),
                config.getString(base + "abilities.header-format", "<gold>Ability: {name} <yellow><bold>{trigger}"),
                config.getString(base + "abilities.mana-cost-format", "<dark_gray>Mana Cost: <aqua>{mana}"),
                config.getString(base + "abilities.cooldown-format", "<dark_gray>Cooldown: <green>{cooldown}s"),
                config.getString(base + "rarity-tag.format", "{color}<bold>{rarity}{type}")
        );
    }

    public List<Section> getSectionOrder() { return sectionOrder; }
    public boolean isSpacerBetweenSections() { return spacerBetweenSections; }
    public String getBreakingPowerFormat() { return breakingPowerFormat; }
    public String getStatLineFormat() { return statLineFormat; }
    public String getModifierLineFormat() { return modifierLineFormat; }
    public String getAbilityHeaderFormat() { return abilityHeaderFormat; }
    public String getAbilityManaCostFormat() { return abilityManaCostFormat; }
    public String getAbilityCooldownFormat() { return abilityCooldownFormat; }
    public String getRarityTagFormat() { return rarityTagFormat; }
}
