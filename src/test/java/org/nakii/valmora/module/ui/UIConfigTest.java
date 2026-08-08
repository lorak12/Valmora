package org.nakii.valmora.module.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UIConfigTest {

    @Test
    void carriesAllFieldsThrough() {
        UIConfig config = new UIConfig("<gold>VALMORA", List.of("line1", "line2"),
                "<red>HP: 20", "header", "footer");

        assertEquals("<gold>VALMORA", config.getScoreboardTitle());
        assertEquals(List.of("line1", "line2"), config.getScoreboardLines());
        assertEquals("<red>HP: 20", config.getActionBarDefault());
        assertEquals("header", config.getTabHeader());
        assertEquals("footer", config.getTabFooter());
    }
}
