package org.nakii.valmora.module.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nakii.valmora.util.Formatter;

import java.util.Collections;
import java.util.List;

public class GuiOpenCommand extends Command {

    private final GuiModule guiModule;
    private final String guiId;

    public GuiOpenCommand(String name, String guiId, String permission, GuiModule guiModule) {
        super(name);
        this.guiId = guiId;
        this.guiModule = guiModule;
        if (permission != null) {
            setPermission(permission);
            setPermissionMessage("<red>You don't have permission to use this command.");
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players."));
            return true;
        }
        if (getPermission() != null && !player.hasPermission(getPermission())) {
            player.sendMessage(Formatter.format(getPermissionMessage()));
            return true;
        }
        guiModule.openGui(player, guiId);
        return true;
    }
}
