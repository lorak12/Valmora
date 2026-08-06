package org.nakii.valmora.module.notify;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.module.notify.io.*;

public class NotifyModule implements ReloadableModule {

    private final Valmora plugin;
    private NotifyManager notifyManager;
    private BossBarIO bossBarIO;
    private NotifyQuitListener quitListener;

    public NotifyModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Enabling Notify Module...");
        this.notifyManager = new NotifyManager();
        this.bossBarIO = new BossBarIO(plugin);

        notifyManager.registerIO(new ChatIO());
        notifyManager.registerIO(new ActionBarIO());
        notifyManager.registerIO(new TitleIO());
        notifyManager.registerIO(new SubTitleIO());
        notifyManager.registerIO(bossBarIO);
        notifyManager.registerIO(new SoundIO());
        notifyManager.registerIO(new AdvancementIO());

        this.quitListener = new NotifyQuitListener(bossBarIO);
        plugin.getServer().getPluginManager().registerEvents(quitListener, plugin);

        plugin.getScriptModule().registerEvent(new NotifyEvent());
        plugin.getScriptModule().registerEvent(new NotifyAllEvent());
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Notify Module...");
        if (quitListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(quitListener);
            quitListener = null;
        }
        this.bossBarIO = null;
        this.notifyManager = null;
    }

    @Override public String getId() { return "notify"; }
    @Override public String getName() { return "Notification System"; }

    public NotifyManager getNotifyManager() { return notifyManager; }
}
