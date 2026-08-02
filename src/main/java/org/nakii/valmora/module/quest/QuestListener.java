package org.nakii.valmora.module.quest;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.module.npc.event.NpcInteractEvent;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.profile.event.TagAddedEvent;
import org.nakii.valmora.module.quest.points.PointsChangedEvent;
import org.nakii.valmora.module.skill.SkillLevelUpEvent;
import org.nakii.valmora.module.skill.SkillXpGainEvent;
import org.nakii.valmora.module.zone.event.ZoneEnterEvent;
import org.nakii.valmora.util.Keys;

public class QuestListener implements Listener {

    private final QuestManager questManager;

    public QuestListener(QuestManager questManager) {
        this.questManager = questManager;
    }

    // ── Auto-once + LOGIN ────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        questManager.startAutoOnceObjectivesForPlayer(event.getPlayer());
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.LOGIN, "login", 1);
        checkStatReachObjectives(event.getPlayer());
        checkExperienceObjectives(event.getPlayer());
        checkPointObjectives(event.getPlayer());
    }

    // ── LOGOUT ───────────────────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.LOGOUT, "logout", 1);
    }

    // ── KILL ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() == null) return;
        String mobId = entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        String target = mobId != null ? mobId : entity.getType().name();
        questManager.trigger(entity.getKiller(), QuestObjectiveTypes.KILL, target, 1);
    }

    // ── COLLECT ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String itemId = event.getItem().getItemStack().getPersistentDataContainer()
                .get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        String target = itemId != null ? itemId : event.getItem().getItemStack().getType().name();
        questManager.trigger(player, QuestObjectiveTypes.COLLECT, target,
                event.getItem().getItemStack().getAmount());
    }

    // ── REACH_ZONE ───────────────────────────────────────────────────────────

    @EventHandler
    public void onZoneEnter(ZoneEnterEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.REACH_ZONE,
                event.getZone().getId(), 1);
    }

    // ── TALK_TO_NPC ──────────────────────────────────────────────────────────

    @EventHandler
    public void onNpcInteract(NpcInteractEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.TALK_TO_NPC,
                event.getNpc().getId(), 1);
    }

    // ── DIE ──────────────────────────────────────────────────────────────────

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.DIE, "die", 1);
    }

    // ── BLOCK_BREAK ──────────────────────────────────────────────────────────

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.BLOCK_BREAK,
                event.getBlock().getType().name(), 1);
    }

    // ── BLOCK_PLACE ──────────────────────────────────────────────────────────

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.BLOCK_PLACE,
                event.getBlock().getType().name(), 1);

        // Tag furnace-type blocks with their placer so SMELT objectives can attribute
        // smelts back to a player (FurnaceSmeltEvent itself carries no player reference).
        if (event.getBlockPlaced().getState() instanceof TileState tileState
                && isFurnace(event.getBlock().getType())) {
            tileState.getPersistentDataContainer().set(Keys.FURNACE_OWNER_KEY,
                    PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
            tileState.update();
        }
    }

    // ── FISH ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        String itemType = event.getCaught() != null ? event.getCaught().getType().name() : "FISH";
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.FISH, itemType, 1);
    }

    // ── SHEAR ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.SHEAR,
                event.getEntity().getType().name(), 1);
    }

    // ── BREED ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        questManager.trigger(player, QuestObjectiveTypes.BREED,
                event.getEntityType().name(), 1);
    }

    // ── TAME ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        questManager.trigger(player, QuestObjectiveTypes.TAME,
                event.getEntity().getType().name(), 1);
    }

    // ── CONSUME (+ legacy DRINK_POTION alias) ────────────────────────────────

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String itemId = item.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        String target = itemId != null ? itemId : item.getType().name();
        questManager.trigger(player, QuestObjectiveTypes.CONSUME, target, 1);
        // backward-compat alias for any potion
        if (item.getType().name().contains("POTION")) {
            questManager.trigger(player, QuestObjectiveTypes.DRINK_POTION, target, 1);
        }
    }

    // ── CRAFT ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getRecipe().getResult();
        String itemId = result.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        String target = itemId != null ? itemId : result.getType().name();
        // Clicking "craft all" can produce multiples; each click = 1 craft action
        questManager.trigger(player, QuestObjectiveTypes.CRAFT, target, 1);
    }

    // ── ENCHANT ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        // Fire once for each enchantment applied
        event.getEnchantsToAdd().forEach((enchant, level) ->
            questManager.trigger(player, QuestObjectiveTypes.ENCHANT,
                    enchant.getKey().getKey(), 1));
        // Also allow matching "any"
        if (!event.getEnchantsToAdd().isEmpty()) {
            questManager.trigger(player, QuestObjectiveTypes.ENCHANT, "any", 1);
        }
    }

    // ── JUMP ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.JUMP, "jump", 1);
    }

    // ── RIDE ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String target = event.getMount().getType().name();
        questManager.trigger(player, QuestObjectiveTypes.RIDE, target, 1);
        questManager.trigger(player, QuestObjectiveTypes.RIDE, "any", 1);
    }

    // ── STEP (pressure plate at specific location) ───────────────────────────

    @EventHandler
    public void onStep(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        if (event.getClickedBlock() == null) return;
        if (!isPressurePlate(event.getClickedBlock().getType())) return;

        Player player = event.getPlayer();
        if (!questManager.hasActiveObjectiveType(player, QuestObjectiveTypes.STEP)) return;

        String locationTarget = locationToString(event.getClickedBlock().getLocation());
        questManager.trigger(player, QuestObjectiveTypes.STEP, locationTarget, 1);
    }

    // ── LOCATION (reach a coordinate within range) ───────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only check when the player changes block position
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (!questManager.hasActiveObjectiveType(player, QuestObjectiveTypes.LOCATION)) return;

        checkLocationObjectives(player);
    }

    // ── LEVEL_SKILL ──────────────────────────────────────────────────────────

    @EventHandler
    public void onSkillLevelUp(SkillLevelUpEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.LEVEL_SKILL,
                event.getSkill().getId(), 1);
    }

    // ── EXP_GAIN ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onXpGain(SkillXpGainEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.EXP_GAIN,
                event.getSkill().getId(), (int) Math.ceil(event.getXp()));
    }

    // ── SMELT ────────────────────────────────────────────────────────────────
    // FurnaceSmeltEvent carries no player reference, so we attribute the smelt to whoever
    // placed the furnace (tagged in onBlockPlace above).

    @EventHandler
    public void onSmelt(FurnaceSmeltEvent event) {
        if (!(event.getBlock().getState() instanceof TileState tileState)) return;
        String ownerId = tileState.getPersistentDataContainer()
                .get(Keys.FURNACE_OWNER_KEY, PersistentDataType.STRING);
        if (ownerId == null) return;

        Player owner = org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(ownerId));
        if (owner == null) return;
        if (!questManager.hasActiveObjectiveType(owner, QuestObjectiveTypes.SMELT)) return;

        ItemStack result = event.getResult();
        String itemId = result.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        String target = itemId != null ? itemId : result.getType().name();
        questManager.trigger(owner, QuestObjectiveTypes.SMELT, target, result.getAmount());
    }

    // ── ACTION (block click) ─────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onAction(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        boolean isRight = action == Action.RIGHT_CLICK_BLOCK;
        boolean isLeft  = action == Action.LEFT_CLICK_BLOCK;
        if (!isRight && !isLeft) return;
        if (event.getClickedBlock() == null) return;

        if (!questManager.hasActiveObjectiveType(event.getPlayer(), QuestObjectiveTypes.ACTION)) return;

        String clickStr = isRight ? "right" : "left";
        String blockStr = event.getClickedBlock().getType().name();

        // Fire 4 combinations so objective target can be: right:OAK_DOOR, any:OAK_DOOR, right:any, any:any
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.ACTION, clickStr + ":" + blockStr, 1);
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.ACTION, "any:" + blockStr, 1);
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.ACTION, clickStr + ":any", 1);
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.ACTION, "any:any", 1);
    }

    // ── ARROW ────────────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onArrow(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;
        if (!questManager.hasActiveObjectiveType(player, QuestObjectiveTypes.ARROW)) return;

        Location hitLoc = arrow.getLocation();
        ValmoraProfile profile = questManager.getProfile(player);
        if (profile == null) return;

        for (var quest : questManager.getRegistry().values()) {
            if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) continue;
            for (var obj : quest.getObjectives()) {
                if (!obj.getType().equalsIgnoreCase(QuestObjectiveTypes.ARROW)) continue;
                Location target = parseLocation(obj.getTarget(), player.getWorld());
                if (target == null) continue;
                double radius = obj.getRequired();
                if (hitLoc.getWorld() != null && hitLoc.getWorld().equals(target.getWorld())
                        && hitLoc.distanceSquared(target) <= radius * radius) {
                    questManager.trigger(player, QuestObjectiveTypes.ARROW, obj.getTarget(), 1);
                }
            }
        }
    }

    // ── COMMAND ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!questManager.hasActiveObjectiveType(event.getPlayer(), QuestObjectiveTypes.COMMAND)) return;
        // Strip leading slash for consistent matching
        String cmd = event.getMessage().startsWith("/")
                ? event.getMessage().substring(1)
                : event.getMessage();
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.COMMAND, cmd.toLowerCase(), 1);
    }

    // ── EQUIP ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onEquip(PlayerArmorChangeEvent event) {
        if (!questManager.hasActiveObjectiveType(event.getPlayer(), QuestObjectiveTypes.EQUIP)) return;
        ItemStack newItem = event.getNewItem();
        if (newItem == null || newItem.getType() == Material.AIR) return;

        String slotStr = event.getSlotType().name(); // HEAD, CHEST, LEGS, FEET
        String itemId  = newItem.getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        String itemStr = itemId != null ? itemId : newItem.getType().name();

        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.EQUIP, slotStr + ":" + itemStr, 1);
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.EQUIP, "any:" + itemStr, 1);
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.EQUIP, slotStr + ":any", 1);
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.EQUIP, "any:any", 1);
    }

    // ── EXPERIENCE ───────────────────────────────────────────────────────────

    @EventHandler
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (!questManager.hasActiveObjectiveType(player, QuestObjectiveTypes.EXPERIENCE)) return;

        int level = event.getNewLevel();
        ValmoraProfile profile = questManager.getProfile(player);
        if (profile == null) return;

        for (var quest : questManager.getRegistry().values()) {
            if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) continue;
            for (var obj : quest.getObjectives()) {
                if (!obj.getType().equalsIgnoreCase(QuestObjectiveTypes.EXPERIENCE)) continue;
                if (level >= obj.getRequired()) {
                    questManager.trigger(player, QuestObjectiveTypes.EXPERIENCE, obj.getTarget(), obj.getRequired());
                }
            }
        }
    }

    // ── INTERACT (entity) ────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!questManager.hasActiveObjectiveType(event.getPlayer(), QuestObjectiveTypes.INTERACT)) return;
        fireInteractTriggers(event.getPlayer(), "right", event.getRightClicked());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!questManager.hasActiveObjectiveType(event.getPlayer(), QuestObjectiveTypes.INTERACT)) return;
        fireInteractTriggers(event.getPlayer(), "right", event.getRightClicked());
    }

    private void fireInteractTriggers(Player player, String clickStr, Entity entity) {
        String customId = entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        String entityStr = customId != null ? customId : entity.getType().name();

        questManager.trigger(player, QuestObjectiveTypes.INTERACT, clickStr + ":" + entityStr, 1);
        questManager.trigger(player, QuestObjectiveTypes.INTERACT, "any:" + entityStr, 1);
        questManager.trigger(player, QuestObjectiveTypes.INTERACT, clickStr + ":any", 1);
        questManager.trigger(player, QuestObjectiveTypes.INTERACT, "any:any", 1);
    }

    // ── TAG ──────────────────────────────────────────────────────────────────

    @EventHandler
    public void onTagAdded(TagAddedEvent event) {
        questManager.trigger(event.getPlayer(), QuestObjectiveTypes.TAG, event.getTag(), 1);
    }

    // ── POINT ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPointsChanged(PointsChangedEvent event) {
        Player player = event.getPlayer();
        if (!questManager.hasActiveObjectiveType(player, QuestObjectiveTypes.POINT)) return;
        ValmoraProfile profile = questManager.getProfile(player);
        if (profile == null) return;

        for (var quest : questManager.getRegistry().values()) {
            if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) continue;
            for (var obj : quest.getObjectives()) {
                if (!obj.getType().equalsIgnoreCase(QuestObjectiveTypes.POINT)) continue;
                if (!obj.getTarget().equalsIgnoreCase(event.getCategory())) continue;
                if (event.getNewAmount() >= obj.getRequired()) {
                    questManager.trigger(player, QuestObjectiveTypes.POINT, obj.getTarget(), obj.getRequired());
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private void checkStatReachObjectives(Player player) {
        var api = org.nakii.valmora.api.ValmoraAPI.getInstance();
        if (api == null) return;
        var pm = api.getPlayerManager();
        if (pm == null) return;
        ValmoraPlayer vp = pm.getSession(player.getUniqueId());
        if (vp == null || vp.getActiveProfile() == null) return;
        ValmoraProfile profile = vp.getActiveProfile();

        questManager.getRegistry().values().forEach(quest -> {
            if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) return;
            quest.getObjectives().stream()
                    .filter(o -> o.getType().equalsIgnoreCase(QuestObjectiveTypes.STAT_REACH))
                    .forEach(o -> {
                        double statVal = profile.getStatManager().getStat(o.getTarget());
                        if (statVal >= o.getRequired()) {
                            questManager.trigger(player, QuestObjectiveTypes.STAT_REACH,
                                    o.getTarget(), o.getRequired());
                        }
                    });
        });
    }

    private void checkLocationObjectives(Player player) {
        var api = org.nakii.valmora.api.ValmoraAPI.getInstance();
        if (api == null) return;
        var pm = api.getPlayerManager();
        if (pm == null) return;
        ValmoraPlayer vp = pm.getSession(player.getUniqueId());
        if (vp == null || vp.getActiveProfile() == null) return;
        ValmoraProfile profile = vp.getActiveProfile();

        questManager.getRegistry().values().forEach(quest -> {
            if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) return;
            quest.getObjectives().stream()
                    .filter(o -> o.getType().equalsIgnoreCase(QuestObjectiveTypes.LOCATION))
                    .forEach(o -> {
                        Location target = parseLocation(o.getTarget(), player.getWorld());
                        if (target == null) return;
                        // required field stores range in blocks
                        double range = o.getRequired();
                        if (player.getLocation().distanceSquared(target) <= range * range) {
                            questManager.trigger(player, QuestObjectiveTypes.LOCATION,
                                    o.getTarget(), o.getRequired());
                        }
                    });
        });
    }

    /** Parses "x;y;z;world" into a Location. Falls back to the given defaultWorld if world not found. */
    private Location parseLocation(String target, World defaultWorld) {
        if (target == null || target.isBlank()) return null;
        String[] parts = target.split(";");
        if (parts.length < 3) return null;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            World world = parts.length >= 4
                    ? org.bukkit.Bukkit.getWorld(parts[3])
                    : null;
            if (world == null) world = defaultWorld;
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Converts a block location to the canonical "x;y;z;world" target string. */
    private String locationToString(Location loc) {
        return loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ()
                + (loc.getWorld() != null ? ";" + loc.getWorld().getName() : "");
    }

    private boolean isPressurePlate(Material mat) {
        return Tag.PRESSURE_PLATES.isTagged(mat);
    }

    private boolean isFurnace(Material mat) {
        return mat == Material.FURNACE || mat == Material.BLAST_FURNACE || mat == Material.SMOKER;
    }

    private void checkExperienceObjectives(Player player) {
        ValmoraProfile profile = questManager.getProfile(player);
        if (profile == null) return;
        int level = player.getLevel();

        questManager.getRegistry().values().forEach(quest -> {
            if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) return;
            quest.getObjectives().stream()
                    .filter(o -> o.getType().equalsIgnoreCase(QuestObjectiveTypes.EXPERIENCE))
                    .filter(o -> level >= o.getRequired())
                    .forEach(o -> questManager.trigger(player, QuestObjectiveTypes.EXPERIENCE, o.getTarget(), o.getRequired()));
        });
    }

    private void checkPointObjectives(Player player) {
        var api = org.nakii.valmora.api.ValmoraAPI.getInstance();
        if (api == null) return;
        var pm = api.getPointsManager();
        if (pm == null) return;
        ValmoraProfile profile = questManager.getProfile(player);
        if (profile == null) return;

        questManager.getRegistry().values().forEach(quest -> {
            if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) return;
            quest.getObjectives().stream()
                    .filter(o -> o.getType().equalsIgnoreCase(QuestObjectiveTypes.POINT))
                    .forEach(o -> {
                        int pts = pm.getPoints(player.getUniqueId(), o.getTarget());
                        if (pts >= o.getRequired())
                            questManager.trigger(player, QuestObjectiveTypes.POINT, o.getTarget(), o.getRequired());
                    });
        });
    }
}
