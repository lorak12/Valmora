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
        new YamlLoader<WarpDefinition>(plugin, "warps", "Warps")
                .load(this::parse, def -> registry.register(def.getId(), def));
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
            return LoadResult.success(new WarpDefinition(
                    id,
                    sec.getString("display-name", id),
                    sec.getString("world", "world"),
                    sec.getDouble("x", 0), sec.getDouble("y", 64), sec.getDouble("z", 0),
                    (float) sec.getDouble("yaw", 0), (float) sec.getDouble("pitch", 0),
                    sec.getString("unlock-condition", "always"),
                    pads
            ));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing warp '" + id + "': " + e.getMessage());
        }
    }
}
