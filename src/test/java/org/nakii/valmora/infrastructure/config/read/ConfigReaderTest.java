package org.nakii.valmora.infrastructure.config.read;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.Severity;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigReaderTest {

    private final List<ConfigDiagnostic> diags = new ArrayList<>();

    private YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(text);
        return c;
    }

    private <T> T withScope(java.util.function.Supplier<T> body) {
        try (LoadScope ignored = LoadScope.enter("Test", "test/a.yml", "entry", diags::add)) {
            return body.get();
        }
    }

    @Test
    void requiredAndOptionalStrings() throws Exception {
        var c = yaml("name: Bob\nlist: [a, b]\n");
        withScope(() -> {
            ConfigReader r = ConfigReader.of(c);
            assertEquals("Bob", r.requireString("name"));
            assertNull(r.requireString("missing"));
            assertEquals("def", r.string("list", "def"));
            assertTrue(r.hasErrors());
            LoadResult<String, String> result = r.result(() -> "built");
            assertFalse(result.isSuccess());
            return null;
        });
        assertEquals(Severity.ERROR, diags.get(0).severity());
        assertEquals("missing", diags.get(0).path());
        assertEquals(Severity.WARN, diags.get(1).severity());
    }

    @Test
    void enumsAreLenientAndSuggest() throws Exception {
        var c = yaml("day: monday\nbad: Mnoday\nfancy: SATUR-DAY\n");
        withScope(() -> {
            ConfigReader r = ConfigReader.of(c);
            assertEquals(DayOfWeek.MONDAY, r.enumOf("day", DayOfWeek.class, DayOfWeek.SUNDAY));
            assertEquals(DayOfWeek.SUNDAY, r.enumOf("bad", DayOfWeek.class, DayOfWeek.SUNDAY));
            assertNull(r.requireEnum("fancy", DayOfWeek.class));
            return null;
        });
        assertEquals("did you mean 'MONDAY'?", diags.get(0).hint());
        assertEquals(Severity.ERROR, diags.get(1).severity());
    }

    @Test
    void materials() throws Exception {
        var c = yaml("a: diamond_sword\nb: minecraft:stone\nc: DIAMOND_SWROD\n");
        withScope(() -> {
            ConfigReader r = ConfigReader.of(c);
            assertEquals(Material.DIAMOND_SWORD, r.requireMaterial("a"));
            assertEquals(Material.STONE, r.material("b", null));
            assertEquals(Material.AIR, r.material("c", Material.AIR));
            return null;
        });
        assertEquals(1, diags.size());
        assertEquals("did you mean 'DIAMOND_SWORD'?", diags.get(0).hint());
    }

    @Test
    void numbersClampAndReportBadValues() throws Exception {
        var c = yaml("low: -5\nhigh: 200\nword: lots\nfrac: 2.5\nok: 7\n");
        withScope(() -> {
            ConfigReader r = ConfigReader.of(c);
            assertEquals(0, r.intRange("low", 1, 0, 64));
            assertEquals(64, r.intRange("high", 1, 0, 64));
            assertEquals(1, r.intRange("word", 1, 0, 64));
            assertEquals(3, r.intRange("frac", 1, 0, 64));
            assertEquals(7, r.intRange("ok", 1, 0, 64));
            assertEquals(1.0, r.doubleRange("high", 0.5, 0.0, 1.0));
            assertEquals(9, r.intRange("absent", 9, 0, 64));
            return null;
        });
        assertEquals(5, diags.size());
        assertTrue(diags.stream().allMatch(d -> d.severity() == Severity.WARN));
    }

    @Test
    void stringListAcceptsScalarOrList() throws Exception {
        var c = yaml("one: hello\nmany: [a, b]\n");
        withScope(() -> {
            ConfigReader r = ConfigReader.of(c);
            assertEquals(List.of("hello"), r.stringList("one"));
            assertEquals(List.of("a", "b"), r.stringList("many"));
            assertEquals(List.of(), r.stringList("none"));
            return null;
        });
        assertTrue(diags.isEmpty());
    }

    @Test
    void unknownKeysAreReportedWithSuggestions() throws Exception {
        var c = yaml("materail: STONE\namount: 2\nprevious-ids: [old]\n");
        withScope(() -> ConfigReader.of(c).knownKeys("material", "amount"));
        assertEquals(1, diags.size());
        assertEquals("materail", diags.get(0).path());
        assertEquals("did you mean 'material'?", diags.get(0).hint());
    }

    @Test
    void sectionListReportsAtIndexedPaths() throws Exception {
        var c = yaml("drops:\n  - item: a\n  - 5\n  - {}\n");
        withScope(() -> {
            List<ConfigReader> drops = ConfigReader.of(c).sectionList("drops");
            assertEquals(2, drops.size());
            drops.get(1).requireString("item");
            return null;
        });
        assertEquals("drops[1]", diags.get(0).path());
        assertEquals("drops[2].item", diags.get(1).path());
    }
}
