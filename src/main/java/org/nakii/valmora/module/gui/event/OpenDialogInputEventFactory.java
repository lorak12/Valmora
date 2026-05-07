package org.nakii.valmora.module.gui.event;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiModule;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Shows a Paper dialog with a text input field and stores the result in a GUI session prop.
 *
 * DSL: open_dialog_input &lt;prop_key&gt; [title] [label] [placeholder...] [return=&lt;gui_id&gt;]
 *
 * Optional arguments use underscores in place of spaces.
 * If return= is specified, both Confirm and Cancel navigate to that GUI instead of the current one.
 * The raw typed string is stored in the prop; economy events parse it via CoinExpressionParser.
 *
 * Example: open_dialog_input sign_value Deposit_Coins Amount e.g._2.5k return=bank
 */
public class OpenDialogInputEventFactory implements EventFactory {

    private final Valmora plugin;
    private final GuiModule guiModule;

    public OpenDialogInputEventFactory(Valmora plugin, GuiModule guiModule) {
        this.plugin = plugin;
        this.guiModule = guiModule;
    }

    @Override
    public String getName() { return "open_dialog_input"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return context -> {};
        String propKey = args[0];

        // Parse optional display params and optional return= override
        Component title = Component.text("Enter Value");
        Component label = Component.text("Value");
        String returnGui = null;
        List<String> placeholderParts = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("return=")) {
                returnGui = arg.substring(7);
            } else if (i == 1) {
                title = decoded(arg);
            } else if (i == 2) {
                label = decoded(arg);
            } else {
                placeholderParts.add(decode(arg));
            }
        }
        String placeholder = String.join(" ", placeholderParts);
        final String finalReturnGui = returnGui;

        // Capture for lambdas
        final Component fTitle = title;
        final Component fLabel = label;
        final String fPlaceholder = placeholder;

        return context -> {
            if (!(context instanceof GuiExecutionContext guiCtx)) return;
            GuiSession session = guiCtx.getSession();
            if (session == null) return;
            Player player = session.getPlayer();

            session.setInputPending(true);
            session.setInputPropKey(propKey);

            Dialog dialog = buildDialog(fTitle, fLabel, fPlaceholder, propKey, session, finalReturnGui);

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory(); // InventoryCloseEvent fires; guard skips session destruction
                player.showDialog(dialog);
            });
        };
    }

    private Dialog buildDialog(Component title, Component label, String placeholder,
                               String propKey, GuiSession session, String returnGui) {
        String sessionGuiId = session.getDefinition().getId();

        var confirmAction = DialogAction.customClick(
            (view, audience) -> {
                if (!(audience instanceof Player p)) return;
                String raw = view.getText("value");
                if (raw != null && !raw.isBlank()) {
                    session.getProps().put(propKey, raw.trim());
                }
                clearPending(session);
                String target = returnGui != null ? returnGui : sessionGuiId;
                reopenGui(p, session, target);
            },
            ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()
        );

        var cancelAction = DialogAction.customClick(
            (view, audience) -> {
                if (!(audience instanceof Player p)) return;
                clearPending(session);
                String target = returnGui != null ? returnGui : sessionGuiId;
                reopenGui(p, session, target);
            },
            ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()
        );

        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(title)
                .canCloseWithEscape(true)
                .inputs(List.of(
                    DialogInput.text("value", label)
                        .initial(placeholder)
                        .width(250)
                        .build()
                ))
                .build()
            )
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("Confirm")).action(confirmAction).build(),
                ActionButton.builder(Component.text("Cancel")).action(cancelAction).build()
            ))
        );
    }

    private void clearPending(GuiSession session) {
        session.setInputPending(false);
        session.setInputPropKey(null);
        if (session.getUpdateTask() != null) {
            session.getUpdateTask().cancel();
            session.setUpdateTask(null);
        }
    }

    private void reopenGui(Player player, GuiSession session, String guiId) {
        var props = new HashMap<>(session.getProps());
        Bukkit.getScheduler().runTask(plugin, () -> guiModule.openGui(player, guiId, props));
    }

    private static Component decoded(String arg) {
        return Component.text(decode(arg));
    }

    private static String decode(String s) {
        return s.replace('_', ' ');
    }
}
