package org.nakii.valmora.module.script.variable.providers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.accessory.AccessoryModule;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.variable.VariableProvider;
import org.nakii.valmora.module.skill.SkillDefinition;
import org.nakii.valmora.module.stat.StatDefinition;
import org.nakii.valmora.module.stat.StatRegistry;
import org.nakii.valmora.module.stat.SystemStats;

import java.util.Optional;

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
        ValmoraPlayer vp = api.getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return null;
        ValmoraProfile profile = vp.getActiveProfile();
        if (profile == null) return null;

        if (key.equalsIgnoreCase("stat") && path.length > 1) {
            String statName = path[1];

            if (statName.equalsIgnoreCase("list")) {
                SystemStats sys = api.getSystemStats();
                StatRegistry registry = api.getStatRegistry();
                JsonArray array = new JsonArray();

                for (StatDefinition def : registry.values()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", def.getId());
                    obj.addProperty("name", def.getColor() + def.getDisplayName());
                    obj.addProperty("material", def.getIcon());
                    obj.addProperty("description", def.getDescription());

                    if (def.isPool() && def.getId().equals(sys.getHealth())) {
                        double current = profile.getPlayerState().getCurrentHealth();
                        double max = profile.getStatManager().getStat(def.getId());
                        obj.addProperty("value", (int) current);
                        obj.addProperty("max", (int) max);
                        obj.addProperty("display_value", (int) current + "/" + (int) max);
                    } else if (def.isPool() && def.getId().equals(sys.getMana())) {
                        double current = profile.getPlayerState().getCurrentMana();
                        double max = profile.getStatManager().getStat(def.getId());
                        obj.addProperty("value", (int) current);
                        obj.addProperty("max", (int) max);
                        obj.addProperty("display_value", (int) current + "/" + (int) max);
                    } else {
                        double val = profile.getStatManager().getStat(def.getId());
                        obj.addProperty("value", (int) val);
                        obj.addProperty("display_value", (int) val + (def.getId().contains("chance") ? "%" : ""));
                    }
                    array.add(obj);
                }
                return new Gson().toJson(array);
            }

            // Individual stat lookup
            StatDefinition def = api.getStatRegistry().get(statName.toLowerCase()).orElse(null);
            if (def == null) return null;
            return profile.getStatManager().getStat(def.getId());
        }

        if (key.equalsIgnoreCase("skill") && path.length > 1) {
            String subKey = path[1];

            if (subKey.equalsIgnoreCase("list")) {
                JsonArray array = new JsonArray();
                for (SkillDefinition skill : api.getSkillManager().getSkillRegistry().values()) {
                    JsonObject obj = new JsonObject();
                    double xp = profile.getSkillManager().getXp(skill.getId());
                    org.nakii.valmora.module.skill.SkillRegistry.ProgressData data =
                            api.getSkillManager().getSkillRegistry().getProgressData(skill.getXpCurve(), xp);

                    obj.addProperty("id", skill.getId());
                    obj.addProperty("name", skill.getName());
                    String unformatted = skill.getName().toLowerCase()
                            .replaceAll("[^a-z ]", "").trim().replaceAll("\\s+", " ");
                    obj.addProperty("unformatted", unformatted);
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

            Optional<SkillDefinition> maybeSkill = api.getSkillManager().getSkillRegistry().get(subKey);
            if (maybeSkill.isEmpty()) return null;

            double xp = profile.getSkillManager().getXp(subKey);
            org.nakii.valmora.module.skill.SkillRegistry.ProgressData data =
                    api.getSkillManager().getSkillRegistry().getProgressData(maybeSkill.get().getXpCurve(), xp);

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

        SystemStats sys = api.getSystemStats();
        if (key.equalsIgnoreCase("hp")) return profile.getPlayerState().getCurrentHealth();
        if (key.equalsIgnoreCase("max_hp")) return profile.getStatManager().getStat(sys.getHealth());
        if (key.equalsIgnoreCase("health_percent"))
            return (int) ((profile.getPlayerState().getCurrentHealth() / profile.getStatManager().getStat(sys.getHealth())) * 100);
        if (key.equalsIgnoreCase("missing_hp_percent"))
            return 100 - (int) ((profile.getPlayerState().getCurrentHealth() / profile.getStatManager().getStat(sys.getHealth())) * 100);
        if (key.equalsIgnoreCase("mana")) return profile.getPlayerState().getCurrentMana();
        if (key.equalsIgnoreCase("max_mana")) return profile.getStatManager().getStat(sys.getMana());

        if (key.equalsIgnoreCase("profile")) return profile.getName();

        // Most recent damage this player dealt (set by the combat pipeline) — used by ON_HIT
        // abilities that scale off the triggering hit.
        if (key.equalsIgnoreCase("last_damage"))
            return org.nakii.valmora.module.item.CombatTracker.getLastDamageDealt(player.getUniqueId());

        // Base weapon damage stat, used by greed-style abilities ("100x the weapon damage").
        if (key.equalsIgnoreCase("weapon_damage")) {
            StatDefinition dmg = api.getStatRegistry().get("damage").orElse(null);
            return dmg == null ? 0.0 : profile.getStatManager().getStat(dmg.getId());
        }

        if (key.equalsIgnoreCase("var") && path.length > 1) {
            return profile.getVariables().get(path[1]);
        }

        if (key.equalsIgnoreCase("accessories")) {
            return resolveAccessories(path, api, profile);
        }

        return null;
    }

    /**
     * $player.accessories.<sub>$ — everything script content is likely to want to know
     * about a profile's accessory bag, without needing to reach into ItemStack arrays.
     */
    private Object resolveAccessories(String[] path, ValmoraAPI api, ValmoraProfile profile) {
        AccessoryModule accessoryModule = api.getAccessoryModule();
        if (accessoryModule == null) return null;

        int unlocked = accessoryModule.getUnlockedSlots(profile);
        int cap = accessoryModule.getMaxSlotsCap();

        ItemStack[] items = profile.getAccessoryItems();
        int used = 0;
        for (int i = 0; i < Math.min(unlocked, items.length); i++) {
            ItemStack item = items[i];
            if (item != null && !item.getType().isAir()) used++;
        }
        int empty = unlocked - used;
        int remainingToCap = cap - unlocked;
        int totalPages = accessoryModule.computeLayout(profile, 0).totalPages();
        int percentFull = unlocked == 0 ? 0 : (int) Math.round((used / (double) unlocked) * 100);

        if (path.length == 1) return unlocked;

        String subKey = path[1];
        if (subKey.equalsIgnoreCase("unlocked") || subKey.equalsIgnoreCase("slots")) return unlocked;
        if (subKey.equalsIgnoreCase("cap") || subKey.equalsIgnoreCase("max")) return cap;
        if (subKey.equalsIgnoreCase("used") || subKey.equalsIgnoreCase("count") || subKey.equalsIgnoreCase("filled")) return used;
        if (subKey.equalsIgnoreCase("empty") || subKey.equalsIgnoreCase("free")) return empty;
        if (subKey.equalsIgnoreCase("remaining_to_cap") || subKey.equalsIgnoreCase("lockable")) return remainingToCap;
        if (subKey.equalsIgnoreCase("pages") || subKey.equalsIgnoreCase("total_pages")) return totalPages;
        if (subKey.equalsIgnoreCase("percent_full") || subKey.equalsIgnoreCase("percent")) return percentFull;
        if (subKey.equalsIgnoreCase("is_full")) return used >= unlocked;
        if (subKey.equalsIgnoreCase("is_maxed")) return unlocked >= cap;

        return null;
    }
}
