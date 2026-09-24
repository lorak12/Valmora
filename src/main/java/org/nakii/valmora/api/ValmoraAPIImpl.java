package org.nakii.valmora.api;

import org.nakii.valmora.api.economy.EconomyService;
import org.nakii.valmora.module.ModuleManager;
import org.nakii.valmora.module.alchemy.AlchemyManager;
import org.nakii.valmora.module.alchemy.AlchemyModule;
import org.nakii.valmora.module.calendar.CalendarEventModule;
import org.nakii.valmora.module.collection.CollectionModule;
import org.nakii.valmora.module.combat.CombatModule;
import org.nakii.valmora.module.combat.DamageIndicatorManager;
import org.nakii.valmora.module.economy.EconomyModule;
import org.nakii.valmora.module.enchant.EnchantModule;
import org.nakii.valmora.module.fishing.FishingModule;
import org.nakii.valmora.module.gui.GuiModule;
import org.nakii.valmora.module.hud.HudItemModule;
import org.nakii.valmora.module.item.AbilityManager;
import org.nakii.valmora.module.item.ItemManager;
import org.nakii.valmora.module.mob.MobManager;
import org.nakii.valmora.module.notify.NotifyManager;
import org.nakii.valmora.module.notify.NotifyModule;
import org.nakii.valmora.module.npc.NpcManager;
import org.nakii.valmora.module.npc.NpcModule;
import org.nakii.valmora.module.npc.dialogue.DialogueManager;
import org.nakii.valmora.module.pet.PetModule;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.progression.ProgressionManager;
import org.nakii.valmora.module.progression.ProgressionModule;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestModule;
import org.nakii.valmora.module.quest.pkg.QuestPackageManager;
import org.nakii.valmora.module.quest.points.PointsManager;
import org.nakii.valmora.module.quest.points.PointsModule;
import org.nakii.valmora.module.recipe.RecipeModule;
import org.nakii.valmora.module.resource.ResourceModule;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.skill.SkillManager;
import org.nakii.valmora.module.skill.SkillModule;
import org.nakii.valmora.module.stat.StatModule;
import org.nakii.valmora.module.stat.StatRegistry;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.module.time.TimeManager;
import org.nakii.valmora.module.time.TimeModule;
import org.nakii.valmora.module.ui.UIManager;
import org.nakii.valmora.module.warp.WarpManager;
import org.nakii.valmora.module.warp.WarpModule;
import org.nakii.valmora.module.zone.ZoneManager;
import org.nakii.valmora.module.zone.ZoneModule;

/**
 * Standalone {@link ValmoraAPI} implementation, decoupled from the {@code Valmora} JavaPlugin
 * entry point (Phase 1 of the generic-engine refactor — see docs/REFACTOR_BLUEPRINT.md and
 * docs/REFACTOR/PROGRESS.md).
 *
 * <p>Deliberately does NOT extend {@code JavaPlugin} and does NOT hold direct field references to
 * module instances. Instead it holds only the {@link ModuleManager} and resolves every module by
 * id at call time via {@link ModuleManager#getModule(String, Class)}. This means:
 * <ul>
 *   <li>It never goes stale across a {@code /valmora reload} — modules are looked up fresh.</li>
 *   <li>Adding a module never requires touching this class' constructor.</li>
 *   <li>Callers get {@code null} (matching the previous {@code Valmora}-as-API behavior) if a
 *       module hasn't been registered/enabled yet, rather than an NPE on a stale reference.</li>
 * </ul>
 */
public class ValmoraAPIImpl implements ValmoraAPI {

    private final ModuleManager moduleManager;

    /** Optional override so an external economy plugin (e.g. Vault bridge) can supersede the built-in ledger. */
    private volatile EconomyService economyServiceOverride;

    public ValmoraAPIImpl(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private <T extends org.nakii.valmora.api.ReloadableModule> T module(String id, Class<T> type) {
        return moduleManager.getModule(id, type).orElse(null);
    }

    public void setEconomyService(EconomyService override) {
        this.economyServiceOverride = override;
    }

    @Override
    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    @Override
    public PlayerManager getPlayerManager() {
        return module("profiles", PlayerManager.class);
    }

    @Override
    public ItemManager getItemManager() {
        return module("items", ItemManager.class);
    }

    @Override
    public MobManager getMobManager() {
        return module("mobs", MobManager.class);
    }

    @Override
    public StatModule getStatModule() {
        return module("stats", StatModule.class);
    }

    @Override
    public StatRegistry getStatRegistry() {
        StatModule m = getStatModule();
        return m != null ? m.getStatRegistry() : null;
    }

    @Override
    public org.nakii.valmora.module.stat.StatRoleRegistry getStatRoleRegistry() {
        StatModule m = getStatModule();
        return m != null ? m.getStatRoleRegistry() : null;
    }

    @Override
    public SystemStats getSystemStats() {
        StatModule m = getStatModule();
        return m != null ? m.getSystemStats() : null;
    }

    @Override
    public UIManager getUIManager() {
        return module("ui", UIManager.class);
    }

    @Override
    public SkillManager getSkillManager() {
        SkillModule m = module("skills", SkillModule.class);
        return m != null ? m.getSkillManager() : null;
    }

    @Override
    public AbilityManager getAbilityManager() {
        return module("abilities", AbilityManager.class);
    }

    @Override
    public DamageIndicatorManager getDamageIndicatorManager() {
        CombatModule m = getCombatModule();
        return m != null ? m.getDamageIndicatorManager() : null;
    }

    @Override
    public CombatModule getCombatModule() {
        return module("combat", CombatModule.class);
    }

    @Override
    public ScriptModule getScriptModule() {
        return module("script", ScriptModule.class);
    }

    @Override
    public EnchantModule getEnchantModule() {
        return module("enchants", EnchantModule.class);
    }

    @Override
    public TimeManager getTimeManager() {
        TimeModule m = module("time", TimeModule.class);
        return m != null ? m.getTimeManager() : null;
    }

    @Override
    public EconomyService getEconomy() {
        return economyServiceOverride != null ? economyServiceOverride : getEconomyModule();
    }

    @Override
    public EconomyModule getEconomyModule() {
        return module("economy", EconomyModule.class);
    }

    @Override
    public AlchemyManager getAlchemyManager() {
        AlchemyModule m = getAlchemyModule();
        return m != null ? m.getAlchemyManager() : null;
    }

    @Override
    public AlchemyModule getAlchemyModule() {
        return module("alchemy", AlchemyModule.class);
    }

    @Override
    public ZoneManager getZoneManager() {
        ZoneModule m = getZoneModule();
        return m != null ? m.getZoneManager() : null;
    }

    @Override
    public ZoneModule getZoneModule() {
        return module("zone", ZoneModule.class);
    }

    @Override
    public org.nakii.valmora.module.death.DeathModule getDeathModule() {
        return module("death", org.nakii.valmora.module.death.DeathModule.class);
    }

    @Override
    public NpcManager getNpcManager() {
        NpcModule m = getNpcModule();
        return m != null ? m.getNpcManager() : null;
    }

    @Override
    public DialogueManager getDialogueManager() {
        NpcModule m = getNpcModule();
        return m != null ? m.getDialogueManager() : null;
    }

    @Override
    public NpcModule getNpcModule() {
        return module("npc", NpcModule.class);
    }

    @Override
    public WarpManager getWarpManager() {
        WarpModule m = getWarpModule();
        return m != null ? m.getWarpManager() : null;
    }

    @Override
    public WarpModule getWarpModule() {
        return module("warp", WarpModule.class);
    }

    @Override
    public QuestManager getQuestManager() {
        QuestModule m = getQuestModule();
        return m != null ? m.getQuestManager() : null;
    }

    @Override
    public QuestPackageManager getQuestPackageManager() {
        QuestModule m = getQuestModule();
        return m != null ? m.getPackageManager() : null;
    }

    @Override
    public QuestModule getQuestModule() {
        return module("quest", QuestModule.class);
    }

    @Override
    public PointsManager getPointsManager() {
        PointsModule m = getPointsModule();
        return m != null ? m.getPointsManager() : null;
    }

    @Override
    public PointsModule getPointsModule() {
        return module("points", PointsModule.class);
    }

    @Override
    public NotifyManager getNotifyManager() {
        NotifyModule m = getNotifyModule();
        return m != null ? m.getNotifyManager() : null;
    }

    @Override
    public NotifyModule getNotifyModule() {
        return module("notify", NotifyModule.class);
    }

    @Override
    public ProgressionManager getProgressionManager() {
        ProgressionModule m = getProgressionModule();
        return m != null ? m.getProgressionManager() : null;
    }

    @Override
    public ProgressionModule getProgressionModule() {
        return module("progression", ProgressionModule.class);
    }

    @Override
    public org.nakii.valmora.module.pack.PackModule getPackModule() {
        return module("pack", org.nakii.valmora.module.pack.PackModule.class);
    }

    @Override
    public ResourceModule getResourceModule() {
        return module("resource", ResourceModule.class);
    }

    @Override
    public FishingModule getFishingModule() {
        return module("fishing", FishingModule.class);
    }

    @Override
    public org.nakii.valmora.module.blockloot.BlockLootModule getBlockLootModule() {
        return module("block_loot", org.nakii.valmora.module.blockloot.BlockLootModule.class);
    }

    @Override
    public CollectionModule getCollectionModule() {
        return module("collections", CollectionModule.class);
    }

    @Override
    public HudItemModule getHudItemModule() {
        return module("hud", HudItemModule.class);
    }

    @Override
    public CalendarEventModule getCalendarEventModule() {
        return module("calendar", CalendarEventModule.class);
    }

    @Override
    public PetModule getPetModule() {
        return module("pets", PetModule.class);
    }

    @Override
    public GuiModule getGuiModule() {
        return module("gui", GuiModule.class);
    }

    @Override
    public RecipeModule getRecipeModule() {
        return module("recipe", RecipeModule.class);
    }

    @Override
    public org.nakii.valmora.module.rarity.RarityModule getRarityModule() {
        return module("rarity", org.nakii.valmora.module.rarity.RarityModule.class);
    }

    @Override
    public org.nakii.valmora.module.modifier.ModifierModule getModifierModule() {
        return module("modifier", org.nakii.valmora.module.modifier.ModifierModule.class);
    }

    @Override
    public org.nakii.valmora.api.pipeline.HookBus getHookBus() {
        ScriptModule m = getScriptModule();
        return m != null ? m.getHookBus() : null;
    }
}
