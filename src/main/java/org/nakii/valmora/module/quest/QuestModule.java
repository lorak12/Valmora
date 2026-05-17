package org.nakii.valmora.module.quest;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.quest.hider.PlayerHiderManager;
import org.nakii.valmora.module.quest.journal.JournalEventFactory;
import org.nakii.valmora.module.quest.journal.JournalManager;
import org.nakii.valmora.module.quest.pkg.QuestPackageManager;

public class QuestModule implements ReloadableModule {

    private final Valmora plugin;
    private QuestManager questManager;
    private QuestPackageManager packageManager;
    private QuestListener listener;
    private JournalManager journalManager;
    private PlayerHiderManager playerHiderManager;

    public QuestModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Quest Module...");
        this.questManager = new QuestManager(plugin);
        this.playerHiderManager = new PlayerHiderManager(plugin);
        this.journalManager = new JournalManager();
        // Load legacy flat quests/ files first, then package-based quests on top
        new QuestLoader(plugin, questManager.getRegistry()).load();
        this.packageManager = new QuestPackageManager(plugin);
        packageManager.loadAll();
        playerHiderManager.start();
        new QuestEventFactory(questManager).all().forEach(plugin.getScriptModule()::registerEvent);
        plugin.getScriptModule().registerProvider(new QuestVariableProvider());
        plugin.getScriptModule().registerEvent(new JournalEventFactory(journalManager));
        this.listener = new QuestListener(questManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer().getPluginManager().registerEvents(journalManager, plugin);

        // Trigger auto-once objectives for all currently online players
        for (var player : plugin.getServer().getOnlinePlayers()) {
            questManager.startAutoOnceObjectivesForPlayer(player);
        }
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Quest Module...");
        if (playerHiderManager != null) { playerHiderManager.stop(); playerHiderManager = null; }
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
        if (journalManager != null) { HandlerList.unregisterAll(journalManager); journalManager = null; }
        if (questManager != null) { questManager.getRegistry().clear(); questManager = null; }
        packageManager = null;
    }

    @Override public String getId() { return "quest"; }
    @Override public String getName() { return "Quest System"; }

    public QuestManager getQuestManager() { return questManager; }
    public QuestPackageManager getPackageManager() { return packageManager; }
    public JournalManager getJournalManager() { return journalManager; }
    public PlayerHiderManager getPlayerHiderManager() { return playerHiderManager; }
}
