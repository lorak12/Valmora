package org.nakii.valmora.module.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.gui.components.InputComponent;
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
    private GuiListener listener;

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
    }

    public void openGui(Player player, String id, Map<String, Object> props) {
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

        GuiRenderer renderer = new GuiRenderer(plugin);
        GuiSession tempSession = new GuiSession(player, def, null, props);
        String resolvedTitle = renderer.resolveVariables(def.getTitle(), tempSession, null, null);

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
                    if (def.getOnUpdate().conditions() == null || def.getOnUpdate().conditions().evaluate(updateContext)) {
                        if (def.getOnUpdate().actions() != null) def.getOnUpdate().actions().execute(updateContext);
                    } else {
                        if (def.getOnUpdate().failActions() != null) def.getOnUpdate().failActions().execute(updateContext);
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
                session.getDefinition().getOnClose().actions().execute(context);
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
        guiRegistry.clear();
        GuiDefinitionParser parser = new GuiDefinitionParser(plugin);
        YamlLoader<GuiDefinition> loader = new YamlLoader<>(plugin, "guis", "GUIs");
        loader.load(parser::parse, def -> guiRegistry.put(def.getId(), def));
    }

    public Map<String, GuiDefinition> getGuiRegistry() {
        return guiRegistry;
    }
}
