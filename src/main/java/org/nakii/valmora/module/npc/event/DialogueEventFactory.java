package org.nakii.valmora.module.npc.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * DSL: dialogue start <dialogue-id>
 */
public class DialogueEventFactory implements EventFactory {

    private final Valmora plugin;

    public DialogueEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "dialogue"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("start")) return context -> {};
        String dialogueId = args[1];
        return context -> context.getPlayerCaster().ifPresent(player -> {
            DialogueManager dm = plugin.getDialogueManager();
            if (dm != null) dm.startDialogue(player, dialogueId);
        });
    }
}
