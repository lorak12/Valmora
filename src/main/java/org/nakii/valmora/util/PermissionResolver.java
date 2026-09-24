package org.nakii.valmora.util;

import org.bukkit.command.CommandSender;
import org.nakii.valmora.Valmora;

/**
 * HC-006/HC-013: every admin command used to hardcode the literal string {@code "valmora.admin"},
 * so a server couldn't grant a staff member (say) eco access without also handing them reload/
 * pack-install/every other admin command. Each command now resolves its own permission node
 * through here — {@code permissions.<key>} in config.yml if set, else {@code permissions.admin},
 * else the literal {@code "valmora.admin"} — so the *default* behavior (one blanket admin node)
 * is unchanged, but an admin can carve out {@code permissions.eco: "valmora.staff.eco"} etc.
 * without any code change.
 */
public final class PermissionResolver {

    private PermissionResolver() {
    }

    /** Resolves the permission node for {@code key} (e.g. "eco", "reload", "pack"). */
    public static String node(String key) {
        Valmora plugin = Valmora.getInstance();
        if (plugin == null || plugin.getConfig() == null) return "valmora.admin";
        String adminFallback = plugin.getConfig().getString("permissions.admin", "valmora.admin");
        return plugin.getConfig().getString("permissions." + key, adminFallback);
    }

    /** {@code sender.hasPermission(node(key))} — the common case at every command's gate check. */
    public static boolean has(CommandSender sender, String key) {
        return sender.hasPermission(node(key));
    }
}
