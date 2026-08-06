package org.nakii.valmora.module.gui.components;

import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.module.gui.GuiComponent;

/**
 * A slot (or set of slots, one per mapped layout character) that stores items and persists
 * them — either bound to the physical item this GUI was opened from ({@link Owner#ITEM}, e.g.
 * a backpack's own contents), or bound to the viewing player ({@link Owner#PLAYER}, e.g. a
 * quiver or accessory bag), DB-backed so it survives restarts and crashes.
 */
public final class StorageComponent extends GuiComponent {

    public enum Owner { PLAYER, ITEM }

    private final String id;
    private final Owner owner;
    private final String storageId;
    private final @Nullable Condition condition;
    private final boolean openContainer;

    public StorageComponent(String id, Owner owner, String storageId, @Nullable Condition condition, boolean openContainer) {
        this.id = id;
        this.owner = owner;
        this.storageId = storageId;
        this.condition = condition;
        this.openContainer = openContainer;
    }

    public String getId() { return id; }
    public Owner getOwner() { return owner; }
    public String getStorageId() { return storageId; }
    public @Nullable Condition getCondition() { return condition; }
    public boolean isOpenContainer() { return openContainer; }
}
