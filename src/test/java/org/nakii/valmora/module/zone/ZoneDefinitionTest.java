package org.nakii.valmora.module.zone;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers {@link ZoneDefinition}'s pure geometry logic (`contains`/`volume`/`getAllBoxes`) —
 *  previously untested directly, only exercised indirectly through `ZoneManager`/`ZoneCommand`.
 *  Per docs/IMPLEMENTATION_BACKLOG.md's cross-cutting "unit tests for untested modules" item. */
public class ZoneDefinitionTest {

    private ZoneDefinition zone(List<int[]> extraBoxes) {
        return new ZoneDefinition("hub", "<green>Hub", "world",
                0, 0, 0, 10, 10, 10, extraBoxes, ZoneFlags.defaults(), null,
                new ArrayList<>(), Map.of(), new ArrayList<>(), new ArrayList<>());
    }

    private Location loc(String worldName, int x, int y, int z) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(x);
        when(location.getBlockY()).thenReturn(y);
        when(location.getBlockZ()).thenReturn(z);
        return location;
    }

    @Test
    void containsIsTrueInsidePrimaryBox() {
        ZoneDefinition zone = zone(List.of());
        assertTrue(zone.contains(loc("world", 5, 5, 5)));
        assertTrue(zone.contains(loc("world", 0, 0, 0))); // inclusive min
        assertTrue(zone.contains(loc("world", 10, 10, 10))); // inclusive max
    }

    @Test
    void containsIsFalseOutsidePrimaryBox() {
        ZoneDefinition zone = zone(List.of());
        assertFalse(zone.contains(loc("world", 11, 5, 5)));
        assertFalse(zone.contains(loc("world", -1, 5, 5)));
    }

    @Test
    void containsIsFalseInDifferentWorld() {
        ZoneDefinition zone = zone(List.of());
        assertFalse(zone.contains(loc("other_world", 5, 5, 5)));
    }

    @Test
    void containsIsFalseWithNullWorld() {
        ZoneDefinition zone = zone(List.of());
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(null);
        assertFalse(zone.contains(location));
    }

    @Test
    void containsChecksExtraBoxesWhenOutsidePrimary() {
        List<int[]> extraBoxes = List.of(new int[]{20, 0, 0, 30, 10, 10});
        ZoneDefinition zone = zone(extraBoxes);

        assertFalse(zone.contains(loc("world", 15, 5, 5))); // gap between primary and extra box
        assertTrue(zone.contains(loc("world", 25, 5, 5))); // inside the extra box
    }

    @Test
    void volumeComputesOnlyThePrimaryBox() {
        ZoneDefinition zone = zone(List.of(new int[]{20, 0, 0, 30, 10, 10}));
        assertEquals(11L * 11L * 11L, zone.volume()); // 0..10 inclusive on each axis
    }

    @Test
    void getAllBoxesReturnsPrimaryFirstThenExtras() {
        int[] extra1 = {20, 0, 0, 30, 10, 10};
        int[] extra2 = {40, 0, 0, 50, 10, 10};
        ZoneDefinition zone = zone(List.of(extra1, extra2));

        List<int[]> boxes = zone.getAllBoxes();
        assertEquals(3, boxes.size());
        assertArrayEquals(new int[]{0, 0, 0, 10, 10, 10}, boxes.get(0));
        assertArrayEquals(extra1, boxes.get(1));
        assertArrayEquals(extra2, boxes.get(2));
    }

    @Test
    void withFlagsReturnsNewInstanceWithUpdatedFlags() {
        ZoneDefinition zone = zone(List.of());
        ZoneFlags newFlags = new ZoneFlags(true, false, false, false, false, false, false, false);
        ZoneDefinition updated = zone.withFlags(newFlags);

        assertNotSame(zone, updated);
        assertTrue(updated.getFlags().pvp());
        assertFalse(zone.getFlags().pvp()); // original untouched
        assertEquals(zone.getId(), updated.getId()); // everything else carried over
    }

    @Test
    void withExtraBoxesReturnsNewInstanceLeavingOriginalUntouched() {
        ZoneDefinition zone = zone(List.of());
        List<int[]> boxes = List.of(new int[]{20, 0, 0, 30, 10, 10});
        ZoneDefinition updated = zone.withExtraBoxes(boxes);

        assertTrue(zone.getExtraBoxes().isEmpty());
        assertEquals(1, updated.getExtraBoxes().size());
    }

    @Test
    void withResourceBlocksReturnsNewInstanceLeavingOriginalUntouched() {
        ZoneDefinition zone = zone(List.of());
        Map<Material, ZoneResourceConfig> blocks = Map.of(Material.COAL_ORE, new ZoneResourceConfig(200, List.of()));
        ZoneDefinition updated = zone.withResourceBlocks(blocks);

        assertTrue(zone.getResourceBlocks().isEmpty());
        assertEquals(1, updated.getResourceBlocks().size());
    }

    @Test
    void extraBoxesListIsDefensivelyImmutable() {
        List<int[]> mutableSource = new ArrayList<>();
        mutableSource.add(new int[]{20, 0, 0, 30, 10, 10});
        ZoneDefinition zone = zone(mutableSource);

        mutableSource.clear(); // mutating the caller's list afterward must not affect the zone
        assertEquals(1, zone.getExtraBoxes().size());
    }

    @Test
    void nullExtraBoxesDefaultsToEmptyList() {
        ZoneDefinition zone = zone(null);
        assertTrue(zone.getExtraBoxes().isEmpty());
    }
}
