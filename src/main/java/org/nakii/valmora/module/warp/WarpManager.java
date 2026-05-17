package org.nakii.valmora.module.warp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.Optional;

public class WarpManager {

    private final Valmora plugin;
    private final Registry<WarpDefinition> registry = new SimpleRegistry<>();

    public WarpManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public Registry<WarpDefinition> getRegistry() { return registry; }

    public boolean isUnlocked(Player player, WarpDefinition warp) {
        String condition = warp.getUnlockCondition();
        if (condition == null || condition.equalsIgnoreCase("always")) return true;

        ValmoraPlayer vp = plugin.getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return false;
        ValmoraProfile profile = vp.getActiveProfile();
        if (profile == null) return false;

        if (condition.startsWith("tag:")) return profile.getTags().contains(condition.substring(4));

        if (condition.startsWith("skill:")) {
            String[] parts = condition.substring(6).split(":");
            if (parts.length < 2) return false;
            String skillId = parts[0];
            int required;
            try { required = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return false; }
            var skillDefOpt = plugin.getSkillModule().getSkillRegistry().get(skillId);
            if (skillDefOpt.isEmpty()) return false;
            double xp = profile.getSkillManager().getXp(skillId);
            String curve = skillDefOpt.get().getXpCurve();
            int level = plugin.getSkillManager().getSkillRegistry().getProgressData(curve, xp).currentLevel();
            return level >= required;
        }
        return false;
    }

    public void teleport(Player player, WarpDefinition warp) {
        if (!isUnlocked(player, warp)) {
            player.sendMessage(Formatter.format("<red>This warp is locked! Condition: <gray>" + warp.getUnlockCondition()));
            return;
        }
        World world = Bukkit.getWorld(warp.getWorldName());
        if (world == null) { player.sendMessage(Formatter.format("<red>World not loaded.")); return; }
        Location dest = new Location(world, warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch());
        player.teleportAsync(dest).thenAccept(success -> {
            if (success) player.sendMessage(Formatter.format("<green>Teleported to <white>" + warp.getDisplayName()));
        });
    }

    public Optional<WarpDefinition> getWarpByPad(String worldName, int bx, int by, int bz) {
        for (WarpDefinition warp : registry.values()) {
            if (!warp.getWorldName().equals(worldName)) continue;
            for (int[] pad : warp.getPadLocations()) {
                if (pad[0] == bx && pad[1] == by && pad[2] == bz) return Optional.of(warp);
            }
        }
        return Optional.empty();
    }
}
