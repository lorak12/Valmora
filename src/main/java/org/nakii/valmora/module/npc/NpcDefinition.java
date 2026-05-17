package org.nakii.valmora.module.npc;

import org.bukkit.entity.EntityType;

import java.util.List;

public class NpcDefinition {
    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final String worldName;
    private final double x, y, z;
    private final float yaw;
    private final List<String> onRightClick;
    private final List<String> onLeftClick;
    private final String boundConversationId;
    /** Relative path from the plugin data folder, e.g. "npcs/hub.yml". */
    private final String sourceFile;
    /** Base64-encoded Mojang texture value for PLAYER-type NPCs (nullable). */
    private final String skinTexture;
    /** Mojang texture signature (nullable — unsigned textures work but may not render on all clients). */
    private final String skinSignature;
    /** Whether this NPC should rotate to face nearby players. */
    private final boolean lookAtPlayer;
    /** Whether this NPC's floating name tag is visible. */
    private final boolean showName;
    /** Conditional floating-text lines shown above the NPC. */
    private final List<HologramDefinition> holograms;

    public static final String DEFAULT_SOURCE = "npcs/from_command.yml";

    // ── Canonical constructor ─────────────────────────────────────────────────

    public NpcDefinition(String id, String displayName, EntityType entityType,
                         String worldName, double x, double y, double z, float yaw,
                         List<String> onRightClick, List<String> onLeftClick,
                         String boundConversationId, String sourceFile,
                         String skinTexture, String skinSignature,
                         boolean lookAtPlayer, boolean showName,
                         List<HologramDefinition> holograms) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.worldName = worldName;
        this.x = x; this.y = y; this.z = z; this.yaw = yaw;
        this.onRightClick = onRightClick;
        this.onLeftClick = onLeftClick;
        this.boundConversationId = boundConversationId;
        this.sourceFile = sourceFile != null ? sourceFile : DEFAULT_SOURCE;
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
        this.lookAtPlayer = lookAtPlayer;
        this.showName = showName;
        this.holograms = holograms != null ? List.copyOf(holograms) : List.of();
    }

    // ── Legacy convenience constructors ──────────────────────────────────────

    public NpcDefinition(String id, String displayName, EntityType entityType,
                         String worldName, double x, double y, double z, float yaw,
                         List<String> onRightClick, List<String> onLeftClick) {
        this(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, null, DEFAULT_SOURCE, null, null, false, true, List.of());
    }

    public NpcDefinition(String id, String displayName, EntityType entityType,
                         String worldName, double x, double y, double z, float yaw,
                         List<String> onRightClick, List<String> onLeftClick,
                         String boundConversationId) {
        this(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, DEFAULT_SOURCE, null, null, false, true, List.of());
    }

    public NpcDefinition(String id, String displayName, EntityType entityType,
                         String worldName, double x, double y, double z, float yaw,
                         List<String> onRightClick, List<String> onLeftClick,
                         String boundConversationId, String sourceFile) {
        this(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, null, null, false, true, List.of());
    }

    public NpcDefinition(String id, String displayName, EntityType entityType,
                         String worldName, double x, double y, double z, float yaw,
                         List<String> onRightClick, List<String> onLeftClick,
                         String boundConversationId, String sourceFile,
                         String skinTexture, String skinSignature) {
        this(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile,
                skinTexture, skinSignature, false, true, List.of());
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public EntityType getEntityType() { return entityType; }
    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public List<String> getOnRightClick() { return onRightClick; }
    public List<String> getOnLeftClick() { return onLeftClick; }
    public String getBoundConversationId() { return boundConversationId; }
    public String getSourceFile() { return sourceFile; }
    public String getSkinTexture() { return skinTexture; }
    public String getSkinSignature() { return skinSignature; }
    public boolean isLookAtPlayer() { return lookAtPlayer; }
    public boolean isShowName() { return showName; }
    public List<HologramDefinition> getHolograms() { return holograms; }

    // ── Copy-with helpers ─────────────────────────────────────────────────────

    public NpcDefinition withDisplayName(String name) {
        return new NpcDefinition(id, name, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, skinTexture, skinSignature,
                lookAtPlayer, showName, holograms);
    }

    public NpcDefinition withEntityType(EntityType type) {
        return new NpcDefinition(id, displayName, type, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, skinTexture, skinSignature,
                lookAtPlayer, showName, holograms);
    }

    public NpcDefinition withPosition(String world, double nx, double ny, double nz, float nyaw) {
        return new NpcDefinition(id, displayName, entityType, world, nx, ny, nz, nyaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, skinTexture, skinSignature,
                lookAtPlayer, showName, holograms);
    }

    public NpcDefinition withYaw(float nyaw) {
        return new NpcDefinition(id, displayName, entityType, worldName, x, y, z, nyaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, skinTexture, skinSignature,
                lookAtPlayer, showName, holograms);
    }

    public NpcDefinition withConversation(String convId) {
        return new NpcDefinition(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, convId, sourceFile, skinTexture, skinSignature,
                lookAtPlayer, showName, holograms);
    }

    public NpcDefinition withSourceFile(String file) {
        return new NpcDefinition(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, file, skinTexture, skinSignature,
                lookAtPlayer, showName, holograms);
    }

    public NpcDefinition withSkin(String texture, String signature) {
        return new NpcDefinition(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, texture, signature,
                lookAtPlayer, showName, holograms);
    }

    public NpcDefinition withLookAtPlayer(boolean look) {
        return new NpcDefinition(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, skinTexture, skinSignature,
                look, showName, holograms);
    }

    public NpcDefinition withShowName(boolean show) {
        return new NpcDefinition(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, skinTexture, skinSignature,
                lookAtPlayer, show, holograms);
    }

    public NpcDefinition withHolograms(List<HologramDefinition> holos) {
        return new NpcDefinition(id, displayName, entityType, worldName, x, y, z, yaw,
                onRightClick, onLeftClick, boundConversationId, sourceFile, skinTexture, skinSignature,
                lookAtPlayer, showName, holos);
    }
}
