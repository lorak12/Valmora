package org.nakii.valmora.module.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry-backed item type tag, replacing the old {@code enum ItemType} (Phase 4.1 of the
 * refactor — see docs/REFACTOR/PROGRESS.md). Follows the same pattern proven twice already on
 * {@link org.nakii.valmora.module.combat.DamageType} and {@link org.nakii.valmora.module.mob.MobCategory}:
 * static fields for the original 21 values keep every existing call site (`ItemType.SWORD`,
 * `.valueOf(...)`, `==` comparisons) compiling unchanged, while new types can be registered from
 * {@code item_types.yml} without a code change.
 */
public final class ItemType {

    private static final java.util.Map<String, ItemType> REGISTRY = new ConcurrentHashMap<>();
    /** Ids defined in code (the constants below); everything else came from YAML. */
    private static final java.util.Set<String> BUILTINS = ConcurrentHashMap.newKeySet();
    private static volatile List<ItemType> materialMatchPriorityCache;

    public static final ItemType SWORD = define("SWORD");
    public static final ItemType AXE = define("AXE");
    public static final ItemType PICKAXE = define("PICKAXE");
    public static final ItemType SHOVEL = define("SHOVEL");
    public static final ItemType HOE = define("HOE");
    public static final ItemType TRIDENT = define("TRIDENT");
    public static final ItemType BOW = define("BOW");
    public static final ItemType CROSSBOW = define("CROSSBOW");
    public static final ItemType FISHING_ROD = define("FISHING_ROD");
    public static final ItemType SHEARS = define("SHEARS");
    public static final ItemType SHIELD = define("SHIELD");
    public static final ItemType ELYTRA = define("ELYTRA");
    public static final ItemType HELMET = define("HELMET");
    public static final ItemType CHESTPLATE = define("CHESTPLATE");
    public static final ItemType LEGGINGS = define("LEGGINGS");
    public static final ItemType BOOTS = define("BOOTS");
    public static final ItemType HORSE_ARMOR = define("HORSE_ARMOR");
    public static final ItemType PET = define("PET");
    public static final ItemType ACCESSORY = define("ACCESSORY");
    public static final ItemType BACKPACK = define("BACKPACK");
    public static final ItemType ALL = define("ALL");
    public static final ItemType NONE = define("NONE");

    static {
        BUILTINS.addAll(REGISTRY.keySet());
    }

    /**
     * Drops every YAML-defined entry, keeping only the built-in constants. Called by the loader
     * before (re)loading, so an entry removed from YAML disappears on reload instead of lingering
     * until restart.
     */
    public static void resetToBuiltins() {
        REGISTRY.keySet().retainAll(BUILTINS);
    }

    private final String id;

    private ItemType(String id) {
        this.id = id;
    }

    /** Registers a type id if it doesn't already exist. Redefining an existing id is a safe no-op (same instance). */
    public static ItemType define(String id) {
        ItemType type = REGISTRY.computeIfAbsent(id.toUpperCase(Locale.ROOT), ItemType::new);
        materialMatchPriorityCache = null; // invalidate — a new candidate may affect fromMaterial() ordering
        return type;
    }

    /** Case-insensitive lookup, throwing like {@code Enum.valueOf} did for backward compatibility. */
    public static ItemType valueOf(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown item type: " + id));
    }

    public static Optional<ItemType> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(REGISTRY.get(id.toUpperCase(Locale.ROOT)));
    }

    public static Collection<ItemType> values() {
        return List.copyOf(REGISTRY.values());
    }

    public String getId() {
        return id;
    }

    public String name() {
        return id;
    }

    /**
     * Determines the ItemType based on the material name. Uses length-based priority to correctly
     * handle overlapping names (e.g. PICKAXE vs AXE). The priority order is cached and only
     * recomputed when a new type is registered (rare — load time only, never on a hot path).
     */
    /**
     * Determines the ItemType for a live item stack: the PDC {@code item_type} tag is
     * authoritative for Valmora items, falling back to {@link #fromMaterial(Material)} for
     * vanilla items with no tag.
     */
    public static ItemType fromItemStack(ItemStack item) {
        // Live definition first (so a YAML item-type change applies to existing items), then the
        // stored tag, then the material — see ItemView.
        return ItemView.type(item);
    }

    public static ItemType fromMaterial(Material material) {
        String name = material.name();
        for (ItemType type : materialMatchPriorityOrder()) {
            if (name.contains(type.name())) {
                return type;
            }
        }
        return NONE;
    }

    private static List<ItemType> materialMatchPriorityOrder() {
        List<ItemType> cached = materialMatchPriorityCache;
        if (cached != null) return cached;
        List<ItemType> computed = values().stream()
                .filter(t -> t != ALL && t != NONE)
                .sorted((a, b) -> Integer.compare(b.name().length(), a.name().length()))
                .toList();
        materialMatchPriorityCache = computed;
        return computed;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
