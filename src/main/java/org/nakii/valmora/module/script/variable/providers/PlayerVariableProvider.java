package org.nakii.valmora.module.script.variable.providers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.variable.VariableProvider;
import org.nakii.valmora.module.skill.SkillDefinition;
import org.nakii.valmora.module.stat.Stat;

import java.util.Optional;

/**
 * Handles player-related variables: $player.name$, $player.stat.HEALTH$, etc.
 */
public class PlayerVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "player";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        Optional<Player> maybePlayer = context.getPlayerCaster();
        if (maybePlayer.isEmpty()) return null;

        Player player = maybePlayer.get();
        if (path.length == 0) return null;

        String key = path[0];
        if (key.equalsIgnoreCase("name")) return player.getName();
        if (key.equalsIgnoreCase("world")) return player.getWorld().getName();
        if (key.equalsIgnoreCase("ping")) return player.getPing();
        if (key.equalsIgnoreCase("biome")) return player.getLocation().getBlock().getBiome().name();

        ValmoraAPI api = ValmoraAPI.getInstance();
        ValmoraProfile profile = api.getPlayerManager().getSession(player.getUniqueId()).getActiveProfile();
        if (profile == null) return null;

        if (key.equalsIgnoreCase("stat") && path.length > 1) {
            String statName = path[1];
            if (statName.equalsIgnoreCase("list")){
                JsonArray array = new JsonArray();
                Stat[] orderedStats = {
                        Stat.HEALTH, Stat.MANA, Stat.DAMAGE, Stat.DEFENSE,
                        Stat.SPEED, Stat.STRENGTH, Stat.CRIT_CHANCE,
                        Stat.CRIT_DAMAGE, Stat.HEALTH_REGEN, Stat.MANA_REGEN, Stat.LUCK
                };

                for (Stat stat : orderedStats) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("name", getFormattedName(stat));
                    obj.addProperty("material", getStatMaterial(stat));
                    obj.addProperty("description", getStatDescription(stat));

                    if (stat == Stat.HEALTH) {
                        double current = profile.getPlayerState().getCurrentHealth();
                        double max = profile.getStatManager().getStat(Stat.HEALTH);
                        obj.addProperty("value", (int) current);
                        obj.addProperty("max", (int) max);
                        obj.addProperty("display_value", (int) current + "/" + (int) max);
                    } else if (stat == Stat.MANA) {
                        double current = profile.getPlayerState().getCurrentMana();
                        double max = profile.getStatManager().getStat(Stat.MANA);
                        obj.addProperty("value", (int) current);
                        obj.addProperty("max", (int) max);
                        obj.addProperty("display_value", (int) current + "/" + (int) max);
                    } else {
                        double val = profile.getStatManager().getStat(stat);
                        obj.addProperty("value", (int) val);
                        obj.addProperty("display_value", (int) val + (stat.name().contains("CHANCE") ? "%" : ""));
                    }
                    array.add(obj);
                }
                return new Gson().toJson(array);
            }
            try {
                Stat stat = Stat.valueOf(statName.toUpperCase());
                return profile.getStatManager().getStat(stat);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        if (key.equalsIgnoreCase("skill") && path.length > 1) {
            String subKey = path[1];
            
            // JSON Array generation for Pagination GUI
            if (subKey.equalsIgnoreCase("list")) {
                JsonArray array = new JsonArray();
                for (SkillDefinition skill : ValmoraAPI.getInstance().getSkillManager().getSkillRegistry().values()) {
                    JsonObject obj = new JsonObject();
                    double xp = profile.getSkillManager().getXp(skill.getId());
                    org.nakii.valmora.module.skill.SkillRegistry.ProgressData data = 
                        ValmoraAPI.getInstance().getSkillManager().getSkillRegistry().getProgressData(skill.getXpCurve(), xp);
                    
                    obj.addProperty("id", skill.getId());
                    obj.addProperty("name", skill.getName());
                    obj.addProperty("description", skill.getDescription());
                    obj.addProperty("material", skill.getMaterial().name());
                    obj.addProperty("level", data.currentLevel());
                    obj.addProperty("next_level", data.nextLevel());
                    obj.addProperty("xp", (int) xp);
                    obj.addProperty("xp_in_level", data.xpInLevel());
                    obj.addProperty("xp_required", data.xpRequired());
                    obj.addProperty("progress", data.percent());
                    obj.addProperty("max_level", skill.getMaxLevel());
                    array.add(obj);
                }
                return new Gson().toJson(array);
            }
            
            // Fetch individual skill info
            Optional<SkillDefinition> maybeSkill = ValmoraAPI.getInstance().getSkillManager().getSkillRegistry().get(subKey);
            if (maybeSkill.isEmpty()) return null;
            
            double xp = profile.getSkillManager().getXp(subKey);
            org.nakii.valmora.module.skill.SkillRegistry.ProgressData data = 
                ValmoraAPI.getInstance().getSkillManager().getSkillRegistry().getProgressData(maybeSkill.get().getXpCurve(), xp);

            if (path.length > 2) {
                String trait = path[2].toLowerCase();
                if (trait.equals("xp")) return (int) xp;
                if (trait.equals("level")) return data.currentLevel();
                if (trait.equals("next_level")) return data.nextLevel();
                if (trait.equals("progress")) return data.percent();
                if (trait.equals("xp_in_level")) return data.xpInLevel();
                if (trait.equals("xp_required")) return data.xpRequired();
            }

            return data.currentLevel();
        }

        if (key.equalsIgnoreCase("hp")) return profile.getPlayerState().getCurrentHealth();
        if (key.equalsIgnoreCase("max_hp")) return profile.getStatManager().getStat(Stat.HEALTH);
        if (key.equalsIgnoreCase("health_percent")) return (int) ((profile.getPlayerState().getCurrentHealth() / profile.getStatManager().getStat(Stat.HEALTH)) * 100);
        if (key.equalsIgnoreCase("mana")) return profile.getPlayerState().getCurrentMana();
        if (key.equalsIgnoreCase("max_mana")) return profile.getStatManager().getStat(Stat.MANA);

        if (key.equalsIgnoreCase("var") && path.length > 1) {
            return profile.getVariables().get(path[1]);
        }


        return null;
    }

    private String getFormattedName(Stat stat) {
        return switch (stat) {
            case HEALTH -> "<red><bold>❤ Health</bold></red>";
            case MANA -> "<aqua><bold>✦ Mana</bold></aqua>";
            case DAMAGE -> "<yellow><bold>⚔ Damage</bold></yellow>";
            case DEFENSE -> "<green><bold>🛡 Defense</bold></green>";
            case SPEED -> "<white><bold>⚡ Speed</bold></white>";
            case STRENGTH -> "<dark_red><bold>💢 Strength</bold></dark_red>";
            case CRIT_CHANCE -> "<gold><bold>☆ Crit Chance</bold></gold>";
            case CRIT_DAMAGE -> "<gold><bold>☆ Crit Damage</bold></gold>";
            case HEALTH_REGEN -> "<light_purple><bold>♥ Health Regen</bold></light_purple>";
            case MANA_REGEN -> "<dark_aqua><bold>♻ Mana Regen</bold></dark_aqua>";
            case LUCK -> "<gold><bold>☘ Luck</bold></gold>";
            default -> stat.name();
        };
    }

    private String getStatMaterial(Stat stat) {
        return switch (stat) {
            case HEALTH -> "APPLE";
            case MANA -> "LAPIS_LAZULI";
            case DAMAGE -> "IRON_SWORD";
            case DEFENSE -> "SHIELD";
            case SPEED -> "SUGAR";
            case STRENGTH -> "BLAZE_POWDER";
            case CRIT_CHANCE -> "GOLD_NUGGET";
            case CRIT_DAMAGE -> "GLOWSTONE_DUST";
            case HEALTH_REGEN -> "GHAST_TEAR";
            case MANA_REGEN -> "PRISMARINE_CRYSTALS";
            case LUCK -> "RABBIT_FOOT";
            default -> "PAPER";
        };
    }

    private String getStatDescription(Stat stat) {
        return switch (stat) {
            case HEALTH -> "Increases your maximum health capacity.";
            case MANA -> "Resource used for casting powerful abilities.";
            case DAMAGE -> "Base attack power dealt to enemies.";
            case DEFENSE -> "Reduces damage taken from physical attacks.";
            case SPEED -> "Increases movement and sprinting speed.";
            case STRENGTH -> "Boosts the power of your melee strikes.";
            case CRIT_CHANCE -> "Chance to deal a critical hit.";
            case CRIT_DAMAGE -> "Multiplier for your critical hits.";
            case HEALTH_REGEN -> "Speed at which your health restores over time.";
            case MANA_REGEN -> "Speed at which your mana restores over time.";
            case LUCK -> "Increases the quality of loot and drops.";
            default -> "";
        };
    }
}
