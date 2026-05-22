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

    // Accessory bag (45 slots)
    private ItemStack[] accessoryItems = new ItemStack[45];

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

    public ItemStack[] getAccessoryItems() { return accessoryItems; }
    public void setAccessoryItems(ItemStack[] items) { this.accessoryItems = items; }
}
