package org.nakii.valmora.module.gui.event;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.Map;

public class EnchantApplyEventFactory implements EventFactory {

    private final Valmora plugin;

    public EnchantApplyEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "enchant_apply";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3) return context -> {};
        // Syntax: enchant_apply <inputSlotId> <enchantId> <level>
        String inputSlotId = args[0];
        String enchantId = args[1];
        String levelStr = args[2];
        
        return context -> {
            if (!(context instanceof GuiExecutionContext guiContext)) return;
            GuiSession session = guiContext.getSession();
            if (session == null) return;

            Map<String, ItemStack> inputs = session.getInputSnapshot();
            ItemStack item = inputs.get(inputSlotId);
            if (item == null) return;

            try {
                // Resolve variables in enchantId and level if they are passed as $var$
                String resolvedEnchantId = resolve(enchantId, guiContext);
                int level = Integer.parseInt(resolve(levelStr, guiContext));

                EnchantmentHelper.applyEnchantment(item, resolvedEnchantId, level);

                // Return to enchant selection view
                session.getProps().remove("selected_enchant");
                session.setCurrentPage(0);
                
                // Force re-render
                new GuiRenderer(plugin).render(session);
            } catch (Exception ignored) {}
        };
    }

    private String resolve(String input, GuiExecutionContext context) {
        if (input.startsWith("$") && input.endsWith("$")) {
            Object resolved = context.getVariableResolver().resolve(input.substring(1, input.length() - 1), context);
            return resolved != null ? resolved.toString() : input;
        }
        return input;
    }
}
