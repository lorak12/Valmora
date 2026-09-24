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
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 2;
    }

    @Override
    public String usage() {
        return "dialogue start <conversation>";
    }

    @Override
    public void references(String[] args, ReferenceSink sink) {
        if (args.length > 1 && args[0].equalsIgnoreCase("start") && !args[1].contains("$")) sink.ref(org.nakii.valmora.infrastructure.config.refs.Kinds.DIALOGUE, args[1]);
    }

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
