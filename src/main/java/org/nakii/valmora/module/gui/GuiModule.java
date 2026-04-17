package org.nakii.valmora.module.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.gui.parser.GuiDefinitionParser;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class GuiModule implements ReloadableModule {

    private final Valmora plugin;
    private final Map<String, GuiDefinition> guiRegistry = new HashMap<>();
    private final Map<UUID, GuiSession> openSessions = new HashMap<>();

    public GuiModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        loadGuis();
        plugin.getServer().getPluginManager().registerEvents(new GuiListener(plugin, this), plugin);
        
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.SoundEventFactory());
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.OpenGuiEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.CloseEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.GiveXpEventFactory(plugin));
    }

    @Override
    public void onDisable() {
        for (UUID uuid : new HashSet<>(openSessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) closeGuiSession(player);
        }
    }

    public void openGui(Player player, String id) {
        GuiDefinition def = guiRegistry.get(id);
        if (def == null) return;

        // Fire onOpen conditions/actions
        GuiExecutionContext context = new GuiExecutionContext(player, null);
        if (def.getOnOpen() != null && def.getOnOpen().conditions() != null) {
            if (!def.getOnOpen().conditions().evaluate(context)) {
                if (def.getOnOpen().failActions() != null) def.getOnOpen().failActions().execute(context);
                return;
            }
        }
        if (def.getOnOpen() != null && def.getOnOpen().actions() != null) {
            def.getOnOpen().actions().execute(context);
        }

        Inventory inv = Bukkit.createInventory(null, def.getRows() * 9, def.getTitle());
        GuiSession session = new GuiSession(player, def, inv);
        openSessions.put(player.getUniqueId(), session);
        
        GuiRenderer renderer = new GuiRenderer(plugin);
        renderer.render(session);
        
        player.openInventory(inv);

        if (def.getUpdateIntervalTicks() > 0) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> renderer.render(session), 
                def.getUpdateIntervalTicks(), def.getUpdateIntervalTicks());
            session.setUpdateTask(task);
        }
    }

    public void closeGuiSession(Player player) {
        GuiSession session = openSessions.remove(player.getUniqueId());
        if (session != null) {
            if (session.getUpdateTask() != null) session.getUpdateTask().cancel();
            
            GuiExecutionContext context = new GuiExecutionContext(player, session);
            if (session.getDefinition().getOnClose() != null && session.getDefinition().getOnClose().actions() != null) {
                session.getDefinition().getOnClose().actions().execute(context);
            }
        }
    }

    public GuiSession getSession(UUID uuid) {
        return openSessions.get(uuid);
    }

    @Override
    public String getId() {
        return "gui";
    }

    @Override
    public String getName() {
        return "GUI System";
    }

    private void loadGuis() {
        guiRegistry.clear();
        GuiDefinitionParser parser = new GuiDefinitionParser(plugin);
        YamlLoader<GuiDefinition> loader = new YamlLoader<>(plugin, "guis", "GUIs");
        loader.load(parser::parse, def -> guiRegistry.put(def.getId(), def));
    }

    public Map<String, GuiDefinition> getGuiRegistry() {
        return guiRegistry;
    }
}
