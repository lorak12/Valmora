package org.nakii.valmora.module.quest.pkg;

import org.nakii.valmora.module.npc.dialogue.DialogueDefinition;
import org.nakii.valmora.module.quest.QuestDefinition;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.module.quest.hider.PlayerHiderEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A loaded quest package. Holds all features defined anywhere in the package folder.
 * Feature names are unique within a package (case-insensitive).
 */
public class QuestPackage {

    /** Dot-separated path from quests root, e.g. "dailyQuests" or "weekly-weekOne". */
    private final String path;
    private final boolean enabled;
    private final List<String> templateNames;

    /** Named event action lists: eventName → list of DSL strings */
    private final Map<String, List<String>> events = new HashMap<>();

    /** Named condition strings: conditionName → DSL string */
    private final Map<String, String> conditions = new HashMap<>();

    /** Named objectives: objectiveId → QuestObjective */
    private final Map<String, QuestObjective> objectives = new HashMap<>();

    /** Quest definitions loaded from this package */
    private final Map<String, QuestDefinition> quests = new HashMap<>();

    /** Dialogue definitions loaded from this package */
    private final Map<String, DialogueDefinition> conversations = new HashMap<>();

    /** Notification category settings: categoryName → {io, ...} */
    private final Map<String, Map<String, String>> notifications = new HashMap<>();

    /** Player hider rules defined in this package */
    private final List<PlayerHiderEntry> playerHiders = new ArrayList<>();

    /** NPC → conversation bindings defined in quest.yml: npc_id → conversation_id */
    private final Map<String, String> npcConversationBindings = new HashMap<>();

    public QuestPackage(String path, boolean enabled, List<String> templateNames) {
        this.path = path;
        this.enabled = enabled;
        this.templateNames = templateNames;
    }

    public String getPath() { return path; }
    public boolean isEnabled() { return enabled; }
    public List<String> getTemplateNames() { return templateNames; }
    public Map<String, List<String>> getEvents() { return events; }
    public Map<String, String> getConditions() { return conditions; }
    public Map<String, QuestObjective> getObjectives() { return objectives; }
    public Map<String, QuestDefinition> getQuests() { return quests; }
    public Map<String, DialogueDefinition> getConversations() { return conversations; }
    public Map<String, Map<String, String>> getNotifications() { return notifications; }
    public List<PlayerHiderEntry> getPlayerHiders() { return playerHiders; }
    public Map<String, String> getNpcConversationBindings() { return npcConversationBindings; }
}
