package org.nakii.valmora.module.notify;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.HashMap;
import java.util.Map;

public class NotifyEvent implements EventFactory {

    @Override public String getName() { return "notify"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return ctx -> {};

        // Parse: notify <message> [category:<name>] [io:<type>] [key:value ...]
        StringBuilder msgBuilder = new StringBuilder();
        String category = null;
        String ioName = null;
        Map<String, String> extra = new HashMap<>();

        for (String arg : args) {
            if (arg.startsWith("category:")) {
                category = arg.substring(9);
            } else if (arg.startsWith("io:")) {
                ioName = arg.substring(3);
            } else if (arg.contains(":")) {
                String[] kv = arg.split(":", 2);
                extra.put(kv[0], kv[1]);
            } else {
                if (msgBuilder.length() > 0) msgBuilder.append(' ');
                msgBuilder.append(arg);
            }
        }

        final String message = msgBuilder.toString();
        final String finalCategory = category;
        final String finalIO = ioName;
        final Map<String, String> finalExtra = extra;

        return ctx -> ctx.getPlayerCaster().ifPresent(entity -> {
            if (!(entity instanceof Player player)) return;
            NotifyManager nm = ValmoraAPI.getInstance().getNotifyManager();
            if (nm != null) nm.send(player, message, finalIO, finalCategory, finalExtra);
        });
    }
}
