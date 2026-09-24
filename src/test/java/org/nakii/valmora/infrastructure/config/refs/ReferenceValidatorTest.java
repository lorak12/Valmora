package org.nakii.valmora.infrastructure.config.refs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.infrastructure.config.diag.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceValidatorTest {

    private final ContentIndex index = new ContentIndex();
    private final ReferenceIndex refs = new ReferenceIndex();
    private final Set<String> items = Set.of("enchanted_flesh", "pack:frost_sword");

    {
        index.register(Kinds.ITEM, id -> items.contains(id.toLowerCase()), () -> items);
    }

    @AfterEach
    void cleanup() {
        YamlLoader.setIdQualifier(null);
    }

    private List<ConfigDiagnostic> run(Map<String, Set<String>> pending) {
        List<ConfigDiagnostic> out = new ArrayList<>();
        new ReferenceValidator(index, refs).runAll(out::add, pending);
        return out;
    }

    private ConfigSource src(String file) {
        return new ConfigSource("Mobs", file, "ghoul", "drops[0].item");
    }

    @Test
    void danglingReferenceWarnsWithSuggestion() {
        refs.record(Kinds.ITEM, "enchanted_flsh", src("mobs/a.yml"));
        refs.record(Kinds.ITEM, "enchanted_flesh", src("mobs/a.yml"));
        List<ConfigDiagnostic> out = run(null);
        assertEquals(1, out.size());
        assertEquals(Severity.WARN, out.get(0).severity());
        assertEquals("[mobs/a.yml] 'ghoul' › drops[0].item: unknown item 'enchanted_flsh' (did you mean 'enchanted_flesh'?)",
                out.get(0).format());
    }

    @Test
    void unknownKindsAreIgnored() {
        refs.record("spell", "fireball", src("mobs/a.yml"));
        assertTrue(run(null).isEmpty());
    }

    @Test
    void bareIdsInsideAPackResolveToThePackNamespace() {
        YamlLoader.setIdQualifier((id, file) -> file.startsWith("packs/") ? "pack:" + id : id);
        refs.record(Kinds.ITEM, "frost_sword", src("packs/mobs.yml"));
        refs.record(Kinds.ITEM, "frost_sword", src("mobs/base.yml"));
        List<ConfigDiagnostic> out = run(null);
        assertEquals(1, out.size(), out.toString());
        assertEquals("mobs/base.yml", out.get(0).file());
    }

    @Test
    void pendingIdsCountAsExisting() {
        refs.record(Kinds.ITEM, "brand_new", src("mobs/a.yml"));
        assertTrue(run(Map.of(Kinds.ITEM, Set.of("brand_new"))).isEmpty());
    }

    @Test
    void clearingAFolderDropsItsReferences() {
        refs.record(Kinds.ITEM, "missing", src("mobs/a.yml"));
        refs.record(Kinds.ITEM, "missing", src("recipes/a.yml"));
        refs.clearFiles("mobs");
        assertEquals(1, refs.size());
        assertEquals("recipes/a.yml", refs.all().get(0).source().file());
    }

    @Test
    void registeredChecksRunAndReport() {
        ReferenceValidator validator = new ReferenceValidator(index, refs);
        validator.register(new ReferenceCheck() {
            @Override public String name() { return "custom"; }
            @Override public void check(ReferenceContext ctx) { ctx.warn("Machines", null, "forge", "slot mismatch", null); }
        });
        List<ConfigDiagnostic> out = new ArrayList<>();
        validator.runAll(out::add);
        assertEquals(1, out.size());
        assertEquals("Machines", out.get(0).category());
    }
}
