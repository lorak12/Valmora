package org.nakii.valmora.module.skill;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;

public class SkillModule implements ReloadableModule {

    private final Valmora plugin;
    private final SkillManager skillManager;
    private final SkillListener skillListener;
    private final SkillRegistry skillRegistry;
    private final SkillLoader skillLoader;

    public SkillModule(Valmora plugin) {
        this.plugin = plugin;
        this.skillRegistry = new SkillRegistry();
        this.skillLoader = new SkillLoader(plugin, skillRegistry);
        this.skillManager = new SkillManager();
        this.skillListener = new SkillListener(skillManager, plugin);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Skill Module...");
        this.skillLoader.loadSkills();
        plugin.getServer().getPluginManager().registerEvents(skillListener, plugin);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Skill Module...");
        org.bukkit.event.HandlerList.unregisterAll(skillListener);
    }

    @Override
    public String getId() {
        return "skills";
    }

    @Override
    public String getName() {
        return "Skill System";
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }
}
