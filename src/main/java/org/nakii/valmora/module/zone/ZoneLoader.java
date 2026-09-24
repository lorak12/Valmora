package org.nakii.valmora.module.zone;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;
import org.nakii.valmora.infrastructure.config.refs.Kinds;
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
        new YamlLoader<ZoneDefinition>(plugin, "zones", "Zones").kind(Kinds.ZONE)
                .load(this::parse, def -> registry.register(def.getId(), def));
    }

    private LoadResult<ZoneDefinition, String> parse(String id, ConfigurationSection sec, String path) {
        try {
            ConfigReader reader = ConfigReader.of(sec).knownKeys(KNOWN_KEYS);
            ConfigReader allowReader = reader.section("allow");
            if (allowReader != null) allowReader.knownKeys(ALLOW_KEYS);
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
                    allowSec.getBoolean("leaf-decay", true),
                    allowSec.isSet("keep-inventory-on-death") ? allowSec.getBoolean("keep-inventory-on-death") : null,
                    allowSec.isSet("keep-experience-on-death") ? allowSec.getBoolean("keep-experience-on-death") : null,
                    allowSec.getBoolean("sleeping", true),
                    allowSec.getBoolean("natural-block-changes", true)
                );
            } else {
                flags = new ZoneFlags(sec.getBoolean("pvp-enabled", false), false, false, false, true, true, true, true, null, null, true, true);
            }

            List<Integer> minList = sec.getIntegerList("min");
            List<Integer> maxList = sec.getIntegerList("max");
            if (minList.size() < 3 || maxList.size() < 3)
                return LoadResult.failure("[" + path + "] Zone '" + id + "' missing min/max bounds (each a list of 3 numbers: [x, y, z]).");

            int minX = minList.get(0), minY = minList.get(1), minZ = minList.get(2);
            int maxX = maxList.get(0), maxY = maxList.get(1), maxZ = maxList.get(2);
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                // contains() tests min <= v <= max per axis, so a swapped corner makes the zone empty.
                reader.warn("min", "min " + minList + " is greater than max " + maxList
                        + " on some axis — swapped to make a valid box");
                int t;
                if (minX > maxX) { t = minX; minX = maxX; maxX = t; }
                if (minY > maxY) { t = minY; minY = maxY; maxY = t; }
                if (minZ > maxZ) { t = minZ; minZ = maxZ; maxZ = t; }
            }

            // Optional extra bounding boxes
            List<int[]> extraBoxes = new ArrayList<>();
            List<?> extraBoxesList = sec.getList("extra-boxes");
            if (extraBoxesList != null) {
                for (Object entry : extraBoxesList) {
                    if (!(entry instanceof Map<?, ?> m)) {
                        reader.warn("extra-boxes", "every entry needs min: and max: — one was skipped");
                        continue;
                    }
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
                    ConfigReader spawnerReader = reader.section("mob-spawners").section(key)
                            .knownKeys("mob", "x", "y", "z", "spawn-interval", "max-alive", "radius", "spawn-radius");
                    spawnerReader.ref("mob", Kinds.MOB, s.getString("mob", "zombie"));
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
                    if (mat == null) {
                        reader.section("resource-blocks").warn(matName, "unknown material '" + matName + "' — ignored",
                                ConfigReader.materialHint(matName));
                        continue;
                    }
                    ConfigurationSection rbEntry = rbSec.getConfigurationSection(matName);
                    if (rbEntry == null) continue;
                    int regenDelay = rbEntry.getInt("regen-delay", 600);
                    double requiredPower = rbEntry.getDouble("required-power", 0.0);

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
                                        reader.ref("resource-blocks." + matName + ".stages", Kinds.ITEM_OR_MATERIAL, str(dropMap, "item", "COBBLESTONE"));
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
                            if (nextStr != null && nextMat == null) {
                                reader.section("resource-blocks").warn(matName, "unknown next: material '" + nextStr + "'",
                                        ConfigReader.materialHint(nextStr));
                            }
                            stages.add(new ResourceStage(drops, nextMat));
                        }
                    } else {
                        // Legacy flat drops format — wrap as single stage, block goes to AIR then regenerates
                        List<ZoneResourceDrop> drops = new ArrayList<>();
                        for (Map<?, ?> dropMap : rbEntry.getMapList("drops")) {
                            reader.ref("resource-blocks." + matName + ".drops", Kinds.ITEM_OR_MATERIAL, str(dropMap, "item", "COBBLESTONE"));
                            drops.add(new ZoneResourceDrop(
                                str(dropMap, "item", "COBBLESTONE"),
                                intVal(dropMap, "min", 1),
                                intVal(dropMap, "max", 1),
                                doubleVal(dropMap, "chance", 1.0)
                            ));
                        }
                        stages.add(new ResourceStage(drops, null));
                    }

                    resourceBlocks.put(mat, new ZoneResourceConfig(regenDelay, stages, requiredPower));
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

    private static final java.util.List<String> KNOWN_KEYS = java.util.List.of(
            "display-name", "world", "fishing-loot-table", "allow", "pvp-enabled", "min", "max", "extra-boxes",
            "mob-spawners", "resource-blocks", "enter-actions", "exit-actions");

    private static final java.util.List<String> ALLOW_KEYS = java.util.List.of(
            "pvp", "natural-mob-spawning", "block-breaking", "block-placing", "hunger", "entry", "teleportation",
            "leaf-decay", "keep-inventory-on-death", "keep-experience-on-death", "sleeping", "natural-block-changes");

    private String str(Map<?, ?> m, String key, String def) { Object v = m.get(key); return v != null ? v.toString() : def; }
    private int intVal(Map<?, ?> m, String key, int def) { Object v = m.get(key); return v instanceof Number n ? n.intValue() : def; }
    private double doubleVal(Map<?, ?> m, String key, double def) { Object v = m.get(key); return v instanceof Number n ? n.doubleValue() : def; }
}
