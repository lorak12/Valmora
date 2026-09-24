package org.nakii.valmora.module.warp;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.ArrayList;
import java.util.List;

public class WarpLoader {

    private final Valmora plugin;
    private final Registry<WarpDefinition> registry;

    public WarpLoader(Valmora plugin, Registry<WarpDefinition> registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void load() {
        registry.clear();
        new YamlLoader<WarpDefinition>(plugin, "warps", "Warps").kind(org.nakii.valmora.infrastructure.config.refs.Kinds.WARP)
                .load(this::parse, def -> {
                    if (registry.get(def.getId()).isPresent()) {
                        plugin.getLogger().warning("[Warps] Duplicate warp id '" + def.getId()
                                + "' — overwriting the previous definition.");
                    }
                    registry.register(def.getId(), def);
                });
    }

    private LoadResult<WarpDefinition, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            List<int[]> pads = new ArrayList<>();
            for (var padSec : sec.getMapList("pad-locations")) {
                int px = padSec.containsKey("x") ? ((Number) padSec.get("x")).intValue() : 0;
                int py = padSec.containsKey("y") ? ((Number) padSec.get("y")).intValue() : 0;
                int pz = padSec.containsKey("z") ? ((Number) padSec.get("z")).intValue() : 0;
                pads.add(new int[]{px, py, pz});
            }
            // HC-153: `world`/`y` silently defaulting to "world"/64 is a real footgun on a
            // void world or a build well above/below y=64 — warn (not fail) so an author notices
            // before a warp teleports players into the void.
            if (!sec.contains("world")) {
                plugin.getLogger().warning("[" + path + "] Warp '" + id + "' has no 'world:' — defaulting to \"world\", which is almost certainly wrong for this server.");
            }
            if (!sec.contains("y")) {
                plugin.getLogger().warning("[" + path + "] Warp '" + id + "' has no 'y:' — defaulting to 64, which may not be safe on this world.");
            }
            return LoadResult.success(new WarpDefinition(
                    id,
                    sec.getString("display-name", id),
                    sec.getString("world", plugin.getConfig().getString("warps.defaults.world", "world")),
                    sec.getDouble("x", 0), sec.getDouble("y", plugin.getConfig().getDouble("warps.defaults.y", 64)), sec.getDouble("z", 0),
                    (float) sec.getDouble("yaw", 0), (float) sec.getDouble("pitch", 0),
                    sec.getString("unlock-condition", plugin.getConfig().getString("warps.defaults.unlock-condition", "always")),
                    pads,
                    sec.getDouble("cost", plugin.getConfig().getDouble("warps.defaults.cost", 0.0)),
                    sec.getInt("cooldown-seconds", plugin.getConfig().getInt("warps.defaults.cooldown", 0)),
                    sec.getInt("warmup-seconds", plugin.getConfig().getInt("warps.defaults.warmup", 0)),
                    sec.contains("permission") ? sec.getString("permission") : null
            ));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing warp '" + id + "': " + e.getMessage());
        }
    }
}
