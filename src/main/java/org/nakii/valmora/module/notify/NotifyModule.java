package org.nakii.valmora.module.notify;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.notify.io.*;

public class NotifyModule implements ReloadableModule {

    private final Valmora plugin;
    private NotifyManager notifyManager;

    public NotifyModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Notify Module...");
        this.notifyManager = new NotifyManager();

        notifyManager.registerIO(new ChatIO());
        notifyManager.registerIO(new ActionBarIO());
        notifyManager.registerIO(new TitleIO());
        notifyManager.registerIO(new SubTitleIO());
        notifyManager.registerIO(new BossBarIO(plugin));
        notifyManager.registerIO(new SoundIO());
        notifyManager.registerIO(new AdvancementIO());

        plugin.getScriptModule().registerEvent(new NotifyEvent());
        plugin.getScriptModule().registerEvent(new NotifyAllEvent());
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Notify Module...");
        this.notifyManager = null;
    }

    @Override public String getId() { return "notify"; }
    @Override public String getName() { return "Notification System"; }

    public NotifyManager getNotifyManager() { return notifyManager; }
}
