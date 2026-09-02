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
import org.nakii.valmora.module.collection.CollectionModule;
import org.nakii.valmora.module.collection.CollectionCommand;
import org.nakii.valmora.module.hud.HudItemModule;
import org.nakii.valmora.module.calendar.CalendarEventModule;
import org.nakii.valmora.module.pet.PetModule;
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
    private boolean packetEventsLoaded = false;

    @Override
    public void onLoad() {
        // Guarded (added 2026-08-07, was unguarded) — PacketEvents.setAPI/.load() previously threw
        // straight out of onLoad() on any failure (a version mismatch, a corrupted install, ...),
        // which Bukkit surfaces as an unhandled-exception stack trace rather than a clean disable.
        // Only the NPC dialogue-interception feature (ConversationPacketManager) actually needs
        // PacketEvents — everything else in the plugin works without it — so a failure here now
        // degrades to "dialogue interception unavailable" instead of a hard crash.
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
            PacketEvents.getAPI().load();
            packetEventsLoaded = true;
        } catch (Throwable t) {
            getLogger().severe("Failed to load PacketEvents — NPC dialogue interception will be unavailable: " + t.getMessage());
        }
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
    private org.nakii.valmora.module.worldrules.WorldRulesModule worldRulesModule;

    private UIManager uiManager;
    private org.nakii.valmora.module.gui.GuiModule guiModule;
    private org.nakii.valmora.module.recipe.RecipeModule recipeModule;
    private AlchemyModule alchemyModule;
    private org.nakii.valmora.module.enchant.EnchantModule enchantModule;

    private ModuleManager moduleManager;
    private org.nakii.valmora.api.ValmoraAPIImpl apiImpl;
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
    private CollectionModule collectionModule;
    private HudItemModule hudItemModule;
    private CalendarEventModule calendarEventModule;
    private PetModule petModule;
    private org.nakii.valmora.module.progression.ProgressionModule progressionModule;
    private org.nakii.valmora.module.rarity.RarityModule rarityModule;
    private org.nakii.valmora.module.modifier.ModifierModule modifierModule;
    private org.nakii.valmora.module.machine.MachineModule machineModule;
    private org.nakii.valmora.module.pack.PackModule packModule;
    private org.nakii.valmora.module.pack.PackFileIndex packFileIndex;

    @Override
    public void onEnable() {
        instance = this;
        ValmoraAPI.setProvider(this);
        if (packetEventsLoaded) {
            try {
                PacketEvents.getAPI().init();
            } catch (Throwable t) {
                packetEventsLoaded = false;
                getLogger().severe("Failed to initialize PacketEvents — NPC dialogue interception will be unavailable: " + t.getMessage());
            }
        }

        this.moduleManager = new ModuleManager(this);
        // Decoupled API implementation (Phase 1 — see docs/REFACTOR/PROGRESS.md). Resolves modules
        // by id through moduleManager rather than holding direct field references, so it never goes
        // stale across /valmora reload. Installed as the provider immediately: any code that runs
        // during module construction/enable and calls ValmoraAPI.getInstance() gets the decoupled
        // impl, not a `this` cast.
        this.apiImpl = new org.nakii.valmora.api.ValmoraAPIImpl(moduleManager);
        ValmoraAPI.setProvider(apiImpl);

        saveDefaultConfig();
        saveAllResources();


        // Initialize Keys
        Keys.init(this);

        // 1. Initialize Database first
        this.dataStore = DatabaseFactory.createDataStore(this);
        try {
            this.dataStore.init();
        } catch (RuntimeException e) {
            getLogger().severe("Database initialization failed — disabling Valmora to avoid data loss.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.economyModule = new EconomyModule(this, dataStore);
        this.economyService = economyModule;

        // Prime the content pack manager's YamlLoader namespacing hook now, before any content
        // loader below runs — see PackModule's class doc for why this can't wait for PackModule's
        // own onEnable() (registered/enabled last, but every module's onEnable() below needs the
        // hook active while it loads its own content). Undone once, at actual plugin shutdown, in
        // onDisable() — never torn down by /valmora reload, since it isn't tied to any module.
        primePackNamespacing();

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
        this.worldRulesModule = new org.nakii.valmora.module.worldrules.WorldRulesModule(this);
        this.rarityModule = new org.nakii.valmora.module.rarity.RarityModule(this);
        this.uiManager = new UIManager(this);
        this.guiModule = new GuiModule(this, dataStore);
        this.recipeModule = new RecipeModule(this);
        this.machineModule = new org.nakii.valmora.module.machine.MachineModule(this);
        this.modifierModule = new org.nakii.valmora.module.modifier.ModifierModule(this);
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
        this.collectionModule = new CollectionModule(this);
        this.hudItemModule = new HudItemModule(this);
        this.calendarEventModule = new CalendarEventModule(this);
        this.petModule = new PetModule(this);
        this.progressionModule = new org.nakii.valmora.module.progression.ProgressionModule(this);
        this.packModule = new org.nakii.valmora.module.pack.PackModule(this, dataStore, packFileIndex);

        // 3. Register Modules in Order
        // Foundational Modules (No dependencies)
        moduleManager.registerModule(scriptModule);
        moduleManager.registerModule(worldRulesModule); // No dependencies; purely config-driven GameRule application (VANILLA_CONTROL_AUDIT.md §8)
        moduleManager.registerModule(timeModule);    // No dependencies; scoreboard and scripts read from it
        moduleManager.registerModule(rarityModule);  // No dependencies; item stats/lore and the modifier engine read rarity metadata
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
        moduleManager.registerModule(machineModule); // Depends on guiModule (validates GUI slot counts) + recipeModule; must run before modifier/alchemy/enchant register their own machine handlers
        moduleManager.registerModule(modifierModule); // Depends on recipeModule (registers the unified anvil's modifier-recipe step), rarityModule, itemManager, abilityManager
        moduleManager.registerModule(alchemyModule);
        moduleManager.registerModule(enchantModule);
        moduleManager.registerModule(zoneModule);
        moduleManager.registerModule(resourceModule);
        moduleManager.registerModule(fishingModule);
        moduleManager.registerModule(npcModule);
        moduleManager.registerModule(warpModule);
        moduleManager.registerModule(pointsModule);
        // notifyModule must register before questModule: quest-package notification categories
        // are registered during QuestModule.onEnable() and need a non-null NotifyManager.
        moduleManager.registerModule(notifyModule);
        moduleManager.registerModule(questModule);
        moduleManager.registerModule(collectionModule);
        moduleManager.registerModule(hudItemModule);       // Depends on scriptModule for click DSL
        moduleManager.registerModule(calendarEventModule); // Depends on scriptModule + timeModule
        moduleManager.registerModule(petModule);          // Depends on scriptModule + statModule
        moduleManager.registerModule(progressionModule);  // Depends on scriptModule + pointsModule (generic tree/skill-point engine)
        // Registered last, deliberately: the content pack manager only orchestrates other modules'
        // existing reload machinery (ModuleManager.reloadModules) and must never be a dependency of
        // anything else (docs/modules/design/pack.md).
        moduleManager.registerModule(packModule);

        // 4. Enable Modules
        moduleManager.enableModules();

        // 5. Commands
        QuestCommand questCommand = new QuestCommand(this);
        getCommand("quest").setExecutor(questCommand);
        getCommand("quest").setTabCompleter(questCommand);
        NpcCommand npcCommand = new NpcCommand(this);
        getCommand("npc").setExecutor(npcCommand);
        getCommand("npc").setTabCompleter(npcCommand);
        getCommand("valmora").setExecutor(new ValmoraCommand(this));
        ProfileCommand profileCommand = new ProfileCommand(playerManager);
        getCommand("profile").setExecutor(profileCommand);
        getCommand("profile").setTabCompleter(profileCommand);
        getCommand("stat").setExecutor(new StatCommand(playerManager));
        getCommand("stats").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player p)) {
                sender.sendMessage("This command is for players only.");
                return true;
            }
            guiModule.openGui(p, "stats");
            return true;
        });
        getCommand("item").setExecutor(new ItemCommand(this));
        getCommand("mob").setExecutor(new MobCommand(this, mobManager));
        SkillCommand skillCommand = new SkillCommand(this, playerManager);
        getCommand("skill").setExecutor(skillCommand);
        getCommand("skill").setTabCompleter(skillCommand);
        org.nakii.valmora.module.pet.PetCommand petCommand = new org.nakii.valmora.module.pet.PetCommand(this);
        getCommand("pet").setExecutor(petCommand);
        getCommand("pet").setTabCompleter(petCommand);
        org.nakii.valmora.module.progression.ProgressionCommand progressionCommand = new org.nakii.valmora.module.progression.ProgressionCommand(this);
        getCommand("progression").setExecutor(progressionCommand);
        getCommand("progression").setTabCompleter(progressionCommand);
        org.nakii.valmora.module.recipe.RecipeCommand recipeCommand = new org.nakii.valmora.module.recipe.RecipeCommand(this);
        getCommand("recipe").setExecutor(recipeCommand);
        getCommand("recipe").setTabCompleter(recipeCommand);
        getCommand("gui").setExecutor(new GuiCommand(this));
        getCommand("time").setExecutor(new TimeCommand(timeModule.getTimeManager()));
        org.nakii.valmora.module.calendar.CalendarCommand calendarCommand = new org.nakii.valmora.module.calendar.CalendarCommand(this);
        getCommand("calendar").setExecutor(calendarCommand);
        getCommand("calendar").setTabCompleter(calendarCommand);
        org.nakii.valmora.module.modifier.ModifierCommand modifierCommand = new org.nakii.valmora.module.modifier.ModifierCommand(this);
        getCommand("modifier").setExecutor(modifierCommand);
        getCommand("modifier").setTabCompleter(modifierCommand);
        getCommand("ui").setExecutor(new org.nakii.valmora.module.ui.UICommand(this));
        EcoCommand ecoCommand = new EcoCommand(economyModule);
        getCommand("eco").setExecutor(ecoCommand);
        getCommand("eco").setTabCompleter(ecoCommand);
        PotionCommand potionCommand = new PotionCommand(this, alchemyModule.getAlchemyManager());
        getCommand("potion").setExecutor(potionCommand);
        getCommand("potion").setTabCompleter(potionCommand);
        getCommand("effects").setExecutor(new EffectsCommand(this));
        WarpCommand warpCommand = new WarpCommand(this);
        getCommand("warp").setExecutor(warpCommand);
        getCommand("warp").setTabCompleter(warpCommand);
        ZoneCommand zoneCommand = new ZoneCommand(this, zoneModule);
        getCommand("zone").setExecutor(zoneCommand);
        getCommand("zone").setTabCompleter(zoneCommand);
        CollectionCommand collectionCommand = new CollectionCommand(this);
        getCommand("collections").setExecutor(collectionCommand);
        getCommand("collections").setTabCompleter(collectionCommand);
        TestCommand testCommand = new TestCommand(this);
        getCommand("test").setExecutor(testCommand);
        getCommand("test").setTabCompleter(testCommand);
    }

     @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableModules();
        }
        // Plugin-lifetime hook, not module-lifetime — see primePackNamespacing()/PackModule's class doc.
        org.nakii.valmora.module.pack.PackNamespacer.uninstall();

        if (packetEventsLoaded) {
            try {
                PacketEvents.getAPI().terminate();
            } catch (Throwable t) {
                getLogger().warning("Failed to terminate PacketEvents cleanly: " + t.getMessage());
            }
        }

        if (playerManager != null && dataStore != null) {
            // Concurrent (fixed 2026-08-07, was a synchronous per-player .join() loop) — same fix
            // as PlayerManager.onDisable()'s own save loop, which normally already saved and
            // cleared every session by the time moduleManager.disableModules() (above) finishes;
            // this is the defensive fallback for any session that somehow wasn't covered by that.
            java.util.List<java.util.concurrent.CompletableFuture<Void>> saves = new java.util.ArrayList<>();
            for (org.nakii.valmora.module.profile.ValmoraPlayer player : playerManager.getAllSessions()) {
                saves.add(dataStore.savePlayer(player));
            }
            java.util.concurrent.CompletableFuture.allOf(saves.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            dataStore.close();
        }
    }

    public static Valmora getInstance() {
        return instance;
    }

    /** Whether PacketEvents loaded and initialized successfully — gates NPC dialogue interception (added 2026-08-07). */
    public boolean isPacketEventsLoaded() {
        return packetEventsLoaded;
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
    public CombatModule getCombatModule() {
        return combatModule;
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

    @Override
    public org.nakii.valmora.module.gui.GuiModule getGuiModule() {
        return guiModule;
    }

    @Override
    public org.nakii.valmora.module.recipe.RecipeModule getRecipeModule() {
        return recipeModule;
    }

    @Override
    public org.nakii.valmora.module.enchant.EnchantModule getEnchantModule() {
        return enchantModule;
    }

    @Override
    public org.nakii.valmora.module.stat.StatRegistry getStatRegistry() {
        return statModule.getStatRegistry();
    }

    @Override
    public org.nakii.valmora.module.stat.StatRoleRegistry getStatRoleRegistry() {
        return statModule.getStatRoleRegistry();
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
        if (apiImpl != null) {
            apiImpl.setEconomyService(service);
        }
    }

    /** The decoupled {@link ValmoraAPI} implementation installed as the API provider (Phase 1). */
    public org.nakii.valmora.api.ValmoraAPIImpl getApi() {
        return apiImpl;
    }

    @Override
    public ZoneManager getZoneManager() {
        return zoneModule != null ? zoneModule.getZoneManager() : null;
    }

    @Override
    public ZoneModule getZoneModule() {
        return zoneModule;
    }

    @Override
    public ResourceModule getResourceModule() {
        return resourceModule;
    }

    @Override
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

    @Override
    public NpcModule getNpcModule() { return npcModule; }
    @Override
    public WarpModule getWarpModule() { return warpModule; }
    @Override
    public QuestModule getQuestModule() { return questModule; }
    @Override
    public CollectionModule getCollectionModule() { return collectionModule; }
    @Override
    public HudItemModule getHudItemModule() { return hudItemModule; }
    @Override
    public CalendarEventModule getCalendarEventModule() { return calendarEventModule; }
    @Override
    public org.nakii.valmora.module.rarity.RarityModule getRarityModule() { return rarityModule; }
    @Override
    public org.nakii.valmora.module.modifier.ModifierModule getModifierModule() { return modifierModule; }

    public org.nakii.valmora.module.machine.MachineModule getMachineModule() { return machineModule; }
    @Override
    public PetModule getPetModule() { return petModule; }
    @Override
    public org.nakii.valmora.module.alchemy.AlchemyModule getAlchemyModule() { return alchemyModule; }
    @Override
    public NotifyModule getNotifyModule() { return notifyModule; }
    @Override
    public PointsModule getPointsModule() { return pointsModule; }

    @Override
    public org.nakii.valmora.module.progression.ProgressionManager getProgressionManager() {
        return progressionModule != null ? progressionModule.getProgressionManager() : null;
    }

    @Override
    public org.nakii.valmora.module.progression.ProgressionModule getProgressionModule() {
        return progressionModule;
    }

    @Override
    public org.nakii.valmora.module.quest.points.PointsManager getPointsManager() {
        return pointsModule != null ? pointsModule.getPointsManager() : null;
    }

    @Override
    public org.nakii.valmora.api.pipeline.HookBus getHookBus() {
        return scriptModule != null ? scriptModule.getHookBus() : null;
    }

    @Override
    public org.nakii.valmora.module.pack.PackModule getPackModule() {
        return packModule;
    }

    @Override
    public org.nakii.valmora.module.notify.NotifyManager getNotifyManager() {
        return notifyModule != null ? notifyModule.getNotifyManager() : null;
    }

    @Override
    public org.nakii.valmora.module.quest.pkg.QuestPackageManager getQuestPackageManager() {
        return questModule != null ? questModule.getPackageManager() : null;
    }

    /**
     * Installs the content pack manager's {@code YamlLoader} namespacing hook and rebuilds
     * {@link org.nakii.valmora.module.pack.PackFileIndex} from the pack ledger — see
     * {@code PackModule}'s class doc for why this runs here (before {@code moduleManager.enableModules()})
     * rather than in {@code PackModule.onEnable()} itself. The DB read here blocks the calling
     * thread, same as {@code dataStore.init()} immediately above it — both are one-time,
     * plugin-startup costs.
     */
    private void primePackNamespacing() {
        this.packFileIndex = new org.nakii.valmora.module.pack.PackFileIndex();
        org.nakii.valmora.module.pack.PackNamespacer.install(packFileIndex);
        try {
            for (var record : dataStore.loadPackRecords().join()) {
                packFileIndex.reindexFromFileManifest(record.packId(), record.fileManifest());
            }
        } catch (Exception e) {
            getLogger().warning("Failed to prime content pack namespacing from the pack ledger: " + e.getMessage());
        }
    }

    private void saveAllResources() {
        // Fixed 2026-08-07: previously ran this "copy if missing" pass on every startup, which
        // meant deleting a shipped default (a zone, a mob, a GUI, ...) never actually stuck — it
        // came right back on the next restart, since "the file is missing" looked identical
        // whether it was never seeded or an admin deliberately removed it. Now it only runs once
        // per install (marked by this file); an intentional deletion after that stays deleted.
        // The tradeoff: a plugin update that ships a brand-new default file under one of the
        // seeded folders won't auto-appear on existing installs either — same as most plugins'
        // one-time config-seeding behavior.
        File seededMarker = new File(getDataFolder(), ".resources_seeded");
        if (seededMarker.exists()) return;

        try {
            File codeSource = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

            if (codeSource.isFile()) {
                try (ZipInputStream zip = new ZipInputStream(new FileInputStream(codeSource))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (!entry.isDirectory()) seedResourceIfSeedable(entry.getName());
                    }
                }
            } else if (codeSource.isDirectory()) {
                // Fixed 2026-08-07: dev/exploded-classpath runs (e.g. `./gradlew runServer`, or
                // any IDE launch where the plugin's classes/resources sit as loose files rather
                // than a packaged jar) previously hit `!jarFile.isFile() -> return` and silently
                // seeded nothing at all. A directory code source is a real filesystem directory
                // in this case, so it can be walked directly instead of zip-scanned.
                try (var stream = java.nio.file.Files.walk(codeSource.toPath())) {
                    for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) stream.filter(java.nio.file.Files::isRegularFile)::iterator) {
                        String name = codeSource.toPath().relativize(path).toString().replace(File.separatorChar, '/');
                        seedResourceIfSeedable(name);
                    }
                }
            } else {
                return;
            }

            getDataFolder().mkdirs();
            seededMarker.createNewFile();
        } catch (IOException | URISyntaxException e) {
            getLogger().warning("Failed to auto-save resources: " + e.getMessage());
        }
    }

    /** Copies one jar/classpath resource entry into the data folder if it's one of the seedable default-content files and doesn't already exist there. */
    private void seedResourceIfSeedable(String name) {
        if (name.endsWith(".class") || name.equals("plugin.yml") || name.equals("config.yml")) {
            return;
        }

        if (name.equals("mob_categories.yml") || name.equals("entity_categories.yml") || name.equals("item_types.yml")
                || name.equals("combat_pipeline.yml") || name.equals("resource_pipeline.yml")
                || name.equals("fishing_pipeline.yml") || name.equals("item_pipeline.yml")
                || name.equals("mob_pipeline.yml") || name.equals("rarities.yml")) {
            if (!new File(getDataFolder(), name).exists()) {
                saveResource(name, false);
            }
            return;
        }

        if (name.startsWith("items/") || name.startsWith("mobs/") || name.startsWith("guis/") ||
                name.startsWith("recipes/") || name.startsWith("skills/") || name.startsWith("enchants/") ||
                name.startsWith("enchant/") ||
                name.startsWith("alchemy/") || name.startsWith("stats/") || name.startsWith("damage_types/") ||
                name.startsWith("zones/") || name.startsWith("fishing/") ||
                name.startsWith("npcs/") || name.startsWith("dialogues/") ||
                name.startsWith("warps/") || name.startsWith("quests/") ||
                name.startsWith("collections/") || name.startsWith("hud-items/") ||
                name.startsWith("calendar/") ||
                name.startsWith("modifiers/") ||
                name.startsWith("pets/") ||
                name.startsWith("set_bonuses/") || name.startsWith("progression/") ||
                name.startsWith("quest_boards/")) {
            // Only save if the file doesn't already exist — don't overwrite server edits
            if (!new File(getDataFolder(), name).exists()) {
                saveResource(name, false);
            }
        }
    }
}
