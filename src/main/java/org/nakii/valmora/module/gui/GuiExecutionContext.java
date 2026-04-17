package org.nakii.valmora.module.gui;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

public class GuiExecutionContext extends SimpleExecutionContext {
    private final @Nullable GuiSession session;

    public GuiExecutionContext(Player player, @Nullable GuiSession session) {
        super(player, player.getLocation(), new MemoryConfiguration());
        this.session = session;
    }

    public @Nullable GuiSession getSession() {
        return session;
    }
}
