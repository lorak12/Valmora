package org.nakii.valmora.module.notify.io;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.nakii.valmora.module.notify.NotifyIO;
import org.nakii.valmora.util.Formatter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real advancement-toast notifications, using the standard "dynamic fake advancement" technique:
 * a throwaway, hidden advancement is loaded via {@link org.bukkit.UnsafeValues#loadAdvancement},
 * instantly granted to trigger the toast, then removed a few ticks later. This is the supported
 * public-API equivalent of a packet-based toast — no NMS/packet library needed after all.
 */
public class AdvancementIO implements NotifyIO {

    private final Plugin plugin;
    private final AtomicLong counter = new AtomicLong();

    public AdvancementIO(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "advancement"; }

    @Override
    public void send(Player player, String message, Map<String, String> settings) {
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(Formatter.format(message));
        String frame = settings.getOrDefault("frame", "task").toLowerCase();
        if (!frame.equals("task") && !frame.equals("goal") && !frame.equals("challenge")) frame = "task";

        Material icon = Material.matchMaterial(settings.getOrDefault("icon", "EXPERIENCE_BOTTLE").toUpperCase());
        if (icon == null) icon = Material.EXPERIENCE_BOTTLE;

        NamespacedKey key = new NamespacedKey(plugin, "toast_" + counter.incrementAndGet() + "_" + UUID.randomUUID());
        String json = buildAdvancementJson(icon, plainTitle, frame);

        Advancement advancement;
        try {
            advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
        } catch (Exception e) {
            // Fall back to action bar if the server rejects the generated JSON for any reason.
            player.sendActionBar(Formatter.format("✦ " + message));
            return;
        }
        if (advancement == null) {
            player.sendActionBar(Formatter.format("✦ " + message));
            return;
        }

        var progress = player.getAdvancementProgress(advancement);
        for (String criterion : advancement.getCriteria()) {
            progress.awardCriteria(criterion);
        }

        // Remove the throwaway advancement shortly after — long enough for the toast to render.
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getUnsafe().removeAdvancement(key), 40L);
    }

    private String buildAdvancementJson(Material icon, String title, String frame) {
        String escapedTitle = escapeJson(title);
        return "{"
                + "\"criteria\":{\"impossible\":{\"trigger\":\"minecraft:impossible\"}},"
                + "\"display\":{"
                + "\"icon\":{\"item\":\"minecraft:" + icon.getKey().getKey() + "\"},"
                + "\"title\":{\"text\":\"" + escapedTitle + "\"},"
                + "\"description\":{\"text\":\"\"},"
                + "\"frame\":\"" + frame + "\","
                + "\"announce_to_chat\":false,"
                + "\"show_toast\":true,"
                + "\"hidden\":true"
                + "}"
                + "}";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
