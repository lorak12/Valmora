package org.nakii.valmora;

import org.bukkit.plugin.java.JavaPlugin;
import org.nakii.valmora.item.ItemCommand;
import org.nakii.valmora.item.ItemManager;
import org.nakii.valmora.mob.MobCommand;
import org.nakii.valmora.mob.MobManager;
import org.nakii.valmora.profile.PlayerConnectionListener;
import org.nakii.valmora.profile.PlayerManager;
import org.nakii.valmora.profile.ProfileCommand;
import org.nakii.valmora.profile.ValmoraPlayer;
import org.nakii.valmora.stat.PlayerListener;
import org.nakii.valmora.combat.CombatListener;
import org.nakii.valmora.combat.DamageCalculator;
import org.nakii.valmora.combat.DamageIndicatorManager;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.database.DatabaseFactory;
import org.nakii.valmora.stat.StatCommand;
import org.nakii.valmora.stat.StatStorage;


public final class Valmora extends JavaPlugin {

    private static Valmora instance;

    private DataStore dataStore;

    private PlayerManager playerManager;
    private ItemManager itemManager;
    private StatStorage statStorage;
    private DamageIndicatorManager damageIndicatorManager;
    private MobManager mobManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("items/example.yml", true);
        saveResource("mobs/test_mobs.yml", true);

         // Initialize Database
        this.dataStore = DatabaseFactory.createDataStore(this);
        this.dataStore.init();

        this.playerManager = new PlayerManager(dataStore);
        this.itemManager = new ItemManager(this);
        this.itemManager.initialize();
        this.statStorage = new StatStorage(this);
        this.damageIndicatorManager = new DamageIndicatorManager(this);
        new DamageCalculator(this); // Initialize static plugin field
        this.mobManager = new MobManager(this);
        this.mobManager.initialize();

        

        // ── Listeners ───────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(playerManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        

        // ── Commands ────────────────────────────────────────────────────────
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

}
