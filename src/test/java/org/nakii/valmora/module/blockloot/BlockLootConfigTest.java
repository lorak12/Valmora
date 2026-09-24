package org.nakii.valmora.module.blockloot;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlockLootConfigTest {

    @Test
    public void storesMaterialAndDrops() {
        List<BlockLootDrop> drops = List.of(new BlockLootDrop("iron_dust", 1, 1, 1.0));
        BlockLootConfig config = new BlockLootConfig(Material.STONE, drops);

        assertEquals(Material.STONE, config.material());
        assertEquals(drops, config.drops());
    }
}
