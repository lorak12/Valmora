package org.nakii.valmora.module.skill;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.Bukkit;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.mob.MobCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SkillDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final Material material;
    private final int maxLevel;
    private final String xpCurve;
    
    // Internal source structures for fast lookup
    private final Map<String, Map<String, Double>> exactMatches = new HashMap<>();
    private final Map<String, List<SourcePattern>> patterns = new HashMap<>();
    private final Map<String, Map<NamespacedKey, Double>> tagMatches = new HashMap<>();
    private final Map<String, Double> defaults = new HashMap<>();

    private final CompiledEvent perLevelReward;
    private final Map<Integer, CompiledEvent> milestoneRewards;

    public SkillDefinition(String id, String name, String description, Material material, int maxLevel, String xpCurve,
                           Map<String, Map<String, Double>> rawSources, CompiledEvent perLevelReward, Map<Integer, CompiledEvent> milestoneRewards) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.material = material;
        this.maxLevel = maxLevel;
        this.xpCurve = xpCurve;
        this.perLevelReward = perLevelReward;
        this.milestoneRewards = milestoneRewards;
        
        parseSources(rawSources);
    }

    private void parseSources(Map<String, Map<String, Double>> rawSources) {
        if (rawSources == null) return;

        for (Map.Entry<String, Map<String, Double>> entry : rawSources.entrySet()) {
            String type = entry.getKey().toUpperCase();
            Map<String, Double> idMap = entry.getValue();

            Map<String, Double> exacts = new HashMap<>();
            List<SourcePattern> pats = new ArrayList<>();
            Map<NamespacedKey, Double> tags = new HashMap<>();

            for (Map.Entry<String, Double> idEntry : idMap.entrySet()) {
                String key = idEntry.getKey().toUpperCase();
                double value = idEntry.getValue();

                if (key.equals("DEFAULT")) {
                    defaults.put(type, value);
                } else if (key.startsWith("#")) {
                    String tagStr = key.substring(1).toLowerCase();
                    if (!tagStr.contains(":")) tagStr = "minecraft:" + tagStr;
                    tags.put(NamespacedKey.fromString(tagStr), value);
                } else if (key.contains("*")) {
                    pats.add(new SourcePattern(key, value));
                } else {
                    exacts.put(key, value);
                }
            }

            exactMatches.put(type, exacts);
            patterns.put(type, pats);
            tagMatches.put(type, tags);
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Material getMaterial() { return material; }
    public int getMaxLevel() { return maxLevel; }
    public String getXpCurve() { return xpCurve; }
    public CompiledEvent getPerLevelReward() { return perLevelReward; }
    public Map<Integer, CompiledEvent> getMilestoneRewards() { return milestoneRewards; }

    public Double getSourceXp(String sourceType, String identifier) {
        sourceType = sourceType.toUpperCase();
        identifier = identifier.toUpperCase();

        // 1. Exact Match
        Map<String, Double> typeExacts = exactMatches.get(sourceType);
        if (typeExacts != null && typeExacts.containsKey(identifier)) {
            return typeExacts.get(identifier);
        }

        // 2. Pattern Match
        List<SourcePattern> typePatterns = patterns.get(sourceType);
        if (typePatterns != null) {
            for (SourcePattern sp : typePatterns) {
                if (sp.matches(identifier)) {
                    return sp.xp();
                }
            }
        }

        // 3. Tag Match (Requires Material/EntityType resolution)
        Map<NamespacedKey, Double> typeTags = tagMatches.get(sourceType);
        if (typeTags != null && !typeTags.isEmpty()) {
            Double tagXp = checkTags(sourceType, identifier, typeTags);
            if (tagXp != null) return tagXp;
        }

        // 4. Default
        return defaults.get(sourceType);
    }

    private Double checkTags(String sourceType, String identifier, Map<NamespacedKey, Double> typeTags) {
        if (sourceType.equals("BLOCK_BREAK") || sourceType.equals("FISHING")) {
            Material mat = Material.matchMaterial(identifier);
            if (mat != null) {
                for (Map.Entry<NamespacedKey, Double> tagEntry : typeTags.entrySet()) {
                    Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, tagEntry.getKey(), Material.class);
                    if (tag == null) tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, tagEntry.getKey(), Material.class);
                    
                    if (tag != null && tag.isTagged(mat)) {
                        return tagEntry.getValue();
                    }
                }
            }
        } else if (sourceType.equals("MOB_KILL")) {
            // Check MobCategory (Valmora specific)
            try {
                MobCategory category = MobCategory.valueOf(identifier);
                for (Map.Entry<NamespacedKey, Double> tagEntry : typeTags.entrySet()) {
                    if (tagEntry.getKey().getKey().equalsIgnoreCase(category.name())) {
                        return tagEntry.getValue();
                    }
                }
            } catch (IllegalArgumentException ignored) {}

            // Check EntityType tags
            // Note: Bukkit 1.21 has Tag.REGISTRY_ENTITY_TYPES
            try {
                org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(identifier);
                for (Map.Entry<NamespacedKey, Double> tagEntry : typeTags.entrySet()) {
                    Tag<org.bukkit.entity.EntityType> tag = Bukkit.getTag("entity_types", tagEntry.getKey(), org.bukkit.entity.EntityType.class);
                    if (tag != null && tag.isTagged(type)) {
                        return tagEntry.getValue();
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private record SourcePattern(Pattern pattern, double xp) {
        public SourcePattern(String glob, double xp) {
            this(Pattern.compile("^" + glob.replace(".", "\\.").replace("*", ".*") + "$", Pattern.CASE_INSENSITIVE), xp);
        }
        public boolean matches(String identifier) {
            return pattern.matcher(identifier).matches();
        }
    }
}
