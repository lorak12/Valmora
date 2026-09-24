package org.nakii.valmora.module.blockloot;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.Valmora;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockLootRegistryTest {

    private BlockLootRegistry newRegistry() {
        Valmora plugin = mock(Valmora.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("BlockLootRegistryTest"));
        return new BlockLootRegistry(plugin);
    }

    @Test
    public void getReturnsNullForUnconfiguredMaterial() {
        BlockLootRegistry registry = newRegistry();
        assertNull(registry.get(Material.DIRT));
    }

    @Test
    public void registerThenGet_roundTrips() {
        BlockLootRegistry registry = newRegistry();
        BlockLootConfig config = new BlockLootConfig(Material.STONE, List.of());
        registry.register(Material.STONE, config);

        assertEquals(config, registry.get(Material.STONE));
        assertEquals(1, registry.size());
    }

    @Test
    public void collidingMaterial_firstRegistrationWins() {
        BlockLootRegistry registry = newRegistry();
        BlockLootConfig first = new BlockLootConfig(Material.STONE, List.of(new BlockLootDrop("a", 1, 1, 1.0)));
        BlockLootConfig second = new BlockLootConfig(Material.STONE, List.of(new BlockLootDrop("b", 1, 1, 1.0)));

        registry.register(Material.STONE, first);
        registry.register(Material.STONE, second); // should log a warning, not overwrite

        assertEquals(first, registry.get(Material.STONE));
        assertEquals(1, registry.size());
    }

    @Test
    public void clearRemovesEverything() {
        BlockLootRegistry registry = newRegistry();
        registry.register(Material.STONE, new BlockLootConfig(Material.STONE, List.of()));
        registry.clear();

        assertNull(registry.get(Material.STONE));
        assertEquals(0, registry.size());
    }
}
