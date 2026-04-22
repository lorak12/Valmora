package org.nakii.valmora.module.gui;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class GuiExecutionContext extends SimpleExecutionContext {
    private final @Nullable GuiSession session;
    private final Map<String, Object> loopVars = new HashMap<>();

    public GuiExecutionContext(Player player, @Nullable GuiSession session) {
        super(player, player.getLocation(), new MemoryConfiguration());
        this.session = session;
    }

    public @Nullable GuiSession getSession() {
        return session;
    }

    public void setLoopVar(String name, Object value) {
        loopVars.put(name, value);
    }

    public Object getLoopVar(String name) {
        return loopVars.get(name);
    }

    public Map<String, Object> getLoopVars() {
        return loopVars;
    }
}
