package org.nakii.valmora.module.mob;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /mob <spawn|list|reload|info> [args]"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "spawn":
                if (args.length < 2) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /mob spawn <mob> [player]"));
                    return true;
                }

                String mobId = args[1];
                MobDefinition mobDef = mobManager.getMobDefinition(mobId);
                if (mobDef == null) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Mob '" + mobId + "' not found."));
                    return true;
                }

                Player target = player;
                if (args.length >= 3) {
                    target = plugin.getServer().getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Player '" + args[2] + "' not found."));
                        return true;
                    }
                }

                mobManager.spawnMob(mobDef, target.getLocation());
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Spawned <white>" + mobDef.getName() + " <green>at " + target.getName() + "'s location."));
                return true;

            case "list":
                List<String> mobIds = new ArrayList<>(mobManager.getMobRegistry().getAllMobIds());
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                player.sendMessage(Formatter.format(" <gold><bold>AVAILABLE MOBS"));
                for (String id : mobIds) {
                    player.sendMessage(Formatter.format(" <gray>- <white>" + id));
                }
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                return true;

            case "reload":
                org.nakii.valmora.api.ValmoraAPI.getInstance().getModuleManager().reloadModule("mobs");
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Configuration reloaded."));
                return true;

            case "info":
                Entity targetEntity = player.getTargetEntity(10, false);
                if (targetEntity == null) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>You must be looking at a mob to get its info. (Max 10 blocks)"));
                    return true;
                }
                
                if (!targetEntity.getPersistentDataContainer().has(Keys.MOB_ID_KEY, PersistentDataType.STRING)) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>That entity is not a Valmora mob."));
                    return true;
                }
                
                String targetMobId = targetEntity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
                MobDefinition targetMobDef = mobManager.getMobDefinition(targetMobId);
                
                if (targetMobDef == null) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Mob Definition not found for ID: " + targetMobId));
                    return true;
                }
                
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                player.sendMessage(Formatter.format(" <gold><bold>MOB INFO: " + targetMobDef.getId().toUpperCase()));
                player.sendMessage(Formatter.format(" <gray>Name: <white>" + targetMobDef.getName()));
                player.sendMessage(Formatter.format(" <gray>Entity Type: <yellow>" + targetMobDef.getEntityType().name()));
                player.sendMessage(Formatter.format(" <gray>Level: <aqua>" + targetMobDef.getLevel()));
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                return true;

            default:
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Unknown command. Use /mob list to see available mobs."));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("spawn", "list", "reload", "info");
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
