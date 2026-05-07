package org.nakii.valmora.module.alchemy.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.alchemy.AlchemyManager;
import org.nakii.valmora.module.alchemy.effect.ActiveEffect;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.List;
import java.util.Optional;

public class AlchemyVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "alchemy";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;

        Optional<Player> maybePlayer = context.getPlayerCaster();
        if (maybePlayer.isEmpty()) return null;

        Player player = maybePlayer.get();
        AlchemyManager manager = ValmoraAPI.getInstance().getAlchemyManager();
        if (manager == null) return null;

        if (path[0].equalsIgnoreCase("effects")) {
            if (path.length < 2) return null;
            List<ActiveEffect> effects = manager.getActiveEffects(player.getUniqueId());
            effects = effects.stream().filter(e -> !e.isExpired()).toList();

            return switch (path[1].toLowerCase()) {
                case "count" -> effects.size();
                case "list" -> buildEffectList(effects, manager);
                default -> null;
            };
        }

        return null;
    }

    private String buildEffectList(List<ActiveEffect> effects, AlchemyManager manager) {
        JsonArray array = new JsonArray();
        for (ActiveEffect ae : effects) {
            Optional<AlchemyEffect> defOpt = manager.getEffect(ae.effectId());
            JsonObject obj = new JsonObject();
            obj.addProperty("id", ae.effectId());
            obj.addProperty("level", ae.level());
            obj.addProperty("remaining", ae.remainingSeconds());

            if (defOpt.isPresent()) {
                AlchemyEffect def = defOpt.get();
                obj.addProperty("name", def.getName() + " " + toRoman(ae.level()));
                obj.addProperty("type", def.getType().name());
                obj.addProperty("material", def.getType().name().equals("BUFF") ? "LIME_DYE" : "RED_DYE");
                obj.addProperty("rarity", def.getRarity());
                JsonArray statsArr = new JsonArray();
                var registry = ValmoraAPI.getInstance().getStatRegistry();
                for (String statId : def.getStats().keySet()) {
                    double val = def.getStatValue(statId, ae.level());
                    JsonObject statObj = new JsonObject();
                    statObj.addProperty("name", registry.get(statId)
                            .map(org.nakii.valmora.module.stat.StatDefinition::getDisplayName)
                            .orElse(statId));
                    statObj.addProperty("value", (int) val);
                    statsArr.add(statObj);
                }
                obj.add("stats", statsArr);
            } else {
                obj.addProperty("name", ae.effectId() + " " + toRoman(ae.level()));
                obj.addProperty("type", "UNKNOWN");
                obj.addProperty("material", "GLASS_BOTTLE");
                obj.addProperty("rarity", "COMMON");
                obj.add("stats", new JsonArray());
            }

            array.add(obj);
        }
        return new com.google.gson.Gson().toJson(array);
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(level);
        };
    }
}
