package org.nakii.valmora.module.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.gui.components.InputComponent;
import org.nakii.valmora.module.gui.event.OpenDialogInputEventFactory;
import org.nakii.valmora.module.gui.parser.GuiDefinitionParser;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.script.event.ConditionAbortException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiModule implements ReloadableModule {

    private final Valmora plugin;
    private final Map<String, GuiDefinition> guiRegistry = new HashMap<>();
    private final Map<UUID, GuiSession> openSessions = new HashMap<>();
    private GuiListener listener;
    private final List<String> registeredCommandNames = new ArrayList<>();

    public GuiModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.listener = new GuiListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        
        plugin.getScriptModule().registerProvider(new GuiVariableProvider(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.SoundEventFactory());
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.OpenGuiEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.CloseEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.GiveXpEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.EnchantApplyEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.EnchantSelectEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.EnchantRemoveEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.EnchantBackEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.GuiForceCraftEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.AlchemyBrewStartEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.AlchemyBrewEventFactory(plugin));

        plugin.getScriptModule().registerEvent(new OpenDialogInputEventFactory(plugin, this));

        loadGuis();
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }
        for (UUID uuid : new HashSet<>(openSessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) closeGuiSession(player);
        }
        unregisterGuiCommands();
    }

    public void openGui(Player player, String id, Map<String, Object> props) {
        GuiDefinition def = guiRegistry.get(id);
        if (def == null) return;

        GuiRenderer renderer = new GuiRenderer(plugin);
        // Create a prop-bearing temp session BEFORE running on-open so $prop.*$ resolves correctly.
        GuiSession tempSession = new GuiSession(player, def, null, props);
        String resolvedTitle = renderer.resolveVariables(def.getTitle(), tempSession, null, null);

        // Fire onOpen — use tempSession context so PropVariableProvider can read incoming props.
        if (def.getOnOpen() != null) {
            GuiExecutionContext openCtx = new GuiExecutionContext(player, tempSession);
            if (def.getOnOpen().conditions() != null && !def.getOnOpen().conditions().evaluate(openCtx)) {
                if (def.getOnOpen().failActions() != null) {
                    try { def.getOnOpen().failActions().execute(openCtx); } catch (ConditionAbortException ignored) {}
                }
                return;
            }
            if (def.getOnOpen().actions() != null) {
                try {
                    def.getOnOpen().actions().execute(openCtx);
                } catch (ConditionAbortException ignored) {
                    if (def.getOnOpen().failActions() != null) {
                        try { def.getOnOpen().failActions().execute(openCtx); } catch (ConditionAbortException ignored2) {}
                    }
                }
            }
        }

        Inventory inv = Bukkit.createInventory(null, def.getRows() * 9, org.nakii.valmora.util.Formatter.format(resolvedTitle));
        GuiSession session = new GuiSession(player, def, inv, props);
        openSessions.put(player.getUniqueId(), session);
        
        renderer.render(session);
        
        player.openInventory(inv);

        if (def.getUpdateIntervalTicks() > 0) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                // 1. Run the on-update script if present
                if (def.getOnUpdate() != null) {
                    GuiExecutionContext updateContext = new GuiExecutionContext(player, session);
                    if (def.getOnUpdate().conditions() != null && !def.getOnUpdate().conditions().evaluate(updateContext)) {
                        if (def.getOnUpdate().failActions() != null) {
                            try { def.getOnUpdate().failActions().execute(updateContext); } catch (ConditionAbortException ignored) {}
                        }
                    } else if (def.getOnUpdate().actions() != null) {
                        try {
                            def.getOnUpdate().actions().execute(updateContext);
                        } catch (ConditionAbortException ignored) {
                            if (def.getOnUpdate().failActions() != null) {
                                try { def.getOnUpdate().failActions().execute(updateContext); } catch (ConditionAbortException ignored2) {}
                            }
                        }
                    }
                }
                
                // 2. Re-render the GUI
                renderer.render(session);
            }, 
                def.getUpdateIntervalTicks(), def.getUpdateIntervalTicks());
            session.setUpdateTask(task);
        }
    }

    public void openGui(Player player, String id) {
        openGui(player, id, new HashMap<>());
    }

    public void closeGuiSession(Player player) {
        GuiSession session = openSessions.remove(player.getUniqueId());
        if (session != null) {
            if (session.getUpdateTask() != null) session.getUpdateTask().cancel();
            
            GuiExecutionContext context = new GuiExecutionContext(player, session);
            if (session.getDefinition().getOnClose() != null && session.getDefinition().getOnClose().actions() != null) {
                try { session.getDefinition().getOnClose().actions().execute(context); } catch (ConditionAbortException ignored) {}
            }

            // --- BEST PRACTICE ITEM REFUND LOGIC ---
            org.bukkit.inventory.Inventory inv = session.getInventory();
            java.util.List<java.util.List<Character>> layout = session.getDefinition().getLayout();

            for (int r = 0; r < layout.size(); r++) {
                java.util.List<Character> row = layout.get(r);
                for (int c = 0; c < row.size(); c++) {
                    char ch = row.get(c);
                    // Only target valid InputComponents. Outputs and Displays are ignored.
                    if (session.getDefinition().getComponents().get(ch) instanceof InputComponent) {
                        int slot = r * 9 + c;
                        org.bukkit.inventory.ItemStack item = inv.getItem(slot);
                        
                        if (item != null && item.getType() != org.bukkit.Material.AIR) {
                            // 1. Clear slot FIRST to prevent race-condition dupes
                            inv.setItem(slot, null);
                            
                            // 2. Add to player inventory
                            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(item);
                            
                            // 3. If inventory is full, drop leftovers at player's location
                            for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                            }
                        }
                    }
                }
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
        unregisterGuiCommands();
        guiRegistry.clear();
        GuiDefinitionParser parser = new GuiDefinitionParser(plugin);
        YamlLoader<GuiDefinition> loader = new YamlLoader<>(plugin, "guis", "GUIs");
        loader.load(parser::parse, def -> {
            guiRegistry.put(def.getId(), def);
            if (def.getCommand() != null) {
                registerGuiCommand(def);
            }
        });
        if (!registeredCommandNames.isEmpty()) {
            syncCommandsWithClients();
        }
    }

    private void registerGuiCommand(GuiDefinition def) {
        org.bukkit.command.CommandMap commandMap = Bukkit.getServer().getCommandMap();
        String name = def.getCommand().toLowerCase();
        GuiOpenCommand cmd = new GuiOpenCommand(name, def.getId(), def.getCommandPermission(), this);
        if (commandMap.register("valmora", cmd)) {
            registeredCommandNames.add(name);
            plugin.getLogger().info("[GUI] Registered command /" + name + " → opens GUI '" + def.getId() + "'");
        } else {
            plugin.getLogger().warning("[GUI] Could not register command /" + name + " for GUI '" + def.getId() + "' — name already taken.");
        }
    }

    private void unregisterGuiCommands() {
        if (registeredCommandNames.isEmpty()) return;
        Map<String, org.bukkit.command.Command> knownCommands = Bukkit.getServer().getCommandMap().getKnownCommands();
        for (String name : registeredCommandNames) {
            knownCommands.remove(name);
            knownCommands.remove("valmora:" + name);
        }
        registeredCommandNames.clear();
        syncCommandsWithClients();
    }

    private void syncCommandsWithClients() {
        try {
            plugin.getServer().getClass().getMethod("syncCommands").invoke(plugin.getServer());
        } catch (Exception e) {
            plugin.getLogger().warning("[GUI] Could not sync commands with clients: " + e.getMessage());
        }
    }

    public Map<String, GuiDefinition> getGuiRegistry() {
        return guiRegistry;
    }
}
