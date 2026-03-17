package org.nakii.valmora;

import org.bukkit.plugin.java.JavaPlugin;
import org.nakii.valmora.item.ItemCommand;
import org.nakii.valmora.item.ItemManager;
import org.nakii.valmora.mob.MobCommand;
import org.nakii.valmora.mob.MobManager;
import org.nakii.valmora.profile.PlayerConnectionListener;
import org.nakii.valmora.profile.PlayerManager;
import org.nakii.valmora.profile.ProfileCommand;
import org.nakii.valmora.skill.SkillRegistry;
import org.nakii.valmora.skill.XpAccumulator;
import org.nakii.valmora.skill.command.SkillAdminCommand;
import org.nakii.valmora.skill.command.SkillCommand;
import org.nakii.valmora.skill.listener.SkillMechanicListener;
import org.nakii.valmora.skill.listener.SkillTriggerListener;
import org.nakii.valmora.stat.PlayerListener;
import org.nakii.valmora.combat.CombatListener;
import org.nakii.valmora.combat.DamageCalculator;
import org.nakii.valmora.combat.DamageIndicatorManager;
import org.nakii.valmora.stat.StatCommand;
import org.nakii.valmora.stat.StatStorage;


public final class Valmora extends JavaPlugin {

    private static Valmora instance;

    private PlayerManager playerManager;
    private ItemManager itemManager;
    private StatStorage statStorage;
    private DamageIndicatorManager damageIndicatorManager;
    private MobManager mobManager;
    private SkillRegistry skillRegistry;
    private XpAccumulator xpAccumulator;

    @Override
    public void onEnable() {
        instance = this;

        saveResource("items/example.yml", true);
        saveResource("mobs/test_mobs.yml", true);

        DataStore mockDb = new MockDataStore();

        this.playerManager = new PlayerManager(mockDb);
        this.itemManager = new ItemManager(this);
        this.itemManager.initialize();
        this.statStorage = new StatStorage(this);
        this.damageIndicatorManager = new DamageIndicatorManager(this);
        new DamageCalculator(this); // Initialize static plugin field
        this.mobManager = new MobManager(this);
        this.mobManager.initialize();

        // ── Skill system ────────────────────────────────────────────────────
        this.skillRegistry = new SkillRegistry(this);
        this.skillRegistry.initialize();
        this.xpAccumulator = new XpAccumulator(this);

        // ── Listeners ───────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(playerManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillTriggerListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillMechanicListener(), this);

        // ── Commands ────────────────────────────────────────────────────────
        getCommand("profile").setExecutor(new ProfileCommand(playerManager));
        getCommand("stat").setExecutor(new StatCommand(playerManager));
        getCommand("item").setExecutor(new ItemCommand(this));
        getCommand("mob").setExecutor(new MobCommand(this, mobManager));

        SkillCommand skillCommand = new SkillCommand(this);
        getCommand("skill").setExecutor(skillCommand);
        getCommand("skill").setTabCompleter(skillCommand);
        getCommand("skills").setExecutor(skillCommand);
        getCommand("skills").setTabCompleter(skillCommand);

        SkillAdminCommand skillAdminCommand = new SkillAdminCommand(this);
        getCommand("skilladmin").setExecutor(skillAdminCommand);
        getCommand("skilladmin").setTabCompleter(skillAdminCommand);
    }

    @Override
    public void onDisable() {
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

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public XpAccumulator getXpAccumulator() {
        return xpAccumulator;
    }
}
