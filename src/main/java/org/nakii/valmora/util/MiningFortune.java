package org.nakii.valmora.util;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraPlayer;

/**
 * Shared Mining Fortune math. Extracted from {@code module.resource.ResourceManager}'s private
 * copy (VANILLA_CONTROL_AUDIT.md §1/§4 block-loot-formatting work) so the global
 * {@code module.blockloot} override system can scale drop amounts identically to zone
 * resource-blocks without a second copy of the same formula.
 */
public final class MiningFortune {

    private MiningFortune() {}

    /** The player's current Mining Fortune stat value, or {@code 0.0} if they have no active profile/session. */
    public static double getPlayerMiningFortune(Player player) {
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session == null) return 0.0;
        var profile = session.getActiveProfile();
        if (profile == null) return 0.0;
        return profile.getStatManager().getStat(ValmoraAPI.getInstance().getSystemStats().getMiningFortune());
    }

    /** Scales a base drop amount by Mining Fortune (percentage bonus), never scaling below the base amount. */
    public static int applyFortune(int baseAmount, double miningFortune) {
        if (miningFortune <= 0) return baseAmount;
        double multiplier = 1.0 + miningFortune / 100.0;
        return (int) Math.max(baseAmount, Math.round(baseAmount * multiplier));
    }
}
