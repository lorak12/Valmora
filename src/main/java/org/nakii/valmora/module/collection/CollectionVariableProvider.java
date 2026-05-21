package org.nakii.valmora.module.collection;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.gui.GuiExecutionContext;
import org.nakii.valmora.module.gui.GuiSession;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.variable.VariableProvider;

import java.util.List;
import java.util.Optional;

public class CollectionVariableProvider implements VariableProvider {

    private final Valmora plugin;
    private final Gson gson = new Gson();

    public CollectionVariableProvider(Valmora plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getNamespace() {
        return "collection";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;

        Optional<Player> maybePlayer = context.getPlayerCaster();
        if (maybePlayer.isEmpty()) return null;
        Player player = maybePlayer.get();

        ValmoraPlayer vp = plugin.getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return null;
        ValmoraProfile profile = vp.getActiveProfile();
        if (profile == null) return null;

        CollectionModule module = plugin.getCollectionModule();
        if (module == null) return null;
        CollectionRegistry registry = module.getRegistry();
        CollectionManager manager = profile.getCollectionManager();

        GuiSession session = null;
        if (context instanceof GuiExecutionContext guiCtx) {
            session = guiCtx.getSession();
        }

        return switch (path[0].toLowerCase()) {
            case "category_list" -> buildCategoryList(registry, manager);
            case "item_list" -> buildItemList(registry, manager, session);
            case "stage_list" -> buildStageList(registry, manager, session);
            case "detail_name" -> resolveDetailName(registry, session);
            case "detail_icon" -> resolveDetailIcon(registry, session);
            case "detail_count" -> resolveDetailCount(manager, session);
            case "detail_stage" -> resolveDetailStage(registry, manager, session);
            case "detail_max_stage" -> resolveDetailMaxStage(registry, session);
            case "detail_next_required" -> resolveDetailNextRequired(registry, manager, session);
            default -> null;
        };
    }

    // ── Category list ─────────────────────────────────────────────────────

    private String buildCategoryList(CollectionRegistry registry, CollectionManager manager) {
        JsonArray array = new JsonArray();
        for (CollectionCategory cat : registry.getCategories()) {
            List<CollectionDefinition> items = registry.getCollectionsInCategory(cat.getId());
            long completed = items.stream().filter(def -> {
                long count = manager.getCount(def.getId());
                return def.getMaxStage() > 0 && def.getStageForCount(count) >= def.getMaxStage();
            }).count();

            JsonObject obj = new JsonObject();
            obj.addProperty("id", cat.getId());
            obj.addProperty("name", cat.getName());
            obj.addProperty("icon", cat.getIcon());
            obj.addProperty("description", cat.getDescription());
            obj.addProperty("total", items.size());
            obj.addProperty("completed", (int) completed);
            array.add(obj);
        }
        return gson.toJson(array);
    }

    // ── Item list for selected category ──────────────────────────────────

    private String buildItemList(CollectionRegistry registry, CollectionManager manager, GuiSession session) {
        if (session == null) return gson.toJson(new JsonArray());
        String selectedCategory = (String) session.getProps().get("selected_category");
        if (selectedCategory == null) return gson.toJson(new JsonArray());

        JsonArray array = new JsonArray();
        for (CollectionDefinition def : registry.getCollectionsInCategory(selectedCategory)) {
            long count = manager.getCount(def.getId());
            int currentStage = def.getStageForCount(count);
            boolean completed = def.getMaxStage() > 0 && currentStage >= def.getMaxStage();

            long nextRequired = count;
            for (CollectionStage stage : def.getStages()) {
                if (stage.getNumber() > currentStage) {
                    nextRequired = stage.getRequired();
                    break;
                }
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("id", def.getId());
            obj.addProperty("name", def.getName());
            obj.addProperty("icon", def.getIcon());
            obj.addProperty("count", count);
            obj.addProperty("stage", currentStage);
            obj.addProperty("max_stage", def.getMaxStage());
            obj.addProperty("next_required", nextRequired);
            obj.addProperty("status", completed ? "completed" : "in_progress");
            array.add(obj);
        }
        return gson.toJson(array);
    }

    // ── Stage list for selected collection ───────────────────────────────

    private String buildStageList(CollectionRegistry registry, CollectionManager manager, GuiSession session) {
        if (session == null) return gson.toJson(new JsonArray());
        String selectedCollection = (String) session.getProps().get("selected_collection");
        if (selectedCollection == null) return gson.toJson(new JsonArray());

        CollectionDefinition def = registry.getCollection(selectedCollection).orElse(null);
        if (def == null) return gson.toJson(new JsonArray());

        long playerCount = manager.getCount(selectedCollection);
        int currentStage = def.getStageForCount(playerCount);

        JsonArray array = new JsonArray();
        for (CollectionStage stage : def.getStages()) {
            String status;
            if (stage.getNumber() <= currentStage) {
                status = "completed";
            } else if (stage.getNumber() == currentStage + 1) {
                status = "current";
            } else {
                status = "locked";
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("number", stage.getNumber());
            obj.addProperty("required", stage.getRequired());
            obj.addProperty("rewards", String.join("\n", stage.getRewards()));
            obj.addProperty("status", status);
            array.add(obj);
        }
        return gson.toJson(array);
    }

    // ── Detail scalars ────────────────────────────────────────────────────

    private String resolveDetailName(CollectionRegistry registry, GuiSession session) {
        CollectionDefinition def = getSelectedCollection(registry, session);
        return def != null ? def.getName() : "?";
    }

    private String resolveDetailIcon(CollectionRegistry registry, GuiSession session) {
        CollectionDefinition def = getSelectedCollection(registry, session);
        return def != null ? def.getIcon() : "BARRIER";
    }

    private long resolveDetailCount(CollectionManager manager, GuiSession session) {
        if (session == null) return 0L;
        String id = (String) session.getProps().get("selected_collection");
        if (id == null) return 0L;
        return manager.getCount(id);
    }

    private int resolveDetailStage(CollectionRegistry registry, CollectionManager manager, GuiSession session) {
        if (session == null) return 0;
        String id = (String) session.getProps().get("selected_collection");
        if (id == null) return 0;
        CollectionDefinition def = registry.getCollection(id).orElse(null);
        return manager.getCurrentStage(id, def);
    }

    private int resolveDetailMaxStage(CollectionRegistry registry, GuiSession session) {
        CollectionDefinition def = getSelectedCollection(registry, session);
        return def != null ? def.getMaxStage() : 0;
    }

    private long resolveDetailNextRequired(CollectionRegistry registry, CollectionManager manager, GuiSession session) {
        if (session == null) return 0L;
        String id = (String) session.getProps().get("selected_collection");
        if (id == null) return 0L;
        CollectionDefinition def = registry.getCollection(id).orElse(null);
        if (def == null) return 0L;
        long count = manager.getCount(id);
        int currentStage = def.getStageForCount(count);
        for (CollectionStage stage : def.getStages()) {
            if (stage.getNumber() > currentStage) return stage.getRequired();
        }
        return count;
    }

    private CollectionDefinition getSelectedCollection(CollectionRegistry registry, GuiSession session) {
        if (session == null) return null;
        String id = (String) session.getProps().get("selected_collection");
        if (id == null) return null;
        return registry.getCollection(id).orElse(null);
    }
}
