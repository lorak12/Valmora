package org.nakii.valmora.module.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin command for distributing pet items — previously nothing in the codebase created an item
 * carrying {@link Keys#PET_ID_KEY}; admins had to hand-tag items manually.
 */
public class PetCommand implements TabExecutor {

    private final Valmora plugin;

    public PetCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            handleList(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            handleGive(sender, args);
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        sender.sendMessage(Formatter.format(" <gold><bold>VALMORA PETS"));
        for (PetDefinition def : plugin.getPetModule().getDefinitions()) {
            sender.sendMessage(Formatter.format(" <gray>- <white>" + def.getName() + " <dark_gray>(" + def.getId() + ")"));
        }
        sender.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!org.nakii.valmora.util.PermissionResolver.has(sender, "pet")) {
            sender.sendMessage(Formatter.format("<red>No permission."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Formatter.format("<red>Usage: /pet give <player> <petId> [level]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Formatter.format("<red>Player not found."));
            return;
        }

        String petId = args[2].toLowerCase();
        PetDefinition def = plugin.getPetModule().getDefinition(petId);
        if (def == null) {
            sender.sendMessage(Formatter.format("<red>Unknown pet: " + petId));
            return;
        }

        int level = 1;
        if (args.length >= 4) {
            try { level = Math.max(1, Math.min(def.getMaxLevel(), Integer.parseInt(args[3]))); }
            catch (NumberFormatException ignored) {}
        }

        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Formatter.format("<gold>" + def.getName() + " <gray>[Lvl " + level + "]"));
        meta.lore(List.of(Formatter.format("<gray>Right-click to summon this pet.")));
        meta.getPersistentDataContainer().set(Keys.PET_ID_KEY, PersistentDataType.STRING, def.getId());
        meta.getPersistentDataContainer().set(Keys.PET_LEVEL_KEY, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(Keys.PET_XP_KEY, PersistentDataType.DOUBLE, 0.0);
        meta.getPersistentDataContainer().set(Keys.PET_INSTANCE_KEY, PersistentDataType.STRING, java.util.UUID.randomUUID().toString());
        item.setItemMeta(meta);

        var leftover = target.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItem(target.getLocation(), item);
        }

        sender.sendMessage(Formatter.format("<green>Gave " + target.getName() + " a " + def.getName() + " (Lvl " + level + ") pet item."));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Formatter.format("<gold><bold>PET COMMANDS:"));
        sender.sendMessage(Formatter.format(" <gray>/pet list"));
        if (org.nakii.valmora.util.PermissionResolver.has(sender, "pet")) {
            sender.sendMessage(Formatter.format(" <gray>/pet give <player> <petId> [level]"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("list", "give"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(args[2], new ArrayList<>(plugin.getPetModule().getDefinitions().stream()
                    .map(PetDefinition::getId).collect(Collectors.toList())));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
