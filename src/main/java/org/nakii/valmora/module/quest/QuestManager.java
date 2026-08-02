package org.nakii.valmora.module.quest;

import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.api.quest.ObjectiveHandler;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.notify.NotifyManager;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.Map;

public class QuestManager {

    public static final String STATUS_NOT_STARTED = "not_started";
    public static final String STATUS_IN_PROGRESS  = "in_progress";
    public static final String STATUS_COMPLETED    = "completed";
    public static final String STATUS_FAILED       = "failed";

    private final Valmora plugin;
    private final Registry<QuestDefinition> registry = new SimpleRegistry<>();
    private final Registry<ObjectiveHandler> handlerRegistry = new SimpleRegistry<>();

    public QuestManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public Registry<QuestDefinition> getRegistry() { return registry; }

    public void registerObjectiveHandler(ObjectiveHandler handler) {
        handlerRegistry.register(handler.getTypeId(), handler);
    }

    // -------------------------------------------------------------------------
    // Status / progress queries
    // -------------------------------------------------------------------------

    public String getStatus(ValmoraProfile profile, String questId) {
        Object s = profile.getVariables().get("quest." + questId + ".status");
        return s != null ? s.toString() : STATUS_NOT_STARTED;
    }

    public int getProgress(ValmoraProfile profile, String questId, int index) {
        Object p = profile.getVariables().get("quest." + questId + ".obj." + index);
        return p instanceof Number n ? n.intValue() : 0;
    }

    public int getObjectiveProgress(ValmoraProfile profile, String questId, String objectiveId) {
        Object p = profile.getVariables().get("quest." + questId + ".obj." + objectiveId);
        return p instanceof Number n ? n.intValue() : 0;
    }

    public boolean isObjectiveActive(ValmoraProfile profile, String objectiveId) {
        Object v = profile.getVariables().get("objective." + objectiveId + ".active");
        return Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
    }

    /**
     * Returns true if the player has at least one in-progress objective of the given type.
     * Use this as a cheap guard before expensive listeners (e.g. PlayerMoveEvent).
     */
    public boolean hasActiveObjectiveType(Player player, String typeId) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return false;
        for (QuestDefinition quest : registry.values()) {
            if (!getStatus(profile, quest.getId()).equals(STATUS_IN_PROGRESS)) continue;
            for (QuestObjective obj : quest.getObjectives()) {
                if (obj.getType().equalsIgnoreCase(typeId)) return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Quest lifecycle
    // -------------------------------------------------------------------------

    public void startQuest(Player player, String questId) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        QuestDefinition quest = registry.get(questId).orElse(null);
        if (quest == null) return;
        String status = getStatus(profile, questId);
        if (status.equals(STATUS_IN_PROGRESS) || status.equals(STATUS_COMPLETED)) return;

        Map<String, Object> vars = profile.getVariables();
        vars.put("quest." + questId + ".status", STATUS_IN_PROGRESS);

        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective obj = objectives.get(i);
            String key = obj.getId() != null ? obj.getId() : String.valueOf(i);
            vars.put("quest." + questId + ".obj." + key, 0);
            if (obj.getId() != null) {
                vars.put("objective." + obj.getId() + ".active", true);
            }
            // Notify registered handler (e.g. DELAY scheduling)
            handlerRegistry.get(obj.getType()).ifPresent(h -> h.onQuestStart(player, obj, this));
        }

        player.sendMessage(Formatter.format("<gold>Quest started: " + quest.getName()));
    }

    public void completeQuest(Player player, String questId) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        QuestDefinition quest = registry.get(questId).orElse(null);
        if (quest == null) return;
        finishQuest(player, profile, quest);
    }

    public void cancelQuest(Player player, String questId) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        clearObjectiveFlags(profile, questId);
        profile.getVariables().put("quest." + questId + ".status", STATUS_NOT_STARTED);
    }

    public void failQuest(Player player, String questId) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        clearObjectiveFlags(profile, questId);
        profile.getVariables().put("quest." + questId + ".status", STATUS_FAILED);
        player.sendMessage(Formatter.format("<red>Quest failed: " + questId));
    }

    /**
     * Resets a quest back to {@link #STATUS_NOT_STARTED} and clears every objective's progress,
     * so it can be immediately restarted. Used by systems (e.g. a quest board) that hand out a
     * fresh instance of the same quest after the player has collected its rewards, rather than
     * relying on a cooldown timer.
     */
    public void resetQuestProgress(ValmoraProfile profile, String questId) {
        if (profile == null) return;
        QuestDefinition quest = registry.get(questId).orElse(null);
        if (quest == null) return;
        clearObjectiveFlags(profile, questId);
        Map<String, Object> vars = profile.getVariables();
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective obj = objectives.get(i);
            String key = obj.getId() != null ? obj.getId() : String.valueOf(i);
            vars.remove("quest." + questId + ".obj." + key);
        }
        vars.put("quest." + questId + ".status", STATUS_NOT_STARTED);
    }

    // -------------------------------------------------------------------------
    // Objective lifecycle
    // -------------------------------------------------------------------------

    public void startObjective(Player player, String objectiveId) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        profile.getVariables().put("objective." + objectiveId + ".active", true);
        profile.getVariables().put("objective." + objectiveId + ".progress", 0);
    }

    public void deleteObjective(Player player, String objectiveId) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        profile.getVariables().remove("objective." + objectiveId + ".active");
        profile.getVariables().remove("objective." + objectiveId + ".progress");
    }

    /**
     * Public entry point for triggering objective progress from any Bukkit listener
     * or external plugin. Scans all in-progress quests for matching objectives,
     * evaluates conditions, increments progress, and fires completion logic.
     *
     * @param typeId  lowercase type ID, e.g. {@code "kill"}, {@code "my_custom_type"}
     * @param target  type-specific target string, e.g. a mob ID or material name
     * @param amount  how much progress to add (usually 1)
     */
    public void trigger(Player player, String typeId, String target, int amount) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        SimpleExecutionContext ctx = new SimpleExecutionContext(player, player.getLocation(), null);

        for (QuestDefinition quest : registry.values()) {
            if (!getStatus(profile, quest.getId()).equals(STATUS_IN_PROGRESS)) continue;
            List<QuestObjective> objectives = quest.getObjectives();
            boolean anyChanged = false;

            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective obj = objectives.get(i);
                if (!obj.getType().equalsIgnoreCase(typeId)) continue;

                // DELAY matches by objective ID; all other types match by target string
                if (typeId.equalsIgnoreCase(QuestObjectiveTypes.DELAY)) {
                    if (obj.getId() == null || !obj.getId().equalsIgnoreCase(target)) continue;
                } else {
                    if (!obj.getTarget().equalsIgnoreCase(target) && !obj.getTarget().equalsIgnoreCase("any")) continue;
                }

                if (!evaluateConditions(obj.getConditions(), ctx)) continue;

                String key = obj.getId() != null ? obj.getId() : String.valueOf(i);
                int current = getProgressByKey(profile, quest.getId(), key);
                if (current >= obj.getRequired()) continue;

                int newVal = Math.min(current + amount, obj.getRequired());
                profile.getVariables().put("quest." + quest.getId() + ".obj." + key, newVal);
                anyChanged = true;

                sendProgressNotification(player, obj, newVal);

                if (newVal >= obj.getRequired()) {
                    if (obj.getId() != null)
                        profile.getVariables().put("objective." + obj.getId() + ".active", false);

                    if (!obj.getEvents().isEmpty())
                        plugin.getScriptModule().getEventParser().parseList(obj.getEvents()).execute(ctx);

                    if (obj.isPersistent()) {
                        profile.getVariables().put("quest." + quest.getId() + ".obj." + key, 0);
                        if (obj.getId() != null)
                            profile.getVariables().put("objective." + obj.getId() + ".active", true);
                    }
                }
            }
            if (anyChanged && !isAnyPersistentPending(quest)) checkCompletion(player, profile, quest, ctx);
        }
    }

    // -------------------------------------------------------------------------
    // Auto-once handling
    // -------------------------------------------------------------------------

    public void startAutoOnceObjectivesForPlayer(Player player) {
        ValmoraProfile profile = getProfile(player);
        if (profile == null) return;
        for (QuestDefinition quest : registry.values()) {
            for (QuestObjective obj : quest.getObjectives()) {
                if (!obj.isAutoOnce()) continue;
                String guardTag = quest.getId() + ".auto-once-" + (obj.getId() != null ? obj.getId() : obj.getType());
                if (profile.getTags().contains(guardTag)) continue;
                profile.getTags().add(guardTag);
                startObjectiveInQuest(player, profile, quest, obj);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void startObjectiveInQuest(Player player, ValmoraProfile profile, QuestDefinition quest, QuestObjective obj) {
        String key = obj.getId() != null ? obj.getId() : obj.getType();
        profile.getVariables().put("quest." + quest.getId() + ".obj." + key, 0);
        if (obj.getId() != null) profile.getVariables().put("objective." + obj.getId() + ".active", true);
    }

    private void checkCompletion(Player player, ValmoraProfile profile, QuestDefinition quest, SimpleExecutionContext ctx) {
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective obj = objectives.get(i);
            if (obj.isPersistent()) continue;
            String key = obj.getId() != null ? obj.getId() : String.valueOf(i);
            if (getProgressByKey(profile, quest.getId(), key) < obj.getRequired()) return;
        }
        finishQuest(player, profile, quest);
    }

    private void finishQuest(Player player, ValmoraProfile profile, QuestDefinition quest) {
        clearObjectiveFlags(profile, quest.getId());
        profile.getVariables().put("quest." + quest.getId() + ".status", STATUS_COMPLETED);
        player.sendMessage(Formatter.format("<gold><bold>Quest Completed: " + quest.getName()));
    }

    private void clearObjectiveFlags(ValmoraProfile profile, String questId) {
        QuestDefinition quest = registry.get(questId).orElse(null);
        if (quest == null) return;
        for (QuestObjective obj : quest.getObjectives()) {
            if (obj.getId() != null) profile.getVariables().remove("objective." + obj.getId() + ".active");
        }
    }

    private int getProgressByKey(ValmoraProfile profile, String questId, String key) {
        Object p = profile.getVariables().get("quest." + questId + ".obj." + key);
        return p instanceof Number n ? n.intValue() : 0;
    }

    /**
     * True if every objective in this quest is persistent (loops without ever completing).
     * {@link #checkCompletion} skips persistent objectives, so a quest made entirely of them
     * must never be handed to {@link #finishQuest} — otherwise it would auto-complete on its
     * first progress tick and (since {@link #startQuest} refuses to restart a completed quest)
     * permanently lock itself.
     */
    private boolean isAnyPersistentPending(QuestDefinition quest) {
        for (QuestObjective obj : quest.getObjectives()) {
            if (!obj.isPersistent()) return false;
        }
        return true;
    }

    private boolean evaluateConditions(List<String> conditionStrings, SimpleExecutionContext ctx) {
        if (conditionStrings == null || conditionStrings.isEmpty()) return true;
        var group = plugin.getScriptModule().getConditionParser().parseList(conditionStrings);
        return group.evaluate(ctx);
    }

    private void sendProgressNotification(Player player, QuestObjective obj, int current) {
        if (obj.getNotifyInterval() <= 0) return;
        if (current % obj.getNotifyInterval() != 0 && current < obj.getRequired()) return;
        NotifyManager nm = plugin.getNotifyManager();
        if (nm == null) return;
        String msg = "<yellow>" + obj.getTarget() + " <gray>(" + current + "/" + obj.getRequired() + ")";
        nm.sendCategory(player, msg, "info");
    }

    ValmoraProfile getProfile(Player player) {
        ValmoraPlayer vp = plugin.getPlayerManager().getSession(player.getUniqueId());
        return vp != null ? vp.getActiveProfile() : null;
    }
}
