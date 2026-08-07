package org.nakii.valmora.module.zone;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.resource.ResourceModule;
import org.nakii.valmora.module.zone.event.ZoneEnterEvent;
import org.nakii.valmora.module.zone.event.ZoneExitEvent;

import java.util.Set;

public class ZoneListener implements Listener {

    private static final Set<SpawnReason> BLOCKED_SPAWN_REASONS = Set.of(
        SpawnReason.NATURAL, SpawnReason.SLIME_SPLIT, SpawnReason.SPAWNER
    );

    private final Valmora plugin;
    private final ZoneManager zoneManager;

    public ZoneListener(Valmora plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMoveEntryCheck(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;
        ZoneDefinition fromZone = zoneManager.getZoneAt(from).orElse(null);
        ZoneDefinition toZone   = zoneManager.getZoneAt(to).orElse(null);
        if (toZone != null && toZone != fromZone && !toZone.getFlags().entry()) {
            event.setTo(from.clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        zoneManager.checkTransition(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> zoneManager.checkTransition(event.getPlayer()));
    }

    /**
     * Enforces the zone {@code teleportation} flag for every {@link PlayerTeleportEvent}, not
     * just the script engine's {@code teleport} event (fixed 2026-08-07 — warps
     * (`WarpManager.teleport` calls `player.teleportAsync()` directly) and any other
     * plugin/vanilla teleport (ender pearl, chorus fruit, `/tp`, …) previously bypassed this
     * check entirely). Same semantic as {@code TeleportEventFactory.isBlockedByZone}: blocked
     * when the player's *current* zone (the one they're teleporting away from) disables
     * teleportation — e.g. to prevent escaping a boss arena.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleportGate(PlayerTeleportEvent event) {
        zoneManager.getZoneAt(event.getFrom()).ifPresent(zone -> {
            if (!zone.getFlags().teleportation()) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { zoneManager.onPlayerJoin(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { zoneManager.onPlayerQuit(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onZoneEnter(ZoneEnterEvent event) {
        Player player = event.getPlayer();
        ZoneDefinition zone = event.getZone();
        plugin.getUIManager().getActionBar().showTemporary(player, zone.getDisplayName(), 60);
        setPlayerStateZoneId(player, zone.getId());
        if (!zone.getEnterActions().isEmpty()) {
            SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
            plugin.getScriptModule().getEventParser().parseList(zone.getEnterActions()).execute(ctx);
        }
    }

    @EventHandler
    public void onZoneExit(ZoneExitEvent event) {
        ZoneDefinition zone = event.getZone();
        Player player = event.getPlayer();
        // Cleared here rather than left for the next onZoneEnter — a transition straight into
        // the wilderness (no new zone) never fires a ZoneEnterEvent to overwrite it.
        setPlayerStateZoneId(player, null);
        if (!zone.getExitActions().isEmpty()) {
            SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);
            plugin.getScriptModule().getEventParser().parseList(zone.getExitActions()).execute(ctx);
        }
    }

    /**
     * Keeps the profile-persisted {@code PlayerState.currentZoneId} (consumed by the {@code zone}
     * script condition — {@code ZoneCondition}) in sync with zone transitions. Fixed 2026-08-07:
     * this was never called anywhere, so {@code setCurrentZoneId} sat dead and the {@code zone}
     * condition always evaluated false. Note this is a *separate* tracker from
     * {@code ZoneManager.playerZones} (used by {@code getCurrentZone(Player)}/`$zone.*$`), which
     * was already correctly maintained by {@code checkTransition} — the gap was specifically the
     * profile-persisted copy other systems (like this condition) read from.
     */
    private void setPlayerStateZoneId(Player player, String zoneId) {
        var vp = org.nakii.valmora.api.ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (vp == null || vp.getActiveProfile() == null) return;
        vp.getActiveProfile().getPlayerState().setCurrentZoneId(zoneId);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player)) return;
        zoneManager.getZoneAt(victim.getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().pvp()) event.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ZoneDefinition zone = zoneManager.getZoneAt(block.getLocation()).orElse(null);
        if (zone == null || zone.getFlags().blockBreaking()) return;
        // Resource blocks and their intermediate regeneration stages are always breakable
        if (zone.getResourceBlocks().containsKey(block.getType())) return;
        ResourceModule rm = plugin.getResourceModule();
        if (rm != null && rm.getResourceManager() != null
                && rm.getResourceManager().isTrackedResource(block.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ZoneDefinition zone = zoneManager.getZoneAt(event.getBlock().getLocation()).orElse(null);
        if (zone != null && !zone.getFlags().blockPlacing()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!BLOCKED_SPAWN_REASONS.contains(event.getSpawnReason())) return;
        ZoneDefinition zone = zoneManager.getZoneAt(event.getLocation()).orElse(null);
        if (zone != null && !zone.getFlags().naturalMobSpawning()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        zoneManager.getZoneAt(player.getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().hunger()) event.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().leafDecay()) event.setCancelled(true);
        });
    }
}
