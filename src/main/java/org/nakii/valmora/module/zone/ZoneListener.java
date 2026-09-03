package org.nakii.valmora.module.zone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.resource.ResourceModule;
import org.nakii.valmora.module.zone.event.ZoneEnterEvent;
import org.nakii.valmora.module.zone.event.ZoneExitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ZoneListener implements Listener {

    private static final Set<SpawnReason> BLOCKED_SPAWN_REASONS = Set.of(
        SpawnReason.NATURAL, SpawnReason.SLIME_SPLIT, SpawnReason.SPAWNER
    );

    /**
     * Vanilla block tags (Mojang keys, resolved dynamically via {@link Bukkit#getTag} rather than
     * static {@code Tag} constants — some of these aren't exposed as Bukkit `Tag.*` fields) for the
     * small set of attached-decoration block types that can silently detach/pop when their support
     * is removed, with no {@link BlockBreakEvent} of their own. See {@link #onBlockPhysics}.
     */
    private static final String[] ATTACHED_DECORATION_TAGS =
            {"torches", "signs", "banners", "buttons", "pressure_plates", "rails"};

    private final Valmora plugin;
    private final ZoneManager zoneManager;
    private List<Tag<Material>> attachedDecorationTags;

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

    /**
     * VANILLA_CONTROL_AUDIT.md §6 gap: {@link PlayerMoveEvent} does NOT fire for a player riding a
     * vehicle — its position updates are delivered separately via {@link VehicleMoveEvent}, keyed to
     * the vehicle entity, not the passenger. Without this handler, a boat/minecart/horse could ride
     * straight through a zone's {@code entry} restriction and past the enter/exit script hooks
     * ({@link ZoneEnterEvent}/{@link ZoneExitEvent}) entirely — both of which
     * {@link #onMoveEntryCheck}/{@link #onMove} only ever see for players moving on foot. Mirrors both
     * of those handlers combined: soft push-back (teleport the vehicle back to {@code from}, since
     * {@code VehicleMoveEvent} isn't cancellable, same non-cancel-but-revert approach
     * {@code onMoveEntryCheck} already uses) when entry is denied, otherwise re-run zone tracking for
     * every player passenger. Non-player-piloted/passengerless vehicle drift is skipped entirely.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent event) {
        List<Player> playerPassengers = new ArrayList<>();
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player) playerPassengers.add(player);
        }
        if (playerPassengers.isEmpty()) return;

        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        ZoneDefinition fromZone = zoneManager.getZoneAt(from).orElse(null);
        ZoneDefinition toZone   = zoneManager.getZoneAt(to).orElse(null);
        if (toZone != null && toZone != fromZone && !toZone.getFlags().entry()) {
            event.getVehicle().teleport(from);
            return;
        }
        for (Player player : playerPassengers) {
            zoneManager.checkTransition(player);
        }
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
        // HC-140: zone-enter popup duration (ticks) — snappy vs. readable.
        int enterTitleDurationTicks = plugin.getConfig().getInt("zones.enter-title-duration-ticks", 60);
        plugin.getUIManager().getActionBar().showTemporary(player, zone.getDisplayName(), enterTitleDurationTicks);
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

    // ── VANILLA_CONTROL_AUDIT.md §3-5 batch: extend existing zone protection flags to mechanics
    // that previously bypassed them entirely. No new ZoneFlags fields — deliberately reuses
    // blockBreaking ("can existing blocks in this zone be destroyed/altered") and blockPlacing
    // ("can new blocks/growth appear in this zone"), the same semantic split ZoneListener already
    // enforces for player mining/building, matching the precedent set by
    // death.RespawnAnchorListener.filterProtectedBlocks (reuses blockBreaking for explosion
    // protection rather than inventing a dedicated flag). ──────────────────────────────────────

    /** Fire starting a new fire block is treated as "placing" — gated by blockPlacing. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockPlacing()) event.setCancelled(true);
        });
    }

    /** Fire consuming an existing block is treated as "breaking" — gated by blockBreaking. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockBreaking()) event.setCancelled(true);
        });
    }

    /**
     * Water/lava flow silently destroying an existing block (torches, crops, etc. — vanilla fires
     * no {@code BlockBreakEvent} for this). Only relevant when the target actually holds something
     * worth protecting; ordinary flow into air/already-matching-fluid is left alone both for
     * correctness (nothing to protect) and to avoid a zone lookup on every single flow tick.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block to = event.getToBlock();
        Material toType = to.getType();
        if (toType.isAir() || toType == Material.WATER || toType == Material.LAVA) return;
        zoneManager.getZoneAt(to.getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockBreaking()) event.setCancelled(true);
        });
    }

    /**
     * Pistons moving protected blocks out of a zone (blockBreaking) or into one (blockPlacing) at
     * either end of the push/pull. {@code getBlocks()} already excludes the piston/piston-head
     * itself.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (isPistonMoveProtected(event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (isPistonMoveProtected(event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    private boolean isPistonMoveProtected(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        for (Block block : blocks) {
            if (zoneManager.getZoneAt(block.getLocation()).map(z -> !z.getFlags().blockBreaking()).orElse(false)) {
                return true;
            }
            Block destination = block.getRelative(direction);
            if (zoneManager.getZoneAt(destination.getLocation()).map(z -> !z.getFlags().blockPlacing()).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** General TNT/creeper/wither/end-crystal/etc. explosions — same filtering as respawn-anchor/bed
     * explosions ({@code death.RespawnAnchorListener}), just applied to every explosion source. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosionBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosionBlocks(event.blockList());
    }

    private void filterExplosionBlocks(List<Block> blocks) {
        blocks.removeIf(block -> zoneManager.getZoneAt(block.getLocation())
                .map(zone -> !zone.getFlags().blockBreaking())
                .orElse(false));
    }

    /**
     * Mob griefing (enderman block pickup/place, sheep eating grass, silverfish infesting stone,
     * ravager/wither destroying blocks, snow golem trail, etc.). {@code GameRule.MOB_GRIEFING}/
     * {@code WITHER_BREAK_BLOCKS} already apply server/world-wide via the generic {@code world_rules}
     * pass-through (config {@code world.gamerules.*}); this adds the finer-grained per-zone override
     * on top, gated by blockBreaking the same as player mining.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockBreaking()) event.setCancelled(true);
        });
    }

    /** Crop/amethyst/bamboo/cave-vine random-tick growth — treated as "placing". */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockPlacing()) event.setCancelled(true);
        });
    }

    /** Grass/mycelium/vine/mushroom spread — treated as "placing" (introducing new block state). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockPlacing()) event.setCancelled(true);
        });
    }

    /**
     * Sapling-to-tree / huge-mushroom structure growth. Gated on the growth origin's zone only
     * (not every block the resulting structure would occupy) — a tree canopy can extend past a
     * narrow zone boundary; treating the sapling's own zone as authoritative is the same
     * simplification vanilla-adjacent protection plugins make and is good enough in practice.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        zoneManager.getZoneAt(event.getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockPlacing()) event.setCancelled(true);
        });
    }

    /**
     * Ambient block-state transitions (ice/snow melting, coral dying, redstone-ore glow fading,
     * water freezing, snow-layer forming, Frost Walker ice, concrete solidifying, etc.) — previously
     * completely outside zone control, unlike every other block-change mechanic in this listener.
     * Gated by the new {@code naturalBlockChanges} flag rather than reusing blockBreaking/
     * blockPlacing: this isn't "can a player destroy/build here", it's "should ambient world
     * processes touch blocks here at all" — e.g. a snow zone that wants its ice to stay frozen
     * regardless of biome temperature or light level.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().naturalBlockChanges()) event.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().naturalBlockChanges()) event.setCancelled(true);
        });
    }

    /**
     * {@link EntityBlockFormEvent} (Frost Walker ice, snow golem trail) has its own separate
     * {@code HandlerList} — it does NOT get delivered to a plain {@link BlockFormEvent} handler even
     * though it extends it, so it needs a listener of its own to fall under the same flag.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().naturalBlockChanges()) event.setCancelled(true);
        });
    }

    /**
     * Support-chain breaks (§3/§1 audit gap): removing the block a torch/sign/banner/button/
     * pressure-plate/rail is attached to detaches it via {@link BlockPhysicsEvent}, not
     * {@link BlockBreakEvent} — so it previously bypassed zone protection entirely even though the
     * block doing the detaching could sit outside the protected zone (e.g. mining the wall behind a
     * protected torch from an adjacent, unprotected area). Deliberately narrow: only the physical
     * block actually being checked matters (not every physics tick), and only for this small,
     * tag-driven set of attachable decoration types — a blanket cancel would break redstone/fence/
     * stair shape updates. Gated by blockBreaking, same semantic as every other "protect existing
     * blocks" check in this listener.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!isAttachedDecoration(block.getType())) return;
        zoneManager.getZoneAt(block.getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockBreaking()) event.setCancelled(true);
        });
    }

    private boolean isAttachedDecoration(Material type) {
        if (attachedDecorationTags == null) {
            List<Tag<Material>> resolved = new ArrayList<>();
            for (String key : ATTACHED_DECORATION_TAGS) {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(key), Material.class);
                if (tag != null) resolved.add(tag);
            }
            attachedDecorationTags = resolved;
        }
        for (Tag<Material> tag : attachedDecorationTags) {
            if (tag.isTagged(type)) return true;
        }
        return false;
    }

    // ── VANILLA_CONTROL_AUDIT.md §4/§10 batch: buckets and portal creation, the two remaining
    // fluid/dimension mechanics that bypassed zone protection entirely. Portal *use* (walking
    // through one) is verified already covered — PlayerPortalEvent extends PlayerTeleportEvent, so
    // it already goes through onTeleportGate's existing `teleportation` flag, same precedent as
    // BlockMultiPlaceEvent extending BlockPlaceEvent (fourth pass). Cross-world tracking
    // (PlayerChangedWorldEvent) is likewise already covered — onTeleport reschedules
    // zoneManager.checkTransition() after every PlayerTeleportEvent, portals included, and
    // checkTransition reads the player's live (post-teleport, post-world-change) location. ────────

    /** Filling a bucket removes the source block — treated as "breaking". */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockBreaking()) event.setCancelled(true);
        });
    }

    /** Emptying a bucket creates a fluid/powder-snow block — treated as "placing". */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        zoneManager.getZoneAt(event.getBlock().getLocation()).ifPresent(zone -> {
            if (!zone.getFlags().blockPlacing()) event.setCancelled(true);
        });
    }

    /**
     * Nether/end portal frame igniting into an actual portal — creates new blocks, so treated as
     * "placing", same as {@link #onBlockIgnite}. Cancels the whole creation if any resulting block
     * would land in a blockPlacing-disabled zone (mirrors {@link #isPistonMoveProtected}'s
     * any-block-blocks-the-whole-action approach).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        for (BlockState state : event.getBlocks()) {
            if (zoneManager.getZoneAt(state.getLocation()).map(z -> !z.getFlags().blockPlacing()).orElse(false)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
