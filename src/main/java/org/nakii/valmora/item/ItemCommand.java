package org.nakii.valmora.item;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;

public class ItemCommand implements TabExecutor {

    private final Valmora plugin;

    public ItemCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("Usage: /item <id>");
            return true;
        }

        String itemId = args[0];
        ItemManager itemManager = plugin.getItemManager();
        itemManager.getItemRegistry().createItemStack(itemId).ifPresentOrElse(item -> {
            player.getInventory().addItem(item);
            player.sendMessage("Gave you: " + itemId);
        }, () -> player.sendMessage("Item '" + itemId + "' not found!"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1){
            ItemManager itemManager = plugin.getItemManager();
                    return filterMatches(args[0], 
                        itemManager.getItemRegistry().getAllItemIds().stream()
                            .map(String::toLowerCase)
                            .collect(Collectors.toList()));
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
