package org.nakii.valmora;

import org.bukkit.plugin.java.JavaPlugin;
import org.nakii.valmora.item.ItemCommand;
import org.nakii.valmora.item.ItemManager;
import org.nakii.valmora.item.ability.AbilityListener;
import org.nakii.valmora.item.ability.AbilityManager;
import org.nakii.valmora.mob.MobCommand;
import org.nakii.valmora.mob.MobManager;
import org.nakii.valmora.profile.PlayerConnectionListener;
import org.nakii.valmora.profile.PlayerManager;
import org.nakii.valmora.profile.ProfileCommand;
import org.nakii.valmora.profile.ValmoraPlayer;
import org.nakii.valmora.skill.SkillListener;
import org.nakii.valmora.skill.SkillManager;
import org.nakii.valmora.stat.PlayerListener;
import org.nakii.valmora.combat.CombatListener;
import org.nakii.valmora.combat.DamageCalculator;
import org.nakii.valmora.combat.DamageIndicatorManager;
import org.nakii.valmora.combat.RegenTask;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.database.DatabaseFactory;
import org.nakii.valmora.stat.StatCommand;
import org.nakii.valmora.stat.StatStorage;
import org.nakii.valmora.ui.UIManager;


public final class Valmora extends JavaPlugin {

    private static Valmora instance;

    private DataStore dataStore;

    private PlayerManager playerManager;
    private ItemManager itemManager;
    private StatStorage statStorage;
    private DamageIndicatorManager damageIndicatorManager;
    private MobManager mobManager;
    private SkillManager skillManager;
    private AbilityManager abilityManager;

    private UIManager uiManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("items/example.yml", true);
        saveResource("mobs/test_mobs.yml", true);

        // 1. Initialize Database first (Profiles depend on this)
        this.dataStore = DatabaseFactory.createDataStore(this);
        this.dataStore.init();

        // 2. Initialize UI (Used by almost everything)
        this.uiManager = new UIManager(this);

        // 3. Initialize Player Manager (Stores the sessions)
        this.playerManager = new PlayerManager(dataStore);

        // 4. Initialize Ability Manager BEFORE Item Manager
        this.abilityManager = new AbilityManager(this);
        this.abilityManager.initialize(); 

        // 5. Initialize Item Manager
        this.itemManager = new ItemManager(this);
        this.itemManager.initialize();

        // 6. Initialize Mob Manager (Depends on items for equipment)
        this.mobManager = new MobManager(this);
        this.mobManager.initialize();

        // 7. Initialize remaining systems
        this.statStorage = new StatStorage(this);
        this.damageIndicatorManager = new DamageIndicatorManager(this);
        new DamageCalculator(this); 
        this.skillManager = new SkillManager();

        // 8. Tasks and Listeners
        new RegenTask(this).start();
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(playerManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillListener(skillManager, this), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);

        // 9. Commands
        getCommand("profile").setExecutor(new ProfileCommand(playerManager));
        getCommand("stat").setExecutor(new StatCommand(playerManager));
        getCommand("item").setExecutor(new ItemCommand(this));
        getCommand("mob").setExecutor(new MobCommand(this, mobManager));
    }

     @Override
    public void onDisable() {
        // Save all currently online players before shutting down
        for (ValmoraPlayer player : playerManager.getAllSessions()) {
            dataStore.savePlayer(player).join(); // .join() blocks the main thread temporarily to ensure it saves before the server dies
        }
        
        if (dataStore != null) {
            dataStore.close();
        }
    }

    public static Valmora getInstance() {
        return instance;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public StatStorage getStatStorage() {
        return statStorage;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public DamageIndicatorManager getDamageIndicatorManager() {
        return damageIndicatorManager;
    }

    public MobManager getMobManager() {
        return mobManager;
    }

    public UIManager getUIManager() {
        return uiManager;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

}
