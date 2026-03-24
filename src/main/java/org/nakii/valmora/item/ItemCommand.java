package org.nakii.valmora.item;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ItemCommand implements TabExecutor {

    private final Valmora plugin;

    public ItemCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /item <give|reload|info|list>"));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        ItemManager itemManager = plugin.getItemManager();

        switch (subCommand) {
            case "give":
                if (args.length < 2) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /item give <id> [amount] [player]"));
                    return true;
                }
                
                String itemId = args[1];
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Invalid amount: " + args[2]));
                        return true;
                    }
                }
                
                Player target = player;
                if (args.length >= 4) {
                    target = Bukkit.getPlayer(args[3]);
                    if (target == null) {
                        player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Player '" + args[3] + "' not found!"));
                        return true;
                    }
                }
                
                ItemStack item = itemManager.createItemStack(itemId);
                if (item != null) {
                    item.setAmount(amount);
                    target.getInventory().addItem(item);
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Gave <white>" + amount + "x " + itemId + " <green>to " + target.getName()));
                } else {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Item '" + itemId + "' not found!"));
                }
                break;
                
            case "info":
                if (args.length < 2) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /item info <id>"));
                    return true;
                }
                Optional<ItemDefinition> defOpt = itemManager.getItemRegistry().getItem(args[1]);
                if (defOpt.isPresent()) {
                    ItemDefinition def = defOpt.get();
                    player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                    player.sendMessage(Formatter.format(" <gold><bold>ITEM INFO: " + def.getId().toUpperCase()));
                    player.sendMessage(Formatter.format(" <gray>Name: <white>" + def.getName()));
                    player.sendMessage(Formatter.format(" <gray>Material: <yellow>" + def.getMaterial().name()));
                    player.sendMessage(Formatter.format(" <gray>Rarity: <aqua>" + def.getRarity().name()));
                    player.sendMessage(Formatter.format(" <gray>Type: <green>" + def.getItemType().name()));
                    player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                } else {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Item '" + args[1] + "' not found!"));
                }
                break;
                
            case "list":
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                player.sendMessage(Formatter.format(" <gold><bold>AVAILABLE ITEMS"));
                for (String id : itemManager.getItemRegistry().getAllItemIds()) {
                    player.sendMessage(Formatter.format(" <gray>- <white>" + id));
                }
                player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
                break;
                
            case "reload":
                itemManager.reload();
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Configuration reloaded."));
                break;
                
            default:
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Unknown subcommand."));
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterMatches(args[0], List.of("give", "reload", "info", "list"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("info"))) {
            ItemManager itemManager = plugin.getItemManager();
            return filterMatches(args[1], 
                itemManager.getItemRegistry().getAllItemIds().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filterMatches(args[2], List.of("1", "16", "32", "64"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filterMatches(args[3], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }
        return List.of();
    }

    private List<String> filterMatches(String input, List<String> options) {
        String lowerInput = input.toLowerCase();
        
        return options.stream()
                .filter(option -> option.toLowerCase().contains(lowerInput))
                .sorted((a, b) -> {
                    // Prioritize options that start with the input
                    boolean aStarts = a.toLowerCase().startsWith(lowerInput);
                    boolean bStarts = b.toLowerCase().startsWith(lowerInput);
                    if (aStarts && !bStarts) return -1;
                    if (!aStarts && bStarts) return 1;
                    return a.compareTo(b);
                })
                .collect(Collectors.toList());
    }
}
