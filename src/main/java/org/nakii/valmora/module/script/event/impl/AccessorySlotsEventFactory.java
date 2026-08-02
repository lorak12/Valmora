package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.accessory.AccessoryModule;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

/**
 * Grants, revokes, or sets a player's unlocked accessory bag slots. Values are always
 * clamped to the server-wide {@code accessories.max-slots-cap} by AccessoryModule.
 *
 * DSL:
 *   accessory_slots add <amount>     — grants more unlocked slots
 *   accessory_slots remove <amount>  — revokes unlocked slots
 *   accessory_slots set <amount>     — sets the unlocked slot count outright
 *
 * <amount> supports $variable$ expressions resolved at execution time.
 */
public class AccessorySlotsEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "accessory_slots";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return ctx -> {};

        String action = args[0].toLowerCase();
        String rawValue = args[1];

        return ctx -> ctx.getPlayerCaster().ifPresent(player -> {
            ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
            if (vp == null) return;
            ValmoraProfile profile = vp.getActiveProfile();
            if (profile == null) return;

            AccessoryModule module = ValmoraAPI.getInstance().getAccessoryModule();
            if (module == null) return;

            int amount = (int) Math.round(resolveDouble(rawValue, ctx, player));
            switch (action) {
                case "add" -> module.addUnlockedSlots(profile, amount);
                case "remove" -> module.addUnlockedSlots(profile, -amount);
                case "set" -> module.setUnlockedSlots(profile, amount);
            }
        });
    }

    private double resolveDouble(String raw, ExecutionContext ctx, Player player) {
        String value = raw;
        if (raw.startsWith("$") && raw.endsWith("$")) {
            Object resolved = ctx.getVariableResolver().resolve(raw, ctx);
            value = resolved != null ? resolved.toString() : "0";
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
