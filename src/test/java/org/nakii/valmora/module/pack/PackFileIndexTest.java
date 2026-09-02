package org.nakii.valmora.module.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackFileIndexTest {

    @Test
    void ownerOfMatchesRegisteredPrefix() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");

        assertEquals("frostspire", index.ownerOf("items/frostspire/frost_blade.yml").orElseThrow());
        assertEquals("frostspire", index.ownerOf("Items/Frostspire/nested/deep.yml").orElseThrow(),
                "matching is case-insensitive");
    }

    @Test
    void unrelatedPathsHaveNoOwner() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");

        assertTrue(index.ownerOf("items/base_sword.yml").isEmpty());
        assertTrue(index.ownerOf("recipes/anvil/some_recipe.yml").isEmpty(),
                "an organizational subfolder must not be mistaken for a pack namespace");
        assertTrue(index.ownerOf(null).isEmpty());
    }

    @Test
    void unregisterFolderRemovesOwnership() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");
        index.unregisterFolder("items/frostspire");

        assertTrue(index.ownerOf("items/frostspire/blade.yml").isEmpty());
    }

    @Test
    void unregisterAllForPackRemovesEveryFolderItOwnsButNotOthers() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");
        index.registerFolder("quests/frostspire", "frostspire");
        index.registerFolder("items/other_pack", "other_pack");

        index.unregisterAllForPack("frostspire");

        assertTrue(index.ownerOf("items/frostspire/blade.yml").isEmpty());
        assertTrue(index.ownerOf("quests/frostspire/quest.yml").isEmpty());
        assertEquals("other_pack", index.ownerOf("items/other_pack/thing.yml").orElseThrow());
    }

    @Test
    void clearRemovesEverything() {
        PackFileIndex index = new PackFileIndex();
        index.registerFolder("items/frostspire", "frostspire");
        index.clear();
        assertTrue(index.ownerOf("items/frostspire/blade.yml").isEmpty());
    }
}
