package org.nakii.valmora.module.gui;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Resolves {@code $candidate.item.*$} — the item currently being tested against a
 * {@link org.nakii.valmora.module.gui.components.StorageComponent}'s {@code condition}. The
 * candidate item is injected as a loop var (same mechanism {@code PaginatedComponent} uses)
 * right before the condition is evaluated in {@code GuiListener}.
 */
public class CandidateVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "candidate";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (!(context instanceof GuiExecutionContext guiContext)) return null;
        Object raw = guiContext.getLoopVar("candidate");
        if (!(raw instanceof ItemStack item) || item.getType().isAir()) return null;
        if (path.length < 2 || !path[0].equalsIgnoreCase("item")) return null;

        return switch (path[1].toLowerCase()) {
            case "type" -> ItemType.fromItemStack(item).getId();
            case "material" -> item.getType().name();
            case "amount" -> item.getAmount();
            default -> null;
        };
    }
}
