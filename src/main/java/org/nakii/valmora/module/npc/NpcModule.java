package org.nakii.valmora.module.npc;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.npc.dialogue.DialogueDefinition;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;
import org.nakii.valmora.module.npc.dialogue.intercept.ConversationPacketManager;
import org.nakii.valmora.module.npc.event.DialogueEventFactory;
import org.nakii.valmora.module.npc.event.GuiOpenEventFactory;

public class NpcModule implements ReloadableModule {

    private final Valmora plugin;
    private final SimpleRegistry<NpcDefinition> npcRegistry = new SimpleRegistry<>();
    private DialogueManager dialogueManager;
    private ConversationPacketManager packetManager;
    private NpcManager npcManager;
    private NpcListener listener;
    private SkinFileServer skinFileServer;

    public NpcModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling NPC Module...");
        plugin.getScriptModule().registerEvent(new DialogueEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new GuiOpenEventFactory(plugin));
        this.dialogueManager = new DialogueManager(plugin);
        this.packetManager = new ConversationPacketManager(dialogueManager);
        packetManager.register();
        dialogueManager.setPacketManager(packetManager);
        this.npcManager = new NpcManager(plugin, npcRegistry, dialogueManager);

        new NpcLoader(plugin, npcRegistry, dialogueManager.getDialogueRegistry()).load();
        this.listener = new NpcListener(npcManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer().getPluginManager().registerEvents(dialogueManager, plugin);

        // Defer entity spawning to the first tick so the server is fully ticking
        // before we call world.spawn(). Calling world.spawn() inside onEnable()
        // (pre-first-tick) causes entities to be silently dropped by Paper.
        plugin.getServer().getScheduler().runTask(plugin, npcManager::spawnAll);
        npcManager.startRespawnTask();
        npcManager.startLookTask();

        var cfg = plugin.getConfig();
        if (cfg.getBoolean("npc-skin-server.enabled", false)) {
            int port = cfg.getInt("npc-skin-server.port", 2525);
            String host = cfg.getString("npc-skin-server.host", "");
            if (host == null || host.isBlank()) {
                host = plugin.getServer().getIp();
                if (host == null || host.isBlank() || host.equals("0.0.0.0")) {
                    try { host = java.net.InetAddress.getLocalHost().getHostAddress(); }
                    catch (Exception ignored) { host = "127.0.0.1"; }
                }
            }
            this.skinFileServer = new SkinFileServer(plugin.getDataFolder(), port, host, plugin.getLogger());
            try {
                skinFileServer.start();
            } catch (Exception e) {
                plugin.getLogger().warning("[NPC] Failed to start skin file server: " + e.getMessage());
                this.skinFileServer = null;
            }
        }
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling NPC Module...");
        if (packetManager != null) { packetManager.unregister(); packetManager = null; }
        if (npcManager != null) { npcManager.stopRespawnTask(); npcManager.stopLookTask(); npcManager.despawnAll(); }
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
        if (dialogueManager != null) { HandlerList.unregisterAll(dialogueManager); dialogueManager = null; }
        if (skinFileServer != null) { skinFileServer.stop(); skinFileServer = null; }
        npcRegistry.clear();
    }

    @Override public String getId() { return "npc"; }
    @Override public String getName() { return "NPC System"; }

    public NpcManager getNpcManager() { return npcManager; }
    public DialogueManager getDialogueManager() { return dialogueManager; }
    public SkinFileServer getSkinFileServer() { return skinFileServer; }
    public SimpleRegistry<NpcDefinition> getNpcRegistry() { return npcRegistry; }
}
