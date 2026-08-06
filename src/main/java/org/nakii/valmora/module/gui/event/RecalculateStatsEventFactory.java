package org.nakii.valmora.module.gui.event;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Recalculates the caster's effective stats, e.g. wired into a storage GUI's {@code on-close}
 * block so accessory-bag-style contents apply their stat bonuses immediately. Replaces the old
 * AccessoryModule's automatic recalc-on-close — now an explicit, GUI-author-wired action.
 */
public class RecalculateStatsEventFactory implements EventFactory {

    private final Valmora plugin;

    public RecalculateStatsEventFactory(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "recalculate_stats"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        return context -> context.getPlayerCaster().ifPresent(player -> {
            var vp = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (vp == null) return;
            var profile = vp.getActiveProfile();
            if (profile == null) return;
            profile.getStatManager().recalculateStats(player);
        });
    }
}
