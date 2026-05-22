package org.nakii.valmora.module.script.event.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.module.warp.WarpDefinition;
import org.nakii.valmora.module.warp.WarpManager;
import org.nakii.valmora.module.zone.ZoneManager;
import org.nakii.valmora.util.Formatter;

/**
 * Teleports the caster to a warp, absolute coordinates, or in their look direction.
 *
 * DSL:
 *   teleport warp:<id>             — named warp (respects unlock conditions)
 *   teleport @look <blocks>        — forward in look direction
 *   teleport <x> <y> <z>           — absolute coords in caster's world
 *   teleport <world> <x> <y> <z>   — absolute coords in explicit world
 */
public class TeleportEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "teleport";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length == 0) return ctx -> {};
        return ctx -> ctx.getPlayerCaster().ifPresent(player -> {
            if (isBlockedByZone(player)) {
                player.sendMessage(Formatter.format("<red>Teleportation is disabled in this area."));
                return;
            }
            doTeleport(args, player);
        });
    }

    private boolean isBlockedByZone(Player player) {
        ZoneManager zm = ValmoraAPI.getInstance().getZoneManager();
        if (zm == null) return false;
        return zm.getCurrentZone(player).map(z -> !z.getFlags().teleportation()).orElse(false);
    }

    private void doTeleport(String[] args, Player player) {
        // warp:<id>
        if (args[0].startsWith("warp:")) {
            String warpId = args[0].substring(5);
            WarpManager wm = ValmoraAPI.getInstance().getWarpManager();
            if (wm == null) return;
            WarpDefinition warp = wm.getRegistry().get(warpId).orElse(null);
            if (warp == null) return;
            wm.teleport(player, warp);
            return;
        }

        // @look <blocks>
        if (args[0].equalsIgnoreCase("@look")) {
            double blocks = args.length > 1 ? parseDouble(args[1], 5.0) : 5.0;
            Location eye = player.getEyeLocation();
            Location dest = eye.add(eye.getDirection().normalize().multiply(blocks));
            dest.setYaw(player.getLocation().getYaw());
            dest.setPitch(player.getLocation().getPitch());
            player.teleportAsync(dest);
            return;
        }

        // <x> <y> <z>  (same world as caster)
        if (args.length == 3) {
            double x = parseDouble(args[0], 0), y = parseDouble(args[1], 64), z = parseDouble(args[2], 0);
            Location dest = new Location(player.getWorld(), x, y, z,
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleportAsync(dest);
            return;
        }

        // <world> <x> <y> <z>
        if (args.length >= 4) {
            String worldName = args[0];
            double x = parseDouble(args[1], 0), y = parseDouble(args[2], 64), z = parseDouble(args[3], 0);
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            Location dest = new Location(world, x, y, z,
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleportAsync(dest);
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }
}
