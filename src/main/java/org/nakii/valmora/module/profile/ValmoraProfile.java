package org.nakii.valmora.module.profile;

import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.module.collection.CollectionManager;
import org.nakii.valmora.module.item.CooldownManager;
import org.nakii.valmora.module.skill.SkillManager;
import org.nakii.valmora.module.stat.StatManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ValmoraProfile {
    private final UUID id;
    private final String name;
    private final long createdAt;
    private long lastUsed;
    private final StatManager statManager = new StatManager();
    private final SkillManager skillManager = new SkillManager();
    private final CollectionManager collectionManager = new CollectionManager();
    private final PlayerState playerState = new PlayerState();
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Set<String> tags = new HashSet<>();
    private final Map<String, Object> variables = new HashMap<>();

    // Per-profile inventory snapshots (null = no snapshot yet → treat as empty)
    private ItemStack[] savedInventory = null;
    private ItemStack[] savedArmor = null;
    private ItemStack savedOffhand = null;

    // In-memory mirror of this profile's generic GUI storage slots (see module/gui/storage),
    // keyed by storage-id (e.g. "accessories"). Eagerly populated from the DB on profile load
    // and kept in sync by GuiModule on every write-through save, so stat calculation (which
    // needs synchronous access to equipped accessory contents) never has to touch the DB.
    private final Map<String, ItemStack[]> storageCache = new HashMap<>();

    // Phase 5 (docs/REFACTOR/PROGRESS.md Task 21): stat ids found in this profile's saved SQL row
    // that no longer exist in the live StatRegistry (e.g. an admin deleted or renamed a custom
    // stat role). Kept here — separate from StatManager's baseStats/effectiveStats — so they never
    // silently affect gameplay math, but are written back to the SQL row unchanged on save rather
    // than being dropped. If the stat is ever re-registered, it starts resolving normally again
    // the next time this profile is loaded (SQLDataStore re-splits recognized/unrecognized keys
    // fresh on every load).
    private final Map<String, Double> quarantinedStats = new HashMap<>();

    public ValmoraProfile(UUID id, String name, long createdAt, long lastUsed) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.lastUsed = lastUsed;
    }

    public ValmoraProfile(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.lastUsed = System.currentTimeMillis();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public long getCreatedAt() { return createdAt; }
    public long getLastUsed() { return lastUsed; }
    public void touchLastUsed() { this.lastUsed = System.currentTimeMillis(); }

    public StatManager getStatManager() {
        return statManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public CollectionManager getCollectionManager() {
        return collectionManager;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public ItemStack[] getSavedInventory() { return savedInventory; }
    public ItemStack[] getSavedArmor() { return savedArmor; }
    public ItemStack getSavedOffhand() { return savedOffhand; }

    public void setSavedInventory(ItemStack[] inventory) { this.savedInventory = inventory; }
    public void setSavedArmor(ItemStack[] armor) { this.savedArmor = armor; }
    public void setSavedOffhand(ItemStack offhand) { this.savedOffhand = offhand; }

    /** Returns the cached contents for a generic GUI storage-id, or an empty array if never loaded/populated. */
    public ItemStack[] getStorage(String storageId) {
        return storageCache.getOrDefault(storageId, new ItemStack[0]);
    }

    public void putStorage(String storageId, ItemStack[] contents) {
        storageCache.put(storageId, contents);
    }

    /** Unrecognized stat ids preserved verbatim across load/save — see field comment above. */
    public Map<String, Double> getQuarantinedStats() { return quarantinedStats; }
}
