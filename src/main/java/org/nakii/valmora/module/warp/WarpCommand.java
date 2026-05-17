package org.nakii.valmora.module.warp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;

public class WarpCommand implements TabExecutor {

    private final Valmora plugin;

    public WarpCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        WarpManager wm = plugin.getWarpManager();
        if (wm == null) { player.sendMessage(Formatter.format("<red>Warp system not loaded.")); return true; }

        if (args.length == 0) {
            plugin.getGuiModule().openGui(player, "fast_travel", new java.util.HashMap<>());
            return true;
        }

        wm.getRegistry().get(args[0]).ifPresentOrElse(
                warp -> wm.teleport(player, warp),
                () -> player.sendMessage(Formatter.format("<red>Unknown warp: " + args[0]))
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        WarpManager wm = plugin.getWarpManager();
        if (wm == null || args.length != 1) return List.of();
        List<String> completions = new ArrayList<>(wm.getRegistry().getKeys());
        completions.removeIf(k -> !k.startsWith(args[0].toLowerCase()));
        return completions;
    }
}
