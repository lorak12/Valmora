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
import org.nakii.valmora.util.DebugManager;
import org.nakii.valmora.util.Formatter;

import java.util.Optional;

public class WarpManager {

    private final Valmora plugin;
    private final Registry<WarpDefinition> registry = new SimpleRegistry<>(org.nakii.valmora.infrastructure.versioning.IdAliases.WARPS);

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
        teleport(player, warp, false);
    }

    private void teleport(Player player, WarpDefinition warp, boolean warmupDone) {
        if (!isUnlocked(player, warp)) {
            player.sendMessage(Formatter.format("<red>This warp is locked! Condition: <gray>" + warp.getUnlockCondition()));
            return;
        }

        // Permission, cooldown, and cost gates (added 2026-08-07 — /warp previously had none at all).
        if (warp.getPermission() != null && !warp.getPermission().isEmpty() && !player.hasPermission(warp.getPermission())) {
            player.sendMessage(Formatter.format("<red>You don't have permission to use this warp."));
            return;
        }

        ValmoraPlayer vp = plugin.getPlayerManager().getSession(player.getUniqueId());
        ValmoraProfile profile = vp != null ? vp.getActiveProfile() : null;
        if (profile == null) { player.sendMessage(Formatter.format("<red>Your profile isn't loaded yet.")); return; }

        String cooldownKey = "warp:" + warp.getId();
        if (warp.getCooldownSeconds() > 0 && profile.getCooldownManager().isOnCooldown(cooldownKey)) {
            long remaining = (profile.getCooldownManager().getRemainingCooldown(cooldownKey) + 999) / 1000;
            player.sendMessage(Formatter.format("<red>This warp is on cooldown for <white>" + remaining + "s<red>."));
            return;
        }

        if (warp.getCost() > 0) {
            var economy = plugin.getEconomy();
            if (economy == null || !economy.hasCoins(player, warp.getCost())) {
                player.sendMessage(Formatter.format("<red>You need <gold>" + (long) warp.getCost() + " coins<red> to use this warp."));
                return;
            }
        }

        World world = Bukkit.getWorld(warp.getWorldName());
        if (world == null) { player.sendMessage(Formatter.format("<red>World not loaded.")); return; }
        Location dest = new Location(world, warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch());

        Runnable doTeleport = () -> player.teleportAsync(dest).thenAccept(success -> {
            DebugManager.log("warp", player.getName() + " -> warp '" + warp.getId() + "' success=" + success);
            if (!success) return;
            if (warp.getCost() > 0) plugin.getEconomy().removeCoins(player, warp.getCost());
            if (warp.getCooldownSeconds() > 0) profile.getCooldownManager().setCooldown(cooldownKey, warp.getCooldownSeconds());
            player.sendMessage(Formatter.format("<green>Teleported to <white>" + warp.getDisplayName()));
        });

        if (warmupDone || warp.getWarmupSeconds() <= 0) {
            doTeleport.run();
            return;
        }

        // Warmup: cancel if the player moves (block-level) or takes damage before it completes.
        player.sendMessage(Formatter.format("<yellow>Teleporting to <white>" + warp.getDisplayName()
                + "<yellow> in " + warp.getWarmupSeconds() + "s. Don't move!"));
        Location warmupStart = player.getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Location now = player.getLocation();
            if (now.getBlockX() != warmupStart.getBlockX() || now.getBlockY() != warmupStart.getBlockY()
                    || now.getBlockZ() != warmupStart.getBlockZ()) {
                player.sendMessage(Formatter.format("<red>Warp cancelled — you moved."));
                return;
            }
            // Re-resolve everything after the warmup instead of using what was captured before it:
            // the warp may have been edited or deleted by a reload meanwhile, the profile object
            // replaced (its cooldown would be set on a discarded one), or the coins spent.
            // The warp module may have reloaded, replacing this manager — use the live one.
            WarpManager live = plugin.getWarpManager();
            WarpDefinition current = live != null ? live.getRegistry().get(warp.getId()).orElse(null) : null;
            if (current == null) {
                player.sendMessage(Formatter.format("<red>That warp no longer exists."));
                return;
            }
            live.teleport(player, current, true);
        }, warp.getWarmupSeconds() * 20L);
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
