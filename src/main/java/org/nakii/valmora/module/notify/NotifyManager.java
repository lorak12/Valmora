package org.nakii.valmora.module.notify;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class NotifyManager {

    private final Map<String, NotifyIO> ioRegistry = new HashMap<>();
    private final Map<String, Map<String, String>> categories = new HashMap<>();

    public NotifyManager() {
        // Built-in category defaults
        categories.put("info", Map.of("io", "chat"));
        categories.put("error", Map.of("io", "actionbar"));
    }

    public void registerIO(NotifyIO io) {
        ioRegistry.put(io.getName().toLowerCase(), io);
    }

    public void loadCategory(String name, Map<String, String> settings) {
        categories.put(name.toLowerCase(), new HashMap<>(settings));
    }

    /**
     * Sends a notification to a player.
     *
     * @param player     recipient
     * @param message    the message text (MiniMessage)
     * @param ioName     explicit IO type override (nullable — falls back to category or "chat")
     * @param category   category name to load default settings from (nullable)
     * @param overrides  per-call setting overrides (e.g. barColor, sound)
     */
    public void send(Player player, String message, String ioName, String category, Map<String, String> overrides) {
        Map<String, String> settings = new HashMap<>();

        // 1. Apply category defaults
        if (category != null) {
            Map<String, String> catSettings = categories.get(category.toLowerCase());
            if (catSettings != null) settings.putAll(catSettings);
        }

        // 2. Apply per-call overrides
        if (overrides != null) settings.putAll(overrides);

        // 3. Resolve IO name: explicit arg > category io key > "chat"
        String resolvedIO = ioName != null ? ioName
                : settings.getOrDefault("io", "chat");

        NotifyIO io = ioRegistry.getOrDefault(resolvedIO.toLowerCase(), ioRegistry.get("chat"));
        if (io != null) io.send(player, message, Collections.unmodifiableMap(settings));
    }

    /** Convenience for objective notifications that only need a category. */
    public void sendCategory(Player player, String message, String category) {
        send(player, message, null, category, null);
    }
}
