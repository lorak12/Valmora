package org.nakii.valmora.module.script.variable.providers;

import org.bukkit.Bukkit;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

public class ServerVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "server";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        
        String key = path[0];
        if (key.equalsIgnoreCase("online")) return Bukkit.getOnlinePlayers().size();
        if (key.equalsIgnoreCase("max_players")) return Bukkit.getMaxPlayers();
        if (key.equalsIgnoreCase("motd")) return Bukkit.motd();
        
        return null;
    }
}
