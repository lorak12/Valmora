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

    private final Map<UUID, Integer> activePetSlot = new HashMap<>();
    private final Map<UUID, Entity> activePetEntity = new HashMap<>();

    private PetListener listener;

    public PetModule(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        definitions.clear();
        activePetSlot.clear();
        activePetEntity.clear();
        loadDefinitions();

        this.listener = new PetListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getScriptModule().registerProvider(new PetVariableProvider(this));
    }

    @Override
    public void onDisable() {
        for (Entity entity : activePetEntity.values()) {
            if (entity.isValid()) entity.remove();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        definitions.clear();
        activePetSlot.clear();
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

    public PetDefinition getActivePetDefinition(Player player) {
        Integer slot = activePetSlot.get(player.getUniqueId());
        if (slot == null) return null;
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return null;
        String petId = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.PET_ID_KEY, PersistentDataType.STRING);
        return petId != null ? definitions.get(petId) : null;
    }

    public int getActivePetLevel(Player player) {
        Integer slot = activePetSlot.get(player.getUniqueId());
        if (slot == null) return 1;
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return 1;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(Keys.PET_LEVEL_KEY, PersistentDataType.INTEGER, 1);
    }

    public double getActivePetXp(Player player) {
        Integer slot = activePetSlot.get(player.getUniqueId());
        if (slot == null) return 0;
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(Keys.PET_XP_KEY, PersistentDataType.DOUBLE, 0.0);
    }

    public void toggleSummon(Player player, int slot) {
        UUID uid = player.getUniqueId();
        Integer currentSlot = activePetSlot.get(uid);

        if (currentSlot != null && currentSlot != slot) {
            player.sendMessage(Formatter.format("<red>You already have a pet active. Unsummon it first."));
            return;
        }

        if (currentSlot != null) {
            unsummon(player);
            return;
        }

        ItemStack petItem = player.getInventory().getItem(slot);
        if (petItem == null || !petItem.hasItemMeta()) return;
        String petId = petItem.getItemMeta().getPersistentDataContainer()
                .get(Keys.PET_ID_KEY, PersistentDataType.STRING);
        if (petId == null) return;
        PetDefinition def = definitions.get(petId);
        if (def == null) return;

        Location loc = player.getLocation().add(1, 0, 0);
        try {
            LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(loc, def.getEntityType());
            entity.customName(Formatter.format("<gold>" + def.getName()));
            entity.setCustomNameVisible(true);
            entity.setAI(false);

            activePetSlot.put(uid, slot);
            activePetEntity.put(uid, entity);

            int level = getActivePetLevel(player);
            player.sendMessage(Formatter.format(
                    "<green>You summoned your <gold>" + def.getName() + " <green>(Lvl " + level + ")"));
            triggerStatRecalc(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn pet entity " + def.getEntityType() + ": " + e.getMessage());
        }
    }

    public void unsummon(Player player) {
        UUID uid = player.getUniqueId();
        Entity entity = activePetEntity.remove(uid);
        activePetSlot.remove(uid);
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
        Integer slot = activePetSlot.get(player.getUniqueId());
        if (slot == null) return;
        ItemStack petItem = player.getInventory().getItem(slot);
        if (petItem == null || !petItem.hasItemMeta()) return;

        ItemMeta meta = petItem.getItemMeta();
        int level = meta.getPersistentDataContainer()
                .getOrDefault(Keys.PET_LEVEL_KEY, PersistentDataType.INTEGER, 1);
        int initialLevel = level;
        double xp = meta.getPersistentDataContainer()
                .getOrDefault(Keys.PET_XP_KEY, PersistentDataType.DOUBLE, 0.0);
        xp += amount;

        while (level < 200) {
            long needed = PetDefinition.xpForLevel(level);
            if (xp >= needed) {
                xp -= needed;
                level++;
                fireMilestones(player, level, slot);
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

    private void fireMilestones(Player player, int level, int slot) {
        ItemStack petItem = player.getInventory().getItem(slot);
        if (petItem == null || !petItem.hasItemMeta()) return;
        String petId = petItem.getItemMeta().getPersistentDataContainer()
                .get(Keys.PET_ID_KEY, PersistentDataType.STRING);
        PetDefinition def = petId != null ? definitions.get(petId) : null;
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

    private void loadDefinitions() {
        YamlLoader<PetDefinition> loader = new YamlLoader<>(plugin, "pets", "Pet");
        loader.load(this::parseDefinition, def -> definitions.put(def.getId(), def));
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

            return LoadResult.success(new PetDefinition(id, name, entityType, baseStats, statsPerLevel, abilities, milestones));
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
