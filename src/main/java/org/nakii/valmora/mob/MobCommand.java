package org.nakii.valmora.mob;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.nakii.valmora.Valmora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MobCommand implements TabExecutor {

    private final Valmora plugin;
    private final MobManager mobManager;

    public MobCommand(Valmora plugin, MobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§cUsage: /mob <spawn|list|reload> [mob] [player]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "spawn":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mob spawn <mob> [player]");
                    return true;
                }

                String mobId = args[1];
                MobDefinition mobDef = mobManager.getMobDefinition(mobId);
                if (mobDef == null) {
                    player.sendMessage("§cMob '" + mobId + "' not found.");
                    return true;
                }

                Player target = player;
                if (args.length >= 3) {
                    target = plugin.getServer().getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage("§cPlayer '" + args[2] + "' not found.");
                        return true;
                    }
                }

                mobManager.spawnMob(mobDef, target.getLocation());
                player.sendMessage("§aSpawned '" + mobDef.getName() + "' at your location.");
                return true;

            case "list":
                List<String> mobIds = new ArrayList<>(mobManager.getMobRegistry().getAllMobIds());
                player.sendMessage("§eAvailable mobs: " + String.join(", ", mobIds));
                return true;

            case "reload":
                mobManager.reload();
                player.sendMessage("§aReloaded mobs.");
                return true;

            default:
                player.sendMessage("§cUnknown command. Use /mob list to see available mobs.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("spawn", "list", "reload");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            List<String> mobIds = new ArrayList<>(mobManager.getMobRegistry().getAllMobIds());
            StringUtil.copyPartialMatches(args[1], mobIds, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            List<String> playerNames = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                playerNames.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[2], playerNames, completions);
        }

        completions.sort(String::compareTo);
        return completions;
    }
}
