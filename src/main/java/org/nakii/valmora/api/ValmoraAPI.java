package org.nakii.valmora.api;

import org.nakii.valmora.module.ModuleManager;
import org.nakii.valmora.module.item.AbilityManager;
import org.nakii.valmora.module.script.ScriptModule;

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

    org.nakii.valmora.module.ui.UIManager getUIManager();

    org.nakii.valmora.module.skill.SkillManager getSkillManager();

    AbilityManager getAbilityManager();

    org.nakii.valmora.module.combat.DamageIndicatorManager getDamageIndicatorManager();

    ScriptModule getScriptModule();

    org.nakii.valmora.module.enchant.EnchantModule getEnchantModule();
}

class Holder {
    static ValmoraAPI provider;
}