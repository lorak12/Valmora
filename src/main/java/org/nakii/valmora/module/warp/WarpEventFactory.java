package org.nakii.valmora.module.warp;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

public class WarpEventFactory implements EventFactory {

    @Override public String getName() { return "warp_to"; }
    @Override public int minArgs() { return 1; }
    @Override public int maxArgs() { return 1; }
    @Override public String usage() { return "warp_to <warp>"; }
    @Override public void references(String[] args, ReferenceSink sink) {
        if (args.length > 0 && !args[0].contains("$")) sink.ref(org.nakii.valmora.infrastructure.config.refs.Kinds.WARP, args[0]);
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 1) return ctx -> {};
        String warpId = args[0];
        return ctx -> ctx.getPlayerCaster().ifPresent(player -> {
            WarpManager wm = ValmoraAPI.getInstance().getWarpManager();
            if (wm == null) return;
            wm.getRegistry().get(warpId).ifPresent(warp -> wm.teleport((Player) player, warp));
        });
    }
}
