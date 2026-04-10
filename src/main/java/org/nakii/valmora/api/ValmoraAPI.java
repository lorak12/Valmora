package org.nakii.valmora.api;

import org.nakii.valmora.module.ModuleManager;
import org.nakii.valmora.module.item.AbilityManager;
import org.nakii.valmora.module.script.ScriptModule;

/**
 * The public interface for Valmora plugin.
 * This is the primary entry point for other plugins and modules to interact with the engine.
 */
public interface ValmoraAPI {

    /**
     * Set the current API provider.
     * @param provider new API provider
     */
    static void setProvider(ValmoraAPI provider) {
        Holder.provider = provider;
    }

    /**
     * Get the current API provider.
     * @return current API provider or null if none is set
     */
    static ValmoraAPI getInstance() {
        return Holder.provider;
    }

    /**
     * Returns the module manager.
     * @return ModuleManager instance
     */
    ModuleManager getModuleManager();

    /**
     * Returns the profile manager (player sessions).
     */
    org.nakii.valmora.module.profile.PlayerManager getPlayerManager();

    /**
     * Returns the item module.
     */
    org.nakii.valmora.module.item.ItemManager getItemManager();

    /**
     * Returns the mob module.
     */
    org.nakii.valmora.module.mob.MobManager getMobManager();

    /**
     * Returns the stat module.
     */
    org.nakii.valmora.module.stat.StatModule getStatModule();

    /**
     * Returns the UI manager.
     */
    org.nakii.valmora.module.ui.UIManager getUIManager();

    /**
     * Returns the skill manager.
     */
    org.nakii.valmora.module.skill.SkillManager getSkillManager();

    /**
     * Returns the ability manager.
     */
    AbilityManager getAbilityManager();

    /**
     * Returns the damage indicator manager.
     */
    org.nakii.valmora.module.combat.DamageIndicatorManager getDamageIndicatorManager();
    /**
     * Returns the script engine module.
     */
    ScriptModule getScriptModule();
}


class Holder {
    static ValmoraAPI provider;
}
