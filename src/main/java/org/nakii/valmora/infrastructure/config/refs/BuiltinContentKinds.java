package org.nakii.valmora.infrastructure.config.refs;

import org.bukkit.Material;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.infrastructure.versioning.IdAliases;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Registers the built-in content kinds in the {@link ContentIndex}. Every lookup goes through the
 * plugin's live managers at check time, so it is registered once and always reflects the current
 * content (a module that is disabled simply has nothing).
 */
public final class BuiltinContentKinds {

    private BuiltinContentKinds() {}

    public static void register(Valmora plugin, ContentIndex index) {
        // Items and materials
        index.register(Kinds.MATERIAL, id -> Material.matchMaterial(id) != null, BuiltinContentKinds::materialNames);
        index.register(Kinds.ITEM, id -> itemExists(plugin, id), () -> itemIds(plugin));
        index.register(Kinds.ITEM_OR_MATERIAL,
                id -> itemExists(plugin, id) || Material.matchMaterial(id) != null,
                () -> {
                    List<String> all = new ArrayList<>(itemIds(plugin));
                    all.addAll(materialNames());
                    return all;
                });

        registry(index, Kinds.MOB, IdAliases.MOBS,
                () -> plugin.getMobManager() == null ? null : plugin.getMobManager().getMobRegistry());
        registry(index, Kinds.QUEST, IdAliases.QUESTS,
                () -> plugin.getQuestManager() == null ? null : plugin.getQuestManager().getRegistry());
        registry(index, Kinds.NPC, null,
                () -> plugin.getNpcModule() == null ? null : plugin.getNpcModule().getNpcRegistry());
        registry(index, Kinds.DIALOGUE, null,
                () -> plugin.getDialogueManager() == null ? null : plugin.getDialogueManager().getDialogueRegistry());
        registry(index, Kinds.ZONE, null,
                () -> plugin.getZoneModule() == null ? null : plugin.getZoneModule().getZoneRegistry());
        registry(index, Kinds.WARP, IdAliases.WARPS,
                () -> plugin.getWarpManager() == null ? null : plugin.getWarpManager().getRegistry());

        index.register(Kinds.GUI,
                id -> plugin.getGuiModule() != null && plugin.getGuiModule().getGuiRegistry().containsKey(lower(id)),
                () -> plugin.getGuiModule() == null ? List.of() : plugin.getGuiModule().getGuiRegistry().keySet());

        index.register(Kinds.STAT,
                id -> plugin.getStatRegistry() != null && plugin.getStatRegistry().contains(lower(id)),
                () -> plugin.getStatRegistry() == null ? List.of() : plugin.getStatRegistry().getKeys());

        index.register(Kinds.SET_BONUS,
                id -> plugin.getItemManager() != null && plugin.getItemManager().getSetBonusRegistry().get(id).isPresent(),
                () -> plugin.getItemManager() == null ? List.of() : plugin.getItemManager().getSetBonusRegistry().ids());

        // A recipe's machine: is valid if some GUI declares that machine:, a machines/*.yml entry
        // exists, a dynamic handler (anvil, alchemy, ...) is registered, or it's the crafting table.
        index.register(Kinds.MACHINE, id -> machineIds(plugin).contains(lower(id)), () -> machineIds(plugin));
    }

    private static <T> void registry(ContentIndex index, String kind, String aliasType, Supplier<Registry<T>> registry) {
        index.register(kind, id -> {
            Registry<T> r = registry.get();
            if (r == null) return false;
            if (r.contains(lower(id))) return true;
            return aliasType != null && r.contains(IdAliases.resolve(aliasType, id));
        }, () -> {
            Registry<T> r = registry.get();
            return r == null ? List.of() : r.getKeys();
        });
    }

    private static boolean itemExists(Valmora plugin, String id) {
        var items = plugin.getItemManager();
        if (items == null) return false;
        var registry = items.getItemRegistry();
        return registry.contains(lower(id)) || registry.contains(IdAliases.resolve(IdAliases.ITEMS, id));
    }

    private static Collection<String> itemIds(Valmora plugin) {
        var items = plugin.getItemManager();
        return items == null ? List.of() : items.getItemRegistry().getAllItemIds();
    }

    private static Set<String> machineIds(Valmora plugin) {
        Set<String> ids = new HashSet<>();
        ids.add("crafting_table");
        if (plugin.getGuiModule() != null) {
            for (var gui : plugin.getGuiModule().getGuiRegistry().values()) {
                if (gui.getMachine() != null) ids.add(lower(gui.getMachine()));
            }
        }
        if (plugin.getMachineModule() != null && plugin.getMachineModule().getRegistry() != null) {
            for (var machine : plugin.getMachineModule().getRegistry().values()) ids.add(lower(machine.getId()));
        }
        if (plugin.getRecipeModule() != null && plugin.getRecipeModule().getRecipeEngine() != null) {
            ids.addAll(plugin.getRecipeModule().getRecipeEngine().getHandlerIds());
        }
        return ids;
    }

    private static List<String> materialNames() {
        List<String> names = new ArrayList<>();
        for (Material m : Material.values()) if (!m.isLegacy()) names.add(m.name());
        return names;
    }

    private static String lower(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }
}
