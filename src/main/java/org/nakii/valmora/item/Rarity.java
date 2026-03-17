package org.nakii.valmora.item;

public enum Rarity {
    COMMON("Common", "<white>"),
    UNCOMMON("Uncommon", "<green>"),
    RARE("Rare", "<blue>"),
    EPIC("Epic", "<dark_purple>"),
    LEGENDARY("Legendary", "<gold>"),
    MYTHIC("Mythic", "<light_purple>");

    private final String name;
    private final String color;

    Rarity(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
