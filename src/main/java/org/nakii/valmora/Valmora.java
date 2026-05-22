package org.nakii.valmora;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;
import org.nakii.valmora.module.item.ItemCommand;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.item.AbilityManager;
import org.nakii.valmora.module.mob.MobCommand;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ProfileCommand;
import org.nakii.valmora.module.recipe.RecipeModule;
import org.nakii.valmora.module.combat.CombatModule;
import org.nakii.valmora.module.time.TimeModule;
import org.nakii.valmora.module.time.TimeCommand;
import org.nakii.valmora.module.combat.DamageIndicatorManager;
import org.nakii.valmora.module.gui.GuiCommand;
import org.nakii.valmora.module.gui.GuiModule;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.database.DatabaseFactory;
import org.nakii.valmora.module.stat.StatCommand;
import org.nakii.valmora.module.stat.StatModule;
import org.nakii.valmora.module.ui.UIManager;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.ModuleManager;
import org.nakii.valmora.module.skill.SkillCommand;
import org.nakii.valmora.module.skill.SkillManager;
import org.nakii.valmora.module.skill.SkillModule;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.enchant.EnchantModule;
import org.nakii.valmora.module.alchemy.AlchemyModule;
import org.nakii.valmora.module.zone.ZoneModule;
import org.nakii.valmora.module.zone.ZoneManager;
import org.nakii.valmora.module.zone.ZoneCommand;
import org.nakii.valmora.module.resource.ResourceModule;
import org.nakii.valmora.module.fishing.FishingModule;
import org.nakii.valmora.module.npc.NpcModule;
import org.nakii.valmora.module.npc.NpcManager;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;
import org.nakii.valmora.module.warp.WarpModule;
import org.nakii.valmora.module.warp.WarpManager;
import org.nakii.valmora.module.warp.WarpCommand;
import org.nakii.valmora.module.quest.QuestModule;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestCommand;
import org.nakii.valmora.module.quest.points.PointsModule;
import org.nakii.valmora.module.npc.NpcCommand;
import org.nakii.valmora.module.notify.NotifyModule;
import org.nakii.valmora.module.alchemy.command.PotionCommand;
import org.nakii.valmora.module.alchemy.command.EffectsCommand;
import org.nakii.valmora.module.reforge.ReforgeModule;
import org.nakii.valmora.api.economy.EconomyService;
import org.nakii.valmora.module.economy.EcoCommand;
import org.nakii.valmora.module.economy.EconomyModule;
import org.nakii.valmora.util.Keys;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public final class Valmora extends JavaPlugin implements ValmoraAPI {

    private static Valmora instance;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    private DataStore dataStore;

    private PlayerManager playerManager;
    private ItemManager itemManager;
    private StatModule statModule;
    private MobManager mobManager;
    private SkillModule skillModule;
    private AbilityManager abilityManager;
    private CombatModule combatModule;
    private ScriptModule scriptModule;
    private TimeModule timeModule;

    private UIManager uiManager;
    private org.nakii.valmora.module.gui.GuiModule guiModule;
    private org.nakii.valmora.module.recipe.RecipeModule recipeModule;
    private AlchemyModule alchemyModule;
    private org.nakii.valmora.module.enchant.EnchantModule enchantModule;

    private ModuleManager moduleManager;
    private EconomyModule economyModule;
    private EconomyService economyService;
    private ZoneModule zoneModule;
    private ResourceModule resourceModule;
    private FishingModule fishingModule;
    private NpcModule npcModule;
    private WarpModule warpModule;
    private QuestModule questModule;
    private PointsModule pointsModule;
    private NotifyModule notifyModule;
    private ReforgeModule reforgeModule;

    @Override
    public void onEnable() {
        instance = this;
        ValmoraAPI.setProvider(this);
        PacketEvents.getAPI().init();
        
        this.moduleManager = new ModuleManager(this);

        saveDefaultConfig();
        saveAllResources();


        // Initialize Keys
        Keys.init(this);

        // 1. Initialize Database first
        this.dataStore = DatabaseFactory.createDataStore(this);
        this.dataStore.init();
        this.economyModule = new EconomyModule(this, dataStore);
        this.economyService = economyModule;

        // 2. Initialize Managers/Modules
        this.playerManager = new PlayerManager(this, dataStore);
        this.statModule = new StatModule(this);
        this.abilityManager = new AbilityManager(this);
        this.itemManager = new ItemManager(this);
        this.mobManager = new MobManager(this);
        this.skillModule = new SkillModule(this);
        this.combatModule = new CombatModule(this);
        this.scriptModule = new ScriptModule(this);
        this.timeModule = new TimeModule(this);
        this.uiManager = new UIManager(this);
        this.guiModule = new GuiModule(this);
        this.recipeModule = new RecipeModule(this);
        this.alchemyModule = new AlchemyModule(this);
        this.enchantModule = new EnchantModule(this);
        this.zoneModule = new ZoneModule(this);
        this.resourceModule = new ResourceModule(this);
        this.fishingModule = new FishingModule(this);
        this.npcModule = new NpcModule(this);
        this.warpModule = new WarpModule(this);
        this.questModule = new QuestModule(this);
        this.pointsModule = new PointsModule(this);
        this.notifyModule = new NotifyModule(this);
        this.reforgeModule = new ReforgeModule(this);

        // 3. Register Modules in Order
        // Foundational Modules (No dependencies)
        moduleManager.registerModule(scriptModule);
        moduleManager.registerModule(timeModule);    // No dependencies; scoreboard and scripts read from it
        moduleManager.registerModule(statModule);
        moduleManager.registerModule(playerManager);
        moduleManager.registerModule(economyModule); // Depends on playerManager for join/quit lifecycle
        
        // Dependent Modules
        moduleManager.registerModule(uiManager);
        moduleManager.registerModule(abilityManager);
        moduleManager.registerModule(itemManager);
        moduleManager.registerModule(mobManager);
        moduleManager.registerModule(skillModule);
        moduleManager.registerModule(combatModule);
        moduleManager.registerModule(guiModule);
        moduleManager.registerModule(recipeModule);
        moduleManager.registerModule(alchemyModule);
        moduleManager.registerModule(enchantModule);
        moduleManager.registerModule(zoneModule);
        moduleManager.registerModule(resourceModule);
        moduleManager.registerModule(fishingModule);
        moduleManager.registerModule(npcModule);
        moduleManager.registerModule(warpModule);
        moduleManager.registerModule(questModule);
        moduleManager.registerModule(pointsModule);
        moduleManager.registerModule(notifyModule);
        moduleManager.registerModule(reforgeModule); // Depends on recipeModule (registers handlers)

        // 4. Enable Modules
        moduleManager.enableModules();

        // 5. Commands
        getCommand("quest").setExecutor(new QuestCommand(this));
        NpcCommand npcCommand = new NpcCommand(this);
        getCommand("npc").setExecutor(npcCommand);
        getCommand("npc").setTabCompleter(npcCommand);
        getCommand("valmora").setExecutor(new ValmoraCommand(this));
        getCommand("profile").setExecutor(new ProfileCommand(playerManager));
        getCommand("stat").setExecutor(new StatCommand(playerManager));
        getCommand("item").setExecutor(new ItemCommand(this));
        getCommand("mob").setExecutor(new MobCommand(this, mobManager));
        getCommand("skill").setExecutor(new SkillCommand(this, playerManager));
        getCommand("gui").setExecutor(new GuiCommand(this));
        getCommand("time").setExecutor(new TimeCommand(timeModule.getTimeManager()));
        getCommand("eco").setExecutor(new EcoCommand(economyModule));
        getCommand("potion").setExecutor(new PotionCommand(this, alchemyModule.getAlchemyManager()));
        getCommand("effects").setExecutor(new EffectsCommand(this));
        getCommand("warp").setExecutor(new WarpCommand(this));
        ZoneCommand zoneCommand = new ZoneCommand(this, zoneModule);
        getCommand("zone").setExecutor(zoneCommand);
        getCommand("zone").setTabCompleter(zoneCommand);
    }

     @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableModules();
        }

        PacketEvents.getAPI().terminate();

        if (playerManager != null && dataStore != null) {
            for (org.nakii.valmora.module.profile.ValmoraPlayer player : playerManager.getAllSessions()) {
                dataStore.savePlayer(player).join(); 
            }
            dataStore.close();
        }
    }

    public static Valmora getInstance() {
        return instance;
    }

    @Override
    public ItemManager getItemManager() {
        return itemManager;
    }

    @Override
    public StatModule getStatModule() {
        return statModule;
    }

    @Override
    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    @Override
    public DamageIndicatorManager getDamageIndicatorManager() {
        return combatModule.getDamageIndicatorManager();
    }

    @Override
    public MobManager getMobManager() {
        return mobManager;
    }

    @Override
    public UIManager getUIManager() {
        return uiManager;
    }

    @Override
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    @Override
    public SkillManager getSkillManager() {
        return skillModule.getSkillManager();
    }

    public SkillModule getSkillModule() {
        return skillModule;
    }

    @Override
    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    @Override
    public ScriptModule getScriptModule() {
        return scriptModule;
    }

    @Override
    public org.nakii.valmora.module.time.TimeManager getTimeManager() {
        return timeModule.getTimeManager();
    }

    public org.nakii.valmora.module.gui.GuiModule getGuiModule() {
        return guiModule;
    }

    public org.nakii.valmora.module.recipe.RecipeModule getRecipeModule() {
        return recipeModule;
    }

    public org.nakii.valmora.module.enchant.EnchantModule getEnchantModule() {
        return enchantModule;
    }

    @Override
    public org.nakii.valmora.module.stat.StatRegistry getStatRegistry() {
        return statModule.getStatRegistry();
    }

    @Override
    public org.nakii.valmora.module.stat.SystemStats getSystemStats() {
        return statModule.getSystemStats();
    }

    @Override
    public org.nakii.valmora.module.alchemy.AlchemyManager getAlchemyManager() {
        return alchemyModule != null ? alchemyModule.getAlchemyManager() : null;
    }

    @Override
    public EconomyService getEconomy() {
        return economyService;
    }

    @Override
    public EconomyModule getEconomyModule() {
        return economyModule;
    }

    public void setEconomyService(EconomyService service) {
        this.economyService = service;
    }

    @Override
    public ZoneManager getZoneManager() {
        return zoneModule != null ? zoneModule.getZoneManager() : null;
    }

    public ZoneModule getZoneModule() {
        return zoneModule;
    }

    public ResourceModule getResourceModule() {
        return resourceModule;
    }

    public FishingModule getFishingModule() {
        return fishingModule;
    }

    @Override
    public NpcManager getNpcManager() {
        return npcModule != null ? npcModule.getNpcManager() : null;
    }

    @Override
    public DialogueManager getDialogueManager() {
        return npcModule != null ? npcModule.getDialogueManager() : null;
    }

    @Override
    public WarpManager getWarpManager() {
        return warpModule != null ? warpModule.getWarpManager() : null;
    }

    @Override
    public QuestManager getQuestManager() {
        return questModule != null ? questModule.getQuestManager() : null;
    }

    public NpcModule getNpcModule() { return npcModule; }
    public WarpModule getWarpModule() { return warpModule; }
    public QuestModule getQuestModule() { return questModule; }
    public ReforgeModule getReforgeModule() { return reforgeModule; }

    @Override
    public org.nakii.valmora.module.quest.points.PointsManager getPointsManager() {
        return pointsModule != null ? pointsModule.getPointsManager() : null;
    }

    @Override
    public org.nakii.valmora.module.notify.NotifyManager getNotifyManager() {
        return notifyModule != null ? notifyModule.getNotifyManager() : null;
    }

    @Override
    public org.nakii.valmora.module.quest.pkg.QuestPackageManager getQuestPackageManager() {
        return questModule != null ? questModule.getPackageManager() : null;
    }

    private void saveAllResources() {
        try {
            File jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jarFile.isFile()) return;

            try (ZipInputStream zip = new ZipInputStream(new FileInputStream(jarFile))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || name.endsWith(".class") || name.equals("plugin.yml") || name.equals("config.yml")) {
                        continue;
                    }

                    if (name.startsWith("items/") || name.startsWith("mobs/") || name.startsWith("guis/") ||
                            name.startsWith("recipes/") || name.startsWith("skills/") || name.startsWith("enchants/") ||
                            name.startsWith("alchemy/") || name.startsWith("stats/") ||
                            name.startsWith("zones/") || name.startsWith("fishing/") ||
                            name.startsWith("npcs/") || name.startsWith("dialogues/") ||
                            name.startsWith("warps/") || name.startsWith("quests/") ||
                            name.startsWith("reforges/")) {
                        // Only save if the file doesn't already exist — don't overwrite server edits
                        if (!new File(getDataFolder(), name).exists()) {
                            saveResource(name, false);
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            getLogger().warning("Failed to auto-save resources: " + e.getMessage());
        }
    }
}
