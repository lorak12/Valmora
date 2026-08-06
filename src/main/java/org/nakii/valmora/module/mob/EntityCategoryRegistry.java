package org.nakii.valmora.module.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Keys;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Runtime rule-based entity classifier, loaded from {@code entity_categories.yml}. Originally
 * built as {@code SlayerCategoryRegistry} for slayer target-category matching (Phase 3.4 of the
 * refactor — see docs/REFACTOR/PROGRESS.md) and relocated here so any system can reuse it — most
 * notably the Quest module's KILL objective, which needs to match "any undead mob" rather than
 * one exact mob id/entity type. Rules are compiled once at load time into {@code Predicate<Entity>}
 * chains — never re-interpreted per kill.
 *
 * <p>Supported rule keys under a category's {@code match:} section (all OR'd together):
 * <ul>
 *   <li>{@code type_equals: "SPIDER"} — entity type name equals (case-insensitive)</li>
 *   <li>{@code type_contains: ["ZOMBIE", "SKELETON"]} — entity type name contains any substring</li>
 *   <li>{@code instanceof: "Monster"} — simple {@code org.bukkit.entity} interface/class name</li>
 *   <li>{@code has_pdc: "some_key"} — entity PDC has a key whose short name matches</li>
 *   <li>{@code always: true} — matches every entity (used for the built-in ALL/ANY categories)</li>
 * </ul>
 *
 * <p>If a category id isn't registered, {@link #matches} falls back to the exact pre-refactor
 * default: the entity's Valmora mob id or vanilla {@code EntityType} name equalling the category
 * string verbatim — this is what lets a target category be a specific custom mob id or vanilla
 * type directly instead of a named category.
 */
public class EntityCategoryRegistry {

    private final Map<String, EntityCategoryDefinition> categories = new ConcurrentHashMap<>();

    public void load(Valmora plugin) {
        categories.clear();

        File file = new File(plugin.getDataFolder(), "entity_categories.yml");
        if (!file.exists()) {
            plugin.getLogger().info("[EntityCategoryRegistry] entity_categories.yml not found — category matching falls back to mob-id/entity-type equality only.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("categories");
        if (section == null) return;

        int count = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection categorySection = section.getConfigurationSection(id);
            if (categorySection == null) continue;
            ConfigurationSection matchSection = categorySection.getConfigurationSection("match");
            if (matchSection == null) continue;

            List<Predicate<Entity>> rules = compileRules(matchSection, plugin);
            if (rules.isEmpty()) {
                plugin.getLogger().warning("[EntityCategoryRegistry] Category '" + id + "' has no valid match rules — skipped.");
                continue;
            }
            String normalizedId = id.toUpperCase(Locale.ROOT);
            categories.put(normalizedId, new EntityCategoryDefinition(normalizedId, rules));
            count++;
        }
        plugin.getLogger().info("[EntityCategoryRegistry] Loaded " + count + " entity categories.");
    }

    private List<Predicate<Entity>> compileRules(ConfigurationSection match, Valmora plugin) {
        List<Predicate<Entity>> rules = new ArrayList<>();

        if (match.isBoolean("always") && match.getBoolean("always")) {
            rules.add(entity -> true);
        }
        if (match.isString("type_equals")) {
            String value = match.getString("type_equals");
            rules.add(entity -> entity.getType().name().equalsIgnoreCase(value));
        }
        List<String> containsList = match.getStringList("type_contains");
        if (!containsList.isEmpty()) {
            List<String> upper = containsList.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
            rules.add(entity -> {
                String typeName = entity.getType().name();
                return upper.stream().anyMatch(typeName::contains);
            });
        }
        if (match.isString("instanceof")) {
            String className = match.getString("instanceof");
            Class<?> clazz = resolveEntityInterface(className);
            if (clazz != null) {
                rules.add(clazz::isInstance);
            } else {
                plugin.getLogger().warning("[EntityCategoryRegistry] Unknown 'instanceof' class: " + className);
            }
        }
        if (match.isString("has_pdc")) {
            String key = match.getString("has_pdc");
            rules.add(entity -> entity.getPersistentDataContainer().getKeys().stream()
                    .anyMatch(k -> k.getKey().equalsIgnoreCase(key)));
        }
        return rules;
    }

    private Class<?> resolveEntityInterface(String simpleName) {
        try {
            return Class.forName("org.bukkit.entity." + simpleName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * @return true if {@code category} is a registered rule-based category and it matches this
     * entity, or (if unregistered) the entity's mob id / vanilla type name equals it verbatim.
     */
    public boolean matches(Entity entity, String category) {
        if (category == null) return false;
        EntityCategoryDefinition def = categories.get(category.toUpperCase(Locale.ROOT));
        if (def != null) return def.matches(entity);

        String mobId = entity.getPersistentDataContainer().get(Keys.MOB_ID_KEY, PersistentDataType.STRING);
        if (mobId != null && mobId.equalsIgnoreCase(category)) return true;
        return entity.getType().name().equalsIgnoreCase(category);
    }

    /**
     * @return every registered category id that this entity matches (used by systems that need
     * to precompute a full set of matchable target strings for an entity, e.g. quest KILL
     * objective matching).
     */
    public List<String> matchingCategories(Entity entity) {
        List<String> result = new ArrayList<>();
        for (EntityCategoryDefinition def : categories.values()) {
            if (def.matches(entity)) result.add(def.getId());
        }
        return result;
    }

    public void clear() {
        categories.clear();
    }
}
