package org.nakii.valmora.module.warp;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

/**
 * Sign-warp subsystem (added 2026-08-07 — {@code Keys.WARP_ID_KEY} was defined but had zero
 * usages). Follows the common "[warp]" sign convention (EssentialsX/CMI-style):
 *
 * <pre>
 * [warp]
 * &lt;warp id&gt;
 * </pre>
 *
 * Creating one requires the {@code permissions.warp} node (defaults to {@code valmora.admin}; anyone could otherwise place a sign that
 * impersonates a warp trigger). The warp id is stored in the sign block's PDC — never parsed
 * back out of the display text at trigger time (CLAUDE.md §13's item-name-forgery caution
 * applies to signs too: a player could otherwise edit an existing sign's *text* without
 * permission checks re-running, but the stored PDC id is what actually gets used).
 */
public class WarpSignListener implements Listener {

    private static final String HEADER = "[warp]";

    private final Valmora plugin;
    private final WarpManager warpManager;

    public WarpSignListener(Valmora plugin, WarpManager warpManager) {
        this.plugin = plugin;
        this.warpManager = warpManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!HEADER.equalsIgnoreCase(plainLine(event, 0))) return;

        if (!org.nakii.valmora.util.PermissionResolver.has(event.getPlayer(), "warp")) {
            event.getPlayer().sendMessage(Formatter.format("<red>You don't have permission to create a warp sign."));
            event.line(0, net.kyori.adventure.text.Component.empty());
            return;
        }

        String warpId = plainLine(event, 1);
        if (warpId == null || warpId.isEmpty() || warpManager.getRegistry().get(warpId).isEmpty()) {
            event.getPlayer().sendMessage(Formatter.format("<red>Unknown warp: " + warpId));
            event.line(0, net.kyori.adventure.text.Component.empty());
            return;
        }

        event.line(0, Formatter.format("<dark_blue>[warp]"));
        event.getPlayer().sendMessage(Formatter.format("<green>Warp sign created for <white>" + warpId));

        Block block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getState() instanceof Sign sign) {
                sign.getPersistentDataContainer().set(Keys.WARP_ID_KEY, PersistentDataType.STRING, warpId);
                sign.update();
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) return;

        String warpId = sign.getPersistentDataContainer().get(Keys.WARP_ID_KEY, PersistentDataType.STRING);
        if (warpId == null) return;

        warpManager.getRegistry().get(warpId).ifPresentOrElse(
                warp -> warpManager.teleport(event.getPlayer(), warp),
                () -> event.getPlayer().sendMessage(Formatter.format("<red>This warp sign points to a warp that no longer exists.")));
    }

    private String plainLine(SignChangeEvent event, int index) {
        var line = event.line(index);
        return line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line).trim();
    }
}
