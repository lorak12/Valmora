package org.nakii.valmora.module.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers Phase 4.1 of the refactor (docs/REFACTOR/PROGRESS.md): {@link ItemType} replaced the old
 * {@code enum ItemType} with a registry-backed class, following the same backward-compat pattern
 * proven by {@code DamageType} (Phase 2) and {@code MobCategory} (Phase 3).
 */
public class ItemTypeTest {

    @Test
    void builtInTypesAreBackwardCompatible() {
        assertEquals("SWORD", ItemType.SWORD.getId());
        assertSame(ItemType.SWORD, ItemType.valueOf("SWORD"));
        assertSame(ItemType.SWORD, ItemType.valueOf("sword"), "valueOf must be case-insensitive");
    }

    @Test
    void valueOfUnknownThrowsLikeEnumValueOfDid() {
        assertThrows(IllegalArgumentException.class, () -> ItemType.valueOf("not_a_real_type"));
    }

    @Test
    void findUnknownReturnsEmptyOptional() {
        assertTrue(ItemType.find("not_a_real_type").isEmpty());
    }

    @Test
    void defineNewTypeIsDiscoverableAndStable() {
        ItemType first = ItemType.define("test_wand");
        ItemType second = ItemType.define("test_wand");

        assertSame(first, second);
        assertSame(first, ItemType.valueOf("test_wand"));
        assertTrue(ItemType.values().stream().anyMatch(t -> t.getId().equals("TEST_WAND")));
    }

    @Test
    void fromMaterialUsesLongestNameMatchToDisambiguateOverlappingNames() {
        // DIAMOND_PICKAXE contains both "AXE" and "PICKAXE" — PICKAXE (longer) must win.
        assertEquals(ItemType.PICKAXE, ItemType.fromMaterial(Material.DIAMOND_PICKAXE));
        assertEquals(ItemType.AXE, ItemType.fromMaterial(Material.DIAMOND_AXE));
        assertEquals(ItemType.SWORD, ItemType.fromMaterial(Material.DIAMOND_SWORD));
    }

    @Test
    void fromMaterialReturnsNoneForUnrelatedMaterials() {
        assertEquals(ItemType.NONE, ItemType.fromMaterial(Material.DIRT));
    }

    @Test
    void fromMaterialStillWorksAfterRegisteringANewType() {
        // Registering a new type invalidates fromMaterial()'s cached priority order — must not break it.
        ItemType.define("test_registration_side_effect");
        assertEquals(ItemType.PICKAXE, ItemType.fromMaterial(Material.DIAMOND_PICKAXE));
    }
}
