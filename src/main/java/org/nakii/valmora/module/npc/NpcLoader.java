package org.nakii.valmora.module.npc;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.npc.dialogue.DialogueDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NpcLoader {

    private final Valmora plugin;
    private final Registry<NpcDefinition> npcRegistry;
    private final Registry<DialogueDefinition> dialogueRegistry;

    public NpcLoader(Valmora plugin, Registry<NpcDefinition> npcRegistry, Registry<DialogueDefinition> dialogueRegistry) {
        this.plugin = plugin;
        this.npcRegistry = npcRegistry;
        this.dialogueRegistry = dialogueRegistry;
    }

    public void load() {
        npcRegistry.clear();
        dialogueRegistry.clear();
        new YamlLoader<NpcDefinition>(plugin, "npcs", "NPCs")
                .load(this::parseNpc, def -> npcRegistry.register(def.getId(), def));
    }

    private LoadResult<NpcDefinition, String> parseNpc(String id, ConfigurationSection sec, String path) {
        // Skip non-NPC top-level sections (npc_conversations now lives in quest packages)
        if (id.equalsIgnoreCase("npc_conversations")) return LoadResult.failure("skip");
        try {
            String displayName = sec.getString("display-name", "<white>" + id);
            EntityType entityType;
            try { entityType = EntityType.valueOf(sec.getString("entity-type", "VILLAGER").toUpperCase()); }
            catch (IllegalArgumentException e) { entityType = EntityType.VILLAGER; }
            String boundConv = sec.getString("conversation", null);

            String skinTexture   = sec.getString("skin-texture", null);
            String skinSignature = sec.getString("skin-signature", null);
            boolean lookAtPlayer = sec.getBoolean("look-at-player", false);
            boolean showName     = sec.getBoolean("show-name", true);
            List<HologramDefinition> holograms = parseHolograms(sec);

            return LoadResult.success(new NpcDefinition(
                    id, displayName, entityType,
                    sec.getString("world", "world"),
                    sec.getDouble("x", 0), sec.getDouble("y", 64), sec.getDouble("z", 0),
                    (float) sec.getDouble("yaw", 0),
                    sec.getStringList("on-right-click"),
                    sec.getStringList("on-left-click"),
                    boundConv,
                    path,  // e.g. "npcs/hub.yml"
                    skinTexture,
                    skinSignature,
                    lookAtPlayer,
                    showName,
                    holograms
            ));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing NPC '" + id + "': " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<HologramDefinition> parseHolograms(ConfigurationSection sec) {
        List<Map<?, ?>> raw = sec.getMapList("holograms");
        if (raw == null || raw.isEmpty()) return List.of();
        List<HologramDefinition> result = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            try {
                String name = String.valueOf(entry.get("name"));
                String text = entry.containsKey("text") ? String.valueOf(entry.get("text")) : "";
                Map<?, ?> vec = entry.containsKey("vector") ? (Map<?, ?>) entry.get("vector") : Map.of();
                double ox = toDouble(vec.get("x"));
                double oy = toDouble(vec.get("y"));
                double oz = toDouble(vec.get("z"));
                List<String> conditions = entry.containsKey("conditions")
                        ? (List<String>) entry.get("conditions") : List.of();
                Object rawInterval = entry.containsKey("check_interval") ? entry.get("check_interval") : 60;
                int interval = toInt(rawInterval);
                result.add(new HologramDefinition(name, text, ox, oy, oz, conditions, interval));
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) { try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {} }
        return 0.0;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {} }
        return 60;
    }
}
