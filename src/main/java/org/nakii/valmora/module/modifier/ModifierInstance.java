package org.nakii.valmora.module.modifier;

import java.util.HashMap;
import java.util.Map;

/**
 * One active attachment of a modifier to an item instance (docs/
 * Valmora_Modifier_Framework_Design.docx §3/§13/§14). Persisted via {@link ModifierComponentStore}
 * under the item's {@code modifiers:<group_id>} component (§3 core model — "the existing Component
 * Store already models modifier groups with a group ID, modifier ID, application count, stats, and
 * metadata").
 */
public class ModifierInstance {

    private final String groupId;
    private final String modifierId;
    private final int tier;
    private int count;
    private final Map<String, Integer> state;

    public ModifierInstance(String groupId, String modifierId, int tier, int count, Map<String, Integer> state) {
        this.groupId = groupId;
        this.modifierId = modifierId;
        this.tier = tier;
        this.count = count;
        this.state = state != null ? state : new HashMap<>();
    }

    public String getGroupId() { return groupId; }
    public String getModifierId() { return modifierId; }
    public int getTier() { return tier; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public Map<String, Integer> getState() { return state; }

    public ModifierInstance withCount(int newCount) {
        return new ModifierInstance(groupId, modifierId, tier, newCount, new HashMap<>(state));
    }

    public ModifierInstance withState(Map<String, Integer> newState) {
        return new ModifierInstance(groupId, modifierId, tier, count, newState);
    }
}
