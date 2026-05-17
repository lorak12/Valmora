package org.nakii.valmora.module.quest.journal;

import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

public class JournalEventFactory implements EventFactory {

    private final JournalManager journalManager;

    public JournalEventFactory(JournalManager journalManager) {
        this.journalManager = journalManager;
    }

    @Override
    public String getName() { return "journal"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "open";
        return switch (sub) {
            case "open" -> context -> {
                if (context.getCaster() instanceof org.bukkit.entity.Player player)
                    journalManager.openJournal(player);
            };
            default -> context -> {};
        };
    }
}
