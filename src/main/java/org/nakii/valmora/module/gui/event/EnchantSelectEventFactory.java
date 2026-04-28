package org.nakii.valmora.module.gui.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

public class EnchantSelectEventFactory implements EventFactory {

    private final Valmora plugin;

    public EnchantSelectEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "enchant_select";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return context -> {};
        // Syntax: enchant_select <inputSlotId> <enchantId>
        String inputSlotId = args[0];
        String enchantId = args[1];

        return context -> {
            if (!(context instanceof GuiExecutionContext guiContext)) return;
            GuiSession session = guiContext.getSession();
            if (session == null) return;

            String resolvedEnchantId = resolve(enchantId, guiContext);

            session.getProps().put("selected_enchant", resolvedEnchantId);
            session.setCurrentPage(0);

            new GuiRenderer(plugin).render(session);
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
