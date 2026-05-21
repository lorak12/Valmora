package org.nakii.valmora.module.collection;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.gui.GuiModule;

public class CollectionCommand implements CommandExecutor {

    private final Valmora plugin;

    public CollectionCommand(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        GuiModule guiModule = plugin.getGuiModule();
        if (guiModule == null) {
            player.sendMessage("GUI module is not available.");
            return true;
        }

        guiModule.openGui(player, "collections_categories");
        return true;
    }
}
