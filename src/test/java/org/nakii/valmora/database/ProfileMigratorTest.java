package org.nakii.valmora.database;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfileMigratorTest {

    @Test
    void v1NormalizesLegacyShapes() {
        Map<String, String> columns = new HashMap<>();
        columns.put("player_state", "[80.5, 20.0]");
        columns.put("collections", "{\"wheat\": 12}");
        ProfileMigrator.migrate(columns, 0);

        var state = JsonParser.parseString(columns.get("player_state")).getAsJsonObject();
        assertEquals(80.5, state.get("health").getAsDouble());
        assertEquals(20.0, state.get("mana").getAsDouble());
        var collections = JsonParser.parseString(columns.get("collections")).getAsJsonObject();
        assertEquals(12, collections.getAsJsonObject("counts").get("wheat").getAsLong());
        assertTrue(collections.getAsJsonObject("grantedStages").isEmpty());
    }

    @Test
    void currentShapesAreLeftAlone() {
        Map<String, String> columns = new HashMap<>();
        String state = "{\"health\":1.0,\"mana\":2.0,\"lastCombatTime\":5,\"zoneId\":\"hub\"}";
        String collections = "{\"counts\":{\"wheat\":1},\"grantedStages\":{\"wheat\":1}}";
        columns.put("player_state", state);
        columns.put("collections", collections);
        columns.put("stats", null);
        ProfileMigrator.migrate(columns, 0);
        assertEquals(state, columns.get("player_state"));
        assertEquals(collections, columns.get("collections"));
    }

    @Test
    void newerDataVersionIsRejected() {
        assertThrows(IllegalStateException.class, () -> ProfileMigrator.migrate(new HashMap<>(), ProfileMigrator.LATEST_VERSION + 1));
    }
}
