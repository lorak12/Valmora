package org.nakii.valmora.module.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HologramDefinitionTest {

    @Test
    void carriesAllFieldsThrough() {
        HologramDefinition def = new HologramDefinition("greeting", "<gold>Hello!",
                0.5, 2.0, -0.5, List.of("tag:vip"), 20);

        assertEquals("greeting", def.getName());
        assertEquals("<gold>Hello!", def.getText());
        assertEquals(0.5, def.getOffsetX());
        assertEquals(2.0, def.getOffsetY());
        assertEquals(-0.5, def.getOffsetZ());
        assertEquals(List.of("tag:vip"), def.getConditions());
        assertEquals(20, def.getCheckInterval());
    }

    @Test
    void checkInterval_clampsToMinimumOne() {
        assertEquals(1, new HologramDefinition("a", "t", 0, 0, 0, List.of(), 0).getCheckInterval());
        assertEquals(1, new HologramDefinition("a", "t", 0, 0, 0, List.of(), -5).getCheckInterval());
    }

    @Test
    void conditions_areDefensivelyCopied() {
        var mutable = new java.util.ArrayList<String>();
        mutable.add("tag:vip");
        HologramDefinition def = new HologramDefinition("a", "t", 0, 0, 0, mutable, 20);
        mutable.add("tag:admin");

        assertEquals(1, def.getConditions().size(), "definition's list must not see later mutation of the source list");
    }
}
