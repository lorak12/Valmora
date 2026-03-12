package org.nakii.valmora.item;

public enum Rarity {
    COMMON("Common", "<white>"),
    UNCOMMON("Uncommon", "<green>"),
    RARE("Rare", "<blue>"),
    EPIC("Epic", "<dark_purple>"),
    LEGENDARY("Legendary", "<gold>"),
    DIVINE("Divine", "<aqua>"),
    SPECIAL("Special", "<red>");

    private final String displayName;
    private final String color;

    Rarity(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return color + displayName;
    }
}
