package org.nakii.valmora.module.modifier;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.rarity.RarityDefinition;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $item.rarity.*$} for expression-based modifier values (docs/
 * Valmora_Modifier_Framework_Design.docx §5: "The modifier engine should expose rarity context
 * through item variables ... including at least id, name, rank, and power").
 *
 * <p>Namespace-scoped to {@code item.rarity.*} only — a fuller {@code $item.*$} provider (type,
 * stats, etc.) is future work; see docs/MODIFIER_FRAMEWORK_BACKLOG.md. The rarity value is attached
 * to the {@link ExecutionContext} by {@code ModifierEngine} before evaluating any modifier
 * expression/condition against an item (context attachment key {@code "item:rarity"}).
 */
public class ItemRarityVariableProvider implements VariableProvider {

    public static final String ATTACHMENT_KEY = "item:rarity";

    @Override
    public String getNamespace() { return "item"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0 || !path[0].equalsIgnoreCase("rarity")) return null;
        RarityDefinition rarity = context.get(ATTACHMENT_KEY);
        if (rarity == null) return null;
        if (path.length == 1) return rarity.getId();
        return switch (path[1].toLowerCase(java.util.Locale.ROOT)) {
            case "id" -> rarity.getId();
            case "name" -> rarity.getName();
            case "color" -> rarity.getColor();
            case "rank" -> rarity.getRank();
            case "power" -> rarity.getPower();
            default -> null;
        };
    }
}
