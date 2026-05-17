package org.nakii.valmora.module.quest.points;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

public class PointEvent implements EventFactory {

    @Override public String getName() { return "point"; }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 3) return ctx -> {};
        String category = args[0];
        String action = args[1].toLowerCase();
        int amount;
        try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException e) { return ctx -> {}; }
        final int finalAmount = amount;
        return ctx -> ctx.getPlayerCaster().ifPresent(entity -> {
            if (!(entity instanceof Player player)) return;
            PointsManager pm = ValmoraAPI.getInstance().getPointsManager();
            if (pm == null) return;
            switch (action) {
                case "add" -> pm.addPoints(player.getUniqueId(), category, finalAmount);
                case "set" -> pm.setPoints(player.getUniqueId(), category, finalAmount);
                case "take" -> pm.takePoints(player.getUniqueId(), category, finalAmount);
            }
        });
    }
}
