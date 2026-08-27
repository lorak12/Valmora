package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.rarity.RarityDefinition;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.Locale;
import java.util.Map;

/**
 * The sole provider for the {@code item} namespace — {@link org.nakii.valmora.module.script
 * .ScriptModule#registerProvider} keys providers by namespace in a plain map (last registration for
 * a given namespace wins, see {@code SimpleRegistry}/{@code VariableResolverImpl}), so everything
 * that wants {@code $item.*$} has to live in this one class rather than each registering its own
 * competing provider.
 *
 * <p>Covers two independent things that happen to share the namespace:
 * <ul>
 *   <li>{@code $item.ability_id$} / {@code $item.ability_trigger$} — attached by {@code
 *   AbilityExecutor} before running the {@code item:pre_ability}/{@code item:post_ability} pipeline
 *   points (see docs/VALMORA_DOCUMENTATION.md §39).</li>
 *   <li>{@code $item.rarity.*$} / {@code $item.type$} / {@code $item.id$} / {@code
 *   $item.stats.<statId>$} — attached by {@code ModifierEngine} before evaluating a modifier
 *   requirement/condition/expression against a specific item (docs/
 *   Valmora_Modifier_Framework_Design.docx §5). {@code stats} reads the item's own baked stat map
 *   (via {@code StatModule.loadStats}) — not the dynamically-resolved effective stats a player has
 *   equipped, so this is safe to compute from inside the modifier engine's own stat-contribution
 *   pass without recursing into it.</li>
 * </ul>
 */
public class ItemAbilityVariableProvider implements VariableProvider {

    /** Context attachment key for the item's resolved {@link RarityDefinition} (may be absent/null). */
    public static final String RARITY_ATTACHMENT_KEY = "item:rarity";
    /** Context attachment key for the item's {@code ItemType} name (String). */
    public static final String TYPE_ATTACHMENT_KEY = "item:type";
    /** Context attachment key for the item's Valmora id, or {@code null} for an unregistered/vanilla item. */
    public static final String ID_ATTACHMENT_KEY = "item:id";
    /** Context attachment key for the item's own baked stat map ({@code Map<String, Double>}). */
    public static final String STATS_ATTACHMENT_KEY = "item:stats";

    @Override
    public String getNamespace() {
        return "item";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;

        if (path[0].equalsIgnoreCase("rarity")) {
            RarityDefinition rarity = context.get(RARITY_ATTACHMENT_KEY);
            if (rarity == null) return null;
            if (path.length == 1) return rarity.getId();
            return switch (path[1].toLowerCase(Locale.ROOT)) {
                case "id" -> rarity.getId();
                case "name" -> rarity.getName();
                case "color" -> rarity.getColor();
                case "rank" -> rarity.getRank();
                case "power" -> rarity.getPower();
                default -> null;
            };
        }

        if (path[0].equalsIgnoreCase("stats")) {
            if (path.length < 2) return null;
            Map<String, Double> stats = context.get(STATS_ATTACHMENT_KEY);
            return stats == null ? null : stats.get(path[1].toLowerCase(Locale.ROOT));
        }

        // Generic one-level attachment lookup — $item.type$, $item.id$, $item.ability_id$,
        // $item.ability_trigger$, and any future single-value "item:<key>" attachment.
        return context.get("item:" + path[0]);
    }
}
