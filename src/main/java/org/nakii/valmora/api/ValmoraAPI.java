package org.nakii.valmora.api;

import org.nakii.valmora.api.economy.EconomyService;
import org.nakii.valmora.module.ModuleManager;
import org.nakii.valmora.module.item.AbilityManager;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.time.TimeManager;

public interface ValmoraAPI {

    static void setProvider(ValmoraAPI provider) {
        Holder.provider = provider;
    }

    static ValmoraAPI getInstance() {
        return Holder.provider;
    }

    ModuleManager getModuleManager();

    org.nakii.valmora.module.profile.PlayerManager getPlayerManager();

    org.nakii.valmora.module.item.ItemManager getItemManager();

    org.nakii.valmora.module.mob.MobManager getMobManager();

    org.nakii.valmora.module.stat.StatModule getStatModule();

    org.nakii.valmora.module.stat.StatRegistry getStatRegistry();

    org.nakii.valmora.module.stat.StatRoleRegistry getStatRoleRegistry();

    org.nakii.valmora.module.stat.SystemStats getSystemStats();

    org.nakii.valmora.module.ui.UIManager getUIManager();

    org.nakii.valmora.module.skill.SkillManager getSkillManager();

    AbilityManager getAbilityManager();

    org.nakii.valmora.module.combat.DamageIndicatorManager getDamageIndicatorManager();

    org.nakii.valmora.module.combat.CombatModule getCombatModule();

    ScriptModule getScriptModule();

    org.nakii.valmora.module.enchant.EnchantModule getEnchantModule();

    TimeManager getTimeManager();

    EconomyService getEconomy();

    org.nakii.valmora.module.economy.EconomyModule getEconomyModule();

    org.nakii.valmora.module.alchemy.AlchemyManager getAlchemyManager();

    org.nakii.valmora.module.zone.ZoneManager getZoneManager();

    org.nakii.valmora.module.npc.NpcManager getNpcManager();

    org.nakii.valmora.module.npc.dialogue.DialogueManager getDialogueManager();

    org.nakii.valmora.module.warp.WarpManager getWarpManager();

    org.nakii.valmora.module.quest.QuestManager getQuestManager();

    org.nakii.valmora.module.quest.points.PointsManager getPointsManager();

    org.nakii.valmora.module.notify.NotifyManager getNotifyManager();

    org.nakii.valmora.module.quest.pkg.QuestPackageManager getQuestPackageManager();

    org.nakii.valmora.module.progression.ProgressionManager getProgressionManager();

    // --- Module-level accessors (Phase 1 API expansion — see docs/REFACTOR/PROGRESS.md) ---

    org.nakii.valmora.module.zone.ZoneModule getZoneModule();

    /** VANILLA_CONTROL_AUDIT.md §9 — owns PlayerDeathEvent/PlayerRespawnEvent, see docs/modules/design/death.md. */
    org.nakii.valmora.module.death.DeathModule getDeathModule();

    org.nakii.valmora.module.npc.NpcModule getNpcModule();

    org.nakii.valmora.module.warp.WarpModule getWarpModule();

    org.nakii.valmora.module.quest.QuestModule getQuestModule();

    org.nakii.valmora.module.quest.points.PointsModule getPointsModule();

    org.nakii.valmora.module.progression.ProgressionModule getProgressionModule();

    org.nakii.valmora.module.resource.ResourceModule getResourceModule();

    org.nakii.valmora.module.fishing.FishingModule getFishingModule();

    org.nakii.valmora.module.collection.CollectionModule getCollectionModule();

    org.nakii.valmora.module.hud.HudItemModule getHudItemModule();

    org.nakii.valmora.module.calendar.CalendarEventModule getCalendarEventModule();

    org.nakii.valmora.module.pet.PetModule getPetModule();

    org.nakii.valmora.module.alchemy.AlchemyModule getAlchemyModule();

    org.nakii.valmora.module.notify.NotifyModule getNotifyModule();

    org.nakii.valmora.module.gui.GuiModule getGuiModule();

    org.nakii.valmora.module.recipe.RecipeModule getRecipeModule();

    org.nakii.valmora.module.rarity.RarityModule getRarityModule();

    org.nakii.valmora.module.modifier.ModifierModule getModifierModule();

    org.nakii.valmora.module.pack.PackModule getPackModule();

    /**
     * Shared pipeline dispatch bus — register a Java hook to react to a named insertion point
     * (e.g. {@code "combat:pre_damage"}, {@code "combat:post_calculation"},
     * {@code "combat:post_application"}, {@code "combat:on_dmg_dealt"}, {@code "combat:on_death"})
     * without writing script DSL. See {@link org.nakii.valmora.api.pipeline.HookBus}.
     */
    org.nakii.valmora.api.pipeline.HookBus getHookBus();
}

class Holder {
    static ValmoraAPI provider;
}