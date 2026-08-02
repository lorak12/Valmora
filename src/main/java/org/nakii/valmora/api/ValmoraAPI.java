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

    org.nakii.valmora.module.stat.SystemStats getSystemStats();

    org.nakii.valmora.module.ui.UIManager getUIManager();

    org.nakii.valmora.module.skill.SkillManager getSkillManager();

    AbilityManager getAbilityManager();

    org.nakii.valmora.module.combat.DamageIndicatorManager getDamageIndicatorManager();

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
}

class Holder {
    static ValmoraAPI provider;
}