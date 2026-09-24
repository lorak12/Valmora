package org.nakii.valmora.module.pack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The whitelist of content-type folders and shared/misc config files a pack manifest's
 * {@code provides:} block is allowed to name (docs/modules/design/pack.md §2). Mirrors
 * {@code Valmora.seedResourceIfSeedable}'s own whitelist (kept in sync manually — that method
 * governs jar→data-folder seeding of default content, this one governs what a third-party pack
 * is allowed to declare) plus {@code machines/}, which loads content the same way but predates this
 * list and was never added to the seeding whitelist.
 */
public final class PackContentFolders {

    private PackContentFolders() {}

    /** Content-type folders under the data folder, one per game-content domain. */
    public static final List<String> CONTENT_FOLDERS = List.of(
            "items", "mobs", "guis", "recipes", "skills", "enchants", "alchemy", "stats",
            "damage_types", "zones", "fishing", "npcs", "dialogues", "warps", "quests",
            "collections", "hud-items", "calendar", "modifiers", "pets", "set_bonuses",
            "progression", "machines", "quest_boards"
    );

    /**
     * Shared/misc single-file configs that can be non-destructively merged into by a pack (a
     * subset of CLAUDE.md's ~12 misc configs — {@code config.yml} and {@code plugin.yml} are
     * excluded since they're server-instance/jar-descriptor files, not mergeable game content).
     */
    public static final List<String> SHARED_CONFIGS = List.of(
            "ui.yml", "mob_categories.yml", "entity_categories.yml", "item_types.yml",
            "combat_pipeline.yml", "resource_pipeline.yml", "fishing_pipeline.yml",
            "item_pipeline.yml", "mob_pipeline.yml", "rarities.yml"
    );

    /**
     * Which {@link org.nakii.valmora.api.ReloadableModule} id owns each content folder — lets the
     * pack manager compute the exact module subset to pass to
     * {@code ModuleManager.reloadModules(Set)} from a manifest's {@code provides.content} list,
     * instead of reloading everything. Built by inspecting each module's own
     * {@code new YamlLoader<>(plugin, "<folder>", ...)} call site; kept here (not derived
     * reflectively) since several folders map to a module whose id doesn't match the folder name
     * (e.g. {@code damage_types} loads into the {@code combat} module, {@code set_bonuses} into
     * {@code items}). A {@code provides.content} entry naming a subfolder (e.g.
     * {@code "modifiers/groups"}) resolves via its top-level segment, same as
     * {@link #isKnownContentEntry}.
     */
    private static final Map<String, String> FOLDER_TO_MODULE_ID = Map.ofEntries(
            Map.entry("items", "items"),
            Map.entry("mobs", "mobs"),
            Map.entry("guis", "gui"),
            Map.entry("recipes", "recipe"),
            Map.entry("skills", "skills"),
            Map.entry("enchants", "enchants"),
            Map.entry("alchemy", "alchemy"),
            Map.entry("stats", "stats"),
            Map.entry("damage_types", "combat"),
            Map.entry("zones", "zone"),
            Map.entry("fishing", "fishing"),
            Map.entry("npcs", "npc"),
            Map.entry("dialogues", "npc"),
            Map.entry("warps", "warp"),
            Map.entry("quests", "quest"),
            Map.entry("collections", "collections"),
            Map.entry("hud-items", "hud"),
            Map.entry("calendar", "calendar"),
            Map.entry("modifiers", "modifier"),
            Map.entry("pets", "pets"),
            Map.entry("set_bonuses", "items"),
            Map.entry("progression", "progression"),
            Map.entry("machines", "machine"),
            Map.entry("quest_boards", "quest"),
            // Shared single-file configs a pack can merge into — their owning modules must reload
            // too, or merged keys (e.g. a pack's new rarity) stay invisible until a full reload.
            Map.entry("ui.yml", "ui"),
            Map.entry("mob_categories.yml", "mobs"),
            Map.entry("entity_categories.yml", "mobs"),
            Map.entry("mob_pipeline.yml", "mobs"),
            Map.entry("item_types.yml", "items"),
            Map.entry("item_pipeline.yml", "items"),
            Map.entry("combat_pipeline.yml", "combat"),
            Map.entry("resource_pipeline.yml", "resource"),
            Map.entry("fishing_pipeline.yml", "fishing"),
            Map.entry("rarities.yml", "rarity")
    );

    private static final Set<String> CONTENT_FOLDERS_LOWER = toLowerSet(CONTENT_FOLDERS);
    private static final Set<String> SHARED_CONFIGS_LOWER = toLowerSet(SHARED_CONFIGS);

    private static Set<String> toLowerSet(List<String> values) {
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (String v : values) set.add(v.toLowerCase(Locale.ROOT));
        return set;
    }

    /**
     * Whether {@code entry} is a known content folder, or a subfolder/file under one (e.g.
     * {@code modifiers/groups} or {@code recipes/anvil} are valid {@code provides.content} entries,
     * not just the bare top-level folder name).
     */
    public static boolean isKnownContentEntry(String entry) {
        if (entry == null || entry.isBlank()) return false;
        String normalized = entry.replace('\\', '/');
        String top = normalized.contains("/") ? normalized.substring(0, normalized.indexOf('/')) : normalized;
        return CONTENT_FOLDERS_LOWER.contains(top.toLowerCase(Locale.ROOT));
    }

    /** Whether {@code fileName} is one of the known non-destructively-mergeable shared configs. */
    public static boolean isKnownSharedConfig(String fileName) {
        return fileName != null && SHARED_CONFIGS_LOWER.contains(fileName.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the id of the {@link org.nakii.valmora.api.ReloadableModule} that owns
     * {@code contentEntry} (a {@code provides.content} entry, e.g. {@code "items"} or
     * {@code "modifiers/groups"}), or empty if it's not a known content entry.
     */
    public static Optional<String> moduleIdFor(String contentEntry) {
        if (contentEntry == null || contentEntry.isBlank()) return Optional.empty();
        String normalized = contentEntry.replace('\\', '/');
        String top = normalized.contains("/") ? normalized.substring(0, normalized.indexOf('/')) : normalized;
        return Optional.ofNullable(FOLDER_TO_MODULE_ID.get(top.toLowerCase(Locale.ROOT)));
    }
}
