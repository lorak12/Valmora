package org.nakii.valmora.module.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.enchant.EnchantmentDefinition;
import org.nakii.valmora.module.enchant.EnchantmentHelper;
import org.nakii.valmora.module.stat.Stat;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                if (args.length >= 2) {
                    // Info by ID from registry
                    Optional<ItemDefinition> defOpt = itemManager.getItemRegistry().getItem(args[1]);
                    if (defOpt.isPresent()) {
                        sendDefinitionInfo(player, defOpt.get());
                    } else {
                        player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Item '" + args[1] + "' not found!"));
                    }
                } else {
                    // Info from held item
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (held == null || held.getType() == Material.AIR) {
                        player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Hold an item or provide an item ID."));
                        return true;
                    }
                    sendHeldItemInfo(player, held, itemManager);
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
                org.nakii.valmora.api.ValmoraAPI.getInstance().getModuleManager().reloadModule("items");
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Item configuration reloaded."));
                break;

            case "enchant":
                if (args.length < 3) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /item enchant <enchant_id> <level>"));
                    return true;
                }

                String enchantId = args[1];
                int enchantLevel;
                try {
                    enchantLevel = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Invalid level: " + args[2]));
                    return true;
                }

                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand == null || mainHand.getType() == Material.AIR) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>You must hold an item in your main hand!"));
                    return true;
                }

                if (!EnchantmentHelper.canApplyEnchantment(mainHand, enchantId)) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>That enchantment cannot be applied to this item!"));
                    return true;
                }

                EnchantmentHelper.applyEnchantment(mainHand, enchantId, enchantLevel);
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Applied <white>" + enchantId + " " + enchantLevel + " <green>to your item."));
                break;

            case "enchantbook":
                if (args.length < 3) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <gray>Usage: /item enchantbook <enchant_id> <level>"));
                    return true;
                }

                String bookEnchantId = args[1];
                int bookLevel;
                try {
                    bookLevel = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Invalid level: " + args[2]));
                    return true;
                }

                ItemStack enchantedBook = EnchantmentHelper.createEnchantedBook(bookEnchantId, bookLevel);
                player.getInventory().addItem(enchantedBook);
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <green>Gave you an enchanted book with <white>" + bookEnchantId + " " + bookLevel + "</green>."));
                break;

            default:
                player.sendMessage(Formatter.format("<dark_gray>[<gold>Valmora<dark_gray>] <red>Unknown subcommand."));
                break;
        }

        return true;
    }

    private void sendDefinitionInfo(Player player, ItemDefinition def) {
        String hr = "<dark_gray><st>                                                </st>";
        player.sendMessage(Formatter.format(hr));
        player.sendMessage(Formatter.format(" <gold><bold>ITEM INFO: " + def.getId().toUpperCase()));
        player.sendMessage(Formatter.format(" <gray>Name: <white>" + def.getName()));
        player.sendMessage(Formatter.format(" <gray>Material: <yellow>" + def.getMaterial().name()));
        Rarity rarity = def.getRarity();
        player.sendMessage(Formatter.format(" <gray>Rarity: " + rarity.getColor() + rarity.getName()));
        player.sendMessage(Formatter.format(" <gray>Type: <green>" + def.getItemType().name()));

        List<String> lore = def.getLore();
        if (!lore.isEmpty()) {
            player.sendMessage(Formatter.format(" <gray>Lore:"));
            for (String line : lore) {
                player.sendMessage(Formatter.format("   <dark_gray>| <white>" + line));
            }
        }

        Map<Stat, Double> stats = def.getStats();
        if (!stats.isEmpty()) {
            player.sendMessage(Formatter.format(" <gray>Stats:"));
            for (Map.Entry<Stat, Double> entry : stats.entrySet()) {
                player.sendMessage(Formatter.format("   <dark_gray>| " + entry.getKey().format(entry.getValue())));
            }
        }

        Map<String, AbilityDefinition> abilities = def.getAbilities();
        if (!abilities.isEmpty()) {
            player.sendMessage(Formatter.format(" <gray>Abilities:"));
            for (AbilityDefinition ability : abilities.values()) {
                player.sendMessage(Formatter.format("   <dark_gray>| <light_purple>" + ability.getName()
                        + " <gray>(" + ability.getTrigger().name() + ")"
                        + " <aqua>CD:<white>" + ability.getCooldown() + "s"
                        + " <aqua>Mana:<white>" + ability.getManaCost()
                        + " <aqua>Range:<white>" + ability.getTargetRange()));
                for (String desc : ability.getDescription()) {
                    player.sendMessage(Formatter.format("     <dark_gray>» <gray>" + desc));
                }
            }
        }

        player.sendMessage(Formatter.format(hr));
    }

    private void sendHeldItemInfo(Player player, ItemStack item, ItemManager itemManager) {
        String hr = "<dark_gray><st>                                                </st>";
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta != null ? meta.getPersistentDataContainer() : null;

        player.sendMessage(Formatter.format(hr));
        player.sendMessage(Formatter.format(" <gold><bold>HELD ITEM INFO"));

        // --- Vanilla / display ---
        player.sendMessage(Formatter.format(" <gray>Material: <yellow>" + item.getType().name()));
        player.sendMessage(Formatter.format(" <gray>Amount: <white>" + item.getAmount()));

        if (meta != null) {
            if (meta.hasDisplayName()) {
                String display = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                player.sendMessage(Formatter.format(" <gray>Display Name: <white>" + display));
            }

            if (meta.hasLore()) {
                List<Component> loreComponents = meta.lore();
                if (loreComponents != null && !loreComponents.isEmpty()) {
                    player.sendMessage(Formatter.format(" <gray>Lore (" + loreComponents.size() + " lines):"));
                    for (Component line : loreComponents) {
                        String plain = PlainTextComponentSerializer.plainText().serialize(line);
                        player.sendMessage(Formatter.format("   <dark_gray>| <white>" + plain));
                    }
                }
            }

            if (meta.hasCustomModelData()) {
                player.sendMessage(Formatter.format(" <gray>Custom Model Data: <white>" + meta.getCustomModelData()));
            }

            if (meta.isUnbreakable()) {
                player.sendMessage(Formatter.format(" <gray>Unbreakable: <green>true"));
            }
        }

        // --- Valmora PDC keys ---
        player.sendMessage(Formatter.format(" <gray>--- <gold>Valmora PDC<gray> ---"));

        if (pdc != null && pdc.has(Keys.ITEM_ID_KEY, PersistentDataType.STRING)) {
            String itemId = pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            player.sendMessage(Formatter.format(" <gray>Item ID: <aqua>" + itemId));

            // Pull full definition for extra context
            itemManager.getItemRegistry().getItem(itemId).ifPresent(def -> {
                Rarity rarity = def.getRarity();
                player.sendMessage(Formatter.format(" <gray>Definition Rarity: " + rarity.getColor() + rarity.getName()));
                player.sendMessage(Formatter.format(" <gray>Definition Type: <green>" + def.getItemType().name()));
            });
        } else {
            player.sendMessage(Formatter.format(" <gray>Item ID: <dark_gray>none (vanilla / unregistered)"));
        }

        if (pdc != null && pdc.has(Keys.RARITY_KEY, PersistentDataType.STRING)) {
            player.sendMessage(Formatter.format(" <gray>PDC Rarity: <white>" + pdc.get(Keys.RARITY_KEY, PersistentDataType.STRING)));
        }

        if (pdc != null && pdc.has(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING)) {
            player.sendMessage(Formatter.format(" <gray>PDC Item Type: <white>" + pdc.get(Keys.ITEM_TYPE_KEY, PersistentDataType.STRING)));
        }

        // --- Stats ---
        if (meta != null) {
            Map<Stat, Double> stats = plugin.getStatModule().loadStats(meta);
            if (!stats.isEmpty()) {
                player.sendMessage(Formatter.format(" <gray>--- <gold>Stats<gray> ---"));
                for (Map.Entry<Stat, Double> entry : stats.entrySet()) {
                    player.sendMessage(Formatter.format("   <dark_gray>| " + entry.getKey().format(entry.getValue())));
                }
            }
        }

        // --- Enchantments ---
        Map<String, Integer> enchants = EnchantmentHelper.getEnchantments(item);
        if (!enchants.isEmpty()) {
            player.sendMessage(Formatter.format(" <gray>--- <gold>Valmora Enchantments<gray> ---"));
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                String enchantId = entry.getKey();
                int level = entry.getValue();
                Optional<EnchantmentDefinition> enchDef = plugin.getEnchantModule().getRegistry().get(enchantId);
                String name = enchDef.map(EnchantmentDefinition::getName).orElse(enchantId);
                String maxLevel = enchDef.map(d -> "/" + d.getAbsoluteMaxLevel()).orElse("");
                player.sendMessage(Formatter.format("   <dark_gray>| <aqua>" + name + " <white>" + level + maxLevel));
            }
        }

        // --- Raw PDC dump ---
        player.sendMessage(Formatter.format(" <gray>--- <gold>All PDC Keys<gray> ---"));
        if (pdc != null) {
            Set<NamespacedKey> keys = pdc.getKeys();
            if (keys.isEmpty()) {
                player.sendMessage(Formatter.format("   <dark_gray>none"));
            } else {
                for (NamespacedKey key : keys) {
                    player.sendMessage(Formatter.format("   <dark_gray>| <white>" + key.toString()));
                }
            }
        } else {
            player.sendMessage(Formatter.format("   <dark_gray>no meta"));
        }

        player.sendMessage(Formatter.format(hr));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterMatches(args[0], List.of("give", "reload", "info", "list", "enchant", "enchantbook"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("info"))) {
            ItemManager itemManager = plugin.getItemManager();
            return filterMatches(args[1], 
                itemManager.getItemRegistry().getAllItemIds().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList()));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("enchant") || args[0].equalsIgnoreCase("enchantbook"))) {
            return filterMatches(args[1],
                plugin.getEnchantModule().getRegistry().getKeys().stream()
                    .collect(Collectors.toList()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filterMatches(args[2], List.of("1", "16", "32", "64"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("enchant")) {
            return filterMatches(args[2], List.of("1", "2", "3", "4", "5"));
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
