package org.nakii.valmora.module.pack.validate;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.pack.manifest.PackDependency;
import org.nakii.valmora.module.pack.manifest.PackManifest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PackDependencyResolverTest {

    private static PackManifest manifest(String id, String version, List<PackDependency> deps, List<PackDependency> softDeps) {
        return new PackManifest(id, id, version, "author", "desc", "1.0.0", null,
                List.of(), deps, softDeps, List.of("items"), List.of(), null);
    }

    private static PackManifest simple(String id) {
        return manifest(id, "1.0.0", List.of(), List.of());
    }

    @Test
    void ordersIndependentPacksInAnyValidOrderWithNoErrors() {
        Map<String, PackManifest> candidates = Map.of("a", simple("a"), "b", simple("b"));
        var result = PackDependencyResolver.resolve(candidates, Map.of());

        assertTrue(result.report().isValid());
        assertEquals(2, result.installOrder().size());
        assertTrue(result.installOrder().containsAll(List.of("a", "b")));
    }

    @Test
    void ordersADependencyBeforeItsDependent() {
        PackManifest base = simple("base");
        PackManifest dependent = manifest("addon", "1.0.0", List.of(new PackDependency("base", null)), List.of());
        Map<String, PackManifest> candidates = Map.of("base", base, "addon", dependent);

        var result = PackDependencyResolver.resolve(candidates, Map.of());

        assertTrue(result.report().isValid());
        assertTrue(result.installOrder().indexOf("base") < result.installOrder().indexOf("addon"));
    }

    @Test
    void detectsACircularDependency() {
        PackManifest a = manifest("a", "1.0.0", List.of(new PackDependency("b", null)), List.of());
        PackManifest b = manifest("b", "1.0.0", List.of(new PackDependency("a", null)), List.of());
        Map<String, PackManifest> candidates = Map.of("a", a, "b", b);

        var result = PackDependencyResolver.resolve(candidates, Map.of());

        assertFalse(result.report().isValid());
        assertTrue(result.report().getErrors().stream().anyMatch(e -> e.contains("Circular")));
    }

    @Test
    void missingHardDependencyIsAnError() {
        PackManifest addon = manifest("addon", "1.0.0", List.of(new PackDependency("missing_pack", null)), List.of());
        var result = PackDependencyResolver.resolve(Map.of("addon", addon), Map.of());

        assertFalse(result.report().isValid());
        assertTrue(result.report().getErrors().stream().anyMatch(e -> e.contains("missing_pack")));
    }

    @Test
    void missingSoftDependencyIsOnlyAWarning() {
        PackManifest addon = manifest("addon", "1.0.0", List.of(), List.of(new PackDependency("missing_pack", null)));
        var result = PackDependencyResolver.resolve(Map.of("addon", addon), Map.of());

        assertTrue(result.report().isValid());
        assertTrue(result.report().getWarnings().stream().anyMatch(w -> w.contains("missing_pack")));
    }

    @Test
    void alreadyInstalledPackSatisfiesADependency() {
        PackManifest installedBase = simple("base");
        PackManifest addon = manifest("addon", "1.0.0", List.of(new PackDependency("base", ">=1.0.0")), List.of());

        var result = PackDependencyResolver.resolve(Map.of("addon", addon), Map.of("base", installedBase));

        assertTrue(result.report().isValid());
        assertEquals(List.of("addon"), result.installOrder());
    }

    @Test
    void versionConstraintMismatchAgainstInstalledPackIsAnError() {
        PackManifest installedBase = manifest("base", "0.5.0", List.of(), List.of());
        PackManifest addon = manifest("addon", "1.0.0", List.of(new PackDependency("base", ">=1.0.0")), List.of());

        var result = PackDependencyResolver.resolve(Map.of("addon", addon), Map.of("base", installedBase));

        assertFalse(result.report().isValid());
        assertTrue(result.report().getErrors().stream().anyMatch(e -> e.contains("base")));
    }

    @Test
    void unparseableVersionConstraintIsAnError() {
        PackManifest installedBase = simple("base");
        PackManifest addon = manifest("addon", "1.0.0", List.of(new PackDependency("base", ">=not-a-version")), List.of());

        var result = PackDependencyResolver.resolve(Map.of("addon", addon), Map.of("base", installedBase));

        assertFalse(result.report().isValid());
    }
}
