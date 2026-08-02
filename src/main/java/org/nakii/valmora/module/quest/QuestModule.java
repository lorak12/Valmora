package org.nakii.valmora.module.quest;

import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.quest.hider.PlayerHiderManager;
import org.nakii.valmora.module.quest.journal.JournalEventFactory;
import org.nakii.valmora.module.quest.journal.JournalManager;
import org.nakii.valmora.module.quest.objective.DelayObjectiveHandler;
import org.nakii.valmora.module.quest.objective.NpcRangeObjectiveHandler;
import org.nakii.valmora.module.quest.objective.TimerObjectiveHandler;
import org.nakii.valmora.module.quest.pkg.QuestPackageManager;
import org.nakii.valmora.module.quest.board.QuestBoardEventFactory;
import org.nakii.valmora.module.quest.board.QuestBoardLoader;
import org.nakii.valmora.module.quest.board.QuestBoardManager;
import org.nakii.valmora.module.quest.board.QuestBoardRegistry;
import org.nakii.valmora.module.quest.board.QuestBoardVariableProvider;

public class QuestModule implements ReloadableModule {

    private final Valmora plugin;
    private QuestManager questManager;
    private QuestPackageManager packageManager;
    private QuestListener listener;
    private JournalManager journalManager;
    private PlayerHiderManager playerHiderManager;
    private NpcRangeObjectiveHandler npcRangeHandler;
    private TimerObjectiveHandler timerHandler;
    private QuestBoardRegistry questBoardRegistry;
    private QuestBoardManager questBoardManager;

    public QuestModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Quest Module...");
        this.questManager = new QuestManager(plugin);
        questManager.registerObjectiveHandler(new DelayObjectiveHandler(plugin));
        this.timerHandler = new TimerObjectiveHandler(plugin, questManager);
        questManager.registerObjectiveHandler(timerHandler);
        this.npcRangeHandler = new NpcRangeObjectiveHandler(plugin, questManager);
        questManager.registerObjectiveHandler(npcRangeHandler);
        this.playerHiderManager = new PlayerHiderManager(plugin);
        this.journalManager = new JournalManager();
        // Load legacy flat quests/ files first, then package-based quests on top
        new QuestLoader(plugin, questManager.getRegistry()).load();
        this.packageManager = new QuestPackageManager(plugin);
        packageManager.loadAll();
        playerHiderManager.start();
        npcRangeHandler.start();
        new QuestEventFactory(questManager).all().forEach(plugin.getScriptModule()::registerEvent);
        plugin.getScriptModule().registerProvider(new QuestVariableProvider());

        this.questBoardRegistry = new QuestBoardRegistry();
        new QuestBoardLoader(plugin, questBoardRegistry).load();
        this.questBoardManager = new QuestBoardManager(plugin, questBoardRegistry);
        new QuestBoardEventFactory(questBoardManager).all().forEach(plugin.getScriptModule()::registerEvent);
        plugin.getScriptModule().registerProvider(new QuestBoardVariableProvider());
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
        if (npcRangeHandler != null) { npcRangeHandler.stop(); npcRangeHandler = null; }
        if (timerHandler != null) { timerHandler.cancelAll(); timerHandler = null; }
        if (playerHiderManager != null) { playerHiderManager.stop(); playerHiderManager = null; }
        if (listener != null) { HandlerList.unregisterAll(listener); listener = null; }
        if (journalManager != null) { HandlerList.unregisterAll(journalManager); journalManager = null; }
        if (questManager != null) { questManager.getRegistry().clear(); questManager = null; }
        if (questBoardRegistry != null) { questBoardRegistry.clear(); questBoardRegistry = null; }
        questBoardManager = null;
        packageManager = null;
    }

    @Override public String getId() { return "quest"; }
    @Override public String getName() { return "Quest System"; }

    public QuestManager getQuestManager() { return questManager; }
    public QuestPackageManager getPackageManager() { return packageManager; }
    public JournalManager getJournalManager() { return journalManager; }
    public PlayerHiderManager getPlayerHiderManager() { return playerHiderManager; }
    public QuestBoardManager getQuestBoardManager() { return questBoardManager; }
}
