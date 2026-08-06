package org.nakii.valmora.module.pet;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.stat.StatManager;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.*;

public class PetModule implements ReloadableModule {

    private final Valmora plugin;
    private final Map<String, PetDefinition> definitions = new HashMap<>();

    // Keyed by a per-item PET_INSTANCE_KEY tag (stamped on first summon) rather than an inventory
    // slot index — the pet item can be moved anywhere in the player's inventory and still resolve.
    private final Map<UUID, UUID> activePetInstance = new HashMap<>();
    private final Map<UUID, Entity> activePetEntity = new HashMap<>();

    private PetListener listener;
    private org.bukkit.scheduler.BukkitTask followTask;

    // Phase 3.3 (docs/REFACTOR/PROGRESS.md): server-wide pet XP defaults, loaded from
    // pets/defaults.yml before pet definitions so each pet can fall back to them.
    private String defaultXpFormula = "100 * $curve.level$ * $curve.level$";
    private int defaultMaxLevel = 200;

    public PetModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        definitions.clear();
        activePetInstance.clear();
        activePetEntity.clear();
        loadPetDefaultsConfig();
        loadDefinitions();

        this.listener = new PetListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getScriptModule().registerProvider(new PetVariableProvider(this));

        if (followTask != null) followTask.cancel();
        if (plugin.getServer() != null && plugin.getServer().getScheduler() != null) {
            followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new PetFollowTask(plugin, activePetEntity), 5L, 5L);
        }
    }

    @Override
    public void onDisable() {
        if (followTask != null) { followTask.cancel(); followTask = null; }
        for (Entity entity : activePetEntity.values()) {
            if (entity.isValid()) entity.remove();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        definitions.clear();
        activePetInstance.clear();
        activePetEntity.clear();
    }

    @Override
    public String getId() { return "pets"; }

    @Override
    public String getName() { return "Pet System"; }

    public PetDefinition getDefinition(String id) { return definitions.get(id.toLowerCase()); }
    public Collection<PetDefinition> getDefinitions() { return definitions.values(); }
    public Map<UUID, Entity> getActivePetEntities() { return activePetEntity; }

    public boolean hasPetActive(Player player) {
        return activePetEntity.containsKey(player.getUniqueId());
    }

    /** Clears the active-pet slot mapping for a UUID without touching the entity (see PetListener#onQuit). */
    public void clearActivePetSlot(UUID uuid) {
        activePetInstance.remove(uuid);
    }

    /**
     * Finds the currently-summoned pet item anywhere in the player's inventory, by matching the
     * per-instance tag stamped on it at summon time — not by a fixed slot index, so the item can
     * be freely moved around the inventory (or even end up in a different hotbar slot from a
     * relog) and still resolve correctly.
     */
    private ItemStack findActivePetItem(Player player) {
        UUID instanceId = activePetInstance.get(player.getUniqueId());
        if (instanceId == null) return null;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || !stack.hasItemMeta()) continue;
            String tag = stack.getItemMeta().getPersistentDataContainer()
                    .get(Keys.PET_INSTANCE_KEY, PersistentDataType.STRING);
            if (instanceId.toString().equals(tag)) return stack;
        }
        return null;
    }

    public PetDefinition getActivePetDefinition(Player player) {
        ItemStack item = findActivePetItem(player);
        if (item == null) return null;
        String petId = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.PET_ID_KEY, PersistentDataType.STRING);
        return petId != null ? definitions.get(petId) : null;
    }

    public int getActivePetLevel(Player player) {
        ItemStack item = findActivePetItem(player);
        if (item == null) return 1;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(Keys.PET_LEVEL_KEY, PersistentDataType.INTEGER, 1);
    }

    public double getActivePetXp(Player player) {
        ItemStack item = findActivePetItem(player);
        if (item == null) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(Keys.PET_XP_KEY, PersistentDataType.DOUBLE, 0.0);
    }

    public void toggleSummon(Player player, int slot) {
        UUID uid = player.getUniqueId();

        if (activePetInstance.containsKey(uid)) {
            ItemStack activeItem = findActivePetItem(player);
            ItemStack clickedItem = player.getInventory().getItem(slot);
            boolean sameItem = activeItem != null && clickedItem != null
                    && instanceIdOf(activeItem) != null && instanceIdOf(activeItem).equals(instanceIdOf(clickedItem));
            if (!sameItem) {
                player.sendMessage(Formatter.format("<red>You already have a pet active. Unsummon it first."));
                return;
            }
            unsummon(player);
            return;
        }

        ItemStack petItem = player.getInventory().getItem(slot);
        if (petItem == null || !petItem.hasItemMeta()) return;
        ItemMeta petMeta = petItem.getItemMeta();
        String petId = petMeta.getPersistentDataContainer()
                .get(Keys.PET_ID_KEY, PersistentDataType.STRING);
        if (petId == null) return;
        PetDefinition def = definitions.get(petId);
        if (def == null) return;

        // Stamp an instance id if this item predates the instance-tracking system (e.g. an
        // admin-crafted item from before this change) so it can still be tracked going forward.
        String instanceTag = petMeta.getPersistentDataContainer().get(Keys.PET_INSTANCE_KEY, PersistentDataType.STRING);
        UUID instanceId;
        if (instanceTag == null) {
            instanceId = UUID.randomUUID();
            petMeta.getPersistentDataContainer().set(Keys.PET_INSTANCE_KEY, PersistentDataType.STRING, instanceId.toString());
            petItem.setItemMeta(petMeta);
        } else {
            instanceId = UUID.fromString(instanceTag);
        }

        Location loc = player.getLocation().add(1, 0, 0);
        try {
            LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(loc, def.getEntityType());
            entity.customName(Formatter.format("<gold>" + def.getName()));
            entity.setCustomNameVisible(true);
            entity.setAI(false);

            activePetInstance.put(uid, instanceId);
            activePetEntity.put(uid, entity);

            int level = getActivePetLevel(player);
            player.sendMessage(Formatter.format(
                    "<green>You summoned your <gold>" + def.getName() + " <green>(Lvl " + level + ")"));
            triggerStatRecalc(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn pet entity " + def.getEntityType() + ": " + e.getMessage());
        }
    }

    private UUID instanceIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String tag = item.getItemMeta().getPersistentDataContainer().get(Keys.PET_INSTANCE_KEY, PersistentDataType.STRING);
        return tag != null ? UUID.fromString(tag) : null;
    }

    public void unsummon(Player player) {
        UUID uid = player.getUniqueId();
        Entity entity = activePetEntity.remove(uid);
        activePetInstance.remove(uid);
        if (entity != null && entity.isValid()) entity.remove();
        player.sendMessage(Formatter.format("<yellow>Pet unsummoned."));
        triggerStatRecalc(player);
    }

    public void applyPetStats(Player player, StatManager statManager) {
        PetDefinition def = getActivePetDefinition(player);
        if (def == null) return;
        int level = getActivePetLevel(player);
        for (Map.Entry<String, Double> entry : def.computeStats(level).entrySet()) {
            statManager.addModifier(entry.getKey(), entry.getValue());
        }
    }

    public void gainPetXp(Player player, double amount) {
        ItemStack petItem = findActivePetItem(player);
        if (petItem == null) return;

        String petId = petItem.getItemMeta().getPersistentDataContainer()
                .get(Keys.PET_ID_KEY, PersistentDataType.STRING);
        PetDefinition def = petId != null ? getDefinition(petId) : null;
        if (def == null) return;

        ItemMeta meta = petItem.getItemMeta();
        int level = meta.getPersistentDataContainer()
                .getOrDefault(Keys.PET_LEVEL_KEY, PersistentDataType.INTEGER, 1);
        int initialLevel = level;
        double xp = meta.getPersistentDataContainer()
                .getOrDefault(Keys.PET_XP_KEY, PersistentDataType.DOUBLE, 0.0);
        xp += amount;

        while (level < def.getMaxLevel()) {
            long needed = def.xpForLevel(level);
            if (xp >= needed) {
                xp -= needed;
                level++;
                fireMilestones(player, def, level);
                player.sendMessage(Formatter.format("<gold>✦ Pet leveled up to <yellow>Level " + level + "<gold>!"));
            } else {
                break;
            }
        }

        meta.getPersistentDataContainer().set(Keys.PET_LEVEL_KEY, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(Keys.PET_XP_KEY, PersistentDataType.DOUBLE, xp);
        petItem.setItemMeta(meta);

        if (level > initialLevel) triggerStatRecalc(player);
    }

    private void fireMilestones(Player player, PetDefinition def, int level) {
        if (def == null) return;
        List<String> events = def.getMilestones().get(level);
        if (events == null || events.isEmpty()) return;
        var ctx = new org.nakii.valmora.api.execution.SimpleExecutionContext(
                player, player.getLocation(), new org.bukkit.configuration.file.YamlConfiguration());
        plugin.getScriptModule().getEventParser().parseList(events).execute(ctx);
    }

    private void triggerStatRecalc(Player player) {
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (session != null && session.getActiveProfile() != null) {
            session.getActiveProfile().getStatManager().recalculateStats(player);
        }
    }

    /**
     * Reads pets/defaults.yml → pet_defaults (Phase 3.3). Missing file/section keeps the
     * pre-refactor hardcoded values (100 * level^2, max level 200) as fallback defaults.
     */
    private void loadPetDefaultsConfig() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "pets/defaults.yml");
        if (!file.exists()) return;
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("pet_defaults");
        if (section == null) return;
        defaultXpFormula = section.getString("xp-formula", defaultXpFormula);
        defaultMaxLevel = Math.max(1, section.getInt("max-level", defaultMaxLevel));
    }

    /** Pre-computes a level->XP-needed table by evaluating {@code formula} once per level (Phase 3.3). Never re-evaluated afterward. */
    private long[] computeXpThresholds(String formula, int maxLevel) {
        var expression = plugin.getScriptModule().getExpressionParser().parse(formula);
        long[] thresholds = new long[maxLevel];
        for (int level = 1; level <= maxLevel; level++) {
            var ctx = new org.nakii.valmora.api.execution.SimpleExecutionContext(null, null, null);
            ctx.set("curve:level", (double) level);
            Object result = expression.evaluate(ctx);
            thresholds[level - 1] = result instanceof Number n ? Math.round(n.doubleValue()) : 0L;
        }
        return thresholds;
    }

    private void loadDefinitions() {
        YamlLoader<PetDefinition> loader = new YamlLoader<>(plugin, "pets", "Pet");
        loader.load(this::parseDefinition, def -> {
            // pets/defaults.yml's top-level "pet_defaults" key parses as a (discarded) dummy
            // PetDefinition through the same generic loader — it's server-wide config, not a pet.
            if (!"pet_defaults".equalsIgnoreCase(def.getId())) {
                definitions.put(def.getId(), def);
            }
        });
    }

    private LoadResult<PetDefinition, String> parseDefinition(String id, ConfigurationSection section, String filePath) {
        try {
            String name = section.getString("name", id);
            EntityType entityType = EntityType.WOLF;
            if (section.contains("entity-type")) {
                try { entityType = EntityType.valueOf(section.getString("entity-type").toUpperCase()); }
                catch (IllegalArgumentException ignored) {}
            }
            Map<String, Double> baseStats = parseStatMap(section.getConfigurationSection("base-stats"));
            Map<String, Double> statsPerLevel = parseStatMap(section.getConfigurationSection("stats-per-level"));

            List<PetAbilityDefinition> abilities = new ArrayList<>();
            var parser = plugin.getScriptModule().getEventParser();
            for (Map<?, ?> abilityMap : section.getMapList("abilities")) {
                String triggerStr = (String) abilityMap.get("trigger");
                if (triggerStr == null) continue;
                PetAbilityTrigger trigger;
                try { trigger = PetAbilityTrigger.valueOf(triggerStr.toUpperCase()); }
                catch (IllegalArgumentException ignored) { continue; }
                @SuppressWarnings("unchecked")
                List<String> evList = (List<String>) abilityMap.get("events");
                if (evList != null) abilities.add(new PetAbilityDefinition(trigger, parser.parseList(evList)));
            }

            TreeMap<Integer, List<String>> milestones = new TreeMap<>();
            ConfigurationSection msSec = section.getConfigurationSection("milestones");
            if (msSec != null) {
                for (String key : msSec.getKeys(false)) {
                    try { milestones.put(Integer.parseInt(key), msSec.getStringList(key)); }
                    catch (NumberFormatException ignored) {}
                }
            }

            String xpFormula = section.getString("xp-formula", defaultXpFormula);
            int maxLevel = Math.max(1, section.getInt("max-level", defaultMaxLevel));
            long[] xpThresholds = computeXpThresholds(xpFormula, maxLevel);

            return LoadResult.success(new PetDefinition(id, name, entityType, baseStats, statsPerLevel, abilities, milestones, xpThresholds));
        } catch (Exception e) {
            return LoadResult.failure("[" + filePath + "] Failed to parse pet '" + id + "': " + e.getMessage());
        }
    }

    private Map<String, Double> parseStatMap(ConfigurationSection section) {
        Map<String, Double> map = new HashMap<>();
        if (section == null) return map;
        for (String key : section.getKeys(false)) {
            map.put(key.toLowerCase(), section.getDouble(key));
        }
        return map;
    }
}
