package org.nakii.valmora.infrastructure.config.diag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsModelTest {

    @AfterEach
    void cleanup() {
        LoadReport.global().begin();
    }

    @Test
    void formatIncludesFileEntryPathAndHint() {
        var d = new ConfigDiagnostic(Severity.WARN, "Mobs", "mobs/undead.yml", "ghoul", "drops[2].item",
                "unknown item 'enchanted_flsh'", "did you mean 'enchanted_flesh'?");
        assertEquals("[mobs/undead.yml] 'ghoul' › drops[2].item: unknown item 'enchanted_flsh' (did you mean 'enchanted_flesh'?)",
                d.format());
    }

    @Test
    void legacyMessagesRoundTripToTheOriginalString() {
        String raw = "[items/a.yml] 'sword': negative power";
        assertEquals(raw, ConfigDiagnostic.legacy(Severity.ERROR, "Items", "items/a.yml", "sword", raw).format());
        String other = "In item 'sword': Missing required field 'material'.";
        assertEquals("[items/a.yml] " + other,
                ConfigDiagnostic.legacy(Severity.ERROR, "Items", "items/a.yml", "sword", other).format());
        // Message that doesn't name the entry gets it added.
        assertEquals("[items/a.yml] 'sword': bad",
                ConfigDiagnostic.legacy(Severity.ERROR, "Items", "items/a.yml", "sword", "bad").format());
    }

    @Test
    void keptPreviousHintMatchesOldWording() {
        var d = ConfigDiagnostic.legacy(Severity.ERROR, "Items", "items/a.yml", "sword", "[items/a.yml] 'sword': broken")
                .withHint("kept the previous version of 'sword'");
        assertEquals("[items/a.yml] 'sword': broken (kept the previous version of 'sword')", d.format());
    }

    @Test
    void errorLinesOnlyContainErrors() {
        LoadReport.global().begin();
        LoadReport.global().add(new ConfigDiagnostic(Severity.ERROR, "X", "x.yml", "a", null, "bad", null));
        LoadReport.global().add(new ConfigDiagnostic(Severity.WARN, "X", "x.yml", "b", null, "meh", null));
        assertEquals(List.of("[x.yml] 'a': bad"), LoadReport.errorLines(LoadReport.global().drain()));
        assertTrue(LoadReport.global().drain().isEmpty());
    }

    @Test
    void completeKeepsLastSnapshot() {
        LoadReport.global().begin();
        LoadReport.global().add(new ConfigDiagnostic(Severity.WARN, "X", "x.yml", "b", null, "meh", null));
        LoadReport.global().recordLoaded(new LoadReport.TypeStats("X", 3, 1, 5, 0, 0, 1));
        var snap = LoadReport.global().complete("Test");
        assertEquals(1, snap.count(Severity.WARN));
        assertEquals(3, snap.totalLoaded());
        assertSame(snap, LoadReport.global().lastCompleted());
    }

    @Test
    void scopeAttributesAndNestsPaths() {
        List<ConfigDiagnostic> sink = new ArrayList<>();
        try (LoadScope scope = LoadScope.enter("Mobs", "mobs/a.yml", "ghoul", sink::add)) {
            scope.sub("drops").sub("2").sub("item").warn("unknown item 'x'");
            Diagnostics.error("broken");
            assertEquals(1, scope.errorCount());
            assertEquals(1, scope.warningCount());
        }
        assertEquals(0, LoadScope.depth());
        assertEquals("drops[2].item", sink.get(0).path());
        assertEquals("ghoul", sink.get(1).entryId());
        assertEquals(Severity.ERROR, sink.get(1).severity());
    }

    @Test
    void nestedScopeWinsAndUnwinds() {
        List<ConfigDiagnostic> outer = new ArrayList<>();
        List<ConfigDiagnostic> inner = new ArrayList<>();
        try (LoadScope a = LoadScope.enter("A", "a.yml", "one", outer::add)) {
            try (LoadScope b = LoadScope.enter("B", "b.yml", "two", inner::add)) {
                Diagnostics.warn("inner");
            }
            Diagnostics.warn("outer");
        }
        assertEquals(1, outer.size());
        assertEquals(1, inner.size());
        assertEquals(0, LoadScope.depth());
    }

    @Test
    void suggestionsFindCloseNames() {
        List<String> names = List.of("give", "teleport", "spawn_mob", "enchanted_flesh", "pack:frost_sword");
        assertEquals("give", Suggestions.closest("gve", names).orElseThrow());
        assertEquals("enchanted_flesh", Suggestions.closest("enchanted_flsh", names).orElseThrow());
        assertEquals("teleport", Suggestions.closest("TELEPROT", names).orElseThrow());
        assertEquals("pack:frost_sword", Suggestions.closest("frost_swrd", names).orElseThrow());
        assertTrue(Suggestions.closest("completely_different", names).isEmpty());
        assertNull(Suggestions.hint("xyz", names));
    }
}
