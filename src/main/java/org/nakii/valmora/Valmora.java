package org.nakii.valmora;

import org.bukkit.plugin.java.JavaPlugin;
import org.nakii.valmora.module.item.ItemCommand;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.item.AbilityManager;
import org.nakii.valmora.module.mob.MobCommand;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.ProfileCommand;
import org.nakii.valmora.module.combat.CombatModule;
import org.nakii.valmora.module.combat.DamageIndicatorManager;
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
import org.nakii.valmora.util.Keys;


public final class Valmora extends JavaPlugin implements ValmoraAPI {

    private static Valmora instance;

    private DataStore dataStore;

    private PlayerManager playerManager;
    private ItemManager itemManager;
    private StatModule statModule;
    private MobManager mobManager;
    private SkillModule skillModule;
    private AbilityManager abilityManager;
    private CombatModule combatModule;
    private ScriptModule scriptModule;

    private UIManager uiManager;

    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        instance = this;
        ValmoraAPI.setProvider(this);
        
        this.moduleManager = new ModuleManager(this);

        saveDefaultConfig();
        saveResource("items/example.yml", true);
        saveResource("mobs/test_mobs.yml", true);
        saveResource("gui/forge.yml", true);

        // Initialize Keys
        Keys.init(this);

        // 1. Initialize Database first
        this.dataStore = DatabaseFactory.createDataStore(this);
        this.dataStore.init();

        // 2. Initialize Managers/Modules
        this.playerManager = new PlayerManager(this, dataStore);
        this.statModule = new StatModule(this);
        this.abilityManager = new AbilityManager(this);
        this.itemManager = new ItemManager(this);
        this.mobManager = new MobManager(this);
        this.skillModule = new SkillModule(this);
        this.combatModule = new CombatModule(this);
        this.scriptModule = new ScriptModule(this);
        this.uiManager = new UIManager(this);

        // 3. Register Modules in Order
        moduleManager.registerModule(playerManager);
        moduleManager.registerModule(statModule);
        moduleManager.registerModule(uiManager);
        moduleManager.registerModule(abilityManager);
        moduleManager.registerModule(itemManager);
        moduleManager.registerModule(mobManager);
        moduleManager.registerModule(skillModule);
        moduleManager.registerModule(combatModule);
        moduleManager.registerModule(scriptModule);

        // 4. Enable Modules
        moduleManager.enableModules();

        // 5. Commands
        getCommand("valmora").setExecutor(new ValmoraCommand(this));
        getCommand("profile").setExecutor(new ProfileCommand(playerManager));
        getCommand("stat").setExecutor(new StatCommand(playerManager));
        getCommand("item").setExecutor(new ItemCommand(this));
        getCommand("mob").setExecutor(new MobCommand(this, mobManager));
        getCommand("skill").setExecutor(new SkillCommand(this, playerManager));
    }

     @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableModules();
        }

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

    @Override
    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    @Override
    public ScriptModule getScriptModule() {
        return scriptModule;
    }
}
