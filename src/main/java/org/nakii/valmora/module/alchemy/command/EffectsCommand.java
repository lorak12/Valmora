package org.nakii.valmora.module.alchemy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;

import java.util.HashMap;

public class EffectsCommand implements CommandExecutor {

    private final Valmora plugin;

    public EffectsCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Only players can use this command."));
            return true;
        }

        if (!plugin.getGuiModule().getGuiRegistry().containsKey("active_effects")) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "Active effects GUI is not configured. Add active_effects.yml to your guis folder."));
            return true;
        }

        plugin.getGuiModule().openGui(player, "active_effects", new HashMap<>());
        return true;
    }
}
