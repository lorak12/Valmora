package org.nakii.valmora.module.death;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.zone.ZoneDefinition;
import org.nakii.valmora.module.zone.ZoneManager;

/**
 * Resolves per-death keepInventory/keepExperience policy (VANILLA_CONTROL_AUDIT.md §9): a dying
 * zone's {@code ZoneFlags} override if it sets one (non-null), else the server-wide
 * {@code death.keep-inventory-default}/{@code death.keep-experience-default} config default — both
 * ship {@code false}, matching vanilla's own default rather than making a game-design decision on
 * the server operator's behalf. Also resolves the optional zone-declared custom respawn location
 * override (see {@code death.zone-respawn-overrides} in config.yml).
 */
public final class DeathPolicyResolver {

    private DeathPolicyResolver() {}

    public record Policy(boolean keepInventory, boolean keepExperience) {}

    public static Policy resolve(Player player) {
        Valmora plugin = Valmora.getInstance();
        boolean defaultKeepInventory = plugin.getConfig().getBoolean("death.keep-inventory-default", false);
        boolean defaultKeepExperience = plugin.getConfig().getBoolean("death.keep-experience-default", false);

        ZoneDefinition zone = deathZone(player);
        if (zone == null) return new Policy(defaultKeepInventory, defaultKeepExperience);

        Boolean invOverride = zone.getFlags().keepInventoryOnDeath();
        Boolean xpOverride = zone.getFlags().keepExperienceOnDeath();
        return new Policy(
                invOverride != null ? invOverride : defaultKeepInventory,
                xpOverride != null ? xpOverride : defaultKeepExperience
        );
    }

    /** The zone the player is standing in at time of death, if any. */
    public static ZoneDefinition deathZone(Player player) {
        ZoneManager zoneManager = ValmoraAPI.getInstance().getZoneManager();
        if (zoneManager == null) return null;
        return zoneManager.getZoneAt(player.getLocation()).orElse(null);
    }

    /**
     * Optional zone-declared custom respawn override:
     * {@code death.zone-respawn-overrides.<zoneId>: "world,x,y,z[,yaw]"}. Returns null (fall
     * through to vanilla's own bed/anchor/world-spawn resolution) if unset or malformed.
     */
    public static Location resolveRespawnOverride(String zoneId) {
        if (zoneId == null) return null;
        Valmora plugin = Valmora.getInstance();
        String raw = plugin.getConfig().getString("death.zone-respawn-overrides." + zoneId, null);
        if (raw == null || raw.isBlank()) return null;

        String[] parts = raw.split(",");
        if (parts.length < 4) {
            plugin.getLogger().warning("[death] Malformed zone-respawn-overrides entry for zone '" + zoneId + "': " + raw);
            return null;
        }
        try {
            World world = Bukkit.getWorld(parts[0].trim());
            if (world == null) {
                plugin.getLogger().warning("[death] zone-respawn-overrides for zone '" + zoneId
                        + "' references unknown world '" + parts[0].trim() + "'");
                return null;
            }
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0f;
            return new Location(world, x, y, z, yaw, 0f);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[death] Malformed zone-respawn-overrides entry for zone '" + zoneId + "': " + raw);
            return null;
        }
    }
}
