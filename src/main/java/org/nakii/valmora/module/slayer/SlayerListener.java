package org.nakii.valmora.module.slayer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Illager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

public class SlayerListener implements Listener {

    private static final String VAR_ACTIVE = "slayer.active";
    private static final String VAR_KILLS = "slayer.kills";
    private static final String VAR_BOSS = "slayer.boss";

    private final SlayerModule module;
    private final Valmora plugin;

    public SlayerListener(SlayerModule module, Valmora plugin) {
        this.module = module;
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Check if this is a slayer boss
        String taskKey = entity.getPersistentDataContainer().get(Keys.SLAYER_BOSS_KEY, PersistentDataType.STRING);
        if (taskKey != null && entity.getKiller() != null) {
            handleBossDeath(entity.getKiller(), taskKey);
            return;
        }

        // Normal mob kill — progress active slayer task
        if (entity.getKiller() == null) return;
        Player killer = entity.getKiller();
        ValmoraProfile profile = getProfile(killer);
        if (profile == null) return;

        String active = (String) profile.getVariables().get(VAR_ACTIVE);
        if (active == null || active.isBlank()) return;

        String[] parts = active.split(":", 2);
        if (parts.length != 2) return;
        SlayerDefinition def = module.getDefinition(parts[0]);
        if (def == null) return;
        int tierNum;
        try { tierNum = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { return; }
        SlayerTier tier = def.getTier(tierNum);
        if (tier == null) return;

        if (!matchesCategory(entity, tier.getTargetCategory())) return;

        int kills = (int) profile.getVariables().getOrDefault(VAR_KILLS, 0);
        kills++;
        profile.getVariables().put(VAR_KILLS, kills);

        killer.sendMessage(Formatter.format("<yellow>[Slayer] Kill " + kills + "/" + tier.getKillsRequired()));

        if (kills >= tier.getKillsRequired() && profile.getVariables().get(VAR_BOSS) == null) {
            spawnBoss(killer, def, tier, active);
        }
    }

    private void handleBossDeath(Player killer, String taskKey) {
        ValmoraProfile profile = getProfile(killer);
        if (profile == null) return;

        String active = (String) profile.getVariables().get(VAR_ACTIVE);
        if (!taskKey.equals(active)) return;

        String[] parts = taskKey.split(":", 2);
        if (parts.length != 2) return;
        SlayerDefinition def = module.getDefinition(parts[0]);
        if (def == null) return;
        int tierNum;
        try { tierNum = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { return; }
        SlayerTier tier = def.getTier(tierNum);
        if (tier == null) return;

        profile.getVariables().remove(VAR_ACTIVE);
        profile.getVariables().remove(VAR_KILLS);
        profile.getVariables().remove(VAR_BOSS);

        killer.sendMessage(Formatter.format(
                "<gold><bold>[Slayer] <green>Quest complete! You defeated the " + def.getName() + " boss!"));

        if (!tier.getCompletionEvents().isEmpty()) {
            var ctx = new SimpleExecutionContext(killer, killer.getLocation(), new YamlConfiguration());
            plugin.getScriptModule().getEventParser().parseList(tier.getCompletionEvents()).execute(ctx);
        }
    }

    private void spawnBoss(Player player, SlayerDefinition def, SlayerTier tier, String taskKey) {
        if (tier.getBossMob().isBlank()) return;
        var mobDef = plugin.getMobManager().getMobDefinition(tier.getBossMob());
        if (mobDef == null) {
            plugin.getLogger().warning("Slayer boss mob not found: " + tier.getBossMob());
            return;
        }
        var entity = plugin.getMobManager().spawnMob(mobDef, player.getLocation().add(0, 0, 2));
        if (entity != null) {
            entity.getPersistentDataContainer().set(Keys.SLAYER_BOSS_KEY, PersistentDataType.STRING, taskKey);
            ValmoraProfile profile = getProfile(player);
            if (profile != null) profile.getVariables().put(VAR_BOSS, entity.getUniqueId().toString());
            player.sendMessage(Formatter.format(
                    "<red><bold>[Slayer] The " + def.getName() + " boss has appeared! Defeat it!"));
        }
    }

    private boolean matchesCategory(Entity entity, String category) {
        return switch (category.toUpperCase()) {
            case "MONSTER" -> entity instanceof Monster;
            case "ILLAGER" -> entity instanceof Illager;
            case "ANIMAL" -> entity instanceof Animals;
            case "ALL", "ANY" -> true;
            case "UNDEAD" -> {
                String typeName = entity.getType().name();
                yield typeName.contains("ZOMBIE") || typeName.contains("SKELETON")
                        || typeName.contains("PHANTOM") || typeName.contains("DROWNED")
                        || typeName.contains("WITHER") || typeName.contains("STRAY")
                        || typeName.contains("HUSK");
            }
            default -> {
                String mobId = entity.getPersistentDataContainer()
                        .get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
                if (mobId != null && mobId.equalsIgnoreCase(category)) yield true;
                yield entity.getType().name().equalsIgnoreCase(category);
            }
        };
    }

    private ValmoraProfile getProfile(Player player) {
        ValmoraPlayer session = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        return session != null ? session.getActiveProfile() : null;
    }
}
