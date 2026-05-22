package org.nakii.valmora.module.zone;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ZoneLoader {

    private final Valmora plugin;
    private final ZoneRegistry registry;

    public ZoneLoader(Valmora plugin, ZoneRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void loadZones() {
        registry.clear();
        new YamlLoader<ZoneDefinition>(plugin, "zones", "Zones")
                .load(this::parse, def -> registry.register(def.getId(), def));
    }

    private LoadResult<ZoneDefinition, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            String displayName = sec.getString("display-name", "<green>" + id);
            String world = sec.getString("world", "world");
            String fishingTable = sec.getString("fishing-loot-table", null);

            ZoneFlags flags;
            ConfigurationSection allowSec = sec.getConfigurationSection("allow");
            if (allowSec != null) {
                flags = new ZoneFlags(
                    allowSec.getBoolean("pvp", false),
                    allowSec.getBoolean("natural-mob-spawning", false),
                    allowSec.getBoolean("block-breaking", false),
                    allowSec.getBoolean("block-placing", false),
                    allowSec.getBoolean("hunger", true),
                    allowSec.getBoolean("entry", true),
                    allowSec.getBoolean("teleportation", true),
                    allowSec.getBoolean("leaf-decay", true)
                );
            } else {
                flags = new ZoneFlags(sec.getBoolean("pvp-enabled", false), false, false, false, true, true, true, true);
            }

            List<Integer> minList = sec.getIntegerList("min");
            List<Integer> maxList = sec.getIntegerList("max");
            if (minList.size() < 3 || maxList.size() < 3)
                return LoadResult.failure("[" + path + "] Zone '" + id + "' missing min/max bounds.");

            int minX = minList.get(0), minY = minList.get(1), minZ = minList.get(2);
            int maxX = maxList.get(0), maxY = maxList.get(1), maxZ = maxList.get(2);

            // Optional extra bounding boxes
            List<int[]> extraBoxes = new ArrayList<>();
            List<?> extraBoxesList = sec.getList("extra-boxes");
            if (extraBoxesList != null) {
                for (Object entry : extraBoxesList) {
                    if (!(entry instanceof Map<?, ?> m)) continue;
                    Object minObj = m.get("min");
                    Object maxObj = m.get("max");
                    if (!(minObj instanceof List<?> minL) || !(maxObj instanceof List<?> maxL)) continue;
                    if (minL.size() < 3 || maxL.size() < 3) continue;
                    try {
                        int bMinX = ((Number) minL.get(0)).intValue();
                        int bMinY = ((Number) minL.get(1)).intValue();
                        int bMinZ = ((Number) minL.get(2)).intValue();
                        int bMaxX = ((Number) maxL.get(0)).intValue();
                        int bMaxY = ((Number) maxL.get(1)).intValue();
                        int bMaxZ = ((Number) maxL.get(2)).intValue();
                        extraBoxes.add(new int[]{bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ});
                    } catch (ClassCastException ignored) {}
                }
            }

            List<ZoneMobSpawner> spawners = new ArrayList<>();
            ConfigurationSection spawnersSec = sec.getConfigurationSection("mob-spawners");
            if (spawnersSec != null) {
                for (String key : spawnersSec.getKeys(false)) {
                    ConfigurationSection s = spawnersSec.getConfigurationSection(key);
                    if (s == null) continue;
                    spawners.add(new ZoneMobSpawner(
                            key,
                            s.getString("mob", "zombie"),
                            s.getInt("x", 0), s.getInt("y", 64), s.getInt("z", 0),
                            s.getInt("spawn-interval", 200),
                            s.getInt("max-alive", 5),
                            s.getDouble("radius", 20.0),
                            s.getInt("spawn-radius", 3)
                    ));
                }
            }

            Map<Material, ZoneResourceConfig> resourceBlocks = new EnumMap<>(Material.class);
            ConfigurationSection rbSec = sec.getConfigurationSection("resource-blocks");
            if (rbSec != null) {
                for (String matName : rbSec.getKeys(false)) {
                    Material mat = Material.matchMaterial(matName.toUpperCase());
                    if (mat == null) { plugin.getLogger().warning("[Zones] Unknown material: " + matName); continue; }
                    ConfigurationSection rbEntry = rbSec.getConfigurationSection(matName);
                    if (rbEntry == null) continue;
                    int regenDelay = rbEntry.getInt("regen-delay", 600);

                    List<ResourceStage> stages = new ArrayList<>();
                    List<?> stagesList = rbEntry.getList("stages");

                    if (stagesList != null) {
                        for (Object stageObj : stagesList) {
                            if (!(stageObj instanceof Map<?, ?> stageMap)) continue;
                            List<ZoneResourceDrop> drops = new ArrayList<>();
                            Object dropsObj = stageMap.get("drops");
                            if (dropsObj instanceof List<?> dropsList) {
                                for (Object dropObj : dropsList) {
                                    if (dropObj instanceof Map<?, ?> dropMap) {
                                        drops.add(new ZoneResourceDrop(
                                            str(dropMap, "item", "COBBLESTONE"),
                                            intVal(dropMap, "min", 1),
                                            intVal(dropMap, "max", 1),
                                            doubleVal(dropMap, "chance", 1.0)
                                        ));
                                    }
                                }
                            }
                            String nextStr = str(stageMap, "next", null);
                            Material nextMat = nextStr != null ? Material.matchMaterial(nextStr.toUpperCase()) : null;
                            stages.add(new ResourceStage(drops, nextMat));
                        }
                    } else {
                        // Legacy flat drops format — wrap as single stage, block goes to AIR then regenerates
                        List<ZoneResourceDrop> drops = new ArrayList<>();
                        for (Map<?, ?> dropMap : rbEntry.getMapList("drops")) {
                            drops.add(new ZoneResourceDrop(
                                str(dropMap, "item", "COBBLESTONE"),
                                intVal(dropMap, "min", 1),
                                intVal(dropMap, "max", 1),
                                doubleVal(dropMap, "chance", 1.0)
                            ));
                        }
                        stages.add(new ResourceStage(drops, null));
                    }

                    resourceBlocks.put(mat, new ZoneResourceConfig(regenDelay, stages));
                }
            }

            List<String> enterActions = sec.getStringList("enter-actions");
            List<String> exitActions = sec.getStringList("exit-actions");

            return LoadResult.success(new ZoneDefinition(
                    id, displayName, world,
                    minX, minY, minZ, maxX, maxY, maxZ,
                    extraBoxes, flags, fishingTable, spawners, resourceBlocks,
                    enterActions, exitActions
            ));
        } catch (Exception e) {
            return LoadResult.failure("[" + path + "] Error parsing zone '" + id + "': " + e.getMessage());
        }
    }

    private String str(Map<?, ?> m, String key, String def) { Object v = m.get(key); return v != null ? v.toString() : def; }
    private int intVal(Map<?, ?> m, String key, int def) { Object v = m.get(key); return v instanceof Number n ? n.intValue() : def; }
    private double doubleVal(Map<?, ?> m, String key, double def) { Object v = m.get(key); return v instanceof Number n ? n.doubleValue() : def; }
}
